// RylweenDsl.kt -- Kotlin implementation for the Rylween authoring DSL.
//
// This file is implementation/compiler scaffolding: typed handles, immutable
// models, builders, and recorded script ASTs. Current-game handles are
// generated from RylweenGame.dsl.kts by the Gradle content-generation task.
// Game-specific content belongs in RylweenGame.dsl.kts, with reusable
// game-script helpers in
// RylweenGameScripts.kt. Validation helpers live in RylweenDslValidation.kt.
//
// Validate the game source with:
//
//   ./gradlew check
//
// Current authoring goals:
//
//   1. Kotlin is the source format.
//   2. Scripts record a real AST instead of being Unit-shaped notes.
//   3. Flags, monsters, items, scenes, text ids, NPC roles, and spells are
//      typed handles.
//   4. A script author cannot write arbitrary flag or encounter strings at use
//      sites. The implementor declares the native slug once; script bodies use
//      typed state markers and catalog handles.
//   5. Game scripts mostly use game verbs. Engine-native calls are explicit
//      under `native.*`.
//   6. Kotlin implementation code should be idiomatic Kotlin.
//   7. Game-specific declarations should read like game data, not Kotlin
//      programming. Generated handles and catalogs should derive from the
//      author's declarations.

@file:Suppress(
    "unused",
    "MemberVisibilityCanBePrivate",
    "RedundantVisibilityModifier",
    "UNUSED_PARAMETER",
    "CanBeParameter",
    "ConstPropertyName",
    "FunctionName",
    "KotlinExplicitApi",
    "PropertyName",
)

package rylween.dsl

// =============================================================================
// 01. DSL kernel: typed handles and factories
// =============================================================================
//
// Current content catalogs use these reusable types and factories.

enum class FlagScope { Party, Encounter }

abstract class FlagRef(
    val slug: String,
    val scope: FlagScope = FlagScope.Party,
) {
    private val declaredStates = mutableListOf<FlagState>()
    val states: List<FlagState> get() = declaredStates

    protected fun state(native: String): FlagState =
        FlagState(this, native).also { declaredStates += it }
}

open class FlagState internal constructor(
    val flag: FlagRef,
    val native: String,
) : BoolExpr() {
    override val ast: BoolAst = BoolAst.FlagIs(flag.slug, native)
}

val declaredFlags: MutableList<FlagRef> = mutableListOf()

fun <F : FlagRef> F.declareFlag(): F = also { declaredFlags += it }

private class RuntimeFlagRef(slug: String) : FlagRef(slug) {
    fun value(native: String): FlagState = state(native)
}

fun runtimeFlagState(flag: String, native: String): FlagState =
    declaredFlags
        .firstOrNull { it.slug == flag }
        ?.states
        ?.firstOrNull { it.native == native }
        ?: RuntimeFlagRef(flag).value(native)

data class TextId(val id: String)
data class TextPattern(val pattern: String)
sealed interface TextPart {
    data class Literal(val text: String) : TextPart
    data class IntValue(val value: IntAst) : TextPart
    data class StringValue(val value: StrAst) : TextPart
    data class RoleName(val role: NpcRole) : TextPart
    data class RoleLocation(val role: NpcRole) : TextPart
    data class TownName(val town: TownId) : TextPart
    data class FuzzyTownLocation(val town: TownId, val radius: Int) : TextPart
    data class LocationCoordinate(val location: LocationId) : TextPart
    object OfferedItemName : TextPart
    object OfferedItemPrice : TextPart
    object SelectedItemName : TextPart
    object ShopPrice : TextPart
    object ShopCarryCap : TextPart
    object HuntMemberName : TextPart
    object HuntMemberSubjectPronoun : TextPart
    object HuntFoodGained : TextPart
    object PartyMemberMax : TextPart
    object WildernessDungeonName : TextPart
    object WildernessDungeonLevels : TextPart
    object DungeonDepth : TextPart
    object ConversationNpcName : TextPart
}

fun TextPart.fallbackText(): String =
    when (this) {
        is TextPart.Literal -> text
        is TextPart.IntValue -> "{int}"
        is TextPart.StringValue -> "{string}"
        is TextPart.RoleName -> "{${role.slug}.name}"
        is TextPart.RoleLocation -> "{${role.slug}.location}"
        is TextPart.TownName -> town.slug
        is TextPart.FuzzyTownLocation -> "${town.slug}.near"
        is TextPart.LocationCoordinate -> "{${location.slug}.coordinates}"
        TextPart.OfferedItemName -> "{item_name}"
        TextPart.OfferedItemPrice -> "{item_price}"
        TextPart.SelectedItemName -> "{selected_item_name}"
        TextPart.ShopPrice -> "{price}"
        TextPart.ShopCarryCap -> "{cap}"
        TextPart.HuntMemberName -> "{who_name}"
        TextPart.HuntMemberSubjectPronoun -> "{pro_sub}"
        TextPart.HuntFoodGained -> "{fdplus}"
        TextPart.PartyMemberMax -> "{party_max}"
        TextPart.WildernessDungeonName -> "{dungeon}"
        TextPart.WildernessDungeonLevels -> "{levels}"
        TextPart.DungeonDepth -> "{level}"
        TextPart.ConversationNpcName -> "{npc_name}"
    }

data class TextLine(val parts: List<TextPart>)
sealed interface TextBlock {
    data class Ref(val id: TextId) : TextBlock
    data class Inline(val lines: List<String>) : TextBlock
    data class Structured(val lines: List<TextLine>) : TextBlock
}
fun inlineText(vararg lines: String): TextBlock = TextBlock.Inline(lines.toList())
fun structuredText(body: StructuredTextBlockBuilder.() -> Unit): TextBlock =
    StructuredTextBlockBuilder().apply(body).build()
fun textLines(vararg lines: String): TextBlock = structuredText { lines(*lines) }

interface TextLineSink {
    fun addTextLine(line: TextLine)
}

fun TextLineSink.line(vararg parts: Any) {
    addTextLine(TextLine(parts.map(::textPart)))
}

fun TextLineSink.lines(vararg lines: String) {
    lines.forEach { line(it) }
}

fun <T : TextLineSink> T.lines(body: T.() -> Unit) {
    body()
}

fun combineTextBlocks(vararg blocks: TextBlock): TextBlock =
    combineTextBlocks(blocks.toList())

fun combineTextBlocks(blocks: List<TextBlock>): TextBlock =
    TextBlock.Structured(
        blocks.flatMap { block -> block.asStructuredLines() },
    )

private fun TextBlock.asStructuredLines(): List<TextLine> =
    when (this) {
        is TextBlock.Inline -> lines.map { line -> TextLine(listOf(TextPart.Literal(line))) }
        is TextBlock.Ref -> listOf(TextLine(listOf(TextPart.Literal("[${id.id}]"))))
        is TextBlock.Structured -> lines
    }

private fun textPart(part: Any): TextPart =
    when (part) {
        is TextPart -> part
        is IntExpr -> TextPart.IntValue(part.ast)
        is StrExpr -> TextPart.StringValue(part.ast)
        is String -> TextPart.Literal(part)
        else -> TextPart.Literal(part.toString())
    }

class CharacterTextRef(private val role: NpcRole) {
    val name: TextPart = TextPart.RoleName(role)
    val location: TextPart = TextPart.RoleLocation(role)
}
fun character(role: NpcRole): CharacterTextRef = CharacterTextRef(role)

class TownTextRef(private val town: TownId) {
    val name: TextPart = TextPart.TownName(town)
    fun near(radius: Int = 5): TextPart = TextPart.FuzzyTownLocation(town, radius)
}

interface TownHandle {
    val id: TownId
}

fun town(town: TownHandle): TownTextRef = TownTextRef(town.id)

class LocationTextRef(private val location: LocationId) {
    val coordinates: TextPart = TextPart.LocationCoordinate(location)
}
fun location(location: LocationId): LocationTextRef = LocationTextRef(location)

class ItemId private constructor(val slug: String) {
    override fun equals(other: Any?): Boolean =
        other is ItemId && slug == other.slug

    override fun hashCode(): Int = slug.hashCode()

    override fun toString(): String =
        "ItemId(slug=$slug)"

    companion object {
        fun authored(slug: String): ItemId = ItemId(slug)
    }
}
class ItemPoolId private constructor(
    val slug: String,
    val items: List<ItemId> = emptyList(),
) {
    override fun equals(other: Any?): Boolean =
        other is ItemPoolId && slug == other.slug

    override fun hashCode(): Int = slug.hashCode()

    override fun toString(): String =
        "ItemPoolId(slug=$slug)"

    companion object {
        fun authored(slug: String, items: List<ItemId> = emptyList()): ItemPoolId =
            ItemPoolId(slug, items)
    }
}
enum class ItemKind { Empty, Weapon, Armor, Gear, Potion, Treasure, Special, MagicItem }
enum class EquipmentCommand { ReadyWeapon, ReadyArmor, RemoveWeapon, RemoveArmor }
enum class ItemStorageKind { EmptySentinel, SlottedInventory, SpecialMilestone }
enum class Resource { Food, Torches }
enum class TemperatureBand { Low, VeryLow }
data class ItemRecordModel(
    val id: ItemId,
    val kind: ItemKind,
    val displayName: String,
    val cost: Int,
    val storage: ItemStorageKind,
    val weaponDamage: Int? = null,
    val armorClass: Int? = null,
    val campImage: ImageId? = null,
    val campProtection: Set<TemperatureBand> = emptySet(),
    val resourceCapacityBonus: Map<Resource, Int> = emptyMap(),
)
data class RealmContentModel(
    val baseResourceCapacity: Map<Resource, Int> = emptyMap(),
    val upkeep: WildernessUpkeepRules = WildernessUpkeepRules(),
    val camp: CampRulesModel = CampRulesModel(),
    val hunt: HuntRulesModel? = null,
)

enum class WildernessFatigueMatch { Equal, LessThan }

data class WildernessFatigueRule(
    val threshold: Int,
    val text: TextBlock,
    val match: WildernessFatigueMatch = WildernessFatigueMatch.Equal,
    val drain: Boolean = false,
)

/**
 * Authored wilderness turn upkeep. The runtime owns applying a turn; designers
 * own food cadence, starvation damage/threshold, mana recovery chance, sleep
 * loss, and the fatigue message ladder.
 */
data class WildernessUpkeepRules(
    val foodCostLeaderDiv: Int = 0,
    val foodTickEvery: Int = 0,
    val starveFoodThreshold: Int = 0,
    val starveDamage: Int = 0,
    val manaRegenChance: Int = 0,
    val restedHoursCostPerTurn: Int = 0,
    val fatigue: List<WildernessFatigueRule> = emptyList(),
) {
    init {
        require(foodTickEvery >= 0) { "food tick interval must be non-negative" }
        require(foodCostLeaderDiv >= 0) { "food leader divisor must be non-negative" }
        require(starveFoodThreshold >= 0) { "starvation food threshold must be non-negative" }
        require(starveDamage >= 0) { "starve damage must be non-negative" }
        require(manaRegenChance in 0..100) { "mana regen chance must be 0..100" }
        require(restedHoursCostPerTurn >= 0) { "rested-hours turn cost must be non-negative" }
    }
}

/**
 * Authored camp / rest balance. Read by the camp runtime; every value is
 * data so a different game can change healing, food cost, temperature
 * bands, or shelter rules without touching the runtime. Damage applies
 * to members not protected by an item with [TemperatureBand] coverage
 * declared on the carried inventory (see [ItemRecordModel.campProtection]).
 */
data class CampRulesModel(
    val healthGain: Int = 0,
    val foodCostPerMember: Int = 0,
    val hoursAdvanced: Int = 0,
    val hoursRestedReset: Int = 0,
    val temperatureMax: Int = 0,
    val temperatureBands: List<TemperatureBandRange> = emptyList(),
    val temperatureDamage: Map<TemperatureBand, Int> = emptyMap(),
    val ambushChancePercent: Int = 0,
    val ambushTier: EncounterTier? = null,
    val notTired: TextBlock = inlineText("You are not tired."),
    val notEnoughFood: TextBlock = inlineText("Not enough food."),
    val guardPrompt: TextBlock = inlineText("Camp.", "", "Post a guard?", ""),
    val guardMemberPrompt: TextBlock = inlineText("Who?"),
    val guardMemberDead: TextBlock = inlineText("Dead."),
    val standsGuard: TextBlock = inlineText("Guard stands watch."),
    val sleeping: TextBlock = inlineText("Zzz..."),
    val rested: TextBlock = inlineText("You feel rested."),
    val ambush: TextBlock = inlineText("Ambush!!!"),
    val surpriseKill: TextBlock = inlineText("A camper is dead", "before you can react!"),
    val temperatureMessage: Map<TemperatureBand, TextBlock> = emptyMap(),
    val noShelterMessage: Map<TemperatureBand, TextBlock> = emptyMap(),
)

data class TemperatureBandRange(val band: TemperatureBand, val range: IntRange)

/**
 * Authored wilderness hunting balance. The runtime owns party-state mutation
 * and deterministic RNG; designers own the chance, move cost, food range, and
 * player-facing narration.
 */
data class HuntRulesModel(
    val successChancePercent: Int,
    val timeCost: Int,
    val foodMin: Int,
    val foodMax: Int,
    val whoPrompt: TextBlock,
    val dead: TextBlock,
    val notRanger: TextBlock,
    val tooFewMoves: TextBlock,
    val tries: TextBlock,
    val succeeds: TextBlock,
    val fails: TextBlock,
) {
    init {
        require(successChancePercent in 0..100) { "hunt success chance must be 0..100" }
        require(timeCost >= 0) { "hunt time cost must be non-negative" }
        require(foodMin >= 0) { "hunt food minimum must be non-negative" }
        require(foodMax >= foodMin) { "hunt food range must be ascending: $foodMin..$foodMax" }
    }
}

data class MonsterId(val slug: String)
data class EncounterTier(val slug: String)
data class WorldMapId(val slug: String)
data class MapLayerId(val slug: String)
data class TerrainId(val slug: String, val sourceMapCode: Char? = null)
data class TerrainTagId(val slug: String)
data class ImageId(val slug: String)
data class StaticScreenId(val slug: String)
data class PaletteId(val slug: String)
data class PaletteColorId(val slug: String)
data class LayoutId(val slug: String)
data class ScreenRegionId(val slug: String)
data class SceneId(val slug: String)
data class SpellId(val slug: String)
data class MissionId(val slug: String)
data class NpcRole(val slug: String)
data class NpcJobId(val slug: String)
data class NativeOpId(val slug: String)
data class StateId(val slug: String)
data class PartyId(val slug: String)
data class NpcId(val slug: String)
data class LocationId(val slug: String)
data class TownId(val slug: String)
data class BuildingId(val slug: String)
data class FormulaId(val slug: String)
data class KeyActionId(val slug: String)
data class DungeonId(val slug: String)
data class DungeonEventId(val slug: String)
data class GameMilestoneId(val slug: String)
data class LootTableId(val slug: String)
data class RewardBundleId(val slug: String)
data class RumourTableId(val slug: String)
data class RumourId(val slug: String)

data class TerminalTerrainStyleModel(
    val terrain: TerrainId,
    val glyph: String,
    val color: PaletteColorId,
)

data class ScreenRegionModel(
    val id: ScreenRegionId,
    val x: Int,
    val y: Int,
    val width: Int? = null,
    val height: Int? = null,
)

data class TerminalPresentationModel(
    val terrain: List<TerminalTerrainStyleModel> = emptyList(),
    val regions: List<ScreenRegionModel> = emptyList(),
)

data class WorldMapModel(
    val id: WorldMapId,
    val layers: List<WorldMapLayerModel>,
)

data class WorldMapLayerModel(
    val id: MapLayerId,
    val rows: List<String>,
)

data class VisualsModel(
    val staticScreens: List<StaticScreenModel> = emptyList(),
)

data class StaticScreenModel(
    val id: StaticScreenId,
    val image: ImageId? = null,
    val resource: String? = null,
    val commandHelp: Boolean = false,
)

fun text(id: String): TextId = TextId(id)
fun textPattern(pattern: String): TextPattern = TextPattern(pattern)
fun item(slug: String): ItemId = ItemId.authored(slug)
fun itemPool(slug: String, items: List<ItemId> = emptyList()): ItemPoolId = ItemPoolId.authored(slug, items)
fun monster(slug: String): MonsterId = MonsterId(slug)
fun encounterTier(slug: String): EncounterTier = EncounterTier(slug)
fun worldMap(slug: String): WorldMapId = WorldMapId(slug)
fun mapLayer(slug: String): MapLayerId = MapLayerId(slug)
fun terrain(slug: String, sourceMapCode: Char? = null): TerrainId = TerrainId(slug, sourceMapCode)
fun terrainTag(slug: String): TerrainTagId = TerrainTagId(slug)
fun image(slug: String): ImageId = ImageId(slug)
fun staticScreen(slug: String): StaticScreenId = StaticScreenId(slug)
fun palette(slug: String): PaletteId = PaletteId(slug)
fun paletteColor(slug: String): PaletteColorId = PaletteColorId(slug)
fun layout(slug: String): LayoutId = LayoutId(slug)
fun screenRegion(slug: String): ScreenRegionId = ScreenRegionId(slug)
fun sceneId(slug: String): SceneId = SceneId(slug)
fun spellId(slug: String): SpellId = SpellId(slug)
fun missionId(slug: String): MissionId = MissionId(slug)
fun npcRole(slug: String): NpcRole = NpcRole(slug)
fun npcJob(slug: String): NpcJobId = NpcJobId(slug)
fun nativeOp(slug: String): NativeOpId = NativeOpId(slug)
fun stateId(slug: String): StateId = StateId(slug)
fun partyId(slug: String): PartyId = PartyId(slug)
fun npc(slug: String): NpcId = NpcId(slug)
fun location(slug: String): LocationId = LocationId(slug)
fun town(slug: String): TownId = TownId(slug)
fun building(slug: String): BuildingId = BuildingId(slug)
fun formula(slug: String): FormulaId = FormulaId(slug)
fun keyAction(slug: String): KeyActionId = KeyActionId(slug)
fun dungeon(slug: String): DungeonId = DungeonId(slug)
fun dungeonEvent(slug: String): DungeonEventId = DungeonEventId(slug)
fun milestone(slug: String): GameMilestoneId = GameMilestoneId(slug)
fun lootTable(slug: String): LootTableId = LootTableId(slug)
fun rewardBundle(slug: String): RewardBundleId = RewardBundleId(slug)
fun rumourTable(slug: String): RumourTableId = RumourTableId(slug)
fun rumour(slug: String): RumourId = RumourId(slug)

// Engine-native operation handles are part of the reusable runtime boundary,
// not game content.
class NativeOpRefs {
    val inventoryAdd: NativeOpId = nativeOp("inventory.add")
    val townEnterService: NativeOpId = nativeOp("town.enter_service")
    val inputKeyUpper: NativeOpId = nativeOp("input.key_upper")
    val partyMemberName: NativeOpId = nativeOp("party.member.name")
    val combatMonsterName: NativeOpId = nativeOp("combat.monster.name")
    val partyPronounSubject: NativeOpId = nativeOp("party.member.pronoun.subject")
    val partyPronounObject: NativeOpId = nativeOp("party.member.pronoun.object")
    val partyPronounPossessive: NativeOpId = nativeOp("party.member.pronoun.possessive")
    val townNpcName: NativeOpId = nativeOp("town.npc.name")
    val npcRoleName: NativeOpId = nativeOp("npc.role.name")
    val npcRoleLocation: NativeOpId = nativeOp("npc.role.location")
}
val NativeOps: NativeOpRefs = NativeOpRefs()


// =============================================================================
// 02. DSL kernel: recorded script AST
// =============================================================================

sealed interface IntAst {
    data class Lit(val value: Int) : IntAst
    data class Var(val name: String) : IntAst
    data class Binary(val left: IntAst, val op: String, val right: IntAst) : IntAst
    data class If(val cond: BoolAst, val thenValue: IntAst, val elseValue: IntAst) : IntAst
    data class Random(val lo: IntAst, val hi: IntAst) : IntAst
    data class MemberField(val member: IntAst, val field: String) : IntAst
    data class MonsterField(val monster: IntAst, val field: String) : IntAst
    data class BattleFlag(val name: String) : IntAst
    data class BattleQuery(val name: String) : IntAst
    data class RealmScalar(val name: String) : IntAst
    data class PartyScalar(val name: String) : IntAst
    data class InventoryPoolCount(val pool: ItemPoolId) : IntAst
    data class InventorySlotCost(val slot: IntAst) : IntAst
    data class NativeInt(val path: String, val args: List<String>) : IntAst
}

sealed interface BoolAst {
    data class Cmp(val left: IntAst, val op: String, val right: IntAst) : BoolAst
    data class Not(val expr: BoolAst) : BoolAst
    data class And(val left: BoolAst, val right: BoolAst) : BoolAst
    data class Or(val left: BoolAst, val right: BoolAst) : BoolAst
    data class FlagIs(val slug: String, val nativeValue: String) : BoolAst
    data class MilestoneAchieved(val milestone: GameMilestoneId) : BoolAst
    data class MemberFlag(val member: IntAst, val name: String) : BoolAst
    data class MonsterFlag(val monster: IntAst, val name: String) : BoolAst
    data class BattleFlag(val name: String) : BoolAst
    data class RuntimeFlag(val name: String) : BoolAst
}

sealed interface StrAst {
    data class NativeString(val path: String, val args: List<NativeStringArg>) : StrAst
}

sealed interface NativeStringArg {
    data class Literal(val value: String) : NativeStringArg
    data class IntValue(val value: IntAst) : NativeStringArg
}

sealed interface LValueAst {
    data class RealmScalar(val name: String) : LValueAst
    data class PartyScalar(val name: String) : LValueAst
    data class MemberField(val member: IntAst, val field: String) : LValueAst
    data class MonsterField(val monster: IntAst, val field: String) : LValueAst
    data class BattleFlag(val name: String) : LValueAst
}

enum class ScriptControlSignal { Continue, BreakNearestLoop, StopScript }
enum class MemberChoiceFilter { Any, AliveOnly, DeadOnly, SpellcasterOnly }
enum class InventorySlotChoiceFilter { Any, NonEmpty, Empty }

sealed interface ViewCommand {
    data object ClearText : ViewCommand
    data object ClearMapWindow : ViewCommand
    data object RenderMap : ViewCommand
    data object StatusLine : ViewCommand
    data class ShowImage(val image: ImageId) : ViewCommand
}

data class InputBindingModel(
    val primary: String,
    val aliases: List<String> = emptyList(),
    val label: String = primary,
) {
    val tokens: List<String> get() = listOf(primary) + aliases
    val primaryChar: Char
        get() = primary.singleOrNull()?.uppercaseChar()
            ?: error("input binding primary '$primary' is not a single character")
}

object InputAliases {
    const val ArrowUp: String = "\u001B[A"
    const val ArrowDown: String = "\u001B[B"
    const val ArrowRight: String = "\u001B[C"
    const val ArrowLeft: String = "\u001B[D"
}

fun inputKey(primary: Char, vararg aliases: String, label: String = primary.uppercaseChar().toString()): InputBindingModel =
    inputKey(primary.uppercaseChar().toString(), *aliases, label = label)

fun inputKey(primary: String, vararg aliases: String, label: String = primary): InputBindingModel =
    InputBindingModel(primary = primary, aliases = aliases.toList(), label = label)

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
    data class Screen(val text: TextBlock) : Op
    data class Emit(val text: TextId) : Op
    data class EmitIndexed(val pattern: TextPattern, val value: IntAst) : Op
    data class WaitInput(val reason: String? = null) : Op
    data class Pause(val millis: Int) : Op
    data class View(val command: ViewCommand) : Op
    data class LetInt(val name: String, val value: IntAst) : Op
    data class BindText(val name: String, val value: StrAst) : Op
    data class Set(val target: LValueAst, val value: IntAst) : Op
    data class Add(val target: LValueAst, val value: IntAst) : Op
    data class Sub(val target: LValueAst, val value: IntAst) : Op
    data class AddClamped(val target: LValueAst, val value: IntAst, val max: IntAst) : Op
    data class SetFlag(val slug: String, val nativeValue: String) : Op
    data class AppendJournal(val text: TextBlock) : Op
    data class If(val cond: BoolAst, val thenOps: List<Op>, val elseOps: List<Op>) : Op
    data class Loop(val body: List<Op>) : Op
    data class Break(
        val reason: String? = null,
        val signal: ScriptControlSignal = ScriptControlSignal.BreakNearestLoop,
    ) : Op
    data class Stop(
        val reason: String? = null,
        val signal: ScriptControlSignal = ScriptControlSignal.StopScript,
    ) : Op
    data class Switch(val expr: IntAst, val cases: List<SwitchCase>) : Op
    data class Menu(val prompt: TextBlock, val options: List<MenuOption>, val defaultOps: List<Op>) : Op
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
    data class AskMonster(val prompt: TextId, val varName: String) : Op
    data class ForEachMember(val varName: String, val includeDead: Boolean, val body: List<Op>) : Op
    data class ForEachMonster(val varName: String, val includeDead: Boolean, val body: List<Op>) : Op
    data class Speak(val building: BuildingId) : Op
    data class TellRumour(val table: RumourTableId) : Op
    data class RunScene(val scene: SceneId, val bindings: Map<String, String>) : Op
    data class Encounter(val foe: MonsterId, val mandatory: Boolean, val tier: EncounterTier? = null) : Op
    data class EnterDungeon(val dungeon: DungeonId, val levels: Int) : Op
    object ConsumeActiveItem : Op
    data class ClearInventorySlot(val slot: IntAst) : Op
    data class ConsumeInventoryPool(val pool: ItemPoolId, val quantity: IntAst) : Op
    object ConsumeTorch : Op
    data class TeleportParty(val directionKey: IntAst, val distance: IntAst) : Op
    data class DiscoverCave(val dungeon: DungeonId, val levels: IntAst, val moveCost: IntAst) : Op
    data class GrantReward(val reward: ScriptRewardModel) : Op
    data class RandomItemOffer(val offer: RandomItemOfferModel) : Op
    data class ShopService(val shop: ShopServiceModel) : Op
    data class AchieveMilestone(val milestone: GameMilestoneId) : Op
    data class NativeCall(val path: String, val args: List<String>) : Op
}

data class SwitchCase(val match: Int?, val body: List<Op>)
data class MenuOption(val binding: InputBindingModel, val body: List<Op>) {
    constructor(key: Char, body: List<Op>) : this(inputKey(key), body)

    val key: Char get() = binding.primaryChar
}

data class SceneModel(val id: SceneId, val ops: List<Op>)
data class DialogueModel(val npc: NpcId, val branches: List<DialogueBranchModel>)
data class DialogueBranchModel(val whenState: FlagState?, val ops: List<Op>)
data class SpellModel(val id: SpellId, val slot: Int, val baseCost: Int, val name: String, val ops: List<Op>)
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
) {
    fun displayName(dungeon: DungeonId): String =
        scenarios
            .firstOrNull { it.dungeon == dungeon }
            ?.name
            ?: dungeon.slug.replace('_', ' ')
}

sealed interface CharacterPlacementModel {
    data class Fixed(val position: WorldPosition) : CharacterPlacementModel
    object Randomized : CharacterPlacementModel
    object RandomizedWilderness : CharacterPlacementModel
}

enum class TownKind { Village, City }

data class TownServiceModel(
    val building: BuildingId,
)

data class TownModel(
    val id: TownId,
    val name: String,
    val position: GridPoint,
    val taxPercent: Int,
    val kind: TownKind,
    val services: List<TownServiceModel>,
)

data class TownMenuActionModel(
    val binding: InputBindingModel,
    val action: KeyActionId,
    val building: BuildingId?,
    val endsMenu: Boolean,
) {
    constructor(
        key: Char,
        action: KeyActionId,
        building: BuildingId?,
        endsMenu: Boolean,
    ) : this(inputKey(key), action, building, endsMenu)

    val key: Char get() = binding.primaryChar
}

data class BuildingServiceModel(
    val building: BuildingId,
    val action: KeyActionId,
    val scene: SceneId,
    val opensAt: Int?,
    val closesAt: Int?,
    val nativeServices: List<EngineServiceUse>,
)

data class TownSystemModel(
    val menu: List<TownMenuActionModel>,
    val buildingServices: List<BuildingServiceModel>,
    val leavePrompts: List<EquipmentCommand>,
    val nativeServices: List<EngineServiceUse>,
)

data class CharacterModel(
    val id: NpcId,
    val name: String,
    val job: NpcJobId,
    val gender: Gender,
    val placement: CharacterPlacementModel,
    val roles: List<NpcRole>,
)

data class FlagContentModel(
    val ref: FlagRef,
    val states: List<FlagState>,
)

data class MonsterModel(
    val id: MonsterId,
    val name: String,
    val count: IntRange,
    val countMaxLevelDivisor: Int?,
    val hitPoints: IntRange,
    val weaponSkill: Int,
    val spellPower: Int,
    val tier: EncounterTier?,
    val immune: Boolean,
)

data class MissionModel(
    val id: MissionId,
    val trigger: Trigger,
    val requires: BoolAst?,
    val action: SceneId,
)

enum class SourceSpecialField { Fireshield, Bracelets, Mastercrown, Jazzyfoot, Silverkey }

data class SourceSpecialMapping(
    val field: SourceSpecialField,
    val achievedValue: Int,
)

data class GameMilestoneModel(
    val id: GameMilestoneId,
    val specialInventoryItem: ItemId?,
    val requiredToWin: Boolean,
    val sourceSpecial: SourceSpecialMapping?,
)

data class WinConditionModel(
    val required: List<GameMilestoneId>,
    val completedBy: GameMilestoneId?,
)

data class GameProgressModel(
    val milestones: List<GameMilestoneModel>,
    val win: WinConditionModel?,
)

data class LevelingModel(
    val maxLevel: Int,
    val xpThresholds: List<Int>,
    val hitPointFloor: FormulaId,
    val classGrowth: List<ClassLevelUpModel>,
)

data class ClassLevelUpModel(
    val klass: PcClass,
    val weaponSkill: Int,
    val health: Int,
    val wisdom: Int,
    val charisma: Int,
)

enum class MandatoryRunRule { NoEscape }
enum class CombatActionKind { Attack, Defend, UseMagicItem, BattleCry, CastMageSpell }
enum class CombatActionRequirement { Always, Mage }
enum class EngineService {
    KeyboardInput,
    Rendering,
    RandomNumbers,
    CombatContextStorage,
    InventoryStorage,
    NpcStateStorage,
    TownStateStorage,
    TimeState,
    PartyStateMutation,
    TextFormatting,
}

data class EngineServiceUse(
    val service: EngineService,
    val reason: String,
)

data class EncounterOpeningModel(
    val fleeChancePercent: Int,
    val desperateFleeChancePercent: Int,
    val mandatoryRun: MandatoryRunRule,
    val banners: List<ImageId>,
)

data class CombatActionModel(
    val binding: InputBindingModel,
    val action: KeyActionId,
    val kind: CombatActionKind,
    val requirement: CombatActionRequirement,
    val prompt: TextBlock,
) {
    constructor(
        key: Char,
        action: KeyActionId,
        kind: CombatActionKind,
        requirement: CombatActionRequirement,
        prompt: TextBlock,
    ) : this(inputKey(key), action, kind, requirement, prompt)

    val key: Char get() = binding.primaryChar
}

data class BattleOpeningKeysModel(
    val fight: InputBindingModel = inputKey('F'),
    val run: InputBindingModel = inputKey('R'),
)

data class YesNoKeysModel(
    val yes: InputBindingModel = inputKey('Y'),
    val no: InputBindingModel = inputKey('N'),
)

val StandardYesNoKeys: YesNoKeysModel = YesNoKeysModel()

data class BattleTextModel(
    val encounterBanner: TextBlock = inlineText("ENCOUNTER!", ""),
    val youFace: TextBlock = inlineText("You face"),
    val stillFace: TextBlock = inlineText("You still face"),
    val fight: TextBlock = inlineText("(F)ight"),
    val run: TextBlock = inlineText("(R)un"),
    val bloodFlow: TextBlock = inlineText("Blood shall flow!"),
    val whatNow: TextBlock = inlineText("What now?", ""),
    val targetPrompt: TextBlock = inlineText("Who?", ""),
    val useWhat: TextBlock = inlineText("Use what?", ""),
    val spellAbort: TextBlock = inlineText("7. Abort"),
    val runCoward: TextBlock = inlineText("Coward. You won't get", "any EXP for this battle."),
    val runDesperateFail: TextBlock = inlineText("You desperately try to", "get away, but fail!"),
    val runFailAway: TextBlock = inlineText("You fail to get away!"),
    val runEscaped: TextBlock = inlineText("You escaped..."),
    val notPossible: TextBlock = inlineText("Not possible."),
    val magicImmune: TextBlock = inlineText("The magic item doesn't seem", "to work on this enemy!!!"),
    val spellFizzle: TextBlock = inlineText("The spell fizzles!", "this creature is immune", "to ordinary magic!!!"),
    val notEnoughMagic: TextBlock = inlineText("Not enough magic!"),
    val restGainHits: TextBlock = inlineText("You gain some hits pts", "as you rest this round."),
    val defendStance: TextBlock = inlineText("You put up a defensive", "stance..."),
    val battleCry: TextBlock = inlineText(
        "HI-YAAAA!",
        "",
        "(You manage to scare your",
        " enemies enough to get",
        " them off guard.)",
    ),
    val killed: TextBlock = inlineText("...KILLED!"),
    val partyHeld: TextBlock = inlineText("PARTY IS HELD!"),
    val victoryBested: TextBlock = inlineText("Thou hast bested", "thy foes.", ""),
    val victoryEachSurvivor: TextBlock = inlineText("Each survivor gets"),
    val victoryFoundObject: TextBlock = inlineText("You find a"),
)

data class PlayerAttackRulesModel(
    val hitBonus: Int,
    val damageSpreadMax: Int,
    val confusionDivisor: Int,
    val hpDefenseDivisor: Int,
)

data class DefendRulesModel(
    val weaponSkillPenalty: Int,
    val restThreshold: Int,
    val cap: Int,
)

data class BattleCryRulesModel(
    val weaponSkillPenalty: Int,
)

data class PlayerRoundModel(
    val attack: PlayerAttackRulesModel,
    val defend: DefendRulesModel,
    val battleCry: BattleCryRulesModel,
)

data class MonsterMeleeRulesModel(
    val hitBonus: Int,
    val damageDivisor: Int,
    val damageSpreadMax: Int,
    val damageMin: Int,
    val fallback: SceneId,
)

data class MonsterRoundModel(
    val spellChanceBasePercent: Int,
    val spellPowerDivisor: Int,
    val melee: MonsterMeleeRulesModel,
)

data class LootTableModel(
    val id: LootTableId,
    val dropChancePercent: Int,
    val treasureChancePercent: Int,
    val treasure: ItemPoolId,
    val magic: ItemPoolId,
)

enum class RewardItemPlacement { FirstFreeInventorySlot }
enum class RewardOverflowPolicy { SkipIfInventoryFull, PromptDropThenPlace }
enum class RewardSpecialItemPolicy { ProjectMilestoneState }
data class RewardApplicationModel(
    val regularItems: RewardItemPlacement,
    val overflow: RewardOverflowPolicy,
    val specialItems: RewardSpecialItemPolicy,
)

data class RewardMessageModel(
    val cantCarry: TextBlock? = null,
    val pickedUp: TextBlock? = null,
)

data class ScriptRewardModel(
    val gold: IntAst?,
    val items: List<ItemId>,
    val application: RewardApplicationModel,
    val messages: RewardMessageModel,
)

data class RandomItemOfferModel(
    val pool: ItemPoolId,
    val offer: TextBlock,
    val choicePrompt: TextBlock,
    val broke: TextBlock,
    val deal: TextBlock,
    val declined: TextBlock,
    val application: RewardApplicationModel,
    val messages: RewardMessageModel,
    val buy: InputBindingModel,
    val decline: InputBindingModel,
    val priceOverride: Int?,
    val brokePauseMillis: Int,
    val fullPauseMillis: Int,
    val dealPauseMillis: Int,
    val declinedPauseMillis: Int,
) {
    val buyKey: Char get() = buy.primaryChar
    val declineKey: Char get() = decline.primaryChar
}

enum class ShopZeroQuantityDealPolicy { Always, NonZeroOnly }

data class ShopBargainBonusModel(
    val rollGreaterThan: Int,
    val addCurrentDivisor: Int,
) {
    init {
        require(rollGreaterThan in 0..100) { "shop bargain bonus threshold must be 0..100" }
        require(addCurrentDivisor > 0) { "shop bargain bonus divisor must be positive" }
    }
}

data class ShopSellBargainModel(
    val bonuses: List<ShopBargainBonusModel>,
    val rejectRollBelow: Int?,
) {
    init {
        rejectRollBelow?.let { threshold ->
            require(threshold in 0..101) { "shop bargain rejection threshold must be 0..101" }
        }
    }
}

sealed interface ShopQuantityCap {
    val resource: Resource

    data class FixedAmount(override val resource: Resource, val amount: Int) : ShopQuantityCap
    data class CarryCapacity(override val resource: Resource) : ShopQuantityCap
}

object ShopResources {
    val torches: Resource = Resource.Torches
    val food: Resource = Resource.Food
}

object ShopQuantityCaps {
    fun fixed(resource: Resource, amount: Int): ShopQuantityCap =
        ShopQuantityCap.FixedAmount(resource, amount)

    fun carryCapacity(resource: Resource): ShopQuantityCap =
        ShopQuantityCap.CarryCapacity(resource)
}

object ShopZeroQuantityDeal {
    val always: ShopZeroQuantityDealPolicy = ShopZeroQuantityDealPolicy.Always
    val nonZeroOnly: ShopZeroQuantityDealPolicy = ShopZeroQuantityDealPolicy.NonZeroOnly
}

object HuntText {
    val memberName: TextPart = TextPart.HuntMemberName
    val memberSubjectPronoun: TextPart = TextPart.HuntMemberSubjectPronoun
    val foodGained: TextPart = TextPart.HuntFoodGained
    val partyMemberMax: TextPart = TextPart.PartyMemberMax
}

sealed interface ShopPriceModel {
    object ItemCost : ShopPriceModel
    object HalfItemCost : ShopPriceModel
    data class Fixed(val amount: Int) : ShopPriceModel
    data class LeaderLevelPlus(val addend: Int) : ShopPriceModel
    data class ItemCostPlusLeaderLevelTimes(val multiplier: Int) : ShopPriceModel
}

class ShopPriceRefs {
    val itemCost: ShopPriceModel = ShopPriceModel.ItemCost
    val halfItemCost: ShopPriceModel = ShopPriceModel.HalfItemCost
    fun fixed(amount: Int): ShopPriceModel = ShopPriceModel.Fixed(amount)
    fun leaderLevelPlus(addend: Int): ShopPriceModel = ShopPriceModel.LeaderLevelPlus(addend)
    fun itemCostPlusLeaderLevelTimes(multiplier: Int): ShopPriceModel =
        ShopPriceModel.ItemCostPlusLeaderLevelTimes(multiplier)
}
val ShopPrices: ShopPriceRefs = ShopPriceRefs()

sealed interface ShopPurchaseModel {
    object InventoryItem : ShopPurchaseModel
    data class Bulk(
        val resource: Resource,
        val quantityPrompt: TextBlock,
        val cap: ShopQuantityCap,
        val tooMany: TextBlock,
        val zeroQuantityDeal: ShopZeroQuantityDealPolicy,
    ) : ShopPurchaseModel
}

data class ShopItemOfferModel(
    val binding: InputBindingModel,
    val item: ItemId,
    val label: String?,
    val description: TextBlock?,
    val price: ShopPriceModel,
    val purchase: ShopPurchaseModel,
) {
    constructor(
        key: Char,
        item: ItemId,
        label: String?,
        description: TextBlock?,
        price: ShopPriceModel,
        purchase: ShopPurchaseModel,
    ) : this(inputKey(key), item, label, description, price, purchase)

    val key: Char get() = binding.primaryChar
}

data class ShopCatalogPageModel(
    val entries: List<ShopItemOfferModel>,
    val footer: TextBlock?,
)

data class ShopCatalogModel(
    val binding: InputBindingModel,
    val title: String,
    val intro: TextBlock?,
    val pages: List<ShopCatalogPageModel>,
) {
    constructor(
        key: Char,
        title: String,
        intro: TextBlock?,
        pages: List<ShopCatalogPageModel>,
    ) : this(inputKey(key), title, intro, pages)

    val key: Char get() = binding.primaryChar
}

data class ShopBuyModel(
    val prompt: TextBlock,
    val catalogs: List<ShopCatalogModel>,
    val tooExpensive: TextBlock,
    val cantCarry: TextBlock,
    val deal: TextBlock,
    val unitPrice: TextBlock?,
    val singlePrice: TextBlock?,
    val payPrompt: TextBlock,
    val paymentKeys: ShopPurchaseKeysModel,
    val inventoryApplication: RewardApplicationModel,
)

data class ShopSellModel(
    val prompt: TextBlock,
    val acceptedKinds: Set<ItemKind>,
    val acceptedItems: Set<ItemId>,
    val rejectedItems: Set<ItemId>,
    val wrongKind: TextBlock,
    val offer: TextBlock,
    val sold: TextBlock?,
    val price: ShopPriceModel,
    val bargain: ShopSellBargainModel?,
    val saleKeys: ShopSaleKeysModel,
)

data class ShopInterludeModel(
    val chancePercent: Int,
    val offer: TextBlock,
    val accept: InputBindingModel,
    val decline: InputBindingModel,
    val accepted: List<TextBlock>,
    val declined: TextBlock,
    val runsRumourAfterAccept: Boolean,
) {
    val acceptKey: Char get() = accept.primaryChar
    val declineKey: Char get() = decline.primaryChar
}

data class ShopMenuKeysModel(
    val buy: InputBindingModel = inputKey('B'),
    val sell: InputBindingModel = inputKey('S'),
    val leave: InputBindingModel = inputKey('L'),
)

data class ShopPurchaseKeysModel(
    val pay: InputBindingModel = inputKey('P'),
    val decline: InputBindingModel = inputKey('N'),
)

data class ShopSaleKeysModel(
    val accept: InputBindingModel = inputKey('Y'),
    val decline: InputBindingModel = inputKey('N'),
)

data class ShopServiceModel(
    val building: BuildingId,
    val image: ImageId,
    val closed: TextBlock,
    val intro: TextBlock,
    val menu: TextBlock,
    val keys: ShopMenuKeysModel,
    val goodbye: TextBlock,
    val buy: ShopBuyModel?,
    val sell: ShopSellModel?,
    val interlude: ShopInterludeModel?,
)

data class RewardBundleModel(
    val id: RewardBundleId,
    val experience: FormulaId,
    val gold: FormulaId,
    val application: RewardApplicationModel,
    val loot: LootTableModel,
)

data class FormulaModel(
    val id: FormulaId,
    val value: IntAst,
)

enum class ItemUseContext { Combat, Inventory }
enum class ItemConsumePolicy { BeforeUse, ScriptControlled, NeverConsumes }

data class StandardBattleModel(
    val opening: EncounterOpeningModel,
    val openingKeys: BattleOpeningKeysModel,
    val actions: List<CombatActionModel>,
    val playerRound: PlayerRoundModel,
    val monsterRound: MonsterRoundModel,
    val text: BattleTextModel,
    val rewards: RewardBundleId,
    val encounterFoeCountMax: Int,
    val nativeServices: List<EngineServiceUse>,
)

data class CombatSystemModel(
    val standardBattle: StandardBattleModel?,
)

enum class ConversationNpcSelection { BuildingListOrWildernessLocation }
enum class NpcVisitPolicy { PerPartyVisitedBit }
enum class DialogueBranchSelection { FirstMatchingStateThenDefault }
enum class MissionHintPolicy { BeforeEachMenu }
enum class ConversationTopic { Chitchat, Question, Rumour, Bye }
enum class QuestionTopic { Lodging, Magic, People, Nothing }
enum class ChitchatSubject { Well, Weather, Health }
enum class ChitchatReplyPolicy { BySubjectAndNpcJob }
enum class RumourUsagePolicy { OncePerConversationThenFallback }
enum class RumourSourcePolicy { StandardTownRumours }
enum class ChitchatPromptAudience { Any, Female, Male }

data class ConversationMenuActionModel(
    val topic: ConversationTopic,
    val binding: InputBindingModel,
    val action: KeyActionId,
    val label: String = topic.defaultLabel(),
) {
    constructor(topic: ConversationTopic, key: Char, action: KeyActionId) :
        this(topic, inputKey(key), action)

    val key: Char get() = binding.primaryChar
}

data class QuestionMenuActionModel(
    val topic: QuestionTopic,
    val binding: InputBindingModel,
    val action: KeyActionId,
    val label: String = topic.defaultLabel(),
) {
    constructor(topic: QuestionTopic, key: Char, action: KeyActionId) :
        this(topic, inputKey(key), action)

    val key: Char get() = binding.primaryChar
}

data class ConversationMenuModel(
    val actions: List<ConversationMenuActionModel>,
    val questions: List<QuestionMenuActionModel>,
)

data class ChitchatModel(
    val subjects: List<ChitchatSubject>,
    val replies: ChitchatReplyPolicy,
)

data class ConversationRumourModel(
    val usage: RumourUsagePolicy,
    val source: RumourSourcePolicy,
)

data class ConversationPromptTextModel(
    val chooseNpc: TextBlock = inlineText("Speak with whom?", ""),
    val mainMenu: TextBlock = structuredText {
        line("Chatting with")
        line(TextPart.ConversationNpcName, ":")
        line("")
    },
) {
    fun renderMainMenu(npcName: String): TextBlock =
        mainMenu.resolvePromptValues(PromptTextValues(conversationNpcName = npcName))
}

data class ReusableScreenTextModel(
    val id: TextId,
    val text: TextBlock,
)

data class ChitchatPromptVariantModel(
    val audience: ChitchatPromptAudience,
    val text: TextBlock,
)

data class ChitchatPromptTextModel(
    val subject: ChitchatSubject,
    val variants: List<ChitchatPromptVariantModel>,
)

data class JobTextModel(
    val job: NpcJobId,
    val text: TextBlock,
)

data class ChitchatReplyTextModel(
    val subject: ChitchatSubject,
    val replies: List<JobTextModel>,
)

data class QuestionReplyTextModel(
    val topic: QuestionTopic,
    val replies: List<JobTextModel>,
)

data class RumourEntryModel(
    val id: RumourId?,
    val text: TextBlock,
    val excludedBy: List<FlagState>,
)

data class RumourTextTableModel(
    val id: RumourTableId,
    val entries: List<RumourEntryModel>,
)

data class QuestionActionScriptModel(
    val topic: QuestionTopic,
    val job: NpcJobId,
    val ops: List<Op>,
)

data class SpeakTextTablesModel(
    val screens: List<ReusableScreenTextModel> = emptyList(),
    val chitchatPrompts: List<ChitchatPromptTextModel> = emptyList(),
    val chitchatReplies: List<ChitchatReplyTextModel> = emptyList(),
    val questionReplies: List<QuestionReplyTextModel> = emptyList(),
    val rumourTables: List<RumourTextTableModel> = emptyList(),
)

data class StandardConversationModel(
    val npcSelection: ConversationNpcSelection,
    val visitPolicy: NpcVisitPolicy,
    val branchSelection: DialogueBranchSelection,
    val missionHints: MissionHintPolicy,
    val menu: ConversationMenuModel,
    val chitchat: ChitchatModel,
    val rumour: ConversationRumourModel,
    val prompts: ConversationPromptTextModel,
    val nativeServices: List<EngineServiceUse>,
)

data class SpeakSystemModel(
    val standardConversation: StandardConversationModel?,
    val textTables: SpeakTextTablesModel = SpeakTextTablesModel(),
    val questionActions: List<QuestionActionScriptModel> = emptyList(),
)

/**
 * Bundled view of [GameModel] content the reusable sessions actually consume:
 * combat tables, monster catalog, formulas, rewards, leveling rules, spells,
 * item-use scripts, item records, realm-level balance, dungeon/wilderness
 * subsystem config, town list, town menu, scenes, missions, characters.
 *
 * Sessions and runtimes accept this as a single parameter so adding a new
 * content kind is one builder field plus one read site, not a parameter
 * threaded through twelve constructors.
 */
data class RuntimeContent(
    val combat: CombatSystemModel = CombatSystemModel(null),
    val monsters: List<MonsterModel> = emptyList(),
    val formulas: List<FormulaModel> = emptyList(),
    val rewards: List<RewardBundleModel> = emptyList(),
    val leveling: LevelingModel? = null,
    val spells: List<SpellModel> = emptyList(),
    val itemUses: List<ItemUseModel> = emptyList(),
    val itemRecords: List<ItemRecordModel> = emptyList(),
    val realm: RealmContentModel = RealmContentModel(),
    val prompts: GamePromptsModel = GamePromptsModel(),
    val progress: GameProgressModel = GameProgressModel(emptyList(), null),
    val dungeons: DungeonSystemModel = DungeonSystemModel(),
    val ydarg: YdargContentModel? = null,
    val wilderness: WildernessSystemModel = WildernessSystemModel(),
    val towns: List<TownModel> = emptyList(),
    val townSystem: TownSystemModel? = null,
    val scenes: List<SceneModel> = emptyList(),
    val missions: List<MissionModel> = emptyList(),
    val characters: List<CharacterModel> = emptyList(),
    val speak: SpeakSystemModel = SpeakSystemModel(null),
    val dialogue: List<DialogueModel> = emptyList(),
) {
    val battleConfigured: Boolean get() = combat.standardBattle != null
}

data class GameModel(
    val itemCatalog: List<ItemRecordModel>,
    val itemPools: List<ItemPoolId>,
    val realm: RealmContentModel = RealmContentModel(),
    val prompts: GamePromptsModel,
    val visuals: VisualsModel,
    val worldMaps: List<WorldMapModel>,
    val terminal: TerminalPresentationModel,
    val flags: List<FlagRef>,
    val authoredFlags: List<FlagContentModel>,
    val progress: GameProgressModel,
    val leveling: LevelingModel?,
    val formulas: List<FormulaModel>,
    val rewards: List<RewardBundleModel>,
    val combat: CombatSystemModel,
    val wilderness: WildernessSystemModel,
    val dungeons: DungeonSystemModel,
    val ydarg: YdargContentModel?,
    val speak: SpeakSystemModel,
    val towns: List<TownModel>,
    val townSystem: TownSystemModel?,
    val characters: List<CharacterModel>,
    val monsters: List<MonsterModel>,
    val scenes: List<SceneModel>,
    val dialogue: List<DialogueModel>,
    val spells: List<SpellModel>,
    val itemUses: List<ItemUseModel>,
    val missions: List<MissionModel>,
)

fun GameModel.itemPool(pool: ItemPoolId): ItemPoolId =
    itemPools.firstOrNull { it == pool } ?: pool

fun GameModel.runtimeContent(): RuntimeContent =
    RuntimeContent(
        combat = combat,
        monsters = monsters,
        formulas = formulas,
        rewards = rewards,
        leveling = leveling,
        spells = spells,
        itemUses = itemUses,
        itemRecords = itemCatalog,
        realm = realm,
        prompts = prompts,
        progress = progress,
        dungeons = dungeons,
        ydarg = ydarg,
        wilderness = wilderness,
        towns = towns,
        townSystem = townSystem,
        scenes = scenes,
        missions = missions,
        characters = characters,
        speak = speak,
        dialogue = dialogue,
    )

sealed interface Trigger {
    data class RandomStep(val percent: Int) : Trigger
    data class AtPosition(val point: GridPoint) : Trigger
    data class AtLocation(val location: LocationId) : Trigger
    data class OnTerrain(val terrain: TerrainId) : Trigger
}

data class WildernessSystemModel(
    val keys: WildernessKeysModel = WildernessKeysModel(),
    val events: WildernessEventModel = WildernessEventModel(),
    val terrainTransitions: TerrainTransitionModel = TerrainTransitionModel(),
)

data class WildernessKeysModel(
    val moveNorth: InputBindingModel = inputKey("8", InputAliases.ArrowUp, "UP", "ARROW_UP", label = "8/Up"),
    val moveSouth: InputBindingModel = inputKey("2", InputAliases.ArrowDown, "DOWN", "ARROW_DOWN", label = "2/Down"),
    val moveWest: InputBindingModel = inputKey("4", InputAliases.ArrowLeft, "LEFT", "ARROW_LEFT", label = "4/Left"),
    val moveEast: InputBindingModel = inputKey("6", InputAliases.ArrowRight, "RIGHT", "ARROW_RIGHT", label = "6/Right"),
    val pass: InputBindingModel = inputKey(" ", "SPACE", label = "Space"),
    val save: InputBindingModel = inputKey("S"),
    val readyWeapon: InputBindingModel = inputKey("W"),
    val readyArmor: InputBindingModel = inputKey("R"),
    val redraw: InputBindingModel = inputKey("!"),
    val journal: InputBindingModel = inputKey("J"),
    val help: InputBindingModel = inputKey("?"),
    val hunt: InputBindingModel = inputKey("H"),
    val viewParty: InputBindingModel = inputKey("V"),
    val removeArmor: InputBindingModel = inputKey("T"),
    val removeWeapon: InputBindingModel = inputKey("P"),
    val enter: InputBindingModel = inputKey("E"),
    val drop: InputBindingModel = inputKey("L"),
    val camp: InputBindingModel = inputKey("C"),
    val castSpell: InputBindingModel = inputKey("Z"),
    val quaff: InputBindingModel = inputKey("Q"),
    val quit: InputBindingModel = inputKey("X"),
    val caveEnter: InputBindingModel = inputKey("E"),
    val caveLeave: InputBindingModel = inputKey("L"),
    val outOfMovesSave: InputBindingModel = inputKey("S"),
    val outOfMovesQuit: InputBindingModel = inputKey("Q"),
    val quitYes: InputBindingModel = inputKey("Y"),
    val quitNo: InputBindingModel = inputKey("N"),
    val commandHint: TextBlock = inlineText("Press ? for commands."),
    val helpPages: List<TextBlock> = emptyList(),
)

fun WildernessKeysModel.exploreKeys(): List<InputBindingModel> =
    listOf(
        moveNorth,
        moveSouth,
        moveWest,
        moveEast,
        pass,
        save,
        readyWeapon,
        readyArmor,
        redraw,
        journal,
        help,
        hunt,
        viewParty,
        removeArmor,
        removeWeapon,
        enter,
        drop,
        camp,
        castSpell,
        quaff,
        quit,
    )

fun WildernessKeysModel.caveKeys(): List<InputBindingModel> =
    listOf(caveEnter, caveLeave)

fun WildernessKeysModel.outOfMovesKeys(): List<InputBindingModel> =
    listOf(outOfMovesSave, outOfMovesQuit)

fun WildernessKeysModel.quitConfirmKeys(): List<InputBindingModel> =
    listOf(quitYes, quitNo)

data class DungeonKeysModel(
    val descend: InputBindingModel = inputKey("D"),
    val ascend: InputBindingModel = inputKey("A"),
    val help: InputBindingModel = inputKey("?"),
    val drop: InputBindingModel = inputKey("L"),
    val removeArmor: InputBindingModel = inputKey("T"),
    val removeWeapon: InputBindingModel = inputKey("P"),
    val readyWeapon: InputBindingModel = inputKey("W"),
    val readyArmor: InputBindingModel = inputKey("R"),
    val quaff: InputBindingModel = inputKey("Q"),
    val castSpell: InputBindingModel = inputKey("Z"),
    val viewParty: InputBindingModel = inputKey("V"),
    val save: InputBindingModel = inputKey("S"),
    val commandHint: TextBlock = inlineText("Press ? for commands."),
    val helpPages: List<TextBlock> = emptyList(),
)

data class GamePromptsModel(
    val pauseVerbs: List<String> = emptyList(),
    val returnKeyName: String = "RETURN key",
    val priest: PriestSpellPromptsModel = PriestSpellPromptsModel(),
    val inventoryUse: InventoryUsePromptsModel = InventoryUsePromptsModel(),
    val equipment: EquipmentPromptsModel = EquipmentPromptsModel(),
    val save: SavePromptsModel = SavePromptsModel(),
    val town: TownPromptsModel = TownPromptsModel(),
    val wilderness: WildernessPromptsModel = WildernessPromptsModel(),
    val dungeon: DungeonPromptsModel = DungeonPromptsModel(),
    val party: PartyPromptsModel = PartyPromptsModel(),
)

data class SavePromptsModel(
    val saving: TextBlock = missingPrompt("save.saving"),
    val done: TextBlock = missingPrompt("save.done"),
    val failed: TextBlock = missingPrompt("save.failed"),
)

data class PriestSpellPromptsModel(
    val who: TextBlock = structuredText { line("Who? (1-", HuntText.partyMemberMax, ")") },
    val mageCombatOnly: TextBlock = inlineText("Sorry. Mage spells are", "combat-only."),
    val wrongClass: TextBlock = inlineText("Wrong class."),
    val dead: TextBlock = inlineText("Dead."),
    val invalidCaster: TextBlock = inlineText("Not a party member."),
    val invalidSpell: TextBlock = inlineText("Not possible."),
    val noSpells: TextBlock = inlineText("No priest spells are", "declared."),
) {
    fun spellList(member: PartyMemberState, spells: List<SpellModel>, abortSlot: Int): TextBlock =
        inlineText(
            *(spells.map { spell ->
                val cost = spell.baseCost + member.level
                "${spell.slot}. ${spell.name.padEnd(12)}(${cost.toString().padStart(3)}/${member.mana.current.toString().padStart(3)})"
            } + "$abortSlot. Abort").toTypedArray(),
        )
}

data class InventoryUsePromptsModel(
    val use: TextBlock = inlineText("Use what?"),
    val quaff: TextBlock = inlineText("Quaff what?"),
    val noItems: TextBlock = inlineText("You are not carrying", "anything."),
    val noItem: TextBlock = inlineText("There is nothing", "in that slot."),
    val invalidSlotFirstPrefix: String = "Inventory slot ",
    val invalidSlotFirstSuffix: String = "",
    val invalidSlotSecond: String = "does not exist.",
    val notUsableFirst: String = "Nothing happens",
    val notUsableSecondPrefix: String = "with ",
    val notUsableSecondSuffix: String = ".",
) {
    fun choose(prompt: TextBlock, choiceLines: List<String>): TextBlock =
        combineTextBlocks(prompt, TextBlock.Inline(choiceLines))

    fun invalidSlot(slot: Int): TextBlock =
        inlineText("$invalidSlotFirstPrefix$slot$invalidSlotFirstSuffix", invalidSlotSecond)

    fun notUsable(itemName: String): TextBlock =
        inlineText(notUsableFirst, "$notUsableSecondPrefix$itemName$notUsableSecondSuffix")
}

data class EquipmentPromptsModel(
    val inventory: TextBlock = inlineText("Ready what?"),
    val who: TextBlock = inlineText("Who? (1-N)"),
    val readyWeapons: TextBlock = inlineText("Do you want to ready", "some weapons before", "leaving?", ""),
    val readyArmors: TextBlock = inlineText("Do you want to ready", "some armors before", "leaving?", ""),
)

data class TownPromptsModel(
    val unavailableSuffix: String = " is not available here.",
    val closedSuffix: String = " is closed.",
) {
    fun unavailable(building: BuildingId): TextBlock =
        inlineText("${building.slug}$unavailableSuffix")

    fun closed(building: BuildingId): TextBlock =
        inlineText("${building.slug}$closedSuffix")
}

data class PartyPromptsModel(
    val viewWho: TextBlock = structuredText { line("Who? (1-", HuntText.partyMemberMax, "/Q/A)") },
    val journalHeader: TextBlock = missingPrompt("party.journal_header"),
    val dropPrompt: TextBlock = inlineText("Drop what?"),
    val dropEmptySlot: TextBlock = inlineText("Nothing to drop."),
    val dropAborted: TextBlock = inlineText("Aborted."),
    val dropDropped: TextBlock = structuredText {
        line("You drop the")
        line(TextPart.SelectedItemName, ".")
    },
    val dropCapacityReduced: TextBlock = inlineText("You are forced to", "leave some of your", "food behind..."),
) {
    fun renderDropped(itemName: String): TextBlock =
        dropDropped.resolvePromptValues(PromptTextValues(selectedItemName = itemName))
}

data class WildernessPromptsModel(
    val pass: TextBlock = missingPrompt("wilderness.pass"),
    val noMoves: TextBlock = missingPrompt("wilderness.no_moves"),
    val outOfMoves: TextBlock = missingPrompt("wilderness.out_of_moves"),
    val notInWilderness: TextBlock = missingPrompt("wilderness.not_in_wilderness"),
    val nothingToEnter: TextBlock = missingPrompt("wilderness.nothing_to_enter"),
    val quitConfirm: TextBlock = missingPrompt("wilderness.quit_confirm"),
    val dungeonHandoff: TextBlock = missingPrompt("wilderness.dungeon_handoff"),
) {
    fun renderDungeonHandoff(dungeonName: String, levels: Int): TextBlock =
        dungeonHandoff.resolvePromptValues(
            PromptTextValues(
                wildernessDungeonName = dungeonName,
                wildernessDungeonLevels = levels,
            ),
        )
}

data class DungeonPromptsModel(
    val levelMenu: TextBlock = missingPrompt("dungeon.level_menu"),
    val enterLevel: TextBlock = missingPrompt("dungeon.enter_level"),
    val atMaxDepth: TextBlock = missingPrompt("dungeon.at_max_depth"),
    val noTorches: TextBlock = missingPrompt("dungeon.no_torches"),
    val leaveCavern: TextBlock = missingPrompt("dungeon.leave_cavern"),
) {
    fun renderLevelMenu(depth: Int): TextBlock =
        levelMenu.resolvePromptValues(PromptTextValues(dungeonDepth = depth))

    fun renderEnterLevel(depth: Int): TextBlock =
        enterLevel.resolvePromptValues(PromptTextValues(dungeonDepth = depth))
}

private fun missingPrompt(name: String): TextBlock =
    inlineText("[missing prompt: $name]")

private data class PromptTextValues(
    val wildernessDungeonName: String? = null,
    val wildernessDungeonLevels: Int? = null,
    val dungeonDepth: Int? = null,
    val selectedItemName: String? = null,
    val conversationNpcName: String? = null,
)

private fun TextBlock.resolvePromptValues(values: PromptTextValues): TextBlock =
    when (this) {
        is TextBlock.Inline,
        is TextBlock.Ref -> this
        is TextBlock.Structured -> TextBlock.Structured(
            lines.map { line ->
                TextLine(line.parts.map { part -> part.resolvePromptValue(values) })
            },
        )
    }

private fun TextPart.resolvePromptValue(values: PromptTextValues): TextPart =
    when (this) {
        TextPart.WildernessDungeonName -> TextPart.Literal(values.wildernessDungeonName ?: fallbackText())
        TextPart.WildernessDungeonLevels -> TextPart.Literal(values.wildernessDungeonLevels?.toString() ?: fallbackText())
        TextPart.DungeonDepth -> TextPart.Literal(values.dungeonDepth?.toString() ?: fallbackText())
        TextPart.SelectedItemName -> TextPart.Literal(values.selectedItemName ?: fallbackText())
        TextPart.ConversationNpcName -> TextPart.Literal(values.conversationNpcName ?: fallbackText())
        else -> this
    }

private fun ConversationTopic.defaultLabel(): String =
    when (this) {
        ConversationTopic.Chitchat -> "Chitchat"
        ConversationTopic.Question -> "Question"
        ConversationTopic.Rumour -> "Rumors"
        ConversationTopic.Bye -> "Bye"
    }

private fun QuestionTopic.defaultLabel(): String =
    when (this) {
        QuestionTopic.Lodging -> "Lodging"
        QuestionTopic.Magic -> "Magic"
        QuestionTopic.People -> "People"
        QuestionTopic.Nothing -> "Nothing"
    }


data class WildernessEventModel(
    val caveChancePercent: Int = 3,
    val quietChancePercent: Int = 91,
    val encounterSplitPercent: Int = 50,
    val easyEncounterTier: EncounterTier = DslDefaultEncounterTiers.weak,
    val hardEncounterTier: EncounterTier = DslDefaultEncounterTiers.wild,
    val caveDungeon: DungeonId = DslDefaultDungeons.randomCave,
    val caveDepthRange: IntRange = 3..16,
    val caveEntryCost: Int = 10,
    val caveImage: ImageId? = null,
    val caveFoundPrompt: TextBlock = inlineText("You find a cave!", "", "(E)nter", "(L)eave"),
    val caveNoMoves: TextBlock = inlineText("You don't have enough", "moves left to explore", "the cave...", ""),
)

data class TerrainTransitionModel(
    val chancePercent: Int = 9,
    val entries: List<TerrainTransitionTextModel> = emptyList(),
)

data class TerrainTransitionTextModel(
    val terrain: TerrainId,
    val text: TextBlock,
)

// =============================================================================
// 03. DSL kernel: exact state model
// =============================================================================

enum class Gender { Male, Female }
enum class Race { Human, Dwarf, Elf, Halfling }
enum class PcClass { Fighter, Thief, Mage, Priest, Bard, Ranger }
val PcClass.sourceId: Int
    get() = when (this) {
        PcClass.Fighter -> 1
        PcClass.Thief -> 2
        PcClass.Mage -> 3
        PcClass.Priest -> 4
        PcClass.Bard -> 5
        PcClass.Ranger -> 6
    }
enum class RuntimeMode { TitleMenu, Wilderness, Town, Dungeon, Battle, YdargCastle, GameOver }
enum class Facing { North, East, South, West }
enum class StateKind { AuthoredFixture, RuntimeSnapshot }

const val InventorySaveSlotMax: Int = 16

data class HitPoints(val current: Int, val max: Int)
infix fun Int.of(max: Int): HitPoints = HitPoints(this, max)
data class Mana(val current: Int, val max: Int)
infix fun Int.manaOf(max: Int): Mana = Mana(this, max)

data class GridPoint(val x: Int, val y: Int)
fun xy(x: Int, y: Int): GridPoint = GridPoint(x, y)

sealed interface WorldPosition {
    data class Wilderness(val point: GridPoint) : WorldPosition
    data class Town(val town: TownId, val building: BuildingId) : WorldPosition
}

fun townPosition(town: TownId, building: BuildingId): WorldPosition =
    WorldPosition.Town(town, building)

object Wilderness {
    fun xy(x: Int, y: Int): WorldPosition = WorldPosition.Wilderness(GridPoint(x, y))
}

data class RealmState(
    val day: Int,
    val hour: Int,
    val minute: Int,
    val rngState: Long?,
    val activeParty: PartyId,
    val currentMap: WorldMapId,
    val currentMode: RuntimeMode,
    val currentTown: TownId?,
    val banished: Boolean,
    val lastPlayedDay: Int,
    val partyMemberMax: Int,
    val lastTerrain: TerrainId? = null,
)

data class LocationPlacement(val location: LocationId, val point: GridPoint)
data class NpcPlacement(val npc: NpcId, val position: WorldPosition)

data class PartyResources(
    val gold: Int,
    val torches: Int,
    val food: Int,
    val timeLeft: Int,
    val restedHours: Int,
)

data class PartyCounters(
    val memberCount: Int?,
    val kills: Int,
    val waitingMail: Boolean,
    val attacked: Boolean,
    val attackedToday: List<PartyAttackRecord>,
)

data class PartyAttackRecord(val party: PartyId, val attacked: Boolean)
data class InventorySlotState(val slot: Int, val item: ItemId?)
data class EquipmentState(val weapon: ItemId, val armor: ItemId)
data class NpcVisitState(val npc: NpcId, val visited: Boolean)

sealed interface JournalEntryState {
    data class Text(val id: TextId) : JournalEntryState
    data class Line(val text: String) : JournalEntryState
}

data class PartyMemberState(
    val name: String,
    val gender: Gender,
    val race: Race,
    val pcClass: PcClass,
    val level: Int,
    val health: Int,
    val hitPoints: HitPoints,
    val weaponSkill: Int,
    val wisdom: Int,
    val mana: Mana,
    val charisma: Int,
    val equipment: EquipmentState,
    val experience: Int,
    val dead: Boolean,
) {
    val armor: ItemId get() = equipment.armor
    val weapon: ItemId get() = equipment.weapon
}

data class PartyState(
    val id: PartyId,
    val player: String,
    val name: String,
    val position: WorldPosition,
    val facing: Facing?,
    val resources: PartyResources,
    val counters: PartyCounters,
    val flags: List<FlagState>,
    val milestones: List<GameMilestoneId>,
    val inventory: List<InventorySlotState>,
    val members: List<PartyMemberState>,
    val visitedNpcs: List<NpcVisitState>,
    val journal: List<JournalEntryState>,
)

data class StateModel(
    val id: StateId,
    val kind: StateKind,
    val realm: RealmState,
    val locations: List<LocationPlacement>,
    val npcs: List<NpcPlacement>,
    val parties: List<PartyState>,
)

data class RylweenModel(
    val content: GameModel,
    val states: List<StateModel>,
)

// =============================================================================
// 04. DSL kernel: readable expressions and mutable slots
// =============================================================================

abstract class IntExpr {
    abstract val ast: IntAst

    operator fun plus(other: IntExpr): IntExpr = Expr(IntAst.Binary(ast, "add", other.ast))
    operator fun plus(other: Int): IntExpr = this + lit(other)
    operator fun minus(other: IntExpr): IntExpr = Expr(IntAst.Binary(ast, "sub", other.ast))
    operator fun minus(other: Int): IntExpr = this - lit(other)
    operator fun times(other: IntExpr): IntExpr = Expr(IntAst.Binary(ast, "mul", other.ast))
    operator fun times(other: Int): IntExpr = this * lit(other)
    operator fun div(other: IntExpr): IntExpr = Expr(IntAst.Binary(ast, "div", other.ast))
    operator fun div(other: Int): IntExpr = this / lit(other)
}

data class Expr(override val ast: IntAst) : IntExpr()
class LocalIntRef internal constructor(val name: String) : IntExpr() {
    override val ast: IntAst = IntAst.Var(name)
}

fun lit(value: Int): IntExpr = Expr(IntAst.Lit(value))
fun v(name: String): IntExpr = Expr(IntAst.Var(name))
fun random(range: IntRange): IntExpr = Expr(IntAst.Random(IntAst.Lit(range.first), IntAst.Lit(range.last)))
fun chance(percent: IntExpr): BoolExpr = random(0..99) lt percent
fun chance(percent: Int): BoolExpr = chance(lit(percent))

infix fun IntExpr.eq(other: IntExpr): BoolExpr = Bool(BoolAst.Cmp(ast, "eq", other.ast))
infix fun IntExpr.eq(other: Int): BoolExpr = this eq lit(other)
infix fun IntExpr.ne(other: IntExpr): BoolExpr = Bool(BoolAst.Cmp(ast, "ne", other.ast))
infix fun IntExpr.ne(other: Int): BoolExpr = this ne lit(other)
infix fun IntExpr.lt(other: IntExpr): BoolExpr = Bool(BoolAst.Cmp(ast, "lt", other.ast))
infix fun IntExpr.lt(other: Int): BoolExpr = this lt lit(other)
infix fun IntExpr.lte(other: IntExpr): BoolExpr = Bool(BoolAst.Cmp(ast, "lte", other.ast))
infix fun IntExpr.lte(other: Int): BoolExpr = this lte lit(other)
infix fun IntExpr.gt(other: IntExpr): BoolExpr = Bool(BoolAst.Cmp(ast, "gt", other.ast))
infix fun IntExpr.gt(other: Int): BoolExpr = this gt lit(other)
infix fun IntExpr.gte(other: IntExpr): BoolExpr = Bool(BoolAst.Cmp(ast, "gte", other.ast))
infix fun IntExpr.gte(other: Int): BoolExpr = this gte lit(other)

abstract class BoolExpr {
    abstract val ast: BoolAst

    infix fun and(other: BoolExpr): BoolExpr = Bool(BoolAst.And(ast, other.ast))
    infix fun or(other: BoolExpr): BoolExpr = Bool(BoolAst.Or(ast, other.ast))
    operator fun not(): BoolExpr = Bool(BoolAst.Not(ast))
}

data class Bool(override val ast: BoolAst) : BoolExpr()

fun achieved(milestone: GameMilestoneId): BoolExpr =
    Bool(BoolAst.MilestoneAchieved(milestone))

class IntSlot(
    private val script: ScriptScope,
    private val lvalue: LValueAst,
    override val ast: IntAst,
) : IntExpr() {
    infix fun set(value: IntExpr): Unit = script.record(Op.Set(lvalue, value.ast))
    infix fun set(value: Int): Unit = set(lit(value))
    operator fun plusAssign(value: IntExpr): Unit = script.record(Op.Add(lvalue, value.ast))
    operator fun plusAssign(value: Int): Unit = plusAssign(lit(value))
    operator fun minusAssign(value: IntExpr): Unit = script.record(Op.Sub(lvalue, value.ast))
    operator fun minusAssign(value: Int): Unit = minusAssign(lit(value))
    operator fun timesAssign(value: IntExpr): Unit = script.record(Op.Set(lvalue, (this * value).ast))
    operator fun timesAssign(value: Int): Unit = timesAssign(lit(value))
    operator fun divAssign(value: IntExpr): Unit = script.record(Op.Set(lvalue, (this / value).ast))
    operator fun divAssign(value: Int): Unit = divAssign(lit(value))
    fun addClamped(value: IntExpr, max: IntExpr): Unit = script.record(Op.AddClamped(lvalue, value.ast, max.ast))
    fun addClamped(value: Int, max: IntExpr): Unit = addClamped(lit(value), max)
}

class RealmRef(private val script: ScriptScope) {
    val hour: IntSlot = IntSlot(script, LValueAst.RealmScalar("hour"), IntAst.RealmScalar("hour"))
    val partyMemberMax: IntSlot = IntSlot(
        script,
        LValueAst.RealmScalar("party_member_max"),
        IntAst.RealmScalar("party_member_max"),
    )
    val banished: BoolSlot = BoolSlot(
        script,
        LValueAst.RealmScalar("banished"),
        BoolAst.Cmp(IntAst.RealmScalar("banished"), "ne", IntAst.Lit(0)),
    )
}

class RuntimeContextRef {
    val inDungeon: BoolExpr = Bool(BoolAst.RuntimeFlag("in_dungeon"))
    val underground: BoolExpr = inDungeon
    val dungeonLevel: IntExpr = v("dungeon_level")
}

class BoolSlot(
    private val script: ScriptScope,
    private val lvalue: LValueAst,
    private val read: BoolAst,
) : BoolExpr() {
    override val ast: BoolAst = read
    infix fun set(value: Boolean): Unit = script.record(Op.Set(lvalue, IntAst.Lit(if (value) 1 else 0)))
}

class PartyRef(private val script: ScriptScope) {
    val gold: IntSlot = IntSlot(script, LValueAst.PartyScalar("gold"), IntAst.PartyScalar("gold"))
    val torches: IntSlot = IntSlot(script, LValueAst.PartyScalar("torches"), IntAst.PartyScalar("torches"))
    val food: IntSlot = IntSlot(script, LValueAst.PartyScalar("food"), IntAst.PartyScalar("food"))
    val timeLeft: IntSlot = IntSlot(script, LValueAst.PartyScalar("time_left"), IntAst.PartyScalar("time_left"))
    val restedHours: IntSlot = IntSlot(script, LValueAst.PartyScalar("rested_hours"), IntAst.PartyScalar("rested_hours"))
    val memberCount: IntSlot = IntSlot(script, LValueAst.PartyScalar("member_count"), IntAst.PartyScalar("member_count"))
    val inventory: PartyInventoryRef = PartyInventoryRef(script)
    val leader: MemberRef get() = member(lit(1))
    fun member(index: Int): MemberRef = member(lit(index))
    fun member(index: IntExpr): MemberRef = MemberRef(script, index)
}

class PartyInventoryRef(private val script: ScriptScope) {
    fun count(pool: ItemPoolId): IntExpr = Expr(IntAst.InventoryPoolCount(pool))

    fun itemCost(slot: InventorySlotRef): IntExpr =
        Expr(IntAst.InventorySlotCost(slot.index.ast))

    fun consume(pool: ItemPoolId, quantity: IntExpr) {
        script.record(Op.ConsumeInventoryPool(pool, quantity.ast))
    }

    fun consume(pool: ItemPoolId, quantity: Int): Unit = consume(pool, lit(quantity))

    fun clear(slot: InventorySlotRef) {
        script.record(Op.ClearInventorySlot(slot.index.ast))
    }
}

class MemberRef(private val script: ScriptScope, val index: IntExpr) {
    val hp: IntSlot = intField("hit_points")
    val maxHp: IntSlot = intField("health")
    val mp: IntSlot = intField("magic")
    val maxMp: IntSlot = intField("magic_max")
    val mana: IntSlot = mp
    val maxMana: IntSlot = maxMp
    val level: IntSlot = intField("level")
    val experience: IntSlot = intField("experience")
    val wisdom: IntSlot = intField("wisdom")
    val charisma: IntSlot = intField("charisma")
    val weaponSkill: IntSlot = intField("weapon_skill")
    val pcClass: IntSlot = intField("class")
    val dead: BoolSlot = BoolSlot(
        script,
        LValueAst.MemberField(index.ast, "dead"),
        BoolAst.MemberFlag(index.ast, "dead"),
    )
    val spellcaster: BoolExpr = Bool(BoolAst.MemberFlag(index.ast, "spellcaster"))

    fun isClass(klass: PcClass): BoolExpr = pcClass eq klass.sourceId
    fun fullHeal() {
        script.record(Op.Set(LValueAst.MemberField(index.ast, "hit_points"), maxHp.ast))
    }

    private fun intField(field: String): IntSlot =
        IntSlot(script, LValueAst.MemberField(index.ast, field), IntAst.MemberField(index.ast, field))
}

class MonsterRef(private val script: ScriptScope, val index: IntExpr) {
    val hp: IntSlot = IntSlot(script, LValueAst.MonsterField(index.ast, "hit_points"), IntAst.MonsterField(index.ast, "hit_points"))
    val dead: BoolSlot = BoolSlot(
        script,
        LValueAst.MonsterField(index.ast, "dead"),
        BoolAst.MonsterFlag(index.ast, "dead"),
    )
}

class BattleRef(private val script: ScriptScope) {
    val monsterWeaponSkill: IntSlot = IntSlot(
        script,
        LValueAst.BattleFlag("monster_weapon_skill"),
        IntAst.BattleFlag("monster_weapon_skill"),
    )
    val encounterHitPointMax: IntExpr = Expr(IntAst.BattleFlag("encounter_hit_point_max"))
    val monsterConfused: BoolSlot = boolFlag("monster_confused")
    val monstersHeld: BoolSlot = boolFlag("monsters_held")
    val antimagic: BoolSlot = boolFlag("antimagic")
    val partyHeld: BoolSlot = boolFlag("party_held")
    val partyConfused: BoolSlot = boolFlag("party_confused")
    val actionRetry: BoolSlot = boolFlag("action_retry")

    fun firstAliveMonster(): MonsterRef =
        MonsterRef(script, script.localInt(Expr(IntAst.BattleQuery("first_alive_monster")), "monster"))

    fun randomAliveMember(): MemberRef =
        MemberRef(script, script.localInt(Expr(IntAst.BattleQuery("random_alive_member")), "member"))

    private fun boolFlag(name: String): BoolSlot =
        BoolSlot(script, LValueAst.BattleFlag(name), BoolAst.BattleFlag(name))
}

sealed interface StrExpr {
    val ast: StrAst
}

data class Str(override val ast: StrAst) : StrExpr

enum class PronounCase { Subject, Object, Possessive }

sealed class NativeSpec(val id: NativeOpId, val reason: String) {
    val path: String get() = id.slug
}
class NativeEffectSpec(id: NativeOpId, reason: String) : NativeSpec(id, reason)
class NativeIntSpec(id: NativeOpId, reason: String) : NativeSpec(id, reason)
class NativeStringSpec(id: NativeOpId, reason: String) : NativeSpec(id, reason)

class NativeAllowlistRefs {
    val inventoryAdd: NativeEffectSpec = effect(NativeOps.inventoryAdd, "inventory slot mutation")
    val townEnterService: NativeEffectSpec = effect(NativeOps.townEnterService, "town building-service trampoline")

    val inputKeyUpper: NativeIntSpec = int(NativeOps.inputKeyUpper, "keyboard input normalization")

    val partyMemberName: NativeStringSpec = string(NativeOps.partyMemberName, "party-member display name binding")
    val combatMonsterName: NativeStringSpec = string(NativeOps.combatMonsterName, "combat monster display name binding")
    val partyPronounSubject: NativeStringSpec = string(NativeOps.partyPronounSubject, "party-member pronoun binding")
    val partyPronounObject: NativeStringSpec = string(NativeOps.partyPronounObject, "party-member pronoun binding")
    val partyPronounPossessive: NativeStringSpec = string(NativeOps.partyPronounPossessive, "party-member pronoun binding")
    val townNpcName: NativeStringSpec = string(NativeOps.townNpcName, "current-town NPC display name binding")
    val npcRoleName: NativeStringSpec = string(NativeOps.npcRoleName, "role-resolved NPC name binding")
    val npcRoleLocation: NativeStringSpec = string(NativeOps.npcRoleLocation, "role-resolved NPC location binding")

    val all: List<NativeSpec> = listOf(
        inventoryAdd,
        townEnterService,
        inputKeyUpper,
        partyMemberName,
        combatMonsterName,
        partyPronounSubject,
        partyPronounObject,
        partyPronounPossessive,
        townNpcName,
        npcRoleName,
        npcRoleLocation,
    )

    val byPath: Map<String, NativeSpec> = all.associateBy { it.path }

    init {
        check(byPath.size == all.size) { "duplicate native allowlist path" }
    }

    private fun effect(id: NativeOpId, reason: String) = NativeEffectSpec(id, reason)
    private fun int(id: NativeOpId, reason: String) = NativeIntSpec(id, reason)
    private fun string(id: NativeOpId, reason: String) = NativeStringSpec(id, reason)
}
val NativeAllowlist: NativeAllowlistRefs = NativeAllowlistRefs()

fun partyName(member: MemberRef): StrExpr =
    Str(StrAst.NativeString(NativeAllowlist.partyMemberName.path, listOf(NativeStringArg.IntValue(member.index.ast))))

fun monsterName(monster: MonsterRef): StrExpr =
    Str(StrAst.NativeString(NativeAllowlist.combatMonsterName.path, listOf(NativeStringArg.IntValue(monster.index.ast))))

fun pronoun(member: MemberRef, case: PronounCase): StrExpr {
    val native = when (case) {
        PronounCase.Subject -> NativeAllowlist.partyPronounSubject
        PronounCase.Object -> NativeAllowlist.partyPronounObject
        PronounCase.Possessive -> NativeAllowlist.partyPronounPossessive
    }
    return Str(StrAst.NativeString(native.path, listOf(NativeStringArg.IntValue(member.index.ast))))
}

fun Gender.pronoun(case: PronounCase): String =
    when (case) {
        PronounCase.Subject -> if (this == Gender.Female) "she" else "he"
        PronounCase.Object -> if (this == Gender.Female) "her" else "him"
        PronounCase.Possessive -> if (this == Gender.Female) "her" else "his"
    }

fun townNpcName(job: NpcJobId): StrExpr =
    Str(StrAst.NativeString(NativeAllowlist.townNpcName.path, listOf(NativeStringArg.Literal(render(job)))))

fun npcName(role: NpcRole): StrExpr =
    Str(StrAst.NativeString(NativeAllowlist.npcRoleName.path, listOf(NativeStringArg.Literal(role.slug))))

fun npcLocation(role: NpcRole): StrExpr =
    Str(StrAst.NativeString(NativeAllowlist.npcRoleLocation.path, listOf(NativeStringArg.Literal(role.slug))))

fun render(ast: IntAst): String = ast.toString()
fun render(arg: Any): String = when (arg) {
    is IntExpr -> render(arg.ast)
    is IntAst -> render(arg)
    is TextId -> arg.id
    is ItemId -> arg.slug
    is ItemPoolId -> arg.slug
    is MonsterId -> arg.slug
    is EncounterTier -> arg.slug
    is WorldMapId -> arg.slug
    is MapLayerId -> arg.slug
    is TerrainId -> arg.slug
    is TerrainTagId -> arg.slug
    is ImageId -> arg.slug
    is PaletteId -> arg.slug
    is PaletteColorId -> arg.slug
    is LayoutId -> arg.slug
    is ScreenRegionId -> arg.slug
    is SceneId -> arg.slug
    is SpellId -> arg.slug
    is MissionId -> arg.slug
    is NpcRole -> arg.slug
    is NativeOpId -> arg.slug
    is FlagState -> "${arg.flag.slug}.${arg.native}"
    is StateId -> arg.slug
    is PartyId -> arg.slug
    is NpcId -> arg.slug
    is LocationId -> arg.slug
    is TownId -> arg.slug
    is BuildingId -> arg.slug
    is FormulaId -> arg.slug
    is KeyActionId -> arg.slug
    is DungeonId -> arg.slug
    is GameMilestoneId -> arg.slug
    is LootTableId -> arg.slug
    is RewardBundleId -> arg.slug
    is NpcJobId -> arg.slug
    is RumourTableId -> arg.slug
    is RumourId -> arg.slug
    else -> arg.toString()
}

// =============================================================================
// 05. DSL kernel: builders and validation
// =============================================================================

class ScriptContext {
    private var nextVar = 0
    private val opTargets = mutableListOf<MutableList<Op>>()

    fun fresh(prefix: String): String = "${prefix}_${nextVar++}"

    fun record(fallback: MutableList<Op>, op: Op) {
        (opTargets.lastOrNull() ?: fallback) += op
    }

    fun <T> recording(target: MutableList<Op>, body: () -> T): T {
        opTargets += target
        return try {
            body()
        } finally {
            opTargets.removeAt(opTargets.lastIndex)
        }
    }
}

@DslMarker
annotation class RylNextDsl

@RylNextDsl
class ScriptScope(
    private val ctx: ScriptContext,
    private val ops: MutableList<Op>,
) {
    val realm: RealmRef = RealmRef(this)
    val context: RuntimeContextRef = RuntimeContextRef()
    val party: PartyRef = PartyRef(this)
    val battle: BattleRef = BattleRef(this)
    val ask: AskScope = AskScope(this)
    val choice: ChoiceScope = ChoiceScope(this)
    val view: ViewScope = ViewScope(this)
    val native: NativeScope = NativeScope(this)

    val currentCaster: MemberRef
        get() = party.member(v("caster_idx"))

    fun record(op: Op) {
        ctx.record(ops, op)
    }

    fun fresh(prefix: String): String = ctx.fresh(prefix)

    fun screen(text: TextBlock): Unit = record(Op.Screen(text))
    fun screen(id: TextId): Unit = screen(TextBlock.Ref(id))
    fun screen(vararg lines: String): Unit = screen(textLines(*lines))
    fun screen(body: StructuredTextBlockBuilder.() -> Unit): Unit = screen(structuredText(body))

    fun say(text: TextBlock): Unit = screen(text)
    fun say(id: TextId): Unit = screen(id)
    fun say(vararg lines: String): Unit = screen(*lines)
    fun say(body: StructuredTextBlockBuilder.() -> Unit): Unit = screen(body)
    fun emit(id: TextId): Unit = record(Op.Emit(id))
    fun emitIndexed(pattern: TextPattern, value: IntExpr): Unit = record(Op.EmitIndexed(pattern, value.ast))
    fun journal(text: TextBlock): Unit = record(Op.AppendJournal(text))
    fun journal(vararg lines: String): Unit = journal(textLines(*lines))
    fun journal(body: StructuredTextBlockBuilder.() -> Unit): Unit = journal(structuredText(body))
    fun waitInput(reason: String? = null): Unit = record(Op.WaitInput(reason))
    fun pause(ms: Int): Unit = record(Op.Pause(ms))

    fun clearText(): Unit = view.clearText()
    fun clearMapWindow(): Unit = view.clearMapWindow()
    fun renderMap(): Unit = view.renderMap()
    fun showBackdrop(image: ImageId): Unit = view.showBackdrop(image)
    fun showImage(image: ImageId): Unit = view.showImage(image)
    fun statusLine(): Unit = view.statusLine()

    fun let(name: String, value: IntExpr): IntExpr {
        record(Op.LetInt(name, value.ast))
        return v(name)
    }

    fun let(name: String, value: Int): IntExpr = let(name, lit(value))

    fun localInt(value: IntExpr, hint: String = "local"): LocalIntRef {
        val ref = LocalIntRef(fresh(hint))
        record(Op.LetInt(ref.name, value.ast))
        return ref
    }

    fun localInt(value: Int, hint: String = "local"): LocalIntRef = localInt(lit(value), hint)

    infix fun LocalIntRef.set(value: IntExpr) {
        record(Op.LetInt(name, value.ast))
    }

    infix fun LocalIntRef.set(value: Int): Unit = set(lit(value))

    operator fun LocalIntRef.plusAssign(value: IntExpr) {
        record(Op.LetInt(name, (this + value).ast))
    }

    operator fun LocalIntRef.plusAssign(value: Int): Unit = plusAssign(lit(value))

    operator fun LocalIntRef.minusAssign(value: IntExpr) {
        record(Op.LetInt(name, (this - value).ast))
    }

    operator fun LocalIntRef.minusAssign(value: Int): Unit = minusAssign(lit(value))

    fun bind(name: String, value: StrExpr) {
        record(Op.BindText(name, value.ast))
    }

    fun mark(state: FlagState) {
        record(Op.SetFlag(state.flag.slug, state.native))
    }

    fun whenever(cond: BoolExpr, then: ScriptScope.() -> Unit): ElseBranch {
        val thenOps = capture(then)
        val index = ops.size
        ops += Op.If(cond.ast, thenOps, emptyList())
        return ElseBranch(ctx) { elseOps -> ops[index] = Op.If(cond.ast, thenOps, elseOps) }
    }

    @Deprecated("Use whenever(...) in author-facing script blocks.")
    fun when_(cond: BoolExpr, then: ScriptScope.() -> Unit): ElseBranch = whenever(cond, then)

    fun stop(reason: String? = null): Unit = record(Op.Stop(reason))
    fun breakLoop(reason: String? = null): Unit = record(Op.Break(reason))

    fun loop(body: ScriptScope.() -> Unit) {
        record(Op.Loop(capture(body)))
    }

    fun choose(expr: IntExpr, body: SwitchBuilder.() -> Unit) {
        val builder = SwitchBuilder(ctx)
        builder.body()
        record(Op.Switch(expr.ast, builder.cases))
    }

    fun menu(prompt: TextId, body: MenuBuilder.() -> Unit) {
        val builder = MenuBuilder(ctx)
        builder.body()
        record(builder.build(TextBlock.Ref(prompt)))
    }

    fun menu(vararg promptLines: String, body: MenuBuilder.() -> Unit) {
        val builder = MenuBuilder(ctx)
        builder.body()
        record(builder.build(textLines(*promptLines)))
    }

    fun menu(prompt: TextBlock, body: MenuBuilder.() -> Unit) {
        val builder = MenuBuilder(ctx)
        builder.body()
        record(builder.build(prompt))
    }

    fun forEachMonster(includeDead: Boolean = false, body: ScriptScope.(MonsterRef) -> Unit) {
        val name = ctx.fresh("monster")
        val childOps = capture {
            body(MonsterRef(this, v(name)))
        }
        record(Op.ForEachMonster(name, includeDead, childOps))
    }

    fun forEachMember(includeDead: Boolean = false, body: ScriptScope.(MemberRef) -> Unit) {
        val name = ctx.fresh("member")
        val childOps = capture {
            body(MemberRef(this, v(name)))
        }
        record(Op.ForEachMember(name, includeDead, childOps))
    }

    fun healLivingMembers(amount: IntExpr) {
        forEachMember {
            it.hp.addClamped(amount, it.maxHp)
        }
    }

    fun healLivingMembers(amount: Int): Unit = healLivingMembers(lit(amount))

    fun run(scene: SceneId, vararg bindings: Pair<String, Any>) {
        record(Op.RunScene(scene, bindings.associate { it.first to render(it.second) }))
    }

    fun speak(building: BuildingId) {
        record(Op.Speak(building))
    }

    fun tellRumour(table: RumourTableId = DslDefaultRumourTables.standardTown) {
        record(Op.TellRumour(table))
    }

    fun encounter(monster: MonsterId, mandatory: Boolean = false, tier: EncounterTier? = null) {
        record(Op.Encounter(foe = monster, mandatory = mandatory, tier = tier))
    }

    fun enterDungeon(dungeon: DungeonId, levels: Int) {
        record(Op.EnterDungeon(dungeon, levels))
    }

    fun teleportParty(directionKey: IntExpr, distance: IntExpr) {
        record(Op.TeleportParty(directionKey.ast, distance.ast))
    }

    fun teleportParty(directionKey: Int, distance: Int) {
        teleportParty(lit(directionKey), lit(distance))
    }

    fun discoverCave(
        levels: IntExpr,
        dungeon: DungeonId = DslDefaultDungeons.randomCave,
        moveCost: IntExpr = lit(10),
    ) {
        record(Op.DiscoverCave(dungeon, levels.ast, moveCost.ast))
    }

    fun discoverCave(
        levels: Int,
        dungeon: DungeonId = DslDefaultDungeons.randomCave,
        moveCost: Int = 10,
    ) {
        discoverCave(lit(levels), dungeon, lit(moveCost))
    }

    fun consumeActiveItem(): Unit = record(Op.ConsumeActiveItem)

    fun consumeTorch(): Unit = record(Op.ConsumeTorch)

    fun reward(body: ScriptRewardBuilder.() -> Unit) {
        record(Op.GrantReward(ScriptRewardBuilder().apply(body).build()))
    }

    fun shopService(building: BuildingId, body: ShopServiceBuilder.() -> Unit) {
        record(Op.ShopService(ShopServiceBuilder(building).apply(body).build()))
    }

    fun achieve(milestone: GameMilestoneId) {
        record(Op.AchieveMilestone(milestone))
    }

    fun pay(amount: IntExpr, broke: ScriptScope.() -> Unit, ok: ScriptScope.() -> Unit) {
        whenever(party.gold lt amount) {
            broke()
        } otherwise {
            party.gold -= amount
            ok()
        }
    }

    internal fun capture(body: ScriptScope.() -> Unit): List<Op> {
        return recordScript(ctx, body)
    }
}

@RylNextDsl
class ScriptRewardBuilder {
    private var goldAst: IntAst? = null
    private val itemIds = mutableListOf<ItemId>()
    private var applicationModel = RewardApplicationBuilder().build()
    private var messagesModel = RewardMessageModel()

    fun gold(amount: Int) {
        goldAst = IntAst.Lit(amount)
    }

    fun gold(amount: IntExpr) {
        goldAst = amount.ast
    }

    fun items(vararg items: ItemId) {
        itemIds += items
    }

    fun placement(body: RewardApplicationBuilder.() -> Unit) {
        applicationModel = RewardApplicationBuilder(applicationModel).apply(body).build()
    }

    fun promptDrop(cantCarry: TextBlock, pickedUp: TextBlock? = null) {
        applicationModel = applicationModel.copy(overflow = RewardOverflowPolicy.PromptDropThenPlace)
        messagesModel = RewardMessageModel(cantCarry, pickedUp)
    }

    fun promptDrop(cantCarry: String, pickedUp: String? = null) {
        promptDrop(
            TextBlock.Inline(listOf(cantCarry)),
            pickedUp?.let { TextBlock.Inline(listOf(it)) },
        )
    }

    fun promptDrop(cantCarry: TextId, pickedUp: TextId? = null) {
        promptDrop(TextBlock.Ref(cantCarry), pickedUp?.let(TextBlock::Ref))
    }

    fun build(): ScriptRewardModel =
        ScriptRewardModel(goldAst, itemIds.toList(), applicationModel, messagesModel)
}

@RylNextDsl
class RandomItemOfferTextBuilder : TextLineSink {
    private val text = StructuredTextBlockBuilder()

    val itemName: TextPart = TextPart.OfferedItemName
    val itemPrice: TextPart = TextPart.OfferedItemPrice

    override fun addTextLine(line: TextLine) {
        text.addTextLine(line)
    }

    fun build(): TextBlock = text.build()
}

@RylNextDsl
class RandomItemOfferBuilder(private val pool: ItemPoolId) {
    private var offerText: TextBlock? = null
    private var choicePromptText: TextBlock = inlineText("(B)uy it", "(N)o thanks")
    private var brokeText: TextBlock? = null
    private var dealText: TextBlock? = null
    private var declinedText: TextBlock? = null
    private var applicationModel = RewardApplicationBuilder().apply {
        overflow = RewardOverflowPolicy.PromptDropThenPlace
    }.build()
    private var messagesModel = RewardMessageModel()
    private var buyBinding: InputBindingModel = inputKey('B')
    private var declineBinding: InputBindingModel = inputKey('N')
    private var priceOverrideModel: Int? = null

    var buyKey: Char
        get() = buyBinding.primaryChar
        set(value) {
            buyBinding = inputKey(value)
        }
    var declineKey: Char
        get() = declineBinding.primaryChar
        set(value) {
            declineBinding = inputKey(value)
        }
    var brokePauseMillis: Int = 800
    var fullPauseMillis: Int = 1000
    var dealPauseMillis: Int = 800
    var declinedPauseMillis: Int = 1000

    fun offer(vararg lines: String) {
        offerText = inlineText(*lines)
    }

    fun offer(body: RandomItemOfferTextBuilder.() -> Unit) {
        offerText = RandomItemOfferTextBuilder().apply(body).build()
    }

    fun choicePrompt(vararg lines: String) {
        choicePromptText = inlineText(*lines)
    }

    fun choicePrompt(body: RandomItemOfferTextBuilder.() -> Unit) {
        choicePromptText = RandomItemOfferTextBuilder().apply(body).build()
    }

    fun choiceKeys(buy: InputBindingModel = buyBinding, decline: InputBindingModel = declineBinding) {
        buyBinding = buy
        declineBinding = decline
    }

    fun free() {
        priceOverrideModel = 0
    }

    fun price(amount: Int) {
        priceOverrideModel = amount.coerceAtLeast(0)
    }

    fun broke(vararg lines: String) {
        brokeText = inlineText(*lines)
    }

    fun broke(body: RandomItemOfferTextBuilder.() -> Unit) {
        brokeText = RandomItemOfferTextBuilder().apply(body).build()
    }

    fun full(vararg lines: String) {
        messagesModel = messagesModel.copy(cantCarry = inlineText(*lines))
    }

    fun full(body: RandomItemOfferTextBuilder.() -> Unit) {
        messagesModel = messagesModel.copy(cantCarry = RandomItemOfferTextBuilder().apply(body).build())
    }

    fun promptDrop(pickedUp: TextBlock? = null) {
        applicationModel = applicationModel.copy(overflow = RewardOverflowPolicy.PromptDropThenPlace)
        messagesModel = messagesModel.copy(pickedUp = pickedUp)
    }

    fun promptDrop(pickedUp: String) {
        promptDrop(inlineText(pickedUp))
    }

    fun promptDrop(pickedUp: TextId) {
        promptDrop(TextBlock.Ref(pickedUp))
    }

    fun deal(vararg lines: String) {
        dealText = inlineText(*lines)
    }

    fun deal(body: RandomItemOfferTextBuilder.() -> Unit) {
        dealText = RandomItemOfferTextBuilder().apply(body).build()
    }

    fun declined(vararg lines: String) {
        declinedText = inlineText(*lines)
    }

    fun declined(body: RandomItemOfferTextBuilder.() -> Unit) {
        declinedText = RandomItemOfferTextBuilder().apply(body).build()
    }

    fun build(): RandomItemOfferModel =
        RandomItemOfferModel(
            pool = pool,
            offer = requireNotNull(offerText) { "random item offer requires offer text" },
            choicePrompt = choicePromptText,
            broke = requireNotNull(brokeText) { "random item offer requires broke text" },
            deal = requireNotNull(dealText) { "random item offer requires deal text" },
            declined = requireNotNull(declinedText) { "random item offer requires declined text" },
            application = applicationModel,
            messages = messagesModel.copy(
                cantCarry = requireNotNull(messagesModel.cantCarry) {
                    "random item offer requires full/cant-carry text"
                },
            ),
            buy = buyBinding,
            decline = declineBinding,
            priceOverride = priceOverrideModel,
            brokePauseMillis = brokePauseMillis,
            fullPauseMillis = fullPauseMillis,
            dealPauseMillis = dealPauseMillis,
            declinedPauseMillis = declinedPauseMillis,
        )
}

@RylNextDsl
class ShopTextBuilder : TextLineSink {
    private val text = StructuredTextBlockBuilder()

    val price: TextPart = TextPart.ShopPrice
    val cap: TextPart = TextPart.ShopCarryCap

    override fun addTextLine(line: TextLine) {
        text.addTextLine(line)
    }

    fun build(): TextBlock = text.build()
}
fun shopText(body: ShopTextBuilder.() -> Unit): TextBlock =
    ShopTextBuilder().apply(body).build()

@RylNextDsl
class ShopServiceBuilder(private val building: BuildingId) {
    var image: ImageId? = null
    private var closedText: TextBlock = inlineText("Shop Closed.")
    private var introText: TextBlock? = null
    private var menuText: TextBlock? = null
    private var keysModel: ShopMenuKeysModel = ShopMenuKeysModel()
    private var goodbyeText: TextBlock = inlineText("Well, bye then...")
    private var buyModel: ShopBuyModel? = null
    private var sellModel: ShopSellModel? = null
    private var interludeModel: ShopInterludeModel? = null

    fun closed(vararg lines: String) {
        closedText = inlineText(*lines)
    }

    fun closed(body: StructuredTextBlockBuilder.() -> Unit) {
        closedText = structuredText(body)
    }

    fun intro(vararg lines: String) {
        introText = inlineText(*lines)
    }

    fun intro(body: StructuredTextBlockBuilder.() -> Unit) {
        introText = structuredText(body)
    }

    fun menu(vararg lines: String) {
        menuText = inlineText(*lines)
    }

    fun menu(body: StructuredTextBlockBuilder.() -> Unit) {
        menuText = structuredText(body)
    }

    fun keys(body: ShopMenuKeysBuilder.() -> Unit) {
        keysModel = ShopMenuKeysBuilder(keysModel).apply(body).build()
    }

    fun goodbye(vararg lines: String) {
        goodbyeText = inlineText(*lines)
    }

    fun goodbye(body: StructuredTextBlockBuilder.() -> Unit) {
        goodbyeText = structuredText(body)
    }

    fun interlude(chancePercent: Int, body: ShopInterludeBuilder.() -> Unit) {
        interludeModel = ShopInterludeBuilder(chancePercent).apply(body).build()
    }

    fun buy(body: ShopBuyBuilder.() -> Unit) {
        buyModel = ShopBuyBuilder().apply(body).build()
    }

    fun sell(body: ShopSellBuilder.() -> Unit) {
        sellModel = ShopSellBuilder().apply(body).build()
    }

    fun build(): ShopServiceModel =
        ShopServiceModel(
            building = building,
            image = requireNotNull(image) { "shop ${building.slug} requires image" },
            closed = closedText,
            intro = requireNotNull(introText) { "shop ${building.slug} requires intro text" },
            menu = requireNotNull(menuText) { "shop ${building.slug} requires menu text" },
            keys = keysModel,
            goodbye = goodbyeText,
            buy = buyModel,
            sell = sellModel,
            interlude = interludeModel,
        )
}

@RylNextDsl
class ShopMenuKeysBuilder(initial: ShopMenuKeysModel = ShopMenuKeysModel()) {
    var buy: InputBindingModel = initial.buy
    var sell: InputBindingModel = initial.sell
    var leave: InputBindingModel = initial.leave

    fun build(): ShopMenuKeysModel = ShopMenuKeysModel(buy, sell, leave)
}

@RylNextDsl
class ShopInterludeBuilder(private val chancePercent: Int) {
    private var offerText: TextBlock? = null
    private val acceptedTexts = mutableListOf<TextBlock>()
    private var declinedText: TextBlock? = null
    private var acceptBinding: InputBindingModel = inputKey('A')
    private var declineBinding: InputBindingModel = inputKey('D')
    var runsRumourAfterAccept: Boolean = false

    var acceptKey: Char
        get() = acceptBinding.primaryChar
        set(value) {
            acceptBinding = inputKey(value)
        }
    var declineKey: Char
        get() = declineBinding.primaryChar
        set(value) {
            declineBinding = inputKey(value)
        }

    fun choiceKeys(accept: InputBindingModel = acceptBinding, decline: InputBindingModel = declineBinding) {
        acceptBinding = accept
        declineBinding = decline
    }

    fun offer(vararg lines: String) {
        offerText = inlineText(*lines)
    }

    fun offer(body: StructuredTextBlockBuilder.() -> Unit) {
        offerText = structuredText(body)
    }

    fun accepted(vararg lines: String) {
        acceptedTexts += inlineText(*lines)
    }

    fun accepted(body: StructuredTextBlockBuilder.() -> Unit) {
        acceptedTexts += structuredText(body)
    }

    fun declined(vararg lines: String) {
        declinedText = inlineText(*lines)
    }

    fun declined(body: StructuredTextBlockBuilder.() -> Unit) {
        declinedText = structuredText(body)
    }

    fun build(): ShopInterludeModel =
        ShopInterludeModel(
            chancePercent = chancePercent,
            offer = requireNotNull(offerText) { "shop interlude requires offer text" },
            accept = acceptBinding,
            decline = declineBinding,
            accepted = acceptedTexts.toList(),
            declined = requireNotNull(declinedText) { "shop interlude requires declined text" },
            runsRumourAfterAccept = runsRumourAfterAccept,
        )
}

@RylNextDsl
class ShopBuyBuilder {
    private var promptText: TextBlock? = null
    private val catalogModels = mutableListOf<ShopCatalogModel>()
    private var tooExpensiveText: TextBlock = inlineText("Too expensive!")
    private var cantCarryText: TextBlock = inlineText("You cannot carry more.")
    private var dealText: TextBlock = inlineText("Deal!")
    private var unitPriceText: TextBlock? = null
    private var singlePriceText: TextBlock? = null
    private var payPromptText: TextBlock = inlineText("(P)ay", "(N)one")
    private var paymentKeysModel: ShopPurchaseKeysModel = ShopPurchaseKeysModel()
    private var applicationModel = RewardApplicationBuilder().apply {
        overflow = RewardOverflowPolicy.PromptDropThenPlace
    }.build()

    fun prompt(vararg lines: String) {
        promptText = inlineText(*lines)
    }

    fun prompt(body: StructuredTextBlockBuilder.() -> Unit) {
        promptText = structuredText(body)
    }

    fun tooExpensive(vararg lines: String) {
        tooExpensiveText = inlineText(*lines)
    }

    fun cantCarry(vararg lines: String) {
        cantCarryText = inlineText(*lines)
    }

    fun deal(vararg lines: String) {
        dealText = inlineText(*lines)
    }

    fun unitPrice(body: ShopTextBuilder.() -> Unit) {
        unitPriceText = ShopTextBuilder().apply(body).build()
    }

    fun singlePrice(body: ShopTextBuilder.() -> Unit) {
        singlePriceText = ShopTextBuilder().apply(body).build()
    }

    fun payPrompt(vararg lines: String) {
        payPromptText = inlineText(*lines)
    }

    fun paymentKeys(body: ShopPurchaseKeysBuilder.() -> Unit) {
        paymentKeysModel = ShopPurchaseKeysBuilder(paymentKeysModel).apply(body).build()
    }

    fun exactFullInventoryReject() {
        applicationModel = applicationModel.copy(overflow = RewardOverflowPolicy.SkipIfInventoryFull)
    }

    fun catalog(key: Char, title: String, body: ShopCatalogBuilder.() -> Unit) {
        catalog(inputKey(key), title, body)
    }

    fun catalog(binding: InputBindingModel, title: String, body: ShopCatalogBuilder.() -> Unit) {
        catalogModels += ShopCatalogBuilder(binding, title).apply(body).build()
    }

    fun build(): ShopBuyModel =
        ShopBuyModel(
            prompt = requireNotNull(promptText) { "shop buy section requires prompt text" },
            catalogs = catalogModels.toList(),
            tooExpensive = tooExpensiveText,
            cantCarry = cantCarryText,
            deal = dealText,
            unitPrice = unitPriceText,
            singlePrice = singlePriceText,
            payPrompt = payPromptText,
            paymentKeys = paymentKeysModel,
            inventoryApplication = applicationModel,
        )
}

@RylNextDsl
class ShopPurchaseKeysBuilder(initial: ShopPurchaseKeysModel = ShopPurchaseKeysModel()) {
    var pay: InputBindingModel = initial.pay
    var decline: InputBindingModel = initial.decline

    fun build(): ShopPurchaseKeysModel = ShopPurchaseKeysModel(pay, decline)
}

@RylNextDsl
class ShopCatalogBuilder(private val binding: InputBindingModel, private val title: String) {
    private var introText: TextBlock? = null
    private val pages = mutableListOf<ShopCatalogPageModel>()
    private val looseEntries = mutableListOf<ShopItemOfferModel>()

    fun intro(vararg lines: String) {
        introText = inlineText(*lines)
    }

    fun item(
        key: Char,
        item: ItemId,
        label: String? = null,
        price: ShopPriceModel = ShopPriceModel.ItemCost,
        description: TextBlock? = null,
    ) {
        item(inputKey(key), item, label, price, description)
    }

    fun item(
        binding: InputBindingModel,
        item: ItemId,
        label: String? = null,
        price: ShopPriceModel = ShopPriceModel.ItemCost,
        description: TextBlock? = null,
    ) {
        looseEntries += ShopItemOfferModel(
            binding = binding,
            item = item,
            label = label,
            description = description,
            price = price,
            purchase = ShopPurchaseModel.InventoryItem,
        )
    }

    fun item(
        key: Char,
        item: ItemId,
        label: String? = null,
        price: ShopPriceModel = ShopPriceModel.ItemCost,
        description: StructuredTextBlockBuilder.() -> Unit,
    ) {
        item(inputKey(key), item, label, price, structuredText(description))
    }

    fun item(
        binding: InputBindingModel,
        item: ItemId,
        label: String? = null,
        price: ShopPriceModel = ShopPriceModel.ItemCost,
        description: StructuredTextBlockBuilder.() -> Unit,
    ) {
        item(binding, item, label, price, structuredText(description))
    }

    fun resource(
        key: Char,
        item: ItemId,
        resource: Resource,
        cap: ShopQuantityCap,
        tooMany: TextBlock,
        label: String? = null,
        price: ShopPriceModel = ShopPriceModel.ItemCost,
        zeroQuantityDeal: ShopZeroQuantityDealPolicy = ShopZeroQuantityDealPolicy.NonZeroOnly,
        quantityPrompt: TextBlock = inlineText("How many? "),
        description: TextBlock? = null,
    ) {
        resource(inputKey(key), item, resource, cap, tooMany, label, price, zeroQuantityDeal, quantityPrompt, description)
    }

    fun resource(
        binding: InputBindingModel,
        item: ItemId,
        resource: Resource,
        cap: ShopQuantityCap,
        tooMany: TextBlock,
        label: String? = null,
        price: ShopPriceModel = ShopPriceModel.ItemCost,
        zeroQuantityDeal: ShopZeroQuantityDealPolicy = ShopZeroQuantityDealPolicy.NonZeroOnly,
        quantityPrompt: TextBlock = inlineText("How many? "),
        description: TextBlock? = null,
    ) {
        looseEntries += ShopItemOfferModel(
            binding = binding,
            item = item,
            label = label,
            description = description,
            price = price,
            purchase = ShopPurchaseModel.Bulk(resource, quantityPrompt, cap, tooMany, zeroQuantityDeal),
        )
    }

    fun resource(
        key: Char,
        item: ItemId,
        resource: Resource,
        cap: ShopQuantityCap,
        tooMany: TextBlock,
        label: String? = null,
        price: ShopPriceModel = ShopPriceModel.ItemCost,
        zeroQuantityDeal: ShopZeroQuantityDealPolicy = ShopZeroQuantityDealPolicy.NonZeroOnly,
        quantityPrompt: TextBlock = inlineText("How many? "),
        description: StructuredTextBlockBuilder.() -> Unit,
    ) {
        resource(
            inputKey(key),
            item,
            resource,
            cap,
            tooMany,
            label,
            price,
            zeroQuantityDeal,
            quantityPrompt,
            structuredText(description),
        )
    }

    fun resource(
        binding: InputBindingModel,
        item: ItemId,
        resource: Resource,
        cap: ShopQuantityCap,
        tooMany: TextBlock,
        label: String? = null,
        price: ShopPriceModel = ShopPriceModel.ItemCost,
        zeroQuantityDeal: ShopZeroQuantityDealPolicy = ShopZeroQuantityDealPolicy.NonZeroOnly,
        quantityPrompt: TextBlock = inlineText("How many? "),
        description: StructuredTextBlockBuilder.() -> Unit,
    ) {
        resource(
            binding,
            item,
            resource,
            cap,
            tooMany,
            label,
            price,
            zeroQuantityDeal,
            quantityPrompt,
            structuredText(description),
        )
    }

    fun page(footer: TextBlock? = null, body: ShopCatalogPageBuilder.() -> Unit) {
        pages += ShopCatalogPageBuilder().apply(body).build(footer)
    }

    fun page(vararg footerLines: String, body: ShopCatalogPageBuilder.() -> Unit) {
        page(inlineText(*footerLines), body)
    }

    fun build(): ShopCatalogModel =
        ShopCatalogModel(
            binding = binding,
            title = title,
            intro = introText,
            pages = pages.toList() + looseEntries.takeIf { entries -> entries.isNotEmpty() }?.let { entries ->
                listOf(ShopCatalogPageModel(entries.toList(), footer = null))
            }.orEmpty(),
        )
}

@RylNextDsl
class ShopCatalogPageBuilder {
    private val entries = mutableListOf<ShopItemOfferModel>()

    fun item(
        key: Char,
        item: ItemId,
        label: String? = null,
        price: ShopPriceModel = ShopPriceModel.ItemCost,
        description: TextBlock? = null,
    ) {
        item(inputKey(key), item, label, price, description)
    }

    fun item(
        binding: InputBindingModel,
        item: ItemId,
        label: String? = null,
        price: ShopPriceModel = ShopPriceModel.ItemCost,
        description: TextBlock? = null,
    ) {
        entries += ShopItemOfferModel(
            binding = binding,
            item = item,
            label = label,
            description = description,
            price = price,
            purchase = ShopPurchaseModel.InventoryItem,
        )
    }

    fun build(footer: TextBlock?): ShopCatalogPageModel =
        ShopCatalogPageModel(entries.toList(), footer)
}

@RylNextDsl
class ShopSellBuilder {
    private var promptText: TextBlock? = null
    private val acceptedKindModels = mutableSetOf<ItemKind>()
    private val acceptedItemModels = mutableSetOf<ItemId>()
    private val rejectedItemModels = mutableSetOf<ItemId>()
    private var wrongKindText: TextBlock = inlineText("Wrong kind.")
    private var offerText: TextBlock? = null
    private var soldText: TextBlock? = null
    var price: ShopPriceModel = ShopPriceModel.HalfItemCost
    private var bargainModel: ShopSellBargainModel? = null
    private var saleKeysModel: ShopSaleKeysModel = ShopSaleKeysModel()

    fun prompt(vararg lines: String) {
        promptText = inlineText(*lines)
    }

    fun acceptsKinds(vararg kinds: ItemKind) {
        acceptedKindModels += kinds
    }

    fun accepts(vararg items: ItemId) {
        acceptedItemModels += items
    }

    fun rejects(vararg items: ItemId) {
        rejectedItemModels += items
    }

    fun wrongKind(vararg lines: String) {
        wrongKindText = inlineText(*lines)
    }

    fun offer(body: ShopTextBuilder.() -> Unit) {
        offerText = ShopTextBuilder().apply(body).build()
    }

    fun sold(vararg lines: String) {
        soldText = inlineText(*lines)
    }

    fun saleKeys(body: ShopSaleKeysBuilder.() -> Unit) {
        saleKeysModel = ShopSaleKeysBuilder(saleKeysModel).apply(body).build()
    }

    fun bargain(body: ShopSellBargainBuilder.() -> Unit) {
        bargainModel = ShopSellBargainBuilder().apply(body).build()
    }

    fun smithHaggle() {
        bargain {
            bonus(rollGreaterThan = 90, addCurrentDivisor = 4)
            bonus(rollGreaterThan = 75, addCurrentDivisor = 6)
            rejectWhenRollBelow(76)
        }
    }

    fun build(): ShopSellModel =
        ShopSellModel(
            prompt = requireNotNull(promptText) { "shop sell section requires prompt text" },
            acceptedKinds = acceptedKindModels.toSet(),
            acceptedItems = acceptedItemModels.toSet(),
            rejectedItems = rejectedItemModels.toSet(),
            wrongKind = wrongKindText,
            offer = requireNotNull(offerText) { "shop sell section requires offer text" },
            sold = soldText,
            price = price,
            bargain = bargainModel,
            saleKeys = saleKeysModel,
        )
}

@RylNextDsl
class ShopSellBargainBuilder {
    private val bonuses = mutableListOf<ShopBargainBonusModel>()
    private var rejectRollBelow: Int? = null

    fun bonus(rollGreaterThan: Int, addCurrentDivisor: Int) {
        bonuses += ShopBargainBonusModel(rollGreaterThan, addCurrentDivisor)
    }

    fun rejectWhenRollBelow(threshold: Int) {
        rejectRollBelow = threshold
    }

    internal fun build(): ShopSellBargainModel =
        ShopSellBargainModel(
            bonuses = bonuses.toList(),
            rejectRollBelow = rejectRollBelow,
        )
}

@RylNextDsl
class ShopSaleKeysBuilder(initial: ShopSaleKeysModel = ShopSaleKeysModel()) {
    var accept: InputBindingModel = initial.accept
    var decline: InputBindingModel = initial.decline

    fun build(): ShopSaleKeysModel = ShopSaleKeysModel(accept, decline)
}

class ElseBranch(
    private val ctx: ScriptContext,
    private val finish: (List<Op>) -> Unit,
) {
    infix fun otherwise(body: ScriptScope.() -> Unit) {
        finish(recordScript(ctx, body))
    }
}

@RylNextDsl
class SwitchBuilder(private val ctx: ScriptContext) {
    val cases: MutableList<SwitchCase> = mutableListOf()

    fun case(value: Int, body: ScriptScope.() -> Unit) {
        cases += SwitchCase(value, recordScript(ctx, body))
    }

    fun default(body: ScriptScope.() -> Unit) {
        cases += SwitchCase(null, recordScript(ctx, body))
    }
}

@RylNextDsl
class MenuBuilder(private val ctx: ScriptContext) {
    private val options = mutableListOf<MenuOption>()
    private var defaultOps = emptyList<Op>()

    fun option(key: Char, body: ScriptScope.() -> Unit) {
        option(inputKey(key), body)
    }

    fun option(binding: InputBindingModel, body: ScriptScope.() -> Unit) {
        options += MenuOption(binding, recordScript(ctx, body))
    }

    fun defaultOption(key: Char, body: ScriptScope.() -> Unit) {
        defaultOption(inputKey(key), body)
    }

    fun defaultOption(binding: InputBindingModel, body: ScriptScope.() -> Unit) {
        val bodyOps = recordScript(ctx, body)
        options += MenuOption(binding, bodyOps)
        defaultOps = bodyOps
    }

    fun default(body: ScriptScope.() -> Unit) {
        defaultOps = recordScript(ctx, body)
    }

    fun build(prompt: TextBlock): Op.Menu = Op.Menu(prompt, options.toList(), defaultOps)
}

class AskScope(private val script: ScriptScope) {
    fun key(prompt: TextBlock, vararg bindings: InputBindingModel): IntExpr {
        val uniqueBindings = bindings.toList()
        require(uniqueBindings.isNotEmpty()) { "key prompt must accept at least one key" }
        val name = script.fresh("key")
        script.record(Op.AskKey(prompt, name, uniqueBindings))
        return v(name)
    }

    fun key(prompt: TextBlock, vararg keys: Char): IntExpr =
        key(prompt, *keys.map(::inputKey).toTypedArray())

    fun digit(prompt: TextBlock, range: IntRange): IntExpr =
        key(prompt, *range.map { inputKey(it.toString()) }.toTypedArray())

    fun wildernessDirection(prompt: TextBlock): IntExpr {
        val keys = WildernessKeysModel()
        return key(prompt, keys.moveSouth, keys.moveWest, keys.moveEast, keys.moveNorth)
    }

    fun member(
        prompt: TextId,
        filter: MemberChoiceFilter = MemberChoiceFilter.Any,
        accepting: MemberChoiceFilter = filter,
    ): MemberRef =
        member(TextBlock.Ref(prompt), filter, accepting)

    fun member(
        prompt: TextBlock,
        filter: MemberChoiceFilter = MemberChoiceFilter.Any,
        accepting: MemberChoiceFilter = filter,
    ): MemberRef {
        val name = script.fresh("member")
        script.record(Op.AskMember(prompt, name, filter, accepting))
        return script.party.member(v(name))
    }

    fun inventorySlot(
        prompt: TextBlock,
        firstKey: Char = 'A',
        slots: IntRange = 1..InventorySaveSlotMax,
        filter: InventorySlotChoiceFilter = InventorySlotChoiceFilter.Any,
    ): InventorySlotRef {
        val name = script.fresh("inventory_slot")
        script.record(Op.AskInventorySlot(prompt, name, firstKey.uppercaseChar(), slots, filter))
        return InventorySlotRef(v(name))
    }

    fun monsterAlive(prompt: TextId): MonsterRef {
        val name = script.fresh("monster")
        script.record(Op.AskMonster(prompt, name))
        return MonsterRef(script, v(name))
    }
}

class ChoiceScope(private val script: ScriptScope) {
    fun yesNo(
        prompt: TextBlock,
        yes: ScriptScope.() -> Unit,
        no: ScriptScope.() -> Unit = {},
    ) {
        yesNo(prompt, StandardYesNoKeys, yes, no)
    }

    fun yesNo(
        prompt: TextBlock,
        keys: YesNoKeysModel,
        yes: ScriptScope.() -> Unit,
        no: ScriptScope.() -> Unit = {},
    ) {
        script.menu(prompt) {
            option(keys.yes, yes)
            option(keys.no, no)
            default(no)
        }
    }

    fun yesNo(
        vararg promptLines: String,
        yes: ScriptScope.() -> Unit,
        no: ScriptScope.() -> Unit = {},
    ) {
        yesNo(TextBlock.Inline(promptLines.toList()), yes, no)
    }

    fun takeOrLeave(prompt: TextBlock, take: ScriptScope.() -> Unit, leave: ScriptScope.() -> Unit) {
        script.menu(prompt) {
            option('T', take)
            option('L', leave)
            default(leave)
        }
    }

    fun enterOrLeave(
        prompt: TextBlock,
        enter: ScriptScope.() -> Unit,
        leave: ScriptScope.() -> Unit = {},
    ) {
        script.menu(prompt) {
            option('E', enter)
            option('L', leave)
        }
    }

    fun payNoThanks(amount: IntExpr, broke: ScriptScope.() -> Unit, paid: ScriptScope.() -> Unit) {
        script.menu("(P)ay", "(N)o thanks") {
            option('P') {
                pay(amount, broke, paid)
            }
            option('N') {}
        }
    }

    fun randomItemOffer(pool: ItemPoolId, body: RandomItemOfferBuilder.() -> Unit) {
        script.record(Op.RandomItemOffer(RandomItemOfferBuilder(pool).apply(body).build()))
    }

    fun textAnswer(
        prompt: TextBlock,
        accepting: List<String>,
        caseSensitive: Boolean = false,
        trim: Boolean = true,
        maxLength: Int? = null,
        match: ScriptScope.() -> Unit,
        miss: ScriptScope.() -> Unit,
    ) {
        require(accepting.isNotEmpty()) { "text answer prompt must accept at least one value" }
        script.record(
            Op.AskText(
                prompt = prompt,
                accepted = accepting,
                matchOps = script.capture(match),
                missOps = script.capture(miss),
                caseSensitive = caseSensitive,
                trim = trim,
                maxLength = maxLength,
            ),
        )
    }

    fun textAnswer(
        prompt: String,
        accepting: List<String>,
        caseSensitive: Boolean = false,
        trim: Boolean = true,
        maxLength: Int? = null,
        match: ScriptScope.() -> Unit,
        miss: ScriptScope.() -> Unit,
    ) {
        textAnswer(inlineText(prompt), accepting, caseSensitive, trim, maxLength, match, miss)
    }

    fun member(
        prompt: TextId,
        filter: MemberChoiceFilter = MemberChoiceFilter.Any,
        accepting: MemberChoiceFilter = filter,
    ): MemberRef =
        script.ask.member(prompt, filter, accepting)

    fun member(
        prompt: TextBlock,
        filter: MemberChoiceFilter = MemberChoiceFilter.Any,
        accepting: MemberChoiceFilter = filter,
    ): MemberRef =
        script.ask.member(prompt, filter, accepting)

    fun member(
        prompt: TextId,
        filter: MemberChoiceFilter = MemberChoiceFilter.Any,
        accepting: MemberChoiceFilter = filter,
        body: ScriptScope.(MemberRef) -> Unit,
    ) {
        script.body(member(prompt, filter, accepting))
    }

    fun inventorySlot(
        prompt: TextBlock,
        firstKey: Char = 'A',
        slots: IntRange = 1..InventorySaveSlotMax,
        filter: InventorySlotChoiceFilter = InventorySlotChoiceFilter.Any,
        body: ScriptScope.(InventorySlotRef) -> Unit,
    ) {
        script.body(script.ask.inventorySlot(prompt, firstKey, slots, filter))
    }
}

data class InventorySlotRef(val index: IntExpr)

class ViewScope(private val script: ScriptScope) {
    fun clearText(): Unit = script.record(Op.View(ViewCommand.ClearText))
    fun clearMapWindow(): Unit = script.record(Op.View(ViewCommand.ClearMapWindow))
    fun renderMap(): Unit = script.record(Op.View(ViewCommand.RenderMap))
    fun redrawMap(): Unit = renderMap()
    fun showBackdrop(image: ImageId): Unit = showImage(image)
    fun showImage(image: ImageId): Unit = script.record(Op.View(ViewCommand.ShowImage(image)))
    fun statusLine(): Unit = script.record(Op.View(ViewCommand.StatusLine))
}

class NativeScope(private val script: ScriptScope) {
    // Native is deliberately narrow: rendering, keyboard input, inventory slot
    // plumbing, and helper calls whose implementation is genuinely engine-owned.
    // Game rules should become typed DSL verbs before they are allowed here.
    val screen: NativeScreen = NativeScreen(script)
    val inventory: NativeInventory = NativeInventory(script)
    val town: NativeTown = NativeTown(script)
    val input: NativeInput = NativeInput(script)
}

class NativeScreen(private val script: ScriptScope) {
    @Deprecated("Use view.clearText() or clearText(); rendering is no longer a native string request.")
    fun clearText(): Unit = script.record(Op.View(ViewCommand.ClearText))

    @Deprecated("Use view.clearMapWindow() or clearMapWindow(); rendering is no longer a native string request.")
    fun clearMapWindow(): Unit = script.record(Op.View(ViewCommand.ClearMapWindow))

    @Deprecated("Use view.renderMap() or renderMap(); rendering is no longer a native string request.")
    fun redrawMap(): Unit = script.record(Op.View(ViewCommand.RenderMap))

    @Deprecated("Use view.showBackdrop(...) or showBackdrop(...); rendering is no longer a native string request.")
    fun showImage(image: ImageId): Unit = script.record(Op.View(ViewCommand.ShowImage(image)))

    @Deprecated("Use view.statusLine() or statusLine(); rendering is no longer a native string request.")
    fun statusLine(): Unit = script.record(Op.View(ViewCommand.StatusLine))
}

class NativeInventory(private val script: ScriptScope) {
    fun add(itemId: IntExpr): Unit =
        script.record(Op.NativeCall(NativeAllowlist.inventoryAdd.path, listOf(render(itemId))))
}

class NativeTown(private val script: ScriptScope) {
    fun enterService(building: BuildingId): Unit =
        script.record(Op.NativeCall(NativeAllowlist.townEnterService.path, listOf(render(building))))
}

class NativeInput(private val script: ScriptScope) {
    fun keyUpper(): IntExpr = Expr(IntAst.NativeInt(NativeAllowlist.inputKeyUpper.path, emptyList()))
}

private fun recordScript(body: ScriptScope.() -> Unit): List<Op> =
    recordScript(ScriptContext(), body)

private fun recordScript(ctx: ScriptContext, body: ScriptScope.() -> Unit): List<Op> =
    buildList {
        ctx.recording(this) {
            ScriptScope(ctx, this).body()
        }
    }

data class ItemCatalogDeclaration(
    val records: List<ItemRecordModel>,
    val pools: List<ItemPoolId>,
)

@RylNextDsl
class ItemPoolBuilder {
    private val items = mutableListOf<ItemId>()

    fun item(id: ItemId) {
        items += id
    }

    fun items(vararg ids: ItemId) {
        items += ids
    }

    internal fun build(): List<ItemId> = items.toList()
}

@RylNextDsl
class CampProtectionBuilder {
    private val bands = mutableSetOf<TemperatureBand>()

    fun protects(vararg bands: TemperatureBand) {
        this.bands += bands
    }

    internal fun build(): Set<TemperatureBand> = bands.toSet()
}

@RylNextDsl
class ItemRecordBuilder {
    private var image: ImageId? = null
    private var protection: Set<TemperatureBand> = emptySet()
    private val capacityBonuses = mutableMapOf<Resource, Int>()

    fun campImage(image: ImageId) {
        this.image = image
    }

    fun campProtection(body: CampProtectionBuilder.() -> Unit) {
        protection = CampProtectionBuilder().apply(body).build()
    }

    fun resourceCapacityBonus(resource: Resource, amount: Int) {
        capacityBonuses[resource] = amount
    }

    internal fun campImageValue(): ImageId? = image
    internal fun campProtectionValue(): Set<TemperatureBand> = protection
    internal fun resourceCapacityBonusValue(): Map<Resource, Int> = capacityBonuses.toMap()
}

@RylNextDsl
class RealmContentBuilder {
    private val baseCapacity = mutableMapOf<Resource, Int>()
    private var upkeep = WildernessUpkeepRules()
    private var camp = CampRulesModel()
    private var hunt: HuntRulesModel? = null

    fun baseResourceCapacity(resource: Resource, amount: Int) {
        baseCapacity[resource] = amount
    }

    fun upkeep(body: WildernessUpkeepRulesBuilder.() -> Unit) {
        upkeep = WildernessUpkeepRulesBuilder(upkeep).apply(body).build()
    }

    fun camp(body: CampRulesBuilder.() -> Unit) {
        camp = CampRulesBuilder().apply(body).build()
    }

    fun hunt(body: HuntRulesBuilder.() -> Unit) {
        hunt = HuntRulesBuilder().apply(body).build()
    }

    internal fun build(): RealmContentModel =
        RealmContentModel(
            baseResourceCapacity = baseCapacity.toMap(),
            upkeep = upkeep,
            camp = camp,
            hunt = hunt,
        )
}

@RylNextDsl
class WildernessUpkeepRulesBuilder(initial: WildernessUpkeepRules = WildernessUpkeepRules()) {
    var foodCostLeaderDiv: Int = initial.foodCostLeaderDiv
    var foodTickEvery: Int = initial.foodTickEvery
    var starveFoodThreshold: Int = initial.starveFoodThreshold
    var starveDamage: Int = initial.starveDamage
    var manaRegenChance: Int = initial.manaRegenChance
    var restedHoursCostPerTurn: Int = initial.restedHoursCostPerTurn
    private val fatigue = initial.fatigue.toMutableList()

    fun fatigueAt(restedHours: Int, text: TextBlock, drain: Boolean = false) {
        fatigue += WildernessFatigueRule(
            threshold = restedHours,
            text = text,
            drain = drain,
        )
    }

    fun fatigueBelow(restedHours: Int, text: TextBlock, drain: Boolean = false) {
        fatigue += WildernessFatigueRule(
            threshold = restedHours,
            text = text,
            match = WildernessFatigueMatch.LessThan,
            drain = drain,
        )
    }

    internal fun build(): WildernessUpkeepRules =
        WildernessUpkeepRules(
            foodCostLeaderDiv = foodCostLeaderDiv,
            foodTickEvery = foodTickEvery,
            starveFoodThreshold = starveFoodThreshold,
            starveDamage = starveDamage,
            manaRegenChance = manaRegenChance,
            restedHoursCostPerTurn = restedHoursCostPerTurn,
            fatigue = fatigue.toList(),
        )
}

@RylNextDsl
class CampRulesBuilder {
    var healthGain: Int = 0
    var foodCostPerMember: Int = 0
    var hoursAdvanced: Int = 0
    var hoursRestedReset: Int = 0
    var temperatureMax: Int = 0
    var ambushChancePercent: Int = 0
    var ambushTier: EncounterTier? = null
    var notTired: TextBlock = inlineText("You are not tired.")
    var notEnoughFood: TextBlock = inlineText("Not enough food.")
    var guardPrompt: TextBlock = inlineText("Camp.", "", "Post a guard?", "")
    var guardMemberPrompt: TextBlock = inlineText("Who?")
    var guardMemberDead: TextBlock = inlineText("Dead.")
    var standsGuard: TextBlock = inlineText("Guard stands watch.")
    var sleeping: TextBlock = inlineText("Zzz...")
    var rested: TextBlock = inlineText("You feel rested.")
    var ambush: TextBlock = inlineText("Ambush!!!")
    var surpriseKill: TextBlock = inlineText("A camper is dead", "before you can react!")
    private val bands = mutableListOf<TemperatureBandRange>()
    private val damage = mutableMapOf<TemperatureBand, Int>()
    private val temperatureMessage = mutableMapOf<TemperatureBand, TextBlock>()
    private val noShelterMessage = mutableMapOf<TemperatureBand, TextBlock>()

    fun temperatureBand(
        band: TemperatureBand,
        range: IntRange,
        damage: Int,
        message: TextBlock,
        noShelter: TextBlock,
    ) {
        bands += TemperatureBandRange(band, range)
        this.damage[band] = damage
        temperatureMessage[band] = message
        noShelterMessage[band] = noShelter
    }

    internal fun build(): CampRulesModel = CampRulesModel(
        healthGain = healthGain,
        foodCostPerMember = foodCostPerMember,
        hoursAdvanced = hoursAdvanced,
        hoursRestedReset = hoursRestedReset,
        temperatureMax = temperatureMax,
        temperatureBands = bands.toList(),
        temperatureDamage = damage.toMap(),
        ambushChancePercent = ambushChancePercent,
        ambushTier = ambushTier,
        notTired = notTired,
        notEnoughFood = notEnoughFood,
        guardPrompt = guardPrompt,
        guardMemberPrompt = guardMemberPrompt,
        guardMemberDead = guardMemberDead,
        standsGuard = standsGuard,
        sleeping = sleeping,
        rested = rested,
        ambush = ambush,
        surpriseKill = surpriseKill,
        temperatureMessage = temperatureMessage.toMap(),
        noShelterMessage = noShelterMessage.toMap(),
    )
}

@RylNextDsl
class HuntRulesBuilder {
    var successChancePercent: Int = 0
    var timeCost: Int = 0
    var foodMin: Int = 0
    var foodMax: Int = 0
    var whoPrompt: TextBlock = structuredText { line("Who? (1-", HuntText.partyMemberMax, ")") }
    var dead: TextBlock = inlineText("Dead.")
    var notRanger: TextBlock = structuredText { line(HuntText.memberSubjectPronoun, " is not a ranger.") }
    var tooFewMoves: TextBlock = inlineText("Too few moves for that.")
    var tries: TextBlock = structuredText {
        line(HuntText.memberName, " tries")
        line("to trap a rabbit...")
    }
    var succeeds: TextBlock = structuredText {
        line("...and succeeds.")
        line("The party gains")
        line(HuntText.foodGained, " food rations.")
        line("")
    }
    var fails: TextBlock = inlineText("...but fails.", "")

    internal fun build(): HuntRulesModel =
        HuntRulesModel(
            successChancePercent = successChancePercent,
            timeCost = timeCost,
            foodMin = foodMin,
            foodMax = foodMax,
            whoPrompt = whoPrompt,
            dead = dead,
            notRanger = notRanger,
            tooFewMoves = tooFewMoves,
            tries = tries,
            succeeds = succeeds,
            fails = fails,
        )
}

@RylNextDsl
class ItemCatalogBuilder {
    private val records = mutableListOf<ItemRecordModel>()
    private val pools = mutableListOf<ItemPoolId>()

    fun empty(id: ItemId, name: String = "-", weaponDamage: Int = 1) {
        record(
            id = id,
            kind = ItemKind.Empty,
            name = name,
            cost = 0,
            storage = ItemStorageKind.EmptySentinel,
            weaponDamage = weaponDamage,
        )
    }

    fun weapon(id: ItemId, name: String, cost: Int, damage: Int) {
        record(id, ItemKind.Weapon, name, cost, weaponDamage = damage)
    }

    fun armor(id: ItemId, name: String, cost: Int, armorClass: Int) {
        record(id, ItemKind.Armor, name, cost, armorClass = armorClass)
    }

    fun gear(id: ItemId, name: String, cost: Int) {
        record(id, ItemKind.Gear, name, cost)
    }

    fun gear(id: ItemId, name: String, cost: Int, body: ItemRecordBuilder.() -> Unit) {
        customizable(id, ItemKind.Gear, name, cost, body)
    }

    fun potion(id: ItemId, name: String, cost: Int) {
        record(
            id = id,
            kind = ItemKind.Potion,
            name = name,
            cost = cost,
        )
    }

    fun treasure(id: ItemId, name: String, cost: Int) {
        record(id, ItemKind.Treasure, name, cost)
    }

    fun treasure(id: ItemId, name: String, cost: Int, body: ItemRecordBuilder.() -> Unit) {
        customizable(id, ItemKind.Treasure, name, cost, body)
    }

    private fun customizable(
        id: ItemId,
        kind: ItemKind,
        name: String,
        cost: Int,
        body: ItemRecordBuilder.() -> Unit,
    ) {
        val builder = ItemRecordBuilder().apply(body)
        record(
            id = id,
            kind = kind,
            name = name,
            cost = cost,
            campImage = builder.campImageValue(),
            campProtection = builder.campProtectionValue(),
            resourceCapacityBonus = builder.resourceCapacityBonusValue(),
        )
    }

    fun special(id: ItemId, name: String) {
        record(id, ItemKind.Special, name, cost = 0, storage = ItemStorageKind.SpecialMilestone)
    }

    fun magicItem(id: ItemId, name: String, cost: Int) {
        record(id, ItemKind.MagicItem, name, cost)
    }

    fun pool(id: ItemPoolId, vararg items: ItemId) {
        pool(id, items.toList())
    }

    fun pool(id: ItemPoolId, items: List<ItemId>) {
        pools += itemPool(id.slug, items)
    }

    fun pool(id: ItemPoolId, body: ItemPoolBuilder.() -> Unit) {
        pool(id, ItemPoolBuilder().apply(body).build())
    }

    private fun record(
        id: ItemId,
        kind: ItemKind,
        name: String,
        cost: Int,
        storage: ItemStorageKind = ItemStorageKind.SlottedInventory,
        weaponDamage: Int? = null,
        armorClass: Int? = null,
        campImage: ImageId? = null,
        campProtection: Set<TemperatureBand> = emptySet(),
        resourceCapacityBonus: Map<Resource, Int> = emptyMap(),
    ) {
        records += ItemRecordModel(
            id = id,
            kind = kind,
            displayName = name,
            cost = cost,
            storage = storage,
            weaponDamage = weaponDamage,
            armorClass = armorClass,
            campImage = campImage,
            campProtection = campProtection,
            resourceCapacityBonus = resourceCapacityBonus,
        )
    }

    internal fun build(): ItemCatalogDeclaration =
        ItemCatalogDeclaration(records.toList(), pools.toList())
}

@RylNextDsl
class TerminalPresentationBuilder {
    private val terrainStyles = mutableListOf<TerminalTerrainStyleModel>()
    private val screenRegions = mutableListOf<ScreenRegionModel>()

    fun terrain(id: TerrainId, mapCode: Char? = null, glyph: String, color: PaletteColorId) {
        mapCode?.let {
            require(id.sourceMapCode == null || id.sourceMapCode == it) {
                "terrain ${id.slug} map code ${id.sourceMapCode} does not match declared map code $it"
            }
        }
        terrainStyles += TerminalTerrainStyleModel(id, glyph, color)
    }

    fun region(
        id: ScreenRegionId,
        x: Int,
        y: Int,
        width: Int? = null,
        height: Int? = null,
    ) {
        screenRegions += ScreenRegionModel(id, x, y, width, height)
    }

    fun build(): TerminalPresentationModel =
        TerminalPresentationModel(
            terrain = terrainStyles.toList(),
            regions = screenRegions.toList(),
        )
}

private fun RewardBundleModel.resolveItemPools(resolve: (ItemPoolId) -> ItemPoolId): RewardBundleModel =
    copy(loot = loot.copy(treasure = resolve(loot.treasure), magic = resolve(loot.magic)))

private fun SceneModel.resolveItemPools(resolve: (ItemPoolId) -> ItemPoolId): SceneModel =
    copy(ops = ops.resolveItemPools(resolve))

private fun DialogueModel.resolveItemPools(resolve: (ItemPoolId) -> ItemPoolId): DialogueModel =
    copy(branches = branches.map { it.copy(ops = it.ops.resolveItemPools(resolve)) })

private fun SpeakSystemModel.resolveItemPools(resolve: (ItemPoolId) -> ItemPoolId): SpeakSystemModel =
    copy(questionActions = questionActions.map { it.copy(ops = it.ops.resolveItemPools(resolve)) })

private fun SpellModel.resolveItemPools(resolve: (ItemPoolId) -> ItemPoolId): SpellModel =
    copy(ops = ops.resolveItemPools(resolve))

private fun ItemUseModel.resolveItemPools(resolve: (ItemPoolId) -> ItemPoolId): ItemUseModel =
    copy(ops = ops.resolveItemPools(resolve))

private fun DungeonSystemModel.resolveItemPools(resolve: (ItemPoolId) -> ItemPoolId): DungeonSystemModel =
    copy(events = events.map { it.copy(ops = it.ops.resolveItemPools(resolve)) })

private fun List<Op>.resolveItemPools(resolve: (ItemPoolId) -> ItemPoolId): List<Op> =
    map { it.resolveItemPools(resolve) }

private fun List<SwitchCase>.resolveSwitchItemPools(resolve: (ItemPoolId) -> ItemPoolId): List<SwitchCase> =
    map { it.copy(body = it.body.resolveItemPools(resolve)) }

private fun List<MenuOption>.resolveMenuItemPools(resolve: (ItemPoolId) -> ItemPoolId): List<MenuOption> =
    map { it.copy(body = it.body.resolveItemPools(resolve)) }

private fun Op.resolveItemPools(resolve: (ItemPoolId) -> ItemPoolId): Op =
    when (this) {
        is Op.LetInt -> copy(value = value.resolveItemPools(resolve))
        is Op.Set -> copy(target = target.resolveItemPools(resolve), value = value.resolveItemPools(resolve))
        is Op.Add -> copy(target = target.resolveItemPools(resolve), value = value.resolveItemPools(resolve))
        is Op.Sub -> copy(target = target.resolveItemPools(resolve), value = value.resolveItemPools(resolve))
        is Op.AddClamped -> copy(
            target = target.resolveItemPools(resolve),
            value = value.resolveItemPools(resolve),
            max = max.resolveItemPools(resolve),
        )
        is Op.If -> copy(
            cond = cond.resolveItemPools(resolve),
            thenOps = thenOps.resolveItemPools(resolve),
            elseOps = elseOps.resolveItemPools(resolve),
        )
        is Op.Loop -> copy(body = body.resolveItemPools(resolve))
        is Op.Switch -> copy(expr = expr.resolveItemPools(resolve), cases = cases.resolveSwitchItemPools(resolve))
        is Op.Menu -> copy(
            options = options.resolveMenuItemPools(resolve),
            defaultOps = defaultOps.resolveItemPools(resolve),
        )
        is Op.AskKey -> copy()
        is Op.AskMember -> copy()
        is Op.AskInventorySlot -> copy()
        is Op.AskText -> copy(
            matchOps = matchOps.resolveItemPools(resolve),
            missOps = missOps.resolveItemPools(resolve),
        )
        is Op.ForEachMember -> copy(body = body.resolveItemPools(resolve))
        is Op.ForEachMonster -> copy(body = body.resolveItemPools(resolve))
        is Op.ConsumeInventoryPool -> copy(pool = resolve(pool), quantity = quantity.resolveItemPools(resolve))
        is Op.TeleportParty -> copy(
            directionKey = directionKey.resolveItemPools(resolve),
            distance = distance.resolveItemPools(resolve),
        )
        is Op.DiscoverCave -> copy(
            levels = levels.resolveItemPools(resolve),
            moveCost = moveCost.resolveItemPools(resolve),
        )
        is Op.GrantReward -> copy(reward = reward.resolveItemPools(resolve))
        is Op.RandomItemOffer -> copy(offer = offer.copy(pool = resolve(offer.pool)))
        else -> this
    }

private fun ScriptRewardModel.resolveItemPools(resolve: (ItemPoolId) -> ItemPoolId): ScriptRewardModel =
    copy(gold = gold?.resolveItemPools(resolve))

private fun LValueAst.resolveItemPools(resolve: (ItemPoolId) -> ItemPoolId): LValueAst =
    when (this) {
        is LValueAst.MemberField -> copy(member = member.resolveItemPools(resolve))
        is LValueAst.MonsterField -> copy(monster = monster.resolveItemPools(resolve))
        else -> this
    }

private fun IntAst.resolveItemPools(resolve: (ItemPoolId) -> ItemPoolId): IntAst =
    when (this) {
        is IntAst.Binary -> copy(left = left.resolveItemPools(resolve), right = right.resolveItemPools(resolve))
        is IntAst.If -> copy(
            cond = cond.resolveItemPools(resolve),
            thenValue = thenValue.resolveItemPools(resolve),
            elseValue = elseValue.resolveItemPools(resolve),
        )
        is IntAst.Random -> copy(lo = lo.resolveItemPools(resolve), hi = hi.resolveItemPools(resolve))
        is IntAst.MemberField -> copy(member = member.resolveItemPools(resolve))
        is IntAst.MonsterField -> copy(monster = monster.resolveItemPools(resolve))
        is IntAst.InventoryPoolCount -> copy(pool = resolve(pool))
        is IntAst.InventorySlotCost -> copy(slot = slot.resolveItemPools(resolve))
        else -> this
    }

private fun BoolAst.resolveItemPools(resolve: (ItemPoolId) -> ItemPoolId): BoolAst =
    when (this) {
        is BoolAst.Cmp -> copy(left = left.resolveItemPools(resolve), right = right.resolveItemPools(resolve))
        is BoolAst.Not -> copy(expr = expr.resolveItemPools(resolve))
        is BoolAst.And -> copy(left = left.resolveItemPools(resolve), right = right.resolveItemPools(resolve))
        is BoolAst.Or -> copy(left = left.resolveItemPools(resolve), right = right.resolveItemPools(resolve))
        is BoolAst.MemberFlag -> copy(member = member.resolveItemPools(resolve))
        is BoolAst.MonsterFlag -> copy(monster = monster.resolveItemPools(resolve))
        is BoolAst.RuntimeFlag -> this
        else -> this
    }

@RylNextDsl
class GameBuilder {
    private val itemRecords = mutableListOf<ItemRecordModel>()
    private val itemPools = mutableListOf<ItemPoolId>()
    private var realmModel = RealmContentModel()
    private var promptsModel = GamePromptsModel()
    private var visualsModel = VisualsModel()
    private val worldMaps = mutableListOf<WorldMapModel>()
    private var terminalPresentationModel = TerminalPresentationModel()
    private val authoredFlags = mutableListOf<FlagContentModel>()
    private var progressModel = GameProgressModel(emptyList(), null)
    private var levelingModel: LevelingModel? = null
    private val formulasModel = mutableListOf<FormulaModel>()
    private val rewardsModel = mutableListOf<RewardBundleModel>()
    private var combatModel = CombatSystemModel(null)
    private var wildernessModel = WildernessSystemModel()
    private var dungeonsModel = DungeonSystemModel()
    private var ydargModel: YdargContentModel? = null
    private var speakModel = SpeakSystemModel(null)
    private val towns = mutableListOf<TownModel>()
    private var townSystemModel: TownSystemModel? = null
    private val characters = mutableListOf<CharacterModel>()
    private val monsters = mutableListOf<MonsterModel>()
    private val scenes = mutableListOf<SceneModel>()
    private val dialogue = mutableListOf<DialogueModel>()
    private val spells = mutableListOf<SpellModel>()
    private val itemUses = mutableListOf<ItemUseModel>()
    private val missions = mutableListOf<MissionModel>()

    fun flags(body: FlagContentBuilder.() -> Unit) {
        authoredFlags += FlagContentBuilder().apply(body).build()
    }

    fun items(body: ItemCatalogBuilder.() -> Unit) {
        val declaration = ItemCatalogBuilder().apply(body).build()
        itemRecords += declaration.records
        itemPools += declaration.pools
    }

    fun realm(body: RealmContentBuilder.() -> Unit) {
        realmModel = RealmContentBuilder().apply(body).build()
    }

    fun prompts(body: GamePromptsBuilder.() -> Unit) {
        promptsModel = GamePromptsBuilder(promptsModel).apply(body).build()
    }

    fun visuals(body: VisualsBuilder.() -> Unit) {
        visualsModel = VisualsBuilder(visualsModel).apply(body).build()
    }

    fun worldMap(id: WorldMapId, body: WorldMapBuilder.() -> Unit) {
        worldMaps += WorldMapBuilder(id).apply(body).build()
    }

    fun terminal(body: TerminalPresentationBuilder.() -> Unit) {
        terminalPresentationModel = TerminalPresentationBuilder().apply(body).build()
    }

    fun gameProgress(body: GameProgressBuilder.() -> Unit) {
        progressModel = GameProgressBuilder().apply(body).build()
    }

    fun leveling(body: LevelingBuilder.() -> Unit) {
        levelingModel = LevelingBuilder().apply(body).build()
    }

    fun formulas(body: FormulaCatalogBuilder.() -> Unit) {
        formulasModel += FormulaCatalogBuilder().apply(body).build()
    }

    fun rewards(body: RewardCatalogBuilder.() -> Unit) {
        rewardsModel += RewardCatalogBuilder().apply(body).build()
    }

    fun combat(body: CombatBuilder.() -> Unit) {
        combatModel = CombatBuilder().apply(body).build()
    }

    fun wilderness(body: WildernessSystemBuilder.() -> Unit) {
        wildernessModel = WildernessSystemBuilder().apply(body).build()
    }

    fun dungeons(body: DungeonSystemBuilder.() -> Unit) {
        dungeonsModel = DungeonSystemBuilder().apply(body).build()
    }

    fun ydarg(body: YdargBuilder.() -> Unit) {
        ydargModel = YdargBuilder().apply(body).build()
    }

    fun speak(body: SpeakBuilder.() -> Unit) {
        speakModel = SpeakBuilder().apply(body).build()
    }

    fun towns(body: TownCatalogBuilder.() -> Unit) {
        towns += TownCatalogBuilder().apply(body).build()
    }

    fun townSystem(body: TownSystemBuilder.() -> Unit) {
        townSystemModel = TownSystemBuilder().apply(body).build()
    }

    fun characters(body: CharacterCatalogBuilder.() -> Unit) {
        characters += CharacterCatalogBuilder().apply(body).build()
    }

    fun monsters(body: MonsterCatalogBuilder.() -> Unit) {
        monsters += MonsterCatalogBuilder().apply(body).build()
    }

    fun missions(body: MissionCatalogBuilder.() -> Unit) {
        missions += MissionCatalogBuilder().apply(body).build()
    }

    fun scene(id: SceneId, body: ScriptScope.() -> Unit) {
        scenes += SceneModel(id, recordScript(body))
    }

    fun dialogue(body: DialogueCatalogBuilder.() -> Unit) {
        dialogue += DialogueCatalogBuilder().apply(body).build()
    }

    fun spell(id: SpellId, slot: Int, baseCost: Int, name: String, body: ScriptScope.() -> Unit) {
        spells += SpellModel(id, slot, baseCost, name, recordScript(body))
    }

    fun itemUse(id: ItemId, body: ItemUseBuilder.() -> Unit) {
        itemUses += ItemUseBuilder(id).apply(body).build()
    }

    fun build(): GameModel {
        val itemPoolBySlug = itemPools.associateBy { it.slug }
        fun resolveItemPool(pool: ItemPoolId): ItemPoolId =
            itemPoolBySlug[pool.slug] ?: pool

        return GameModel(
            itemCatalog = itemRecords.toList(),
            itemPools = itemPools.toList(),
            realm = realmModel,
            prompts = promptsModel,
            visuals = visualsModel,
            worldMaps = worldMaps.toList(),
            terminal = terminalPresentationModel,
            flags = declaredFlags.toList(),
            authoredFlags = authoredFlags.toList(),
            progress = progressModel,
            leveling = levelingModel,
            formulas = formulasModel.toList(),
            rewards = rewardsModel.map { it.resolveItemPools(::resolveItemPool) },
            combat = combatModel,
            wilderness = wildernessModel,
            dungeons = dungeonsModel.resolveItemPools(::resolveItemPool),
            ydarg = ydargModel,
            speak = speakModel.resolveItemPools(::resolveItemPool),
            towns = towns.toList(),
            townSystem = townSystemModel,
            characters = characters.toList(),
            monsters = monsters.toList(),
            scenes = scenes.map { it.resolveItemPools(::resolveItemPool) },
            dialogue = dialogue.map { it.resolveItemPools(::resolveItemPool) },
            spells = spells.map { it.resolveItemPools(::resolveItemPool) },
            itemUses = itemUses.map { it.resolveItemPools(::resolveItemPool) },
            missions = missions.toList(),
        )
    }

}

@RylNextDsl
class WorldMapBuilder(private val id: WorldMapId) {
    private val layerModels = mutableListOf<WorldMapLayerModel>()

    fun layer(id: MapLayerId, body: WorldMapLayerBuilder.() -> Unit) {
        layerModels += WorldMapLayerBuilder(id).apply(body).build()
    }

    fun build(): WorldMapModel =
        WorldMapModel(id = id, layers = layerModels.toList())
}

@RylNextDsl
class WorldMapLayerBuilder(private val id: MapLayerId) {
    private var rowModels = emptyList<String>()

    fun rows(vararg rows: String) {
        rowModels = rows.toList()
    }

    fun rows(rows: List<String>) {
        rowModels = rows.toList()
    }

    fun build(): WorldMapLayerModel =
        WorldMapLayerModel(id = id, rows = rowModels)
}

@RylNextDsl
class VisualsBuilder(initial: VisualsModel = VisualsModel()) {
    private val staticScreenModels = initial.staticScreens.toMutableList()

    fun staticScreens(body: StaticScreensBuilder.() -> Unit) {
        staticScreenModels += StaticScreensBuilder().apply(body).build()
    }

    fun build(): VisualsModel =
        VisualsModel(staticScreens = staticScreenModels.toList())
}

@RylNextDsl
class StaticScreensBuilder {
    private val screens = mutableListOf<StaticScreenModel>()

    fun screen(id: StaticScreenId, body: StaticScreenBuilder.() -> Unit) {
        screens += StaticScreenBuilder(id).apply(body).build()
    }

    fun image(id: StaticScreenId, staticImage: ImageId) {
        screen(id) { image(staticImage) }
    }

    fun resource(id: StaticScreenId, path: String) {
        screen(id) { resource(path) }
    }

    fun commandHelp(id: StaticScreenId) {
        screen(id) { commandHelp() }
    }

    fun build(): List<StaticScreenModel> = screens.toList()
}

@RylNextDsl
class StaticScreenBuilder(private val id: StaticScreenId) {
    private var imageModel: ImageId? = null
    private var resourcePath: String? = null
    private var usesCommandHelp = false

    fun image(image: ImageId) {
        imageModel = image
    }

    fun resource(path: String) {
        resourcePath = path
    }

    fun commandHelp() {
        usesCommandHelp = true
    }

    fun build(): StaticScreenModel =
        StaticScreenModel(
            id = id,
            image = imageModel,
            resource = resourcePath,
            commandHelp = usesCommandHelp,
        )
}

@RylNextDsl
class GamePromptsBuilder(initial: GamePromptsModel = GamePromptsModel()) {
    private var pauseVerbList = initial.pauseVerbs
    var returnKeyName: String = initial.returnKeyName
    private var priest = initial.priest
    private var inventoryUse = initial.inventoryUse
    private var equipment = initial.equipment
    private var save = initial.save
    private var town = initial.town
    private var wilderness = initial.wilderness
    private var dungeon = initial.dungeon
    private var party = initial.party

    fun pauseVerbs(vararg verbs: String) {
        pauseVerbList = verbs.toList()
    }

    fun pauseVerbs(verbs: List<String>) {
        pauseVerbList = verbs.toList()
    }

    fun priest(body: PriestSpellPromptsBuilder.() -> Unit) {
        priest = PriestSpellPromptsBuilder(priest).apply(body).build()
    }

    fun inventoryUse(body: InventoryUsePromptsBuilder.() -> Unit) {
        inventoryUse = InventoryUsePromptsBuilder(inventoryUse).apply(body).build()
    }

    fun equipment(body: EquipmentPromptsBuilder.() -> Unit) {
        equipment = EquipmentPromptsBuilder(equipment).apply(body).build()
    }

    fun save(body: SavePromptsBuilder.() -> Unit) {
        save = SavePromptsBuilder(save).apply(body).build()
    }

    fun town(body: TownPromptsBuilder.() -> Unit) {
        town = TownPromptsBuilder(town).apply(body).build()
    }

    fun wilderness(body: WildernessPromptsBuilder.() -> Unit) {
        wilderness = WildernessPromptsBuilder(wilderness).apply(body).build()
    }

    fun dungeon(body: DungeonPromptsBuilder.() -> Unit) {
        dungeon = DungeonPromptsBuilder(dungeon).apply(body).build()
    }

    fun party(body: PartyPromptsBuilder.() -> Unit) {
        party = PartyPromptsBuilder(party).apply(body).build()
    }

    fun build(): GamePromptsModel =
        GamePromptsModel(
            pauseVerbs = pauseVerbList,
            returnKeyName = returnKeyName,
            priest = priest,
            inventoryUse = inventoryUse,
            equipment = equipment,
            save = save,
            town = town,
            wilderness = wilderness,
            dungeon = dungeon,
            party = party,
        )
}

@RylNextDsl
class PriestSpellPromptsBuilder(initial: PriestSpellPromptsModel = PriestSpellPromptsModel()) {
    var who: TextBlock = initial.who
    var mageCombatOnly: TextBlock = initial.mageCombatOnly
    var wrongClass: TextBlock = initial.wrongClass
    var dead: TextBlock = initial.dead
    var invalidCaster: TextBlock = initial.invalidCaster
    var invalidSpell: TextBlock = initial.invalidSpell
    var noSpells: TextBlock = initial.noSpells

    fun build(): PriestSpellPromptsModel =
        PriestSpellPromptsModel(
            who = who,
            mageCombatOnly = mageCombatOnly,
            wrongClass = wrongClass,
            dead = dead,
            invalidCaster = invalidCaster,
            invalidSpell = invalidSpell,
            noSpells = noSpells,
        )
}

@RylNextDsl
class InventoryUsePromptsBuilder(initial: InventoryUsePromptsModel = InventoryUsePromptsModel()) {
    var use: TextBlock = initial.use
    var quaff: TextBlock = initial.quaff
    var noItems: TextBlock = initial.noItems
    var noItem: TextBlock = initial.noItem
    var invalidSlotFirstPrefix: String = initial.invalidSlotFirstPrefix
    var invalidSlotFirstSuffix: String = initial.invalidSlotFirstSuffix
    var invalidSlotSecond: String = initial.invalidSlotSecond
    var notUsableFirst: String = initial.notUsableFirst
    var notUsableSecondPrefix: String = initial.notUsableSecondPrefix
    var notUsableSecondSuffix: String = initial.notUsableSecondSuffix

    fun build(): InventoryUsePromptsModel =
        InventoryUsePromptsModel(
            use = use,
            quaff = quaff,
            noItems = noItems,
            noItem = noItem,
            invalidSlotFirstPrefix = invalidSlotFirstPrefix,
            invalidSlotFirstSuffix = invalidSlotFirstSuffix,
            invalidSlotSecond = invalidSlotSecond,
            notUsableFirst = notUsableFirst,
            notUsableSecondPrefix = notUsableSecondPrefix,
            notUsableSecondSuffix = notUsableSecondSuffix,
        )
}

@RylNextDsl
class EquipmentPromptsBuilder(initial: EquipmentPromptsModel = EquipmentPromptsModel()) {
    var inventory: TextBlock = initial.inventory
    var who: TextBlock = initial.who
    var readyWeapons: TextBlock = initial.readyWeapons
    var readyArmors: TextBlock = initial.readyArmors

    fun build(): EquipmentPromptsModel =
        EquipmentPromptsModel(
            inventory = inventory,
            who = who,
            readyWeapons = readyWeapons,
            readyArmors = readyArmors,
        )
}

@RylNextDsl
class SavePromptsBuilder(initial: SavePromptsModel = SavePromptsModel()) {
    var saving: TextBlock = initial.saving
    var done: TextBlock = initial.done
    var failed: TextBlock = initial.failed

    fun build(): SavePromptsModel =
        SavePromptsModel(
            saving = saving,
            done = done,
            failed = failed,
        )
}

@RylNextDsl
class TownPromptsBuilder(initial: TownPromptsModel = TownPromptsModel()) {
    var unavailableSuffix: String = initial.unavailableSuffix
    var closedSuffix: String = initial.closedSuffix

    fun build(): TownPromptsModel =
        TownPromptsModel(
            unavailableSuffix = unavailableSuffix,
            closedSuffix = closedSuffix,
        )
}

@RylNextDsl
class PartyPromptsBuilder(initial: PartyPromptsModel = PartyPromptsModel()) {
    var viewWho: TextBlock = initial.viewWho
    var journalHeader: TextBlock = initial.journalHeader
    var dropPrompt: TextBlock = initial.dropPrompt
    var dropEmptySlot: TextBlock = initial.dropEmptySlot
    var dropAborted: TextBlock = initial.dropAborted
    private var dropDroppedText: TextBlock = initial.dropDropped
    var dropCapacityReduced: TextBlock = initial.dropCapacityReduced

    fun dropped(body: SelectedItemPromptBuilder.() -> Unit) {
        dropDroppedText = SelectedItemPromptBuilder().apply(body).build()
    }

    fun build(): PartyPromptsModel =
        PartyPromptsModel(
            viewWho = viewWho,
            journalHeader = journalHeader,
            dropPrompt = dropPrompt,
            dropEmptySlot = dropEmptySlot,
            dropAborted = dropAborted,
            dropDropped = dropDroppedText,
            dropCapacityReduced = dropCapacityReduced,
        )
}

@RylNextDsl
class WildernessPromptsBuilder(initial: WildernessPromptsModel = WildernessPromptsModel()) {
    var pass: TextBlock = initial.pass
    var noMoves: TextBlock = initial.noMoves
    var outOfMoves: TextBlock = initial.outOfMoves
    var notInWilderness: TextBlock = initial.notInWilderness
    var nothingToEnter: TextBlock = initial.nothingToEnter
    var quitConfirm: TextBlock = initial.quitConfirm
    private var dungeonHandoffText: TextBlock = initial.dungeonHandoff

    fun dungeonHandoff(body: WildernessDungeonHandoffPromptBuilder.() -> Unit) {
        dungeonHandoffText = WildernessDungeonHandoffPromptBuilder().apply(body).build()
    }

    fun build(): WildernessPromptsModel =
        WildernessPromptsModel(
            pass = pass,
            noMoves = noMoves,
            outOfMoves = outOfMoves,
            notInWilderness = notInWilderness,
            nothingToEnter = nothingToEnter,
            quitConfirm = quitConfirm,
            dungeonHandoff = dungeonHandoffText,
        )
}

@RylNextDsl
class DungeonPromptsBuilder(initial: DungeonPromptsModel = DungeonPromptsModel()) {
    private var levelMenuText: TextBlock = initial.levelMenu
    private var enterLevelText: TextBlock = initial.enterLevel
    var atMaxDepth: TextBlock = initial.atMaxDepth
    var noTorches: TextBlock = initial.noTorches
    var leaveCavern: TextBlock = initial.leaveCavern

    fun levelMenu(body: DungeonLevelPromptBuilder.() -> Unit) {
        levelMenuText = DungeonLevelPromptBuilder().apply(body).build()
    }

    fun enterLevel(body: DungeonLevelPromptBuilder.() -> Unit) {
        enterLevelText = DungeonLevelPromptBuilder().apply(body).build()
    }

    fun build(): DungeonPromptsModel =
        DungeonPromptsModel(
            levelMenu = levelMenuText,
            enterLevel = enterLevelText,
            atMaxDepth = atMaxDepth,
            noTorches = noTorches,
            leaveCavern = leaveCavern,
        )
}

@RylNextDsl
class WildernessDungeonHandoffPromptBuilder : TextLineSink {
    private val text = StructuredTextBlockBuilder()

    val dungeonName: TextPart = TextPart.WildernessDungeonName
    val levels: TextPart = TextPart.WildernessDungeonLevels

    override fun addTextLine(line: TextLine) {
        text.addTextLine(line)
    }

    fun build(): TextBlock = text.build()
}

@RylNextDsl
class DungeonLevelPromptBuilder : TextLineSink {
    private val text = StructuredTextBlockBuilder()

    val level: TextPart = TextPart.DungeonDepth

    override fun addTextLine(line: TextLine) {
        text.addTextLine(line)
    }

    fun build(): TextBlock = text.build()
}

@RylNextDsl
class SelectedItemPromptBuilder : TextLineSink {
    private val text = StructuredTextBlockBuilder()

    val itemName: TextPart = TextPart.SelectedItemName

    override fun addTextLine(line: TextLine) {
        text.addTextLine(line)
    }

    fun build(): TextBlock = text.build()
}

@RylNextDsl
class WildernessSystemBuilder {
    private var keysModel = WildernessKeysBuilder().build()
    private var eventsModel = WildernessEventBuilder().build()
    private var terrainTransitionModel = TerrainTransitionBuilder().build()

    fun keys(body: WildernessKeysBuilder.() -> Unit) {
        keysModel = WildernessKeysBuilder(keysModel).apply(body).build()
    }

    fun events(body: WildernessEventBuilder.() -> Unit) {
        eventsModel = WildernessEventBuilder().apply(body).build()
    }

    fun terrainTransitions(body: TerrainTransitionBuilder.() -> Unit) {
        terrainTransitionModel = TerrainTransitionBuilder().apply(body).build()
    }

    fun build(): WildernessSystemModel =
        WildernessSystemModel(
            keys = keysModel,
            events = eventsModel,
            terrainTransitions = terrainTransitionModel,
        )
}

@RylNextDsl
class WildernessKeysBuilder(initial: WildernessKeysModel = WildernessKeysModel()) {
    private var moveNorthKey = initial.moveNorth
    private var moveSouthKey = initial.moveSouth
    private var moveWestKey = initial.moveWest
    private var moveEastKey = initial.moveEast
    var pass: InputBindingModel = initial.pass
    var save: InputBindingModel = initial.save
    var readyWeapon: InputBindingModel = initial.readyWeapon
    var readyArmor: InputBindingModel = initial.readyArmor
    var redraw: InputBindingModel = initial.redraw
    var journal: InputBindingModel = initial.journal
    var help: InputBindingModel = initial.help
    var hunt: InputBindingModel = initial.hunt
    var viewParty: InputBindingModel = initial.viewParty
    var removeArmor: InputBindingModel = initial.removeArmor
    var removeWeapon: InputBindingModel = initial.removeWeapon
    var enter: InputBindingModel = initial.enter
    var drop: InputBindingModel = initial.drop
    var camp: InputBindingModel = initial.camp
    var castSpell: InputBindingModel = initial.castSpell
    var quaff: InputBindingModel = initial.quaff
    var quit: InputBindingModel = initial.quit
    private var caveEnterKey = initial.caveEnter
    private var caveLeaveKey = initial.caveLeave
    private var outOfMovesSaveKey = initial.outOfMovesSave
    private var outOfMovesQuitKey = initial.outOfMovesQuit
    private var quitYesKey = initial.quitYes
    private var quitNoKey = initial.quitNo
    private var commandHintText = initial.commandHint
    private var helpPageText = initial.helpPages

    fun movement(
        north: InputBindingModel = moveNorthKey,
        south: InputBindingModel = moveSouthKey,
        west: InputBindingModel = moveWestKey,
        east: InputBindingModel = moveEastKey,
    ) {
        moveNorthKey = north
        moveSouthKey = south
        moveWestKey = west
        moveEastKey = east
    }

    fun movement(north: Char, south: Char, west: Char, east: Char) {
        movement(inputKey(north), inputKey(south), inputKey(west), inputKey(east))
    }

    fun numberPadMovementWithArrows() {
        movement(
            north = inputKey("8", InputAliases.ArrowUp, "UP", "ARROW_UP", label = "8/Up"),
            south = inputKey("2", InputAliases.ArrowDown, "DOWN", "ARROW_DOWN", label = "2/Down"),
            west = inputKey("4", InputAliases.ArrowLeft, "LEFT", "ARROW_LEFT", label = "4/Left"),
            east = inputKey("6", InputAliases.ArrowRight, "RIGHT", "ARROW_RIGHT", label = "6/Right"),
        )
    }

    fun cave(enter: InputBindingModel = caveEnterKey, leave: InputBindingModel = caveLeaveKey) {
        caveEnterKey = enter
        caveLeaveKey = leave
    }

    fun cave(enter: Char, leave: Char) {
        cave(inputKey(enter), inputKey(leave))
    }

    fun outOfMoves(save: InputBindingModel = outOfMovesSaveKey, quit: InputBindingModel = outOfMovesQuitKey) {
        outOfMovesSaveKey = save
        outOfMovesQuitKey = quit
    }

    fun outOfMoves(save: Char, quit: Char) {
        outOfMoves(inputKey(save), inputKey(quit))
    }

    fun quitConfirm(yes: InputBindingModel = quitYesKey, no: InputBindingModel = quitNoKey) {
        quitYesKey = yes
        quitNoKey = no
    }

    fun quitConfirm(yes: Char, no: Char) {
        quitConfirm(inputKey(yes), inputKey(no))
    }

    fun commandHint(vararg lines: String) {
        commandHintText = inlineText(*lines)
    }

    fun commandHint(body: StructuredTextBlockBuilder.() -> Unit) {
        commandHintText = structuredText(body)
    }

    fun helpPages(body: WildernessHelpPagesBuilder.() -> Unit) {
        helpPageText = WildernessHelpPagesBuilder().apply(body).build()
    }

    fun build(): WildernessKeysModel =
        WildernessKeysModel(
            moveNorth = moveNorthKey,
            moveSouth = moveSouthKey,
            moveWest = moveWestKey,
            moveEast = moveEastKey,
            pass = pass,
            save = save,
            readyWeapon = readyWeapon,
            readyArmor = readyArmor,
            redraw = redraw,
            journal = journal,
            help = help,
            hunt = hunt,
            viewParty = viewParty,
            removeArmor = removeArmor,
            removeWeapon = removeWeapon,
            enter = enter,
            drop = drop,
            camp = camp,
            castSpell = castSpell,
            quaff = quaff,
            quit = quit,
            caveEnter = caveEnterKey,
            caveLeave = caveLeaveKey,
            outOfMovesSave = outOfMovesSaveKey,
            outOfMovesQuit = outOfMovesQuitKey,
            quitYes = quitYesKey,
            quitNo = quitNoKey,
            commandHint = commandHintText,
            helpPages = helpPageText,
        )
}

@RylNextDsl
class WildernessHelpPagesBuilder {
    private val pages = mutableListOf<TextBlock>()

    fun page(vararg lines: String) {
        pages += inlineText(*lines)
    }

    fun page(body: StructuredTextBlockBuilder.() -> Unit) {
        pages += structuredText(body)
    }

    fun build(): List<TextBlock> = pages.toList()
}

@RylNextDsl
class DungeonSystemBuilder {
    private val events = mutableListOf<DungeonEventModel>()
    private val scenarios = mutableListOf<DungeonScenarioModel>()
    private var backdrop: ImageId? = null

    fun backdrop(image: ImageId?) {
        backdrop = image
    }

    fun event(id: DungeonEventId, body: DungeonEventBuilder.() -> Unit) {
        events += DungeonEventBuilder(id).apply(body).build()
    }

    fun scenario(dungeon: DungeonId, body: DungeonScenarioBuilder.() -> Unit) {
        scenarios += DungeonScenarioBuilder(dungeon).apply(body).build()
    }

    fun build(): DungeonSystemModel =
        DungeonSystemModel(events.toList(), scenarios.toList(), backdrop)
}

@RylNextDsl
class DungeonEventBuilder(private val id: DungeonEventId) {
    private var disposition = DungeonEventDisposition.ClearAfterRun
    private var randomEncounterTier: EncounterTier? = null
    private val ops = mutableListOf<Op>()

    fun recurring() {
        disposition = DungeonEventDisposition.Recurring
    }

    fun clearsAfterRun() {
        disposition = DungeonEventDisposition.ClearAfterRun
    }

    fun randomEncounter(tier: EncounterTier) {
        randomEncounterTier = tier
    }

    fun run(body: ScriptScope.() -> Unit) {
        ops += recordScript(body)
    }

    fun build(): DungeonEventModel =
        DungeonEventModel(
            id = id,
            disposition = disposition,
            ops = ops.toList(),
            randomEncounterTier = randomEncounterTier,
        )
}

@RylNextDsl
class DungeonScenarioBuilder(private val dungeon: DungeonId) {
    private var name: String? = null
    private var levelRange: IntRange? = null
    private val fixedLevelEvents = linkedMapOf<Int, DungeonEventId>()
    private val randomEvents = mutableListOf<DungeonRandomEventModel>()

    fun name(value: String) {
        name = value
    }

    fun levels(count: Int) {
        levelRange = count..count
    }

    fun randomLevels(range: IntRange) {
        levelRange = range
    }

    fun level(number: Int, event: DungeonEventId) {
        fixedLevelEvents[number] = event
    }

    fun randomEvents(body: DungeonRandomEventPoolBuilder.() -> Unit) {
        randomEvents += DungeonRandomEventPoolBuilder().apply(body).build()
    }

    fun build(): DungeonScenarioModel =
        DungeonScenarioModel(
            dungeon = dungeon,
            name = name,
            levelRange = levelRange,
            fixedLevelEvents = fixedLevelEvents.toMap(),
            randomEvents = randomEvents.toList(),
        )
}

@RylNextDsl
class DungeonRandomEventPoolBuilder {
    private val events = mutableListOf<DungeonRandomEventModel>()

    fun event(event: DungeonEventId, weight: Int = 1) {
        events += DungeonRandomEventModel(event, weight)
    }

    fun build(): List<DungeonRandomEventModel> = events.toList()
}

@RylNextDsl
class WildernessEventBuilder {
    var caveChancePercent: Int = 3
    var quietChancePercent: Int = 91
    var encounterSplitPercent: Int = 50
    var easyEncounterTier: EncounterTier = DslDefaultEncounterTiers.weak
    var hardEncounterTier: EncounterTier = DslDefaultEncounterTiers.wild
    var caveDungeon: DungeonId = DslDefaultDungeons.randomCave
    var caveDepthRange: IntRange = 3..16
    var caveEntryCost: Int = 10
    var caveImage: ImageId? = null
    private var caveFoundPromptText: TextBlock = inlineText("You find a cave!", "", "(E)nter", "(L)eave")
    private var caveNoMovesText: TextBlock = inlineText("You don't have enough", "moves left to explore", "the cave...", "")

    fun caveFoundPrompt(vararg lines: String) {
        caveFoundPromptText = inlineText(*lines)
    }

    fun caveFoundPrompt(body: StructuredTextBlockBuilder.() -> Unit) {
        caveFoundPromptText = StructuredTextBlockBuilder().apply(body).build()
    }

    fun caveNoMoves(vararg lines: String) {
        caveNoMovesText = inlineText(*lines)
    }

    fun caveNoMoves(body: StructuredTextBlockBuilder.() -> Unit) {
        caveNoMovesText = StructuredTextBlockBuilder().apply(body).build()
    }

    fun build(): WildernessEventModel =
        WildernessEventModel(
            caveChancePercent = caveChancePercent,
            quietChancePercent = quietChancePercent,
            encounterSplitPercent = encounterSplitPercent,
            easyEncounterTier = easyEncounterTier,
            hardEncounterTier = hardEncounterTier,
            caveDungeon = caveDungeon,
            caveDepthRange = caveDepthRange,
            caveEntryCost = caveEntryCost,
            caveImage = caveImage,
            caveFoundPrompt = caveFoundPromptText,
            caveNoMoves = caveNoMovesText,
        )
}

@RylNextDsl
class TerrainTransitionBuilder {
    var chancePercent: Int = 9
    private val entries = mutableListOf<TerrainTransitionTextModel>()

    fun terrain(id: TerrainId, vararg lines: String) {
        entries += TerrainTransitionTextModel(id, inlineText(*lines))
    }

    fun terrain(id: TerrainId, body: StructuredTextBlockBuilder.() -> Unit) {
        entries += TerrainTransitionTextModel(id, StructuredTextBlockBuilder().apply(body).build())
    }

    fun build(): TerrainTransitionModel =
        TerrainTransitionModel(chancePercent = chancePercent, entries = entries.toList())
}

@RylNextDsl
class DialogueCatalogBuilder {
    private val dialogue = mutableListOf<DialogueModel>()

    fun npc(id: NpcId, body: NpcDialogueBuilder.() -> Unit) {
        dialogue += NpcDialogueBuilder(id).apply(body).build()
    }

    fun build(): List<DialogueModel> = dialogue.toList()
}

@RylNextDsl
class NpcDialogueBuilder(private val id: NpcId) {
    private val branches = mutableListOf<DialogueBranchModel>()

    fun whenState(state: FlagState, body: ScriptScope.() -> Unit) {
        branches += DialogueBranchModel(state, recordScript(body))
    }

    fun default(body: ScriptScope.() -> Unit) {
        branches += DialogueBranchModel(null, recordScript(body))
    }

    fun build(): DialogueModel = DialogueModel(id, branches.toList())
}

@RylNextDsl
class TownCatalogBuilder {
    private val towns = mutableListOf<TownModel>()

    fun town(slug: String, body: TownBuilder.() -> Unit) {
        towns += TownBuilder(town(slug)).apply(body).build()
    }

    fun town(ref: TownHandle, body: TownBuilder.() -> Unit) {
        towns += TownBuilder(ref.id).apply(body).build()
    }

    fun build(): List<TownModel> = towns.toList()
}

@RylNextDsl
class TownBuilder(private val id: TownId) {
    var name: String = ""
    var position: GridPoint = xy(0, 0)
    var taxPercent: Int = 0
    private var kindModel: TownKind = TownKind.Village
    private val serviceModels = mutableListOf<TownServiceModel>()

    fun village() {
        kindModel = TownKind.Village
    }

    fun city() {
        kindModel = TownKind.City
    }

    fun services(body: TownServicesBuilder.() -> Unit) {
        serviceModels += TownServicesBuilder().apply(body).build()
    }

    fun build(): TownModel =
        TownModel(id, name, position, taxPercent, kindModel, serviceModels.toList())
}

@RylNextDsl
class TownServicesBuilder {
    private val services = mutableListOf<TownServiceModel>()

    fun merchant() {
        service(DslDefaultBuildings.merchant)
    }

    fun inn() {
        service(DslDefaultBuildings.inn)
    }

    fun tavern() {
        service(DslDefaultBuildings.tavern)
    }

    fun smith() {
        service(DslDefaultBuildings.smith)
    }

    fun church() {
        service(DslDefaultBuildings.church)
    }

    fun service(building: BuildingId) {
        services += TownServiceModel(building)
    }

    fun build(): List<TownServiceModel> = services.toList()
}

@RylNextDsl
class TownSystemBuilder {
    private var menuModel = TownMenuBuilder().build()
    private var buildingServiceModels = BuildingServiceCatalogBuilder().build()
    private var leavePromptCommands: List<EquipmentCommand> = emptyList()

    fun menu(body: TownMenuBuilder.() -> Unit) {
        menuModel = TownMenuBuilder().apply(body).build()
    }

    fun leavePrompts(vararg commands: EquipmentCommand) {
        leavePrompts(commands.toList())
    }

    fun leavePrompts(commands: List<EquipmentCommand>) {
        leavePromptCommands = commands.toList()
    }

    fun buildingServices(body: BuildingServiceCatalogBuilder.() -> Unit) {
        buildingServiceModels = BuildingServiceCatalogBuilder().apply(body).build()
    }

    fun build(): TownSystemModel =
        TownSystemModel(
            menu = menuModel,
            buildingServices = buildingServiceModels,
            leavePrompts = leavePromptCommands,
            nativeServices = listOf(
                EngineServiceUse(EngineService.KeyboardInput, "town menu command selection"),
                EngineServiceUse(EngineService.Rendering, "town entry art, text panes, and status refresh"),
                EngineServiceUse(EngineService.TownStateStorage, "current town lookup and available-service checks"),
                EngineServiceUse(EngineService.TimeState, "service opening-hour checks"),
                EngineServiceUse(EngineService.PartyStateMutation, "tax, service costs, lodging, healing, and time passage"),
                EngineServiceUse(EngineService.InventoryStorage, "merchant, smith, and service inventory effects"),
                EngineServiceUse(EngineService.NpcStateStorage, "building NPC lookup and town conversations"),
                EngineServiceUse(EngineService.TextFormatting, "town, building, NPC, item, price, and tax bindings"),
            ),
        )
}

@RylNextDsl
class TownMenuBuilder {
    private val actions = mutableListOf<TownMenuActionModel>()

    fun church(key: Char = 'C') {
        service(inputKey(key), DslDefaultBuildings.church, DslDefaultKeyActions.enterChurch)
    }

    fun church(binding: InputBindingModel) {
        service(binding, DslDefaultBuildings.church, DslDefaultKeyActions.enterChurch)
    }

    fun smith(key: Char = 'S') {
        service(inputKey(key), DslDefaultBuildings.smith, DslDefaultKeyActions.enterSmith)
    }

    fun smith(binding: InputBindingModel) {
        service(binding, DslDefaultBuildings.smith, DslDefaultKeyActions.enterSmith)
    }

    fun inn(key: Char = 'I') {
        service(inputKey(key), DslDefaultBuildings.inn, DslDefaultKeyActions.enterInn)
    }

    fun inn(binding: InputBindingModel) {
        service(binding, DslDefaultBuildings.inn, DslDefaultKeyActions.enterInn)
    }

    fun merchant(key: Char = 'M') {
        service(inputKey(key), DslDefaultBuildings.merchant, DslDefaultKeyActions.enterMerchant)
    }

    fun merchant(binding: InputBindingModel) {
        service(binding, DslDefaultBuildings.merchant, DslDefaultKeyActions.enterMerchant)
    }

    fun tavern(key: Char = 'T') {
        service(inputKey(key), DslDefaultBuildings.tavern, DslDefaultKeyActions.enterTavern)
    }

    fun tavern(binding: InputBindingModel) {
        service(binding, DslDefaultBuildings.tavern, DslDefaultKeyActions.enterTavern)
    }

    fun leave(key: Char = 'L', action: KeyActionId = DslDefaultKeyActions.leaveTown) {
        leave(inputKey(key), action)
    }

    fun leave(binding: InputBindingModel, action: KeyActionId = DslDefaultKeyActions.leaveTown) {
        actions += TownMenuActionModel(binding, action, building = null, endsMenu = true)
    }

    fun service(key: Char, building: BuildingId, action: KeyActionId) {
        service(inputKey(key), building, action)
    }

    fun service(binding: InputBindingModel, building: BuildingId, action: KeyActionId) {
        actions += TownMenuActionModel(binding, action, building, endsMenu = false)
    }

    fun build(): List<TownMenuActionModel> = actions.toList()
}

@RylNextDsl
class BuildingServiceCatalogBuilder {
    private val services = mutableListOf<BuildingServiceModel>()

    fun church(body: BuildingServiceBuilder.() -> Unit = {}) {
        building(DslDefaultBuildings.church, DslDefaultKeyActions.enterChurch, DslDefaultScenes.Towne.church, body)
    }

    fun smith(body: BuildingServiceBuilder.() -> Unit = {}) {
        building(DslDefaultBuildings.smith, DslDefaultKeyActions.enterSmith, DslDefaultScenes.Towne.smith, body)
    }

    fun inn(body: BuildingServiceBuilder.() -> Unit = {}) {
        building(DslDefaultBuildings.inn, DslDefaultKeyActions.enterInn, DslDefaultScenes.Towne.inn, body)
    }

    fun merchant(body: BuildingServiceBuilder.() -> Unit = {}) {
        building(DslDefaultBuildings.merchant, DslDefaultKeyActions.enterMerchant, DslDefaultScenes.Towne.merchant, body)
    }

    fun tavern(body: BuildingServiceBuilder.() -> Unit = {}) {
        building(DslDefaultBuildings.tavern, DslDefaultKeyActions.enterTavern, DslDefaultScenes.Towne.tavern, body)
    }

    fun building(
        building: BuildingId,
        action: KeyActionId,
        scene: SceneId,
        body: BuildingServiceBuilder.() -> Unit = {},
    ) {
        services += BuildingServiceBuilder(building, action, scene).apply(body).build()
    }

    fun build(): List<BuildingServiceModel> = services.toList()
}

@RylNextDsl
class BuildingServiceBuilder(
    private val building: BuildingId,
    private val defaultAction: KeyActionId,
    private val defaultScene: SceneId,
) {
    var action: KeyActionId = defaultAction
    var scene: SceneId = defaultScene
    var hours: IntRange? = null

    fun alwaysOpen() {
        hours = null
    }

    fun build(): BuildingServiceModel =
        BuildingServiceModel(
            building = building,
            action = action,
            scene = scene,
            opensAt = hours?.first,
            closesAt = hours?.last,
            nativeServices = listOf(
                EngineServiceUse(EngineService.Rendering, "${building.slug} service screens and map-window art"),
                EngineServiceUse(EngineService.KeyboardInput, "${building.slug} service menu input"),
                EngineServiceUse(EngineService.TimeState, "${building.slug} opening-hour checks"),
                EngineServiceUse(EngineService.PartyStateMutation, "${building.slug} cost, time, and resource effects"),
            ),
        )
}

@RylNextDsl
class CharacterCatalogBuilder {
    private val characters = mutableListOf<CharacterModel>()

    fun character(id: NpcId, body: CharacterBuilder.() -> Unit) {
        characters += CharacterBuilder(id).apply(body).build()
    }

    fun character(id: NpcId, name: String, job: NpcJobId, body: CharacterBuilder.() -> Unit = {}) {
        characters += CharacterBuilder(id).apply {
            this.name = name
            this.job = job
            body()
        }.build()
    }

    fun build(): List<CharacterModel> = characters.toList()
}

@RylNextDsl
class CharacterBuilder(private val id: NpcId) {
    var name: String = ""
    var job: NpcJobId = DslDefaultNpcJobs.merchant
    var gender: Gender = Gender.Male
    private var placementModel: CharacterPlacementModel = CharacterPlacementModel.Randomized
    private val roleModels = mutableListOf<NpcRole>()

    fun fixed(position: WorldPosition) {
        placementModel = CharacterPlacementModel.Fixed(position)
    }

    fun randomPlacement() {
        placementModel = CharacterPlacementModel.Randomized
    }

    fun randomWilderness() {
        placementModel = CharacterPlacementModel.RandomizedWilderness
    }

    fun role(vararg roles: NpcRole) {
        roleModels += roles
    }

    fun build(): CharacterModel =
        CharacterModel(
            id = id,
            name = name,
            job = job,
            gender = gender,
            placement = placementModel,
            roles = roleModels.toList(),
        )
}

@RylNextDsl
class FlagContentBuilder {
    private val flags = mutableListOf<FlagContentModel>()

    fun story(flag: FlagRef, body: FlagContentEntryBuilder.() -> Unit = {}) {
        require(flag.scope == FlagScope.Party) { "story flag ${flag.slug} must have party scope" }
        flags += FlagContentEntryBuilder(flag).apply(body).build()
    }

    fun encounter(flag: FlagRef, body: FlagContentEntryBuilder.() -> Unit = {}) {
        require(flag.scope == FlagScope.Encounter) { "encounter flag ${flag.slug} must have encounter scope" }
        flags += FlagContentEntryBuilder(flag).apply(body).build()
    }

    fun build(): List<FlagContentModel> = flags.toList()
}

@RylNextDsl
class FlagContentEntryBuilder(private val flag: FlagRef) {
    private val explicitStates = mutableListOf<FlagState>()

    fun states(vararg states: FlagState) {
        explicitStates += states
    }

    fun build(): FlagContentModel =
        FlagContentModel(flag, explicitStates.ifEmpty { flag.states })
}

@RylNextDsl
class MonsterCatalogBuilder {
    private val monsters = mutableListOf<MonsterModel>()

    fun monster(id: MonsterId, body: MonsterBuilder.() -> Unit) {
        monsters += MonsterBuilder(id).apply(body).build()
    }

    fun build(): List<MonsterModel> = monsters.toList()
}

@RylNextDsl
class MonsterBuilder(private val id: MonsterId) {
    var name: String = ""
    var count: IntRange = 1..1
    var countMaxLevelDivisor: Int? = null
    var hitPoints: IntRange = 1..1
    var weaponSkill: Int = 0
    var spellPower: Int = 0
    var tier: EncounterTier? = null
    var immune: Boolean = false

    fun build(): MonsterModel =
        MonsterModel(id, name, count, countMaxLevelDivisor, hitPoints, weaponSkill, spellPower, tier, immune)
}

@RylNextDsl
class MissionCatalogBuilder {
    private val missions = mutableListOf<MissionModel>()

    fun mission(id: MissionId, body: MissionBuilder.() -> Unit) {
        missions += MissionBuilder(id).apply(body).build()
    }

    fun build(): List<MissionModel> = missions.toList()
}

@RylNextDsl
class MissionBuilder(private val id: MissionId) {
    private var trigger: Trigger? = null
    private var requires: BoolAst? = null
    private var action: SceneId? = null

    fun randomStep(percent: Int) {
        trigger = Trigger.RandomStep(percent)
    }

    fun at(point: GridPoint) {
        trigger = Trigger.AtPosition(point)
    }

    fun at(position: WorldPosition) {
        val point = (position as? WorldPosition.Wilderness)?.point
            ?: error("mission ${id.slug} position trigger must be a wilderness position")
        at(point)
    }

    fun at(location: LocationId) {
        trigger = Trigger.AtLocation(location)
    }

    fun onTerrain(terrain: TerrainId) {
        trigger = Trigger.OnTerrain(terrain)
    }

    fun requires(state: FlagState) {
        requires = state.ast
    }

    fun requires(cond: BoolExpr) {
        requires = cond.ast
    }

    fun runs(scene: SceneId) {
        action = scene
    }

    fun build(): MissionModel =
        MissionModel(
            id = id,
            trigger = checkNotNull(trigger) { "mission ${id.slug} requires a trigger" },
            requires = requires,
            action = checkNotNull(action) { "mission ${id.slug} requires an action scene" },
        )
}

@RylNextDsl
class GameProgressBuilder {
    private val milestones = mutableListOf<GameMilestoneModel>()
    private var winBuilder: WinConditionBuilder? = null

    fun milestone(id: GameMilestoneId, body: GameMilestoneBuilder.() -> Unit) {
        milestones += GameMilestoneBuilder(id).apply(body).build()
    }

    fun winningConfiguration(body: WinConditionBuilder.() -> Unit) {
        winBuilder = WinConditionBuilder().apply(body)
    }

    fun build(): GameProgressModel =
        GameProgressModel(milestones.toList(), winBuilder?.build(milestones))
}

@RylNextDsl
class GameMilestoneBuilder(private val id: GameMilestoneId) {
    private var specialInventoryItem: ItemId? = null
    private var requiredToWin: Boolean = false
    private var sourceSpecial: SourceSpecialMapping? = null

    fun showsAs(item: ItemId) {
        specialInventoryItem = item
    }

    fun requiredToWin() {
        requiredToWin = true
    }

    fun sourcePartyField(field: SourceSpecialField, achievedValue: Int = 1) {
        sourceSpecial = SourceSpecialMapping(field, achievedValue)
    }

    fun build(): GameMilestoneModel =
        GameMilestoneModel(id, specialInventoryItem, requiredToWin, sourceSpecial)
}

@RylNextDsl
class WinConditionBuilder {
    private var includeMarkedRequired = false
    private val explicitRequired = mutableListOf<GameMilestoneId>()
    private var completionMilestone: GameMilestoneId? = null

    fun requiresAllMilestonesMarkedRequired() {
        includeMarkedRequired = true
    }

    fun requires(vararg milestones: GameMilestoneId) {
        explicitRequired += milestones
    }

    fun completedBy(milestone: GameMilestoneId) {
        completionMilestone = milestone
    }

    fun build(defined: List<GameMilestoneModel>): WinConditionModel {
        val markedRequired = if (includeMarkedRequired) {
            defined.filter { it.requiredToWin }.map { it.id }
        } else {
            emptyList()
        }

        return WinConditionModel((explicitRequired + markedRequired).distinct(), completionMilestone)
    }
}

@RylNextDsl
class LevelingBuilder {
    var maxLevel: Int = 28
    var hitPointFloor: FormulaId = DslDefaultFormulas.levelUpHitPointFloor
    private var thresholdModel: List<Int> = emptyList()
    private val classGrowthModel = mutableListOf<ClassLevelUpModel>()

    fun xpThresholds(vararg thresholds: Int) {
        thresholdModel = thresholds.toList()
    }

    fun classLevelUp(klass: PcClass, body: ClassLevelUpBuilder.() -> Unit) {
        classGrowthModel += ClassLevelUpBuilder(klass).apply(body).build()
    }

    fun build(): LevelingModel =
        LevelingModel(
            maxLevel = maxLevel,
            xpThresholds = thresholdModel,
            hitPointFloor = hitPointFloor,
            classGrowth = classGrowthModel.toList(),
        )
}

@RylNextDsl
class ClassLevelUpBuilder(private val klass: PcClass) {
    var weaponSkill: Int = 0
    var health: Int = 0
    var wisdom: Int = 0
    var charisma: Int = 0

    fun build(): ClassLevelUpModel =
        ClassLevelUpModel(klass, weaponSkill, health, wisdom, charisma)
}

@RylNextDsl
class FormulaCatalogBuilder {
    private val formulas = mutableListOf<FormulaModel>()

    fun formula(id: FormulaId, body: FormulaBuilder.() -> Unit) {
        formulas += FormulaBuilder(id).apply(body).build()
    }

    fun build(): List<FormulaModel> = formulas.toList()
}

@RylNextDsl
class FormulaBuilder(private val id: FormulaId) {
    var value: IntExpr = lit(0)

    val encounterHitPointMinBase: IntExpr = v("encounter_hit_point_min_base")
    val encounterHitPointMaxBase: IntExpr = v("encounter_hit_point_max_base")
    val partyAverage: IntExpr = v("party_avg")
    val firstLevel: IntExpr = v("first_level")
    val encounterFoeCount: IntExpr = v("encounter_foe_count")
    val encounterHitPointMax: IntExpr = v("encounter_hit_point_max")
    val monsterWeaponSkill: IntExpr = v("monster_weapon_skill")
    val monsterSpellPower: IntExpr = v("monster_spell_power")
    val health: IntExpr = v("health")

    fun whenValue(condition: BoolExpr, thenValue: IntExpr, elseValue: IntExpr = lit(0)): IntExpr =
        Expr(IntAst.If(condition.ast, thenValue.ast, elseValue.ast))

    fun whenValue(condition: BoolExpr, thenValue: Int, elseValue: Int = 0): IntExpr =
        whenValue(condition, lit(thenValue), lit(elseValue))

    fun build(): FormulaModel = FormulaModel(id, value.ast)
}

@RylNextDsl
class ItemUseBuilder(private val id: ItemId) {
    private val contexts = mutableSetOf(ItemUseContext.Combat)
    var consume: ItemConsumePolicy = ItemConsumePolicy.BeforeUse
    private var ops = emptyList<Op>()

    fun usableIn(vararg contexts: ItemUseContext) {
        this.contexts.clear()
        this.contexts += contexts
    }

    fun combat(): Unit = usableIn(ItemUseContext.Combat)

    fun inventory(): Unit = usableIn(ItemUseContext.Inventory)

    fun combatAndInventory(): Unit = usableIn(ItemUseContext.Combat, ItemUseContext.Inventory)

    fun effect(body: ScriptScope.() -> Unit) {
        ops = recordScript(body)
    }

    fun build(): ItemUseModel =
        ItemUseModel(id, contexts.toSet(), consume, ops)
}

@RylNextDsl
class RewardCatalogBuilder {
    private val rewards = mutableListOf<RewardBundleModel>()

    fun reward(id: RewardBundleId, body: RewardBundleBuilder.() -> Unit) {
        rewards += RewardBundleBuilder(id).apply(body).build()
    }

    fun build(): List<RewardBundleModel> = rewards.toList()
}

@RylNextDsl
class RewardBundleBuilder(private val id: RewardBundleId) {
    var experience: FormulaId = DslDefaultFormulas.rewardXpPerSurvivor
    var gold: FormulaId = DslDefaultFormulas.rewardGoldParty
    private var applicationModel = RewardApplicationBuilder().build()
    private var lootModel = LootTableBuilder(DslDefaultLootTables.standardBattle).build()

    fun itemPlacement(body: RewardApplicationBuilder.() -> Unit) {
        applicationModel = RewardApplicationBuilder(applicationModel).apply(body).build()
    }

    fun lootTable(id: LootTableId, body: LootTableBuilder.() -> Unit) {
        lootModel = LootTableBuilder(id).apply(body).build()
    }

    fun build(): RewardBundleModel = RewardBundleModel(id, experience, gold, applicationModel, lootModel)
}

@RylNextDsl
class RewardApplicationBuilder(initial: RewardApplicationModel? = null) {
    var regularItems: RewardItemPlacement =
        initial?.regularItems ?: RewardItemPlacement.FirstFreeInventorySlot
    var overflow: RewardOverflowPolicy =
        initial?.overflow ?: RewardOverflowPolicy.SkipIfInventoryFull
    var specialItems: RewardSpecialItemPolicy =
        initial?.specialItems ?: RewardSpecialItemPolicy.ProjectMilestoneState

    fun build(): RewardApplicationModel =
        RewardApplicationModel(regularItems, overflow, specialItems)
}

@RylNextDsl
class LootTableBuilder(private val id: LootTableId) {
    var dropChancePercent: Int = 5
    var treasureChancePercent: Int = 59
    var treasure: ItemPoolId = DslDefaultItemPools.battleSpoils
    var magic: ItemPoolId = DslDefaultItemPools.magicItems

    fun build(): LootTableModel =
        LootTableModel(id, dropChancePercent, treasureChancePercent, treasure, magic)
}

@RylNextDsl
class CombatBuilder {
    private var standardBattleModel: StandardBattleModel? = null

    fun standardBattle(body: StandardBattleBuilder.() -> Unit) {
        standardBattleModel = StandardBattleBuilder().apply(body).build()
    }

    fun build(): CombatSystemModel = CombatSystemModel(standardBattleModel)
}

@RylNextDsl
class StandardBattleBuilder {
    private var openingModel = EncounterOpeningBuilder().build()
    private var openingKeysModel = BattleOpeningKeysModel()
    private var actionModels = PlayerCombatActionsBuilder().build()
    private var playerRoundModel = PlayerRoundBuilder().build()
    private var monsterRoundModel = MonsterRoundBuilder().build()
    private var textModel = BattleTextBuilder().build()
    var rewards: RewardBundleId = DslDefaultRewards.standardBattle
    var encounterFoeCountMax: Int = 8

    fun text(body: BattleTextBuilder.() -> Unit) {
        textModel = BattleTextBuilder(textModel).apply(body).build()
    }

    fun opening(body: EncounterOpeningBuilder.() -> Unit) {
        openingModel = EncounterOpeningBuilder(openingModel).apply(body).build()
    }

    fun openingKeys(body: BattleOpeningKeysBuilder.() -> Unit) {
        openingKeysModel = BattleOpeningKeysBuilder(openingKeysModel).apply(body).build()
    }

    fun playerActions(body: PlayerCombatActionsBuilder.() -> Unit) {
        actionModels = PlayerCombatActionsBuilder().apply(body).build()
    }

    fun playerRound(body: PlayerRoundBuilder.() -> Unit) {
        playerRoundModel = PlayerRoundBuilder(playerRoundModel).apply(body).build()
    }

    fun monsterRound(body: MonsterRoundBuilder.() -> Unit) {
        monsterRoundModel = MonsterRoundBuilder(monsterRoundModel).apply(body).build()
    }

    fun build(): StandardBattleModel =
        StandardBattleModel(
            opening = openingModel,
            openingKeys = openingKeysModel,
            actions = actionModels,
            playerRound = playerRoundModel,
            monsterRound = monsterRoundModel,
            text = textModel,
            rewards = rewards,
            encounterFoeCountMax = encounterFoeCountMax,
            nativeServices = defaultNativeServices,
        )

    companion object {
        private val defaultNativeServices = listOf(
            EngineServiceUse(EngineService.KeyboardInput, "fight/run, action, spell, item, and target selection"),
            EngineServiceUse(EngineService.Rendering, "combat banners, text panes, map-window art, and status refresh"),
            EngineServiceUse(EngineService.RandomNumbers, "encounter sizing, hit rolls, damage rolls, fleeing, loot, and AI"),
            EngineServiceUse(EngineService.CombatContextStorage, "live monster hit points, death bits, and round status effects"),
            EngineServiceUse(EngineService.InventoryStorage, "item use, consume, loot placement, and first-free-slot lookup"),
            EngineServiceUse(EngineService.PartyStateMutation, "HP, mana, XP, gold, kills, and death-state updates"),
            EngineServiceUse(EngineService.TextFormatting, "party, monster, item, pronoun, and reward bindings"),
        )
    }
}

@RylNextDsl
class BattleOpeningKeysBuilder(initial: BattleOpeningKeysModel = BattleOpeningKeysModel()) {
    var fight: InputBindingModel = initial.fight
    var run: InputBindingModel = initial.run

    fun build(): BattleOpeningKeysModel = BattleOpeningKeysModel(fight, run)
}

@RylNextDsl
class BattleTextBuilder(initial: BattleTextModel? = null) {
    private val defaults = initial ?: BattleTextModel()
    private var encounterBannerText = defaults.encounterBanner
    private var youFaceText = defaults.youFace
    private var stillFaceText = defaults.stillFace
    private var fightText = defaults.fight
    private var runText = defaults.run
    private var bloodFlowText = defaults.bloodFlow
    private var whatNowText = defaults.whatNow
    private var targetPromptText = defaults.targetPrompt
    private var useWhatText = defaults.useWhat
    private var spellAbortText = defaults.spellAbort
    private var runCowardText = defaults.runCoward
    private var runDesperateFailText = defaults.runDesperateFail
    private var runFailAwayText = defaults.runFailAway
    private var runEscapedText = defaults.runEscaped
    private var notPossibleText = defaults.notPossible
    private var magicImmuneText = defaults.magicImmune
    private var spellFizzleText = defaults.spellFizzle
    private var notEnoughMagicText = defaults.notEnoughMagic
    private var restGainHitsText = defaults.restGainHits
    private var defendStanceText = defaults.defendStance
    private var battleCryText = defaults.battleCry
    private var killedText = defaults.killed
    private var partyHeldText = defaults.partyHeld
    private var victoryBestedText = defaults.victoryBested
    private var victoryEachSurvivorText = defaults.victoryEachSurvivor
    private var victoryFoundObjectText = defaults.victoryFoundObject

    fun encounterBanner(body: StructuredTextBlockBuilder.() -> Unit) {
        encounterBannerText = structuredText(body)
    }

    fun youFace(body: StructuredTextBlockBuilder.() -> Unit) {
        youFaceText = structuredText(body)
    }

    fun stillFace(body: StructuredTextBlockBuilder.() -> Unit) {
        stillFaceText = structuredText(body)
    }

    fun fight(body: StructuredTextBlockBuilder.() -> Unit) {
        fightText = structuredText(body)
    }

    fun run(body: StructuredTextBlockBuilder.() -> Unit) {
        runText = structuredText(body)
    }

    fun bloodFlow(body: StructuredTextBlockBuilder.() -> Unit) {
        bloodFlowText = structuredText(body)
    }

    fun whatNow(body: StructuredTextBlockBuilder.() -> Unit) {
        whatNowText = structuredText(body)
    }

    fun targetPrompt(body: StructuredTextBlockBuilder.() -> Unit) {
        targetPromptText = structuredText(body)
    }

    fun useWhat(body: StructuredTextBlockBuilder.() -> Unit) {
        useWhatText = structuredText(body)
    }

    fun spellAbort(body: StructuredTextBlockBuilder.() -> Unit) {
        spellAbortText = structuredText(body)
    }

    fun runCoward(body: StructuredTextBlockBuilder.() -> Unit) {
        runCowardText = structuredText(body)
    }

    fun runDesperateFail(body: StructuredTextBlockBuilder.() -> Unit) {
        runDesperateFailText = structuredText(body)
    }

    fun runFailAway(body: StructuredTextBlockBuilder.() -> Unit) {
        runFailAwayText = structuredText(body)
    }

    fun runEscaped(body: StructuredTextBlockBuilder.() -> Unit) {
        runEscapedText = structuredText(body)
    }

    fun notPossible(body: StructuredTextBlockBuilder.() -> Unit) {
        notPossibleText = structuredText(body)
    }

    fun magicImmune(body: StructuredTextBlockBuilder.() -> Unit) {
        magicImmuneText = structuredText(body)
    }

    fun spellFizzle(body: StructuredTextBlockBuilder.() -> Unit) {
        spellFizzleText = structuredText(body)
    }

    fun notEnoughMagic(body: StructuredTextBlockBuilder.() -> Unit) {
        notEnoughMagicText = structuredText(body)
    }

    fun restGainHits(body: StructuredTextBlockBuilder.() -> Unit) {
        restGainHitsText = structuredText(body)
    }

    fun defendStance(body: StructuredTextBlockBuilder.() -> Unit) {
        defendStanceText = structuredText(body)
    }

    fun battleCry(body: StructuredTextBlockBuilder.() -> Unit) {
        battleCryText = structuredText(body)
    }

    fun killed(body: StructuredTextBlockBuilder.() -> Unit) {
        killedText = structuredText(body)
    }

    fun partyHeld(body: StructuredTextBlockBuilder.() -> Unit) {
        partyHeldText = structuredText(body)
    }

    fun victoryBested(body: StructuredTextBlockBuilder.() -> Unit) {
        victoryBestedText = structuredText(body)
    }

    fun victoryEachSurvivor(body: StructuredTextBlockBuilder.() -> Unit) {
        victoryEachSurvivorText = structuredText(body)
    }

    fun victoryFoundObject(body: StructuredTextBlockBuilder.() -> Unit) {
        victoryFoundObjectText = structuredText(body)
    }

    fun build(): BattleTextModel =
        BattleTextModel(
            encounterBanner = encounterBannerText,
            youFace = youFaceText,
            stillFace = stillFaceText,
            fight = fightText,
            run = runText,
            bloodFlow = bloodFlowText,
            whatNow = whatNowText,
            targetPrompt = targetPromptText,
            useWhat = useWhatText,
            spellAbort = spellAbortText,
            runCoward = runCowardText,
            runDesperateFail = runDesperateFailText,
            runFailAway = runFailAwayText,
            runEscaped = runEscapedText,
            notPossible = notPossibleText,
            magicImmune = magicImmuneText,
            spellFizzle = spellFizzleText,
            notEnoughMagic = notEnoughMagicText,
            restGainHits = restGainHitsText,
            defendStance = defendStanceText,
            battleCry = battleCryText,
            killed = killedText,
            partyHeld = partyHeldText,
            victoryBested = victoryBestedText,
            victoryEachSurvivor = victoryEachSurvivorText,
            victoryFoundObject = victoryFoundObjectText,
        )
}

@RylNextDsl
class SpeakBuilder {
    private var standardConversationModel: StandardConversationModel? = null
    private var textTablesModel = SpeakTextTablesModel()
    private val questionActions = mutableListOf<QuestionActionScriptModel>()

    fun standardConversation(body: StandardConversationBuilder.() -> Unit) {
        standardConversationModel = StandardConversationBuilder().apply(body).build()
    }

    fun textTables(body: SpeakTextTablesBuilder.() -> Unit) {
        textTablesModel = SpeakTextTablesBuilder().apply(body).build()
    }

    fun questionAction(topic: QuestionTopic, job: NpcJobId, body: ScriptScope.() -> Unit) {
        questionActions += QuestionActionScriptModel(topic, job, recordScript(body))
    }

    fun build(): SpeakSystemModel =
        SpeakSystemModel(
            standardConversation = standardConversationModel,
            textTables = textTablesModel,
            questionActions = questionActions.toList(),
        )
}

@RylNextDsl
class SpeakTextTablesBuilder {
    private val screens = mutableListOf<ReusableScreenTextModel>()
    private val chitchatPrompts = mutableListOf<ChitchatPromptTextModel>()
    private val chitchatReplies = mutableListOf<ChitchatReplyTextModel>()
    private val questionReplies = mutableListOf<QuestionReplyTextModel>()
    private val rumourTables = mutableListOf<RumourTextTableModel>()

    fun screen(id: TextId, body: StructuredTextBlockBuilder.() -> Unit) {
        screens += ReusableScreenTextModel(id, StructuredTextBlockBuilder().apply(body).build())
    }

    fun chitchatPrompt(subject: ChitchatSubject, body: ChitchatPromptTextBuilder.() -> Unit) {
        chitchatPrompts += ChitchatPromptTextBuilder(subject).apply(body).build()
    }

    fun chitchatReplies(subject: ChitchatSubject, body: JobTextTableBuilder.() -> Unit) {
        chitchatReplies += ChitchatReplyTextModel(subject, JobTextTableBuilder().apply(body).build())
    }

    fun questionReplies(topic: QuestionTopic, body: JobTextTableBuilder.() -> Unit) {
        questionReplies += QuestionReplyTextModel(topic, JobTextTableBuilder().apply(body).build())
    }

    fun rumourTable(id: RumourTableId, body: RumourTextTableBuilder.() -> Unit) {
        rumourTables += RumourTextTableBuilder(id).apply(body).build()
    }

    fun build(): SpeakTextTablesModel =
        SpeakTextTablesModel(
            screens = screens.toList(),
            chitchatPrompts = chitchatPrompts.toList(),
            chitchatReplies = chitchatReplies.toList(),
            questionReplies = questionReplies.toList(),
            rumourTables = rumourTables.toList(),
        )
}

@RylNextDsl
class ChitchatPromptTextBuilder(private val subject: ChitchatSubject) {
    private val variants = mutableListOf<ChitchatPromptVariantModel>()

    fun any(body: StructuredTextBlockBuilder.() -> Unit) {
        variants += ChitchatPromptVariantModel(
            ChitchatPromptAudience.Any,
            StructuredTextBlockBuilder().apply(body).build(),
        )
    }

    fun female(body: StructuredTextBlockBuilder.() -> Unit) {
        variants += ChitchatPromptVariantModel(
            ChitchatPromptAudience.Female,
            StructuredTextBlockBuilder().apply(body).build(),
        )
    }

    fun male(body: StructuredTextBlockBuilder.() -> Unit) {
        variants += ChitchatPromptVariantModel(
            ChitchatPromptAudience.Male,
            StructuredTextBlockBuilder().apply(body).build(),
        )
    }

    fun build(): ChitchatPromptTextModel = ChitchatPromptTextModel(subject, variants.toList())
}

@RylNextDsl
class StructuredTextBlockBuilder : TextLineSink {
    private val lines = mutableListOf<TextLine>()

    override fun addTextLine(line: TextLine) {
        lines += line
    }

    fun build(): TextBlock = TextBlock.Structured(lines.toList())
}

@RylNextDsl
class JobTextTableBuilder {
    private val replies = mutableListOf<JobTextModel>()

    fun job(id: NpcJobId, body: StructuredTextBlockBuilder.() -> Unit) {
        replies += JobTextModel(id, StructuredTextBlockBuilder().apply(body).build())
    }

    fun build(): List<JobTextModel> = replies.toList()
}

@RylNextDsl
class RumourTextTableBuilder(private val id: RumourTableId) {
    private val entries = mutableListOf<RumourEntryModel>()

    fun rumour(body: RumourEntryBuilder.() -> Unit) {
        entries += RumourEntryBuilder(null).apply(body).build()
    }

    fun rumour(id: RumourId, body: RumourEntryBuilder.() -> Unit) {
        entries += RumourEntryBuilder(id).apply(body).build()
    }

    fun build(): RumourTextTableModel = RumourTextTableModel(id, entries.toList())
}

@RylNextDsl
class RumourEntryBuilder(private val id: RumourId?) : TextLineSink {
    private val text = StructuredTextBlockBuilder()
    private val excludedStates = mutableListOf<FlagState>()

    override fun addTextLine(line: TextLine) {
        text.addTextLine(line)
    }

    fun excludeWhen(vararg states: FlagState) {
        excludedStates += states
    }

    fun build(): RumourEntryModel =
        RumourEntryModel(id, text.build(), excludedStates.toList())
}

@RylNextDsl
class StandardConversationBuilder {
    var npcSelection: ConversationNpcSelection = ConversationNpcSelection.BuildingListOrWildernessLocation
    var visitPolicy: NpcVisitPolicy = NpcVisitPolicy.PerPartyVisitedBit
    var branchSelection: DialogueBranchSelection = DialogueBranchSelection.FirstMatchingStateThenDefault
    var missionHints: MissionHintPolicy = MissionHintPolicy.BeforeEachMenu

    private var menuModel = ConversationMenuBuilder().apply {
        chitchat()
        question {
            lodging()
            magic()
            people()
            nothing()
        }
        rumour()
        bye()
    }.build()
    private var chitchatModel = ChitchatBuilder().build()
    private var rumourModel = ConversationRumourBuilder().build()
    private var promptsModel = ConversationPromptTextBuilder().build()

    fun menu(body: ConversationMenuBuilder.() -> Unit) {
        menuModel = ConversationMenuBuilder().apply(body).build()
    }

    fun chitchat(body: ChitchatBuilder.() -> Unit) {
        chitchatModel = ChitchatBuilder(chitchatModel).apply(body).build()
    }

    fun rumour(body: ConversationRumourBuilder.() -> Unit) {
        rumourModel = ConversationRumourBuilder(rumourModel).apply(body).build()
    }

    fun prompts(body: ConversationPromptTextBuilder.() -> Unit) {
        promptsModel = ConversationPromptTextBuilder(promptsModel).apply(body).build()
    }

    fun build(): StandardConversationModel =
        StandardConversationModel(
            npcSelection = npcSelection,
            visitPolicy = visitPolicy,
            branchSelection = branchSelection,
            missionHints = missionHints,
            menu = menuModel,
            chitchat = chitchatModel,
            rumour = rumourModel,
            prompts = promptsModel,
            nativeServices = defaultNativeServices,
        )

    companion object {
        private val defaultNativeServices = listOf(
            EngineServiceUse(EngineService.KeyboardInput, "NPC selection and conversation menu choices"),
            EngineServiceUse(EngineService.Rendering, "text-window clearing, menu rendering, and dialogue screens"),
            EngineServiceUse(EngineService.RandomNumbers, "chitchat subject and rumour selection"),
            EngineServiceUse(EngineService.NpcStateStorage, "NPC lookup, job/gender/role metadata, and visited bits"),
            EngineServiceUse(EngineService.PartyStateMutation, "dialogue scripts can advance flags, rewards, and journals"),
            EngineServiceUse(EngineService.TextFormatting, "NPC names, jobs, locations, and role-bound text placeholders"),
        )
    }
}

@RylNextDsl
class ConversationPromptTextBuilder(initial: ConversationPromptTextModel = ConversationPromptTextModel()) {
    var chooseNpc: TextBlock = initial.chooseNpc
    private var mainMenuText: TextBlock = initial.mainMenu

    fun mainMenu(body: ConversationMainMenuPromptBuilder.() -> Unit) {
        mainMenuText = ConversationMainMenuPromptBuilder().apply(body).build()
    }

    fun build(): ConversationPromptTextModel =
        ConversationPromptTextModel(
            chooseNpc = chooseNpc,
            mainMenu = mainMenuText,
        )
}

@RylNextDsl
class ConversationMainMenuPromptBuilder : TextLineSink {
    private val text = StructuredTextBlockBuilder()

    val npcName: TextPart = TextPart.ConversationNpcName

    override fun addTextLine(line: TextLine) {
        text.addTextLine(line)
    }

    fun build(): TextBlock = text.build()
}

@RylNextDsl
class ConversationMenuBuilder {
    private val actions = mutableListOf<ConversationMenuActionModel>()
    private var questionActions = emptyList<QuestionMenuActionModel>()

    fun chitchat(key: Char = 'C', action: KeyActionId = DslDefaultKeyActions.speakChitchat) {
        chitchat(inputKey(key), action)
    }

    fun chitchat(binding: InputBindingModel, action: KeyActionId = DslDefaultKeyActions.speakChitchat) {
        actions += ConversationMenuActionModel(ConversationTopic.Chitchat, binding, action)
    }

    fun question(
        key: Char = 'Q',
        action: KeyActionId = DslDefaultKeyActions.speakQuestion,
        body: QuestionMenuBuilder.() -> Unit,
    ) {
        question(inputKey(key), action, body)
    }

    fun question(
        binding: InputBindingModel,
        action: KeyActionId = DslDefaultKeyActions.speakQuestion,
        body: QuestionMenuBuilder.() -> Unit,
    ) {
        actions += ConversationMenuActionModel(ConversationTopic.Question, binding, action)
        questionActions = QuestionMenuBuilder().apply(body).build()
    }

    fun rumour(key: Char = 'R', action: KeyActionId = DslDefaultKeyActions.speakRumour) {
        rumour(inputKey(key), action)
    }

    fun rumour(binding: InputBindingModel, action: KeyActionId = DslDefaultKeyActions.speakRumour) {
        actions += ConversationMenuActionModel(ConversationTopic.Rumour, binding, action)
    }

    fun bye(key: Char = 'B', action: KeyActionId = DslDefaultKeyActions.speakBye) {
        bye(inputKey(key), action)
    }

    fun bye(binding: InputBindingModel, action: KeyActionId = DslDefaultKeyActions.speakBye) {
        actions += ConversationMenuActionModel(ConversationTopic.Bye, binding, action)
    }

    fun build(): ConversationMenuModel =
        ConversationMenuModel(actions.toList(), questionActions)
}

@RylNextDsl
class QuestionMenuBuilder {
    private val actions = mutableListOf<QuestionMenuActionModel>()

    fun lodging(key: Char = 'L', action: KeyActionId = DslDefaultKeyActions.questionLodging) {
        lodging(inputKey(key), action)
    }

    fun lodging(binding: InputBindingModel, action: KeyActionId = DslDefaultKeyActions.questionLodging) {
        actions += QuestionMenuActionModel(QuestionTopic.Lodging, binding, action)
    }

    fun magic(key: Char = 'M', action: KeyActionId = DslDefaultKeyActions.questionMagic) {
        magic(inputKey(key), action)
    }

    fun magic(binding: InputBindingModel, action: KeyActionId = DslDefaultKeyActions.questionMagic) {
        actions += QuestionMenuActionModel(QuestionTopic.Magic, binding, action)
    }

    fun people(key: Char = 'P', action: KeyActionId = DslDefaultKeyActions.questionPeople) {
        people(inputKey(key), action)
    }

    fun people(binding: InputBindingModel, action: KeyActionId = DslDefaultKeyActions.questionPeople) {
        actions += QuestionMenuActionModel(QuestionTopic.People, binding, action)
    }

    fun nothing(key: Char = 'N', action: KeyActionId = DslDefaultKeyActions.questionNothing) {
        nothing(inputKey(key), action)
    }

    fun nothing(binding: InputBindingModel, action: KeyActionId = DslDefaultKeyActions.questionNothing) {
        actions += QuestionMenuActionModel(QuestionTopic.Nothing, binding, action)
    }

    fun build(): List<QuestionMenuActionModel> = actions.toList()
}

@RylNextDsl
class ChitchatBuilder(initial: ChitchatModel? = null) {
    private var subjectModels: List<ChitchatSubject> =
        initial?.subjects ?: listOf(ChitchatSubject.Well, ChitchatSubject.Weather, ChitchatSubject.Health)
    var replies: ChitchatReplyPolicy =
        initial?.replies ?: ChitchatReplyPolicy.BySubjectAndNpcJob

    fun subjects(vararg subjects: ChitchatSubject) {
        subjectModels = subjects.toList()
    }

    fun build(): ChitchatModel = ChitchatModel(subjectModels, replies)
}

@RylNextDsl
class ConversationRumourBuilder(initial: ConversationRumourModel? = null) {
    var usage: RumourUsagePolicy =
        initial?.usage ?: RumourUsagePolicy.OncePerConversationThenFallback
    var source: RumourSourcePolicy =
        initial?.source ?: RumourSourcePolicy.StandardTownRumours

    fun build(): ConversationRumourModel = ConversationRumourModel(usage, source)
}

@RylNextDsl
class EncounterOpeningBuilder(initial: EncounterOpeningModel? = null) {
    var fleeChancePercent: Int = initial?.fleeChancePercent ?: 95
    var desperateFleeChancePercent: Int = initial?.desperateFleeChancePercent ?: 31
    var mandatoryRun: MandatoryRunRule = initial?.mandatoryRun ?: MandatoryRunRule.NoEscape
    private val banners = initial?.banners?.toMutableList() ?: mutableListOf(
        DslDefaultImages.Scene.encounterBanner1,
        DslDefaultImages.Scene.encounterBanner2,
        DslDefaultImages.Scene.encounterBanner3,
        DslDefaultImages.Scene.encounterBanner4,
        DslDefaultImages.Scene.encounterBanner5,
    )

    fun encounterBanners(vararg images: ImageId) {
        banners.clear()
        banners += images
    }

    fun build(): EncounterOpeningModel =
        EncounterOpeningModel(fleeChancePercent, desperateFleeChancePercent, mandatoryRun, banners.toList())
}

@RylNextDsl
class PlayerCombatActionsBuilder {
    private val actions = mutableListOf<CombatActionModel>()

    fun attack(
        key: Char = 'A',
        action: KeyActionId = DslDefaultKeyActions.attack,
        prompt: StructuredTextBlockBuilder.() -> Unit = { lines("(A)ttack") },
    ) {
        action(inputKey(key), action, CombatActionKind.Attack, CombatActionRequirement.Always, prompt)
    }

    fun attack(
        binding: InputBindingModel,
        action: KeyActionId = DslDefaultKeyActions.attack,
        prompt: StructuredTextBlockBuilder.() -> Unit = { lines("(A)ttack") },
    ) {
        action(binding, action, CombatActionKind.Attack, CombatActionRequirement.Always, prompt)
    }

    fun defend(
        key: Char = 'D',
        action: KeyActionId = DslDefaultKeyActions.defend,
        prompt: StructuredTextBlockBuilder.() -> Unit = { lines("(D)efend") },
    ) {
        action(inputKey(key), action, CombatActionKind.Defend, CombatActionRequirement.Always, prompt)
    }

    fun defend(
        binding: InputBindingModel,
        action: KeyActionId = DslDefaultKeyActions.defend,
        prompt: StructuredTextBlockBuilder.() -> Unit = { lines("(D)efend") },
    ) {
        action(binding, action, CombatActionKind.Defend, CombatActionRequirement.Always, prompt)
    }

    fun useMagicItem(
        key: Char = 'U',
        action: KeyActionId = DslDefaultKeyActions.useItem,
        prompt: StructuredTextBlockBuilder.() -> Unit = { lines("(U)se Item") },
    ) {
        action(inputKey(key), action, CombatActionKind.UseMagicItem, CombatActionRequirement.Always, prompt)
    }

    fun useMagicItem(
        binding: InputBindingModel,
        action: KeyActionId = DslDefaultKeyActions.useItem,
        prompt: StructuredTextBlockBuilder.() -> Unit = { lines("(U)se Item") },
    ) {
        action(binding, action, CombatActionKind.UseMagicItem, CombatActionRequirement.Always, prompt)
    }

    fun battleCry(
        key: Char = 'B',
        action: KeyActionId = DslDefaultKeyActions.battleCry,
        prompt: StructuredTextBlockBuilder.() -> Unit = { lines("(B)attle Cry") },
    ) {
        action(inputKey(key), action, CombatActionKind.BattleCry, CombatActionRequirement.Always, prompt)
    }

    fun battleCry(
        binding: InputBindingModel,
        action: KeyActionId = DslDefaultKeyActions.battleCry,
        prompt: StructuredTextBlockBuilder.() -> Unit = { lines("(B)attle Cry") },
    ) {
        action(binding, action, CombatActionKind.BattleCry, CombatActionRequirement.Always, prompt)
    }

    fun castMageSpell(
        key: Char = 'C',
        action: KeyActionId = DslDefaultKeyActions.castSpell,
        prompt: StructuredTextBlockBuilder.() -> Unit = { lines("(C)ast Spell") },
    ) {
        action(inputKey(key), action, CombatActionKind.CastMageSpell, CombatActionRequirement.Mage, prompt)
    }

    fun castMageSpell(
        binding: InputBindingModel,
        action: KeyActionId = DslDefaultKeyActions.castSpell,
        prompt: StructuredTextBlockBuilder.() -> Unit = { lines("(C)ast Spell") },
    ) {
        action(binding, action, CombatActionKind.CastMageSpell, CombatActionRequirement.Mage, prompt)
    }

    fun build(): List<CombatActionModel> = actions.toList()

    private fun action(
        binding: InputBindingModel,
        action: KeyActionId,
        kind: CombatActionKind,
        requirement: CombatActionRequirement,
        prompt: StructuredTextBlockBuilder.() -> Unit,
    ) {
        actions += CombatActionModel(binding, action, kind, requirement, structuredText(prompt))
    }
}

@RylNextDsl
class PlayerRoundBuilder(initial: PlayerRoundModel? = null) {
    private var attackRules = initial?.attack ?: PlayerAttackRulesModel(
        hitBonus = 1,
        damageSpreadMax = 4,
        confusionDivisor = 3,
        hpDefenseDivisor = 12,
    )
    private var defendRules = initial?.defend ?: DefendRulesModel(
        weaponSkillPenalty = 2,
        restThreshold = 5,
        cap = 250,
    )
    private var battleCryRules = initial?.battleCry ?: BattleCryRulesModel(
        weaponSkillPenalty = 4,
    )

    fun attack(hitBonus: Int, damageSpreadMax: Int, confusionDivisor: Int, hpDefenseDivisor: Int = 12) {
        attackRules = PlayerAttackRulesModel(hitBonus, damageSpreadMax, confusionDivisor, hpDefenseDivisor)
    }

    fun defend(weaponSkillPenalty: Int, restThreshold: Int, cap: Int = 250) {
        defendRules = DefendRulesModel(weaponSkillPenalty, restThreshold, cap)
    }

    fun battleCry(weaponSkillPenalty: Int) {
        battleCryRules = BattleCryRulesModel(weaponSkillPenalty)
    }

    fun build(): PlayerRoundModel =
        PlayerRoundModel(attackRules, defendRules, battleCryRules)
}

@RylNextDsl
class MonsterRoundBuilder(initial: MonsterRoundModel? = null) {
    var spellChanceBasePercent: Int = initial?.spellChanceBasePercent ?: 15
    var spellPowerDivisor: Int = initial?.spellPowerDivisor ?: 2
    private var meleeRules = initial?.melee ?: MonsterMeleeRulesModel(
        hitBonus = 1,
        damageDivisor = 6,
        damageSpreadMax = 9,
        damageMin = 1,
        fallback = DslDefaultScenes.Battle.monsterMelee,
    )

    fun spellChance(basePercent: Int, spellPowerDivisor: Int) {
        spellChanceBasePercent = basePercent
        this.spellPowerDivisor = spellPowerDivisor
    }

    fun melee(
        hitBonus: Int,
        damageDivisor: Int,
        damageSpreadMax: Int,
        damageMin: Int,
        fallback: SceneId,
    ) {
        meleeRules = MonsterMeleeRulesModel(hitBonus, damageDivisor, damageSpreadMax, damageMin, fallback)
    }

    fun build(): MonsterRoundModel =
        MonsterRoundModel(spellChanceBasePercent, spellPowerDivisor, meleeRules)
}

fun rylweenNext(body: GameBuilder.() -> Unit): GameModel =
    GameBuilder().apply(body).build()

@RylNextDsl
class RylweenBuilder {
    private var contentModel: GameModel? = null
    private val states = mutableListOf<StateModel>()

    fun content(body: GameBuilder.() -> Unit) {
        contentModel = GameBuilder().apply(body).build()
    }

    fun state(id: StateId, body: StateBuilder.() -> Unit) {
        stateFixture(id, body)
    }

    fun stateFixture(id: StateId, body: StateBuilder.() -> Unit) {
        states += StateBuilder(id, StateKind.AuthoredFixture).apply(body).build()
    }

    fun runtimeState(id: StateId, body: StateBuilder.() -> Unit) {
        states += StateBuilder(id, StateKind.RuntimeSnapshot).apply(body).build()
    }

    fun build(): RylweenModel =
        RylweenModel(
            content = checkNotNull(contentModel) { "rylween requires content { ... }" },
            states = states.toList(),
        )
}

fun rylween(body: RylweenBuilder.() -> Unit): RylweenModel =
    RylweenBuilder().apply(body).build()

@RylNextDsl
class StateBuilder(private val id: StateId, private val kind: StateKind) {
    private var realmState: RealmState? = null
    private val locationPlacements = mutableListOf<LocationPlacement>()
    private val npcPlacements = mutableListOf<NpcPlacement>()
    private val parties = mutableListOf<PartyState>()

    fun realm(body: RealmStateBuilder.() -> Unit) {
        realmState = RealmStateBuilder().apply(body).build()
    }

    fun locations(body: LocationStateBuilder.() -> Unit) {
        locationPlacements += LocationStateBuilder().apply(body).build()
    }

    fun npcs(body: NpcStateBuilder.() -> Unit) {
        npcPlacements += NpcStateBuilder().apply(body).build()
    }

    fun party(id: PartyId, body: PartyStateBuilder.() -> Unit) {
        parties += PartyStateBuilder(id).apply(body).build()
    }

    fun build(): StateModel =
        StateModel(
            id = id,
            kind = kind,
            realm = checkNotNull(realmState) { "state ${id.slug} requires realm { ... }" },
            locations = locationPlacements.toList(),
            npcs = npcPlacements.toList(),
            parties = parties.toList(),
        )
}

@RylNextDsl
class RealmStateBuilder {
    var day: Int = 0
    var hour: Int = 0
    var minute: Int = 0
    var rngState: Long? = null
    var activeParty: PartyId? = null
    var currentMap: WorldMapId = DslDefaultMaps.wilderness
    var currentMode: RuntimeMode = RuntimeMode.Wilderness
    var currentTown: TownId? = null
    var banished: Boolean = false
    var lastPlayedDay: Int? = null
    var partyMemberMax: Int = 4
    var lastTerrain: TerrainId? = null

    fun build(): RealmState =
        RealmState(
            day = day,
            hour = hour,
            minute = minute,
            rngState = rngState,
            activeParty = checkNotNull(activeParty) { "realm requires activeParty" },
            currentMap = currentMap,
            currentMode = currentMode,
            currentTown = currentTown,
            banished = banished,
            lastPlayedDay = lastPlayedDay ?: day,
            partyMemberMax = partyMemberMax,
            lastTerrain = lastTerrain,
        )
}

@RylNextDsl
class LocationStateBuilder {
    private val placements = mutableListOf<LocationPlacement>()

    infix fun LocationId.at(point: GridPoint) {
        placements += LocationPlacement(this, point)
    }

    fun build(): List<LocationPlacement> = placements.toList()
}

@RylNextDsl
class NpcStateBuilder {
    private val placements = mutableListOf<NpcPlacement>()

    infix fun NpcId.at(position: WorldPosition) {
        placements += NpcPlacement(this, position)
    }

    fun build(): List<NpcPlacement> = placements.toList()
}

@RylNextDsl
class PartyStateBuilder(private val id: PartyId) {
    var player: String = ""
    var name: String = ""
    var position: WorldPosition = Wilderness.xy(0, 0)
    var facing: Facing? = null
    private var resourcesState = PartyResources(0, 0, 0, 0, 0)
    private var countersState = PartyCounters(
        memberCount = null,
        kills = 0,
        waitingMail = false,
        attacked = false,
        attackedToday = emptyList(),
    )
    private val flags = mutableListOf<FlagState>()
    private val milestones = mutableListOf<GameMilestoneId>()
    private val inventory = mutableListOf<InventorySlotState>()
    private val members = mutableListOf<PartyMemberState>()
    private val visitedNpcs = mutableMapOf<NpcId, NpcVisitState>()
    private val journal = mutableListOf<JournalEntryState>()

    fun resources(body: PartyResourcesBuilder.() -> Unit) {
        resourcesState = PartyResourcesBuilder().apply(body).build()
    }

    fun counters(body: PartyCountersBuilder.() -> Unit) {
        countersState = PartyCountersBuilder().apply(body).build()
    }

    fun flags(body: StateFlagBuilder.() -> Unit) {
        flags += StateFlagBuilder().apply(body).build()
    }

    fun milestones(body: PartyMilestonesBuilder.() -> Unit) {
        milestones += PartyMilestonesBuilder().apply(body).build()
    }

    fun inventory(body: InventoryStateBuilder.() -> Unit) {
        inventory += InventoryStateBuilder().apply(body).build()
    }

    fun members(body: PartyMembersBuilder.() -> Unit) {
        members += PartyMembersBuilder().apply(body).build()
    }

    fun visited(vararg npcs: NpcId) {
        npcs.forEach { npc -> visitedNpcs[npc] = NpcVisitState(npc = npc, visited = true) }
    }

    fun npcVisits(body: NpcVisitBuilder.() -> Unit) {
        NpcVisitBuilder().apply(body).build().forEach { visitedNpcs[it.npc] = it }
    }

    fun journal(vararg entries: TextId) {
        journal += entries.map(JournalEntryState::Text)
    }

    fun journalLine(text: String) {
        journal += JournalEntryState.Line(text)
    }

    fun journal(body: JournalBuilder.() -> Unit) {
        journal += JournalBuilder().apply(body).build()
    }

    fun build(): PartyState =
        PartyState(
            id = id,
            player = player,
            name = name,
            position = position,
            facing = facing,
            resources = resourcesState,
            counters = countersState,
            flags = flags.toList(),
            milestones = milestones.toList(),
            inventory = inventory.sortedBy { it.slot },
            members = members.toList(),
            visitedNpcs = visitedNpcs.values.sortedBy { it.npc.slug },
            journal = journal.toList(),
        )
}

@RylNextDsl
class PartyResourcesBuilder {
    var gold: Int = 0
    var torches: Int = 0
    var food: Int = 0
    var timeLeft: Int = 0
    var restedHours: Int = 0

    fun build(): PartyResources = PartyResources(gold, torches, food, timeLeft, restedHours)
}

@RylNextDsl
class PartyCountersBuilder {
    var memberCount: Int? = null
    var kills: Int = 0
    var waitingMail: Boolean = false
    var attacked: Boolean = false
    private val attackedToday = mutableMapOf<PartyId, PartyAttackRecord>()

    fun attackToday(body: AttackTodayBuilder.() -> Unit) {
        AttackTodayBuilder().apply(body).build().forEach { attackedToday[it.party] = it }
    }

    fun build(): PartyCounters =
        PartyCounters(memberCount, kills, waitingMail, attacked, attackedToday.values.sortedBy { it.party.slug })
}

@RylNextDsl
class AttackTodayBuilder {
    private val records = mutableMapOf<PartyId, PartyAttackRecord>()

    fun party(party: PartyId, attacked: Boolean) {
        records[party] = PartyAttackRecord(party = party, attacked = attacked)
    }

    fun attacked(party: PartyId) {
        party(party, attacked = true)
    }

    fun clear(party: PartyId) {
        party(party, attacked = false)
    }

    fun build(): List<PartyAttackRecord> = records.values.sortedBy { it.party.slug }
}

@RylNextDsl
class StateFlagBuilder {
    private val flags = mutableListOf<FlagState>()

    fun mark(state: FlagState) {
        flags += state
    }

    fun build(): List<FlagState> = flags.toList()
}

@RylNextDsl
class PartyMilestonesBuilder {
    private val milestones = mutableListOf<GameMilestoneId>()

    fun achieved(milestone: GameMilestoneId) {
        milestones += milestone
    }

    fun build(): List<GameMilestoneId> = milestones.toList()
}

@RylNextDsl
class NpcVisitBuilder {
    private val visits = mutableMapOf<NpcId, NpcVisitState>()

    fun visited(npc: NpcId) {
        visits[npc] = NpcVisitState(npc = npc, visited = true)
    }

    fun notVisited(npc: NpcId) {
        visits[npc] = NpcVisitState(npc = npc, visited = false)
    }

    fun build(): List<NpcVisitState> = visits.values.sortedBy { it.npc.slug }
}

@RylNextDsl
class JournalBuilder {
    private val entries = mutableListOf<JournalEntryState>()

    fun entry(id: TextId) {
        entries += JournalEntryState.Text(id)
    }

    fun line(text: String) {
        entries += JournalEntryState.Line(text)
    }

    fun build(): List<JournalEntryState> = entries.toList()
}

@RylNextDsl
class InventoryStateBuilder {
    private val slots = mutableMapOf<Int, InventorySlotState>()

    fun slot(index: Int): InventorySlotBuilder =
        InventorySlotBuilder(index) { slot -> slots[index] = slot }

    fun build(): List<InventorySlotState> = slots.values.sortedBy { it.slot }
}

@RylNextDsl
class InventorySlotBuilder(
    private val index: Int,
    private val record: (InventorySlotState) -> Unit,
) {
    fun empty() {
        record(InventorySlotState(index, null))
    }

    fun item(item: ItemId) {
        record(InventorySlotState(index, item))
    }
}

@RylNextDsl
class PartyMembersBuilder {
    private val members = mutableListOf<PartyMemberState>()

    fun member(name: String, body: PartyMemberBuilder.() -> Unit) {
        members += PartyMemberBuilder(name).apply(body).build()
    }

    fun build(): List<PartyMemberState> = members.toList()
}

@RylNextDsl
class PartyMemberBuilder(private val name: String) {
    var gender: Gender = Gender.Male
    var race: Race = Race.Human
    var klass: PcClass = PcClass.Thief
    var level: Int = 1
    var health: Int = 1
    var hitPoints: HitPoints = 1 of 1
    var weaponSkill: Int = 0
    var wisdom: Int = 0
    var mana: Mana = 0 manaOf 0
    var charisma: Int = 0
    private var equipmentState: EquipmentState = EquipmentState(DslDefaultItems.none, DslDefaultItems.none)
    var armor: ItemId
        get() = equipmentState.armor
        set(value) {
            equipmentState = equipmentState.copy(armor = value)
        }
    var weapon: ItemId
        get() = equipmentState.weapon
        set(value) {
            equipmentState = equipmentState.copy(weapon = value)
        }
    var experience: Int = 0
    private var dead: Boolean = false

    fun equipped(body: EquipmentBuilder.() -> Unit) {
        equipmentState = EquipmentBuilder(equipmentState).apply(body).build()
    }

    fun alive() {
        dead = false
    }

    fun dead() {
        dead = true
    }

    fun build(): PartyMemberState =
        PartyMemberState(
            name = name,
            gender = gender,
            race = race,
            pcClass = klass,
            level = level,
            health = health,
            hitPoints = hitPoints,
            weaponSkill = weaponSkill,
            wisdom = wisdom,
            mana = mana,
            charisma = charisma,
            equipment = equipmentState,
            experience = experience,
            dead = dead,
        )
}

@RylNextDsl
class EquipmentBuilder(initial: EquipmentState = EquipmentState(DslDefaultItems.none, DslDefaultItems.none)) {
    var weapon: ItemId = initial.weapon
    var armor: ItemId = initial.armor

    fun build(): EquipmentState = EquipmentState(weapon, armor)
}

fun nativePathsIn(model: GameModel): Set<String> {
    val paths = mutableSetOf<String>()

    lateinit var int: (IntAst) -> Unit
    lateinit var bool: (BoolAst) -> Unit

    int = fun(ast: IntAst) {
        when (ast) {
            is IntAst.Lit -> Unit
            is IntAst.Var -> Unit
            is IntAst.Binary -> {
                int(ast.left)
                int(ast.right)
            }
            is IntAst.If -> {
                bool(ast.cond)
                int(ast.thenValue)
                int(ast.elseValue)
            }
            is IntAst.Random -> {
                int(ast.lo)
                int(ast.hi)
            }
            is IntAst.MemberField -> int(ast.member)
            is IntAst.MonsterField -> int(ast.monster)
            is IntAst.BattleFlag -> Unit
            is IntAst.BattleQuery -> Unit
            is IntAst.RealmScalar -> Unit
            is IntAst.PartyScalar -> Unit
            is IntAst.InventoryPoolCount -> Unit
            is IntAst.InventorySlotCost -> int(ast.slot)
            is IntAst.NativeInt -> paths += ast.path
        }
    }

    bool = fun(ast: BoolAst) {
        when (ast) {
            is BoolAst.Cmp -> {
                int(ast.left)
                int(ast.right)
            }
            is BoolAst.Not -> bool(ast.expr)
            is BoolAst.And -> {
                bool(ast.left)
                bool(ast.right)
            }
            is BoolAst.Or -> {
                bool(ast.left)
                bool(ast.right)
            }
            is BoolAst.FlagIs -> Unit
            is BoolAst.MilestoneAchieved -> Unit
            is BoolAst.MemberFlag -> int(ast.member)
            is BoolAst.MonsterFlag -> int(ast.monster)
            is BoolAst.BattleFlag -> Unit
            is BoolAst.RuntimeFlag -> Unit
        }
    }

    fun str(ast: StrAst) {
        when (ast) {
            is StrAst.NativeString -> {
                paths += ast.path
                ast.args.forEach { arg ->
                    when (arg) {
                        is NativeStringArg.IntValue -> int(arg.value)
                        is NativeStringArg.Literal -> Unit
                    }
                }
            }
        }
    }

    fun text(block: TextBlock) {
        if (block is TextBlock.Structured) {
            block.lines.forEach { line ->
                line.parts.forEach { part ->
                    when (part) {
                        is TextPart.IntValue -> int(part.value)
                        is TextPart.StringValue -> str(part.value)
                        is TextPart.Literal,
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
                        TextPart.ConversationNpcName -> Unit
                    }
                }
            }
        }
    }

    fun ops(items: List<Op>) {
        items.forEach { op ->
            when (op) {
                is Op.Screen -> text(op.text)
                is Op.Emit -> Unit
                is Op.EmitIndexed -> int(op.value)
                is Op.WaitInput -> Unit
                is Op.Pause -> Unit
                is Op.View -> Unit
                is Op.LetInt -> int(op.value)
                is Op.BindText -> str(op.value)
                is Op.Set -> int(op.value)
                is Op.Add -> int(op.value)
                is Op.Sub -> int(op.value)
                is Op.AddClamped -> {
                    int(op.value)
                    int(op.max)
                }
                is Op.SetFlag -> Unit
                is Op.AppendJournal -> text(op.text)
                is Op.If -> {
                    bool(op.cond)
                    ops(op.thenOps)
                    ops(op.elseOps)
                }
                is Op.Loop -> ops(op.body)
                is Op.Break -> Unit
                is Op.Stop -> Unit
                is Op.Switch -> {
                    int(op.expr)
                    op.cases.forEach { ops(it.body) }
                }
                is Op.Menu -> {
                    text(op.prompt)
                    op.options.forEach { ops(it.body) }
                    ops(op.defaultOps)
                }
                is Op.AskKey -> text(op.prompt)
                is Op.AskMember -> text(op.prompt)
                is Op.AskInventorySlot -> text(op.prompt)
                is Op.AskText -> {
                    text(op.prompt)
                    ops(op.matchOps)
                    ops(op.missOps)
                }
                is Op.AskMonster -> Unit
                is Op.ForEachMember -> ops(op.body)
                is Op.ForEachMonster -> ops(op.body)
                is Op.Speak -> Unit
                is Op.TellRumour -> Unit
                is Op.RunScene -> Unit
                is Op.Encounter -> Unit
                is Op.EnterDungeon -> Unit
                Op.ConsumeActiveItem -> Unit
                is Op.ClearInventorySlot -> int(op.slot)
                is Op.ConsumeInventoryPool -> int(op.quantity)
                Op.ConsumeTorch -> Unit
                is Op.TeleportParty -> {
                    int(op.directionKey)
                    int(op.distance)
                }
                is Op.DiscoverCave -> {
                    int(op.levels)
                    int(op.moveCost)
                }
                is Op.GrantReward -> op.reward.gold?.let { int(it) }
                is Op.RandomItemOffer -> {
                    text(op.offer.offer)
                    text(op.offer.choicePrompt)
                    text(op.offer.broke)
                    op.offer.messages.cantCarry?.let(::text)
                    op.offer.messages.pickedUp?.let(::text)
                    text(op.offer.deal)
                    text(op.offer.declined)
                }
                is Op.ShopService -> {
                    text(op.shop.closed)
                    text(op.shop.intro)
                    text(op.shop.menu)
                    text(op.shop.goodbye)
                    op.shop.interlude?.let { interlude ->
                        text(interlude.offer)
                        interlude.accepted.forEach(::text)
                        text(interlude.declined)
                    }
                    op.shop.buy?.let { buy ->
                        text(buy.prompt)
                        text(buy.tooExpensive)
                        text(buy.cantCarry)
                        text(buy.deal)
                        buy.unitPrice?.let(::text)
                        buy.singlePrice?.let(::text)
                        text(buy.payPrompt)
                        buy.catalogs.forEach { catalog ->
                            catalog.intro?.let(::text)
                            catalog.pages.forEach { page ->
                                page.footer?.let(::text)
                                page.entries.forEach { entry ->
                                    entry.description?.let(::text)
                                    (entry.purchase as? ShopPurchaseModel.Bulk)?.let { resource ->
                                        text(resource.quantityPrompt)
                                        text(resource.tooMany)
                                    }
                                }
                            }
                        }
                    }
                    op.shop.sell?.let { sell ->
                        text(sell.prompt)
                        text(sell.wrongKind)
                        text(sell.offer)
                        sell.sold?.let(::text)
                    }
                }
                is Op.AchieveMilestone -> Unit
                is Op.NativeCall -> paths += op.path
            }
        }
    }

    model.scenes.forEach { ops(it.ops) }
    model.dungeons.events.forEach { ops(it.ops) }
    model.dialogue.forEach { dialogue ->
        dialogue.branches.forEach { ops(it.ops) }
    }
    model.spells.forEach { ops(it.ops) }
    model.itemUses.forEach { ops(it.ops) }
    model.missions.forEach { it ->
        it.requires?.let { bool(it) }
    }
    return paths
}
