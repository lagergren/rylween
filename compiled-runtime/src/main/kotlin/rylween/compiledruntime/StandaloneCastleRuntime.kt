/**
 * Generic grid-castle loop for the standalone compiled-bundle runtime.
 *
 * Castles are data rows in `runtimeSystems.castles`, not Kotlin classes named
 * for one scenario. This runtime seeds rooms, moves through the authored grid,
 * runs lifecycle scripts, battles, traps, treasure, stairs, and utility
 * commands from compiled content instead of a legacy castle implementation.
 */

package rylween.compiledruntime

import rylween.compiled.CompiledContentBundle
import rylween.compiled.CompiledHandleKind
import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledScriptProgram
import rylween.compiled.ExternalRuntimeCastle
import rylween.compiled.ExternalRuntimeCommand
import rylween.compiled.ExternalRuntimeSystems
import rylween.compiled.ExternalRuntimeTextBlock
import rylween.dsl.GridPoint
import rylween.dsl.PcClass
import rylween.dsl.RuntimeMode
import java.io.File

/** Saveable standalone castle state. */
internal data class StandaloneCastleState(
    val castleId: String,
    val floor: Int,
    val point: GridPoint,
    val rooms: Map<GridPoint, String>,
    val roomEntryPending: Boolean,
)

/** Runs one generic castle from stable runtime-system rows. */
internal class StandaloneCastleRuntime(
    private val content: CompiledContentBundle,
    private val systems: ExternalRuntimeSystems,
    private val input: StandaloneInput,
    private val screen: CompiledAnsiScreen,
    private val saveFile: File,
    private val bundleContentHash: String,
) {
    private val utility = StandaloneUtilityActions(content, systems, input, screen)

    fun run(initialRuntime: StandaloneRuntimeState): StandaloneRuntimeState {
        var runtime = initialRuntime
        while (runtime.currentMode == RuntimeMode.Castle) {
            val castle =
                runtime.castle?.let { state -> systems.castleRows.firstOrNull { row -> row.id == state.castleId } } ?: return runtime
            if (runtime.castle.roomEntryPending) {
                runtime = enterRoom(runtime, castle)
                if (input.exhausted || runtime.currentMode != RuntimeMode.Castle) return runtime
            }
            screen.renderCastle(runtime, castle)
            val command = input.readCommand() ?: return runtime
            runtime = applyNavigation(runtime, castle, command)
            if (input.exhausted) return runtime
        }
        return runtime
    }

    fun startCastle(
        runtime: StandaloneRuntimeState,
        castle: ExternalRuntimeCastle,
    ): StandaloneRuntimeState {
        castle.image?.let(screen::showImage)
        val completion = castle.completionMilestone?.let { milestone -> content.ref(CompiledHandleKind.Milestone, milestone) }
        if (completion != null && completion in runtime.party.milestones) {
            return castle.scripts.alreadyCompleted?.let { scene -> runScene(runtime, scene).finishCastleRuntime() }
                ?: runtime.asWildernessCastle()
        }

        val entryRequirement = castle.entryRequirement?.let { milestone -> content.ref(CompiledHandleKind.Milestone, milestone) }
        if (entryRequirement != null && entryRequirement !in runtime.party.milestones) {
            return castle.scripts.entryBlocked?.let { scene -> runScene(runtime, scene).finishCastleRuntime() }
                ?: runtime.asWildernessCastle()
        }

        val seeded = seedRooms(runtime, castle)
        val entered =
            seeded.runtime.copy(
                currentMode = RuntimeMode.Castle,
                currentTown = null,
                castle =
                    StandaloneCastleState(
                        castleId = castle.id,
                        floor = 1,
                        point = GridPoint(1, 1),
                        rooms = seeded.rooms,
                        roomEntryPending = true,
                    ),
                dungeon = null,
            )
        return castle.scripts.entered?.let { scene -> runScene(entered, scene) } ?: entered
    }

    private fun applyNavigation(
        runtime: StandaloneRuntimeState,
        castle: ExternalRuntimeCastle,
        command: Char,
    ): StandaloneRuntimeState {
        val navigation = systems.commands.getValue("castle.${castle.id}.navigation")
        return when {
            navigation.matches("moveNorth", command) -> move(runtime, castle, dx = 0, dy = -1, castle.text.moveNorth)
            navigation.matches("moveSouth", command) -> move(runtime, castle, dx = 0, dy = 1, castle.text.moveSouth)
            navigation.matches("moveWest", command) -> move(runtime, castle, dx = -1, dy = 0, castle.text.moveWest)
            navigation.matches("moveEast", command) -> move(runtime, castle, dx = 1, dy = 0, castle.text.moveEast)
            navigation.matches("save", command) -> runtime.also { saveRuntime(it) }
            navigation.matches("help", command) -> runtime.also { showNavigationHelp(castle) }
            navigation.matches(
                "viewParty",
                command,
            ) -> runtime.also { showCastlePage(it, castle, StandalonePartyPages.partyLines(content, it.party)) }
            navigation.matches("drop", command) -> utility.drop(runtime).also { redrawCastleIfCurrent(it, castle) }
            navigation.matches("removeArmor", command) -> utility.removeArmor(runtime).also { redrawCastleIfCurrent(it, castle) }
            navigation.matches("removeWeapon", command) -> utility.removeWeapon(runtime).also { redrawCastleIfCurrent(it, castle) }
            navigation.matches("readyWeapon", command) -> utility.readyWeapon(runtime).also { redrawCastleIfCurrent(it, castle) }
            navigation.matches("readyArmor", command) -> utility.readyArmor(runtime).also { redrawCastleIfCurrent(it, castle) }
            navigation.matches("quaff", command) -> utility.quaff(runtime).also { redrawCastleIfCurrent(it, castle) }
            navigation.matches("castSpell", command) -> utility.castPriestSpell(runtime).also { redrawCastleIfCurrent(it, castle) }
            else -> runtime
        }
    }

    private fun enterRoom(
        runtime: StandaloneRuntimeState,
        castle: ExternalRuntimeCastle,
    ): StandaloneRuntimeState {
        val state = runtime.castle ?: return runtime
        if (state.point.x == castle.mapSize && state.point.y == castle.mapSize) return stairs(runtime.clearRoomEntry(), castle)
        return when (state.rooms[state.point] ?: CASTLE_ROOM_EMPTY) {
            CASTLE_ROOM_MONSTER -> roomMonster(runtime.clearCurrentRoom().clearRoomEntry(), castle)
            CASTLE_ROOM_PITFALL -> pitfall(runtime.clearCurrentRoom().clearRoomEntry(), castle)
            CASTLE_ROOM_TREASURE -> treasure(runtime.clearCurrentRoom().clearRoomEntry(), castle)
            else -> runtime.clearRoomEntry()
        }
    }

    private fun move(
        runtime: StandaloneRuntimeState,
        castle: ExternalRuntimeCastle,
        dx: Int,
        dy: Int,
        text: ExternalRuntimeTextBlock,
    ): StandaloneRuntimeState {
        val state = runtime.castle ?: return runtime
        val next = GridPoint(state.point.x + dx, state.point.y + dy)
        if (next.x !in 1..castle.mapSize || next.y !in 1..castle.mapSize) return runtime
        screen.writeText(text.renderCastle())
        return runtime.copy(castle = state.copy(point = next, roomEntryPending = true))
    }

    private fun roomMonster(
        runtime: StandaloneRuntimeState,
        castle: ExternalRuntimeCastle,
    ): StandaloneRuntimeState {
        screen.writeText(castle.text.roomMonster.renderCastle())
        val battle =
            StandaloneCompiledBattleRunner(content, systems.combat, input, screen)
                .runTier(runtime, castle.monsterTier)
        if (battle.messages.isNotEmpty()) screen.writeText(battle.messages)
        return battle.runtime.copy(
            currentMode =
                battle.runtime.currentMode.takeUnless { it == RuntimeMode.Wilderness } ?: RuntimeMode.Castle,
        )
    }

    private fun pitfall(
        runtime: StandaloneRuntimeState,
        castle: ExternalRuntimeCastle,
    ): StandaloneRuntimeState {
        var next = runtime
        val thiefIndex = next.party.members.indexOfFirst { member -> !member.dead && member.pcClass == PcClass.Thief }
        val disarmed =
            thiefIndex
                .takeIf { index -> index >= 0 }
                ?.let { index ->
                    val thief = next.party.members[index]
                    screen.writeText(listOf(content.strings.value(thief.nameString)) + castle.text.trapTriesDisarmSuffix.renderCastle())
                    val draw = next.party.drawRandom(0, 99)
                    next = next.withParty(draw.party)
                    draw.value < thief.wisdom.coerceIn(0, 100)
                } ?: false
        if (thiefIndex >= 0) {
            screen.writeText(
                if (disarmed) castle.text.trapDisarmSucceed.renderCastle() else castle.text.trapDisarmFail.renderCastle(),
            )
        }
        if (disarmed) return next

        val trapText = next.randomTrapText(castle)
        next = trapText.runtime
        if (trapText.lines.isNotEmpty()) screen.writeText(trapText.lines)
        return next.withParty(
            next.party.copy(
                members =
                    next.party.members.map { member ->
                        member.takeUnless { it.dead }?.withDamage(member.hitPoints.current / castle.pitfall.damageDivisor) ?: member
                    },
            ),
        )
    }

    private fun treasure(
        runtime: StandaloneRuntimeState,
        castle: ExternalRuntimeCastle,
    ): StandaloneRuntimeState {
        val roll = runtime.rollTreasure(castle)
        var next = roll.runtime
        val item = roll.item ?: return next
        screen.writeText(listOf(itemName(item)) + castle.text.treasureMenu.renderCastle())
        val command = input.readCommand() ?: return next
        val treasureCommands = systems.commands.getValue("castle.${castle.id}.treasure")
        if (!treasureCommands.matches("take", command)) return next
        val freeSlot =
            next.party.inventory
                .firstOrNull { slot -> slot.item == null }
                ?.slot
        if (freeSlot == null) {
            screen.writeText(castle.text.treasureFull.renderCastle())
            return next
        }
        next = next.withParty(next.party.withInventoryItem(freeSlot, item))
        screen.writeText(castle.text.treasureTaken.renderCastle())
        return next
    }

    private fun stairs(
        runtime: StandaloneRuntimeState,
        castle: ExternalRuntimeCastle,
    ): StandaloneRuntimeState {
        screen.writeText(
            castle.text.stairsIntro.renderCastle() + castle.text.stairsGuardianOffer.renderCastle() + castle.text.stairsMenu.renderCastle(),
        )
        val command = input.readCommand() ?: return runtime
        val stairsCommands = systems.commands.getValue("castle.${castle.id}.stairs")
        return when {
            stairsCommands.matches("teleportOut", command) -> teleportOut(runtime, castle)
            stairsCommands.matches("giveItem", command) -> giveStairItem(runtime, castle)
            stairsCommands.matches("leave", command) -> leaveStairs(runtime, castle)
            else -> runtime
        }
    }

    private fun leaveStairs(
        runtime: StandaloneRuntimeState,
        castle: ExternalRuntimeCastle,
    ): StandaloneRuntimeState {
        screen.writeText(castle.text.stairsLeave.renderCastle())
        val state = runtime.castle ?: return runtime
        return runtime.copy(castle = state.copy(point = state.point.copy(y = (state.point.y - 1).coerceAtLeast(1))))
    }

    private fun teleportOut(
        runtime: StandaloneRuntimeState,
        castle: ExternalRuntimeCastle,
    ): StandaloneRuntimeState {
        screen.writeText(castle.text.teleportVortex.renderCastle() + castle.text.teleportOutside.renderCastle())
        return runtime.asWildernessCastle()
    }

    private fun giveStairItem(
        runtime: StandaloneRuntimeState,
        castle: ExternalRuntimeCastle,
    ): StandaloneRuntimeState {
        screen.writeText(castle.text.giveMenu.renderCastle())
        val digit = input.readCommand()?.digitToIntOrNull() ?: return runtime
        val item = castle.stairs.items.firstOrNull { stair -> stair.digit == digit } ?: return runtime
        val milestone = content.ref(CompiledHandleKind.Milestone, item.milestone)
        if (milestone !in runtime.party.milestones) {
            screen.writeText(item.reject.renderCastle())
            return runtime
        }
        val state = runtime.castle ?: return runtime
        val nextFloor = state.floor + 1
        val nextParty = runtime.party.copy(milestones = runtime.party.milestones - milestone)
        val next =
            runtime
                .withParty(
                    nextParty,
                ).copy(castle = state.copy(floor = nextFloor, point = GridPoint(1, 1), roomEntryPending = true))
        if (nextFloor > castle.stairs.floorCount) return completeCastle(next, castle)
        screen.writeText(castle.text.stairsGuardianGrateful.renderCastle())
        return next
    }

    private fun completeCastle(
        runtime: StandaloneRuntimeState,
        castle: ExternalRuntimeCastle,
    ): StandaloneRuntimeState {
        val scripted = castle.scripts.completed?.let { scene -> runScene(runtime, scene) }
        if (scripted != null) return scripted.finishCastleRuntime()
        val milestone = castle.completionMilestone?.let { content.ref(CompiledHandleKind.Milestone, it) }
        val party = milestone?.let { runtime.party.copy(milestones = (runtime.party.milestones + it).distinct()) } ?: runtime.party
        return runtime.withParty(party).asWildernessCastle()
    }

    private fun showNavigationHelp(castle: ExternalRuntimeCastle) {
        val lines =
            systems.commands.getValue("castle.${castle.id}.navigation").map { command ->
                "${command.binding.primary}. ${command.label ?: command.binding.label}"
            }
        screen.writeText(lines)
        input.readCommand()
    }

    private fun showCastlePage(
        runtime: StandaloneRuntimeState,
        castle: ExternalRuntimeCastle,
        lines: List<String>,
    ) {
        screen.writeText(lines)
        if (input.readCommand() != null) redrawCastleIfCurrent(runtime, castle)
    }

    private fun redrawCastleIfCurrent(
        runtime: StandaloneRuntimeState,
        castle: ExternalRuntimeCastle,
    ) {
        if (runtime.currentMode == RuntimeMode.Castle) screen.renderCastle(runtime, castle)
    }

    private fun saveRuntime(runtime: StandaloneRuntimeState) {
        StandaloneCompiledSave.write(saveFile, runtime, bundleContentHash, content)
        screen.writeText(listOf(saveFile.path))
    }

    private fun seedRooms(
        runtime: StandaloneRuntimeState,
        castle: ExternalRuntimeCastle,
    ): SeededCastleRooms {
        var next = runtime
        val rooms =
            buildMap {
                (1..castle.mapSize).forEach { x ->
                    (1..castle.mapSize).forEach { y ->
                        val draw = next.party.drawRandom(1, castle.roomTypeMax)
                        next = next.withParty(draw.party)
                        put(GridPoint(x, y), castle.roomEvents[draw.value] ?: CASTLE_ROOM_EMPTY)
                    }
                }
                put(GridPoint(castle.mapSize, castle.mapSize), CASTLE_ROOM_EMPTY)
            }
        return SeededCastleRooms(next, rooms)
    }

    private fun StandaloneRuntimeState.rollTreasure(castle: ExternalRuntimeCastle): CastleTreasureRoll {
        val gate = party.drawRandom(0, castle.treasure.gateMax)
        var next = withParty(gate.party)
        val itemSlug =
            if (gate.value == 0) {
                if (castle.treasure.magic.isEmpty()) return CastleTreasureRoll(next, null)
                val draw = next.party.drawRandom(0, castle.treasure.magic.lastIndex)
                next = next.withParty(draw.party)
                castle.treasure.magic.getOrNull(draw.value)
            } else {
                if (castle.treasure.regularKinds.isEmpty()) return CastleTreasureRoll(next, null)
                val kind = next.party.drawRandom(0, castle.treasure.regularKinds.lastIndex)
                next = next.withParty(kind.party)
                val table =
                    castle.treasure.regularKinds
                        .getOrNull(kind.value)
                        .orEmpty()
                if (table.isEmpty()) return CastleTreasureRoll(next, null)
                val item = next.party.drawRandom(0, table.lastIndex)
                next = next.withParty(item.party)
                table.getOrNull(item.value)
            }
        return CastleTreasureRoll(next, itemSlug?.let { slug -> content.ref(CompiledHandleKind.Item, slug) })
    }

    private fun StandaloneRuntimeState.randomTrapText(castle: ExternalRuntimeCastle): CastleTrapText {
        if (castle.pitfall.trapTypes.isEmpty()) return CastleTrapText(this, emptyList())
        val draw = party.drawRandom(0, castle.pitfall.trapTypes.lastIndex)
        return CastleTrapText(withParty(draw.party), castle.pitfall.trapTypes[draw.value].renderCastle())
    }

    private fun runScene(
        runtime: StandaloneRuntimeState,
        sceneSlug: String,
    ): StandaloneRuntimeState {
        val program = sceneProgram(sceneSlug) ?: return runtime
        return StandaloneScriptExecutor(content, systems, input, screen, runtime).execute(program)
    }

    private fun sceneProgram(sceneSlug: String): CompiledScriptProgram? =
        content.scripts.roots.firstOrNull { root -> root.root.kind.name == "Scene" && root.root.slug == sceneSlug }

    private fun itemName(item: CompiledHandleRef): String =
        content.items
            .firstOrNull { record -> record.id == item }
            ?.let { record -> content.strings.value(record.displayNameString) }
            ?: content.slug(item)
}

internal fun StandaloneRuntimeState.asWildernessCastle(): StandaloneRuntimeState =
    copy(currentMode = RuntimeMode.Wilderness, currentTown = null, castle = null, dungeon = null)

private fun StandaloneRuntimeState.finishCastleRuntime(): StandaloneRuntimeState =
    if (currentMode == RuntimeMode.GameOver) this else asWildernessCastle()

private fun StandaloneRuntimeState.clearRoomEntry(): StandaloneRuntimeState = copy(castle = castle?.copy(roomEntryPending = false))

private fun StandaloneRuntimeState.clearCurrentRoom(): StandaloneRuntimeState {
    val state = castle ?: return this
    return copy(castle = state.copy(rooms = state.rooms + (state.point to CASTLE_ROOM_EMPTY)))
}

private fun rylween.compiled.CompiledPartyMember.withDamage(damage: Int): rylween.compiled.CompiledPartyMember {
    val nextHp = (hitPoints.current - damage).coerceAtLeast(0)
    return copy(hitPoints = hitPoints.copy(current = nextHp), dead = nextHp == 0)
}

private fun ExternalRuntimeTextBlock.renderCastle(): List<String> = render { null }

private fun List<ExternalRuntimeCommand>.matches(
    action: String,
    command: Char,
): Boolean =
    firstOrNull { candidate -> candidate.action == action }
        ?.binding
        ?.matches(command)
        ?: false

private data class SeededCastleRooms(
    val runtime: StandaloneRuntimeState,
    val rooms: Map<GridPoint, String>,
)

private data class CastleTreasureRoll(
    val runtime: StandaloneRuntimeState,
    val item: CompiledHandleRef?,
)

private data class CastleTrapText(
    val runtime: StandaloneRuntimeState,
    val lines: List<String>,
)

private const val CASTLE_ROOM_EMPTY = "Empty"
private const val CASTLE_ROOM_MONSTER = "Monster"
private const val CASTLE_ROOM_PITFALL = "Pitfall"
private const val CASTLE_ROOM_TREASURE = "Treasure"
