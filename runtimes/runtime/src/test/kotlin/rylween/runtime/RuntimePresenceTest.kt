package rylween.runtime

import org.junit.jupiter.api.io.TempDir
import rylween.compiled.RuntimeCommandActionId
import rylween.compiled.RuntimeCommandGroupId
import rylween.compiled.readExternalCompiledBundleContentHash
import rylween.compiled.readExternalCompiledGameBundle
import rylween.compiled.readExternalRuntimeSystems
import rylween.dsl.impl.RuntimeMode
import java.io.File
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/** The runtime tells its host where the party is, so other players can see it in the wilderness. */
class RuntimePresenceTest {
    private val bundleFile = File(requiredRuntimeTestProperty("rylween.runtime.bundle"))

    @TempDir
    private lateinit var testTmp: File
    private val bundle = bundleFile.readExternalCompiledGameBundle()
    private val systems = bundleFile.readExternalRuntimeSystems()
    private val content = bundle.content
    private val factory = RuntimeSessionFactory.load(bundle, systems, bundleFile.readExternalCompiledBundleContentHash())
    private val town = content.towns.first()
    private val fresh = RuntimeState.from(bundle.states.single { state -> content.slug(state.state) == "fresh-replay" }, content)

    @Test
    fun aPartyIsSeenInTheWildernessButNotInsideATown() {
        val wilderness = systems.commands.getValue(RuntimeCommandGroupId.Wilderness.id)
        val enter = wilderness.key(RuntimeCommandActionId.Enter)
        val leave = systems.commands.getValue(RuntimeCommandGroupId.Town.id).key(RuntimeCommandActionId.LeaveTown)
        val redraw = wilderness.key(RuntimeCommandActionId.Redraw)
        // Enter, pay the tax, leave, and redraw, which waits in the same place again and so reports nothing new.
        val updates = playFromTown("${enter}P$leave$redraw", hour = 12)

        val outside = RuntimePartyPresence(fresh.party.name, content.slug(fresh.currentMap), true, town.position, sight = 3)
        assertEquals(listOf(outside, outside.copy(inWilderness = false, sight = 0), outside), updates)
    }

    @Test
    fun partiesSeeLessFarAtNight() {
        assertEquals(1, playFromTown("", hour = 2).single().sight)
        assertEquals(3, fresh.copy(hour = 6).presence(content, systems).sight)
    }

    @Test
    fun aPartyOutsideTheWildernessSeesNoOne() {
        val presence = fresh.copy(currentMode = RuntimeMode.Dungeon).presence(content, systems)

        assertEquals(false, presence.inWilderness)
        assertEquals(0, presence.sight)
    }

    /** Starts a party on the first town's square at [hour], plays [commands], and returns every presence update. */
    private fun playFromTown(
        commands: String,
        hour: Int,
    ): List<RuntimePartyPresence> {
        val saveFile = testTmp.resolve("presence.rsave").also(File::delete)
        runSession(ScriptedInput(""), saveFile, loadFile = null)
        val values = SaveCodec.decodeValues(saveFile.readLines())
        val prefix = values.localUserPrefix()
        saveFile.writeText(
            SaveCodec.encodeValues(
                values +
                    mapOf(
                        "${prefix}party.x" to town.position.x.toString(),
                        "${prefix}party.y" to town.position.y.toString(),
                        "${prefix}party.gold" to "2000",
                        "${prefix}realm.hour" to hour.toString(),
                    ),
            ),
        )

        val updates = mutableListOf<RuntimePartyPresence>()
        runSession(ScriptedInput(commands), saveFile, loadFile = saveFile, presence = updates::add)
        return updates.toList()
    }

    private fun runSession(
        input: RuntimeInputPort,
        saveFile: File,
        loadFile: File?,
        presence: RuntimePresencePort = RuntimePresencePort.None,
    ) {
        RuntimeLog.open(testTmp.resolve("presence.log")).use { log ->
            factory.start(
                input = input,
                screen = NoOpRuntimeScreen(),
                config =
                    RuntimeSessionConfig(
                        userId = "local",
                        stateSlug = "fresh-replay",
                        loadFile = loadFile,
                        saveFile = saveFile,
                        sessionDate = LocalDate.of(2026, 5, 11),
                    ),
                log = log,
                presence = presence,
            )
        }
    }

    private fun Map<String, String>.localUserPrefix(): String =
        (1..getValue("users.count").toInt())
            .map { index -> "user.$index." }
            .first { prefix -> get("${prefix}id") == "local" }
}
