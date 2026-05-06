/**
 * Lightweight runtime event logging primitives.
 *
 * Runtime sessions and engine drivers can emit structured lines through
 * [RuntimeEventSink]; app launchers and tests can install scoped sinks with
 * [RuntimeEventScope].
 */
package rylween.engine

/** Severity and audit level for a runtime event line. */
enum class RuntimeEventLevel(
    val tag: String,
    val priority: Int,
) {
    Debug("DBG", 10),
    Info("INF", 20),
    Warn("WRN", 30),
    Error("ERR", 40),
    Audit("AUD", 50),
}

/** Structured event payload rendered as a stable single-line log record. */
data class RuntimeEvent(
    val level: RuntimeEventLevel,
    val module: String,
    val action: String,
    val fields: Map<String, String> = emptyMap(),
) {
    fun line(): String =
        buildString {
            append('[').append(level.tag).append(']')
            append('[').append(module).append("] ")
            append(action)
            fields.forEach { (key, value) ->
                append(' ').append(key).append('=').append(value.logValue())
            }
        }
}

/** Sink abstraction for host-provided runtime event collection. */
fun interface RuntimeEventSink {
    fun emit(event: RuntimeEvent)

    companion object {
        val Noop: RuntimeEventSink = RuntimeEventSink { }
    }
}

/** Thread-local sink scope used by command-line hosts and tests. */
object RuntimeEventScope {
    private val currentSink = ThreadLocal.withInitial { RuntimeEventSink.Noop }

    fun current(): RuntimeEventSink = currentSink.get()

    fun <T> withSink(
        sink: RuntimeEventSink,
        body: () -> T,
    ): T {
        val previous = currentSink.get()
        currentSink.set(sink)
        return try {
            body()
        } finally {
            currentSink.set(previous)
        }
    }
}

/** Convenience factory that drops fields whose values are `null`. */
fun runtimeEvent(
    level: RuntimeEventLevel,
    module: String,
    action: String,
    vararg fields: Pair<String, Any?>,
): RuntimeEvent =
    RuntimeEvent(
        level = level,
        module = module,
        action = action,
        fields =
            fields
                .mapNotNull { (key, value) ->
                    value?.let { key to it.toString() }
                }.toMap(),
    )

private fun String.logValue(): String =
    if (all { char -> char.isLetterOrDigit() || char in "_-.:/@" }) {
        this
    } else {
        "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }
