// Pure Kotlin runtime helpers for DSL systems that should work behind any
// frontend or engine bridge. These functions mutate immutable PartyState values
// and keep UI/input decisions explicit in their inputs.

enum class EquipmentKind { Weapon, Armor }

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

enum class ShopTransactionStatus {
    Bought,
    Sold,
    NeedsDropSlot,
    InventoryFull,
    InvalidDropSlot,
    ItemNotSoldHere,
    ItemNotAcceptedHere,
    EmptySlot,
    NotEnoughGold,
    InvalidQuantity,
    Declined,
}

data class ShopTransactionResult(
    val status: ShopTransactionStatus,
    val party: PartyState,
    val item: ItemId? = null,
    val goldDelta: Int = 0,
    val slot: Int? = null,
    val quantity: Int = 1,
)

data class CampGearEffect(
    val image: ImageId,
    val protectsLowTemperature: Boolean,
    val protectsVeryLowTemperature: Boolean,
)

class ShopServiceInterpreter(
    itemRecords: List<ItemRecordModel> = ItemCatalog.all,
) {
    private val itemRecordById = itemRecords.associateBy { it.id }

    fun buy(
        shop: ShopServiceModel,
        party: PartyState,
        item: ItemId,
        quantity: Int = 1,
        dropSlot: Int? = null,
    ): ShopTransactionResult {
        val buy = shop.buy ?: return ShopTransactionResult(ShopTransactionStatus.ItemNotSoldHere, party, item)
        val offer = buy.findOffer(item)
            ?: return ShopTransactionResult(ShopTransactionStatus.ItemNotSoldHere, party, item)
        val unitPrice = price(offer.price, offer.item, party)

        return when (val purchase = offer.purchase) {
            ShopPurchaseModel.InventoryItem -> buyInventoryItem(buy, party, offer.item, unitPrice, dropSlot)
            is ShopPurchaseModel.Resource -> buyResource(party, offer.item, purchase, unitPrice, quantity)
        }
    }

    fun sell(
        shop: ShopServiceModel,
        party: PartyState,
        slot: Int,
        accept: Boolean = true,
        bargainRolls: List<Int> = emptyList(),
    ): ShopTransactionResult {
        val sell = shop.sell ?: return ShopTransactionResult(ShopTransactionStatus.ItemNotAcceptedHere, party)
        val item = party.inventoryItem(slot)
            ?: return ShopTransactionResult(ShopTransactionStatus.EmptySlot, party, slot = slot)
        if (!sell.accepts(item)) {
            return ShopTransactionResult(ShopTransactionStatus.ItemNotAcceptedHere, party, item, slot = slot)
        }

        val offerPrice = bargainPrice(sell, item, party, bargainRolls)
            ?: return ShopTransactionResult(ShopTransactionStatus.Declined, party, item, slot = slot)
        if (!accept) {
            return ShopTransactionResult(ShopTransactionStatus.Declined, party, item, slot = slot)
        }

        val updated = party
            .withInventoryItem(slot, null)
            .withGold(party.resources.gold + offerPrice)
        return ShopTransactionResult(
            status = ShopTransactionStatus.Sold,
            party = updated,
            item = item,
            goldDelta = offerPrice,
            slot = slot,
        )
    }

    private fun buyInventoryItem(
        buy: ShopBuyModel,
        party: PartyState,
        item: ItemId,
        unitPrice: Int,
        dropSlot: Int?,
    ): ShopTransactionResult {
        if (party.resources.gold < unitPrice) {
            return ShopTransactionResult(ShopTransactionStatus.NotEnoughGold, party, item, goldDelta = -unitPrice)
        }
        return when (val placed = party.placeInventoryItem(item, buy.inventoryApplication.overflow, dropSlot)) {
            is InventoryPlacementResult.Placed -> ShopTransactionResult(
                status = ShopTransactionStatus.Bought,
                party = placed.party.withGold(placed.party.resources.gold - unitPrice),
                item = item,
                goldDelta = -unitPrice,
                slot = placed.slot,
            )
            is InventoryPlacementResult.NeedsDropSlot -> ShopTransactionResult(
                status = ShopTransactionStatus.NeedsDropSlot,
                party = placed.party,
                item = item,
            )
            is InventoryPlacementResult.Full -> ShopTransactionResult(
                status = ShopTransactionStatus.InventoryFull,
                party = placed.party,
                item = item,
            )
            is InventoryPlacementResult.InvalidDropSlot -> ShopTransactionResult(
                status = ShopTransactionStatus.InvalidDropSlot,
                party = placed.party,
                item = item,
                slot = placed.slot,
            )
        }
    }

    private fun buyResource(
        party: PartyState,
        item: ItemId,
        purchase: ShopPurchaseModel.Resource,
        unitPrice: Int,
        requestedQuantity: Int,
    ): ShopTransactionResult {
        if (requestedQuantity < 0) {
            return ShopTransactionResult(ShopTransactionStatus.InvalidQuantity, party, item, quantity = requestedQuantity)
        }

        val quantity = minOf(requestedQuantity, party.remainingCapacity(purchase.cap))
        if (quantity == 0 && purchase.zeroQuantityDeal == ShopZeroQuantityDealPolicy.NonZeroOnly) {
            return ShopTransactionResult(ShopTransactionStatus.InvalidQuantity, party, item, quantity = 0)
        }

        val total = unitPrice * quantity
        if (party.resources.gold < total) {
            return ShopTransactionResult(ShopTransactionStatus.NotEnoughGold, party, item, goldDelta = -total)
        }

        val updated = party
            .withGold(party.resources.gold - total)
            .withResource(purchase.resource, quantity)
        return ShopTransactionResult(
            status = ShopTransactionStatus.Bought,
            party = updated,
            item = item,
            goldDelta = -total,
            quantity = quantity,
        )
    }

    private fun bargainPrice(
        sell: ShopSellModel,
        item: ItemId,
        party: PartyState,
        rolls: List<Int>,
    ): Int? {
        var current = price(sell.price, item, party)
        if (sell.bargain != ShopSellBargainPolicy.SmithHaggle) return current

        rolls.forEach { roll ->
            if (roll > 90) current += current / 4
            if (roll > 75) current += current / 6
            if (roll < 76) return null
        }
        return current
    }

    private fun ShopBuyModel.findOffer(item: ItemId): ShopItemOfferModel? =
        catalogs
            .asSequence()
            .flatMap { it.pages.asSequence() }
            .flatMap { it.entries.asSequence() }
            .firstOrNull { it.item == item }

    private fun ShopSellModel.accepts(item: ItemId): Boolean {
        if (item in rejectedItems) return false
        if (item in acceptedItems) return true
        return itemRecordById[item]?.kind in acceptedKinds
    }

    private fun price(model: ShopPriceModel, item: ItemId, party: PartyState): Int =
        when (model) {
            ShopPriceModel.ItemCost -> itemRecord(item).cost
            ShopPriceModel.HalfItemCost -> itemRecord(item).cost / 2
            is ShopPriceModel.Fixed -> model.amount
            is ShopPriceModel.LeaderLevelPlus -> party.leaderLevel + model.addend
            is ShopPriceModel.ItemCostPlusLeaderLevelTimes -> itemRecord(item).cost + party.leaderLevel * model.multiplier
        }

    private fun itemRecord(item: ItemId): ItemRecordModel =
        checkNotNull(itemRecordById[item]) { "unknown item ${item.slug}" }
}

fun PartyState.placeInventoryItem(
    item: ItemId,
    overflow: RewardOverflowPolicy = RewardOverflowPolicy.PromptDropThenPlace,
    dropSlot: Int? = null,
): InventoryPlacementResult {
    firstFreeInventorySlot()?.let { slot ->
        return InventoryPlacementResult.Placed(withInventoryItem(slot, item), slot)
    }

    if (overflow == RewardOverflowPolicy.SkipIfInventoryFull) {
        return InventoryPlacementResult.Full(this)
    }

    val slot = dropSlot ?: return InventoryPlacementResult.NeedsDropSlot(this)
    if (slot !in 1..InventorySaveSlotMax || inventoryItem(slot) == null) {
        return InventoryPlacementResult.InvalidDropSlot(this, slot)
    }
    return InventoryPlacementResult.Placed(withInventoryItem(slot, item), slot)
}

fun PartyState.equipFromInventory(
    memberNumber: Int,
    slot: Int,
    kind: EquipmentKind,
    itemRecords: List<ItemRecordModel> = ItemCatalog.all,
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

fun PartyState.inventoryItem(slot: Int): ItemId? =
    inventory.firstOrNull { it.slot == slot }?.item

fun PartyState.firstFreeInventorySlot(): Int? =
    (1..InventorySaveSlotMax).firstOrNull { inventoryItem(it) == null }

fun PartyState.withInventoryItem(slot: Int, item: ItemId?): PartyState {
    require(slot in 0..InventorySaveSlotMax) { "inventory slot $slot outside 0..$InventorySaveSlotMax" }
    val slots = inventory.associateBy { it.slot }.toMutableMap()
    val previous = slots[slot]?.item
    slots[slot] = InventorySlotState(slot, item)
    val updated = copy(inventory = slots.values.sortedBy { it.slot })
    return if (previous == Items.donkey && item != Items.donkey) {
        updated.withFoodClampedToDonkeyCapacity()
    } else {
        updated
    }
}

fun PartyState.foodCapacityByDonkeys(): Int =
    150 * (countInventoryItem(Items.donkey) + 1)

fun PartyState.hasInventoryItem(item: ItemId): Boolean =
    inventory.any { it.slot in 1..InventorySaveSlotMax && it.item == item }

fun PartyState.campGearEffect(): CampGearEffect {
    val hasTent = hasInventoryItem(Items.tent)
    val hasBlanket = hasInventoryItem(Items.blanket)
    return CampGearEffect(
        image = if (hasTent) Images.Scene.tent else Images.Scene.noTent,
        protectsLowTemperature = hasBlanket || hasTent,
        protectsVeryLowTemperature = hasTent,
    )
}

private fun PartyState.withGold(gold: Int): PartyState =
    copy(resources = resources.copy(gold = gold.coerceAtLeast(0)))

private fun PartyState.withResource(resource: ShopResourceKind, quantity: Int): PartyState =
    when (resource) {
        ShopResourceKind.Torches -> copy(resources = resources.copy(torches = resources.torches + quantity))
        ShopResourceKind.Food -> copy(resources = resources.copy(food = resources.food + quantity))
    }

private fun PartyState.withMember(index: Int, member: PartyMemberState): PartyState =
    copy(members = members.mapIndexed { i, existing -> if (i == index) member else existing })

private val PartyState.leaderLevel: Int
    get() = members.firstOrNull()?.level ?: 0

private fun PartyState.remainingCapacity(cap: ShopQuantityCap): Int =
    when (cap) {
        ShopQuantityCap.TorchLimit80 -> 80 - resources.torches
        ShopQuantityCap.FoodByDonkeys -> foodCapacityByDonkeys() - resources.food
    }.coerceAtLeast(0)

fun PartyState.countInventoryItem(item: ItemId): Int =
    inventory.count { it.slot in 1..InventorySaveSlotMax && it.item == item }

private fun PartyState.withFoodClampedToDonkeyCapacity(): PartyState {
    val cap = foodCapacityByDonkeys()
    return if (resources.food <= cap) this else copy(resources = resources.copy(food = cap))
}

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
