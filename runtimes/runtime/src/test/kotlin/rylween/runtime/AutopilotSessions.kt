/**
 * Plays an autopilot run as a series of runtime sessions, one per game day.
 *
 * The runtime grants a fresh allowance of daily moves when a saved party is
 * continued on a later calendar date, so a long run saves and quits at the end
 * of each day and the next session loads that save one date later. The same
 * [Autopilot] carries its plan across sessions.
 *
 * A fallen party ends its session without saving, as in the original game, so
 * a player starts over from the last save. A run may do the same a few times:
 * the reloaded session gets a fresh seed, as a new session would, so the fight
 * that was lost does not simply repeat.
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
    /** Every day's transcript, in order. */
    val transcript: List<String>,
    /** How often the party fell and the run started over from the last save. */
    val reloads: Int = 0,
) {
    val state: RuntimeState get() = checkNotNull(lastPorts.state) { "the runtime never drew a state" }

    val milestones: List<CompiledHandleRef> get() = state.party.milestones

    fun summary(): String =
        "autopilot ended with ${lastResult.reason} on day $days after $reloads reloads and ${lastPorts.keys} keys that day:\n" +
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
     * Plays [stateSlug] with [autopilot] until it finishes, the game ends, or [maxDays] days have been played.
     * [seed] seeds the first session only; later sessions continue the saved random state. Up to [maxReloads]
     * times, a fallen party starts the day over from the last save instead of ending the run. With [showTitleMenu],
     * the first session opens at the title menu, where the autopilot creates its party.
     */
    fun play(
        name: String,
        stateSlug: String,
        autopilot: Autopilot,
        seed: Long? = null,
        maxDays: Int = 1,
        keysPerDay: Int = 2_000,
        firstDay: LocalDate = LocalDate.of(2026, 1, 1),
        maxReloads: Int = 0,
        showTitleMenu: Boolean = false,
    ): AutopilotRunResult {
        val saveFile = workDir.resolve("$name.rsave").also(File::delete)
        var day = 0
        var reloads = 0
        var reloading = false
        val transcript = mutableListOf<String>()
        while (true) {
            day++
            val reloadSeed = (seed ?: 0L) * RELOAD_SEED_STRIDE + day * RELOAD_SEED_STRIDE + reloads
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
                                loadFile = saveFile.takeIf(File::isFile),
                                saveFile = saveFile,
                                seed = if (reloading) reloadSeed else seed.takeIf { day == 1 },
                                showTitleMenu = showTitleMenu && day == 1 && !reloading,
                                sessionDate = firstDay.plusDays(day - 1L),
                            ),
                        log = log,
                    )
                }
            transcript += "=== day $day ==="
            transcript += ports.transcript
            reloading = result.reason == RuntimeSessionExitReason.GameOver && reloads < maxReloads
            if (reloading) {
                reloads++
                day--
                autopilot.reload()
                continue
            }
            val ended = result.reason != RuntimeSessionExitReason.Quit || autopilot.finished
            if (ended || day >= maxDays) return AutopilotRunResult(day, result, ports, saveFile, transcript, reloads)
        }
    }

    /** Plays one session from [saveFile] that opens at the title menu, as a returning player does. */
    fun fromTitle(
        name: String,
        stateSlug: String,
        saveFile: File,
        autopilot: Autopilot,
        keys: Int = TITLE_KEYS,
    ): AutopilotPorts {
        val ports = AutopilotPorts(autopilot, keys)
        RuntimeLog.open(workDir.resolve("$name.log")).use { log ->
            factory.start(
                input = ports,
                screen = ports,
                config = RuntimeSessionConfig("autopilot", stateSlug, saveFile, saveFile, showTitleMenu = true),
                log = log,
            )
        }
        return ports
    }

    private companion object {
        const val RELOAD_SEED_STRIDE = 1_000_003L
        const val TITLE_KEYS = 2_000
    }
}
