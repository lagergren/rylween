/**
 * Data-only standard-conversation continuation snapshots.
 *
 * The live Kotlin speak session is archived under `legacy/reference-runtime`.
 * Active compiled-bundle contracts retain only stable NPC/building handles,
 * prompt state, and nested script continuation records.
 */
package rylween.runtime.town

import rylween.dsl.BuildingId
import rylween.dsl.NpcId
import rylween.dsl.TextBlock
import rylween.runtime.ScriptContinuationSnapshot
import rylween.runtime.ScriptRootRef

/** Portable conversation session state stored inside a script continuation snapshot. */
data class SpeakSessionSnapshot(
    val building: BuildingId,
    val selectedNpc: NpcId? = null,
    val rumourTold: Boolean = false,
    val step: SpeakSavedStep,
)

/** Snapshot form of the active conversation prompt or branch state. */
sealed interface SpeakSavedStep {
    object ChooseNpc : SpeakSavedStep

    data class ScriptBranch(
        val npc: NpcId,
        val root: ScriptRootRef,
        val next: SpeakSavedStep,
        val continuation: ScriptContinuationSnapshot? = null,
    ) : SpeakSavedStep

    data class MainMenu(
        val npc: NpcId,
    ) : SpeakSavedStep

    data class QuestionMenu(
        val npc: NpcId,
    ) : SpeakSavedStep

    data class Message(
        val blocks: List<TextBlock>,
        val next: SpeakSavedStep,
    ) : SpeakSavedStep

    object Finished : SpeakSavedStep
}
