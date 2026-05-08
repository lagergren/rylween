/**
 * Data-only shop-service continuation snapshots.
 *
 * The old live shop session belongs to `legacy/reference-runtime`. Active code
 * keeps these stable prompt/result records for strict runtime snapshots and
 * compiled bundle state images.
 */
package rylween.runtime.town

import rylween.dsl.BuildingId
import rylween.dsl.ItemId

/** Stable shop transaction result categories. */
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

/** Stable shop sale quote result categories. */
enum class ShopSellQuoteStatus {
    Quoted,
    ItemNotAcceptedHere,
    EmptySlot,
    Declined,
}

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
