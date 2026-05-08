/**
 * ANSI terminal boundary for the standalone compiled-bundle runtime.
 *
 * This file owns only the compiled runtime's terminal transport: JLine raw
 * mode for interactive play and stdout for scripted smoke runs. Escape
 * constants and key decoding live in the `:ansi-terminal` module, shared with
 * the old ANSI launcher, so this source set does not fork that protocol or
 * depend on the classic host.
 */
@file:Suppress("ReplacePrintlnWithLogging", "UseOfSystemOutOrSystemErr")

package rylween.compiledruntime

import org.jline.terminal.Attributes
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import org.jline.utils.NonBlockingReader
import rylween.frontend.ansi.AnsiCodes
import rylween.frontend.ansi.AnsiInput
import java.io.Closeable
import java.io.PrintWriter

/** Minimal raw ANSI I/O contract used by [CompiledAnsiScreen]. */
internal interface CompiledAnsiTerminal : Closeable {
    /** Writes raw text, including ANSI escape sequences, without adding a newline. */
    fun writeRaw(text: String)

    /** Flushes any buffered terminal output. */
    fun flush()

    /** Reads one normalized key token, or `null` when input closes. */
    fun readKeyToken(): String?

    /** Reads an editable text line for future compiled prompts that need text input. */
    fun readTextLine(maxLength: Int? = null): String?

    /** Sleeps without giving terminal ownership to game logic. */
    fun pause(ms: Int)
}

/** Opens terminal implementations for interactive and scripted compiled-runtime runs. */
internal object CompiledAnsiTerminals {
    /** Opens raw JLine for interactive play and plain stdout for scripted smoke runs. */
    fun open(scripted: Boolean): CompiledAnsiTerminal {
        if (scripted) return StdoutCompiledAnsiTerminal()
        return JLineCompiledAnsiTerminal.open()
    }

    /** Opens plain stdout for one-shot static screen rendering. */
    fun stdout(): CompiledAnsiTerminal = StdoutCompiledAnsiTerminal()
}

private class StdoutCompiledAnsiTerminal : CompiledAnsiTerminal {
    private val writer = PrintWriter(System.out, false)

    override fun writeRaw(text: String) {
        writer.print(text)
    }

    override fun flush() {
        writer.flush()
    }

    override fun readKeyToken(): String? = readlnOrNull()?.firstOrNull()?.toString()

    override fun readTextLine(maxLength: Int?): String? {
        val line = readlnOrNull() ?: return null
        return maxLength?.let(line::take) ?: line
    }

    override fun pause(ms: Int) {
        if (ms > 0) Thread.sleep(ms.toLong())
    }

    override fun close() {
        writeRaw("${AnsiCodes.RESET}${AnsiCodes.CSI}?25h")
        flush()
    }
}

private class JLineCompiledAnsiTerminal private constructor(
    private val terminal: Terminal,
    private val savedAttributes: Attributes,
) : CompiledAnsiTerminal {
    private val reader: NonBlockingReader = terminal.reader()
    private val writer = terminal.writer()

    override fun writeRaw(text: String) {
        writer.print(text)
    }

    override fun flush() {
        writer.flush()
    }

    override fun readKeyToken(): String? = AnsiInput.decodeKeyToken(::readCode)

    override fun readTextLine(maxLength: Int?): String? = AnsiInput.readTextLine(maxLength, ::readCode, {}, ::writeRaw, ::flush)

    override fun pause(ms: Int) {
        if (ms > 0) Thread.sleep(ms.toLong())
    }

    override fun close() {
        writeRaw("${AnsiCodes.RESET}${AnsiCodes.CSI}?25h")
        flush()
        terminal.attributes = savedAttributes
        terminal.close()
    }

    private fun readCode(timeoutMs: Int?): Int = timeoutMs?.let { reader.read(it.toLong()) } ?: reader.read()

    companion object {
        fun open(): JLineCompiledAnsiTerminal {
            val terminal =
                TerminalBuilder
                    .builder()
                    .name("rylween-compiled")
                    .system(true)
                    .build()
            return JLineCompiledAnsiTerminal(terminal, terminal.enterRawMode())
        }
    }
}
