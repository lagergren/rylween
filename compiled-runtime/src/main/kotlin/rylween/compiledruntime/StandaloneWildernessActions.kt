/**
 * Direct wilderness actions for the standalone compiled-bundle runtime.
 *
 * This file owns the first runtime-system-backed wilderness behavior that used
 * to live in the Kotlin session graph: turn upkeep, hunting, and camping. It
 * consumes `ExternalRuntimeSystems.realm` plus direct compiled party/item rows
 * and deliberately avoids composing the old session graph.
 */

package rylween.compiledruntime

import rylween.compiled.CompiledContentBundle
import rylween.compiled.CompiledPartyMember
import rylween.compiled.CompiledTerrain
import rylween.compiled.ExternalCampRules
import rylween.compiled.ExternalHuntRules
import rylween.compiled.ExternalRuntimeCombat
import rylween.compiled.ExternalRuntimeRealm
import rylween.compiled.ExternalRuntimeTextBlock
import rylween.compiled.ExternalWildernessFatigueRule
import rylween.compiled.ExternalWildernessRuntime
import rylween.dsl.GridPoint
import rylween.dsl.PcClass
import rylween.dsl.PronounCase
import rylween.dsl.RuntimeMode
import rylween.dsl.TemperatureBand
import rylween.dsl.WildernessFatigueMatch
import rylween.dsl.pronoun

/** Result of one standalone wilderness action. */
internal data class StandaloneWildernessActionResult(
    val runtime: StandaloneRuntimeState,
    val messages: List<String> = emptyList(),
)

private val manaRegenerationClasses = setOf(PcClass.Mage, PcClass.Priest)

/** Applies bundle-authored wilderness rules to standalone runtime state. */
internal class StandaloneWildernessActions(
    private val content: CompiledContentBundle,
    private val realm: ExternalRuntimeRealm,
    private val combat: ExternalRuntimeCombat,
    private val wilderness: ExternalWildernessRuntime,
    private val input: StandaloneInput,
    private val screen: CompiledAnsiScreen,
) {
    fun applyTurnUpkeep(runtime: StandaloneRuntimeState): StandaloneWildernessActionResult {
        var party = runtime.party
        val messages = mutableListOf<String>()
        party = party.regenerateMana(realm.upkeep.manaRegenChance)

        val foodTick = realm.upkeep.foodTickEvery > 0 && party.resources.timeLeft % realm.upkeep.foodTickEvery == 0
        val foodAfterTick = if (foodTick) (party.resources.food - party.foodTickCost()).coerceAtLeast(0) else party.resources.food
        party = party.applyStarvation(foodAfterTick, realm.upkeep.starveFoodThreshold, realm.upkeep.starveDamage, messages)

        val restedHours = party.resources.restedHours - realm.upkeep.restedHoursCostPerTurn
        party = party.applyFatigue(restedHours, realm.upkeep.fatigue, messages)
        party =
            party.copy(
                resources =
                    party.resources.copy(
                        food = foodAfterTick,
                        restedHours = restedHours,
                    ),
            )

        val allDead = party.members.isNotEmpty() && party.members.all(CompiledPartyMember::dead)
        val mode = if (allDead) RuntimeMode.GameOver else runtime.currentMode
        return StandaloneWildernessActionResult(
            runtime =
                runtime.copy(
                    hour = (runtime.hour + 1).positiveMod(24),
                    currentMode = mode,
                    party = party,
                ),
            messages = messages,
        )
    }

    fun hunt(runtime: StandaloneRuntimeState): StandaloneWildernessActionResult {
        val rules =
            realm.hunt
                ?: return StandaloneWildernessActionResult(runtime, listOf("Hunt is not available in this bundle."))
        val whoPrompt = rules.text.whoPrompt.renderHunt(runtime)
        val memberNumber = input.readIntFromPrompt(whoPrompt) ?: return StandaloneWildernessActionResult(runtime)
        val member = runtime.party.members.getOrNull(memberNumber - 1)
        return when {
            member == null || member.dead ->
                StandaloneWildernessActionResult(runtime, rules.text.dead.renderHunt(runtime, memberNumber = memberNumber))
            member.pcClass != PcClass.Ranger ->
                StandaloneWildernessActionResult(
                    runtime,
                    rules.text.notRanger.renderHunt(runtime, memberNumber = memberNumber, member = member),
                )
            runtime.party.resources.timeLeft < rules.timeCost ->
                StandaloneWildernessActionResult(
                    runtime,
                    rules.text.tooFewMoves.renderHunt(runtime, memberNumber = memberNumber),
                )
            else -> runtime.resolveHunt(rules, memberNumber, member)
        }
    }

    fun camp(runtime: StandaloneRuntimeState): StandaloneWildernessActionResult {
        val rules = realm.camp
        val livingCount = runtime.party.members.count { member -> !member.dead }
        if (livingCount == 0) return StandaloneWildernessActionResult(runtime)
        if (runtime.party.resources.restedHours > 0) {
            return StandaloneWildernessActionResult(runtime, rules.text.notTired.renderCamp(runtime))
        }

        val guardAnswer =
            input.readCommandFromPrompt(rules.text.guardPrompt.renderCamp(runtime))
                ?: return StandaloneWildernessActionResult(runtime)
        val guardIndex =
            guardAnswer.takeIf { it == 'Y' }?.let {
                val memberNumber =
                    input.readIntFromPrompt(rules.text.guardMemberPrompt.renderCamp(runtime))
                        ?: return StandaloneWildernessActionResult(runtime)
                val member = runtime.party.members.getOrNull(memberNumber - 1)
                if (member == null || member.dead) {
                    return StandaloneWildernessActionResult(runtime, rules.text.guardMemberDead.renderCamp(runtime))
                }
                memberNumber - 1
            }

        val foodCost = rules.foodCostPerMember * livingCount
        if (runtime.party.resources.food < foodCost) {
            return StandaloneWildernessActionResult(runtime, rules.text.notEnoughFood.renderCamp(runtime))
        }
        return runtime.resolveCamp(rules, guardIndex, foodCost)
    }

    fun applyMoveEvents(
        runtime: StandaloneRuntimeState,
        move: StandaloneMoveEvent,
    ): StandaloneWildernessActionResult {
        if (runtime.party.rngState == null) return StandaloneWildernessActionResult(runtime)
        var current = runtime
        val messages = mutableListOf<String>()
        current = current.applyTerrainTransition(move, messages)

        var party = current.party
        val eventDraw = party.drawRandom(0, 99)
        party = eventDraw.party
        current = current.withParty(party)
        val events = wilderness.events
        if (eventDraw.value < events.caveChancePercent) {
            val caveLines = events.caveFoundPrompt.renderStandalone()
            return StandaloneWildernessActionResult(current, messages + caveLines)
        }

        val quietLimit = events.caveChancePercent + events.quietChancePercent
        if (eventDraw.value < quietLimit) {
            return StandaloneWildernessActionResult(current, messages)
        }

        val splitDraw = current.party.drawRandom(0, 99)
        current = current.withParty(splitDraw.party)
        val tier = if (splitDraw.value < events.encounterSplitPercent) events.easyEncounterTier else events.hardEncounterTier
        val battle = battleRunner().runTier(current, tier)
        return StandaloneWildernessActionResult(battle.runtime, messages + battle.messages)
    }

    private fun StandaloneInput.readCommandFromPrompt(lines: List<String>): Char? {
        screen.writeText(lines)
        return readCommand()
    }

    private fun StandaloneInput.readIntFromPrompt(lines: List<String>): Int? {
        screen.writeText(lines)
        return readInt()
    }

    private fun StandaloneRuntimeState.resolveHunt(
        rules: ExternalHuntRules,
        memberNumber: Int,
        member: CompiledPartyMember,
    ): StandaloneWildernessActionResult {
        var party =
            party.copy(
                resources = party.resources.copy(timeLeft = party.resources.timeLeft - rules.timeCost),
            )
        val chance = party.drawRandom(0, 99)
        party = chance.party
        if (chance.value >= rules.successChancePercent) {
            return StandaloneWildernessActionResult(
                withParty(party),
                rules.text.tries.renderHunt(this, memberNumber, member) +
                    rules.text.fails.renderHunt(this, memberNumber, member),
            )
        }
        val food = party.drawRandom(rules.foodMin, rules.foodMax)
        val gained = food.value + party.leaderLevel()
        party =
            food.party.copy(
                resources = food.party.resources.copy(food = food.party.resources.food + gained),
            )
        return StandaloneWildernessActionResult(
            withParty(party),
            rules.text.tries.renderHunt(this, memberNumber, member, gained) +
                rules.text.succeeds.renderHunt(this, memberNumber, member, gained),
        )
    }

    private fun StandaloneRuntimeState.resolveCamp(
        rules: ExternalCampRules,
        guardIndex: Int?,
        foodCost: Int,
    ): StandaloneWildernessActionResult {
        var party =
            party.copy(
                resources =
                    party.resources.copy(
                        food = (party.resources.food - foodCost).coerceAtLeast(0),
                        restedHours = rules.hoursRestedReset,
                    ),
                members =
                    party.members.mapIndexed { index, member ->
                        when {
                            member.dead || index == guardIndex -> member
                            else ->
                                member.copy(
                                    hitPoints =
                                        member.hitPoints.copy(
                                            current = (member.hitPoints.current + rules.healthGain).coerceAtMost(member.hitPoints.max),
                                        ),
                                )
                        }
                    },
            )
        var runtime = copy(hour = (hour + rules.hoursAdvanced).positiveMod(24), party = party)
        val messages =
            buildList {
                if (guardIndex != null) addAll(rules.text.standsGuard.renderCamp(runtime))
                addAll(rules.text.sleeping.renderCamp(runtime))
                val temperature = runtime.applyTemperature(rules)
                runtime = temperature.runtime
                addAll(temperature.messages)
                val ambush = runtime.applyCampAmbush(rules, guardIndex)
                runtime = ambush.runtime
                addAll(ambush.messages)
                if (!ambush.triggered) addAll(rules.text.rested.renderCamp(runtime))
            }
        return StandaloneWildernessActionResult(runtime, messages)
    }

    private fun StandaloneRuntimeState.applyTemperature(rules: ExternalCampRules): CampMutation {
        if (rules.temperatureMax <= 0 || rules.temperatureBands.isEmpty()) return CampMutation(this)
        val draw = party.drawRandom(1, rules.temperatureMax)
        val temperature = draw.value
        val activeBands = rules.temperatureBands.filter { band -> temperature in band.first..band.last }
        if (activeBands.isEmpty()) return CampMutation(withParty(draw.party))

        val protectedBands = draw.party.campProtection()
        val damaged =
            activeBands
                .map(rylween.compiled.ExternalTemperatureBandRange::band)
                .filterNot(protectedBands::contains)
                .sumOf { band -> rules.temperatureDamage[band] ?: 0 }
        val damagedParty =
            draw.party.takeUnless { damaged > 0 }
                ?: draw.party.copy(
                    members =
                        draw.party.members.map { member ->
                            member.takeIf(CompiledPartyMember::dead)
                                ?: member.copy(
                                    hitPoints =
                                        member.hitPoints.copy(
                                            current = (member.hitPoints.current - damaged).coerceAtLeast(1),
                                        ),
                                )
                        },
                )
        val messages =
            buildList {
                activeBands.forEach { band ->
                    rules.text.temperatureMessage[band.band]?.let { addAll(it.renderCamp(this@applyTemperature)) }
                    if (band.band !in protectedBands) {
                        rules.text.noShelterMessage[band.band]?.let { addAll(it.renderCamp(this@applyTemperature)) }
                    }
                }
            }
        return CampMutation(withParty(damagedParty), messages)
    }

    private fun StandaloneRuntimeState.applyCampAmbush(
        rules: ExternalCampRules,
        guardIndex: Int?,
    ): CampMutation {
        val ambushTier = rules.ambushTier
        if (rules.ambushChancePercent <= 0 || ambushTier == null) return CampMutation(this)
        val chance = party.drawRandom(0, 99)
        if (chance.value >= rules.ambushChancePercent) return CampMutation(withParty(chance.party))

        var party = chance.party
        val messages = mutableListOf<String>()
        messages += rules.text.ambush.renderCamp(this)
        val surprise = guardIndex == null && party.members.count { member -> !member.dead } > 1
        if (surprise) {
            val aliveIndexes = party.members.mapIndexedNotNull { index, member -> index.takeUnless { member.dead } }
            val victim = party.drawRandom(0, aliveIndexes.lastIndex)
            val victimIndex = aliveIndexes[victim.value]
            party =
                victim.party.copy(
                    members =
                        victim.party.members.mapIndexed { index, member ->
                            member.takeUnless { index == victimIndex }
                                ?: member.copy(hitPoints = member.hitPoints.copy(current = 0), dead = true)
                        },
                )
            messages += rules.text.surpriseKill.renderCamp(withParty(party))
        }
        val battle = battleRunner().runTier(withParty(party), ambushTier)
        return CampMutation(battle.runtime, messages + battle.messages, triggered = true)
    }

    private fun StandaloneRuntimeState.applyTerrainTransition(
        move: StandaloneMoveEvent,
        messages: MutableList<String>,
    ): StandaloneRuntimeState {
        val from = move.fromTerrain?.let { terrain -> content.slug(terrain.id) }
        val to = move.toTerrain?.let { terrain -> content.slug(terrain.id) }
        if (from == null || to == null || from == to) return this
        val transition = wilderness.terrainTransitions.entries.firstOrNull { entry -> entry.terrain == to } ?: return this
        val draw = party.drawRandom(0, 99)
        if (draw.value < wilderness.terrainTransitions.chancePercent) {
            messages += transition.text.renderStandalone()
        }
        return withParty(draw.party)
    }

    private fun StandaloneParty.regenerateMana(chancePercent: Int): StandaloneParty {
        var party = this
        val members =
            members.map { member ->
                if (member.pcClass !in manaRegenerationClasses || member.mana.current >= member.mana.max) return@map member
                val draw = party.drawRandom(0, 99)
                party = draw.party
                member.takeUnless { draw.value < chancePercent }
                    ?: member.copy(
                        mana = member.mana.copy(current = (member.mana.current + 1).coerceAtMost(member.mana.max)),
                    )
            }
        return party.copy(members = members)
    }

    private fun StandaloneParty.foodTickCost(): Int {
        val leaderLevelCost = if (realm.upkeep.foodCostLeaderDiv > 0) leaderLevel() / realm.upkeep.foodCostLeaderDiv else 0
        return (counters.memberCount ?: members.size) + leaderLevelCost
    }

    private fun StandaloneParty.applyStarvation(
        foodAfterTick: Int,
        threshold: Int,
        damage: Int,
        messages: MutableList<String>,
    ): StandaloneParty {
        if (foodAfterTick > threshold || damage == 0) return this
        return copy(
            members =
                members.map { member ->
                    if (member.dead) return@map member
                    val nextHp = member.hitPoints.current - damage
                    if (nextHp >= 1) return@map member.copy(hitPoints = member.hitPoints.copy(current = nextHp))
                    messages += "${content.strings.value(member.nameString)} starved"
                    messages += "to death!!!"
                    member.copy(
                        hitPoints = member.hitPoints.copy(current = 0),
                        dead = true,
                    )
                },
        )
    }

    private fun StandaloneParty.applyFatigue(
        restedHours: Int,
        rules: List<ExternalWildernessFatigueRule>,
        messages: MutableList<String>,
    ): StandaloneParty {
        val rule = rules.firstOrNull { rule -> rule.matches(restedHours) } ?: return this
        messages += rule.text.renderStandalone()
        if (!rule.drain) return this
        return copy(
            members =
                members.map { member ->
                    member.takeUnless { it.hitPoints.current > 1 }
                        ?: member.copy(hitPoints = member.hitPoints.copy(current = member.hitPoints.current - 1))
                },
        )
    }

    private fun ExternalWildernessFatigueRule.matches(restedHours: Int): Boolean =
        when (match) {
            WildernessFatigueMatch.Equal -> restedHours == threshold
            WildernessFatigueMatch.LessThan -> restedHours < threshold
        }

    private fun StandaloneParty.campProtection(): Set<TemperatureBand> {
        val itemsById = content.items.associateBy { item -> item.id }
        return inventory
            .asSequence()
            .mapNotNull { slot -> slot.item?.let(itemsById::get) }
            .flatMap { item -> item.campProtection.asSequence() }
            .toSet()
    }

    private fun ExternalRuntimeTextBlock.renderStandalone(): List<String> =
        render { kind ->
            when (kind) {
                "partyMemberMax" -> "?"
                else -> null
            }
        }

    private fun battleRunner(): StandaloneCompiledBattleRunner = StandaloneCompiledBattleRunner(content, combat, input, screen)

    private fun ExternalRuntimeTextBlock.renderCamp(runtime: StandaloneRuntimeState): List<String> =
        render { kind ->
            when (kind) {
                "partyMemberMax" -> runtime.partyMemberMax.toString()
                else -> null
            }
        }.map { line -> line.replace("{party_max}", runtime.partyMemberMax.toString()) }

    private fun ExternalRuntimeTextBlock.renderHunt(
        runtime: StandaloneRuntimeState,
        memberNumber: Int? = null,
        member: CompiledPartyMember? = memberNumber?.let { runtime.party.members.getOrNull(it - 1) },
        foodGained: Int? = null,
    ): List<String> =
        render { kind ->
            when (kind) {
                "partyMemberMax" -> runtime.partyMemberMax.toString()
                "huntMemberName" -> member?.let { content.strings.value(it.nameString) }
                "huntMemberSubjectPronoun" -> member?.gender?.pronoun(PronounCase.Subject)
                "huntFoodGained" -> foodGained?.toString()
                else -> null
            }
        }

    private fun StandaloneParty.leaderLevel(): Int = members.firstOrNull()?.level ?: 0
}

private data class CampMutation(
    val runtime: StandaloneRuntimeState,
    val messages: List<String> = emptyList(),
    val triggered: Boolean = false,
)

/** Movement context used for direct standalone wilderness event polling. */
internal data class StandaloneMoveEvent(
    val from: GridPoint,
    val to: GridPoint,
    val fromTerrain: CompiledTerrain?,
    val toTerrain: CompiledTerrain?,
)

private fun Int.positiveMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus
