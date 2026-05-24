/**
 * Server-side perspective projection for the optional bitmap wilderness view.
 *
 * The browser owns whether the player sees the normal 2D map or the
 * pseudo-3D projection, but the runtime server still owns the full world map.
 * Emitting all four facing projections with each wilderness frame keeps the
 * coordinate math testable and avoids making display facing part of gameplay
 * state or save files.
 */

package rylween.bitmapweb

import rylween.dsl.impl.GridPoint
import kotlin.math.abs
import kotlin.math.roundToInt

internal const val BITMAP_PERSPECTIVE_WIDTH = 1000
internal const val BITMAP_PERSPECTIVE_HEIGHT = 1000
internal const val DEFAULT_BITMAP_PERSPECTIVE_DEPTH = 3
internal const val DEFAULT_BITMAP_PERSPECTIVE_SIDE_RANGE = 2

internal enum class BitmapWildernessFacing {
    North,
    East,
    South,
    West,
}

internal data class BitmapPerspectiveRect(
    val x: Int,
    val y: Int,
    val w: Int,
    val h: Int,
) {
    fun toJson(): String =
        jsonObject(
            "x" to x.toString(),
            "y" to y.toString(),
            "w" to w.toString(),
            "h" to h.toString(),
        )
}

internal data class BitmapPerspectiveCell(
    val worldX: Int,
    val worldY: Int,
    val forward: Int,
    val side: Int,
    val distanceBand: Int,
    val scale: Double,
    val rect: BitmapPerspectiveRect,
    val zOrder: Int,
    val tile: BitmapTile,
) {
    fun toJson(): String =
        jsonObject(
            "worldX" to worldX.toString(),
            "worldY" to worldY.toString(),
            "forward" to forward.toString(),
            "side" to side.toString(),
            "distanceBand" to distanceBand.toString(),
            "scale" to scale.toString(),
            "rect" to rect.toJson(),
            "zOrder" to zOrder.toString(),
            "tile" to tile.toJson(),
        )
}

internal data class BitmapPerspectiveView(
    val facing: BitmapWildernessFacing,
    val cells: List<BitmapPerspectiveCell>,
) {
    fun toJson(): String =
        jsonObject(
            "facing" to facing.name.json(),
            "cells" to cells.joinToString(prefix = "[", postfix = "]") { cell -> cell.toJson() },
        )
}

internal data class BitmapPerspectiveSet(
    val width: Int,
    val height: Int,
    val views: List<BitmapPerspectiveView>,
) {
    fun view(facing: BitmapWildernessFacing): BitmapPerspectiveView = views.first { view -> view.facing == facing }

    fun toJson(): String =
        jsonObject(
            "width" to width.toString(),
            "height" to height.toString(),
            "views" to views.joinToString(prefix = "[", postfix = "]") { view -> view.toJson() },
        )
}

internal fun buildBitmapWildernessPerspectiveSet(
    center: GridPoint,
    depth: Int = DEFAULT_BITMAP_PERSPECTIVE_DEPTH,
    sideRange: Int = DEFAULT_BITMAP_PERSPECTIVE_SIDE_RANGE,
    tileAt: (GridPoint) -> BitmapTile,
): BitmapPerspectiveSet =
    BitmapPerspectiveSet(
        width = BITMAP_PERSPECTIVE_WIDTH,
        height = BITMAP_PERSPECTIVE_HEIGHT,
        views =
            BitmapWildernessFacing.entries.map { facing ->
                buildBitmapWildernessPerspective(center, facing, depth, sideRange, tileAt)
            },
    )

internal fun buildBitmapWildernessPerspective(
    center: GridPoint,
    facing: BitmapWildernessFacing,
    depth: Int = DEFAULT_BITMAP_PERSPECTIVE_DEPTH,
    sideRange: Int = DEFAULT_BITMAP_PERSPECTIVE_SIDE_RANGE,
    tileAt: (GridPoint) -> BitmapTile,
): BitmapPerspectiveView {
    val safeDepth = depth.coerceIn(1, 8)
    val safeSideRange = sideRange.coerceIn(0, 4)
    val cells =
        buildList {
            for (forward in safeDepth downTo 0) {
                val maxSide = safeSideRange
                for (side in -maxSide..maxSide) {
                    val point = center.projectFacing(facing, forward, side)
                    add(
                        BitmapPerspectiveCell(
                            worldX = point.x,
                            worldY = point.y,
                            forward = forward,
                            side = side,
                            distanceBand = forward,
                            scale = perspectiveScale(forward),
                            rect = perspectiveRect(forward, side),
                            zOrder = perspectiveZOrder(safeDepth, safeSideRange, forward, side),
                            tile = tileAt(point),
                        ),
                    )
                }
            }
        }.sortedWith(compareBy<BitmapPerspectiveCell> { cell -> cell.zOrder }.thenBy { cell -> cell.side })
    return BitmapPerspectiveView(facing, cells)
}

private fun GridPoint.projectFacing(
    facing: BitmapWildernessFacing,
    forward: Int,
    side: Int,
): GridPoint =
    when (facing) {
        BitmapWildernessFacing.North -> GridPoint(x + side, y - forward)
        BitmapWildernessFacing.East -> GridPoint(x + forward, y + side)
        BitmapWildernessFacing.South -> GridPoint(x - side, y + forward)
        BitmapWildernessFacing.West -> GridPoint(x - forward, y - side)
    }

private fun perspectiveScale(forward: Int): Double =
    when (forward) {
        0 -> 1.0
        1 -> 0.86
        2 -> 0.72
        3 -> 0.58
        4 -> 0.46
        else -> 0.36
    }

private fun perspectiveRect(
    forward: Int,
    side: Int,
): BitmapPerspectiveRect {
    val scale = perspectiveScale(forward)
    val width = (360 * scale).roundToInt()
    val height = (250 * scale).roundToInt()
    val centerX = BITMAP_PERSPECTIVE_WIDTH / 2 + (side * 218 * scale).roundToInt()
    val baselineY = 790 - forward * 116
    return BitmapPerspectiveRect(
        x = centerX - width / 2,
        y = baselineY - height,
        w = width,
        h = height,
    )
}

private fun perspectiveZOrder(
    depth: Int,
    sideRange: Int,
    forward: Int,
    side: Int,
): Int = (depth - forward) * 100 + (sideRange - abs(side)) * 10
