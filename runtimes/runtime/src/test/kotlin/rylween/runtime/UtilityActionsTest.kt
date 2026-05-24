package rylween.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

class UtilityActionsTest {
    @Test
    fun priestSpellMenuLineSeparatesKeyAndSpellName() {
        assertEquals("(A) Healing", priestSpellMenuLine('A', "Healing"))
    }
}
