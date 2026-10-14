package rylween.runtime

import org.junit.jupiter.api.io.TempDir
import rylween.compiled.CompiledHandleKind
import rylween.compiled.RuntimeCommandActionId
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
 * for balance work; the report, coverage, and daily logs land in
 * `build/test-logs/AutopilotFullGameTest/aRolledPartyWinsTheGameWithinBalanceBounds`.
 */
class AutopilotFullGameTest {
    private val bundleFile = File(requiredRuntimeTestProperty("rylween.runtime.bundle"))
    private val bundle = bundleFile.readExternalCompiledGameBundle()
    private val systems = bundleFile.readExternalRuntimeSystems()
    private val content = bundle.content

    @TempDir
    private lateinit var workDir: File
    private val sessions by lazy {
        AutopilotSessions(
            factory = RuntimeSessionFactory.load(bundle, systems, bundleFile.readExternalCompiledBundleContentHash()),
            workDir = workDir,
        )
    }
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
        val line =
            "seed $seed won=${castleVictory in run.milestones} step='${plan.currentStepName}' blockers=${plan.blockers(run.state)} " +
                "journalReads=${plan.journalReads} castleRetreatReasons=${plan.castleRetreatReasons} $difficulty " +
                "rolls=${creation.rolls.values} stats=$rolled"
        autopilot.coverage.recordKey("title", creation.titleKey)
        visitTitleScreens(seed, run, autopilot.coverage)
        val unused = autopilot.coverage.missing(run.state)
        workDir.resolve("seed$seed-coverage.txt").writeText(unused.joinToString("\n"))
        val problems =
            buildList {
                if (castleVictory !in run.milestones) add("seed $seed did not win\n${run.summary()}")
                if (stats.priestSpells == 0) add("seed $seed: the priest never cast a spell")
                if (stats.mageSpells == 0) add("seed $seed: the mage never cast a spell")
                if (stats.hunts == 0) add("seed $seed: the ranger never hunted")
                if (plan.journalReads == 0) add("seed $seed: the party never read its journal")
                if (difficulty.score !in scoreBounds) add("seed $seed: difficulty out of bounds $scoreBounds: $difficulty")
                val required = unused - notInGame - situational
                if (required.isNotEmpty()) add("seed $seed left parts of the game unused: $required")
            }
        return FullGameOutcome(line, problems)
    }

    /**
     * Returns to the title menu after the run, as a player would: reads the scores, the hall of heroes, and the manual,
     * loads the saved game and quits, then deletes the party and leaves.
     */
    private fun visitTitleScreens(
        seed: Long,
        run: AutopilotRunResult,
        coverage: AutopilotCoverage,
    ) {
        val actions = AutopilotActions(systems)
        val quitAfterLoading =
            object : AutopilotPlan {
                private var playedOn = false

                override fun done(state: RuntimeState): Boolean = false

                // The first quit is called off at the confirmation, so both answers get used.
                override fun wilderness(state: RuntimeState): AutopilotAction =
                    if (playedOn) actions.quit() else actions.quit(confirm = false).also { playedOn = true }

                override fun dungeon(state: RuntimeState): AutopilotAction = actions.ascend()
            }
        listOf(
            listOf(
                RuntimeCommandActionId.Scoreboard,
                RuntimeCommandActionId.HallOfFame,
                RuntimeCommandActionId.Documentation,
                RuntimeCommandActionId.ContinueSavedGame,
            ),
            listOf(RuntimeCommandActionId.TerminateParty, RuntimeCommandActionId.Leave),
        ).forEachIndexed { index, visits ->
            val title = AutopilotTitleTour(systems, coverage, visits)
            sessions.fromTitle(
                "seed$seed-title$index",
                "fresh-replay",
                run.saveFile,
                Autopilot(content, systems, quitAfterLoading, coverage = coverage, title = title),
            )
        }
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
        val scoreBounds = 80..260

        /** Content the bundle declares that no scene reaches: no church scene ever sets the donation flag. */
        val notInGame = setOf("quest: church.donation_once.yes")

        /**
         * Content only a situation calls for, which a run may not meet: Rebirth needs a member to fall while the
         * priest can afford it, and dropping in the castle needs junk to carry in.
         */
        val situational = setOf("spell: priest.rebirth", "castle.ydarg.navigation: drop")

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
