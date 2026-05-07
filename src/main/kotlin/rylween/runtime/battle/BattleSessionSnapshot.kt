/**
 * Engine snapshot records used when a script blocks in combat.
 *
 * `runtime/ScriptContinuation.kt` embeds these snapshots beside authored or
 * compiled parent frames, while `save/StateSnapshot.kt` can render them into
 * the strict runtime snapshot DSL for validation, tests, or backend handoff.
 * This is not a frontend policy that permits player saves during combat; save
 * availability stays with the active session/frontend. The live [BattleSession]
 * still owns combat rules and prompt handling, and this file only records
 * enough host state for another runtime to resume the same battle boundary.
 */

package rylween.runtime.battle

import rylween.dsl.CombatActionRequirement
import rylween.dsl.EncounterTier
import rylween.dsl.ImageId
import rylween.dsl.ItemUseContext
import rylween.dsl.MonsterId
import rylween.dsl.PcClass
import rylween.dsl.RuntimeContent
import rylween.dsl.TextBlock
import rylween.dsl.ViewCommand
import rylween.runtime.RuntimeSessionState

/** Portable battle session state stored inside a script continuation snapshot. */
data class BattleSessionSnapshot(
    val tier: EncounterTier?,
    val monster: MonsterId,
    val count: Int,
    val hitPoints: List<Int>,
    val hitPointMax: Int,
    val mandatory: Boolean,
    val round: BattleRoundState,
    val step: BattleSavedStep,
    val lastResult: BattleSessionResultSnapshot,
) {
    init {
        require(count > 0) { "battle snapshot monster count must be positive" }
        require(hitPointMax > 0) { "battle snapshot hit point max must be positive" }
        require(hitPoints.size == count) { "battle snapshot hit point list must match monster count" }
    }
}

/** Snapshot result marker for the active battle. */
data class BattleSessionResultSnapshot(
    val status: BattleSessionResultStatus,
    val monster: MonsterId? = null,
    val tier: EncounterTier? = null,
)

/** Snapshot form of battle prompt state. */
sealed interface BattleSavedStep {
    data class Opening(
        val prompt: TextBlock,
        val image: ImageId? = null,
    ) : BattleSavedStep

    data class RoundOpening(
        val prompt: TextBlock,
    ) : BattleSavedStep

    data class Message(
        val texts: List<TextBlock>,
        val next: BattleSavedStep,
        val viewCommands: List<ViewCommand> = emptyList(),
    ) : BattleSavedStep

    data class PlayerAction(
        val memberIndex: Int,
        val prompt: TextBlock,
    ) : BattleSavedStep

    data class MonsterTarget(
        val memberIndex: Int,
        val prompt: TextBlock,
        val targetKeys: List<Char>,
    ) : BattleSavedStep

    data class ScriptMonsterTarget(
        val prompt: TextBlock,
        val targetKeys: List<Char>,
        val script: CombatScriptSnapshot,
    ) : BattleSavedStep

    data class MemberTarget(
        val prompt: TextBlock,
        val targetKeys: List<Char>,
        val script: CombatScriptSnapshot,
    ) : BattleSavedStep

    data class MagicItemSlot(
        val memberIndex: Int,
        val prompt: TextBlock,
        val slotKeys: List<Char>,
    ) : BattleSavedStep

    data class SpellChoice(
        val memberIndex: Int,
        val prompt: TextBlock,
        val spellKeys: List<Char>,
    ) : BattleSavedStep

    object MonsterRound : BattleSavedStep

    object Finished : BattleSavedStep
}

/** Combat-script continuation record with authored ops recovered from runtime content on restore. */
data class CombatScriptSnapshot(
    val memberIndex: Int?,
    val sourceKind: CombatScriptSourceKind,
    val sourceSlug: String,
    val nextIndex: Int,
    val pendingVar: String,
    val locals: Map<String, Int>,
    val activeInventorySlot: Int? = null,
)

/** Converts a live battle session into a content-independent snapshot. */
fun BattleSession.toSnapshot(): BattleSessionSnapshot =
    BattleSessionSnapshot(
        tier = tier,
        monster = encounter.monster.id,
        count = encounter.count,
        hitPoints = encounter.hitPoints,
        hitPointMax = encounter.hitPointMax,
        mandatory = encounter.mandatory,
        round = round,
        step = step.toSnapshot(),
        lastResult = lastResult.toSnapshot(),
    )

/** Restores a battle session from snapshot data and current authored [content]. */
fun BattleSessionSnapshot.toBattleSession(
    runtime: RuntimeSessionState,
    content: RuntimeContent,
): BattleSession {
    val battle = requireNotNull(content.combat.standardBattle) { "standard battle is not configured" }
    val monsterModel =
        content.monsters.singleOrNull { candidate -> candidate.id == monster }
            ?: error("battle snapshot references unknown monster ${monster.slug}")
    return BattleSession(
        runtime = runtime,
        battle = battle,
        content = content,
        tier = tier,
        encounter =
            BattleMonsterState(
                monster = monsterModel,
                count = count,
                hitPoints = hitPoints,
                hitPointMax = hitPointMax,
                mandatory = mandatory,
            ),
        round = round,
        step = step.toBattleSessionStep(runtime, content),
        lastResult =
            BattleSessionResult(
                status = lastResult.status,
                monster = lastResult.monster,
                tier = lastResult.tier,
            ),
    )
}

private fun BattleSessionResult.toSnapshot(): BattleSessionResultSnapshot = BattleSessionResultSnapshot(status, monster, tier)

private fun BattleSessionStep.toSnapshot(): BattleSavedStep =
    when (this) {
        is BattleSessionStep.Opening -> BattleSavedStep.Opening(prompt, image)
        is BattleSessionStep.RoundOpening -> BattleSavedStep.RoundOpening(prompt)
        is BattleSessionStep.Message -> BattleSavedStep.Message(texts, next.toSnapshot(), viewCommands)
        is BattleSessionStep.PlayerAction -> BattleSavedStep.PlayerAction(memberIndex, prompt)
        is BattleSessionStep.MonsterTarget -> BattleSavedStep.MonsterTarget(memberIndex, prompt, targetKeys)
        is BattleSessionStep.ScriptMonsterTarget -> BattleSavedStep.ScriptMonsterTarget(prompt, targetKeys, script.toSnapshot())
        is BattleSessionStep.MemberTarget -> BattleSavedStep.MemberTarget(prompt, targetKeys, script.toSnapshot())
        is BattleSessionStep.MagicItemSlot -> BattleSavedStep.MagicItemSlot(memberIndex, prompt, slotKeys)
        is BattleSessionStep.SpellChoice -> BattleSavedStep.SpellChoice(memberIndex, prompt, spellKeys)
        BattleSessionStep.MonsterRound -> BattleSavedStep.MonsterRound
        is BattleSessionStep.Finished -> BattleSavedStep.Finished
    }

private fun BattleSavedStep.toBattleSessionStep(
    runtime: RuntimeSessionState,
    content: RuntimeContent,
): BattleSessionStep =
    when (this) {
        is BattleSavedStep.Opening -> BattleSessionStep.Opening(prompt, image)
        is BattleSavedStep.RoundOpening -> BattleSessionStep.RoundOpening(prompt)
        is BattleSavedStep.Message -> BattleSessionStep.Message(texts, next.toBattleSessionStep(runtime, content), viewCommands)
        is BattleSavedStep.PlayerAction ->
            BattleSessionStep.PlayerAction(memberIndex, prompt, availableActions(runtime, content, memberIndex))
        is BattleSavedStep.MonsterTarget -> BattleSessionStep.MonsterTarget(memberIndex, prompt, targetKeys)
        is BattleSavedStep.ScriptMonsterTarget ->
            BattleSessionStep.ScriptMonsterTarget(prompt, targetKeys, script.toCombatScriptContinuation(content))
        is BattleSavedStep.MemberTarget -> BattleSessionStep.MemberTarget(prompt, targetKeys, script.toCombatScriptContinuation(content))
        is BattleSavedStep.MagicItemSlot -> BattleSessionStep.MagicItemSlot(memberIndex, prompt, slotKeys)
        is BattleSavedStep.SpellChoice -> BattleSessionStep.SpellChoice(memberIndex, prompt, spellKeys)
        BattleSavedStep.MonsterRound -> BattleSessionStep.MonsterRound
        BattleSavedStep.Finished -> BattleSessionStep.Finished(runtime)
    }

private fun CombatScriptContinuation.toSnapshot(): CombatScriptSnapshot =
    CombatScriptSnapshot(
        memberIndex = memberIndex,
        sourceKind = sourceKind,
        sourceSlug = sourceSlug,
        nextIndex = nextIndex,
        pendingVar = pendingVar,
        locals = locals,
        activeInventorySlot = activeInventorySlot,
    )

private fun CombatScriptSnapshot.toCombatScriptContinuation(content: RuntimeContent): CombatScriptContinuation {
    val ops =
        when (sourceKind) {
            CombatScriptSourceKind.Spell,
            CombatScriptSourceKind.MonsterSpell,
            ->
                content.spells
                    .singleOrNull { spell -> spell.id.slug == sourceSlug }
                    ?.ops
            CombatScriptSourceKind.MagicItem ->
                content.itemUses
                    .singleOrNull { use -> use.id.slug == sourceSlug && ItemUseContext.Combat in use.contexts }
                    ?.ops
        } ?: error("battle script snapshot references unknown ${sourceKind.name} script $sourceSlug")
    return CombatScriptContinuation(
        memberIndex = memberIndex,
        sourceKind = sourceKind,
        sourceSlug = sourceSlug,
        ops = ops,
        nextIndex = nextIndex,
        pendingVar = pendingVar,
        locals = locals,
        activeInventorySlot = activeInventorySlot,
    )
}

private fun availableActions(
    runtime: RuntimeSessionState,
    content: RuntimeContent,
    memberIndex: Int,
) = content.combat.standardBattle
    ?.actions
    .orEmpty()
    .filter { action ->
        val member = runtime.activeParty.members.getOrNull(memberIndex)
        when (action.requirement) {
            CombatActionRequirement.Always -> true
            CombatActionRequirement.Mage -> member?.pcClass == PcClass.Mage
        }
    }
