/**
 * Small game-wide content catalog builders.
 *
 * These builders author reusable shell data around the game: metadata, limits,
 * terminal terrain rendering, screen layout, and terrain semantics. They remain
 * in the public DSL package, but live outside `Dsl.kt` because they are plain
 * content declarations rather than script-kernel behavior.
 */
package rylween.dsl

import rylween.dsl.impl.GameLimitsModel
import rylween.dsl.impl.GameMetadataModel
import rylween.dsl.impl.GameUniverseModel
import rylween.dsl.impl.LayoutModel
import rylween.dsl.impl.ScreenRegionModel
import rylween.dsl.impl.TerminalPresentationModel
import rylween.dsl.impl.TerminalTerrainStyleModel
import rylween.dsl.impl.TerrainCatalogModel
import rylween.dsl.impl.TerrainSemanticsModel
import rylween.dsl.impl.TerrainSettlement

@RylNextDsl
class TerminalPresentationBuilder {
    private val terrainStyles = mutableListOf<TerminalTerrainStyleModel>()

    fun terrain(
        id: TerrainId,
        glyph: String,
        color: PaletteColorId,
    ) {
        terrainStyles += TerminalTerrainStyleModel(id, glyph, color)
    }

    fun build(): TerminalPresentationModel = TerminalPresentationModel(terrain = terrainStyles.toList())
}

@RylNextDsl
class LayoutBuilder(
    initial: LayoutModel = LayoutModel(),
) {
    private val screenRegions = initial.regions.toMutableList()

    fun region(
        id: ScreenRegionId,
        x: Int,
        y: Int,
        width: Int? = null,
        height: Int? = null,
    ) {
        screenRegions += ScreenRegionModel(id, x, y, width, height)
    }

    fun build(): LayoutModel = LayoutModel(regions = screenRegions.toList())
}

@RylNextDsl
class GameMetadataBuilder(
    initial: GameMetadataModel = GameMetadataModel(),
) {
    private var universeModel = initial.universe

    fun universe(
        name: String,
        version: String,
    ) {
        universeModel = GameUniverseModel(name = name, version = version)
    }

    internal fun build(): GameMetadataModel = GameMetadataModel(universe = universeModel)
}

@RylNextDsl
class GameLimitsBuilder(
    initial: GameLimitsModel = GameLimitsModel(),
) {
    var maxWorldMaps: Int? = initial.maxWorldMaps

    internal fun build(): GameLimitsModel =
        GameLimitsModel(
            maxWorldMaps = maxWorldMaps,
        )
}

@RylNextDsl
class TerrainCatalogBuilder(
    initial: TerrainCatalogModel = TerrainCatalogModel(),
) {
    private val terrainModels = initial.terrain.toMutableList()

    fun terrain(
        id: TerrainId,
        mapCode: Char? = id.sourceMapCode,
        body: TerrainSemanticsBuilder.() -> Unit = {},
    ) {
        val tile = requireNotNull(mapCode) { "terrain ${id.slug} requires a map code" }
        terrainModels += TerrainSemanticsBuilder(id, tile).apply(body).build()
    }

    fun build(): TerrainCatalogModel = TerrainCatalogModel(terrain = terrainModels.toList())
}

@RylNextDsl
class TerrainSemanticsBuilder(
    private val id: TerrainId,
    private val mapCode: Char,
) {
    var passable: Boolean = true
    private var movementCostModel: Int = 1
    private var allowsNpcPlacementModel: Boolean? = null
    private var settlementModel: TerrainSettlement? = null

    fun blocksMovement() {
        passable = false
        movementCostModel = 0
        allowsNpcPlacementModel = false
    }

    fun settlement(kind: TerrainSettlement) {
        settlementModel = kind
        allowsNpcPlacementModel = false
    }

    fun build(): TerrainSemanticsModel {
        val placement = allowsNpcPlacementModel ?: (passable && settlementModel == null)
        return TerrainSemanticsModel(
            id = id,
            mapCode = mapCode,
            passable = passable,
            movementCost = movementCostModel,
            allowsNpcPlacement = placement,
            settlement = settlementModel,
            tags = emptySet(),
        )
    }
}
