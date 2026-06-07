/**
 * Bundle-only standard battle runner for the runtime.
 *
 * The runner consumes `runtimeSystems.combat.standardBattle` plus compiled
 * monster, formula, reward, item, and party rows. It deliberately does not
 * import the old Kotlin battle session: game text is rendered from authored
 * runtime-system text blocks, while this file supplies only the generic combat
 * mechanics required to mutate the runtime party state.
 *
 * The battle round is a runtime service loop, not an authored script-control
 * loop. Individual combat item/spell effects may run compiled script loops, but
 * round cadence, targeting, rendering, monster turns, rewards, and game-over
 * boundaries are host responsibilities fed by compiled rows.
 */

package rylween.runtime

import rylween.compiled.CompiledBoolExpr
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
import rylween.compiled.ExternalBattlePacing
import rylween.compiled.ExternalBattleText
import rylween.compiled.ExternalCombatAction
import rylween.compiled.ExternalLevelingRules
import rylween.compiled.ExternalRuntimeCombat
import rylween.compiled.ExternalRuntimeTextBlock
import rylween.compiled.ExternalStandardBattle
import rylween.compiled.applyCompiledBinary
import rylween.compiled.compareCompiled
import rylween.dsl.MemberChoiceFilter
import rylween.dsl.NativeAllowlist
import rylween.dsl.PartyMemberSelection
import rylween.dsl.PronounCase
import rylween.dsl.impl.CombatActionKind
import rylween.dsl.impl.CombatActionRequirement
import rylween.dsl.impl.Gender
import rylween.dsl.impl.ItemConsumePolicy
import rylween.dsl.impl.ItemKind
import rylween.dsl.impl.ItemUseContext
import rylween.dsl.impl.PcClass
import rylween.dsl.impl.RuntimeMode
import rylween.dsl.impl.of
import rylween.dsl.impl.sourceId
import rylween.dsl.pronoun

private const val RANDOM_PERCENT_MAX = 99
private const val BATTLE_SPELL_CANCEL_KEY = 'N'

private const val BATTLE_FOE_DISPLAY_NAME = "battleFoeDisplayName"
private const val BATTLE_ATTACKER_NAME = "battleAttackerName"
private const val BATTLE_TARGET_NAME = "battleTargetName"
private const val BATTLE_DAMAGE = "battleDamage"
private const val BATTLE_EXPERIENCE = "battleExperience"
private const val BATTLE_GOLD = "battleGold"
private const val BATTLE_LOOT_NAME = "battleLootName"
private const val BATTLE_LEVEL_MEMBER_NAME = "battleLevelMemberName"
private const val BATTLE_LEVELS_GAINED = "battleLevelsGained"
private const val BATTLE_MESSAGE_PAGE_LINES = 4
private const val BATTLE_VICTORY_PAGE_LINES = 7

/** Result of a battle handoff. */
internal data class BattleResult(
    val runtime: RuntimeState,
    val messages: List<String> = emptyList(),
)

/** Runs standard battles from compiled bundle/runtime-system rows only. */
internal class BattleRunner(
    private val content: CompiledContentBundle,
    private val combat: ExternalRuntimeCombat,
    private val pacing: ExternalBattlePacing,
    private val input: RuntimeInputPort,
    private val screen: RuntimeScreenPort,
    private val log: RuntimeLog,
    private val pausePrompts: PausePrompts? = null,
) {
    private val battle: ExternalStandardBattle? = combat.standardBattle

    fun runTier(
        runtime: RuntimeState,
        tier: String,
    ): BattleResult {
        val candidates = content.monsters.filter { monster -> monster.tier?.let(content::slug) == tier }
        if (candidates.isEmpty()) {
            return BattleResult(
                runtime,
                runtimeDiagnosticText("No compiled monsters for tier $tier."),
            )
        }
        val pick = runtime.party.drawRandom(0, candidates.lastIndex)
        log.rngDraw(
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
        runtime: RuntimeState,
        monsterRef: CompiledHandleRef,
        mandatory: Boolean = false,
    ): BattleResult {
        val monster =
            content.monsters.firstOrNull { current -> current.id == monsterRef }
                ?: return BattleResult(
                    runtime,
                    runtimeDiagnosticText("Missing compiled monster ${content.slug(monsterRef)}."),
                )
        return runMonster(runtime, monster, mandatory)
    }

    private fun runMonster(
        runtime: RuntimeState,
        monster: CompiledMonster,
        mandatory: Boolean = false,
    ): BattleResult {
        val rules =
            battle ?: return BattleResult(
                runtime,
                runtimeDiagnosticText("Missing compiled standard-battle contract."),
            )
        val started = startEncounter(runtime, rules, monster, mandatory)
        started.openingImage()?.let { image ->
            screen.writeViewCommand(RuntimeViewCommand.ClearMapWindow)
            screen.writeViewCommand(RuntimeViewCommand.ShowImage(image))
        }
        screen.updateStatus(started.runtime)
        while (true) {
            screen.writeText(rules.openingText(started))
            val choice = input.readCommand() ?: return BattleResult(runtime)
            when {
                rules.openingKeys.run.matches(choice) -> {
                    val outcome = attemptRun(started)
                    return if (outcome.escaped) {
                        BattleResult(outcome.encounter.runtimeAfterBattle(), outcome.messages)
                    } else {
                        finishFight(outcome.encounter, outcome.messages)
                    }
                }

                rules.openingKeys.fight.matches(choice) -> return finishFight(
                    started,
                    rules.text.bloodFlow.renderBattle(),
                )

                else -> screen.writeText(rules.text.notPossible.renderBattle())
            }
        }
    }

    private fun attemptRun(encounter: BattleEncounter): BattleRunOutcome {
        val rules = encounter.rules
        if (encounter.mandatory) {
            return BattleRunOutcome(
                encounter = encounter,
                escaped = false,
                messages = rules.text.runFailAway.renderBattle(),
            )
        }
        if (encounter.runtime.alwaysRunFromNonMandatoryEncounters) {
            log.event("battle.flee_secret_always user=${encounter.runtime.activeUserId.orEmpty()}")
            return BattleRunOutcome(
                encounter = encounter,
                escaped = true,
                messages = rules.text.runEscaped.renderBattle(),
            )
        }
        val draw = encounter.runtime.party.drawRandom(0, RANDOM_PERCENT_MAX)
        val escaped = draw.value < rules.opening.fleeChancePercent
        log.rngDraw(
            context = "battle.flee",
            lo = 0,
            hi = RANDOM_PERCENT_MAX,
            value = draw.value,
            hit = escaped,
            details = mapOf("threshold_lt" to rules.opening.fleeChancePercent),
        )
        val runtime = encounter.runtime.withParty(draw.party)
        return if (escaped) {
            BattleRunOutcome(
                encounter = encounter.copy(runtime = runtime),
                escaped = true,
                messages = rules.text.runEscaped.renderBattle(),
            )
        } else {
            BattleRunOutcome(
                encounter = encounter.copy(runtime = runtime.withMode(RuntimeMode.Battle)),
                escaped = false,
                messages = rules.text.runFailAway.renderBattle(),
            )
        }
    }

    private fun finishFight(
        initial: BattleEncounter,
        initialMessages: List<String>,
    ): BattleResult {
        var encounter = initial
        if (initialMessages.isNotEmpty()) {
            showBattleMessages(initialMessages, delayMs = pacing.openingPauseMs)
            screen.writeViewCommand(RuntimeViewCommand.ClearText)
        }
        var round = 0
        while (round < encounter.rules.roundLimit && encounter.partyHasLivingMembers() && encounter.monsterHasLivingMembers()) {
            if (!encounter.partyHeld) {
                encounter = encounter.playerRound()
            }
            if (encounter.monsterHasLivingMembers()) {
                encounter = encounter.monsterRound()
            }
            round++
            if (round < encounter.rules.roundLimit && encounter.partyHasLivingMembers() && encounter.monsterHasLivingMembers()) {
                val decision = encounter.nextRoundDecision(round + 1) ?: return BattleResult(encounter.runtime)
                if (decision.escaped) {
                    if (decision.messages.isNotEmpty()) {
                        showBattleMessages(decision.messages, delayMs = pacing.openingPauseMs)
                    }
                    return BattleResult(decision.encounter.runtimeAfterBattle())
                }
                encounter = decision.encounter
                if (decision.messages.isNotEmpty()) {
                    showBattleMessages(decision.messages, delayMs = pacing.openingPauseMs)
                    screen.writeViewCommand(RuntimeViewCommand.ClearText)
                }
            }
        }
        val finished = encounter.finish()
        showBattleMessagesUntilReturn(
            messages = finished.messages,
            pageLines = BATTLE_VICTORY_PAGE_LINES,
        )
        return BattleResult(finished.runtime)
    }

    private fun showBattleMessages(
        messages: List<String>,
        pageLines: Int = BATTLE_MESSAGE_PAGE_LINES,
        delayMs: Int = pacing.messagePauseMs,
    ) {
        if (messages.isEmpty()) return
        messages.chunked(pageLines).forEach { page ->
            screen.writeText(page)
            input.pause(delayMs)
        }
    }

    /**
     * Shows post-battle pages as blocking reward/summary screens.
     *
     * Ordinary combat narration can be timed, but the victory summary contains
     * rewards, loot, and level-up information that the player must be able to
     * read before the runtime returns to the previous exploration mode.
     */
    private fun showBattleMessagesUntilReturn(
        messages: List<String>,
        pageLines: Int,
    ) {
        if (messages.isEmpty()) return
        messages.chunked(pageLines).forEach { page ->
            val lines = page.withReturnPrompt()
            screen.writeText(lines)
            waitForReturn(lines)
        }
    }

    /** Adds the authored return prompt while preserving normal text wrapping. */
    private fun List<String>.withReturnPrompt(): List<String> =
        pausePrompts?.let { prompts -> withPausePrompt(prompts) }
            ?: (dropLastWhile(String::isBlank) + "" + "RETURN")

    /** Ignores stray keys on post-battle pages until RETURN/ENTER is pressed. */
    private fun waitForReturn(lines: List<String>) {
        var choice = input.readCommand() ?: return
        while (choice != '\n') {
            if (choice == '!') screen.redrawCurrentView(lines)
            choice = input.readCommand() ?: return
        }
    }

    private fun ExternalStandardBattle.openingText(encounter: BattleEncounter): List<String> =
        text.encounterBanner.renderBattle() +
            text.youFace.renderBattle() +
            text.foeDisplayName.renderBattle(BATTLE_FOE_DISPLAY_NAME to encounter.foeDisplayName()) +
            text.fight.renderBattle() +
            text.run.renderBattle()

    private fun BattleEncounter.nextRoundDecision(roundNumber: Int): BattleRunOutcome? {
        while (true) {
            val lines = rules.nextRoundText(this, roundNumber)
            screen.writeText(lines)
            val choice = input.readCommand() ?: return null
            when {
                rules.openingKeys.fight.matches(choice) ->
                    return BattleRunOutcome(
                        encounter = this,
                        escaped = false,
                        messages = emptyList(),
                    )

                rules.openingKeys.run.matches(choice) -> return attemptRun(this)

                else -> screen.writeText(rules.text.notPossible.renderBattle())
            }
        }
    }

    private fun ExternalStandardBattle.nextRoundText(
        encounter: BattleEncounter,
        roundNumber: Int,
    ): List<String> =
        listOf("Round $roundNumber", "") +
            text.stillFace.renderBattle() +
            text.foeDisplayName.renderBattle(BATTLE_FOE_DISPLAY_NAME to encounter.foeDisplayName()) +
            text.fight.renderBattle() +
            text.run.renderBattle()

    private fun BattleEncounter.playerRound(): BattleEncounter {
        var next = this
        runtime.party.members.forEachIndexed { index, member ->
            if (!member.dead && next.monsterHasLivingMembers()) {
                var retry = true
                var attempts = 0
                while (retry && attempts < rules.roundLimit && next.monsterHasLivingMembers()) {
                    val messages = mutableListOf<String>()
                    next = next.playerAction(index, member, messages)
                    retry = next.actionRetry
                    next = next.copy(actionRetry = false)
                    screen.updateStatus(next.runtime)
                    showBattleMessages(messages)
                    attempts++
                }
            }
        }
        return next
    }

    private fun BattleEncounter.playerAction(
        memberIndex: Int,
        member: CompiledPartyMember,
        messages: MutableList<String>,
    ): BattleEncounter {
        val actions = rules.actions.filter { action -> action.availableTo(member) }
        val prompt = rules.text.playerActionPrompt(member, actions)
        screen.writeText(prompt)
        val command = input.readCommand()
        val action = command?.let { key -> actions.firstOrNull { current -> current.binding.matches(key) } }
        if (action == null) {
            messages += rules.text.notPossible.renderBattle()
            return copy(actionRetry = true)
        }
        return when (action.kind) {
            CombatActionKind.Attack -> attack(member, messages)
            CombatActionKind.Defend -> defend(memberIndex, member, messages)
            CombatActionKind.BattleCry -> battleCry(messages)
            CombatActionKind.UseMagicItem -> useMagicItem(memberIndex, messages)
            CombatActionKind.CastMageSpell -> castMageSpell(memberIndex, member, messages)
        }
    }

    private fun BattleEncounter.attack(
        member: CompiledPartyMember,
        messages: MutableList<String>,
    ): BattleEncounter {
        val targetIndex = selectMonsterTarget() ?: return copy(actionRetry = true)
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
        val nextHp =
            monsterHp.mapIndexed { index, hp ->
                hp.takeUnless { index == targetIndex } ?: (hp - damage).coerceAtLeast(0)
            }
        val killed = monsterHp[targetIndex] > 0 && nextHp[targetIndex] == 0
        messages +=
            rules.text.playerHit.renderBattle(
                BATTLE_ATTACKER_NAME to content.strings.value(member.nameString),
                BATTLE_DAMAGE to damage.toString(),
            )
        if (killed) messages += rules.text.killed.renderBattle()
        return copy(runtime = runtime.withParty(damageDraw.party), monsterHp = nextHp)
    }

    private fun BattleEncounter.selectMonsterTarget(): Int? {
        val lines = rules.text.targetPrompt.renderBattle() + monsterTargetLines()
        screen.writeText(lines)
        return input
            .readValidChoice(screen, lines) { choice -> selectedMonsterTargetIndex(choice) != null }
            ?.let { choice -> selectedMonsterTargetIndex(choice) }
    }

    private fun BattleEncounter.defend(
        memberIndex: Int,
        member: CompiledPartyMember,
        messages: MutableList<String>,
    ): BattleEncounter {
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

    private fun BattleEncounter.battleCry(messages: MutableList<String>): BattleEncounter {
        val lines = rules.text.battleCry.renderBattle()
        if (lines.size > 1) {
            screen.writeText(lines.take(1))
            input.pause(pacing.battleCryPauseMs)
            screen.appendText(listOf("") + lines.drop(1))
            input.pause(pacing.messagePauseMs)
        } else {
            messages += lines
        }
        return copy(
            monsterWeaponSkill =
                (monsterWeaponSkill - rules.playerRound.battleCry.weaponSkillPenalty).coerceAtLeast(
                    0,
                ),
        )
    }

    private fun BattleEncounter.useMagicItem(
        memberIndex: Int,
        messages: MutableList<String>,
    ): BattleEncounter {
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
                ItemConsumePolicy.BeforeUse ->
                    copy(
                        runtime = runtime.withParty(runtime.party.withInventoryItem(slot, null)),
                    )

                ItemConsumePolicy.ScriptControlled,
                ItemConsumePolicy.NeverConsumes,
                -> this
            }
        return active.runBattleScript(
            scriptRoot = use.scriptRoot,
            locals = mapOf("caster_idx" to memberIndex, "inv_slot" to slot),
            activeInventorySlot = slot,
        )
    }

    private fun BattleEncounter.castMageSpell(
        memberIndex: Int,
        member: CompiledPartyMember,
        messages: MutableList<String>,
    ): BattleEncounter {
        val spells = mageSpells()
        screen.writeText(spellChoiceLines(member, spells))
        val command = input.readCommand()?.uppercaseChar() ?: return copy(actionRetry = true)
        if (command == BATTLE_SPELL_CANCEL_KEY) return copy(actionRetry = true)
        val slot = command.digitToIntOrNull() ?: return copy(actionRetry = true)
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
                locals = mapOf("caster_idx" to memberIndex),
            )
    }

    private fun BattleEncounter.monsterRound(): BattleEncounter {
        var next = copy(partyHeld = false, partyConfused = false, actionRetry = false)
        monsterHp.forEachIndexed { index, hp ->
            if (hp > 0 && next.partyHasLivingMembers() && !next.monstersHeld) {
                val messages = mutableListOf<String>()
                val cast = next.shouldMonsterCast()
                next = cast.encounter
                next =
                    when {
                        cast.value -> next.castMonsterSpell(index)
                        else -> next.monsterAttack(hp, messages)
                    }
                screen.updateStatus(next.runtime)
                showBattleMessages(messages)
            }
        }
        return next.afterMonsterRound()
    }

    private fun BattleEncounter.monsterAttack(
        monsterHitPoints: Int,
        messages: MutableList<String>,
    ): BattleEncounter {
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

    private fun BattleEncounter.shouldMonsterCast(): BattleCastDecision {
        val options = monsterSpells()
        if (antimagic || monster.spellPower <= 0 || options.isEmpty()) return BattleCastDecision(false, this)
        val chance =
            rules.monsterRound.spellChanceBasePercent + monster.spellPower / rules.monsterRound.spellPowerDivisor
        val draw = runtime.party.drawRandom(0, RANDOM_PERCENT_MAX)
        return BattleCastDecision(draw.value < chance, copy(runtime = runtime.withParty(draw.party)))
    }

    private fun BattleEncounter.castMonsterSpell(monsterIndex: Int): BattleEncounter {
        val options = monsterSpells()
        if (options.isEmpty()) return this
        val pick = runtime.party.drawRandom(0, options.lastIndex)
        return copy(runtime = runtime.withParty(pick.party))
            .runBattleScript(
                scriptRoot = options[pick.value].scriptRoot,
                locals = mapOf("caster_idx" to monsterIndex, "attacker_idx" to monsterIndex),
            )
    }

    private fun BattleEncounter.runBattleScript(
        scriptRoot: CompiledHandleRef,
        locals: Map<String, Int>,
        activeInventorySlot: Int? = null,
    ): BattleEncounter {
        val program = content.scripts.roots.firstOrNull { root -> root.rootHandle == scriptRoot }
        return program
            ?.let {
                CompiledCombatScriptRunner(this, activeInventorySlot).run(it.instructions, locals)
            }
            ?: this
    }

    private fun BattleEncounter.afterMonsterRound(): BattleEncounter =
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

    private fun BattleEncounter.finish(): BattleResult =
        when {
            !partyHasLivingMembers() -> {
                val defeated = runtime.copy(currentMode = RuntimeMode.GameOver)
                rules.defeatImage?.let { image ->
                    screen.writeViewCommand(RuntimeViewCommand.ClearMapWindow)
                    screen.writeViewCommand(RuntimeViewCommand.ShowImage(image))
                }
                screen.updateStatus(defeated)
                BattleResult(defeated, rules.text.partyDefeated.renderBattle())
            }

            monsterHasLivingMembers() ->
                BattleResult(
                    runtimeAfterBattle(),
                    rules.text.runFailAway.renderBattle(),
                )

            else -> victory()
        }

    private fun BattleEncounter.victory(): BattleResult {
        rules.victoryImage?.let { image ->
            screen.writeViewCommand(RuntimeViewCommand.ClearMapWindow)
            screen.writeViewCommand(RuntimeViewCommand.ShowImage(image))
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
        val experience =
            reward?.experience?.let { formula ->
                val result = evalFormula(formula, party, bindings)
                party = result.party
                result.value
            }
                ?: 0
        val gold =
            reward?.gold?.let { formula ->
                val result = evalFormula(formula, party, bindings)
                party = result.party
                result.value
            } ?: 0
        val loot =
            reward?.let { bundle ->
                val selection = selectLoot(party, bundle)
                party = selection.party
                selection.item
            }
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
        return BattleResult(runtimeAfterBattle(party), messages)
    }

    private fun startEncounter(
        runtime: RuntimeState,
        rules: ExternalStandardBattle,
        monster: CompiledMonster,
        mandatory: Boolean,
    ): BattleEncounter {
        var party = runtime.party
        val firstLevel = party.members.firstOrNull()?.level ?: 0
        val countMax = monster.count.last + (monster.countMaxLevelDivisor?.let { divisor -> firstLevel / divisor } ?: 0)
        val bannerDraw =
            rules.opening.banners
                .takeIf { banners -> banners.isNotEmpty() }
                ?.let { banners -> party.drawRandom(0, banners.lastIndex) }
        bannerDraw?.let { draw ->
            log.rngDraw(
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
        log.rngDraw(
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
                    log.rngDraw(
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
        return BattleEncounter(
            runtime = runtime.withParty(party).withMode(RuntimeMode.Battle),
            returnMode = runtime.currentMode,
            returnTown = runtime.currentTown,
            rules = rules,
            monster = monster,
            monsterCount = count,
            monsterHp = hitPoints,
            monsterHpMax = hitPoints,
            hitPointMax = hpRange.last,
            monsterWeaponSkill = monster.weaponSkill + firstLevel,
            mandatory = mandatory,
            bannerIndex = bannerDraw?.value,
        )
    }

    private fun scaledHitPointRange(
        monster: CompiledMonster,
        rules: ExternalStandardBattle,
        party: PartyState,
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

    private fun BattleEncounter.rewardBundle(): CompiledRewardBundle? =
        content
            .ref(CompiledHandleKind.RewardBundle, rules.rewards)
            .let { ref -> content.rewards.firstOrNull { reward -> reward.id == ref } }

    private fun selectLoot(
        party: PartyState,
        reward: CompiledRewardBundle,
    ): BattleLootSelection {
        val freeSlot = party.inventory.firstOrNull { slot -> slot.item == null }?.slot
        if (freeSlot == null) return BattleLootSelection(party, null)
        val drop = party.drawRandom(0, RANDOM_PERCENT_MAX)
        if (drop.value >= reward.loot.dropChancePercent) return BattleLootSelection(drop.party, null)
        val treasure = drop.party.drawRandom(0, RANDOM_PERCENT_MAX)
        val poolRef =
            reward.loot.treasure.takeIf { treasure.value < reward.loot.treasureChancePercent } ?: reward.loot.magic
        val pool =
            content.itemPools.firstOrNull { itemPool -> itemPool.id == poolRef } ?: return BattleLootSelection(
                treasure.party,
                null,
            )
        if (pool.items.isEmpty()) return BattleLootSelection(treasure.party, null)
        val item = treasure.party.drawRandom(0, pool.items.lastIndex)
        return BattleLootSelection(item.party, pool.items[item.value])
    }

    private fun PartyState.applyBattleRewards(
        experience: Int,
        gold: Int,
        loot: CompiledHandleRef?,
        defeatedCount: Int,
    ): PartyState {
        val survivorExperience =
            members.map { member ->
                member.takeIf { it.dead } ?: member.copy(experience = member.experience + experience)
            }
        val withCounters =
            copy(counters = counters.copy(kills = counters.kills + defeatedCount), members = survivorExperience)
        val withGold = withCounters.withGold(withCounters.resources.gold + gold)
        val freeSlot = withGold.inventory.firstOrNull { slot -> slot.item == null }?.slot
        return loot?.let { item -> freeSlot?.let { slot -> withGold.withInventoryItem(slot, item) } } ?: withGold
    }

    private fun PartyState.applyLevelUps(leveling: ExternalLevelingRules?): BattleLevelUps {
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
                    dexterity = member.dexterity + (growth?.dexterity ?: 0) * gained,
                    hitPoints = member.hitPoints.current.coerceAtLeast(hpFloor) of nextHealth,
                )
            }
        return BattleLevelUps(copy(members = nextMembers), gains.toList())
    }

    private fun evalFormula(
        formulaRef: CompiledHandleRef,
        party: PartyState,
        bindings: Map<String, Int>,
    ): FormulaResult {
        val formula = content.formulas.firstOrNull { current -> current.id == formulaRef }
        return evalFormula(
            checkNotNull(formula) { "missing compiled formula ${content.slug(formulaRef)}" },
            party,
            bindings,
        )
    }

    private fun evalFormula(
        formulaSlug: String,
        party: PartyState,
        bindings: Map<String, Int>,
    ): FormulaResult {
        val formulaRef = content.ref(CompiledHandleKind.Formula, formulaSlug)
        val formula = content.formulas.firstOrNull { current -> current.id == formulaRef }
        return evalFormula(checkNotNull(formula) { "missing compiled formula $formulaSlug" }, party, bindings)
    }

    private fun evalFormula(
        formula: CompiledFormula,
        party: PartyState,
        bindings: Map<String, Int>,
    ): FormulaResult = FormulaEvaluator(party, bindings).eval(formula.value)

    private fun CompiledPartyMember.weaponDamage(): Int = content.itemRecord(equipment.weapon)?.weaponDamage ?: 1

    private fun CompiledPartyMember.armorClass(): Int = content.itemRecord(equipment.armor)?.armorClass ?: 0

    private fun CompiledHandleRef.displayName(): String =
        content.itemRecord(this)?.let { item -> content.strings.value(item.displayNameString) } ?: content.slug(this)

    private fun CompiledContentBundle.itemRecord(item: CompiledHandleRef): CompiledItemRecord? =
        items.firstOrNull { record -> record.id == item }

    private fun PartyState.countInventoryPool(pool: CompiledHandleRef): Int {
        val pooledItems = itemPoolItems(pool)
        return inventory.count { slot -> slot.item in pooledItems }
    }

    private fun PartyState.consumeInventoryPool(
        pool: CompiledHandleRef,
        quantity: Int,
    ): PartyState {
        val pooledItems = itemPoolItems(pool)
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

    private fun itemPoolItems(pool: CompiledHandleRef): Set<CompiledHandleRef> =
        content.itemPools
            .firstOrNull { itemPool -> itemPool.id == pool }
            ?.items
            ?.toSet()
            .orEmpty()

    private fun PartyState.withLivingCount(): PartyState =
        copy(counters = counters.copy(memberCount = members.count { member -> !member.dead }))

    private fun CompiledPartyMember.matchesCombatFilter(filter: MemberChoiceFilter): Boolean =
        when (filter) {
            MemberChoiceFilter.Any -> true
            MemberChoiceFilter.AliveOnly -> !dead
            MemberChoiceFilter.DeadOnly -> dead
            MemberChoiceFilter.SpellcasterOnly -> !dead && pcClass in setOf(PcClass.Mage, PcClass.Priest)
        }

    private fun RuntimeState.withMode(mode: RuntimeMode): RuntimeState = copy(currentMode = mode, currentTown = null)

    private fun RuntimeState.withMember(
        index: Int,
        member: CompiledPartyMember,
    ): RuntimeState =
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

    private fun ExternalRuntimeTextBlock.renderBattle(values: Map<String, String> = emptyMap()): List<String> =
        render(values::get, screen.textColumns)

    private fun BattleEncounter.foeDisplayName(): String = "$monsterCount ${monsterName()}${"s".takeIf { monsterCount > 1 }.orEmpty()}"

    private fun BattleEncounter.monsterName(): String = content.strings.value(monster.nameString)

    private fun BattleEncounter.openingImage(): String? =
        monster.encounterImage?.let(content::slug) ?: rules.opening.banners.getOrNull(bannerIndex ?: -1)

    private fun BattleEncounter.partyHasLivingMembers(): Boolean = runtime.party.members.any { member -> !member.dead }

    private fun BattleEncounter.monsterHasLivingMembers(): Boolean = monsterHp.any { hp -> hp > 0 }

    private fun combatItemUse(item: CompiledHandleRef): CompiledItemUse? =
        content.itemUses.firstOrNull { use -> use.item == item && ItemUseContext.Combat in use.contexts }

    private fun mageSpells(): List<CompiledSpell> = spellsByPrefix("mage.")

    private fun monsterSpells(): List<CompiledSpell> =
        spellsByPrefix("monster.")
            .filter { spell -> content.slug(spell.id) != "monster.melee" }

    private fun spellsByPrefix(prefix: String): List<CompiledSpell> =
        buildList {
            content.spells.forEach { spell ->
                if (content.slug(spell.id).startsWith(prefix)) addBySlot(spell)
            }
        }

    private fun MutableList<CompiledSpell>.addBySlot(spell: CompiledSpell) {
        var index = 0
        while (index < size && this[index].slot <= spell.slot) index++
        add(index, spell)
    }

    private fun PartyState.inventoryChoiceLines(): List<String> =
        inventory
            .sortedInventorySlots()
            .map { slot ->
                val itemName =
                    slot.item
                        ?.let { item ->
                            content.itemRecord(item)?.let { record -> content.strings.value(record.displayNameString) }
                        }
                        ?: "empty"
                "${'A' + slot.slot - 1}. $itemName"
            }

    private fun List<rylween.compiled.CompiledInventorySlot>.sortedInventorySlots(): List<rylween.compiled.CompiledInventorySlot> =
        buildList {
            this@sortedInventorySlots.forEach { slot ->
                var index = 0
                while (index < size && this[index].slot <= slot.slot) index++
                add(index, slot)
            }
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

    private fun Char.monsterTargetIndex(): Int? = (uppercaseChar() - 'A').takeIf { index -> index >= 0 }

    private fun BattleEncounter.monsterTargetLines(): List<String> =
        buildList {
            monsterHp.forEachIndexed { index, hp ->
                val key = ('A'.code + index).toChar()
                val maxHp = monsterHpMax.getOrElse(index) { hitPointMax }
                if (hp > 0) {
                    add("$key. ${monsterName()} $hp/$maxHp HP")
                } else {
                    add("   ${monsterName()} (dead)")
                }
            }
        }

    private fun BattleEncounter.selectedMonsterTargetIndex(choice: Char): Int? =
        choice.monsterTargetIndex()?.takeIf { index -> monsterHp.getOrElse(index) { 0 } > 0 }

    private fun BattleEncounter.firstAliveMonsterIndex(): Int = monsterHp.indexOfFirst { hp -> hp > 0 }.coerceAtLeast(0)

    private inner class CompiledCombatScriptRunner(
        initialEncounter: BattleEncounter,
        private val activeInventorySlot: Int?,
    ) {
        private var encounter = initialEncounter
        private var locals = emptyMap<String, Int>()
        private var currentTextLines: List<String> = emptyList()

        fun run(
            instructions: List<CompiledInstruction>,
            initialLocals: Map<String, Int>,
        ): BattleEncounter {
            locals = initialLocals
            execute(instructions)
            return encounter
        }

        private fun writeText(lines: List<String>) {
            currentTextLines = lines
            screen.writeText(lines)
        }

        private fun execute(instructions: List<CompiledInstruction>) {
            instructions.forEach { instruction ->
                if (execute(instruction)) return
            }
        }

        private fun execute(instruction: CompiledInstruction): Boolean =
            when (instruction.opcode) {
                CompiledOpcode.Screen ->
                    false.also {
                        writeText(instruction.textBlockOperand(0).renderScriptLines())
                    }

                CompiledOpcode.Emit ->
                    false.also {
                        writeText(
                            runtimeDiagnosticText("[text:${content.slug(instruction.handleOperand(0))}]"),
                        )
                    }

                CompiledOpcode.WaitInput ->
                    false.also {
                        pausePrompts?.let { prompts -> screen.writeText(currentTextLines.withPausePrompt(prompts)) }
                        input.readCommand()
                    }
                CompiledOpcode.Pause ->
                    false.also {
                        input.pause((instruction.operands[0] as CompiledOperand.Integer).value)
                    }
                CompiledOpcode.View ->
                    false.also {
                        screen.writeViewCommand(
                            instruction.viewOperand(0).toRuntimeViewCommand(),
                        )
                    }

                CompiledOpcode.LetInt ->
                    false.also {
                        locals = locals + (
                            content.strings.value(instruction.stringIdOperand(0)) to
                                int(
                                    instruction.intOperand(1),
                                )
                        )
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

                CompiledOpcode.If ->
                    false.also {
                        execute(
                            instruction.instructionsOperand(
                                if (bool(
                                        instruction.boolOperand(
                                            0,
                                        ),
                                    )
                                ) {
                                    1
                                } else {
                                    2
                                },
                            ),
                        )
                    }

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
                        val lines = instruction.textBlockOperand(0).renderScriptLines() + encounter.monsterTargetLines()
                        screen.writeText(lines)
                        val target =
                            input
                                .readValidChoice(screen, lines) { choice -> encounter.selectedMonsterTargetIndex(choice) != null }
                                ?.let { choice -> encounter.selectedMonsterTargetIndex(choice) }
                        locals =
                            locals +
                            (
                                content.strings.value(instruction.stringIdOperand(1)) to
                                    (target ?: encounter.firstAliveMonsterIndex())
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
                            encounter = encounter.withClearedInventorySlot(slot)
                        }
                    }

                CompiledOpcode.ClearInventorySlot ->
                    false.also {
                        val slot = int(instruction.intOperand(0))
                        encounter = encounter.withClearedInventorySlot(slot)
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

                else -> error("combat script opcode ${instruction.opcode.name} is not supported by battle")
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
                is CompiledIntExpr.Binary -> int(expr.left).applyCompiledBinary(expr.op, int(expr.right))
                is CompiledIntExpr.If -> if (bool(expr.cond)) int(expr.thenValue) else int(expr.elseValue)
                is CompiledIntExpr.Random -> random(int(expr.lo), int(expr.hi))
                is CompiledIntExpr.MemberField ->
                    readMemberField(
                        int(expr.member),
                        content.strings.value(expr.fieldString),
                    )

                is CompiledIntExpr.MonsterField ->
                    readMonsterField(
                        int(expr.monster),
                        content.strings.value(expr.fieldString),
                    )

                is CompiledIntExpr.BattleFlag -> readBattleFlag(content.strings.value(expr.nameString))
                is CompiledIntExpr.BattleQuery -> readBattleQuery(content.strings.value(expr.nameString))
                is CompiledIntExpr.PartyMemberQuery -> readPartyMemberQuery(expr.selection, expr.filter)
                is CompiledIntExpr.RealmScalar -> readRealmScalar(content.strings.value(expr.nameString))
                is CompiledIntExpr.PartyScalar -> readPartyScalar(content.strings.value(expr.nameString))
                is CompiledIntExpr.InventoryPoolCount -> encounter.runtime.party.countInventoryPool(expr.pool)
                is CompiledIntExpr.InventorySlotCost ->
                    content.itemRecord(encounter.runtime.party.inventoryItem(int(expr.slot)) ?: return 0)?.cost
                        ?: 0
            }

        private fun bool(expr: CompiledBoolExpr): Boolean =
            when (expr) {
                is CompiledBoolExpr.Cmp -> int(expr.left).compareCompiled(expr.op, int(expr.right))
                is CompiledBoolExpr.Not -> !bool(expr.expr)
                is CompiledBoolExpr.And -> bool(expr.left) && bool(expr.right)
                is CompiledBoolExpr.Or -> bool(expr.left) || bool(expr.right)
                is CompiledBoolExpr.FlagIs -> expr.state in encounter.runtime.party.flags
                is CompiledBoolExpr.MilestoneAchieved -> expr.milestone in encounter.runtime.party.milestones
                is CompiledBoolExpr.MemberFlag ->
                    readMemberFlag(
                        int(expr.member),
                        content.strings.value(expr.nameString),
                    )

                is CompiledBoolExpr.MonsterFlag ->
                    readMonsterFlag(
                        int(expr.monster),
                        content.strings.value(expr.nameString),
                    )

                is CompiledBoolExpr.BattleFlag -> readBattleFlag(content.strings.value(expr.nameString)) != 0
                is CompiledBoolExpr.RuntimeFlag ->
                    when (content.strings.value(expr.nameString)) {
                        "in_dungeon" -> encounter.runtime.currentMode == RuntimeMode.Dungeon
                        "in_castle" -> encounter.runtime.currentMode == RuntimeMode.Castle
                        else -> false
                    }
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
                is CompiledLValue.MemberField ->
                    readMemberField(
                        int(target.member),
                        content.strings.value(target.fieldString),
                    )

                is CompiledLValue.MonsterField ->
                    readMonsterField(
                        int(target.monster),
                        content.strings.value(target.fieldString),
                    )

                is CompiledLValue.BattleFlag -> readBattleFlag(content.strings.value(target.nameString))
                is CompiledLValue.PartyScalar -> readPartyScalar(content.strings.value(target.nameString))
                is CompiledLValue.RealmScalar -> readRealmScalar(content.strings.value(target.nameString))
            }

        private fun write(
            target: CompiledLValue,
            value: Int,
        ) {
            when (target) {
                is CompiledLValue.MemberField ->
                    writeMemberField(
                        int(target.member),
                        content.strings.value(target.fieldString),
                        value,
                    )

                is CompiledLValue.MonsterField ->
                    writeMonsterField(
                        int(target.monster),
                        content.strings.value(target.fieldString),
                        value,
                    )

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
                "dexterity" -> member.dexterity
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
                    "magic_max" ->
                        value
                            .coerceAtLeast(0)
                            .let { max -> member.copy(mana = member.mana.current.coerceAtMost(max) of max) }

                    "level" -> member.copy(level = value.coerceAtLeast(1))
                    "wisdom" -> member.copy(wisdom = value.coerceAtLeast(0))
                    "charisma" -> member.copy(charisma = value.coerceAtLeast(0))
                    "weapon_skill" -> member.copy(weaponSkill = value.coerceAtLeast(0))
                    "dexterity" -> member.copy(dexterity = value.coerceAtLeast(0))
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
                        encounter.withMonsterHitPoints(index, value)

                    "dead" ->
                        value
                            .takeIf { it != 0 }
                            ?.let {
                                encounter.withMonsterHitPoints(index, 0)
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

        private fun readPartyMemberQuery(
            selection: PartyMemberSelection,
            filter: MemberChoiceFilter,
        ): Int {
            val matching =
                encounter.runtime.party
                    .memberChoices(filter)
                    .map { memberNumber -> memberNumber - 1 }
            if (matching.isEmpty()) return 0
            if (selection == PartyMemberSelection.First) return matching.first()
            val draw = encounter.runtime.party.drawRandom(0, matching.lastIndex)
            encounter = encounter.copy(runtime = encounter.runtime.withParty(draw.party))
            return matching[draw.value]
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
                    "gold" ->
                        encounter.copy(
                            runtime =
                                encounter.runtime.withParty(
                                    encounter.runtime.party.withGold(
                                        value,
                                    ),
                                ),
                        )

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
            renderTextLines(
                CompiledTextRenderContext(
                    content = content,
                    flowColumns = screen.textColumns,
                    intValue = { expr -> int(expr).toString() },
                    stringValue = ::string,
                ),
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
                        NativeAllowlist.partyPronounSubject.path ->
                            pronoun(
                                expr.args.single().stringArgInt(),
                                PronounCase.Subject,
                            )

                        NativeAllowlist.partyPronounObject.path ->
                            pronoun(
                                expr.args.single().stringArgInt(),
                                PronounCase.Object,
                            )

                        NativeAllowlist.partyPronounPossessive.path ->
                            pronoun(
                                expr.args.single().stringArgInt(),
                                PronounCase.Possessive,
                            )

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

        private fun CompiledViewCommand.toRuntimeViewCommand(): RuntimeViewCommand =
            when (this) {
                CompiledViewCommand.ClearText -> RuntimeViewCommand.ClearText
                CompiledViewCommand.ClearMapWindow -> RuntimeViewCommand.ClearMapWindow
                CompiledViewCommand.RenderMap -> RuntimeViewCommand.RenderMap
                CompiledViewCommand.StatusLine -> RuntimeViewCommand.StatusLine
                is CompiledViewCommand.ShowImage -> RuntimeViewCommand.ShowImage(content.slug(image))
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

    private inner class FormulaEvaluator(
        private var party: PartyState,
        private val bindings: Map<String, Int>,
    ) {
        fun eval(expr: CompiledIntExpr): FormulaResult {
            val value = int(expr)
            return FormulaResult(value, party)
        }

        private fun int(expr: CompiledIntExpr): Int =
            when (expr) {
                is CompiledIntExpr.Literal -> expr.value
                is CompiledIntExpr.Local -> bindings[content.strings.value(expr.nameString)] ?: 0
                is CompiledIntExpr.Binary -> int(expr.left).applyCompiledBinary(expr.op, int(expr.right))
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
                is CompiledIntExpr.PartyMemberQuery,
                is CompiledIntExpr.RealmScalar,
                is CompiledIntExpr.PartyScalar,
                is CompiledIntExpr.InventoryPoolCount,
                is CompiledIntExpr.InventorySlotCost,
                -> 0
            }

        private fun bool(expr: CompiledBoolExpr): Boolean =
            when (expr) {
                is CompiledBoolExpr.Cmp -> int(expr.left).compareCompiled(expr.op, int(expr.right))
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
                "dexterity" -> member.dexterity
                "experience" -> member.experience
                "dead" -> if (member.dead) 1 else 0
                else -> 0
            }
        }
    }
}

private fun BattleEncounter.withClearedInventorySlot(slot: Int): BattleEncounter =
    copy(runtime = runtime.withParty(runtime.party.withInventoryItem(slot, null)))

private fun BattleEncounter.withMonsterHitPoints(
    index: Int,
    hitPoints: Int,
): BattleEncounter =
    copy(
        monsterHp =
            monsterHp.mapIndexed { monsterIndex, current ->
                current.takeUnless { monsterIndex == index } ?: hitPoints.coerceAtLeast(0)
            },
        monsterHpMax =
            monsterHpMax.mapIndexed { monsterIndex, current ->
                current.takeUnless { monsterIndex == index } ?: maxOf(current, hitPoints)
            },
    )

private data class BattleEncounter(
    val runtime: RuntimeState,
    val returnMode: RuntimeMode,
    val returnTown: CompiledHandleRef?,
    val rules: ExternalStandardBattle,
    val monster: CompiledMonster,
    val monsterCount: Int,
    val monsterHp: List<Int>,
    val monsterHpMax: List<Int>,
    val hitPointMax: Int,
    val monsterWeaponSkill: Int,
    val mandatory: Boolean,
    val defendCount: Int = 0,
    val monsterConfused: Boolean = false,
    val monstersHeld: Boolean = false,
    val antimagic: Boolean = false,
    val partyHeld: Boolean = false,
    val partyConfused: Boolean = false,
    val actionRetry: Boolean = false,
    val bannerIndex: Int? = null,
) {
    fun runtimeAfterBattle(party: PartyState = runtime.party): RuntimeState =
        runtime.withParty(party).copy(currentMode = returnMode, currentTown = returnTown)
}

private data class BattleCastDecision(
    val value: Boolean,
    val encounter: BattleEncounter,
)

private data class BattleRunOutcome(
    val encounter: BattleEncounter,
    val escaped: Boolean,
    val messages: List<String>,
)

private data class BattleLootSelection(
    val party: PartyState,
    val item: CompiledHandleRef?,
)

private data class BattleLevelUps(
    val party: PartyState,
    val gains: List<BattleLevelGain> = emptyList(),
)

private data class BattleLevelGain(
    val memberName: String,
    val levels: Int,
)

private data class FormulaResult(
    val value: Int,
    val party: PartyState,
)
