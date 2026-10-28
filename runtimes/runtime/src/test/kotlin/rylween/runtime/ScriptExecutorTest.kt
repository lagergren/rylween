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

    @Test
    fun appendedTextStaysBelowThePageAsItsOwnParagraph() {
        val screen = TextCapturingScreen()
        // Dismiss Ron's plea, then the page that names the orc cave.
        runScript(
            scriptRoot(ScriptRootKind.DialogueBranch, "dialogue.ron.peasant.wife.none"),
            thordRuntime(),
            "AA",
            screen,
            "ron-append.log",
        )

        val page = screen.texts.single { lines -> lines.any { line -> line.startsWith("The orcs have a cave") } && pausePromptIn(lines) }
        val cave = page.indexOfFirst { line -> line.startsWith("The orcs have a cave") }
        assertTrue(page.first().startsWith("About 3,000"), "the cave is added below Ron's offer, not on a page of its own: $page")
        assertEquals("", page[cave - 1], "appended flow text starts its own paragraph: $page")
    }

    @Test
    fun aPauseMarkerShowsThePageUpToItThenTheRest() {
        val events = pagesAndPauses(scriptRoot(ScriptRootKind.DialogueBranch, "dialogue.tybalt.erethor.lute.none"), "AAAA")

        val beat = events.indexOf("pause 1500")
        assertTrue(beat > 0, "the rumor pauses at its {pause} marker: $events")
        assertTrue("Erethor is a mighty" !in events[beat - 1], "the page before the beat stops at the marker: ${events[beat - 1]}")
        assertTrue(
            events[beat + 1].startsWith(events[beat - 1]) && "- Erethor is a mighty" in events[beat + 1],
            "after the beat the same page shows the rest below it: ${events[beat + 1]}",
        )
    }

    @Test
    fun aPauseMarkerAtTheEndHoldsThePageOnce() {
        val events = pagesAndPauses(scriptRoot(ScriptRootKind.Scene, "towne.hut"), "L")

        val shown = events.indexOf("You enter the hut.")
        assertEquals("pause 600", events.getOrNull(shown + 1), "the trailing marker holds the page: $events")
        assertEquals(1, events.count { event -> event == "You enter the hut." }, "the held page is not shown again: $events")
    }

    // Every page the script writes, joined into one string, and every pause, in order.
    private fun pagesAndPauses(
        program: CompiledScriptProgram,
        commands: String,
    ): List<String> {
        val events = mutableListOf<String>()
        val screen =
            object : NoOpRuntimeScreen(textColumns = CLASSIC_TEXT_WINDOW_COLUMNS) {
                override fun writeText(lines: List<String>) {
                    events += lines.joinToString(" / ")
                }
            }
        val scripted = ScriptedInput(commands)
        val input =
            object : RuntimeInputPort by scripted {
                override fun pause(ms: Int) {
                    events += "pause $ms"
                }
            }
        RuntimeLog.open(testTmp.resolve("${program.root.slug}-beats.log")).use { log ->
            ScriptExecutor(bundle.content, systems, input, screen, thordRuntime(), log = log).execute(program)
        }
        return events
    }

    private fun pausePromptIn(lines: List<String>): Boolean = lines.lastOrNull()?.startsWith("(") == true

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
