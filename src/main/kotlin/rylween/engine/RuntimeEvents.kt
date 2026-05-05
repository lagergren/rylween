package rylween.engine

enum class RuntimeEventLevel(val tag: String, val priority: Int) {
    Debug("DBG", 10),
    Info("INF", 20),
    Warn("WRN", 30),
    Error("ERR", 40),
    Audit("AUD", 50),
}

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

fun interface RuntimeEventSink {
    fun emit(event: RuntimeEvent)

    companion object {
        val Noop: RuntimeEventSink = RuntimeEventSink { }
    }
}

object RuntimeEventScope {
    private val currentSink = ThreadLocal.withInitial { RuntimeEventSink.Noop }

    fun current(): RuntimeEventSink =
        currentSink.get()

    fun <T> withSink(sink: RuntimeEventSink, body: () -> T): T {
        val previous = currentSink.get()
        currentSink.set(sink)
        return try {
            body()
        } finally {
            currentSink.set(previous)
        }
    }
}

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
        fields = fields.mapNotNull { (key, value) ->
            value?.let { key to it.toString() }
        }.toMap(),
    )

private fun String.logValue(): String =
    if (all { char -> char.isLetterOrDigit() || char in "_-.:/@" }) {
        this
    } else {
        "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }
