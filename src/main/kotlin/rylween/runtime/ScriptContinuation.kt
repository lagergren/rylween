/**
 * Resumable script-execution data shared by stateful authored-script runners.
 *
 * Town services, dungeon events, item use, rewards, and future compiled-VM
 * frames all need a backend-neutral continuation shape. Rendering and input are
 * handled by adapters such as
 * `ansi-runtime/src/main/kotlin/rylween/frontend/terminal/TerminalScriptBridge.kt`.
 */

package rylween.runtime

import rylween.dsl.DialogueBranchModel
import rylween.dsl.DungeonEventId
import rylween.dsl.ItemId
import rylween.dsl.NpcId
import rylween.dsl.PartyState
import rylween.dsl.QuestionActionScriptModel
import rylween.dsl.RealmState
import rylween.dsl.RuntimeContent
import rylween.dsl.SceneId
import rylween.dsl.SpellId
import rylween.dsl.dungeonEvent
import rylween.dsl.npc
import rylween.runtime.battle.BattleSession
import rylween.runtime.battle.BattleSessionSnapshot
import rylween.runtime.battle.toBattleSession
import rylween.runtime.battle.toSnapshot
import rylween.runtime.reward.RandomItemOfferSession
import rylween.runtime.reward.RandomItemOfferSessionSnapshot
import rylween.runtime.reward.RewardGrantSession
import rylween.runtime.reward.RewardGrantSessionSnapshot
import rylween.runtime.reward.toRandomItemOfferSession
import rylween.runtime.reward.toRewardGrantSession
import rylween.runtime.reward.toSnapshot
import rylween.runtime.town.ShopServiceSession
import rylween.runtime.town.ShopServiceSessionSnapshot
import rylween.runtime.town.SpeakRuntimeContent
import rylween.runtime.town.SpeakSession
import rylween.runtime.town.SpeakSessionSnapshot
import rylween.runtime.town.toShopServiceSession
import rylween.runtime.town.toSnapshot
import rylween.runtime.town.toSpeakSession
import rylween.runtime.vm.CompiledVmContinuation
import rylween.runtime.vm.CompiledVmContinuationSnapshot
import rylween.runtime.vm.toContinuation
import rylween.runtime.vm.toSnapshot

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

/** Returns the catalogued script root for a scene. */
fun RuntimeContent.scriptRootForScene(id: SceneId): ScriptRootRef = scriptRootCatalog().rootFor(ScriptRootRef.scene(id))

/** Returns the catalogued script root for a dungeon event. */
fun RuntimeContent.scriptRootForDungeonEvent(id: DungeonEventId): ScriptRootRef =
    scriptRootCatalog().rootFor(ScriptRootRef.dungeonEvent(id))

/** Returns the catalogued script root for a spell. */
fun RuntimeContent.scriptRootForSpell(id: SpellId): ScriptRootRef = scriptRootCatalog().rootFor(ScriptRootRef.spell(id))

/** Returns the catalogued script root for an inventory or combat item-use script. */
fun RuntimeContent.scriptRootForItemUse(id: ItemId): ScriptRootRef = scriptRootCatalog().rootFor(ScriptRootRef.itemUse(id))

/** Returns the script root for a conversation dialogue branch. */
fun SpeakRuntimeContent.scriptRootForDialogueBranch(
    npc: NpcId,
    branch: DialogueBranchModel,
): ScriptRootRef = ScriptRootRef.dialogueBranch(npc, branch)

/** Returns the script root for a conversation question action. */
fun SpeakRuntimeContent.scriptRootForQuestionAction(action: QuestionActionScriptModel): ScriptRootRef = ScriptRootRef.questionAction(action)

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
 * Runtime continuation for authored or compiled script execution.
 *
 * The legacy fields describe the authored `Op` interpreter stack and
 * host-owned child sessions such as speak, rewards, random offers, shops, and
 * battles. [compiledVmContinuation] carries the parallel J2 VM frame when a
 * compiled script blocks at a prompt or child-session request; strict runtime
 * snapshots can persist that frame through [ScriptContinuationSnapshot]. These
 * snapshots are engine execution-boundary records, not a decision that every
 * frontend may expose player saves at that point. Active battle children are
 * recorded with their parent frame so a runtime can restore the host battle
 * first, then resume the parent VM after combat finishes.
 */
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
    val shopServiceSession: ShopServiceSession? = null,
    val activeInventorySlot: Int? = null,
    val compiledVmContinuation: CompiledVmContinuation? = null,
) {
    /** Active party in the continuation runtime state. */
    val party: PartyState get() = runtime.activeParty

    /** Realm state in the continuation runtime state. */
    val realm: RealmState get() = runtime.realm

    /** Binds this continuation to [root] or verifies it is already bound to the same root. */
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
        /** Starts a script continuation from live runtime state. */
        fun start(
            runtime: RuntimeSessionState,
            root: ScriptRootRef = ScriptRootRef.Unspecified,
        ): ScriptContinuation =
            ScriptContinuation(
                runtime = runtime,
                root = root,
                stack = listOf(ScriptFrame(ScriptBlockRef.Root, nextIndex = 0)),
            )

        /** Starts a script continuation from a single-party runtime shell. */
        fun start(
            party: PartyState,
            root: ScriptRootRef = ScriptRootRef.Unspecified,
        ): ScriptContinuation = start(RuntimeSessionState.singleParty(party, hour = 0, currentTown = null), root)
    }
}

/**
 * Portable subset of [ScriptContinuation] for strict runtime snapshots.
 *
 * This record is intentionally data-only so it can cross JVM/native/server
 * boundaries. User-visible save policy is enforced by the session/frontend that
 * chooses whether to write a snapshot, not by the presence of this type.
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

/** Converts a continuation into a portable snapshot when supported child sessions can be represented. */
fun ScriptContinuation.toSnapshot(): ScriptContinuationSnapshot =
    ScriptContinuationSnapshot(
        root = root,
        locals = locals,
        realmScalars = realmScalars,
        stack = stack,
        activeInventorySlot = activeInventorySlot,
        speakSession = speakSession?.toSnapshot(),
        battleSession = battleSession?.toSnapshot(),
        randomItemOfferSession = randomItemOfferSession?.toSnapshot(),
        rewardGrantSession = rewardGrantSession?.toSnapshot(),
        shopServiceSession = shopServiceSession?.toSnapshot(),
        compiledVmContinuation = compiledVmContinuation?.toSnapshot(),
    )

/**
 * Restores a runtime continuation from this snapshot and live [runtime].
 *
 * Use the [RuntimeContent] overload when [battleSession] is present so authored
 * combat scripts can be looked up while rebuilding the battle child session.
 */
fun ScriptContinuationSnapshot.toContinuation(runtime: RuntimeSessionState): ScriptContinuation {
    require(speakSession == null) { "script continuation with speak session requires runtime content" }
    require(battleSession == null) { "script continuation with battle session requires runtime content" }
    require(randomItemOfferSession == null) { "script continuation with random-item offer session requires runtime content" }
    require(rewardGrantSession == null) { "script continuation with reward grant session requires runtime content" }
    require(shopServiceSession == null) { "script continuation with shop service session requires runtime content" }
    return toContinuation(runtime, restoredBattleSession = null)
}

/** Restores a runtime continuation and any saveable child sessions from current authored [content]. */
fun ScriptContinuationSnapshot.toContinuation(
    runtime: RuntimeSessionState,
    content: RuntimeContent,
): ScriptContinuation =
    toContinuation(
        runtime = runtime,
        restoredSpeakSession = speakSession?.toSpeakSession(runtime, content),
        restoredBattleSession = battleSession?.toBattleSession(runtime, content),
        restoredRandomItemOfferSession = randomItemOfferSession?.toRandomItemOfferSession(runtime.activeParty, content),
        restoredRewardGrantSession = rewardGrantSession?.toRewardGrantSession(runtime.activeParty, content),
        restoredShopServiceSession = shopServiceSession?.toShopServiceSession(runtime.activeParty, content),
    )

private fun ScriptContinuationSnapshot.toContinuation(
    runtime: RuntimeSessionState,
    restoredSpeakSession: SpeakSession? = null,
    restoredBattleSession: BattleSession?,
    restoredRandomItemOfferSession: RandomItemOfferSession? = null,
    restoredRewardGrantSession: RewardGrantSession? = null,
    restoredShopServiceSession: ShopServiceSession? = null,
): ScriptContinuation =
    ScriptContinuation(
        runtime = runtime,
        root = root,
        locals = locals,
        realmScalars = realmScalars,
        stack = stack,
        activeInventorySlot = activeInventorySlot,
        speakSession = restoredSpeakSession,
        battleSession = restoredBattleSession,
        randomItemOfferSession = restoredRandomItemOfferSession,
        rewardGrantSession = restoredRewardGrantSession,
        shopServiceSession = restoredShopServiceSession,
        compiledVmContinuation = compiledVmContinuation?.toContinuation(runtime),
    )
