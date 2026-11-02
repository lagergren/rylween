package rylween.bitmapweb

import rylween.dsl.impl.GridPoint
import rylween.runtime.RuntimePartyPresence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BitmapPartyPresenceTest {
    private val presence = BitmapPartyPresence(Runnable::run)
    private val sent = mutableMapOf<String, MutableList<String>>()

    @Test
    fun aPartySeesTheOthersWithinItsSightOnTheSameMap() {
        val viewer = join("viewer")
        val diagonal = join("diagonal")
        val beyond = join("beyond")
        val elsewhere = join("elsewhere")
        val inTown = join("in-town")

        viewer.update(at("Viewer", 10, 10, sight = 3))
        diagonal.update(at("Diagonal", 13, 13))
        beyond.update(at("Beyond", 14, 10))
        elsewhere.update(at("Elsewhere", 10, 11).copy(map = "underworld"))
        inTown.update(at("In town", 10, 10).copy(inWilderness = false))

        assertEquals(listOf("Diagonal"), presence.seenBy("viewer").map(BitmapSeenParty::name))
    }

    @Test
    fun sightIsTheSeeingPartys() {
        val owl = join("owl")
        val mole = join("mole")

        owl.update(at("Owl", 1, 1, sight = 3))
        mole.update(at("Mole", 3, 1, sight = 1))

        assertEquals(listOf("Mole"), presence.seenBy("owl").map(BitmapSeenParty::name))
        assertEquals(emptyList(), presence.seenBy("mole"))
    }

    @Test
    fun partiesOnOneSquareAreAllSeenInTheSameOrderEveryTime() {
        val viewer = join("viewer")
        val others = listOf("Carol", "Alice", "Bob", "Dave").map { name -> join(name.lowercase()) to name }

        viewer.update(at("Viewer", 5, 5))
        others.forEach { (seat, name) -> seat.update(at(name, 5, 5)) }
        join("zed").update(at("Zed", 4, 5))

        assertEquals(
            listOf("Zed" to GridPoint(4, 5)) + listOf("Alice", "Bob", "Carol", "Dave").map { name -> name to GridPoint(5, 5) },
            presence.seenBy("viewer").map { party -> party.name to GridPoint(party.x, party.y) },
        )
    }

    @Test
    fun aPartyThatLeavesPlayIsNoLongerSeen() {
        val viewer = join("viewer")
        val other = join("other")
        viewer.update(at("Viewer", 1, 1))
        other.update(at("Other", 2, 2))

        other.hide()
        assertEquals(emptyList(), presence.seenBy("viewer"))

        other.update(at("Other", 2, 2))
        other.leave()
        assertEquals(emptyList(), presence.seenBy("viewer"))
    }

    @Test
    fun aSessionThatOutlivedItsSocketCannotMoveTheNextSessionsParty() {
        val viewer = join("viewer")
        viewer.update(at("Viewer", 1, 1))
        val stale = join("other")
        stale.leave()
        val current = join("other")
        current.update(at("Other", 2, 2))

        stale.update(at("Other", 9, 9))
        stale.leave()

        assertEquals(listOf(GridPoint(2, 2)), presence.seenBy("viewer").map { party -> GridPoint(party.x, party.y) })
    }

    @Test
    fun playersOnlineTogetherHaveDifferentColorsWhileThereAreEnough() {
        val viewer = join("viewer")
        viewer.update(at("Viewer", 1, 1, sight = 9))
        (1 until BITMAP_PARTY_COLORS).forEach { index -> join("player-$index").update(at("Player $index", 2, 2)) }

        val colors = presence.seenBy("viewer").map(BitmapSeenParty::color)
        assertEquals(BITMAP_PARTY_COLORS - 1, colors.toSet().size)
        assertTrue(colors.all { color -> color in 0 until BITMAP_PARTY_COLORS }, "$colors")
    }

    @Test
    fun aBrowserIsSentWhatItsPlayerSeesOnlyWhenThatChanges() {
        val viewer = join("viewer")
        val other = join("other")
        viewer.update(at("Viewer", 1, 1))
        other.update(at("Other", 2, 2))
        val color = presence.seenBy("viewer").single().color
        other.update(at("Other", 2, 2))
        other.update(at("Other", 3, 3))
        other.update(at("Other", 9, 9))

        assertEquals(
            listOf(
                """{"type":"parties", "parties":[{"name":"Other", "x":2, "y":2, "color":$color}]}""",
                """{"type":"parties", "parties":[{"name":"Other", "x":3, "y":3, "color":$color}]}""",
                """{"type":"parties", "parties":[]}""",
            ),
            sent.getValue("viewer"),
        )
    }

    private fun join(account: String): BitmapPartyPresence.Seat =
        presence.join(account) { message -> sent.getOrPut(account) { mutableListOf() } += message }

    private fun at(
        name: String,
        x: Int,
        y: Int,
        sight: Int = 3,
    ): RuntimePartyPresence = RuntimePartyPresence(name, "wilderness", inWilderness = true, GridPoint(x, y), sight)
}
