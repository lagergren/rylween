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
    fun theOpeningRunEscapesAsACowardOrFailsAway() {
        val runtime = strongReplay()
        val commands = "R" + "D".repeat(runtime.party.members.size)

        val escaped = seededFights(runtime, commands).first { fight -> fight.flee("battle.flee")?.escaped == true }
        assertContains(escaped.shown, "Coward.")
        assertFalse("You escaped" in escaped.shown)
        assertEquals(runtime.currentMode, escaped.result.runtime.currentMode)
        assertEquals(runtime.party.experience(), escaped.result.runtime.party.experience())

        val caught = seededFights(runtime, commands).first { fight -> fight.flee("battle.flee")?.escaped == false }
        assertContains(caught.shown, "You fail to get away!")
        assertFalse("Coward." in caught.shown)
    }

    @Test
    fun runningBetweenRoundsIsADesperateFlee() {
        val runtime = strongReplay()
        val chance = checkNotNull(systems.combat.standardBattle).opening.desperateFleeChancePercent
        val defend = "D".repeat(runtime.party.members.size)

        val fights = seededFights(runtime, "F${defend}R").take(SEEDS).toList()

        fights.forEach { fight ->
            val flee = checkNotNull(fight.flee("battle.flee_desperate")) { fight.shown }
            assertEquals(flee.value >= chance, flee.escaped)
            assertEquals(flee.escaped, "You escaped..." in fight.shown, fight.shown)
            assertEquals(!flee.escaped, "You fail to get away!" in fight.shown, fight.shown)
        }
        assertTrue(fights.any { fight -> fight.flee("battle.flee_desperate")?.escaped == true })
        assertTrue(fights.any { fight -> fight.flee("battle.flee_desperate")?.escaped == false })
    }

    @Test
    fun nobodyRunsFromAMandatoryEncounter() {
        val runtime = strongReplay()
        val defend = "D".repeat(runtime.party.members.size)

        val opening = fight(runtime, "R$defend", mandatory = true)
        assertContains(opening.shown, "You desperately try to get away")
        assertContains(opening.shown, "Round 2")
        assertEquals(null, opening.flee("battle.flee"))

        val betweenRounds = fight(runtime, "F${defend}R$defend", mandatory = true)
        assertContains(betweenRounds.shown, "You fail to get away!")
        assertContains(betweenRounds.shown, "Round 3")
        assertEquals(null, betweenRounds.flee("battle.flee_desperate"))
    }

    @Test
    fun theAlwaysRunSecretEscapesWithoutADraw() {
        val runtime = strongReplay().copy(alwaysRunFromNonMandatoryEncounters = true)

        val fight = fight(runtime, "R")

        assertContains(fight.shown, "Coward.")
        assertEquals(null, fight.flee("battle.flee"))
        assertEquals(runtime.currentMode, fight.result.runtime.currentMode)
    }

    @Test
    fun aHeldPartyLosesItsTurnWithoutBeingAskedToFightOrRun() {
        val content = bundle.content
        val runtime = RuntimeState.from(bundle.states.single { state -> content.slug(state.state) == "strong-replay" }, content)
        val chief = content.ref(CompiledHandleKind.Monster, "hobgoblin_chief")
        // The chief casts every round, and Hold Creature is the only spell he knows.
        val holding =
            content.copy(
                monsters = content.monsters.map { monster -> if (monster.id == chief) monster.copy(spellPower = 200) else monster },
                spells =
                    content.spells.filter { spell ->
                        val slug = content.slug(spell.id)
                        !slug.startsWith("monster.") || slug == "monster.hold"
                    },
            )
        val screen = CapturingBattleScreen()

        RuntimeLog.open(testTmp.resolve("battle-party-held.log")).use { log ->
            BattleRunner(holding, systems.combat, systems.pacing.battle, ScriptedInput("F" + "D".repeat(runtime.party.members.size)), screen, log)
                .runMonster(runtime, chief, mandatory = true)
        }

        val shown = screen.textLines
        val rounds = checkNotNull(systems.combat.standardBattle).roundLimit
        assertEquals(rounds - 1, shown.count { line -> line == "PARTY IS HELD!" }, "every round after the first is held")
        assertFalse("You still face" in shown, "a held party is never asked to fight or run")
        assertEquals(runtime.party.members.size, shown.count { line -> line == "What now?" }, "the party acts only in the first round")
    }

    @Test
    fun defendingAndBattleCriesLowerMonsterWeaponSkillOnlyWhileItIsAboveThePenalty() {
        assertEquals(1, 5.loweredBy(4))
        assertEquals(4, 4.loweredBy(4))
        assertEquals(3, 3.loweredBy(4))
        assertEquals(1, 3.loweredBy(2))
        assertEquals(2, 2.loweredBy(2))
    }

    private fun strongReplay(): RuntimeState =
        RuntimeState.from(bundle.states.single { state -> bundle.content.slug(state.state) == "strong-replay" }, bundle.content)

    /** Fights the hobgoblin chief, who always fights alone, with [commands]. */
    private fun fight(
        runtime: RuntimeState,
        commands: String,
        mandatory: Boolean = false,
    ): BattleFight {
        val content = bundle.content
        val screen = CapturingBattleScreen()
        val logged = mutableListOf<String>()
        val result =
            RuntimeLog.open(testTmp.resolve("battle-run.log"), listOf { line -> logged += line }).use { log ->
                BattleRunner(content, systems.combat, systems.pacing.battle, ScriptedInput(commands), screen, log)
                    .runMonster(runtime, content.ref(CompiledHandleKind.Monster, "hobgoblin_chief"), mandatory)
            }
        return BattleFight(result, (screen.textLines + result.messages).joinToString("\n"), logged)
    }

    /** The same fight from one seed after another, so a test can find the draw it needs. */
    private fun seededFights(
        runtime: RuntimeState,
        commands: String,
    ): Sequence<BattleFight> = generateSequence(1L) { seed -> seed + 1 }.map { seed -> fight(runtime.withSeed(seed), commands) }

    private fun PartyState.experience(): Int = members.sumOf { member -> member.experience }

    private companion object {
        const val ROUNDS = 20
        const val SEEDS = 40
    }
}

private class BattleFight(
    val result: BattleResult,
    val shown: String,
    private val log: List<String>,
) {
    /** The flee drawn under [context], or null when the battle drew none. */
    fun flee(context: String): FleeDraw? =
        log.firstNotNullOfOrNull { line ->
            FLEE_DRAW
                .find(line)
                ?.takeIf { match -> match.groupValues[1] == context }
                ?.let { match -> FleeDraw(match.groupValues[2].toInt(), match.groupValues[3].toBooleanStrict()) }
        }

    private companion object {
        val FLEE_DRAW = Regex("""rng\.draw context=(\S+) range=\S+ value=(\d+) hit=(true|false)""")
    }
}

private data class FleeDraw(
    val value: Int,
    val escaped: Boolean,
)

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
