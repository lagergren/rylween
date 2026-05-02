package rylween.runtime.ydarg

import rylween.dsl.*
import rylween.runtime.*

enum class YdargSessionStatus {
    Entered,
    MissingKey,
    AlreadyCleared,
    Moved,
    Blocked,
    HelpShown,
    ViewParty,
    UnsupportedCommand,
    MonsterEncounter,
    Pitfall,
    TreasureFound,
    TreasureTaken,
    TreasureLeft,
    TreasureInventoryFull,
    Stairs,
    StairsItemGiven,
    StairsItemMissing,
    TeleportedOut,
    LeftStairs,
    ConfrontationStarted,
    Victory,
    PartyDefeated,
    Finished,
}

data class YdargSessionResult(
    val status: YdargSessionStatus,
    val floor: Int? = null,
    val point: GridPoint? = null,
    val item: ItemId? = null,
    val milestone: GameMilestoneId? = null,
    val monster: MonsterId? = null,
)

enum class YdargCommand {
    Help,
    ViewParty,
    RemoveArmor,
    RemoveWeapon,
    Drop,
    ReadyWeapon,
    ReadyArmor,
    Quaff,
    CastSpell,
}

sealed interface YdargSessionInput {
    object Continue : YdargSessionInput
    data class Move(val direction: WildernessDirection) : YdargSessionInput
    data class Command(val command: YdargCommand) : YdargSessionInput
    object TakeTreasure : YdargSessionInput
    object LeaveTreasure : YdargSessionInput
    object TeleportOut : YdargSessionInput
    object GiveStairsItem : YdargSessionInput
    object LeaveStairs : YdargSessionInput
    data class StairItem(val digit: Int) : YdargSessionInput
}

sealed interface YdargBattleContinuation {
    object RoomMonster : YdargBattleContinuation
    data class ConfrontationSequence(val index: Int) : YdargBattleContinuation
    object Baron : YdargBattleContinuation
}

sealed interface YdargSessionStep {
    data class Messages(
        val blocks: List<TextBlock>,
        val next: YdargSessionStep,
        val image: ImageId? = null,
    ) : YdargSessionStep
    object RoomEntry : YdargSessionStep
    object Navigate : YdargSessionStep
    data class TreasureOffer(val item: ItemId) : YdargSessionStep
    object StairsMenu : YdargSessionStep
    object StairGive : YdargSessionStep
    data class Battle(
        val session: BattleSession,
        val continuation: YdargBattleContinuation,
    ) : YdargSessionStep
    data class Finished(val runtime: RuntimeSessionState) : YdargSessionStep
}

data class YdargSession(
    val runtime: RuntimeSessionState,
    val content: RuntimeContent,
    val ydarg: YdargContentModel,
    val rooms: Map<GridPoint, YdargRoomEvent>,
    val floor: Int,
    val point: GridPoint,
    val step: YdargSessionStep,
    val lastResult: YdargSessionResult? = null,
) {
    val party: PartyState get() = runtime.activeParty

    fun apply(input: YdargSessionInput): YdargSession =
        when (val current = step) {
            is YdargSessionStep.Messages -> {
                require(input is YdargSessionInput.Continue) { "Ydarg session is showing messages" }
                copy(
                    runtime = (current.next as? YdargSessionStep.Finished)?.runtime ?: runtime,
                    step = current.next,
                )
            }
            YdargSessionStep.RoomEntry ->
                error("Ydarg room entry is advanced by enterRoom()")
            YdargSessionStep.Navigate -> when (input) {
                is YdargSessionInput.Move -> move(input.direction)
                is YdargSessionInput.Command -> command(input.command)
                else -> error("Ydarg navigation cannot apply $input")
            }
            is YdargSessionStep.TreasureOffer -> when (input) {
                YdargSessionInput.TakeTreasure -> takeTreasure(current.item)
                YdargSessionInput.LeaveTreasure -> copy(
                    step = YdargSessionStep.Navigate,
                    lastResult = YdargSessionResult(YdargSessionStatus.TreasureLeft, floor, point, current.item),
                )
                else -> error("Ydarg treasure room cannot apply $input")
            }
            YdargSessionStep.StairsMenu -> when (input) {
                YdargSessionInput.TeleportOut -> teleportOut()
                YdargSessionInput.GiveStairsItem -> copy(step = YdargSessionStep.StairGive)
                YdargSessionInput.LeaveStairs -> leaveStairs()
                else -> error("Ydarg stairs cannot apply $input")
            }
            YdargSessionStep.StairGive -> {
                val digit = (input as? YdargSessionInput.StairItem)
                    ?: error("Ydarg stairs are waiting for an item digit")
                giveStairItem(digit.digit)
            }
            is YdargSessionStep.Battle ->
                error("Ydarg battle is advanced by replaceBattleSession()")
            is YdargSessionStep.Finished ->
                error("Ydarg session is already finished: $current")
        }

    fun enterRoom(): YdargSession {
        require(step is YdargSessionStep.RoomEntry) { "Ydarg session is not entering a room" }
        if (point.x == ydarg.mapSize && point.y == ydarg.mapSize) {
            return copy(
                step = YdargSessionStep.Messages(
                    listOf(ydarg.text.stairsIntro, ydarg.text.stairsMageOffer),
                    YdargSessionStep.StairsMenu,
                ),
                lastResult = YdargSessionResult(YdargSessionStatus.Stairs, floor, point),
            )
        }
        return when (rooms[point] ?: YdargRoomEvent.Empty) {
            YdargRoomEvent.Empty -> copy(step = YdargSessionStep.Navigate)
            YdargRoomEvent.Monster -> startRoomMonster()
            YdargRoomEvent.Pitfall -> applyPitfall()
            YdargRoomEvent.Treasure -> offerTreasure()
        }
    }

    fun replaceBattleSession(session: BattleSession): YdargSession {
        val current = step as? YdargSessionStep.Battle
            ?: error("Ydarg session is not running a battle")
        if (session.step !is BattleSessionStep.Finished) {
            return copy(runtime = session.runtime, step = current.copy(session = session))
        }
        if (session.runtime.realm.currentMode == RuntimeMode.GameOver) {
            return copy(
                runtime = session.runtime,
                step = YdargSessionStep.Finished(session.runtime),
                lastResult = YdargSessionResult(YdargSessionStatus.PartyDefeated, floor, point, monster = session.encounter.monster.id),
            )
        }
        val nextRuntime = session.runtime.asYdarg()
        return when (val continuation = current.continuation) {
            YdargBattleContinuation.RoomMonster ->
                copy(
                    runtime = nextRuntime,
                    step = YdargSessionStep.Navigate,
                    lastResult = YdargSessionResult(
                        YdargSessionStatus.MonsterEncounter,
                        floor,
                        point,
                        monster = session.encounter.monster.id,
                    ),
                )
            is YdargBattleContinuation.ConfrontationSequence ->
                continueConfrontationSequence(nextRuntime, continuation.index)
            YdargBattleContinuation.Baron ->
                finishConfrontation(nextRuntime)
        }
    }

    fun navigationBlocks(): List<TextBlock> =
        listOf(
            inlineText("You are at ${point.x}x${point.y}.", ""),
            inlineText("You are on level $floor."),
            inlineText(floorName(floor), ""),
            compassBlock(),
        )

    fun treasureOfferBlock(item: ItemId): TextBlock {
        val itemName = content.itemRecords.firstOrNull { it.id == item }?.displayName ?: item.slug
        return combineTextBlocks(inlineText("You find a", "$itemName.", ""), ydarg.text.treasureMenu)
    }

    private fun startRoomMonster(): YdargSession {
        val cleared = clearCurrentRoom()
        val battle = BattleSession.start(
            runtime = cleared.runtime,
            tier = ydarg.monsterTier,
            content = content,
        )
        return cleared.copy(
            runtime = battle.runtime,
            step = YdargSessionStep.Messages(
                blocks = listOf(ydarg.text.roomMonster),
                next = YdargSessionStep.Battle(battle, YdargBattleContinuation.RoomMonster),
            ),
            lastResult = YdargSessionResult(YdargSessionStatus.MonsterEncounter, floor, point, monster = battle.encounter.monster.id),
        )
    }

    private fun applyPitfall(): YdargSession {
        var session = clearCurrentRoom()
        val victimDraw = session.runtime.drawRandom(0, (session.runtime.realm.partyMemberMax - 1).coerceAtLeast(0))
        session = session.copy(runtime = victimDraw.runtime)
        val victim = session.party.members.getOrNull(victimDraw.value)?.name ?: "Someone"
        val texts = buildList {
            add(inlineText("$victim springs a", "trap!!!"))
        }.toMutableList()

        val thiefIndex = session.party.members.indexOfFirst { !it.dead && it.pcClass == PcClass.Thief }
        var disarmed = false
        if (thiefIndex >= 0) {
            val thief = session.party.members[thiefIndex]
            texts += combineTextBlocks(inlineText("${thief.name} "), ydarg.text.trapTriesDisarmSuffix)
            val chance = session.runtime.drawRandom(0, 99)
            session = session.copy(runtime = chance.runtime)
            disarmed = chance.value < thief.wisdom.coerceIn(0, 100)
            texts += if (disarmed) ydarg.text.trapDisarmSucceed else ydarg.text.trapDisarmFail
        }

        if (!disarmed) {
            val traps = ydarg.pitfall.trapTypes
            val trapDraw = session.runtime.drawRandom(0, traps.lastIndex)
            session = session.copy(runtime = trapDraw.runtime)
            texts += traps[trapDraw.value]
            session = session.copy(runtime = session.runtime.withActiveParty(session.party.applyPitfallDamage()))
        }

        return session.copy(
            step = YdargSessionStep.Messages(texts, YdargSessionStep.Navigate),
            lastResult = YdargSessionResult(YdargSessionStatus.Pitfall, floor, point),
        )
    }

    private fun offerTreasure(): YdargSession {
        val cleared = clearCurrentRoom()
        val roll = cleared.rollTreasure()
        return cleared.copy(
            runtime = roll.runtime,
            step = YdargSessionStep.TreasureOffer(roll.item),
            lastResult = YdargSessionResult(YdargSessionStatus.TreasureFound, floor, point, item = roll.item),
        )
    }

    private fun takeTreasure(item: ItemId): YdargSession =
        when (val placed = party.placeInventoryItem(
            item = item,
            overflow = RewardOverflowPolicy.SkipIfInventoryFull,
            itemRecords = content.itemRecords,
            realmContent = content.realm,
        )) {
            is InventoryPlacementResult.Placed ->
                copy(
                    runtime = runtime.withActiveParty(placed.party),
                    step = YdargSessionStep.Messages(listOf(ydarg.text.treasureTaken), YdargSessionStep.Navigate),
                    lastResult = YdargSessionResult(YdargSessionStatus.TreasureTaken, floor, point, item = item),
                )
            is InventoryPlacementResult.Full,
            is InventoryPlacementResult.NeedsDropSlot,
            is InventoryPlacementResult.InvalidDropSlot ->
                copy(
                    step = YdargSessionStep.Messages(listOf(ydarg.text.treasureFull), YdargSessionStep.Navigate),
                    lastResult = YdargSessionResult(YdargSessionStatus.TreasureInventoryFull, floor, point, item = item),
                )
        }

    private fun move(direction: WildernessDirection): YdargSession {
        val next = point + direction.delta()
        if (next.x !in 1..ydarg.mapSize || next.y !in 1..ydarg.mapSize) {
            return copy(lastResult = YdargSessionResult(YdargSessionStatus.Blocked, floor, point))
        }
        return copy(
            point = next,
            step = YdargSessionStep.Messages(listOf(directionText(direction)), YdargSessionStep.RoomEntry),
            lastResult = YdargSessionResult(YdargSessionStatus.Moved, floor, next),
        )
    }

    private fun command(command: YdargCommand): YdargSession =
        when (command) {
            YdargCommand.Help ->
                copy(
                    step = YdargSessionStep.Messages(listOf(ydarg.text.commandHelp), YdargSessionStep.Navigate),
                    lastResult = YdargSessionResult(YdargSessionStatus.HelpShown, floor, point),
                )
            YdargCommand.ViewParty ->
                copy(
                    step = YdargSessionStep.Messages(listOf(partyOverview()), YdargSessionStep.Navigate),
                    lastResult = YdargSessionResult(YdargSessionStatus.ViewParty, floor, point),
                )
            YdargCommand.RemoveArmor,
            YdargCommand.RemoveWeapon,
            YdargCommand.Drop,
            YdargCommand.ReadyWeapon,
            YdargCommand.ReadyArmor,
            YdargCommand.Quaff,
            YdargCommand.CastSpell ->
                copy(
                    step = YdargSessionStep.Messages(listOf(ydarg.text.unsupportedCommand), YdargSessionStep.Navigate),
                    lastResult = YdargSessionResult(YdargSessionStatus.UnsupportedCommand, floor, point),
                )
        }

    private fun leaveStairs(): YdargSession =
        copy(
            point = point.copy(y = (point.y - 1).coerceAtLeast(1)),
            step = YdargSessionStep.Messages(listOf(ydarg.text.stairsLeave), YdargSessionStep.Navigate),
            lastResult = YdargSessionResult(YdargSessionStatus.LeftStairs, floor, point),
        )

    private fun teleportOut(): YdargSession {
        val finished = runtime.asWilderness()
        return copy(
            runtime = finished,
            step = YdargSessionStep.Messages(
                blocks = listOf(ydarg.text.teleportVortex, ydarg.text.teleportOutside),
                next = YdargSessionStep.Finished(finished),
            ),
            lastResult = YdargSessionResult(YdargSessionStatus.TeleportedOut, floor, point),
        )
    }

    private fun giveStairItem(digit: Int): YdargSession {
        val item = ydarg.stairs.items.firstOrNull { it.digit == digit }
        if (item == null) {
            return copy(step = YdargSessionStep.StairsMenu)
        }
        if (item.milestone !in party.milestones) {
            return copy(
                step = YdargSessionStep.Messages(listOf(item.reject), YdargSessionStep.StairsMenu),
                lastResult = YdargSessionResult(
                    YdargSessionStatus.StairsItemMissing,
                    floor,
                    point,
                    milestone = item.milestone,
                ),
            )
        }

        val nextFloor = floor + 1
        val nextRuntime = runtime.withActiveParty(party.withoutMilestone(item.milestone))
        val moved = copy(
            runtime = nextRuntime,
            floor = nextFloor,
            point = GridPoint(1, 1),
            lastResult = YdargSessionResult(
                YdargSessionStatus.StairsItemGiven,
                floor,
                point,
                milestone = item.milestone,
            ),
        )
        return if (nextFloor > ydarg.stairs.floorCount) {
            moved.startConfrontation()
        } else {
            moved.copy(step = YdargSessionStep.Messages(listOf(ydarg.text.mageGrateful), YdargSessionStep.RoomEntry))
        }
    }

    private fun startConfrontation(): YdargSession {
        val battle = startConfrontationBattle(runtime, 0)
        return copy(
            runtime = battle.runtime,
            step = YdargSessionStep.Messages(
                blocks = ydarg.text.confrontationAct1,
                next = YdargSessionStep.Battle(battle, YdargBattleContinuation.ConfrontationSequence(0)),
            ),
            lastResult = YdargSessionResult(
                YdargSessionStatus.ConfrontationStarted,
                floor,
                point,
                monster = battle.encounter.monster.id,
            ),
        )
    }

    private fun continueConfrontationSequence(runtime: RuntimeSessionState, completedIndex: Int): YdargSession {
        val nextIndex = completedIndex + 1
        if (nextIndex < ydarg.confrontation.sequence.size) {
            val battle = startConfrontationBattle(runtime, nextIndex)
            return copy(
                runtime = battle.runtime,
                step = YdargSessionStep.Battle(battle, YdargBattleContinuation.ConfrontationSequence(nextIndex)),
                lastResult = YdargSessionResult(
                    YdargSessionStatus.ConfrontationStarted,
                    floor,
                    point,
                    monster = battle.encounter.monster.id,
                ),
            )
        }
        val baron = requireNotNull(ydarg.confrontation.baron) { "Ydarg confrontation baron is missing" }
        val battle = startMonsterBattle(runtime, baron, mandatory = true)
        return copy(
            runtime = battle.runtime,
            step = YdargSessionStep.Messages(
                blocks = ydarg.text.confrontationAct2,
                next = YdargSessionStep.Battle(battle, YdargBattleContinuation.Baron),
            ),
            lastResult = YdargSessionResult(
                YdargSessionStatus.ConfrontationStarted,
                floor,
                point,
                monster = battle.encounter.monster.id,
            ),
        )
    }

    private fun finishConfrontation(runtime: RuntimeSessionState): YdargSession {
        val completed = runtime.withActiveParty(runtime.activeParty.withMilestone(ydarg.victoryMilestone))
        val finished = completed.asWilderness()
        return copy(
            runtime = completed,
            step = YdargSessionStep.Messages(
                blocks = ydarg.text.confrontationAct3,
                next = YdargSessionStep.Messages(
                    blocks = ydarg.text.confrontationAct4,
                    next = YdargSessionStep.Finished(finished),
                ),
            ),
            lastResult = YdargSessionResult(YdargSessionStatus.Victory, floor, point),
        )
    }

    private fun startConfrontationBattle(runtime: RuntimeSessionState, index: Int): BattleSession =
        startMonsterBattle(runtime, ydarg.confrontation.sequence[index], mandatory = true)

    private fun startMonsterBattle(runtime: RuntimeSessionState, monsterId: MonsterId, mandatory: Boolean): BattleSession {
        val monster = content.monsters.firstOrNull { it.id == monsterId }
            ?: error("Ydarg references missing monster ${monsterId.slug}")
        return BattleSession.startMonster(
            runtime = runtime,
            monster = monster,
            content = content,
            mandatory = mandatory,
        )
    }

    private fun clearCurrentRoom(): YdargSession =
        copy(rooms = rooms + (point to YdargRoomEvent.Empty))

    private fun rollTreasure(): TreasureRoll {
        val gate = runtime.drawRandom(0, ydarg.treasure.gateMax)
        if (gate.value == 0) {
            val item = gate.runtime.drawRandom(0, ydarg.treasure.magic.lastIndex)
            return TreasureRoll(item.runtime, ydarg.treasure.magic[item.value])
        }
        val kind = gate.runtime.drawRandom(0, ydarg.treasure.regularKinds.lastIndex)
        val table = ydarg.treasure.regularKinds[kind.value]
        val item = kind.runtime.drawRandom(0, table.lastIndex)
        return TreasureRoll(item.runtime, table[item.value])
    }

    private fun PartyState.applyPitfallDamage(): PartyState =
        copy(
            members = members.map { member ->
                if (member.dead) {
                    member
                } else {
                    val damage = member.hitPoints.current / ydarg.pitfall.damageDivisor
                    val nextHp = (member.hitPoints.current - damage).coerceAtLeast(0)
                    member.copy(
                        hitPoints = nextHp of member.hitPoints.max,
                        dead = member.dead || nextHp == 0,
                    )
                }
            },
        )

    private fun partyOverview(): TextBlock =
        inlineText(
            *party.members.flatMapIndexed { index, member ->
                listOf(
                    "${index + 1}. ${member.name}",
                    "HP ${member.hitPoints.current}/${member.hitPoints.max}  ${if (member.dead) "Dead" else member.pcClass.name}",
                )
            }.plus("").toTypedArray(),
        )

    private fun compassBlock(): TextBlock {
        val north = if (point.y > 1) "    8  North  " else ""
        val northStem = if (point.y > 1) "    |" else ""
        val eastWest = when {
            point.x > 1 && point.x < ydarg.mapSize -> " 4 -+- 6  East/West"
            point.x < ydarg.mapSize -> "    +- 6  East"
            point.x > 1 -> " 4 -+     West"
            else -> ""
        }
        val south = if (point.y < ydarg.mapSize) listOf("    |", "    2  South  ") else emptyList()
        return inlineText(*(listOf(north, northStem, eastWest) + south).toTypedArray())
    }

    private fun directionText(direction: WildernessDirection): TextBlock =
        inlineText(
            when (direction) {
                WildernessDirection.North -> "North."
                WildernessDirection.South -> "South."
                WildernessDirection.East -> "East."
                WildernessDirection.West -> "West."
            },
        )

    private fun floorName(floor: Int): String =
        when (floor) {
            1 -> "(Ground Level)"
            2 -> "(First Floor)"
            3 -> "(Base of Tower)"
            4 -> "(Top of Tower)"
            else -> "(Top of Tower)"
        }

    private data class TreasureRoll(
        val runtime: RuntimeSessionState,
        val item: ItemId,
    )

    companion object {
        fun start(runtime: RuntimeSessionState, content: RuntimeContent): YdargSession {
            val ydarg = requireNotNull(content.ydarg) { "Ydarg content is not configured" }
            val startRuntime = runtime.asYdarg()
            val base = YdargSession(
                runtime = startRuntime,
                content = content,
                ydarg = ydarg,
                rooms = emptyMap(),
                floor = 1,
                point = GridPoint(1, 1),
                step = YdargSessionStep.RoomEntry,
            )

            if (ydarg.victoryMilestone in startRuntime.activeParty.milestones) {
                val finished = startRuntime.asWilderness()
                return base.copy(
                    runtime = finished,
                    step = YdargSessionStep.Messages(
                        blocks = listOf(ydarg.text.ruin),
                        next = YdargSessionStep.Finished(finished),
                        image = ydarg.image,
                    ),
                    lastResult = YdargSessionResult(YdargSessionStatus.AlreadyCleared),
                )
            }

            if (ydarg.requiredKey !in startRuntime.activeParty.milestones) {
                val finished = startRuntime.asWilderness()
                return base.copy(
                    runtime = finished,
                    step = YdargSessionStep.Messages(
                        blocks = listOf(ydarg.text.approach, ydarg.text.jaaraForm, ydarg.text.jaaraSpeech, ydarg.text.noKey),
                        next = YdargSessionStep.Finished(finished),
                        image = ydarg.image,
                    ),
                    lastResult = YdargSessionResult(YdargSessionStatus.MissingKey),
                )
            }

            val seeded = base.seedRooms()
            return base.copy(
                runtime = seeded.runtime,
                rooms = seeded.rooms,
                step = YdargSessionStep.Messages(
                    blocks = listOf(
                        ydarg.text.approach,
                        ydarg.text.jaaraForm,
                        ydarg.text.jaaraSpeech,
                        ydarg.text.keyAccepted,
                        ydarg.text.enter,
                        ydarg.text.doorSlams,
                    ),
                    next = YdargSessionStep.RoomEntry,
                    image = ydarg.image,
                ),
                lastResult = YdargSessionResult(YdargSessionStatus.Entered, floor = 1, point = GridPoint(1, 1)),
            )
        }
    }

    private fun seedRooms(): SeededRooms {
        var nextRuntime = runtime
        val generated = buildMap {
            (1..ydarg.mapSize).forEach { x ->
                (1..ydarg.mapSize).forEach { y ->
                    val draw = nextRuntime.drawRandom(1, ydarg.roomTypeMax)
                    nextRuntime = draw.runtime
                    put(GridPoint(x, y), ydarg.roomEvents[draw.value] ?: YdargRoomEvent.Empty)
                }
            }
            put(GridPoint(ydarg.mapSize, ydarg.mapSize), YdargRoomEvent.Empty)
        }
        return SeededRooms(nextRuntime, generated)
    }

    private data class SeededRooms(
        val runtime: RuntimeSessionState,
        val rooms: Map<GridPoint, YdargRoomEvent>,
    )
}

private fun RuntimeSessionState.asYdarg(): RuntimeSessionState =
    withRealm(
        realm.copy(
            currentMode = RuntimeMode.YdargCastle,
            currentTown = null,
        ),
    )

private fun RuntimeSessionState.asWilderness(): RuntimeSessionState =
    withRealm(
        realm.copy(
            currentMode = RuntimeMode.Wilderness,
            currentTown = null,
        ),
    )
