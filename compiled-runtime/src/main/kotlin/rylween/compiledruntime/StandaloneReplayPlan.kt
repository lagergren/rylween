/**
 * Named replay plans for the standalone compiled-runtime acceptance checks.
 *
 * The Gradle smoke tasks should select these plans by name instead of embedding
 * long opaque command strings in build logic. Each plan is assembled from small
 * components with explicit route and content-purpose names, using only compiled
 * bundle tables plus stable `runtimeSystems` data. That makes the current
 * acceptance route reproducible and gives later full-game playthrough work a
 * natural place to insert conversation, shop, mission, and checkpoint pieces.
 */

package rylween.compiledruntime

import rylween.compiled.CompiledContentBundle
import rylween.compiled.CompiledGameBundle
import rylween.compiled.CompiledHandleKind
import rylween.compiled.CompiledStateImage
import rylween.compiled.CompiledTerrain
import rylween.compiled.CompiledWorldPosition
import rylween.compiled.ExternalRuntimeSystems
import rylween.dsl.GridPoint

/** One reusable semantic piece of a compiled-runtime replay. */
internal data class StandaloneReplayComponent(
    val name: String,
    val commands: String,
    val checkpoints: List<String> = emptyList(),
)

/** A complete replay command stream built by fusing named [components]. */
internal data class StandaloneReplayPlan(
    val name: String,
    val stateSlug: String,
    val components: List<StandaloneReplayComponent>,
) {
    val script: String = components.joinToString(separator = "") { component -> component.commands }

    fun summaryLines(): List<String> =
        buildList {
            add("compiled replay: $name state=$stateSlug components=${components.size} commands=${script.length}")
            components.forEachIndexed { index, component ->
                val checkpointText =
                    component.checkpoints
                        .takeIf(List<String>::isNotEmpty)
                        ?.joinToString(prefix = " checkpoints=", separator = ",")
                        .orEmpty()
                add("compiled replay component ${index + 1}: ${component.name} commands=${component.commands.length}$checkpointText")
            }
        }
}

/** Factory for replay plans supported by the standalone compiled runtime. */
internal object StandaloneCompiledReplayPlans {
    private const val ACCEPTANCE_POWER_ROUTE = "acceptance-power-route"
    private const val ACCEPTANCE_STATE = "acceptance-replay"

    fun build(
        name: String,
        bundle: CompiledGameBundle,
        systems: ExternalRuntimeSystems,
        stateOverride: String?,
    ): StandaloneReplayPlan =
        when (val normalized = name.lowercase().replace('_', '-')) {
            ACCEPTANCE_POWER_ROUTE -> acceptancePowerRoute(bundle, systems, stateOverride ?: ACCEPTANCE_STATE)
            else -> error("unknown compiled replay plan $normalized")
        }

    private fun acceptancePowerRoute(
        bundle: CompiledGameBundle,
        systems: ExternalRuntimeSystems,
        stateSlug: String,
    ): StandaloneReplayPlan {
        val content = bundle.content
        val state = bundle.stateBySlug(stateSlug)
        val world = ReplayWorldMap.from(state, content = content)
        var point = state.startPoint()

        fun moveTo(
            name: String,
            target: GridPoint,
        ): StandaloneReplayComponent {
            val route = world.routeTo(point, target)
            point = route.end
            return StandaloneReplayComponent(name, route.commands)
        }

        fun moveToTerrain(
            name: String,
            terrainSlug: String,
        ): StandaloneReplayComponent {
            val route = world.routeToTerrain(point, terrainSlug)
            point = route.end
            return StandaloneReplayComponent(name, route.commands)
        }

        fun location(slug: String): GridPoint = state.locationPoint(content, slug)

        fun teleportAfterThun(): StandaloneReplayComponent {
            point = GridPoint(point.x - 18, point.y)
            return StandaloneReplayComponent("wilderness.teleport_after_thun", "Z16AA49")
        }

        return StandaloneReplayPlan(
            name = ACCEPTANCE_POWER_ROUTE,
            stateSlug = stateSlug,
            components =
                buildList {
                    add(moveTo("travel.to_ogre_cave", location("beast_cave")))
                    add(StandaloneReplayComponent("mission.ogre_cave", ogreCaveRoute(), listOf("Foot of Jazzy prerequisite")))
                    add(moveToTerrain("travel.to_foothills", "foothills"))
                    add(StandaloneReplayComponent("mission.jazzy_foot", jazzyRoute(), listOf("Foot of Jazzy")))
                    add(moveTo("travel.to_fire_shield_cave", location("hermit_dungeon")))
                    add(StandaloneReplayComponent("mission.fire_shield", fireShieldDungeonRoute(), listOf("FIRE SHIELD")))
                    add(moveTo("travel.to_hobgoblin_stronghold", location("hobgoblin_cave")))
                    add(StandaloneReplayComponent("mission.hobgoblin_stronghold", hobgoblinStrongholdRoute(), listOf("Silver Key")))
                    add(moveTo("travel.to_thun_temple", location("thun_temple")))
                    add(StandaloneReplayComponent("mission.thun_temple", thunTempleRoute(), listOf("Bracelets of Thun")))
                    add(teleportAfterThun())
                    add(moveTo("travel.to_master_crown", location("master_crown_dungeon")))
                    add(StandaloneReplayComponent("mission.master_crown", masterCrownRoute(), listOf("Master Crown")))
                    add(moveTo("travel.to_castle", systems.castleRows.single().entry))
                    add(StandaloneReplayComponent("castle.victory", castleVictoryRoute(), listOf("Ydarg banished")))
                    add(StandaloneReplayComponent("save.winning_state", "S", listOf("Hall of Heroes save")))
                },
        )
    }

    private fun ogreCaveRoute(): String =
        "E" +
            waitForInput() +
            battle(attacks = 4) +
            waitForInput() +
            battle(attacks = 1) +
            waitForInput(4)

    private fun jazzyRoute(): String =
        waitForInput(2) +
            battle(attacks = 7) +
            waitForInput(2) +
            battle(attacks = 1) +
            waitForInput()

    private fun fireShieldDungeonRoute(): String =
        "E" +
            dungeonRoute(
                levels = 20,
                descendEvents =
                    mapOf(
                        19 to waitForInput() + battle(attacks = 1),
                        20 to waitForInput(),
                    ),
            )

    private fun thunTempleRoute(): String =
        waitForInput(2) +
            "Y" +
            waitForInput() +
            dungeonRoute(
                levels = 18,
                descendEvents =
                    mapOf(
                        4 to waitForInput() + "water " + waitForInput(),
                        8 to waitForInput() + "night " + waitForInput(),
                        12 to waitForInput() + "\"a well\" " + waitForInput(),
                        18 to waitForInput(),
                    ),
            )

    private fun masterCrownRoute(): String =
        "E" +
            dungeonRoute(
                levels = 10,
                descendEvents = mapOf(10 to waitForInput(2) + battle(attacks = 1)),
            )

    private fun hobgoblinStrongholdRoute(): String =
        "E" +
            dungeonRoute(
                levels = 22,
                descendEvents =
                    mapOf(
                        3 to hobgoblinPatrolRoute(),
                        6 to hobgoblinPatrolRoute(),
                        9 to hobgoblinPatrolRoute(),
                        14 to hobgoblinPatrolRoute(),
                        17 to hobgoblinPatrolRoute(),
                        19 to hobgoblinPatrolRoute(),
                        20 to waitForInput() + battle(attacks = 5),
                        21 to waitForInput() + battle(attacks = 5),
                        22 to hobgoblinChiefRoute(),
                    ),
                ascendRecurringEvents =
                    mapOf(
                        19 to hobgoblinPatrolRoute(),
                        17 to hobgoblinPatrolRoute(),
                        14 to hobgoblinPatrolRoute(),
                        9 to hobgoblinPatrolRoute(),
                        6 to hobgoblinPatrolRoute(),
                        3 to hobgoblinPatrolRoute(),
                    ),
            )

    private fun hobgoblinPatrolRoute(): String = waitForInput() + battle(attacks = 2) + waitForInput()

    private fun hobgoblinChiefRoute(): String =
        waitForInput() +
            "\"RISH EKKIN\" " +
            waitForInput() +
            battle(attacks = 1) +
            waitForInput()

    private fun castleVictoryRoute(): String =
        buildString {
            append('E')
            append(battle())
            append(castleFloor('1'))
            append(castleFloor('2'))
            append(castleFloor('3'))
            append(castleFloor('4'))
            repeat(5) { append(battle()) }
        }

    private fun castleFloor(itemDigit: Char): String =
        buildString {
            repeat(4) { append(castleStep('6')) }
            repeat(3) { append(castleStep('2')) }
            append("2G")
            append(itemDigit)
        }

    private fun castleStep(command: Char): String = command.toString() + battle()

    private fun dungeonRoute(
        levels: Int,
        descendEvents: Map<Int, String>,
        ascendRecurringEvents: Map<Int, String> = emptyMap(),
    ): String =
        buildString {
            (2..levels).forEach { level ->
                append('D')
                append(descendEvents[level].orEmpty())
            }
            (levels - 1 downTo 1).forEach { level ->
                append('A')
                append(ascendRecurringEvents[level].orEmpty())
            }
            append('A')
        }

    private fun waitForInput(count: Int = 1): String = "A".repeat(count)

    private fun battle(attacks: Int = 5): String = "F" + "A".repeat(attacks)
}

private data class ReplayRoute(
    val commands: String,
    val end: GridPoint,
)

private class ReplayWorldMap(
    private val content: CompiledContentBundle,
    private val rows: List<String>,
    private val terrainByCode: Map<Char, CompiledTerrain>,
) {
    fun routeTo(
        start: GridPoint,
        target: GridPoint,
    ): ReplayRoute = routeFrom(start) { point -> point == target }

    fun routeToTerrain(
        start: GridPoint,
        terrainSlug: String,
    ): ReplayRoute = routeFrom(start) { point -> point != start && terrainSlug(point) == terrainSlug }

    private fun routeFrom(
        start: GridPoint,
        target: (GridPoint) -> Boolean,
    ): ReplayRoute {
        val queue = ArrayDeque<GridPoint>()
        val previous = linkedMapOf<GridPoint, Pair<GridPoint, Char>?>()
        queue.add(start)
        previous[start] = null
        while (queue.isNotEmpty()) {
            val point = queue.removeFirst()
            if (target(point)) return ReplayRoute(commandsTo(point, previous), point)
            point
                .neighbors()
                .filter { next -> next.point.isPassable() && next.point !in previous }
                .forEach { next ->
                    previous[next.point] = point to next.command
                    queue.add(next.point)
                }
        }
        error("no compiled-runtime replay route from $start")
    }

    private fun commandsTo(
        end: GridPoint,
        previous: Map<GridPoint, Pair<GridPoint, Char>?>,
    ): String {
        val commands = ArrayDeque<Char>()
        var cursor = end
        while (true) {
            val step = previous[cursor] ?: break
            commands.addFirst(step.second)
            cursor = step.first
        }
        return commands.joinToString(separator = "")
    }

    private fun GridPoint.neighbors(): List<ReplayStep> =
        listOf(
            ReplayStep('8', GridPoint(x, y - 1)),
            ReplayStep('2', GridPoint(x, y + 1)),
            ReplayStep('4', GridPoint(x - 1, y)),
            ReplayStep('6', GridPoint(x + 1, y)),
        )

    private fun GridPoint.isPassable(): Boolean = terrainAt(this)?.passable == true

    private fun terrainSlug(point: GridPoint): String? = terrainAt(point)?.let { terrain -> content.slug(terrain.id) }

    private fun terrainAt(point: GridPoint): CompiledTerrain? = rows.getOrNull(point.y)?.getOrNull(point.x)?.let(terrainByCode::get)

    companion object {
        fun from(
            state: CompiledStateImage,
            content: CompiledContentBundle,
        ): ReplayWorldMap {
            val map =
                content.worldMaps.firstOrNull { worldMap -> worldMap.id == state.realm.currentMap }
                    ?: error("replay state map ${content.slug(state.realm.currentMap)} is missing")
            val layer = map.layers.firstOrNull() ?: error("replay map ${content.slug(map.id)} has no layers")
            return ReplayWorldMap(
                content = content,
                rows = layer.rowStrings.map(content.strings::value),
                terrainByCode = content.terrain.associateBy(CompiledTerrain::mapCode),
            )
        }
    }
}

private data class ReplayStep(
    val command: Char,
    val point: GridPoint,
)

private fun CompiledGameBundle.stateBySlug(slug: String): CompiledStateImage {
    val stateRef = content.ref(CompiledHandleKind.State, slug)
    return states.firstOrNull { state -> state.state == stateRef } ?: error("compiled replay state $slug not found")
}

private fun CompiledStateImage.startPoint(): GridPoint =
    when (val position = activeParty().position) {
        is CompiledWorldPosition.Wilderness -> position.point
        is CompiledWorldPosition.Town -> error("compiled replay state must start in wilderness")
    }

private fun CompiledStateImage.locationPoint(
    content: CompiledContentBundle,
    slug: String,
): GridPoint {
    val locationRef = locations.firstOrNull { location -> content.slug(location.location) == slug }
    return locationRef?.point ?: error("compiled replay location $slug not present in state")
}

private fun CompiledStateImage.activeParty() =
    parties.firstOrNull { party -> party.id == realm.activeParty }
        ?: error("compiled replay state has no active party ${realm.activeParty}")
