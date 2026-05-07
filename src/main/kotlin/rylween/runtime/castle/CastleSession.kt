/**
 * Backend-neutral runtime state machine for authored grid castles.
 *
 * The session advances castle-room events, handles nested battle/party flows,
 * and serializes through `RuntimeSnapshotModel.kt`; terminal rendering is kept
 * in `frontend/terminal/CastleSessionTerminalAdapter.kt`.
 */
package rylween.runtime.castle

import rylween.dsl.CastleContentModel
import rylween.dsl.CastleRoomEvent
import rylween.dsl.EquipmentCommand
import rylween.dsl.GameMilestoneId
import rylween.dsl.GridPoint
import rylween.dsl.ImageId
import rylween.dsl.ItemId
import rylween.dsl.ItemKind
import rylween.dsl.MonsterId
import rylween.dsl.PartyState
import rylween.dsl.PcClass
import rylween.dsl.RewardOverflowPolicy
import rylween.dsl.RuntimeContent
import rylween.dsl.RuntimeMode
import rylween.dsl.SceneId
import rylween.dsl.SceneModel
import rylween.dsl.TextBlock
import rylween.dsl.Wilderness
import rylween.dsl.chance
import rylween.dsl.combineTextBlocks
import rylween.dsl.inlineText
import rylween.dsl.item
import rylween.dsl.milestone
import rylween.dsl.monster
import rylween.dsl.of
import rylween.dsl.text
import rylween.runtime.CastleSavedStep
import rylween.runtime.CastleSessionSnapshot
import rylween.runtime.EquipmentPromptSession
import rylween.runtime.EquipmentPromptStep
import rylween.runtime.InventoryPlacementResult
import rylween.runtime.RuntimeModeSnapshot
import rylween.runtime.RuntimeSessionState
import rylween.runtime.ScriptContinuation
import rylween.runtime.ScriptRootRef
import rylween.runtime.WildernessDirection
import rylween.runtime.battle.BattleSession
import rylween.runtime.battle.BattleSessionStep
import rylween.runtime.delta
import rylween.runtime.drawRandom
import rylween.runtime.party
import rylween.runtime.party.InventoryDropSession
import rylween.runtime.party.InventoryDropStep
import rylween.runtime.party.InventoryItemUseSession
import rylween.runtime.party.InventoryItemUseStep
import rylween.runtime.party.PartyViewSession
import rylween.runtime.party.PartyViewStep
import rylween.runtime.party.PriestSpellPlace
import rylween.runtime.party.PriestSpellSession
import rylween.runtime.party.PriestSpellStep
import rylween.runtime.party.toParentStatus
import rylween.runtime.placeInventoryItem
import rylween.runtime.plus
import rylween.runtime.toContinuation
import rylween.runtime.toParentStatus
import rylween.runtime.toSnapshot
import rylween.runtime.withMilestone
import rylween.runtime.withoutMilestone

/** Stable result tokens describing the last castle transition. */
enum class CastleSessionStatus {
    Entered,
    EntryBlocked,
    AlreadyCompleted,
    Moved,
    Blocked,
    SaveRequested,
    Saved,
    SaveFailed,
    HelpShown,
    ViewPartyRequested,
    ViewPartyShown,
    DropRequested,
    DropUsed,
    EquipmentRequested,
    EquipmentUsed,
    EquipmentUnavailable,
    InventoryRequested,
    InventoryUsed,
    InventoryUnavailable,
    CastSpellRequested,
    CastSpellUsed,
    CastSpellUnavailable,
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
    CompletionStarted,
    Completed,
    PartyDefeated,
    Finished,
}

/** Result metadata exposed after a Castle session transition. */
data class CastleSessionResult(
    val status: CastleSessionStatus,
    val floor: Int? = null,
    val point: GridPoint? = null,
    val item: ItemId? = null,
    val milestone: GameMilestoneId? = null,
    val monster: MonsterId? = null,
)

/** Menu-like commands available while navigating a castle. */
enum class CastleCommand {
    Save,
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

/** Inputs accepted by [CastleSession.apply]. */
sealed interface CastleSessionInput {
    object Continue : CastleSessionInput

    data class Move(
        val direction: WildernessDirection,
    ) : CastleSessionInput

    data class Command(
        val command: CastleCommand,
    ) : CastleSessionInput

    object TakeTreasure : CastleSessionInput

    object LeaveTreasure : CastleSessionInput

    object TeleportOut : CastleSessionInput

    object GiveStairsItem : CastleSessionInput

    object LeaveStairs : CastleSessionInput

    data class StairItem(
        val digit: Int,
    ) : CastleSessionInput
}

/** Continuation point after an authored castle hook script finishes. */
enum class CastleScriptContinuation { RoomEntry, Finished }

/** Current prompt, child session, or completion state requested by [CastleSession]. */
sealed interface CastleSessionStep {
    data class Messages(
        val blocks: List<TextBlock>,
        val next: CastleSessionStep,
        val image: ImageId? = null,
    ) : CastleSessionStep

    data class Save(
        val next: CastleSessionStep,
    ) : CastleSessionStep

    object RoomEntry : CastleSessionStep

    object Navigate : CastleSessionStep

    data class TreasureOffer(
        val item: ItemId,
    ) : CastleSessionStep

    object StairsMenu : CastleSessionStep

    object StairGive : CastleSessionStep

    data class Battle(
        val session: BattleSession,
    ) : CastleSessionStep

    data class Script(
        val scene: SceneModel,
        val continuation: ScriptContinuation,
        val next: CastleScriptContinuation,
    ) : CastleSessionStep

    data class PartyView(
        val session: PartyViewSession,
    ) : CastleSessionStep

    data class InventoryDrop(
        val session: InventoryDropSession,
    ) : CastleSessionStep

    data class Equipment(
        val session: EquipmentPromptSession,
    ) : CastleSessionStep

    data class InventoryItemUse(
        val session: InventoryItemUseSession,
    ) : CastleSessionStep

    data class PriestSpell(
        val session: PriestSpellSession,
    ) : CastleSessionStep

    data class Finished(
        val runtime: RuntimeSessionState,
    ) : CastleSessionStep
}

/** Pure castle session state plus transition functions. */
data class CastleSession(
    val runtime: RuntimeSessionState,
    val content: RuntimeContent,
    val castleContent: CastleContentModel,
    val rooms: Map<GridPoint, CastleRoomEvent>,
    val floor: Int,
    val point: GridPoint,
    val step: CastleSessionStep,
    val lastResult: CastleSessionResult? = null,
) {
    val party: PartyState get() = runtime.activeParty

    fun apply(input: CastleSessionInput): CastleSession =
        when (val current = step) {
            is CastleSessionStep.Messages -> {
                require(input is CastleSessionInput.Continue) { "Castle session is showing messages" }
                copy(
                    runtime = (current.next as? CastleSessionStep.Finished)?.runtime ?: runtime,
                    step = current.next,
                )
            }
            is CastleSessionStep.Save ->
                error("Castle save step is advanced by completeSave()")
            CastleSessionStep.RoomEntry ->
                error("Castle room entry is advanced by enterRoom()")
            CastleSessionStep.Navigate ->
                when (input) {
                    is CastleSessionInput.Move -> move(input.direction)
                    is CastleSessionInput.Command -> command(input.command)
                    else -> error("Castle navigation cannot apply $input")
                }
            is CastleSessionStep.TreasureOffer ->
                when (input) {
                    CastleSessionInput.TakeTreasure -> takeTreasure(current.item)
                    CastleSessionInput.LeaveTreasure ->
                        copy(
                            step = CastleSessionStep.Navigate,
                            lastResult = CastleSessionResult(CastleSessionStatus.TreasureLeft, floor, point, current.item),
                        )
                    else -> error("Castle treasure room cannot apply $input")
                }
            CastleSessionStep.StairsMenu ->
                when (input) {
                    CastleSessionInput.TeleportOut -> teleportOut()
                    CastleSessionInput.GiveStairsItem -> copy(step = CastleSessionStep.StairGive)
                    CastleSessionInput.LeaveStairs -> leaveStairs()
                    else -> error("Castle stairs cannot apply $input")
                }
            CastleSessionStep.StairGive -> {
                val digit =
                    (input as? CastleSessionInput.StairItem)
                        ?: error("Castle stairs are waiting for an item digit")
                giveStairItem(digit.digit)
            }
            is CastleSessionStep.Battle ->
                error("Castle battle is advanced by replaceBattleSession()")
            is CastleSessionStep.Script ->
                error("Castle script is advanced by finishScript()")
            is CastleSessionStep.PartyView,
            is CastleSessionStep.InventoryDrop,
            is CastleSessionStep.Equipment,
            is CastleSessionStep.InventoryItemUse,
            is CastleSessionStep.PriestSpell,
            ->
                error("Castle child session is advanced by its terminal adapter")
            is CastleSessionStep.Finished ->
                error("Castle session is already finished: $current")
        }

    fun enterRoom(): CastleSession {
        require(step is CastleSessionStep.RoomEntry) { "Castle session is not entering a room" }
        if (point.x == castleContent.mapSize && point.y == castleContent.mapSize) {
            return copy(
                step =
                    CastleSessionStep.Messages(
                        listOf(castleContent.text.stairsIntro, castleContent.text.stairsGuardianOffer),
                        CastleSessionStep.StairsMenu,
                    ),
                lastResult = CastleSessionResult(CastleSessionStatus.Stairs, floor, point),
            )
        }
        return when (rooms[point] ?: CastleRoomEvent.Empty) {
            CastleRoomEvent.Empty -> copy(step = CastleSessionStep.Navigate)
            CastleRoomEvent.Monster -> startRoomMonster()
            CastleRoomEvent.Pitfall -> applyPitfall()
            CastleRoomEvent.Treasure -> offerTreasure()
        }
    }

    fun replaceBattleSession(session: BattleSession): CastleSession {
        val current =
            step as? CastleSessionStep.Battle
                ?: error("Castle session is not running a battle")
        if (session.step !is BattleSessionStep.Finished) {
            return copy(runtime = session.runtime, step = current.copy(session = session))
        }
        if (session.runtime.realm.currentMode == RuntimeMode.GameOver) {
            return copy(
                runtime = session.runtime,
                step = CastleSessionStep.Finished(session.runtime),
                lastResult = CastleSessionResult(CastleSessionStatus.PartyDefeated, floor, point, monster = session.encounter.monster.id),
            )
        }
        val nextRuntime = session.runtime.asCastle()
        return copy(
            runtime = nextRuntime,
            step = CastleSessionStep.Navigate,
            lastResult =
                CastleSessionResult(
                    CastleSessionStatus.MonsterEncounter,
                    floor,
                    point,
                    monster = session.encounter.monster.id,
                ),
        )
    }

    fun replaceScriptContinuation(continuation: ScriptContinuation): CastleSession {
        val current =
            step as? CastleSessionStep.Script
                ?: error("Castle session is not running a script")
        return copy(runtime = continuation.runtime, step = current.copy(continuation = continuation))
    }

    fun finishScript(runtime: RuntimeSessionState): CastleSession {
        val current =
            step as? CastleSessionStep.Script
                ?: error("Castle session is not running a script")
        if (runtime.realm.currentMode == RuntimeMode.GameOver) {
            return copy(
                runtime = runtime,
                step = CastleSessionStep.Finished(runtime),
                lastResult = CastleSessionResult(CastleSessionStatus.PartyDefeated, floor, point),
            )
        }
        return when (current.next) {
            CastleScriptContinuation.RoomEntry ->
                copy(runtime = runtime.asCastle(), step = CastleSessionStep.RoomEntry)
            CastleScriptContinuation.Finished -> {
                val finished = runtime.asWilderness()
                val result =
                    if (lastResult?.status == CastleSessionStatus.CompletionStarted) {
                        CastleSessionResult(CastleSessionStatus.Completed, floor, point)
                    } else {
                        lastResult
                    }
                copy(runtime = finished, step = CastleSessionStep.Finished(finished), lastResult = result)
            }
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
        return combineTextBlocks(inlineText("You find a", "$itemName.", ""), castleContent.text.treasureMenu)
    }

    private fun startRoomMonster(): CastleSession {
        val cleared = clearCurrentRoom()
        val battle =
            BattleSession.start(
                runtime = cleared.runtime,
                tier = castleContent.monsterTier,
                content = content,
            )
        return cleared.copy(
            runtime = battle.runtime,
            step =
                CastleSessionStep.Messages(
                    blocks = listOf(castleContent.text.roomMonster),
                    next = CastleSessionStep.Battle(battle),
                ),
            lastResult = CastleSessionResult(CastleSessionStatus.MonsterEncounter, floor, point, monster = battle.encounter.monster.id),
        )
    }

    private fun applyPitfall(): CastleSession {
        var session = clearCurrentRoom()
        val victimDraw = session.runtime.drawRandom(0, (session.runtime.realm.partyMemberMax - 1).coerceAtLeast(0))
        session = session.copy(runtime = victimDraw.runtime)
        val victim =
            session.party.members
                .getOrNull(victimDraw.value)
                ?.name ?: "Someone"
        val texts =
            buildList {
                add(inlineText("$victim springs a", "trap!!!"))
            }.toMutableList()

        val thiefIndex = session.party.members.indexOfFirst { !it.dead && it.pcClass == PcClass.Thief }
        var disarmed = false
        if (thiefIndex >= 0) {
            val thief = session.party.members[thiefIndex]
            texts += combineTextBlocks(inlineText("${thief.name} "), castleContent.text.trapTriesDisarmSuffix)
            val chance = session.runtime.drawRandom(0, 99)
            session = session.copy(runtime = chance.runtime)
            disarmed = chance.value < thief.wisdom.coerceIn(0, 100)
            texts += if (disarmed) castleContent.text.trapDisarmSucceed else castleContent.text.trapDisarmFail
        }

        if (!disarmed) {
            val traps = castleContent.pitfall.trapTypes
            val trapDraw = session.runtime.drawRandom(0, traps.lastIndex)
            session = session.copy(runtime = trapDraw.runtime)
            texts += traps[trapDraw.value]
            session = session.copy(runtime = session.runtime.withActiveParty(session.party.applyPitfallDamage()))
        }

        return session.copy(
            step = CastleSessionStep.Messages(texts, CastleSessionStep.Navigate),
            lastResult = CastleSessionResult(CastleSessionStatus.Pitfall, floor, point),
        )
    }

    private fun offerTreasure(): CastleSession {
        val cleared = clearCurrentRoom()
        val roll = cleared.rollTreasure()
        return cleared.copy(
            runtime = roll.runtime,
            step = CastleSessionStep.TreasureOffer(roll.item),
            lastResult = CastleSessionResult(CastleSessionStatus.TreasureFound, floor, point, item = roll.item),
        )
    }

    private fun takeTreasure(item: ItemId): CastleSession =
        when (
            val placed =
                party.placeInventoryItem(
                    item = item,
                    overflow = RewardOverflowPolicy.SkipIfInventoryFull,
                    itemRecords = content.itemRecords,
                    realmContent = content.realm,
                )
        ) {
            is InventoryPlacementResult.Placed ->
                copy(
                    runtime = runtime.withActiveParty(placed.party),
                    step = CastleSessionStep.Messages(listOf(castleContent.text.treasureTaken), CastleSessionStep.Navigate),
                    lastResult = CastleSessionResult(CastleSessionStatus.TreasureTaken, floor, point, item = item),
                )
            is InventoryPlacementResult.Full,
            is InventoryPlacementResult.NeedsDropSlot,
            is InventoryPlacementResult.InvalidDropSlot,
            ->
                copy(
                    step = CastleSessionStep.Messages(listOf(castleContent.text.treasureFull), CastleSessionStep.Navigate),
                    lastResult = CastleSessionResult(CastleSessionStatus.TreasureInventoryFull, floor, point, item = item),
                )
        }

    private fun move(direction: WildernessDirection): CastleSession {
        val next = point + direction.delta()
        if (next.x !in 1..castleContent.mapSize || next.y !in 1..castleContent.mapSize) {
            return copy(lastResult = CastleSessionResult(CastleSessionStatus.Blocked, floor, point))
        }
        return copy(
            point = next,
            step = CastleSessionStep.Messages(listOf(directionText(direction)), CastleSessionStep.RoomEntry),
            lastResult = CastleSessionResult(CastleSessionStatus.Moved, floor, next),
        )
    }

    private fun command(command: CastleCommand): CastleSession =
        when (command) {
            CastleCommand.Save ->
                copy(
                    step = CastleSessionStep.Save(CastleSessionStep.Navigate),
                    lastResult = CastleSessionResult(CastleSessionStatus.SaveRequested, floor, point),
                )
            CastleCommand.Help ->
                copy(
                    step = CastleSessionStep.Messages(castleContent.keys.navigationHelpPages(), CastleSessionStep.Navigate),
                    lastResult = CastleSessionResult(CastleSessionStatus.HelpShown, floor, point),
                )
            CastleCommand.ViewParty ->
                copy(
                    step =
                        CastleSessionStep.PartyView(
                            PartyViewSession(
                                party = party,
                                itemRecords = content.itemRecords,
                                progress = content.progress,
                                prompts = content.prompts.party,
                            ),
                        ),
                    lastResult = CastleSessionResult(CastleSessionStatus.ViewPartyRequested, floor, point),
                )
            CastleCommand.RemoveArmor -> equipment(EquipmentCommand.RemoveArmor)
            CastleCommand.RemoveWeapon -> equipment(EquipmentCommand.RemoveWeapon)
            CastleCommand.Drop -> drop()
            CastleCommand.ReadyWeapon -> equipment(EquipmentCommand.ReadyWeapon)
            CastleCommand.ReadyArmor -> equipment(EquipmentCommand.ReadyArmor)
            CastleCommand.Quaff -> quaff()
            CastleCommand.CastSpell -> castSpell()
        }

    fun completeSave(saved: Boolean): CastleSession {
        val current =
            step as? CastleSessionStep.Save
                ?: error("Castle session is not saving")
        return copy(
            step = current.next,
            lastResult =
                CastleSessionResult(
                    if (saved) CastleSessionStatus.Saved else CastleSessionStatus.SaveFailed,
                    floor,
                    point,
                ),
        )
    }

    fun replacePartyViewSession(session: PartyViewSession): CastleSession {
        require(step is CastleSessionStep.PartyView) { "Castle session is not running party view" }
        return if (session.step is PartyViewStep.Finished) {
            copy(
                step = CastleSessionStep.Navigate,
                lastResult = CastleSessionResult(CastleSessionStatus.ViewPartyShown, floor, point),
            )
        } else {
            copy(step = CastleSessionStep.PartyView(session))
        }
    }

    fun replaceInventoryDropSession(session: InventoryDropSession): CastleSession {
        require(step is CastleSessionStep.InventoryDrop) { "Castle session is not running inventory drop" }
        return if (session.step is InventoryDropStep.Finished) {
            val status =
                session.lastResult.status.toParentStatus(
                    dropped = CastleSessionStatus.DropUsed,
                    unavailable = CastleSessionStatus.InventoryUnavailable,
                )
            copy(
                runtime = runtime.withActiveParty(session.party),
                step = CastleSessionStep.Navigate,
                lastResult = CastleSessionResult(status, floor, point),
            )
        } else {
            copy(runtime = runtime.withActiveParty(session.party), step = CastleSessionStep.InventoryDrop(session))
        }
    }

    fun replaceEquipmentSession(session: EquipmentPromptSession): CastleSession {
        require(step is CastleSessionStep.Equipment) { "Castle session is not running equipment prompts" }
        val status =
            (session.step as? EquipmentPromptStep.Finished)?.result?.toParentStatus(
                used = CastleSessionStatus.EquipmentUsed,
                unavailable = CastleSessionStatus.EquipmentUnavailable,
            )
        return if (status != null) {
            copy(
                runtime = runtime.withActiveParty(session.party),
                step = CastleSessionStep.Navigate,
                lastResult = CastleSessionResult(status, floor, point),
            )
        } else {
            copy(runtime = runtime.withActiveParty(session.party), step = CastleSessionStep.Equipment(session))
        }
    }

    fun replaceInventoryItemUseSession(session: InventoryItemUseSession): CastleSession {
        require(step is CastleSessionStep.InventoryItemUse) { "Castle session is not running inventory item use" }
        return if (session.step is InventoryItemUseStep.Finished) {
            val status =
                session.lastResult?.status.toParentStatus(
                    used = CastleSessionStatus.InventoryUsed,
                    unavailable = CastleSessionStatus.InventoryUnavailable,
                )
            copy(
                runtime = session.runtime,
                step = CastleSessionStep.Navigate,
                lastResult = CastleSessionResult(status, floor, point),
            )
        } else {
            copy(runtime = session.runtime, step = CastleSessionStep.InventoryItemUse(session))
        }
    }

    fun replacePriestSpellSession(session: PriestSpellSession): CastleSession {
        require(step is CastleSessionStep.PriestSpell) { "Castle session is not running priest spell casting" }
        return if (session.step is PriestSpellStep.Finished) {
            val status =
                session.lastResult?.status.toParentStatus(
                    cast = CastleSessionStatus.CastSpellUsed,
                    unavailable = CastleSessionStatus.CastSpellUnavailable,
                )
            copy(
                runtime = session.runtime,
                step = CastleSessionStep.Navigate,
                lastResult = CastleSessionResult(status, floor, point),
            )
        } else {
            copy(runtime = session.runtime, step = CastleSessionStep.PriestSpell(session))
        }
    }

    private fun equipment(command: EquipmentCommand): CastleSession =
        copy(
            step =
                CastleSessionStep.Equipment(
                    EquipmentPromptSession.start(command, party, content.itemRecords, content.prompts.equipment),
                ),
            lastResult = CastleSessionResult(CastleSessionStatus.EquipmentRequested, floor, point),
        )

    private fun drop(): CastleSession =
        copy(
            step =
                CastleSessionStep.InventoryDrop(
                    InventoryDropSession(
                        party = party,
                        itemRecords = content.itemRecords,
                        realm = content.realm,
                        prompts = content.prompts.party,
                    ),
                ),
            lastResult = CastleSessionResult(CastleSessionStatus.DropRequested, floor, point),
        )

    private fun quaff(): CastleSession =
        copy(
            step =
                CastleSessionStep.InventoryItemUse(
                    InventoryItemUseSession.start(
                        runtime = runtime,
                        content = content,
                        allowedKinds = setOf(ItemKind.Potion),
                        choosePrompt = content.prompts.inventoryUse.quaff,
                    ),
                ),
            lastResult = CastleSessionResult(CastleSessionStatus.InventoryRequested, floor, point),
        )

    private fun castSpell(): CastleSession =
        copy(
            step =
                CastleSessionStep.PriestSpell(
                    PriestSpellSession.start(
                        runtime = runtime,
                        spells = content.spells,
                        place = PriestSpellPlace.Dungeon,
                        prompts = content.prompts.priest,
                    ),
                ),
            lastResult = CastleSessionResult(CastleSessionStatus.CastSpellRequested, floor, point),
        )

    private fun leaveStairs(): CastleSession =
        copy(
            point = point.copy(y = (point.y - 1).coerceAtLeast(1)),
            step = CastleSessionStep.Messages(listOf(castleContent.text.stairsLeave), CastleSessionStep.Navigate),
            lastResult = CastleSessionResult(CastleSessionStatus.LeftStairs, floor, point),
        )

    private fun teleportOut(): CastleSession {
        val finished = runtime.asWilderness()
        return copy(
            runtime = finished,
            step =
                CastleSessionStep.Messages(
                    blocks = listOf(castleContent.text.teleportVortex, castleContent.text.teleportOutside),
                    next = CastleSessionStep.Finished(finished),
                ),
            lastResult = CastleSessionResult(CastleSessionStatus.TeleportedOut, floor, point),
        )
    }

    private fun giveStairItem(digit: Int): CastleSession {
        val item = castleContent.stairs.items.firstOrNull { it.digit == digit }
        if (item == null) {
            return copy(step = CastleSessionStep.StairsMenu)
        }
        if (item.milestone !in party.milestones) {
            return copy(
                step = CastleSessionStep.Messages(listOf(item.reject), CastleSessionStep.StairsMenu),
                lastResult =
                    CastleSessionResult(
                        CastleSessionStatus.StairsItemMissing,
                        floor,
                        point,
                        milestone = item.milestone,
                    ),
            )
        }

        val nextFloor = floor + 1
        val nextRuntime = runtime.withActiveParty(party.withoutMilestone(item.milestone))
        val moved =
            copy(
                runtime = nextRuntime,
                floor = nextFloor,
                point = GridPoint(1, 1),
                lastResult =
                    CastleSessionResult(
                        CastleSessionStatus.StairsItemGiven,
                        floor,
                        point,
                        milestone = item.milestone,
                    ),
            )
        if (nextFloor > castleContent.stairs.floorCount) {
            return moved.completeCastle()
        }
        return moved.copy(step = CastleSessionStep.Messages(listOf(castleContent.text.stairsGuardianGrateful), CastleSessionStep.RoomEntry))
    }

    private fun completeCastle(): CastleSession {
        val script = castleContent.scripts.completed
        if (script != null) {
            return startScript(
                sceneId = script,
                next = CastleScriptContinuation.Finished,
                lastResult = CastleSessionResult(CastleSessionStatus.CompletionStarted, floor, point),
            )
        }
        val completed =
            castleContent.completionMilestone
                ?.let { milestone -> runtime.withActiveParty(party.withMilestone(milestone)) }
                ?: runtime
        val finished = completed.asWilderness()
        return copy(
            runtime = finished,
            step = CastleSessionStep.Finished(finished),
            lastResult = CastleSessionResult(CastleSessionStatus.Completed, floor, point),
        )
    }

    private fun clearCurrentRoom(): CastleSession = copy(rooms = rooms + (point to CastleRoomEvent.Empty))

    private fun rollTreasure(): TreasureRoll {
        val gate = runtime.drawRandom(0, castleContent.treasure.gateMax)
        if (gate.value == 0) {
            val item = gate.runtime.drawRandom(0, castleContent.treasure.magic.lastIndex)
            return TreasureRoll(item.runtime, castleContent.treasure.magic[item.value])
        }
        val kind = gate.runtime.drawRandom(0, castleContent.treasure.regularKinds.lastIndex)
        val table = castleContent.treasure.regularKinds[kind.value]
        val item = kind.runtime.drawRandom(0, table.lastIndex)
        return TreasureRoll(item.runtime, table[item.value])
    }

    private fun PartyState.applyPitfallDamage(): PartyState =
        copy(
            members =
                members.map { member ->
                    if (member.dead) {
                        member
                    } else {
                        val damage = member.hitPoints.current / castleContent.pitfall.damageDivisor
                        val nextHp = (member.hitPoints.current - damage).coerceAtLeast(0)
                        member.copy(
                            hitPoints = nextHp of member.hitPoints.max,
                            dead = nextHp == 0,
                        )
                    }
                },
        )

    private fun compassBlock(): TextBlock {
        val northKey = castleContent.keys.moveNorth.primary
        val southKey = castleContent.keys.moveSouth.primary
        val westKey = castleContent.keys.moveWest.primary
        val eastKey = castleContent.keys.moveEast.primary
        val north = if (point.y > 1) "    $northKey  North  " else ""
        val northStem = if (point.y > 1) "    |" else ""
        val eastWest =
            when {
                point.x > 1 && point.x < castleContent.mapSize -> " $westKey -+- $eastKey  East/West"
                point.x < castleContent.mapSize -> "    +- $eastKey  East"
                point.x > 1 -> " $westKey -+     West"
                else -> ""
            }
        val south = if (point.y < castleContent.mapSize) listOf("    |", "    $southKey  South  ") else emptyList()
        return inlineText(*(listOf(north, northStem, eastWest) + south).toTypedArray())
    }

    private fun directionText(direction: WildernessDirection): TextBlock =
        when (direction) {
            WildernessDirection.North -> castleContent.text.moveNorth
            WildernessDirection.South -> castleContent.text.moveSouth
            WildernessDirection.East -> castleContent.text.moveEast
            WildernessDirection.West -> castleContent.text.moveWest
        }

    private fun floorName(floor: Int): String = castleContent.text.floorNames.getOrNull(floor - 1) ?: "(Level $floor)"

    private data class TreasureRoll(
        val runtime: RuntimeSessionState,
        val item: ItemId,
    )

    private fun startScript(
        sceneId: SceneId,
        next: CastleScriptContinuation,
        lastResult: CastleSessionResult,
    ): CastleSession {
        val scene =
            content.scenes.singleOrNull { it.id == sceneId }
                ?: error("castle ${castleContent.id.slug} references missing scene ${sceneId.slug}")
        val root = ScriptRootRef.scene(scene.id)
        return copy(
            step =
                CastleSessionStep.Script(
                    scene = scene,
                    continuation = ScriptContinuation.start(runtime, root),
                    next = next,
                ),
            lastResult = lastResult,
        )
    }

    companion object {
        fun start(
            runtime: RuntimeSessionState,
            content: RuntimeContent,
            castle: CastleContentModel? = null,
        ): CastleSession {
            val castleContent =
                castle
                    ?: content.castles.singleOrNull()
                    ?: error("Castle content is not configured")
            val startRuntime = runtime.asCastle()
            val base =
                CastleSession(
                    runtime = startRuntime,
                    content = content,
                    castleContent = castleContent,
                    rooms = emptyMap(),
                    floor = 1,
                    point = GridPoint(1, 1),
                    step = CastleSessionStep.RoomEntry,
                )

            val completionMilestone = castleContent.completionMilestone
            if (completionMilestone != null && completionMilestone in startRuntime.activeParty.milestones) {
                castleContent.scripts.alreadyCompleted?.let { scene ->
                    return base.startScript(
                        sceneId = scene,
                        next = CastleScriptContinuation.Finished,
                        lastResult = CastleSessionResult(CastleSessionStatus.AlreadyCompleted),
                    )
                }
                val finished = startRuntime.asWilderness()
                return base.copy(
                    runtime = finished,
                    step = CastleSessionStep.Finished(finished),
                    lastResult = CastleSessionResult(CastleSessionStatus.AlreadyCompleted),
                )
            }

            val entryRequirement = castleContent.entryRequirement
            if (entryRequirement != null && entryRequirement !in startRuntime.activeParty.milestones) {
                castleContent.scripts.entryBlocked?.let { scene ->
                    return base.startScript(
                        sceneId = scene,
                        next = CastleScriptContinuation.Finished,
                        lastResult = CastleSessionResult(CastleSessionStatus.EntryBlocked),
                    )
                }
                val finished = startRuntime.asWilderness()
                return base.copy(
                    runtime = finished,
                    step = CastleSessionStep.Finished(finished),
                    lastResult = CastleSessionResult(CastleSessionStatus.EntryBlocked),
                )
            }

            val seeded = base.seedRooms()
            val entered =
                base.copy(
                    runtime = seeded.runtime,
                    rooms = seeded.rooms,
                    lastResult = CastleSessionResult(CastleSessionStatus.Entered, floor = 1, point = GridPoint(1, 1)),
                )
            castleContent.scripts.entered?.let { scene ->
                return entered.startScript(
                    sceneId = scene,
                    next = CastleScriptContinuation.RoomEntry,
                    lastResult = CastleSessionResult(CastleSessionStatus.Entered, floor = 1, point = GridPoint(1, 1)),
                )
            }
            return entered.copy(
                runtime = seeded.runtime,
                rooms = seeded.rooms,
                step = CastleSessionStep.RoomEntry,
                lastResult = CastleSessionResult(CastleSessionStatus.Entered, floor = 1, point = GridPoint(1, 1)),
            )
        }
    }

    private fun seedRooms(): SeededRooms {
        var nextRuntime = runtime
        val generated =
            buildMap {
                (1..castleContent.mapSize).forEach { x ->
                    (1..castleContent.mapSize).forEach { y ->
                        val draw = nextRuntime.drawRandom(1, castleContent.roomTypeMax)
                        nextRuntime = draw.runtime
                        put(GridPoint(x, y), castleContent.roomEvents[draw.value] ?: CastleRoomEvent.Empty)
                    }
                }
                put(GridPoint(castleContent.mapSize, castleContent.mapSize), CastleRoomEvent.Empty)
            }
        return SeededRooms(nextRuntime, generated)
    }

    private data class SeededRooms(
        val runtime: RuntimeSessionState,
        val rooms: Map<GridPoint, CastleRoomEvent>,
    )
}

fun CastleSession.toCastleSnapshot(): RuntimeModeSnapshot.Castle =
    snapshotStep().let { current ->
        val script = current as? CastleSessionStep.Script
        RuntimeModeSnapshot.Castle(
            CastleSessionSnapshot(
                castle = castleContent.id,
                floor = floor,
                point = point,
                rooms = rooms,
                step = savedStep(),
                treasureItem = (current as? CastleSessionStep.TreasureOffer)?.item,
                scriptScene = script?.scene?.id,
                scriptContinuation = script?.continuation?.toSnapshot(),
                scriptNext = script?.next,
            ),
        )
    }

fun RuntimeModeSnapshot.Castle.toCastleSession(
    runtime: RuntimeSessionState,
    content: RuntimeContent,
): CastleSession {
    val castleContent =
        content.castles.singleOrNull { castle -> castle.id == session.castle }
            ?: error("Castle ${session.castle.slug} content is not configured")
    val castleRuntime = runtime.asCastle()
    return CastleSession(
        runtime = castleRuntime,
        content = content,
        castleContent = castleContent,
        rooms = session.rooms,
        floor = session.floor,
        point = session.point,
        step = session.toStep(content, castleRuntime),
    )
}

private fun CastleSession.savedStep(): CastleSavedStep =
    when (snapshotStep()) {
        CastleSessionStep.RoomEntry -> CastleSavedStep.RoomEntry
        CastleSessionStep.Navigate -> CastleSavedStep.Navigate
        is CastleSessionStep.TreasureOffer -> CastleSavedStep.TreasureOffer
        CastleSessionStep.StairsMenu -> CastleSavedStep.StairsMenu
        CastleSessionStep.StairGive -> CastleSavedStep.StairGive
        is CastleSessionStep.Script -> CastleSavedStep.Script
        else -> error("cannot save castle while ${step.javaClass.simpleName} is active")
    }

private fun CastleSession.snapshotStep(): CastleSessionStep = (step as? CastleSessionStep.Save)?.next ?: step

private fun CastleSessionSnapshot.toStep(
    content: RuntimeContent,
    runtime: RuntimeSessionState,
): CastleSessionStep =
    when (step) {
        CastleSavedStep.RoomEntry -> CastleSessionStep.RoomEntry
        CastleSavedStep.Navigate -> CastleSessionStep.Navigate
        CastleSavedStep.TreasureOffer ->
            CastleSessionStep.TreasureOffer(
                checkNotNull(treasureItem) { "castle treasure-offer snapshot requires treasureItem" },
            )
        CastleSavedStep.StairsMenu -> CastleSessionStep.StairsMenu
        CastleSavedStep.StairGive -> CastleSessionStep.StairGive
        CastleSavedStep.Script -> {
            val sceneId = checkNotNull(scriptScene) { "castle script snapshot requires scriptScene" }
            val scene =
                content.scenes.singleOrNull { scene -> scene.id == sceneId }
                    ?: error("castle script snapshot references unknown scene ${sceneId.slug}")
            CastleSessionStep.Script(
                scene = scene,
                continuation =
                    checkNotNull(scriptContinuation) {
                        "castle script snapshot requires scriptContinuation"
                    }.toContinuation(runtime),
                next = checkNotNull(scriptNext) { "castle script snapshot requires scriptNext" },
            )
        }
    }

private fun RuntimeSessionState.asCastle(): RuntimeSessionState =
    withRealm(
        realm.copy(
            currentMode = RuntimeMode.Castle,
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
