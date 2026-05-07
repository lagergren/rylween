/**
 * Engine snapshot records for blocked standard conversation sessions.
 *
 * `runtime/ScriptContinuation.kt` embeds these records when a parent authored
 * script or compiled VM is blocked inside [SpeakSession]. The snapshot stores
 * stable handles and prompt state; [SpeakSession] still owns NPC selection,
 * visited-bit mutation, question routing, and rumour behavior.
 */

package rylween.runtime.town

import rylween.dsl.BuildingId
import rylween.dsl.CharacterModel
import rylween.dsl.DialogueBranchModel
import rylween.dsl.NpcId
import rylween.dsl.Op
import rylween.dsl.RuntimeContent
import rylween.dsl.TextBlock
import rylween.runtime.RuntimeSessionState
import rylween.runtime.ScriptContinuationSnapshot
import rylween.runtime.ScriptRootKind
import rylween.runtime.ScriptRootRef
import rylween.runtime.scriptRootForDialogueBranch
import rylween.runtime.scriptRootForQuestionAction
import rylween.runtime.toContinuation
import rylween.runtime.toSnapshot

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

/** Converts a live conversation into a portable snapshot. */
fun SpeakSession.toSnapshot(): SpeakSessionSnapshot =
    SpeakSessionSnapshot(
        building = building,
        selectedNpc = selectedNpc?.id,
        rumourTold = rumourTold,
        step = step.toSnapshot(),
    )

/** Restores a conversation session from snapshot data and current authored [content]. */
fun SpeakSessionSnapshot.toSpeakSession(
    runtime: RuntimeSessionState,
    content: RuntimeContent,
): SpeakSession {
    val speakContent = content.speakRuntimeContent()
    return SpeakSession(
        content = speakContent,
        runtime = runtime,
        building = building,
        selectedNpc = selectedNpc?.let(speakContent::character),
        rumourTold = rumourTold,
        step = step.toSpeakStep(building, runtime, content, speakContent),
    )
}

private fun SpeakStep.toSnapshot(): SpeakSavedStep =
    when (this) {
        is SpeakStep.ChooseNpc -> SpeakSavedStep.ChooseNpc
        is SpeakStep.ScriptBranch ->
            SpeakSavedStep.ScriptBranch(
                npc = npc.id,
                root = root,
                next = next.toSnapshot(),
                continuation = continuation?.toSnapshot(),
            )
        is SpeakStep.MainMenu -> SpeakSavedStep.MainMenu(npc.id)
        is SpeakStep.QuestionMenu -> SpeakSavedStep.QuestionMenu(npc.id)
        is SpeakStep.Message -> SpeakSavedStep.Message(blocks, next.toSnapshot())
        is SpeakStep.Finished -> SpeakSavedStep.Finished
    }

private fun SpeakSavedStep.toSpeakStep(
    building: BuildingId,
    runtime: RuntimeSessionState,
    content: RuntimeContent,
    speakContent: SpeakRuntimeContent,
): SpeakStep =
    when (this) {
        SpeakSavedStep.ChooseNpc -> SpeakStep.ChooseNpc(speakContent.charactersFor(runtime, building))
        is SpeakSavedStep.ScriptBranch -> {
            val branch = speakContent.scriptBranch(npc, root)
            SpeakStep.ScriptBranch(
                npc = speakContent.character(npc),
                ops = branch.ops,
                root = root,
                next = next.toSpeakStep(building, runtime, content, speakContent),
                continuation = continuation?.toContinuation(runtime, content),
            )
        }
        is SpeakSavedStep.MainMenu ->
            SpeakStep.MainMenu(speakContent.character(npc), speakContent.conversation.menu.actions)
        is SpeakSavedStep.QuestionMenu ->
            SpeakStep.QuestionMenu(speakContent.character(npc), speakContent.conversation.menu.questions)
        is SpeakSavedStep.Message ->
            SpeakStep.Message(blocks, next.toSpeakStep(building, runtime, content, speakContent))
        SpeakSavedStep.Finished -> SpeakStep.Finished(runtime)
    }

private val SpeakRuntimeContent.conversation
    get() =
        requireNotNull(speak.standardConversation) {
            "standard conversation system is not configured"
        }

private fun SpeakRuntimeContent.character(npc: NpcId): CharacterModel =
    characters.firstOrNull { character -> character.id == npc }
        ?: error("speak snapshot references unknown NPC ${npc.slug}")

private data class RestoredSpeakBranch(
    val ops: List<Op>,
)

private fun SpeakRuntimeContent.scriptBranch(
    npc: NpcId,
    root: ScriptRootRef,
): RestoredSpeakBranch =
    when (root.kind) {
        ScriptRootKind.DialogueBranch ->
            dialogue
                .firstOrNull { dialogue -> dialogue.npc == npc }
                ?.branches
                ?.firstOrNull { branch -> scriptRootForDialogueBranch(npc, branch).sameRoot(root) }
                ?.let(DialogueBranchModel::ops)
                ?.let(::RestoredSpeakBranch)
        ScriptRootKind.QuestionAction ->
            speak.questionActions
                .firstOrNull { action -> scriptRootForQuestionAction(action).sameRoot(root) }
                ?.ops
                ?.let(::RestoredSpeakBranch)
        else -> null
    } ?: error("speak snapshot references unknown script root ${root.kind}.${root.slug}")
