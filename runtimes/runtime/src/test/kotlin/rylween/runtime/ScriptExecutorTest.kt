package rylween.runtime

import org.junit.jupiter.api.io.TempDir
import rylween.compiled.CompiledHandleKind
import rylween.compiled.CompiledScriptProgram
import rylween.compiled.readExternalCompiledGameBundle
import rylween.compiled.readExternalRuntimeSystems
import rylween.dsl.impl.RuntimeMode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScriptExecutorTest {
    private val bundleFile = File(requiredRuntimeTestProperty("rylween.runtime.bundle"))

    @TempDir
    private lateinit var testTmp: File
    private val bundle = bundleFile.readExternalCompiledGameBundle()
    private val systems = bundleFile.readExternalRuntimeSystems()

    @Test
    fun innLodgingSpendRefreshesStatusBeforeReturningToTown() {
        val runtime = thordRuntime()
        val program = scriptRoot(ScriptRootKind.Scene, "towne.inn")
        val screen = CapturingStatusScreen()

        val after = runScript(program, runtime, "NNYT\n", screen, "inn-lodging-status.log")

        val leaderLevel =
            runtime.party.members
                .first { member -> !member.dead }
                .level
        val cost = leaderLevel * leaderLevel * 15
        val expectedGold = runtime.party.resources.gold - cost
        assertEquals(expectedGold, after.party.resources.gold)
        assertTrue(
            expectedGold in screen.statusGoldUpdates,
            "inn lodging gold spend must be visible in status before the inn scene returns",
        )
    }

    @Test
    fun garthRewardRefreshesStatusBeforeConversationReturns() {
        val runtime = thordRuntime()
        val program = scriptRoot(ScriptRootKind.DialogueBranch, "dialogue.garth.garth.bandits.recovered")
        val screen = CapturingStatusScreen()

        val after = runScript(program, runtime, "\n", screen, "garth-reward-status.log")

        val expectedGold = runtime.party.resources.gold + 2_000
        assertEquals(expectedGold, after.party.resources.gold)
        assertTrue(
            expectedGold in screen.statusGoldUpdates,
            "Garth mission reward gold must be visible in status before the dialogue returns",
        )
    }

    @Test
    fun churchMenuSeparatesProseFromKeyChoices() {
        val menu = townSceneTexts("towne.church", "L").first { lines -> "(H)eal person" in lines }

        val firstChoice = menu.indexOf("(H)eal person")
        assertEquals("", menu[firstChoice - 1], "church menu needs a blank line before its key choices: $menu")
        assertTrue(menu[firstChoice - 2].isNotBlank(), "church menu should keep exactly one blank line: $menu")
        assertTrue(menu.size <= CLASSIC_TEXT_WINDOW_ROWS, "church menu must fit the classic text window: $menu")
    }

    @Test
    fun shopMenusSeparateProseFromKeyChoicesWithoutRepeatingThem() {
        // Decline the forced beer interlude, dismiss its pause, open Buy, then decline the catalog choice.
        val texts = townSceneTexts("towne.smith", "DXBN")

        val home = texts.first { lines -> "(B)uy" in lines }
        assertEquals("", home[home.indexOf("(B)uy") - 1], "shop home needs a blank line before its key choices: $home")
        val catalog = texts.first { lines -> "Armor or Weapon?" in lines }
        assertEquals(1, catalog.count { line -> line.offersChoiceKey("A") }, "catalog choices must be listed once: $catalog")
    }

    private fun townSceneTexts(
        sceneSlug: String,
        commands: String,
    ): List<List<String>> {
        val screen = TextCapturingScreen()
        runScript(scriptRoot(ScriptRootKind.Scene, sceneSlug), thordRuntime(), commands, screen, "$sceneSlug-text.log")
        return screen.texts
    }

    /** The town-services fixture party standing in Thord. */
    private fun thordRuntime(): RuntimeState {
        val content = bundle.content
        val state = bundle.states.single { state -> content.slug(state.state) == "town-services-replay" }
        val thord = content.ref(CompiledHandleKind.Town, "thord")
        return RuntimeState
            .from(state, content)
            .copy(currentMode = RuntimeMode.Town, currentTown = thord)
    }

    private fun scriptRoot(
        kind: ScriptRootKind,
        slug: String,
    ): CompiledScriptProgram =
        bundle.content.scripts.roots.single { root ->
            root.root.kind == kind && root.root.slug == slug
        }

    private fun runScript(
        program: CompiledScriptProgram,
        runtime: RuntimeState,
        commands: String,
        screen: RuntimeScreenPort,
        logName: String,
    ): RuntimeState =
        RuntimeLog.open(testTmp.resolve(logName)).use { log ->
            ScriptExecutor(bundle.content, systems, ScriptedInput(commands), screen, runtime, log = log).execute(program)
        }
}

// The classic ANSI text window region; it truncates anything past its last row.
private const val CLASSIC_TEXT_WINDOW_COLUMNS = 26
private const val CLASSIC_TEXT_WINDOW_ROWS = 11

private class CapturingStatusScreen : NoOpRuntimeScreen() {
    val statusGoldUpdates = mutableListOf<Int>()

    override fun updateStatus(runtime: RuntimeState) {
        statusGoldUpdates += runtime.party.resources.gold
    }
}

private class TextCapturingScreen : NoOpRuntimeScreen(textColumns = CLASSIC_TEXT_WINDOW_COLUMNS) {
    val texts = mutableListOf<List<String>>()

    override fun writeText(lines: List<String>) {
        texts += lines
    }
}
