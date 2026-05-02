// Pure Kotlin runtime helpers for DSL systems that should work behind any
// frontend or engine bridge. These functions mutate immutable PartyState values
// and keep UI/input decisions explicit in their inputs.

enum class EquipmentKind { Weapon, Armor }
enum class EquipmentCommand { ReadyWeapon, ReadyArmor, RemoveWeapon, RemoveArmor }
enum class ShopMainChoice { Buy, Sell, Leave }

enum class SpeakMainChoice { Chitchat, Question, Rumour, Bye }

sealed interface RandomItemOfferInput {
    data class Buy(val accept: Boolean) : RandomItemOfferInput
    data class DropSlot(val slot: Int) : RandomItemOfferInput
    object Continue : RandomItemOfferInput
}

sealed interface RewardGrantInput {
    data class DropSlot(val slot: Int) : RewardGrantInput
    object Continue : RewardGrantInput
}

sealed interface EquipmentPromptInput {
    data class InventorySlot(val slot: Int) : EquipmentPromptInput
    data class Member(val memberNumber: Int) : EquipmentPromptInput
}

sealed interface SpeakInput {
    data class Npc(val npc: NpcId) : SpeakInput
    data class Main(val choice: SpeakMainChoice) : SpeakInput
    data class Question(val topic: QuestionTopic) : SpeakInput
    object Continue : SpeakInput
}

sealed interface TownLeaveEquipmentInput {
    data class ReadyAnswer(val yes: Boolean) : TownLeaveEquipmentInput
    data class Equipment(val input: EquipmentPromptInput) : TownLeaveEquipmentInput
}

sealed interface ShopServiceInput {
    object ContinueIntro : ShopServiceInput
    data class Main(val choice: ShopMainChoice) : ShopServiceInput
    data class BuyItem(val item: ItemId) : ShopServiceInput
    data class Quantity(val quantity: Int) : ShopServiceInput
    data class ConfirmPurchase(val accept: Boolean) : ShopServiceInput
    data class DropSlot(val slot: Int) : ShopServiceInput
    data class SellSlot(val slot: Int, val bargainRolls: List<Int> = emptyList()) : ShopServiceInput
    data class ConfirmSale(val accept: Boolean) : ShopServiceInput
}

sealed interface TownSessionInput {
    data class MenuKey(val key: Char) : TownSessionInput
    data class Shop(val input: ShopServiceInput) : TownSessionInput
    data class Equipment(val input: TownLeaveEquipmentInput) : TownSessionInput
    data class FinishNativeService(val runtime: RuntimeSessionState) : TownSessionInput
}

enum class BattleOpeningChoice { Fight, Run }

sealed interface BattleSessionInput {
    data class Opening(val choice: BattleOpeningChoice) : BattleSessionInput
    data class PlayerAction(val kind: CombatActionKind) : BattleSessionInput
    data class TargetMonster(val index: Int) : BattleSessionInput
    data class TargetMember(val index: Int) : BattleSessionInput
    data class InventorySlot(val slot: Int) : BattleSessionInput
    data class SpellSlot(val slot: Int) : BattleSessionInput
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
    data class Opening(val prompt: TextBlock) : BattleSessionStep
    data class RoundOpening(val prompt: TextBlock) : BattleSessionStep
    data class Message(val texts: List<TextBlock>, val next: BattleSessionStep) : BattleSessionStep
    data class PlayerAction(val memberIndex: Int, val prompt: TextBlock, val actions: List<CombatActionModel>) :
        BattleSessionStep
    data class MonsterTarget(val memberIndex: Int, val prompt: TextBlock, val targetKeys: List<Char>) :
        BattleSessionStep
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
    data class MagicItemSlot(val memberIndex: Int, val prompt: TextBlock, val slotKeys: List<Char>) :
        BattleSessionStep
    data class SpellChoice(val memberIndex: Int, val prompt: TextBlock, val spellKeys: List<Char>) :
        BattleSessionStep
    object MonsterRound : BattleSessionStep
    data class Finished(val runtime: RuntimeSessionState) : BattleSessionStep
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
    val itemRecords: List<ItemRecordModel>,
    val formulas: List<FormulaModel>,
    val rewards: List<RewardBundleModel>,
    val leveling: LevelingModel?,
    val spells: List<SpellModel>,
    val magicItems: List<MagicItemModel>,
    val tier: EncounterTier?,
    val encounter: BattleMonsterState,
    val round: BattleRoundState,
    val step: BattleSessionStep,
    val lastResult: BattleSessionResult,
) {
    fun apply(input: BattleSessionInput): BattleSession =
        when (val current = step) {
            is BattleSessionStep.Opening -> {
                val opening = (input as? BattleSessionInput.Opening)
                    ?: error("battle session is waiting for fight/run input")
                answerOpening(opening.choice)
            }
            is BattleSessionStep.RoundOpening -> {
                val opening = (input as? BattleSessionInput.Opening)
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
                val action = (input as? BattleSessionInput.PlayerAction)
                    ?: error("battle session is waiting for a player action")
                answerPlayerAction(current.memberIndex, action.kind)
            }
            is BattleSessionStep.MonsterTarget -> {
                val target = (input as? BattleSessionInput.TargetMonster)
                    ?: error("battle session is waiting for a monster target")
                require(target.index in encounter.hitPoints.indices && encounter.hitPoints[target.index] > 0) {
                    "battle target ${target.index} is not alive"
                }
                attackMonster(current.memberIndex, target.index)
            }
            is BattleSessionStep.ScriptMonsterTarget -> {
                val target = (input as? BattleSessionInput.TargetMonster)
                    ?: error("battle session is waiting for a script monster target")
                require(target.index in encounter.hitPoints.indices && encounter.hitPoints[target.index] > 0) {
                    "battle target ${target.index} is not alive"
                }
                resumeScript(current.script, current.script.locals + (current.script.pendingVar to target.index))
            }
            is BattleSessionStep.MemberTarget -> {
                val target = (input as? BattleSessionInput.TargetMember)
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
                val slot = (input as? BattleSessionInput.InventorySlot)
                    ?: error("battle session is waiting for an inventory slot")
                useMagicItem(current.memberIndex, slot.slot)
            }
            is BattleSessionStep.SpellChoice -> {
                val slot = (input as? BattleSessionInput.SpellSlot)
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
            BattleOpeningChoice.Fight -> continueToPlayerRound(
                status = BattleSessionResultStatus.FightSelected,
                text = battle.text.bloodFlow,
            )
            BattleOpeningChoice.Run -> runFromEncounter()
        }

    private fun answerRoundOpening(choice: BattleOpeningChoice): BattleSession =
        when (choice) {
            BattleOpeningChoice.Fight -> continueToPlayerRound(
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
                step = messageStep(
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
                step = messageStep(
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

    private fun continueToPlayerRound(status: BattleSessionResultStatus, text: TextBlock): BattleSession =
        copy(
            step = messageStep(text, nextPlayerActionStep(fromIndex = 0)),
            lastResult = result(status),
        )

    private fun answerPlayerAction(memberIndex: Int, kind: CombatActionKind): BattleSession =
        when (kind) {
            CombatActionKind.Attack -> copy(
                round = round.copy(currentMemberIndex = memberIndex),
                step = BattleSessionStep.MonsterTarget(
                    memberIndex = memberIndex,
                    prompt = BattlePromptText.targetPrompt(battle.text, encounter),
                    targetKeys = encounter.aliveTargetKeys(),
                ),
            )
            CombatActionKind.Defend -> defend(memberIndex)
            CombatActionKind.BattleCry -> battleCry(memberIndex)
            CombatActionKind.UseMagicItem -> copy(
                step = BattleSessionStep.MagicItemSlot(
                    memberIndex = memberIndex,
                    prompt = BattlePromptText.useWhat(battle.text, runtime.activeParty, itemRecords),
                    slotKeys = inventorySlotKeys(),
                ),
            )
            CombatActionKind.CastMageSpell -> copy(
                step = BattleSessionStep.SpellChoice(
                    memberIndex = memberIndex,
                    prompt = BattlePromptText.spellChoice(
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
        val nextRuntime = runtime.withActiveMember(
            memberIndex,
            member.copy(hitPoints = nextHitPoints of member.hitPoints.max),
        )
        val defendRules = battle.playerRound.defend
        val nextRound = round.copy(
            defendCount = (round.defendCount + 1).coerceAtMost(defendRules.cap),
            monsterWeaponSkill = round.monsterWeaponSkill.reducedBy(defendRules.weaponSkillPenalty),
        )
        return copy(runtime = nextRuntime, round = nextRound).copy(
            step = messageStep(
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
        val nextRound = round.copy(
            monsterWeaponSkill = round.monsterWeaponSkill.reducedBy(battle.playerRound.battleCry.weaponSkillPenalty),
        )
        return copy(round = nextRound).copy(
            step = messageStep(battle.text.battleCry, nextAfterMemberAction(memberIndex, encounter)),
        )
    }

    private fun useMagicItem(memberIndex: Int, slot: Int): BattleSession {
        val item = runtime.activeParty.inventoryItem(slot)
        val model = item?.let { itemId ->
            magicItems.firstOrNull { use ->
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

        val consumedRuntime = if (model.consume == ItemConsumePolicy.BeforeUse) {
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

    private fun castMageSpell(memberIndex: Int, slot: Int): BattleSession {
        if (slot == 7) return copy(step = playerActionStep(memberIndex))

        val spell = mageSpells().firstOrNull { it.slot == slot }
            ?: return copy(step = messageStep(battle.text.notPossible, playerActionStep(memberIndex)))
        if (encounter.monster.immune) {
            return copy(step = messageStep(battle.text.spellFizzle, nextAfterMemberAction(memberIndex, encounter)))
        }

        val caster = runtime.activeParty.members[memberIndex]
        val cost = spell.baseCost + caster.level
        if (caster.mana.current < cost) {
            return copy(
                step = messageStep(
                    battle.text.notEnoughMagic,
                    nextAfterMemberAction(memberIndex, encounter),
                ),
            )
        }

        val nextCaster = caster.copy(mana = (caster.mana.current - cost) manaOf caster.mana.max)
        val nextRuntime = runtime.withActiveMember(memberIndex, nextCaster)
        return copy(runtime = nextRuntime).beginScript(
            memberIndex = memberIndex,
            sourceKind = CombatScriptSourceKind.Spell,
            sourceSlug = spell.id.slug,
            ops = spell.ops,
            locals = mapOf("caster_idx" to memberIndex),
        )
    }

    private fun attackMonster(memberIndex: Int, targetIndex: Int): BattleSession {
        val member = runtime.activeParty.members[memberIndex]
        val hitDraw = runtime.drawRandom(0, 99)
        val skill = if (round.partyConfused) {
            member.weaponSkill / battle.playerRound.attack.confusionDivisor
        } else {
            member.weaponSkill
        }
        val hitChance = (skill + battle.playerRound.attack.hitBonus).coerceIn(0, 100)
        return if (hitDraw.value < hitChance) {
            val damageDraw = hitDraw.runtime.drawRandom(0, battle.playerRound.attack.damageSpreadMax)
            val damage = member.weaponDamage() +
                damageDraw.value +
                member.hitPoints.current / battle.playerRound.attack.hpDefenseDivisor
            val nextEncounter = encounter.damage(targetIndex, damage)
            val killed = encounter.hitPoints[targetIndex] > 0 && nextEncounter.hitPoints[targetIndex] == 0
            copy(runtime = damageDraw.runtime, encounter = nextEncounter).copy(
                step = messageStep(
                    BattlePromptText.attackHit(
                        text = battle.text,
                        attackerName = member.name,
                        damage = damage,
                        killed = killed,
                    ),
                    nextAfterMemberAction(memberIndex, nextEncounter),
                ),
                lastResult = if (nextEncounter.aliveCount() == 0) {
                    result(BattleSessionResultStatus.Victory)
                } else {
                    lastResult
                },
            )
        } else {
            copy(runtime = hitDraw.runtime).copy(
                step = messageStep(
                    BattlePromptText.attackMiss(member.name),
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

    private fun resumeScript(script: CombatScriptContinuation, locals: Map<String, Int>): BattleSession =
        runScript(script.copy(locals = locals, pendingVar = ""))

    private fun runScript(script: CombatScriptContinuation): BattleSession {
        val result = CombatScriptRunner(
            initialRuntime = runtime,
            initialEncounter = encounter,
            initialRound = round,
            itemRecords = itemRecords,
        ).run(script)
        val base = copy(runtime = result.runtime, encounter = result.encounter, round = result.round)
        return when (result) {
            is CombatScriptRunResult.Done -> base.finishScript(script, result.texts)
            is CombatScriptRunResult.NeedMonster -> base.copy(
                step = messageStep(
                    result.texts,
                    BattleSessionStep.ScriptMonsterTarget(
                        prompt = result.prompt,
                        targetKeys = result.encounter.aliveTargetKeys(),
                        script = result.continuation,
                    ),
                ),
            )
            is CombatScriptRunResult.NeedMember -> base.copy(
                step = messageStep(
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

    private fun finishScript(script: CombatScriptContinuation, texts: List<TextBlock>): BattleSession {
        val memberIndex = script.memberIndex ?: return copy(step = messageStep(texts, BattleSessionStep.MonsterRound))
        val actionRetry = round.actionRetry
        val nextRound = if (actionRetry) round.copy(actionRetry = false) else round
        val next = if (actionRetry) {
            playerActionStep(memberIndex)
        } else {
            nextAfterMemberAction(memberIndex, encounter)
        }
        return copy(
            round = nextRound,
            step = messageStep(texts, next),
            lastResult = if (encounter.aliveCount() == 0) {
                result(BattleSessionResultStatus.Victory)
            } else {
                lastResult
            },
        )
    }

    private fun nextAfterMemberAction(memberIndex: Int, nextEncounter: BattleMonsterState): BattleSessionStep =
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
                    prompt = BattlePromptText.playerActionPrompt(
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
            val lootName = victory.loot?.let { item ->
                itemRecords.firstOrNull { it.id == item }?.displayName ?: item.slug
            }
            BattleSessionStep.Message(
                texts = BattlePromptText.victory(
                    battle.text,
                    victory.experience,
                    victory.gold,
                    lootName,
                    victory.levelGains,
                ),
                next = BattleSessionStep.Finished(victory.runtime.asWilderness()),
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
                    val cast = castMonsterSpell(
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
                        val damage = (hitPoints / battle.monsterRound.melee.damageDivisor + damageDraw.value - armor)
                            .coerceAtLeast(battle.monsterRound.melee.damageMin)
                        val nextHp = (target.hitPoints.current - damage).coerceAtLeast(0)
                        val nextTarget = target.copy(
                            hitPoints = nextHp of target.hitPoints.max,
                            dead = target.dead || nextHp == 0,
                        )
                        nextRuntime = nextRuntime.withActiveMember(targetIndex, nextTarget)
                        texts += BattlePromptText.monsterHit(nextEncounter.monster.name, target.name, damage, nextHp == 0)
                    } else {
                        texts += BattlePromptText.monsterMiss(nextEncounter.monster.name, target.name)
                    }
                }
            }
        }

        if (nextRuntime.activeParty.aliveMembers().isEmpty()) {
            val gameOver = nextRuntime.withRealm(nextRuntime.realm.copy(currentMode = RuntimeMode.GameOver))
            return copy(
                runtime = gameOver,
                round = nextRound,
                step = BattleSessionStep.Finished(gameOver),
                lastResult = result(BattleSessionResultStatus.PartyDefeated),
            )
        }

        nextRound = nextRound.afterMonsterRound()
        val nextStep = if (nextRound.partyHeld) {
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

    private fun shouldMonsterCast(runtime: RuntimeSessionState, round: BattleRoundState): RuntimeBooleanDraw {
        if (round.antimagic || encounter.monster.spellPower <= 0 || monsterSpells().isEmpty()) {
            return RuntimeBooleanDraw(false, runtime)
        }
        val chance = battle.monsterRound.spellChanceBasePercent +
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
        val script = CombatScriptContinuation(
            memberIndex = null,
            sourceKind = CombatScriptSourceKind.MonsterSpell,
            sourceSlug = spell.id.slug,
            ops = spell.ops,
            nextIndex = 0,
            pendingVar = "",
            locals = mapOf(
                "caster_idx" to monsterIndex,
                "attacker_idx" to monsterIndex,
            ),
        )
        return when (val result = CombatScriptRunner(pick.runtime, encounter, round, itemRecords).run(script)) {
            is CombatScriptRunResult.Done -> BattleScriptEffect(
                runtime = result.runtime,
                encounter = result.encounter,
                round = result.round,
                texts = result.texts,
            )
            is CombatScriptRunResult.NeedMember,
            is CombatScriptRunResult.NeedMonster -> error("monster combat spell ${spell.id.slug} cannot ask the player")
        }
    }

    private fun applyVictory(nextEncounter: BattleMonsterState): BattleVictoryResult {
        val reward = rewards.firstOrNull { it.id == battle.rewards }
        val bindings = mapOf(
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
        val partyBeforeLevelUp = rewardRuntime.activeParty
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

    private fun finish(status: BattleSessionResultStatus): BattleSession {
        val nextRuntime = runtime.asWilderness()
        return copy(
            runtime = nextRuntime,
            step = BattleSessionStep.Finished(nextRuntime),
            lastResult = result(status),
        )
    }

    private fun result(status: BattleSessionResultStatus): BattleSessionResult =
        BattleSessionResult(status, encounter.monster.id, tier)

    companion object {
        fun start(
            runtime: RuntimeSessionState,
            combat: CombatSystemModel,
            monsters: List<MonsterModel>,
            tier: EncounterTier,
            mandatory: Boolean = false,
            itemRecords: List<ItemRecordModel> = ItemCatalog.all,
            formulas: List<FormulaModel> = emptyList(),
            rewards: List<RewardBundleModel> = emptyList(),
            leveling: LevelingModel? = null,
            spells: List<SpellModel> = emptyList(),
            magicItems: List<MagicItemModel> = emptyList(),
        ): BattleSession {
            requireNotNull(combat.standardBattle) { "standard battle is not configured" }
            val candidates = monsters.filter { it.tier == tier }
            require(candidates.isNotEmpty()) { "no monsters declared for encounter tier ${tier.slug}" }

            val pick = runtime.drawRandom(0, candidates.lastIndex)
            val monster = candidates[pick.value]
            return startMonster(
                runtime = pick.runtime,
                combat = combat,
                monster = monster,
                tier = tier,
                mandatory = mandatory,
                itemRecords = itemRecords,
                formulas = formulas,
                rewards = rewards,
                leveling = leveling,
                spells = spells,
                magicItems = magicItems,
            )
        }

        fun startMonster(
            runtime: RuntimeSessionState,
            combat: CombatSystemModel,
            monster: MonsterModel,
            tier: EncounterTier? = monster.tier,
            mandatory: Boolean = false,
            itemRecords: List<ItemRecordModel> = ItemCatalog.all,
            formulas: List<FormulaModel> = emptyList(),
            rewards: List<RewardBundleModel> = emptyList(),
            leveling: LevelingModel? = null,
            spells: List<SpellModel> = emptyList(),
            magicItems: List<MagicItemModel> = emptyList(),
        ): BattleSession {
            val battle = requireNotNull(combat.standardBattle) { "standard battle is not configured" }
            val firstLevel = runtime.activeParty.members.firstOrNull().levelOrZero()
            val countMax = monster.count.last + (monster.countMaxLevelDivisor?.let { firstLevel / it } ?: 0)
            val countDraw = runtime.drawRandom(1, countMax)
            val count = countDraw.value.coerceAtLeast(monster.count.first).coerceAtMost(battle.encounterFoeCountMax)
            val hpRange = countDraw.runtime.scaledEncounterHpRange(monster, formulas)
            var hpRuntime = countDraw.runtime
            val hitPoints = buildList {
                repeat(count) {
                    val hpDraw = hpRuntime.drawRandom(1, hpRange.last)
                    hpRuntime = hpDraw.runtime
                    add(hpDraw.value.coerceAtLeast(hpRange.first))
                }
            }
            val nextRuntime = hpRuntime.withRealm(
                hpRuntime.realm.copy(
                    currentMode = RuntimeMode.Battle,
                    currentTown = null,
                ),
            )
            val encounter = BattleMonsterState(monster, count, hitPoints, hpRange.last, mandatory)
            return BattleSession(
                runtime = nextRuntime,
                battle = battle,
                itemRecords = itemRecords,
                formulas = formulas,
                rewards = rewards,
                leveling = leveling,
                spells = spells,
                magicItems = magicItems,
                tier = tier,
                encounter = encounter,
                round = BattleRoundState(
                    currentMemberIndex = 0,
                    defendCount = 0,
                    monsterWeaponSkill = monster.weaponSkill + nextRuntime.activeParty.members.firstOrNull().levelOrZero(),
                ),
                step = BattleSessionStep.Opening(BattlePromptText.opening(battle.text, encounter)),
                lastResult = BattleSessionResult(BattleSessionResultStatus.EncounterStarted, monster.id, tier),
            )
        }
    }

    private fun messageStep(text: TextBlock, next: BattleSessionStep): BattleSessionStep.Message =
        BattleSessionStep.Message(listOf(text), next)

    private fun messageStep(texts: List<TextBlock>, next: BattleSessionStep): BattleSessionStep.Message =
        BattleSessionStep.Message(texts, next)

    private fun mageSpells(): List<SpellModel> =
        spells.filter { it.id.slug.startsWith("mage.") }.sortedBy { it.slot }

    private fun monsterSpells(): List<SpellModel> =
        spells.filter { it.id.slug.startsWith("monster.") && it.id != Spells.Monster.melee }.sortedBy { it.slot }

    private fun inventorySlotKeys(): List<Char> =
        (1..InventorySaveSlotMax).map { slot -> 'A' + slot - 1 }

    private fun itemRecord(item: ItemId): ItemRecordModel =
        itemRecords.firstOrNull { it.id == item } ?: error("missing item record ${item.slug}")

    private fun PartyMemberState.weaponDamage(): Int =
        itemRecords.firstOrNull { it.id == weapon }?.weaponDamage ?: 1
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
    private val itemRecords: List<ItemRecordModel>,
) {
    private var runtime = initialRuntime
    private var encounter = initialEncounter
    private var round = initialRound
    private var locals = emptyMap<String, Int>()
    private val texts = mutableListOf<TextBlock>()

    private val evaluator = object : IntAstEvaluator() {
        override fun local(name: String): Int = locals[name] ?: 0
        override fun random(lo: Int, hi: Int): Int {
            val draw = runtime.drawRandom(lo, hi)
            runtime = draw.runtime
            return draw.value
        }
        override fun memberField(member: Int, field: String): Int = readMemberField(member, field)
        override fun monsterField(monster: Int, field: String): Int = readMonsterField(monster, field)
        override fun battleFlag(name: String): Int = readBattleFlag(name)
        override fun battleQuery(name: String): Int = readBattleQuery(name)
        override fun realmScalar(name: String): Int = readRealmScalar(name)
        override fun partyScalar(name: String): Int = readPartyScalar(name)
        override fun memberFlag(member: Int, name: String): Boolean = readMemberFlag(member, name)
        override fun monsterFlag(monster: Int, name: String): Boolean = readMonsterFlag(monster, name)
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
                is Op.AskMonster -> return needMonster(start, index, op)
                is Op.AskMember -> return needMember(start, index, op)
                Op.ConsumeActiveItem -> start.activeInventorySlot?.let { slot ->
                    runtime = runtime.withActiveParty(runtime.activeParty.withInventoryItem(slot, null))
                }
                is Op.NativeCall -> Unit
                is Op.SetFlag,
                is Op.AppendJournal,
                is Op.Loop,
                is Op.Break,
                is Op.Stop,
                is Op.Switch,
                is Op.Menu,
                is Op.AskInventorySlot,
                is Op.Speak,
                is Op.RunScene,
                is Op.Encounter,
                is Op.EnterDungeon,
                Op.ConsumeTorch,
                is Op.GrantReward,
                is Op.RandomItemOffer,
                is Op.ShopService,
                is Op.AchieveMilestone -> unsupportedCombatOp(op)
            }
            index++
        }
        return CombatScriptRunResult.Done(runtime, encounter, round, texts.toList())
    }

    private fun runNested(ops: List<Op>, parent: CombatScriptContinuation) {
        val nested = run(parent.copy(ops = ops, nextIndex = 0, locals = locals))
        runtime = nested.runtime
        encounter = nested.encounter
        round = nested.round
        locals = when (nested) {
            is CombatScriptRunResult.Done -> locals
            is CombatScriptRunResult.NeedMember,
            is CombatScriptRunResult.NeedMonster -> error("nested combat target prompts are not supported yet")
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
            prompt = TextBlock.Ref(op.prompt),
            continuation = script.copy(nextIndex = index + 1, pendingVar = op.varName, locals = locals),
        )

    private fun needMember(
        script: CombatScriptContinuation,
        index: Int,
        op: Op.AskMember,
    ): CombatScriptRunResult.NeedMember {
        val keys = runtime.activeParty.members.mapIndexedNotNull { memberIndex, member ->
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
            is TextBlock.Inline -> block
            is TextBlock.Structured -> TextBlock.Inline(
                block.lines.map { line ->
                    line.parts.joinToString(separator = "") { part ->
                        when (part) {
                            is TextPart.Literal -> part.text
                            is TextPart.IntValue -> evaluator.int(part.value).toString()
                            is TextPart.StringValue -> evalString(part.value)
                            is TextPart.RoleName -> "{${part.role.slug}.name}"
                            is TextPart.RoleLocation -> "{${part.role.slug}.location}"
                            is TextPart.TownName -> part.town.slug
                            is TextPart.FuzzyTownLocation -> "${part.town.slug}.near"
                            is TextPart.LocationCoordinate -> "${part.location.slug}.coordinates"
                            TextPart.OfferedItemName -> "{item_name}"
                            TextPart.OfferedItemPrice -> "{item_price}"
                            TextPart.ShopPrice -> "{price}"
                            TextPart.ShopCarryCap -> "{cap}"
                        }
                    }
                },
            )
        }

    private fun evalString(ast: StrAst): String =
        when (ast) {
            is StrAst.NativeString -> when (ast.path) {
                NativeAllowlist.partyMemberName.path ->
                    runtime.activeParty.members.getOrNull(evalStringArg(ast.args.single()))?.name.orEmpty()
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

    private fun pronoun(memberIndex: Int, case: PronounCase): String {
        val gender = runtime.activeParty.members.getOrNull(memberIndex)?.gender ?: Gender.Male
        return when (case) {
            PronounCase.Subject -> if (gender == Gender.Female) "she" else "he"
            PronounCase.Object -> if (gender == Gender.Female) "her" else "him"
            PronounCase.Possessive -> if (gender == Gender.Female) "her" else "his"
        }
    }

    private fun read(target: LValueAst): Int =
        when (target) {
            is LValueAst.MemberField -> readMemberField(evaluator.int(target.member), target.field)
            is LValueAst.MonsterField -> readMonsterField(evaluator.int(target.monster), target.field)
            is LValueAst.BattleFlag -> readBattleFlag(target.name)
            is LValueAst.PartyScalar -> readPartyScalar(target.name)
            is LValueAst.RealmScalar -> readRealmScalar(target.name)
        }

    private fun write(target: LValueAst, value: Int) {
        when (target) {
            is LValueAst.MemberField -> writeMemberField(evaluator.int(target.member), target.field, value)
            is LValueAst.MonsterField -> writeMonsterField(evaluator.int(target.monster), target.field, value)
            is LValueAst.BattleFlag -> writeBattleFlag(target.name, value)
            is LValueAst.PartyScalar -> writePartyScalar(target.name, value)
            is LValueAst.RealmScalar -> writeRealmScalar(target.name, value)
        }
    }

    private fun readMemberField(index: Int, field: String): Int {
        val member = runtime.activeParty.members.getOrNull(index) ?: return 0
        return when (field) {
            "hit_points" -> member.hitPoints.current
            "health" -> member.health
            "magic" -> member.mana.current
            "level" -> member.level
            "wisdom" -> member.wisdom
            "charisma" -> member.charisma
            "weapon_skill" -> member.weaponSkill
            "class" -> member.pcClass.sourceId
            "dead" -> if (member.dead) 1 else 0
            else -> error("unknown battle member field $field")
        }
    }

    private fun writeMemberField(index: Int, field: String, value: Int) {
        val member = runtime.activeParty.members.getOrNull(index) ?: return
        val next = when (field) {
            "hit_points" -> member.copy(hitPoints = value.coerceAtLeast(0) of member.hitPoints.max)
            "health" -> member.copy(health = value.coerceAtLeast(0), hitPoints = member.hitPoints.current of value.coerceAtLeast(0))
            "magic" -> member.copy(mana = value.coerceAtLeast(0) manaOf member.mana.max)
            "level" -> member.copy(level = value.coerceAtLeast(1))
            "wisdom" -> member.copy(wisdom = value.coerceAtLeast(0))
            "charisma" -> member.copy(charisma = value.coerceAtLeast(0))
            "weapon_skill" -> member.copy(weaponSkill = value.coerceAtLeast(0))
            "dead" -> member.copy(dead = value != 0)
            else -> error("unknown battle member field $field")
        }
        val updated = runtime.withActiveMember(index, next)
        runtime = if (field == "dead") {
            updated.withActiveParty(updated.activeParty.withLivingCount())
        } else {
            updated
        }
    }

    private fun readMonsterField(index: Int, field: String): Int =
        when (field) {
            "hit_points" -> encounter.hitPoints.getOrElse(index) { 0 }
            "dead" -> if (encounter.hitPoints.getOrElse(index) { 0 } <= 0) 1 else 0
            else -> error("unknown battle monster field $field")
        }

    private fun writeMonsterField(index: Int, field: String, value: Int) {
        when (field) {
            "hit_points" -> encounter = encounter.copy(
                hitPoints = encounter.hitPoints.mapIndexed { monster, hp ->
                    if (monster == index) value.coerceAtLeast(0) else hp
                },
            )
            "dead" -> if (value != 0) {
                encounter = encounter.copy(
                    hitPoints = encounter.hitPoints.mapIndexed { monster, hp ->
                        if (monster == index) 0 else hp
                    },
                )
            }
            else -> error("unknown battle monster field $field")
        }
    }

    private fun readBattleFlag(name: String): Int =
        when (name) {
            "monster_weapon_skill" -> round.monsterWeaponSkill
            "encounter_hit_point_max" -> encounter.hitPointMax
            "monster_confused" -> if (round.monsterConfused) 1 else 0
            "monsters_held" -> if (round.monstersHeld) 1 else 0
            "antimagic" -> if (round.antimagic) 1 else 0
            "party_held" -> if (round.partyHeld) 1 else 0
            "party_confused" -> if (round.partyConfused) 1 else 0
            "action_retry" -> if (round.actionRetry) 1 else 0
            else -> error("unknown battle flag $name")
        }

    private fun writeBattleFlag(name: String, value: Int) {
        round = when (name) {
            "monster_weapon_skill" -> round.copy(monsterWeaponSkill = value.coerceAtLeast(0))
            "monster_confused" -> round.copy(monsterConfused = value != 0)
            "monsters_held" -> round.copy(monstersHeld = value != 0)
            "antimagic" -> round.copy(antimagic = value != 0)
            "party_held" -> round.copy(partyHeld = value != 0)
            "party_confused" -> round.copy(partyConfused = value != 0)
            "action_retry" -> round.copy(actionRetry = value != 0)
            "encounter_hit_point_max" -> error("encounter hit point max is read-only")
            else -> error("unknown battle flag $name")
        }
    }

    private fun readBattleQuery(name: String): Int =
        when (name) {
            "first_alive_monster" -> encounter.hitPoints.indexOfFirst { it > 0 }
            "random_alive_member" -> {
                val draw = runtime.drawAliveMemberIndex()
                runtime = draw.runtime
                draw.value
            }
            else -> error("unknown battle query $name")
        }

    private fun readMemberFlag(index: Int, name: String): Boolean =
        when (name) {
            "dead" -> runtime.activeParty.members.getOrNull(index)?.dead ?: true
            "spellcaster" -> runtime.activeParty.members.getOrNull(index)
                ?.let { !it.dead && it.pcClass in setOf(PcClass.Mage, PcClass.Priest) }
                ?: false
            else -> error("unknown battle member flag $name")
        }

    private fun readMonsterFlag(index: Int, name: String): Boolean =
        when (name) {
            "dead" -> encounter.hitPoints.getOrElse(index) { 0 } <= 0
            else -> error("unknown battle monster flag $name")
        }

    private fun readPartyScalar(name: String): Int =
        when (name) {
            "gold" -> runtime.activeParty.resources.gold
            else -> error("combat scripts cannot read party scalar $name yet")
        }

    private fun writePartyScalar(name: String, value: Int) {
        when (name) {
            "gold" -> runtime = runtime.withActiveParty(runtime.activeParty.withGold(value))
            else -> error("combat scripts cannot write party scalar $name yet")
        }
    }

    private fun readRealmScalar(name: String): Int =
        when (name) {
            "party_member_max" -> runtime.realm.partyMemberMax
            else -> error("combat scripts cannot read realm scalar $name yet")
        }

    private fun writeRealmScalar(name: String, value: Int): Unit =
        error("combat scripts cannot write realm scalar $name yet")

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

abstract class IntAstEvaluator {
    fun int(ast: IntAst): Int =
        when (ast) {
            is IntAst.Lit -> ast.value
            is IntAst.Var -> local(ast.name)
            is IntAst.Binary -> evalBinary(int(ast.left), ast.op, int(ast.right))
            is IntAst.If -> if (bool(ast.cond)) int(ast.thenValue) else int(ast.elseValue)
            is IntAst.Random -> random(int(ast.lo), int(ast.hi))
            is IntAst.MemberField -> memberField(int(ast.member), ast.field)
            is IntAst.MonsterField -> monsterField(int(ast.monster), ast.field)
            is IntAst.BattleFlag -> battleFlag(ast.name)
            is IntAst.BattleQuery -> battleQuery(ast.name)
            is IntAst.RealmScalar -> realmScalar(ast.name)
            is IntAst.PartyScalar -> partyScalar(ast.name)
            is IntAst.NativeInt -> nativeInt(ast.path, ast.args)
        }

    fun bool(ast: BoolAst): Boolean =
        when (ast) {
            is BoolAst.Cmp -> compare(int(ast.left), ast.op, int(ast.right))
            is BoolAst.Not -> !bool(ast.expr)
            is BoolAst.And -> bool(ast.left) && bool(ast.right)
            is BoolAst.Or -> bool(ast.left) || bool(ast.right)
            is BoolAst.FlagIs -> flagIs(ast.slug, ast.nativeValue)
            is BoolAst.MemberFlag -> memberFlag(int(ast.member), ast.name)
            is BoolAst.MonsterFlag -> monsterFlag(int(ast.monster), ast.name)
            is BoolAst.BattleFlag -> battleFlag(ast.name) != 0
        }

    protected open fun local(name: String): Int = unsupported("local $name")
    protected open fun random(lo: Int, hi: Int): Int = unsupported("random")
    protected open fun memberField(member: Int, field: String): Int = unsupported("member field $field")
    protected open fun monsterField(monster: Int, field: String): Int = unsupported("monster field $field")
    protected open fun battleFlag(name: String): Int = unsupported("battle flag $name")
    protected open fun battleQuery(name: String): Int = unsupported("battle query $name")
    protected open fun realmScalar(name: String): Int = unsupported("realm scalar $name")
    protected open fun partyScalar(name: String): Int = unsupported("party scalar $name")
    protected open fun nativeInt(path: String, args: List<String>): Int = unsupported("native int $path")
    protected open fun flagIs(slug: String, native: String): Boolean = unsupported("flag $slug")
    protected open fun memberFlag(member: Int, name: String): Boolean = unsupported("member flag $name")
    protected open fun monsterFlag(monster: Int, name: String): Boolean = unsupported("monster flag $name")

    private fun evalBinary(left: Int, op: String, right: Int): Int =
        when (op) {
            "add" -> left + right
            "sub" -> left - right
            "mul" -> left * right
            "div" -> if (right == 0) 0 else left / right
            else -> unsupported("binary op $op")
        }

    private fun compare(left: Int, op: String, right: Int): Boolean =
        when (op) {
            "eq" -> left == right
            "ne" -> left != right
            "lt" -> left < right
            "lte" -> left <= right
            "gt" -> left > right
            "gte" -> left >= right
            else -> unsupported("comparison op $op")
        }

    protected fun unsupported(what: String): Nothing =
        error("expression cannot read $what in this runtime context")
}

fun List<FormulaModel>.evalFormula(id: FormulaId, bindings: Map<String, Int>): Int {
    val formula = firstOrNull { it.id == id } ?: error("missing formula ${id.slug}")
    val evaluator = object : IntAstEvaluator() {
        override fun local(name: String): Int =
            bindings[name] ?: error("formula ${id.slug} requires binding $name")
    }
    return evaluator.int(formula.value)
}

private fun selectBattleLoot(runtime: RuntimeSessionState, reward: RewardBundleModel): BattleLootSelection {
    if (runtime.activeParty.firstFreeInventorySlot() == null) {
        return BattleLootSelection(runtime, null)
    }

    val dropDraw = runtime.drawRandom(0, 99)
    if (dropDraw.value >= reward.loot.dropChancePercent) {
        return BattleLootSelection(dropDraw.runtime, null)
    }

    val treasureDraw = dropDraw.runtime.drawRandom(0, 99)
    val pool = if (treasureDraw.value < reward.loot.treasureChancePercent) {
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

private fun PartyState.withKills(count: Int): PartyState =
    copy(counters = counters.copy(kills = counters.kills + count))

private fun PartyState.withExperienceForSurvivors(amount: Int): PartyState =
    copy(
        members = members.map { member ->
            if (member.dead) member else member.copy(experience = member.experience + amount)
        },
    )

private fun PartyState.withLivingCount(): PartyState =
    copy(counters = counters.copy(memberCount = members.count { !it.dead }))

private fun PartyMemberState.matchesCombatFilter(filter: MemberChoiceFilter): Boolean =
    when (filter) {
        MemberChoiceFilter.Any -> true
        MemberChoiceFilter.AliveOnly -> !dead
        MemberChoiceFilter.DeadOnly -> dead
        MemberChoiceFilter.SpellcasterOnly -> !dead && pcClass in setOf(PcClass.Mage, PcClass.Priest)
    }

private fun PartyState.applyLevelUps(leveling: LevelingModel?, formulas: List<FormulaModel>): PartyLevelUpResult {
    leveling ?: return PartyLevelUpResult(this, emptyList())
    val growthByClass = leveling.classGrowth.associateBy { it.klass }
    val gains = mutableListOf<BattleLevelGain>()
    val nextParty = copy(
        members = members.map { member ->
            val gained = ((member.level + 1)..leveling.maxLevel).count { targetLevel ->
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

private fun PartyState.placeBattleLoot(item: ItemId): PartyState =
    firstFreeInventorySlot()?.let { withInventoryItem(it, item) } ?: this

private fun PartyState.aliveMembers(): List<PartyMemberState> =
    members.filter { !it.dead }

private fun PartyState.aliveMemberIndexes(): List<Int> =
    members.mapIndexedNotNull { index, member -> index.takeUnless { member.dead } }

data class RuntimeSessionState(
    val realm: RealmState,
    val parties: List<PartyState>,
    val npcs: List<NpcPlacement> = emptyList(),
    val locations: List<LocationPlacement> = emptyList(),
) {
    val activeParty: PartyState
        get() = parties.firstOrNull { party -> party.id == realm.activeParty }
            ?: error("active party ${realm.activeParty.slug} is missing from runtime state")

    fun withRealm(realm: RealmState): RuntimeSessionState =
        copy(realm = realm)

    fun withActiveParty(party: PartyState): RuntimeSessionState {
        val nextParties = if (parties.any { it.id == party.id }) {
            parties.map { existing -> if (existing.id == party.id) party else existing }
        } else {
            parties + party
        }
        return copy(
            realm = realm.copy(activeParty = party.id),
            parties = nextParties,
        )
    }

    companion object {
        fun singleParty(
            party: PartyState,
            hour: Int,
            currentTown: TownId?,
            rngState: Long? = null,
            npcs: List<NpcPlacement> = emptyList(),
            locations: List<LocationPlacement> = emptyList(),
        ): RuntimeSessionState =
            RuntimeSessionState(
                realm = RealmState(
                    day = 0,
                    hour = hour,
                    minute = 0,
                    rngState = rngState,
                    activeParty = party.id,
                    currentMap = Maps.wilderness,
                    currentMode = RuntimeMode.Town,
                    currentTown = currentTown,
                    banished = false,
                    lastPlayedDay = 0,
                    partyMemberMax = 4,
                ),
                parties = listOf(party),
                npcs = npcs,
                locations = locations,
            )
    }
}

object BattlePromptText {
    fun opening(text: BattleTextModel, encounter: BattleMonsterState): TextBlock =
        combineTextBlocks(
            text.encounterBanner,
            text.youFace,
            inlineText(encounter.foeDisplayName(), ""),
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

    fun targetPrompt(text: BattleTextModel, encounter: BattleMonsterState): TextBlock =
        combineTextBlocks(
            text.targetPrompt,
            TextBlock.Inline(
                encounter.hitPoints.mapIndexed { index, hitPoints ->
                    "${'A' + index}. ${if (hitPoints > 0) "Alive." else "Dead."} (${hitPoints.toString().padStart(3)} hp)"
                },
            ),
        )

    fun useWhat(text: BattleTextModel, party: PartyState, itemRecords: List<ItemRecordModel>): TextBlock =
        combineTextBlocks(
            text.useWhat,
            TextBlock.Inline(
                (1..InventorySaveSlotMax).map { slot ->
                    val item = party.inventoryItem(slot)?.let { item ->
                        itemRecords.firstOrNull { it.id == item }?.displayName ?: item.slug
                    } ?: "empty"
                    "${'A' + slot - 1}. $item"
                },
            ),
        )

    fun spellChoice(text: BattleTextModel, member: PartyMemberState, spells: List<SpellModel>): TextBlock =
        combineTextBlocks(
            TextBlock.Inline(
                spells.map { spell ->
                    "${spell.slot}. ${spell.name} (${spell.baseCost + member.level}/${member.mana.current})"
                },
            ),
            text.spellAbort,
        )

    fun stillFace(text: BattleTextModel, encounter: BattleMonsterState): TextBlock =
        combineTextBlocks(
            text.stillFace,
            inlineText(encounter.foeDisplayName(), ""),
            text.fight,
            text.run,
        )

    fun attackHit(
        text: BattleTextModel,
        attackerName: String,
        damage: Int,
        killed: Boolean,
    ): List<TextBlock> {
        return buildList {
            add(inlineText("$attackerName hits for"))
            add(inlineText("$damage damage pts!"))
            if (killed) add(text.killed)
        }
    }

    fun attackMiss(attackerName: String): TextBlock =
        inlineText("$attackerName misses!")

    fun monsterHit(monsterName: String, targetName: String, damage: Int, killed: Boolean): TextBlock =
        inlineText(
            "$monsterName attacks",
            "$targetName!",
            "And hits for",
            "$damage damage pts!!!",
            if (killed) "\u001B[31m$targetName DIES!!!\u001B[37m" else "",
        )

    fun monsterMiss(monsterName: String, targetName: String): TextBlock =
        inlineText("$monsterName attacks", "$targetName!", "Blow misses!")

    fun monsterCasts(monsterName: String): TextBlock =
        inlineText("$monsterName casts")

    fun victory(
        text: BattleTextModel,
        experience: Int,
        gold: Int,
        lootName: String?,
        levelGains: List<BattleLevelGain>,
    ): List<TextBlock> =
        buildList {
            add(text.victoryBested)
            add(combineTextBlocks(text.victoryEachSurvivor, inlineText("$experience experience pts", "and $gold gold pieces.", "")))
            lootName?.let { add(combineTextBlocks(text.victoryFoundObject, inlineText("$it."))) }
            levelGains.forEach { gain ->
                add(inlineText("${gain.memberName} gains >${gain.levels}<", "experience levels."))
            }
        }
}

data class RylweenRandomDraw(
    val value: Int,
    val nextSeed: Long,
)

data class RuntimeRandomDraw(
    val value: Int,
    val runtime: RuntimeSessionState,
)

interface RylweenRandomPort {
    fun draw(seed: Long, lo: Int, hi: Int): RylweenRandomDraw
}

object ReferenceRylweenRandom : RylweenRandomPort {
    private const val Multiplier: Long = 0x2545F4914F6CDD1DL

    override fun draw(seed: Long, lo: Int, hi: Int): RylweenRandomDraw {
        require(lo <= hi) { "random range must be ascending: $lo..$hi" }
        var state = seed.takeUnless { it == 0L } ?: 1L
        state = state xor (state ushr 12)
        state = state xor (state shl 25)
        state = state xor (state ushr 27)
        val mixed = state * Multiplier
        val span = hi.toLong() - lo.toLong() + 1L
        val offset = java.lang.Long.remainderUnsigned(mixed, span)
        return RylweenRandomDraw((lo.toLong() + offset).toInt(), state)
    }
}

fun RuntimeSessionState.drawRandom(
    lo: Int,
    hi: Int,
    random: RylweenRandomPort = ReferenceRylweenRandom,
): RuntimeRandomDraw {
    require(lo <= hi) { "random range must be ascending: $lo..$hi" }
    val seed = realm.rngState ?: return RuntimeRandomDraw(lo, this)
    val draw = random.draw(seed, lo, hi)
    return RuntimeRandomDraw(draw.value, withRealm(realm.copy(rngState = draw.nextSeed)))
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

private fun RuntimeSessionState.withActiveMember(index: Int, member: PartyMemberState): RuntimeSessionState {
    val party = activeParty
    val nextMembers = party.members.mapIndexed { memberIndex, existing ->
        if (memberIndex == index) member else existing
    }
    return withActiveParty(party.copy(members = nextMembers))
}

private fun RuntimeSessionState.scaledEncounterHpRange(monster: MonsterModel, formulas: List<FormulaModel>): IntRange {
    val firstLevel = activeParty.members.firstOrNull()?.level ?: 0
    val partyAverage = activeParty.members
        .takeIf { it.isNotEmpty() }
        ?.map { it.level }
        ?.average()
        ?.toInt()
        ?: 0
    val bindings = mapOf(
        "encounter_hit_point_min_base" to monster.hitPoints.first,
        "encounter_hit_point_max_base" to monster.hitPoints.last,
        "party_avg" to partyAverage,
        "first_level" to firstLevel,
    )
    val min = formulas.evalFormula(Formulas.encounterH1Scaled, bindings)
    val max = formulas.evalFormula(Formulas.encounterH2Scaled, bindings)
    return min.coerceAtMost(max)..max
}

private fun BattleMonsterState.foeDisplayName(): String =
    buildString {
        append(count)
        append(' ')
        append(monster.name)
        if (count > 1) append('s')
    }

private fun BattleMonsterState.aliveCount(): Int =
    hitPoints.count { it > 0 }

private fun BattleMonsterState.aliveTargetKeys(): List<Char> =
    hitPoints.mapIndexedNotNull { index, hitPoints -> ('A' + index).takeIf { hitPoints > 0 } }

private fun BattleMonsterState.damage(index: Int, amount: Int): BattleMonsterState =
    copy(
        hitPoints = hitPoints.mapIndexed { monsterIndex, hitPoints ->
            if (monsterIndex == index) (hitPoints - amount).coerceAtLeast(0) else hitPoints
        },
    )

private fun PartyMemberState?.levelOrZero(): Int =
    this?.level ?: 0

private fun BattleRoundState.afterMonsterRound(): BattleRoundState =
    copy(
        currentMemberIndex = 0,
        monsterWeaponSkill = if (monsterConfused) monsterWeaponSkill * 2 else monsterWeaponSkill,
        monsterConfused = false,
        monstersHeld = false,
        antimagic = false,
        actionRetry = false,
    )

private fun Int.reducedBy(amount: Int): Int =
    if (this > amount) this - amount else this

private fun combineTextBlocks(vararg blocks: TextBlock): TextBlock =
    combineTextBlocks(blocks.toList())

private fun combineTextBlocks(blocks: List<TextBlock>): TextBlock =
    TextBlock.Structured(
        blocks.flatMap { block -> block.asStructuredLines() },
    )

private fun TextBlock.asStructuredLines(): List<TextLine> =
    when (this) {
        is TextBlock.Inline -> lines.map { line -> TextLine(listOf(TextPart.Literal(line))) }
        is TextBlock.Ref -> listOf(TextLine(listOf(TextPart.Literal("[${id.id}]"))))
        is TextBlock.Structured -> lines
    }

data class WildernessNpcPlacementPolicy(
    val x: IntRange = 8..57,
    val y: IntRange = 8..27,
    val allowedPoints: Set<GridPoint>? = null,
    val maxAttempts: Int = 1024,
) {
    init {
        require(x.first <= x.last) { "wilderness NPC x range must be ascending: $x" }
        require(y.first <= y.last) { "wilderness NPC y range must be ascending: $y" }
        require(maxAttempts > 0) { "wilderness NPC placement attempts must be positive" }
    }

    fun allows(point: GridPoint): Boolean =
        allowedPoints?.contains(point) ?: true
}

data class NpcPlacementRealizerConfig(
    val townSlack: Int = 1,
    val townBuildings: List<BuildingId> = listOf(Buildings.inn, Buildings.tavern),
    val wilderness: WildernessNpcPlacementPolicy = WildernessNpcPlacementPolicy(),
    val deterministicSeed: Long = 1L,
)

fun RuntimeSessionState.realizeNpcPlacements(
    characters: List<CharacterModel>,
    towns: List<TownModel>,
    config: NpcPlacementRealizerConfig = NpcPlacementRealizerConfig(),
    random: RylweenRandomPort = ReferenceRylweenRandom,
): RuntimeSessionState {
    require(config.townBuildings.isNotEmpty()) { "NPC placement requires at least one town building target" }
    val bins = listOf<TownId?>(null) + towns.map { it.id }
    val randomTownOrWildernessCount = characters.count { it.placement == CharacterPlacementModel.Randomized }
    val capacity = ((randomTownOrWildernessCount + bins.lastIndex) / bins.size) + config.townSlack.coerceAtLeast(0)
    val counts = IntArray(bins.size)
    val placements = mutableListOf<NpcPlacement>()
    val occupiedWilderness = characters
        .mapNotNull { (it.placement as? CharacterPlacementModel.Fixed)?.position as? WorldPosition.Wilderness }
        .mapTo(mutableSetOf()) { it.point }
    val fixtureRng = realm.rngState == null
    var runtime = if (fixtureRng) withRealm(realm.copy(rngState = config.deterministicSeed)) else this

    characters.forEach { character ->
        val position = when (val placement = character.placement) {
            is CharacterPlacementModel.Fixed -> placement.position
            CharacterPlacementModel.Randomized -> {
                val draw = runtime.drawRandom(0, bins.lastIndex, random)
                runtime = draw.runtime
                val bin = firstAvailableBin(draw.value, counts, capacity)
                counts[bin]++
                val realized = bins[bin]?.let { town -> runtime.drawTownBuilding(town, config.townBuildings, random) }
                    ?: runtime.drawWildernessHutPoint(occupiedWilderness, config.wilderness, random)
                runtime = realized.runtime
                realized.position
            }
            CharacterPlacementModel.RandomizedWilderness -> {
                val realized = runtime.drawWildernessHutPoint(occupiedWilderness, config.wilderness, random)
                runtime = realized.runtime
                realized.position
            }
        }
        placements += NpcPlacement(character.id, position)
        (position as? WorldPosition.Wilderness)?.let { occupiedWilderness += it.point }
    }

    val realizedRuntime = if (fixtureRng) runtime.withRealm(runtime.realm.copy(rngState = null)) else runtime
    return realizedRuntime.copy(npcs = placements)
}

private fun firstAvailableBin(start: Int, counts: IntArray, capacity: Int): Int {
    require(capacity > 0) { "NPC placement bin capacity must be positive" }
    var bin = start
    repeat(counts.size) {
        if (counts[bin] < capacity) return bin
        bin = (bin + 1) % counts.size
    }
    error("no NPC placement bin has remaining capacity")
}

private data class NpcPositionDraw(
    val runtime: RuntimeSessionState,
    val position: WorldPosition,
)

private fun RuntimeSessionState.drawTownBuilding(
    town: TownId,
    buildings: List<BuildingId>,
    random: RylweenRandomPort,
): NpcPositionDraw {
    val draw = drawRandom(0, buildings.lastIndex, random)
    return NpcPositionDraw(draw.runtime, WorldPosition.Town(town, buildings[draw.value]))
}

private fun RuntimeSessionState.drawWildernessHutPoint(
    occupied: Set<GridPoint>,
    policy: WildernessNpcPlacementPolicy,
    random: RylweenRandomPort,
): NpcPositionDraw {
    var runtime = this
    repeat(policy.maxAttempts) {
        val x = runtime.drawRandom(policy.x.first, policy.x.last, random)
        val y = x.runtime.drawRandom(policy.y.first, policy.y.last, random)
        runtime = y.runtime
        val point = GridPoint(x.value, y.value)
        if (point !in occupied && policy.allows(point)) {
            return NpcPositionDraw(runtime, WorldPosition.Wilderness(point))
        }
    }
    error("could not place wilderness NPC after ${policy.maxAttempts} attempts")
}

enum class TownServiceScriptBranch {
    IfThen,
    IfElse,
    LoopBody,
    ForEachMemberBody,
    ForEachMonsterBody,
    SwitchCase,
    MenuOption,
    MenuDefault,
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

sealed interface InventoryPlacementResult {
    data class Placed(val party: PartyState, val slot: Int) : InventoryPlacementResult
    data class NeedsDropSlot(val party: PartyState) : InventoryPlacementResult
    data class Full(val party: PartyState) : InventoryPlacementResult
    data class InvalidDropSlot(val party: PartyState, val slot: Int) : InventoryPlacementResult
}

sealed interface EquipmentActionResult {
    data class Equipped(val party: PartyState, val memberNumber: Int, val inventorySlot: Int) : EquipmentActionResult
    data class Unequipped(val party: PartyState, val memberNumber: Int, val inventorySlot: Int) : EquipmentActionResult
    data class InvalidMember(val party: PartyState, val memberNumber: Int) : EquipmentActionResult
    data class DeadMember(val party: PartyState, val memberNumber: Int) : EquipmentActionResult
    data class InvalidInventorySlot(val party: PartyState, val slot: Int) : EquipmentActionResult
    data class WrongItemKind(val party: PartyState, val slot: Int, val item: ItemId?) : EquipmentActionResult
    data class NothingEquipped(val party: PartyState, val memberNumber: Int, val kind: EquipmentKind) : EquipmentActionResult
    data class InventoryFull(val party: PartyState) : EquipmentActionResult
}

sealed interface TorchActionResult {
    data class Consumed(val party: PartyState) : TorchActionResult
    data class NoTorches(val party: PartyState) : TorchActionResult
}

sealed interface DungeonDescentResult {
    data class Descended(val party: PartyState) : DungeonDescentResult
    data class NoTorches(val party: PartyState) : DungeonDescentResult
    data class AtMaxDepth(val party: PartyState) : DungeonDescentResult
}

enum class ShopTransactionStatus {
    Bought,
    Sold,
    NeedsDropSlot,
    InventoryFull,
    InvalidDropSlot,
    ItemNotSoldHere,
    ItemNotAcceptedHere,
    EmptySlot,
    NotEnoughGold,
    InvalidQuantity,
    Declined,
}

enum class ShopSellQuoteStatus {
    Quoted,
    ItemNotAcceptedHere,
    EmptySlot,
    Declined,
}

enum class TownSessionResultStatus {
    EnteredShop,
    EnteredNativeService,
    ServiceNotOffered,
    ServiceClosed,
    MissingService,
    ShopFinished,
    NativeServiceFinished,
}

data class ShopTransactionResult(
    val status: ShopTransactionStatus,
    val party: PartyState,
    val item: ItemId? = null,
    val goldDelta: Int = 0,
    val slot: Int? = null,
    val quantity: Int = 1,
)

data class ShopSellQuoteResult(
    val status: ShopSellQuoteStatus,
    val party: PartyState,
    val item: ItemId? = null,
    val slot: Int? = null,
    val price: Int = 0,
    val bargainRolls: List<Int> = emptyList(),
)

data class TownSessionResult(
    val status: TownSessionResultStatus,
    val party: PartyState,
    val building: BuildingId? = null,
)

data class CampGearEffect(
    val image: ImageId,
    val protectsLowTemperature: Boolean,
    val protectsVeryLowTemperature: Boolean,
)

enum class RandomItemOfferStatus {
    Offered,
    Bought,
    Declined,
    NotEnoughGold,
    NeedsDropSlot,
    InventoryFull,
    InvalidDropSlot,
}

enum class RewardGrantStatus {
    Granted,
    NeedsDropSlot,
    InventoryFull,
    InvalidDropSlot,
}

data class RandomItemOfferResult(
    val status: RandomItemOfferStatus,
    val party: PartyState,
    val item: ItemId,
    val price: Int,
    val slot: Int? = null,
)

data class RewardGrantResult(
    val status: RewardGrantStatus,
    val party: PartyState,
    val item: ItemId? = null,
    val slot: Int? = null,
)

sealed interface RandomItemOfferStep {
    data class Choice(
        val offerText: TextBlock,
        val prompt: TextBlock,
        val item: ItemId,
        val itemName: String,
        val price: Int,
        val buy: InputBindingModel,
        val decline: InputBindingModel,
    ) : RandomItemOfferStep {
        val buyKey: Char get() = buy.primaryChar
        val declineKey: Char get() = decline.primaryChar
    }

    data class DropSlot(
        val prompt: TextBlock,
        val item: ItemId,
        val itemName: String,
        val price: Int,
        val result: RandomItemOfferResult,
    ) : RandomItemOfferStep

    data class Message(
        val text: TextBlock,
        val next: RandomItemOfferStep,
        val result: RandomItemOfferResult,
    ) : RandomItemOfferStep

    data class Finished(val result: RandomItemOfferResult) : RandomItemOfferStep {
        val party: PartyState get() = result.party
    }
}

sealed interface RewardGrantStep {
    data class DropSlot(
        val prompt: TextBlock,
        val item: ItemId,
        val result: RewardGrantResult,
    ) : RewardGrantStep

    data class Message(
        val text: TextBlock,
        val result: RewardGrantResult,
    ) : RewardGrantStep

    data class Finished(val result: RewardGrantResult) : RewardGrantStep {
        val party: PartyState get() = result.party
    }
}

data class RandomItemOfferSession(
    val offer: RandomItemOfferModel,
    val party: PartyState,
    val item: ItemId,
    val itemName: String,
    val price: Int,
    val step: RandomItemOfferStep,
    val lastResult: RandomItemOfferResult? = null,
) {
    fun apply(input: RandomItemOfferInput): RandomItemOfferSession =
        when (val current = step) {
            is RandomItemOfferStep.Choice -> {
                val buy = (input as? RandomItemOfferInput.Buy)
                    ?: error("random item offer is waiting for buy/decline input")
                if (buy.accept) buy() else decline()
            }
            is RandomItemOfferStep.DropSlot -> {
                val drop = (input as? RandomItemOfferInput.DropSlot)
                    ?: error("random item offer is waiting for a drop slot")
                place(drop.slot)
            }
            is RandomItemOfferStep.Message -> {
                require(input is RandomItemOfferInput.Continue) { "random item offer is showing message text" }
                copy(step = current.next, lastResult = current.result)
            }
            is RandomItemOfferStep.Finished -> error("random item offer is already finished: $current")
        }

    private fun buy(): RandomItemOfferSession =
        when {
            party.resources.gold < price -> finish(RandomItemOfferStatus.NotEnoughGold, party, offer.broke)
            else -> place(dropSlot = null)
        }

    private fun decline(): RandomItemOfferSession =
        finish(RandomItemOfferStatus.Declined, party, offer.declined)

    private fun place(dropSlot: Int?): RandomItemOfferSession =
        when (val placed = party.placeInventoryItem(item, offer.application.overflow, dropSlot)) {
            is InventoryPlacementResult.Placed -> {
                val updated = placed.party.copy(resources = placed.party.resources.copy(gold = placed.party.resources.gold - price))
                finish(RandomItemOfferStatus.Bought, updated, offer.deal, placed.slot)
            }
            is InventoryPlacementResult.NeedsDropSlot -> {
                val result = result(RandomItemOfferStatus.NeedsDropSlot, placed.party)
                copy(
                    step = RandomItemOfferStep.DropSlot(
                        prompt = checkNotNull(offer.messages.cantCarry) {
                            "drop-prompt random item offer requires cant-carry text"
                        },
                        item = item,
                        itemName = itemName,
                        price = price,
                        result = result,
                    ),
                    lastResult = result,
                )
            }
            is InventoryPlacementResult.Full ->
                finish(RandomItemOfferStatus.InventoryFull, placed.party, checkNotNull(offer.messages.cantCarry))
            is InventoryPlacementResult.InvalidDropSlot -> {
                val result = result(RandomItemOfferStatus.InvalidDropSlot, placed.party, placed.slot)
                copy(
                    step = RandomItemOfferStep.DropSlot(
                        prompt = checkNotNull(offer.messages.cantCarry),
                        item = item,
                        itemName = itemName,
                        price = price,
                        result = result,
                    ),
                    lastResult = result,
                )
            }
        }

    private fun finish(
        status: RandomItemOfferStatus,
        nextParty: PartyState,
        message: TextBlock,
        slot: Int? = null,
    ): RandomItemOfferSession {
        val result = result(status, nextParty, slot)
        return copy(
            party = nextParty,
            step = RandomItemOfferStep.Message(message, RandomItemOfferStep.Finished(result), result),
            lastResult = result,
        )
    }

    private fun result(
        status: RandomItemOfferStatus,
        nextParty: PartyState,
        slot: Int? = null,
    ): RandomItemOfferResult =
        RandomItemOfferResult(status, nextParty, item, price, slot)

    companion object {
        fun start(
            offer: RandomItemOfferModel,
            party: PartyState,
            itemRecords: List<ItemRecordModel> = ItemCatalog.all,
            selectedIndex: Int = 0,
        ): RandomItemOfferSession {
            require(offer.pool.items.isNotEmpty()) { "random item offer ${offer.pool.slug} has no items" }
            val item = offer.pool.items[selectedIndex.floorMod(offer.pool.items.size)]
            val record = checkNotNull(itemRecords.associateBy { it.id }[item]) { "unknown item ${item.slug}" }
            return RandomItemOfferSession(
                offer = offer,
                party = party,
                item = item,
                itemName = record.displayName,
                price = record.cost,
                step = RandomItemOfferStep.Choice(
                    offerText = offer.offer.withRandomItemOffer(record.displayName, record.cost),
                    prompt = offer.choicePrompt,
                    item = item,
                    itemName = record.displayName,
                    price = record.cost,
                    buy = offer.buy,
                    decline = offer.decline,
                ),
            )
        }
    }
}

data class RewardGrantSession(
    val reward: ScriptRewardModel,
    val party: PartyState,
    val pendingItems: List<ItemId>,
    val step: RewardGrantStep,
    val lastResult: RewardGrantResult? = null,
) {
    fun apply(input: RewardGrantInput): RewardGrantSession =
        when (val current = step) {
            is RewardGrantStep.DropSlot -> {
                val drop = (input as? RewardGrantInput.DropSlot)
                    ?: error("reward grant is waiting for a drop slot")
                place(party, current.item, drop.slot, pendingItems)
            }
            is RewardGrantStep.Message -> {
                require(input is RewardGrantInput.Continue) { "reward grant is showing message text" }
                continueWith(party, pendingItems)
            }
            is RewardGrantStep.Finished -> error("reward grant is already finished: $current")
        }

    private fun continueWith(nextParty: PartyState, items: List<ItemId>): RewardGrantSession {
        val item = items.firstOrNull() ?: return finish(RewardGrantStatus.Granted, nextParty)
        return place(nextParty, item, dropSlot = null, pendingItems = items)
    }

    private fun place(
        nextParty: PartyState,
        item: ItemId,
        dropSlot: Int?,
        pendingItems: List<ItemId>,
    ): RewardGrantSession {
        val remainingItems = pendingItems.drop(1)
        return when (val placed = nextParty.placeInventoryItem(item, reward.application.overflow, dropSlot)) {
            is InventoryPlacementResult.Placed -> {
                val result = result(RewardGrantStatus.Granted, placed.party, item, placed.slot)
                val pickedUp = reward.messages.pickedUp
                if (dropSlot != null && pickedUp != null) {
                    copy(
                        party = placed.party,
                        pendingItems = remainingItems,
                        step = RewardGrantStep.Message(pickedUp, result),
                        lastResult = result,
                    )
                } else {
                    continueWith(placed.party, remainingItems)
                }
            }
            is InventoryPlacementResult.NeedsDropSlot -> {
                val result = result(RewardGrantStatus.NeedsDropSlot, placed.party, item)
                copy(
                    party = placed.party,
                    pendingItems = pendingItems,
                    step = RewardGrantStep.DropSlot(
                        prompt = checkNotNull(reward.messages.cantCarry) {
                            "drop-prompt reward requires cant-carry text"
                        },
                        item = item,
                        result = result,
                    ),
                    lastResult = result,
                )
            }
            is InventoryPlacementResult.Full -> {
                val result = result(RewardGrantStatus.InventoryFull, placed.party, item)
                continueWith(placed.party, remainingItems).copy(lastResult = result)
            }
            is InventoryPlacementResult.InvalidDropSlot -> {
                val result = result(RewardGrantStatus.InvalidDropSlot, placed.party, item, placed.slot)
                copy(
                    party = placed.party,
                    pendingItems = pendingItems,
                    step = RewardGrantStep.DropSlot(
                        prompt = checkNotNull(reward.messages.cantCarry),
                        item = item,
                        result = result,
                    ),
                    lastResult = result,
                )
            }
        }
    }

    private fun finish(status: RewardGrantStatus, nextParty: PartyState): RewardGrantSession {
        val result = result(status, nextParty)
        return copy(
            party = nextParty,
            pendingItems = emptyList(),
            step = RewardGrantStep.Finished(result),
            lastResult = result,
        )
    }

    private fun result(
        status: RewardGrantStatus,
        nextParty: PartyState,
        item: ItemId? = null,
        slot: Int? = null,
    ): RewardGrantResult =
        RewardGrantResult(status, nextParty, item, slot)

    companion object {
        fun start(
            reward: ScriptRewardModel,
            party: PartyState,
            gold: Int = 0,
            itemRecords: List<ItemRecordModel> = ItemCatalog.all,
        ): RewardGrantSession {
            val itemRecordById = itemRecords.associateBy { it.id }
            reward.items.forEach { item ->
                val record = checkNotNull(itemRecordById[item]) { "unknown reward item ${item.slug}" }
                check(record.storage == ItemStorageKind.SlottedInventory) {
                    "script reward item ${item.slug} is ${record.storage}; use achieve(...) for milestone-special items"
                }
            }
            val session = RewardGrantSession(
                reward = reward,
                party = party.withGold(party.resources.gold + gold),
                pendingItems = reward.items,
                step = RewardGrantStep.Finished(RewardGrantResult(RewardGrantStatus.Granted, party)),
            )
            return session.continueWith(session.party, session.pendingItems)
        }
    }
}

data class SpeakRuntimeContent(
    val speak: SpeakSystemModel = SpeakSystemModel(null),
    val characters: List<CharacterModel> = emptyList(),
    val dialogue: List<DialogueModel> = emptyList(),
)

data class BattleRuntimeContent(
    val combat: CombatSystemModel = CombatSystemModel(null),
    val monsters: List<MonsterModel> = emptyList(),
    val formulas: List<FormulaModel> = emptyList(),
    val rewards: List<RewardBundleModel> = emptyList(),
    val leveling: LevelingModel? = null,
    val spells: List<SpellModel> = emptyList(),
    val magicItems: List<MagicItemModel> = emptyList(),
    val itemRecords: List<ItemRecordModel> = ItemCatalog.all,
) {
    val configured: Boolean get() = combat.standardBattle != null
}

sealed interface SpeakStep {
    data class ChooseNpc(val npcs: List<CharacterModel>) : SpeakStep
    data class DialogueBranch(
        val npc: CharacterModel,
        val branch: DialogueBranchModel,
        val next: SpeakStep,
        val continuation: TownServiceContinuation? = null,
    ) : SpeakStep
    data class MainMenu(val npc: CharacterModel, val actions: List<ConversationMenuActionModel>) : SpeakStep
    data class QuestionMenu(val npc: CharacterModel, val actions: List<QuestionMenuActionModel>) : SpeakStep
    data class Message(val blocks: List<TextBlock>, val next: SpeakStep) : SpeakStep
    data class Finished(val runtime: RuntimeSessionState) : SpeakStep {
        val party: PartyState get() = runtime.activeParty
    }
}

data class SpeakSession(
    val content: SpeakRuntimeContent,
    val runtime: RuntimeSessionState,
    val building: BuildingId,
    val step: SpeakStep,
    val selectedNpc: CharacterModel? = null,
    val rumourTold: Boolean = false,
) {
    private val conversation: StandardConversationModel
        get() = requireNotNull(content.speak.standardConversation) {
            "standard conversation system is not configured"
        }

    fun apply(input: SpeakInput): SpeakSession =
        when (val current = step) {
            is SpeakStep.ChooseNpc -> {
                val npc = (input as? SpeakInput.Npc)
                    ?: error("speak session is choosing an NPC")
                val character = current.npcs.firstOrNull { it.id == npc.npc }
                    ?: error("NPC ${npc.npc.slug} is not available for ${building.slug}")
                val nextRuntime = runtime.withActiveParty(runtime.activeParty.withNpcVisit(character.id, visited = true))
                val branch = content.firstMatchingDialogueBranch(character.id, nextRuntime.activeParty)
                val menu = mainMenu(character)
                copy(
                    runtime = nextRuntime,
                    selectedNpc = character,
                    step = branch?.let { SpeakStep.DialogueBranch(character, it, next = menu) } ?: menu,
                )
            }
            is SpeakStep.DialogueBranch -> error("speak session is running a dialogue branch")
            is SpeakStep.MainMenu -> {
                val choice = (input as? SpeakInput.Main)
                    ?: error("speak session is at the main conversation menu")
                when (choice.choice) {
                    SpeakMainChoice.Chitchat -> chitchat(current.npc)
                    SpeakMainChoice.Question -> copy(step = questionMenu(current.npc))
                    SpeakMainChoice.Rumour -> rumour(current.npc)
                    SpeakMainChoice.Bye -> copy(step = SpeakStep.Finished(runtime))
                }
            }
            is SpeakStep.QuestionMenu -> {
                val question = (input as? SpeakInput.Question)
                    ?: error("speak session is at the question menu")
                if (question.topic == QuestionTopic.Nothing) {
                    copy(step = mainMenu(current.npc))
                } else {
                    copy(step = message(questionReply(question.topic, current.npc), mainMenu(current.npc)))
                }
            }
            is SpeakStep.Message -> {
                require(input is SpeakInput.Continue) { "speak session is showing message text" }
                copy(step = current.next)
            }
            is SpeakStep.Finished -> error("speak session is already finished: $current")
        }

    private fun chitchat(npc: CharacterModel): SpeakSession {
        val subjects = conversation.chitchat.subjects.ifEmpty { listOf(ChitchatSubject.Well) }
        val draw = runtime.drawRandom(0, subjects.lastIndex)
        val subject = subjects[draw.value]
        return copy(
            runtime = draw.runtime,
            step = message(
                listOf(chitchatPrompt(subject), chitchatReply(subject, npc)),
                mainMenu(npc),
            ),
        )
    }

    private fun rumour(npc: CharacterModel): SpeakSession {
        val selection = if (rumourTold) null else content.selectStandardRumour(runtime)
        val text = selection?.rumour?.text ?: noRumourText()
        return copy(
            runtime = selection?.runtime ?: runtime,
            rumourTold = true,
            step = message(text, mainMenu(npc)),
        )
    }

    private fun mainMenu(npc: CharacterModel): SpeakStep.MainMenu =
        SpeakStep.MainMenu(npc, conversation.menu.actions)

    private fun questionMenu(npc: CharacterModel): SpeakStep.QuestionMenu =
        SpeakStep.QuestionMenu(npc, conversation.menu.questions)

    private fun message(text: TextBlock, next: SpeakStep): SpeakStep.Message =
        message(listOf(text), next)

    private fun message(blocks: List<TextBlock>, next: SpeakStep): SpeakStep.Message =
        SpeakStep.Message(blocks, next)

    private fun chitchatPrompt(subject: ChitchatSubject): TextBlock {
        val leaderGender = runtime.activeParty.members.firstOrNull()?.gender
        val variants = content.speak.textTables.chitchatPrompts.firstOrNull { it.subject == subject }?.variants.orEmpty()
        return variants.firstOrNull { it.audience.matches(leaderGender) }?.text
            ?: variants.firstOrNull()?.text
            ?: inlineText("- Hello.")
    }

    private fun chitchatReply(subject: ChitchatSubject, npc: CharacterModel): TextBlock =
        content.speak.textTables.chitchatReplies
            .firstOrNull { it.subject == subject }
            ?.replies
            ?.firstOrNull { it.job == npc.job }
            ?.text
            ?: inlineText("- I have nothing to say.")

    private fun questionReply(topic: QuestionTopic, npc: CharacterModel): TextBlock =
        content.speak.textTables.questionReplies
            .firstOrNull { it.topic == topic }
            ?.replies
            ?.firstOrNull { it.job == npc.job }
            ?.text
            ?: noRumourText()

    private fun noRumourText(): TextBlock =
        content.speak.textTables.screens.firstOrNull { it.id == T.Speak.noRumour }?.text
            ?: inlineText("- Sorry. There's", "nothing new I can", "tell you.", "")

    companion object {
        fun start(
            content: SpeakRuntimeContent,
            runtime: RuntimeSessionState,
            building: BuildingId,
        ): SpeakSession {
            val npcs = content.charactersFor(runtime, building)
            val step = if (npcs.isEmpty()) {
                SpeakStep.Message(
                    blocks = listOf(
                        content.speak.textTables.screens.firstOrNull { it.id == T.Speak.noNpc }?.text
                            ?: inlineText("Noone is here!"),
                    ),
                    next = SpeakStep.Finished(runtime),
                )
            } else {
                SpeakStep.ChooseNpc(npcs)
            }
            return SpeakSession(content, runtime, building, step)
        }
    }
}

data class EquipmentPromptModel(
    val command: EquipmentCommand,
    val kind: EquipmentKind,
    val memberFilter: MemberChoiceFilter,
    val inventoryFilter: InventorySlotChoiceFilter?,
)

object EquipmentPromptText {
    val inventory = inlineText("Ready what?")
    val who = inlineText("Who? (1-N)")
    val readyWeapons = inlineText("Do you want to ready", "some weapons before", "leaving?", "")
    val readyArmors = inlineText("Do you want to ready", "some armors before", "leaving?", "")
}

sealed interface EquipmentPromptStep {
    data class ChooseInventorySlot(
        val prompt: TextBlock,
        val filter: InventorySlotChoiceFilter,
    ) : EquipmentPromptStep
    data class ChooseMember(
        val prompt: TextBlock,
        val filter: MemberChoiceFilter,
    ) : EquipmentPromptStep
    data class Finished(val result: EquipmentActionResult) : EquipmentPromptStep
}

data class EquipmentPromptSession(
    val command: EquipmentCommand,
    val party: PartyState,
    val step: EquipmentPromptStep,
    val inventorySlot: Int? = null,
) {
    fun apply(input: EquipmentPromptInput): EquipmentPromptSession =
        when (val current = step) {
            is EquipmentPromptStep.ChooseInventorySlot -> {
                val slot = (input as? EquipmentPromptInput.InventorySlot)
                    ?: error("equipment session is choosing an inventory slot")
                copy(
                    step = EquipmentPromptStep.ChooseMember(
                        EquipmentPromptText.who,
                        command.prompt().memberFilter,
                    ),
                    inventorySlot = slot.slot,
                )
            }
            is EquipmentPromptStep.ChooseMember -> {
                val member = (input as? EquipmentPromptInput.Member)
                    ?: error("equipment session is choosing a member")
                val result = party.applyEquipmentCommand(command, member.memberNumber, inventorySlot)
                copy(party = result.party, step = EquipmentPromptStep.Finished(result))
            }
            is EquipmentPromptStep.Finished -> error("equipment session is already finished: $current")
        }

    companion object {
        fun start(command: EquipmentCommand, party: PartyState): EquipmentPromptSession {
            val prompt = command.prompt()
            val step = prompt.inventoryFilter?.let {
                EquipmentPromptStep.ChooseInventorySlot(EquipmentPromptText.inventory, it)
            } ?: EquipmentPromptStep.ChooseMember(EquipmentPromptText.who, prompt.memberFilter)
            return EquipmentPromptSession(command, party, step)
        }
    }
}

sealed interface TownLeaveEquipmentStep {
    data class ReadyQuestion(val command: EquipmentCommand, val prompt: TextBlock) : TownLeaveEquipmentStep
    data class Equipment(val session: EquipmentPromptSession) : TownLeaveEquipmentStep
    data class Finished(val party: PartyState) : TownLeaveEquipmentStep
}

data class TownLeaveEquipmentSession(
    val party: PartyState,
    val step: TownLeaveEquipmentStep,
    val itemRecords: List<ItemRecordModel> = ItemCatalog.all,
    val phase: Int = 0,
    val lastResult: EquipmentActionResult? = null,
) {
    fun apply(input: TownLeaveEquipmentInput): TownLeaveEquipmentSession =
        when (val current = step) {
            is TownLeaveEquipmentStep.ReadyQuestion -> {
                val answer = (input as? TownLeaveEquipmentInput.ReadyAnswer)
                    ?: error("town-leave equipment session is asking a ready question")
                if (answer.yes) {
                    copy(step = TownLeaveEquipmentStep.Equipment(EquipmentPromptSession.start(current.command, party)))
                } else {
                    advance(party, lastResult = null)
                }
            }
            is TownLeaveEquipmentStep.Equipment -> {
                val equipment = (input as? TownLeaveEquipmentInput.Equipment)
                    ?: error("town-leave equipment session is running equipment prompts")
                val updated = current.session.apply(equipment.input)
                when (val finished = updated.step) {
                    is EquipmentPromptStep.Finished ->
                        advance(updated.party, finished.result)
                    else -> copy(step = TownLeaveEquipmentStep.Equipment(updated), party = updated.party)
                }
            }
            is TownLeaveEquipmentStep.Finished ->
                error("town-leave equipment session is already finished: $current")
        }

    private fun advance(nextParty: PartyState, lastResult: EquipmentActionResult?): TownLeaveEquipmentSession {
        val next = nextStep(nextParty, phase + 1)
        return copy(
            party = nextParty,
            phase = next.phase,
            lastResult = lastResult,
            step = next.step,
        )
    }

    private fun nextStep(nextParty: PartyState, nextPhase: Int): NextTownLeaveStep =
        readyCommands
            .withIndex()
            .drop(nextPhase)
            .firstOrNull { (_, command) -> nextParty.needsTownLeaveReadyPrompt(command, itemRecords) }
            ?.let { (index, command) ->
                NextTownLeaveStep(index, TownLeaveEquipmentStep.ReadyQuestion(command, command.townLeavePrompt()))
            }
            ?: NextTownLeaveStep(readyCommands.size, TownLeaveEquipmentStep.Finished(nextParty))

    private data class NextTownLeaveStep(val phase: Int, val step: TownLeaveEquipmentStep)

    companion object {
        private val readyCommands = listOf(EquipmentCommand.ReadyWeapon, EquipmentCommand.ReadyArmor)

        fun start(
            party: PartyState,
            itemRecords: List<ItemRecordModel> = ItemCatalog.all,
        ): TownLeaveEquipmentSession {
            val first = readyCommands
                .withIndex()
                .firstOrNull { (_, command) -> party.needsTownLeaveReadyPrompt(command, itemRecords) }
            return TownLeaveEquipmentSession(
                party = party,
                phase = first?.index ?: readyCommands.size,
                itemRecords = itemRecords,
                step = first?.value?.let { TownLeaveEquipmentStep.ReadyQuestion(it, it.townLeavePrompt()) }
                    ?: TownLeaveEquipmentStep.Finished(party),
            )
        }
    }
}

object TownPromptText {
    fun menu(town: TownModel): TextBlock =
        inlineText(town.name, "")

    fun unavailable(building: BuildingId): TextBlock =
        inlineText("${building.slug} is not available here.")

    fun missingService(building: BuildingId): TextBlock =
        inlineText("${building.slug} service is not implemented yet.")

    fun closed(building: BuildingId): TextBlock =
        inlineText("${building.slug} is closed.")
}

sealed interface TownSessionStep {
    data class Menu(
        val town: TownModel,
        val actions: List<TownMenuActionModel>,
    ) : TownSessionStep
    data class Notice(
        val text: TextBlock,
        val next: Menu,
    ) : TownSessionStep
    data class Shop(
        val service: BuildingServiceModel,
        val session: ShopServiceSession,
    ) : TownSessionStep
    data class NativeService(
        val service: BuildingServiceModel,
        val scene: SceneModel,
        val continuation: TownServiceContinuation? = null,
    ) : TownSessionStep
    data class LeaveEquipment(
        val session: TownLeaveEquipmentSession,
    ) : TownSessionStep
    data class Finished(
        val runtime: RuntimeSessionState,
    ) : TownSessionStep {
        val party: PartyState get() = runtime.activeParty
    }
}

data class TownSession(
    val town: TownModel,
    val townSystem: TownSystemModel,
    val runtime: RuntimeSessionState,
    val shopServices: Map<BuildingId, ShopServiceModel>,
    val serviceScenes: Map<SceneId, SceneModel>,
    val itemRecords: List<ItemRecordModel> = ItemCatalog.all,
    val step: TownSessionStep = TownSessionStep.Menu(town, availableTownActions(town, townSystem)),
    val lastResult: TownSessionResult? = null,
) {
    val realm: RealmState get() = runtime.realm
    val party: PartyState get() = runtime.activeParty
    val currentHour: Int get() = runtime.realm.hour

    fun apply(input: TownSessionInput): TownSession =
        when (input) {
            is TownSessionInput.MenuKey -> chooseMenu(input.key)
            is TownSessionInput.Shop -> applyShop(input.input)
            is TownSessionInput.Equipment -> applyEquipment(input.input)
            is TownSessionInput.FinishNativeService -> finishNative(input.runtime)
        }

    fun continueNotice(): TownSession {
        val current = step as? TownSessionStep.Notice
            ?: error("town session is not showing a notice")
        return copy(step = current.next)
    }

    fun replaceShopSession(session: ShopServiceSession): TownSession {
        val current = step as? TownSessionStep.Shop
            ?: error("town session is not running a shop")
        return if (session.step is ShopServiceStep.Finished) {
            finishShop(session, current.service.building)
        } else {
            copy(runtime = runtime.withActiveParty(session.party), step = current.copy(session = session))
        }
    }

    fun replaceLeaveEquipmentSession(session: TownLeaveEquipmentSession): TownSession {
        require(step is TownSessionStep.LeaveEquipment) { "town session is not running leave equipment prompts" }
        return if (session.step is TownLeaveEquipmentStep.Finished) {
            val nextRuntime = runtime.withActiveParty(session.party)
            copy(runtime = nextRuntime, step = TownSessionStep.Finished(nextRuntime))
        } else {
            copy(runtime = runtime.withActiveParty(session.party), step = TownSessionStep.LeaveEquipment(session))
        }
    }

    fun replaceNativeServiceContinuation(continuation: TownServiceContinuation): TownSession {
        val current = step as? TownSessionStep.NativeService
            ?: error("town session is not running a native service")
        return copy(
            runtime = continuation.runtime,
            step = current.copy(continuation = continuation),
        )
    }

    private fun chooseMenu(key: Char): TownSession {
        val action = townSystem.menu.firstOrNull { it.key == key.uppercaseChar() }
            ?: return copy(lastResult = null)
        if (action.endsMenu) {
            val equipment = TownLeaveEquipmentSession.start(party, itemRecords)
            return if (equipment.step is TownLeaveEquipmentStep.Finished) {
                copy(step = TownSessionStep.Finished(runtime))
            } else {
                copy(step = TownSessionStep.LeaveEquipment(equipment), lastResult = null)
            }
        }

        val building = checkNotNull(action.building) { "town service action ${action.action.slug} has no building" }
        if (!town.offers(building)) {
            return notice(
                TownPromptText.unavailable(building),
                TownSessionResult(TownSessionResultStatus.ServiceNotOffered, party, building),
            )
        }

        val service = townSystem.buildingServices.firstOrNull { it.building == building }
            ?: return notice(
                TownPromptText.missingService(building),
                TownSessionResult(TownSessionResultStatus.MissingService, party, building),
            )
        val scene = serviceScenes[service.scene]
            ?: return notice(
                TownPromptText.missingService(building),
                TownSessionResult(TownSessionResultStatus.MissingService, party, building),
            )
        val shop = shopServices[building]
        if (!service.isOpenAt(currentHour)) {
            return notice(
                shop?.closed ?: TownPromptText.closed(building),
                TownSessionResult(TownSessionResultStatus.ServiceClosed, party, building),
            )
        }

        return if (shop != null) {
            copy(
                step = TownSessionStep.Shop(service, ShopServiceSession.start(shop, party, itemRecords)),
                lastResult = TownSessionResult(TownSessionResultStatus.EnteredShop, party, building),
            )
        } else {
            copy(
                step = TownSessionStep.NativeService(service, scene),
                lastResult = TownSessionResult(TownSessionResultStatus.EnteredNativeService, party, building),
            )
        }
    }

    private fun applyShop(input: ShopServiceInput): TownSession {
        val current = step as? TownSessionStep.Shop
            ?: error("town session is not running a shop")
        return replaceShopSession(applyShopInput(current.session, input))
    }

    private fun applyEquipment(input: TownLeaveEquipmentInput): TownSession {
        val current = step as? TownSessionStep.LeaveEquipment
            ?: error("town session is not running leave equipment prompts")
        return replaceLeaveEquipmentSession(current.session.apply(input))
    }

    private fun finishNative(nextRuntime: RuntimeSessionState): TownSession {
        val current = step as? TownSessionStep.NativeService
            ?: error("town session is not running a native service")
        val nextParty = nextRuntime.activeParty
        return copy(
            runtime = nextRuntime,
            step = menuStep(),
            lastResult = TownSessionResult(TownSessionResultStatus.NativeServiceFinished, nextParty, current.service.building),
        )
    }

    private fun finishShop(session: ShopServiceSession, building: BuildingId): TownSession =
        runtime.withActiveParty(session.party).let { nextRuntime ->
            copy(
                runtime = nextRuntime,
                step = menuStep(),
                lastResult = TownSessionResult(TownSessionResultStatus.ShopFinished, session.party, building),
            )
        }

    private fun notice(text: TextBlock, result: TownSessionResult): TownSession =
        copy(step = TownSessionStep.Notice(text, menuStep()), lastResult = result)

    private fun menuStep(): TownSessionStep.Menu =
        TownSessionStep.Menu(town, availableTownActions(town, townSystem))

    companion object {
        fun start(
            town: TownModel,
            townSystem: TownSystemModel,
            scenes: List<SceneModel>,
            runtime: RuntimeSessionState,
            itemRecords: List<ItemRecordModel> = ItemCatalog.all,
        ): TownSession =
            TownSession(
                town = town,
                townSystem = townSystem,
                runtime = runtime,
                shopServices = scenes.shopServicesByBuilding(),
                serviceScenes = scenes.associateBy { scene -> scene.id },
                itemRecords = itemRecords,
            )

        fun start(
            town: TownModel,
            townSystem: TownSystemModel,
            scenes: List<SceneModel>,
            party: PartyState,
            currentHour: Int,
            rngState: Long? = null,
            npcs: List<NpcPlacement> = emptyList(),
            itemRecords: List<ItemRecordModel> = ItemCatalog.all,
        ): TownSession =
            start(
                town = town,
                townSystem = townSystem,
                scenes = scenes,
                runtime = RuntimeSessionState.singleParty(
                    party = party,
                    hour = currentHour,
                    currentTown = town.id,
                    rngState = rngState,
                    npcs = npcs,
                ),
                itemRecords = itemRecords,
            )
    }
}

sealed interface ShopServiceStep {
    data class Intro(val text: TextBlock) : ShopServiceStep
    data class MainMenu(val text: TextBlock, val canBuy: Boolean, val canSell: Boolean) : ShopServiceStep
    data class BuyItem(val prompt: TextBlock, val catalogs: List<ShopCatalogModel>) : ShopServiceStep
    data class Quantity(val offer: ShopItemOfferModel, val prompt: TextBlock) : ShopServiceStep
    data class ConfirmPurchase(
        val offer: ShopItemOfferModel,
        val quantity: Int,
        val unitPrice: Int,
        val totalPrice: Int,
        val prompt: TextBlock,
    ) : ShopServiceStep
    data class DropSlot(
        val offer: ShopItemOfferModel,
        val quantity: Int,
        val prompt: TextBlock,
        val result: ShopTransactionResult,
    ) : ShopServiceStep
    data class SellSlot(val prompt: TextBlock) : ShopServiceStep
    data class ConfirmSale(val quote: ShopSellQuoteResult, val prompt: TextBlock) : ShopServiceStep
    data class Finished(val text: TextBlock) : ShopServiceStep
}

data class ShopServiceSession(
    val shop: ShopServiceModel,
    val party: PartyState,
    val step: ShopServiceStep,
    val lastTransaction: ShopTransactionResult? = null,
    val lastSellQuote: ShopSellQuoteResult? = null,
    val itemRecords: List<ItemRecordModel> = ItemCatalog.all,
) {
    private fun interpreter(): ShopServiceInterpreter = ShopServiceInterpreter(itemRecords)
    private fun mainMenu(): ShopServiceStep =
        ShopServiceStep.MainMenu(shop.menu, canBuy = shop.buy != null, canSell = shop.sell != null)

    fun continueIntro(): ShopServiceSession {
        require(step is ShopServiceStep.Intro) { "shop session is not showing intro" }
        return copy(step = mainMenu(), lastTransaction = null, lastSellQuote = null)
    }

    fun chooseMain(choice: ShopMainChoice): ShopServiceSession {
        require(step is ShopServiceStep.MainMenu) { "shop session is not at main menu" }
        return when (choice) {
            ShopMainChoice.Buy -> {
                val buy = checkNotNull(shop.buy) { "shop ${shop.building.slug} has no buy section" }
                copy(step = ShopServiceStep.BuyItem(buy.prompt, buy.catalogs), lastTransaction = null, lastSellQuote = null)
            }
            ShopMainChoice.Sell -> {
                val sell = checkNotNull(shop.sell) { "shop ${shop.building.slug} has no sell section" }
                copy(step = ShopServiceStep.SellSlot(sell.prompt), lastTransaction = null, lastSellQuote = null)
            }
            ShopMainChoice.Leave -> copy(step = ShopServiceStep.Finished(shop.goodbye))
        }
    }

    fun chooseBuyItem(item: ItemId): ShopServiceSession {
        require(step is ShopServiceStep.BuyItem) { "shop session is not choosing a buy item" }
        val buy = checkNotNull(shop.buy) { "shop ${shop.building.slug} has no buy section" }
        val offer = buy.offerFor(item)
            ?: return copy(
                step = mainMenu(),
                lastTransaction = ShopTransactionResult(ShopTransactionStatus.ItemNotSoldHere, party, item),
            )
        val purchase = offer.purchase
        return if (purchase is ShopPurchaseModel.Resource) {
            copy(step = ShopServiceStep.Quantity(offer, purchase.quantityPrompt), lastTransaction = null, lastSellQuote = null)
        } else {
            purchaseStep(offer, quantity = 1)
        }
    }

    fun enterQuantity(quantity: Int): ShopServiceSession {
        val current = step as? ShopServiceStep.Quantity
            ?: error("shop session is not asking for a quantity")
        return purchaseStep(current.offer, quantity)
    }

    fun confirmPurchase(accept: Boolean): ShopServiceSession {
        val current = step as? ShopServiceStep.ConfirmPurchase
            ?: error("shop session is not confirming a purchase")
        val buy = checkNotNull(shop.buy) { "shop ${shop.building.slug} has no buy section" }
        if (!accept) {
            return copy(
                step = mainMenu(),
                lastTransaction = ShopTransactionResult(
                    status = ShopTransactionStatus.Declined,
                    party = party,
                    item = current.offer.item,
                    quantity = current.quantity,
                ),
            )
        }

        val result = interpreter().buy(shop, party, current.offer.item, current.quantity)
        return if (result.status == ShopTransactionStatus.NeedsDropSlot) {
            copy(step = ShopServiceStep.DropSlot(current.offer, current.quantity, buy.cantCarry, result))
        } else {
            copy(step = mainMenu(), party = result.party, lastTransaction = result)
        }
    }

    fun chooseDropSlot(slot: Int): ShopServiceSession {
        val current = step as? ShopServiceStep.DropSlot
            ?: error("shop session is not asking for a drop slot")
        val result = interpreter().buy(shop, party, current.offer.item, current.quantity, dropSlot = slot)
        return if (result.status == ShopTransactionStatus.Bought) {
            copy(step = mainMenu(), party = result.party, lastTransaction = result)
        } else {
            copy(step = current.copy(result = result), lastTransaction = result)
        }
    }

    fun chooseSellSlot(slot: Int, bargainRolls: List<Int> = emptyList()): ShopServiceSession {
        require(step is ShopServiceStep.SellSlot) { "shop session is not choosing a sell slot" }
        val sell = checkNotNull(shop.sell) { "shop ${shop.building.slug} has no sell section" }
        val quote = interpreter().sellQuote(shop, party, slot, bargainRolls)
        return if (quote.status == ShopSellQuoteStatus.Quoted) {
            copy(step = ShopServiceStep.ConfirmSale(quote, sell.offer), lastSellQuote = quote)
        } else {
            copy(step = mainMenu(), lastSellQuote = quote)
        }
    }

    fun confirmSale(accept: Boolean): ShopServiceSession {
        val current = step as? ShopServiceStep.ConfirmSale
            ?: error("shop session is not confirming a sale")
        val result = if (accept) {
            interpreter().sell(shop, party, checkNotNull(current.quote.slot), bargainRolls = current.quote.bargainRolls)
        } else {
            ShopTransactionResult(
                status = ShopTransactionStatus.Declined,
                party = party,
                item = current.quote.item,
                slot = current.quote.slot,
            )
        }
        return copy(step = mainMenu(), party = result.party, lastTransaction = result)
    }

    private fun purchaseStep(offer: ShopItemOfferModel, quantity: Int): ShopServiceSession {
        val buy = checkNotNull(shop.buy) { "shop ${shop.building.slug} has no buy section" }
        val unitPrice = interpreter().unitPrice(offer, party)
        return copy(
            step = ShopServiceStep.ConfirmPurchase(
                offer = offer,
                quantity = quantity,
                unitPrice = unitPrice,
                totalPrice = unitPrice * quantity,
                prompt = buy.payPrompt,
            ),
            lastTransaction = null,
            lastSellQuote = null,
        )
    }

    companion object {
        fun start(
            shop: ShopServiceModel,
            party: PartyState,
            itemRecords: List<ItemRecordModel> = ItemCatalog.all,
        ): ShopServiceSession =
            ShopServiceSession(
                shop = shop,
                party = party,
                step = ShopServiceStep.Intro(shop.intro),
                itemRecords = itemRecords,
            )
    }
}

fun applyShopInput(session: ShopServiceSession, input: ShopServiceInput): ShopServiceSession =
    when (input) {
        ShopServiceInput.ContinueIntro -> session.continueIntro()
        is ShopServiceInput.Main -> session.chooseMain(input.choice)
        is ShopServiceInput.BuyItem -> session.chooseBuyItem(input.item)
        is ShopServiceInput.Quantity -> session.enterQuantity(input.quantity)
        is ShopServiceInput.ConfirmPurchase -> session.confirmPurchase(input.accept)
        is ShopServiceInput.DropSlot -> session.chooseDropSlot(input.slot)
        is ShopServiceInput.SellSlot -> session.chooseSellSlot(input.slot, input.bargainRolls)
        is ShopServiceInput.ConfirmSale -> session.confirmSale(input.accept)
    }

fun List<SceneModel>.shopServicesByBuilding(): Map<BuildingId, ShopServiceModel> =
    flatMap { scene -> scene.ops.filterIsInstance<Op.ShopService>().map { it.shop } }
        .associateBy { shop -> shop.building }

fun BuildingServiceModel.isOpenAt(hour: Int): Boolean {
    val opensAt = opensAt ?: return true
    val closesAt = closesAt ?: return true
    val current = hour.floorMod(24)
    return if (opensAt <= closesAt) {
        current in opensAt..closesAt
    } else {
        current >= opensAt || current <= closesAt
    }
}

private fun availableTownActions(
    town: TownModel,
    townSystem: TownSystemModel,
): List<TownMenuActionModel> =
    townSystem.menu.filter { action -> action.endsMenu || action.building?.let(town::offers) == true }

private fun TownModel.offers(building: BuildingId): Boolean =
    services.any { it.building == building }

private fun Int.floorMod(divisor: Int): Int =
    ((this % divisor) + divisor) % divisor

class ShopServiceInterpreter(
    itemRecords: List<ItemRecordModel> = ItemCatalog.all,
) {
    private val itemRecordById = itemRecords.associateBy { it.id }

    fun buy(
        shop: ShopServiceModel,
        party: PartyState,
        item: ItemId,
        quantity: Int = 1,
        dropSlot: Int? = null,
    ): ShopTransactionResult {
        val buy = shop.buy ?: return ShopTransactionResult(ShopTransactionStatus.ItemNotSoldHere, party, item)
        val offer = buy.offerFor(item)
            ?: return ShopTransactionResult(ShopTransactionStatus.ItemNotSoldHere, party, item)
        val unitPrice = unitPrice(offer, party)

        return when (val purchase = offer.purchase) {
            ShopPurchaseModel.InventoryItem -> buyInventoryItem(buy, party, offer.item, unitPrice, dropSlot)
            is ShopPurchaseModel.Resource -> buyResource(party, offer.item, purchase, unitPrice, quantity)
        }
    }

    fun sell(
        shop: ShopServiceModel,
        party: PartyState,
        slot: Int,
        accept: Boolean = true,
        bargainRolls: List<Int> = emptyList(),
    ): ShopTransactionResult {
        val quote = sellQuote(shop, party, slot, bargainRolls)
        val item = quote.item
        val offerPrice = quote.price
        when (quote.status) {
            ShopSellQuoteStatus.EmptySlot ->
                return ShopTransactionResult(ShopTransactionStatus.EmptySlot, party, slot = slot)
            ShopSellQuoteStatus.ItemNotAcceptedHere ->
                return ShopTransactionResult(ShopTransactionStatus.ItemNotAcceptedHere, party, item, slot = slot)
            ShopSellQuoteStatus.Declined ->
                return ShopTransactionResult(ShopTransactionStatus.Declined, party, item, slot = slot)
            ShopSellQuoteStatus.Quoted -> Unit
        }
        if (!accept) {
            return ShopTransactionResult(ShopTransactionStatus.Declined, party, item, slot = slot)
        }

        val updated = party
            .withInventoryItem(slot, null)
            .withGold(party.resources.gold + offerPrice)
        return ShopTransactionResult(
            status = ShopTransactionStatus.Sold,
            party = updated,
            item = item,
            goldDelta = offerPrice,
            slot = slot,
        )
    }

    fun sellQuote(
        shop: ShopServiceModel,
        party: PartyState,
        slot: Int,
        bargainRolls: List<Int> = emptyList(),
    ): ShopSellQuoteResult {
        val sell = shop.sell ?: return ShopSellQuoteResult(ShopSellQuoteStatus.ItemNotAcceptedHere, party, slot = slot)
        val item = party.inventoryItem(slot)
            ?: return ShopSellQuoteResult(ShopSellQuoteStatus.EmptySlot, party, slot = slot)
        if (!sell.accepts(item)) {
            return ShopSellQuoteResult(ShopSellQuoteStatus.ItemNotAcceptedHere, party, item, slot)
        }

        val offerPrice = bargainPrice(sell, item, party, bargainRolls)
            ?: return ShopSellQuoteResult(ShopSellQuoteStatus.Declined, party, item, slot, bargainRolls = bargainRolls)
        return ShopSellQuoteResult(
            status = ShopSellQuoteStatus.Quoted,
            party = party,
            item = item,
            slot = slot,
            price = offerPrice,
            bargainRolls = bargainRolls,
        )
    }

    fun unitPrice(offer: ShopItemOfferModel, party: PartyState): Int =
        price(offer.price, offer.item, party)

    private fun buyInventoryItem(
        buy: ShopBuyModel,
        party: PartyState,
        item: ItemId,
        unitPrice: Int,
        dropSlot: Int?,
    ): ShopTransactionResult {
        if (party.resources.gold < unitPrice) {
            return ShopTransactionResult(ShopTransactionStatus.NotEnoughGold, party, item, goldDelta = -unitPrice)
        }
        return when (val placed = party.placeInventoryItem(item, buy.inventoryApplication.overflow, dropSlot)) {
            is InventoryPlacementResult.Placed -> ShopTransactionResult(
                status = ShopTransactionStatus.Bought,
                party = placed.party.withGold(placed.party.resources.gold - unitPrice),
                item = item,
                goldDelta = -unitPrice,
                slot = placed.slot,
            )
            is InventoryPlacementResult.NeedsDropSlot -> ShopTransactionResult(
                status = ShopTransactionStatus.NeedsDropSlot,
                party = placed.party,
                item = item,
            )
            is InventoryPlacementResult.Full -> ShopTransactionResult(
                status = ShopTransactionStatus.InventoryFull,
                party = placed.party,
                item = item,
            )
            is InventoryPlacementResult.InvalidDropSlot -> ShopTransactionResult(
                status = ShopTransactionStatus.InvalidDropSlot,
                party = placed.party,
                item = item,
                slot = placed.slot,
            )
        }
    }

    private fun buyResource(
        party: PartyState,
        item: ItemId,
        purchase: ShopPurchaseModel.Resource,
        unitPrice: Int,
        requestedQuantity: Int,
    ): ShopTransactionResult {
        if (requestedQuantity < 0) {
            return ShopTransactionResult(ShopTransactionStatus.InvalidQuantity, party, item, quantity = requestedQuantity)
        }

        val quantity = minOf(requestedQuantity, party.remainingCapacity(purchase.cap))
        if (quantity == 0 && purchase.zeroQuantityDeal == ShopZeroQuantityDealPolicy.NonZeroOnly) {
            return ShopTransactionResult(ShopTransactionStatus.InvalidQuantity, party, item, quantity = 0)
        }

        val total = unitPrice * quantity
        if (party.resources.gold < total) {
            return ShopTransactionResult(ShopTransactionStatus.NotEnoughGold, party, item, goldDelta = -total)
        }

        val updated = party
            .withGold(party.resources.gold - total)
            .withResource(purchase.resource, quantity)
        return ShopTransactionResult(
            status = ShopTransactionStatus.Bought,
            party = updated,
            item = item,
            goldDelta = -total,
            quantity = quantity,
        )
    }

    private fun bargainPrice(
        sell: ShopSellModel,
        item: ItemId,
        party: PartyState,
        rolls: List<Int>,
    ): Int? {
        var current = price(sell.price, item, party)
        if (sell.bargain != ShopSellBargainPolicy.SmithHaggle) return current

        rolls.forEach { roll ->
            if (roll > 90) current += current / 4
            if (roll > 75) current += current / 6
            if (roll < 76) return null
        }
        return current
    }

    private fun ShopSellModel.accepts(item: ItemId): Boolean {
        if (item in rejectedItems) return false
        if (item in acceptedItems) return true
        return itemRecordById[item]?.kind in acceptedKinds
    }

    private fun price(model: ShopPriceModel, item: ItemId, party: PartyState): Int =
        when (model) {
            ShopPriceModel.ItemCost -> itemRecord(item).cost
            ShopPriceModel.HalfItemCost -> itemRecord(item).cost / 2
            is ShopPriceModel.Fixed -> model.amount
            is ShopPriceModel.LeaderLevelPlus -> party.leaderLevel + model.addend
            is ShopPriceModel.ItemCostPlusLeaderLevelTimes -> itemRecord(item).cost + party.leaderLevel * model.multiplier
        }

    private fun itemRecord(item: ItemId): ItemRecordModel =
        checkNotNull(itemRecordById[item]) { "unknown item ${item.slug}" }
}

fun ShopBuyModel.offers(): List<ShopItemOfferModel> =
    catalogs.flatMap { catalog -> catalog.pages.flatMap { page -> page.entries } }

fun ShopBuyModel.offerFor(item: ItemId): ShopItemOfferModel? =
    offers().firstOrNull { it.item == item }

fun PartyState.placeInventoryItem(
    item: ItemId,
    overflow: RewardOverflowPolicy = RewardOverflowPolicy.PromptDropThenPlace,
    dropSlot: Int? = null,
): InventoryPlacementResult {
    firstFreeInventorySlot()?.let { slot ->
        return InventoryPlacementResult.Placed(withInventoryItem(slot, item), slot)
    }

    if (overflow == RewardOverflowPolicy.SkipIfInventoryFull) {
        return InventoryPlacementResult.Full(this)
    }

    val slot = dropSlot ?: return InventoryPlacementResult.NeedsDropSlot(this)
    if (slot !in 1..InventorySaveSlotMax || inventoryItem(slot) == null) {
        return InventoryPlacementResult.InvalidDropSlot(this, slot)
    }
    return InventoryPlacementResult.Placed(withInventoryItem(slot, item), slot)
}

fun PartyState.equipFromInventory(
    memberNumber: Int,
    slot: Int,
    kind: EquipmentKind,
    itemRecords: List<ItemRecordModel> = ItemCatalog.all,
): EquipmentActionResult {
    val memberIndex = memberNumber - 1
    val member = members.getOrNull(memberIndex)
        ?: return EquipmentActionResult.InvalidMember(this, memberNumber)
    if (member.dead) return EquipmentActionResult.DeadMember(this, memberNumber)
    if (slot !in 1..InventorySaveSlotMax) return EquipmentActionResult.InvalidInventorySlot(this, slot)

    val item = inventoryItem(slot)
        ?: return EquipmentActionResult.WrongItemKind(this, slot, null)
    val record = itemRecords.associateBy { it.id }[item]
        ?: return EquipmentActionResult.WrongItemKind(this, slot, item)
    if (!record.matches(kind)) {
        return EquipmentActionResult.WrongItemKind(this, slot, item)
    }

    val oldEquipped = member.equipment.item(kind)
    val updatedMember = member.withEquipmentItem(kind, item)
    val updatedInventoryItem = oldEquipped.takeUnless { it == Items.none }
    return EquipmentActionResult.Equipped(
        party = withMember(memberIndex, updatedMember).withInventoryItem(slot, updatedInventoryItem),
        memberNumber = memberNumber,
        inventorySlot = slot,
    )
}

fun PartyState.unequipToInventory(
    memberNumber: Int,
    kind: EquipmentKind,
): EquipmentActionResult {
    val memberIndex = memberNumber - 1
    val member = members.getOrNull(memberIndex)
        ?: return EquipmentActionResult.InvalidMember(this, memberNumber)
    val equipped = member.equipment.item(kind)
    if (equipped == Items.none) {
        return EquipmentActionResult.NothingEquipped(this, memberNumber, kind)
    }

    val slot = firstFreeInventorySlot()
        ?: return EquipmentActionResult.InventoryFull(this)
    val updated = withMember(memberIndex, member.withEquipmentItem(kind, Items.none))
        .withInventoryItem(slot, equipped)
    return EquipmentActionResult.Unequipped(updated, memberNumber, slot)
}

fun EquipmentCommand.prompt(): EquipmentPromptModel =
    when (this) {
        EquipmentCommand.ReadyWeapon -> EquipmentPromptModel(
            command = this,
            kind = EquipmentKind.Weapon,
            memberFilter = MemberChoiceFilter.AliveOnly,
            inventoryFilter = InventorySlotChoiceFilter.NonEmpty,
        )
        EquipmentCommand.ReadyArmor -> EquipmentPromptModel(
            command = this,
            kind = EquipmentKind.Armor,
            memberFilter = MemberChoiceFilter.AliveOnly,
            inventoryFilter = InventorySlotChoiceFilter.NonEmpty,
        )
        EquipmentCommand.RemoveWeapon -> EquipmentPromptModel(
            command = this,
            kind = EquipmentKind.Weapon,
            memberFilter = MemberChoiceFilter.Any,
            inventoryFilter = null,
        )
        EquipmentCommand.RemoveArmor -> EquipmentPromptModel(
            command = this,
            kind = EquipmentKind.Armor,
            memberFilter = MemberChoiceFilter.Any,
            inventoryFilter = null,
        )
    }

fun EquipmentCommand.townLeavePrompt(): TextBlock =
    when (this) {
        EquipmentCommand.ReadyWeapon -> EquipmentPromptText.readyWeapons
        EquipmentCommand.ReadyArmor -> EquipmentPromptText.readyArmors
        EquipmentCommand.RemoveWeapon,
        EquipmentCommand.RemoveArmor -> error("$this is not a town-leave ready command")
    }

fun PartyState.applyEquipmentCommand(
    command: EquipmentCommand,
    memberNumber: Int,
    inventorySlot: Int? = null,
): EquipmentActionResult =
    when (command) {
        EquipmentCommand.ReadyWeapon -> equipFromInventory(memberNumber, inventorySlot ?: 0, EquipmentKind.Weapon)
        EquipmentCommand.ReadyArmor -> equipFromInventory(memberNumber, inventorySlot ?: 0, EquipmentKind.Armor)
        EquipmentCommand.RemoveWeapon -> unequipToInventory(memberNumber, EquipmentKind.Weapon)
        EquipmentCommand.RemoveArmor -> unequipToInventory(memberNumber, EquipmentKind.Armor)
    }

val EquipmentActionResult.party: PartyState
    get() = when (this) {
        is EquipmentActionResult.Equipped -> party
        is EquipmentActionResult.Unequipped -> party
        is EquipmentActionResult.InvalidMember -> party
        is EquipmentActionResult.DeadMember -> party
        is EquipmentActionResult.InvalidInventorySlot -> party
        is EquipmentActionResult.WrongItemKind -> party
        is EquipmentActionResult.NothingEquipped -> party
        is EquipmentActionResult.InventoryFull -> party
    }

fun PartyState.needsTownLeaveReadyPrompt(
    command: EquipmentCommand,
    itemRecords: List<ItemRecordModel> = ItemCatalog.all,
): Boolean =
    when (command) {
        EquipmentCommand.ReadyWeapon ->
            hasInventoryEquipment(EquipmentKind.Weapon, itemRecords) && hasLivingMemberWithout(EquipmentKind.Weapon)
        EquipmentCommand.ReadyArmor ->
            hasInventoryEquipment(EquipmentKind.Armor, itemRecords) && hasLivingMemberWithout(EquipmentKind.Armor)
        EquipmentCommand.RemoveWeapon,
        EquipmentCommand.RemoveArmor -> false
    }

fun PartyState.consumeTorch(): TorchActionResult =
    if (resources.torches <= 0) {
        TorchActionResult.NoTorches(this)
    } else {
        TorchActionResult.Consumed(copy(resources = resources.copy(torches = resources.torches - 1)))
    }

fun PartyState.tryDungeonDescent(currentDepth: Int, maxDepth: Int): DungeonDescentResult {
    require(currentDepth >= 0) { "current dungeon depth must be non-negative" }
    require(maxDepth >= 0) { "max dungeon depth must be non-negative" }
    if (resources.torches <= 0) return DungeonDescentResult.NoTorches(this)
    if (currentDepth >= maxDepth) return DungeonDescentResult.AtMaxDepth(this)
    return when (val result = consumeTorch()) {
        is TorchActionResult.Consumed -> DungeonDescentResult.Descended(result.party)
        is TorchActionResult.NoTorches -> DungeonDescentResult.NoTorches(result.party)
    }
}

fun PartyState.inventoryItem(slot: Int): ItemId? =
    inventory.firstOrNull { it.slot == slot }?.item

fun PartyState.firstFreeInventorySlot(): Int? =
    (1..InventorySaveSlotMax).firstOrNull { inventoryItem(it) == null }

fun PartyState.withInventoryItem(slot: Int, item: ItemId?): PartyState {
    require(slot in 0..InventorySaveSlotMax) { "inventory slot $slot outside 0..$InventorySaveSlotMax" }
    val slots = inventory.associateBy { it.slot }.toMutableMap()
    val previous = slots[slot]?.item
    slots[slot] = InventorySlotState(slot, item)
    val updated = copy(inventory = slots.values.sortedBy { it.slot })
    return if (previous == Items.donkey && item != Items.donkey) {
        updated.withFoodClampedToDonkeyCapacity()
    } else {
        updated
    }
}

fun PartyState.foodCapacityByDonkeys(): Int =
    150 * (countInventoryItem(Items.donkey) + 1)

fun PartyState.hasInventoryItem(item: ItemId): Boolean =
    inventory.any { it.slot in 1..InventorySaveSlotMax && it.item == item }

fun PartyState.hasInventoryEquipment(
    kind: EquipmentKind,
    itemRecords: List<ItemRecordModel> = ItemCatalog.all,
): Boolean {
    val records = itemRecords.associateBy { it.id }
    return inventory.any { slot ->
        slot.slot in 1..InventorySaveSlotMax && slot.item?.let { records[it]?.matches(kind) } == true
    }
}

fun PartyState.hasLivingMemberWithout(kind: EquipmentKind): Boolean =
    members.any { member -> !member.dead && member.equipment.item(kind) == Items.none }

fun PartyState.campGearEffect(): CampGearEffect {
    val hasTent = hasInventoryItem(Items.tent)
    val hasBlanket = hasInventoryItem(Items.blanket)
    return CampGearEffect(
        image = if (hasTent) Images.Scene.tent else Images.Scene.noTent,
        protectsLowTemperature = hasBlanket || hasTent,
        protectsVeryLowTemperature = hasTent,
    )
}

fun SpeakRuntimeContent.charactersFor(runtime: RuntimeSessionState, building: BuildingId): List<CharacterModel> {
    val town = runtime.realm.currentTown ?: return emptyList()
    val realizedByNpc = runtime.npcs.associateBy { it.npc }
    return characters
        .filter { character ->
            val position = realizedByNpc[character.id]?.position
                ?: (character.placement as? CharacterPlacementModel.Fixed)?.position
            position == WorldPosition.Town(town, building)
        }
        .sortedBy { it.name }
}

fun SpeakRuntimeContent.firstMatchingDialogueBranch(npc: NpcId, party: PartyState): DialogueBranchModel? {
    val branches = dialogue.firstOrNull { it.npc == npc }?.branches.orEmpty()
    return branches.firstOrNull { branch ->
        branch.whenState?.let(party::hasFlagState) == true
    } ?: branches.firstOrNull { it.whenState == null }
}

data class RumourSelection(
    val rumour: RumourEntryModel,
    val runtime: RuntimeSessionState,
)

fun SpeakRuntimeContent.standardRumours(party: PartyState): List<RumourEntryModel> =
    speak.textTables.rumourTables
        .firstOrNull { it.id == RumourTables.standardTown }
        ?.entries
        ?.filter { entry -> entry.excludedBy.none(party::hasFlagState) }
        .orEmpty()

fun SpeakRuntimeContent.firstStandardRumour(party: PartyState): TextBlock? =
    standardRumours(party).firstOrNull()?.text

fun SpeakRuntimeContent.selectStandardRumour(runtime: RuntimeSessionState): RumourSelection? {
    val entries = standardRumours(runtime.activeParty)
    if (entries.isEmpty()) return null
    val draw = runtime.drawRandom(0, entries.lastIndex)
    return RumourSelection(entries[draw.value], draw.runtime)
}

fun PartyState.withNpcVisit(npc: NpcId, visited: Boolean): PartyState {
    val visits = visitedNpcs.associateBy { it.npc } + (npc to NpcVisitState(npc, visited))
    return copy(visitedNpcs = visits.values.sortedBy { it.npc.slug })
}

fun PartyState.withFlagState(flag: String, native: String): PartyState =
    copy(
        flags = (flags.filterNot { it.flag.slug == flag } + runtimeFlagState(flag, native))
            .sortedWith(compareBy({ it.flag.slug }, { it.native })),
    )

fun PartyState.hasFlagState(state: FlagState): Boolean =
    flags.any { it.flag == state.flag && it.native == state.native }

fun PartyState.withMilestone(milestone: GameMilestoneId): PartyState =
    if (milestone in milestones) this else copy(milestones = (milestones + milestone).sortedBy { it.slug })

fun PartyState.withJournalLines(lines: List<String>): PartyState =
    copy(journal = journal + lines.filter { it.isNotBlank() }.map(JournalEntryState::Line))

fun TextBlock.withRandomItemOffer(itemName: String, itemPrice: Int): TextBlock =
    when (this) {
        is TextBlock.Inline -> this
        is TextBlock.Ref -> this
        is TextBlock.Structured -> TextBlock.Structured(
            lines.map { line ->
                TextLine(
                    line.parts.map { part ->
                        when (part) {
                            TextPart.OfferedItemName -> TextPart.Literal(itemName)
                            TextPart.OfferedItemPrice -> TextPart.Literal(itemPrice.toString())
                            else -> part
                        }
                    },
                )
            },
        )
    }

private fun ChitchatPromptAudience.matches(gender: Gender?): Boolean =
    when (this) {
        ChitchatPromptAudience.Any -> true
        ChitchatPromptAudience.Female -> gender == Gender.Female
        ChitchatPromptAudience.Male -> gender == Gender.Male
    }

private fun PartyState.withGold(gold: Int): PartyState =
    copy(resources = resources.copy(gold = gold.coerceAtLeast(0)))

private fun PartyState.withResource(resource: ShopResourceKind, quantity: Int): PartyState =
    when (resource) {
        ShopResourceKind.Torches -> copy(resources = resources.copy(torches = resources.torches + quantity))
        ShopResourceKind.Food -> copy(resources = resources.copy(food = resources.food + quantity))
    }

private fun PartyState.withMember(index: Int, member: PartyMemberState): PartyState =
    copy(members = members.mapIndexed { i, existing -> if (i == index) member else existing })

private val PartyState.leaderLevel: Int
    get() = members.firstOrNull()?.level ?: 0

private fun PartyState.remainingCapacity(cap: ShopQuantityCap): Int =
    when (cap) {
        ShopQuantityCap.TorchLimit80 -> 80 - resources.torches
        ShopQuantityCap.FoodByDonkeys -> foodCapacityByDonkeys() - resources.food
    }.coerceAtLeast(0)

fun PartyState.countInventoryItem(item: ItemId): Int =
    inventory.count { it.slot in 1..InventorySaveSlotMax && it.item == item }

private fun PartyState.withFoodClampedToDonkeyCapacity(): PartyState {
    val cap = foodCapacityByDonkeys()
    return if (resources.food <= cap) this else copy(resources = resources.copy(food = cap))
}

private fun ItemRecordModel.matches(kind: EquipmentKind): Boolean =
    when (kind) {
        EquipmentKind.Weapon -> this.kind == ItemKind.Weapon
        EquipmentKind.Armor -> this.kind == ItemKind.Armor
    }

private fun EquipmentState.item(kind: EquipmentKind): ItemId =
    when (kind) {
        EquipmentKind.Weapon -> weapon
        EquipmentKind.Armor -> armor
    }

private fun PartyMemberState.withEquipmentItem(kind: EquipmentKind, item: ItemId): PartyMemberState =
    copy(
        equipment = when (kind) {
            EquipmentKind.Weapon -> equipment.copy(weapon = item)
            EquipmentKind.Armor -> equipment.copy(armor = item)
        },
    )
