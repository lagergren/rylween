// Reusable runtime session for using one item out of inventory.
// Routes weapon/armor selections into the equipment ready flow; routes
// authored inventory item-use scripts into the shared script continuation.

enum class InventoryItemUseStatus {
    Used,
    Equipped,
    NoItems,
    NoItem,
    InvalidSlot,
    NotUsable,
}

data class InventoryItemUseResult(
    val status: InventoryItemUseStatus,
    val runtime: RuntimeSessionState,
    val item: ItemId? = null,
    val slot: Int? = null,
)

data class InventoryItemChoice(
    val key: Char,
    val slot: Int,
    val item: ItemId,
    val label: String,
    val kind: ItemKind,
)

sealed interface InventoryItemUseStep {
    data class ChooseItem(val prompt: TextBlock, val choices: List<InventoryItemChoice>) : InventoryItemUseStep
    data class Script(
        val use: ItemUseModel,
        val slot: Int,
        val item: ItemId,
        val continuation: TownServiceContinuation,
    ) : InventoryItemUseStep
    data class Equipment(val session: EquipmentPromptSession, val item: ItemId, val slot: Int) : InventoryItemUseStep
    data class Message(val text: TextBlock, val next: InventoryItemUseStep) : InventoryItemUseStep
    data class Finished(val result: InventoryItemUseResult) : InventoryItemUseStep {
        val runtime: RuntimeSessionState get() = result.runtime
    }
}

data class InventoryItemUseSession(
    val runtime: RuntimeSessionState,
    val itemUses: List<ItemUseModel>,
    val itemRecords: List<ItemRecordModel>,
    val allowedKinds: Set<ItemKind>? = null,
    val choosePrompt: TextBlock = InventoryUsePromptText.use,
    val step: InventoryItemUseStep,
    val lastResult: InventoryItemUseResult? = null,
) {
    val party: PartyState get() = runtime.activeParty

    fun apply(input: InventoryItemUseInput): InventoryItemUseSession =
        when (val current = step) {
            is InventoryItemUseStep.ChooseItem -> {
                val slot = (input as? InventoryItemUseInput.InventorySlot)
                    ?: error("inventory item-use session is choosing an inventory slot")
                chooseSlot(slot.slot)
            }
            is InventoryItemUseStep.Equipment -> {
                val equipmentInput = (input as? InventoryItemUseInput.Equipment)
                    ?: error("inventory item-use session is running equipment prompts")
                replaceEquipmentSession(current.session.apply(equipmentInput.input))
            }
            is InventoryItemUseStep.Message -> {
                require(input is InventoryItemUseInput.Continue) { "inventory item-use session is showing a message" }
                val nextRuntime = (current.next as? InventoryItemUseStep.Finished)?.runtime ?: runtime
                copy(runtime = nextRuntime, step = current.next)
            }
            is InventoryItemUseStep.Script -> error("inventory item-use script must be driven by a script runner")
            is InventoryItemUseStep.Finished -> error("inventory item-use session is already finished: $current")
        }

    fun replaceEquipmentSession(session: EquipmentPromptSession): InventoryItemUseSession =
        if (session.step is EquipmentPromptStep.Finished) {
            val current = step as? InventoryItemUseStep.Equipment
                ?: error("inventory item-use session is not running equipment prompts")
            val nextRuntime = runtime.withActiveParty(session.party)
            val status = when (session.step.result) {
                is EquipmentActionResult.Equipped -> InventoryItemUseStatus.Equipped
                else -> InventoryItemUseStatus.NotUsable
            }
            finish(status, nextRuntime, item = current.item, slot = current.slot)
        } else {
            val current = step as? InventoryItemUseStep.Equipment
                ?: error("inventory item-use session is not running equipment prompts")
            copy(runtime = runtime.withActiveParty(session.party), step = current.copy(session = session))
        }

    fun replaceScriptContinuation(continuation: TownServiceContinuation): InventoryItemUseSession {
        val current = step as? InventoryItemUseStep.Script
            ?: error("inventory item-use session is not running a script")
        return copy(
            runtime = continuation.runtime,
            step = current.copy(continuation = continuation),
        )
    }

    fun finishScript(nextRuntime: RuntimeSessionState): InventoryItemUseSession {
        val current = step as? InventoryItemUseStep.Script
            ?: error("inventory item-use session is not running a script")
        return finish(InventoryItemUseStatus.Used, nextRuntime, current.item, current.slot)
    }

    private fun chooseSlot(slot: Int): InventoryItemUseSession {
        if (slot !in 1..InventorySaveSlotMax) {
            return message(
                InventoryItemUseStatus.InvalidSlot,
                InventoryUsePromptText.invalidSlot(slot),
                slot = slot,
            )
        }
        val item = party.inventoryItem(slot)
            ?: return message(InventoryItemUseStatus.NoItem, InventoryUsePromptText.noItem(), slot = slot)
        val record = itemRecords.associateBy { it.id }[item]
            ?: return message(InventoryItemUseStatus.NotUsable, InventoryUsePromptText.notUsable(item.slug), item, slot)
        if (allowedKinds?.contains(record.kind) == false) {
            return message(InventoryItemUseStatus.NotUsable, InventoryUsePromptText.notUsable(record.displayName), item, slot)
        }
        val use = itemUses.firstOrNull { it.id == item && ItemUseContext.Inventory in it.contexts }
        return when {
            use != null -> beginScript(use, item, slot)
            record.kind == ItemKind.Weapon -> beginEquipment(EquipmentCommand.ReadyWeapon, item, slot)
            record.kind == ItemKind.Armor -> beginEquipment(EquipmentCommand.ReadyArmor, item, slot)
            else -> message(InventoryItemUseStatus.NotUsable, InventoryUsePromptText.notUsable(record.displayName), item, slot)
        }
    }

    private fun beginScript(use: ItemUseModel, item: ItemId, slot: Int): InventoryItemUseSession {
        val nextRuntime = if (use.consume == ItemConsumePolicy.BeforeUse) {
            runtime.withActiveParty(party.withInventoryItem(slot, null))
        } else {
            runtime
        }
        return copy(
            runtime = nextRuntime,
            step = InventoryItemUseStep.Script(
                use = use,
                slot = slot,
                item = item,
                continuation = TownServiceContinuation.start(nextRuntime).copy(
                    locals = mapOf("inv_slot" to slot),
                    activeInventorySlot = slot,
                ),
            ),
        )
    }

    private fun beginEquipment(command: EquipmentCommand, item: ItemId, slot: Int): InventoryItemUseSession =
        copy(
            step = InventoryItemUseStep.Equipment(
                session = EquipmentPromptSession.startWithInventorySlot(command, party, slot, itemRecords),
                item = item,
                slot = slot,
            ),
        )

    private fun message(
        status: InventoryItemUseStatus,
        text: TextBlock,
        item: ItemId? = null,
        slot: Int? = null,
    ): InventoryItemUseSession {
        val finished = InventoryItemUseStep.Finished(result(status, runtime, item, slot))
        return copy(step = InventoryItemUseStep.Message(text, finished), lastResult = finished.result)
    }

    private fun finish(
        status: InventoryItemUseStatus,
        nextRuntime: RuntimeSessionState,
        item: ItemId? = null,
        slot: Int? = null,
    ): InventoryItemUseSession {
        val result = result(status, nextRuntime, item, slot)
        return copy(runtime = nextRuntime, step = InventoryItemUseStep.Finished(result), lastResult = result)
    }

    private fun result(
        status: InventoryItemUseStatus,
        nextRuntime: RuntimeSessionState,
        item: ItemId? = null,
        slot: Int? = null,
    ): InventoryItemUseResult =
        InventoryItemUseResult(status, nextRuntime, item, slot)

    companion object {
        fun start(
            runtime: RuntimeSessionState,
            itemUses: List<ItemUseModel> = emptyList(),
            itemRecords: List<ItemRecordModel> = emptyList(),
            allowedKinds: Set<ItemKind>? = null,
            choosePrompt: TextBlock = InventoryUsePromptText.use,
        ): InventoryItemUseSession {
            val choices = runtime.activeParty.inventoryItemChoices(itemRecords)
                .filter { choice -> allowedKinds?.contains(choice.kind) ?: true }
            val result = InventoryItemUseResult(InventoryItemUseStatus.NoItems, runtime)
            val step = if (choices.isEmpty()) {
                InventoryItemUseStep.Message(
                    InventoryUsePromptText.noItems(),
                    InventoryItemUseStep.Finished(result),
                )
            } else {
                InventoryItemUseStep.ChooseItem(InventoryUsePromptText.choose(choosePrompt, choices), choices)
            }
            return InventoryItemUseSession(
                runtime = runtime,
                itemUses = itemUses,
                itemRecords = itemRecords,
                allowedKinds = allowedKinds,
                choosePrompt = choosePrompt,
                step = step,
                lastResult = result.takeIf { choices.isEmpty() },
            )
        }
    }
}

object InventoryUsePromptText {
    val use = inlineText("Use what?")
    val quaff = inlineText("Quaff what?")

    fun choose(choices: List<InventoryItemChoice>): TextBlock =
        combineTextBlocks(
            use,
            TextBlock.Inline(choices.map { "${it.key}. ${it.label}" }),
        )

    fun choose(prompt: TextBlock, choices: List<InventoryItemChoice>): TextBlock =
        combineTextBlocks(prompt, TextBlock.Inline(choices.map { "${it.key}. ${it.label}" }))

    fun noItems(): TextBlock =
        inlineText("You are not carrying", "anything.")

    fun noItem(): TextBlock =
        inlineText("There is nothing", "in that slot.")

    fun invalidSlot(slot: Int): TextBlock =
        inlineText("Inventory slot $slot", "does not exist.")

    fun notUsable(itemName: String): TextBlock =
        inlineText("Nothing happens", "with $itemName.")
}
