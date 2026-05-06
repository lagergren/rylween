/**
 * Shared grid-navigation primitives for wilderness, dungeon, and castle flows.
 *
 * Session state machines use [WildernessDirection] for semantic movement, while
 * frontends and map renderers use [delta], [facing], and [GridPoint.plus] to
 * translate that movement into map coordinates and orientation.
 */
package rylween.runtime

import rylween.dsl.Facing
import rylween.dsl.GridPoint

/** Cardinal movement direction accepted by map-like runtime sessions. */
enum class WildernessDirection { North, South, East, West }

/** Grid delta for moving one tile in this direction. */
fun WildernessDirection.delta(): GridPoint =
    when (this) {
        WildernessDirection.North -> GridPoint(0, -1)
        WildernessDirection.South -> GridPoint(0, 1)
        WildernessDirection.East -> GridPoint(1, 0)
        WildernessDirection.West -> GridPoint(-1, 0)
    }

/** Facing value corresponding to this movement direction. */
fun WildernessDirection.facing(): Facing =
    when (this) {
        WildernessDirection.North -> Facing.North
        WildernessDirection.South -> Facing.South
        WildernessDirection.East -> Facing.East
        WildernessDirection.West -> Facing.West
    }

/** Adds two grid points as coordinate vectors. */
operator fun GridPoint.plus(other: GridPoint): GridPoint = GridPoint(x + other.x, y + other.y)
