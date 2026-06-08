package rylween.runtime

import rylween.compiled.readExternalCompiledGameBundle
import rylween.compiled.readExternalRuntimeSystems
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class BattleRunnerTest {
    private val bundleFile = File(requiredRuntimeBattleProperty("rylween.runtime.bundle"))
    private val testTmp = File(requiredRuntimeBattleProperty("rylween.runtime.testTmp")).also(File::mkdirs)
    private val bundle = bundleFile.readExternalCompiledGameBundle()
    private val systems = bundleFile.readExternalRuntimeSystems()

    @Test
    fun playerAttackTextUsesSavedPartyMemberNames() {
        val content = bundle.content
        val state = bundle.states.single { state -> content.slug(state.state) == "strong-replay" }
        val base = RuntimeState.from(state, content)
        val renamedParty =
            base.party.copy(
                memberNames = listOf("Alice", "Bryn", "Cleo", "Dina"),
                members =
                    base.party.members.mapIndexed { index, member ->
                        member.copy(weaponSkill = member.weaponSkill.takeUnless { index == 0 } ?: 100)
                    },
            )
        val runtime = base.copy(party = renamedParty)
        val screen = CapturingBattleScreen()
        val input = BattleInput("FAA")

        RuntimeLog.open(testTmp.resolve("battle-saved-names.log")).use { log ->
            BattleRunner(content, systems.combat, systems.pacing.battle, input, screen, log)
                .runMonster(runtime, content.monsters.first().id, mandatory = true)
        }

        val output = screen.textLines.joinToString("\n")
        assertContains(output, "Alice hits for")
        assertFalse("Aragorn hits for" in output, "battle attack text must not use fixture member names")
    }
}

private fun requiredRuntimeBattleProperty(name: String): String =
    System.getProperty(name)
        ?: error("missing required test system property $name")

private class BattleInput(
    commands: String,
) : RuntimeInputPort {
    private val commands = ArrayDeque(commands.toList())

    override val exhausted: Boolean get() = commands.isEmpty()

    override fun pause(ms: Int) = Unit

    override fun readCommand(): Char? = commands.removeFirstOrNull()

    override fun readInt(maxDigits: Int): Int? = readCommand()?.digitToIntOrNull()

    override fun readText(maxLength: Int?): String? = null
}

private class CapturingBattleScreen : RuntimeScreenPort {
    val textLines = mutableListOf<String>()

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

    override fun writeText(lines: List<String>) {
        textLines += lines
    }

    override fun showMessage(lines: List<String>) {
        textLines += lines
    }

    override fun updateStatus(runtime: RuntimeState) = Unit

    override fun writeViewCommand(command: RuntimeViewCommand) = Unit

    override fun showImage(imageSlug: String) = Unit

    override fun redrawCurrentView(lines: List<String>) {
        textLines += lines
    }

    override fun flashLogLine(line: String) = Unit
}
