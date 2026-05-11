package rylween.runtime

import rylween.terminal.AnsiTerminal
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class RuntimeSessionFactoryTest {
    private val bundleFile = File(requiredRuntimeProperty("rylween.runtime.bundle"))
    private val testTmp = File(requiredRuntimeProperty("rylween.runtime.testTmp")).also(File::mkdirs)

    @Test
    fun staticScreensRenderThroughInjectedTerminal() {
        val terminal = RecordingAnsiTerminal()
        val logFile = testTmp.resolve("session-api-static.log")

        RuntimeLog.open(logFile).use { log ->
            val result =
                RuntimeSessionFactory
                    .load(bundleFile)
                    .renderStaticScreen(terminal, RuntimeStaticScreenConfig(screenSlug = "title"), log)

            assertEquals(RuntimeSessionExitReason.StaticScreenRendered, result.reason)
        }

        assertContains(terminal.output, "ADVENTURERS OF")
        assertFalse(terminal.closed, "programmatic callers own terminal lifetime")
    }

    @Test
    fun wildernessSessionStartsThroughInjectedTerminal() {
        val terminal = RecordingAnsiTerminal()
        val saveFile = testTmp.resolve("session-api.rsave")
        val logFile = testTmp.resolve("session-api-wilderness.log")

        RuntimeLog.open(logFile).use { log ->
            val result =
                RuntimeSessionFactory
                    .load(bundleFile)
                    .start(
                        terminal,
                        RuntimeSessionConfig(
                            saveFile = saveFile,
                            scriptedInput = "",
                        ),
                        log,
                    )

            assertEquals(RuntimeSessionExitReason.ScriptExhausted, result.reason)
        }

        assertContains(terminal.output, "Wilderness")
        assertFalse(terminal.closed, "programmatic callers own terminal lifetime")
    }
}

private class RecordingAnsiTerminal : AnsiTerminal {
    private val buffer = StringBuilder()
    var closed: Boolean = false
        private set

    val output: String
        get() = buffer.toString()

    override fun writeRaw(text: String) {
        buffer.append(text)
    }

    override fun flush() = Unit

    override fun readKeyToken(): String? = null

    override fun readTextLine(maxLength: Int?): String? = null

    override fun pause(ms: Int) = Unit

    override fun close() {
        closed = true
    }
}

private fun requiredRuntimeProperty(name: String): String =
    System.getProperty(name)
        ?: error("missing required test system property $name")
