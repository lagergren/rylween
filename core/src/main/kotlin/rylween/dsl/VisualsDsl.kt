/**
 * Visual and static-screen authoring builders.
 *
 * These builders keep image assets and runtime-rendered static screens in the
 * reusable DSL package while the concrete resources remain game-authored data.
 * They are split out of `Dsl.kt` so presentation declarations can evolve
 * without growing the generic script/kernel file.
 */
package rylween.dsl

import rylween.dsl.impl.ImageAssetKind
import rylween.dsl.impl.ImageAssetModel
import rylween.dsl.impl.ImagePlacement
import rylween.dsl.impl.ImageTransition
import rylween.dsl.impl.StaticScreenModel
import rylween.dsl.impl.StaticScreenRuntimeReport
import rylween.dsl.impl.VisualsModel

@RylNextDsl
class VisualsBuilder(
    initial: VisualsModel = VisualsModel(),
) {
    private val imageModels = initial.images.toMutableList()
    private val staticScreenModels = initial.staticScreens.toMutableList()

    fun images(body: ImageAssetsBuilder.() -> Unit) {
        imageModels += ImageAssetsBuilder().apply(body).build()
    }

    fun staticScreens(body: StaticScreensBuilder.() -> Unit) {
        staticScreenModels += StaticScreensBuilder().apply(body).build()
    }

    fun build(): VisualsModel =
        VisualsModel(
            images = imageModels.toList(),
            staticScreens = staticScreenModels.toList(),
        )
}

@RylNextDsl
class ImageAssetsBuilder {
    private val images = mutableListOf<ImageAssetModel>()

    fun ansiFullScreen(
        id: ImageId,
        resource: String,
    ) {
        ansi(id, resource = resource, placement = ImagePlacement.FullScreen)
    }

    fun ansiMapWindow(
        id: ImageId,
        resource: String,
    ) {
        ansi(id, resource = resource, placement = ImagePlacement.MapWindow)
    }

    fun ansi(
        id: ImageId,
        resource: String,
        placement: ImagePlacement,
        transition: ImageTransition = ImageTransition.None,
    ) {
        images +=
            ImageAssetModel(
                id = id,
                kind = ImageAssetKind.Ansi,
                resource = resource,
                placement = placement,
                transition = transition,
            )
    }

    fun bitmapMapWindow(
        id: ImageId,
        resource: String,
        transition: ImageTransition = ImageTransition.Fade,
    ) {
        bitmap(id, resource = resource, placement = ImagePlacement.MapWindow, transition = transition)
    }

    /**
     * Declares several bitmap map-window assets from one classpath directory.
     *
     * This keeps game content explicit about each image id while avoiding long,
     * repeated resource prefixes for grouped assets such as party portraits.
     * The helper does not scan the filesystem; every asset remains a normal
     * authored image declaration and is validated/packaged like [bitmapMapWindow].
     */
    fun bitmapMapWindowGroup(
        resourceDirectory: String,
        transition: ImageTransition = ImageTransition.Fade,
        body: BitmapMapWindowGroupBuilder.() -> Unit,
    ) {
        BitmapMapWindowGroupBuilder(this, resourceDirectory, transition).apply(body)
    }

    fun bitmap(
        id: ImageId,
        resource: String,
        placement: ImagePlacement,
        transition: ImageTransition = ImageTransition.None,
    ) {
        images +=
            ImageAssetModel(
                id = id,
                kind = ImageAssetKind.Bitmap,
                resource = resource,
                placement = placement,
                transition = transition,
            )
    }

    fun build(): List<ImageAssetModel> = images.toList()
}

@RylNextDsl
class BitmapMapWindowGroupBuilder internal constructor(
    private val images: ImageAssetsBuilder,
    resourceDirectory: String,
    private val defaultTransition: ImageTransition,
) {
    private val directory = resourceDirectory.trimEnd('/')

    /**
     * Adds one bitmap image from the group's resource directory.
     *
     * The caller supplies only the file name relative to the group directory,
     * while the compiled model still receives the full classpath resource path.
     */
    fun image(
        id: ImageId,
        fileName: String,
        transition: ImageTransition = defaultTransition,
    ) {
        images.bitmapMapWindow(id, "$directory/${fileName.trimStart('/')}", transition)
    }
}

@RylNextDsl
class StaticScreensBuilder {
    private val screens = mutableListOf<StaticScreenModel>()

    fun screen(
        id: StaticScreenId,
        body: StaticScreenBuilder.() -> Unit,
    ) {
        screens += StaticScreenBuilder(id).apply(body).build()
    }

    fun image(
        id: StaticScreenId,
        staticImage: ImageId,
    ) {
        screen(id) { image(staticImage) }
    }

    fun resource(
        id: StaticScreenId,
        path: String,
    ) {
        screen(id) { resource(path) }
    }

    fun commandHelp(id: StaticScreenId) {
        screen(id) { commandHelp() }
    }

    fun build(): List<StaticScreenModel> = screens.toList()
}

@RylNextDsl
class StaticScreenBuilder(
    private val id: StaticScreenId,
) {
    private var imageModel: ImageId? = null
    private var resourcePath: String? = null
    private var usesCommandHelp = false
    private var runtimeReportValue: StaticScreenRuntimeReport? = null

    fun image(image: ImageId) {
        imageModel = image
    }

    fun resource(path: String) {
        resourcePath = path
    }

    fun commandHelp() {
        usesCommandHelp = true
    }

    fun scoreReport() {
        runtimeReportValue = StaticScreenRuntimeReport.Score
    }

    fun verboseScoreReport() {
        runtimeReportValue = StaticScreenRuntimeReport.VerboseScore
    }

    fun hallOfHeroesReport() {
        runtimeReportValue = StaticScreenRuntimeReport.HallOfHeroes
    }

    fun build(): StaticScreenModel =
        StaticScreenModel(
            id = id,
            image = imageModel,
            resource = resourcePath,
            commandHelp = usesCommandHelp,
            runtimeReport = runtimeReportValue,
        )
}
