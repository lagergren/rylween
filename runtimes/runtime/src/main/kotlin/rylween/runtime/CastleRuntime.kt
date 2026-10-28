/**
 * Generic grid-castle loop for the runtime.
 *
 * Castles are data rows in `runtimeSystems.castles`, not Kotlin classes named
 * for one scenario. This runtime seeds rooms, moves through the authored grid,
 * runs lifecycle scripts, battles, traps, treasure, stairs, and utility
 * commands from compiled content instead of a legacy castle implementation.
 *
 * This is a runtime mode loop, not an authored script-control loop. It owns
 * live room cadence, rendering, save/load, room seeding, and movement, while
 * castle text, room records, scripts, and rewards stay in compiled content and
 * runtime-system rows.
 */

package rylween.runtime

import rylween.compiled.CompiledHandleKind
import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledScriptProgram
import rylween.compiled.ExternalRuntimeCastle
import rylween.compiled.ExternalRuntimeTextBlock
import rylween.compiled.RuntimeCommandActionId
import rylween.compiled.RuntimeCommandGroupId
import rylween.dsl.AudioCue
import rylween.dsl.impl.GridPoint
import rylween.dsl.impl.PcClass
import rylween.dsl.impl.RuntimeMode

/** Savable castle state. */
data class CastleState(
    val castleId: String,
    val floor: Int,
    val point: GridPoint,
    val rooms: Map<GridPoint, String>,
    val visited: Set<GridPoint> = emptySet(),
    val roomEntryPending: Boolean,
)

/** Runs one generic castle from stable runtime-system rows. */
internal class CastleRuntime(
    context: RuntimeLoopContext,
) {
    private val content = context.content
    private val systems = context.systems
    private val input = context.input
    private val screen = context.screen
    private val save = context.save
    private val log = context.log
    private val utility = UtilityActions(content, systems, input, screen, log)
    private val audio = RuntimeAudio(systems.audio, screen)

    fun run(initialRuntime: RuntimeState): RuntimeState {
        var runtime = initialRuntime
        while (runtime.currentMode == RuntimeMode.Castle) {
            val castle =
                runtime.castle?.let { state -> systems.castleRows.firstOrNull { row -> row.id == state.castleId } } ?: return runtime
            audio.castleMusic(castle.id)
            if (runtime.castle.roomEntryPending) {
                screen.renderCastle(runtime, castle)
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
        runtime: RuntimeState,
        castle: ExternalRuntimeCastle,
    ): RuntimeState {
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

        audio.cue(AudioCue.EnterCastle)
        audio.castleMusic(castle.id)
        // Guardians the party has won over do not come back: a party that left the castle returns to the first floor
        // whose guardian still waits for an item of power.
        val floor = ((runtime.party.castleFloorsOpened[castle.id] ?: 0) + 1).coerceAtMost(castle.stairs.floorCount)
        val seeded = seedRooms(runtime, castle)
        val entered =
            seeded.runtime.copy(
                currentMode = RuntimeMode.Castle,
                currentTown = null,
                castle =
                    CastleState(
                        castleId = castle.id,
                        floor = floor,
                        point = GridPoint(1, 1),
                        rooms = seeded.rooms,
                        visited = setOf(GridPoint(1, 1)),
                        roomEntryPending = true,
                    ),
                dungeon = null,
            )
        return castle.scripts.entered?.let { scene -> runScene(entered, scene) } ?: entered
    }

    private fun applyNavigation(
        runtime: RuntimeState,
        castle: ExternalRuntimeCastle,
        command: Char,
    ): RuntimeState {
        val navigation = systems.commands.getValue(RuntimeCommandGroupId.castleNavigation(castle.id))
        return when {
            navigation.matches(RuntimeCommandActionId.MoveNorth, command) -> move(runtime, castle, dx = 0, dy = -1, castle.text.moveNorth)
            navigation.matches(RuntimeCommandActionId.MoveSouth, command) -> move(runtime, castle, dx = 0, dy = 1, castle.text.moveSouth)
            navigation.matches(RuntimeCommandActionId.MoveWest, command) -> move(runtime, castle, dx = -1, dy = 0, castle.text.moveWest)
            navigation.matches(RuntimeCommandActionId.MoveEast, command) -> move(runtime, castle, dx = 1, dy = 0, castle.text.moveEast)
            navigation.matches(RuntimeCommandActionId.Save, command) -> runtime.also { saveRuntime(it) }
            navigation.matches(RuntimeCommandActionId.Help, command) -> runtime.also { showNavigationHelp(castle) }
            navigation.matches(
                RuntimeCommandActionId.ViewParty,
                command,
            ) -> runtime.also { showCastlePageViews(it, castle) }

            navigation.matches(RuntimeCommandActionId.Drop, command) -> utility.drop(runtime).also { redrawCastleIfCurrent(it, castle) }
            navigation.matches(
                RuntimeCommandActionId.RemoveArmor,
                command,
            ) -> utility.removeArmor(runtime).also { redrawCastleIfCurrent(it, castle) }
            navigation.matches(
                RuntimeCommandActionId.RemoveWeapon,
                command,
            ) -> utility.removeWeapon(runtime).also { redrawCastleIfCurrent(it, castle) }
            navigation.matches(
                RuntimeCommandActionId.ReadyWeapon,
                command,
            ) -> utility.readyWeapon(runtime).also { redrawCastleIfCurrent(it, castle) }
            navigation.matches(
                RuntimeCommandActionId.ReadyArmor,
                command,
            ) -> utility.readyArmor(runtime).also { redrawCastleIfCurrent(it, castle) }
            navigation.matches(RuntimeCommandActionId.Quaff, command) -> utility.quaff(runtime).also { redrawCastleIfCurrent(it, castle) }
            navigation.matches(
                RuntimeCommandActionId.CastSpell,
                command,
            ) -> utility.castPriestSpell(runtime).also { redrawCastleIfCurrent(it, castle) }
            else -> runtime
        }
    }

    private fun enterRoom(
        runtime: RuntimeState,
        castle: ExternalRuntimeCastle,
    ): RuntimeState {
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
        runtime: RuntimeState,
        castle: ExternalRuntimeCastle,
        dx: Int,
        dy: Int,
        text: ExternalRuntimeTextBlock,
    ): RuntimeState {
        val state = runtime.castle ?: return runtime
        val next = GridPoint(state.point.x + dx, state.point.y + dy)
        if (next.x !in 1..castle.mapSize || next.y !in 1..castle.mapSize) return runtime
        screen.writeText(text.renderCastle(screen.textColumns))
        return runtime.copy(castle = state.copy(point = next, visited = state.visited + next, roomEntryPending = true))
    }

    private fun roomMonster(
        runtime: RuntimeState,
        castle: ExternalRuntimeCastle,
    ): RuntimeState {
        screen.writeText(castle.text.roomMonster.renderCastle(screen.textColumns))
        val battle =
            BattleRunner(
                content,
                systems.combat,
                systems.realm.spellCost,
                systems.pacing.battle,
                input,
                screen,
                log,
                PausePrompts(systems.prompts),
                systems.audio,
            ).runTier(runtime, castle.monsterTier)
        if (battle.messages.isNotEmpty()) screen.writeText(battle.messages)
        return battle.runtime.copy(
            currentMode =
                battle.runtime.currentMode.takeUnless { it == RuntimeMode.Wilderness } ?: RuntimeMode.Castle,
        )
    }

    private fun pitfall(
        runtime: RuntimeState,
        castle: ExternalRuntimeCastle,
    ): RuntimeState {
        castle.text.pitfallIllustration?.let(screen::showImage)
        var next = runtime
        val thiefIndex =
            next.party.members.indexOfFirst { member ->
                !member.dead && member.pcClass == PcClass.Thief
            }
        val disarmed =
            thiefIndex
                .takeIf { index -> index >= 0 }
                ?.let { index ->
                    val thief = next.party.members[index]
                    // The suffix continues the thief's name, so the first of its lines joins the name's.
                    val suffix = castle.text.trapTriesDisarmSuffix.renderCastle(screen.textColumns)
                    val tries = next.party.memberName(content, index) + suffix.firstOrNull()?.let { line -> " $line" }.orEmpty()
                    screen.writeText(listOf(tries) + suffix.drop(1))
                    val disarmChance = content.evalFormula(castle.pitfall.disarmChance, next.party, thief.statBindings())
                    val draw = disarmChance.party.drawRandom(0, 99)
                    next = next.withParty(draw.party)
                    draw.value < disarmChance.value.coerceIn(0, 100)
                } ?: false
        if (thiefIndex >= 0) {
            screen.writeText(
                if (disarmed) {
                    castle.text.trapDisarmSucceed.renderCastle(
                        screen.textColumns,
                    )
                } else {
                    castle.text.trapDisarmFail.renderCastle(screen.textColumns)
                },
            )
        }
        if (disarmed) {
            audio.cue(AudioCue.CastleTrapDisarmed)
            return next
        }

        audio.cue(AudioCue.CastleTrap)
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
        runtime: RuntimeState,
        castle: ExternalRuntimeCastle,
    ): RuntimeState {
        val roll = runtime.rollTreasure(castle)
        var next = roll.runtime
        val item = roll.item ?: return next
        castle.text.treasureIllustration?.let(screen::showImage)
        val lines = screen.choiceMenuLines(listOf(itemName(item)), castle.text.treasureMenu.renderCastle(screen.textColumns))
        screen.writeText(lines)
        val treasureCommands = systems.commands.getValue(RuntimeCommandGroupId.castleTreasure(castle.id))
        var command = input.readCommand() ?: return next
        while (true) {
            when {
                command == '!' -> screen.redrawCurrentView(lines)
                treasureCommands.matches(RuntimeCommandActionId.Take, command) -> break
                treasureCommands.matches(RuntimeCommandActionId.Leave, command) -> return next
                else -> screen.redrawCurrentView(lines)
            }
            command = input.readCommand() ?: return next
        }
        val freeSlot =
            next.party.inventory
                .firstOrNull { slot -> slot.item == null }
                ?.slot
        val targetSlot =
            freeSlot ?: chooseDropSlotForInventoryOverflow(
                systems = systems,
                input = input,
                screen = screen,
                party = next.party,
                leadLines = castle.text.treasureFull.renderCastle(screen.textColumns),
                itemName = ::itemName,
            )
        if (targetSlot == null) return next
        val carried = next.party.withInventoryItem(targetSlot, item).leaveExcessFood(content, systems, screen.textColumns)
        next = next.withParty(carried.party)
        audio.cue(AudioCue.CastleTreasure)
        screen.writeText(castle.text.treasureTaken.renderCastle(screen.textColumns) + carried.notice)
        return next
    }

    private fun stairs(
        runtime: RuntimeState,
        castle: ExternalRuntimeCastle,
    ): RuntimeState {
        castle.text.stairsIllustration?.let(screen::showImage)
        val lines =
            castle.text.stairsIntro.renderCastle(screen.textColumns) + castle.text.stairsGuardianOffer.renderCastle(screen.textColumns) +
                castle.text.stairsMenu.renderCastle(screen.textColumns)
        screen.writeText(lines)
        val stairsCommands = systems.commands.getValue(RuntimeCommandGroupId.castleStairs(castle.id))
        val command =
            input.readValidChoice(screen, lines, audio) { choice ->
                stairsCommands.matches(RuntimeCommandActionId.TeleportOut, choice) ||
                    stairsCommands.matches(RuntimeCommandActionId.GiveItem, choice) ||
                    stairsCommands.matches(RuntimeCommandActionId.Leave, choice)
            } ?: return runtime
        return when {
            stairsCommands.matches(RuntimeCommandActionId.TeleportOut, command) -> teleportOut(runtime, castle)
            stairsCommands.matches(RuntimeCommandActionId.GiveItem, command) -> giveStairItem(runtime, castle)
            stairsCommands.matches(RuntimeCommandActionId.Leave, command) -> leaveStairs(runtime, castle)
            else -> runtime
        }
    }

    private fun leaveStairs(
        runtime: RuntimeState,
        castle: ExternalRuntimeCastle,
    ): RuntimeState {
        screen.writeText(castle.text.stairsLeave.renderCastle(screen.textColumns))
        val state = runtime.castle ?: return runtime
        val nextPoint = state.point.copy(y = (state.point.y - 1).coerceAtLeast(1))
        return runtime.copy(castle = state.copy(point = nextPoint, visited = state.visited + nextPoint))
    }

    private fun teleportOut(
        runtime: RuntimeState,
        castle: ExternalRuntimeCastle,
    ): RuntimeState {
        audio.cue(AudioCue.Teleport)
        screen.writeText(
            castle.text.teleportVortex.renderCastle(screen.textColumns) + castle.text.teleportOutside.renderCastle(screen.textColumns),
        )
        return runtime.asWildernessCastle()
    }

    private fun giveStairItem(
        runtime: RuntimeState,
        castle: ExternalRuntimeCastle,
    ): RuntimeState {
        val lines = castle.text.giveMenu.renderCastle(screen.textColumns)
        screen.writeText(lines)
        val digits = castle.stairs.items.mapTo(mutableSetOf()) { stair -> stair.digit } + 5
        val digit = input.readValidDigitChoice(screen, lines, digits, audio = audio) ?: return runtime
        if (digit == 5) return runtime
        val item = castle.stairs.items.firstOrNull { stair -> stair.digit == digit } ?: return runtime
        val milestone = content.ref(CompiledHandleKind.Milestone, item.milestone)
        if (milestone !in runtime.party.milestones) {
            screen.writeText(item.reject.renderCastle(screen.textColumns))
            return runtime
        }
        val state = runtime.castle ?: return runtime
        val nextFloor = state.floor + 1
        val nextParty =
            runtime.party.copy(
                milestones = runtime.party.milestones - milestone,
                castleFloorsOpened = runtime.party.castleFloorsOpened + (castle.id to state.floor),
            )
        val next =
            runtime
                .withParty(
                    nextParty,
                ).copy(
                    castle =
                        state.copy(
                            floor = nextFloor,
                            point = GridPoint(1, 1),
                            visited = setOf(GridPoint(1, 1)),
                            roomEntryPending = true,
                        ),
                )
        if (nextFloor > castle.stairs.floorCount) return completeCastle(next, castle)
        screen.writeText(castle.text.stairsGuardianGrateful.renderCastle(screen.textColumns))
        return next
    }

    private fun completeCastle(
        runtime: RuntimeState,
        castle: ExternalRuntimeCastle,
    ): RuntimeState {
        val scripted = castle.scripts.completed?.let { scene -> runScene(runtime, scene) }
        if (scripted != null) return scripted.finishCastleRuntime()
        val milestone = castle.completionMilestone?.let { content.ref(CompiledHandleKind.Milestone, it) }
        val party = milestone?.let { runtime.party.copy(milestones = (runtime.party.milestones + it).distinct()) } ?: runtime.party
        return runtime.withParty(party).asWildernessCastle()
    }

    private fun showNavigationHelp(castle: ExternalRuntimeCastle) {
        val lines =
            systems.commands.getValue(RuntimeCommandGroupId.castleNavigation(castle.id)).map { command ->
                command.runtimeMnemonicLine()
            }
        screen.writeText(lines.withPausePrompt(PausePrompts(systems.prompts)))
        input.readCommand()
    }

    private fun showCastlePageViews(
        runtime: RuntimeState,
        castle: ExternalRuntimeCastle,
    ) {
        if (
            PartyPages.showPartyPageViews(
                content = content,
                systems = systems,
                input = input,
                screen = screen,
                runtime = runtime,
            )
        ) {
            redrawCastleIfCurrent(runtime, castle)
        }
    }

    private fun redrawCastleIfCurrent(
        runtime: RuntimeState,
        castle: ExternalRuntimeCastle,
    ) {
        if (runtime.currentMode == RuntimeMode.Castle) screen.renderCastle(runtime, castle)
    }

    private fun saveRuntime(runtime: RuntimeState) {
        saveRuntimeForPlayer(runtime, content, systems, save, screen, log)
    }

    private fun seedRooms(
        runtime: RuntimeState,
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

    private fun RuntimeState.rollTreasure(castle: ExternalRuntimeCastle): CastleTreasureRoll {
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

    private fun RuntimeState.randomTrapText(castle: ExternalRuntimeCastle): CastleTrapText {
        if (castle.pitfall.trapTypes.isEmpty()) return CastleTrapText(this, emptyList())
        val draw = party.drawRandom(0, castle.pitfall.trapTypes.lastIndex)
        return CastleTrapText(withParty(draw.party), castle.pitfall.trapTypes[draw.value].renderCastle(screen.textColumns))
    }

    private fun runScene(
        runtime: RuntimeState,
        sceneSlug: String,
    ): RuntimeState {
        val program = sceneProgram(sceneSlug) ?: return runtime
        return ScriptExecutor(content, systems, input, screen, runtime, log = log).execute(program)
    }

    private fun sceneProgram(sceneSlug: String): CompiledScriptProgram? =
        content.scripts.roots.firstOrNull { root -> root.root.kind.name == "Scene" && root.root.slug == sceneSlug }

    private fun itemName(item: CompiledHandleRef): String =
        content.items
            .firstOrNull { record -> record.id == item }
            ?.let { record -> content.strings.value(record.displayNameString) }
            ?: content.slug(item)
}

internal fun RuntimeState.asWildernessCastle(): RuntimeState =
    copy(currentMode = RuntimeMode.Wilderness, currentTown = null, castle = null, dungeon = null)

private fun RuntimeState.finishCastleRuntime(): RuntimeState = if (currentMode == RuntimeMode.GameOver) this else asWildernessCastle()

private fun RuntimeState.clearRoomEntry(): RuntimeState = copy(castle = castle?.copy(roomEntryPending = false))

private fun RuntimeState.clearCurrentRoom(): RuntimeState {
    val state = castle ?: return this
    return copy(castle = state.copy(rooms = state.rooms + (state.point to CASTLE_ROOM_EMPTY)))
}

private fun rylween.compiled.CompiledPartyMember.withDamage(damage: Int): rylween.compiled.CompiledPartyMember {
    val nextHp = (hitPoints.current - damage).coerceAtLeast(0)
    return copy(hitPoints = hitPoints.copy(current = nextHp), dead = nextHp == 0)
}

private fun ExternalRuntimeTextBlock.renderCastle(wrapWidth: Int?): List<String> = render({ null }, wrapWidth)

private data class SeededCastleRooms(
    val runtime: RuntimeState,
    val rooms: Map<GridPoint, String>,
)

private data class CastleTreasureRoll(
    val runtime: RuntimeState,
    val item: CompiledHandleRef?,
)

private data class CastleTrapText(
    val runtime: RuntimeState,
    val lines: List<String>,
)

private const val CASTLE_ROOM_EMPTY = "Empty"
private const val CASTLE_ROOM_MONSTER = "Monster"
private const val CASTLE_ROOM_PITFALL = "Pitfall"
private const val CASTLE_ROOM_TREASURE = "Treasure"
