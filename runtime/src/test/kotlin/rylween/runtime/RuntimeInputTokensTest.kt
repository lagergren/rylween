package rylween.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

class RuntimeInputTokensTest {
    @Test
    fun `normalizes symbolic browser keys to runtime commands`() {
        assertEquals('8', RuntimeInputTokens.commandChar("ARROW_UP"))
        assertEquals('2', RuntimeInputTokens.commandChar("ARROW_DOWN"))
        assertEquals('4', RuntimeInputTokens.commandChar("ARROW_LEFT"))
        assertEquals('6', RuntimeInputTokens.commandChar("ARROW_RIGHT"))
    }

    @Test
    fun `passes through character command tokens`() {
        assertEquals('V', RuntimeInputTokens.commandChar("v"))
        assertEquals('?', RuntimeInputTokens.commandChar("?"))
        assertEquals(' ', RuntimeInputTokens.commandChar(" "))
        assertEquals('\n', RuntimeInputTokens.commandChar("ENTER"))
        assertEquals('\n', RuntimeInputTokens.commandChar(""))
        assertEquals(RuntimeInputTokens.ESCAPE_COMMAND, RuntimeInputTokens.commandChar("ESCAPE"))
    }
}
