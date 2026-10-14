package rylween.runtime

import org.junit.jupiter.api.io.TempDir
import rylween.compiled.CompiledHandleKind
import rylween.compiled.readExternalCompiledBundleContentHash
import rylween.compiled.readExternalCompiledGameBundle
import rylween.compiled.readExternalRuntimeSystems
import rylween.dsl.impl.RuntimeMode
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PartyPagesTest {
    @TempDir
    private lateinit var testTmp: File
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

    @Test
    fun theLastPartyPageListsTheMonstersSlainMostFirstAndSurvivesASave() {
        val content = bundle.content
        val base = RuntimeState.from(bundle.states.single { state -> content.slug(state.state) == "fresh-replay" }, content)
        val kobold = content.ref(CompiledHandleKind.Monster, "kobold")
        val orc = content.ref(CompiledHandleKind.Monster, "orc")
        val runtime = base.withParty(base.party.copy(monsterKills = mapOf(kobold to 3, orc to 12)))
        val save = testTmp.resolve("kill-ledger.rsave")
        SaveFile.write(save, runtime, bundleFile.readExternalCompiledBundleContentHash(), content, "ledger", null)
        val loaded = SaveFile.read(save, content, { slug -> bundle.states.single { state -> content.slug(state.state) == slug } }, "ledger")
        val screen = CountingPartyPageScreen()

        // Paging back from the first page wraps around to the last one.
        PartyPages.showPartyPageViews(content, systems, ScriptedInput("4\n"), screen, loaded.copy(currentMode = RuntimeMode.Wilderness))

        val ledger = screen.pages.last()
        assertEquals(RuntimePartyPageKind.Kills, ledger.kind)
        assertEquals(listOf("Orc" to 12, "Kobold" to 3), ledger.kills?.entries?.map { kill -> kill.monster to kill.count })
    }

    @Test
    fun aPartyThatHasSlainNothingSeesTheEmptyLedger() {
        val runtime = RuntimeState.from(bundle.states.first(), bundle.content).copy(currentMode = RuntimeMode.Wilderness)
        val screen = CountingPartyPageScreen()

        PartyPages.showPartyPageViews(bundle.content, systems, ScriptedInput("4\n"), screen, runtime)

        val ledger = screen.pages.last()
        assertEquals(RuntimePartyPageKind.Kills, ledger.kind)
        assertTrue(ledger.lines.contains("No monsters slain yet."), ledger.lines.joinToString("\n"))
    }
}

private class CountingPartyPageScreen : NoOpRuntimeScreen() {
    val pages = mutableListOf<RuntimePartyPageView>()
    val partyPageShows: Int get() = pages.size

    override fun showPartyPage(
        page: RuntimePartyPageView,
        pauseLine: String,
    ) {
        pages += page
    }
}
