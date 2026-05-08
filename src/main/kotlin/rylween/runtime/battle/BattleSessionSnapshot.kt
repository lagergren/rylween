/**
 * Data-only battle continuation snapshots for compiled/runtime state records.
 *
 * The live Kotlin battle session is archived under `legacy/reference-runtime`.
 * Active code keeps only these stable records so compiled bundles and external
 * runtimes can describe a blocked battle boundary without depending on that
 * old interpreter.
 */
package rylween.runtime.battle

import rylween.dsl.EncounterTier
import rylween.dsl.ImageId
import rylween.dsl.MonsterId
import rylween.dsl.TextBlock
import rylween.dsl.ViewCommand

/** Stable result categories for a battle boundary. */
enum class BattleSessionResultStatus {
    EncounterStarted,
    FightSelected,
    RunEscaped,
    RunFailed,
    RunDenied,
    Victory,
    PartyDefeated,
}

/** Portable battle-round state needed to resume a blocked battle boundary. */
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

/** Script source families that can block inside a battle boundary. */
enum class CombatScriptSourceKind { Spell, MagicItem, MonsterSpell }

/** Combat-script continuation record with authored ops recovered by the runtime implementation. */
data class CombatScriptSnapshot(
    val memberIndex: Int?,
    val sourceKind: CombatScriptSourceKind,
    val sourceSlug: String,
    val nextIndex: Int,
    val pendingVar: String,
    val locals: Map<String, Int>,
    val activeInventorySlot: Int? = null,
)
