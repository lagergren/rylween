// Reusable camp / rest session. Backend-neutral: a frontend feeds CampSessionInput
// values (guard Y/N, member digit, continue) and renders the messages step;
// nested ambush combat hands off to BattleSession via the Ambush step. Every
// game rule (food cost, healing, temperature bands and damage, ambush
// probability, surprise-kill text) is authored data on RealmContentModel.camp.

package rylween.runtime.wilderness

import rylween.dsl.ImageId
import rylween.dsl.PartyState
import rylween.dsl.RuntimeContent
import rylween.dsl.TextBlock
import rylween.runtime.RuntimeSessionState
import rylween.runtime.battle.BattleSession
import rylween.runtime.battle.BattleSessionStep
import rylween.runtime.campGearEffect
import rylween.runtime.drawRandom
import rylween.runtime.floorMod
import rylween.runtime.party

enum class CampSessionStatus {
    Rested,
    NotTired,
    NotEnoughFood,
    Ambushed,
    SurpriseKill,
}

data class CampSessionResult(
    val runtime: RuntimeSessionState,
    val status: CampSessionStatus,
)

sealed interface CampSessionInput {
    data class GuardAnswer(
        val yes: Boolean,
    ) : CampSessionInput

    data class GuardMember(
        val memberNumber: Int,
    ) : CampSessionInput

    object Continue : CampSessionInput
}

sealed interface CampSessionStep {
    data class GuardChoice(
        val prompt: TextBlock,
    ) : CampSessionStep

    data class GuardMember(
        val prompt: TextBlock,
    ) : CampSessionStep

    data class Messages(
        val blocks: List<TextBlock>,
        val next: CampSessionStep,
    ) : CampSessionStep

    data class Ambush(
        val session: BattleSession,
    ) : CampSessionStep

    data class Finished(
        val result: CampSessionResult,
    ) : CampSessionStep
}

private data class RestApplication(
    val runtime: RuntimeSessionState,
    val messages: List<TextBlock>,
)

data class CampSession(
    val runtime: RuntimeSessionState,
    val content: RuntimeContent,
    val step: CampSessionStep,
    val guardMemberIndex: Int? = null,
    val lastResult: CampSessionResult? = null,
) {
    val party: PartyState get() = runtime.activeParty

    fun apply(input: CampSessionInput): CampSession =
        when (val current = step) {
            is CampSessionStep.GuardChoice -> {
                val answer =
                    (input as? CampSessionInput.GuardAnswer)
                        ?: error("camp session is asking the guard Y/N prompt")
                if (answer.yes) {
                    copy(step = CampSessionStep.GuardMember(content.realm.camp.guardMemberPrompt))
                } else {
                    advanceFromGuard(guardIndex = null)
                }
            }
            is CampSessionStep.GuardMember -> {
                val pick =
                    (input as? CampSessionInput.GuardMember)
                        ?: error("camp session is choosing a guard member")
                val idx = pick.memberNumber - 1
                val member = party.members.getOrNull(idx)
                when {
                    member == null -> this
                    member.dead ->
                        copy(
                            step =
                                CampSessionStep.Messages(
                                    blocks = listOf(content.realm.camp.guardMemberDead),
                                    next = CampSessionStep.GuardMember(content.realm.camp.guardMemberPrompt),
                                ),
                        )
                    else -> advanceFromGuard(guardIndex = idx)
                }
            }
            is CampSessionStep.Messages -> {
                require(input is CampSessionInput.Continue) {
                    "camp session is showing messages; expected Continue"
                }
                copy(step = current.next)
            }
            is CampSessionStep.Ambush, is CampSessionStep.Finished ->
                error("$current must be driven by its outer adapter, not apply()")
        }

    fun replaceAmbushSession(session: BattleSession): CampSession {
        require(step is CampSessionStep.Ambush) { "camp session is not in an ambush" }
        if (session.step !is BattleSessionStep.Finished) {
            return copy(step = CampSessionStep.Ambush(session))
        }
        val result = CampSessionResult(session.runtime, CampSessionStatus.Ambushed)
        return copy(
            runtime = session.runtime,
            step = CampSessionStep.Finished(result),
            lastResult = result,
        )
    }

    private fun advanceFromGuard(guardIndex: Int?): CampSession {
        val rules = content.realm.camp
        val livingCount = party.members.count { !it.dead }
        val foodCost = rules.foodCostPerMember * livingCount
        if (party.resources.food < foodCost) {
            val result = CampSessionResult(runtime, CampSessionStatus.NotEnoughFood)
            return copy(
                step =
                    CampSessionStep.Messages(
                        blocks = listOf(rules.notEnoughFood),
                        next = CampSessionStep.Finished(result),
                    ),
                lastResult = result,
                guardMemberIndex = guardIndex,
            )
        }

        val rest = applyRest(runtime, content, guardIndex)
        val (afterAmbush, ambushMessages, ambushStep) = applyAmbush(rest.runtime, content, guardIndex)
        val tail =
            ambushStep ?: CampSessionStep.Finished(
                CampSessionResult(afterAmbush, CampSessionStatus.Rested),
            )
        val messages =
            buildList {
                if (guardIndex != null) add(rules.standsGuard)
                addAll(rest.messages)
                addAll(ambushMessages)
                if (ambushStep == null) add(rules.rested)
            }
        return copy(
            runtime = afterAmbush,
            guardMemberIndex = guardIndex,
            step = if (messages.isEmpty()) tail else CampSessionStep.Messages(messages, tail),
            lastResult = (tail as? CampSessionStep.Finished)?.result ?: lastResult,
        )
    }

    companion object {
        fun start(
            runtime: RuntimeSessionState,
            content: RuntimeContent,
        ): CampSession {
            val rules = content.realm.camp
            val party = runtime.activeParty
            return when {
                party.members.none { !it.dead } -> {
                    val result = CampSessionResult(runtime, CampSessionStatus.Rested)
                    CampSession(runtime, content, CampSessionStep.Finished(result), lastResult = result)
                }
                party.resources.restedHours > 0 -> {
                    val result = CampSessionResult(runtime, CampSessionStatus.NotTired)
                    CampSession(
                        runtime = runtime,
                        content = content,
                        step =
                            CampSessionStep.Messages(
                                blocks = listOf(rules.notTired),
                                next = CampSessionStep.Finished(result),
                            ),
                        lastResult = result,
                    )
                }
                else ->
                    CampSession(
                        runtime = runtime,
                        content = content,
                        step = CampSessionStep.GuardChoice(rules.guardPrompt),
                    )
            }
        }
    }
}

/**
 * Apply the deterministic state changes of a successful rest: deduct food,
 * advance the hour, heal each living non-guard member, set rested-hours
 * floor, and roll a temperature band that may damage unprotected members.
 */
private fun applyRest(
    runtime: RuntimeSessionState,
    content: RuntimeContent,
    guardIndex: Int?,
): RestApplication {
    val rules = content.realm.camp
    val party = runtime.activeParty
    val livingCount = party.members.count { !it.dead }
    val foodCost = rules.foodCostPerMember * livingCount

    val healed =
        party.copy(
            resources =
                party.resources.copy(
                    food = (party.resources.food - foodCost).coerceAtLeast(0),
                    restedHours = rules.hoursRestedReset,
                ),
            members =
                party.members.mapIndexed { idx, member ->
                    when {
                        member.dead -> member
                        idx == guardIndex -> member
                        else ->
                            member.copy(
                                hitPoints =
                                    member.hitPoints.copy(
                                        current =
                                            (member.hitPoints.current + rules.healthGain)
                                                .coerceAtMost(member.hitPoints.max),
                                    ),
                            )
                    }
                },
        )
    val advancedHour =
        runtime.realm.copy(
            hour = (runtime.realm.hour + rules.hoursAdvanced).floorMod(24),
        )
    val afterHeal = runtime.withActiveParty(healed).withRealm(advancedHour)

    val (afterTemperature, tempMessages) = applyTemperature(afterHeal, content)
    return RestApplication(
        afterTemperature,
        buildList {
            add(rules.sleeping)
            addAll(tempMessages)
        },
    )
}

private fun applyTemperature(
    runtime: RuntimeSessionState,
    content: RuntimeContent,
): Pair<RuntimeSessionState, List<TextBlock>> {
    val rules = content.realm.camp
    if (rules.temperatureMax <= 0 || rules.temperatureBands.isEmpty()) {
        return runtime to emptyList()
    }

    val draw = runtime.drawRandom(1, rules.temperatureMax)
    val temperature = draw.value
    val effect = draw.runtime.activeParty.campGearEffect(content.itemRecords, ImageId(""))
    val activeBands = rules.temperatureBands.filter { temperature in it.range }
    if (activeBands.isEmpty()) return draw.runtime to emptyList()

    val damaged =
        activeBands
            .filter { !effect.protects(it.band) }
            .sumOf { rules.temperatureDamage[it.band] ?: 0 }
    val nextRuntime =
        if (damaged > 0) {
            draw.runtime.withActiveParty(draw.runtime.activeParty.applyTemperatureDamage(damaged))
        } else {
            draw.runtime
        }

    val messages =
        buildList {
            activeBands.forEach { band ->
                rules.temperatureMessage[band.band]?.let(::add)
                if (!effect.protects(band.band)) {
                    rules.noShelterMessage[band.band]?.let(::add)
                }
            }
        }
    return nextRuntime to messages
}

/**
 * Roll the post-rest ambush. Returns the (possibly updated) runtime, any
 * narration messages, and an Ambush step to drive nested combat — or null
 * if the rest finishes peacefully.
 */
private fun applyAmbush(
    runtime: RuntimeSessionState,
    content: RuntimeContent,
    guardIndex: Int?,
): Triple<RuntimeSessionState, List<TextBlock>, CampSessionStep?> {
    val rules = content.realm.camp
    val tier = rules.ambushTier
    if (rules.ambushChancePercent <= 0 || tier == null) {
        return Triple(runtime, emptyList(), null)
    }

    val chanceDraw = runtime.drawRandom(0, 99)
    if (chanceDraw.value >= rules.ambushChancePercent) {
        return Triple(chanceDraw.runtime, emptyList(), null)
    }

    val party = chanceDraw.runtime.activeParty
    val unguarded = guardIndex == null
    val multipleAlive = party.members.count { !it.dead } > 1

    val (afterKill, killMessages) =
        if (unguarded && multipleAlive) {
            applySurpriseKill(chanceDraw.runtime, content)
        } else {
            chanceDraw.runtime to emptyList()
        }

    val survivorsAlive = afterKill.activeParty.members.any { !it.dead }
    if (!survivorsAlive) {
        val finished = CampSessionStep.Finished(CampSessionResult(afterKill, CampSessionStatus.SurpriseKill))
        return Triple(
            afterKill,
            buildList {
                add(rules.ambush)
                addAll(killMessages)
            },
            finished,
        )
    }

    val battle =
        BattleSession.start(
            runtime = afterKill,
            tier = tier,
            content = content,
        )
    val messages =
        buildList {
            add(rules.ambush)
            addAll(killMessages)
        }
    return Triple(battle.runtime, messages, CampSessionStep.Ambush(battle))
}

private fun applySurpriseKill(
    runtime: RuntimeSessionState,
    content: RuntimeContent,
): Pair<RuntimeSessionState, List<TextBlock>> {
    val rules = content.realm.camp
    val aliveIndexes =
        runtime.activeParty.members
            .mapIndexedNotNull { idx, member -> idx.takeUnless { member.dead } }
    if (aliveIndexes.isEmpty()) return runtime to emptyList()

    val draw = runtime.drawRandom(0, aliveIndexes.lastIndex)
    val victimIdx = aliveIndexes[draw.value]
    val nextParty =
        draw.runtime.activeParty.copy(
            members =
                draw.runtime.activeParty.members.mapIndexed { idx, member ->
                    if (idx == victimIdx) {
                        member.copy(
                            hitPoints = member.hitPoints.copy(current = 0),
                            dead = true,
                        )
                    } else {
                        member
                    }
                },
        )
    return draw.runtime.withActiveParty(nextParty) to listOf(rules.surpriseKill)
}

private fun PartyState.applyTemperatureDamage(damage: Int): PartyState =
    copy(
        members =
            members.map { member ->
                if (member.dead) {
                    member
                } else {
                    member.copy(
                        hitPoints =
                            member.hitPoints.copy(
                                current = (member.hitPoints.current - damage).coerceAtLeast(1),
                            ),
                    )
                }
            },
    )
