package rylween.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

class PausePromptsTest {
    @Test
    fun pausePromptNormalizesAuthoredTrailingBlankLines() {
        val prompts = PausePrompts(listOf("Smash"), "RETURN")

        assertEquals(listOf("Found it.", "", "(Smash RETURN)"), listOf("Found it.", "", "").withPausePrompt(prompts))
    }

    @Test
    fun pausePromptSelectsAVerbForEachLine() {
        val selected = ArrayDeque(listOf(1, 2))
        val prompts = PausePrompts(listOf("Smash", "Bash", "Slash"), "RETURN key") { selected.removeFirst() }

        assertEquals("(Bash RETURN key)", prompts.line())
        assertEquals("(Slash RETURN key)", prompts.line())
    }
}
