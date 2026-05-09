/**
 * Direct compiled-table shop service for the standalone compiled runtime.
 *
 * This file is the first town host service that does real gameplay work
 * without delegating to the old Kotlin `ShopServiceSession`. It consumes the
 * compiled `ShopService` operand, item table, input bindings, party image, and
 * runtime-system realm constants directly.
 */

package rylween.compiledruntime

import rylween.compiled.CompiledContentBundle
import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledInputBinding
import rylween.compiled.CompiledItemRecord
import rylween.compiled.CompiledRuntimeTextPart
import rylween.compiled.CompiledShopBuy
import rylween.compiled.CompiledShopCatalog
import rylween.compiled.CompiledShopCatalogPage
import rylween.compiled.CompiledShopItemOffer
import rylween.compiled.CompiledShopPrice
import rylween.compiled.CompiledShopPurchase
import rylween.compiled.CompiledShopSell
import rylween.compiled.CompiledShopService
import rylween.compiled.CompiledTextBlock
import rylween.compiled.ExternalRuntimeRealm
import rylween.dsl.Resource
import rylween.dsl.RewardOverflowPolicy
import rylween.dsl.ShopQuantityCap
import rylween.dsl.ShopZeroQuantityDealPolicy
import kotlin.math.max

/** Runs one compiled shop service against the standalone runtime party model. */
internal class StandaloneCompiledShopService(
    private val content: CompiledContentBundle,
    private val realm: ExternalRuntimeRealm,
    private val input: StandaloneInput,
    private val screen: CompiledAnsiScreen,
) {
    private val itemsById: Map<CompiledHandleRef, CompiledItemRecord> = content.items.associateBy { it.id }

    fun run(
        shop: CompiledShopService,
        party: StandaloneParty,
    ): StandaloneParty {
        var currentParty = party
        screen.showImage(content.slug(shop.image))
        val homeLines = shopHomeLines(shop)

        while (true) {
            screen.writeText(homeLines)
            val choice = input.readCommand() ?: return currentParty
            val buy = shop.buy
            val sell = shop.sell
            when {
                shop.keys.leave.matchesStandalone(content, choice) -> {
                    return currentParty
                }

                choice == '!' -> screen.redrawCurrentView(homeLines)

                buy != null && shop.keys.buy.matchesStandalone(content, choice) -> {
                    currentParty = runBuy(buy, currentParty)
                }

                sell != null && shop.keys.sell.matchesStandalone(content, choice) -> {
                    currentParty = runSell(sell, currentParty)
                }

                else -> writeTextAndWait(listOf("No such shop choice."))
            }
        }
    }

    private fun shopHomeLines(shop: CompiledShopService): List<String> =
        shop.intro.renderStandaloneLines(content) + shop.menu.renderStandaloneLines(content)

    private fun runBuy(
        buy: CompiledShopBuy,
        party: StandaloneParty,
    ): StandaloneParty {
        val catalog = chooseCatalog(buy) ?: return party
        val offer = chooseOffer(buy, catalog, party) ?: return party
        return confirmPurchase(buy, offer, party)
    }

    private fun chooseCatalog(buy: CompiledShopBuy): CompiledShopCatalog? {
        if (buy.catalogs.size == 1) return buy.catalogs.single()
        val lines =
            buildList {
                addAll(buy.prompt.renderStandaloneLines(content))
                buy.catalogs.forEach { catalog ->
                    add("${catalog.binding.primaryStandaloneLabel(content)}. ${content.strings.value(catalog.titleString)}")
                }
                add("${buy.paymentKeys.decline.primaryStandaloneLabel(content)}. Nothing")
            }
        screen.writeText(lines)
        val choice = input.readCommand() ?: return null
        if (buy.paymentKeys.decline.matchesStandalone(content, choice)) return null
        return buy.catalogs.firstOrNull { catalog -> catalog.binding.matchesStandalone(content, choice) }
    }

    private fun chooseOffer(
        buy: CompiledShopBuy,
        catalog: CompiledShopCatalog,
        party: StandaloneParty,
    ): CompiledShopItemOffer? {
        var pageIndex = 0
        while (true) {
            val page = catalog.pages[pageIndex]
            showCatalogPage(catalog, page, party)
            val choice = input.readCommand() ?: return null
            val targetPage = catalog.navigationTarget(pageIndex, choice)
            if (targetPage != null) {
                pageIndex = targetPage
                continue
            }
            if (buy.paymentKeys.decline.matchesStandalone(content, choice)) return null
            page.entries
                .firstOrNull { item -> item.binding.matchesStandalone(content, choice) }
                ?.let { return it }
            screen.writeText(listOf("That item is not sold here."))
        }
    }

    private fun showCatalogPage(
        catalog: CompiledShopCatalog,
        page: CompiledShopCatalogPage,
        party: StandaloneParty,
    ) {
        val lines =
            buildList {
                catalog.intro?.let { addAll(it.renderStandaloneLines(content)) }
                page.entries.forEach { offer ->
                    val label = offer.labelString?.let(content.strings::value) ?: itemName(offer.item)
                    add("${offer.binding.primaryStandaloneLabel(content)}. $label  ${unitPrice(offer, party)} au")
                }
                page.navigation.previous?.let { add(it.optionLine(content)) }
                page.navigation.next?.let { add(it.optionLine(content)) }
                page.footer?.let { addAll(it.renderStandaloneLines(content)) }
            }
        screen.writeText(lines)
    }

    private fun CompiledShopCatalog.navigationTarget(
        pageIndex: Int,
        choice: Char,
    ): Int? {
        val page = pages[pageIndex]
        return when {
            page.navigation.previous.matchesChoice(choice) -> (pageIndex - 1).coerceAtLeast(0)
            page.navigation.next.matchesChoice(choice) -> (pageIndex + 1).coerceAtMost(pages.lastIndex)
            else -> null
        }
    }

    private fun CompiledInputBinding?.matchesChoice(choice: Char): Boolean = this?.matchesStandalone(content, choice) == true

    private fun confirmPurchase(
        buy: CompiledShopBuy,
        offer: CompiledShopItemOffer,
        party: StandaloneParty,
    ): StandaloneParty {
        val quantity = purchaseQuantity(offer, party)
        if (quantity == null) return party
        if (quantity == 0 && offer.purchase.zeroQuantityRequiresNonZero()) {
            val purchase = offer.purchase as CompiledShopPurchase.Bulk
            screen.writeText(purchase.tooMany.renderShopLines(carryCap = capacityLimit(party, purchase.cap)))
            return party
        }

        val unitPrice = unitPrice(offer, party)
        val totalPrice = unitPrice * quantity
        val promptLines =
            buildList {
                offer.description?.let { addAll(it.renderShopLines(item = offer.item, price = totalPrice)) }
                addAll(purchasePriceLines(buy, offer, unitPrice, totalPrice))
                addAll(buy.payPrompt.renderStandaloneLines(content))
            }
        screen.writeText(promptLines)
        val choice = input.readCommand() ?: return party
        if (buy.paymentKeys.decline.matchesStandalone(content, choice)) return party
        if (!buy.paymentKeys.pay.matchesStandalone(content, choice)) return party
        if (party.resources.gold < totalPrice) {
            writeTextAndWait(buy.tooExpensive.renderShopLines(item = offer.item, price = totalPrice))
            return party
        }

        val result = applyPurchase(buy, offer, party, quantity, totalPrice)
        writeTextAndWait(result.lines)
        return result.party
    }

    private fun purchaseQuantity(
        offer: CompiledShopItemOffer,
        party: StandaloneParty,
    ): Int? {
        val purchase = offer.purchase
        if (purchase !is CompiledShopPurchase.Bulk) return 1
        screen.writeText(purchase.quantityPrompt.renderStandaloneLines(content))
        val requested = input.readInt() ?: return null
        val remaining = remainingCapacity(party, purchase.cap)
        return minOf(requested, remaining)
    }

    private fun applyPurchase(
        buy: CompiledShopBuy,
        offer: CompiledShopItemOffer,
        party: StandaloneParty,
        quantity: Int,
        totalPrice: Int,
    ): ShopResult =
        when (val purchase = offer.purchase) {
            CompiledShopPurchase.InventoryItem -> buyInventoryItem(buy, offer, party, totalPrice)
            is CompiledShopPurchase.Bulk -> buyResource(buy, purchase, party, quantity, totalPrice)
        }

    private fun buyInventoryItem(
        buy: CompiledShopBuy,
        offer: CompiledShopItemOffer,
        party: StandaloneParty,
        totalPrice: Int,
    ): ShopResult {
        val freeSlot = party.inventory.firstOrNull { slot -> slot.item == null }?.slot
        val targetSlot = freeSlot ?: chooseDropSlot(buy, party)
        if (targetSlot == null) {
            return ShopResult(party, buy.cantCarry.renderStandaloneLines(content))
        }
        val updated =
            party
                .withInventoryItem(targetSlot, offer.item)
                .withGold(party.resources.gold - totalPrice)
        return ShopResult(updated, buy.deal.renderStandaloneLines(content) + "Bought ${itemName(offer.item)}.")
    }

    private fun chooseDropSlot(
        buy: CompiledShopBuy,
        party: StandaloneParty,
    ): Int? {
        if (buy.inventoryApplication.overflow != RewardOverflowPolicy.PromptDropThenPlace) return null
        screen.writeText(buy.cantCarry.renderStandaloneLines(content) + "" + "Drop an item? (Y/N)")
        val confirm = input.readCommand()?.uppercaseChar() ?: return null
        if (confirm != 'Y') return null
        val lines =
            buildList {
                add("Drop which item?")
                add("Any other key leaves.")
                addAll(inventoryLines(party))
            }
        screen.writeText(lines)
        val choice = input.readCommand() ?: return null
        val slot = choice.inventorySlotFromKey() ?: return null
        return slot.takeIf { party.inventoryItem(it) != null }
    }

    private fun buyResource(
        buy: CompiledShopBuy,
        purchase: CompiledShopPurchase.Bulk,
        party: StandaloneParty,
        quantity: Int,
        totalPrice: Int,
    ): ShopResult {
        if (quantity == 0 && purchase.zeroQuantityDeal == ShopZeroQuantityDealPolicy.NonZeroOnly) {
            return ShopResult(party, purchase.tooMany.renderShopLines(carryCap = capacityLimit(party, purchase.cap)))
        }
        val updated =
            party
                .withResource(purchase.resource, party.resourceAmount(purchase.resource) + quantity)
                .withGold(party.resources.gold - totalPrice)
        return ShopResult(updated, buy.deal.renderStandaloneLines(content))
    }

    private fun runSell(
        sell: CompiledShopSell,
        party: StandaloneParty,
    ): StandaloneParty {
        screen.writeText(sellPromptLines(sell, party))
        val choice = input.readCommand() ?: return party
        val slot = choice.inventorySlotFromKey() ?: return party
        val item = party.inventoryItem(slot)
        if (item == null) {
            writeTextAndWait(listOf("That slot is empty."))
            return party
        }
        if (!sell.accepts(item)) {
            writeTextAndWait(sell.wrongKind.renderStandaloneLines(content))
            return party
        }

        val quote = sellQuote(sell, item, party)
        val price = quote.price ?: return quote.party
        val offerLines = sell.offer.renderShopLines(item = item, price = price)
        screen.writeText(
            offerLines +
                "Accept? ${sell.saleKeys.accept.primaryStandaloneLabel(content)}/" +
                sell.saleKeys.decline.primaryStandaloneLabel(content),
        )
        val confirm = input.readCommand() ?: return quote.party
        if (!sell.saleKeys.accept.matchesStandalone(content, confirm)) return quote.party
        val updated =
            quote.party
                .withInventoryItem(slot, null)
                .withGold(quote.party.resources.gold + price)
        val soldLines = sell.sold?.renderStandaloneLines(content) ?: listOf("Sold!")
        writeTextAndWait(soldLines)
        return updated
    }

    private fun writeTextAndWait(lines: List<String>) {
        screen.writeText(lines + "" + "Press any key.")
        input.readCommand()
    }

    private fun sellPromptLines(
        sell: CompiledShopSell,
        party: StandaloneParty,
    ): List<String> =
        buildList {
            addAll(sell.prompt.renderStandaloneLines(content))
            addAll(inventoryLines(party))
        }

    private fun inventoryLines(party: StandaloneParty): List<String> =
        inventoryMenuLines(
            party.inventory.map { slot ->
                inventorySlotKey(slot.slot) to (slot.item?.let(::itemName) ?: "")
            },
        )

    private fun CompiledShopSell.accepts(item: CompiledHandleRef): Boolean {
        if (item in rejectedItems) return false
        if (item in acceptedItems) return true
        return itemRecord(item).kind in acceptedKinds
    }

    private fun purchasePriceLines(
        buy: CompiledShopBuy,
        offer: CompiledShopItemOffer,
        unitPrice: Int,
        totalPrice: Int,
    ): List<String> {
        val priceText =
            when (offer.purchase) {
                is CompiledShopPurchase.Bulk -> buy.unitPrice
                CompiledShopPurchase.InventoryItem -> buy.singlePrice
            }
        val renderedPrice =
            when (offer.purchase) {
                is CompiledShopPurchase.Bulk -> unitPrice
                CompiledShopPurchase.InventoryItem -> totalPrice
            }
        return priceText?.renderShopLines(item = offer.item, price = renderedPrice) ?: listOf("Price: $totalPrice")
    }

    private fun unitPrice(
        offer: CompiledShopItemOffer,
        party: StandaloneParty,
    ): Int = price(offer.price, offer.item, party)

    private fun sellQuote(
        sell: CompiledShopSell,
        item: CompiledHandleRef,
        party: StandaloneParty,
    ): StandaloneSellQuote {
        var current = price(sell.price, item, party)
        val bargain = sell.bargain ?: return StandaloneSellQuote(current, party)
        val draw = party.drawRandom(0, 99)
        bargain.rejectRollBelow?.let { threshold ->
            if (draw.value < threshold) return StandaloneSellQuote(price = null, party = draw.party)
        }
        bargain.bonuses.forEach { bonus ->
            if (draw.value > bonus.rollGreaterThan) {
                current += current / max(1, bonus.addCurrentDivisor)
            }
        }
        return StandaloneSellQuote(current, draw.party)
    }

    private fun price(
        price: CompiledShopPrice,
        item: CompiledHandleRef,
        party: StandaloneParty?,
    ): Int =
        when (price) {
            CompiledShopPrice.ItemCost -> itemRecord(item).cost
            CompiledShopPrice.HalfItemCost -> itemRecord(item).cost / 2
            is CompiledShopPrice.Fixed -> price.amount
            is CompiledShopPrice.LeaderLevelPlus -> (party?.leaderLevel ?: 0) + price.addend
            is CompiledShopPrice.ItemCostPlusLeaderLevelTimes -> itemRecord(item).cost + (party?.leaderLevel ?: 0) * price.multiplier
        }

    private fun remainingCapacity(
        party: StandaloneParty,
        cap: ShopQuantityCap,
    ): Int = (capacityLimit(party, cap) - party.resourceAmount(cap.resource)).coerceAtLeast(0)

    private fun capacityLimit(
        party: StandaloneParty,
        cap: ShopQuantityCap,
    ): Int =
        when (cap) {
            is ShopQuantityCap.FixedAmount -> cap.amount
            is ShopQuantityCap.CarryCapacity -> party.resourceCapacity(cap.resource)
        }

    private fun StandaloneParty.resourceCapacity(resource: Resource): Int {
        val base = realm.baseResourceCapacity[resource] ?: resourceAmount(resource)
        val itemBonus =
            inventory
                .mapNotNull { slot -> slot.item?.let(::itemRecord) }
                .sumOf { item -> item.resourceCapacityBonus[resource] ?: 0 }
        return base + itemBonus
    }

    private fun StandaloneParty.resourceAmount(resource: Resource): Int =
        when (resource) {
            Resource.Food -> resources.food
            Resource.Torches -> resources.torches
        }

    private val StandaloneParty.leaderLevel: Int
        get() = members.firstOrNull()?.level ?: 0

    private fun itemName(item: CompiledHandleRef): String = content.strings.value(itemRecord(item).displayNameString)

    private fun itemRecord(item: CompiledHandleRef): CompiledItemRecord =
        checkNotNull(itemsById[item]) { "unknown compiled item ${content.slug(item)}" }

    private fun CompiledShopPurchase.zeroQuantityRequiresNonZero(): Boolean =
        this is CompiledShopPurchase.Bulk && zeroQuantityDeal == ShopZeroQuantityDealPolicy.NonZeroOnly

    private fun CompiledTextBlock.renderShopLines(
        item: CompiledHandleRef? = null,
        price: Int? = null,
        carryCap: Int? = null,
    ): List<String> =
        renderStandaloneLines(
            content,
            runtimeValue = { part ->
                when (part) {
                    CompiledRuntimeTextPart.OfferedItemName,
                    CompiledRuntimeTextPart.SelectedItemName,
                    -> item?.let(::itemName) ?: "[${part.name}]"

                    CompiledRuntimeTextPart.OfferedItemPrice,
                    CompiledRuntimeTextPart.ShopPrice,
                    -> price?.toString() ?: "[${part.name}]"

                    CompiledRuntimeTextPart.ShopCarryCap -> carryCap?.toString() ?: "[${part.name}]"
                    else -> "[${part.name}]"
                }
            },
        )
}

private data class ShopResult(
    val party: StandaloneParty,
    val lines: List<String>,
)

private data class StandaloneSellQuote(
    val price: Int?,
    val party: StandaloneParty,
)

internal fun CompiledInputBinding.matchesStandalone(
    content: CompiledContentBundle,
    choice: Char,
): Boolean {
    val accepted = listOf(primaryString) + aliasStrings
    return accepted
        .map(content.strings::value)
        .any { value -> value.equals(choice.toString(), ignoreCase = true) }
}

internal fun CompiledInputBinding.primaryStandaloneLabel(content: CompiledContentBundle): String = content.strings.value(primaryString)

private fun CompiledInputBinding.optionLine(content: CompiledContentBundle): String =
    "${primaryStandaloneLabel(content)}. ${content.strings.value(labelString)}"
