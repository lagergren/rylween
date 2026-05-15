/**
 * Explicit authoring facade for game content sources.
 *
 * The reusable DSL is split into two packages with different audiences:
 *
 * - `rylween.dsl` is the authoring surface. Game content files, generated game
 *   handle catalogs, replay fixtures, and tests that read like game content
 *   should be able to use `import rylween.dsl.*` and nothing from
 *   `rylween.dsl.impl`.
 * - `rylween.dsl.impl` is the implementation package. It owns the large model
 *   graph and subsystem builders so those files can stay organized by domain
 *   without turning the root package into one oversized source file.
 *
 * This facade deliberately re-exports only concepts that are part of current
 * authoring practice: enum values such as [PcClass] and [ImagePlacement], value
 * helpers such as [xy], [cells], and [of], the [rylween] entry point, mutable
 * replay-state builders such as [PartyMembersBuilder], and singleton text/price
 * namespaces such as [BattleText] and [ShopPrices]. For example, authored game
 * files should read like:
 *
 * ```
 * import rylween.dsl.*
 *
 * val game = rylween {
 *     content {
 *         visuals {
 *             images {
 *                 bitmap(Images.Scene.tavern, "bitmap/assets/scene-tavern.png", ImagePlacement.MapWindow)
 *             }
 *         }
 *     }
 * }
 * ```
 *
 * Keeping this narrow facade solves two practical problems. First, game authors
 * and generated game catalogs no longer depend on package names that advertise
 * implementation ownership. Second, implementation files can still refactor
 * between `ContentModelsDsl.kt`, `ShopDsl.kt`, `StateDsl.kt`, and the other
 * subsystem files without changing every authored game source, as long as this
 * facade preserves the author-facing names. When a new type is needed here, add
 * it because authored content actually uses it; do not re-export the whole
 * implementation package just to make imports convenient.
 */

package rylween.dsl

import rylween.dsl.impl.item as implShopItem
import rylween.dsl.impl.withStateModule as implWithStateModule

typealias FlagScope = rylween.dsl.impl.FlagScope
typealias FlagRef = rylween.dsl.impl.FlagRef
typealias FlagState = rylween.dsl.impl.FlagState
typealias Gender = rylween.dsl.impl.Gender
typealias Race = rylween.dsl.impl.Race
typealias PcClass = rylween.dsl.impl.PcClass
typealias RuntimeMode = rylween.dsl.impl.RuntimeMode
typealias Facing = rylween.dsl.impl.Facing
typealias StateKind = rylween.dsl.impl.StateKind
typealias StatPool = rylween.dsl.impl.StatPool
typealias HitPoints = rylween.dsl.impl.HitPoints
typealias Mana = rylween.dsl.impl.Mana
typealias GridPoint = rylween.dsl.impl.GridPoint
typealias GridOffset = rylween.dsl.impl.GridOffset
typealias GridSize = rylween.dsl.impl.GridSize
typealias WorldPosition = rylween.dsl.impl.WorldPosition
typealias RylweenModel = rylween.dsl.impl.RylweenModel
typealias GameModel = rylween.dsl.impl.GameModel
typealias RuntimeContent = rylween.dsl.impl.RuntimeContent
typealias RylweenBuilder = rylween.dsl.impl.RylweenBuilder
typealias RylweenStateModuleBuilder = rylween.dsl.impl.RylweenStateModuleBuilder
typealias StateBuilder = rylween.dsl.impl.StateBuilder
typealias NpcStateBuilder = rylween.dsl.impl.NpcStateBuilder
typealias PartyMembersBuilder = rylween.dsl.impl.PartyMembersBuilder
typealias PartyResources = rylween.dsl.impl.PartyResources
typealias PartyCounters = rylween.dsl.impl.PartyCounters
typealias PartyMemberState = rylween.dsl.impl.PartyMemberState
typealias PartyState = rylween.dsl.impl.PartyState
typealias RealmState = rylween.dsl.impl.RealmState
typealias StateModel = rylween.dsl.impl.StateModel
typealias JournalEntryState = rylween.dsl.impl.JournalEntryState
typealias ItemKind = rylween.dsl.impl.ItemKind
typealias EquipmentCommand = rylween.dsl.impl.EquipmentCommand
typealias ItemStorageKind = rylween.dsl.impl.ItemStorageKind
typealias Resource = rylween.dsl.impl.Resource
typealias TemperatureBand = rylween.dsl.impl.TemperatureBand
typealias TemperatureBandRange = rylween.dsl.impl.TemperatureBandRange
typealias TerrainSettlement = rylween.dsl.impl.TerrainSettlement
typealias ImageAssetKind = rylween.dsl.impl.ImageAssetKind
typealias ImagePlacement = rylween.dsl.impl.ImagePlacement
typealias ImageTransition = rylween.dsl.impl.ImageTransition
typealias StaticScreenRuntimeReport = rylween.dsl.impl.StaticScreenRuntimeReport
typealias TownKind = rylween.dsl.impl.TownKind
typealias CharacterCreationStat = rylween.dsl.impl.CharacterCreationStat
typealias CharacterCreationStatRollModel = rylween.dsl.impl.CharacterCreationStatRollModel
typealias SourceSpecialField = rylween.dsl.impl.SourceSpecialField
typealias MandatoryRunRule = rylween.dsl.impl.MandatoryRunRule
typealias RewardItemPlacement = rylween.dsl.impl.RewardItemPlacement
typealias RewardOverflowPolicy = rylween.dsl.impl.RewardOverflowPolicy
typealias RewardSpecialItemPolicy = rylween.dsl.impl.RewardSpecialItemPolicy
typealias ItemUseContext = rylween.dsl.impl.ItemUseContext
typealias ItemConsumePolicy = rylween.dsl.impl.ItemConsumePolicy
typealias ConversationNpcSelection = rylween.dsl.impl.ConversationNpcSelection
typealias NpcVisitPolicy = rylween.dsl.impl.NpcVisitPolicy
typealias DialogueBranchSelection = rylween.dsl.impl.DialogueBranchSelection
typealias MissionHintPolicy = rylween.dsl.impl.MissionHintPolicy
typealias QuestionTopic = rylween.dsl.impl.QuestionTopic
typealias ChitchatSubject = rylween.dsl.impl.ChitchatSubject
typealias ChitchatReplyPolicy = rylween.dsl.impl.ChitchatReplyPolicy
typealias RumorUsagePolicy = rylween.dsl.impl.RumorUsagePolicy
typealias RumorSourcePolicy = rylween.dsl.impl.RumorSourcePolicy
typealias ShopHaggleBonusModel = rylween.dsl.impl.ShopHaggleBonusModel
typealias ShopSellHaggleModel = rylween.dsl.impl.ShopSellHaggleModel
typealias ShopZeroQuantityDealPolicy = rylween.dsl.impl.ShopZeroQuantityDealPolicy
typealias ShopQuantityCap = rylween.dsl.impl.ShopQuantityCap
typealias ShopTextBuilder = rylween.dsl.impl.ShopTextBuilder
typealias ShopItemSink = rylween.dsl.impl.ShopItemSink

val Wilderness: rylween.dsl.impl.Wilderness get() = rylween.dsl.impl.Wilderness
val BattleText: rylween.dsl.impl.BattleText get() = rylween.dsl.impl.BattleText
val HuntText: rylween.dsl.impl.HuntText get() = rylween.dsl.impl.HuntText
val TownText: rylween.dsl.impl.TownText get() = rylween.dsl.impl.TownText
val ShopResources: rylween.dsl.impl.ShopResources get() = rylween.dsl.impl.ShopResources
val ShopQuantityCaps: rylween.dsl.impl.ShopQuantityCaps get() = rylween.dsl.impl.ShopQuantityCaps
val ShopPrices: rylween.dsl.impl.ShopPriceRefs get() = rylween.dsl.impl.ShopPrices
val ShopZeroQuantityDeal: rylween.dsl.impl.ShopZeroQuantityDeal get() = rylween.dsl.impl.ShopZeroQuantityDeal

fun rylween(body: RylweenBuilder.() -> Unit): RylweenModel = rylween.dsl.impl.rylween(body)

fun RylweenModel.withStateModule(body: RylweenStateModuleBuilder.() -> Unit): RylweenModel = implWithStateModule(body)

fun xy(
    x: Int,
    y: Int,
): GridPoint = rylween.dsl.impl.xy(x, y)

fun gridOffset(
    dx: Int,
    dy: Int,
): GridOffset = rylween.dsl.impl.gridOffset(dx, dy)

fun townPosition(
    town: TownId,
    building: BuildingId,
): WorldPosition =
    rylween.dsl.impl.WorldPosition
        .Town(town, building)

fun cells(
    width: Int,
    height: Int,
): GridSize = rylween.dsl.impl.cells(width, height)

infix fun Int.of(max: Int): StatPool = StatPool(this, max)

fun shopText(body: ShopTextBuilder.() -> Unit): TextBlock = rylween.dsl.impl.shopText(body)

fun <F : FlagRef> F.declareFlag(): F = also { flag -> rylween.dsl.impl.declaredFlags += flag }

fun ShopItemSink.item(
    binding: InputBindingModel,
    item: ItemId,
    label: String? = null,
    price: rylween.dsl.impl.ShopPriceModel = rylween.dsl.impl.ShopPriceModel.ItemCost,
    description: TextBlock? = null,
) = implShopItem(binding, item, label, price, description)

fun ShopItemSink.item(
    key: Char,
    item: ItemId,
    label: String? = null,
    price: rylween.dsl.impl.ShopPriceModel = rylween.dsl.impl.ShopPriceModel.ItemCost,
    description: TextBlock? = null,
) = implShopItem(key, item, label, price, description)

fun ShopItemSink.item(
    binding: InputBindingModel,
    item: ItemId,
    label: String? = null,
    price: rylween.dsl.impl.ShopPriceModel = rylween.dsl.impl.ShopPriceModel.ItemCost,
    description: StructuredTextBlockBuilder.() -> Unit,
) = implShopItem(binding, item, label, price, description)

fun ShopItemSink.item(
    key: Char,
    item: ItemId,
    label: String? = null,
    price: rylween.dsl.impl.ShopPriceModel = rylween.dsl.impl.ShopPriceModel.ItemCost,
    description: StructuredTextBlockBuilder.() -> Unit,
) = implShopItem(key, item, label, price, description)
