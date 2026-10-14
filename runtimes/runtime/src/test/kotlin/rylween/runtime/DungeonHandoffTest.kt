package rylween.runtime

import org.junit.jupiter.api.io.TempDir
import rylween.compiled.CompiledHandleKind
import rylween.compiled.RuntimeCommandActionId
import rylween.compiled.RuntimeCommandGroupId
import rylween.compiled.readExternalCompiledGameBundle
import rylween.compiled.readExternalRuntimeSystems
import rylween.dsl.impl.RuntimeMode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every way into a dungeon shows the authored "Entering <dungeon>" screen and waits for RETURN before the dungeon. */
class DungeonHandoffTest {
    private val bundleFile = File(requiredRuntimeTestProperty("rylween.runtime.bundle"))
    private val bundle = bundleFile.readExternalCompiledGameBundle()
    private val systems = bundleFile.readExternalRuntimeSystems()
    private val content = bundle.content

    @TempDir
    private lateinit var testTmp: File

    @Test
    fun aCaveFoundInTheWildernessIsAnnouncedWithItsLevels() {
        val enter = systems.commands.getValue(RuntimeCommandGroupId.WildernessCave.id).key(RuntimeCommandActionId.Enter)
        val input = ScriptedInput("$enter\n")
        val screen = TextRecordingScreen()

        val entered =
            RuntimeLog.open(testTmp.resolve("cave.log")).use { log ->
                WildernessActions(
                    content = content,
                    systems = systems,
                    realm = systems.realm,
                    combat = systems.combat,
                    wilderness = systems.wilderness,
                    commands = systems.commands,
                    input = input,
                    screen = screen,
                    log = log,
                ).forceCaveDiscovery(runtime()).runtime
            }

        assertEquals(RuntimeMode.Dungeon, entered.currentMode)
        assertHandoff(screen, "Entering Random Cave.", "Levels: ${entered.dungeon!!.maxDepth}")
        assertTrue(input.exhausted, "the handoff waits for RETURN")
    }

    @Test
    fun aMissionDungeonIsAnnouncedOnceTheScriptEntersIt() {
        val gems =
            listOf("red_gem", "blue_gem", "green_gem", "red_gem", "blue_gem").map { slug ->
                content.ref(CompiledHandleKind.Item, slug)
            }
        val base = runtime()
        val withGems =
            base.withParty(gems.foldIndexed(base.party) { index, party, gem -> party.withInventoryItem(party.inventory[index].slot, gem) })
        val program =
            content.scripts.roots.single { root ->
                root.root.kind == ScriptRootKind.Scene &&
                    root.root.slug == "missions.thun_temple"
            }
        // Dismiss the two temple pages, put the gems in the sockets, dismiss the opening altar, then the handoff.
        val input = ScriptedInput("\n\nY\n\n")
        val screen = TextRecordingScreen()

        val entered =
            RuntimeLog.open(testTmp.resolve("mission.log")).use { log ->
                ScriptExecutor(content, systems, input, screen, withGems, log = log).execute(program)
            }

        assertEquals(RuntimeMode.Dungeon, entered.currentMode)
        assertHandoff(screen, "Entering Thun Temple.", "Levels: 18")
        assertTrue(input.exhausted, "the handoff waits for RETURN")
    }

    private fun assertHandoff(
        screen: TextRecordingScreen,
        vararg expected: String,
    ) {
        val handoff = screen.texts.single { lines -> lines.firstOrNull()?.startsWith("Entering ") == true }
        assertEquals(expected.toList() + "", handoff.dropLast(1))
        assertTrue(systems.prompts.returnKeyName in handoff.last(), "the handoff ends in a RETURN prompt: $handoff")
    }

    private fun runtime(): RuntimeState {
        val base = RuntimeState.from(bundle.states.single { state -> content.slug(state.state) == "fresh-replay" }, content)
        return base.withParty(base.party.copy(resources = base.party.resources.copy(timeLeft = 100)))
    }

    private class TextRecordingScreen : NoOpRuntimeScreen() {
        val texts = mutableListOf<List<String>>()

        override fun writeText(lines: List<String>) {
            texts += lines
        }
    }
}
