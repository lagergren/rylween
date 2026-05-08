/**
 * Data-only random-item-offer continuation snapshots.
 *
 * The live Kotlin random-offer session is archived under
 * `legacy/reference-runtime`; active compiled-bundle code keeps this portable
 * prompt/result shape for strict snapshots and future non-JVM runtimes.
 */
package rylween.runtime.reward

import rylween.dsl.InputBindingModel
import rylween.dsl.ItemId
import rylween.dsl.RandomItemOfferModel
import rylween.dsl.TextBlock

/** Stable result categories for a random item offer. */
enum class RandomItemOfferStatus {
    Bought,
    Declined,
    NotEnoughGold,
    NeedsDropSlot,
    InventoryFull,
    InvalidDropSlot,
}

/** Portable random-offer state stored inside a script continuation snapshot. */
data class RandomItemOfferSessionSnapshot(
    val offer: RandomItemOfferModel,
    val item: ItemId,
    val itemName: String,
    val price: Int,
    val step: RandomItemOfferSavedStep,
    val lastResult: RandomItemOfferResultSnapshot? = null,
)

/** Snapshot result marker for the active random offer. */
data class RandomItemOfferResultSnapshot(
    val status: RandomItemOfferStatus,
    val item: ItemId,
    val price: Int,
    val slot: Int? = null,
)

/** Snapshot form of random-offer prompt state. */
sealed interface RandomItemOfferSavedStep {
    data class Choice(
        val offerText: TextBlock,
        val prompt: TextBlock,
        val item: ItemId,
        val itemName: String,
        val price: Int,
        val buy: InputBindingModel,
        val decline: InputBindingModel,
    ) : RandomItemOfferSavedStep

    data class DropSlot(
        val prompt: TextBlock,
        val item: ItemId,
        val itemName: String,
        val price: Int,
        val result: RandomItemOfferResultSnapshot,
    ) : RandomItemOfferSavedStep

    data class Message(
        val text: TextBlock,
        val next: RandomItemOfferSavedStep,
        val result: RandomItemOfferResultSnapshot,
    ) : RandomItemOfferSavedStep

    data class Finished(
        val result: RandomItemOfferResultSnapshot,
    ) : RandomItemOfferSavedStep
}
