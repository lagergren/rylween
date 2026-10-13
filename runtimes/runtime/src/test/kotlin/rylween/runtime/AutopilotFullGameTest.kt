package rylween.runtime

import rylween.compiled.CompiledHandleKind
import rylween.compiled.readExternalCompiledBundleContentHash
import rylween.compiled.readExternalCompiledGameBundle
import rylween.compiled.readExternalRuntimeSystems
import rylween.dsl.impl.CharacterCreationStat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Plays the whole game with a party rolled at the title menu, from its first
 * steps outside Thord to Baron Ydarg's defeat, and checks that the run was
 * neither much harder nor much easier than the game is meant to be.
 *
 * One seed plays by default. `-Prylween.autopilot.seeds=1,2,3` plays several,
 * for balance work; the report lands in the test tmp directory.
 */
class AutopilotFullGameTest {
    private val bundleFile = File(requiredRuntimeTestProperty("rylween.runtime.bundle"))
    private val bundle = bundleFile.readExternalCompiledGameBundle()
    private val systems = bundleFile.readExternalRuntimeSystems()
    private val content = bundle.content
    private val workDir = File(requiredRuntimeTestProperty("rylween.runtime.testTmp")).resolve("autopilot-full-game").also(File::mkdirs)
    private val sessions =
        AutopilotSessions(
            factory = RuntimeSessionFactory.load(bundle, systems, bundleFile.readExternalCompiledBundleContentHash()),
            workDir = workDir,
        )
    private val castle = systems.castleRows.single()
    private val castleVictory = content.ref(CompiledHandleKind.Milestone, checkNotNull(castle.completionMilestone))

    @Test
    fun aRolledPartyWinsTheGameWithinBalanceBounds() {
        val seeds =
            System
                .getProperty("rylween.autopilot.seeds", "1")
                .split(',')
                .map(String::trim)
                .filter(String::isNotEmpty)
                .map(String::toLong)
        val outcomes = seeds.map(::play)
        workDir.resolve("report.txt").writeText(outcomes.joinToString("\n") { outcome -> outcome.line })

        val problems = outcomes.flatMap(FullGameOutcome::problems)
        assertTrue(problems.isEmpty(), problems.joinToString("\n\n"))
    }

    private fun play(seed: Long): FullGameOutcome {
        val creation = AutopilotCreation(systems, "Fellowship", recruits)
        val plan = AutopilotFullGamePlan(content, systems, castle, seed)
        val autopilot = Autopilot(content, systems, plan, creation)
        val run =
            runCatching {
                sessions.play(
                    name = "seed$seed",
                    stateSlug = "fresh-replay",
                    autopilot = autopilot,
                    seed = seed,
                    maxDays = MAX_DAYS,
                    keysPerDay = KEYS_PER_DAY,
                    maxReloads = MAX_RELOADS,
                    showTitleMenu = true,
                )
            }.getOrElse { problem -> return FullGameOutcome("seed $seed: ${problem.message}", listOf("seed $seed: ${problem.message}")) }
        workDir.resolve("seed$seed-last-day.txt").writeText(run.lastPorts.transcript.joinToString("\n"))
        val stats = autopilot.stats
        val difficulty = AutopilotDifficulty.of(run, stats)
        val rolled = creation.accepted.map { (member, rolledStats) -> "$member:${rolledStats.values}" }
        val line = "seed $seed won=${castleVictory in run.milestones} step='${plan.currentStepName}' blockers=${plan.blockers(run.state)} journalReads=${plan.journalReads} $difficulty rolls=${creation.rolls.values} stats=$rolled"
        val problems =
            buildList {
                if (castleVictory !in run.milestones) add("seed $seed did not win\n${run.summary()}")
                if (stats.priestSpells == 0) add("seed $seed: the priest never cast a spell")
                if (stats.mageSpells == 0) add("seed $seed: the mage never cast a spell")
                if (stats.hunts == 0) add("seed $seed: the ranger never hunted")
                if (plan.journalReads == 0) add("seed $seed: the party never read its journal")
                if (difficulty.score !in SCORE_BOUNDS) add("seed $seed: difficulty out of bounds $SCORE_BOUNDS: $difficulty")
            }
        return FullGameOutcome(line, problems)
    }

    private class FullGameOutcome(
        val line: String,
        val problems: List<String>,
    )

    private companion object {
        const val MAX_DAYS = 300
        const val KEYS_PER_DAY = 6_000
        const val MAX_RELOADS = 5
        // Seeds 1 to 5 scored 106 to 179 when these bounds were set: a game that takes a rolled party much less or
        // much longer than that has had its balance shifted.
        val SCORE_BOUNDS = 80..260

        /** A fighter, a ranger, a mage, and a priest, each rolled until fit for the class. */
        val recruits =
            listOf(
                AutopilotRecruit(
                    "Aragorn",
                    "Male",
                    "Human",
                    "Fighter",
                    mapOf(CharacterCreationStat.WeaponSkill to 55, CharacterCreationStat.Health to 45),
                ),
                AutopilotRecruit(
                    "Legolas",
                    "Male",
                    "Elf",
                    "Ranger",
                    mapOf(CharacterCreationStat.WeaponSkill to 50, CharacterCreationStat.Health to 40),
                ),
                AutopilotRecruit(
                    "Gandalf",
                    "Male",
                    "Human",
                    "Mage",
                    mapOf(CharacterCreationStat.Wisdom to 55, CharacterCreationStat.Health to 30),
                ),
                AutopilotRecruit(
                    "Mira",
                    "Female",
                    "Human",
                    "Priest",
                    mapOf(CharacterCreationStat.Wisdom to 55, CharacterCreationStat.Health to 35),
                ),
            )
    }
}
