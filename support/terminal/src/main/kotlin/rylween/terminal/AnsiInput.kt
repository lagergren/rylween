/**
 * Shared raw-key decoding for ANSI terminal clients.
 *
 * Runtime launchers normalize arrow keys to the original numeric keypad
 * movement tokens and need the same simple editable line reader. This file
 * keeps that protocol in the `:terminal` module while each launcher owns its
 * terminal transport.
 */
package rylween.terminal

/** Stateless ANSI key and text-line decoder used by terminal transports. */
object AnsiInput {
    /** Reads one key token, normalizing arrow escape sequences to keypad movement. */
    fun decodeKeyToken(
        readCode: (timeoutMs: Int?) -> Int,
        consumeQueuedLf: () -> Unit = {},
    ): String? {
        val ch = readCode(null)
        if (ch < 0) return null
        if (ch == AnsiCodes.ESC.single().code) return decodeEscapeKey(readCode)
        return decodedPlainKey(ch, consumeQueuedLf)
    }

    /** Reads an editable line with backspace support from a raw terminal. */
    fun readTextLine(
        maxLength: Int?,
        readCode: (timeoutMs: Int?) -> Int,
        consumeQueuedLf: () -> Unit,
        writeRaw: (String) -> Unit,
        flush: () -> Unit,
    ): String? {
        val out = StringBuilder()
        while (true) {
            when (val ch = readCode(null)) {
                -1,
                '\u0003'.code,
                '\u0004'.code,
                -> return null
                '\n'.code,
                '\r'.code,
                -> {
                    consumeQueuedLf()
                    return out.toString()
                }
                '\b'.code,
                '\u007F'.code,
                -> {
                    if (out.isNotEmpty()) {
                        out.deleteCharAt(out.lastIndex)
                        writeRaw("\b \b")
                        flush()
                    }
                }
                else -> appendPrintable(maxLength, out, ch, writeRaw, flush)
            }
        }
    }

    private fun decodeEscapeKey(readCode: (timeoutMs: Int?) -> Int): String {
        val b1 = readCode(50)
        val b2 = if (b1 == '['.code) readCode(50) else -1
        return when (b2) {
            'A'.code -> "8"
            'B'.code -> "2"
            'C'.code -> "6"
            'D'.code -> "4"
            else -> AnsiCodes.ESC
        }
    }

    private fun decodedPlainKey(
        ch: Int,
        consumeQueuedLf: () -> Unit,
    ): String? =
        when (ch) {
            '\u0003'.code,
            '\u0004'.code,
            -> null
            '\n'.code,
            '\r'.code,
            -> {
                consumeQueuedLf()
                ""
            }
            else -> ch.toChar().toString()
        }

    private fun appendPrintable(
        maxLength: Int?,
        out: StringBuilder,
        ch: Int,
        writeRaw: (String) -> Unit,
        flush: () -> Unit,
    ) {
        val cap = maxLength ?: Int.MAX_VALUE
        if (out.length >= cap || ch < ' '.code) return
        val c = ch.toChar()
        out.append(c)
        writeRaw(c.toString())
        flush()
    }
}
