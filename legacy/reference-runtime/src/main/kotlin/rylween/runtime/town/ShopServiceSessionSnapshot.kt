/**
 * Engine snapshot records for blocked reusable shop sessions.
 *
 * A parent script stores this record when [ShopServiceSession] is waiting on a
 * shop prompt. The snapshot keeps only stable authored handles plus transaction
 * prompt state; shop catalog definitions are recovered from current
 * [rylween.dsl.RuntimeContent] when the live session is restored.
 */

package rylween.runtime.town

import rylween.dsl.BuildingId
import rylween.dsl.ItemId
import rylween.dsl.PartyState
import rylween.dsl.RuntimeContent
import rylween.dsl.ShopItemOfferModel
import rylween.dsl.ShopPurchaseModel
import rylween.dsl.ShopServiceModel

/** Portable shop-service state stored inside a script continuation snapshot. */
data class ShopServiceSessionSnapshot(
    val building: BuildingId,
    val step: ShopServiceSavedStep,
    val lastTransaction: ShopTransactionResultSnapshot? = null,
    val lastSellQuote: ShopSellQuoteResultSnapshot? = null,
)

/** Snapshot form of shop transaction results. */
data class ShopTransactionResultSnapshot(
    val status: ShopTransactionStatus,
    val item: ItemId? = null,
    val goldDelta: Int = 0,
    val slot: Int? = null,
    val quantity: Int = 1,
)

/** Snapshot form of shop sale quotes. */
data class ShopSellQuoteResultSnapshot(
    val status: ShopSellQuoteStatus,
    val item: ItemId? = null,
    val slot: Int? = null,
    val price: Int = 0,
    val bargainRolls: List<Int> = emptyList(),
)

/** Snapshot token for the active shop prompt. */
sealed interface ShopServiceSavedStep {
    object Intro : ShopServiceSavedStep

    object MainMenu : ShopServiceSavedStep

    object SelectCatalog : ShopServiceSavedStep

    data class BuyItem(
        val catalogKeys: List<Char>,
    ) : ShopServiceSavedStep

    data class Quantity(
        val item: ItemId,
    ) : ShopServiceSavedStep

    data class ConfirmPurchase(
        val item: ItemId,
        val quantity: Int,
        val unitPrice: Int,
        val totalPrice: Int,
    ) : ShopServiceSavedStep

    data class DropSlot(
        val item: ItemId,
        val quantity: Int,
        val result: ShopTransactionResultSnapshot,
    ) : ShopServiceSavedStep

    object SellSlot : ShopServiceSavedStep

    data class ConfirmSale(
        val quote: ShopSellQuoteResultSnapshot,
    ) : ShopServiceSavedStep

    object Finished : ShopServiceSavedStep
}

/** Converts a live shop service into a portable snapshot. */
fun ShopServiceSession.toSnapshot(): ShopServiceSessionSnapshot =
    ShopServiceSessionSnapshot(
        building = shop.building,
        step = step.toSnapshot(),
        lastTransaction = lastTransaction?.toSnapshot(),
        lastSellQuote = lastSellQuote?.toSnapshot(),
    )

/** Restores a shop service from snapshot data and current authored [content]. */
fun ShopServiceSessionSnapshot.toShopServiceSession(
    party: PartyState,
    content: RuntimeContent,
): ShopServiceSession {
    val shop =
        content.scenes.shopServicesByBuilding()[building]
            ?: error("shop snapshot references unknown shop building ${building.slug}")
    return ShopServiceSession(
        shop = shop,
        party = party,
        step = step.toShopServiceStep(party, shop),
        lastTransaction = lastTransaction?.toShopTransactionResult(party),
        lastSellQuote = lastSellQuote?.toShopSellQuoteResult(party),
        content = content,
    )
}

private fun ShopServiceStep.toSnapshot(): ShopServiceSavedStep =
    when (this) {
        is ShopServiceStep.Intro -> ShopServiceSavedStep.Intro
        is ShopServiceStep.MainMenu -> ShopServiceSavedStep.MainMenu
        is ShopServiceStep.SelectCatalog -> ShopServiceSavedStep.SelectCatalog
        is ShopServiceStep.BuyItem -> ShopServiceSavedStep.BuyItem(catalogs.map { catalog -> catalog.key })
        is ShopServiceStep.Quantity -> ShopServiceSavedStep.Quantity(offer.item)
        is ShopServiceStep.ConfirmPurchase ->
            ShopServiceSavedStep.ConfirmPurchase(
                item = offer.item,
                quantity = quantity,
                unitPrice = unitPrice,
                totalPrice = totalPrice,
            )
        is ShopServiceStep.DropSlot -> ShopServiceSavedStep.DropSlot(offer.item, quantity, result.toSnapshot())
        is ShopServiceStep.SellSlot -> ShopServiceSavedStep.SellSlot
        is ShopServiceStep.ConfirmSale -> ShopServiceSavedStep.ConfirmSale(quote.toSnapshot())
        is ShopServiceStep.Finished -> ShopServiceSavedStep.Finished
    }

private fun ShopServiceSavedStep.toShopServiceStep(
    party: PartyState,
    shop: ShopServiceModel,
): ShopServiceStep =
    when (this) {
        ShopServiceSavedStep.Intro -> ShopServiceStep.Intro(shop.intro)
        ShopServiceSavedStep.MainMenu -> ShopServiceStep.MainMenu(shop.menu, canBuy = shop.buy != null, canSell = shop.sell != null)
        ShopServiceSavedStep.SelectCatalog -> {
            val buy = checkNotNull(shop.buy) { "shop snapshot select-catalog step requires buy section" }
            ShopServiceStep.SelectCatalog(buy.prompt, buy.catalogs)
        }
        is ShopServiceSavedStep.BuyItem -> {
            val buy = checkNotNull(shop.buy) { "shop snapshot buy-item step requires buy section" }
            val selected =
                catalogKeys
                    .map { key ->
                        buy.catalogs.firstOrNull { catalog -> catalog.key == key }
                            ?: error("shop snapshot references unknown catalog key $key")
                    }
            ShopServiceStep.BuyItem(buy.prompt, selected)
        }
        is ShopServiceSavedStep.Quantity -> {
            val offer = shop.offer(item)
            val purchase =
                offer.purchase as? ShopPurchaseModel.Bulk
                    ?: error("shop snapshot quantity step requires bulk purchase item ${item.slug}")
            ShopServiceStep.Quantity(offer, purchase.quantityPrompt)
        }
        is ShopServiceSavedStep.ConfirmPurchase -> {
            val buy = checkNotNull(shop.buy) { "shop snapshot confirm-purchase step requires buy section" }
            ShopServiceStep.ConfirmPurchase(shop.offer(item), quantity, unitPrice, totalPrice, buy.payPrompt)
        }
        is ShopServiceSavedStep.DropSlot -> {
            val buy = checkNotNull(shop.buy) { "shop snapshot drop-slot step requires buy section" }
            ShopServiceStep.DropSlot(shop.offer(item), quantity, buy.cantCarry, result.toShopTransactionResult(party))
        }
        ShopServiceSavedStep.SellSlot -> {
            val sell = checkNotNull(shop.sell) { "shop snapshot sell-slot step requires sell section" }
            ShopServiceStep.SellSlot(sell.prompt)
        }
        is ShopServiceSavedStep.ConfirmSale -> {
            val sell = checkNotNull(shop.sell) { "shop snapshot confirm-sale step requires sell section" }
            ShopServiceStep.ConfirmSale(quote.toShopSellQuoteResult(party), sell.offer)
        }
        ShopServiceSavedStep.Finished -> ShopServiceStep.Finished(shop.goodbye)
    }

private fun ShopServiceModel.offer(item: ItemId): ShopItemOfferModel =
    checkNotNull(buy?.offerFor(item)) {
        "shop snapshot references item ${item.slug} that is not sold by ${building.slug}"
    }

private fun ShopTransactionResult.toSnapshot(): ShopTransactionResultSnapshot =
    ShopTransactionResultSnapshot(status, item, goldDelta, slot, quantity)

private fun ShopTransactionResultSnapshot.toShopTransactionResult(party: PartyState): ShopTransactionResult =
    ShopTransactionResult(status, party, item, goldDelta, slot, quantity)

private fun ShopSellQuoteResult.toSnapshot(): ShopSellQuoteResultSnapshot =
    ShopSellQuoteResultSnapshot(status, item, slot, price, bargainRolls)

private fun ShopSellQuoteResultSnapshot.toShopSellQuoteResult(party: PartyState): ShopSellQuoteResult =
    ShopSellQuoteResult(status, party, item, slot, price, bargainRolls)
