// Resumable script-execution shape shared by town services and any other
// stateful authored-script runner. Pure data; no rendering, no I/O.

package rylween.runtime

import rylween.dsl.*
import rylween.runtime.battle.BattleSession
import rylween.runtime.reward.RandomItemOfferSession
import rylween.runtime.reward.RewardGrantSession
import rylween.runtime.town.SpeakRuntimeContent
import rylween.runtime.town.SpeakSession

enum class ScriptRootKind {
    Unspecified,
    Scene,
    DungeonEvent,
    Spell,
    ItemUse,
    DialogueBranch,
    QuestionAction,
}

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

    val isSpecified: Boolean get() = kind != ScriptRootKind.Unspecified

    fun sameRoot(other: ScriptRootRef): Boolean =
        kind == other.kind && slug == other.slug

    companion object {
        val Unspecified: ScriptRootRef = ScriptRootRef(ScriptRootKind.Unspecified)

        fun scene(id: SceneId, denseId: Int? = null): ScriptRootRef =
            ScriptRootRef(ScriptRootKind.Scene, denseId, id.slug)

        fun dungeonEvent(id: DungeonEventId, denseId: Int? = null): ScriptRootRef =
            ScriptRootRef(ScriptRootKind.DungeonEvent, denseId, id.slug)

        fun spell(id: SpellId, denseId: Int? = null): ScriptRootRef =
            ScriptRootRef(ScriptRootKind.Spell, denseId, id.slug)

        fun itemUse(id: ItemId, denseId: Int? = null): ScriptRootRef =
            ScriptRootRef(ScriptRootKind.ItemUse, denseId, id.slug)

        fun dialogueBranch(npc: NpcId, branch: DialogueBranchModel, denseId: Int? = null): ScriptRootRef =
            ScriptRootRef(
                ScriptRootKind.DialogueBranch,
                denseId,
                listOfNotNull(
                    "dialogue",
                    npc.slug,
                    branch.whenState?.let { state -> "${state.flag.slug}.${state.native}" } ?: "default",
                ).joinToString("."),
            )

        fun questionAction(action: QuestionActionScriptModel, denseId: Int? = null): ScriptRootRef =
            ScriptRootRef(
                ScriptRootKind.QuestionAction,
                denseId,
                "question.${action.topic.name.lowercase()}.${action.job.slug}",
            )
    }
}

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

    fun rootFor(fallback: ScriptRootRef): ScriptRootRef =
        roots.firstOrNull { root -> root.sameRoot(fallback) } ?: fallback
}

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

fun RuntimeContent.scriptRootForScene(id: SceneId): ScriptRootRef =
    scriptRootCatalog().rootFor(ScriptRootRef.scene(id))

fun RuntimeContent.scriptRootForDungeonEvent(id: DungeonEventId): ScriptRootRef =
    scriptRootCatalog().rootFor(ScriptRootRef.dungeonEvent(id))

fun RuntimeContent.scriptRootForSpell(id: SpellId): ScriptRootRef =
    scriptRootCatalog().rootFor(ScriptRootRef.spell(id))

fun SpeakRuntimeContent.scriptRootForDialogueBranch(npc: NpcId, branch: DialogueBranchModel): ScriptRootRef =
    ScriptRootRef.dialogueBranch(npc, branch)

fun SpeakRuntimeContent.scriptRootForQuestionAction(action: QuestionActionScriptModel): ScriptRootRef =
    ScriptRootRef.questionAction(action)

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

data class ScriptSegment(
    val opIndex: Int,
    val branch: ScriptBranch,
    val caseIndex: Int? = null,
    val key: Char? = null,
)

data class ScriptBlockRef(
    val segments: List<ScriptSegment> = emptyList(),
) {
    fun child(
        opIndex: Int,
        branch: ScriptBranch,
        caseIndex: Int? = null,
        key: Char? = null,
    ): ScriptBlockRef =
        copy(segments = segments + ScriptSegment(opIndex, branch, caseIndex, key))

    companion object {
        val Root: ScriptBlockRef = ScriptBlockRef()
    }
}

data class ScriptFrame(
    val block: ScriptBlockRef,
    val nextIndex: Int,
    val loop: Boolean = false,
    val iteration: Int = 0,
    val localName: String? = null,
    val localValue: Int? = null,
)

data class ScriptContinuation(
    val runtime: RuntimeSessionState,
    val root: ScriptRootRef = ScriptRootRef.Unspecified,
    val locals: Map<String, Int> = emptyMap(),
    val realmScalars: Map<String, Int> = emptyMap(),
    val stack: List<ScriptFrame>,
    val speakSession: SpeakSession? = null,
    val battleSession: BattleSession? = null,
    val randomItemOfferSession: RandomItemOfferSession? = null,
    val rewardGrantSession: RewardGrantSession? = null,
    val activeInventorySlot: Int? = null,
) {
    val party: PartyState get() = runtime.activeParty
    val realm: RealmState get() = runtime.realm

    fun withRoot(root: ScriptRootRef): ScriptContinuation {
        if (!root.isSpecified) return this
        if (!this.root.isSpecified) return copy(root = root)
        require(this.root.sameRoot(root)) {
            "script continuation root ${this.root.kind}.${this.root.slug} cannot resume as ${root.kind}.${root.slug}"
        }
        return if (this.root.denseId == null && root.denseId != null) {
            copy(root = root)
        } else {
            this
        }
    }

    companion object {
        fun start(
            runtime: RuntimeSessionState,
            root: ScriptRootRef = ScriptRootRef.Unspecified,
        ): ScriptContinuation =
            ScriptContinuation(
                runtime = runtime,
                root = root,
                stack = listOf(ScriptFrame(ScriptBlockRef.Root, nextIndex = 0)),
            )

        fun start(
            party: PartyState,
            root: ScriptRootRef = ScriptRootRef.Unspecified,
        ): ScriptContinuation =
            start(RuntimeSessionState.singleParty(party, hour = 0, currentTown = null), root)
    }
}

data class ScriptContinuationSnapshot(
    val root: ScriptRootRef = ScriptRootRef.Unspecified,
    val locals: Map<String, Int> = emptyMap(),
    val realmScalars: Map<String, Int> = emptyMap(),
    val stack: List<ScriptFrame>,
    val activeInventorySlot: Int? = null,
)

fun ScriptContinuation.toSnapshot(): ScriptContinuationSnapshot {
    require(speakSession == null) { "script continuation with active speak session cannot be saved yet" }
    require(battleSession == null) { "script continuation with active battle session cannot be saved yet" }
    require(randomItemOfferSession == null) {
        "script continuation with active random-item offer session cannot be saved yet"
    }
    require(rewardGrantSession == null) { "script continuation with active reward grant session cannot be saved yet" }
    return ScriptContinuationSnapshot(
        root = root,
        locals = locals,
        realmScalars = realmScalars,
        stack = stack,
        activeInventorySlot = activeInventorySlot,
    )
}

fun ScriptContinuationSnapshot.toContinuation(runtime: RuntimeSessionState): ScriptContinuation =
    ScriptContinuation(
        runtime = runtime,
        root = root,
        locals = locals,
        realmScalars = realmScalars,
        stack = stack,
        activeInventorySlot = activeInventorySlot,
    )
