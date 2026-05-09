package rylween.compiledruntime

import java.io.Closeable
import java.io.File
import java.io.PrintWriter
import java.time.Instant

class StandaloneRuntimeLog private constructor(
    private val writer: PrintWriter,
    val file: File,
) : Closeable {
    private var mirror: ((String) -> Unit)? = null

    fun mirrorTo(sink: ((String) -> Unit)?) {
        mirror = sink
    }

    fun event(message: String) {
        val line = "${Instant.now()} $message"
        writer.println(line)
        writer.flush()
        runCatching { mirror?.invoke(line) }
    }

    fun rngDraw(
        context: String,
        lo: Int,
        hi: Int,
        value: Int,
        hit: Boolean? = null,
        details: Map<String, Any?> = emptyMap(),
    ) {
        val hitText = hit?.let { " hit=$it" }.orEmpty()
        val detailText =
            details
                .filterValues { it != null }
                .entries
                .joinToString(separator = "") { (key, detail) -> " $key=$detail" }
        event("rng.draw context=$context range=$lo..$hi value=$value$hitText$detailText")
    }

    override fun close() {
        event("log.close")
        writer.close()
    }

    companion object {
        val defaultFile: File = File("build/logs/compiled-runtime/latest.log")

        fun open(file: File): StandaloneRuntimeLog {
            file.parentFile?.mkdirs()
            return StandaloneRuntimeLog(file.printWriter(), file).also { log ->
                log.event("log.open path=${file.path}")
            }
        }
    }
}
