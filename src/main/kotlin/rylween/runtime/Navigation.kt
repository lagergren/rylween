package rylween.runtime

import rylween.dsl.Facing
import rylween.dsl.GridPoint

enum class WildernessDirection { North, South, East, West }

fun WildernessDirection.delta(): GridPoint =
    when (this) {
        WildernessDirection.North -> GridPoint(0, -1)
        WildernessDirection.South -> GridPoint(0, 1)
        WildernessDirection.East -> GridPoint(1, 0)
        WildernessDirection.West -> GridPoint(-1, 0)
    }

fun WildernessDirection.facing(): Facing =
    when (this) {
        WildernessDirection.North -> Facing.North
        WildernessDirection.South -> Facing.South
        WildernessDirection.East -> Facing.East
        WildernessDirection.West -> Facing.West
    }

operator fun GridPoint.plus(other: GridPoint): GridPoint = GridPoint(x + other.x, y + other.y)
