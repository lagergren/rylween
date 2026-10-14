package rylween.bitmapweb

import rylween.compiled.readExternalCompiledGameBundle
import rylween.compiled.readExternalRuntimeSystems
import rylween.compiled.toExternalBundleArtifact
import rylween.game.RylweenCompiledGame
import rylween.webcommon.WebSocketConnection
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals

class BitmapMusicTest {
    private val bundleBytes = RylweenCompiledGame.nextRylween.toExternalBundleArtifact()

    /** After an escaped battle the wilderness track is chosen with the map already on screen, so it can't wait for a frame. */
    @Test
    fun aNewTrackReachesTheBrowserAtOnceAndARepeatedOneDoesNot() {
        val output = ByteArrayOutputStream()
        val screen =
            BitmapRuntimeScreen(
                bundleBytes.readExternalCompiledGameBundle(),
                bundleBytes.readExternalRuntimeSystems(),
                WebSocketConnection(ByteArrayInputStream(ByteArray(0)), output),
            )

        screen.playMusic("battle")
        screen.playMusic("wilderness-day")
        screen.playMusic("wilderness-day")
        screen.playMusic(null)

        assertEquals(
            listOf(
                """{"type":"music", "id":"battle"}""",
                """{"type":"music", "id":"wilderness-day"}""",
                """{"type":"music", "id":null}""",
            ),
            serverTextFrames(output.toByteArray()),
        )
    }

    /** Decodes the unmasked text frames a server writes, which are all short here. */
    private fun serverTextFrames(bytes: ByteArray): List<String> =
        buildList {
            var index = 0
            while (index < bytes.size) {
                val length = bytes[index + 1].toInt() and 0x7F
                check(length < 126) { "test frames must be short" }
                add(String(bytes, index + 2, length, Charsets.UTF_8))
                index += 2 + length
            }
        }
}
