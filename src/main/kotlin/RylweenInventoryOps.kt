// Pure state operations on PartyState for inventory, equipment, food, torches,
// and the small camp-gear projection. No UI, no RNG, no scripts.

sealed interface InventoryPlacementResult {
    data class Placed(val party: PartyState, val slot: Int) : InventoryPlacementResult
    data class NeedsDropSlot(val party: PartyState) : InventoryPlacementResult
    data class Full(val party: PartyState) : InventoryPlacementResult
    data class InvalidDropSlot(val party: PartyState, val slot: Int) : InventoryPlacementResult
}

sealed interface EquipmentActionResult {
    data class Equipped(val party: PartyState, val memberNumber: Int, val inventorySlot: Int) : EquipmentActionResult
    data class Unequipped(val party: PartyState, val memberNumber: Int, val inventorySlot: Int) : EquipmentActionResult
    data class InvalidMember(val party: PartyState, val memberNumber: Int) : EquipmentActionResult
    data class DeadMember(val party: PartyState, val memberNumber: Int) : EquipmentActionResult
    data class InvalidInventorySlot(val party: PartyState, val slot: Int) : EquipmentActionResult
    data class WrongItemKind(val party: PartyState, val slot: Int, val item: ItemId?) : EquipmentActionResult
    data class NothingEquipped(val party: PartyState, val memberNumber: Int, val kind: EquipmentKind) : EquipmentActionResult
    data class InventoryFull(val party: PartyState) : EquipmentActionResult
}

sealed interface TorchActionResult {
    data class Consumed(val party: PartyState) : TorchActionResult
    data class NoTorches(val party: PartyState) : TorchActionResult
}

sealed interface DungeonDescentResult {
    data class Descended(val party: PartyState) : DungeonDescentResult
    data class NoTorches(val party: PartyState) : DungeonDescentResult
    data class AtMaxDepth(val party: PartyState) : DungeonDescentResult
}

data class CampGearEffect(
    val image: ImageId,
    val protectedBands: Set<TemperatureBand>,
) {
    fun protects(band: TemperatureBand): Boolean = band in protectedBands
}

val EquipmentActionResult.party: PartyState
    get() = when (this) {
        is EquipmentActionResult.Equipped -> party
        is EquipmentActionResult.Unequipped -> party
        is EquipmentActionResult.InvalidMember -> party
        is EquipmentActionResult.DeadMember -> party
        is EquipmentActionResult.InvalidInventorySlot -> party
        is EquipmentActionResult.WrongItemKind -> party
        is EquipmentActionResult.NothingEquipped -> party
        is EquipmentActionResult.InventoryFull -> party
    }

fun PartyState.placeInventoryItem(
    item: ItemId,
    overflow: RewardOverflowPolicy = RewardOverflowPolicy.PromptDropThenPlace,
    dropSlot: Int? = null,
    itemRecords: List<ItemRecordModel> = emptyList(),
    realmContent: RealmContentModel = RealmContentModel(),
): InventoryPlacementResult {
    fun placed(slot: Int): InventoryPlacementResult.Placed =
        InventoryPlacementResult.Placed(
            withInventoryItem(slot, item).clampResourcesToCapacity(itemRecords, realmContent),
            slot,
        )

    firstFreeInventorySlot()?.let { return placed(it) }

    if (overflow == RewardOverflowPolicy.SkipIfInventoryFull) {
        return InventoryPlacementResult.Full(this)
    }

    val slot = dropSlot ?: return InventoryPlacementResult.NeedsDropSlot(this)
    if (slot !in 1..InventorySaveSlotMax || inventoryItem(slot) == null) {
        return InventoryPlacementResult.InvalidDropSlot(this, slot)
    }
    return placed(slot)
}

fun PartyState.equipFromInventory(
    memberNumber: Int,
    slot: Int,
    kind: EquipmentKind,
    itemRecords: List<ItemRecordModel> = emptyList(),
): EquipmentActionResult {
    val memberIndex = memberNumber - 1
    val member = members.getOrNull(memberIndex)
        ?: return EquipmentActionResult.InvalidMember(this, memberNumber)
    if (member.dead) return EquipmentActionResult.DeadMember(this, memberNumber)
    if (slot !in 1..InventorySaveSlotMax) return EquipmentActionResult.InvalidInventorySlot(this, slot)

    val item = inventoryItem(slot)
        ?: return EquipmentActionResult.WrongItemKind(this, slot, null)
    val record = itemRecords.associateBy { it.id }[item]
        ?: return EquipmentActionResult.WrongItemKind(this, slot, item)
    if (!record.matches(kind)) {
        return EquipmentActionResult.WrongItemKind(this, slot, item)
    }

    val oldEquipped = member.equipment.item(kind)
    val updatedMember = member.withEquipmentItem(kind, item)
    val updatedInventoryItem = oldEquipped.takeUnless { it == Items.none }
    return EquipmentActionResult.Equipped(
        party = withMember(memberIndex, updatedMember).withInventoryItem(slot, updatedInventoryItem),
        memberNumber = memberNumber,
        inventorySlot = slot,
    )
}

fun PartyState.unequipToInventory(
    memberNumber: Int,
    kind: EquipmentKind,
): EquipmentActionResult {
    val memberIndex = memberNumber - 1
    val member = members.getOrNull(memberIndex)
        ?: return EquipmentActionResult.InvalidMember(this, memberNumber)
    val equipped = member.equipment.item(kind)
    if (equipped == Items.none) {
        return EquipmentActionResult.NothingEquipped(this, memberNumber, kind)
    }

    val slot = firstFreeInventorySlot()
        ?: return EquipmentActionResult.InventoryFull(this)
    val updated = withMember(memberIndex, member.withEquipmentItem(kind, Items.none))
        .withInventoryItem(slot, equipped)
    return EquipmentActionResult.Unequipped(updated, memberNumber, slot)
}

fun PartyState.applyEquipmentCommand(
    command: EquipmentCommand,
    memberNumber: Int,
    inventorySlot: Int? = null,
    itemRecords: List<ItemRecordModel> = emptyList(),
): EquipmentActionResult =
    when (command) {
        EquipmentCommand.ReadyWeapon -> equipFromInventory(memberNumber, inventorySlot ?: 0, EquipmentKind.Weapon, itemRecords)
        EquipmentCommand.ReadyArmor -> equipFromInventory(memberNumber, inventorySlot ?: 0, EquipmentKind.Armor, itemRecords)
        EquipmentCommand.RemoveWeapon -> unequipToInventory(memberNumber, EquipmentKind.Weapon)
        EquipmentCommand.RemoveArmor -> unequipToInventory(memberNumber, EquipmentKind.Armor)
    }

fun PartyState.needsTownLeaveReadyPrompt(
    command: EquipmentCommand,
    itemRecords: List<ItemRecordModel> = emptyList(),
): Boolean =
    when (command) {
        EquipmentCommand.ReadyWeapon ->
            hasInventoryEquipment(EquipmentKind.Weapon, itemRecords) && hasLivingMemberWithout(EquipmentKind.Weapon)
        EquipmentCommand.ReadyArmor ->
            hasInventoryEquipment(EquipmentKind.Armor, itemRecords) && hasLivingMemberWithout(EquipmentKind.Armor)
        EquipmentCommand.RemoveWeapon,
        EquipmentCommand.RemoveArmor -> false
    }

fun PartyState.consumeTorch(): TorchActionResult =
    if (resources.torches <= 0) {
        TorchActionResult.NoTorches(this)
    } else {
        TorchActionResult.Consumed(copy(resources = resources.copy(torches = resources.torches - 1)))
    }

fun PartyState.tryDungeonDescent(currentDepth: Int, maxDepth: Int): DungeonDescentResult {
    require(currentDepth >= 0) { "current dungeon depth must be non-negative" }
    require(maxDepth >= 0) { "max dungeon depth must be non-negative" }
    if (resources.torches <= 0) return DungeonDescentResult.NoTorches(this)
    if (currentDepth >= maxDepth) return DungeonDescentResult.AtMaxDepth(this)
    return when (val result = consumeTorch()) {
        is TorchActionResult.Consumed -> DungeonDescentResult.Descended(result.party)
        is TorchActionResult.NoTorches -> DungeonDescentResult.NoTorches(result.party)
    }
}

fun PartyState.inventoryItem(slot: Int): ItemId? =
    inventory.firstOrNull { it.slot == slot }?.item

fun PartyState.inventoryItemChoices(itemRecords: List<ItemRecordModel> = emptyList()): List<InventoryItemChoice> {
    val records = itemRecords.associateBy { it.id }
    return (1..InventorySaveSlotMax).mapNotNull { slot ->
        inventoryItem(slot)?.let { item ->
            val record = records[item]
            InventoryItemChoice(
                key = 'A' + slot - 1,
                slot = slot,
                item = item,
                label = record?.displayName ?: item.slug,
                kind = record?.kind ?: ItemKind.Gear,
            )
        }
    }
}

fun PartyState.firstFreeInventorySlot(): Int? =
    (1..InventorySaveSlotMax).firstOrNull { inventoryItem(it) == null }

fun PartyState.withInventoryItem(slot: Int, item: ItemId?): PartyState {
    require(slot in 0..InventorySaveSlotMax) { "inventory slot $slot outside 0..$InventorySaveSlotMax" }
    val slots = inventory.associateBy { it.slot }.toMutableMap()
    slots[slot] = InventorySlotState(slot, item)
    return copy(inventory = slots.values.sortedBy { it.slot })
}

fun PartyState.resourceCapacity(
    itemRecords: List<ItemRecordModel>,
    resource: Resource,
    baseCapacity: Int,
): Int {
    val byId = itemRecords.associateBy { it.id }
    val bonus = inventory
        .asSequence()
        .filter { it.slot in 1..InventorySaveSlotMax }
        .sumOf { slot ->
            val item = slot.item ?: return@sumOf 0
            byId[item]?.resourceCapacityBonus?.get(resource) ?: 0
        }
    return baseCapacity + bonus
}

fun PartyState.clampResourcesToCapacity(
    itemRecords: List<ItemRecordModel>,
    realmContent: RealmContentModel,
): PartyState {
    val foodBase = realmContent.baseResourceCapacity[Resource.Food] ?: return this
    val cap = resourceCapacity(itemRecords, Resource.Food, foodBase)
    return if (resources.food <= cap) this else copy(resources = resources.copy(food = cap))
}

fun PartyState.hasInventoryItem(item: ItemId): Boolean =
    inventory.any { it.slot in 1..InventorySaveSlotMax && it.item == item }

fun PartyState.hasInventoryEquipment(
    kind: EquipmentKind,
    itemRecords: List<ItemRecordModel> = emptyList(),
): Boolean {
    val records = itemRecords.associateBy { it.id }
    return inventory.any { slot ->
        slot.slot in 1..InventorySaveSlotMax && slot.item?.let { records[it]?.matches(kind) } == true
    }
}

fun PartyState.hasLivingMemberWithout(kind: EquipmentKind): Boolean =
    members.any { member -> !member.dead && member.equipment.item(kind) == Items.none }

fun PartyState.campGearEffect(
    itemRecords: List<ItemRecordModel>,
    fallbackImage: ImageId,
): CampGearEffect {
    val byId = itemRecords.associateBy { it.id }
    val carriedRecords = inventory
        .asSequence()
        .filter { it.slot in 1..InventorySaveSlotMax }
        .mapNotNull { slot -> slot.item?.let(byId::get) }
        .toList()
    val protections = carriedRecords.flatMapTo(mutableSetOf()) { it.campProtection }
    val image = carriedRecords.firstNotNullOfOrNull { it.campImage } ?: fallbackImage
    return CampGearEffect(image = image, protectedBands = protections)
}

fun PartyState.withGold(gold: Int): PartyState =
    copy(resources = resources.copy(gold = gold.coerceAtLeast(0)))

fun PartyState.countInventoryItem(item: ItemId): Int =
    inventory.count { it.slot in 1..InventorySaveSlotMax && it.item == item }

fun PartyState.countInventoryItems(pool: ItemPoolId): Int {
    val pooledItems = pool.items.toSet()
    return inventory.count { it.slot in 1..InventorySaveSlotMax && it.item in pooledItems }
}

fun PartyState.consumeInventoryItems(pool: ItemPoolId, quantity: Int): PartyState {
    val pooledItems = pool.items.toSet()
    val consumedSlots = inventory
        .asSequence()
        .filter { it.slot in 1..InventorySaveSlotMax && it.item in pooledItems }
        .take(quantity.coerceAtLeast(0))
        .map { it.slot }
        .toSet()
    return copy(
        inventory = inventory.map { slot -> slot.copy(item = slot.item.takeUnless { slot.slot in consumedSlots }) },
    )
}

fun PartyState.withMilestone(milestone: GameMilestoneId): PartyState =
    if (milestone in milestones) this else copy(milestones = (milestones + milestone).sortedBy { it.slug })

fun PartyState.withJournalLines(lines: List<String>): PartyState =
    copy(journal = journal + lines.filter { it.isNotBlank() }.map(JournalEntryState::Line))

private fun PartyState.withMember(index: Int, member: PartyMemberState): PartyState =
    copy(members = members.mapIndexed { i, existing -> if (i == index) member else existing })

private fun ItemRecordModel.matches(kind: EquipmentKind): Boolean =
    when (kind) {
        EquipmentKind.Weapon -> this.kind == ItemKind.Weapon
        EquipmentKind.Armor -> this.kind == ItemKind.Armor
    }

private fun EquipmentState.item(kind: EquipmentKind): ItemId =
    when (kind) {
        EquipmentKind.Weapon -> weapon
        EquipmentKind.Armor -> armor
    }

private fun PartyMemberState.withEquipmentItem(kind: EquipmentKind, item: ItemId): PartyMemberState =
    copy(
        equipment = when (kind) {
            EquipmentKind.Weapon -> equipment.copy(weapon = item)
            EquipmentKind.Armor -> equipment.copy(armor = item)
        },
    )

data class EquipmentPromptModel(
    val command: EquipmentCommand,
    val kind: EquipmentKind,
    val memberFilter: MemberChoiceFilter,
    val inventoryFilter: InventorySlotChoiceFilter?,
)

object EquipmentPromptText {
    val inventory = inlineText("Ready what?")
    val who = inlineText("Who? (1-N)")
    val readyWeapons = inlineText("Do you want to ready", "some weapons before", "leaving?", "")
    val readyArmors = inlineText("Do you want to ready", "some armors before", "leaving?", "")
}

sealed interface EquipmentPromptStep {
    data class ChooseInventorySlot(
        val prompt: TextBlock,
        val filter: InventorySlotChoiceFilter,
    ) : EquipmentPromptStep
    data class ChooseMember(
        val prompt: TextBlock,
        val filter: MemberChoiceFilter,
    ) : EquipmentPromptStep
    data class Finished(val result: EquipmentActionResult) : EquipmentPromptStep
}

data class EquipmentPromptSession(
    val command: EquipmentCommand,
    val party: PartyState,
    val step: EquipmentPromptStep,
    val itemRecords: List<ItemRecordModel> = emptyList(),
    val inventorySlot: Int? = null,
) {
    fun apply(input: EquipmentPromptInput): EquipmentPromptSession =
        when (val current = step) {
            is EquipmentPromptStep.ChooseInventorySlot -> {
                val slot = (input as? EquipmentPromptInput.InventorySlot)
                    ?: error("equipment session is choosing an inventory slot")
                copy(
                    step = EquipmentPromptStep.ChooseMember(
                        EquipmentPromptText.who,
                        command.prompt().memberFilter,
                    ),
                    inventorySlot = slot.slot,
                )
            }
            is EquipmentPromptStep.ChooseMember -> {
                val member = (input as? EquipmentPromptInput.Member)
                    ?: error("equipment session is choosing a member")
                val result = party.applyEquipmentCommand(command, member.memberNumber, inventorySlot, itemRecords)
                copy(party = result.party, step = EquipmentPromptStep.Finished(result))
            }
            is EquipmentPromptStep.Finished -> error("equipment session is already finished: $current")
        }

    companion object {
        fun start(
            command: EquipmentCommand,
            party: PartyState,
            itemRecords: List<ItemRecordModel> = emptyList(),
        ): EquipmentPromptSession {
            val prompt = command.prompt()
            val step = prompt.inventoryFilter?.let {
                EquipmentPromptStep.ChooseInventorySlot(EquipmentPromptText.inventory, it)
            } ?: EquipmentPromptStep.ChooseMember(EquipmentPromptText.who, prompt.memberFilter)
            return EquipmentPromptSession(command, party, step, itemRecords)
        }

        fun startWithInventorySlot(
            command: EquipmentCommand,
            party: PartyState,
            slot: Int,
            itemRecords: List<ItemRecordModel> = emptyList(),
        ): EquipmentPromptSession {
            val prompt = command.prompt()
            require(prompt.inventoryFilter != null) { "$command does not choose inventory items" }
            return EquipmentPromptSession(
                command = command,
                party = party,
                step = EquipmentPromptStep.ChooseMember(EquipmentPromptText.who, prompt.memberFilter),
                itemRecords = itemRecords,
                inventorySlot = slot,
            )
        }
    }
}

fun EquipmentCommand.prompt(): EquipmentPromptModel =
    when (this) {
        EquipmentCommand.ReadyWeapon -> EquipmentPromptModel(
            command = this,
            kind = EquipmentKind.Weapon,
            memberFilter = MemberChoiceFilter.AliveOnly,
            inventoryFilter = InventorySlotChoiceFilter.NonEmpty,
        )
        EquipmentCommand.ReadyArmor -> EquipmentPromptModel(
            command = this,
            kind = EquipmentKind.Armor,
            memberFilter = MemberChoiceFilter.AliveOnly,
            inventoryFilter = InventorySlotChoiceFilter.NonEmpty,
        )
        EquipmentCommand.RemoveWeapon -> EquipmentPromptModel(
            command = this,
            kind = EquipmentKind.Weapon,
            memberFilter = MemberChoiceFilter.Any,
            inventoryFilter = null,
        )
        EquipmentCommand.RemoveArmor -> EquipmentPromptModel(
            command = this,
            kind = EquipmentKind.Armor,
            memberFilter = MemberChoiceFilter.Any,
            inventoryFilter = null,
        )
    }

fun EquipmentCommand.townLeavePrompt(): TextBlock =
    when (this) {
        EquipmentCommand.ReadyWeapon -> EquipmentPromptText.readyWeapons
        EquipmentCommand.ReadyArmor -> EquipmentPromptText.readyArmors
        EquipmentCommand.RemoveWeapon,
        EquipmentCommand.RemoveArmor -> error("$this is not a town-leave ready command")
    }
