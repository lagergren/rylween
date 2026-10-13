package rylween.runtime

import rylween.compiled.readExternalCompiledGameBundle
import rylween.compiled.readExternalRuntimeSystems
import rylween.dsl.impl.RuntimeMode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PartyPagesTest {
    private val bundleFile = File(requiredRuntimeTestProperty("rylween.runtime.bundle"))
    private val bundle = bundleFile.readExternalCompiledGameBundle()
    private val systems = bundleFile.readExternalRuntimeSystems()

    @Test
    fun invalidPartyPageKeysDoNotRedrawTheModalView() {
        val runtime = RuntimeState.from(bundle.states.first(), bundle.content).copy(currentMode = RuntimeMode.Wilderness)
        val input = ScriptedInput("X\n")
        val screen = CountingPartyPageScreen()

        val completed =
            PartyPages.showPartyPageViews(
                content = bundle.content,
                systems = systems,
                input = input,
                screen = screen,
                runtime = runtime,
            )

        assertTrue(completed)
        assertEquals(1, screen.partyPageShows)
    }

    @Test
    fun explicitRedrawKeyStillRefreshesTheCurrentPartyPage() {
        val runtime = RuntimeState.from(bundle.states.first(), bundle.content).copy(currentMode = RuntimeMode.Wilderness)
        val input = ScriptedInput("!\n")
        val screen = CountingPartyPageScreen()

        PartyPages.showPartyPageViews(
            content = bundle.content,
            systems = systems,
            input = input,
            screen = screen,
            runtime = runtime,
        )

        assertEquals(2, screen.partyPageShows)
    }
}

private class CountingPartyPageScreen : NoOpRuntimeScreen() {
    var partyPageShows = 0

    override fun showPartyPage(
        page: RuntimePartyPageView,
        pauseLine: String,
    ) {
        partyPageShows += 1
    }
}
