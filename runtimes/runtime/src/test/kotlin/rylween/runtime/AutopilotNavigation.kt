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

    /** The first move of a shortest passable path from [start] to [target], or `null` when there is none. */
    fun stepTowards(
        map: CompiledHandleRef,
        start: GridPoint,
        target: GridPoint,
    ): AutopilotDirection? = firstStepToward(map, start) { point -> point == target }

    /** The first move towards the nearest tile whose terrain slug is [terrainSlug]. */
    fun stepToTerrain(
        map: CompiledHandleRef,
        start: GridPoint,
        terrainSlug: String,
    ): AutopilotDirection? = firstStepToward(map, start) { point -> point != start && terrainSlugAt(map, point) == terrainSlug }

    /** A random passable move, used to wander while waiting for a random wilderness event. */
    fun wander(
        map: CompiledHandleRef,
        start: GridPoint,
        random: Random,
        stayOn: String? = null,
    ): AutopilotDirection? {
        val moves = AutopilotDirection.entries.filter { direction -> passable(map, direction.from(start)) }
        val preferred = stayOn?.let { slug -> moves.filter { direction -> terrainSlugAt(map, direction.from(start)) == slug } }
        return (preferred?.takeIf(List<AutopilotDirection>::isNotEmpty) ?: moves).randomOrNull(random)
    }

    fun terrainSlugAt(
        map: CompiledHandleRef,
        point: GridPoint,
    ): String? = world(map).terrainAt(point)?.let { terrain -> content.slug(terrain.id) }

    private fun passable(
        map: CompiledHandleRef,
        point: GridPoint,
    ): Boolean = world(map).terrainAt(point)?.passable == true

    private fun firstStepToward(
        map: CompiledHandleRef,
        start: GridPoint,
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
                if (next !in seen && passable(map, next)) {
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
