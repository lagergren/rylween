/** Where the active party is in the shared world, for hosts that show players each other's parties. */

package rylween.runtime

import rylween.compiled.CompiledContentBundle
import rylween.compiled.ExternalRuntimeSystems
import rylween.dsl.impl.GridPoint
import rylween.dsl.impl.RuntimeMode

/** Where the active party is, and how far it sees other parties. */
data class RuntimePartyPresence(
    val partyName: String,
    /** Slug of the world map the party is on. */
    val map: String,
    /** Whether the party is out in the wilderness. A party in a town, dungeon or castle is not seen and sees no one. */
    val inWilderness: Boolean,
    val position: GridPoint,
    /** How many squares away the party sees other parties, from the game's `realm { partySight }` formula. */
    val sight: Int,
)

/** Told where the active party is whenever that changes, before the runtime waits for the next command. */
fun interface RuntimePresencePort {
    fun update(presence: RuntimePartyPresence)

    companion object {
        /** For hosts that run one party alone. */
        val None = RuntimePresencePort { }
    }
}

/** Passes on only the updates that differ from the one before. */
internal fun RuntimePresencePort.changesOnly(): RuntimePresencePort {
    var last: RuntimePartyPresence? = null
    return RuntimePresencePort { presence ->
        if (presence != last) {
            last = presence
            update(presence)
        }
    }
}

/**
 * The active party's presence. Sight is 0 outside the wilderness or when the game declares no `partySight` formula.
 * The formula sees the hour of the party's day. A random draw in it does not advance the party's random numbers, so
 * showing parties to each other never changes how a game plays out.
 */
internal fun RuntimeState.presence(
    content: CompiledContentBundle,
    systems: ExternalRuntimeSystems,
): RuntimePartyPresence {
    val inWilderness = currentMode == RuntimeMode.Wilderness
    val sight =
        systems.realm.partySight
            ?.takeIf { inWilderness }
            ?.let { formula -> content.evalFormula(formula, party, mapOf("hour" to hour.mod(24))).value.coerceAtLeast(0) }
            ?: 0
    return RuntimePartyPresence(
        partyName = party.name,
        map = content.slug(currentMap),
        inWilderness = inWilderness,
        position = party.position,
        sight = sight,
    )
}
