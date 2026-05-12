/**
 * Recorded script kernel.
 *
 * The authoring DSL records scripts into these expression, lvalue, input, view,
 * and opcode data structures instead of executing Kotlin lambdas directly. The
 * compiler validates and lowers this tree into compiled VM instructions, while
 * tests can still inspect the authored shape. Concrete game behavior should be
 * represented as authored script blocks that produce these generic nodes.
 */
package rylween.dsl

import rylween.dsl.impl.*

sealed interface IntAst {
    data class Lit(
        val value: Int,
    ) : IntAst

    data class Var(
        val name: String,
    ) : IntAst

    data class Binary(
        val left: IntAst,
        val op: String,
        val right: IntAst,
    ) : IntAst

    data class If(
        val cond: BoolAst,
        val thenValue: IntAst,
        val elseValue: IntAst,
    ) : IntAst

    data class Random(
        val lo: IntAst,
        val hi: IntAst,
    ) : IntAst

    data class MemberField(
        val member: IntAst,
        val field: String,
    ) : IntAst

    data class MonsterField(
        val monster: IntAst,
        val field: String,
    ) : IntAst

    data class BattleFlag(
        val name: String,
    ) : IntAst

    data class BattleQuery(
        val name: String,
    ) : IntAst

    data class RealmScalar(
        val name: String,
    ) : IntAst

    data class PartyScalar(
        val name: String,
    ) : IntAst

    data class InventoryPoolCount(
        val pool: ItemPoolId,
    ) : IntAst

    data class InventorySlotCost(
        val slot: IntAst,
    ) : IntAst

    data class NativeInt(
        val path: String,
        val args: List<String>,
    ) : IntAst
}

sealed interface BoolAst {
    data class Cmp(
        val left: IntAst,
        val op: String,
        val right: IntAst,
    ) : BoolAst

    data class Not(
        val expr: BoolAst,
    ) : BoolAst

    data class And(
        val left: BoolAst,
        val right: BoolAst,
    ) : BoolAst

    data class Or(
        val left: BoolAst,
        val right: BoolAst,
    ) : BoolAst

    data class FlagIs(
        val slug: String,
        val nativeValue: String,
    ) : BoolAst

    data class MilestoneAchieved(
        val milestone: GameMilestoneId,
    ) : BoolAst

    data class MemberFlag(
        val member: IntAst,
        val name: String,
    ) : BoolAst

    data class MonsterFlag(
        val monster: IntAst,
        val name: String,
    ) : BoolAst

    data class BattleFlag(
        val name: String,
    ) : BoolAst

    data class RuntimeFlag(
        val name: String,
    ) : BoolAst
}

sealed interface StrAst {
    data class NativeString(
        val path: String,
        val args: List<NativeStringArg>,
    ) : StrAst
}

sealed interface NativeStringArg {
    data class Literal(
        val value: String,
    ) : NativeStringArg

    data class IntValue(
        val value: IntAst,
    ) : NativeStringArg
}

sealed interface LValueAst {
    data class RealmScalar(
        val name: String,
    ) : LValueAst

    data class PartyScalar(
        val name: String,
    ) : LValueAst

    data class MemberField(
        val member: IntAst,
        val field: String,
    ) : LValueAst

    data class MonsterField(
        val monster: IntAst,
        val field: String,
    ) : LValueAst

    data class BattleFlag(
        val name: String,
    ) : LValueAst
}

enum class ScriptControlSignal { Continue, BreakNearestLoop, StopScript }

enum class MemberChoiceFilter { Any, AliveOnly, DeadOnly, SpellcasterOnly }

enum class InventorySlotChoiceFilter { Any, NonEmpty, Empty }

sealed interface ViewCommand {
    data object ClearText : ViewCommand

    data object ClearMapWindow : ViewCommand

    data object RenderMap : ViewCommand

    data object StatusLine : ViewCommand

    data class ShowImage(
        val image: ImageId,
    ) : ViewCommand
}

data class InputBindingModel(
    val primary: String,
    val aliases: List<String> = emptyList(),
    val label: String = primary,
) {
    val tokens: List<String> get() = listOf(primary) + aliases
    val primaryChar: Char
        get() =
            primary.singleOrNull()?.uppercaseChar()
                ?: error("input binding primary '$primary' is not a single character")
}

object InputAliases {
    const val ARROW_UP: String = "\u001B[A"
    const val ARROW_DOWN: String = "\u001B[B"
    const val ARROW_RIGHT: String = "\u001B[C"
    const val ARROW_LEFT: String = "\u001B[D"
}

fun inputKey(
    primary: Char,
    vararg aliases: String,
    label: String = primary.uppercaseChar().toString(),
): InputBindingModel = inputKey(primary.uppercaseChar().toString(), *aliases, label = label)

fun inputKey(
    primary: String,
    vararg aliases: String,
    label: String = primary,
): InputBindingModel = InputBindingModel(primary = primary, aliases = aliases.toList(), label = label)

fun InputBindingModel.matches(raw: String): Boolean {
    val accepted = tokens.flatMap(::inputTokenVariants).toSet()
    return inputTokenVariants(raw).any { it in accepted }
}

fun inputTokenVariants(raw: String): Set<String> =
    buildSet {
        add(raw)
        raw.firstOrNull()?.takeIf { it == ' ' }?.let { add(" ") }
        val trimmed = raw.trim()
        if (trimmed.isNotEmpty()) {
            add(trimmed)
            add(trimmed.uppercase())
            add(trimmed.replace("_", "").uppercase())
        }
    }

sealed interface Op {
    data class Screen(
        val text: TextBlock,
    ) : Op

    data class Emit(
        val text: TextId,
    ) : Op

    data class EmitIndexed(
        val pattern: TextPattern,
        val value: IntAst,
    ) : Op

    data class WaitInput(
        val reason: String? = null,
    ) : Op

    data class Pause(
        val millis: Int,
    ) : Op

    data class View(
        val command: ViewCommand,
    ) : Op

    data class LetInt(
        val name: String,
        val value: IntAst,
    ) : Op

    data class BindText(
        val name: String,
        val value: StrAst,
    ) : Op

    data class Set(
        val target: LValueAst,
        val value: IntAst,
    ) : Op

    data class Add(
        val target: LValueAst,
        val value: IntAst,
    ) : Op

    data class Sub(
        val target: LValueAst,
        val value: IntAst,
    ) : Op

    data class AddClamped(
        val target: LValueAst,
        val value: IntAst,
        val max: IntAst,
    ) : Op

    data class SetFlag(
        val slug: String,
        val nativeValue: String,
    ) : Op

    data class AppendJournal(
        val text: TextBlock,
    ) : Op

    data class If(
        val cond: BoolAst,
        val thenOps: List<Op>,
        val elseOps: List<Op>,
    ) : Op

    data class Loop(
        val body: List<Op>,
    ) : Op

    data class Break(
        val reason: String? = null,
        val signal: ScriptControlSignal = ScriptControlSignal.BreakNearestLoop,
    ) : Op

    data class Stop(
        val reason: String? = null,
        val signal: ScriptControlSignal = ScriptControlSignal.StopScript,
    ) : Op

    data class Switch(
        val expr: IntAst,
        val cases: List<SwitchCase>,
    ) : Op

    data class Menu(
        val prompt: TextBlock,
        val options: List<MenuOption>,
        val defaultOps: List<Op>,
    ) : Op

    data class AskKey(
        val prompt: TextBlock,
        val varName: String,
        val bindings: List<InputBindingModel>,
    ) : Op

    data class AskMember(
        val prompt: TextBlock,
        val varName: String,
        val filter: MemberChoiceFilter,
        val accepting: MemberChoiceFilter = filter,
    ) : Op

    data class AskInventorySlot(
        val prompt: TextBlock,
        val varName: String,
        val firstKey: Char,
        val slots: IntRange,
        val filter: InventorySlotChoiceFilter,
    ) : Op

    data class AskText(
        val prompt: TextBlock,
        val accepted: List<String>,
        val matchOps: List<Op>,
        val missOps: List<Op>,
        val caseSensitive: Boolean = false,
        val trim: Boolean = true,
        val maxLength: Int? = null,
    ) : Op

    data class AskMonster(
        val prompt: TextBlock,
        val varName: String,
    ) : Op

    data class ForEachMember(
        val varName: String,
        val includeDead: Boolean,
        val body: List<Op>,
    ) : Op

    data class ForEachMonster(
        val varName: String,
        val includeDead: Boolean,
        val body: List<Op>,
    ) : Op

    data class Speak(
        val building: BuildingId,
    ) : Op

    data class TellRumor(
        val table: RumorTableId,
    ) : Op

    data class RunScene(
        val scene: SceneId,
        val bindings: Map<String, String>,
    ) : Op

    data class Encounter(
        val foe: MonsterId,
        val mandatory: Boolean,
        val tier: EncounterTier? = null,
    ) : Op

    data class EnterDungeon(
        val dungeon: DungeonId,
        val levels: Int,
    ) : Op

    object ConsumeActiveItem : Op

    data class ClearInventorySlot(
        val slot: IntAst,
    ) : Op

    data class ConsumeInventoryPool(
        val pool: ItemPoolId,
        val quantity: IntAst,
    ) : Op

    object ConsumeTorch : Op

    data class TeleportParty(
        val directionKey: IntAst,
        val distance: IntAst,
    ) : Op

    data class DiscoverCave(
        val dungeon: DungeonId,
        val levels: IntAst,
        val moveCost: IntAst,
    ) : Op

    data class GrantReward(
        val reward: ScriptRewardModel,
    ) : Op

    data class RandomItemOffer(
        val offer: RandomItemOfferModel,
    ) : Op

    data class ShopService(
        val shop: ShopServiceModel,
    ) : Op

    data class AchieveMilestone(
        val milestone: GameMilestoneId,
    ) : Op

    data class NativeCall(
        val path: String,
        val args: List<String>,
    ) : Op
}

data class SwitchCase(
    val match: Int?,
    val body: List<Op>,
)

data class MenuOption(
    val binding: InputBindingModel,
    val body: List<Op>,
) {
    val key: Char get() = binding.primaryChar
}

data class SceneModel(
    val id: SceneId,
    val ops: List<Op>,
)

data class DialogueModel(
    val npc: NpcId,
    val branches: List<DialogueBranchModel>,
)

data class DialogueBranchModel(
    val whenState: FlagState?,
    val ops: List<Op>,
)

data class SpellModel(
    val id: SpellId,
    val slot: Int,
    val baseCost: Int,
    val name: String,
    val ops: List<Op>,
)

data class ItemUseModel(
    val id: ItemId,
    val contexts: Set<ItemUseContext>,
    val consume: ItemConsumePolicy,
    val ops: List<Op>,
)

enum class DungeonEventDisposition { ClearAfterRun, Recurring }

data class DungeonEventModel(
    val id: DungeonEventId,
    val disposition: DungeonEventDisposition,
    val ops: List<Op>,
    val randomEncounterTier: EncounterTier? = null,
    val clearWhenFlag: FlagState? = null,
)

data class DungeonRandomEventModel(
    val event: DungeonEventId,
    val weight: Int,
)

data class DungeonScenarioModel(
    val dungeon: DungeonId,
    val name: String? = null,
    val levelRange: IntRange?,
    val fixedLevelEvents: Map<Int, DungeonEventId>,
    val randomEvents: List<DungeonRandomEventModel>,
)

data class DungeonSystemModel(
    val events: List<DungeonEventModel> = emptyList(),
    val scenarios: List<DungeonScenarioModel> = emptyList(),
    val backdrop: ImageId? = null,
    val keys: DungeonKeysModel = DungeonKeysModel(),
) {
    fun displayName(dungeon: DungeonId): String =
        scenarios
            .firstOrNull { it.dungeon == dungeon }
            ?.name
            ?: dungeon.slug.replace('_', ' ')
}
