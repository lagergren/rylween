/**
 * ANSI terminal transport boundary.
 *
 * This file owns only terminal transport: JLine raw
 * mode for interactive play and stdout for scripted smoke runs. Escape
 * constants and key decoding live in the `:terminal` module, so this
 * source set does not fork that protocol.
 */
@file:Suppress("ReplacePrintlnWithLogging", "UseOfSystemOutOrSystemErr")

package rylween.terminal

import org.jline.terminal.Attributes
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import org.jline.utils.NonBlockingReader
import java.io.Closeable
import java.io.PrintWriter

private const val TEXT_INPUT_CURSOR_ON = "${AnsiCodes.CSI}2 q${AnsiCodes.CSI}?25h"
private const val TEXT_INPUT_CURSOR_OFF = "${AnsiCodes.CSI}?25l"

/** Minimal raw ANSI I/O contract used by runtime screen renderers. */
interface AnsiTerminal : Closeable {
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

/** Opens terminal implementations for interactive and scripted runtime runs. */
object AnsiTerminals {
    /** Opens raw JLine for interactive play and plain stdout for scripted smoke runs. */
    fun open(scripted: Boolean): AnsiTerminal {
        if (scripted) return StdoutAnsiTerminal()
        return JLineAnsiTerminal.open()
    }

    /** Opens plain stdout for one-shot static screen rendering. */
    fun stdout(): AnsiTerminal = StdoutAnsiTerminal()
}

private class StdoutAnsiTerminal : AnsiTerminal {
    private val writer = PrintWriter(System.out, false)

    override fun writeRaw(text: String) {
        writer.print(text)
    }

    override fun flush() {
        writer.flush()
    }

    override fun readKeyToken(): String? = readlnOrNull()?.firstOrNull()?.toString()

    override fun readTextLine(maxLength: Int?): String? {
        writeRaw(TEXT_INPUT_CURSOR_ON)
        flush()
        return try {
            val line = readlnOrNull() ?: return null
            maxLength?.let(line::take) ?: line
        } finally {
            writeRaw(TEXT_INPUT_CURSOR_OFF)
            flush()
        }
    }

    override fun pause(ms: Int) {
        if (ms > 0) Thread.sleep(ms.toLong())
    }

    override fun close() {
        writeRaw("${AnsiCodes.RESET}${AnsiCodes.CSI}?25h")
        flush()
    }
}

private class JLineAnsiTerminal private constructor(
    private val terminal: Terminal,
    private val savedAttributes: Attributes,
) : AnsiTerminal {
    private val reader: NonBlockingReader = terminal.reader()
    private val writer = terminal.writer()

    override fun writeRaw(text: String) {
        writer.print(text)
    }

    override fun flush() {
        writer.flush()
    }

    override fun readKeyToken(): String? = AnsiInput.decodeKeyToken(::readCode)

    override fun readTextLine(maxLength: Int?): String? {
        writeRaw(TEXT_INPUT_CURSOR_ON)
        flush()
        return try {
            AnsiInput.readTextLine(maxLength, ::readCode, {}, ::writeRaw, ::flush)
        } finally {
            writeRaw(TEXT_INPUT_CURSOR_OFF)
            flush()
        }
    }

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
        fun open(): JLineAnsiTerminal {
            val terminal =
                TerminalBuilder
                    .builder()
                    .name("rylween")
                    .system(true)
                    .build()
            return JLineAnsiTerminal(terminal, terminal.enterRawMode())
        }
    }
}
