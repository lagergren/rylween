// Resumable script-execution shape shared by town services and any other
// stateful authored-script runner. Pure data; no rendering, no I/O.

package rylween.runtime

import rylween.dsl.*

enum class TownServiceScriptBranch {
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

data class TownServiceScriptSegment(
    val opIndex: Int,
    val branch: TownServiceScriptBranch,
    val caseIndex: Int? = null,
    val key: Char? = null,
)

data class TownServiceScriptBlockRef(
    val segments: List<TownServiceScriptSegment> = emptyList(),
) {
    fun child(
        opIndex: Int,
        branch: TownServiceScriptBranch,
        caseIndex: Int? = null,
        key: Char? = null,
    ): TownServiceScriptBlockRef =
        copy(segments = segments + TownServiceScriptSegment(opIndex, branch, caseIndex, key))

    companion object {
        val Root = TownServiceScriptBlockRef()
    }
}

data class TownServiceScriptFrame(
    val block: TownServiceScriptBlockRef,
    val nextIndex: Int,
    val loop: Boolean = false,
    val iteration: Int = 0,
    val localName: String? = null,
    val localValue: Int? = null,
)

data class TownServiceContinuation(
    val runtime: RuntimeSessionState,
    val locals: Map<String, Int> = emptyMap(),
    val realmScalars: Map<String, Int> = emptyMap(),
    val stack: List<TownServiceScriptFrame>,
    val speakSession: SpeakSession? = null,
    val battleSession: BattleSession? = null,
    val randomItemOfferSession: RandomItemOfferSession? = null,
    val rewardGrantSession: RewardGrantSession? = null,
    val activeInventorySlot: Int? = null,
) {
    val party: PartyState get() = runtime.activeParty
    val realm: RealmState get() = runtime.realm

    companion object {
        fun start(runtime: RuntimeSessionState): TownServiceContinuation =
            TownServiceContinuation(
                runtime = runtime,
                stack = listOf(TownServiceScriptFrame(TownServiceScriptBlockRef.Root, nextIndex = 0)),
            )

        fun start(party: PartyState): TownServiceContinuation =
            start(RuntimeSessionState.singleParty(party, hour = 0, currentTown = null))
    }
}
