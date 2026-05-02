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
    data class Finished(val runtime: RuntimeSessionState) : DungeonSessionStep
}

data class DungeonSession(
    val runtime: RuntimeSessionState,
    val worldMap: WorldMapGrid,
    val dungeon: DungeonId,
    val maxDepth: Int,
    val depth: Int = 1,
    val keys: DungeonKeysModel = DungeonKeysModel(),
    val spells: List<SpellModel> = emptyList(),
    val itemUses: List<ItemUseModel> = emptyList(),
    val itemRecords: List<ItemRecordModel> = emptyList(),
    val step: DungeonSessionStep = DungeonSessionStep.Explore,
    val lastResult: DungeonSessionResult? = null,
) {
    val party: PartyState get() = runtime.activeParty

    fun apply(input: DungeonSessionInput): DungeonSession =
        when (step) {
            DungeonSessionStep.Explore -> when (input) {
                DungeonSessionInput.Descend -> descend()
                DungeonSessionInput.Ascend -> ascend()
                DungeonSessionInput.Help -> copy(
                    step = DungeonSessionStep.Messages(keys.helpPages, DungeonSessionStep.Explore),
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
            is DungeonSessionStep.PriestSpell -> error("nested dungeon session must be driven by its own adapter")
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

    private fun descend(): DungeonSession =
        when (val descent = party.tryDungeonDescent(depth, maxDepth)) {
            is DungeonDescentResult.Descended -> copy(
                runtime = runtime.withActiveParty(descent.party),
                depth = depth + 1,
                step = DungeonSessionStep.Messages(
                    listOf(DungeonPromptText.enterLevel(depth + 1)),
                    DungeonSessionStep.Explore,
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
                    DungeonSessionStep.Explore,
                ),
                lastResult = result(DungeonSessionResultStatus.Ascended),
            )
        }

    private fun quaff(): DungeonSession =
        copy(
            step = DungeonSessionStep.InventoryItemUse(
                InventoryItemUseSession.start(
                    runtime = runtime,
                    itemUses = itemUses,
                    itemRecords = itemRecords,
                    allowedKinds = setOf(ItemKind.Potion),
                    choosePrompt = InventoryUsePromptText.quaff,
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
                ),
            ),
            lastResult = result(DungeonSessionResultStatus.CastSpellRequested),
        )

    private fun equipment(command: EquipmentCommand): DungeonSession =
        copy(
            step = DungeonSessionStep.Equipment(
                EquipmentPromptSession.start(command, party, itemRecords),
            ),
            lastResult = result(DungeonSessionResultStatus.EquipmentRequested),
        )

    private fun notice(text: TextBlock, status: DungeonSessionResultStatus): DungeonSession =
        copy(step = DungeonSessionStep.Notice(text, DungeonSessionStep.Explore), lastResult = result(status))

    private fun result(status: DungeonSessionResultStatus): DungeonSessionResult =
        DungeonSessionResult(status, dungeon, depth, maxDepth)

    companion object {
        fun start(
            runtime: RuntimeSessionState,
            worldMap: WorldMapGrid,
            dungeon: DungeonId,
            levels: Int,
            spells: List<SpellModel> = emptyList(),
            itemUses: List<ItemUseModel> = emptyList(),
            itemRecords: List<ItemRecordModel> = emptyList(),
        ): DungeonSession =
            DungeonSession(
                runtime = runtime.withRealm(
                    runtime.realm.copy(
                        currentMode = RuntimeMode.Dungeon,
                        currentTown = null,
                        currentMap = worldMap.id,
                    ),
                ),
                worldMap = worldMap,
                dungeon = dungeon,
                maxDepth = levels.coerceAtLeast(1),
                spells = spells,
                itemUses = itemUses,
                itemRecords = itemRecords,
                lastResult = DungeonSessionResult(
                    DungeonSessionResultStatus.Started,
                    dungeon,
                    1,
                    levels.coerceAtLeast(1),
                ),
            )
    }
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
