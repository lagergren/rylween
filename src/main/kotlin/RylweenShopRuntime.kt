// Reusable shop runtime: pure transaction interpreter on PartyState plus a
// backend-neutral prompt-state-machine session for intro/main/buy/sell flows.

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

enum class ShopSellQuoteStatus {
    Quoted,
    ItemNotAcceptedHere,
    EmptySlot,
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

data class ShopSellQuoteResult(
    val status: ShopSellQuoteStatus,
    val party: PartyState,
    val item: ItemId? = null,
    val slot: Int? = null,
    val price: Int = 0,
    val bargainRolls: List<Int> = emptyList(),
)

sealed interface ShopServiceStep {
    data class Intro(val text: TextBlock) : ShopServiceStep
    data class MainMenu(val text: TextBlock, val canBuy: Boolean, val canSell: Boolean) : ShopServiceStep
    data class BuyItem(val prompt: TextBlock, val catalogs: List<ShopCatalogModel>) : ShopServiceStep
    data class Quantity(val offer: ShopItemOfferModel, val prompt: TextBlock) : ShopServiceStep
    data class ConfirmPurchase(
        val offer: ShopItemOfferModel,
        val quantity: Int,
        val unitPrice: Int,
        val totalPrice: Int,
        val prompt: TextBlock,
    ) : ShopServiceStep
    data class DropSlot(
        val offer: ShopItemOfferModel,
        val quantity: Int,
        val prompt: TextBlock,
        val result: ShopTransactionResult,
    ) : ShopServiceStep
    data class SellSlot(val prompt: TextBlock) : ShopServiceStep
    data class ConfirmSale(val quote: ShopSellQuoteResult, val prompt: TextBlock) : ShopServiceStep
    data class Finished(val text: TextBlock) : ShopServiceStep
}

data class ShopServiceSession(
    val shop: ShopServiceModel,
    val party: PartyState,
    val step: ShopServiceStep,
    val lastTransaction: ShopTransactionResult? = null,
    val lastSellQuote: ShopSellQuoteResult? = null,
    val itemRecords: List<ItemRecordModel> = emptyList(),
) {
    private fun interpreter(): ShopServiceInterpreter = ShopServiceInterpreter(itemRecords)
    private fun mainMenu(): ShopServiceStep =
        ShopServiceStep.MainMenu(shop.menu, canBuy = shop.buy != null, canSell = shop.sell != null)

    fun continueIntro(): ShopServiceSession {
        require(step is ShopServiceStep.Intro) { "shop session is not showing intro" }
        return copy(step = mainMenu(), lastTransaction = null, lastSellQuote = null)
    }

    fun chooseMain(choice: ShopMainChoice): ShopServiceSession {
        require(step is ShopServiceStep.MainMenu) { "shop session is not at main menu" }
        return when (choice) {
            ShopMainChoice.Buy -> {
                val buy = checkNotNull(shop.buy) { "shop ${shop.building.slug} has no buy section" }
                copy(step = ShopServiceStep.BuyItem(buy.prompt, buy.catalogs), lastTransaction = null, lastSellQuote = null)
            }
            ShopMainChoice.Sell -> {
                val sell = checkNotNull(shop.sell) { "shop ${shop.building.slug} has no sell section" }
                copy(step = ShopServiceStep.SellSlot(sell.prompt), lastTransaction = null, lastSellQuote = null)
            }
            ShopMainChoice.Leave -> copy(step = ShopServiceStep.Finished(shop.goodbye))
        }
    }

    fun chooseBuyItem(item: ItemId): ShopServiceSession {
        require(step is ShopServiceStep.BuyItem) { "shop session is not choosing a buy item" }
        val buy = checkNotNull(shop.buy) { "shop ${shop.building.slug} has no buy section" }
        val offer = buy.offerFor(item)
            ?: return copy(
                step = mainMenu(),
                lastTransaction = ShopTransactionResult(ShopTransactionStatus.ItemNotSoldHere, party, item),
            )
        val purchase = offer.purchase
        return if (purchase is ShopPurchaseModel.Resource) {
            copy(step = ShopServiceStep.Quantity(offer, purchase.quantityPrompt), lastTransaction = null, lastSellQuote = null)
        } else {
            purchaseStep(offer, quantity = 1)
        }
    }

    fun enterQuantity(quantity: Int): ShopServiceSession {
        val current = step as? ShopServiceStep.Quantity
            ?: error("shop session is not asking for a quantity")
        return purchaseStep(current.offer, quantity)
    }

    fun confirmPurchase(accept: Boolean): ShopServiceSession {
        val current = step as? ShopServiceStep.ConfirmPurchase
            ?: error("shop session is not confirming a purchase")
        val buy = checkNotNull(shop.buy) { "shop ${shop.building.slug} has no buy section" }
        if (!accept) {
            return copy(
                step = mainMenu(),
                lastTransaction = ShopTransactionResult(
                    status = ShopTransactionStatus.Declined,
                    party = party,
                    item = current.offer.item,
                    quantity = current.quantity,
                ),
            )
        }

        val result = interpreter().buy(shop, party, current.offer.item, current.quantity)
        return if (result.status == ShopTransactionStatus.NeedsDropSlot) {
            copy(step = ShopServiceStep.DropSlot(current.offer, current.quantity, buy.cantCarry, result))
        } else {
            copy(step = mainMenu(), party = result.party, lastTransaction = result)
        }
    }

    fun chooseDropSlot(slot: Int): ShopServiceSession {
        val current = step as? ShopServiceStep.DropSlot
            ?: error("shop session is not asking for a drop slot")
        val result = interpreter().buy(shop, party, current.offer.item, current.quantity, dropSlot = slot)
        return if (result.status == ShopTransactionStatus.Bought) {
            copy(step = mainMenu(), party = result.party, lastTransaction = result)
        } else {
            copy(step = current.copy(result = result), lastTransaction = result)
        }
    }

    fun chooseSellSlot(slot: Int, bargainRolls: List<Int> = emptyList()): ShopServiceSession {
        require(step is ShopServiceStep.SellSlot) { "shop session is not choosing a sell slot" }
        val sell = checkNotNull(shop.sell) { "shop ${shop.building.slug} has no sell section" }
        val quote = interpreter().sellQuote(shop, party, slot, bargainRolls)
        return if (quote.status == ShopSellQuoteStatus.Quoted) {
            copy(step = ShopServiceStep.ConfirmSale(quote, sell.offer), lastSellQuote = quote)
        } else {
            copy(step = mainMenu(), lastSellQuote = quote)
        }
    }

    fun confirmSale(accept: Boolean): ShopServiceSession {
        val current = step as? ShopServiceStep.ConfirmSale
            ?: error("shop session is not confirming a sale")
        val result = if (accept) {
            interpreter().sell(shop, party, checkNotNull(current.quote.slot), bargainRolls = current.quote.bargainRolls)
        } else {
            ShopTransactionResult(
                status = ShopTransactionStatus.Declined,
                party = party,
                item = current.quote.item,
                slot = current.quote.slot,
            )
        }
        return copy(step = mainMenu(), party = result.party, lastTransaction = result)
    }

    private fun purchaseStep(offer: ShopItemOfferModel, quantity: Int): ShopServiceSession {
        val buy = checkNotNull(shop.buy) { "shop ${shop.building.slug} has no buy section" }
        val unitPrice = interpreter().unitPrice(offer, party)
        return copy(
            step = ShopServiceStep.ConfirmPurchase(
                offer = offer,
                quantity = quantity,
                unitPrice = unitPrice,
                totalPrice = unitPrice * quantity,
                prompt = buy.payPrompt,
            ),
            lastTransaction = null,
            lastSellQuote = null,
        )
    }

    companion object {
        fun start(
            shop: ShopServiceModel,
            party: PartyState,
            itemRecords: List<ItemRecordModel> = emptyList(),
        ): ShopServiceSession =
            ShopServiceSession(
                shop = shop,
                party = party,
                step = ShopServiceStep.Intro(shop.intro),
                itemRecords = itemRecords,
            )
    }
}

fun applyShopInput(session: ShopServiceSession, input: ShopServiceInput): ShopServiceSession =
    when (input) {
        ShopServiceInput.ContinueIntro -> session.continueIntro()
        is ShopServiceInput.Main -> session.chooseMain(input.choice)
        is ShopServiceInput.BuyItem -> session.chooseBuyItem(input.item)
        is ShopServiceInput.Quantity -> session.enterQuantity(input.quantity)
        is ShopServiceInput.ConfirmPurchase -> session.confirmPurchase(input.accept)
        is ShopServiceInput.DropSlot -> session.chooseDropSlot(input.slot)
        is ShopServiceInput.SellSlot -> session.chooseSellSlot(input.slot, input.bargainRolls)
        is ShopServiceInput.ConfirmSale -> session.confirmSale(input.accept)
    }

fun List<SceneModel>.shopServicesByBuilding(): Map<BuildingId, ShopServiceModel> =
    flatMap { scene -> scene.ops.filterIsInstance<Op.ShopService>().map { it.shop } }
        .associateBy { shop -> shop.building }

fun BuildingServiceModel.isOpenAt(hour: Int): Boolean {
    val opensAt = opensAt ?: return true
    val closesAt = closesAt ?: return true
    val current = hour.floorMod(24)
    return if (opensAt <= closesAt) {
        current in opensAt..closesAt
    } else {
        current >= opensAt || current <= closesAt
    }
}

fun ShopBuyModel.offers(): List<ShopItemOfferModel> =
    catalogs.flatMap { catalog -> catalog.pages.flatMap { page -> page.entries } }

fun ShopBuyModel.offerFor(item: ItemId): ShopItemOfferModel? =
    offers().firstOrNull { it.item == item }

class ShopServiceInterpreter(
    itemRecords: List<ItemRecordModel> = emptyList(),
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
        val offer = buy.offerFor(item)
            ?: return ShopTransactionResult(ShopTransactionStatus.ItemNotSoldHere, party, item)
        val unitPrice = unitPrice(offer, party)

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
        val quote = sellQuote(shop, party, slot, bargainRolls)
        val item = quote.item
        val offerPrice = quote.price
        when (quote.status) {
            ShopSellQuoteStatus.EmptySlot ->
                return ShopTransactionResult(ShopTransactionStatus.EmptySlot, party, slot = slot)
            ShopSellQuoteStatus.ItemNotAcceptedHere ->
                return ShopTransactionResult(ShopTransactionStatus.ItemNotAcceptedHere, party, item, slot = slot)
            ShopSellQuoteStatus.Declined ->
                return ShopTransactionResult(ShopTransactionStatus.Declined, party, item, slot = slot)
            ShopSellQuoteStatus.Quoted -> Unit
        }
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

    fun sellQuote(
        shop: ShopServiceModel,
        party: PartyState,
        slot: Int,
        bargainRolls: List<Int> = emptyList(),
    ): ShopSellQuoteResult {
        val sell = shop.sell ?: return ShopSellQuoteResult(ShopSellQuoteStatus.ItemNotAcceptedHere, party, slot = slot)
        val item = party.inventoryItem(slot)
            ?: return ShopSellQuoteResult(ShopSellQuoteStatus.EmptySlot, party, slot = slot)
        if (!sell.accepts(item)) {
            return ShopSellQuoteResult(ShopSellQuoteStatus.ItemNotAcceptedHere, party, item, slot)
        }

        val offerPrice = bargainPrice(sell, item, party, bargainRolls)
            ?: return ShopSellQuoteResult(ShopSellQuoteStatus.Declined, party, item, slot, bargainRolls = bargainRolls)
        return ShopSellQuoteResult(
            status = ShopSellQuoteStatus.Quoted,
            party = party,
            item = item,
            slot = slot,
            price = offerPrice,
            bargainRolls = bargainRolls,
        )
    }

    fun unitPrice(offer: ShopItemOfferModel, party: PartyState): Int =
        price(offer.price, offer.item, party)

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

private fun PartyState.withResource(resource: ShopResourceKind, quantity: Int): PartyState =
    when (resource) {
        ShopResourceKind.Torches -> copy(resources = resources.copy(torches = resources.torches + quantity))
        ShopResourceKind.Food -> copy(resources = resources.copy(food = resources.food + quantity))
    }

private val PartyState.leaderLevel: Int
    get() = members.firstOrNull()?.level ?: 0

private fun PartyState.remainingCapacity(cap: ShopQuantityCap): Int =
    when (cap) {
        ShopQuantityCap.TorchLimit80 -> 80 - resources.torches
        ShopQuantityCap.FoodByDonkeys -> foodCapacityByDonkeys() - resources.food
    }.coerceAtLeast(0)
