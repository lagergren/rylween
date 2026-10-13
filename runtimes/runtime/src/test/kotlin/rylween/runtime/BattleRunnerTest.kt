package rylween.runtime

import rylween.compiled.CompiledHandleKind
import rylween.compiled.readExternalCompiledGameBundle
import rylween.compiled.readExternalRuntimeSystems
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class BattleRunnerTest {
    private val bundleFile = File(requiredRuntimeTestProperty("rylween.runtime.bundle"))
    private val testTmp = File(requiredRuntimeTestProperty("rylween.runtime.testTmp")).also(File::mkdirs)
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
        val input = ScriptedInput("FAA")

        RuntimeLog.open(testTmp.resolve("battle-saved-names.log")).use { log ->
            BattleRunner(content, systems.combat, systems.pacing.battle, input, screen, log)
                .runMonster(runtime, content.monsters.first().id, mandatory = true)
        }

        val output = screen.textLines.joinToString("\n")
        assertContains(output, "Alice hits for")
        assertFalse("Aragorn hits for" in output, "battle attack text must not use fixture member names")
    }

    @Test
    fun aWonBattleTalliesTheSlainMonstersByKind() {
        val content = bundle.content
        val runtime = RuntimeState.from(bundle.states.single { state -> content.slug(state.state) == "strong-replay" }, content)
        // The chief always fights alone, so attacking target A stays valid for the whole fight.
        val chief = content.ref(CompiledHandleKind.Monster, "hobgoblin_chief")
        val input = ScriptedInput(("F" + "AA".repeat(runtime.party.members.size)).repeat(ROUNDS))

        val screen = CapturingBattleScreen()
        val result =
            RuntimeLog.open(testTmp.resolve("battle-kill-tally.log")).use { log ->
                BattleRunner(content, systems.combat, systems.pacing.battle, input, screen, log)
                    .runMonster(runtime, chief, mandatory = true)
            }

        assertEquals(runtime.party.counters.kills + 1, result.runtime.party.counters.kills, screen.textLines.joinToString("\n"))
        assertEquals(mapOf(chief to 1), result.runtime.party.monsterKills)
    }

    private companion object {
        const val ROUNDS = 20
    }
}

private class CapturingBattleScreen : NoOpRuntimeScreen() {
    val textLines = mutableListOf<String>()

    override fun writeText(lines: List<String>) {
        textLines += lines
    }

    override fun showMessage(lines: List<String>) {
        textLines += lines
    }

    override fun redrawCurrentView(lines: List<String>) {
        textLines += lines
    }
}
