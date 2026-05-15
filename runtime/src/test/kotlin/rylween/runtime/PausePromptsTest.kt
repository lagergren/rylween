package rylween.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

class PausePromptsTest {
    @Test
    fun pausePromptNormalizesAuthoredTrailingBlankLines() {
        val prompts = PausePrompts(listOf("Smash"), "RETURN")

        assertEquals(listOf("Found it.", "", "(Smash RETURN)"), listOf("Found it.", "", "").withPausePrompt(prompts))
    }
}
