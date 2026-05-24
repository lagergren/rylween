/**
 * ANSI terminal transport adapter that connects browser WebSocket input and
 * output to the shared terminal runtime APIs.
 */

package rylween.web

import rylween.terminal.AnsiCodes
import rylween.terminal.AnsiInput
import rylween.terminal.AnsiTerminal
import rylween.webcommon.WebSocketConnection
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private const val TEXT_INPUT_CURSOR_ON = "${AnsiCodes.CSI}2 q${AnsiCodes.CSI}?25h"
private const val TEXT_INPUT_CURSOR_OFF = "${AnsiCodes.CSI}?25l"
private const val EOF_CODE = -1

internal const val BROWSER_INPUT_PREFIX = "key:"

/**
 * Browser-backed implementation of the same terminal interface used by the
 * local ANSI runtime.
 *
 * The game runtime still writes ordinary ANSI escape sequences through
 * [AnsiTerminal.writeRaw]. In the web runtime those bytes are sent as text
 * WebSocket frames to `TerminalPage.kt`, whose JavaScript keeps an 80-column
 * screen buffer, interprets the subset of ANSI that Rylween emits
 * (cursor movement, clear-screen/window commands, colors, and cursor visibility),
 * and redraws the `<pre>` as styled spans. Browser key presses travel the other
 * direction as `key:<chars>` WebSocket messages. This adapter queues those key
 * codes and feeds them back into the shared [AnsiInput] helpers, so command
 * reads, text input, arrow decoding, cursor echo, and EOF handling all use the
 * same code path as the local terminal.
 */
internal class BrowserAnsiTerminal(
    private val connection: WebSocketConnection,
) : AnsiTerminal {
    private val inputCodes = LinkedBlockingQueue<Int>()
    private val closed = AtomicBoolean(false)

    override fun writeRaw(text: String) {
        connection.sendText(text)
    }

    override fun flush() = Unit

    override fun readKeyToken(): String? = AnsiInput.decodeKeyToken(::readCode)

    override fun readTextLine(maxLength: Int?): String? {
        writeRaw(TEXT_INPUT_CURSOR_ON)
        return try {
            AnsiInput.readTextLine(maxLength, ::readCode, {}, ::writeRaw, ::flush)
        } finally {
            writeRaw(TEXT_INPUT_CURSOR_OFF)
        }
    }

    override fun pause(ms: Int) {
        if (ms > 0) Thread.sleep(ms.toLong())
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        inputCodes.offer(EOF_CODE)
        connection.close()
    }

    fun acceptClientMessage(message: String) {
        if (!message.startsWith(BROWSER_INPUT_PREFIX)) return
        val payload = message.removePrefix(BROWSER_INPUT_PREFIX)
        payload.forEach { inputCodes.offer(it.code) }
    }

    private fun readCode(timeoutMs: Int?): Int {
        if (timeoutMs != null) return readCodeWithTimeout(timeoutMs)
        val next = inputCodes.take()
        if (next == EOF_CODE) return EOF_CODE
        return next
    }

    private fun readCodeWithTimeout(timeoutMs: Int): Int {
        val next = inputCodes.poll(timeoutMs.toLong(), TimeUnit.MILLISECONDS) ?: return EOF_CODE
        if (next == EOF_CODE) return EOF_CODE
        return next
    }
}
