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
}

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
