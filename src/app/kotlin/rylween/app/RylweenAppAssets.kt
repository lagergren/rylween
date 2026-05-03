package rylween.app

import rylween.dsl.ImageId
import rylween.dsl.StaticScreenId
import rylween.dsl.staticScreen

import java.nio.charset.StandardCharsets

internal object RylweenAppAssets {
    fun readText(path: String): String =
        stream(path).readBytes().toString(StandardCharsets.UTF_8)

    private fun stream(path: String) =
        Thread.currentThread().contextClassLoader.getResourceAsStream(path)
            ?: error("missing app resource $path")
}

internal sealed interface AnsiImageAsset {
    val resource: String

    data class FullScreen(override val resource: String) : AnsiImageAsset
    data class MapWindow(override val resource: String) : AnsiImageAsset
}

internal object AnsiImageAssets {
    private val mapWindowImages = mapOf(
        "blitgfx.inn" to "tiles/01.ans",
        "blitgfx.tavern" to "tiles/02.ans",
        "blitgfx.smith" to "tiles/03.ans",
        "blitgfx.church" to "tiles/04.ans",
        "blitgfx.merchant" to "tiles/05.ans",
        "blitgfx.inside_cave" to "tiles/10.ans",
        "blitgfx.cave" to "tiles/11.ans",
        "blitgfx.death" to "tiles/12.ans",
        "blitgfx.hut" to "tiles/13.ans",
        "blitgfx.ydarg_castle" to "tiles/14.ans",
        "blitgfx.city" to "tiles/15.ans",
        "blitgfx.battle_victory" to "tiles/16.ans",
        "blitgfx.tent" to "tiles/17.ans",
        "blitgfx.no_tent" to "tiles/18.ans",
        "blitgfx.guild" to "tiles/19.ans",
        "blitgfx.encounter.1" to "tiles/40.ans",
        "blitgfx.encounter.2" to "tiles/41.ans",
        "blitgfx.encounter.3" to "tiles/42.ans",
        "blitgfx.encounter.4" to "tiles/43.ans",
        "blitgfx.encounter.5" to "tiles/44.ans",
    )

    fun resolve(image: ImageId): AnsiImageAsset? =
        resolve(image.slug)

    fun resolve(slug: String): AnsiImageAsset? =
        when {
            slug.startsWith("ansi.") -> AnsiImageAsset.FullScreen("ansi/${slug.removePrefix("ansi.")}.ans")
            else -> mapWindowImages[slug]?.let(AnsiImageAsset::MapWindow)
        }
}

internal fun staticScreenArg(value: String): StaticScreenId {
    val slug = value.lowercase().replace('-', '_')
    return when (slug) {
        "vscore", "verbose", "verbose_score" -> staticScreen("verbose_score")
        else -> staticScreen(slug)
    }
}
