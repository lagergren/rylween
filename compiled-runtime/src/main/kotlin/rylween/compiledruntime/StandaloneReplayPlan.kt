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
                        ?.let { checkpoints -> " checkpoints=${checkpoints.size}" }
                        .orEmpty()
                add("compiled replay component ${index + 1}: ${component.name} commands=${component.commands.length}$checkpointText")
            }
        }
}

/** Factory for replay plans supported by the standalone compiled runtime. */
internal object StandaloneCompiledReplayPlans {
    private const val ACCEPTANCE_POWER_ROUTE = "acceptance-power-route"
    private const val ACCEPTANCE_STATE = "acceptance-replay"
    private const val TOWN_SOCIAL_SMOKE = "town-social-smoke"
    private const val TOWN_GARTH_REWARD_SMOKE = "town-garth-reward-smoke"
    private const val TOWN_MISSION_INTRO_ROUTE = "town-mission-intro-route"
    private const val TOWN_SILVER_KEY_CHAIN_ROUTE = "town-silver-key-chain-route"
    private const val SILVER_KEY_DISCOVERY_ROUTE = "silver-key-discovery-route"
    private const val HOBGOBLIN_CARAVAN_ROUTE = "hobgoblin-caravan-route"
    private const val MASTER_CROWN_DISCOVERY_ROUTE = "master-crown-discovery-route"
    private const val FRESH_STATE = "fresh-replay"
    private const val TOWN_MISSION_STATE = "town-mission-replay"
    private const val TOWN_SILVER_KEY_STATE = "town-silver-key-replay"
    private const val HOBGOBLIN_CARAVAN_STATE = "hobgoblin-caravan-replay"
    private const val MASTER_CROWN_CHAIN_STATE = "master-crown-chain-replay"

    fun build(
        name: String,
        bundle: CompiledGameBundle,
        systems: ExternalRuntimeSystems,
        stateOverride: String?,
    ): StandaloneReplayPlan =
        when (val normalized = name.lowercase().replace('_', '-')) {
            ACCEPTANCE_POWER_ROUTE -> acceptancePowerRoute(bundle, systems, stateOverride ?: ACCEPTANCE_STATE)
            TOWN_SOCIAL_SMOKE -> townSocialSmoke(bundle, stateOverride ?: FRESH_STATE)
            TOWN_GARTH_REWARD_SMOKE -> townGarthRewardSmoke(bundle, stateOverride ?: FRESH_STATE)
            TOWN_MISSION_INTRO_ROUTE -> townMissionIntroRoute(bundle, stateOverride ?: TOWN_MISSION_STATE)
            TOWN_SILVER_KEY_CHAIN_ROUTE -> townSilverKeyChainRoute(bundle, stateOverride ?: TOWN_SILVER_KEY_STATE)
            SILVER_KEY_DISCOVERY_ROUTE -> silverKeyDiscoveryRoute(bundle, stateOverride ?: TOWN_MISSION_STATE)
            HOBGOBLIN_CARAVAN_ROUTE -> hobgoblinCaravanRoute(bundle, stateOverride ?: HOBGOBLIN_CARAVAN_STATE)
            MASTER_CROWN_DISCOVERY_ROUTE -> masterCrownDiscoveryRoute(bundle, stateOverride ?: MASTER_CROWN_CHAIN_STATE)
            else -> error("unknown compiled replay plan $normalized")
        }

    private fun masterCrownDiscoveryRoute(
        bundle: CompiledGameBundle,
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

        fun moveToTown(slug: String): StandaloneReplayComponent = moveTo("travel.to_$slug", content.townPoint(slug))

        fun moveToTerrain(
            name: String,
            terrainSlug: String,
        ): StandaloneReplayComponent {
            val route = world.routeToTerrain(point, terrainSlug)
            point = route.end
            return StandaloneReplayComponent(name, route.commands)
        }

        fun location(slug: String): GridPoint = state.locationPoint(content, slug)

        return StandaloneReplayPlan(
            name = MASTER_CROWN_DISCOVERY_ROUTE,
            stateSlug = stateSlug,
            components =
                listOf(
                    moveToTown("wilor"),
                    StandaloneReplayComponent(
                        name = "town.wilor_master_crown_intro",
                        commands = "E3N" + speakNpc(2) + speakNpc(5) + "L",
                        checkpoints = listOf("output:You are better fit", "state:master_crown.task1_assigned"),
                    ),
                    moveTo("travel.to_kobold_village", location("kobold_village")),
                    StandaloneReplayComponent(
                        name = "mission.kobold_village",
                        commands = koboldVillageRoute(),
                        checkpoints = listOf("output:ugly pendant", "state:master_crown.task1_done"),
                    ),
                    moveToTown("wilor"),
                    StandaloneReplayComponent(
                        name = "town.wilor_master_crown_second_task",
                        commands = "E3N" + speakNpc(5) + "L",
                        checkpoints = listOf("output:second quest", "state:master_crown.task2_assigned"),
                    ),
                    moveToTerrain("travel.to_skullweed_swamp", "swamp"),
                    StandaloneReplayComponent(
                        name = "mission.skullweed",
                        commands = waitForInput(),
                        checkpoints = listOf("output:skullweed plant", "state:master_crown.task2_done"),
                    ),
                    moveToTown("wilor"),
                    StandaloneReplayComponent(
                        name = "town.wilor_master_crown_third_task",
                        commands = "E3N" + speakNpc(5) + "L",
                        checkpoints = listOf("output:DWARF STONE", "state:master_crown.task3_assigned"),
                    ),
                    moveTo("travel.to_borin_camp", location("borin_camp")),
                    StandaloneReplayComponent(
                        name = "mission.borin_meet",
                        commands = waitForInput(6),
                        checkpoints = listOf("output:Nolrim's crypt", "state:master_crown.borin_met"),
                    ),
                    moveTo("travel.to_nolrim_crypt", location("nolrim_crypt")),
                    StandaloneReplayComponent(
                        name = "mission.nolrim_crypt",
                        commands = reebarTombRoute(),
                        checkpoints = listOf("output:dagger you seek", "state:master_crown.dagger_taken"),
                    ),
                    moveTo("travel.to_borin_gift", location("borin_camp")),
                    StandaloneReplayComponent(
                        name = "mission.borin_gift",
                        commands = waitForInput(2),
                        checkpoints = listOf("output:purple dwarf stone", "state:master_crown.borin_paid"),
                    ),
                    moveToTown("wilor"),
                    StandaloneReplayComponent(
                        name = "town.wilor_master_crown_location",
                        commands = "E3N" + speakNpc(5) + "LX",
                        checkpoints = listOf("output:Master Crown is hidden", "state:master_crown.crown_told"),
                    ),
                ),
        )
    }

    private fun silverKeyDiscoveryRoute(
        bundle: CompiledGameBundle,
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

        fun moveToTown(slug: String): StandaloneReplayComponent = moveTo("travel.to_$slug", content.townPoint(slug))

        fun location(slug: String): GridPoint = state.locationPoint(content, slug)

        return StandaloneReplayPlan(
            name = SILVER_KEY_DISCOVERY_ROUTE,
            stateSlug = stateSlug,
            components =
                listOf(
                    moveToTown("wilor"),
                    StandaloneReplayComponent(
                        name = "town.wilor_buy_silver_key_map",
                        commands = "E3N" + speakNpc(1, "B") + "L",
                        checkpoints = listOf("output:precious map", "state:silver_key.map_bought"),
                    ),
                    moveTo("travel.to_silver_sanctum", location("silver_key_pit")),
                    StandaloneReplayComponent(
                        name = "mission.silver_sanctum_note",
                        commands = silverSanctumRoute(),
                        checkpoints = listOf("output:This room shines like", "state:silver_key.note_found"),
                    ),
                    moveToTown("ollk"),
                    StandaloneReplayComponent(
                        name = "town.ollk_smith_confession",
                        commands = "E3NS2BS1BLX",
                        checkpoints = listOf("output:sold the Key", "state:silver_key.smith_confessed"),
                    ),
                ),
        )
    }

    private fun hobgoblinCaravanRoute(
        bundle: CompiledGameBundle,
        stateSlug: String,
    ): StandaloneReplayPlan {
        bundle.stateBySlug(stateSlug)
        return StandaloneReplayPlan(
            name = HOBGOBLIN_CARAVAN_ROUTE,
            stateSlug = stateSlug,
            components =
                listOf(
                    StandaloneReplayComponent(
                        name = "mission.hobgoblin_caravan_random_step",
                        commands = "6" + waitForInput(2) + battle() + waitForInput(2) + "X",
                        checkpoints =
                            listOf(
                                "seed:first random-step draw below hobgoblin trigger",
                                "output:caravan of hobgoblins",
                                "state:silver_key.cave_located",
                            ),
                    ),
                ),
        )
    }

    private fun townSilverKeyChainRoute(
        bundle: CompiledGameBundle,
        stateSlug: String,
    ): StandaloneReplayPlan {
        val content = bundle.content
        val state = bundle.stateBySlug(stateSlug)
        val world = ReplayWorldMap.from(state, content = content)
        val route = world.routeTo(state.startPoint(), content.townPoint("ollk"))
        return StandaloneReplayPlan(
            name = TOWN_SILVER_KEY_CHAIN_ROUTE,
            stateSlug = stateSlug,
            components =
                listOf(
                    StandaloneReplayComponent(
                        name = "travel.to_ollk",
                        commands = route.commands,
                        checkpoints = listOf("position:ollk"),
                    ),
                    StandaloneReplayComponent(
                        name = "town.ollk_silver_key_chain",
                        commands = "E3NS2BS1BLX",
                        checkpoints =
                            listOf(
                                "output:sold the Key",
                                "output:Nah, sorry",
                                "output:Find them and take it back",
                            ),
                    ),
                ),
        )
    }

    private fun townMissionIntroRoute(
        bundle: CompiledGameBundle,
        stateSlug: String,
    ): StandaloneReplayPlan {
        val content = bundle.content
        val state = bundle.stateBySlug(stateSlug)
        val world = ReplayWorldMap.from(state, content = content)
        var point = state.startPoint()

        fun moveToTown(slug: String): StandaloneReplayComponent {
            val route = world.routeTo(point, content.townPoint(slug))
            point = route.end
            return StandaloneReplayComponent(
                name = "travel.to_$slug",
                commands = route.commands,
                checkpoints = listOf("position:$slug"),
            )
        }

        return StandaloneReplayPlan(
            name = TOWN_MISSION_INTRO_ROUTE,
            stateSlug = stateSlug,
            components =
                buildList {
                    add(moveToTown("thord"))
                    add(
                        StandaloneReplayComponent(
                            name = "town.thord_mission_dialogues",
                            commands = thordMissionDialogueRoute(),
                            checkpoints =
                                listOf(
                                    "output:In fact, I have a mission",
                                    "output:You persuade him to",
                                    "output:You take the blade.",
                                    "output:GO TO CAVE AT",
                                ),
                        ),
                    )
                    add(moveToTown("wilor"))
                    add(
                        StandaloneReplayComponent(
                            name = "town.wilor_mission_dialogues",
                            commands = wilorMissionDialogueRoute(),
                            checkpoints =
                                listOf(
                                    "output:precious map",
                                    "output:temple",
                                    "output:Ah, a seeker",
                                    "output:Erethor's Lute",
                                ),
                        ),
                    )
                },
        )
    }

    private fun townGarthRewardSmoke(
        bundle: CompiledGameBundle,
        stateSlug: String,
    ): StandaloneReplayPlan {
        val content = bundle.content
        val state = bundle.stateBySlug(stateSlug)
        val world = ReplayWorldMap.from(state, content = content)
        val route = world.routeTo(state.startPoint(), content.townPoint("thord"))
        return StandaloneReplayPlan(
            name = TOWN_GARTH_REWARD_SMOKE,
            stateSlug = stateSlug,
            components =
                listOf(
                    StandaloneReplayComponent(
                        name = "travel.to_thord",
                        commands = route.commands,
                        checkpoints = listOf("output:Terrain: Town", "position:thord"),
                    ),
                    StandaloneReplayComponent(
                        name = "town.thord_garth_reward_dialogue",
                        commands = "E3S1BLX",
                        checkpoints =
                            listOf(
                                "output:Entering Thord",
                                "output:The merchant is GRATEFUL!",
                                "output:You pick up the gem.",
                            ),
                    ),
                ),
        )
    }

    private fun townSocialSmoke(
        bundle: CompiledGameBundle,
        stateSlug: String,
    ): StandaloneReplayPlan {
        val content = bundle.content
        val state = bundle.stateBySlug(stateSlug)
        val world = ReplayWorldMap.from(state, content = content)
        val route = world.routeTo(state.startPoint(), content.townPoint("thord"))
        return StandaloneReplayPlan(
            name = TOWN_SOCIAL_SMOKE,
            stateSlug = stateSlug,
            components =
                listOf(
                    StandaloneReplayComponent(
                        name = "travel.to_thord",
                        commands = route.commands,
                        checkpoints = listOf("output:Terrain: Town", "position:thord"),
                    ),
                    StandaloneReplayComponent(
                        name = "town.thord_service_social_circuit",
                        commands = "E4QLE3N",
                        checkpoints =
                            listOf(
                                "output:Entering Thord",
                                "output:The smith welcomes you",
                                "output:Welcome! Have a seat",
                            ),
                    ),
                ),
        )
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

    private fun silverSanctumRoute(): String =
        "E" +
            dungeonRoute(
                levels = 20,
                descendEvents = mapOf(20 to waitForInput(4)),
            )

    private fun koboldVillageRoute(): String =
        "A" +
            waitForInput() +
            battle(attacks = 12) +
            waitForInput() +
            battle(attacks = 12) +
            waitForInput() +
            battle(attacks = 12) +
            waitForInput()

    private fun reebarTombRoute(): String =
        "E" +
            dungeonRoute(
                levels = 20,
                descendEvents = reebarDescendEvents(),
                ascendRecurringEvents = reebarAscendEvents(),
            )

    private fun reebarDescendEvents(): Map<Int, String> =
        mapOf(
            12 to waitForInput(),
            20 to waitForInput(),
        ) + (13..19).associateWith(::colourRiddleRoute)

    private fun reebarAscendEvents(): Map<Int, String> = mapOf(12 to waitForInput()) + (13..19).associateWith(::colourRiddleRoute)

    private fun colourRiddleRoute(level: Int): String = waitForInput(2) + (20 - level) + waitForInput()

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

    private fun thordMissionDialogueRoute(): String =
        "E3N" +
            speakNpc(1) +
            speakNpc(3) +
            speakNpc(7) +
            speakNpc(1) +
            speakNpc(8) +
            speakNpc(6) +
            speakNpc(2) +
            speakNpc(5, "AA") +
            "L"

    private fun wilorMissionDialogueRoute(): String =
        "E3N" +
            speakNpc(1, "B") +
            speakNpc(4, "B") +
            speakNpc(2) +
            speakNpc(5) +
            speakNpc(6) +
            speakNpc(7) +
            "LX"

    private fun speakNpc(
        index: Int,
        branchInput: String = "",
    ): String = "S$index${branchInput}B"

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

private fun CompiledContentBundle.townPoint(slug: String): GridPoint =
    towns.firstOrNull { town -> slug(town.id) == slug }?.position
        ?: error("compiled replay town $slug not present in content")

private fun CompiledStateImage.activeParty() =
    parties.firstOrNull { party -> party.id == realm.activeParty }
        ?: error("compiled replay state has no active party ${realm.activeParty}")
