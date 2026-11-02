/** Players' parties seeing each other in the wilderness of the shared world. */

package rylween.bitmapweb

import rylween.runtime.RuntimePartyPresence
import rylween.runtime.RuntimePresencePort
import java.io.IOException
import kotlin.math.abs
import kotlin.math.max

/** How many horse colors the browser client has; players online at the same time get different ones while they last. */
internal const val BITMAP_PARTY_COLORS = 8

/** Another player's party, as one player sees it: where it stands and the color its horse is drawn in. */
internal data class BitmapSeenParty(
    val name: String,
    val x: Int,
    val y: Int,
    val color: Int,
) {
    fun toJson(): String =
        jsonObject(
            "name" to name.json(),
            "x" to x.toString(),
            "y" to y.toString(),
            "color" to color.toString(),
        )
}

/** The message that tells a browser which other parties its player sees. */
internal fun List<BitmapSeenParty>.partiesMessage(): String =
    jsonObject("type" to "parties".json(), "parties" to joinToString(prefix = "[", postfix = "]") { party -> party.toJson() })

/**
 * Where every playing party is, shared by the sessions of one server.
 *
 * A party sees another when both are in the wilderness of the same map and the other is no more squares away than
 * its own sight, in any direction, diagonals included. Sight is the seeing party's, so at night one party may see
 * another that does not see it. Parties do not block each other, so any number may share a square; the list is sorted
 * by square, then name, so the browser draws a crowded square the same way every time.
 *
 * Sessions publish from their own threads. A player's list is recomputed whenever anyone's presence changes, and
 * [dispatch] runs the send, so a slow browser holds up only its own messages, never another player's move. Each send
 * takes the newest list, so a player never ends on an older one.
 */
internal class BitmapPartyPresence(
    private val dispatch: (Runnable) -> Unit,
) {
    private val lock = Any()

    // Guarded by lock, and replaced rather than changed.
    private var players: Map<String, Player> = emptyMap()

    /** Seats [account] while its browser is connected; [send] delivers a message to that browser. */
    fun join(
        account: String,
        send: (String) -> Unit,
    ): Seat {
        val viewer = Viewer(send)
        synchronized(lock) {
            check(account !in players) { "party presence: $account is already seated" }
            players = players + (account to Player(freeColor(account), presence = null, viewer))
        }
        return Seat(account, viewer)
    }

    /** What [account] sees now. */
    fun seenBy(account: String): List<BitmapSeenParty> = synchronized(lock) { seenBy(account, players) }

    // A session that outlived its socket must not move or remove the party of the account's next session.
    private fun publish(
        account: String,
        viewer: Viewer,
        presence: RuntimePartyPresence?,
    ) = change { current ->
        current[account]
            ?.takeIf { player -> player.viewer === viewer }
            ?.let { player -> current + (account to player.copy(presence = presence)) }
            ?: current
    }

    private fun remove(
        account: String,
        viewer: Viewer,
    ) = change { current -> if (current[account]?.viewer === viewer) current - account else current }

    private fun change(next: (Map<String, Player>) -> Map<String, Player>) {
        val changed =
            synchronized(lock) {
                players = next(players)
                players.mapNotNull { (account, player) ->
                    player.viewer.takeIf { viewer -> viewer.see(seenBy(account, players)) }
                }
            }
        changed.forEach { viewer -> dispatch(Runnable(viewer::flush)) }
    }

    /** The account's preferred color, or the next one no one online has, or the preferred one when all are taken. */
    private fun freeColor(account: String): Int {
        val preferred = Math.floorMod(account.hashCode(), BITMAP_PARTY_COLORS)
        val taken = players.values.map(Player::color).toSet()
        return (0 until BITMAP_PARTY_COLORS)
            .map { offset -> (preferred + offset) % BITMAP_PARTY_COLORS }
            .firstOrNull { color -> color !in taken }
            ?: preferred
    }

    private fun seenBy(
        account: String,
        players: Map<String, Player>,
    ): List<BitmapSeenParty> {
        val own = players[account]?.presence?.takeIf { presence -> presence.inWilderness && presence.sight > 0 } ?: return emptyList()
        return players
            .filterKeys { other -> other != account }
            .values
            .mapNotNull { player -> player.presence?.takeIf { other -> own.sees(other) }?.let { other -> other to player.color } }
            .map { (other, color) -> BitmapSeenParty(other.partyName, other.position.x, other.position.y, color) }
            .sortedWith(compareBy(BitmapSeenParty::y, BitmapSeenParty::x, BitmapSeenParty::name))
    }

    private fun RuntimePartyPresence.sees(other: RuntimePartyPresence): Boolean =
        other.inWilderness &&
            other.map == map &&
            max(abs(other.position.x - position.x), abs(other.position.y - position.y)) <= sight

    /** One browser's session: the runtime reports through it, and the server clears it when the party leaves play. */
    inner class Seat(
        private val account: String,
        private val viewer: Viewer,
    ) : RuntimePresencePort {
        override fun update(presence: RuntimePartyPresence) = publish(account, viewer, presence)

        /** Takes the party out of the world while its player is on the title screen. */
        fun hide() = publish(account, viewer, null)

        /** Takes the player off the server. */
        fun leave() = remove(account, viewer)
    }

    private data class Player(
        val color: Int,
        val presence: RuntimePartyPresence?,
        val viewer: Viewer,
    )

    class Viewer(
        private val send: (String) -> Unit,
    ) {
        @Volatile
        private var latest: List<BitmapSeenParty> = emptyList()

        // Guarded by this viewer's monitor.
        private var sent: List<BitmapSeenParty> = emptyList()

        /** Records what the player sees now; true when that differs from the last list recorded. */
        fun see(parties: List<BitmapSeenParty>): Boolean {
            if (parties == latest) return false
            latest = parties
            return true
        }

        fun flush() {
            synchronized(this) {
                val parties = latest
                if (parties == sent) return
                try {
                    send(parties.partiesMessage())
                    sent = parties
                } catch (_: IOException) {
                    // The browser has gone; its session takes it off the server when the socket closes.
                }
            }
        }
    }
}
