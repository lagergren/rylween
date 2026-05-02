// RylweenDsl.kt -- Kotlin implementation for the Rylween authoring DSL.
//
// This file is implementation/compiler scaffolding: typed handles, immutable
// models, builders, and recorded script ASTs. Generated metadata lives in
// src/generated/kotlin/RylweenGeneratedMetadata.kt. Game-specific content belongs in
// rylween-full-scratch.dsl.kts, with reusable game-script helpers in
// RylweenGameScripts.kt. Validation helpers live in RylweenDslValidation.kt.
//
// Validate the split scratch source with:
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
//      programming. Generated handle classes are migration scaffolding in
//      RylweenGeneratedMetadata.kt; a real content compiler should generate
//      them from the author's declarations.

@file:Suppress(
    "unused",
    "MemberVisibilityCanBePrivate",
    "RedundantVisibilityModifier",
    "UNUSED_PARAMETER",
)

// =============================================================================
// 01. DSL kernel: typed handles and factories
// =============================================================================
//
// Generated content catalogs live in src/generated/kotlin. The kernel keeps the
// reusable types and factories those catalogs are built from.

enum class FlagScope { Party, Encounter }

abstract class FlagRef(
    val slug: String,
    val scope: FlagScope = FlagScope.Party,
    val legacySlot: Int? = null,
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

val declaredFlags = mutableListOf<FlagRef>()

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
    object OfferedItemName : TextPart
    object OfferedItemPrice : TextPart
    object ShopPrice : TextPart
    object ShopCarryCap : TextPart
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

class CharacterTextRef(private val role: NpcRole) {
    val name: TextPart = TextPart.RoleName(role)
    val location: TextPart = TextPart.RoleLocation(role)
}
fun character(role: NpcRole) = CharacterTextRef(role)

class TownTextRef(private val town: TownId) {
    val name: TextPart = TextPart.TownName(town)
    fun near(radius: Int = 5): TextPart = TextPart.FuzzyTownLocation(town, radius)
}
fun town(town: TownRef) = TownTextRef(town.id)
class ItemId private constructor(val slug: String, val legacyId: Int? = null) {
    override fun equals(other: Any?): Boolean =
        other is ItemId && slug == other.slug

    override fun hashCode(): Int = slug.hashCode()

    override fun toString(): String =
        "ItemId(slug=$slug, legacyId=$legacyId)"

    companion object {
        fun authored(slug: String): ItemId = ItemId(slug)

        internal fun generated(slug: String, legacyId: Int): ItemId =
            ItemId(slug, legacyId)
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

        internal fun generated(slug: String, items: List<ItemId>): ItemPoolId =
            ItemPoolId(slug, items)
    }
}
enum class ItemKind { Empty, Weapon, Armor, Gear, Potion, Treasure, Special, MagicItem }
enum class ItemStorageKind { EmptySentinel, SlottedInventory, SpecialMilestone }
data class ItemRecordModel(
    val id: ItemId,
    val kind: ItemKind,
    val displayName: String,
    val cost: Int,
    val storage: ItemStorageKind,
    val weaponDamage: Int? = null,
    val armorClass: Int? = null,
    val potionEffect: PotionEffectModel? = null,
)
enum class PotionEffectKind { HealHitPoints, PoisonHitPoints, RestoreMana }
data class PotionEffectModel(val kind: PotionEffectKind, val amount: Int? = null)
data class MonsterId(val slug: String)
data class EncounterTier(val slug: String)
data class WorldMapId(val slug: String)
data class MapLayerId(val slug: String)
data class TerrainId(val slug: String, val legacyMapCode: Char? = null)
data class TerrainTagId(val slug: String)
data class ImageId(val slug: String)
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
data class GameMilestoneId(val slug: String)
data class LootTableId(val slug: String)
data class RewardBundleId(val slug: String)
data class RumourTableId(val slug: String)
data class RumourId(val slug: String)

fun text(id: String) = TextId(id)
fun textPattern(pattern: String) = TextPattern(pattern)
fun item(slug: String) = ItemId.authored(slug)
internal fun generatedItem(slug: String, legacyId: Int) = ItemId.generated(slug, legacyId)
fun itemPool(slug: String, items: List<ItemId> = emptyList()) = ItemPoolId.authored(slug, items)
internal fun generatedItemPool(slug: String, items: List<ItemId>) =
    ItemPoolId.generated(slug, items)
fun monster(slug: String) = MonsterId(slug)
fun encounterTier(slug: String) = EncounterTier(slug)
fun worldMap(slug: String) = WorldMapId(slug)
fun mapLayer(slug: String) = MapLayerId(slug)
fun terrain(slug: String, legacyMapCode: Char? = null) = TerrainId(slug, legacyMapCode)
fun terrainTag(slug: String) = TerrainTagId(slug)
fun image(slug: String) = ImageId(slug)
fun palette(slug: String) = PaletteId(slug)
fun paletteColor(slug: String) = PaletteColorId(slug)
fun layout(slug: String) = LayoutId(slug)
fun screenRegion(slug: String) = ScreenRegionId(slug)
fun sceneId(slug: String) = SceneId(slug)
fun spellId(slug: String) = SpellId(slug)
fun missionId(slug: String) = MissionId(slug)
fun npcRole(slug: String) = NpcRole(slug)
fun npcJob(slug: String) = NpcJobId(slug)
fun nativeOp(slug: String) = NativeOpId(slug)
fun stateId(slug: String) = StateId(slug)
fun partyId(slug: String) = PartyId(slug)
fun npc(slug: String) = NpcId(slug)
fun location(slug: String) = LocationId(slug)
fun town(slug: String) = TownId(slug)
fun building(slug: String) = BuildingId(slug)
fun formula(slug: String) = FormulaId(slug)
fun keyAction(slug: String) = KeyActionId(slug)
fun dungeon(slug: String) = DungeonId(slug)
fun milestone(slug: String) = GameMilestoneId(slug)
fun lootTable(slug: String) = LootTableId(slug)
fun rewardBundle(slug: String) = RewardBundleId(slug)
fun rumourTable(slug: String) = RumourTableId(slug)
fun rumour(slug: String) = RumourId(slug)

// Engine-native operation handles are part of the reusable runtime boundary,
// not generated game content. Generated metadata may reference these APIs, but
// the kernel cannot depend on generated catalog initialization.
class NativeOpRefs {
    val screenClearText = nativeOp("screen.clear_text")
    val screenClearMapWindow = nativeOp("screen.clear_map_window")
    val screenRedrawMap = nativeOp("screen.redraw_map")
    val screenShowImage = nativeOp("screen.show_image")
    val screenStatusLine = nativeOp("screen.status_line")
    val inventoryAdd = nativeOp("inventory.add")
    val townEnterService = nativeOp("town.enter_service")
    val ydargInit = nativeOp("ydarg.init")
    val ydargClearRoomAtCurrent = nativeOp("ydarg.clear_room_at_current")
    val ydargTryMove = nativeOp("ydarg.try_move")
    val rumourRoll = nativeOp("rumour.roll")
    val ydargRoomAtCurrent = nativeOp("ydarg.room_at_current")
    val ydargExitRequested = nativeOp("ydarg.exit_requested")
    val inputKeyUpper = nativeOp("input.key_upper")
    val partyMemberName = nativeOp("party.member.name")
    val combatMonsterName = nativeOp("combat.monster.name")
    val partyPronounSubject = nativeOp("party.member.pronoun.subject")
    val partyPronounObject = nativeOp("party.member.pronoun.object")
    val partyPronounPossessive = nativeOp("party.member.pronoun.possessive")
    val townNpcName = nativeOp("town.npc.name")
    val npcRoleName = nativeOp("npc.role.name")
    val npcRoleLocation = nativeOp("npc.role.location")
}
val NativeOps = NativeOpRefs()


// =============================================================================
// 02. DSL kernel: recorded script AST
// =============================================================================

sealed interface IntAst {
    data class Lit(val value: Int) : IntAst
    data class Var(val name: String) : IntAst
    data class Binary(val left: IntAst, val op: String, val right: IntAst) : IntAst
    data class Random(val lo: IntAst, val hi: IntAst) : IntAst
    data class MemberField(val member: IntAst, val field: String) : IntAst
    data class MonsterField(val monster: IntAst, val field: String) : IntAst
    data class RealmScalar(val name: String) : IntAst
    data class PartyScalar(val name: String) : IntAst
    data class NativeInt(val path: String, val args: List<String>) : IntAst
}

sealed interface BoolAst {
    data class Cmp(val left: IntAst, val op: String, val right: IntAst) : BoolAst
    data class Not(val expr: BoolAst) : BoolAst
    data class And(val left: BoolAst, val right: BoolAst) : BoolAst
    data class Or(val left: BoolAst, val right: BoolAst) : BoolAst
    data class FlagIs(val slug: String, val nativeValue: String) : BoolAst
    data class MemberFlag(val member: IntAst, val name: String) : BoolAst
    data class MonsterFlag(val monster: IntAst, val name: String) : BoolAst
}

sealed interface StrAst {
    data class NativeString(val path: String, val args: List<String>) : StrAst
}

sealed interface LValueAst {
    data class RealmScalar(val name: String) : LValueAst
    data class PartyScalar(val name: String) : LValueAst
    data class MemberField(val member: IntAst, val field: String) : LValueAst
    data class MonsterField(val monster: IntAst, val field: String) : LValueAst
}

enum class ScriptControlSignal { Continue, BreakNearestLoop, StopScript }
enum class MemberChoiceFilter { Any, AliveOnly, DeadOnly, SpellcasterOnly }
enum class InventorySlotChoiceFilter { Any, NonEmpty, Empty }

sealed interface Op {
    data class Screen(val text: TextBlock) : Op
    data class Emit(val text: TextId) : Op
    data class EmitIndexed(val pattern: TextPattern, val value: IntAst) : Op
    data class WaitInput(val reason: String? = null) : Op
    data class Pause(val millis: Int) : Op
    data class LetInt(val name: String, val value: IntAst) : Op
    data class BindText(val name: String, val value: StrAst) : Op
    data class Set(val target: LValueAst, val value: IntAst) : Op
    data class Add(val target: LValueAst, val value: IntAst) : Op
    data class Sub(val target: LValueAst, val value: IntAst) : Op
    data class AddClamped(val target: LValueAst, val value: IntAst, val max: IntAst) : Op
    data class SetFlag(val slug: String, val nativeValue: String) : Op
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
    data class AskMember(val prompt: TextBlock, val varName: String, val filter: MemberChoiceFilter) : Op
    data class AskInventorySlot(
        val prompt: TextBlock,
        val varName: String,
        val firstKey: Char,
        val slots: IntRange,
        val filter: InventorySlotChoiceFilter,
    ) : Op
    data class AskMonster(val prompt: TextId, val varName: String) : Op
    data class ForEachMember(val varName: String, val includeDead: Boolean, val body: List<Op>) : Op
    data class ForEachMonster(val varName: String, val includeDead: Boolean, val body: List<Op>) : Op
    data class Speak(val building: BuildingId) : Op
    data class RunScene(val scene: SceneId, val bindings: Map<String, String>) : Op
    data class Encounter(val foe: MonsterId, val mandatory: Boolean, val tier: EncounterTier? = null) : Op
    object ConsumeActiveItem : Op
    object ConsumeTorch : Op
    data class GrantReward(val reward: ScriptRewardModel) : Op
    data class RandomItemOffer(val offer: RandomItemOfferModel) : Op
    data class ShopService(val shop: ShopServiceModel) : Op
    data class AchieveMilestone(val milestone: GameMilestoneId) : Op
    data class NativeCall(val path: String, val args: List<String>) : Op
}

data class SwitchCase(val match: Int?, val body: List<Op>)
data class MenuOption(val key: Char, val body: List<Op>)

data class SceneModel(val id: SceneId, val ops: List<Op>)
data class DialogueModel(val npc: NpcId, val branches: List<DialogueBranchModel>)
data class DialogueBranchModel(val whenState: FlagState?, val ops: List<Op>)
data class SpellModel(val id: SpellId, val slot: Int, val baseCost: Int, val name: String, val ops: List<Op>)
data class MagicItemModel(
    val id: ItemId,
    val contexts: Set<ItemUseContext>,
    val consume: ItemConsumePolicy,
    val ops: List<Op>,
)

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
    val key: Char,
    val action: KeyActionId,
    val building: BuildingId?,
    val endsMenu: Boolean,
)

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

enum class LegacySpecialField { Fireshield, Bracelets, Mastercrown, Jazzyfoot, Silverkey }

data class LegacySpecialMapping(
    val field: LegacySpecialField,
    val achievedValue: Int,
)

data class GameMilestoneModel(
    val id: GameMilestoneId,
    val specialInventoryItem: ItemId?,
    val requiredToWin: Boolean,
    val legacySpecial: LegacySpecialMapping?,
)

data class WinConditionModel(
    val required: List<GameMilestoneId>,
    val completedBy: GameMilestoneId?,
)

data class GameProgressModel(
    val milestones: List<GameMilestoneModel>,
    val win: WinConditionModel?,
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
    val key: KeyActionId,
    val kind: CombatActionKind,
    val requirement: CombatActionRequirement,
)

data class PlayerAttackRulesModel(
    val hitBonus: Int,
    val damageSpreadMax: Int,
    val confusionDivisor: Int,
)

data class DefendRulesModel(
    val weaponSkillPenalty: Int,
    val restThreshold: Int,
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
    val buyKey: Char,
    val declineKey: Char,
    val brokePauseMillis: Int,
    val fullPauseMillis: Int,
    val dealPauseMillis: Int,
    val declinedPauseMillis: Int,
)

enum class ShopResourceKind { Torches, Food }
enum class ShopQuantityCap { TorchLimit80, FoodByDonkeys }
enum class ShopZeroQuantityDealPolicy { Always, NonZeroOnly }
enum class ShopSellBargainPolicy { None, SmithHaggle }

object ShopResources {
    val torches: ShopResourceKind = ShopResourceKind.Torches
    val food: ShopResourceKind = ShopResourceKind.Food
}

object ShopQuantityCaps {
    val torchLimit80: ShopQuantityCap = ShopQuantityCap.TorchLimit80
    val foodByDonkeys: ShopQuantityCap = ShopQuantityCap.FoodByDonkeys
}

object ShopZeroQuantityDeal {
    val always: ShopZeroQuantityDealPolicy = ShopZeroQuantityDealPolicy.Always
    val nonZeroOnly: ShopZeroQuantityDealPolicy = ShopZeroQuantityDealPolicy.NonZeroOnly
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
val ShopPrices = ShopPriceRefs()

sealed interface ShopPurchaseModel {
    object InventoryItem : ShopPurchaseModel
    data class Resource(
        val resource: ShopResourceKind,
        val quantityPrompt: TextBlock,
        val cap: ShopQuantityCap,
        val tooMany: TextBlock,
        val zeroQuantityDeal: ShopZeroQuantityDealPolicy,
    ) : ShopPurchaseModel
}

data class ShopItemOfferModel(
    val key: Char,
    val item: ItemId,
    val label: String?,
    val description: TextBlock?,
    val price: ShopPriceModel,
    val purchase: ShopPurchaseModel,
)

data class ShopCatalogPageModel(
    val entries: List<ShopItemOfferModel>,
    val footer: TextBlock?,
)

data class ShopCatalogModel(
    val key: Char,
    val title: String,
    val intro: TextBlock?,
    val pages: List<ShopCatalogPageModel>,
)

data class ShopBuyModel(
    val prompt: TextBlock,
    val catalogs: List<ShopCatalogModel>,
    val tooExpensive: TextBlock,
    val cantCarry: TextBlock,
    val deal: TextBlock,
    val unitPrice: TextBlock?,
    val singlePrice: TextBlock?,
    val payPrompt: TextBlock,
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
    val bargain: ShopSellBargainPolicy,
)

data class ShopInterludeModel(
    val chancePercent: Int,
    val offer: TextBlock,
    val acceptKey: Char,
    val declineKey: Char,
    val accepted: List<TextBlock>,
    val declined: TextBlock,
    val runsRumourAfterAccept: Boolean,
)

data class ShopServiceModel(
    val building: BuildingId,
    val image: ImageId,
    val closed: TextBlock,
    val intro: TextBlock,
    val menu: TextBlock,
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

enum class ItemUseContext { Combat, Inventory }
enum class ItemConsumePolicy { BeforeUse, ScriptControlled, NeverConsumes }

data class StandardBattleModel(
    val opening: EncounterOpeningModel,
    val actions: List<CombatActionModel>,
    val playerRound: PlayerRoundModel,
    val monsterRound: MonsterRoundModel,
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
    val key: Char,
    val action: KeyActionId,
)

data class QuestionMenuActionModel(
    val topic: QuestionTopic,
    val key: Char,
    val action: KeyActionId,
)

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
    val nativeServices: List<EngineServiceUse>,
)

data class SpeakSystemModel(
    val standardConversation: StandardConversationModel?,
    val textTables: SpeakTextTablesModel = SpeakTextTablesModel(),
)

data class GameModel(
    val flags: List<FlagRef>,
    val authoredFlags: List<FlagContentModel>,
    val progress: GameProgressModel,
    val rewards: List<RewardBundleModel>,
    val combat: CombatSystemModel,
    val wilderness: WildernessSystemModel,
    val speak: SpeakSystemModel,
    val towns: List<TownModel>,
    val townSystem: TownSystemModel?,
    val characters: List<CharacterModel>,
    val monsters: List<MonsterModel>,
    val scenes: List<SceneModel>,
    val dialogue: List<DialogueModel>,
    val spells: List<SpellModel>,
    val magicItems: List<MagicItemModel>,
    val missions: List<MissionModel>,
)

sealed interface Trigger {
    data class RandomStep(val percent: Int) : Trigger
}

data class WildernessSystemModel(
    val events: WildernessEventModel = WildernessEventModel(),
    val terrainTransitions: TerrainTransitionModel = TerrainTransitionModel(),
)

data class WildernessEventModel(
    val caveChancePercent: Int = 3,
    val quietChancePercent: Int = 91,
    val encounterSplitPercent: Int = 50,
    val easyEncounterTier: EncounterTier = EncounterTiers.weak,
    val hardEncounterTier: EncounterTier = EncounterTiers.wild,
    val caveDungeon: DungeonId = Dungeons.randomCave,
    val caveDepthRange: IntRange = 3..16,
    val caveEntryCost: Int = 10,
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
val PcClass.legacyId: Int
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

const val InventorySaveSlotMax = 16

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

class IntSlot(
    private val script: ScriptScope,
    private val lvalue: LValueAst,
    override val ast: IntAst,
) : IntExpr() {
    infix fun set(value: IntExpr) = script.record(Op.Set(lvalue, value.ast))
    infix fun set(value: Int) = set(lit(value))
    operator fun plusAssign(value: IntExpr) = script.record(Op.Add(lvalue, value.ast))
    operator fun plusAssign(value: Int) = plusAssign(lit(value))
    operator fun minusAssign(value: IntExpr) = script.record(Op.Sub(lvalue, value.ast))
    operator fun minusAssign(value: Int) = minusAssign(lit(value))
    fun addClamped(value: IntExpr, max: IntExpr) = script.record(Op.AddClamped(lvalue, value.ast, max.ast))
    fun addClamped(value: Int, max: IntExpr) = addClamped(lit(value), max)
}

class RealmRef(private val script: ScriptScope) {
    val hour = IntSlot(script, LValueAst.RealmScalar("hour"), IntAst.RealmScalar("hour"))
    val partyMemberMax = IntSlot(
        script,
        LValueAst.RealmScalar("party_member_max"),
        IntAst.RealmScalar("party_member_max"),
    )
}

class BoolSlot(
    private val script: ScriptScope,
    private val lvalue: LValueAst,
    private val read: BoolAst,
) : BoolExpr() {
    override val ast: BoolAst = read
    infix fun set(value: Boolean) = script.record(Op.Set(lvalue, IntAst.Lit(if (value) 1 else 0)))
}

class PartyRef(private val script: ScriptScope) {
    val gold = IntSlot(script, LValueAst.PartyScalar("gold"), IntAst.PartyScalar("gold"))
    val torches = IntSlot(script, LValueAst.PartyScalar("torches"), IntAst.PartyScalar("torches"))
    val food = IntSlot(script, LValueAst.PartyScalar("food"), IntAst.PartyScalar("food"))
    val timeLeft = IntSlot(script, LValueAst.PartyScalar("time_left"), IntAst.PartyScalar("time_left"))
    val restedHours = IntSlot(script, LValueAst.PartyScalar("rested_hours"), IntAst.PartyScalar("rested_hours"))
    val memberCount = IntSlot(script, LValueAst.PartyScalar("member_count"), IntAst.PartyScalar("member_count"))
    val leader: MemberRef get() = member(lit(1))
    fun member(index: IntExpr): MemberRef = MemberRef(script, index)
}

class MemberRef(private val script: ScriptScope, val index: IntExpr) {
    val hp = intField("hit_points")
    val maxHp = intField("health")
    val mp = intField("magic")
    val level = intField("level")
    val wisdom = intField("wisdom")
    val charisma = intField("charisma")
    val pcClass = intField("class")
    val dead = BoolSlot(
        script,
        LValueAst.MemberField(index.ast, "dead"),
        BoolAst.MemberFlag(index.ast, "dead"),
    )
    val spellcaster: BoolExpr = Bool(BoolAst.MemberFlag(index.ast, "spellcaster"))

    fun isClass(klass: PcClass): BoolExpr = pcClass eq klass.legacyId

    private fun intField(field: String): IntSlot =
        IntSlot(script, LValueAst.MemberField(index.ast, field), IntAst.MemberField(index.ast, field))
}

class MonsterRef(private val script: ScriptScope, val index: IntExpr) {
    val hp = IntSlot(script, LValueAst.MonsterField(index.ast, "hit_points"), IntAst.MonsterField(index.ast, "hit_points"))
    val dead = BoolSlot(
        script,
        LValueAst.MonsterField(index.ast, "dead"),
        BoolAst.MonsterFlag(index.ast, "dead"),
    )
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
    val screenClearText = effect(NativeOps.screenClearText, "text-window rendering primitive")
    val screenClearMapWindow = effect(NativeOps.screenClearMapWindow, "map-window clearing primitive")
    val screenRedrawMap = effect(NativeOps.screenRedrawMap, "map rendering primitive")
    val screenShowImage = effect(NativeOps.screenShowImage, "map-window image rendering primitive")
    val screenStatusLine = effect(NativeOps.screenStatusLine, "status-line rendering primitive")
    val inventoryAdd = effect(NativeOps.inventoryAdd, "legacy inventory slot mutation")
    val townEnterService = effect(NativeOps.townEnterService, "town building-service trampoline")
    val ydargInit = effect(NativeOps.ydargInit, "legacy Ydarg dungeon state setup")
    val ydargClearRoomAtCurrent = effect(NativeOps.ydargClearRoomAtCurrent, "legacy Ydarg room state mutation")
    val ydargTryMove = effect(NativeOps.ydargTryMove, "legacy Ydarg movement and collision")

    val rumourRoll = int(NativeOps.rumourRoll, "imported rumour scene roll bridge")
    val ydargRoomAtCurrent = int(NativeOps.ydargRoomAtCurrent, "legacy Ydarg room table lookup")
    val ydargExitRequested = int(NativeOps.ydargExitRequested, "legacy Ydarg exit flag")
    val inputKeyUpper = int(NativeOps.inputKeyUpper, "keyboard input normalization")

    val partyMemberName = string(NativeOps.partyMemberName, "party-member display name binding")
    val combatMonsterName = string(NativeOps.combatMonsterName, "combat monster display name binding")
    val partyPronounSubject = string(NativeOps.partyPronounSubject, "party-member pronoun binding")
    val partyPronounObject = string(NativeOps.partyPronounObject, "party-member pronoun binding")
    val partyPronounPossessive = string(NativeOps.partyPronounPossessive, "party-member pronoun binding")
    val townNpcName = string(NativeOps.townNpcName, "current-town NPC display name binding")
    val npcRoleName = string(NativeOps.npcRoleName, "role-resolved NPC name binding")
    val npcRoleLocation = string(NativeOps.npcRoleLocation, "role-resolved NPC location binding")

    val all: List<NativeSpec> = listOf(
        screenClearText,
        screenClearMapWindow,
        screenRedrawMap,
        screenShowImage,
        screenStatusLine,
        inventoryAdd,
        townEnterService,
        ydargInit,
        ydargClearRoomAtCurrent,
        ydargTryMove,
        rumourRoll,
        ydargRoomAtCurrent,
        ydargExitRequested,
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
val NativeAllowlist = NativeAllowlistRefs()

fun partyName(member: MemberRef): StrExpr =
    Str(StrAst.NativeString(NativeAllowlist.partyMemberName.path, listOf(render(member.index.ast))))

fun monsterName(monster: MonsterRef): StrExpr =
    Str(StrAst.NativeString(NativeAllowlist.combatMonsterName.path, listOf(render(monster.index.ast))))

fun pronoun(member: MemberRef, case: PronounCase): StrExpr {
    val native = when (case) {
        PronounCase.Subject -> NativeAllowlist.partyPronounSubject
        PronounCase.Object -> NativeAllowlist.partyPronounObject
        PronounCase.Possessive -> NativeAllowlist.partyPronounPossessive
    }
    return Str(StrAst.NativeString(native.path, listOf(render(member.index.ast))))
}

fun townNpcName(job: NpcJobId): StrExpr =
    Str(StrAst.NativeString(NativeAllowlist.townNpcName.path, listOf(render(job))))

fun npcName(role: NpcRole): StrExpr =
    Str(StrAst.NativeString(NativeAllowlist.npcRoleName.path, listOf(role.slug)))

fun npcLocation(role: NpcRole): StrExpr =
    Str(StrAst.NativeString(NativeAllowlist.npcRoleLocation.path, listOf(role.slug)))

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
    val realm = RealmRef(this)
    val party = PartyRef(this)
    val ask = AskScope(this)
    val choice = ChoiceScope(this)
    val native = NativeScope(this)

    val currentCaster: MemberRef
        get() = party.member(v("caster_idx"))

    fun record(op: Op) {
        ctx.record(ops, op)
    }

    fun fresh(prefix: String): String = ctx.fresh(prefix)

    fun screen(id: TextId) = record(Op.Screen(TextBlock.Ref(id)))
    fun screen(vararg lines: String) = record(Op.Screen(TextBlock.Inline(lines.toList())))
    fun screen(body: StructuredTextBlockBuilder.() -> Unit) =
        record(Op.Screen(StructuredTextBlockBuilder().apply(body).build()))

    fun say(id: TextId) = screen(id)
    fun say(vararg lines: String) = screen(*lines)
    fun say(body: StructuredTextBlockBuilder.() -> Unit) = screen(body)
    fun emit(id: TextId) = record(Op.Emit(id))
    fun emitIndexed(pattern: TextPattern, value: IntExpr) = record(Op.EmitIndexed(pattern, value.ast))
    fun waitInput(reason: String? = null) = record(Op.WaitInput(reason))
    fun pause(ms: Int) = record(Op.Pause(ms))

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

    infix fun LocalIntRef.set(value: Int) = set(lit(value))

    operator fun LocalIntRef.plusAssign(value: IntExpr) {
        record(Op.LetInt(name, (this + value).ast))
    }

    operator fun LocalIntRef.plusAssign(value: Int) = plusAssign(lit(value))

    operator fun LocalIntRef.minusAssign(value: IntExpr) {
        record(Op.LetInt(name, (this - value).ast))
    }

    operator fun LocalIntRef.minusAssign(value: Int) = minusAssign(lit(value))

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

    fun stop(reason: String? = null) = record(Op.Stop(reason))
    fun breakLoop(reason: String? = null) = record(Op.Break(reason))

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
        record(builder.build(TextBlock.Inline(promptLines.toList())))
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

    fun healLivingMembers(amount: Int) = healLivingMembers(lit(amount))

    fun run(scene: SceneId, vararg bindings: Pair<String, Any>) {
        record(Op.RunScene(scene, bindings.associate { it.first to render(it.second) }))
    }

    fun speak(building: BuildingId) {
        record(Op.Speak(building))
    }

    fun encounter(monster: MonsterId, mandatory: Boolean = false, tier: EncounterTier? = null) {
        record(Op.Encounter(monster, mandatory, tier))
    }

    fun consumeActiveItem() = record(Op.ConsumeActiveItem)

    fun consumeTorch() = record(Op.ConsumeTorch)

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

    private fun capture(body: ScriptScope.() -> Unit): List<Op> {
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
class RandomItemOfferTextBuilder {
    private val text = StructuredTextBlockBuilder()

    val itemName: TextPart = TextPart.OfferedItemName
    val itemPrice: TextPart = TextPart.OfferedItemPrice

    fun line(vararg parts: Any) {
        text.line(*parts)
    }

    fun lines(vararg lines: String) {
        text.lines(*lines)
    }

    fun lines(body: RandomItemOfferTextBuilder.() -> Unit) {
        body()
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

    var buyKey: Char = 'B'
    var declineKey: Char = 'N'
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
            buyKey = buyKey.uppercaseChar(),
            declineKey = declineKey.uppercaseChar(),
            brokePauseMillis = brokePauseMillis,
            fullPauseMillis = fullPauseMillis,
            dealPauseMillis = dealPauseMillis,
            declinedPauseMillis = declinedPauseMillis,
        )
}

@RylNextDsl
class ShopTextBuilder {
    private val text = StructuredTextBlockBuilder()

    val price: TextPart = TextPart.ShopPrice
    val cap: TextPart = TextPart.ShopCarryCap

    fun line(vararg parts: Any) {
        text.line(*parts)
    }

    fun lines(vararg lines: String) {
        text.lines(*lines)
    }

    fun lines(body: ShopTextBuilder.() -> Unit) {
        body()
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
            goodbye = goodbyeText,
            buy = buyModel,
            sell = sellModel,
            interlude = interludeModel,
        )
}

@RylNextDsl
class ShopInterludeBuilder(private val chancePercent: Int) {
    private var offerText: TextBlock? = null
    private val acceptedTexts = mutableListOf<TextBlock>()
    private var declinedText: TextBlock? = null
    var acceptKey: Char = 'A'
    var declineKey: Char = 'D'
    var runsRumourAfterAccept: Boolean = false

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
            acceptKey = acceptKey.uppercaseChar(),
            declineKey = declineKey.uppercaseChar(),
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

    fun exactFullInventoryReject() {
        applicationModel = applicationModel.copy(overflow = RewardOverflowPolicy.SkipIfInventoryFull)
    }

    fun catalog(key: Char, title: String, body: ShopCatalogBuilder.() -> Unit) {
        catalogModels += ShopCatalogBuilder(key, title).apply(body).build()
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
            inventoryApplication = applicationModel,
        )
}

@RylNextDsl
class ShopCatalogBuilder(private val key: Char, private val title: String) {
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
        looseEntries += ShopItemOfferModel(
            key = key.uppercaseChar(),
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
        item(key, item, label, price, structuredText(description))
    }

    fun resource(
        key: Char,
        item: ItemId,
        resource: ShopResourceKind,
        cap: ShopQuantityCap,
        tooMany: TextBlock,
        label: String? = null,
        price: ShopPriceModel = ShopPriceModel.ItemCost,
        zeroQuantityDeal: ShopZeroQuantityDealPolicy = ShopZeroQuantityDealPolicy.NonZeroOnly,
        quantityPrompt: TextBlock = inlineText("How many? "),
        description: TextBlock? = null,
    ) {
        looseEntries += ShopItemOfferModel(
            key = key.uppercaseChar(),
            item = item,
            label = label,
            description = description,
            price = price,
            purchase = ShopPurchaseModel.Resource(resource, quantityPrompt, cap, tooMany, zeroQuantityDeal),
        )
    }

    fun resource(
        key: Char,
        item: ItemId,
        resource: ShopResourceKind,
        cap: ShopQuantityCap,
        tooMany: TextBlock,
        label: String? = null,
        price: ShopPriceModel = ShopPriceModel.ItemCost,
        zeroQuantityDeal: ShopZeroQuantityDealPolicy = ShopZeroQuantityDealPolicy.NonZeroOnly,
        quantityPrompt: TextBlock = inlineText("How many? "),
        description: StructuredTextBlockBuilder.() -> Unit,
    ) {
        resource(
            key,
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
            key = key.uppercaseChar(),
            title = title,
            intro = introText,
            pages = pages.toList() + looseEntries.takeIf { it.isNotEmpty() }?.let {
                listOf(ShopCatalogPageModel(it.toList(), footer = null))
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
        entries += ShopItemOfferModel(
            key = key.uppercaseChar(),
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
    var bargain: ShopSellBargainPolicy = ShopSellBargainPolicy.None

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

    fun smithHaggle() {
        bargain = ShopSellBargainPolicy.SmithHaggle
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
            bargain = bargain,
        )
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
    val cases = mutableListOf<SwitchCase>()

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
        options += MenuOption(key.uppercaseChar(), recordScript(ctx, body))
    }

    fun default(body: ScriptScope.() -> Unit) {
        defaultOps = recordScript(ctx, body)
    }

    fun build(prompt: TextBlock): Op.Menu = Op.Menu(prompt, options.toList(), defaultOps)
}

class AskScope(private val script: ScriptScope) {
    fun member(prompt: TextId, filter: MemberChoiceFilter = MemberChoiceFilter.Any): MemberRef =
        member(TextBlock.Ref(prompt), filter)

    fun member(prompt: TextBlock, filter: MemberChoiceFilter = MemberChoiceFilter.Any): MemberRef {
        val name = script.fresh("member")
        script.record(Op.AskMember(prompt, name, filter))
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
        script.menu(prompt) {
            option('Y', yes)
            option('N', no)
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

    fun member(prompt: TextId, filter: MemberChoiceFilter = MemberChoiceFilter.Any): MemberRef =
        script.ask.member(prompt, filter)

    fun member(prompt: TextBlock, filter: MemberChoiceFilter = MemberChoiceFilter.Any): MemberRef =
        script.ask.member(prompt, filter)

    fun member(
        prompt: TextId,
        filter: MemberChoiceFilter = MemberChoiceFilter.Any,
        body: ScriptScope.(MemberRef) -> Unit,
    ) {
        script.body(member(prompt, filter))
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

class NativeScope(private val script: ScriptScope) {
    // Native is deliberately narrow: rendering, keyboard input, inventory slot
    // plumbing, legacy Ydarg state, and helper calls whose implementation is
    // genuinely engine-owned. Game rules should become typed DSL verbs before
    // they are allowed here.
    val screen = NativeScreen(script)
    val rumour = NativeRumour(script)
    val inventory = NativeInventory(script)
    val town = NativeTown(script)
    val ydarg = NativeYdarg(script)
    val input = NativeInput(script)
}

class NativeScreen(private val script: ScriptScope) {
    fun clearText() = script.record(Op.NativeCall(NativeAllowlist.screenClearText.path, emptyList()))
    fun clearMapWindow() = script.record(Op.NativeCall(NativeAllowlist.screenClearMapWindow.path, emptyList()))
    fun redrawMap() = script.record(Op.NativeCall(NativeAllowlist.screenRedrawMap.path, emptyList()))
    fun showImage(image: ImageId) = script.record(Op.NativeCall(NativeAllowlist.screenShowImage.path, listOf(render(image))))
    fun statusLine() = script.record(Op.NativeCall(NativeAllowlist.screenStatusLine.path, emptyList()))
}

class NativeRumour(private val script: ScriptScope) {
    fun roll(): IntExpr = Expr(IntAst.NativeInt(NativeAllowlist.rumourRoll.path, emptyList()))
}

class NativeInventory(private val script: ScriptScope) {
    fun add(itemId: IntExpr) =
        script.record(Op.NativeCall(NativeAllowlist.inventoryAdd.path, listOf(render(itemId))))
}

class NativeTown(private val script: ScriptScope) {
    fun enterService(building: BuildingId) =
        script.record(Op.NativeCall(NativeAllowlist.townEnterService.path, listOf(render(building))))
}

class NativeYdarg(private val script: ScriptScope) {
    fun init() = script.record(Op.NativeCall(NativeAllowlist.ydargInit.path, emptyList()))
    fun roomAtCurrent(): IntExpr = Expr(IntAst.NativeInt(NativeAllowlist.ydargRoomAtCurrent.path, emptyList()))
    fun clearRoomAtCurrent() =
        script.record(Op.NativeCall(NativeAllowlist.ydargClearRoomAtCurrent.path, emptyList()))

    fun exitRequested(): BoolExpr =
        Bool(BoolAst.Cmp(IntAst.NativeInt(NativeAllowlist.ydargExitRequested.path, emptyList()), "ne", IntAst.Lit(0)))

    fun tryMove(key: IntExpr) =
        script.record(Op.NativeCall(NativeAllowlist.ydargTryMove.path, listOf(render(key))))
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

@RylNextDsl
class GameBuilder {
    private val authoredFlags = mutableListOf<FlagContentModel>()
    private var progressModel = GameProgressModel(emptyList(), null)
    private val rewardsModel = mutableListOf<RewardBundleModel>()
    private var combatModel = CombatSystemModel(null)
    private var wildernessModel = WildernessSystemModel()
    private var speakModel = SpeakSystemModel(null)
    private val towns = mutableListOf<TownModel>()
    private var townSystemModel: TownSystemModel? = null
    private val characters = mutableListOf<CharacterModel>()
    private val monsters = mutableListOf<MonsterModel>()
    private val scenes = mutableListOf<SceneModel>()
    private val dialogue = mutableListOf<DialogueModel>()
    private val spells = mutableListOf<SpellModel>()
    private val magicItems = mutableListOf<MagicItemModel>()
    private val missions = mutableListOf<MissionModel>()

    fun flags(body: FlagContentBuilder.() -> Unit) {
        authoredFlags += FlagContentBuilder().apply(body).build()
    }

    fun gameProgress(body: GameProgressBuilder.() -> Unit) {
        progressModel = GameProgressBuilder().apply(body).build()
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

    fun magicItem(id: ItemId, body: MagicItemUseBuilder.() -> Unit) {
        magicItems += MagicItemUseBuilder(id).apply(body).build()
    }

    fun build(): GameModel =
        GameModel(
            flags = declaredFlags.toList(),
            authoredFlags = authoredFlags.toList(),
            progress = progressModel,
            rewards = rewardsModel.toList(),
            combat = combatModel,
            wilderness = wildernessModel,
            speak = speakModel,
            towns = towns.toList(),
            townSystem = townSystemModel,
            characters = characters.toList(),
            monsters = monsters.toList(),
            scenes = scenes.toList(),
            dialogue = dialogue.toList(),
            spells = spells.toList(),
            magicItems = magicItems.toList(),
            missions = missions.toList(),
        )

}

@RylNextDsl
class WildernessSystemBuilder {
    private var eventsModel = WildernessEventBuilder().build()
    private var terrainTransitionModel = TerrainTransitionBuilder().build()

    fun events(body: WildernessEventBuilder.() -> Unit) {
        eventsModel = WildernessEventBuilder().apply(body).build()
    }

    fun terrainTransitions(body: TerrainTransitionBuilder.() -> Unit) {
        terrainTransitionModel = TerrainTransitionBuilder().apply(body).build()
    }

    fun build(): WildernessSystemModel =
        WildernessSystemModel(
            events = eventsModel,
            terrainTransitions = terrainTransitionModel,
        )
}

@RylNextDsl
class WildernessEventBuilder {
    var caveChancePercent: Int = 3
    var quietChancePercent: Int = 91
    var encounterSplitPercent: Int = 50
    var easyEncounterTier: EncounterTier = EncounterTiers.weak
    var hardEncounterTier: EncounterTier = EncounterTiers.wild
    var caveDungeon: DungeonId = Dungeons.randomCave
    var caveDepthRange: IntRange = 3..16
    var caveEntryCost: Int = 10
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

    fun town(ref: TownRef, body: TownBuilder.() -> Unit) {
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
        service(Buildings.merchant)
    }

    fun inn() {
        service(Buildings.inn)
    }

    fun tavern() {
        service(Buildings.tavern)
    }

    fun smith() {
        service(Buildings.smith)
    }

    fun church() {
        service(Buildings.church)
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

    fun menu(body: TownMenuBuilder.() -> Unit) {
        menuModel = TownMenuBuilder().apply(body).build()
    }

    fun buildingServices(body: BuildingServiceCatalogBuilder.() -> Unit) {
        buildingServiceModels = BuildingServiceCatalogBuilder().apply(body).build()
    }

    fun build(): TownSystemModel =
        TownSystemModel(
            menu = menuModel,
            buildingServices = buildingServiceModels,
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
        service(key, Buildings.church, KeyActions.enterChurch)
    }

    fun smith(key: Char = 'S') {
        service(key, Buildings.smith, KeyActions.enterSmith)
    }

    fun inn(key: Char = 'I') {
        service(key, Buildings.inn, KeyActions.enterInn)
    }

    fun merchant(key: Char = 'M') {
        service(key, Buildings.merchant, KeyActions.enterMerchant)
    }

    fun tavern(key: Char = 'T') {
        service(key, Buildings.tavern, KeyActions.enterTavern)
    }

    fun leave(key: Char = 'L', action: KeyActionId = KeyActions.leaveTown) {
        actions += TownMenuActionModel(key, action, building = null, endsMenu = true)
    }

    fun service(key: Char, building: BuildingId, action: KeyActionId) {
        actions += TownMenuActionModel(key, action, building, endsMenu = false)
    }

    fun build(): List<TownMenuActionModel> = actions.toList()
}

@RylNextDsl
class BuildingServiceCatalogBuilder {
    private val services = mutableListOf<BuildingServiceModel>()

    fun church(body: BuildingServiceBuilder.() -> Unit = {}) {
        building(Buildings.church, KeyActions.enterChurch, Scenes.Towne.church, body)
    }

    fun smith(body: BuildingServiceBuilder.() -> Unit = {}) {
        building(Buildings.smith, KeyActions.enterSmith, Scenes.Towne.smith, body)
    }

    fun inn(body: BuildingServiceBuilder.() -> Unit = {}) {
        building(Buildings.inn, KeyActions.enterInn, Scenes.Towne.inn, body)
    }

    fun merchant(body: BuildingServiceBuilder.() -> Unit = {}) {
        building(Buildings.merchant, KeyActions.enterMerchant, Scenes.Towne.merchant, body)
    }

    fun tavern(body: BuildingServiceBuilder.() -> Unit = {}) {
        building(Buildings.tavern, KeyActions.enterTavern, Scenes.Towne.tavern, body)
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
    var job: NpcJobId = NpcJobs.merchant
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
    var hitPoints: IntRange = 1..1
    var weaponSkill: Int = 0
    var spellPower: Int = 0
    var tier: EncounterTier? = null
    var immune: Boolean = false

    fun build(): MonsterModel =
        MonsterModel(id, name, count, hitPoints, weaponSkill, spellPower, tier, immune)
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

    fun requires(state: FlagState) {
        requires = state.ast
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
    private var legacySpecial: LegacySpecialMapping? = null

    fun showsAs(item: ItemId) {
        specialInventoryItem = item
    }

    fun requiredToWin() {
        requiredToWin = true
    }

    fun legacyPartyField(field: LegacySpecialField, achievedValue: Int = 1) {
        legacySpecial = LegacySpecialMapping(field, achievedValue)
    }

    fun build(): GameMilestoneModel =
        GameMilestoneModel(id, specialInventoryItem, requiredToWin, legacySpecial)
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
class MagicItemUseBuilder(private val id: ItemId) {
    private val contexts = mutableSetOf(ItemUseContext.Combat)
    var consume: ItemConsumePolicy = ItemConsumePolicy.BeforeUse
    private var ops = emptyList<Op>()

    fun usableIn(vararg contexts: ItemUseContext) {
        this.contexts.clear()
        this.contexts += contexts
    }

    fun effect(body: ScriptScope.() -> Unit) {
        ops = recordScript(body)
    }

    fun build(): MagicItemModel =
        MagicItemModel(id, contexts.toSet(), consume, ops)
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
    var experience: FormulaId = Formulas.rewardXpPerSurvivor
    var gold: FormulaId = Formulas.rewardGoldParty
    private var applicationModel = RewardApplicationBuilder().build()
    private var lootModel = LootTableBuilder(LootTables.standardBattle).build()

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
    var treasure: ItemPoolId = ItemPools.battleSpoils
    var magic: ItemPoolId = ItemPools.magicItems

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
    private var actionModels = PlayerCombatActionsBuilder().build()
    private var playerRoundModel = PlayerRoundBuilder().build()
    private var monsterRoundModel = MonsterRoundBuilder().build()
    var rewards: RewardBundleId = Rewards.standardBattle
    var encounterFoeCountMax: Int = 8

    fun opening(body: EncounterOpeningBuilder.() -> Unit) {
        openingModel = EncounterOpeningBuilder(openingModel).apply(body).build()
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
            actions = actionModels,
            playerRound = playerRoundModel,
            monsterRound = monsterRoundModel,
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
class SpeakBuilder {
    private var standardConversationModel: StandardConversationModel? = null
    private var textTablesModel = SpeakTextTablesModel()

    fun standardConversation(body: StandardConversationBuilder.() -> Unit) {
        standardConversationModel = StandardConversationBuilder().apply(body).build()
    }

    fun textTables(body: SpeakTextTablesBuilder.() -> Unit) {
        textTablesModel = SpeakTextTablesBuilder().apply(body).build()
    }

    fun build(): SpeakSystemModel = SpeakSystemModel(standardConversationModel, textTablesModel)
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
class StructuredTextBlockBuilder {
    private val lines = mutableListOf<TextLine>()

    fun line(vararg parts: Any) {
        lines += TextLine(parts.map(::toTextPart))
    }

    fun lines(vararg lines: String) {
        lines.forEach { line(it) }
    }

    fun lines(body: StructuredTextBlockBuilder.() -> Unit) {
        body()
    }

    fun build(): TextBlock = TextBlock.Structured(lines.toList())

    private fun toTextPart(part: Any): TextPart =
        when (part) {
            is TextPart -> part
            is IntExpr -> TextPart.IntValue(part.ast)
            is StrExpr -> TextPart.StringValue(part.ast)
            is String -> TextPart.Literal(part)
            else -> TextPart.Literal(part.toString())
        }
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
class RumourEntryBuilder(private val id: RumourId?) {
    private val text = StructuredTextBlockBuilder()
    private val excludedStates = mutableListOf<FlagState>()

    fun line(vararg parts: Any) {
        text.line(*parts)
    }

    fun lines(vararg lines: String) {
        text.lines(*lines)
    }

    fun lines(body: StructuredTextBlockBuilder.() -> Unit) {
        text.body()
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

    fun menu(body: ConversationMenuBuilder.() -> Unit) {
        menuModel = ConversationMenuBuilder().apply(body).build()
    }

    fun chitchat(body: ChitchatBuilder.() -> Unit) {
        chitchatModel = ChitchatBuilder(chitchatModel).apply(body).build()
    }

    fun rumour(body: ConversationRumourBuilder.() -> Unit) {
        rumourModel = ConversationRumourBuilder(rumourModel).apply(body).build()
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
class ConversationMenuBuilder {
    private val actions = mutableListOf<ConversationMenuActionModel>()
    private var questionActions = emptyList<QuestionMenuActionModel>()

    fun chitchat(key: Char = 'C', action: KeyActionId = KeyActions.speakChitchat) {
        actions += ConversationMenuActionModel(ConversationTopic.Chitchat, key, action)
    }

    fun question(
        key: Char = 'Q',
        action: KeyActionId = KeyActions.speakQuestion,
        body: QuestionMenuBuilder.() -> Unit,
    ) {
        actions += ConversationMenuActionModel(ConversationTopic.Question, key, action)
        questionActions = QuestionMenuBuilder().apply(body).build()
    }

    fun rumour(key: Char = 'R', action: KeyActionId = KeyActions.speakRumour) {
        actions += ConversationMenuActionModel(ConversationTopic.Rumour, key, action)
    }

    fun bye(key: Char = 'B', action: KeyActionId = KeyActions.speakBye) {
        actions += ConversationMenuActionModel(ConversationTopic.Bye, key, action)
    }

    fun build(): ConversationMenuModel =
        ConversationMenuModel(actions.toList(), questionActions)
}

@RylNextDsl
class QuestionMenuBuilder {
    private val actions = mutableListOf<QuestionMenuActionModel>()

    fun lodging(key: Char = 'L', action: KeyActionId = KeyActions.questionLodging) {
        actions += QuestionMenuActionModel(QuestionTopic.Lodging, key, action)
    }

    fun magic(key: Char = 'M', action: KeyActionId = KeyActions.questionMagic) {
        actions += QuestionMenuActionModel(QuestionTopic.Magic, key, action)
    }

    fun people(key: Char = 'P', action: KeyActionId = KeyActions.questionPeople) {
        actions += QuestionMenuActionModel(QuestionTopic.People, key, action)
    }

    fun nothing(key: Char = 'N', action: KeyActionId = KeyActions.questionNothing) {
        actions += QuestionMenuActionModel(QuestionTopic.Nothing, key, action)
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
        Images.Scene.encounterBanner1,
        Images.Scene.encounterBanner2,
        Images.Scene.encounterBanner3,
        Images.Scene.encounterBanner4,
        Images.Scene.encounterBanner5,
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

    fun attack(key: KeyActionId = KeyActions.attack) {
        action(key, CombatActionKind.Attack, CombatActionRequirement.Always)
    }

    fun defend(key: KeyActionId = KeyActions.defend) {
        action(key, CombatActionKind.Defend, CombatActionRequirement.Always)
    }

    fun useMagicItem(key: KeyActionId = KeyActions.useItem) {
        action(key, CombatActionKind.UseMagicItem, CombatActionRequirement.Always)
    }

    fun battleCry(key: KeyActionId = KeyActions.battleCry) {
        action(key, CombatActionKind.BattleCry, CombatActionRequirement.Always)
    }

    fun castMageSpell(key: KeyActionId = KeyActions.castSpell) {
        action(key, CombatActionKind.CastMageSpell, CombatActionRequirement.Mage)
    }

    fun build(): List<CombatActionModel> = actions.toList()

    private fun action(
        key: KeyActionId,
        kind: CombatActionKind,
        requirement: CombatActionRequirement,
    ) {
        actions += CombatActionModel(key, kind, requirement)
    }
}

@RylNextDsl
class PlayerRoundBuilder(initial: PlayerRoundModel? = null) {
    private var attackRules = initial?.attack ?: PlayerAttackRulesModel(
        hitBonus = 1,
        damageSpreadMax = 4,
        confusionDivisor = 3,
    )
    private var defendRules = initial?.defend ?: DefendRulesModel(
        weaponSkillPenalty = 2,
        restThreshold = 5,
    )
    private var battleCryRules = initial?.battleCry ?: BattleCryRulesModel(
        weaponSkillPenalty = 4,
    )

    fun attack(hitBonus: Int, damageSpreadMax: Int, confusionDivisor: Int) {
        attackRules = PlayerAttackRulesModel(hitBonus, damageSpreadMax, confusionDivisor)
    }

    fun defend(weaponSkillPenalty: Int, restThreshold: Int) {
        defendRules = DefendRulesModel(weaponSkillPenalty, restThreshold)
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
        fallback = Scenes.Battle.monsterMelee,
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
    var currentMap: WorldMapId = Maps.wilderness
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
    private var countersState = PartyCounters(null, 0, false, false, emptyList())
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
        npcs.forEach { visitedNpcs[it] = NpcVisitState(it, true) }
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
        records[party] = PartyAttackRecord(party, attacked)
    }

    fun attacked(party: PartyId) {
        party(party, true)
    }

    fun clear(party: PartyId) {
        party(party, false)
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
        visits[npc] = NpcVisitState(npc, true)
    }

    fun notVisited(npc: NpcId) {
        visits[npc] = NpcVisitState(npc, false)
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
    private var equipmentState: EquipmentState = EquipmentState(Items.none, Items.none)
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
class EquipmentBuilder(initial: EquipmentState = EquipmentState(Items.none, Items.none)) {
    var weapon: ItemId = initial.weapon
    var armor: ItemId = initial.armor

    fun build(): EquipmentState = EquipmentState(weapon, armor)
}

fun nativePathsIn(model: GameModel): Set<String> {
    val paths = mutableSetOf<String>()

    fun int(ast: IntAst) {
        when (ast) {
            is IntAst.Lit -> Unit
            is IntAst.Var -> Unit
            is IntAst.Binary -> {
                int(ast.left)
                int(ast.right)
            }
            is IntAst.Random -> {
                int(ast.lo)
                int(ast.hi)
            }
            is IntAst.MemberField -> int(ast.member)
            is IntAst.MonsterField -> int(ast.monster)
            is IntAst.RealmScalar -> Unit
            is IntAst.PartyScalar -> Unit
            is IntAst.NativeInt -> paths += ast.path
        }
    }

    fun bool(ast: BoolAst) {
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
            is BoolAst.MemberFlag -> int(ast.member)
            is BoolAst.MonsterFlag -> int(ast.monster)
        }
    }

    fun str(ast: StrAst) {
        when (ast) {
            is StrAst.NativeString -> paths += ast.path
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
                        TextPart.OfferedItemName,
                        TextPart.OfferedItemPrice,
                        TextPart.ShopPrice,
                        TextPart.ShopCarryCap -> Unit
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
                is Op.AskMember -> text(op.prompt)
                is Op.AskInventorySlot -> text(op.prompt)
                is Op.AskMonster -> Unit
                is Op.ForEachMember -> ops(op.body)
                is Op.ForEachMonster -> ops(op.body)
                is Op.Speak -> Unit
                is Op.RunScene -> Unit
                is Op.Encounter -> Unit
                Op.ConsumeActiveItem -> Unit
                Op.ConsumeTorch -> Unit
                is Op.GrantReward -> op.reward.gold?.let(::int)
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
                                    (entry.purchase as? ShopPurchaseModel.Resource)?.let { resource ->
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
    model.dialogue.forEach { dialogue ->
        dialogue.branches.forEach { ops(it.ops) }
    }
    model.spells.forEach { ops(it.ops) }
    model.magicItems.forEach { ops(it.ops) }
    model.missions.forEach {
        it.requires?.let(::bool)
    }
    return paths
}
