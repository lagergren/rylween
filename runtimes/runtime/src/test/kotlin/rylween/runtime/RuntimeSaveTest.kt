package rylween.runtime

import org.junit.jupiter.api.io.TempDir
import rylween.compiled.readExternalCompiledBundleContentHash
import rylween.compiled.readExternalCompiledGameBundle
import rylween.compiled.readExternalRuntimeSystems
import rylween.dsl.AudioCue
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** What the player sees when they save, whether the write works or fails. */
class RuntimeSaveTest {
    private val bundleFile = File(requiredRuntimeTestProperty("rylween.runtime.bundle"))
    private val bundle = bundleFile.readExternalCompiledGameBundle()
    private val systems = bundleFile.readExternalRuntimeSystems()
    private val content = bundle.content
    private val hash = bundleFile.readExternalCompiledBundleContentHash()

    @TempDir
    private lateinit var testTmp: File
    private val runtime = RuntimeState.from(bundle.states.single { state -> content.slug(state.state) == "fresh-replay" }, content)

    @Test
    fun savingShowsSavingThenDoneWithoutTheSaveFile() {
        val file = testTmp.resolve("saved.rsave").also(File::delete)
        val screen = TextRecordingScreen()

        val logText = save(file, screen, "saved")

        assertEquals(listOf("Saving position...", "Game saved."), screen.lines)
        assertTrue(file.isFile)
        assertContains(logText, "runtime.save reason=command")
        assertEquals(
            systems.audio.cues
                .getValue(AudioCue.GameSaved.name)
                .take(1),
            screen.sounds,
        )
    }

    @Test
    fun aFailedSaveShowsFailedAndPlayGoesOn() {
        val notADirectory = testTmp.resolve("not-a-directory").apply { writeText("") }
        val screen = TextRecordingScreen()

        val logText = save(notADirectory.resolve("save.rsave"), screen, "failed")

        assertEquals(listOf("Saving position...", "Save failed."), screen.lines)
        assertContains(logText, "runtime.save.failed")
        assertEquals(emptyList(), screen.sounds)
        assertFalse(screen.lines.any { line -> "rsave" in line })
    }

    private fun save(
        file: File,
        screen: RuntimeScreenPort,
        name: String,
    ): String {
        val logFile = testTmp.resolve("$name.log")
        RuntimeLog.open(logFile).use { log ->
            saveRuntimeForPlayer(runtime, content, systems, RuntimeSaveContext(file, hash, "local", null), screen, log)
        }
        return logFile.readText()
    }

    private class TextRecordingScreen : NoOpRuntimeScreen() {
        val lines = mutableListOf<String>()
        val sounds = mutableListOf<String>()

        override fun writeText(lines: List<String>) {
            this.lines.clear()
            this.lines += lines
        }

        override fun appendText(lines: List<String>) {
            this.lines += lines
        }

        override fun playSound(soundSlug: String) {
            sounds += soundSlug
        }
    }
}
