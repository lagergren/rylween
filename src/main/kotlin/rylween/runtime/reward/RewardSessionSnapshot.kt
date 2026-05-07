/**
 * Engine snapshot records for reward child sessions in resumable continuations.
 *
 * `runtime/ScriptContinuation.kt` embeds these snapshots while a compiled or
 * authored script is blocked on reward placement. `save/StateSnapshot.kt` can
 * serialize the records into the strict runtime snapshot DSL for validation,
 * tests, or backend handoff. This is not a frontend policy that permits player
 * saves at reward prompts; save availability stays with the active
 * session/frontend. [RewardGrantSession] remains the live runtime state machine
 * that owns inventory placement behavior.
 */

package rylween.runtime.reward

import rylween.dsl.ItemId
import rylween.dsl.PartyState
import rylween.dsl.RuntimeContent
import rylween.dsl.ScriptRewardModel
import rylween.dsl.TextBlock

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

/** Converts a live reward grant into a content-independent snapshot. */
fun RewardGrantSession.toSnapshot(): RewardGrantSessionSnapshot =
    RewardGrantSessionSnapshot(
        reward = reward.copy(gold = null),
        pendingItems = pendingItems,
        step = step.toSnapshot(),
        lastResult = lastResult?.toSnapshot(),
    )

/** Restores a reward grant from snapshot data and current [party] state. */
fun RewardGrantSessionSnapshot.toRewardGrantSession(
    party: PartyState,
    content: RuntimeContent,
): RewardGrantSession =
    RewardGrantSession(
        reward = reward,
        party = party,
        pendingItems = pendingItems,
        step = step.toRewardGrantStep(party),
        lastResult = lastResult?.toRewardGrantResult(party),
        content = content,
    )

private fun RewardGrantStep.toSnapshot(): RewardGrantSavedStep =
    when (this) {
        is RewardGrantStep.DropSlot -> RewardGrantSavedStep.DropSlot(prompt, item, result.toSnapshot())
        is RewardGrantStep.Message -> RewardGrantSavedStep.Message(text, result.toSnapshot())
        is RewardGrantStep.Finished -> RewardGrantSavedStep.Finished(result.toSnapshot())
    }

private fun RewardGrantSavedStep.toRewardGrantStep(party: PartyState): RewardGrantStep =
    when (this) {
        is RewardGrantSavedStep.DropSlot -> RewardGrantStep.DropSlot(prompt, item, result.toRewardGrantResult(party))
        is RewardGrantSavedStep.Message -> RewardGrantStep.Message(text, result.toRewardGrantResult(party))
        is RewardGrantSavedStep.Finished -> RewardGrantStep.Finished(result.toRewardGrantResult(party))
    }

private fun RewardGrantResult.toSnapshot(): RewardGrantResultSnapshot = RewardGrantResultSnapshot(status, item, slot)

private fun RewardGrantResultSnapshot.toRewardGrantResult(party: PartyState): RewardGrantResult =
    RewardGrantResult(status, party, item, slot)
