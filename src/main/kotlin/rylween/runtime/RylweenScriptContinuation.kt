// Resumable script-execution shape shared by town services and any other
// stateful authored-script runner. Pure data; no rendering, no I/O.

package rylween.runtime

import rylween.dsl.*

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
        val Root = ScriptBlockRef()
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

    companion object {
        fun start(runtime: RuntimeSessionState): ScriptContinuation =
            ScriptContinuation(
                runtime = runtime,
                stack = listOf(ScriptFrame(ScriptBlockRef.Root, nextIndex = 0)),
            )

        fun start(party: PartyState): ScriptContinuation =
            start(RuntimeSessionState.singleParty(party, hour = 0, currentTown = null))
    }
}

data class ScriptContinuationSnapshot(
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
        locals = locals,
        realmScalars = realmScalars,
        stack = stack,
        activeInventorySlot = activeInventorySlot,
    )
}

fun ScriptContinuationSnapshot.toContinuation(runtime: RuntimeSessionState): ScriptContinuation =
    ScriptContinuation(
        runtime = runtime,
        locals = locals,
        realmScalars = realmScalars,
        stack = stack,
        activeInventorySlot = activeInventorySlot,
    )
