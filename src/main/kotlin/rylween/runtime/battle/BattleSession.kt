/**
 * Backend-neutral battle session, combat scripting, rewards, and prompt helpers.
 *
 * Battle rules come from [rylween.dsl.CombatSystemModel], spell/item-use
 * scripts, formulas, rewards, monsters, and item records supplied by the game
 * model. Terminal rendering lives in
 * `ansi-runtime/src/main/kotlin/rylween/frontend/terminal/TerminalAdapters.kt`.
 */

package rylween.runtime.battle

import rylween.dsl.BattleTextModel
import rylween.dsl.CombatActionKind
import rylween.dsl.CombatActionModel
import rylween.dsl.CombatActionRequirement
import rylween.dsl.CombatSystemModel
import rylween.dsl.DslDefaultSpells
import rylween.dsl.EncounterTier
import rylween.dsl.FormulaId
import rylween.dsl.FormulaModel
import rylween.dsl.GameMilestoneId
import rylween.dsl.Gender
import rylween.dsl.ImageId
import rylween.dsl.InventorySaveSlotMax
import rylween.dsl.ItemConsumePolicy
import rylween.dsl.ItemId
import rylween.dsl.ItemKind
import rylween.dsl.ItemPoolId
import rylween.dsl.ItemRecordModel
import rylween.dsl.ItemUseContext
import rylween.dsl.ItemUseModel
import rylween.dsl.LValueAst
import rylween.dsl.LevelingModel
import rylween.dsl.MemberChoiceFilter
import rylween.dsl.MonsterId
import rylween.dsl.MonsterModel
import rylween.dsl.NativeAllowlist
import rylween.dsl.NativeStringArg
import rylween.dsl.Op
import rylween.dsl.PartyMemberState
import rylween.dsl.PartyState
import rylween.dsl.PcClass
import rylween.dsl.PronounCase
import rylween.dsl.RewardBundleModel
import rylween.dsl.RuntimeContent
import rylween.dsl.RuntimeMode
import rylween.dsl.SpellModel
import rylween.dsl.StandardBattleModel
import rylween.dsl.StrAst
import rylween.dsl.TextBlock
import rylween.dsl.TextPart
import rylween.dsl.ViewCommand
import rylween.dsl.Wilderness
import rylween.dsl.chance
import rylween.dsl.combineTextBlocks
import rylween.dsl.fallbackText
import rylween.dsl.formula
import rylween.dsl.image
import rylween.dsl.inlineText
import rylween.dsl.item
import rylween.dsl.line
import rylween.dsl.lines
import rylween.dsl.milestone
import rylween.dsl.monster
import rylween.dsl.monsterName
import rylween.dsl.of
import rylween.dsl.pronoun
import rylween.dsl.text
import rylween.engine.RuntimeEventLevel
import rylween.engine.RuntimeEventScope
import rylween.engine.runtimeEvent
import rylween.runtime.IntAstEvaluator
import rylween.runtime.RuntimeRandomDraw
import rylween.runtime.RuntimeSessionState
import rylween.runtime.consumeInventoryItems
import rylween.runtime.countInventoryItems
import rylween.runtime.drawRandom
import rylween.runtime.firstFreeInventorySlot
import rylween.runtime.inventoryItem
import rylween.runtime.party
import rylween.runtime.prompt
import rylween.runtime.scriptMemberFieldOrNull
import rylween.runtime.scriptMemberFlagKnown
import rylween.runtime.scriptMemberFlagOrNull
import rylween.runtime.scriptPartyScalarOrNull
import rylween.runtime.scriptRealmScalarOrNull
import rylween.runtime.scriptRuntimeFlag
import rylween.runtime.withGold
import rylween.runtime.withInventoryItem
import rylween.runtime.withScriptMemberFieldOrNull
import rylween.runtime.withScriptPartyScalarOrNull

enum class BattleOpeningChoice { Fight, Run }

sealed interface BattleSessionInput {
    data class Opening(
        val choice: BattleOpeningChoice,
    ) : BattleSessionInput

    data class PlayerAction(
        val kind: CombatActionKind,
    ) : BattleSessionInput

    data class TargetMonster(
        val index: Int,
    ) : BattleSessionInput

    data class TargetMember(
        val index: Int,
    ) : BattleSessionInput

    data class InventorySlot(
        val slot: Int,
    ) : BattleSessionInput

    data class SpellSlot(
        val slot: Int,
    ) : BattleSessionInput

    object Continue : BattleSessionInput
}

enum class BattleSessionResultStatus {
    EncounterStarted,
    FightSelected,
    RunEscaped,
    RunFailed,
    RunDenied,
    Victory,
    PartyDefeated,
}

data class BattleMonsterState(
    val monster: MonsterModel,
    val count: Int,
    val hitPoints: List<Int>,
    val hitPointMax: Int,
    val mandatory: Boolean,
)

data class BattleRoundState(
    val currentMemberIndex: Int,
    val defendCount: Int,
    val monsterWeaponSkill: Int,
    val monsterConfused: Boolean = false,
    val monstersHeld: Boolean = false,
    val antimagic: Boolean = false,
    val partyHeld: Boolean = false,
    val partyConfused: Boolean = false,
    val actionRetry: Boolean = false,
)

data class BattleSessionResult(
    val status: BattleSessionResultStatus,
    val monster: MonsterId? = null,
    val tier: EncounterTier? = null,
)

sealed interface BattleSessionStep {
    val viewCommands: List<ViewCommand> get() = emptyList()

    data class Opening(
        val prompt: TextBlock,
        val image: ImageId? = null,
    ) : BattleSessionStep {
        override val viewCommands: List<ViewCommand>
            get() =
                image
                    ?.let { listOf(ViewCommand.ClearText, ViewCommand.ClearMapWindow, ViewCommand.ShowImage(it)) }
                    .orEmpty()
    }

    data class RoundOpening(
        val prompt: TextBlock,
    ) : BattleSessionStep

    data class Message(
        val texts: List<TextBlock>,
        val next: BattleSessionStep,
        override val viewCommands: List<ViewCommand> = emptyList(),
    ) : BattleSessionStep

    data class PlayerAction(
        val memberIndex: Int,
        val prompt: TextBlock,
        val actions: List<CombatActionModel>,
    ) : BattleSessionStep

    data class MonsterTarget(
        val memberIndex: Int,
        val prompt: TextBlock,
        val targetKeys: List<Char>,
    ) : BattleSessionStep

    data class ScriptMonsterTarget(
        val prompt: TextBlock,
        val targetKeys: List<Char>,
        val script: CombatScriptContinuation,
    ) : BattleSessionStep

    data class MemberTarget(
        val prompt: TextBlock,
        val targetKeys: List<Char>,
        val script: CombatScriptContinuation,
    ) : BattleSessionStep

    data class MagicItemSlot(
        val memberIndex: Int,
        val prompt: TextBlock,
        val slotKeys: List<Char>,
    ) : BattleSessionStep

    data class SpellChoice(
        val memberIndex: Int,
        val prompt: TextBlock,
        val spellKeys: List<Char>,
    ) : BattleSessionStep

    object MonsterRound : BattleSessionStep

    data class Finished(
        val runtime: RuntimeSessionState,
    ) : BattleSessionStep
}

enum class CombatScriptSourceKind { Spell, MagicItem, MonsterSpell }

data class CombatScriptContinuation(
    val memberIndex: Int?,
    val sourceKind: CombatScriptSourceKind,
    val sourceSlug: String,
    val ops: List<Op>,
    val nextIndex: Int,
    val pendingVar: String,
    val locals: Map<String, Int>,
    val activeInventorySlot: Int? = null,
)

data class BattleSession(
    val runtime: RuntimeSessionState,
    val battle: StandardBattleModel,
    val content: RuntimeContent,
    val tier: EncounterTier?,
    val encounter: BattleMonsterState,
    val round: BattleRoundState,
    val step: BattleSessionStep,
    val lastResult: BattleSessionResult,
) {
    private val itemRecords: List<ItemRecordModel> get() = content.itemRecords
    val formulas: List<FormulaModel> get() = content.formulas
    val rewards: List<RewardBundleModel> get() = content.rewards
    val leveling: LevelingModel? get() = content.leveling
    val spells: List<SpellModel> get() = content.spells
    val itemUses: List<ItemUseModel> get() = content.itemUses

    fun apply(input: BattleSessionInput): BattleSession =
        when (val current = step) {
            is BattleSessionStep.Opening -> {
                val opening =
                    (input as? BattleSessionInput.Opening)
                        ?: error("battle session is waiting for fight/run input")
                answerOpening(opening.choice)
            }
            is BattleSessionStep.RoundOpening -> {
                val opening =
                    (input as? BattleSessionInput.Opening)
                        ?: error("battle session is waiting for fight/run input")
                answerRoundOpening(opening.choice)
            }
            is BattleSessionStep.Message -> {
                require(input is BattleSessionInput.Continue) { "battle session is showing message text" }
                copy(
                    runtime = (current.next as? BattleSessionStep.Finished)?.runtime ?: runtime,
                    step = current.next,
                )
            }
            is BattleSessionStep.PlayerAction -> {
                val action =
                    (input as? BattleSessionInput.PlayerAction)
                        ?: error("battle session is waiting for a player action")
                answerPlayerAction(current.memberIndex, action.kind)
            }
            is BattleSessionStep.MonsterTarget -> {
                val target =
                    (input as? BattleSessionInput.TargetMonster)
                        ?: error("battle session is waiting for a monster target")
                require(target.index in encounter.hitPoints.indices && encounter.hitPoints[target.index] > 0) {
                    "battle target ${target.index} is not alive"
                }
                attackMonster(current.memberIndex, target.index)
            }
            is BattleSessionStep.ScriptMonsterTarget -> {
                val target =
                    (input as? BattleSessionInput.TargetMonster)
                        ?: error("battle session is waiting for a script monster target")
                require(target.index in encounter.hitPoints.indices && encounter.hitPoints[target.index] > 0) {
                    "battle target ${target.index} is not alive"
                }
                resumeScript(current.script, current.script.locals + (current.script.pendingVar to target.index))
            }
            is BattleSessionStep.MemberTarget -> {
                val target =
                    (input as? BattleSessionInput.TargetMember)
                        ?: error("battle session is waiting for a party member target")
                require(target.index in runtime.activeParty.members.indices) {
                    "party member target ${target.index} is outside the active party"
                }
                require('1' + target.index in current.targetKeys) {
                    "party member target ${target.index} is not valid for this prompt"
                }
                resumeScript(current.script, current.script.locals + (current.script.pendingVar to target.index))
            }
            is BattleSessionStep.MagicItemSlot -> {
                val slot =
                    (input as? BattleSessionInput.InventorySlot)
                        ?: error("battle session is waiting for an inventory slot")
                useMagicItem(current.memberIndex, slot.slot)
            }
            is BattleSessionStep.SpellChoice -> {
                val slot =
                    (input as? BattleSessionInput.SpellSlot)
                        ?: error("battle session is waiting for a spell slot")
                castMageSpell(current.memberIndex, slot.slot)
            }
            BattleSessionStep.MonsterRound -> {
                require(input is BattleSessionInput.Continue) { "battle session is running the monster round" }
                runMonsterRound()
            }
            is BattleSessionStep.Finished -> error("battle session is already finished")
        }

    private fun answerOpening(choice: BattleOpeningChoice): BattleSession =
        when (choice) {
            BattleOpeningChoice.Fight ->
                continueToPlayerRound(
                    status = BattleSessionResultStatus.FightSelected,
                    text = battle.text.bloodFlow,
                )
            BattleOpeningChoice.Run -> runFromEncounter()
        }

    private fun answerRoundOpening(choice: BattleOpeningChoice): BattleSession =
        when (choice) {
            BattleOpeningChoice.Fight ->
                continueToPlayerRound(
                    status = BattleSessionResultStatus.FightSelected,
                    text = battle.text.bloodFlow,
                )
            BattleOpeningChoice.Run -> runFromRound()
        }

    private fun runFromEncounter(): BattleSession {
        if (encounter.mandatory) {
            return continueToPlayerRound(
                status = BattleSessionResultStatus.RunDenied,
                text = battle.text.runDesperateFail,
            )
        }

        val draw = runtime.drawRandom(0, 99)
        return if (draw.value < battle.opening.fleeChancePercent) {
            copy(
                runtime = draw.runtime,
                step =
                    messageStep(
                        battle.text.runCoward,
                        BattleSessionStep.Finished(draw.runtime.asWilderness()),
                    ),
                lastResult = result(BattleSessionResultStatus.RunEscaped),
            )
        } else {
            copy(runtime = draw.runtime).continueToPlayerRound(
                status = BattleSessionResultStatus.RunFailed,
                text = battle.text.runFailAway,
            )
        }
    }

    private fun runFromRound(): BattleSession {
        val draw = runtime.drawRandom(0, 99)
        return if (draw.value >= battle.opening.desperateFleeChancePercent && !encounter.mandatory) {
            copy(
                runtime = draw.runtime,
                step =
                    messageStep(
                        battle.text.runEscaped,
                        BattleSessionStep.Finished(draw.runtime.asWilderness()),
                    ),
                lastResult = result(BattleSessionResultStatus.RunEscaped),
            )
        } else {
            copy(runtime = draw.runtime).continueToPlayerRound(
                status = BattleSessionResultStatus.RunFailed,
                text = battle.text.runFailAway,
            )
        }
    }

    private fun continueToPlayerRound(
        status: BattleSessionResultStatus,
        text: TextBlock,
    ): BattleSession =
        copy(
            step = messageStep(text, nextPlayerActionStep(fromIndex = 0)),
            lastResult = result(status),
        )

    private fun answerPlayerAction(
        memberIndex: Int,
        kind: CombatActionKind,
    ): BattleSession =
        when (kind) {
            CombatActionKind.Attack ->
                copy(
                    round = round.copy(currentMemberIndex = memberIndex),
                    step =
                        BattleSessionStep.MonsterTarget(
                            memberIndex = memberIndex,
                            prompt = BattlePromptText.targetPrompt(battle.text, encounter),
                            targetKeys = encounter.aliveTargetKeys(),
                        ),
                )
            CombatActionKind.Defend -> defend(memberIndex)
            CombatActionKind.BattleCry -> battleCry(memberIndex)
            CombatActionKind.UseMagicItem ->
                copy(
                    step =
                        BattleSessionStep.MagicItemSlot(
                            memberIndex = memberIndex,
                            prompt = BattlePromptText.useWhat(battle.text, runtime.activeParty, itemRecords),
                            slotKeys = inventorySlotKeys(),
                        ),
                )
            CombatActionKind.CastMageSpell ->
                copy(
                    step =
                        BattleSessionStep.SpellChoice(
                            memberIndex = memberIndex,
                            prompt =
                                BattlePromptText.spellChoice(
                                    text = battle.text,
                                    member = runtime.activeParty.members[memberIndex],
                                    spells = mageSpells(),
                                ),
                            spellKeys = mageSpells().map { '0' + it.slot } + '7',
                        ),
                )
        }

    private fun defend(memberIndex: Int): BattleSession {
        val member = runtime.activeParty.members[memberIndex]
        val nextHitPoints = (member.hitPoints.current + member.level).coerceAtMost(member.hitPoints.max)
        val nextRuntime =
            runtime.withActiveMember(
                memberIndex,
                member.copy(hitPoints = nextHitPoints of member.hitPoints.max),
            )
        val defendRules = battle.playerRound.defend
        val nextRound =
            round.copy(
                defendCount = (round.defendCount + 1).coerceAtMost(defendRules.cap),
                monsterWeaponSkill = round.monsterWeaponSkill.reducedBy(defendRules.weaponSkillPenalty),
            )
        return copy(runtime = nextRuntime, round = nextRound).copy(
            step =
                messageStep(
                    if (round.defendCount < defendRules.restThreshold) {
                        battle.text.restGainHits
                    } else {
                        battle.text.defendStance
                    },
                    nextAfterMemberAction(memberIndex, encounter),
                ),
        )
    }

    private fun battleCry(memberIndex: Int): BattleSession {
        val nextRound =
            round.copy(
                monsterWeaponSkill = round.monsterWeaponSkill.reducedBy(battle.playerRound.battleCry.weaponSkillPenalty),
            )
        return copy(round = nextRound).copy(
            step = messageStep(battle.text.battleCry, nextAfterMemberAction(memberIndex, encounter)),
        )
    }

    private fun useMagicItem(
        memberIndex: Int,
        slot: Int,
    ): BattleSession {
        val item = runtime.activeParty.inventoryItem(slot)
        val model =
            item?.let { itemId ->
                itemUses.firstOrNull { use ->
                    use.id == itemId && ItemUseContext.Combat in use.contexts
                }
            }
        if (item == null || model == null || itemRecord(item).kind != ItemKind.MagicItem) {
            return copy(step = messageStep(battle.text.notPossible, playerActionStep(memberIndex)))
        }

        if (encounter.monster.immune) {
            val nextRuntime = runtime.withActiveParty(runtime.activeParty.withInventoryItem(slot, null))
            return copy(runtime = nextRuntime).copy(
                step = messageStep(battle.text.magicImmune, nextAfterMemberAction(memberIndex, encounter)),
            )
        }

        val consumedRuntime =
            if (model.consume == ItemConsumePolicy.BeforeUse) {
                runtime.withActiveParty(runtime.activeParty.withInventoryItem(slot, null))
            } else {
                runtime
            }
        return copy(runtime = consumedRuntime).beginScript(
            memberIndex = memberIndex,
            sourceKind = CombatScriptSourceKind.MagicItem,
            sourceSlug = item.slug,
            ops = model.ops,
            locals = mapOf("caster_idx" to memberIndex, "inv_slot" to slot),
            activeInventorySlot = slot,
        )
    }

    private fun castMageSpell(
        memberIndex: Int,
        slot: Int,
    ): BattleSession {
        if (slot == 7) return copy(step = playerActionStep(memberIndex))

        val spell =
            mageSpells().firstOrNull { it.slot == slot }
                ?: return copy(step = messageStep(battle.text.notPossible, playerActionStep(memberIndex)))
        if (encounter.monster.immune) {
            return copy(step = messageStep(battle.text.spellFizzle, nextAfterMemberAction(memberIndex, encounter)))
        }

        val caster = runtime.activeParty.members[memberIndex]
        val cost = spell.baseCost + caster.level
        if (caster.mana.current < cost) {
            return copy(
                step =
                    messageStep(
                        battle.text.notEnoughMagic,
                        nextAfterMemberAction(memberIndex, encounter),
                    ),
            )
        }

        val nextCaster = caster.copy(mana = (caster.mana.current - cost) of caster.mana.max)
        val nextRuntime = runtime.withActiveMember(memberIndex, nextCaster)
        return copy(runtime = nextRuntime).beginScript(
            memberIndex = memberIndex,
            sourceKind = CombatScriptSourceKind.Spell,
            sourceSlug = spell.id.slug,
            ops = spell.ops,
            locals = mapOf("caster_idx" to memberIndex),
        )
    }

    private fun attackMonster(
        memberIndex: Int,
        targetIndex: Int,
    ): BattleSession {
        val member = runtime.activeParty.members[memberIndex]
        val hitDraw = runtime.drawRandom(0, 99)
        val skill =
            if (round.partyConfused) {
                member.weaponSkill / battle.playerRound.attack.confusionDivisor
            } else {
                member.weaponSkill
            }
        val hitChance = (skill + battle.playerRound.attack.hitBonus).coerceIn(0, 100)
        return if (hitDraw.value < hitChance) {
            val damageDraw = hitDraw.runtime.drawRandom(0, battle.playerRound.attack.damageSpreadMax)
            val damage =
                member.weaponDamage() +
                    damageDraw.value +
                    member.hitPoints.current / battle.playerRound.attack.hpDefenseDivisor
            val nextEncounter = encounter.damage(targetIndex, damage)
            val killed = encounter.hitPoints[targetIndex] > 0 && nextEncounter.hitPoints[targetIndex] == 0
            copy(runtime = damageDraw.runtime, encounter = nextEncounter).copy(
                step =
                    messageStep(
                        BattlePromptText.attackHit(
                            text = battle.text,
                            attackerName = member.name,
                            damage = damage,
                            killed = killed,
                        ),
                        nextAfterMemberAction(memberIndex, nextEncounter),
                    ),
                lastResult =
                    if (nextEncounter.aliveCount() == 0) {
                        result(BattleSessionResultStatus.Victory)
                    } else {
                        lastResult
                    },
            )
        } else {
            copy(runtime = hitDraw.runtime).copy(
                step =
                    messageStep(
                        BattlePromptText.attackMiss(battle.text, member.name),
                        nextAfterMemberAction(memberIndex, encounter),
                    ),
            )
        }
    }

    private fun beginScript(
        memberIndex: Int?,
        sourceKind: CombatScriptSourceKind,
        sourceSlug: String,
        ops: List<Op>,
        locals: Map<String, Int>,
        activeInventorySlot: Int? = null,
    ): BattleSession =
        runScript(
            CombatScriptContinuation(
                memberIndex = memberIndex,
                sourceKind = sourceKind,
                sourceSlug = sourceSlug,
                ops = ops,
                nextIndex = 0,
                pendingVar = "",
                locals = locals,
                activeInventorySlot = activeInventorySlot,
            ),
        )

    private fun resumeScript(
        script: CombatScriptContinuation,
        locals: Map<String, Int>,
    ): BattleSession = runScript(script.copy(locals = locals, pendingVar = ""))

    private fun runScript(script: CombatScriptContinuation): BattleSession {
        val result =
            CombatScriptRunner(
                initialRuntime = runtime,
                initialEncounter = encounter,
                initialRound = round,
            ).run(script)
        val base = copy(runtime = result.runtime, encounter = result.encounter, round = result.round)
        return when (result) {
            is CombatScriptRunResult.Done -> base.finishScript(script, result.texts)
            is CombatScriptRunResult.NeedMonster ->
                base.copy(
                    step =
                        messageStep(
                            result.texts,
                            BattleSessionStep.ScriptMonsterTarget(
                                prompt = result.prompt,
                                targetKeys = result.encounter.aliveTargetKeys(),
                                script = result.continuation,
                            ),
                        ),
                )
            is CombatScriptRunResult.NeedMember ->
                base.copy(
                    step =
                        messageStep(
                            result.texts,
                            BattleSessionStep.MemberTarget(
                                prompt = result.prompt,
                                targetKeys = result.targetKeys,
                                script = result.continuation,
                            ),
                        ),
                )
        }
    }

    private fun finishScript(
        script: CombatScriptContinuation,
        texts: List<TextBlock>,
    ): BattleSession {
        val memberIndex = script.memberIndex ?: return copy(step = messageStep(texts, BattleSessionStep.MonsterRound))
        val actionRetry = round.actionRetry
        val nextRound = if (actionRetry) round.copy(actionRetry = false) else round
        val next =
            if (actionRetry) {
                playerActionStep(memberIndex)
            } else {
                nextAfterMemberAction(memberIndex, encounter)
            }
        return copy(
            round = nextRound,
            step = messageStep(texts, next),
            lastResult =
                if (encounter.aliveCount() == 0) {
                    result(BattleSessionResultStatus.Victory)
                } else {
                    lastResult
                },
        )
    }

    private fun nextAfterMemberAction(
        memberIndex: Int,
        nextEncounter: BattleMonsterState,
    ): BattleSessionStep =
        if (nextEncounter.aliveCount() == 0) {
            victoryStep(nextEncounter)
        } else {
            nextPlayerActionStep(fromIndex = memberIndex + 1, nextEncounter = nextEncounter)
        }

    private fun nextPlayerActionStep(
        fromIndex: Int,
        nextEncounter: BattleMonsterState = encounter,
    ): BattleSessionStep =
        runtime.activeParty.members
            .withIndex()
            .drop(fromIndex)
            .firstOrNull { (_, member) -> !member.dead }
            ?.let { (index, member) ->
                val actions = availableActions(member)
                BattleSessionStep.PlayerAction(
                    memberIndex = index,
                    prompt =
                        BattlePromptText.playerActionPrompt(
                            text = battle.text,
                            member = member,
                            actions = actions,
                        ),
                    actions = actions,
                )
            }
            ?: if (nextEncounter.aliveCount() == 0) {
                victoryStep(nextEncounter)
            } else {
                BattleSessionStep.MonsterRound
            }

    private fun playerActionStep(memberIndex: Int): BattleSessionStep.PlayerAction {
        val member = runtime.activeParty.members[memberIndex]
        val actions = availableActions(member)
        return BattleSessionStep.PlayerAction(
            memberIndex = memberIndex,
            prompt = BattlePromptText.playerActionPrompt(battle.text, member, actions),
            actions = actions,
        )
    }

    private fun availableActions(member: PartyMemberState): List<CombatActionModel> =
        battle.actions.filter { action ->
            when (action.requirement) {
                CombatActionRequirement.Always -> true
                CombatActionRequirement.Mage -> member.pcClass == PcClass.Mage
            }
        }

    private fun victoryStep(nextEncounter: BattleMonsterState): BattleSessionStep =
        applyVictory(nextEncounter).let { victory ->
            val lootName =
                victory.loot?.let { item ->
                    itemRecords.firstOrNull { it.id == item }?.displayName ?: item.slug
                }
            BattleSessionStep.Message(
                texts =
                    BattlePromptText.victory(
                        battle.text,
                        victory.experience,
                        victory.gold,
                        lootName,
                        victory.levelGains,
                    ),
                next = BattleSessionStep.Finished(victory.runtime.asWilderness()),
                viewCommands =
                    battle.victoryImage
                        ?.let { listOf(ViewCommand.ClearMapWindow, ViewCommand.ShowImage(it), ViewCommand.ClearText) }
                        .orEmpty(),
            )
        }

    private fun runMonsterRound(): BattleSession {
        var nextRuntime = runtime
        var nextEncounter = encounter
        var nextRound = round.copy(partyHeld = false, partyConfused = false, actionRetry = false)
        val texts = mutableListOf<TextBlock>()
        nextEncounter.hitPoints.forEachIndexed { monsterIndex, hitPoints ->
            if (hitPoints > 0 && nextRuntime.activeParty.aliveMembers().isNotEmpty()) {
                if (!nextRound.monstersHeld && shouldMonsterCast(nextRuntime, nextRound).also { nextRuntime = it.runtime }.value) {
                    texts += BattlePromptText.monsterCasts(nextEncounter.monster.name)
                    val cast =
                        castMonsterSpell(
                            runtime = nextRuntime,
                            encounter = nextEncounter,
                            round = nextRound,
                            monsterIndex = monsterIndex,
                        )
                    nextRuntime = cast.runtime
                    nextEncounter = cast.encounter
                    nextRound = cast.round
                    texts += cast.texts
                } else if (!nextRound.monstersHeld) {
                    val targetDraw = nextRuntime.drawAliveMemberIndex()
                    nextRuntime = targetDraw.runtime
                    val targetIndex = targetDraw.value
                    val target = nextRuntime.activeParty.members[targetIndex]
                    val hitDraw = nextRuntime.drawRandom(0, 99)
                    nextRuntime = hitDraw.runtime
                    if (hitDraw.value < (nextRound.monsterWeaponSkill + battle.monsterRound.melee.hitBonus).coerceIn(0, 100)) {
                        val damageDraw = nextRuntime.drawRandom(0, battle.monsterRound.melee.damageSpreadMax)
                        nextRuntime = damageDraw.runtime
                        val armor = itemRecords.firstOrNull { it.id == target.armor }?.armorClass ?: 0
                        val damage =
                            (hitPoints / battle.monsterRound.melee.damageDivisor + damageDraw.value - armor)
                                .coerceAtLeast(battle.monsterRound.melee.damageMin)
                        val nextHp = (target.hitPoints.current - damage).coerceAtLeast(0)
                        val nextTarget =
                            target.copy(
                                hitPoints = nextHp of target.hitPoints.max,
                                dead = target.dead || nextHp == 0,
                            )
                        nextRuntime = nextRuntime.withActiveMember(targetIndex, nextTarget)
                        texts += BattlePromptText.monsterHit(battle.text, nextEncounter.monster.name, target.name, damage, nextHp == 0)
                    } else {
                        texts += BattlePromptText.monsterMiss(battle.text, nextEncounter.monster.name, target.name)
                    }
                }
            }
        }

        if (nextRuntime.activeParty.aliveMembers().isEmpty()) {
            val gameOver = nextRuntime.withRealm(nextRuntime.realm.copy(currentMode = RuntimeMode.GameOver))
            return copy(
                runtime = gameOver,
                round = nextRound,
                step = messageStep(texts, BattleSessionStep.Finished(gameOver)),
                lastResult = result(BattleSessionResultStatus.PartyDefeated),
            )
        }

        nextRound = nextRound.afterMonsterRound()
        val nextStep =
            if (nextRound.partyHeld) {
                messageStep(texts + battle.text.partyHeld, BattleSessionStep.MonsterRound)
            } else {
                messageStep(
                    texts.ifEmpty { listOf(BattlePromptText.stillFace(battle.text, nextEncounter)) },
                    BattleSessionStep.RoundOpening(BattlePromptText.stillFace(battle.text, nextEncounter)),
                )
            }
        return copy(runtime = nextRuntime, encounter = nextEncounter, round = nextRound).copy(
            step = nextStep,
        )
    }

    private fun shouldMonsterCast(
        runtime: RuntimeSessionState,
        round: BattleRoundState,
    ): RuntimeBooleanDraw {
        if (round.antimagic || encounter.monster.spellPower <= 0 || monsterSpells().isEmpty()) {
            return RuntimeBooleanDraw(false, runtime)
        }
        val chance =
            battle.monsterRound.spellChanceBasePercent +
                encounter.monster.spellPower / battle.monsterRound.spellPowerDivisor
        val draw = runtime.drawRandom(0, 99)
        return RuntimeBooleanDraw(draw.value < chance, draw.runtime)
    }

    private fun castMonsterSpell(
        runtime: RuntimeSessionState,
        encounter: BattleMonsterState,
        round: BattleRoundState,
        monsterIndex: Int,
    ): BattleScriptEffect {
        val options = monsterSpells()
        if (options.isEmpty()) return BattleScriptEffect(runtime, encounter, round, emptyList())
        val pick = runtime.drawRandom(0, options.lastIndex)
        val spell = options[pick.value]
        val script =
            CombatScriptContinuation(
                memberIndex = null,
                sourceKind = CombatScriptSourceKind.MonsterSpell,
                sourceSlug = spell.id.slug,
                ops = spell.ops,
                nextIndex = 0,
                pendingVar = "",
                locals =
                    mapOf(
                        "caster_idx" to monsterIndex,
                        "attacker_idx" to monsterIndex,
                    ),
            )
        return when (val result = CombatScriptRunner(pick.runtime, encounter, round).run(script)) {
            is CombatScriptRunResult.Done ->
                BattleScriptEffect(
                    runtime = result.runtime,
                    encounter = result.encounter,
                    round = result.round,
                    texts = result.texts,
                )
            is CombatScriptRunResult.NeedMember,
            is CombatScriptRunResult.NeedMonster,
            -> error("monster combat spell ${spell.id.slug} cannot ask the player")
        }
    }

    private fun applyVictory(nextEncounter: BattleMonsterState): BattleVictoryResult {
        val reward = rewards.firstOrNull { it.id == battle.rewards }
        val bindings =
            mapOf(
                "encounter_foe_count" to nextEncounter.count,
                "encounter_hit_point_max" to nextEncounter.hitPointMax,
                "monster_weapon_skill" to round.monsterWeaponSkill,
                "monster_spell_power" to nextEncounter.monster.spellPower,
            )
        val experience = reward?.experience?.let { formulas.evalFormula(it, bindings) } ?: 0
        val gold = reward?.gold?.let { formulas.evalFormula(it, bindings) } ?: 0
        val lootSelection = reward?.let { selectBattleLoot(runtime, it) }
        val rewardRuntime = lootSelection?.runtime ?: runtime
        val loot = lootSelection?.item
        val partyBeforeLevelUp =
            rewardRuntime.activeParty
                .withKills(nextEncounter.count)
                .withExperienceForSurvivors(experience)
                .withGold(rewardRuntime.activeParty.resources.gold + gold)
                .let { party -> loot?.let { party.placeBattleLoot(it) } ?: party }
        val levelUps = partyBeforeLevelUp.applyLevelUps(leveling, formulas)
        return BattleVictoryResult(
            runtime = rewardRuntime.withActiveParty(levelUps.party),
            experience = experience,
            gold = gold,
            loot = loot,
            levelGains = levelUps.gains,
        )
    }

    private fun result(status: BattleSessionResultStatus): BattleSessionResult = BattleSessionResult(status, encounter.monster.id, tier)

    companion object {
        fun start(
            runtime: RuntimeSessionState,
            combat: CombatSystemModel,
            monsters: List<MonsterModel>,
            tier: EncounterTier,
            mandatory: Boolean = false,
            itemRecords: List<ItemRecordModel> = emptyList(),
            formulas: List<FormulaModel> = emptyList(),
            rewards: List<RewardBundleModel> = emptyList(),
            leveling: LevelingModel? = null,
            spells: List<SpellModel> = emptyList(),
            itemUses: List<ItemUseModel> = emptyList(),
        ): BattleSession =
            start(
                runtime = runtime,
                tier = tier,
                content =
                    RuntimeContent(
                        combat = combat,
                        monsters = monsters,
                        itemRecords = itemRecords,
                        formulas = formulas,
                        rewards = rewards,
                        leveling = leveling,
                        spells = spells,
                        itemUses = itemUses,
                    ),
                mandatory = mandatory,
            )

        fun start(
            runtime: RuntimeSessionState,
            tier: EncounterTier,
            content: RuntimeContent,
            mandatory: Boolean = false,
        ): BattleSession {
            requireNotNull(content.combat.standardBattle) { "standard battle is not configured" }
            val candidates = content.monsters.filter { it.tier == tier }
            require(candidates.isNotEmpty()) { "no monsters declared for encounter tier ${tier.slug}" }

            val pick = runtime.drawRandom(0, candidates.lastIndex)
            val monster = candidates[pick.value]
            return startMonster(
                runtime = pick.runtime,
                monster = monster,
                content = content,
                tier = tier,
                mandatory = mandatory,
            )
        }

        fun startMonster(
            runtime: RuntimeSessionState,
            monster: MonsterModel,
            content: RuntimeContent,
            tier: EncounterTier? = monster.tier,
            mandatory: Boolean = false,
        ): BattleSession {
            val battle = requireNotNull(content.combat.standardBattle) { "standard battle is not configured" }
            val firstLevel =
                runtime.activeParty.members
                    .firstOrNull()
                    .levelOrZero()
            val countMax = monster.count.last + (monster.countMaxLevelDivisor?.let { firstLevel / it } ?: 0)
            val bannerDraw =
                battle.opening.banners
                    .takeIf { it.isNotEmpty() }
                    ?.let { banners -> runtime.drawRandom(0, banners.lastIndex) }
            val bannerImage = bannerDraw?.value?.let { battle.opening.banners[it] }
            val countDraw = (bannerDraw?.runtime ?: runtime).drawRandom(1, countMax)
            val count = countDraw.value.coerceAtLeast(monster.count.first).coerceAtMost(battle.encounterFoeCountMax)
            val hpRange = countDraw.runtime.scaledEncounterHpRange(monster, battle, content.formulas)
            var hpRuntime = countDraw.runtime
            val hitPoints =
                buildList {
                    repeat(count) {
                        val hpDraw = hpRuntime.drawRandom(1, hpRange.last)
                        hpRuntime = hpDraw.runtime
                        add(hpDraw.value.coerceAtLeast(hpRange.first))
                    }
                }
            val nextRuntime =
                hpRuntime.withRealm(
                    hpRuntime.realm.copy(
                        currentMode = RuntimeMode.Battle,
                        currentTown = null,
                    ),
                )
            val encounter = BattleMonsterState(monster, count, hitPoints, hpRange.last, mandatory)
            return BattleSession(
                runtime = nextRuntime,
                battle = battle,
                content = content,
                tier = tier,
                encounter = encounter,
                round =
                    BattleRoundState(
                        currentMemberIndex = 0,
                        defendCount = 0,
                        monsterWeaponSkill =
                            monster.weaponSkill +
                                nextRuntime.activeParty.members
                                    .firstOrNull()
                                    .levelOrZero(),
                    ),
                step = BattleSessionStep.Opening(BattlePromptText.opening(battle.text, encounter), bannerImage),
                lastResult = BattleSessionResult(BattleSessionResultStatus.EncounterStarted, monster.id, tier),
            )
        }
    }

    private fun messageStep(
        text: TextBlock,
        next: BattleSessionStep,
    ): BattleSessionStep.Message = BattleSessionStep.Message(listOf(text), next)

    private fun messageStep(
        texts: List<TextBlock>,
        next: BattleSessionStep,
    ): BattleSessionStep.Message = BattleSessionStep.Message(texts, next)

    private fun mageSpells(): List<SpellModel> = spells.filter { it.id.slug.startsWith("mage.") }.sortedBy { it.slot }

    private fun monsterSpells(): List<SpellModel> =
        spells.filter { it.id.slug.startsWith("monster.") && it.id != DslDefaultSpells.Monster.melee }.sortedBy { it.slot }

    private fun inventorySlotKeys(): List<Char> = (1..InventorySaveSlotMax).map { slot -> 'A' + slot - 1 }

    private fun itemRecord(item: ItemId): ItemRecordModel =
        itemRecords.firstOrNull { it.id == item } ?: error("missing item record ${item.slug}")

    private fun PartyMemberState.weaponDamage(): Int = itemRecords.firstOrNull { it.id == weapon }?.weaponDamage ?: 1
}

data class BattleVictoryResult(
    val runtime: RuntimeSessionState,
    val experience: Int,
    val gold: Int,
    val loot: ItemId?,
    val levelGains: List<BattleLevelGain>,
)

data class BattleLevelGain(
    val memberName: String,
    val levels: Int,
)

private data class PartyLevelUpResult(
    val party: PartyState,
    val gains: List<BattleLevelGain>,
)

private sealed interface CombatScriptRunResult {
    val runtime: RuntimeSessionState
    val encounter: BattleMonsterState
    val round: BattleRoundState
    val texts: List<TextBlock>

    data class Done(
        override val runtime: RuntimeSessionState,
        override val encounter: BattleMonsterState,
        override val round: BattleRoundState,
        override val texts: List<TextBlock>,
    ) : CombatScriptRunResult

    data class NeedMonster(
        override val runtime: RuntimeSessionState,
        override val encounter: BattleMonsterState,
        override val round: BattleRoundState,
        override val texts: List<TextBlock>,
        val prompt: TextBlock,
        val continuation: CombatScriptContinuation,
    ) : CombatScriptRunResult

    data class NeedMember(
        override val runtime: RuntimeSessionState,
        override val encounter: BattleMonsterState,
        override val round: BattleRoundState,
        override val texts: List<TextBlock>,
        val prompt: TextBlock,
        val targetKeys: List<Char>,
        val continuation: CombatScriptContinuation,
    ) : CombatScriptRunResult
}

private class CombatScriptRunner(
    initialRuntime: RuntimeSessionState,
    initialEncounter: BattleMonsterState,
    initialRound: BattleRoundState,
) {
    private var runtime = initialRuntime
    private var encounter = initialEncounter
    private var round = initialRound
    private var locals = emptyMap<String, Int>()
    private val texts = mutableListOf<TextBlock>()

    private val monsterFieldReaders: Map<String, (Int) -> Int> =
        mapOf(
            "hit_points" to { index -> encounter.hitPoints.getOrElse(index) { 0 } },
            "dead" to { index -> if (encounter.hitPoints.getOrElse(index) { 0 } <= 0) 1 else 0 },
        )

    private val monsterFieldWriters: Map<String, (Int, Int) -> Unit> =
        mapOf(
            "hit_points" to { index, value ->
                encounter =
                    encounter.copy(
                        hitPoints =
                            encounter.hitPoints.mapIndexed { monster, hp ->
                                if (monster == index) value.coerceAtLeast(0) else hp
                            },
                    )
            },
            "dead" to { index, value ->
                if (value != 0) {
                    encounter =
                        encounter.copy(
                            hitPoints =
                                encounter.hitPoints.mapIndexed { monster, hp ->
                                    if (monster == index) 0 else hp
                                },
                        )
                }
            },
        )

    private val battleFlagReaders: Map<String, () -> Int> =
        mapOf(
            "monster_weapon_skill" to { round.monsterWeaponSkill },
            "encounter_hit_point_max" to { encounter.hitPointMax },
            "monster_confused" to { if (round.monsterConfused) 1 else 0 },
            "monsters_held" to { if (round.monstersHeld) 1 else 0 },
            "antimagic" to { if (round.antimagic) 1 else 0 },
            "party_held" to { if (round.partyHeld) 1 else 0 },
            "party_confused" to { if (round.partyConfused) 1 else 0 },
            "action_retry" to { if (round.actionRetry) 1 else 0 },
        )

    private val battleFlagWriters: Map<String, (Int) -> Unit> =
        mapOf(
            "monster_weapon_skill" to { value -> round = round.copy(monsterWeaponSkill = value.coerceAtLeast(0)) },
            "monster_confused" to { value -> round = round.copy(monsterConfused = value != 0) },
            "monsters_held" to { value -> round = round.copy(monstersHeld = value != 0) },
            "antimagic" to { value -> round = round.copy(antimagic = value != 0) },
            "party_held" to { value -> round = round.copy(partyHeld = value != 0) },
            "party_confused" to { value -> round = round.copy(partyConfused = value != 0) },
            "action_retry" to { value -> round = round.copy(actionRetry = value != 0) },
        )

    private val battleQueryReaders: Map<String, () -> Int> =
        mapOf(
            "first_alive_monster" to { encounter.hitPoints.indexOfFirst { it > 0 } },
            "random_alive_member" to {
                val draw = runtime.drawAliveMemberIndex()
                runtime = draw.runtime
                draw.value
            },
        )

    private val monsterFlagReaders: Map<String, (Int) -> Boolean> =
        mapOf(
            "dead" to { index -> encounter.hitPoints.getOrElse(index) { 0 } <= 0 },
        )

    private val battlePartyScalars: Set<String> = setOf("gold")
    private val battleRealmScalars: Set<String> = setOf("party_member_max")

    private val evaluator =
        object : IntAstEvaluator() {
            override fun local(name: String): Int = locals[name] ?: 0

            override fun random(
                lo: Int,
                hi: Int,
            ): Int {
                val draw = runtime.drawRandom(lo, hi)
                runtime = draw.runtime
                return draw.value
            }

            override fun memberField(
                member: Int,
                field: String,
            ): Int = readMemberField(member, field)

            override fun monsterField(
                monster: Int,
                field: String,
            ): Int = readMonsterField(monster, field)

            override fun battleFlag(name: String): Int = readBattleFlag(name)

            override fun battleQuery(name: String): Int = readBattleQuery(name)

            override fun realmScalar(name: String): Int = readRealmScalar(name)

            override fun partyScalar(name: String): Int = readPartyScalar(name)

            override fun inventoryPoolCount(pool: ItemPoolId): Int = runtime.activeParty.countInventoryItems(pool)

            override fun milestoneAchieved(milestone: GameMilestoneId): Boolean = milestone in runtime.activeParty.milestones

            override fun memberFlag(
                member: Int,
                name: String,
            ): Boolean = readMemberFlag(member, name)

            override fun monsterFlag(
                monster: Int,
                name: String,
            ): Boolean = readMonsterFlag(monster, name)

            override fun runtimeFlag(name: String): Boolean = runtime.scriptRuntimeFlag(name)
        }

    fun run(start: CombatScriptContinuation): CombatScriptRunResult {
        locals = start.locals
        var index = start.nextIndex
        while (index < start.ops.size) {
            when (val op = start.ops[index]) {
                is Op.Screen -> texts += renderText(op.text)
                is Op.Emit -> texts += TextBlock.Ref(op.text)
                is Op.EmitIndexed -> texts += inlineText(evaluator.int(op.value).toString())
                is Op.WaitInput -> Unit
                is Op.Pause -> Unit
                is Op.View -> Unit
                is Op.LetInt -> locals = locals + (op.name to evaluator.int(op.value))
                is Op.BindText -> Unit
                is Op.Set -> write(op.target, evaluator.int(op.value))
                is Op.Add -> write(op.target, read(op.target) + evaluator.int(op.value))
                is Op.Sub -> write(op.target, read(op.target) - evaluator.int(op.value))
                is Op.AddClamped -> {
                    val next = read(op.target) + evaluator.int(op.value)
                    write(op.target, next.coerceAtMost(evaluator.int(op.max)))
                }
                is Op.If -> runNested(if (evaluator.bool(op.cond)) op.thenOps else op.elseOps, start)
                is Op.ForEachMonster -> {
                    encounter.hitPoints.indices.forEach { monster ->
                        if (op.includeDead || encounter.hitPoints[monster] > 0) {
                            runNested(op.body, start.copy(locals = locals + (op.varName to monster)))
                        }
                    }
                }
                is Op.ForEachMember -> {
                    runtime.activeParty.members.indices.forEach { member ->
                        if (op.includeDead || !runtime.activeParty.members[member].dead) {
                            runNested(op.body, start.copy(locals = locals + (op.varName to member)))
                        }
                    }
                }
                is Op.AskKey -> unsupportedCombatOp(op)
                is Op.AskMonster -> return needMonster(start, index, op)
                is Op.AskMember -> return needMember(start, index, op)
                Op.ConsumeActiveItem ->
                    start.activeInventorySlot?.let { slot ->
                        runtime = runtime.withActiveParty(runtime.activeParty.withInventoryItem(slot, null))
                    }
                is Op.ClearInventorySlot ->
                    runtime =
                        runtime.withActiveParty(
                            runtime.activeParty.withInventoryItem(evaluator.int(op.slot), null),
                        )
                is Op.ConsumeInventoryPool ->
                    runtime =
                        runtime.withActiveParty(
                            runtime.activeParty.consumeInventoryItems(op.pool, evaluator.int(op.quantity)),
                        )
                is Op.NativeCall -> Unit
                is Op.SetFlag,
                is Op.AppendJournal,
                is Op.Loop,
                is Op.Break,
                is Op.Stop,
                is Op.Switch,
                is Op.Menu,
                is Op.AskInventorySlot,
                is Op.AskText,
                is Op.Speak,
                is Op.TellRumour,
                is Op.RunScene,
                is Op.Encounter,
                is Op.EnterDungeon,
                Op.ConsumeTorch,
                is Op.TeleportParty,
                is Op.DiscoverCave,
                is Op.GrantReward,
                is Op.RandomItemOffer,
                is Op.ShopService,
                is Op.AchieveMilestone,
                -> unsupportedCombatOp(op)
            }
            index++
        }
        return CombatScriptRunResult.Done(runtime, encounter, round, texts.toList())
    }

    private fun runNested(
        ops: List<Op>,
        parent: CombatScriptContinuation,
    ) {
        val nested = run(parent.copy(ops = ops, nextIndex = 0, locals = locals))
        runtime = nested.runtime
        encounter = nested.encounter
        round = nested.round
        locals =
            when (nested) {
                is CombatScriptRunResult.Done -> locals
                is CombatScriptRunResult.NeedMember,
                is CombatScriptRunResult.NeedMonster,
                -> error("nested combat target prompts are not supported yet")
            }
    }

    private fun needMonster(
        script: CombatScriptContinuation,
        index: Int,
        op: Op.AskMonster,
    ): CombatScriptRunResult.NeedMonster =
        CombatScriptRunResult.NeedMonster(
            runtime = runtime,
            encounter = encounter,
            round = round,
            texts = texts.toList(),
            prompt = renderText(op.prompt),
            continuation = script.copy(nextIndex = index + 1, pendingVar = op.varName, locals = locals),
        )

    private fun needMember(
        script: CombatScriptContinuation,
        index: Int,
        op: Op.AskMember,
    ): CombatScriptRunResult.NeedMember {
        val keys =
            runtime.activeParty.members.mapIndexedNotNull { memberIndex, member ->
                ('1' + memberIndex).takeIf { member.matchesCombatFilter(op.accepting) }
            }
        return CombatScriptRunResult.NeedMember(
            runtime = runtime,
            encounter = encounter,
            round = round,
            texts = texts.toList(),
            prompt = renderText(op.prompt),
            targetKeys = keys,
            continuation = script.copy(nextIndex = index + 1, pendingVar = op.varName, locals = locals),
        )
    }

    private fun renderText(block: TextBlock): TextBlock =
        when (block) {
            is TextBlock.Ref,
            is TextBlock.Inline,
            -> block
            is TextBlock.Structured ->
                TextBlock.Inline(
                    block.lines.map { line ->
                        line.parts.joinToString(separator = "") { part ->
                            // CPD-OFF
                            when (part) {
                                is TextPart.Literal -> part.text
                                is TextPart.IntValue -> evaluator.int(part.value).toString()
                                is TextPart.StringValue -> evalString(part.value)
                                is TextPart.RoleName,
                                is TextPart.RoleLocation,
                                is TextPart.TownName,
                                is TextPart.FuzzyTownLocation,
                                is TextPart.LocationCoordinate,
                                TextPart.OfferedItemName,
                                TextPart.OfferedItemPrice,
                                TextPart.SelectedItemName,
                                TextPart.ShopPrice,
                                TextPart.ShopCarryCap,
                                TextPart.HuntMemberName,
                                TextPart.HuntMemberSubjectPronoun,
                                TextPart.HuntFoodGained,
                                TextPart.PartyMemberMax,
                                TextPart.WildernessDungeonName,
                                TextPart.WildernessDungeonLevels,
                                TextPart.DungeonDepth,
                                TextPart.ConversationNpcName,
                                TextPart.CurrentTownName,
                                TextPart.TownTaxPercent,
                                TextPart.TownTaxAmount,
                                TextPart.CreationMemberNumber,
                                TextPart.CreationMemberName,
                                TextPart.CreationWeaponSkill,
                                TextPart.CreationHealth,
                                TextPart.CreationWisdom,
                                TextPart.CreationCharisma,
                                TextPart.BattleFoeDisplayName,
                                TextPart.BattleAttackerName,
                                TextPart.BattleTargetName,
                                TextPart.BattleDamage,
                                TextPart.BattleExperience,
                                TextPart.BattleGold,
                                TextPart.BattleLootName,
                                TextPart.BattleLevelMemberName,
                                TextPart.BattleLevelsGained,
                                -> part.fallbackText()
                                // CPD-ON
                            }
                        }
                    },
                )
        }

    private fun evalString(ast: StrAst): String =
        when (ast) {
            is StrAst.NativeString ->
                when (ast.path) {
                    NativeAllowlist.partyMemberName.path ->
                        runtime.activeParty.members
                            .getOrNull(evalStringArg(ast.args.single()))
                            ?.name
                            .orEmpty()
                    NativeAllowlist.combatMonsterName.path -> encounter.monster.name
                    NativeAllowlist.partyPronounSubject.path ->
                        pronoun(evalStringArg(ast.args.single()), PronounCase.Subject)
                    NativeAllowlist.partyPronounObject.path ->
                        pronoun(evalStringArg(ast.args.single()), PronounCase.Object)
                    NativeAllowlist.partyPronounPossessive.path ->
                        pronoun(evalStringArg(ast.args.single()), PronounCase.Possessive)
                    else -> "{${ast.path}}"
                }
        }

    private fun evalStringArg(arg: NativeStringArg): Int =
        when (arg) {
            is NativeStringArg.IntValue -> evaluator.int(arg.value)
            is NativeStringArg.Literal -> arg.value.toIntOrNull() ?: 0
        }

    private fun pronoun(
        memberIndex: Int,
        case: PronounCase,
    ): String {
        val gender =
            runtime.activeParty.members
                .getOrNull(memberIndex)
                ?.gender ?: Gender.Male
        return gender.pronoun(case)
    }

    private fun read(target: LValueAst): Int =
        when (target) {
            is LValueAst.MemberField -> readMemberField(evaluator.int(target.member), target.field)
            is LValueAst.MonsterField -> readMonsterField(evaluator.int(target.monster), target.field)
            is LValueAst.BattleFlag -> readBattleFlag(target.name)
            is LValueAst.PartyScalar -> readPartyScalar(target.name)
            is LValueAst.RealmScalar -> readRealmScalar(target.name)
        }

    private fun write(
        target: LValueAst,
        value: Int,
    ) {
        when (target) {
            is LValueAst.MemberField -> writeMemberField(evaluator.int(target.member), target.field, value)
            is LValueAst.MonsterField -> writeMonsterField(evaluator.int(target.monster), target.field, value)
            is LValueAst.BattleFlag -> writeBattleFlag(target.name, value)
            is LValueAst.PartyScalar -> writePartyScalar(target.name, value)
            is LValueAst.RealmScalar -> writeRealmScalar(target.name, value)
        }
    }

    private fun readMemberField(
        index: Int,
        field: String,
    ): Int {
        val member = runtime.activeParty.members.getOrNull(index) ?: return 0
        return member.scriptMemberFieldOrNull(field) ?: error("unknown battle member field $field")
    }

    private fun writeMemberField(
        index: Int,
        field: String,
        value: Int,
    ) {
        val member = runtime.activeParty.members.getOrNull(index) ?: return
        val next =
            member.withScriptMemberFieldOrNull(field, value)
                ?: error("unknown battle member field $field")
        val updated = runtime.withActiveMember(index, next)
        runtime =
            if (field == "dead") {
                updated.withActiveParty(updated.activeParty.withLivingCount())
            } else {
                updated
            }
    }

    private fun readMonsterField(
        index: Int,
        field: String,
    ): Int = monsterFieldReaders[field]?.invoke(index) ?: error("unknown battle monster field $field")

    private fun writeMonsterField(
        index: Int,
        field: String,
        value: Int,
    ) {
        val writer = monsterFieldWriters[field] ?: error("unknown battle monster field $field")
        writer(index, value)
    }

    private fun readBattleFlag(name: String): Int = battleFlagReaders[name]?.invoke() ?: error("unknown battle flag $name")

    private fun writeBattleFlag(
        name: String,
        value: Int,
    ) {
        val writer =
            battleFlagWriters[name] ?: when {
                name in battleFlagReaders -> error("battle flag $name is read-only")
                else -> error("unknown battle flag $name")
            }
        writer(value)
    }

    private fun readBattleQuery(name: String): Int = battleQueryReaders[name]?.invoke() ?: error("unknown battle query $name")

    private fun readMemberFlag(
        index: Int,
        name: String,
    ): Boolean {
        if (!scriptMemberFlagKnown(name)) {
            error("unknown battle member flag $name")
        }
        return runtime.activeParty.members
            .getOrNull(index)
            ?.scriptMemberFlagOrNull(name)
            ?: (name == "dead")
    }

    private fun readMonsterFlag(
        index: Int,
        name: String,
    ): Boolean = monsterFlagReaders[name]?.invoke(index) ?: error("unknown battle monster flag $name")

    private fun readPartyScalar(name: String): Int =
        runtime.activeParty
            .scriptPartyScalarOrNull(name)
            ?.takeIf { name in battlePartyScalars }
            ?: error("combat scripts cannot read party scalar $name yet")

    private fun writePartyScalar(
        name: String,
        value: Int,
    ) {
        if (name !in battlePartyScalars) {
            error("combat scripts cannot write party scalar $name yet")
        }
        val party =
            runtime.activeParty.withScriptPartyScalarOrNull(name, value)
                ?: error("combat scripts cannot write party scalar $name yet")
        runtime = runtime.withActiveParty(party)
    }

    private fun readRealmScalar(name: String): Int =
        runtime
            .scriptRealmScalarOrNull(name)
            ?.takeIf { name in battleRealmScalars }
            ?: error("combat scripts cannot read realm scalar $name yet")

    private fun writeRealmScalar(
        name: String,
        value: Int,
    ): Unit = error("combat scripts cannot write realm scalar $name to $value yet")

    private fun unsupportedCombatOp(op: Op): Nothing =
        error("combat script op ${op::class.simpleName} is not supported by BattleSession yet")
}

private data class BattleLootSelection(
    val runtime: RuntimeSessionState,
    val item: ItemId?,
)

private data class RuntimeBooleanDraw(
    val value: Boolean,
    val runtime: RuntimeSessionState,
)

private data class BattleScriptEffect(
    val runtime: RuntimeSessionState,
    val encounter: BattleMonsterState,
    val round: BattleRoundState,
    val texts: List<TextBlock>,
)

fun List<FormulaModel>.evalFormula(
    id: FormulaId,
    bindings: Map<String, Int>,
): Int {
    val formula = firstOrNull { it.id == id } ?: error("missing formula ${id.slug}")
    val evaluator =
        object : IntAstEvaluator() {
            override fun local(name: String): Int = bindings[name] ?: error("formula ${id.slug} requires binding $name")
        }
    val value = evaluator.int(formula.value)
    RuntimeEventScope.current().emit(
        runtimeEvent(
            RuntimeEventLevel.Debug,
            "formula",
            "eval",
            "id" to id.slug,
            "value" to value,
            "bindings" to bindings.entries.joinToString(",") { (key, binding) -> "$key:$binding" },
        ),
    )
    return value
}

private fun selectBattleLoot(
    runtime: RuntimeSessionState,
    reward: RewardBundleModel,
): BattleLootSelection {
    if (runtime.activeParty.firstFreeInventorySlot() == null) {
        return BattleLootSelection(runtime, null)
    }

    val dropDraw = runtime.drawRandom(0, 99)
    if (dropDraw.value >= reward.loot.dropChancePercent) {
        return BattleLootSelection(dropDraw.runtime, null)
    }

    val treasureDraw = dropDraw.runtime.drawRandom(0, 99)
    val pool =
        if (treasureDraw.value < reward.loot.treasureChancePercent) {
            reward.loot.treasure
        } else {
            reward.loot.magic
        }
    if (pool.items.isEmpty()) {
        return BattleLootSelection(treasureDraw.runtime, null)
    }

    val itemDraw = treasureDraw.runtime.drawRandom(0, pool.items.lastIndex)
    return BattleLootSelection(itemDraw.runtime, pool.items[itemDraw.value])
}

private fun PartyState.withKills(count: Int): PartyState = copy(counters = counters.copy(kills = counters.kills + count))

private fun PartyState.withExperienceForSurvivors(amount: Int): PartyState =
    copy(
        members =
            members.map { member ->
                if (member.dead) member else member.copy(experience = member.experience + amount)
            },
    )

private fun PartyState.withLivingCount(): PartyState = copy(counters = counters.copy(memberCount = members.count { !it.dead }))

private fun PartyMemberState.matchesCombatFilter(filter: MemberChoiceFilter): Boolean =
    when (filter) {
        MemberChoiceFilter.Any -> true
        MemberChoiceFilter.AliveOnly -> !dead
        MemberChoiceFilter.DeadOnly -> dead
        MemberChoiceFilter.SpellcasterOnly -> !dead && pcClass in setOf(PcClass.Mage, PcClass.Priest)
    }

private fun PartyState.applyLevelUps(
    leveling: LevelingModel?,
    formulas: List<FormulaModel>,
): PartyLevelUpResult {
    leveling ?: return PartyLevelUpResult(this, emptyList())
    val growthByClass = leveling.classGrowth.associateBy { it.klass }
    val gains = mutableListOf<BattleLevelGain>()
    val nextParty =
        copy(
            members =
                members.map { member ->
                    val gained =
                        ((member.level + 1)..leveling.maxLevel).count { targetLevel ->
                            member.experience >= leveling.xpThresholds.getOrElse(targetLevel) { Int.MAX_VALUE }
                        }
                    if (gained == 0) return@map member
                    gains += BattleLevelGain(member.name, gained)
                    val growth = growthByClass[member.pcClass]
                    val nextHealth = member.health + (growth?.health ?: 0) * gained
                    val hpFloor = formulas.evalFormula(leveling.hitPointFloor, mapOf("health" to nextHealth))
                    member.copy(
                        level = (member.level + gained).coerceAtMost(leveling.maxLevel),
                        weaponSkill = member.weaponSkill + (growth?.weaponSkill ?: 0) * gained,
                        health = nextHealth,
                        wisdom = member.wisdom + (growth?.wisdom ?: 0) * gained,
                        charisma = member.charisma + (growth?.charisma ?: 0) * gained,
                        hitPoints = member.hitPoints.current.coerceAtLeast(hpFloor) of nextHealth,
                    )
                },
        )
    return PartyLevelUpResult(nextParty, gains.toList())
}

private fun PartyState.placeBattleLoot(item: ItemId): PartyState = firstFreeInventorySlot()?.let { withInventoryItem(it, item) } ?: this

private fun PartyState.aliveMembers(): List<PartyMemberState> = members.filter { !it.dead }

private fun PartyState.aliveMemberIndexes(): List<Int> = members.mapIndexedNotNull { index, member -> index.takeUnless { member.dead } }

object BattlePromptText {
    fun opening(
        text: BattleTextModel,
        encounter: BattleMonsterState,
    ): TextBlock =
        combineTextBlocks(
            text.encounterBanner,
            text.youFace,
            text.foeDisplayName.withBattleValues(TextPart.BattleFoeDisplayName to encounter.foeDisplayName()),
            text.fight,
            text.run,
        )

    fun playerActionPrompt(
        text: BattleTextModel,
        member: PartyMemberState,
        actions: List<CombatActionModel>,
    ): TextBlock =
        combineTextBlocks(
            listOf(inlineText(member.name), text.whatNow) + actions.map { action -> action.prompt },
        )

    fun targetPrompt(
        text: BattleTextModel,
        encounter: BattleMonsterState,
    ): TextBlock =
        combineTextBlocks(
            text.targetPrompt,
            TextBlock.Inline(
                encounter.hitPoints.mapIndexed { index, hitPoints ->
                    "${'A' + index}. ${if (hitPoints > 0) "Alive." else "Dead."} (${hitPoints.toString().padStart(3)} hp)"
                },
            ),
        )

    fun useWhat(
        text: BattleTextModel,
        party: PartyState,
        itemRecords: List<ItemRecordModel>,
    ): TextBlock =
        combineTextBlocks(
            text.useWhat,
            TextBlock.Inline(
                (1..InventorySaveSlotMax).map { slot ->
                    val item =
                        party.inventoryItem(slot)?.let { item ->
                            itemRecords.firstOrNull { it.id == item }?.displayName ?: item.slug
                        } ?: "empty"
                    "${'A' + slot - 1}. $item"
                },
            ),
        )

    fun spellChoice(
        text: BattleTextModel,
        member: PartyMemberState,
        spells: List<SpellModel>,
    ): TextBlock =
        combineTextBlocks(
            TextBlock.Inline(
                spells.map { spell ->
                    "${spell.slot}. ${spell.name} (${spell.baseCost + member.level}/${member.mana.current})"
                },
            ),
            text.spellAbort,
        )

    fun stillFace(
        text: BattleTextModel,
        encounter: BattleMonsterState,
    ): TextBlock =
        combineTextBlocks(
            text.stillFace,
            text.foeDisplayName.withBattleValues(TextPart.BattleFoeDisplayName to encounter.foeDisplayName()),
            text.fight,
            text.run,
        )

    fun attackHit(
        text: BattleTextModel,
        attackerName: String,
        damage: Int,
        killed: Boolean,
    ): List<TextBlock> =
        buildList {
            add(
                text.playerHit.withBattleValues(
                    TextPart.BattleAttackerName to attackerName,
                    TextPart.BattleDamage to damage.toString(),
                ),
            )
            if (killed) add(text.killed)
        }

    fun attackMiss(
        text: BattleTextModel,
        attackerName: String,
    ): TextBlock = text.playerMiss.withBattleValues(TextPart.BattleAttackerName to attackerName)

    fun monsterHit(
        text: BattleTextModel,
        monsterName: String,
        targetName: String,
        damage: Int,
        killed: Boolean,
    ): List<TextBlock> =
        buildList {
            add(
                text.monsterHit.withBattleValues(
                    TextPart.BattleAttackerName to monsterName,
                    TextPart.BattleTargetName to targetName,
                    TextPart.BattleDamage to damage.toString(),
                ),
            )
            if (killed) add(text.memberDeath.withBattleValues(TextPart.BattleTargetName to targetName))
        }

    fun monsterMiss(
        text: BattleTextModel,
        monsterName: String,
        targetName: String,
    ): TextBlock =
        text.monsterMiss.withBattleValues(
            TextPart.BattleAttackerName to monsterName,
            TextPart.BattleTargetName to targetName,
        )

    fun monsterCasts(monsterName: String): TextBlock = inlineText("$monsterName casts")

    fun victory(
        text: BattleTextModel,
        experience: Int,
        gold: Int,
        lootName: String?,
        levelGains: List<BattleLevelGain>,
    ): List<TextBlock> =
        buildList {
            add(text.victoryBested)
            add(
                combineTextBlocks(
                    text.victoryEachSurvivor,
                    text.victoryReward.withBattleValues(
                        TextPart.BattleExperience to experience.toString(),
                        TextPart.BattleGold to gold.toString(),
                    ),
                ),
            )
            lootName?.let {
                add(
                    combineTextBlocks(
                        text.victoryFoundObject,
                        text.victoryLoot.withBattleValues(TextPart.BattleLootName to it),
                    ),
                )
            }
            levelGains.forEach { gain ->
                add(
                    text.levelGain.withBattleValues(
                        TextPart.BattleLevelMemberName to gain.memberName,
                        TextPart.BattleLevelsGained to gain.levels.toString(),
                    ),
                )
            }
        }

    private fun TextBlock.withBattleValues(vararg values: Pair<TextPart, String>): TextBlock = withBattleValues(values.toMap())

    private fun TextBlock.withBattleValues(values: Map<TextPart, String>): TextBlock =
        when (this) {
            is TextBlock.Inline,
            is TextBlock.Ref,
            -> this
            is TextBlock.Structured ->
                TextBlock.Inline(
                    lines.map { line ->
                        line.parts.joinToString(separator = "") { part ->
                            values[part] ?: part.fallbackText()
                        }
                    },
                )
        }
}

private fun RuntimeSessionState.drawAliveMemberIndex(): RuntimeRandomDraw {
    val alive = activeParty.aliveMemberIndexes()
    require(alive.isNotEmpty()) { "cannot choose a target from an empty living party" }
    val draw = drawRandom(0, alive.lastIndex)
    return RuntimeRandomDraw(alive[draw.value], draw.runtime)
}

private fun RuntimeSessionState.asWilderness(): RuntimeSessionState =
    withRealm(
        realm.copy(
            currentMode = RuntimeMode.Wilderness,
            currentTown = null,
        ),
    )

private fun RuntimeSessionState.withActiveMember(
    index: Int,
    member: PartyMemberState,
): RuntimeSessionState {
    val party = activeParty
    val nextMembers =
        party.members.mapIndexed { memberIndex, existing ->
            if (memberIndex == index) member else existing
        }
    return withActiveParty(party.copy(members = nextMembers))
}

private fun RuntimeSessionState.scaledEncounterHpRange(
    monster: MonsterModel,
    battle: StandardBattleModel,
    formulas: List<FormulaModel>,
): IntRange {
    val firstLevel = activeParty.members.firstOrNull()?.level ?: 0
    val partyAverage =
        activeParty.members
            .takeIf { it.isNotEmpty() }
            ?.map { it.level }
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
    val min = formulas.evalFormula(battle.encounterHitPointMinFormula, bindings)
    val max = formulas.evalFormula(battle.encounterHitPointMaxFormula, bindings)
    return min.coerceAtMost(max)..max
}

private fun BattleMonsterState.foeDisplayName(): String =
    buildString {
        append(count)
        append(' ')
        append(monster.name)
        if (count > 1) append('s')
    }

private fun BattleMonsterState.aliveCount(): Int = hitPoints.count { it > 0 }

private fun BattleMonsterState.aliveTargetKeys(): List<Char> =
    hitPoints.mapIndexedNotNull { index, hitPoints -> ('A' + index).takeIf { hitPoints > 0 } }

private fun BattleMonsterState.damage(
    index: Int,
    amount: Int,
): BattleMonsterState =
    copy(
        hitPoints =
            hitPoints.mapIndexed { monsterIndex, hitPoints ->
                if (monsterIndex == index) (hitPoints - amount).coerceAtLeast(0) else hitPoints
            },
    )

private fun PartyMemberState?.levelOrZero(): Int = this?.level ?: 0

private fun BattleRoundState.afterMonsterRound(): BattleRoundState =
    copy(
        currentMemberIndex = 0,
        monsterWeaponSkill = if (monsterConfused) monsterWeaponSkill * 2 else monsterWeaponSkill,
        monsterConfused = false,
        monstersHeld = false,
        antimagic = false,
        actionRetry = false,
    )

private fun Int.reducedBy(amount: Int): Int = if (this > amount) this - amount else this
