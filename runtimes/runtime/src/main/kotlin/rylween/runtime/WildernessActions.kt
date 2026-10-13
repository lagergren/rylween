/**
 * Direct wilderness actions for the runtime.
 *
 * This file owns the first runtime-system-backed wilderness behavior that used
 * to live in the Kotlin session graph: turn upkeep, hunting, and camping. It
 * consumes `ExternalRuntimeSystems.realm` plus direct compiled party/item rows
 * and deliberately avoids composing the old session graph.
 */

package rylween.runtime

import rylween.compiled.CompiledBoolExpr
import rylween.compiled.CompiledContentBundle
import rylween.compiled.CompiledHandleKind
import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledInstruction
import rylween.compiled.CompiledIntExpr
import rylween.compiled.CompiledMission
import rylween.compiled.CompiledOpcode
import rylween.compiled.CompiledOperand
import rylween.compiled.CompiledPartyMember
import rylween.compiled.CompiledTerrain
import rylween.compiled.CompiledTrigger
import rylween.compiled.ExternalCampRules
import rylween.compiled.ExternalHuntRules
import rylween.compiled.ExternalRuntimeCombat
import rylween.compiled.ExternalRuntimeCommand
import rylween.compiled.ExternalRuntimeRealm
import rylween.compiled.ExternalRuntimeSystems
import rylween.compiled.ExternalRuntimeTextBlock
import rylween.compiled.ExternalWildernessFatigueRule
import rylween.compiled.ExternalWildernessRuntime
import rylween.compiled.RuntimeCommandActionId
import rylween.compiled.RuntimeCommandGroupId
import rylween.compiled.applyCompiledBinary
import rylween.compiled.compareCompiled
import rylween.dsl.AudioCue
import rylween.dsl.PartyMemberSelection
import rylween.dsl.PronounCase
import rylween.dsl.impl.GridPoint
import rylween.dsl.impl.PcClass
import rylween.dsl.impl.Resource
import rylween.dsl.impl.RuntimeMode
import rylween.dsl.impl.TemperatureBand
import rylween.dsl.impl.WildernessFatigueMatch
import rylween.dsl.pronoun

/** Result of one runtime wilderness action. */
internal data class WildernessActionResult(
    val runtime: RuntimeState,
    val messages: List<String> = emptyList(),
)

private val manaRegenerationClasses = setOf(PcClass.Mage, PcClass.Priest)

/** Applies bundle-authored wilderness rules to runtime state. */
internal class WildernessActions(
    private val content: CompiledContentBundle,
    private val systems: ExternalRuntimeSystems,
    private val realm: ExternalRuntimeRealm,
    private val combat: ExternalRuntimeCombat,
    private val wilderness: ExternalWildernessRuntime,
    private val commands: Map<String, List<ExternalRuntimeCommand>>,
    private val input: RuntimeInputPort,
    private val screen: RuntimeScreenPort,
    private val log: RuntimeLog,
) {
    private val audio = RuntimeAudio(systems.audio, screen)

    fun applyTurnUpkeep(
        runtime: RuntimeState,
        leadingMessages: List<String> = emptyList(),
    ): WildernessActionResult {
        var party = runtime.party
        val upkeepMessages = mutableListOf<String>()
        party = party.regenerateMana(realm.upkeep.manaRegenChance)

        val foodTick = realm.upkeep.foodTickEvery > 0 && party.resources.timeLeft % realm.upkeep.foodTickEvery == 0
        val foodAfterTick = if (foodTick) (party.resources.food - party.foodTickCost()).coerceAtLeast(0) else party.resources.food
        party =
            party.applyStarvation(
                foodAfterTick,
                realm.upkeep.starveFoodThreshold,
                realm.upkeep.starveDamage,
                realm.upkeep.starvationDeath,
                upkeepMessages,
            )
        if (foodTick && foodAfterTick <= realm.upkeep.starveFoodThreshold && realm.upkeep.starveDamage > 0) audio.cue(AudioCue.Hunger)

        val restedHours = party.resources.restedHours - realm.upkeep.restedHoursCostPerTurn
        party = party.applyFatigue(restedHours, realm.upkeep.fatigue, upkeepMessages)
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
        return WildernessActionResult(
            runtime =
                runtime.copy(
                    hour = (runtime.hour + 1).positiveMod(24),
                    currentMode = mode,
                    party = party,
                ),
            messages = leadingMessages + upkeepMessages,
        )
    }

    fun hunt(runtime: RuntimeState): WildernessActionResult {
        val rules = checkNotNull(realm.hunt) { "wilderness hunt command requires authored hunt rules" }
        val whoPrompt = rules.text.whoPrompt.renderHunt(runtime)
        val memberNumber = input.readIntFromPrompt(whoPrompt) ?: return WildernessActionResult(runtime)
        val member = runtime.party.members.getOrNull(memberNumber - 1)
        return when {
            member == null || member.dead ->
                WildernessActionResult(runtime, rules.text.dead.renderHunt(runtime, memberNumber = memberNumber))

            member.pcClass != PcClass.Ranger ->
                WildernessActionResult(
                    runtime,
                    rules.text.notRanger.renderHunt(runtime, memberNumber = memberNumber, member = member),
                )

            runtime.party.resources.timeLeft < rules.timeCost ->
                WildernessActionResult(
                    runtime,
                    rules.text.tooFewMoves.renderHunt(runtime, memberNumber = memberNumber),
                )

            else -> runtime.resolveHunt(rules, memberNumber, member)
        }
    }

    fun camp(runtime: RuntimeState): WildernessActionResult {
        val rules = realm.camp
        val livingCount = runtime.party.members.count { member -> !member.dead }
        if (livingCount == 0) return WildernessActionResult(runtime)
        if (runtime.party.resources.restedHours > 0) {
            return WildernessActionResult(runtime, rules.text.notTired.renderCamp(runtime))
        }

        val guardAnswer =
            input.readCommandFromPrompt(rules.text.guardPrompt.renderCamp(runtime))
                ?: return WildernessActionResult(runtime)
        val guardIndex =
            guardAnswer.takeIf { it == 'Y' }?.let {
                val memberNumber =
                    input.readIntFromPrompt(rules.text.guardMemberPrompt.renderCamp(runtime))
                        ?: return WildernessActionResult(runtime)
                val member = runtime.party.members.getOrNull(memberNumber - 1)
                if (member == null || member.dead) {
                    return WildernessActionResult(runtime, rules.text.guardMemberDead.renderCamp(runtime))
                }
                memberNumber - 1
            }

        val foodCost = rules.foodCostPerMember * livingCount
        if (runtime.party.resources.food < foodCost) {
            return WildernessActionResult(runtime, rules.text.notEnoughFood.renderCamp(runtime))
        }
        return runtime.resolveCamp(rules, guardIndex, foodCost)
    }

    fun forceCaveDiscovery(runtime: RuntimeState): WildernessActionResult {
        log.event("debug.secret.force_cave position=${runtime.party.position.x},${runtime.party.position.y}")
        return runtime.applyCaveDiscovery(emptyList(), source = "debug_secret")
    }

    fun forceEncounter(runtime: RuntimeState): WildernessActionResult {
        log.event("debug.secret.force_encounter position=${runtime.party.position.x},${runtime.party.position.y}")
        return runtime.applyEncounter(emptyList(), source = "debug_secret")
    }

    fun applyMoveEvents(
        runtime: RuntimeState,
        move: MoveEvent,
    ): WildernessActionResult {
        var current = runtime
        val messages = mutableListOf<String>()
        current = current.applyTerrainTransition(move, messages)

        val mission = current.pollMission(move.toTerrain)
        if (mission.mission != null) {
            if (messages.isNotEmpty()) screen.writeText(messages)
            return WildernessActionResult(mission.runtime.runMission(mission.mission))
        }

        if (current.party.rngState == null) {
            log.event("rng.skip context=wilderness.event_gate reason=no_seed")
            return WildernessActionResult(current, messages)
        }

        var party = current.party
        val eventDraw = party.drawRandom(0, 99)
        party = eventDraw.party
        current = current.withParty(party)
        val events = wilderness.events
        val quietLimit = events.caveChancePercent + events.quietChancePercent
        val eventOutcome =
            when {
                eventDraw.value < events.caveChancePercent -> "cave"
                eventDraw.value < quietLimit -> "quiet"
                else -> "encounter"
            }
        log.rngDraw(
            context = "wilderness.event_gate",
            lo = 0,
            hi = 99,
            value = eventDraw.value,
            hit = eventOutcome != "quiet",
            details =
                mapOf(
                    "cave_hit" to (eventDraw.value < events.caveChancePercent),
                    "encounter_hit" to (eventDraw.value >= quietLimit),
                    "cave_lt" to events.caveChancePercent,
                    "quiet_lt" to quietLimit,
                    "outcome" to eventOutcome,
                ),
        )
        if (eventDraw.value < events.caveChancePercent) return current.applyCaveDiscovery(messages, source = "event_gate")

        if (eventDraw.value < quietLimit) {
            return WildernessActionResult(current, messages)
        }

        return current.applyEncounter(messages, source = "event_gate")
    }

    private fun RuntimeState.pollMission(currentTerrain: CompiledTerrain?): MissionTriggerResult {
        var next = this
        content.missions.forEach { mission ->
            if (mission.id in next.completedMissions || mission.requires?.let { requirement -> next.party.matches(requirement) } == false) {
                return@forEach
            }
            when (val trigger = mission.trigger) {
                is CompiledTrigger.AtLocation -> {
                    val point = locations.firstOrNull { location -> location.location == trigger.location }?.point
                    if (point != null && party.position == point) return MissionTriggerResult(next, mission)
                }

                is CompiledTrigger.AtPosition ->
                    if (party.position == trigger.point) return MissionTriggerResult(next, mission)

                is CompiledTrigger.OnTerrain ->
                    if (currentTerrain?.id == trigger.terrain) return MissionTriggerResult(next, mission)

                is CompiledTrigger.RandomStep -> {
                    val draw = next.party.rngState?.let { next.party.drawRandom(0, 99) } ?: return@forEach
                    next = next.withParty(draw.party)
                    log.rngDraw(
                        context = "wilderness.mission_random_step",
                        lo = 0,
                        hi = 99,
                        value = draw.value,
                        hit = draw.value < trigger.percent,
                        details =
                            mapOf(
                                "mission" to content.slug(mission.id),
                                "threshold_lt" to trigger.percent,
                            ),
                    )
                    if (draw.value < trigger.percent) return MissionTriggerResult(next, mission)
                }
            }
        }
        return MissionTriggerResult(next)
    }

    private fun RuntimeState.runMission(mission: CompiledMission): RuntimeState {
        val program =
            content.scripts.roots.firstOrNull { root ->
                root.root.kind.name == "Scene" && root.root.slug == content.slug(mission.action)
            }
                ?: error("missing compiled mission scene ${content.slug(mission.action)}")
        val ran =
            ScriptExecutor(content, systems, input, screen, this, log = log)
                .execute(program)
        // A mission that opens a dungeon stays open, as in the original game: leaving at the entrance, or climbing
        // out before the last level, must not lock its quest. Its requirement closes it once the dungeon's final
        // event moves the story on.
        if (program.instructions.entersDungeon()) return ran
        return ran.withCompletedMission(mission.id)
    }

    /** Whether these instructions, or a block nested in them such as a menu option, enter a dungeon. */
    private fun List<CompiledInstruction>.entersDungeon(): Boolean =
        any { instruction ->
            instruction.opcode == CompiledOpcode.EnterDungeon ||
                instruction.operands.any { operand ->
                    when (operand) {
                        is CompiledOperand.Instructions -> operand.instructions.entersDungeon()
                        is CompiledOperand.SwitchCases -> operand.cases.any { case -> case.body.entersDungeon() }
                        is CompiledOperand.MenuOptions -> operand.options.any { option -> option.body.entersDungeon() }
                        is CompiledOperand.TextAnswer -> operand.answer.matchOps.entersDungeon() || operand.answer.missOps.entersDungeon()
                        else -> false
                    }
                }
        }

    private fun RuntimeInputPort.readCommandFromPrompt(lines: List<String>): Char? {
        screen.writeText(lines)
        return readCommand()
    }

    private fun RuntimeInputPort.readIntFromPrompt(lines: List<String>): Int? {
        screen.writeText(lines)
        return readCommand()?.digitToIntOrNull()
    }

    private fun RuntimeState.applyCaveDiscovery(
        messages: List<String>,
        source: String,
    ): WildernessActionResult {
        log.event("wilderness.cave_discovery source=$source position=${party.position.x},${party.position.y}")
        if (messages.isNotEmpty()) screen.writeText(messages)
        audio.cue(AudioCue.CaveFound)
        wilderness.events.caveImage?.let(screen::showImage)
        val caveCommands = commands[RuntimeCommandGroupId.WildernessCave.id].orEmpty()
        val lines = wilderness.events.caveFoundPrompt.renderText()
        screen.writeText(lines)
        val command =
            input.readValidChoice(screen, lines, audio) { choice ->
                caveCommands.matches(RuntimeCommandActionId.Enter, choice) || caveCommands.matches(RuntimeCommandActionId.Leave, choice)
            } ?: return WildernessActionResult(this)
        if (!caveCommands.matches(RuntimeCommandActionId.Enter, command)) return WildernessActionResult(this)
        if (party.resources.timeLeft <= wilderness.events.caveEntryCost) {
            screen.writeText(wilderness.events.caveNoMoves.renderText())
            return WildernessActionResult(this)
        }
        audio.cue(AudioCue.EnterDungeon)
        val depth = party.drawRandom(wilderness.events.caveDepthRange.first, wilderness.events.caveDepthRange.last)
        log.rngDraw(
            context = "wilderness.cave_depth",
            lo = wilderness.events.caveDepthRange.first,
            hi = wilderness.events.caveDepthRange.last,
            value = depth.value,
            details =
                mapOf(
                    "dungeon" to content.slug(content.ref(CompiledHandleKind.Dungeon, wilderness.events.caveDungeon)),
                    "source" to source,
                ),
        )
        val next =
            withParty(
                depth.party.copy(
                    resources =
                        depth.party.resources.copy(
                            timeLeft = (depth.party.resources.timeLeft - wilderness.events.caveEntryCost).coerceAtLeast(0),
                        ),
                ),
            ).enterDungeon(
                content = content,
                dungeon = content.ref(CompiledHandleKind.Dungeon, wilderness.events.caveDungeon),
                levels = depth.value,
                log = log,
            )
        return WildernessActionResult(next)
    }

    private fun RuntimeState.applyEncounter(
        messages: List<String>,
        source: String,
    ): WildernessActionResult {
        var current = this
        val events = wilderness.events
        val splitDraw = current.party.drawRandom(0, 99)
        current = current.withParty(splitDraw.party)
        val tier = if (splitDraw.value < events.encounterSplitPercent) events.easyEncounterTier else events.hardEncounterTier
        log.rngDraw(
            context = "wilderness.encounter_split",
            lo = 0,
            hi = 99,
            value = splitDraw.value,
            hit = splitDraw.value < events.encounterSplitPercent,
            details =
                mapOf(
                    "easy_lt" to events.encounterSplitPercent,
                    "tier" to tier,
                    "source" to source,
                ),
        )
        val battle = battleRunner().runTier(current, tier)
        return WildernessActionResult(battle.runtime, messages + battle.messages)
    }

    private fun RuntimeState.resolveHunt(
        rules: ExternalHuntRules,
        memberNumber: Int,
        member: CompiledPartyMember,
    ): WildernessActionResult {
        var party =
            party.copy(
                resources = party.resources.copy(timeLeft = party.resources.timeLeft - rules.timeCost),
            )
        val chance = party.drawRandom(0, 99)
        party = chance.party
        log.rngDraw(
            context = "wilderness.hunt_success",
            lo = 0,
            hi = 99,
            value = chance.value,
            hit = chance.value < rules.successChancePercent,
            details =
                mapOf(
                    "member" to memberNumber,
                    "threshold_lt" to rules.successChancePercent,
                ),
        )
        if (chance.value >= rules.successChancePercent) {
            audio.cue(AudioCue.HuntFail)
            return WildernessActionResult(
                withParty(party),
                rules.text.tries.renderHunt(this, memberNumber, member) +
                    rules.text.fails.renderHunt(this, memberNumber, member),
            )
        }
        val food = party.drawRandom(rules.foodMin, rules.foodMax)
        log.rngDraw(
            context = "wilderness.hunt_food",
            lo = rules.foodMin,
            hi = rules.foodMax,
            value = food.value,
            details = mapOf("member" to memberNumber),
        )
        audio.cue(AudioCue.HuntSuccess)
        // The party keeps only what it can carry, and the text says what it kept.
        val room = (food.party.resourceCapacity(content, realm, Resource.Food) - food.party.resources.food).coerceAtLeast(0)
        val gained = (food.value + party.leaderLevel()).coerceAtMost(room)
        party =
            food.party.copy(
                resources = food.party.resources.copy(food = food.party.resources.food + gained),
            )
        return WildernessActionResult(
            withParty(party),
            rules.text.tries.renderHunt(this, memberNumber, member, gained) +
                rules.text.succeeds.renderHunt(this, memberNumber, member, gained),
        )
    }

    private fun RuntimeState.resolveCamp(
        rules: ExternalCampRules,
        guardIndex: Int?,
        foodCost: Int,
    ): WildernessActionResult {
        val party =
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
        audio.cue(AudioCue.Camp)
        screen.showImage(runtime.campBackdrop())
        if (guardIndex != null) showCampBeat(rules.text.standsGuard.renderCamp(runtime))
        audio.cue(AudioCue.Rest)
        showCampBeat(rules.text.sleeping.renderCamp(runtime))

        val temperature = runtime.applyTemperature(rules)
        runtime = temperature.runtime
        showCampBeat(temperature.messages)

        val ambush = runtime.applyCampAmbush(rules, guardIndex)
        runtime = ambush.runtime
        if (!ambush.triggered) showCampBeat(rules.text.rested.renderCamp(runtime))
        return WildernessActionResult(runtime)
    }

    private fun showCampBeat(lines: List<String>) {
        if (lines.isEmpty()) return
        screen.writeText(lines)
        input.pause(systems.pacing.wilderness.campMessagePauseMs)
    }

    private fun RuntimeState.campBackdrop(): String {
        val itemsById = content.items.associateBy { item -> item.id }
        return party.inventory.firstNotNullOfOrNull { slot ->
            slot.item
                ?.let(itemsById::get)
                ?.campImage
                ?.let(content::slug)
        }
            ?: "wilderness_no_tent"
    }

    private fun RuntimeState.applyTemperature(rules: ExternalCampRules): CampMutation {
        if (rules.temperatureMax <= 0 || rules.temperatureBands.isEmpty()) return CampMutation(this)
        val draw = party.drawRandom(1, rules.temperatureMax)
        val temperature = draw.value
        val activeBands = rules.temperatureBands.filter { band -> temperature in band.first..band.last }
        log.rngDraw(
            context = "wilderness.camp_temperature",
            lo = 1,
            hi = rules.temperatureMax,
            value = temperature,
            hit = activeBands.isNotEmpty(),
            details = mapOf("bands" to activeBands.joinToString(separator = "+") { band -> band.band.name }),
        )
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
        if (damaged > 0) audio.cue(AudioCue.ColdNight)
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

    private fun RuntimeState.applyCampAmbush(
        rules: ExternalCampRules,
        guardIndex: Int?,
    ): CampMutation {
        val ambushTier = rules.ambushTier
        if (rules.ambushChancePercent <= 0 || ambushTier == null) return CampMutation(this)
        val chance = party.drawRandom(0, 99)
        log.rngDraw(
            context = "wilderness.camp_ambush",
            lo = 0,
            hi = 99,
            value = chance.value,
            hit = chance.value < rules.ambushChancePercent,
            details = mapOf("threshold_lt" to rules.ambushChancePercent),
        )
        if (chance.value >= rules.ambushChancePercent) return CampMutation(withParty(chance.party))

        var party = chance.party
        audio.cue(AudioCue.Ambush)
        showCampBeat(rules.text.ambush.renderCamp(this))
        val surprise = guardIndex == null && party.members.count { member -> !member.dead } > 1
        if (surprise) {
            val aliveIndexes = party.members.mapIndexedNotNull { index, member -> index.takeUnless { member.dead } }
            val victim = party.drawRandom(0, aliveIndexes.lastIndex)
            val victimIndex = aliveIndexes[victim.value]
            log.rngDraw(
                context = "wilderness.camp_surprise_victim",
                lo = 0,
                hi = aliveIndexes.lastIndex,
                value = victim.value,
                details = mapOf("member" to (victimIndex + 1)),
            )
            party =
                victim.party.copy(
                    members =
                        victim.party.members.mapIndexed { index, member ->
                            member.takeUnless { index == victimIndex }
                                ?: member.copy(hitPoints = member.hitPoints.copy(current = 0), dead = true)
                        },
                )
            showCampBeat(rules.text.surpriseKill.renderCamp(withParty(party)))
        }
        val battle = battleRunner().runTier(withParty(party), ambushTier)
        showCampBeat(battle.messages)
        return CampMutation(battle.runtime, triggered = true)
    }

    private fun RuntimeState.applyTerrainTransition(
        move: MoveEvent,
        messages: MutableList<String>,
    ): RuntimeState {
        val from = move.fromTerrain?.let { terrain -> content.slug(terrain.id) }
        val to = move.toTerrain?.let { terrain -> content.slug(terrain.id) }
        if (from == null || to == null || from == to) return this
        val transition = wilderness.terrainTransitions.entries.firstOrNull { entry -> entry.terrain == to } ?: return this
        val draw = party.drawRandom(0, 99)
        log.rngDraw(
            context = "wilderness.terrain_transition",
            lo = 0,
            hi = 99,
            value = draw.value,
            hit = draw.value < wilderness.terrainTransitions.chancePercent,
            details =
                mapOf(
                    "from" to from,
                    "to" to to,
                    "threshold_lt" to wilderness.terrainTransitions.chancePercent,
                ),
        )
        if (draw.value < wilderness.terrainTransitions.chancePercent) {
            messages += transition.text.renderText()
        }
        return withParty(draw.party)
    }

    private fun PartyState.regenerateMana(chancePercent: Int): PartyState {
        var party = this
        val members =
            members.mapIndexed { index, member ->
                if (member.pcClass !in manaRegenerationClasses || member.mana.current >= member.mana.max) return@mapIndexed member
                val draw = party.drawRandom(0, 99)
                party = draw.party
                log.rngDraw(
                    context = "wilderness.upkeep_mana_regen",
                    lo = 0,
                    hi = 99,
                    value = draw.value,
                    hit = draw.value < chancePercent,
                    details =
                        mapOf(
                            "member" to (index + 1),
                            "threshold_lt" to chancePercent,
                        ),
                )
                member.takeUnless { draw.value < chancePercent }
                    ?: member.copy(
                        mana = member.mana.copy(current = (member.mana.current + 1).coerceAtMost(member.mana.max)),
                    )
            }
        return party.copy(members = members)
    }

    private fun PartyState.foodTickCost(): Int {
        val leaderLevelCost = if (realm.upkeep.foodCostLeaderDiv > 0) leaderLevel() / realm.upkeep.foodCostLeaderDiv else 0
        return (counters.memberCount ?: members.size) + leaderLevelCost
    }

    private fun PartyState.applyStarvation(
        foodAfterTick: Int,
        threshold: Int,
        damage: Int,
        deathText: ExternalRuntimeTextBlock,
        messages: MutableList<String>,
    ): PartyState {
        if (foodAfterTick > threshold || damage == 0) return this
        return copy(
            members =
                members.mapIndexed { index, member ->
                    if (member.dead) return@mapIndexed member
                    val nextHp = member.hitPoints.current - damage
                    if (nextHp >= 1) return@mapIndexed member.copy(hitPoints = member.hitPoints.copy(current = nextHp))
                    messages += deathText.renderUpkeep(memberName(content, index))
                    member.copy(
                        hitPoints = member.hitPoints.copy(current = 0),
                        dead = true,
                    )
                },
        )
    }

    private fun PartyState.applyFatigue(
        restedHours: Int,
        rules: List<ExternalWildernessFatigueRule>,
        messages: MutableList<String>,
    ): PartyState {
        val rule = rules.firstOrNull { rule -> rule.matches(restedHours) } ?: return this
        messages += rule.text.renderText()
        if (rule.startsAt(restedHours)) audio.cue(AudioCue.Fatigue)
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

    /** True on the turn the party first falls under this rule, so its sound plays once rather than every turn. */
    private fun ExternalWildernessFatigueRule.startsAt(restedHours: Int): Boolean =
        when (match) {
            WildernessFatigueMatch.Equal -> restedHours == threshold
            WildernessFatigueMatch.LessThan -> restedHours == threshold - 1
        }

    private fun PartyState.campProtection(): Set<TemperatureBand> {
        val itemsById = content.items.associateBy { item -> item.id }
        return inventory
            .asSequence()
            .mapNotNull { slot -> slot.item?.let(itemsById::get) }
            .flatMap { item -> item.campProtection.asSequence() }
            .toSet()
    }

    private fun ExternalRuntimeTextBlock.renderText(): List<String> = render({ null }, screen.textColumns)

    private fun ExternalRuntimeTextBlock.renderUpkeep(memberName: String): List<String> =
        render(
            { kind ->
                when (kind) {
                    "upkeepMemberName" -> memberName
                    else -> null
                }
            },
            screen.textColumns,
        )

    private fun battleRunner(): BattleRunner =
        BattleRunner(content, combat, systems.pacing.battle, input, screen, log, PausePrompts(systems.prompts), systems.audio)

    private fun PartyState.matches(expr: CompiledBoolExpr): Boolean =
        when (expr) {
            is CompiledBoolExpr.Cmp -> int(expr.left).compareCompiled(expr.op, int(expr.right))
            is CompiledBoolExpr.Not -> !matches(expr.expr)
            is CompiledBoolExpr.And -> matches(expr.left) && matches(expr.right)
            is CompiledBoolExpr.Or -> matches(expr.left) || matches(expr.right)
            is CompiledBoolExpr.FlagIs -> expr.state in flags
            is CompiledBoolExpr.MilestoneAchieved -> expr.milestone in milestones
            is CompiledBoolExpr.MemberFlag,
            is CompiledBoolExpr.MonsterFlag,
            is CompiledBoolExpr.BattleFlag,
            is CompiledBoolExpr.RuntimeFlag,
            -> false
        }

    private fun PartyState.int(expr: CompiledIntExpr): Int =
        when (expr) {
            is CompiledIntExpr.Literal -> expr.value
            is CompiledIntExpr.Binary -> int(expr.left).applyCompiledBinary(expr.op, int(expr.right))
            is CompiledIntExpr.If -> if (matches(expr.cond)) int(expr.thenValue) else int(expr.elseValue)
            is CompiledIntExpr.PartyScalar -> partyScalar(content.strings.value(expr.nameString))
            is CompiledIntExpr.InventoryPoolCount -> countInventoryPool(expr.pool)
            is CompiledIntExpr.MemberField -> memberField(int(expr.member), content.strings.value(expr.fieldString))
            is CompiledIntExpr.PartyMemberQuery ->
                when (expr.selection) {
                    PartyMemberSelection.First -> memberChoices(expr.filter).firstOrNull() ?: 0
                    PartyMemberSelection.Random -> 0
                }
            is CompiledIntExpr.Local,
            is CompiledIntExpr.Random,
            is CompiledIntExpr.MonsterField,
            is CompiledIntExpr.BattleFlag,
            is CompiledIntExpr.BattleQuery,
            is CompiledIntExpr.RealmScalar,
            is CompiledIntExpr.InventorySlotCost,
            -> 0
        }

    private fun PartyState.partyScalar(name: String): Int =
        when (name) {
            "gold" -> resources.gold
            "torches" -> resources.torches
            "food" -> resources.food
            "time_left" -> resources.timeLeft
            "member_count" -> counters.memberCount ?: members.count { member -> !member.dead }
            else -> 0
        }

    private fun PartyState.memberField(
        memberNumber: Int,
        field: String,
    ): Int {
        val member = members.getOrNull(memberNumber - 1) ?: return 0
        return when (field) {
            "level" -> member.level
            "health" -> member.health
            "hit_points" -> member.hitPoints.current
            "wisdom" -> member.wisdom
            "weapon_skill" -> member.weaponSkill
            "mana" -> member.mana.current
            "charisma" -> member.charisma
            "experience" -> member.experience
            else -> 0
        }
    }

    private fun PartyState.countInventoryPool(pool: CompiledHandleRef): Int {
        val pooledItems =
            content.itemPools
                .firstOrNull { itemPool -> itemPool.id == pool }
                ?.items
                ?.toSet()
                .orEmpty()
        return inventory.count { slot -> slot.item in pooledItems }
    }

    private fun ExternalRuntimeTextBlock.renderCamp(runtime: RuntimeState): List<String> =
        renderRuntimeText(content, runtime, screen.textColumns)

    private fun ExternalRuntimeTextBlock.renderHunt(
        runtime: RuntimeState,
        memberNumber: Int? = null,
        member: CompiledPartyMember? = memberNumber?.let { runtime.party.members.getOrNull(it - 1) },
        foodGained: Int? = null,
    ): List<String> =
        renderRuntimeText(content, runtime, screen.textColumns) { kind ->
            when (kind) {
                "huntMemberName" -> member?.let { content.strings.value(it.nameString) }
                "huntMemberSubjectPronoun" -> member?.gender?.pronoun(PronounCase.Subject)
                "huntFoodGained" -> foodGained?.toString()
                else -> null
            }
        }

    private fun PartyState.leaderLevel(): Int = members.firstOrNull()?.level ?: 0
}

private data class MissionTriggerResult(
    val runtime: RuntimeState,
    val mission: CompiledMission? = null,
)

private data class CampMutation(
    val runtime: RuntimeState,
    val messages: List<String> = emptyList(),
    val triggered: Boolean = false,
)

/** Movement context used for direct runtime wilderness event polling. */
internal data class MoveEvent(
    val from: GridPoint,
    val to: GridPoint,
    val fromTerrain: CompiledTerrain?,
    val toTerrain: CompiledTerrain?,
)

private fun Int.positiveMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus
