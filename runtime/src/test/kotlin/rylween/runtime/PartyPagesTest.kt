package rylween.runtime

import rylween.compiled.ExternalRuntimeCastle
import rylween.compiled.readExternalCompiledGameBundle
import rylween.compiled.readExternalRuntimeSystems
import rylween.dsl.impl.RuntimeMode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PartyPagesTest {
    private val bundleFile = File(requiredRuntimeProperty("rylween.runtime.bundle"))
    private val bundle = bundleFile.readExternalCompiledGameBundle()
    private val systems = bundleFile.readExternalRuntimeSystems()

    @Test
    fun invalidPartyPageKeysDoNotRedrawTheModalView() {
        val runtime = RuntimeState.from(bundle.states.first(), bundle.content).copy(currentMode = RuntimeMode.Wilderness)
        val input = PartyPageInput("X\n")
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
        val input = PartyPageInput("!\n")
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

private fun requiredRuntimeProperty(name: String): String =
    System.getProperty(name)
        ?: error("missing required test system property $name")

private class PartyPageInput(
    commands: String,
) : RuntimeInputPort {
    private val commands = ArrayDeque(commands.toList())

    override val exhausted: Boolean get() = commands.isEmpty()

    override fun pause(ms: Int) = Unit

    override fun readCommand(): Char? = commands.removeFirstOrNull()

    override fun readInt(maxDigits: Int): Int? = readCommand()?.digitToIntOrNull()

    override fun readText(maxLength: Int?): String? = null
}

private class CountingPartyPageScreen : RuntimeScreenPort {
    var partyPageShows = 0

    override val textColumns: Int = 36

    override fun bootFrame() = Unit

    override fun showStaticScreen(
        screenSlug: String,
        runtime: RuntimeState,
    ) = Unit

    override fun showPagedStaticScreen(
        screenSlug: String,
        runtime: RuntimeState,
        advance: () -> Char?,
    ) = Unit

    override fun showPartyPage(
        page: RuntimePartyPageView,
        pauseLine: String,
    ) {
        partyPageShows += 1
    }

    override fun renderWilderness(
        runtime: RuntimeState,
        world: WorldMap,
    ) = Unit

    override fun showDebugWorldMap(
        runtime: RuntimeState,
        world: WorldMap,
        advance: () -> Char?,
    ) = Unit

    override fun renderDungeon(runtime: RuntimeState) = Unit

    override fun renderCastle(
        runtime: RuntimeState,
        castle: ExternalRuntimeCastle,
    ) = Unit

    override fun writeText(lines: List<String>) = Unit

    override fun showMessage(lines: List<String>) = Unit

    override fun updateStatus(runtime: RuntimeState) = Unit

    override fun writeViewCommand(command: RuntimeViewCommand) = Unit

    override fun showImage(imageSlug: String) = Unit

    override fun redrawCurrentView(lines: List<String>) = Unit

    override fun flashLogLine(line: String) = Unit
}
