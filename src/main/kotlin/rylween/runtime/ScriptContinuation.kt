/**
 * Portable script-root and continuation snapshot records for compiled bundles.
 *
 * This file is intentionally data-only. The old live authored-script
 * continuation implementation lives under `legacy/reference-runtime`; active
 * compilers and bundle readers keep only the stable root identities, block
 * paths, and nested child-session snapshot shapes needed for serialization and
 * cross-runtime diagnostics.
 */
package rylween.runtime

import rylween.dsl.DialogueBranchModel
import rylween.dsl.DungeonEventId
import rylween.dsl.ItemId
import rylween.dsl.NpcId
import rylween.dsl.QuestionActionScriptModel
import rylween.dsl.RuntimeContent
import rylween.dsl.SceneId
import rylween.dsl.SpellId
import rylween.runtime.battle.BattleSessionSnapshot
import rylween.runtime.reward.RandomItemOfferSessionSnapshot
import rylween.runtime.reward.RewardGrantSessionSnapshot
import rylween.runtime.town.ShopServiceSessionSnapshot
import rylween.runtime.town.SpeakSessionSnapshot
import rylween.runtime.vm.CompiledVmContinuationSnapshot

/** Authored script-root categories used by compiled bundles and save diagnostics. */
enum class ScriptRootKind {
    Unspecified,
    Scene,
    DungeonEvent,
    Spell,
    ItemUse,
    DialogueBranch,
    QuestionAction,
}

/** Stable reference to one authored script root. */
data class ScriptRootRef(
    val kind: ScriptRootKind,
    val denseId: Int? = null,
    val slug: String = "",
) {
    init {
        if (kind == ScriptRootKind.Unspecified) {
            require(denseId == null) { "unspecified script root cannot have a dense id" }
            require(slug.isEmpty()) { "unspecified script root cannot have a slug" }
        } else {
            require(slug.isNotBlank()) { "script root ${kind.name} requires a diagnostic slug" }
            require(denseId == null || denseId > 0) { "script root dense id must be positive" }
        }
    }

    /** True when this ref identifies a real authored root. */
    val isSpecified: Boolean get() = kind != ScriptRootKind.Unspecified

    /** Compares root identity while ignoring optional dense ids. */
    fun sameRoot(other: ScriptRootRef): Boolean = kind == other.kind && slug == other.slug

    companion object {
        /** Placeholder used before a continuation is bound to an authored root. */
        val Unspecified: ScriptRootRef = ScriptRootRef(ScriptRootKind.Unspecified)

        /** Builds a script-root ref for a scene script. */
        fun scene(
            id: SceneId,
            denseId: Int? = null,
        ): ScriptRootRef = ScriptRootRef(ScriptRootKind.Scene, denseId, id.slug)

        /** Builds a script-root ref for a dungeon event script. */
        fun dungeonEvent(
            id: DungeonEventId,
            denseId: Int? = null,
        ): ScriptRootRef = ScriptRootRef(ScriptRootKind.DungeonEvent, denseId, id.slug)

        /** Builds a script-root ref for a spell script. */
        fun spell(
            id: SpellId,
            denseId: Int? = null,
        ): ScriptRootRef = ScriptRootRef(ScriptRootKind.Spell, denseId, id.slug)

        /** Builds a script-root ref for an item-use script. */
        fun itemUse(
            id: ItemId,
            denseId: Int? = null,
        ): ScriptRootRef = ScriptRootRef(ScriptRootKind.ItemUse, denseId, id.slug)

        /** Builds a script-root ref for one dialogue branch body. */
        fun dialogueBranch(
            npc: NpcId,
            branch: DialogueBranchModel,
            denseId: Int? = null,
        ): ScriptRootRef =
            ScriptRootRef(
                ScriptRootKind.DialogueBranch,
                denseId,
                listOfNotNull(
                    "dialogue",
                    npc.slug,
                    branch.whenState?.let { state -> "${state.flag.slug}.${state.native}" } ?: "default",
                ).joinToString("."),
            )

        /** Builds a script-root ref for one scripted question action. */
        fun questionAction(
            action: QuestionActionScriptModel,
            denseId: Int? = null,
        ): ScriptRootRef =
            ScriptRootRef(
                ScriptRootKind.QuestionAction,
                denseId,
                "question.${action.topic.name.lowercase()}.${action.job.slug}",
            )
    }
}

/** Dense-id catalog for all authored script roots in runtime content. */
data class ScriptRootCatalog(
    val roots: List<ScriptRootRef>,
) {
    init {
        require(roots.map { it.kind to it.slug }.toSet().size == roots.size) {
            "script roots must have unique kind/slug pairs"
        }
        require(roots.mapNotNull { it.denseId }.toSet().size == roots.mapNotNull { it.denseId }.size) {
            "script roots must have unique dense ids"
        }
    }

    /** Returns the dense catalog root matching [fallback], or [fallback] if absent. */
    fun rootFor(fallback: ScriptRootRef): ScriptRootRef = roots.firstOrNull { root -> root.sameRoot(fallback) } ?: fallback
}

/** Builds the dense script-root catalog for this runtime content. */
fun RuntimeContent.scriptRootCatalog(): ScriptRootCatalog =
    ScriptRootCatalog(
        buildList {
            fun addDense(root: ScriptRootRef) {
                add(root.copy(denseId = size + 1))
            }

            scenes.forEach { scene -> addDense(ScriptRootRef.scene(scene.id)) }
            dungeons.events.forEach { event -> addDense(ScriptRootRef.dungeonEvent(event.id)) }
            spells.forEach { spell -> addDense(ScriptRootRef.spell(spell.id)) }
            itemUses.forEach { use -> addDense(ScriptRootRef.itemUse(use.id)) }
            dialogue.forEach { npcDialogue ->
                npcDialogue.branches.forEach { branch ->
                    addDense(ScriptRootRef.dialogueBranch(npcDialogue.npc, branch))
                }
            }
            speak.questionActions.forEach { action -> addDense(ScriptRootRef.questionAction(action)) }
        },
    )

/** Nested branch identifiers for resumable script execution. */
enum class ScriptBranch {
    IfThen,
    IfElse,
    LoopBody,
    ForEachMemberBody,
    ForEachMonsterBody,
    SwitchCase,
    MenuOption,
    MenuDefault,
    TextAnswerMatch,
    TextAnswerMiss,
}

/** One nested script-block segment in a continuation path. */
data class ScriptSegment(
    val opIndex: Int,
    val branch: ScriptBranch,
    val caseIndex: Int? = null,
    val key: Char? = null,
)

/** Path to a nested instruction block relative to a script root. */
data class ScriptBlockRef(
    val segments: List<ScriptSegment> = emptyList(),
) {
    /** Returns a child block path under this block. */
    fun child(
        opIndex: Int,
        branch: ScriptBranch,
        caseIndex: Int? = null,
        key: Char? = null,
    ): ScriptBlockRef = copy(segments = segments + ScriptSegment(opIndex, branch, caseIndex, key))

    companion object {
        /** Root script-block path. */
        val Root: ScriptBlockRef = ScriptBlockRef()
    }
}

/** One resumable frame on a script continuation stack. */
data class ScriptFrame(
    val block: ScriptBlockRef,
    val nextIndex: Int,
    val loop: Boolean = false,
    val iteration: Int = 0,
    val localName: String? = null,
    val localValue: Int? = null,
)

/**
 * Portable script continuation stored in strict runtime snapshots.
 *
 * This record is deliberately independent from the old Kotlin session graph.
 * New runtimes should deserialize it as data, validate the referenced compiled
 * root and child snapshot, and resume with their own implementation.
 */
data class ScriptContinuationSnapshot(
    val root: ScriptRootRef = ScriptRootRef.Unspecified,
    val locals: Map<String, Int> = emptyMap(),
    val realmScalars: Map<String, Int> = emptyMap(),
    val stack: List<ScriptFrame>,
    val activeInventorySlot: Int? = null,
    val speakSession: SpeakSessionSnapshot? = null,
    val battleSession: BattleSessionSnapshot? = null,
    val randomItemOfferSession: RandomItemOfferSessionSnapshot? = null,
    val rewardGrantSession: RewardGrantSessionSnapshot? = null,
    val shopServiceSession: ShopServiceSessionSnapshot? = null,
    val compiledVmContinuation: CompiledVmContinuationSnapshot? = null,
)
