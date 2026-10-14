package rylween.runtime

import rylween.compiled.readExternalCompiledGameBundle
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AutopilotJournalTest {
    private val bundle = File(requiredRuntimeTestProperty("rylween.runtime.bundle")).readExternalCompiledGameBundle()
    private val content = bundle.content
    private val state = RuntimeState.from(bundle.states.single { state -> content.slug(state.state) == "fresh-replay" }, content)
    private val journal = AutopilotJournal(content)
    private val crypt = state.locations.single { placement -> content.slug(placement.location) == "nolrim_crypt" }.point

    @Test
    fun aJournalThatMatchesTheWorldHasNoProblems() {
        val lines =
            listOf(
                "Nolrim's Crypt is at ${crypt.x}x${crypt.y}.",
                "Marcus, the hermit lives at 48x7",
                "A hermit named Marcus knows",
                "the way. He lives at 48x7!",
            )

        assertEquals(emptyList(), journal.problems(state, lines, target = "nolrim_crypt" to crypt))
    }

    @Test
    fun wrongCoordinatesAndMissingTargetsAreProblems() {
        val lines = listOf("Nolrim's Crypt is at 1x1.", "Marcus lives at 40x7.")

        val problems = journal.problems(state, lines, target = "nolrim_crypt" to crypt)

        assertTrue(problems.any { problem -> "1x1" in problem }, problems.joinToString("\n"))
        assertTrue(problems.any { problem -> "Marcus is at 48x7" in problem }, problems.joinToString("\n"))
        assertTrue(problems.any { problem -> "does not say where nolrim_crypt is" in problem }, problems.joinToString("\n"))
    }
}
