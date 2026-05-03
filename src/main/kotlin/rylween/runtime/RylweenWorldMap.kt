package rylween.runtime

import rylween.dsl.*
import rylween.runtime.ydarg.YdargSession
import rylween.runtime.ydarg.YdargSessionStep

import java.io.File

enum class WildernessDirection { North, South, East, West }

sealed interface WildernessSessionInput {
    data class Move(val direction: WildernessDirection) : WildernessSessionInput
    object Pass : WildernessSessionInput
    object Save : WildernessSessionInput
    object ReadyWeapon : WildernessSessionInput
    object ReadyArmor : WildernessSessionInput
    object Redraw : WildernessSessionInput
    object Journal : WildernessSessionInput
    object Help : WildernessSessionInput
    object Hunt : WildernessSessionInput
    data class HuntMember(val memberNumber: Int) : WildernessSessionInput
    object HuntContinue : WildernessSessionInput
    object ViewParty : WildernessSessionInput
    object RemoveArmor : WildernessSessionInput
    object RemoveWeapon : WildernessSessionInput
    object Enter : WildernessSessionInput
    object Drop : WildernessSessionInput
    object Camp : WildernessSessionInput
    data class CampGuardAnswer(val yes: Boolean) : WildernessSessionInput
    data class CampGuardMember(val memberNumber: Int) : WildernessSessionInput
    object CampContinue : WildernessSessionInput
    object CastSpell : WildernessSessionInput
    object Quaff : WildernessSessionInput
    object Continue : WildernessSessionInput
    data class CaveAnswer(val enter: Boolean) : WildernessSessionInput
    object Quit : WildernessSessionInput
    data class QuitAnswer(val yes: Boolean) : WildernessSessionInput
    data class OutOfMovesAnswer(val save: Boolean) : WildernessSessionInput
}

enum class WildernessSessionResultStatus {
    Passed,
    Moved,
    Blocked,
    NoMoves,
    EnteredTown,
    EnteredHut,
    EnteredCastle,
    NothingToEnter,
    UnknownTown,
    TerrainNarrated,
    SaveUnavailable,
    RedrawRequested,
    JournalUnavailable,
    HelpShown,
    HuntUnavailable,
    HuntSucceeded,
    HuntFailed,
    HuntNotRanger,
    HuntTooFewMoves,
    ViewPartyUnavailable,
    DropUnavailable,
    CampUnavailable,
    Camped,
    NotTired,
    CampNotEnoughFood,
    CastSpellUnavailable,
    CastSpellRequested,
    CastSpellUsed,
    EquipmentRequested,
    EquipmentUsed,
    EquipmentUnavailable,
    InventoryRequested,
    InventoryUsed,
    InventoryUnavailable,
    MissionHandoff,
    MissionCompleted,
    CaveFound,
    CaveLeft,
    CaveNoMoves,
    DungeonHandoff,
    LeftDungeon,
    LeftYdarg,
    EncounterHandoff,
    LeftTown,
    NotInWilderness,
    QuitRequested,
    QuitCancelled,
    OutOfMovesSaveAndQuit,
    OutOfMovesQuitNoSave,
    Finished,
}

data class WildernessSessionResult(
    val status: WildernessSessionResultStatus,
    val from: GridPoint? = null,
    val to: GridPoint? = null,
    val terrain: Char? = null,
    val town: TownId? = null,
    val dungeon: DungeonId? = null,
    val dungeonLevels: Int? = null,
    val encounterTier: EncounterTier? = null,
    val mission: MissionId? = null,
)

sealed interface WildernessSessionStep {
    object TurnStart : WildernessSessionStep
    object Explore : WildernessSessionStep
    data class Messages(val blocks: List<TextBlock>, val next: WildernessSessionStep) : WildernessSessionStep
    data class OutOfMoves(val prompt: TextBlock) : WildernessSessionStep
    data class CaveFound(val prompt: TextBlock, val image: ImageId?) : WildernessSessionStep
    data class Mission(
        val mission: MissionModel,
        val scene: SceneModel,
        val continuation: TownServiceContinuation? = null,
    ) : WildernessSessionStep
    data class Hut(
        val scene: SceneModel,
        val continuation: TownServiceContinuation? = null,
    ) : WildernessSessionStep
    data class DungeonHandoff(val handoff: WildernessDungeonHandoff) : WildernessSessionStep
    data class Dungeon(val session: DungeonSession) : WildernessSessionStep
    data class Ydarg(val session: YdargSession) : WildernessSessionStep
    data class Battle(val session: BattleSession) : WildernessSessionStep
    data class Camp(val session: CampSession) : WildernessSessionStep
    data class Hunt(val session: HuntSession) : WildernessSessionStep
    data class InventoryItemUse(val session: InventoryItemUseSession) : WildernessSessionStep
    data class Equipment(val session: EquipmentPromptSession) : WildernessSessionStep
    data class PriestSpell(val session: PriestSpellSession) : WildernessSessionStep
    data class Notice(val text: TextBlock, val next: WildernessSessionStep = Explore) : WildernessSessionStep
    data class QuitConfirm(val prompt: TextBlock) : WildernessSessionStep
    data class Town(val session: TownSession) : WildernessSessionStep
    data class Finished(val runtime: RuntimeSessionState) : WildernessSessionStep
}

data class WildernessDungeonHandoff(
    val dungeon: DungeonId,
    val levels: Int,
)

private data class WildernessUpkeepResult(
    val runtime: RuntimeSessionState,
    val messages: List<TextBlock>,
    val allDead: Boolean,
)

private data class TerrainTransitionResult(
    val runtime: RuntimeSessionState,
    val text: TextBlock?,
)

private data class MissionTriggerResult(
    val runtime: RuntimeSessionState,
    val mission: MissionModel?,
)

data class WorldMapGrid(
    val id: WorldMapId,
    val rows: List<String>,
) {
    init {
        require(rows.isNotEmpty()) { "world map requires at least one row" }
    }

    val height: Int get() = rows.size
    val width: Int get() = rows.maxOfOrNull { it.length } ?: 0

    fun tileAt(point: GridPoint): Char? =
        rows.getOrNull(point.y)?.getOrNull(point.x)

    fun isPassable(point: GridPoint): Boolean =
        tileAt(point)?.isImpassableTerrain() == false

    fun isTown(point: GridPoint): Boolean =
        tileAt(point) == DslDefaultTerrain.town.sourceMapCode

    fun isHut(point: GridPoint): Boolean =
        tileAt(point) == DslDefaultTerrain.hut.sourceMapCode

    fun isCastle(point: GridPoint): Boolean =
        tileAt(point) == DslDefaultTerrain.castle.sourceMapCode

    private fun isSettlement(point: GridPoint): Boolean =
        isTown(point) || isHut(point) || isCastle(point)

    fun isNpcPlacementTerrain(point: GridPoint): Boolean =
        isPassable(point) && !isSettlement(point)

    fun passableNpcPoints(): Set<GridPoint> =
        rows.flatMapIndexed { y, row ->
            row.indices.mapNotNull { x ->
                GridPoint(x, y).takeIf(::isNpcPlacementTerrain)
            }
        }.toSet()

    fun withNpcHuts(npcs: List<NpcPlacement>): WorldMapGrid =
        npcs.fold(this) { map, placement ->
            val point = (placement.position as? WorldPosition.Wilderness)?.point
            if (point != null && map.isNpcPlacementTerrain(point)) {
                map.withTileAt(point, requireNotNull(DslDefaultTerrain.hut.sourceMapCode))
            } else {
                map
            }
        }

    fun viewport(center: GridPoint, radius: Int = 2): WorldMapViewport {
        require(radius >= 0) { "map viewport radius must be non-negative" }
        return WorldMapViewport(
            center = center,
            radius = radius,
            rows = (-radius..radius).map { dy ->
                (-radius..radius).map { dx ->
                    val point = GridPoint(center.x + dx, center.y + dy)
                    WorldMapViewportCell(
                        point = point,
                        tile = tileAt(point),
                        isCenter = point == center,
                    )
                }
            },
        )
    }

    private fun withTileAt(point: GridPoint, tile: Char): WorldMapGrid {
        val row = rows.getOrNull(point.y) ?: return this
        if (point.x !in row.indices) return this
        val updatedRow = StringBuilder(row).apply { setCharAt(point.x, tile) }.toString()
        return copy(rows = rows.mapIndexed { y, existing -> if (y == point.y) updatedRow else existing })
    }

    companion object {
        fun fromSourceRows(rows: List<String>): WorldMapGrid =
            WorldMapGrid(
                id = DslDefaultMaps.wilderness,
                rows = listOf("") + rows.map { it.trimEnd('\r', '\n') },
            )

        fun loadReferenceMapDat(file: File): WorldMapGrid =
            fromSourceRows(file.readLines())
    }
}

data class WorldMapViewport(
    val center: GridPoint,
    val radius: Int,
    val rows: List<List<WorldMapViewportCell>>,
)

data class WorldMapViewportCell(
    val point: GridPoint,
    val tile: Char?,
    val isCenter: Boolean,
)

fun GameModel.worldMapGrid(id: WorldMapId): WorldMapGrid? =
    worldMaps.firstOrNull { it.id == id }?.toGrid()

fun WorldMapModel.toGrid(): WorldMapGrid {
    val terrainRows = layers.singleOrNull { it.id == DslDefaultMapLayers.terrain }?.rows
        ?: error("world map ${id.slug} has no terrain layer")
    return WorldMapGrid(id = id, rows = listOf("") + terrainRows)
}

fun SceneModel.hutService(): BuildingServiceModel =
    BuildingServiceModel(
        building = DslDefaultBuildings.hut,
        action = DslDefaultKeyActions.enter,
        scene = id,
        opensAt = null,
        closesAt = null,
        nativeServices = emptyList(),
    )

data class WildernessSession(
    val runtime: RuntimeSessionState,
    val worldMap: WorldMapGrid,
    val content: RuntimeContent = RuntimeContent(),
    val step: WildernessSessionStep = WildernessSessionStep.TurnStart,
    val lastResult: WildernessSessionResult? = null,
) {
    val towns: List<TownModel> get() = content.towns
    val townSystem: TownSystemModel
        get() = requireNotNull(content.townSystem) { "town system is not configured" }
    val scenes: List<SceneModel> get() = content.scenes
    val missions: List<MissionModel> get() = content.missions
    val wilderness: WildernessSystemModel get() = content.wilderness
    val dungeons: DungeonSystemModel get() = content.dungeons
    val combat: CombatSystemModel get() = content.combat
    val monsters: List<MonsterModel> get() = content.monsters
    val formulas: List<FormulaModel> get() = content.formulas
    val rewards: List<RewardBundleModel> get() = content.rewards
    val leveling: LevelingModel? get() = content.leveling
    val spells: List<SpellModel> get() = content.spells
    val itemUses: List<ItemUseModel> get() = content.itemUses
    val itemRecords: List<ItemRecordModel> get() = content.itemRecords
    val realm: RealmContentModel get() = content.realm
    val prompts: WildernessPromptsModel get() = content.prompts.wilderness
    val party: PartyState get() = runtime.activeParty

    fun apply(input: WildernessSessionInput): WildernessSession =
        when (input) {
            is WildernessSessionInput.Move -> move(input.direction)
            WildernessSessionInput.Pass -> pass()
            WildernessSessionInput.Save ->
                pending(RuntimeUnavailableText.savePending(), WildernessSessionResultStatus.SaveUnavailable)
            WildernessSessionInput.ReadyWeapon -> equipment(EquipmentCommand.ReadyWeapon)
            WildernessSessionInput.ReadyArmor -> equipment(EquipmentCommand.ReadyArmor)
            WildernessSessionInput.Redraw -> copy(lastResult = WildernessSessionResult(WildernessSessionResultStatus.RedrawRequested))
            WildernessSessionInput.Journal ->
                pending(RuntimeUnavailableText.journalPending(), WildernessSessionResultStatus.JournalUnavailable)
            WildernessSessionInput.Help -> copy(
                step = WildernessSessionStep.Messages(wilderness.keys.effectiveHelpPages(), WildernessSessionStep.TurnStart),
                lastResult = WildernessSessionResult(WildernessSessionResultStatus.HelpShown),
            )
            WildernessSessionInput.Hunt -> hunt()
            is WildernessSessionInput.HuntMember -> applyHuntInput(HuntSessionInput.Member(input.memberNumber))
            WildernessSessionInput.HuntContinue -> applyHuntInput(HuntSessionInput.Continue)
            WildernessSessionInput.ViewParty ->
                pending(RuntimeUnavailableText.viewPartyPending(), WildernessSessionResultStatus.ViewPartyUnavailable)
            WildernessSessionInput.RemoveArmor -> equipment(EquipmentCommand.RemoveArmor)
            WildernessSessionInput.RemoveWeapon -> equipment(EquipmentCommand.RemoveWeapon)
            WildernessSessionInput.Enter -> enter()
            WildernessSessionInput.Drop ->
                pending(RuntimeUnavailableText.dropPending(), WildernessSessionResultStatus.DropUnavailable)
            WildernessSessionInput.Camp -> camp()
            is WildernessSessionInput.CampGuardAnswer -> applyCampInput(CampSessionInput.GuardAnswer(input.yes))
            is WildernessSessionInput.CampGuardMember -> applyCampInput(CampSessionInput.GuardMember(input.memberNumber))
            WildernessSessionInput.CampContinue -> applyCampInput(CampSessionInput.Continue)
            WildernessSessionInput.CastSpell -> castSpell()
            WildernessSessionInput.Quaff -> quaff()
            WildernessSessionInput.Continue -> continueNotice()
            is WildernessSessionInput.CaveAnswer -> answerCave(input.enter)
            WildernessSessionInput.Quit -> requestQuit()
            is WildernessSessionInput.QuitAnswer -> answerQuit(input.yes)
            is WildernessSessionInput.OutOfMovesAnswer -> answerOutOfMoves(input.save)
        }

    fun beginTurn(): WildernessSession {
        require(step is WildernessSessionStep.TurnStart) { "wilderness session is not starting a turn" }
        val upkeep = runtime.applyWildernessUpkeep(realm.upkeep)
        val next = when {
            upkeep.allDead -> WildernessSessionStep.Finished(
                upkeep.runtime.withRealm(upkeep.runtime.realm.copy(currentMode = RuntimeMode.GameOver)),
            )
            upkeep.runtime.activeParty.resources.timeLeft <= 0 ->
                WildernessSessionStep.OutOfMoves(prompts.outOfMoves)
            else -> WildernessSessionStep.Explore
        }
        return copy(
            runtime = upkeep.runtime,
            step = if (upkeep.messages.isEmpty()) next else WildernessSessionStep.Messages(upkeep.messages, next),
        )
    }

    fun continueNotice(): WildernessSession {
        val current = step as? WildernessSessionStep.Notice
            ?: error("wilderness session is not showing a notice")
        return copy(step = current.next)
    }

    fun continueMessages(): WildernessSession {
        val current = step as? WildernessSessionStep.Messages
            ?: error("wilderness session is not showing messages")
        return copy(step = current.next)
    }

    fun startDungeonFromHandoff(): WildernessSession {
        val current = step as? WildernessSessionStep.DungeonHandoff
            ?: error("wilderness session is not handing off to a dungeon")
        return copy(
            step = WildernessSessionStep.Dungeon(
                DungeonSession.start(
                    runtime = runtime,
                    worldMap = worldMap,
                    dungeon = current.handoff.dungeon,
                    levels = current.handoff.levels,
                    content = content,
                ),
            ),
        )
    }

    fun replaceDungeonSession(session: DungeonSession): WildernessSession {
        require(step is WildernessSessionStep.Dungeon) { "wilderness session is not running a dungeon session" }
        return if (session.step is DungeonSessionStep.Finished) {
            val finishedRuntime = session.runtime.withRealm(
                session.runtime.realm.copy(
                    currentMode = RuntimeMode.Wilderness,
                    currentTown = null,
                    currentMap = worldMap.id,
                ),
            )
            copy(
                runtime = finishedRuntime,
                step = WildernessSessionStep.TurnStart,
                lastResult = WildernessSessionResult(
                    WildernessSessionResultStatus.LeftDungeon,
                    dungeon = session.dungeon,
                    dungeonLevels = session.maxDepth,
                ),
            )
        } else {
            copy(runtime = session.runtime, step = WildernessSessionStep.Dungeon(session))
        }
    }

    fun replaceYdargSession(session: YdargSession): WildernessSession {
        require(step is WildernessSessionStep.Ydarg) { "wilderness session is not running Ydarg" }
        return if (session.step is YdargSessionStep.Finished) {
            if (session.runtime.realm.currentMode == RuntimeMode.GameOver) {
                return copy(
                    runtime = session.runtime,
                    step = WildernessSessionStep.Finished(session.runtime),
                    lastResult = WildernessSessionResult(WildernessSessionResultStatus.Finished),
                )
            }
            val finishedRuntime = session.runtime.withRealm(
                session.runtime.realm.copy(
                    currentMode = RuntimeMode.Wilderness,
                    currentTown = null,
                    currentMap = worldMap.id,
                ),
            )
            copy(
                runtime = finishedRuntime,
                step = WildernessSessionStep.TurnStart,
                lastResult = WildernessSessionResult(WildernessSessionResultStatus.LeftYdarg),
            )
        } else {
            copy(runtime = session.runtime, step = WildernessSessionStep.Ydarg(session))
        }
    }

    fun replaceTownSession(session: TownSession): WildernessSession {
        require(step is WildernessSessionStep.Town) { "wilderness session is not running a town session" }
        return if (session.step is TownSessionStep.Finished) {
            val nextRuntime = session.runtime.withRealm(
                session.runtime.realm.copy(
                    currentMode = RuntimeMode.Wilderness,
                    currentTown = null,
                    currentMap = worldMap.id,
                ),
            )
            copy(
                runtime = nextRuntime,
                step = WildernessSessionStep.TurnStart,
                lastResult = WildernessSessionResult(WildernessSessionResultStatus.LeftTown),
            )
        } else {
            copy(runtime = session.runtime, step = WildernessSessionStep.Town(session))
        }
    }

    fun replaceBattleSession(session: BattleSession): WildernessSession {
        require(step is WildernessSessionStep.Battle) { "wilderness session is not running a battle session" }
        return if (session.step is BattleSessionStep.Finished) {
            val finished = session.step
            val nextRuntime = finished.runtime.withRealm(
                finished.runtime.realm.copy(
                    currentMode = RuntimeMode.Wilderness,
                    currentTown = null,
                    currentMap = worldMap.id,
                ),
            )
            copy(
                runtime = nextRuntime,
                step = WildernessSessionStep.TurnStart,
                lastResult = lastResult?.copy(
                    status = WildernessSessionResultStatus.EncounterHandoff,
                    encounterTier = session.tier,
                ),
            )
        } else {
            copy(runtime = session.runtime, step = WildernessSessionStep.Battle(session))
        }
    }

    fun replaceInventoryItemUseSession(session: InventoryItemUseSession): WildernessSession {
        require(step is WildernessSessionStep.InventoryItemUse) {
            "wilderness session is not running inventory item use"
        }
        return if (session.step is InventoryItemUseStep.Finished) {
            copy(
                runtime = session.runtime.withRealm(
                    session.runtime.realm.copy(
                        currentMode = RuntimeMode.Wilderness,
                        currentTown = null,
                        currentMap = worldMap.id,
                    ),
                ),
                step = WildernessSessionStep.TurnStart,
                lastResult = WildernessSessionResult(
                    status = when (session.lastResult?.status) {
                        InventoryItemUseStatus.Used,
                        InventoryItemUseStatus.Equipped -> WildernessSessionResultStatus.InventoryUsed
                        else -> WildernessSessionResultStatus.InventoryUnavailable
                    },
                ),
            )
        } else {
            copy(runtime = session.runtime, step = WildernessSessionStep.InventoryItemUse(session))
        }
    }

    fun replaceEquipmentSession(session: EquipmentPromptSession): WildernessSession {
        require(step is WildernessSessionStep.Equipment) {
            "wilderness session is not running equipment prompts"
        }
        return if (session.step is EquipmentPromptStep.Finished) {
            val status = when (session.step.result) {
                is EquipmentActionResult.Equipped,
                is EquipmentActionResult.Unequipped -> WildernessSessionResultStatus.EquipmentUsed
                else -> WildernessSessionResultStatus.EquipmentUnavailable
            }
            copy(
                runtime = runtime.withActiveParty(session.party),
                step = WildernessSessionStep.TurnStart,
                lastResult = WildernessSessionResult(status),
            )
        } else {
            copy(runtime = runtime.withActiveParty(session.party), step = WildernessSessionStep.Equipment(session))
        }
    }

    fun replacePriestSpellSession(session: PriestSpellSession): WildernessSession {
        require(step is WildernessSessionStep.PriestSpell) {
            "wilderness session is not running priest spell casting"
        }
        return if (session.step is PriestSpellStep.Finished) {
            val handoff = session.lastResult?.dungeonHandoff
            if (handoff != null) {
                return copy(
                    runtime = session.runtime,
                    step = WildernessSessionStep.DungeonHandoff(handoff),
                    lastResult = WildernessSessionResult(
                        WildernessSessionResultStatus.DungeonHandoff,
                        dungeon = handoff.dungeon,
                        dungeonLevels = handoff.levels,
                    ),
                )
            }
            val status = when (session.lastResult?.status) {
                PriestSpellStatus.Cast -> WildernessSessionResultStatus.CastSpellUsed
                else -> WildernessSessionResultStatus.CastSpellUnavailable
            }
            copy(
                runtime = session.runtime.withRealm(
                    session.runtime.realm.copy(
                        currentMode = RuntimeMode.Wilderness,
                        currentTown = null,
                        currentMap = worldMap.id,
                    ),
                ),
                step = WildernessSessionStep.TurnStart,
                lastResult = WildernessSessionResult(status),
            )
        } else {
            copy(runtime = session.runtime, step = WildernessSessionStep.PriestSpell(session))
        }
    }

    fun replaceMissionContinuation(continuation: TownServiceContinuation): WildernessSession {
        val current = step as? WildernessSessionStep.Mission
            ?: error("wilderness session is not running a mission")
        return copy(
            runtime = continuation.runtime,
            step = current.copy(continuation = continuation),
        )
    }

    fun replaceHutContinuation(continuation: TownServiceContinuation): WildernessSession {
        val current = step as? WildernessSessionStep.Hut
            ?: error("wilderness session is not running a hut")
        return copy(
            runtime = continuation.runtime,
            step = current.copy(continuation = continuation),
        )
    }

    fun finishHut(runtime: RuntimeSessionState): WildernessSession {
        val nextRuntime = runtime.withRealm(
            runtime.realm.copy(
                currentMode = RuntimeMode.Wilderness,
                currentTown = null,
                currentMap = worldMap.id,
            ),
        )
        return copy(
            runtime = nextRuntime,
            step = WildernessSessionStep.TurnStart,
            lastResult = WildernessSessionResult(WildernessSessionResultStatus.EnteredHut),
        )
    }

    fun finishMission(runtime: RuntimeSessionState): WildernessSession {
        val current = step as? WildernessSessionStep.Mission
            ?: error("wilderness session is not running a mission")
        if (runtime.realm.currentMode == RuntimeMode.GameOver) {
            return copy(
                runtime = runtime,
                step = WildernessSessionStep.Finished(runtime),
                lastResult = WildernessSessionResult(
                    WildernessSessionResultStatus.Finished,
                    mission = current.mission.id,
                ),
            )
        }
        val nextRuntime = runtime.withRealm(
            runtime.realm.copy(
                currentMode = RuntimeMode.Wilderness,
                currentTown = null,
                currentMap = worldMap.id,
            ),
        )
        return copy(
            runtime = nextRuntime,
            step = WildernessSessionStep.TurnStart,
            lastResult = WildernessSessionResult(
                WildernessSessionResultStatus.MissionCompleted,
                mission = current.mission.id,
            ),
        )
    }

    fun handOffMissionDungeon(
        runtime: RuntimeSessionState,
        handoff: WildernessDungeonHandoff,
    ): WildernessSession {
        val current = step as? WildernessSessionStep.Mission
            ?: error("wilderness session is not running a mission")
        val nextRuntime = runtime.withRealm(
            runtime.realm.copy(
                currentMode = RuntimeMode.Dungeon,
                currentTown = null,
                currentMap = worldMap.id,
            ),
        )
        return copy(
            runtime = nextRuntime,
            step = WildernessSessionStep.DungeonHandoff(handoff),
            lastResult = WildernessSessionResult(
                WildernessSessionResultStatus.DungeonHandoff,
                from = lastResult?.from,
                to = lastResult?.to,
                terrain = lastResult?.terrain,
                mission = current.mission.id,
                dungeon = handoff.dungeon,
                dungeonLevels = handoff.levels,
            ),
        )
    }

    private fun pass(): WildernessSession =
        notice(
            prompts.pass,
            WildernessSessionResult(WildernessSessionResultStatus.Passed),
        )

    private fun quaff(): WildernessSession =
        copy(
            step = WildernessSessionStep.InventoryItemUse(
                InventoryItemUseSession.start(
                    runtime = runtime,
                    content = content,
                    allowedKinds = setOf(ItemKind.Potion),
                    choosePrompt = content.prompts.inventoryUse.quaff,
                ),
            ),
            lastResult = WildernessSessionResult(WildernessSessionResultStatus.InventoryRequested),
        )

    private fun camp(): WildernessSession =
        copy(
            step = WildernessSessionStep.Camp(CampSession.start(runtime, content)),
            lastResult = WildernessSessionResult(WildernessSessionResultStatus.Camped),
        )

    private fun hunt(): WildernessSession {
        val rules = realm.hunt ?: return pending(
            RuntimeUnavailableText.huntPending(),
            WildernessSessionResultStatus.HuntUnavailable,
        )
        return copy(step = WildernessSessionStep.Hunt(HuntSession.start(runtime, rules)))
    }

    private fun applyCampInput(input: CampSessionInput): WildernessSession {
        val current = step as? WildernessSessionStep.Camp
            ?: error("wilderness session is not running a camp")
        val updated = current.session.apply(input)
        return resolveCampSession(updated)
    }

    private fun applyHuntInput(input: HuntSessionInput): WildernessSession {
        val current = step as? WildernessSessionStep.Hunt
            ?: error("wilderness session is not running a hunt")
        val updated = current.session.apply(input)
        return resolveHuntSession(updated)
    }

    fun replaceCampSession(session: CampSession): WildernessSession =
        resolveCampSession(session)

    fun replaceHuntSession(session: HuntSession): WildernessSession =
        resolveHuntSession(session)

    private fun resolveCampSession(session: CampSession): WildernessSession {
        val finished = (session.step as? CampSessionStep.Finished)?.result
        return when (finished?.status) {
            null -> copy(
                runtime = session.runtime,
                step = WildernessSessionStep.Camp(session),
            )
            else -> copy(
                runtime = finished.runtime,
                step = WildernessSessionStep.TurnStart,
                lastResult = WildernessSessionResult(
                    when (finished.status) {
                        CampSessionStatus.Rested -> WildernessSessionResultStatus.Camped
                        CampSessionStatus.NotTired -> WildernessSessionResultStatus.NotTired
                        CampSessionStatus.NotEnoughFood -> WildernessSessionResultStatus.CampNotEnoughFood
                        CampSessionStatus.Ambushed -> WildernessSessionResultStatus.EncounterHandoff
                        CampSessionStatus.SurpriseKill -> WildernessSessionResultStatus.EncounterHandoff
                    },
                ),
            )
        }
    }

    private fun resolveHuntSession(session: HuntSession): WildernessSession {
        val finished = (session.step as? HuntSessionStep.Finished)?.result
        return when (finished?.status) {
            null -> copy(
                runtime = session.runtime,
                step = WildernessSessionStep.Hunt(session),
            )
            else -> copy(
                runtime = finished.runtime,
                step = WildernessSessionStep.TurnStart,
                lastResult = WildernessSessionResult(
                    when (finished.status) {
                        HuntSessionStatus.Succeeded -> WildernessSessionResultStatus.HuntSucceeded
                        HuntSessionStatus.Failed -> WildernessSessionResultStatus.HuntFailed
                        HuntSessionStatus.NotRanger -> WildernessSessionResultStatus.HuntNotRanger
                        HuntSessionStatus.TooFewMoves -> WildernessSessionResultStatus.HuntTooFewMoves
                        HuntSessionStatus.DeadMember -> WildernessSessionResultStatus.HuntUnavailable
                    },
                ),
            )
        }
    }

    private fun castSpell(): WildernessSession =
        copy(
            step = WildernessSessionStep.PriestSpell(
                PriestSpellSession.start(
                    runtime = runtime,
                    spells = spells,
                    place = PriestSpellPlace.Wilderness,
                    prompts = content.prompts.priest,
                ),
            ),
            lastResult = WildernessSessionResult(WildernessSessionResultStatus.CastSpellRequested),
        )

    private fun equipment(command: EquipmentCommand): WildernessSession =
        copy(
            step = WildernessSessionStep.Equipment(
                EquipmentPromptSession.start(command, party, itemRecords, content.prompts.equipment),
            ),
            lastResult = WildernessSessionResult(WildernessSessionResultStatus.EquipmentRequested),
        )

    private fun move(direction: WildernessDirection): WildernessSession {
        val from = party.wildernessPoint()
            ?: return notice(
                prompts.notInWilderness,
                WildernessSessionResult(WildernessSessionResultStatus.NotInWilderness),
            )
        if (party.resources.timeLeft <= 0) {
            return notice(
                prompts.noMoves,
                WildernessSessionResult(WildernessSessionResultStatus.NoMoves, from = from),
            )
        }

        val to = from + direction.delta()
        if (!worldMap.isPassable(to)) {
            return copy(
                lastResult = WildernessSessionResult(
                    WildernessSessionResultStatus.Blocked,
                    from = from,
                    to = to,
                    terrain = worldMap.tileAt(to),
                ),
            )
        }

        val nextParty = party.copy(
            position = WorldPosition.Wilderness(to),
            facing = direction.facing(),
            resources = party.resources.copy(timeLeft = (party.resources.timeLeft - 1).coerceAtLeast(0)),
        )
        val fromTerrain = worldMap.tileAt(from)
        val toTerrain = worldMap.tileAt(to)
        val nextRuntime = runtime
            .withActiveParty(nextParty)
            .withRealm(
                runtime.realm.copy(
                    currentMode = RuntimeMode.Wilderness,
                    currentTown = null,
                    currentMap = worldMap.id,
                    lastTerrain = wilderness.terrainIdFor(fromTerrain),
                ),
            )
        val moved = WildernessSessionResult(
            WildernessSessionResultStatus.Moved,
            from = from,
            to = to,
            terrain = toTerrain,
        )
        return copy(runtime = nextRuntime, lastResult = moved)
            .afterMoveEvent(
                from = from,
                to = to,
                fromTerrain = fromTerrain,
                toTerrain = toTerrain,
                moved = moved,
            )
    }

    private fun afterMoveEvent(
        from: GridPoint,
        to: GridPoint,
        fromTerrain: Char?,
        toTerrain: Char?,
        moved: WildernessSessionResult,
    ): WildernessSession {
        val events = wilderness.events
        if (runtime.realm.rngState == null) {
            return copy(step = WildernessSessionStep.TurnStart, lastResult = moved)
        }

        val missionTrigger = runtime.pollWildernessMissions(
            missions = missions,
            currentTerrain = wilderness.terrainIdFor(toTerrain),
        )
        missionTrigger.mission?.let { mission ->
            val scene = scenes.firstOrNull { it.id == mission.action }
            if (scene != null) {
                return copy(
                    runtime = missionTrigger.runtime,
                    step = WildernessSessionStep.Mission(mission, scene),
                    lastResult = moved.copy(
                        status = WildernessSessionResultStatus.MissionHandoff,
                        mission = mission.id,
                    ),
                )
            }
        }

        val eventDraw = missionTrigger.runtime.drawRandom(0, 99)
        val eventRuntime = eventDraw.runtime
        if (eventDraw.value < events.caveChancePercent) {
            return copy(
                runtime = eventRuntime,
                step = WildernessSessionStep.CaveFound(events.caveFoundPrompt, events.caveImage),
                lastResult = moved.copy(status = WildernessSessionResultStatus.CaveFound),
            )
        }

        val quietLimit = events.caveChancePercent + events.quietChancePercent
        if (eventDraw.value < quietLimit) {
            val terrainResult = eventRuntime.terrainTransition(wilderness, fromTerrain, toTerrain)
            return copy(
                runtime = terrainResult.runtime,
                step = terrainResult.text?.let {
                    WildernessSessionStep.Messages(listOf(it), WildernessSessionStep.TurnStart)
                } ?: WildernessSessionStep.TurnStart,
                lastResult = terrainResult.text?.let {
                    moved.copy(status = WildernessSessionResultStatus.TerrainNarrated)
                } ?: moved,
            )
        }

        val splitDraw = eventRuntime.drawRandom(0, 99)
        val tier = if (splitDraw.value < events.encounterSplitPercent) {
            events.easyEncounterTier
        } else {
            events.hardEncounterTier
        }
        val battleSession = BattleSession.start(
            runtime = splitDraw.runtime.withRealm(
                splitDraw.runtime.realm.copy(
                    currentMode = RuntimeMode.Battle,
                    currentTown = null,
                    currentMap = worldMap.id,
                ),
            ),
            tier = tier,
            content = content,
        )
        return copy(
            runtime = battleSession.runtime,
            step = WildernessSessionStep.Battle(battleSession),
            lastResult = WildernessSessionResult(
                WildernessSessionResultStatus.EncounterHandoff,
                from = from,
                to = to,
                terrain = toTerrain,
                encounterTier = tier,
            ),
        )
    }

    private fun enter(): WildernessSession {
        val point = party.wildernessPoint()
            ?: return notice(
                prompts.notInWilderness,
                WildernessSessionResult(WildernessSessionResultStatus.NotInWilderness),
            )
        return when {
            worldMap.isTown(point) -> enterTown(point)
            worldMap.isHut(point) -> enterHut(point)
            worldMap.isCastle(point) -> enterCastle(point)
            else -> notice(
                prompts.nothingToEnter,
                WildernessSessionResult(WildernessSessionResultStatus.NothingToEnter, from = point),
            )
        }
    }

    private fun enterTown(point: GridPoint): WildernessSession {
        val town = towns.firstOrNull { it.position == point }
            ?: return notice(
                RuntimeUnavailableText.unknownTown(),
                WildernessSessionResult(WildernessSessionResultStatus.UnknownTown, from = point),
            )
        val townRuntime = runtime.withRealm(
            runtime.realm.copy(
                currentMode = RuntimeMode.Town,
                currentTown = town.id,
                currentMap = worldMap.id,
            ),
        )
        return copy(
            runtime = townRuntime,
            step = WildernessSessionStep.Town(
                TownSession.start(
                    town = town,
                    runtime = townRuntime,
                    content = content,
                ),
            ),
            lastResult = WildernessSessionResult(
                WildernessSessionResultStatus.EnteredTown,
                from = point,
                town = town.id,
            ),
        )
    }

    private fun enterHut(point: GridPoint): WildernessSession {
        val scene = scenes.firstOrNull { it.id == DslDefaultScenes.Towne.hut }
            ?: return notice(
                RuntimeUnavailableText.hutSceneMissing(),
                WildernessSessionResult(WildernessSessionResultStatus.EnteredHut, from = point),
            )
        return copy(
            step = WildernessSessionStep.Hut(scene),
            lastResult = WildernessSessionResult(WildernessSessionResultStatus.EnteredHut, from = point),
        )
    }

    private fun enterCastle(point: GridPoint): WildernessSession {
        val ydarg = content.ydarg ?: return notice(
            RuntimeUnavailableText.castlePending(),
            WildernessSessionResult(WildernessSessionResultStatus.EnteredCastle, from = point),
        )
        if (point != ydarg.entry) {
            return notice(
                RuntimeUnavailableText.castlePending(),
                WildernessSessionResult(WildernessSessionResultStatus.EnteredCastle, from = point),
            )
        }
        val castleRuntime = runtime.withRealm(
            runtime.realm.copy(
                currentMode = RuntimeMode.YdargCastle,
                currentTown = null,
                currentMap = worldMap.id,
            ),
        )
        return copy(
            runtime = castleRuntime,
            step = WildernessSessionStep.Ydarg(YdargSession.start(castleRuntime, content)),
            lastResult = WildernessSessionResult(WildernessSessionResultStatus.EnteredCastle, from = point),
        )
    }

    private fun answerCave(enter: Boolean): WildernessSession {
        require(step is WildernessSessionStep.CaveFound) { "wilderness session is not waiting for cave input" }
        if (!enter) {
            return copy(
                step = WildernessSessionStep.TurnStart,
                lastResult = lastResult?.copy(status = WildernessSessionResultStatus.CaveLeft)
                    ?: WildernessSessionResult(WildernessSessionResultStatus.CaveLeft),
            )
        }

        val events = wilderness.events
        if (party.resources.timeLeft <= events.caveEntryCost) {
            return copy(
                step = WildernessSessionStep.Notice(events.caveNoMoves, WildernessSessionStep.TurnStart),
                lastResult = lastResult?.copy(status = WildernessSessionResultStatus.CaveNoMoves)
                    ?: WildernessSessionResult(WildernessSessionResultStatus.CaveNoMoves),
            )
        }

        val depthDraw = runtime.drawRandom(events.caveDepthRange.first, events.caveDepthRange.last)
        val nextParty = depthDraw.runtime.activeParty.copy(
            resources = depthDraw.runtime.activeParty.resources.copy(
                timeLeft = depthDraw.runtime.activeParty.resources.timeLeft - events.caveEntryCost,
            ),
        )
        val dungeonRuntime = depthDraw.runtime
            .withActiveParty(nextParty)
            .withRealm(
                depthDraw.runtime.realm.copy(
                    currentMode = RuntimeMode.Dungeon,
                    currentTown = null,
                    currentMap = worldMap.id,
                ),
            )
        return copy(
            runtime = dungeonRuntime,
            step = WildernessSessionStep.DungeonHandoff(
                WildernessDungeonHandoff(events.caveDungeon, depthDraw.value),
            ),
            lastResult = WildernessSessionResult(
                WildernessSessionResultStatus.DungeonHandoff,
                from = lastResult?.from,
                to = lastResult?.to,
                terrain = lastResult?.terrain,
                dungeon = events.caveDungeon,
                dungeonLevels = depthDraw.value,
            ),
        )
    }

    private fun requestQuit(): WildernessSession =
        copy(
            step = WildernessSessionStep.QuitConfirm(prompts.quitConfirm),
            lastResult = WildernessSessionResult(WildernessSessionResultStatus.QuitRequested),
        )

    private fun answerQuit(yes: Boolean): WildernessSession {
        require(step is WildernessSessionStep.QuitConfirm) { "wilderness session is not confirming quit" }
        return if (yes) {
            finish()
        } else {
            copy(
                step = WildernessSessionStep.TurnStart,
                lastResult = WildernessSessionResult(WildernessSessionResultStatus.QuitCancelled),
            )
        }
    }

    private fun answerOutOfMoves(save: Boolean): WildernessSession {
        require(step is WildernessSessionStep.OutOfMoves) { "wilderness session is not waiting for out-of-moves input" }
        return finish(
            if (save) {
                WildernessSessionResultStatus.OutOfMovesSaveAndQuit
            } else {
                WildernessSessionResultStatus.OutOfMovesQuitNoSave
            },
        )
    }

    private fun finish(status: WildernessSessionResultStatus = WildernessSessionResultStatus.Finished): WildernessSession {
        val nextRuntime = runtime.withRealm(
            runtime.realm.copy(
                currentMode = RuntimeMode.Wilderness,
                currentTown = null,
                currentMap = worldMap.id,
            ),
        )
        return copy(
            runtime = nextRuntime,
            step = WildernessSessionStep.Finished(nextRuntime),
            lastResult = WildernessSessionResult(status),
        )
    }

    private fun notice(text: TextBlock, result: WildernessSessionResult): WildernessSession =
        copy(step = WildernessSessionStep.Notice(text, WildernessSessionStep.TurnStart), lastResult = result)

    private fun pending(text: TextBlock, status: WildernessSessionResultStatus): WildernessSession =
        notice(text, WildernessSessionResult(status))

    companion object {
        fun start(
            content: GameModel,
            runtime: RuntimeSessionState,
            worldMap: WorldMapGrid,
        ): WildernessSession =
            start(
                content = content.runtimeContent(),
                runtime = runtime,
                worldMap = worldMap,
            )

        fun start(
            content: RuntimeContent,
            runtime: RuntimeSessionState,
            worldMap: WorldMapGrid,
        ): WildernessSession =
            WildernessSession(
                runtime = runtime.withRealm(
                    runtime.realm.copy(
                        currentMap = worldMap.id,
                        currentMode = RuntimeMode.Wilderness,
                        currentTown = null,
                    ),
                ),
                worldMap = worldMap,
                content = content,
            )
    }
}

fun WildernessKeysModel.directionForInput(input: String): WildernessDirection? =
    when {
        moveNorth.matches(input) -> WildernessDirection.North
        moveSouth.matches(input) -> WildernessDirection.South
        moveEast.matches(input) -> WildernessDirection.East
        moveWest.matches(input) -> WildernessDirection.West
        else -> null
    }

fun RuntimeSessionState.teleportParty(
    directionKey: Int,
    distance: Int,
    worldMap: WorldMapGrid?,
): RuntimeSessionState {
    val direction = teleportDirection(directionKey) ?: return this
    val steps = teleportDistance(distance) * 2
    if (steps <= 0) return this
    val start = activeParty.wildernessPoint() ?: return this
    val destination = (1..steps).fold(start) { point, _ ->
        val next = point + direction.delta()
        if (worldMap == null || worldMap.isTeleportOpen(next)) next else point
    }
    return withActiveParty(
        activeParty.copy(
            position = WorldPosition.Wilderness(destination),
            facing = direction.facing(),
        ),
    )
}

private fun WildernessSystemModel.terrainIdFor(tile: Char?): TerrainId? =
    terrainTransitions.entries.firstOrNull { it.terrain.sourceMapCode == tile }?.terrain

private fun TerrainTransitionModel.entryFor(tile: Char): TerrainTransitionTextModel? =
    entries.firstOrNull { it.terrain.sourceMapCode == tile }

fun PartyState.wildernessPoint(): GridPoint? =
    (position as? WorldPosition.Wilderness)?.point

private fun RuntimeSessionState.terrainTransition(
    wilderness: WildernessSystemModel,
    fromTerrain: Char?,
    toTerrain: Char?,
): TerrainTransitionResult {
    if (fromTerrain == null || toTerrain == null || fromTerrain == toTerrain) {
        return TerrainTransitionResult(this, null)
    }
    val transition = wilderness.terrainTransitions
    val entry = transition.entryFor(toTerrain) ?: return TerrainTransitionResult(this, null)
    val draw = drawRandom(0, 99)
    return TerrainTransitionResult(
        runtime = draw.runtime,
        text = entry.text.takeIf { draw.value < transition.chancePercent },
    )
}

private fun RuntimeSessionState.pollWildernessMissions(
    missions: List<MissionModel>,
    currentTerrain: TerrainId?,
): MissionTriggerResult {
    var nextRuntime = this
    missions.forEach { mission ->
        if (mission.requires?.let { nextRuntime.activeParty.matches(it) } == false) {
            return@forEach
        }
        when (val trigger = mission.trigger) {
            is Trigger.RandomStep -> {
                val draw = nextRuntime.drawRandom(0, 99)
                nextRuntime = draw.runtime
                if (draw.value < trigger.percent) {
                    return MissionTriggerResult(nextRuntime, mission)
                }
            }
            is Trigger.AtPosition -> {
                if (nextRuntime.activeParty.wildernessPoint() == trigger.point) {
                    return MissionTriggerResult(nextRuntime, mission)
                }
            }
            is Trigger.AtLocation -> {
                val point = nextRuntime.locations.firstOrNull { it.location == trigger.location }?.point
                if (point != null && nextRuntime.activeParty.wildernessPoint() == point) {
                    return MissionTriggerResult(nextRuntime, mission)
                }
            }
            is Trigger.OnTerrain -> {
                if (currentTerrain == trigger.terrain) {
                    return MissionTriggerResult(nextRuntime, mission)
                }
            }
        }
    }
    return MissionTriggerResult(nextRuntime, null)
}

private fun PartyState.matches(ast: BoolAst): Boolean {
    val party = this
    val evaluator = object : IntAstEvaluator() {
        override fun flagIs(slug: String, native: String): Boolean =
            party.flags.any { it.flag.slug == slug && it.native == native }

        override fun milestoneAchieved(milestone: GameMilestoneId): Boolean =
            milestone in party.milestones

        override fun memberField(member: Int, field: String): Int {
            val record = party.members.getOrNull(member - 1) ?: return 0
            return when (field) {
                "hit_points" -> record.hitPoints.current
                "health" -> record.health
                "magic" -> record.mana.current
                "level" -> record.level
                "wisdom" -> record.wisdom
                "charisma" -> record.charisma
                "class" -> record.pcClass.sourceId
                "dead" -> if (record.dead) 1 else 0
                else -> 0
            }
        }

        override fun memberFlag(member: Int, name: String): Boolean {
            val record = party.members.getOrNull(member - 1) ?: return false
            return when (name) {
                "dead" -> record.dead
                "spellcaster" -> !record.dead && record.pcClass in setOf(PcClass.Mage, PcClass.Priest)
                else -> false
            }
        }

        override fun partyScalar(name: String): Int =
            when (name) {
                "gold" -> party.resources.gold
                "torches" -> party.resources.torches
                "food" -> party.resources.food
                "time_left" -> party.resources.timeLeft
                "rested_hours" -> party.resources.restedHours
                "member_count" -> party.counters.memberCount ?: party.members.count { !it.dead }
                else -> 0
            }

        override fun inventoryPoolCount(pool: ItemPoolId): Int =
            party.countInventoryItems(pool)
    }
    return evaluator.bool(ast)
}

private fun RuntimeSessionState.applyWildernessUpkeep(rules: WildernessUpkeepRules): WildernessUpkeepResult {
    val advancedHour = withRealm(realm.copy(hour = (realm.hour + 1).positiveMod(24)))
    val manaResult = advancedHour.regenerateMana(rules)
    val foodResult = manaResult.runtime.applyFoodAndFatigue(rules)
    return foodResult.copy(messages = manaResult.messages + foodResult.messages)
}

private fun RuntimeSessionState.regenerateMana(rules: WildernessUpkeepRules): WildernessUpkeepResult {
    var runtime = this
    val members = activeParty.members.map { member ->
        if (member.pcClass !in setOf(PcClass.Mage, PcClass.Priest) || member.mana.current >= member.wisdom) {
            member
        } else {
            val draw = runtime.drawRandom(0, 99)
            runtime = draw.runtime
            if (draw.value < rules.manaRegenChance) {
                member.copy(mana = member.mana.copy(current = (member.mana.current + 1).coerceAtMost(member.wisdom)))
            } else {
                member
            }
        }
    }
    return WildernessUpkeepResult(
        runtime = runtime.withActiveParty(runtime.activeParty.copy(members = members)),
        messages = emptyList(),
        allDead = false,
    )
}

private fun RuntimeSessionState.applyFoodAndFatigue(rules: WildernessUpkeepRules): WildernessUpkeepResult {
    val party = activeParty
    val foodAfterTick = if (rules.foodTickEvery > 0 && party.resources.timeLeft % rules.foodTickEvery == 0) {
        (party.resources.food - party.foodTickCost(rules)).coerceAtLeast(0)
    } else {
        party.resources.food
    }
    val starvation = party.applyStarvation(foodAfterTick, rules.starveFoodThreshold, rules.starveDamage)
    val restedHours = party.resources.restedHours - rules.restedHoursCostPerTurn
    val fatigue = starvation.party.applyFatigue(restedHours, rules.fatigue)
    val nextParty = fatigue.party.copy(
        resources = fatigue.party.resources.copy(
            food = foodAfterTick,
            restedHours = restedHours,
        ),
    )
    return WildernessUpkeepResult(
        runtime = withActiveParty(nextParty),
        messages = starvation.messages + fatigue.messages,
        allDead = nextParty.members.isNotEmpty() && nextParty.members.all { it.dead },
    )
}

private data class PartyUpkeepMutation(
    val party: PartyState,
    val messages: List<TextBlock>,
)

private fun PartyState.foodTickCost(rules: WildernessUpkeepRules): Int {
    val leaderLevelCost = if (rules.foodCostLeaderDiv > 0) {
        (members.firstOrNull()?.level ?: 0) / rules.foodCostLeaderDiv
    } else {
        0
    }
    return partySizeForFood() + leaderLevelCost
}

private fun PartyState.partySizeForFood(): Int =
    counters.memberCount ?: members.size

private fun PartyState.applyStarvation(foodAfterTick: Int, threshold: Int, damage: Int): PartyUpkeepMutation {
    if (foodAfterTick > threshold || damage == 0) return PartyUpkeepMutation(this, emptyList())
    val messages = mutableListOf<TextBlock>()
    val nextMembers = members.map { member ->
        if (member.dead) {
            member
        } else {
            val nextHp = member.hitPoints.current - damage
            if (nextHp < 1) {
                messages += inlineText("${member.name} starved", "to death!!!")
                member.copy(hitPoints = member.hitPoints.copy(current = 0), dead = true)
            } else {
                member.copy(hitPoints = member.hitPoints.copy(current = nextHp))
            }
        }
    }
    return PartyUpkeepMutation(copy(members = nextMembers), messages)
}

private fun PartyState.applyFatigue(
    restedHours: Int,
    rules: List<WildernessFatigueRule>,
): PartyUpkeepMutation {
    val rule = rules.firstOrNull { it.matches(restedHours) }
        ?: return PartyUpkeepMutation(this, emptyList())
    val nextParty = if (rule.drain) {
        copy(
            members = members.map { member ->
                if (member.hitPoints.current > 1) {
                    member.copy(hitPoints = member.hitPoints.copy(current = member.hitPoints.current - 1))
                } else {
                    member
                }
            },
        )
    } else {
        this
    }
    return PartyUpkeepMutation(nextParty, listOf(rule.text))
}

private fun WildernessFatigueRule.matches(restedHours: Int): Boolean =
    when (match) {
        WildernessFatigueMatch.Equal -> restedHours == threshold
        WildernessFatigueMatch.LessThan -> restedHours < threshold
    }

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

operator fun GridPoint.plus(other: GridPoint): GridPoint =
    GridPoint(x + other.x, y + other.y)

private fun teleportDirection(key: Int): WildernessDirection? =
    when (key) {
        8, '8'.code -> WildernessDirection.North
        2, '2'.code -> WildernessDirection.South
        6, '6'.code -> WildernessDirection.East
        4, '4'.code -> WildernessDirection.West
        else -> null
    }

private fun teleportDistance(value: Int): Int =
    when (value) {
        in 1..9 -> value
        in '1'.code..'9'.code -> value - '0'.code
        else -> 0
    }

private fun WorldMapGrid.isTeleportOpen(point: GridPoint): Boolean =
    tileAt(point)?.let { it != DslDefaultTerrain.mountain.sourceMapCode } == true

private fun Int.positiveMod(divisor: Int): Int =
    ((this % divisor) + divisor) % divisor

private fun Char.isImpassableTerrain(): Boolean =
    this in ImpassableTerrainTiles

private val ImpassableTerrainTiles: Set<Char> =
    listOfNotNull(
        DslDefaultTerrain.mountain.sourceMapCode,
        DslDefaultTerrain.water.sourceMapCode,
    ).toSet()
