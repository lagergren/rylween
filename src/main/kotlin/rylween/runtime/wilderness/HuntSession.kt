// Reusable wilderness hunt session. Backend-neutral: a frontend feeds the
// selected member, then renders authored narration blocks until Finished.

package rylween.runtime.wilderness

import rylween.dsl.HuntRulesModel
import rylween.dsl.PartyState
import rylween.dsl.PcClass
import rylween.dsl.TextBlock
import rylween.dsl.chance
import rylween.runtime.RuntimeSessionState
import rylween.runtime.drawRandom
import rylween.runtime.party

enum class HuntSessionStatus {
    Succeeded,
    Failed,
    DeadMember,
    NotRanger,
    TooFewMoves,
}

data class HuntSessionResult(
    val runtime: RuntimeSessionState,
    val status: HuntSessionStatus,
    val memberNumber: Int? = null,
    val foodGained: Int = 0,
)

sealed interface HuntSessionInput {
    data class Member(
        val memberNumber: Int,
    ) : HuntSessionInput

    object Continue : HuntSessionInput
}

sealed interface HuntSessionStep {
    data class ChooseMember(
        val prompt: TextBlock,
    ) : HuntSessionStep

    data class Messages(
        val blocks: List<TextBlock>,
        val next: HuntSessionStep,
        val memberNumber: Int? = null,
        val foodGained: Int = 0,
    ) : HuntSessionStep

    data class Finished(
        val result: HuntSessionResult,
    ) : HuntSessionStep
}

data class HuntSession(
    val runtime: RuntimeSessionState,
    val rules: HuntRulesModel,
    val step: HuntSessionStep,
    val lastResult: HuntSessionResult? = null,
) {
    val party: PartyState get() = runtime.activeParty

    fun memberChoices(): List<Int> = (1..runtime.realm.partyMemberMax.coerceAtLeast(party.members.size)).toList()

    fun apply(input: HuntSessionInput): HuntSession =
        when (val current = step) {
            is HuntSessionStep.ChooseMember -> {
                val member =
                    (input as? HuntSessionInput.Member)
                        ?: error("hunt session is asking for a member")
                chooseMember(member.memberNumber)
            }
            is HuntSessionStep.Messages -> {
                require(input is HuntSessionInput.Continue) {
                    "hunt session is showing messages"
                }
                copy(step = current.next, lastResult = (current.next as? HuntSessionStep.Finished)?.result ?: lastResult)
            }
            is HuntSessionStep.Finished ->
                error("hunt session is already finished: $current")
        }

    private fun chooseMember(memberNumber: Int): HuntSession {
        val member = party.members.getOrNull(memberNumber - 1)
        if (member == null || member.dead) {
            val result = HuntSessionResult(runtime, HuntSessionStatus.DeadMember, memberNumber = memberNumber)
            return copy(
                step =
                    HuntSessionStep.Messages(
                        blocks = listOf(rules.dead),
                        next = HuntSessionStep.ChooseMember(rules.whoPrompt),
                        memberNumber = memberNumber,
                    ),
                lastResult = result,
            )
        }

        if (member.pcClass != PcClass.Ranger) {
            val result = HuntSessionResult(runtime, HuntSessionStatus.NotRanger, memberNumber = memberNumber)
            return copy(
                step =
                    HuntSessionStep.Messages(
                        blocks = listOf(rules.notRanger),
                        next = HuntSessionStep.Finished(result),
                        memberNumber = memberNumber,
                    ),
                lastResult = result,
            )
        }

        if (party.resources.timeLeft < rules.timeCost) {
            val result = HuntSessionResult(runtime, HuntSessionStatus.TooFewMoves, memberNumber = memberNumber)
            return copy(
                step =
                    HuntSessionStep.Messages(
                        blocks = listOf(rules.tooFewMoves),
                        next = HuntSessionStep.Finished(result),
                        memberNumber = memberNumber,
                    ),
                lastResult = result,
            )
        }

        val afterTime = runtime.spendHuntTime(rules.timeCost)
        val chance = afterTime.drawRandom(0, 99)
        val succeeded = chance.value < rules.successChancePercent
        return if (succeeded) {
            val food = chance.runtime.drawRandom(rules.foodMin, rules.foodMax)
            val gained = food.value + party.leaderLevel()
            val afterFood = food.runtime.addHuntFood(gained)
            val result =
                HuntSessionResult(
                    runtime = afterFood,
                    status = HuntSessionStatus.Succeeded,
                    memberNumber = memberNumber,
                    foodGained = gained,
                )
            copy(
                runtime = afterFood,
                step =
                    HuntSessionStep.Messages(
                        blocks = listOf(rules.tries, rules.succeeds),
                        next = HuntSessionStep.Finished(result),
                        memberNumber = memberNumber,
                        foodGained = gained,
                    ),
                lastResult = result,
            )
        } else {
            val result =
                HuntSessionResult(
                    runtime = chance.runtime,
                    status = HuntSessionStatus.Failed,
                    memberNumber = memberNumber,
                )
            copy(
                runtime = chance.runtime,
                step =
                    HuntSessionStep.Messages(
                        blocks = listOf(rules.tries, rules.fails),
                        next = HuntSessionStep.Finished(result),
                        memberNumber = memberNumber,
                    ),
                lastResult = result,
            )
        }
    }

    companion object {
        fun start(
            runtime: RuntimeSessionState,
            rules: HuntRulesModel,
        ): HuntSession = HuntSession(runtime = runtime, rules = rules, step = HuntSessionStep.ChooseMember(rules.whoPrompt))
    }
}

private fun RuntimeSessionState.spendHuntTime(timeCost: Int): RuntimeSessionState =
    withActiveParty(
        activeParty.copy(
            resources = activeParty.resources.copy(timeLeft = activeParty.resources.timeLeft - timeCost),
        ),
    )

private fun RuntimeSessionState.addHuntFood(amount: Int): RuntimeSessionState =
    withActiveParty(
        activeParty.copy(
            resources = activeParty.resources.copy(food = activeParty.resources.food + amount),
        ),
    )

private fun PartyState.leaderLevel(): Int = members.firstOrNull()?.level ?: 0
