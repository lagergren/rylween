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

    private fun endgameAutopilot(): Autopilot = Autopilot(bundle.content, systems, AutopilotGoal.CompleteCastle(castle.id))
}
