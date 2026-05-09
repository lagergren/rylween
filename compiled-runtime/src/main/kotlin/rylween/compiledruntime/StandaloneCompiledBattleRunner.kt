/**
 * Bundle-only standard battle runner for the standalone compiled runtime.
 *
 * The runner consumes `runtimeSystems.combat.standardBattle` plus compiled
 * monster, formula, reward, item, and party rows. It deliberately does not
 * import the old Kotlin battle session: game text is rendered from authored
 * runtime-system text blocks, while this file supplies only the generic combat
 * mechanics required to mutate the standalone compiled party state.
 */

package rylween.compiledruntime

import rylween.compiled.CompiledBinaryOp
import rylween.compiled.CompiledBoolExpr
import rylween.compiled.CompiledComparisonOp
import rylween.compiled.CompiledContentBundle
import rylween.compiled.CompiledFormula
import rylween.compiled.CompiledHandleKind
import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledInstruction
import rylween.compiled.CompiledIntExpr
import rylween.compiled.CompiledItemRecord
import rylween.compiled.CompiledItemUse
import rylween.compiled.CompiledLValue
import rylween.compiled.CompiledMonster
import rylween.compiled.CompiledNativeStringArg
import rylween.compiled.CompiledOpcode
import rylween.compiled.CompiledOperand
import rylween.compiled.CompiledPartyMember
import rylween.compiled.CompiledRewardBundle
import rylween.compiled.CompiledSpell
import rylween.compiled.CompiledStringExpr
import rylween.compiled.CompiledTextBlock
import rylween.compiled.CompiledViewCommand
import rylween.compiled.ExternalBattleText
import rylween.compiled.ExternalCombatAction
import rylween.compiled.ExternalLevelingRules
import rylween.compiled.ExternalRuntimeCombat
import rylween.compiled.ExternalRuntimeTextBlock
import rylween.compiled.ExternalStandardBattle
import rylween.dsl.CombatActionKind
import rylween.dsl.CombatActionRequirement
import rylween.dsl.Gender
import rylween.dsl.ItemConsumePolicy
import rylween.dsl.ItemKind
import rylween.dsl.ItemUseContext
import rylween.dsl.MemberChoiceFilter
import rylween.dsl.NativeAllowlist
import rylween.dsl.PcClass
import rylween.dsl.PronounCase
import rylween.dsl.RuntimeMode
import rylween.dsl.of
import rylween.dsl.pronoun
import rylween.dsl.sourceId

private const val RANDOM_PERCENT_MAX = 99
private const val SPELL_ABORT_SLOT = 7

private const val BATTLE_FOE_DISPLAY_NAME = "battleFoeDisplayName"
private const val BATTLE_ATTACKER_NAME = "battleAttackerName"
private const val BATTLE_TARGET_NAME = "battleTargetName"
private const val BATTLE_DAMAGE = "battleDamage"
private const val BATTLE_EXPERIENCE = "battleExperience"
private const val BATTLE_GOLD = "battleGold"
private const val BATTLE_LOOT_NAME = "battleLootName"
private const val BATTLE_LEVEL_MEMBER_NAME = "battleLevelMemberName"
private const val BATTLE_LEVELS_GAINED = "battleLevelsGained"
private const val BATTLE_MESSAGE_PAGE_LINES = 9

/** Result of a standalone compiled battle handoff. */
internal data class StandaloneCompiledBattleResult(
    val runtime: StandaloneRuntimeState,
    val messages: List<String> = emptyList(),
)

/** Runs standard battles from compiled bundle/runtime-system rows only. */
internal class StandaloneCompiledBattleRunner(
    private val content: CompiledContentBundle,
    private val combat: ExternalRuntimeCombat,
    private val input: StandaloneInput,
    private val screen: CompiledAnsiScreen,
    private val pausePrompts: StandalonePausePrompts,
    private val log: StandaloneRuntimeLog? = null,
) {
    private val battle: ExternalStandardBattle? = combat.standardBattle

    fun runTier(
        runtime: StandaloneRuntimeState,
        tier: String,
    ): StandaloneCompiledBattleResult {
        val candidates = content.monsters.filter { monster -> monster.tier?.let(content::slug) == tier }
        if (candidates.isEmpty()) return StandaloneCompiledBattleResult(runtime, listOf("No compiled monsters for tier $tier."))
        val pick = runtime.party.drawRandom(0, candidates.lastIndex)
        log?.rngDraw(
            context = "battle.tier_monster",
            lo = 0,
            hi = candidates.lastIndex,
            value = pick.value,
            details =
                mapOf(
                    "tier" to tier,
                    "monster" to content.slug(candidates[pick.value].id),
                ),
        )
        return runMonster(runtime.withParty(pick.party), candidates[pick.value])
    }

    fun runMonster(
        runtime: StandaloneRuntimeState,
        monsterRef: CompiledHandleRef,
    ): StandaloneCompiledBattleResult {
        val monster =
            content.monsters.firstOrNull { current -> current.id == monsterRef }
                ?: return StandaloneCompiledBattleResult(runtime, listOf("Missing compiled monster ${content.slug(monsterRef)}."))
        return runMonster(runtime, monster)
    }

    private fun runMonster(
        runtime: StandaloneRuntimeState,
        monster: CompiledMonster,
    ): StandaloneCompiledBattleResult {
        val rules = battle ?: return StandaloneCompiledBattleResult(runtime, listOf("Missing compiled standard-battle contract."))
        val started = startEncounter(runtime, rules, monster)
        rules.opening.banners.getOrNull(started.bannerIndex ?: -1)?.let { banner ->
            screen.writeViewCommand(CompiledRuntimeViewCommand.ClearMapWindow)
            screen.writeViewCommand(CompiledRuntimeViewCommand.ShowImage(banner))
        }
        while (true) {
            screen.writeText(rules.openingText(started))
            val choice = input.readCommand() ?: return StandaloneCompiledBattleResult(runtime)
            when {
                rules.openingKeys.run.matches(choice) -> return attemptRun(started)
                rules.openingKeys.fight.matches(choice) -> return finishFight(started, rules.text.bloodFlow.renderBattle())
                else -> screen.writeText(rules.text.notPossible.renderBattle())
            }
        }
    }

    private fun attemptRun(encounter: StandaloneBattleEncounter): StandaloneCompiledBattleResult {
        val rules = encounter.rules
        val draw = encounter.runtime.party.drawRandom(0, RANDOM_PERCENT_MAX)
        val escaped = draw.value < rules.opening.fleeChancePercent
        log?.rngDraw(
            context = "battle.flee",
            lo = 0,
            hi = RANDOM_PERCENT_MAX,
            value = draw.value,
            hit = escaped,
            details = mapOf("threshold_lt" to rules.opening.fleeChancePercent),
        )
        val runtime = encounter.runtime.withParty(draw.party)
        return escaped
            .takeIf { it }
            ?.let {
                StandaloneCompiledBattleResult(
                    encounter.copy(runtime = runtime).runtimeAfterBattle(),
                    rules.text.runEscaped.renderBattle(),
                )
            }
            ?: finishFight(encounter.copy(runtime = runtime.withMode(RuntimeMode.Battle)), rules.text.runFailAway.renderBattle())
    }

    private fun finishFight(
        initial: StandaloneBattleEncounter,
        initialMessages: List<String>,
    ): StandaloneCompiledBattleResult {
        var encounter = initial
        var openingMessages = initialMessages
        var round = 0
        while (round < encounter.rules.roundLimit && encounter.partyHasLivingMembers() && encounter.monsterHasLivingMembers()) {
            if (!encounter.partyHeld) {
                val playerMessages = openingMessages.toMutableList()
                openingMessages = emptyList()
                encounter = encounter.playerRound(playerMessages)
                showBattleMessages(playerMessages)
            }
            if (encounter.monsterHasLivingMembers()) {
                val monsterMessages = mutableListOf<String>()
                encounter = encounter.monsterRound(monsterMessages)
                showBattleMessages(monsterMessages)
            }
            round++
        }
        val finished = encounter.finish()
        showBattleMessages(finished.messages)
        return StandaloneCompiledBattleResult(finished.runtime)
    }

    private fun showBattleMessages(messages: List<String>) {
        if (messages.isEmpty()) return
        messages.chunked(BATTLE_MESSAGE_PAGE_LINES).forEach { page ->
            screen.writeText(page + pausePrompts.line())
            if (!input.scriptedMode) input.readCommand()
        }
    }

    private fun ExternalStandardBattle.openingText(encounter: StandaloneBattleEncounter): List<String> =
        text.encounterBanner.renderBattle() +
            text.youFace.renderBattle() +
            text.foeDisplayName.renderBattle(BATTLE_FOE_DISPLAY_NAME to encounter.foeDisplayName()) +
            text.fight.renderBattle() +
            text.run.renderBattle()

    private fun StandaloneBattleEncounter.playerRound(messages: MutableList<String>): StandaloneBattleEncounter {
        var next = this
        runtime.party.members.forEachIndexed { index, member ->
            if (!member.dead && next.monsterHasLivingMembers()) {
                var retry = true
                var attempts = 0
                while (retry && attempts < rules.roundLimit && next.monsterHasLivingMembers()) {
                    next = next.playerAction(index, member, messages)
                    retry = next.actionRetry
                    next = next.copy(actionRetry = false)
                    attempts++
                }
            }
        }
        return next
    }

    private fun StandaloneBattleEncounter.playerAction(
        memberIndex: Int,
        member: CompiledPartyMember,
        messages: MutableList<String>,
    ): StandaloneBattleEncounter {
        val actions = rules.actions.filter { action -> action.availableTo(member) }
        val prompt = rules.text.playerActionPrompt(member, actions)
        screen.writeText(prompt)
        val command = input.readCommand()
        val action = command?.let { key -> actions.firstOrNull { current -> current.binding.matches(key) } }
        return when (action?.kind ?: CombatActionKind.Attack) {
            CombatActionKind.Attack -> attack(member, messages)
            CombatActionKind.Defend -> defend(memberIndex, member, messages)
            CombatActionKind.BattleCry -> battleCry(messages)
            CombatActionKind.UseMagicItem -> useMagicItem(memberIndex, messages)
            CombatActionKind.CastMageSpell -> castMageSpell(memberIndex, member, messages)
        }
    }

    private fun StandaloneBattleEncounter.attack(
        member: CompiledPartyMember,
        messages: MutableList<String>,
    ): StandaloneBattleEncounter {
        val targetIndex = monsterHp.indexOfFirst { hp -> hp > 0 }
        if (targetIndex < 0) return this
        val hit = runtime.party.drawRandom(0, RANDOM_PERCENT_MAX)
        val skill =
            when {
                partyConfused -> member.weaponSkill / rules.playerRound.attack.confusionDivisor
                else -> member.weaponSkill
            }
        val hitChance = (skill + rules.playerRound.attack.hitBonus).coerceIn(0, 100)
        if (hit.value >= hitChance) {
            messages += rules.text.playerMiss.renderBattle(BATTLE_ATTACKER_NAME to content.strings.value(member.nameString))
            return copy(runtime = runtime.withParty(hit.party))
        }
        val damageDraw = hit.party.drawRandom(0, rules.playerRound.attack.damageSpreadMax)
        val damage =
            member.weaponDamage() +
                damageDraw.value +
                member.hitPoints.current / rules.playerRound.attack.hpDefenseDivisor
        val nextHp = monsterHp.mapIndexed { index, hp -> hp.takeUnless { index == targetIndex } ?: (hp - damage).coerceAtLeast(0) }
        val killed = monsterHp[targetIndex] > 0 && nextHp[targetIndex] == 0
        messages +=
            rules.text.playerHit.renderBattle(
                BATTLE_ATTACKER_NAME to content.strings.value(member.nameString),
                BATTLE_DAMAGE to damage.toString(),
            )
        if (killed) messages += rules.text.killed.renderBattle()
        return copy(runtime = runtime.withParty(damageDraw.party), monsterHp = nextHp)
    }

    private fun StandaloneBattleEncounter.defend(
        memberIndex: Int,
        member: CompiledPartyMember,
        messages: MutableList<String>,
    ): StandaloneBattleEncounter {
        val healed = (member.hitPoints.current + member.level).coerceAtMost(member.hitPoints.max)
        val nextMember = member.copy(hitPoints = healed of member.hitPoints.max)
        val nextMessages =
            rules.text.restGainHits
                .takeIf { defendCount < rules.playerRound.defend.restThreshold }
                ?: rules.text.defendStance
        messages += nextMessages.renderBattle()
        return copy(
            runtime = runtime.withMember(memberIndex, nextMember),
            defendCount = (defendCount + 1).coerceAtMost(rules.playerRound.defend.cap),
            monsterWeaponSkill = (monsterWeaponSkill - rules.playerRound.defend.weaponSkillPenalty).coerceAtLeast(0),
        )
    }

    private fun StandaloneBattleEncounter.battleCry(messages: MutableList<String>): StandaloneBattleEncounter {
        messages += rules.text.battleCry.renderBattle()
        return copy(monsterWeaponSkill = (monsterWeaponSkill - rules.playerRound.battleCry.weaponSkillPenalty).coerceAtLeast(0))
    }

    private fun StandaloneBattleEncounter.useMagicItem(
        memberIndex: Int,
        messages: MutableList<String>,
    ): StandaloneBattleEncounter {
        screen.writeText(rules.text.useWhat.renderBattle() + runtime.party.inventoryChoiceLines())
        val slot = input.readCommand()?.inventorySlot() ?: return copy(actionRetry = true)
        val item = runtime.party.inventoryItem(slot)
        val use = item?.let(::combatItemUse)
        val record = item?.let { ref -> content.itemRecord(ref) }
        if (item == null || use == null || record?.kind != ItemKind.MagicItem) {
            messages += rules.text.notPossible.renderBattle()
            return copy(actionRetry = true)
        }
        if (monster.immune) {
            messages += rules.text.magicImmune.renderBattle()
            return copy(runtime = runtime.withParty(runtime.party.withInventoryItem(slot, null)))
        }
        val active =
            when (use.consume) {
                ItemConsumePolicy.BeforeUse -> copy(runtime = runtime.withParty(runtime.party.withInventoryItem(slot, null)))
                ItemConsumePolicy.ScriptControlled,
                ItemConsumePolicy.NeverConsumes,
                -> this
            }
        return active.runBattleScript(
            scriptRoot = use.scriptRoot,
            memberIndex = memberIndex,
            locals = mapOf("caster_idx" to memberIndex, "inv_slot" to slot),
            activeInventorySlot = slot,
        )
    }

    private fun StandaloneBattleEncounter.castMageSpell(
        memberIndex: Int,
        member: CompiledPartyMember,
        messages: MutableList<String>,
    ): StandaloneBattleEncounter {
        val spells = mageSpells()
        screen.writeText(spellChoiceLines(member, spells))
        val slot = input.readCommand()?.digitToIntOrNull() ?: return copy(actionRetry = true)
        if (slot == SPELL_ABORT_SLOT) return copy(actionRetry = true)
        val spell = spells.firstOrNull { current -> current.slot == slot }
        if (spell == null) {
            messages += rules.text.notPossible.renderBattle()
            return copy(actionRetry = true)
        }
        if (monster.immune) {
            messages += rules.text.spellFizzle.renderBattle()
            return this
        }
        val cost = spell.baseCost + member.level
        if (member.mana.current < cost) {
            messages += rules.text.notEnoughMagic.renderBattle()
            return this
        }
        val nextMember = member.copy(mana = (member.mana.current - cost) of member.mana.max)
        return copy(runtime = runtime.withMember(memberIndex, nextMember))
            .runBattleScript(
                scriptRoot = spell.scriptRoot,
                memberIndex = memberIndex,
                locals = mapOf("caster_idx" to memberIndex),
            )
    }

    private fun StandaloneBattleEncounter.monsterRound(messages: MutableList<String>): StandaloneBattleEncounter {
        var next = copy(partyHeld = false, partyConfused = false, actionRetry = false)
        monsterHp.forEachIndexed { index, hp ->
            if (hp > 0 && next.partyHasLivingMembers() && !next.monstersHeld) {
                val cast = next.shouldMonsterCast()
                next = cast.encounter
                next =
                    when {
                        cast.value -> next.castMonsterSpell(index)
                        else -> next.monsterAttack(hp, messages)
                    }
            }
        }
        return next.afterMonsterRound()
    }

    private fun StandaloneBattleEncounter.monsterAttack(
        monsterHitPoints: Int,
        messages: MutableList<String>,
    ): StandaloneBattleEncounter {
        val alive = runtime.party.members.mapIndexedNotNull { index, member -> index.takeUnless { member.dead } }
        if (alive.isEmpty()) return this
        val targetDraw = runtime.party.drawRandom(0, alive.lastIndex)
        val targetIndex = alive[targetDraw.value]
        val target = targetDraw.party.members[targetIndex]
        val hit = targetDraw.party.drawRandom(0, RANDOM_PERCENT_MAX)
        val hitChance = (monsterWeaponSkill + rules.monsterRound.melee.hitBonus).coerceIn(0, 100)
        if (hit.value >= hitChance) {
            messages +=
                rules.text.monsterMiss.renderBattle(
                    BATTLE_ATTACKER_NAME to monsterName(),
                    BATTLE_TARGET_NAME to content.strings.value(target.nameString),
                )
            return copy(runtime = runtime.withParty(hit.party))
        }
        val damageDraw = hit.party.drawRandom(0, rules.monsterRound.melee.damageSpreadMax)
        val damage =
            (monsterHitPoints / rules.monsterRound.melee.damageDivisor + damageDraw.value - target.armorClass())
                .coerceAtLeast(rules.monsterRound.melee.damageMin)
        val nextHp = (target.hitPoints.current - damage).coerceAtLeast(0)
        val nextTarget = target.copy(hitPoints = nextHp of target.hitPoints.max, dead = target.dead || nextHp == 0)
        messages +=
            rules.text.monsterHit.renderBattle(
                BATTLE_ATTACKER_NAME to monsterName(),
                BATTLE_TARGET_NAME to content.strings.value(target.nameString),
                BATTLE_DAMAGE to damage.toString(),
            )
        if (nextHp == 0) {
            messages += rules.text.memberDeath.renderBattle(BATTLE_TARGET_NAME to content.strings.value(target.nameString))
        }
        return copy(runtime = runtime.withParty(damageDraw.party).withMember(targetIndex, nextTarget))
    }

    private fun StandaloneBattleEncounter.shouldMonsterCast(): BattleCastDecision {
        val options = monsterSpells()
        if (antimagic || monster.spellPower <= 0 || options.isEmpty()) return BattleCastDecision(false, this)
        val chance = rules.monsterRound.spellChanceBasePercent + monster.spellPower / rules.monsterRound.spellPowerDivisor
        val draw = runtime.party.drawRandom(0, RANDOM_PERCENT_MAX)
        return BattleCastDecision(draw.value < chance, copy(runtime = runtime.withParty(draw.party)))
    }

    private fun StandaloneBattleEncounter.castMonsterSpell(monsterIndex: Int): StandaloneBattleEncounter {
        val options = monsterSpells()
        if (options.isEmpty()) return this
        val pick = runtime.party.drawRandom(0, options.lastIndex)
        return copy(runtime = runtime.withParty(pick.party))
            .runBattleScript(
                scriptRoot = options[pick.value].scriptRoot,
                memberIndex = null,
                locals = mapOf("caster_idx" to monsterIndex, "attacker_idx" to monsterIndex),
            )
    }

    private fun StandaloneBattleEncounter.runBattleScript(
        scriptRoot: CompiledHandleRef,
        memberIndex: Int?,
        locals: Map<String, Int>,
        activeInventorySlot: Int? = null,
    ): StandaloneBattleEncounter {
        val program = content.scripts.roots.firstOrNull { root -> root.rootHandle == scriptRoot }
        return program
            ?.let {
                CompiledCombatScriptRunner(this, memberIndex, activeInventorySlot).run(it.instructions, locals)
            }
            ?: this
    }

    private fun StandaloneBattleEncounter.afterMonsterRound(): StandaloneBattleEncounter =
        copy(
            monsterWeaponSkill =
                when {
                    monsterConfused -> monsterWeaponSkill * 2
                    else -> monsterWeaponSkill
                },
            monsterConfused = false,
            monstersHeld = false,
            antimagic = false,
            actionRetry = false,
        )

    private fun StandaloneBattleEncounter.finish(): StandaloneCompiledBattleResult =
        when {
            !partyHasLivingMembers() ->
                StandaloneCompiledBattleResult(
                    runtime.copy(currentMode = RuntimeMode.GameOver),
                    rules.text.partyDefeated.renderBattle(),
                )
            monsterHasLivingMembers() -> StandaloneCompiledBattleResult(runtimeAfterBattle(), rules.text.runFailAway.renderBattle())
            else -> victory()
        }

    private fun StandaloneBattleEncounter.victory(): StandaloneCompiledBattleResult {
        rules.victoryImage?.let { image ->
            screen.writeViewCommand(CompiledRuntimeViewCommand.ClearMapWindow)
            screen.writeViewCommand(CompiledRuntimeViewCommand.ShowImage(image))
        }
        val reward = rewardBundle()
        val bindings =
            mapOf(
                "encounter_foe_count" to monsterCount,
                "encounter_hit_point_max" to hitPointMax,
                "monster_weapon_skill" to monsterWeaponSkill,
                "monster_spell_power" to monster.spellPower,
            )
        var party = runtime.party
        val experience = reward?.experience?.let { formula -> evalFormula(formula, party, bindings).also { party = it.party }.value } ?: 0
        val gold = reward?.gold?.let { formula -> evalFormula(formula, party, bindings).also { party = it.party }.value } ?: 0
        val loot = reward?.let { selectLoot(party, it).also { party = it.party }.item }
        party = party.applyBattleRewards(experience, gold, loot, monsterCount)
        val levelUps = party.applyLevelUps(combat.leveling)
        party = levelUps.party
        val messages =
            buildList {
                addAll(rules.text.victoryBested.renderBattle())
                addAll(
                    rules.text.victoryEachSurvivor.renderBattle() +
                        rules.text.victoryReward.renderBattle(
                            BATTLE_EXPERIENCE to experience.toString(),
                            BATTLE_GOLD to gold.toString(),
                        ),
                )
                loot?.let { item ->
                    addAll(
                        rules.text.victoryFoundObject.renderBattle() +
                            rules.text.victoryLoot.renderBattle(BATTLE_LOOT_NAME to item.displayName()),
                    )
                }
                levelUps.gains.forEach { gain ->
                    addAll(
                        rules.text.levelGain.renderBattle(
                            BATTLE_LEVEL_MEMBER_NAME to gain.memberName,
                            BATTLE_LEVELS_GAINED to gain.levels.toString(),
                        ),
                    )
                }
            }
        return StandaloneCompiledBattleResult(runtimeAfterBattle(party), messages)
    }

    private fun startEncounter(
        runtime: StandaloneRuntimeState,
        rules: ExternalStandardBattle,
        monster: CompiledMonster,
    ): StandaloneBattleEncounter {
        var party = runtime.party
        val firstLevel = party.members.firstOrNull()?.level ?: 0
        val countMax = monster.count.last + (monster.countMaxLevelDivisor?.let { divisor -> firstLevel / divisor } ?: 0)
        val bannerDraw =
            rules.opening.banners
                .takeIf { banners -> banners.isNotEmpty() }
                ?.let { banners -> party.drawRandom(0, banners.lastIndex) }
        bannerDraw?.let { draw ->
            log?.rngDraw(
                context = "battle.encounter_banner",
                lo = 0,
                hi = rules.opening.banners.lastIndex,
                value = draw.value,
                details = mapOf("monster" to content.slug(monster.id)),
            )
        }
        party = bannerDraw?.party ?: party
        val countDraw = party.drawRandom(1, countMax.coerceAtLeast(1))
        party = countDraw.party
        val count = countDraw.value.coerceAtLeast(monster.count.first).coerceAtMost(rules.encounterFoeCountMax)
        log?.rngDraw(
            context = "battle.foe_count",
            lo = 1,
            hi = countMax.coerceAtLeast(1),
            value = countDraw.value,
            details =
                mapOf(
                    "monster" to content.slug(monster.id),
                    "coerced" to count,
                    "min" to monster.count.first,
                    "max" to rules.encounterFoeCountMax,
                ),
        )
        val hpRange = scaledHitPointRange(monster, rules, party)
        val hitPoints =
            buildList {
                repeat(count) { index ->
                    val hpDraw = party.drawRandom(1, hpRange.last)
                    party = hpDraw.party
                    log?.rngDraw(
                        context = "battle.foe_hp",
                        lo = 1,
                        hi = hpRange.last,
                        value = hpDraw.value,
                        details =
                            mapOf(
                                "monster" to content.slug(monster.id),
                                "slot" to (index + 1),
                                "coerced" to hpDraw.value.coerceAtLeast(hpRange.first),
                                "min" to hpRange.first,
                            ),
                    )
                    add(hpDraw.value.coerceAtLeast(hpRange.first))
                }
            }
        return StandaloneBattleEncounter(
            runtime = runtime.withParty(party).withMode(RuntimeMode.Battle),
            returnMode = runtime.currentMode,
            returnTown = runtime.currentTown,
            rules = rules,
            monster = monster,
            monsterCount = count,
            monsterHp = hitPoints,
            hitPointMax = hpRange.last,
            monsterWeaponSkill = monster.weaponSkill + firstLevel,
            bannerIndex = bannerDraw?.value,
        )
    }

    private fun scaledHitPointRange(
        monster: CompiledMonster,
        rules: ExternalStandardBattle,
        party: StandaloneParty,
    ): IntRange {
        val firstLevel = party.members.firstOrNull()?.level ?: 0
        val partyAverage =
            party.members
                .takeIf { members -> members.isNotEmpty() }
                ?.map(CompiledPartyMember::level)
                ?.average()
                ?.toInt()
                ?: 0
        val bindings =
            mapOf(
                "encounter_hit_point_min_base" to monster.hitPoints.first,
                "encounter_hit_point_max_base" to monster.hitPoints.last,
                "party_avg" to partyAverage,
                "first_level" to firstLevel,
            )
        val min = evalFormula(rules.encounterHitPointMinFormula, party, bindings).value
        val max = evalFormula(rules.encounterHitPointMaxFormula, party, bindings).value
        return min.coerceAtMost(max)..max
    }

    private fun StandaloneBattleEncounter.rewardBundle(): CompiledRewardBundle? =
        content
            .ref(CompiledHandleKind.RewardBundle, rules.rewards)
            .let { ref -> content.rewards.firstOrNull { reward -> reward.id == ref } }

    private fun selectLoot(
        party: StandaloneParty,
        reward: CompiledRewardBundle,
    ): BattleLootSelection {
        val freeSlot = party.inventory.firstOrNull { slot -> slot.item == null }?.slot
        if (freeSlot == null) return BattleLootSelection(party, null)
        val drop = party.drawRandom(0, RANDOM_PERCENT_MAX)
        if (drop.value >= reward.loot.dropChancePercent) return BattleLootSelection(drop.party, null)
        val treasure = drop.party.drawRandom(0, RANDOM_PERCENT_MAX)
        val poolRef = reward.loot.treasure.takeIf { treasure.value < reward.loot.treasureChancePercent } ?: reward.loot.magic
        val pool = content.itemPools.firstOrNull { itemPool -> itemPool.id == poolRef } ?: return BattleLootSelection(treasure.party, null)
        if (pool.items.isEmpty()) return BattleLootSelection(treasure.party, null)
        val item = treasure.party.drawRandom(0, pool.items.lastIndex)
        return BattleLootSelection(item.party, pool.items[item.value])
    }

    private fun StandaloneParty.applyBattleRewards(
        experience: Int,
        gold: Int,
        loot: CompiledHandleRef?,
        defeatedCount: Int,
    ): StandaloneParty {
        val survivorExperience =
            members.map { member ->
                member.takeIf { it.dead } ?: member.copy(experience = member.experience + experience)
            }
        val withCounters = copy(counters = counters.copy(kills = counters.kills + defeatedCount), members = survivorExperience)
        val withGold = withCounters.withGold(withCounters.resources.gold + gold)
        val freeSlot = withGold.inventory.firstOrNull { slot -> slot.item == null }?.slot
        return loot?.let { item -> freeSlot?.let { slot -> withGold.withInventoryItem(slot, item) } } ?: withGold
    }

    private fun StandaloneParty.applyLevelUps(leveling: ExternalLevelingRules?): BattleLevelUps {
        leveling ?: return BattleLevelUps(this)
        val growthByClass = leveling.classGrowth.associateBy { growth -> growth.pcClass }
        val gains = mutableListOf<BattleLevelGain>()
        val nextMembers =
            members.map { member ->
                val gained =
                    ((member.level + 1)..leveling.maxLevel).count { targetLevel ->
                        member.experience >= leveling.xpThresholds.getOrElse(targetLevel) { Int.MAX_VALUE }
                    }
                if (gained == 0) return@map member
                gains += BattleLevelGain(content.strings.value(member.nameString), gained)
                val growth = growthByClass[member.pcClass]
                val nextHealth = member.health + (growth?.health ?: 0) * gained
                val hpFloor = evalFormula(leveling.hitPointFloor, this, mapOf("health" to nextHealth)).value
                member.copy(
                    level = (member.level + gained).coerceAtMost(leveling.maxLevel),
                    weaponSkill = member.weaponSkill + (growth?.weaponSkill ?: 0) * gained,
                    health = nextHealth,
                    wisdom = member.wisdom + (growth?.wisdom ?: 0) * gained,
                    charisma = member.charisma + (growth?.charisma ?: 0) * gained,
                    hitPoints = member.hitPoints.current.coerceAtLeast(hpFloor) of nextHealth,
                )
            }
        return BattleLevelUps(copy(members = nextMembers), gains.toList())
    }

    private fun evalFormula(
        formulaRef: CompiledHandleRef,
        party: StandaloneParty,
        bindings: Map<String, Int>,
    ): FormulaResult {
        val formula = content.formulas.firstOrNull { current -> current.id == formulaRef }
        return evalFormula(checkNotNull(formula) { "missing compiled formula ${content.slug(formulaRef)}" }, party, bindings)
    }

    private fun evalFormula(
        formulaSlug: String,
        party: StandaloneParty,
        bindings: Map<String, Int>,
    ): FormulaResult {
        val formulaRef = content.ref(CompiledHandleKind.Formula, formulaSlug)
        val formula = content.formulas.firstOrNull { current -> current.id == formulaRef }
        return evalFormula(checkNotNull(formula) { "missing compiled formula $formulaSlug" }, party, bindings)
    }

    private fun evalFormula(
        formula: CompiledFormula,
        party: StandaloneParty,
        bindings: Map<String, Int>,
    ): FormulaResult = StandaloneFormulaEvaluator(party, bindings).eval(formula.value)

    private fun CompiledPartyMember.weaponDamage(): Int = content.itemRecord(equipment.weapon)?.weaponDamage ?: 1

    private fun CompiledPartyMember.armorClass(): Int = content.itemRecord(equipment.armor)?.armorClass ?: 0

    private fun CompiledHandleRef.displayName(): String =
        content.itemRecord(this)?.let { item -> content.strings.value(item.displayNameString) } ?: content.slug(this)

    private fun CompiledContentBundle.itemRecord(item: CompiledHandleRef): CompiledItemRecord? =
        items.firstOrNull { record -> record.id == item }

    private fun StandaloneParty.countInventoryPool(pool: CompiledHandleRef): Int {
        val pooledItems =
            content.itemPools
                .firstOrNull { itemPool -> itemPool.id == pool }
                ?.items
                ?.toSet()
                .orEmpty()
        return inventory.count { slot -> slot.item in pooledItems }
    }

    private fun StandaloneParty.consumeInventoryPool(
        pool: CompiledHandleRef,
        quantity: Int,
    ): StandaloneParty {
        val pooledItems =
            content.itemPools
                .firstOrNull { itemPool -> itemPool.id == pool }
                ?.items
                ?.toSet()
                .orEmpty()
        val consumedSlots =
            inventory
                .asSequence()
                .filter { slot -> slot.item in pooledItems }
                .take(quantity.coerceAtLeast(0))
                .map { slot -> slot.slot }
                .toSet()
        return copy(
            inventory = inventory.map { slot -> slot.copy(item = slot.item.takeUnless { slot.slot in consumedSlots }) },
        )
    }

    private fun StandaloneParty.withLivingCount(): StandaloneParty =
        copy(counters = counters.copy(memberCount = members.count { member -> !member.dead }))

    private fun CompiledPartyMember.matchesCombatFilter(filter: MemberChoiceFilter): Boolean =
        when (filter) {
            MemberChoiceFilter.Any -> true
            MemberChoiceFilter.AliveOnly -> !dead
            MemberChoiceFilter.DeadOnly -> dead
            MemberChoiceFilter.SpellcasterOnly -> !dead && pcClass in setOf(PcClass.Mage, PcClass.Priest)
        }

    private fun StandaloneRuntimeState.withMode(mode: RuntimeMode): StandaloneRuntimeState = copy(currentMode = mode, currentTown = null)

    private fun StandaloneRuntimeState.withMember(
        index: Int,
        member: CompiledPartyMember,
    ): StandaloneRuntimeState =
        withParty(
            party.copy(
                members =
                    party.members.mapIndexed { memberIndex, current ->
                        current.takeUnless { memberIndex == index } ?: member
                    },
            ),
        )

    private fun ExternalBattleText.playerActionPrompt(
        member: CompiledPartyMember,
        actions: List<ExternalCombatAction>,
    ): List<String> =
        buildList {
            add(content.strings.value(member.nameString))
            addAll(whatNow.renderBattle())
            actions.forEach { action -> addAll(action.prompt.renderBattle()) }
        }

    private fun ExternalCombatAction.availableTo(member: CompiledPartyMember): Boolean =
        when (requirement) {
            CombatActionRequirement.Always -> true
            CombatActionRequirement.Mage -> member.pcClass == PcClass.Mage
        }

    private fun ExternalRuntimeTextBlock.renderBattle(vararg values: Pair<String, String>): List<String> = renderBattle(values.toMap())

    private fun ExternalRuntimeTextBlock.renderBattle(values: Map<String, String> = emptyMap()): List<String> = render(values::get)

    private fun StandaloneBattleEncounter.foeDisplayName(): String =
        "$monsterCount ${monsterName()}${"s".takeIf { monsterCount > 1 }.orEmpty()}"

    private fun StandaloneBattleEncounter.monsterName(): String = content.strings.value(monster.nameString)

    private fun StandaloneBattleEncounter.partyHasLivingMembers(): Boolean = runtime.party.members.any { member -> !member.dead }

    private fun StandaloneBattleEncounter.monsterHasLivingMembers(): Boolean = monsterHp.any { hp -> hp > 0 }

    private fun combatItemUse(item: CompiledHandleRef): CompiledItemUse? =
        content.itemUses.firstOrNull { use -> use.item == item && ItemUseContext.Combat in use.contexts }

    private fun mageSpells(): List<CompiledSpell> =
        content.spells.filter { spell -> content.slug(spell.id).startsWith("mage.") }.sortedBy { spell -> spell.slot }

    private fun monsterSpells(): List<CompiledSpell> =
        content.spells
            .filter { spell ->
                val slug = content.slug(spell.id)
                slug.startsWith("monster.") && slug != "monster.melee"
            }.sortedBy { spell -> spell.slot }

    private fun StandaloneParty.inventoryChoiceLines(): List<String> =
        inventory.sortedBy { slot -> slot.slot }.map { slot ->
            val itemName =
                slot.item
                    ?.let { item -> content.itemRecord(item)?.let { record -> content.strings.value(record.displayNameString) } }
                    ?: "empty"
            "${'A' + slot.slot - 1}. $itemName"
        }

    private fun spellChoiceLines(
        member: CompiledPartyMember,
        spells: List<CompiledSpell>,
    ): List<String> =
        spells.map { spell ->
            "${spell.slot}. ${content.strings.value(spell.nameString)} (${spell.baseCost + member.level}/${member.mana.current})"
        } + (
            combat.standardBattle
                ?.text
                ?.spellAbort
                ?.renderBattle() ?: emptyList()
        )

    private fun Char.inventorySlot(): Int? {
        val slot = uppercaseChar() - 'A' + 1
        return slot.takeIf { candidate -> candidate > 0 }
    }

    private fun StandaloneBattleEncounter.monsterTargetLines(): List<String> =
        monsterHp.mapIndexed { index, hp ->
            val state = if (hp > 0) "Alive." else "Dead."
            "${'A' + index}. $state (${hp.toString().padStart(3)} hp)"
        }

    private inner class CompiledCombatScriptRunner(
        initialEncounter: StandaloneBattleEncounter,
        private val memberIndex: Int?,
        private val activeInventorySlot: Int?,
    ) {
        private var encounter = initialEncounter
        private var locals = emptyMap<String, Int>()

        fun run(
            instructions: List<CompiledInstruction>,
            initialLocals: Map<String, Int>,
        ): StandaloneBattleEncounter {
            locals = initialLocals
            execute(instructions)
            return encounter
        }

        private fun execute(instructions: List<CompiledInstruction>) {
            instructions.forEach { instruction ->
                if (execute(instruction)) return
            }
        }

        private fun execute(instruction: CompiledInstruction): Boolean =
            when (instruction.opcode) {
                CompiledOpcode.Screen -> false.also { screen.writeText(instruction.textBlockOperand(0).renderScriptLines()) }
                CompiledOpcode.Emit -> false.also { screen.writeText(listOf("[text:${content.slug(instruction.handleOperand(0))}]")) }
                CompiledOpcode.EmitIndexed ->
                    false.also {
                        screen.writeText(
                            listOf(
                                content.strings
                                    .value(
                                        instruction.stringIdOperand(0),
                                    ).replace("%d", int(instruction.intOperand(1)).toString()),
                            ),
                        )
                    }
                CompiledOpcode.WaitInput -> false.also { input.readCommand() }
                CompiledOpcode.Pause -> false
                CompiledOpcode.View -> false.also { screen.writeViewCommand(instruction.viewOperand(0).toRuntimeViewCommand()) }
                CompiledOpcode.LetInt ->
                    false.also {
                        locals = locals + (content.strings.value(instruction.stringIdOperand(0)) to int(instruction.intOperand(1)))
                    }
                CompiledOpcode.BindText -> false
                CompiledOpcode.Set -> false.also { write(instruction.lvalueOperand(0), int(instruction.intOperand(1))) }
                CompiledOpcode.Add ->
                    false.also {
                        write(
                            instruction.lvalueOperand(0),
                            read(instruction.lvalueOperand(0)) + int(instruction.intOperand(1)),
                        )
                    }
                CompiledOpcode.Sub ->
                    false.also {
                        write(
                            instruction.lvalueOperand(0),
                            read(instruction.lvalueOperand(0)) - int(instruction.intOperand(1)),
                        )
                    }
                CompiledOpcode.AddClamped ->
                    false.also {
                        val target = instruction.lvalueOperand(0)
                        val next = read(target) + int(instruction.intOperand(1))
                        write(target, next.coerceAtMost(int(instruction.intOperand(2))))
                    }
                CompiledOpcode.If -> false.also { execute(instruction.instructionsOperand(if (bool(instruction.boolOperand(0))) 1 else 2)) }
                CompiledOpcode.ForEachMonster ->
                    false.also {
                        val name = content.strings.value(instruction.stringIdOperand(0))
                        val includeDead = instruction.booleanOperand(1)
                        encounter.monsterHp.indices.forEach { monster ->
                            if (includeDead || encounter.monsterHp[monster] > 0) {
                                executeWithLocal(name, monster, instruction.instructionsOperand(2))
                            }
                        }
                    }
                CompiledOpcode.ForEachMember ->
                    false.also {
                        val name = content.strings.value(instruction.stringIdOperand(0))
                        val includeDead = instruction.booleanOperand(1)
                        encounter.runtime.party.members.indices.forEach { member ->
                            if (includeDead ||
                                !encounter.runtime.party.members[member]
                                    .dead
                            ) {
                                executeWithLocal(name, member, instruction.instructionsOperand(2))
                            }
                        }
                    }
                CompiledOpcode.AskMonster ->
                    false.also {
                        screen.writeText(instruction.textBlockOperand(0).renderScriptLines() + encounter.monsterTargetLines())
                        val target = input.readCommand()?.uppercaseChar()?.let { key -> key - 'A' }
                        locals =
                            locals +
                            (
                                content.strings.value(instruction.stringIdOperand(1)) to
                                    (target?.takeIf { encounter.monsterHp.getOrNull(it)?.let { hp -> hp > 0 } == true } ?: 0)
                            )
                    }
                CompiledOpcode.AskMember ->
                    false.also {
                        screen.writeText(instruction.textBlockOperand(0).renderScriptLines())
                        val accepting = enumValueOf<MemberChoiceFilter>(instruction.enumOperand(3))
                        val target = input.readCommand()?.digitToIntOrNull()?.minus(1)
                        val accepted =
                            target?.takeIf { index ->
                                encounter.runtime.party.members
                                    .getOrNull(index)
                                    ?.matchesCombatFilter(accepting) ==
                                    true
                            }
                        locals = locals + (content.strings.value(instruction.stringIdOperand(1)) to (accepted ?: 0))
                    }
                CompiledOpcode.ConsumeActiveItem ->
                    false.also {
                        activeInventorySlot?.let { slot ->
                            encounter =
                                encounter.copy(runtime = encounter.runtime.withParty(encounter.runtime.party.withInventoryItem(slot, null)))
                        }
                    }
                CompiledOpcode.ClearInventorySlot ->
                    false.also {
                        val slot = int(instruction.intOperand(0))
                        encounter =
                            encounter.copy(runtime = encounter.runtime.withParty(encounter.runtime.party.withInventoryItem(slot, null)))
                    }
                CompiledOpcode.ConsumeInventoryPool ->
                    false.also {
                        encounter =
                            encounter.copy(
                                runtime =
                                    encounter.runtime.withParty(
                                        encounter.runtime.party.consumeInventoryPool(
                                            instruction.handleOperand(0),
                                            int(instruction.intOperand(1)),
                                        ),
                                    ),
                            )
                    }
                CompiledOpcode.Break,
                CompiledOpcode.Stop,
                -> true
                else -> error("combat script opcode ${instruction.opcode.name} is not supported by standalone compiled battle")
            }

        private fun executeWithLocal(
            name: String,
            value: Int,
            instructions: List<CompiledInstruction>,
        ) {
            val previous = locals
            locals = locals + (name to value)
            execute(instructions)
            locals = previous
        }

        private fun int(expr: CompiledIntExpr): Int =
            when (expr) {
                is CompiledIntExpr.Literal -> expr.value
                is CompiledIntExpr.Local -> locals[content.strings.value(expr.nameString)] ?: 0
                is CompiledIntExpr.Binary -> int(expr.left).applyBinary(expr.op, int(expr.right))
                is CompiledIntExpr.If -> if (bool(expr.cond)) int(expr.thenValue) else int(expr.elseValue)
                is CompiledIntExpr.Random -> random(int(expr.lo), int(expr.hi))
                is CompiledIntExpr.MemberField -> readMemberField(int(expr.member), content.strings.value(expr.fieldString))
                is CompiledIntExpr.MonsterField -> readMonsterField(int(expr.monster), content.strings.value(expr.fieldString))
                is CompiledIntExpr.BattleFlag -> readBattleFlag(content.strings.value(expr.nameString))
                is CompiledIntExpr.BattleQuery -> readBattleQuery(content.strings.value(expr.nameString))
                is CompiledIntExpr.RealmScalar -> readRealmScalar(content.strings.value(expr.nameString))
                is CompiledIntExpr.PartyScalar -> readPartyScalar(content.strings.value(expr.nameString))
                is CompiledIntExpr.InventoryPoolCount -> encounter.runtime.party.countInventoryPool(expr.pool)
                is CompiledIntExpr.InventorySlotCost ->
                    content.itemRecord(encounter.runtime.party.inventoryItem(int(expr.slot)) ?: return 0)?.cost
                        ?: 0
                is CompiledIntExpr.NativeInt -> 0
            }

        private fun bool(expr: CompiledBoolExpr): Boolean =
            when (expr) {
                is CompiledBoolExpr.Cmp -> int(expr.left).compare(expr.op, int(expr.right))
                is CompiledBoolExpr.Not -> !bool(expr.expr)
                is CompiledBoolExpr.And -> bool(expr.left) && bool(expr.right)
                is CompiledBoolExpr.Or -> bool(expr.left) || bool(expr.right)
                is CompiledBoolExpr.FlagIs -> expr.state in encounter.runtime.party.flags
                is CompiledBoolExpr.MilestoneAchieved -> expr.milestone in encounter.runtime.party.milestones
                is CompiledBoolExpr.MemberFlag -> readMemberFlag(int(expr.member), content.strings.value(expr.nameString))
                is CompiledBoolExpr.MonsterFlag -> readMonsterFlag(int(expr.monster), content.strings.value(expr.nameString))
                is CompiledBoolExpr.BattleFlag -> readBattleFlag(content.strings.value(expr.nameString)) != 0
                is CompiledBoolExpr.RuntimeFlag ->
                    content.strings.value(expr.nameString) == "in_dungeon" &&
                        encounter.runtime.currentMode == RuntimeMode.Dungeon
            }

        private fun random(
            lo: Int,
            hi: Int,
        ): Int {
            val draw = encounter.runtime.party.drawRandom(lo, hi)
            encounter = encounter.copy(runtime = encounter.runtime.withParty(draw.party))
            return draw.value
        }

        private fun read(target: CompiledLValue): Int =
            when (target) {
                is CompiledLValue.MemberField -> readMemberField(int(target.member), content.strings.value(target.fieldString))
                is CompiledLValue.MonsterField -> readMonsterField(int(target.monster), content.strings.value(target.fieldString))
                is CompiledLValue.BattleFlag -> readBattleFlag(content.strings.value(target.nameString))
                is CompiledLValue.PartyScalar -> readPartyScalar(content.strings.value(target.nameString))
                is CompiledLValue.RealmScalar -> readRealmScalar(content.strings.value(target.nameString))
            }

        private fun write(
            target: CompiledLValue,
            value: Int,
        ) {
            when (target) {
                is CompiledLValue.MemberField -> writeMemberField(int(target.member), content.strings.value(target.fieldString), value)
                is CompiledLValue.MonsterField -> writeMonsterField(int(target.monster), content.strings.value(target.fieldString), value)
                is CompiledLValue.BattleFlag -> writeBattleFlag(content.strings.value(target.nameString), value)
                is CompiledLValue.PartyScalar -> writePartyScalar(content.strings.value(target.nameString), value)
                is CompiledLValue.RealmScalar -> writeRealmScalar(content.strings.value(target.nameString), value)
            }
        }

        private fun readMemberField(
            index: Int,
            field: String,
        ): Int {
            val member =
                encounter.runtime.party.members
                    .getOrNull(index) ?: return 0
            return when (field) {
                "hit_points" -> member.hitPoints.current
                "health" -> member.health
                "magic" -> member.mana.current
                "magic_max" -> member.mana.max
                "level" -> member.level
                "wisdom" -> member.wisdom
                "charisma" -> member.charisma
                "weapon_skill" -> member.weaponSkill
                "class" -> member.pcClass.sourceId
                "experience" -> member.experience
                "dead" -> if (member.dead) 1 else 0
                else -> error("unknown compiled battle member field $field")
            }
        }

        private fun writeMemberField(
            index: Int,
            field: String,
            value: Int,
        ) {
            val member =
                encounter.runtime.party.members
                    .getOrNull(index) ?: return
            val next =
                when (field) {
                    "hit_points" -> member.copy(hitPoints = member.hitPoints.copy(current = value.coerceAtLeast(0)))
                    "health" ->
                        value.coerceAtLeast(0).let { health ->
                            member.copy(health = health, hitPoints = member.hitPoints.copy(max = health))
                        }
                    "magic" -> member.copy(mana = member.mana.copy(current = value.coerceAtLeast(0)))
                    "magic_max" -> value.coerceAtLeast(0).let { max -> member.copy(mana = member.mana.current.coerceAtMost(max) of max) }
                    "level" -> member.copy(level = value.coerceAtLeast(1))
                    "wisdom" -> member.copy(wisdom = value.coerceAtLeast(0))
                    "charisma" -> member.copy(charisma = value.coerceAtLeast(0))
                    "weapon_skill" -> member.copy(weaponSkill = value.coerceAtLeast(0))
                    "experience" -> member.copy(experience = value.coerceAtLeast(0))
                    "dead" -> member.copy(dead = value != 0)
                    else -> error("unknown compiled battle member field $field")
                }
            val updated = encounter.runtime.withMember(index, next)
            encounter =
                when (field) {
                    "dead" -> encounter.copy(runtime = updated.withParty(updated.party.withLivingCount()))
                    else -> encounter.copy(runtime = updated)
                }
        }

        private fun readMonsterField(
            index: Int,
            field: String,
        ): Int =
            when (field) {
                "hit_points" -> encounter.monsterHp.getOrElse(index) { 0 }
                "dead" -> if (encounter.monsterHp.getOrElse(index) { 0 } <= 0) 1 else 0
                else -> error("unknown compiled battle monster field $field")
            }

        private fun writeMonsterField(
            index: Int,
            field: String,
            value: Int,
        ) {
            encounter =
                when (field) {
                    "hit_points" ->
                        encounter.copy(
                            monsterHp =
                                encounter.monsterHp.mapIndexed { monster, hp ->
                                    hp.takeUnless {
                                        monster ==
                                            index
                                    }
                                        ?: value.coerceAtLeast(0)
                                },
                        )
                    "dead" ->
                        value
                            .takeIf { it != 0 }
                            ?.let {
                                encounter.copy(
                                    monsterHp =
                                        encounter.monsterHp.mapIndexed { monster, hp ->
                                            hp.takeUnless {
                                                monster ==
                                                    index
                                            }
                                                ?: 0
                                        },
                                )
                            }
                            ?: encounter
                    else -> error("unknown compiled battle monster field $field")
                }
        }

        private fun readBattleFlag(name: String): Int =
            when (name) {
                "monster_weapon_skill" -> encounter.monsterWeaponSkill
                "encounter_hit_point_max" -> encounter.hitPointMax
                "monster_confused" -> if (encounter.monsterConfused) 1 else 0
                "monsters_held" -> if (encounter.monstersHeld) 1 else 0
                "antimagic" -> if (encounter.antimagic) 1 else 0
                "party_held" -> if (encounter.partyHeld) 1 else 0
                "party_confused" -> if (encounter.partyConfused) 1 else 0
                "action_retry" -> if (encounter.actionRetry) 1 else 0
                else -> error("unknown compiled battle flag $name")
            }

        private fun writeBattleFlag(
            name: String,
            value: Int,
        ) {
            encounter =
                when (name) {
                    "monster_weapon_skill" -> encounter.copy(monsterWeaponSkill = value.coerceAtLeast(0))
                    "monster_confused" -> encounter.copy(monsterConfused = value != 0)
                    "monsters_held" -> encounter.copy(monstersHeld = value != 0)
                    "antimagic" -> encounter.copy(antimagic = value != 0)
                    "party_held" -> encounter.copy(partyHeld = value != 0)
                    "party_confused" -> encounter.copy(partyConfused = value != 0)
                    "action_retry" -> encounter.copy(actionRetry = value != 0)
                    else -> error("unknown compiled battle flag $name")
                }
        }

        private fun readBattleQuery(name: String): Int =
            when (name) {
                "first_alive_monster" -> encounter.monsterHp.indexOfFirst { hp -> hp > 0 }
                "random_alive_member" -> randomAliveMember()
                else -> error("unknown compiled battle query $name")
            }

        private fun randomAliveMember(): Int {
            val alive =
                encounter.runtime.party.members
                    .mapIndexedNotNull { index, member -> index.takeUnless { member.dead } }
            if (alive.isEmpty()) return 0
            val draw = encounter.runtime.party.drawRandom(0, alive.lastIndex)
            encounter = encounter.copy(runtime = encounter.runtime.withParty(draw.party))
            return alive[draw.value]
        }

        private fun readMemberFlag(
            index: Int,
            name: String,
        ): Boolean {
            val member =
                encounter.runtime.party.members
                    .getOrNull(index) ?: return name == "dead"
            return when (name) {
                "dead" -> member.dead
                "spellcaster" -> !member.dead && member.pcClass in setOf(PcClass.Mage, PcClass.Priest)
                else -> error("unknown compiled battle member flag $name")
            }
        }

        private fun readMonsterFlag(
            index: Int,
            name: String,
        ): Boolean =
            when (name) {
                "dead" -> encounter.monsterHp.getOrElse(index) { 0 } <= 0
                else -> error("unknown compiled battle monster flag $name")
            }

        private fun readPartyScalar(name: String): Int =
            when (name) {
                "gold" -> encounter.runtime.party.resources.gold
                "member_count" ->
                    encounter.runtime.party.counters.memberCount
                        ?: encounter.runtime.party.members
                            .count { member -> !member.dead }
                else -> error("compiled battle scripts cannot read party scalar $name yet")
            }

        private fun writePartyScalar(
            name: String,
            value: Int,
        ) {
            encounter =
                when (name) {
                    "gold" -> encounter.copy(runtime = encounter.runtime.withParty(encounter.runtime.party.withGold(value)))
                    "member_count" ->
                        encounter.copy(
                            runtime =
                                encounter.runtime.withParty(
                                    encounter.runtime.party.copy(
                                        counters =
                                            encounter.runtime.party.counters
                                                .copy(memberCount = value.coerceAtLeast(0)),
                                    ),
                                ),
                        )
                    else -> error("compiled battle scripts cannot write party scalar $name yet")
                }
        }

        private fun readRealmScalar(name: String): Int =
            when (name) {
                "party_member_max" -> encounter.runtime.partyMemberMax
                else -> error("compiled battle scripts cannot read realm scalar $name yet")
            }

        private fun writeRealmScalar(
            name: String,
            value: Int,
        ): Unit = error("compiled battle scripts cannot write realm scalar $name to $value yet")

        private fun CompiledTextBlock.renderScriptLines(): List<String> =
            renderStandaloneLines(
                content = content,
                intValue = { expr -> int(expr).toString() },
                stringValue = ::string,
            )

        private fun string(expr: CompiledStringExpr): String =
            when (expr) {
                is CompiledStringExpr.NativeString ->
                    when (content.strings.value(expr.pathString)) {
                        NativeAllowlist.partyMemberName.path ->
                            encounter.runtime.party.members
                                .getOrNull(expr.args.single().stringArgInt())
                                ?.let { member -> content.strings.value(member.nameString) }
                                .orEmpty()
                        NativeAllowlist.combatMonsterName.path -> encounter.monsterName()
                        NativeAllowlist.partyPronounSubject.path -> pronoun(expr.args.single().stringArgInt(), PronounCase.Subject)
                        NativeAllowlist.partyPronounObject.path -> pronoun(expr.args.single().stringArgInt(), PronounCase.Object)
                        NativeAllowlist.partyPronounPossessive.path -> pronoun(expr.args.single().stringArgInt(), PronounCase.Possessive)
                        else -> "{${content.strings.value(expr.pathString)}}"
                    }
            }

        private fun CompiledNativeStringArg.stringArgInt(): Int =
            when (this) {
                is CompiledNativeStringArg.IntValue -> int(value)
                is CompiledNativeStringArg.Literal -> content.strings.value(string).toIntOrNull() ?: 0
            }

        private fun pronoun(
            memberIndex: Int,
            case: PronounCase,
        ): String =
            encounter.runtime.party.members
                .getOrNull(memberIndex)
                ?.gender
                .orDefault()
                .pronoun(case)

        private fun Gender?.orDefault(): Gender = this ?: Gender.Male

        private fun CompiledViewCommand.toRuntimeViewCommand(): CompiledRuntimeViewCommand =
            when (this) {
                CompiledViewCommand.ClearText -> CompiledRuntimeViewCommand.ClearText
                CompiledViewCommand.ClearMapWindow -> CompiledRuntimeViewCommand.ClearMapWindow
                CompiledViewCommand.RenderMap -> CompiledRuntimeViewCommand.RenderMap
                CompiledViewCommand.StatusLine -> CompiledRuntimeViewCommand.StatusLine
                is CompiledViewCommand.ShowImage -> CompiledRuntimeViewCommand.ShowImage(content.slug(image))
            }

        private fun Int.applyBinary(
            op: CompiledBinaryOp,
            right: Int,
        ): Int =
            when (op) {
                CompiledBinaryOp.Add -> this + right
                CompiledBinaryOp.Sub -> this - right
                CompiledBinaryOp.Mul -> this * right
                CompiledBinaryOp.Div -> this / (right.takeUnless { it == 0 } ?: 1)
                CompiledBinaryOp.Mod -> this % (right.takeUnless { it == 0 } ?: 1)
            }

        private fun Int.compare(
            op: CompiledComparisonOp,
            right: Int,
        ): Boolean =
            when (op) {
                CompiledComparisonOp.Eq -> this == right
                CompiledComparisonOp.Ne -> this != right
                CompiledComparisonOp.Lt -> this < right
                CompiledComparisonOp.Lte -> this <= right
                CompiledComparisonOp.Gt -> this > right
                CompiledComparisonOp.Gte -> this >= right
            }

        private fun CompiledInstruction.textBlockOperand(index: Int): CompiledTextBlock =
            (operands[index] as CompiledOperand.TextBlockValue).value

        private fun CompiledInstruction.intOperand(index: Int): CompiledIntExpr = (operands[index] as CompiledOperand.IntExprValue).value

        private fun CompiledInstruction.boolOperand(index: Int): CompiledBoolExpr = (operands[index] as CompiledOperand.BoolExprValue).value

        private fun CompiledInstruction.lvalueOperand(index: Int): CompiledLValue = (operands[index] as CompiledOperand.LValueValue).value

        private fun CompiledInstruction.viewOperand(index: Int): CompiledViewCommand =
            (operands[index] as CompiledOperand.ViewCommandValue).value

        private fun CompiledInstruction.handleOperand(index: Int): CompiledHandleRef = (operands[index] as CompiledOperand.Handle).ref

        private fun CompiledInstruction.stringIdOperand(index: Int): Int = (operands[index] as CompiledOperand.StringId).id

        private fun CompiledInstruction.booleanOperand(index: Int): Boolean = (operands[index] as CompiledOperand.BooleanValue).value

        private fun CompiledInstruction.instructionsOperand(index: Int): List<CompiledInstruction> =
            (operands[index] as CompiledOperand.Instructions).instructions

        private fun CompiledInstruction.enumOperand(index: Int): String = (operands[index] as CompiledOperand.EnumName).name
    }

    private inner class StandaloneFormulaEvaluator(
        private val initialParty: StandaloneParty,
        private val bindings: Map<String, Int>,
    ) {
        private var party = initialParty

        fun eval(expr: CompiledIntExpr): FormulaResult {
            val value = int(expr)
            return FormulaResult(value, party)
        }

        private fun int(expr: CompiledIntExpr): Int =
            when (expr) {
                is CompiledIntExpr.Literal -> expr.value
                is CompiledIntExpr.Local -> bindings[content.strings.value(expr.nameString)] ?: 0
                is CompiledIntExpr.Binary -> int(expr.left).applyBinary(expr.op, int(expr.right))
                is CompiledIntExpr.If ->
                    when {
                        bool(expr.cond) -> int(expr.thenValue)
                        else -> int(expr.elseValue)
                    }
                is CompiledIntExpr.Random -> random(int(expr.lo), int(expr.hi))
                is CompiledIntExpr.MemberField -> memberField(int(expr.member), content.strings.value(expr.fieldString))
                is CompiledIntExpr.MonsterField,
                is CompiledIntExpr.BattleFlag,
                is CompiledIntExpr.BattleQuery,
                is CompiledIntExpr.RealmScalar,
                is CompiledIntExpr.PartyScalar,
                is CompiledIntExpr.InventoryPoolCount,
                is CompiledIntExpr.InventorySlotCost,
                is CompiledIntExpr.NativeInt,
                -> 0
            }

        private fun bool(expr: CompiledBoolExpr): Boolean =
            when (expr) {
                is CompiledBoolExpr.Cmp -> int(expr.left).compare(expr.op, int(expr.right))
                is CompiledBoolExpr.Not -> !bool(expr.expr)
                is CompiledBoolExpr.And -> bool(expr.left) && bool(expr.right)
                is CompiledBoolExpr.Or -> bool(expr.left) || bool(expr.right)
                is CompiledBoolExpr.FlagIs -> expr.state in party.flags
                is CompiledBoolExpr.MilestoneAchieved -> expr.milestone in party.milestones
                is CompiledBoolExpr.MemberFlag,
                is CompiledBoolExpr.MonsterFlag,
                is CompiledBoolExpr.BattleFlag,
                is CompiledBoolExpr.RuntimeFlag,
                -> false
            }

        private fun random(
            lo: Int,
            hi: Int,
        ): Int {
            val draw = party.drawRandom(lo, hi)
            party = draw.party
            return draw.value
        }

        private fun memberField(
            index: Int,
            field: String,
        ): Int {
            val member = party.members.getOrNull(index) ?: return 0
            return when (field) {
                "level" -> member.level
                "health" -> member.health
                "hit_points" -> member.hitPoints.current
                "hit_point_max" -> member.hitPoints.max
                "weapon_skill" -> member.weaponSkill
                "wisdom" -> member.wisdom
                "mana" -> member.mana.current
                "mana_max" -> member.mana.max
                "charisma" -> member.charisma
                "experience" -> member.experience
                "dead" -> if (member.dead) 1 else 0
                else -> 0
            }
        }

        private fun Int.applyBinary(
            op: CompiledBinaryOp,
            right: Int,
        ): Int =
            when (op) {
                CompiledBinaryOp.Add -> this + right
                CompiledBinaryOp.Sub -> this - right
                CompiledBinaryOp.Mul -> this * right
                CompiledBinaryOp.Div -> this / (right.takeUnless { it == 0 } ?: 1)
                CompiledBinaryOp.Mod -> this % (right.takeUnless { it == 0 } ?: 1)
            }

        private fun Int.compare(
            op: CompiledComparisonOp,
            right: Int,
        ): Boolean =
            when (op) {
                CompiledComparisonOp.Eq -> this == right
                CompiledComparisonOp.Ne -> this != right
                CompiledComparisonOp.Lt -> this < right
                CompiledComparisonOp.Lte -> this <= right
                CompiledComparisonOp.Gt -> this > right
                CompiledComparisonOp.Gte -> this >= right
            }
    }
}

private data class StandaloneBattleEncounter(
    val runtime: StandaloneRuntimeState,
    val returnMode: RuntimeMode,
    val returnTown: CompiledHandleRef?,
    val rules: ExternalStandardBattle,
    val monster: CompiledMonster,
    val monsterCount: Int,
    val monsterHp: List<Int>,
    val hitPointMax: Int,
    val monsterWeaponSkill: Int,
    val defendCount: Int = 0,
    val monsterConfused: Boolean = false,
    val monstersHeld: Boolean = false,
    val antimagic: Boolean = false,
    val partyHeld: Boolean = false,
    val partyConfused: Boolean = false,
    val actionRetry: Boolean = false,
    val bannerIndex: Int? = null,
) {
    fun runtimeAfterBattle(party: StandaloneParty = runtime.party): StandaloneRuntimeState =
        runtime.withParty(party).copy(currentMode = returnMode, currentTown = returnTown)
}

private data class BattleCastDecision(
    val value: Boolean,
    val encounter: StandaloneBattleEncounter,
)

private data class BattleLootSelection(
    val party: StandaloneParty,
    val item: CompiledHandleRef?,
)

private data class BattleLevelUps(
    val party: StandaloneParty,
    val gains: List<BattleLevelGain> = emptyList(),
)

private data class BattleLevelGain(
    val memberName: String,
    val levels: Int,
)

private data class FormulaResult(
    val value: Int,
    val party: StandaloneParty,
)
