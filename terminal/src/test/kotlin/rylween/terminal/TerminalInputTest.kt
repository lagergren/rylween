/**
 * Regression tests for shared ANSI key decoding.
 *
 * Runtime terminal clients depend on
 * this module to translate terminal escape sequences into the original keypad
 * movement tokens consumed by their command loops.
 */

package rylween.terminal

import kotlin.test.Test
import kotlin.test.assertEquals

class TerminalInputTest {
    @Test
    fun arrowsDecodeToNumericMovementKeys() {
        assertEquals("8", decodeEscape("[A"))
        assertEquals("2", decodeEscape("[B"))
        assertEquals("6", decodeEscape("[C"))
        assertEquals("4", decodeEscape("[D"))
    }

    private fun decodeEscape(suffix: String): String? {
        val input = ArrayDeque((AnsiCodes.ESC + suffix).map(Char::code))
        return AnsiInput.decodeKeyToken(readCode = { input.removeFirstOrNull() ?: -1 })
    }
}
