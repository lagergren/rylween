package rylween.runtime

import rylween.compiled.CompiledHandleKind
import rylween.compiled.CompiledHandleRef
import rylween.compiled.readExternalCompiledBundleContentHash
import rylween.compiled.readExternalCompiledGameBundle
import rylween.compiled.readExternalRuntimeSystems
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Plays the game with an [Autopilot] instead of a fixed key script. */
class AutopilotPlaythroughTest {
    private val bundleFile = File(requiredRuntimeTestProperty("rylween.runtime.bundle"))
    private val testTmp = File(requiredRuntimeTestProperty("rylween.runtime.testTmp")).also(File::mkdirs)
    private val bundle = bundleFile.readExternalCompiledGameBundle()
    private val systems = bundleFile.readExternalRuntimeSystems()
    private val factory = RuntimeSessionFactory.load(bundle, systems, bundleFile.readExternalCompiledBundleContentHash())
    private val castle = systems.castleRows.single()
    private val castleVictory = bundle.content.ref(CompiledHandleKind.Milestone, checkNotNull(castle.completionMilestone))

    @Test
    fun autopilotCompletesTheCastleFromTheNearWinningState() {
        val run = play("near-winning-replay", AutopilotGoal.CompleteCastle(castle.id))

        assertTrue(castleVictory in run.milestones, run.summary())
        assertTrue(run.ports.transcript.any { line -> "You have banished Ydarg" in line }, run.summary())
        assertTrue(run.saveFile.isFile, run.summary())
    }

    /** Seeded runs roll real encounters, damage, castle rooms, and treasure, unlike the seedless default. */
    @Test
    fun autopilotCompletesTheCastleUnderRandomSeeds() {
        val failures =
            (1L..10L).mapNotNull { seed ->
                runCatching { play("near-winning-replay", AutopilotGoal.CompleteCastle(castle.id), seed = seed) }
                    .fold(
                        onSuccess = { run -> "seed $seed did not win\n${run.summary()}".takeUnless { castleVictory in run.milestones } },
                        onFailure = { problem -> "seed $seed: ${problem.message}" },
                    )
            }
        assertTrue(failures.isEmpty(), failures.joinToString("\n\n"))
    }

    private fun play(
        stateSlug: String,
        goal: AutopilotGoal,
        keyBudget: Int = 2_000,
        seed: Long? = null,
    ): AutopilotRun {
        val runName = "autopilot-$stateSlug-${seed ?: "seedless"}"
        val ports = AutopilotPorts(Autopilot(bundle.content, systems, goal), keyBudget)
        val saveFile = testTmp.resolve("$runName.rsave").also(File::delete)
        val result =
            RuntimeLog.open(testTmp.resolve("$runName.log")).use { log ->
                factory.start(
                    input = ports,
                    screen = ports,
                    config = RuntimeSessionConfig(userId = "autopilot", stateSlug = stateSlug, saveFile = saveFile, seed = seed),
                    log = log,
                )
            }
        return AutopilotRun(result, ports, saveFile)
    }

    private class AutopilotRun(
        val result: RuntimeSessionResult,
        val ports: AutopilotPorts,
        val saveFile: File,
    ) {
        val milestones: List<CompiledHandleRef> get() = checkNotNull(ports.state) { "the runtime never drew a state" }.party.milestones

        fun summary(): String =
            "autopilot ended with ${result.reason} after ${ports.keys} keys:\n" + ports.transcript.takeLast(60).joinToString("\n")
    }
}
