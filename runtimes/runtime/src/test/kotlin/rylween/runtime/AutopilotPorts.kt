/**
 * Screen and input ports that let an [Autopilot] play a runtime session.
 *
 * The runtime draws to this object and asks it for keys, so the autopilot sees
 * what a player sees: the text written since the previous key and the latest
 * [RuntimeState] passed to a render or status call.
 */

package rylween.runtime

import rylween.compiled.ExternalRuntimeCastle

/** The kind of input the runtime is waiting for. */
internal enum class AutopilotRequest { Key, Number, Text }

/**
 * What the autopilot knows when the runtime asks for input.
 *
 * [text] holds the lines written since the previous key. A prompt that rejects
 * a key often redraws nothing, so [prompt] keeps the most recent non-empty
 * [text] until new text arrives.
 */
internal data class AutopilotView(
    val request: AutopilotRequest,
    val state: RuntimeState,
    val text: List<String>,
    val prompt: List<String>,
)

/** Thrown when the autopilot cannot continue; the message ends with the recent transcript. */
internal class AutopilotStuck(
    message: String,
) : AssertionError(message)

internal class AutopilotPorts(
    private val autopilot: Autopilot,
    private val keyBudget: Int,
    private val transcriptTail: Int = 80,
) : NoOpRuntimeScreen(),
    RuntimeInputPort {
    private val transcriptLines = mutableListOf<String>()
    private val pending = mutableListOf<String>()
    private var prompt: List<String> = emptyList()
    private var finished = false

    /** Number of keys sent to the runtime so far. */
    var keys: Int = 0
        private set

    /** The most recent state the runtime drew. */
    var state: RuntimeState? = null
        private set

    /** Everything the runtime wrote, with each key the autopilot sent marked by a `> ` line. */
    val transcript: List<String> get() = transcriptLines

    override val exhausted: Boolean get() = finished

    override fun pause(ms: Int) = Unit

    override fun readCommand(): Char? = next(AutopilotRequest.Key)

    override fun readInt(maxDigits: Int): Int? = next(AutopilotRequest.Number)?.digitToIntOrNull()

    override fun readText(maxLength: Int?): String? {
        if (finished) return null
        val view = view(AutopilotRequest.Text)
        val answer = ask { autopilot.answer(view) } ?: fail("no answer for a text prompt")
        transcriptLines += "> \"$answer\""
        return answer
    }

    override fun writeText(lines: List<String>) = record(lines)

    override fun showMessage(lines: List<String>) = record(lines)

    override fun redrawCurrentView(lines: List<String>) = record(lines)

    override fun updateStatus(runtime: RuntimeState) {
        state = runtime
    }

    override fun renderWilderness(
        runtime: RuntimeState,
        world: WorldMap,
    ) {
        state = runtime
    }

    override fun renderDungeon(runtime: RuntimeState) {
        state = runtime
    }

    override fun renderCastle(
        runtime: RuntimeState,
        castle: ExternalRuntimeCastle,
    ) {
        state = runtime
    }

    override fun showStaticScreen(
        screenSlug: String,
        runtime: RuntimeState,
    ) {
        state = runtime
    }

    /** Fails the session with [reason] and the end of the transcript. */
    fun fail(reason: String): Nothing =
        throw AutopilotStuck(
            buildString {
                appendLine("autopilot: $reason (after $keys keys)")
                state?.let { current -> appendLine("autopilot: ${current.describe()}") }
                appendLine("--- last ${transcriptTail.coerceAtMost(transcriptLines.size)} transcript lines ---")
                transcriptLines.takeLast(transcriptTail).forEach(::appendLine)
            },
        )

    private fun record(lines: List<String>) {
        transcriptLines += lines
        pending += lines
    }

    private fun view(request: AutopilotRequest): AutopilotView {
        val current = state ?: fail("the runtime asked for input before drawing any state")
        val text = pending.toList()
        pending.clear()
        if (text.any(String::isNotBlank)) prompt = text
        return AutopilotView(request, current, text, prompt)
    }

    /** Runs an autopilot decision, turning its own failure into one that carries the transcript. */
    private fun <T> ask(decision: () -> T): T =
        try {
            decision()
        } catch (problem: AutopilotStuck) {
            fail(problem.message.orEmpty())
        }

    private fun next(request: AutopilotRequest): Char? {
        if (finished) return null
        val view = view(request)
        val decision = ask { autopilot.decide(view) }
        if (decision == null) {
            finished = true
            transcriptLines += "> (autopilot finished)"
            return null
        }
        keys++
        if (keys > keyBudget) fail("key budget of $keyBudget exhausted")
        transcriptLines += "> ${decision.printable()}"
        return decision
    }
}

private fun Char.printable(): String =
    when (this) {
        '\n' -> "RETURN"
        ' ' -> "SPACE"
        else -> toString()
    }

private fun RuntimeState.describe(): String =
    buildString {
        append("mode=$currentMode day=$day ${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}")
        append(" position=${party.position.x},${party.position.y}")
        castle?.let { castle -> append(" castle=${castle.castleId} floor=${castle.floor} room=${castle.point.x},${castle.point.y}") }
        append(" rested=${party.resources.restedHours} food=${party.resources.food} gold=${party.resources.gold}")
        append(" hp=")
        append(party.members.joinToString("/") { member -> if (member.dead) "dead" else "${member.hitPoints.current}" })
    }
