package rylween.runtime

import rylween.compiled.CompiledHandleKind
import rylween.compiled.readExternalCompiledGameBundle
import rylween.compiled.readExternalRuntimeSystems
import rylween.dsl.impl.RuntimeMode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScriptExecutorTest {
    private val bundleFile = File(requiredRuntimeTestProperty("rylween.runtime.bundle"))
    private val testTmp = File(requiredRuntimeTestProperty("rylween.runtime.testTmp")).also(File::mkdirs)
    private val bundle = bundleFile.readExternalCompiledGameBundle()
    private val systems = bundleFile.readExternalRuntimeSystems()

    @Test
    fun innLodgingSpendRefreshesStatusBeforeReturningToTown() {
        val content = bundle.content
        val state = bundle.states.single { state -> content.slug(state.state) == "town-services-replay" }
        val thord = content.ref(CompiledHandleKind.Town, "thord")
        val runtime =
            RuntimeState
                .from(state, content)
                .copy(currentMode = RuntimeMode.Town, currentTown = thord)
        val program =
            content.scripts.roots.single { root ->
                root.root.kind == ScriptRootKind.Scene && root.root.slug == "towne.inn"
            }
        val screen = CapturingStatusScreen()
        val input = ScriptedInput("NNYT\n")
        val log = RuntimeLog.open(testTmp.resolve("inn-lodging-status.log"))

        val after =
            log.use {
                ScriptExecutor(content, systems, input, screen, runtime, log = it).execute(program)
            }

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
        val content = bundle.content
        val state = bundle.states.single { state -> content.slug(state.state) == "town-services-replay" }
        val thord = content.ref(CompiledHandleKind.Town, "thord")
        val runtime =
            RuntimeState
                .from(state, content)
                .copy(currentMode = RuntimeMode.Town, currentTown = thord)
        val program =
            content.scripts.roots.single { root ->
                root.root.kind == ScriptRootKind.DialogueBranch && root.root.slug == "dialogue.garth.garth.bandits.recovered"
            }
        val screen = CapturingStatusScreen()
        val input = ScriptedInput("\n")
        val log = RuntimeLog.open(testTmp.resolve("garth-reward-status.log"))

        val after =
            log.use {
                ScriptExecutor(content, systems, input, screen, runtime, log = it).execute(program)
            }

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
        val content = bundle.content
        val state = bundle.states.single { state -> content.slug(state.state) == "town-services-replay" }
        val thord = content.ref(CompiledHandleKind.Town, "thord")
        val runtime =
            RuntimeState
                .from(state, content)
                .copy(currentMode = RuntimeMode.Town, currentTown = thord)
        val program =
            content.scripts.roots.single { root ->
                root.root.kind == ScriptRootKind.Scene && root.root.slug == sceneSlug
            }
        val screen = TextCapturingScreen()
        RuntimeLog.open(testTmp.resolve("$sceneSlug-text.log")).use { log ->
            ScriptExecutor(content, systems, ScriptedInput(commands), screen, runtime, log = log).execute(program)
        }
        return screen.texts
    }
}

// The classic ANSI text window region; it truncates anything past its last row.
private const val CLASSIC_TEXT_WINDOW_COLUMNS = 26
private const val CLASSIC_TEXT_WINDOW_ROWS = 11

private fun requiredRuntimeTestProperty(name: String): String =
    System.getProperty(name)
        ?: error("missing required test system property $name")

private class ScriptedInput(
    commands: String,
) : RuntimeInputPort {
    private val commands = ArrayDeque(commands.toList())

    override val exhausted: Boolean get() = commands.isEmpty()

    override fun pause(ms: Int) = Unit

    override fun readCommand(): Char? = commands.removeFirstOrNull()

    override fun readInt(maxDigits: Int): Int? = readCommand()?.digitToIntOrNull()

    override fun readText(maxLength: Int?): String? = null
}

private class CapturingStatusScreen : RuntimeScreenPort {
    val statusGoldUpdates = mutableListOf<Int>()

    override val textColumns: Int = 36

    override fun bootFrame() = Unit

    override fun showStaticScreen(
        screenSlug: String,
        runtime: RuntimeState,
    ) = Unit

    override fun showPagedStaticScreen(
        screenSlug: String,
        runtime: RuntimeState,
        advance: () -> Char?,
    ) = Unit

    override fun renderWilderness(
        runtime: RuntimeState,
        world: WorldMap,
    ) = Unit

    override fun showDebugWorldMap(
        runtime: RuntimeState,
        world: WorldMap,
        advance: () -> Char?,
    ) = Unit

    override fun renderDungeon(runtime: RuntimeState) = Unit

    override fun renderCastle(
        runtime: RuntimeState,
        castle: rylween.compiled.ExternalRuntimeCastle,
    ) = Unit

    override fun writeText(lines: List<String>) = Unit

    override fun showMessage(lines: List<String>) = Unit

    override fun updateStatus(runtime: RuntimeState) {
        statusGoldUpdates += runtime.party.resources.gold
    }

    override fun writeViewCommand(command: RuntimeViewCommand) = Unit

    override fun showImage(imageSlug: String) = Unit

    override fun redrawCurrentView(lines: List<String>) = Unit

    override fun flashLogLine(line: String) = Unit
}

private class TextCapturingScreen : RuntimeScreenPort {
    val texts = mutableListOf<List<String>>()

    override val textColumns: Int = CLASSIC_TEXT_WINDOW_COLUMNS

    override fun bootFrame() = Unit

    override fun showStaticScreen(
        screenSlug: String,
        runtime: RuntimeState,
    ) = Unit

    override fun showPagedStaticScreen(
        screenSlug: String,
        runtime: RuntimeState,
        advance: () -> Char?,
    ) = Unit

    override fun renderWilderness(
        runtime: RuntimeState,
        world: WorldMap,
    ) = Unit

    override fun showDebugWorldMap(
        runtime: RuntimeState,
        world: WorldMap,
        advance: () -> Char?,
    ) = Unit

    override fun renderDungeon(runtime: RuntimeState) = Unit

    override fun renderCastle(
        runtime: RuntimeState,
        castle: rylween.compiled.ExternalRuntimeCastle,
    ) = Unit

    override fun writeText(lines: List<String>) {
        texts += lines
    }

    override fun showMessage(lines: List<String>) = Unit

    override fun updateStatus(runtime: RuntimeState) = Unit

    override fun writeViewCommand(command: RuntimeViewCommand) = Unit

    override fun showImage(imageSlug: String) = Unit

    override fun redrawCurrentView(lines: List<String>) = Unit

    override fun flashLogLine(line: String) = Unit
}
