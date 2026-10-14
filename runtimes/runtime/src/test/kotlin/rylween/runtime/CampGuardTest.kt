package rylween.runtime

import org.junit.jupiter.api.io.TempDir
import rylween.compiled.readExternalCompiledGameBundle
import rylween.compiled.readExternalRuntimeSystems
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Camping with a guard: the prompt offers its answers, and the text names the guard. */
class CampGuardTest {
    @TempDir
    private lateinit var testTmp: File
    private val bundleFile = File(requiredRuntimeTestProperty("rylween.runtime.bundle"))
    private val bundle = bundleFile.readExternalCompiledGameBundle()
    private val systems = bundleFile.readExternalRuntimeSystems()
    private val content = bundle.content

    // Without an ambush the camp ends without a battle.
    private val realm = systems.realm.copy(camp = systems.realm.camp.copy(ambushChancePercent = 0))

    @Test
    fun theGuardPromptOffersYesAndNoAsChoices() {
        val screen = TextRecordingScreen()

        camp("N", screen)

        val prompt = screen.texts.first()
        assertTrue("(Y)es" in prompt && "(N)o" in prompt, "the guard prompt must offer (Y)es and (N)o: $prompt")
    }

    @Test
    fun theChosenGuardIsNamedInsteadOfGuard() {
        val screen = TextRecordingScreen()
        val runtime = tiredParty()
        val guard = content.strings.value(runtime.party.members[1].nameString)

        camp("Y2", screen, runtime)

        val texts = screen.texts.map { lines -> lines.joinToString(" ") }
        assertTrue(texts.any { text -> text.startsWith("$guard stands guard") }, "the text must name $guard: $texts")
    }

    private fun tiredParty(): RuntimeState {
        val state = bundle.states.single { state -> content.slug(state.state) == "fresh-replay" }
        val runtime = RuntimeState.from(state, content)
        return runtime.withParty(runtime.party.copy(resources = runtime.party.resources.copy(restedHours = 0)))
    }

    private fun camp(
        keys: String,
        screen: RuntimeScreenPort,
        runtime: RuntimeState = tiredParty(),
    ): WildernessActionResult =
        RuntimeLog.open(testTmp.resolve("camp.log")).use { log ->
            WildernessActions(
                content = content,
                systems = systems,
                realm = realm,
                combat = systems.combat,
                wilderness = systems.wilderness,
                commands = systems.commands,
                input = ScriptedInput(keys),
                screen = screen,
                log = log,
            ).camp(runtime)
        }

    private class TextRecordingScreen : NoOpRuntimeScreen() {
        val texts = mutableListOf<List<String>>()

        override fun writeText(lines: List<String>) {
            texts += lines
        }
    }
}
