package rylween.bitmapweb

import rylween.compiled.CompiledContentBundle
import rylween.compiled.CompiledTerrain
import rylween.compiled.toCompiledGameBundle
import rylween.dsl.impl.GridPoint
import rylween.game.RylweenCompiledGame
import rylween.runtime.WorldMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WildernessPerspectiveTest {
    @Test
    fun projectionMapsForwardAndSideForEveryFacing() {
        val center = GridPoint(10, 10)

        assertProjectedPoint(BitmapWildernessFacing.North, center, forward = 2, side = -1, expected = GridPoint(9, 8))
        assertProjectedPoint(BitmapWildernessFacing.North, center, forward = 2, side = 1, expected = GridPoint(11, 8))
        assertProjectedPoint(BitmapWildernessFacing.East, center, forward = 2, side = 1, expected = GridPoint(12, 11))
        assertProjectedPoint(BitmapWildernessFacing.South, center, forward = 2, side = -1, expected = GridPoint(11, 12))
        assertProjectedPoint(BitmapWildernessFacing.West, center, forward = 2, side = 1, expected = GridPoint(8, 9))
    }

    @Test
    fun projectionOrdersFarCellsBeforeNearCells() {
        val view = buildBitmapWildernessPerspective(GridPoint(10, 10), BitmapWildernessFacing.North, depth = 3, sideRange = 2, ::testTile)

        assertEquals(view.cells.map { cell -> cell.zOrder }.sorted(), view.cells.map { cell -> cell.zOrder })
        assertEquals(3, view.cells.first().forward)
        assertEquals(0, view.cells.last().forward)
        assertEquals(0, view.cells.last().side)
        assertTrue(view.cells.first().scale < view.cells.last().scale)
    }

    @Test
    fun projectionKeepsOutOfBoundsCellsAsVoidTerrain() {
        val view =
            buildBitmapWildernessPerspective(GridPoint(0, 1), BitmapWildernessFacing.West, depth = 2, sideRange = 1) { point ->
                if (point.x in 0..2 && point.y in 1..3) testTile(point) else voidTile(point)
            }

        val outOfBounds = view.cells.first { cell -> cell.forward == 2 && cell.side == 0 }
        assertEquals(GridPoint(-2, 1), GridPoint(outOfBounds.worldX, outOfBounds.worldY))
        assertEquals("void", outOfBounds.tile.slug)
        assertEquals(false, outOfBounds.tile.passable)
    }

    @Test
    fun projectionCoversHandCheckedThordTerrainPatchFromRealMap() {
        val bundle = RylweenCompiledGame.nextRylween.toCompiledGameBundle()
        val content = bundle.content
        val world = WorldMap.from(content, content.worldMaps.single().id)
        val center = GridPoint(12, 11)

        val view =
            buildBitmapWildernessPerspective(center, BitmapWildernessFacing.North, depth = 2, sideRange = 1) { point ->
                realTile(world, content, point)
            }

        assertEquals("town", view.cellAt(forward = 0, side = 0).tile.slug)
        assertEquals("plain", view.cellAt(forward = 1, side = 0).tile.slug)
        assertEquals("plain", view.cellAt(forward = 2, side = -1).tile.slug)
        assertEquals("foothills", view.cellAt(forward = 2, side = 1).tile.slug)
    }

    private fun assertProjectedPoint(
        facing: BitmapWildernessFacing,
        center: GridPoint,
        forward: Int,
        side: Int,
        expected: GridPoint,
    ) {
        val view = buildBitmapWildernessPerspective(center, facing, depth = forward, sideRange = 1, ::testTile)
        val cell = view.cellAt(forward, side)
        assertEquals(expected, GridPoint(cell.worldX, cell.worldY))
    }

    private fun BitmapPerspectiveView.cellAt(
        forward: Int,
        side: Int,
    ): BitmapPerspectiveCell = cells.single { cell -> cell.forward == forward && cell.side == side }

    private fun testTile(point: GridPoint): BitmapTile =
        BitmapTile(
            slug = "plain",
            code = ".",
            x = point.x,
            y = point.y,
            variant = 0,
            passable = true,
            settlement = null,
            center = false,
        )

    private fun voidTile(point: GridPoint): BitmapTile =
        BitmapTile(
            slug = "void",
            code = "",
            x = point.x,
            y = point.y,
            variant = 0,
            passable = false,
            settlement = null,
            center = false,
        )

    private fun realTile(
        world: WorldMap,
        content: CompiledContentBundle,
        point: GridPoint,
    ): BitmapTile {
        val terrain = world.terrainAt(point)
        return BitmapTile(
            slug = terrain?.slug(content) ?: "void",
            code = terrain?.mapCode?.toString().orEmpty(),
            x = point.x,
            y = point.y,
            variant = 0,
            passable = terrain?.passable ?: false,
            settlement = terrain?.settlement?.name?.lowercase(),
            center = false,
        )
    }

    private fun CompiledTerrain.slug(content: CompiledContentBundle): String = content.strings.value(content.handles.handle(id).slugString)
}
