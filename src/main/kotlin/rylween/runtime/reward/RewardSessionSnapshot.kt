/**
 * Data-only reward-grant continuation snapshots.
 *
 * Active compiled-bundle contracts keep this portable state shape while the
 * old live reward session implementation is archived under
 * `legacy/reference-runtime`.
 */
package rylween.runtime.reward

import rylween.dsl.ItemId
import rylween.dsl.ScriptRewardModel
import rylween.dsl.TextBlock

/** Stable result categories for a reward grant. */
enum class RewardGrantStatus {
    Granted,
    NeedsDropSlot,
    InventoryFull,
    InvalidDropSlot,
}

/** Portable reward-grant state stored inside a script continuation snapshot. */
data class RewardGrantSessionSnapshot(
    val reward: ScriptRewardModel,
    val pendingItems: List<ItemId>,
    val step: RewardGrantSavedStep,
    val lastResult: RewardGrantResultSnapshot? = null,
)

/** Snapshot result marker for the active reward grant. */
data class RewardGrantResultSnapshot(
    val status: RewardGrantStatus,
    val item: ItemId? = null,
    val slot: Int? = null,
)

/** Snapshot form of reward grant prompt state. */
sealed interface RewardGrantSavedStep {
    data class DropSlot(
        val prompt: TextBlock,
        val item: ItemId,
        val result: RewardGrantResultSnapshot,
    ) : RewardGrantSavedStep

    data class Message(
        val text: TextBlock,
        val result: RewardGrantResultSnapshot,
    ) : RewardGrantSavedStep

    data class Finished(
        val result: RewardGrantResultSnapshot,
    ) : RewardGrantSavedStep
}
