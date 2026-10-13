/**
 * Wilderness path finding for the autopilot.
 *
 * Paths only use passable terrain, the same rule the runtime applies to every
 * move, so a planned step is never refused.
 */

package rylween.runtime

import rylween.compiled.CompiledContentBundle
import rylween.compiled.CompiledHandleRef
import rylween.dsl.impl.GridPoint
import kotlin.random.Random

/** A cardinal wilderness move and its offset. */
internal enum class AutopilotDirection(
    val dx: Int,
    val dy: Int,
) {
    North(0, -1),
    South(0, 1),
    West(-1, 0),
    East(1, 0),
    ;

    fun from(point: GridPoint): GridPoint = GridPoint(point.x + dx, point.y + dy)
}

internal class AutopilotNavigation(
    private val content: CompiledContentBundle,
) {
    private val worlds = mutableMapOf<CompiledHandleRef, WorldMap>()

    /**
     * The first move of a shortest passable path from [start] to [target] that steps on no square in [avoid] and no
     * terrain in [avoidTerrain] on the way, or `null` when there is none.
     */
    fun stepTowards(
        map: CompiledHandleRef,
        start: GridPoint,
        target: GridPoint,
        avoid: Set<GridPoint> = emptySet(),
        avoidTerrain: Set<String> = emptySet(),
    ): AutopilotDirection? = firstStepToward(map, start, avoid - target, avoidTerrain) { point -> point == target }

    /** The first move towards the nearest tile whose terrain slug is [terrainSlug], keeping off [avoid]. */
    fun stepToTerrain(
        map: CompiledHandleRef,
        start: GridPoint,
        terrainSlug: String,
        avoid: Set<GridPoint> = emptySet(),
    ): AutopilotDirection? =
        firstStepToward(map, start, avoid, emptySet()) { point -> point != start && terrainSlugAt(map, point) == terrainSlug }

    /** A random passable move off [avoid], used to wander while waiting for a random wilderness event. */
    fun wander(
        map: CompiledHandleRef,
        start: GridPoint,
        random: Random,
        stayOn: String? = null,
        avoid: Set<GridPoint> = emptySet(),
        avoidTerrain: Set<String> = emptySet(),
    ): AutopilotDirection? {
        val moves =
            AutopilotDirection.entries.filter { direction ->
                val next = direction.from(start)
                passable(map, next) && next !in avoid && terrainSlugAt(map, next) !in avoidTerrain
            }
        val preferred = stayOn?.let { slug -> moves.filter { direction -> terrainSlugAt(map, direction.from(start)) == slug } }
        return (preferred?.takeIf(List<AutopilotDirection>::isNotEmpty) ?: moves).randomOrNull(random)
    }

    fun terrainSlugAt(
        map: CompiledHandleRef,
        point: GridPoint,
    ): String? = world(map).terrainAt(point)?.let { terrain -> content.slug(terrain.id) }

    /** Where a teleport of [miles] miles towards [direction] from [start] lands: terrain the party could not walk onto stops it. */
    fun teleportLanding(
        map: CompiledHandleRef,
        start: GridPoint,
        direction: AutopilotDirection,
        miles: Int,
    ): GridPoint = world(map).lastPassable(start, GridPoint(direction.dx, direction.dy), miles * 2)

    private fun passable(
        map: CompiledHandleRef,
        point: GridPoint,
    ): Boolean = world(map).terrainAt(point)?.passable == true

    private fun firstStepToward(
        map: CompiledHandleRef,
        start: GridPoint,
        avoid: Set<GridPoint>,
        avoidTerrain: Set<String>,
        isGoal: (GridPoint) -> Boolean,
    ): AutopilotDirection? {
        if (isGoal(start)) return null
        val firstStep = mutableMapOf<GridPoint, AutopilotDirection>()
        val queue = ArrayDeque(listOf(start))
        val seen = mutableSetOf(start)
        while (queue.isNotEmpty()) {
            val point = queue.removeFirst()
            if (isGoal(point)) return firstStep[point]
            AutopilotDirection.entries.forEach { direction ->
                val next = direction.from(point)
                val blocked = next in avoid || (!isGoal(next) && terrainSlugAt(map, next) in avoidTerrain)
                if (next !in seen && passable(map, next) && !blocked) {
                    seen += next
                    firstStep[next] = firstStep[point] ?: direction
                    queue.addLast(next)
                }
            }
        }
        return null
    }

    private fun world(map: CompiledHandleRef): WorldMap = worlds.getOrPut(map) { WorldMap.from(content, map) }
}
