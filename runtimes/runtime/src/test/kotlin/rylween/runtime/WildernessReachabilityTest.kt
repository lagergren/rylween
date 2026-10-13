package rylween.runtime

import rylween.compiled.CompiledCharacterPlacement
import rylween.compiled.CompiledGameBundle
import rylween.compiled.CompiledHandleKind
import rylween.compiled.CompiledRandomNpcPlacementOption
import rylween.compiled.CompiledStateImage
import rylween.compiled.CompiledTrigger
import rylween.compiled.CompiledWorldPosition
import rylween.compiled.RuntimeCommandActionId
import rylween.compiled.RuntimeCommandGroupId
import rylween.compiled.readExternalCompiledBundleContentHash
import rylween.compiled.readExternalCompiledGameBundle
import rylween.compiled.readExternalRuntimeSystems
import rylween.dsl.impl.GridPoint
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards that every character and quest location can be walked to and met, so no
 * game can strand a quest out of the party's reach.
 */
class WildernessReachabilityTest {
    private val bundleFile = File(requiredRuntimeTestProperty("rylween.runtime.bundle"))
    private val bundle = bundleFile.readExternalCompiledGameBundle()
    private val systems = bundleFile.readExternalRuntimeSystems()
    private val content = bundle.content
    private val newGame = bundle.state("fresh-replay")
    private val world = WorldMap.from(content, newGame.realm.currentMap)
    private val reachable = reachableFrom(newGame.startPoint())

    @Test
    fun everySquareACharacterCanStandOnIsReachable() {
        val stranded =
            content.characters.flatMap { character ->
                val name = content.strings.value(character.nameString)
                when (val placement = character.placement) {
                    is CompiledCharacterPlacement.Fixed ->
                        (placement.position as? CompiledWorldPosition.Wilderness)
                            ?.point
                            ?.takeUnless(reachable::contains)
                            ?.let { point -> listOf("$name at ${point.x},${point.y}") }
                            .orEmpty()

                    is CompiledCharacterPlacement.Randomized ->
                        placement.policy.options
                            .filterIsInstance<CompiledRandomNpcPlacementOption.WildernessRect>()
                            .flatMap { option -> option.candidateSquares() }
                            .filterNot(reachable::contains)
                            .map { point -> "$name could be placed at ${point.x},${point.y}" }

                    CompiledCharacterPlacement.Unplaced -> emptyList()
                }
            }
        assertTrue(stranded.isEmpty(), "characters placed out of reach:\n${stranded.joinToString("\n")}")
    }

    @Test
    fun everyStatePlacesEveryMissionLocationOnItsOwnReachableSquare() {
        val missionLocations =
            content.missions.mapNotNull { mission -> (mission.trigger as? CompiledTrigger.AtLocation)?.location }.toSet()
        val reserved =
            buildMap {
                content.towns.forEach { town -> put(town.position, "town ${content.strings.value(town.nameString)}") }
                systems.castleRows.forEach { castle -> put(castle.entry, "castle ${castle.id}") }
                content.missions.forEach { mission ->
                    val trigger = mission.trigger as? CompiledTrigger.AtPosition
                    if (trigger != null) put(trigger.point, "mission ${content.slug(mission.id)}")
                }
                content.characters.forEach { character ->
                    ((character.placement as? CompiledCharacterPlacement.Fixed)?.position as? CompiledWorldPosition.Wilderness)
                        ?.let { position -> put(position.point, "character ${content.strings.value(character.nameString)}") }
                }
            }
        val problems =
            bundle.states.flatMap { state ->
                val stateName = content.slug(state.state)
                val placed = state.locations.associateBy { placement -> placement.location }
                val missing = (missionLocations - placed.keys).map { location -> "$stateName does not place ${content.slug(location)}" }
                val misplaced =
                    state.locations.mapNotNull { placement ->
                        val where = "$stateName places ${content.slug(placement.location)} at ${placement.point.x},${placement.point.y}"
                        val sharing = state.locations.count { other -> other.point == placement.point }
                        when {
                            placement.point !in reachable -> "$where, out of reach"
                            placement.point in reserved -> "$where, on the ${reserved.getValue(placement.point)} square"
                            sharing > 1 -> "$where, shared with another location"
                            else -> null
                        }
                    }
                missing + misplaced
            }
        assertTrue(problems.isEmpty(), problems.joinToString("\n"))
    }

    /** Pressing Enter on a wilderness character's square opens the hut and its conversation. */
    @Test
    fun enteringAWildernessCharactersSquareOpensTheHutConversation() {
        val hermit = content.characters.single { character -> content.strings.value(character.nameString) == "Marcus" }
        val hut = ((hermit.placement as CompiledCharacterPlacement.Fixed).position as CompiledWorldPosition.Wilderness).point
        // The near-winning state draws no random wilderness events, so a fixed walk reaches the hut.
        val state = bundle.state("near-winning-replay")
        val walk = walkKeys(state.startPoint(), hut)
        val screen = TextCapturingScreen()
        val testTmp = File(requiredRuntimeTestProperty("rylween.runtime.testTmp")).also(File::mkdirs)
        RuntimeLog.open(testTmp.resolve("wilderness-hut.log")).use { log ->
            RuntimeSessionFactory.load(bundle, systems, bundleFile.readExternalCompiledBundleContentHash()).start(
                input = ScriptedInput("${walk}ET1BBBL"),
                screen = screen,
                config =
                    RuntimeSessionConfig(
                        userId = "hut-test",
                        stateSlug = "near-winning-replay",
                        saveFile = testTmp.resolve("wilderness-hut.rsave").also(File::delete),
                    ),
                log = log,
            )
        }

        assertContains(screen.lines, "You enter the hut.")
        assertContains(screen.lines, "1. Marcus, Hermit")
        assertTrue(screen.lines.any { line -> line.startsWith("Marcus") }, screen.lines.takeLast(40).joinToString("\n"))
    }

    /** Leaving a quest dungeon at its entrance, or climbing out before its last level, keeps the quest open. */
    @Test
    fun aQuestDungeonLeftEarlyCanBeEnteredAgain() {
        val start = RuntimeState.from(newGame, content)
        val crypt = start.locations.single { placement -> content.slug(placement.location) == "nolrim_crypt" }.point
        val beside = AutopilotDirection.entries.map { direction -> direction.from(crypt) }.first { point -> world.terrainAt(point)?.passable == true }
        val flag = content.flags.single { flag -> content.slug(flag.id) == "master_crown" }
        val borinMet = flag.states.single { state -> content.slug(state.id) == "master_crown.borin_met" }.id
        // No random state, so no wilderness event interrupts the walk.
        val party =
            start.party.copy(
                position = beside,
                flags = start.party.flags - flag.states.map { state -> state.id }.toSet() + borinMet,
                rngState = null,
            )
        val testTmp = File(requiredRuntimeTestProperty("rylween.runtime.testTmp")).also(File::mkdirs)
        val hash = bundleFile.readExternalCompiledBundleContentHash()
        val load = testTmp.resolve("crypt-reentry-start.rsave").also(File::delete)
        SaveFile.write(load, start.withParty(party), hash, content, "crypt-test", null)
        val on = walkKeys(beside, crypt)
        val off = walkKeys(crypt, beside)
        val ascend = systems.commands.getValue(RuntimeCommandGroupId.Dungeon.id).key(RuntimeCommandActionId.Ascend)
        val screen = TextCapturingScreen()
        // After entering, RETURN dismisses the "Entering" screen and then the first level's find.
        RuntimeLog.open(testTmp.resolve("crypt-reentry.log")).use { log ->
            RuntimeSessionFactory.load(bundle, systems, hash).start(
                input = ScriptedInput("${on}L$off${on}E\n\n$ascend$off${on}L"),
                screen = screen,
                config =
                    RuntimeSessionConfig(
                        userId = "crypt-test",
                        stateSlug = "fresh-replay",
                        loadFile = load,
                        saveFile = testTmp.resolve("crypt-reentry.rsave").also(File::delete),
                    ),
                log = log,
            )
        }

        assertEquals(3, screen.lines.count { line -> line == "Before you is the crypt" }, screen.lines.joinToString("\n"))
    }

    private fun walkKeys(
        from: GridPoint,
        to: GridPoint,
    ): String {
        val navigation = AutopilotNavigation(content)
        val keys = systems.commands.getValue(RuntimeCommandGroupId.Wilderness.id)
        return buildString {
            var point = from
            while (point != to) {
                val step = checkNotNull(navigation.stepTowards(newGame.realm.currentMap, point, to)) { "no path to ${to.x},${to.y}" }
                val action =
                    when (step) {
                        AutopilotDirection.North -> "moveNorth"
                        AutopilotDirection.South -> "moveSouth"
                        AutopilotDirection.West -> "moveWest"
                        AutopilotDirection.East -> "moveEast"
                    }
                append(keys.first { command -> command.action == action }.binding.primary)
                point = step.from(point)
            }
        }
    }

    private fun reachableFrom(start: GridPoint): Set<GridPoint> {
        val seen = mutableSetOf(start)
        val queue = ArrayDeque(listOf(start))
        while (queue.isNotEmpty()) {
            val point = queue.removeFirst()
            AutopilotDirection.entries.forEach { direction ->
                val next = direction.from(point)
                if (next !in seen && world.terrainAt(next)?.passable == true) {
                    seen += next
                    queue.addLast(next)
                }
            }
        }
        return seen
    }

    private fun CompiledRandomNpcPlacementOption.WildernessRect.candidateSquares(): List<GridPoint> =
        y
            .flatMap { row -> x.map { column -> GridPoint(xReplacements[column] ?: column, row) } }
            .filter { point -> world.terrainAt(point)?.allowsNpcPlacement == true }

    private class TextCapturingScreen : NoOpRuntimeScreen() {
        val lines = mutableListOf<String>()

        override fun writeText(lines: List<String>) {
            this.lines += lines
        }

        override fun showMessage(lines: List<String>) {
            this.lines += lines
        }
    }
}

private fun CompiledGameBundle.state(slug: String): CompiledStateImage {
    val ref = content.ref(CompiledHandleKind.State, slug)
    return states.single { state -> state.state == ref }
}

private fun CompiledStateImage.startPoint(): GridPoint {
    val party = parties.single { party -> party.id == realm.activeParty }
    return (party.position as CompiledWorldPosition.Wilderness).point
}
