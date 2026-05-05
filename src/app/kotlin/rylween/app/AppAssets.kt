package rylween.app

import rylween.dsl.ImageAssetKind
import rylween.dsl.ImageId
import rylween.dsl.ImagePlacement
import rylween.dsl.StaticScreenId
import rylween.dsl.VisualsModel
import rylween.dsl.staticScreen
import java.nio.charset.StandardCharsets

internal object RylweenAppAssets {
    fun readText(path: String): String = stream(path).readBytes().toString(StandardCharsets.UTF_8)

    private fun stream(path: String) =
        Thread.currentThread().contextClassLoader.getResourceAsStream(path)
            ?: error("missing app resource $path")
}

internal sealed interface AnsiImageAsset {
    val resource: String

    data class FullScreen(
        override val resource: String,
    ) : AnsiImageAsset

    data class MapWindow(
        override val resource: String,
    ) : AnsiImageAsset
}

internal object AnsiImageAssets {
    fun resolve(
        visuals: VisualsModel,
        image: ImageId,
    ): AnsiImageAsset? {
        val asset = visuals.images.firstOrNull { it.id == image && it.kind == ImageAssetKind.Ansi } ?: return null
        return when (asset.placement) {
            ImagePlacement.FullScreen -> AnsiImageAsset.FullScreen(asset.resource)
            ImagePlacement.MapWindow -> AnsiImageAsset.MapWindow(asset.resource)
        }
    }
}

internal fun staticScreenArg(value: String): StaticScreenId =
    when (val slug = value.lowercase().replace('-', '_')) {
        "vscore", "verbose", "verbose_score" -> staticScreen("verbose_score")
        "hall", "hall_of_heroes", "heroes" -> staticScreen("heroes")
        else -> staticScreen(slug)
    }
