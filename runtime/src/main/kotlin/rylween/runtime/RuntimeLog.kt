package rylween.runtime

import java.io.Closeable
import java.io.File
import java.io.PrintWriter
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

class RuntimeLog private constructor(
    private val fileWriter: PrintWriter,
    val file: File,
) : Closeable {
    private val sinks =
        CopyOnWriteArrayList<(String) -> Unit>(
            listOf { line ->
                fileWriter.println(line)
                fileWriter.flush()
            },
        )
    private val mirrorLock = Any()
    private var mirrorSink: ((String) -> Unit)? = null

    fun addSink(sink: (String) -> Unit): Closeable {
        sinks.add(sink)
        return Closeable { sinks.remove(sink) }
    }

    fun mirrorTo(sink: ((String) -> Unit)?) {
        synchronized(mirrorLock) {
            mirrorSink?.let(sinks::remove)
            mirrorSink = sink
            sink?.let(sinks::add)
        }
    }

    fun event(message: String) {
        val line = "${Instant.now()} $message"
        sinks.forEach { sink -> runCatching { sink(line) } }
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
        fileWriter.close()
    }

    companion object {
        val defaultFile: File = File("build/logs/runtime/latest.log")

        fun open(
            file: File,
            extraSinks: List<(String) -> Unit> = emptyList(),
        ): RuntimeLog {
            file.parentFile?.mkdirs()
            return RuntimeLog(file.printWriter(), file).also { log ->
                extraSinks.forEach { sink -> log.addSink(sink) }
                log.event("log.open path=${file.path}")
            }
        }
    }
}
