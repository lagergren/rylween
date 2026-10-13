package rylween.runtime

import rylween.compiled.CompiledHandleKind
import rylween.compiled.readExternalCompiledGameBundle
import rylween.compiled.readExternalRuntimeSystems
import rylween.dsl.impl.PcClass
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun aLevelUpRaisesACastersSpellPointsWithWisdom() {
        val content = bundle.content
        val base = RuntimeState.from(bundle.states.single { state -> content.slug(state.state) == "strong-replay" }, content)
        val priest = base.party.members.indexOfFirst { member -> member.pcClass == PcClass.Priest }
        val thresholds = checkNotNull(systems.combat.leveling).xpThresholds
        val runtime = base.updateMember(priest) { member -> member.copy(experience = thresholds[member.level + 1] - 1) }
        val chief = content.ref(CompiledHandleKind.Monster, "hobgoblin_chief")
        val input = ScriptedInput(("F" + "AA".repeat(runtime.party.members.size)).repeat(ROUNDS))

        val result =
            RuntimeLog.open(testTmp.resolve("battle-level-up-mana.log")).use { log ->
                BattleRunner(content, systems.combat, systems.pacing.battle, input, CapturingBattleScreen(), log)
                    .runMonster(runtime, chief, mandatory = true)
            }

        val before = runtime.party.members[priest]
        val after = result.runtime.party.members[priest]
        assertTrue(after.level > before.level, "the priest must level up")
        assertTrue(after.wisdom > before.wisdom, "a priest's wisdom grows with each level")
        assertEquals(after.wisdom, after.mana.max)
    }

    @Test
    fun defendingAndBattleCriesLowerMonsterWeaponSkillOnlyWhileItIsAboveThePenalty() {
        assertEquals(1, 5.loweredBy(4))
        assertEquals(4, 4.loweredBy(4))
        assertEquals(3, 3.loweredBy(4))
        assertEquals(1, 3.loweredBy(2))
        assertEquals(2, 2.loweredBy(2))
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
