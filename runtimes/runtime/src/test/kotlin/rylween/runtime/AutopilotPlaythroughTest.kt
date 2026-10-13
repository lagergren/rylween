package rylween.runtime

import rylween.compiled.CompiledHandleKind
import rylween.compiled.readExternalCompiledBundleContentHash
import rylween.compiled.readExternalCompiledGameBundle
import rylween.compiled.readExternalRuntimeSystems
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Plays the game with an [Autopilot] instead of a fixed key script. */
class AutopilotPlaythroughTest {
    private val bundleFile = File(requiredRuntimeTestProperty("rylween.runtime.bundle"))
    private val bundle = bundleFile.readExternalCompiledGameBundle()
    private val systems = bundleFile.readExternalRuntimeSystems()
    private val sessions =
        AutopilotSessions(
            factory = RuntimeSessionFactory.load(bundle, systems, bundleFile.readExternalCompiledBundleContentHash()),
            workDir = File(requiredRuntimeTestProperty("rylween.runtime.testTmp")).resolve("autopilot").also(File::mkdirs),
        )
    private val castle = systems.castleRows.single()
    private val castleVictory = bundle.content.ref(CompiledHandleKind.Milestone, checkNotNull(castle.completionMilestone))

    @Test
    fun autopilotCompletesTheCastleFromTheNearWinningState() {
        val run = sessions.play("endgame-seedless", "near-winning-replay", endgameAutopilot())

        assertTrue(castleVictory in run.state.party.milestones, run.summary())
        assertTrue(run.lastPorts.transcript.any { line -> "You have banished Ydarg" in line }, run.summary())
        assertTrue(run.saveFile.isFile, run.summary())
    }

    /** Seeded runs roll real encounters, damage, castle rooms, and treasure, unlike the seedless default. */
    @Test
    fun autopilotCompletesTheCastleUnderRandomSeeds() {
        val failures =
            (1L..10L).mapNotNull { seed ->
                runCatching { sessions.play("endgame-seed$seed", "near-winning-replay", endgameAutopilot(), seed = seed) }
                    .fold(
                        onSuccess = { run -> "seed $seed did not win\n${run.summary()}".takeUnless { castleVictory in run.milestones } },
                        onFailure = { problem -> "seed $seed: ${problem.message}" },
                    )
            }
        assertTrue(failures.isEmpty(), failures.joinToString("\n\n"))
    }

    /** A party that teleports out at the second floor's stairs comes back to that floor: freed guardians stay freed. */
    @Test
    fun aPartyThatLeavesTheCastleResumesAboveTheMagesItFreed() {
        val endgame = AutopilotEndgamePlan(bundle.content, systems, castle)
        val floors = mutableListOf<Int>()
        var teleported = false
        val plan =
            object : AutopilotPlan by endgame {
                override fun climbCastle(
                    state: RuntimeState,
                    lastFloor: Boolean,
                ): Boolean {
                    if (teleported || state.castle?.floor != 2) return true
                    teleported = true
                    return false
                }

                override fun observe(view: AutopilotView) {
                    val floor = view.state.castle?.floor ?: OUTSIDE
                    if (floors.lastOrNull() != floor) floors += floor
                }
            }
        val run = sessions.play("castle-reentry", "near-winning-replay", Autopilot(bundle.content, systems, plan))

        assertTrue(castleVictory in run.state.party.milestones, run.summary())
        assertTrue(teleported, "the party never reached the second floor's stairs: $floors")
        val afterLeaving = floors.dropWhile { floor -> floor != 2 }.dropWhile { floor -> floor != OUTSIDE }
        assertTrue(afterLeaving.none { floor -> floor == 1 }, "the party climbed past a freed guardian again: $floors")
    }

    private fun endgameAutopilot(): Autopilot = Autopilot(bundle.content, systems, AutopilotEndgamePlan(bundle.content, systems, castle))

    private companion object {
        const val OUTSIDE = 0
    }
}
