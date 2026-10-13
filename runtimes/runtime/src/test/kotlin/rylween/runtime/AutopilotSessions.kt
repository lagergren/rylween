/**
 * Plays an autopilot run as a series of runtime sessions, one per game day.
 *
 * The runtime grants a fresh allowance of daily moves when a saved party is
 * continued on a later calendar date, so a long run saves and quits at the end
 * of each day and the next session loads that save one date later. The same
 * [Autopilot] carries its plan across sessions.
 */

package rylween.runtime

import rylween.compiled.CompiledHandleRef
import java.io.File
import java.time.LocalDate

/** The outcome of an autopilot run across all its sessions. */
internal class AutopilotRunResult(
    val days: Int,
    val lastResult: RuntimeSessionResult,
    val lastPorts: AutopilotPorts,
    val saveFile: File,
) {
    val state: RuntimeState get() = checkNotNull(lastPorts.state) { "the runtime never drew a state" }

    val milestones: List<CompiledHandleRef> get() = state.party.milestones

    fun summary(): String =
        "autopilot ended with ${lastResult.reason} on day $days after ${lastPorts.keys} keys that day:\n" +
            lastPorts.transcript.takeLast(TRANSCRIPT_TAIL).joinToString("\n")

    private companion object {
        const val TRANSCRIPT_TAIL = 60
    }
}

internal class AutopilotSessions(
    private val factory: RuntimeSessionFactory,
    private val workDir: File,
) {
    /**
     * Plays [stateSlug] with [autopilot] until it finishes, the game ends, or [maxDays] sessions have run.
     * [seed] seeds the first session only; later sessions continue the saved random state.
     */
    fun play(
        name: String,
        stateSlug: String,
        autopilot: Autopilot,
        seed: Long? = null,
        maxDays: Int = 1,
        keysPerDay: Int = 2_000,
        firstDay: LocalDate = LocalDate.of(2026, 1, 1),
    ): AutopilotRunResult {
        val saveFile = workDir.resolve("$name.rsave").also(File::delete)
        var day = 0
        while (true) {
            day++
            val ports = AutopilotPorts(autopilot, keysPerDay)
            val result =
                RuntimeLog.open(workDir.resolve("$name-day$day.log")).use { log ->
                    factory.start(
                        input = ports,
                        screen = ports,
                        config =
                            RuntimeSessionConfig(
                                userId = "autopilot",
                                stateSlug = stateSlug,
                                loadFile = saveFile.takeIf { day > 1 },
                                saveFile = saveFile,
                                seed = seed.takeIf { day == 1 },
                                sessionDate = firstDay.plusDays(day - 1L),
                            ),
                        log = log,
                    )
                }
            val ended = result.reason != RuntimeSessionExitReason.Quit || autopilot.finished
            if (ended || day >= maxDays) return AutopilotRunResult(day, result, ports, saveFile)
        }
    }
}
