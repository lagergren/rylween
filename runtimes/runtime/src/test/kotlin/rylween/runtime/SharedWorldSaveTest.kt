package rylween.runtime

import org.junit.jupiter.api.io.TempDir
import rylween.compiled.CompiledNpcPlacement
import rylween.compiled.CompiledWorldPosition
import rylween.compiled.readExternalCompiledBundleContentHash
import rylween.compiled.readExternalCompiledGameBundle
import rylween.dsl.impl.GridPoint
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** Sessions read the shared world when they start, so a later save must not undo what another session saved since. */
class SharedWorldSaveTest {
    @TempDir
    private lateinit var testTmp: File
    private val bundleFile = File(requiredRuntimeTestProperty("rylween.runtime.bundle"))
    private val bundle = bundleFile.readExternalCompiledGameBundle()
    private val content = bundle.content
    private val bundleHash = bundleFile.readExternalCompiledBundleContentHash()
    private val state = bundle.states.single { content.slug(it.state) == "fresh-replay" }
    private val initialRuntime = RuntimeState.from(state, content)
    private val file get() = testTmp.resolve("world.rsave")

    @Test
    fun aLaterSaveKeepsTheMissionsAnotherSessionCompleted() {
        val (first, second) = content.missions.take(2).map { mission -> mission.id }
        val alice = party("alice")
        val bob = party("bob")
        write(alice, "alice")
        write(bob, "bob")

        write(alice.withCompletedMission(first), "alice")
        val bobAfterSaving = write(bob.withCompletedMission(second), "bob")

        assertEquals(listOf(first, second), read("alice").completedMissions)
        assertEquals(listOf(first, second), bobAfterSaving.completedMissions)
    }

    @Test
    fun anNpcStaysWhereTheFirstSaveReachingTheFilePutIt() {
        val npc = content.characters.first().id
        val placedFirst = CompiledNpcPlacement(npc, CompiledWorldPosition.Wilderness(GridPoint(3, 4)))
        val placedLater = CompiledNpcPlacement(npc, CompiledWorldPosition.Wilderness(GridPoint(9, 9)))

        write(party("alice").copy(npcs = listOf(placedFirst)), "alice")
        val bobAfterSaving = write(party("bob").copy(npcs = listOf(placedLater)), "bob")

        assertEquals(listOf(placedFirst), read("alice").npcs)
        assertEquals(listOf(placedFirst), bobAfterSaving.npcs)
    }

    @Test
    fun eachPartyKeepsItsOwnClock() {
        write(party("alice").copy(day = 2, hour = 3, minute = 15), "alice")
        write(party("bob").copy(day = 5, hour = 20, minute = 45), "bob")

        assertEquals(Triple(2, 3, 15), read("alice").clock())
        assertEquals(Triple(5, 20, 45), read("bob").clock())
    }

    @Test
    fun aPartySavedWhenTheWorldKeptOneClockKeepsThatClock() {
        write(party("alice"), "alice")
        val clockKeys = listOf("realm.day", "realm.hour", "realm.minute")
        val withoutPartyClocks =
            SaveCodec
                .decodeValues(file.readLines())
                .filterKeys { key -> clockKeys.none { clockKey -> key.endsWith(".$clockKey") } }
        file.writeText(SaveCodec.encodeValues(withoutPartyClocks + clockKeys.zip(listOf("4", "7", "30"))))

        write(party("bob").copy(hour = 22), "bob")

        assertEquals(Triple(4, 7, 30), read("alice").clock())
        assertEquals(22, read("bob").hour)
    }

    private fun party(user: String): RuntimeState = initialRuntime.withParty(initialRuntime.party.copy(name = user))

    private fun write(
        runtime: RuntimeState,
        user: String,
    ): RuntimeState = SaveFile.write(file, runtime, bundleHash, content, user, null)

    private fun read(user: String): RuntimeState = SaveFile.read(file, content, { state }, user)

    private fun RuntimeState.clock(): Triple<Int, Int, Int> = Triple(day, hour, minute)
}
