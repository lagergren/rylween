package rylween.runtime

import rylween.dsl.*

sealed interface DungeonSessionInput {
    object Descend : DungeonSessionInput
    object Ascend : DungeonSessionInput
    object Help : DungeonSessionInput
    object Drop : DungeonSessionInput
    object RemoveArmor : DungeonSessionInput
    object RemoveWeapon : DungeonSessionInput
    object ReadyWeapon : DungeonSessionInput
    object ReadyArmor : DungeonSessionInput
    object Quaff : DungeonSessionInput
    object CastSpell : DungeonSessionInput
    object ViewParty : DungeonSessionInput
    object Continue : DungeonSessionInput
}

enum class DungeonSessionResultStatus {
    Started,
    Descended,
    Ascended,
    LeftDungeon,
    NoTorches,
    AtMaxDepth,
    HelpShown,
    DropUnavailable,
    ViewPartyUnavailable,
    EquipmentRequested,
    EquipmentUsed,
    EquipmentUnavailable,
    InventoryRequested,
    InventoryUsed,
    InventoryUnavailable,
    CastSpellRequested,
    CastSpellUsed,
    CastSpellUnavailable,
    EventCompleted,
}

data class DungeonSessionResult(
    val status: DungeonSessionResultStatus,
    val dungeon: DungeonId,
    val depth: Int,
    val maxDepth: Int,
)

sealed interface DungeonSessionStep {
    object Explore : DungeonSessionStep
    data class Messages(val blocks: List<TextBlock>, val next: DungeonSessionStep) : DungeonSessionStep
    data class Notice(val text: TextBlock, val next: DungeonSessionStep = Explore) : DungeonSessionStep
    data class Equipment(val session: EquipmentPromptSession) : DungeonSessionStep
    data class InventoryItemUse(val session: InventoryItemUseSession) : DungeonSessionStep
    data class PriestSpell(val session: PriestSpellSession) : DungeonSessionStep
    data class Event(val event: DungeonEventModel, val continuation: TownServiceContinuation? = null) :
        DungeonSessionStep
    data class RandomEncounter(val event: DungeonEventModel, val session: BattleSession? = null) : DungeonSessionStep
    data class Finished(val runtime: RuntimeSessionState) : DungeonSessionStep
}

private data class DungeonLevelEventPlan(
    val runtime: RuntimeSessionState,
    val levelEvents: Map<Int, DungeonEventId>,
)

data class DungeonSession(
    val runtime: RuntimeSessionState,
    val worldMap: WorldMapGrid,
    val dungeon: DungeonId,
    val maxDepth: Int,
    val depth: Int = 1,
    val levelEvents: Map<Int, DungeonEventId> = emptyMap(),
    val keys: DungeonKeysModel = DungeonKeysModel(),
    val content: RuntimeContent = RuntimeContent(),
    val step: DungeonSessionStep = DungeonSessionStep.Explore,
    val lastResult: DungeonSessionResult? = null,
) {
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
    val party: PartyState get() = runtime.activeParty

    fun apply(input: DungeonSessionInput): DungeonSession =
        when (step) {
            DungeonSessionStep.Explore -> when (input) {
                DungeonSessionInput.Descend -> descend()
                DungeonSessionInput.Ascend -> ascend()
                DungeonSessionInput.Help -> copy(
                    step = DungeonSessionStep.Messages(keys.effectiveHelpPages(), DungeonSessionStep.Explore),
                    lastResult = result(DungeonSessionResultStatus.HelpShown),
                )
                DungeonSessionInput.Drop ->
                    notice(DungeonPromptText.dropPending(), DungeonSessionResultStatus.DropUnavailable)
                DungeonSessionInput.RemoveArmor -> equipment(EquipmentCommand.RemoveArmor)
                DungeonSessionInput.RemoveWeapon -> equipment(EquipmentCommand.RemoveWeapon)
                DungeonSessionInput.ReadyWeapon -> equipment(EquipmentCommand.ReadyWeapon)
                DungeonSessionInput.ReadyArmor -> equipment(EquipmentCommand.ReadyArmor)
                DungeonSessionInput.Quaff -> quaff()
                DungeonSessionInput.CastSpell -> castSpell()
                DungeonSessionInput.ViewParty ->
                    notice(DungeonPromptText.viewPartyPending(), DungeonSessionResultStatus.ViewPartyUnavailable)
                DungeonSessionInput.Continue -> this
            }
            is DungeonSessionStep.Messages -> {
                require(input is DungeonSessionInput.Continue) { "dungeon session is showing messages" }
                continueMessages()
            }
            is DungeonSessionStep.Notice -> {
                require(input is DungeonSessionInput.Continue) { "dungeon session is showing a notice" }
                continueNotice()
            }
            is DungeonSessionStep.Equipment,
            is DungeonSessionStep.InventoryItemUse,
            is DungeonSessionStep.PriestSpell,
            is DungeonSessionStep.Event,
            is DungeonSessionStep.RandomEncounter -> error("nested dungeon session must be driven by its own adapter")
            is DungeonSessionStep.Finished -> error("dungeon session is already finished")
        }

    fun continueMessages(): DungeonSession {
        val current = step as? DungeonSessionStep.Messages
            ?: error("dungeon session is not showing messages")
        return copy(step = current.next)
    }

    fun continueNotice(): DungeonSession {
        val current = step as? DungeonSessionStep.Notice
            ?: error("dungeon session is not showing a notice")
        return copy(step = current.next)
    }

    fun replaceEquipmentSession(session: EquipmentPromptSession): DungeonSession {
        require(step is DungeonSessionStep.Equipment) { "dungeon session is not running equipment prompts" }
        val nextRuntime = runtime.withActiveParty(session.party)
        return if (session.step is EquipmentPromptStep.Finished) {
            val status = when (session.step.result) {
                is EquipmentActionResult.Equipped,
                is EquipmentActionResult.Unequipped -> DungeonSessionResultStatus.EquipmentUsed
                else -> DungeonSessionResultStatus.EquipmentUnavailable
            }
            copy(runtime = nextRuntime, step = DungeonSessionStep.Explore, lastResult = result(status))
        } else {
            copy(runtime = nextRuntime, step = DungeonSessionStep.Equipment(session))
        }
    }

    fun replaceInventoryItemUseSession(session: InventoryItemUseSession): DungeonSession {
        require(step is DungeonSessionStep.InventoryItemUse) { "dungeon session is not running inventory item use" }
        return if (session.step is InventoryItemUseStep.Finished) {
            val status = when (session.lastResult?.status) {
                InventoryItemUseStatus.Used,
                InventoryItemUseStatus.Equipped -> DungeonSessionResultStatus.InventoryUsed
                else -> DungeonSessionResultStatus.InventoryUnavailable
            }
            copy(runtime = session.runtime, step = DungeonSessionStep.Explore, lastResult = result(status))
        } else {
            copy(runtime = session.runtime, step = DungeonSessionStep.InventoryItemUse(session))
        }
    }

    fun replacePriestSpellSession(session: PriestSpellSession): DungeonSession {
        require(step is DungeonSessionStep.PriestSpell) { "dungeon session is not running priest spell casting" }
        return if (session.step is PriestSpellStep.Finished) {
            val status = when (session.lastResult?.status) {
                PriestSpellStatus.Cast -> DungeonSessionResultStatus.CastSpellUsed
                else -> DungeonSessionResultStatus.CastSpellUnavailable
            }
            copy(runtime = session.runtime, step = DungeonSessionStep.Explore, lastResult = result(status))
        } else {
            copy(runtime = session.runtime, step = DungeonSessionStep.PriestSpell(session))
        }
    }

    fun replaceEventContinuation(continuation: TownServiceContinuation): DungeonSession {
        val current = step as? DungeonSessionStep.Event
            ?: error("dungeon session is not running an event script")
        return copy(
            runtime = continuation.runtime,
            step = current.copy(continuation = continuation),
        )
    }

    fun replaceRandomEncounterSession(session: BattleSession): DungeonSession {
        val current = step as? DungeonSessionStep.RandomEncounter
            ?: error("dungeon session is not running a random encounter")
        return if (session.step is BattleSessionStep.Finished) {
            finishEvent(session.runtime)
        } else {
            copy(runtime = session.runtime, step = current.copy(session = session))
        }
    }

    fun finishEvent(nextRuntime: RuntimeSessionState): DungeonSession {
        val event = when (val current = step) {
            is DungeonSessionStep.Event -> current.event
            is DungeonSessionStep.RandomEncounter -> current.event
            else -> error("dungeon session is not resolving an event")
        }
        return copy(
            runtime = nextRuntime.asDungeonRuntime(),
            levelEvents = if (event.disposition == DungeonEventDisposition.ClearAfterRun) {
                levelEvents - depth
            } else {
                levelEvents
            },
            step = DungeonSessionStep.Explore,
            lastResult = result(DungeonSessionResultStatus.EventCompleted),
        )
    }

    private fun descend(): DungeonSession =
        when (val descent = party.tryDungeonDescent(depth, maxDepth)) {
            is DungeonDescentResult.Descended -> copy(
                runtime = runtime.withActiveParty(descent.party),
                depth = depth + 1,
                step = DungeonSessionStep.Messages(
                    listOf(DungeonPromptText.enterLevel(depth + 1)),
                    nextEventStep(depth + 1),
                ),
                lastResult = result(DungeonSessionResultStatus.Descended),
            )
            is DungeonDescentResult.NoTorches ->
                notice(DungeonPromptText.noTorches(), DungeonSessionResultStatus.NoTorches)
            is DungeonDescentResult.AtMaxDepth ->
                notice(DungeonPromptText.atMaxDepth(), DungeonSessionResultStatus.AtMaxDepth)
        }

    private fun ascend(): DungeonSession =
        if (depth <= 1) {
            val nextRuntime = runtime.withRealm(
                runtime.realm.copy(
                    currentMode = RuntimeMode.Wilderness,
                    currentTown = null,
                    currentMap = worldMap.id,
                ),
            )
            copy(
                runtime = nextRuntime,
                step = DungeonSessionStep.Messages(
                    listOf(DungeonPromptText.leaveCavern()),
                    DungeonSessionStep.Finished(nextRuntime),
                ),
                lastResult = result(DungeonSessionResultStatus.LeftDungeon),
            )
        } else {
            copy(
                depth = depth - 1,
                step = DungeonSessionStep.Messages(
                    listOf(DungeonPromptText.enterLevel(depth - 1)),
                    nextEventStep(depth - 1),
                ),
                lastResult = result(DungeonSessionResultStatus.Ascended),
            )
        }

    private fun quaff(): DungeonSession =
        copy(
            step = DungeonSessionStep.InventoryItemUse(
                InventoryItemUseSession.start(
                    runtime = runtime,
                    content = content,
                    allowedKinds = setOf(ItemKind.Potion),
                    choosePrompt = content.prompts.inventoryUse.quaff,
                ),
            ),
            lastResult = result(DungeonSessionResultStatus.InventoryRequested),
        )

    private fun castSpell(): DungeonSession =
        copy(
            step = DungeonSessionStep.PriestSpell(
                PriestSpellSession.start(
                    runtime = runtime,
                    spells = spells,
                    place = PriestSpellPlace.Dungeon,
                    prompts = content.prompts.priest,
                ),
            ),
            lastResult = result(DungeonSessionResultStatus.CastSpellRequested),
        )

    private fun equipment(command: EquipmentCommand): DungeonSession =
        copy(
            step = DungeonSessionStep.Equipment(
                EquipmentPromptSession.start(command, party, itemRecords, content.prompts.equipment),
            ),
            lastResult = result(DungeonSessionResultStatus.EquipmentRequested),
        )

    private fun notice(text: TextBlock, status: DungeonSessionResultStatus): DungeonSession =
        copy(step = DungeonSessionStep.Notice(text, DungeonSessionStep.Explore), lastResult = result(status))

    private fun result(status: DungeonSessionResultStatus): DungeonSessionResult =
        DungeonSessionResult(status, dungeon, depth, maxDepth)

    private fun nextEventStep(targetDepth: Int): DungeonSessionStep {
        val eventId = levelEvents[targetDepth] ?: return DungeonSessionStep.Explore
        val event = dungeons.events.firstOrNull { it.id == eventId } ?: return DungeonSessionStep.Explore
        return if (event.randomEncounterTier != null && event.ops.isEmpty()) {
            DungeonSessionStep.RandomEncounter(event)
        } else {
            DungeonSessionStep.Event(event)
        }
    }

    private fun RuntimeSessionState.asDungeonRuntime(): RuntimeSessionState =
        if (realm.currentMode == RuntimeMode.GameOver) {
            this
        } else {
            withRealm(
                realm.copy(
                    currentMode = RuntimeMode.Dungeon,
                    currentTown = null,
                    currentMap = worldMap.id,
                ),
            )
        }

    companion object {
        fun start(
            runtime: RuntimeSessionState,
            worldMap: WorldMapGrid,
            dungeon: DungeonId,
            levels: Int,
            content: RuntimeContent = RuntimeContent(),
            dungeons: DungeonSystemModel = content.dungeons,
        ): DungeonSession {
            val depth = levels.coerceAtLeast(1)
            val dungeonRuntime = runtime.withRealm(
                runtime.realm.copy(
                    currentMode = RuntimeMode.Dungeon,
                    currentTown = null,
                    currentMap = worldMap.id,
                ),
            )
            val plan = dungeons.planLevelEvents(dungeonRuntime, dungeon, depth)
            val base = DungeonSession(
                runtime = plan.runtime,
                worldMap = worldMap,
                dungeon = dungeon,
                maxDepth = depth,
                levelEvents = plan.levelEvents,
                content = content.copy(dungeons = dungeons),
                lastResult = DungeonSessionResult(
                    DungeonSessionResultStatus.Started,
                    dungeon,
                    1,
                    depth,
                ),
            )
            return base.copy(step = base.nextEventStep(1))
        }
    }
}

private fun DungeonSystemModel.planLevelEvents(
    runtime: RuntimeSessionState,
    dungeon: DungeonId,
    maxDepth: Int,
): DungeonLevelEventPlan {
    val scenario = scenarios.firstOrNull { it.dungeon == dungeon }
    var nextRuntime = runtime
    val randomLevelEvents = (1..maxDepth).mapNotNull { level ->
        val draw = scenario?.randomEvents.orEmpty().drawEvent(nextRuntime) ?: return@mapNotNull null
        nextRuntime = draw.runtime
        level to draw.event
    }.toMap()
    val fixedLevelEvents = scenario
        ?.fixedLevelEvents
        .orEmpty()
        .filterKeys { it in 1..maxDepth }
    return DungeonLevelEventPlan(
        runtime = nextRuntime,
        levelEvents = randomLevelEvents + fixedLevelEvents,
    )
}

private data class DungeonRandomEventDraw(
    val runtime: RuntimeSessionState,
    val event: DungeonEventId,
)

private fun List<DungeonRandomEventModel>.drawEvent(runtime: RuntimeSessionState): DungeonRandomEventDraw? {
    if (isEmpty()) return null
    val totalWeight = sumOf { it.weight }
    if (totalWeight <= 0) return null
    val draw = runtime.drawRandom(1, totalWeight)
    var running = 0
    val event = first { random ->
        running += random.weight
        draw.value <= running
    }.event
    return DungeonRandomEventDraw(draw.runtime, event)
}

object DungeonPromptText {
    fun levelMenu(depth: Int): TextBlock =
        inlineText("You are on level $depth.", "", "(D) Descend", "(A) Ascend", "")

    fun enterLevel(depth: Int): TextBlock =
        inlineText("You enter level $depth.")

    fun atMaxDepth(): TextBlock =
        inlineText("The cave doesn't", "go deeper.")

    fun noTorches(): TextBlock =
        inlineText("You have used your last", "torch. You can't go", "any further.", "")

    fun leaveCavern(): TextBlock =
        inlineText("You leave the", "hideous cavern!", "")

    fun viewPartyPending(): TextBlock =
        inlineText("Dungeon party view is", "not ported yet.")

    fun dropPending(): TextBlock =
        inlineText("Dropping items is not", "ported yet.")
}
