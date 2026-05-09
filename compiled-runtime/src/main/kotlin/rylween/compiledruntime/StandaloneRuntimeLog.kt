package rylween.compiledruntime

import java.io.Closeable
import java.io.File
import java.io.PrintWriter
import java.time.Instant

class StandaloneRuntimeLog private constructor(
    private val writer: PrintWriter,
    val file: File,
) : Closeable {
    fun event(message: String) {
        writer.println("${Instant.now()} $message")
        writer.flush()
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
