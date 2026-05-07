/**
 * Engine snapshot records for blocked random-item offer sessions.
 *
 * Parent scripts embed these records beside their own continuation frames when
 * a tavern-style offer is waiting for buy/decline or inventory drop input. The
 * record stores the selected item and prompt state; [RandomItemOfferSession]
 * remains the live state machine that applies gold and inventory mutations.
 */

package rylween.runtime.reward

import rylween.dsl.InputBindingModel
import rylween.dsl.ItemId
import rylween.dsl.PartyState
import rylween.dsl.RandomItemOfferModel
import rylween.dsl.RuntimeContent
import rylween.dsl.TextBlock

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

/** Converts a live random-item offer into a portable snapshot. */
fun RandomItemOfferSession.toSnapshot(): RandomItemOfferSessionSnapshot =
    RandomItemOfferSessionSnapshot(
        offer = offer,
        item = item,
        itemName = itemName,
        price = price,
        step = step.toSnapshot(),
        lastResult = lastResult?.toSnapshot(),
    )

/** Restores a random-item offer from snapshot data and current [party] state. */
fun RandomItemOfferSessionSnapshot.toRandomItemOfferSession(
    party: PartyState,
    content: RuntimeContent,
): RandomItemOfferSession =
    RandomItemOfferSession(
        offer = offer,
        party = party,
        item = item,
        itemName = itemName,
        price = price,
        step = step.toRandomItemOfferStep(party),
        lastResult = lastResult?.toRandomItemOfferResult(party),
        content = content,
    )

private fun RandomItemOfferStep.toSnapshot(): RandomItemOfferSavedStep =
    when (this) {
        is RandomItemOfferStep.Choice -> RandomItemOfferSavedStep.Choice(offerText, prompt, item, itemName, price, buy, decline)
        is RandomItemOfferStep.DropSlot -> RandomItemOfferSavedStep.DropSlot(prompt, item, itemName, price, result.toSnapshot())
        is RandomItemOfferStep.Message -> RandomItemOfferSavedStep.Message(text, next.toSnapshot(), result.toSnapshot())
        is RandomItemOfferStep.Finished -> RandomItemOfferSavedStep.Finished(result.toSnapshot())
    }

private fun RandomItemOfferSavedStep.toRandomItemOfferStep(party: PartyState): RandomItemOfferStep =
    when (this) {
        is RandomItemOfferSavedStep.Choice ->
            RandomItemOfferStep.Choice(
                offerText = offerText,
                prompt = prompt,
                item = item,
                itemName = itemName,
                price = price,
                buy = buy,
                decline = decline,
            )
        is RandomItemOfferSavedStep.DropSlot ->
            RandomItemOfferStep.DropSlot(prompt, item, itemName, price, result.toRandomItemOfferResult(party))
        is RandomItemOfferSavedStep.Message ->
            RandomItemOfferStep.Message(text, next.toRandomItemOfferStep(party), result.toRandomItemOfferResult(party))
        is RandomItemOfferSavedStep.Finished -> RandomItemOfferStep.Finished(result.toRandomItemOfferResult(party))
    }

private fun RandomItemOfferResult.toSnapshot(): RandomItemOfferResultSnapshot = RandomItemOfferResultSnapshot(status, item, price, slot)

private fun RandomItemOfferResultSnapshot.toRandomItemOfferResult(party: PartyState): RandomItemOfferResult =
    RandomItemOfferResult(status, party, item, price, slot)
