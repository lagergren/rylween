/**
 * The game's sound and music files as the bitmap server serves them.
 */

package rylween.bitmapweb

import rylween.compiled.ExternalAudioAsset
import rylween.compiled.ExternalRuntimeAudio
import java.net.URLEncoder
import java.security.MessageDigest

/** One packaged audio file. [version] is a hash of its bytes, used to cache it safely. */
internal data class BitmapAudioFile(
    val id: String,
    val resource: String,
    val contentType: String,
    val version: String,
)

/**
 * Sound effects and music tracks, checked when the server starts.
 *
 * Every URL carries a hash of the file's bytes, so browsers can keep a file
 * cached indefinitely and still fetch it again when the file changes.
 */
internal class BitmapAudioCatalog private constructor(
    private val sounds: Map<String, BitmapAudioFile>,
    private val music: Map<String, BitmapAudioFile>,
) {
    fun sound(id: String): BitmapAudioFile? = sounds[id]

    fun music(id: String): BitmapAudioFile? = music[id]

    /** The `audio` message that tells a client where each sound and track can be fetched. */
    fun catalogJson(): String =
        jsonObject(
            "type" to "audio".json(),
            "sounds" to urlsJson(sounds.values, SOUND_ROUTE),
            "music" to urlsJson(music.values, MUSIC_ROUTE),
        )

    private fun urlsJson(
        files: Collection<BitmapAudioFile>,
        route: String,
    ): String =
        files.sortedBy { file -> file.id }.joinToString(prefix = "{", postfix = "}", separator = ",") { file ->
            "${file.id.json()}:${"$route${URLEncoder.encode(file.id, Charsets.UTF_8)}?v=${file.version}".json()}"
        }

    companion object {
        const val SOUND_ROUTE = "/assets/game-sound/"
        const val MUSIC_ROUTE = "/assets/game-music/"

        /** Loads the catalog and fails fast when a declared file is missing or has an unsupported type. */
        fun load(
            audio: ExternalRuntimeAudio,
            classLoader: ClassLoader,
        ): BitmapAudioCatalog {
            fun files(assets: List<ExternalAudioAsset>): Map<String, BitmapAudioFile> =
                assets.associate { asset ->
                    val bytes =
                        requireNotNull(classLoader.getResourceAsStream(asset.resource)?.use { it.readBytes() }) {
                            "audio ${asset.id} resource is not packaged: ${asset.resource}"
                        }
                    asset.id to BitmapAudioFile(asset.id, asset.resource, audioContentType(asset), bytes.shortHash())
                }
            return BitmapAudioCatalog(files(audio.sounds), files(audio.music))
        }

        private fun audioContentType(asset: ExternalAudioAsset): String =
            when (asset.resource.substringAfterLast('.').lowercase()) {
                "mp3" -> "audio/mpeg"
                "ogg" -> "audio/ogg"
                "wav" -> "audio/wav"
                "m4a" -> "audio/mp4"
                else -> error("audio ${asset.id} has unsupported file type: ${asset.resource}")
            }

        private fun ByteArray.shortHash(): String =
            MessageDigest
                .getInstance("SHA-256")
                .digest(this)
                .take(8)
                .joinToString("") { byte -> "%02x".format(byte) }
    }
}
