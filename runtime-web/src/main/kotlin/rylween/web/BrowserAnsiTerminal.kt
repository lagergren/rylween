package rylween.web

import rylween.terminal.AnsiCodes
import rylween.terminal.AnsiInput
import rylween.terminal.AnsiTerminal
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private const val TEXT_INPUT_CURSOR_ON = "${AnsiCodes.CSI}2 q${AnsiCodes.CSI}?25h"
private const val TEXT_INPUT_CURSOR_OFF = "${AnsiCodes.CSI}?25l"
private const val INPUT_PREFIX = "key:"
private const val EOF_CODE = -1

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
        if (!message.startsWith(INPUT_PREFIX)) return
        val payload = message.removePrefix(INPUT_PREFIX)
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
