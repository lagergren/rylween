/**
 * In-memory compiled game bundle model and compiler.
 *
 * `Dsl.kt` records authored content and script ASTs; this file lowers that
 * model into J1 compiled tables, handles, string ids, script roots, operands,
 * and runtime state images. `CompiledLoader.kt` validates and materializes the
 * resulting bundle for hosts and the future J2 VM.
 */
package rylween.compiled

import rylween.dsl.BoolAst
import rylween.dsl.BuildingId
import rylween.dsl.BuildingServiceModel
import rylween.dsl.CharacterModel
import rylween.dsl.CharacterPlacementModel
import rylween.dsl.DungeonEventDisposition
import rylween.dsl.DungeonEventId
import rylween.dsl.DungeonEventModel
import rylween.dsl.DungeonId
import rylween.dsl.DungeonScenarioModel
import rylween.dsl.EncounterTier
import rylween.dsl.EngineServiceUse
import rylween.dsl.Facing
import rylween.dsl.FlagContentModel
import rylween.dsl.FlagScope
import rylween.dsl.FlagState
import rylween.dsl.FormulaId
import rylween.dsl.FormulaModel
import rylween.dsl.GameMilestoneId
import rylween.dsl.GameModel
import rylween.dsl.GameUniverseModel
import rylween.dsl.Gender
import rylween.dsl.GridPoint
import rylween.dsl.GridSize
import rylween.dsl.HitPoints
import rylween.dsl.ImageAssetKind
import rylween.dsl.ImageAssetModel
import rylween.dsl.ImageId
import rylween.dsl.ImagePlacement
import rylween.dsl.InputBindingModel
import rylween.dsl.IntAst
import rylween.dsl.ItemConsumePolicy
import rylween.dsl.ItemId
import rylween.dsl.ItemKind
import rylween.dsl.ItemPoolId
import rylween.dsl.ItemRecordModel
import rylween.dsl.ItemStorageKind
import rylween.dsl.ItemUseContext
import rylween.dsl.ItemUseModel
import rylween.dsl.JournalEntryState
import rylween.dsl.KeyActionId
import rylween.dsl.LValueAst
import rylween.dsl.LocationId
import rylween.dsl.LootTableId
import rylween.dsl.LootTableModel
import rylween.dsl.Mana
import rylween.dsl.MapLayerId
import rylween.dsl.MissionId
import rylween.dsl.MissionModel
import rylween.dsl.MonsterId
import rylween.dsl.MonsterModel
import rylween.dsl.NativeStringArg
import rylween.dsl.NpcId
import rylween.dsl.NpcJobId
import rylween.dsl.NpcRole
import rylween.dsl.Op
import rylween.dsl.PartyId
import rylween.dsl.PartyMemberState
import rylween.dsl.PartyResources
import rylween.dsl.PartyState
import rylween.dsl.PcClass
import rylween.dsl.Race
import rylween.dsl.RandomItemOfferModel
import rylween.dsl.RealmState
import rylween.dsl.Resource
import rylween.dsl.RewardApplicationModel
import rylween.dsl.RewardBundleId
import rylween.dsl.RewardBundleModel
import rylween.dsl.RewardMessageModel
import rylween.dsl.RumourTableId
import rylween.dsl.RuntimeMode
import rylween.dsl.RylweenModel
import rylween.dsl.SceneId
import rylween.dsl.ScreenRegionId
import rylween.dsl.ScreenRegionModel
import rylween.dsl.ScriptRewardModel
import rylween.dsl.ShopBuyModel
import rylween.dsl.ShopCatalogModel
import rylween.dsl.ShopInterludeModel
import rylween.dsl.ShopItemOfferModel
import rylween.dsl.ShopPriceModel
import rylween.dsl.ShopPurchaseModel
import rylween.dsl.ShopQuantityCap
import rylween.dsl.ShopSellBargainModel
import rylween.dsl.ShopSellModel
import rylween.dsl.ShopServiceModel
import rylween.dsl.ShopZeroQuantityDealPolicy
import rylween.dsl.SpellId
import rylween.dsl.SpellModel
import rylween.dsl.StateId
import rylween.dsl.StateKind
import rylween.dsl.StateModel
import rylween.dsl.StaticScreenId
import rylween.dsl.StaticScreenModel
import rylween.dsl.StaticScreenRuntimeReport
import rylween.dsl.StrAst
import rylween.dsl.TemperatureBand
import rylween.dsl.TerrainId
import rylween.dsl.TerrainSemanticsModel
import rylween.dsl.TerrainSettlement
import rylween.dsl.TerrainTagId
import rylween.dsl.TextBlock
import rylween.dsl.TextPart
import rylween.dsl.TownId
import rylween.dsl.TownKind
import rylween.dsl.TownModel
import rylween.dsl.Trigger
import rylween.dsl.ViewCommand
import rylween.dsl.Wilderness
import rylween.dsl.WorldMapId
import rylween.dsl.WorldMapModel
import rylween.dsl.WorldPosition
import rylween.dsl.building
import rylween.dsl.character
import rylween.dsl.dungeon
import rylween.dsl.dungeonEvent
import rylween.dsl.formula
import rylween.dsl.image
import rylween.dsl.item
import rylween.dsl.line
import rylween.dsl.lines
import rylween.dsl.location
import rylween.dsl.lootTable
import rylween.dsl.milestone
import rylween.dsl.monster
import rylween.dsl.npc
import rylween.dsl.rumour
import rylween.dsl.runtimeContent
import rylween.dsl.terrain
import rylween.dsl.text
import rylween.dsl.town
import rylween.runtime.CastleSessionSnapshot
import rylween.runtime.DungeonSavedStep
import rylween.runtime.RuntimeModeSnapshot
import rylween.runtime.RuntimeSnapshotMetadata
import rylween.runtime.RuntimeSnapshotModel
import rylween.runtime.ScriptContinuationSnapshot
import rylween.runtime.ScriptRootRef
import rylween.runtime.scriptRootCatalog

/** Complete compiled J1 bundle: content tables plus save/start state images. */
data class CompiledGameBundle(
    val content: CompiledContentBundle,
    val states: List<CompiledStateImage>,
)

/** Dense compiled content tables emitted from [rylween.dsl.GameModel]. */
data class CompiledContentBundle(
    val universe: CompiledUniverse,
    val limits: CompiledLimits,
    val strings: CompiledStringTable,
    val handles: CompiledHandleTable,
    val flags: List<CompiledFlag>,
    val items: List<CompiledItemRecord>,
    val itemPools: List<CompiledItemPool>,
    val formulas: List<CompiledFormula>,
    val rewards: List<CompiledRewardBundle>,
    val terrain: List<CompiledTerrain>,
    val worldMaps: List<CompiledWorldMap>,
    val images: List<CompiledImageAsset>,
    val staticScreens: List<CompiledStaticScreen>,
    val screenRegions: List<CompiledScreenRegion>,
    val towns: List<CompiledTown>,
    val buildingServices: List<CompiledBuildingService>,
    val characters: List<CompiledCharacter>,
    val monsters: List<CompiledMonster>,
    val missions: List<CompiledMission>,
    val spells: List<CompiledSpell>,
    val itemUses: List<CompiledItemUse>,
    val dungeonEvents: List<CompiledDungeonEvent>,
    val dungeonScenarios: List<CompiledDungeonScenario>,
    val scripts: CompiledScriptBundle,
)

/** String-table backed universe metadata stored in the compiled bundle. */
data class CompiledUniverse(
    val nameString: Int,
    val versionString: Int,
    val compatibilityIdString: Int,
)

/** Compiled runtime limits used by hosts and artifact validators. */
data class CompiledLimits(
    val maxWorldMaps: Int?,
    val maxMapCells: Int?,
    val screenSize: GridSize?,
)

/** One-based interned string table used by compiled handles, text, and scripts. */
data class CompiledStringTable(
    val entries: List<String>,
) {
    private val lookup: Map<String, Int> = entries.withIndex().associate { (index, value) -> value to index + 1 }

    /** Hash rows emitted for non-Kotlin readers that need bucketed lookup. */
    val hashEntries: List<CompiledStringHashEntry> =
        entries.mapIndexed { index, value -> CompiledStringHashEntry(value.hashCode(), index + 1) }

    /** Returns the one-based string id for [value], or `null` if absent. */
    fun idOf(value: String): Int? = lookup[value]

    /** Resolves a one-based string [id] or fails if the id is not in the table. */
    fun value(id: Int): String = entries.getOrNull(id - 1) ?: error("unknown string id $id")
}

/** Hash bucket row for one compiled string-table entry. */
data class CompiledStringHashEntry(
    val hash: Int,
    val stringId: Int,
)

/** Stable kind tag for compiled handle references. */
enum class CompiledHandleKind {
    Building,
    Dungeon,
    DungeonEvent,
    EncounterTier,
    Flag,
    FlagState,
    Formula,
    Image,
    Item,
    ItemPool,
    KeyAction,
    Location,
    LootTable,
    MapLayer,
    Milestone,
    Mission,
    Monster,
    NativeOp,
    Npc,
    NpcJob,
    PaletteColor,
    Party,
    RewardBundle,
    Role,
    Rumour,
    RumourTable,
    Scene,
    ScreenRegion,
    ScriptRoot,
    Spell,
    State,
    StaticScreen,
    Terrain,
    TerrainTag,
    Text,
    Town,
    WorldMap,
}

/** Compact reference to a typed handle row in [CompiledHandleTable]. */
data class CompiledHandleRef(
    val kind: CompiledHandleKind,
    val id: Int,
)

/** Interned typed handle with dense id, kind, and slug string id. */
data class CompiledHandle(
    val ref: CompiledHandleRef,
    val denseId: Int,
    val slugString: Int,
)

/** Hash bucket row for one compiled handle-table entry. */
data class CompiledHandleHashEntry(
    val kind: CompiledHandleKind,
    val slugString: Int,
    val hash: Int,
    val ref: CompiledHandleRef,
)

/** Dense handle table plus lookup helpers for compiled handle references. */
data class CompiledHandleTable(
    val handles: List<CompiledHandle>,
) {
    private val lookup: Map<Pair<CompiledHandleKind, Int>, CompiledHandle> =
        handles.associateBy { it.ref.kind to it.slugString }

    /** Hash rows emitted for non-Kotlin readers that need bucketed handle lookup. */
    val hashEntries: List<CompiledHandleHashEntry> =
        handles.map { handle ->
            CompiledHandleHashEntry(
                kind = handle.ref.kind,
                slugString = handle.slugString,
                hash =
                    31 *
                        handle.ref.kind.name
                            .hashCode() + handle.slugString,
                ref = handle.ref,
            )
        }

    /** Resolves a required handle ref by [kind] and slug string id. */
    fun ref(
        kind: CompiledHandleKind,
        slugString: Int,
    ): CompiledHandleRef =
        lookup[kind to slugString]?.ref
            ?: error("unknown compiled handle ${kind.name} string id $slugString")

    /** Resolves an optional handle ref by [kind] and slug string id. */
    fun refOrNull(
        kind: CompiledHandleKind,
        slugString: Int,
    ): CompiledHandleRef? = lookup[kind to slugString]?.ref

    /** Resolves a handle row by compact [ref]. */
    fun handle(ref: CompiledHandleRef): CompiledHandle =
        handles.firstOrNull { it.ref == ref }
            ?: error("unknown compiled handle ${ref.kind.name}#${ref.id}")
}

data class CompiledFlag(
    val id: CompiledHandleRef,
    val scope: FlagScope,
    val states: List<CompiledFlagState>,
)

data class CompiledFlagState(
    val id: CompiledHandleRef,
    val nativeString: Int,
)

data class CompiledItemRecord(
    val id: CompiledHandleRef,
    val kind: ItemKind,
    val displayNameString: Int,
    val cost: Int,
    val storage: ItemStorageKind,
    val weaponDamage: Int?,
    val armorClass: Int?,
    val equippableBy: Set<PcClass>,
    val campImage: CompiledHandleRef?,
    val campProtection: Set<TemperatureBand>,
    val resourceCapacityBonus: Map<Resource, Int>,
)

data class CompiledItemPool(
    val id: CompiledHandleRef,
    val items: List<CompiledHandleRef>,
)

data class CompiledFormula(
    val id: CompiledHandleRef,
    val value: CompiledIntExpr,
)

data class CompiledRewardBundle(
    val id: CompiledHandleRef,
    val experience: CompiledHandleRef,
    val gold: CompiledHandleRef,
    val application: RewardApplicationModel,
    val loot: CompiledLootTable,
)

data class CompiledLootTable(
    val id: CompiledHandleRef,
    val dropChancePercent: Int,
    val treasureChancePercent: Int,
    val treasure: CompiledHandleRef,
    val magic: CompiledHandleRef,
)

data class CompiledTerrain(
    val id: CompiledHandleRef,
    val mapCode: Char,
    val passable: Boolean,
    val movementCost: Int,
    val allowsNpcPlacement: Boolean,
    val settlement: TerrainSettlement?,
    val tags: List<CompiledHandleRef>,
)

data class CompiledWorldMap(
    val id: CompiledHandleRef,
    val width: Int?,
    val height: Int?,
    val storageCapacity: Int?,
    val layers: List<CompiledWorldMapLayer>,
)

data class CompiledWorldMapLayer(
    val id: CompiledHandleRef,
    val rowStrings: List<Int>,
)

data class CompiledImageAsset(
    val id: CompiledHandleRef,
    val kind: ImageAssetKind,
    val resourceString: Int,
    val placement: ImagePlacement,
)

data class CompiledStaticScreen(
    val id: CompiledHandleRef,
    val image: CompiledHandleRef?,
    val resourceString: Int?,
    val commandHelp: Boolean,
    val runtimeReport: StaticScreenRuntimeReport?,
)

data class CompiledScreenRegion(
    val id: CompiledHandleRef,
    val x: Int,
    val y: Int,
    val width: Int?,
    val height: Int?,
)

data class CompiledTown(
    val id: CompiledHandleRef,
    val nameString: Int,
    val position: GridPoint,
    val taxPercent: Int,
    val kind: TownKind,
    val services: List<CompiledHandleRef>,
)

data class CompiledBuildingService(
    val building: CompiledHandleRef,
    val action: CompiledHandleRef,
    val scene: CompiledHandleRef,
    val opensAt: Int?,
    val closesAt: Int?,
    val nativeServices: List<EngineServiceUse>,
)

data class CompiledCharacter(
    val id: CompiledHandleRef,
    val nameString: Int,
    val job: CompiledHandleRef,
    val gender: Gender,
    val placement: CompiledCharacterPlacement,
    val roles: List<CompiledHandleRef>,
)

sealed interface CompiledCharacterPlacement {
    data class Fixed(
        val position: CompiledWorldPosition,
    ) : CompiledCharacterPlacement

    data object Randomized : CompiledCharacterPlacement

    data object RandomizedWilderness : CompiledCharacterPlacement
}

data class CompiledMonster(
    val id: CompiledHandleRef,
    val nameString: Int,
    val count: IntRange,
    val countMaxLevelDivisor: Int?,
    val hitPoints: IntRange,
    val weaponSkill: Int,
    val spellPower: Int,
    val tier: CompiledHandleRef?,
    val immune: Boolean,
)

data class CompiledMission(
    val id: CompiledHandleRef,
    val trigger: CompiledTrigger,
    val requires: CompiledBoolExpr?,
    val action: CompiledHandleRef,
)

data class CompiledSpell(
    val id: CompiledHandleRef,
    val slot: Int,
    val baseCost: Int,
    val nameString: Int,
    val scriptRoot: CompiledHandleRef,
)

data class CompiledItemUse(
    val item: CompiledHandleRef,
    val contexts: Set<ItemUseContext>,
    val consume: ItemConsumePolicy,
    val scriptRoot: CompiledHandleRef,
)

data class CompiledDungeonEvent(
    val id: CompiledHandleRef,
    val disposition: DungeonEventDisposition,
    val scriptRoot: CompiledHandleRef,
    val randomEncounterTier: CompiledHandleRef?,
    val clearWhenFlag: CompiledHandleRef?,
)

data class CompiledDungeonScenario(
    val dungeon: CompiledHandleRef,
    val nameString: Int?,
    val levelFirst: Int?,
    val levelLast: Int?,
    val fixedLevelEvents: Map<Int, CompiledHandleRef>,
    val randomEvents: List<CompiledDungeonRandomEvent>,
)

data class CompiledDungeonRandomEvent(
    val event: CompiledHandleRef,
    val weight: Int,
)

sealed interface CompiledTrigger {
    data class RandomStep(
        val percent: Int,
    ) : CompiledTrigger

    data class AtPosition(
        val point: GridPoint,
    ) : CompiledTrigger

    data class AtLocation(
        val location: CompiledHandleRef,
    ) : CompiledTrigger

    data class OnTerrain(
        val terrain: CompiledHandleRef,
    ) : CompiledTrigger
}

data class CompiledScriptBundle(
    val roots: List<CompiledScriptProgram>,
) {
    fun root(root: ScriptRootRef): CompiledScriptProgram? = roots.firstOrNull { it.root.sameRoot(root) }
}

data class CompiledScriptProgram(
    val root: ScriptRootRef,
    val rootHandle: CompiledHandleRef,
    val instructions: List<CompiledInstruction>,
)

enum class CompiledOpcode {
    Screen,
    Emit,
    EmitIndexed,
    WaitInput,
    Pause,
    View,
    LetInt,
    BindText,
    Set,
    Add,
    Sub,
    AddClamped,
    SetFlag,
    AppendJournal,
    If,
    Loop,
    Break,
    Stop,
    Switch,
    Menu,
    AskKey,
    AskMember,
    AskInventorySlot,
    AskText,
    AskMonster,
    ForEachMember,
    ForEachMonster,
    Speak,
    TellRumour,
    RunScene,
    Encounter,
    EnterDungeon,
    ConsumeActiveItem,
    ClearInventorySlot,
    ConsumeInventoryPool,
    ConsumeTorch,
    TeleportParty,
    DiscoverCave,
    GrantReward,
    RandomItemOffer,
    ShopService,
    AchieveMilestone,
    NativeCall,
}

data class CompiledInstruction(
    val opcode: CompiledOpcode,
    val operands: List<CompiledOperand> = emptyList(),
)

sealed interface CompiledOperand {
    data class Integer(
        val value: Int,
    ) : CompiledOperand

    data class BooleanValue(
        val value: Boolean,
    ) : CompiledOperand

    data class Character(
        val value: Char,
    ) : CompiledOperand

    data class StringId(
        val id: Int,
    ) : CompiledOperand

    data class StringIdList(
        val ids: List<Int>,
    ) : CompiledOperand

    data class Handle(
        val ref: CompiledHandleRef,
    ) : CompiledOperand

    data class HandleList(
        val refs: List<CompiledHandleRef>,
    ) : CompiledOperand

    data class TextBlockValue(
        val value: CompiledTextBlock,
    ) : CompiledOperand

    data class IntExprValue(
        val value: CompiledIntExpr,
    ) : CompiledOperand

    data class BoolExprValue(
        val value: CompiledBoolExpr,
    ) : CompiledOperand

    data class StringExprValue(
        val value: CompiledStringExpr,
    ) : CompiledOperand

    data class LValueValue(
        val value: CompiledLValue,
    ) : CompiledOperand

    data class ViewCommandValue(
        val value: CompiledViewCommand,
    ) : CompiledOperand

    data class Instructions(
        val instructions: List<CompiledInstruction>,
    ) : CompiledOperand

    data class SwitchCases(
        val cases: List<CompiledSwitchCase>,
    ) : CompiledOperand

    data class MenuOptions(
        val options: List<CompiledMenuOption>,
    ) : CompiledOperand

    data class InputBindings(
        val bindings: List<CompiledInputBinding>,
    ) : CompiledOperand

    data class TextAnswer(
        val answer: CompiledTextAnswer,
    ) : CompiledOperand

    data class Reward(
        val reward: CompiledScriptReward,
    ) : CompiledOperand

    data class RandomItemOffer(
        val offer: CompiledRandomItemOffer,
    ) : CompiledOperand

    data class ShopService(
        val service: CompiledShopService,
    ) : CompiledOperand

    data class EnumName(
        val name: String,
    ) : CompiledOperand
}

data class CompiledSwitchCase(
    val match: Int?,
    val body: List<CompiledInstruction>,
)

data class CompiledMenuOption(
    val binding: CompiledInputBinding,
    val body: List<CompiledInstruction>,
)

data class CompiledInputBinding(
    val primaryString: Int,
    val aliasStrings: List<Int>,
    val labelString: Int,
)

data class CompiledTextAnswer(
    val acceptedStrings: List<Int>,
    val matchOps: List<CompiledInstruction>,
    val missOps: List<CompiledInstruction>,
    val caseSensitive: Boolean,
    val trim: Boolean,
    val maxLength: Int?,
)

sealed interface CompiledTextBlock {
    data class Ref(
        val text: CompiledHandleRef,
    ) : CompiledTextBlock

    data class Inline(
        val lineStrings: List<Int>,
    ) : CompiledTextBlock

    data class Structured(
        val lines: List<CompiledTextLine>,
    ) : CompiledTextBlock
}

data class CompiledTextLine(
    val parts: List<CompiledTextPart>,
)

sealed interface CompiledTextPart {
    data class Literal(
        val string: Int,
    ) : CompiledTextPart

    data class IntValue(
        val value: CompiledIntExpr,
    ) : CompiledTextPart

    data class StringValue(
        val value: CompiledStringExpr,
    ) : CompiledTextPart

    data class RoleName(
        val role: CompiledHandleRef,
    ) : CompiledTextPart

    data class RoleLocation(
        val role: CompiledHandleRef,
    ) : CompiledTextPart

    data class TownName(
        val town: CompiledHandleRef,
    ) : CompiledTextPart

    data class FuzzyTownLocation(
        val town: CompiledHandleRef,
        val radius: Int,
    ) : CompiledTextPart

    data class LocationCoordinate(
        val location: CompiledHandleRef,
    ) : CompiledTextPart

    data class Runtime(
        val kind: CompiledRuntimeTextPart,
    ) : CompiledTextPart
}

enum class CompiledRuntimeTextPart {
    OfferedItemName,
    OfferedItemPrice,
    SelectedItemName,
    ShopPrice,
    ShopCarryCap,
    HuntMemberName,
    HuntMemberSubjectPronoun,
    HuntFoodGained,
    PartyMemberMax,
    WildernessDungeonName,
    WildernessDungeonLevels,
    DungeonDepth,
    ConversationNpcName,
    CurrentTownName,
    TownTaxPercent,
    TownTaxAmount,
    CreationMemberNumber,
    CreationMemberName,
    CreationWeaponSkill,
    CreationHealth,
    CreationWisdom,
    CreationCharisma,
    BattleFoeDisplayName,
    BattleAttackerName,
    BattleTargetName,
    BattleDamage,
    BattleExperience,
    BattleGold,
    BattleLootName,
    BattleLevelMemberName,
    BattleLevelsGained,
}

sealed interface CompiledViewCommand {
    data object ClearText : CompiledViewCommand

    data object ClearMapWindow : CompiledViewCommand

    data object RenderMap : CompiledViewCommand

    data object StatusLine : CompiledViewCommand

    data class ShowImage(
        val image: CompiledHandleRef,
    ) : CompiledViewCommand
}

sealed interface CompiledIntExpr {
    data class Literal(
        val value: Int,
    ) : CompiledIntExpr

    data class Local(
        val nameString: Int,
    ) : CompiledIntExpr

    data class Binary(
        val left: CompiledIntExpr,
        val op: CompiledBinaryOp,
        val right: CompiledIntExpr,
    ) : CompiledIntExpr

    data class If(
        val cond: CompiledBoolExpr,
        val thenValue: CompiledIntExpr,
        val elseValue: CompiledIntExpr,
    ) : CompiledIntExpr

    data class Random(
        val lo: CompiledIntExpr,
        val hi: CompiledIntExpr,
    ) : CompiledIntExpr

    data class MemberField(
        val member: CompiledIntExpr,
        val fieldString: Int,
    ) : CompiledIntExpr

    data class MonsterField(
        val monster: CompiledIntExpr,
        val fieldString: Int,
    ) : CompiledIntExpr

    data class BattleFlag(
        val nameString: Int,
    ) : CompiledIntExpr

    data class BattleQuery(
        val nameString: Int,
    ) : CompiledIntExpr

    data class RealmScalar(
        val nameString: Int,
    ) : CompiledIntExpr

    data class PartyScalar(
        val nameString: Int,
    ) : CompiledIntExpr

    data class InventoryPoolCount(
        val pool: CompiledHandleRef,
    ) : CompiledIntExpr

    data class InventorySlotCost(
        val slot: CompiledIntExpr,
    ) : CompiledIntExpr

    data class NativeInt(
        val pathString: Int,
        val argStrings: List<Int>,
    ) : CompiledIntExpr
}

enum class CompiledBinaryOp { Add, Sub, Mul, Div, Mod }

sealed interface CompiledBoolExpr {
    data class Cmp(
        val left: CompiledIntExpr,
        val op: CompiledComparisonOp,
        val right: CompiledIntExpr,
    ) : CompiledBoolExpr

    data class Not(
        val expr: CompiledBoolExpr,
    ) : CompiledBoolExpr

    data class And(
        val left: CompiledBoolExpr,
        val right: CompiledBoolExpr,
    ) : CompiledBoolExpr

    data class Or(
        val left: CompiledBoolExpr,
        val right: CompiledBoolExpr,
    ) : CompiledBoolExpr

    data class FlagIs(
        val flag: CompiledHandleRef,
        val state: CompiledHandleRef,
    ) : CompiledBoolExpr

    data class MilestoneAchieved(
        val milestone: CompiledHandleRef,
    ) : CompiledBoolExpr

    data class MemberFlag(
        val member: CompiledIntExpr,
        val nameString: Int,
    ) : CompiledBoolExpr

    data class MonsterFlag(
        val monster: CompiledIntExpr,
        val nameString: Int,
    ) : CompiledBoolExpr

    data class BattleFlag(
        val nameString: Int,
    ) : CompiledBoolExpr

    data class RuntimeFlag(
        val nameString: Int,
    ) : CompiledBoolExpr
}

enum class CompiledComparisonOp { Eq, Ne, Lt, Lte, Gt, Gte }

sealed interface CompiledStringExpr {
    data class NativeString(
        val pathString: Int,
        val args: List<CompiledNativeStringArg>,
    ) : CompiledStringExpr
}

sealed interface CompiledNativeStringArg {
    data class Literal(
        val string: Int,
    ) : CompiledNativeStringArg

    data class IntValue(
        val value: CompiledIntExpr,
    ) : CompiledNativeStringArg
}

sealed interface CompiledLValue {
    data class RealmScalar(
        val nameString: Int,
    ) : CompiledLValue

    data class PartyScalar(
        val nameString: Int,
    ) : CompiledLValue

    data class MemberField(
        val member: CompiledIntExpr,
        val fieldString: Int,
    ) : CompiledLValue

    data class MonsterField(
        val monster: CompiledIntExpr,
        val fieldString: Int,
    ) : CompiledLValue

    data class BattleFlag(
        val nameString: Int,
    ) : CompiledLValue
}

data class CompiledScriptReward(
    val gold: CompiledIntExpr?,
    val items: List<CompiledHandleRef>,
    val application: RewardApplicationModel,
    val messages: CompiledRewardMessages,
)

data class CompiledRewardMessages(
    val cantCarry: CompiledTextBlock?,
    val pickedUp: CompiledTextBlock?,
)

data class CompiledRandomItemOffer(
    val pool: CompiledHandleRef,
    val offer: CompiledTextBlock,
    val choicePrompt: CompiledTextBlock,
    val broke: CompiledTextBlock,
    val deal: CompiledTextBlock,
    val declined: CompiledTextBlock?,
    val application: RewardApplicationModel,
    val messages: CompiledRewardMessages,
    val buy: CompiledInputBinding,
    val decline: CompiledInputBinding,
    val priceOverride: Int?,
    val pauseMillis: CompiledOfferPauses,
)

data class CompiledOfferPauses(
    val broke: Int,
    val full: Int,
    val deal: Int,
    val declined: Int,
)

data class CompiledShopService(
    val building: CompiledHandleRef,
    val image: CompiledHandleRef,
    val closed: CompiledTextBlock,
    val intro: CompiledTextBlock,
    val menu: CompiledTextBlock,
    val keys: CompiledShopMenuKeys,
    val goodbye: CompiledTextBlock,
    val buy: CompiledShopBuy?,
    val sell: CompiledShopSell?,
    val interlude: CompiledShopInterlude?,
)

data class CompiledShopMenuKeys(
    val buy: CompiledInputBinding,
    val sell: CompiledInputBinding,
    val leave: CompiledInputBinding,
)

data class CompiledShopBuy(
    val prompt: CompiledTextBlock,
    val catalogs: List<CompiledShopCatalog>,
    val tooExpensive: CompiledTextBlock,
    val cantCarry: CompiledTextBlock,
    val deal: CompiledTextBlock,
    val unitPrice: CompiledTextBlock?,
    val singlePrice: CompiledTextBlock?,
    val payPrompt: CompiledTextBlock,
    val paymentKeys: CompiledShopPurchaseKeys,
    val inventoryApplication: RewardApplicationModel,
)

data class CompiledShopPurchaseKeys(
    val pay: CompiledInputBinding,
    val decline: CompiledInputBinding,
)

data class CompiledShopCatalog(
    val binding: CompiledInputBinding,
    val titleString: Int,
    val intro: CompiledTextBlock?,
    val pages: List<CompiledShopCatalogPage>,
)

/** One compiled shop catalog page plus typed controls for page navigation. */
data class CompiledShopCatalogPage(
    val entries: List<CompiledShopItemOffer>,
    val footer: CompiledTextBlock?,
    val navigation: CompiledShopCatalogPageNavigation = CompiledShopCatalogPageNavigation(),
)

/** Optional previous/next bindings consumed by standalone compiled runtimes. */
data class CompiledShopCatalogPageNavigation(
    val previous: CompiledInputBinding? = null,
    val next: CompiledInputBinding? = null,
)

data class CompiledShopItemOffer(
    val binding: CompiledInputBinding,
    val item: CompiledHandleRef,
    val labelString: Int?,
    val description: CompiledTextBlock?,
    val price: CompiledShopPrice,
    val purchase: CompiledShopPurchase,
)

sealed interface CompiledShopPrice {
    data object ItemCost : CompiledShopPrice

    data object HalfItemCost : CompiledShopPrice

    data class Fixed(
        val amount: Int,
    ) : CompiledShopPrice

    data class LeaderLevelPlus(
        val addend: Int,
    ) : CompiledShopPrice

    data class ItemCostPlusLeaderLevelTimes(
        val multiplier: Int,
    ) : CompiledShopPrice
}

sealed interface CompiledShopPurchase {
    data object InventoryItem : CompiledShopPurchase

    data class Bulk(
        val resource: Resource,
        val quantityPrompt: CompiledTextBlock,
        val cap: ShopQuantityCap,
        val tooMany: CompiledTextBlock,
        val zeroQuantityDeal: ShopZeroQuantityDealPolicy,
    ) : CompiledShopPurchase
}

data class CompiledShopSell(
    val prompt: CompiledTextBlock,
    val acceptedKinds: Set<ItemKind>,
    val acceptedItems: List<CompiledHandleRef>,
    val rejectedItems: List<CompiledHandleRef>,
    val wrongKind: CompiledTextBlock,
    val offer: CompiledTextBlock,
    val declined: CompiledTextBlock?,
    val sold: CompiledTextBlock?,
    val price: CompiledShopPrice,
    val bargain: ShopSellBargainModel?,
    val saleKeys: CompiledShopSaleKeys,
)

data class CompiledShopSaleKeys(
    val accept: CompiledInputBinding,
    val decline: CompiledInputBinding,
    val bargain: CompiledInputBinding,
)

data class CompiledShopInterlude(
    val chancePercent: Int,
    val offer: CompiledTextBlock,
    val accept: CompiledInputBinding,
    val decline: CompiledInputBinding,
    val accepted: List<CompiledTextBlock>,
    val declined: CompiledTextBlock,
    val runsRumourAfterAccept: Boolean,
    val forceFirstUntil: CompiledHandleRef? = null,
)

data class CompiledStateImage(
    val state: CompiledHandleRef,
    val kind: StateKind,
    val metadata: RuntimeSnapshotMetadata?,
    val realm: CompiledRealmImage,
    val locations: List<CompiledLocationPlacement>,
    val npcs: List<CompiledNpcPlacement>,
    val parties: List<CompiledPartyImage>,
    val mode: CompiledRuntimeModeImage?,
)

data class CompiledRealmImage(
    val day: Int,
    val hour: Int,
    val minute: Int,
    val rngState: Long?,
    val activeParty: CompiledHandleRef,
    val currentMap: CompiledHandleRef,
    val currentMode: RuntimeMode,
    val currentTown: CompiledHandleRef?,
    val banished: Boolean,
    val lastPlayedDay: Int,
    val partyMemberMax: Int,
    val lastTerrain: CompiledHandleRef?,
)

data class CompiledLocationPlacement(
    val location: CompiledHandleRef,
    val point: GridPoint,
)

data class CompiledNpcPlacement(
    val npc: CompiledHandleRef,
    val position: CompiledWorldPosition,
)

sealed interface CompiledWorldPosition {
    data class Wilderness(
        val point: GridPoint,
    ) : CompiledWorldPosition

    data class Town(
        val town: CompiledHandleRef,
        val building: CompiledHandleRef,
    ) : CompiledWorldPosition
}

data class CompiledPartyImage(
    val id: CompiledHandleRef,
    val playerString: Int,
    val nameString: Int,
    val position: CompiledWorldPosition,
    val facing: Facing?,
    val resources: PartyResources,
    val counters: CompiledPartyCounters,
    val flags: List<CompiledHandleRef>,
    val milestones: List<CompiledHandleRef>,
    val inventory: List<CompiledInventorySlot>,
    val members: List<CompiledPartyMember>,
    val visitedNpcs: List<CompiledNpcVisit>,
    val journal: List<CompiledJournalEntry>,
)

data class CompiledPartyCounters(
    val memberCount: Int?,
    val kills: Int,
    val waitingMail: Boolean,
    val attacked: Boolean,
    val attackedToday: List<CompiledPartyAttackRecord>,
)

data class CompiledPartyAttackRecord(
    val party: CompiledHandleRef,
    val attacked: Boolean,
)

data class CompiledInventorySlot(
    val slot: Int,
    val item: CompiledHandleRef?,
)

data class CompiledNpcVisit(
    val npc: CompiledHandleRef,
    val visited: Boolean,
)

sealed interface CompiledJournalEntry {
    data class Text(
        val text: CompiledHandleRef,
    ) : CompiledJournalEntry

    data class Line(
        val textString: Int,
    ) : CompiledJournalEntry
}

data class CompiledPartyMember(
    val nameString: Int,
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
    val equipment: CompiledEquipment,
    val experience: Int,
    val dead: Boolean,
)

data class CompiledEquipment(
    val weapon: CompiledHandleRef,
    val armor: CompiledHandleRef,
)

sealed interface CompiledRuntimeModeImage {
    data class Castle(
        val session: CastleSessionSnapshot,
    ) : CompiledRuntimeModeImage

    data class Dungeon(
        val session: CompiledDungeonSessionImage,
        val postDungeon: CompiledMissionPostDungeonImage?,
    ) : CompiledRuntimeModeImage
}

data class CompiledDungeonSessionImage(
    val dungeon: CompiledHandleRef,
    val maxDepth: Int,
    val depth: Int,
    val levelEvents: Map<Int, CompiledHandleRef>,
    val step: DungeonSavedStep,
)

data class CompiledMissionPostDungeonImage(
    val mission: CompiledHandleRef,
    val scene: CompiledHandleRef,
    val continuation: ScriptContinuationSnapshot,
)

fun RylweenModel.toCompiledGameBundle(): CompiledGameBundle = CompiledBundleCompiler(content, states).compile()

fun GameModel.toCompiledContentBundle(): CompiledContentBundle = CompiledBundleCompiler(this, emptyList()).compile().content

private class CompiledBundleCompiler(
    private val game: GameModel,
    private val states: List<StateModel>,
) {
    private val strings = MutableStringInterner()
    private val handles = MutableHandleTable(strings)

    fun compile(): CompiledGameBundle {
        declareContentHandles()
        declareStateHandles()
        val scripts = compileScripts()
        val universe = compileUniverse(game.metadata.universe)
        val limits =
            CompiledLimits(
                maxWorldMaps = game.limits.maxWorldMaps,
                maxMapCells = game.limits.maxMapCells,
                screenSize = game.limits.screenSize,
            )
        val flags = game.authoredFlags.map(::compileFlag)
        val items = game.itemCatalog.map(::compileItem)
        val itemPools = game.itemPools.map(::compileItemPool)
        val formulas = game.formulas.map(::compileFormula)
        val rewards = game.rewards.map(::compileRewardBundle)
        val terrain = game.terrain.terrain.map(::compileTerrain)
        val worldMaps = game.worldMaps.map(::compileWorldMap)
        val images = game.visuals.images.map(::compileImage)
        val staticScreens = game.visuals.staticScreens.map(::compileStaticScreen)
        val screenRegions = game.layouts.regions.map(::compileRegion)
        val towns = game.towns.map(::compileTown)
        val buildingServices =
            game.townSystem
                ?.buildingServices
                .orEmpty()
                .map(::compileBuildingService)
        val characters = game.characters.map(::compileCharacter)
        val monsters = game.monsters.map(::compileMonster)
        val missions = game.missions.map(::compileMission)
        val spells = game.spells.map(::compileSpell)
        val itemUses = game.itemUses.map(::compileItemUse)
        val dungeonEvents = game.dungeons.events.map(::compileDungeonEvent)
        val dungeonScenarios = game.dungeons.scenarios.map(::compileDungeonScenario)
        val stateImages = states.map { state -> compileState(RuntimeSnapshotModel(state)) }
        val content =
            CompiledContentBundle(
                universe = universe,
                limits = limits,
                strings = strings.toTable(),
                handles = handles.toTable(),
                flags = flags,
                items = items,
                itemPools = itemPools,
                formulas = formulas,
                rewards = rewards,
                terrain = terrain,
                worldMaps = worldMaps,
                images = images,
                staticScreens = staticScreens,
                screenRegions = screenRegions,
                towns = towns,
                buildingServices = buildingServices,
                characters = characters,
                monsters = monsters,
                missions = missions,
                spells = spells,
                itemUses = itemUses,
                dungeonEvents = dungeonEvents,
                dungeonScenarios = dungeonScenarios,
                scripts = scripts,
            )
        return CompiledGameBundle(
            content = content,
            states = stateImages,
        )
    }

    private fun compileUniverse(universe: GameUniverseModel): CompiledUniverse =
        CompiledUniverse(
            nameString = strings.id(universe.name),
            versionString = strings.id(universe.version),
            compatibilityIdString = strings.id(universe.compatibilityId),
        )

    private fun declareContentHandles() {
        game.flags.forEach { flag ->
            handles.add(CompiledHandleKind.Flag, flag.slug)
            flag.states.forEach { state -> handles.add(CompiledHandleKind.FlagState, flagStateSlug(state)) }
        }
        game.itemCatalog.forEach { handles.add(CompiledHandleKind.Item, it.id.slug) }
        game.itemPools.forEach { pool ->
            handles.add(CompiledHandleKind.ItemPool, pool.slug)
            pool.items.forEach { handles.add(CompiledHandleKind.Item, it.slug) }
        }
        game.monsters.forEach { monster ->
            handles.add(CompiledHandleKind.Monster, monster.id.slug)
            monster.tier?.let { handles.add(CompiledHandleKind.EncounterTier, it.slug) }
        }
        game.worldMaps.forEach { map ->
            handles.add(CompiledHandleKind.WorldMap, map.id.slug)
            map.layers.forEach { layer -> handles.add(CompiledHandleKind.MapLayer, layer.id.slug) }
        }
        game.terrain.terrain.forEach { terrain ->
            handles.add(CompiledHandleKind.Terrain, terrain.id.slug)
            terrain.tags.forEach { tag -> handles.add(CompiledHandleKind.TerrainTag, tag.slug) }
        }
        game.visuals.images.forEach { handles.add(CompiledHandleKind.Image, it.id.slug) }
        game.visuals.staticScreens.forEach { screen ->
            handles.add(CompiledHandleKind.StaticScreen, screen.id.slug)
            screen.image?.let { handles.add(CompiledHandleKind.Image, it.slug) }
        }
        game.terminal.terrain.forEach { style -> handles.add(CompiledHandleKind.PaletteColor, style.color.slug) }
        game.layouts.regions.forEach { handles.add(CompiledHandleKind.ScreenRegion, it.id.slug) }
        game.towns.forEach { town ->
            handles.add(CompiledHandleKind.Town, town.id.slug)
            town.services.forEach { service -> handles.add(CompiledHandleKind.Building, service.building.slug) }
        }
        game.townSystem?.menu.orEmpty().forEach { action ->
            handles.add(CompiledHandleKind.KeyAction, action.action.slug)
            action.building?.let { handles.add(CompiledHandleKind.Building, it.slug) }
        }
        game.townSystem?.buildingServices.orEmpty().forEach { service ->
            handles.add(CompiledHandleKind.Building, service.building.slug)
            handles.add(CompiledHandleKind.KeyAction, service.action.slug)
            handles.add(CompiledHandleKind.Scene, service.scene.slug)
        }
        game.characters.forEach { character ->
            handles.add(CompiledHandleKind.Npc, character.id.slug)
            handles.add(CompiledHandleKind.NpcJob, character.job.slug)
            character.roles.forEach { role -> handles.add(CompiledHandleKind.Role, role.slug) }
            when (val placement = character.placement) {
                is CharacterPlacementModel.Fixed -> declarePositionHandles(placement.position)
                CharacterPlacementModel.Randomized,
                CharacterPlacementModel.RandomizedWilderness,
                -> Unit
            }
        }
        game.scenes.forEach { handles.add(CompiledHandleKind.Scene, it.id.slug) }
        game.spells.forEach { handles.add(CompiledHandleKind.Spell, it.id.slug) }
        game.itemUses.forEach { handles.add(CompiledHandleKind.Item, it.id.slug) }
        game.dungeons.events.forEach { event ->
            handles.add(CompiledHandleKind.DungeonEvent, event.id.slug)
            event.randomEncounterTier?.let { handles.add(CompiledHandleKind.EncounterTier, it.slug) }
            event.clearWhenFlag?.let(::declareFlagState)
        }
        game.dungeons.scenarios.forEach { scenario ->
            handles.add(CompiledHandleKind.Dungeon, scenario.dungeon.slug)
            scenario.fixedLevelEvents.values.forEach { handles.add(CompiledHandleKind.DungeonEvent, it.slug) }
            scenario.randomEvents.forEach { handles.add(CompiledHandleKind.DungeonEvent, it.event.slug) }
        }
        game.dungeons.backdrop?.let { handles.add(CompiledHandleKind.Image, it.slug) }
        game.formulas.forEach { handles.add(CompiledHandleKind.Formula, it.id.slug) }
        game.rewards.forEach { reward ->
            handles.add(CompiledHandleKind.RewardBundle, reward.id.slug)
            handles.add(CompiledHandleKind.Formula, reward.experience.slug)
            handles.add(CompiledHandleKind.Formula, reward.gold.slug)
            handles.add(CompiledHandleKind.LootTable, reward.loot.id.slug)
            handles.add(CompiledHandleKind.ItemPool, reward.loot.treasure.slug)
            handles.add(CompiledHandleKind.ItemPool, reward.loot.magic.slug)
        }
        game.progress.milestones.forEach { milestone ->
            handles.add(CompiledHandleKind.Milestone, milestone.id.slug)
            milestone.specialInventoryItem?.let { handles.add(CompiledHandleKind.Item, it.slug) }
        }
        game.missions.forEach { mission ->
            handles.add(CompiledHandleKind.Mission, mission.id.slug)
            handles.add(CompiledHandleKind.Scene, mission.action.slug)
            declareTriggerHandles(mission.trigger)
        }
        game.locationsFromStatesAndScripts().forEach { handles.add(CompiledHandleKind.Location, it.slug) }
        game.dialogue.forEach { dialogue ->
            handles.add(CompiledHandleKind.Npc, dialogue.npc.slug)
            dialogue.branches.forEach { branch -> branch.whenState?.let(::declareFlagState) }
        }
        game.speak.textTables.rumourTables.forEach { table ->
            handles.add(CompiledHandleKind.RumourTable, table.id.slug)
            table.entries.mapNotNull { rumour -> rumour.id }.forEach { rumour ->
                handles.add(CompiledHandleKind.Rumour, rumour.slug)
            }
        }
        game.speak.questionActions.forEach { action -> handles.add(CompiledHandleKind.NpcJob, action.job.slug) }
        game.runtimeContent().scriptRootCatalog().roots.forEach { root ->
            handles.add(CompiledHandleKind.ScriptRoot, scriptRootSlug(root))
        }
    }

    private fun declareStateHandles() {
        states.forEach { state ->
            handles.add(CompiledHandleKind.State, state.id.slug)
            declareRealmHandles(state.realm)
            state.locations.forEach { placement -> handles.add(CompiledHandleKind.Location, placement.location.slug) }
            state.npcs.forEach { placement ->
                handles.add(CompiledHandleKind.Npc, placement.npc.slug)
                declarePositionHandles(placement.position)
            }
            state.parties.forEach { party -> declarePartyHandles(party) }
        }
    }

    private fun declareRealmHandles(realm: RealmState) {
        handles.add(CompiledHandleKind.Party, realm.activeParty.slug)
        handles.add(CompiledHandleKind.WorldMap, realm.currentMap.slug)
        realm.currentTown?.let { handles.add(CompiledHandleKind.Town, it.slug) }
        realm.lastTerrain?.let { handles.add(CompiledHandleKind.Terrain, it.slug) }
    }

    private fun declarePartyHandles(party: PartyState) {
        handles.add(CompiledHandleKind.Party, party.id.slug)
        declarePositionHandles(party.position)
        party.flags.forEach(::declareFlagState)
        party.milestones.forEach { handles.add(CompiledHandleKind.Milestone, it.slug) }
        party.inventory.mapNotNull { it.item }.forEach { handles.add(CompiledHandleKind.Item, it.slug) }
        party.members.forEach { member ->
            handles.add(CompiledHandleKind.Item, member.equipment.weapon.slug)
            handles.add(CompiledHandleKind.Item, member.equipment.armor.slug)
        }
        party.visitedNpcs.forEach { handles.add(CompiledHandleKind.Npc, it.npc.slug) }
        party.journal.forEach { entry ->
            if (entry is JournalEntryState.Text) handles.add(CompiledHandleKind.Text, entry.id.id)
        }
        party.counters.attackedToday.forEach { handles.add(CompiledHandleKind.Party, it.party.slug) }
    }

    private fun declarePositionHandles(position: WorldPosition) {
        when (position) {
            is WorldPosition.Wilderness -> Unit
            is WorldPosition.Town -> {
                handles.add(CompiledHandleKind.Town, position.town.slug)
                handles.add(CompiledHandleKind.Building, position.building.slug)
            }
        }
    }

    private fun declareTriggerHandles(trigger: Trigger) {
        when (trigger) {
            is Trigger.AtLocation -> handles.add(CompiledHandleKind.Location, trigger.location.slug)
            is Trigger.OnTerrain -> handles.add(CompiledHandleKind.Terrain, trigger.terrain.slug)
            is Trigger.AtPosition,
            is Trigger.RandomStep,
            -> Unit
        }
    }

    private fun declareFlagState(state: FlagState) {
        handles.add(CompiledHandleKind.Flag, state.flag.slug)
        handles.add(CompiledHandleKind.FlagState, flagStateSlug(state))
    }

    private fun compileScripts(): CompiledScriptBundle {
        val catalog = game.runtimeContent().scriptRootCatalog()

        fun rootHandle(root: ScriptRootRef): CompiledHandleRef = handles.ref(CompiledHandleKind.ScriptRoot, scriptRootSlug(root))

        return CompiledScriptBundle(
            buildList {
                game.scenes.forEach { scene ->
                    val root = catalog.rootFor(ScriptRootRef.scene(scene.id))
                    add(CompiledScriptProgram(root, rootHandle(root), compileOps(scene.ops)))
                }
                game.dungeons.events.forEach { event ->
                    val root = catalog.rootFor(ScriptRootRef.dungeonEvent(event.id))
                    add(CompiledScriptProgram(root, rootHandle(root), compileOps(event.ops)))
                }
                game.spells.forEach { spell ->
                    val root = catalog.rootFor(ScriptRootRef.spell(spell.id))
                    add(CompiledScriptProgram(root, rootHandle(root), compileOps(spell.ops)))
                }
                game.itemUses.forEach { use ->
                    val root = catalog.rootFor(ScriptRootRef.itemUse(use.id))
                    add(CompiledScriptProgram(root, rootHandle(root), compileOps(use.ops)))
                }
                game.dialogue.forEach { dialogue ->
                    dialogue.branches.forEach { branch ->
                        val root = catalog.rootFor(ScriptRootRef.dialogueBranch(dialogue.npc, branch))
                        add(CompiledScriptProgram(root, rootHandle(root), compileOps(branch.ops)))
                    }
                }
                game.speak.questionActions.forEach { action ->
                    val root = catalog.rootFor(ScriptRootRef.questionAction(action))
                    add(CompiledScriptProgram(root, rootHandle(root), compileOps(action.ops)))
                }
            },
        )
    }

    private fun compileOps(ops: List<Op>): List<CompiledInstruction> = ops.map(::compileOp)

    private fun compileOp(op: Op): CompiledInstruction =
        when (op) {
            is Op.Screen -> instruction(CompiledOpcode.Screen, CompiledOperand.TextBlockValue(text(op.text)))
            is Op.Emit -> instruction(CompiledOpcode.Emit, CompiledOperand.Handle(textRef(op.text.id)))
            is Op.EmitIndexed ->
                instruction(
                    CompiledOpcode.EmitIndexed,
                    CompiledOperand.StringId(strings.id(op.pattern.pattern)),
                    CompiledOperand.IntExprValue(intExpr(op.value)),
                )
            is Op.WaitInput -> instruction(CompiledOpcode.WaitInput, op.reason?.let { CompiledOperand.StringId(strings.id(it)) })
            is Op.Pause -> instruction(CompiledOpcode.Pause, CompiledOperand.Integer(op.millis))
            is Op.View -> instruction(CompiledOpcode.View, CompiledOperand.ViewCommandValue(viewCommand(op.command)))
            is Op.LetInt ->
                instruction(
                    CompiledOpcode.LetInt,
                    CompiledOperand.StringId(strings.id(op.name)),
                    CompiledOperand.IntExprValue(intExpr(op.value)),
                )
            is Op.BindText ->
                instruction(
                    CompiledOpcode.BindText,
                    CompiledOperand.StringId(strings.id(op.name)),
                    CompiledOperand.StringExprValue(stringExpr(op.value)),
                )
            is Op.Set ->
                instruction(
                    CompiledOpcode.Set,
                    CompiledOperand.LValueValue(lvalue(op.target)),
                    CompiledOperand.IntExprValue(intExpr(op.value)),
                )
            is Op.Add ->
                instruction(
                    CompiledOpcode.Add,
                    CompiledOperand.LValueValue(lvalue(op.target)),
                    CompiledOperand.IntExprValue(intExpr(op.value)),
                )
            is Op.Sub ->
                instruction(
                    CompiledOpcode.Sub,
                    CompiledOperand.LValueValue(lvalue(op.target)),
                    CompiledOperand.IntExprValue(intExpr(op.value)),
                )
            is Op.AddClamped ->
                instruction(
                    CompiledOpcode.AddClamped,
                    CompiledOperand.LValueValue(lvalue(op.target)),
                    CompiledOperand.IntExprValue(intExpr(op.value)),
                    CompiledOperand.IntExprValue(intExpr(op.max)),
                )
            is Op.SetFlag ->
                instruction(
                    CompiledOpcode.SetFlag,
                    CompiledOperand.Handle(flagRef(op.slug)),
                    CompiledOperand.Handle(flagStateRef(op.slug, op.nativeValue)),
                )
            is Op.AppendJournal -> instruction(CompiledOpcode.AppendJournal, CompiledOperand.TextBlockValue(text(op.text)))
            is Op.If ->
                instruction(
                    CompiledOpcode.If,
                    CompiledOperand.BoolExprValue(boolExpr(op.cond)),
                    CompiledOperand.Instructions(compileOps(op.thenOps)),
                    CompiledOperand.Instructions(compileOps(op.elseOps)),
                )
            is Op.Loop -> instruction(CompiledOpcode.Loop, CompiledOperand.Instructions(compileOps(op.body)))
            is Op.Break ->
                instruction(
                    CompiledOpcode.Break,
                    op.reason?.let { CompiledOperand.StringId(strings.id(it)) },
                    CompiledOperand.EnumName(op.signal.name),
                )
            is Op.Stop ->
                instruction(
                    CompiledOpcode.Stop,
                    op.reason?.let { CompiledOperand.StringId(strings.id(it)) },
                    CompiledOperand.EnumName(op.signal.name),
                )
            is Op.Switch ->
                instruction(
                    CompiledOpcode.Switch,
                    CompiledOperand.IntExprValue(intExpr(op.expr)),
                    CompiledOperand.SwitchCases(op.cases.map { case -> CompiledSwitchCase(case.match, compileOps(case.body)) }),
                )
            is Op.Menu ->
                instruction(
                    CompiledOpcode.Menu,
                    CompiledOperand.TextBlockValue(text(op.prompt)),
                    CompiledOperand.MenuOptions(
                        op.options.map { option ->
                            CompiledMenuOption(binding(option.binding), compileOps(option.body))
                        },
                    ),
                    CompiledOperand.Instructions(compileOps(op.defaultOps)),
                )
            is Op.AskKey ->
                instruction(
                    CompiledOpcode.AskKey,
                    CompiledOperand.TextBlockValue(text(op.prompt)),
                    CompiledOperand.StringId(strings.id(op.varName)),
                    CompiledOperand.InputBindings(op.bindings.map(::binding)),
                )
            is Op.AskMember ->
                instruction(
                    CompiledOpcode.AskMember,
                    CompiledOperand.TextBlockValue(text(op.prompt)),
                    CompiledOperand.StringId(strings.id(op.varName)),
                    CompiledOperand.EnumName(op.filter.name),
                    CompiledOperand.EnumName(op.accepting.name),
                )
            is Op.AskInventorySlot ->
                instruction(
                    CompiledOpcode.AskInventorySlot,
                    CompiledOperand.TextBlockValue(text(op.prompt)),
                    CompiledOperand.StringId(strings.id(op.varName)),
                    CompiledOperand.Character(op.firstKey),
                    CompiledOperand.Integer(op.slots.first),
                    CompiledOperand.Integer(op.slots.last),
                    CompiledOperand.EnumName(op.filter.name),
                )
            is Op.AskText ->
                instruction(
                    CompiledOpcode.AskText,
                    CompiledOperand.TextBlockValue(text(op.prompt)),
                    CompiledOperand.TextAnswer(
                        CompiledTextAnswer(
                            acceptedStrings = op.accepted.map(strings::id),
                            matchOps = compileOps(op.matchOps),
                            missOps = compileOps(op.missOps),
                            caseSensitive = op.caseSensitive,
                            trim = op.trim,
                            maxLength = op.maxLength,
                        ),
                    ),
                )
            is Op.AskMonster ->
                instruction(
                    CompiledOpcode.AskMonster,
                    CompiledOperand.TextBlockValue(text(op.prompt)),
                    CompiledOperand.StringId(strings.id(op.varName)),
                )
            is Op.ForEachMember ->
                instruction(
                    CompiledOpcode.ForEachMember,
                    CompiledOperand.StringId(strings.id(op.varName)),
                    CompiledOperand.BooleanValue(op.includeDead),
                    CompiledOperand.Instructions(compileOps(op.body)),
                )
            is Op.ForEachMonster ->
                instruction(
                    CompiledOpcode.ForEachMonster,
                    CompiledOperand.StringId(strings.id(op.varName)),
                    CompiledOperand.BooleanValue(op.includeDead),
                    CompiledOperand.Instructions(compileOps(op.body)),
                )
            is Op.Speak -> instruction(CompiledOpcode.Speak, CompiledOperand.Handle(buildingRef(op.building)))
            is Op.TellRumour -> instruction(CompiledOpcode.TellRumour, CompiledOperand.Handle(rumourTableRef(op.table)))
            is Op.RunScene ->
                instruction(
                    CompiledOpcode.RunScene,
                    CompiledOperand.Handle(sceneRef(op.scene)),
                    CompiledOperand.StringIdList(op.bindings.flatMap { (key, value) -> listOf(strings.id(key), strings.id(value)) }),
                )
            is Op.Encounter ->
                instruction(
                    CompiledOpcode.Encounter,
                    CompiledOperand.Handle(monsterRef(op.foe)),
                    CompiledOperand.BooleanValue(op.mandatory),
                    op.tier?.let { CompiledOperand.Handle(encounterTierRef(it)) },
                )
            is Op.EnterDungeon ->
                instruction(
                    CompiledOpcode.EnterDungeon,
                    CompiledOperand.Handle(dungeonRef(op.dungeon)),
                    CompiledOperand.Integer(op.levels),
                )
            Op.ConsumeActiveItem -> instruction(CompiledOpcode.ConsumeActiveItem)
            is Op.ClearInventorySlot ->
                instruction(
                    CompiledOpcode.ClearInventorySlot,
                    CompiledOperand.IntExprValue(intExpr(op.slot)),
                )
            is Op.ConsumeInventoryPool ->
                instruction(
                    CompiledOpcode.ConsumeInventoryPool,
                    CompiledOperand.Handle(itemPoolRef(op.pool)),
                    CompiledOperand.IntExprValue(intExpr(op.quantity)),
                )
            Op.ConsumeTorch -> instruction(CompiledOpcode.ConsumeTorch)
            is Op.TeleportParty ->
                instruction(
                    CompiledOpcode.TeleportParty,
                    CompiledOperand.IntExprValue(intExpr(op.directionKey)),
                    CompiledOperand.IntExprValue(intExpr(op.distance)),
                )
            is Op.DiscoverCave ->
                instruction(
                    CompiledOpcode.DiscoverCave,
                    CompiledOperand.Handle(dungeonRef(op.dungeon)),
                    CompiledOperand.IntExprValue(intExpr(op.levels)),
                    CompiledOperand.IntExprValue(intExpr(op.moveCost)),
                )
            is Op.GrantReward -> instruction(CompiledOpcode.GrantReward, CompiledOperand.Reward(reward(op.reward)))
            is Op.RandomItemOffer ->
                instruction(
                    CompiledOpcode.RandomItemOffer,
                    CompiledOperand.RandomItemOffer(randomItemOffer(op.offer)),
                )
            is Op.ShopService -> instruction(CompiledOpcode.ShopService, CompiledOperand.ShopService(shopService(op.shop)))
            is Op.AchieveMilestone ->
                instruction(
                    CompiledOpcode.AchieveMilestone,
                    CompiledOperand.Handle(milestoneRef(op.milestone)),
                )
            is Op.NativeCall ->
                instruction(
                    CompiledOpcode.NativeCall,
                    CompiledOperand.StringId(strings.id(op.path)),
                    CompiledOperand.StringIdList(op.args.map(strings::id)),
                )
        }

    private fun instruction(
        opcode: CompiledOpcode,
        vararg operands: CompiledOperand?,
    ): CompiledInstruction = CompiledInstruction(opcode, operands.filterNotNull())

    private fun intExpr(expr: IntAst): CompiledIntExpr =
        when (expr) {
            is IntAst.Lit -> CompiledIntExpr.Literal(expr.value)
            is IntAst.Var -> CompiledIntExpr.Local(strings.id(expr.name))
            is IntAst.Binary -> CompiledIntExpr.Binary(intExpr(expr.left), binaryOp(expr.op), intExpr(expr.right))
            is IntAst.If -> CompiledIntExpr.If(boolExpr(expr.cond), intExpr(expr.thenValue), intExpr(expr.elseValue))
            is IntAst.Random -> CompiledIntExpr.Random(intExpr(expr.lo), intExpr(expr.hi))
            is IntAst.MemberField -> CompiledIntExpr.MemberField(intExpr(expr.member), strings.id(expr.field))
            is IntAst.MonsterField -> CompiledIntExpr.MonsterField(intExpr(expr.monster), strings.id(expr.field))
            is IntAst.BattleFlag -> CompiledIntExpr.BattleFlag(strings.id(expr.name))
            is IntAst.BattleQuery -> CompiledIntExpr.BattleQuery(strings.id(expr.name))
            is IntAst.RealmScalar -> CompiledIntExpr.RealmScalar(strings.id(expr.name))
            is IntAst.PartyScalar -> CompiledIntExpr.PartyScalar(strings.id(expr.name))
            is IntAst.InventoryPoolCount -> CompiledIntExpr.InventoryPoolCount(itemPoolRef(expr.pool))
            is IntAst.InventorySlotCost -> CompiledIntExpr.InventorySlotCost(intExpr(expr.slot))
            is IntAst.NativeInt -> CompiledIntExpr.NativeInt(strings.id(expr.path), expr.args.map(strings::id))
        }

    private fun binaryOp(op: String): CompiledBinaryOp =
        when (op) {
            "+", "add" -> CompiledBinaryOp.Add
            "-", "sub" -> CompiledBinaryOp.Sub
            "*", "mul" -> CompiledBinaryOp.Mul
            "/", "div" -> CompiledBinaryOp.Div
            "%", "mod" -> CompiledBinaryOp.Mod
            else -> error("unknown int operator '$op'")
        }

    private fun boolExpr(expr: BoolAst): CompiledBoolExpr =
        when (expr) {
            is BoolAst.Cmp -> CompiledBoolExpr.Cmp(intExpr(expr.left), comparisonOp(expr.op), intExpr(expr.right))
            is BoolAst.Not -> CompiledBoolExpr.Not(boolExpr(expr.expr))
            is BoolAst.And -> CompiledBoolExpr.And(boolExpr(expr.left), boolExpr(expr.right))
            is BoolAst.Or -> CompiledBoolExpr.Or(boolExpr(expr.left), boolExpr(expr.right))
            is BoolAst.FlagIs -> CompiledBoolExpr.FlagIs(flagRef(expr.slug), flagStateRef(expr.slug, expr.nativeValue))
            is BoolAst.MilestoneAchieved -> CompiledBoolExpr.MilestoneAchieved(milestoneRef(expr.milestone))
            is BoolAst.MemberFlag -> CompiledBoolExpr.MemberFlag(intExpr(expr.member), strings.id(expr.name))
            is BoolAst.MonsterFlag -> CompiledBoolExpr.MonsterFlag(intExpr(expr.monster), strings.id(expr.name))
            is BoolAst.BattleFlag -> CompiledBoolExpr.BattleFlag(strings.id(expr.name))
            is BoolAst.RuntimeFlag -> CompiledBoolExpr.RuntimeFlag(strings.id(expr.name))
        }

    private fun comparisonOp(op: String): CompiledComparisonOp =
        when (op) {
            "==", "eq" -> CompiledComparisonOp.Eq
            "!=", "ne" -> CompiledComparisonOp.Ne
            "<", "lt" -> CompiledComparisonOp.Lt
            "<=", "lte" -> CompiledComparisonOp.Lte
            ">", "gt" -> CompiledComparisonOp.Gt
            ">=", "gte" -> CompiledComparisonOp.Gte
            else -> error("unknown comparison operator '$op'")
        }

    private fun stringExpr(expr: StrAst): CompiledStringExpr =
        when (expr) {
            is StrAst.NativeString ->
                CompiledStringExpr.NativeString(
                    pathString = strings.id(expr.path),
                    args =
                        expr.args.map { arg ->
                            when (arg) {
                                is NativeStringArg.Literal -> CompiledNativeStringArg.Literal(strings.id(arg.value))
                                is NativeStringArg.IntValue -> CompiledNativeStringArg.IntValue(intExpr(arg.value))
                            }
                        },
                )
        }

    private fun lvalue(value: LValueAst): CompiledLValue =
        when (value) {
            is LValueAst.RealmScalar -> CompiledLValue.RealmScalar(strings.id(value.name))
            is LValueAst.PartyScalar -> CompiledLValue.PartyScalar(strings.id(value.name))
            is LValueAst.MemberField -> CompiledLValue.MemberField(intExpr(value.member), strings.id(value.field))
            is LValueAst.MonsterField -> CompiledLValue.MonsterField(intExpr(value.monster), strings.id(value.field))
            is LValueAst.BattleFlag -> CompiledLValue.BattleFlag(strings.id(value.name))
        }

    private fun text(block: TextBlock): CompiledTextBlock =
        when (block) {
            is TextBlock.Ref -> CompiledTextBlock.Ref(textRef(block.id.id))
            is TextBlock.Inline -> CompiledTextBlock.Inline(block.lines.map(strings::id))
            is TextBlock.Structured ->
                CompiledTextBlock.Structured(
                    block.lines.map { line -> CompiledTextLine(line.parts.map(::textPart)) },
                )
        }

    private fun textPart(part: TextPart): CompiledTextPart =
        when (part) {
            is TextPart.Literal -> CompiledTextPart.Literal(strings.id(part.text))
            is TextPart.IntValue -> CompiledTextPart.IntValue(intExpr(part.value))
            is TextPart.StringValue -> CompiledTextPart.StringValue(stringExpr(part.value))
            is TextPart.RoleName -> CompiledTextPart.RoleName(roleRef(part.role))
            is TextPart.RoleLocation -> CompiledTextPart.RoleLocation(roleRef(part.role))
            is TextPart.TownName -> CompiledTextPart.TownName(townRef(part.town))
            is TextPart.FuzzyTownLocation -> CompiledTextPart.FuzzyTownLocation(townRef(part.town), part.radius)
            is TextPart.LocationCoordinate -> CompiledTextPart.LocationCoordinate(locationRef(part.location))
            TextPart.OfferedItemName -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.OfferedItemName)
            TextPart.OfferedItemPrice -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.OfferedItemPrice)
            TextPart.SelectedItemName -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.SelectedItemName)
            TextPart.ShopPrice -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.ShopPrice)
            TextPart.ShopCarryCap -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.ShopCarryCap)
            TextPart.HuntMemberName -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.HuntMemberName)
            TextPart.HuntMemberSubjectPronoun -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.HuntMemberSubjectPronoun)
            TextPart.HuntFoodGained -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.HuntFoodGained)
            TextPart.PartyMemberMax -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.PartyMemberMax)
            TextPart.WildernessDungeonName -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.WildernessDungeonName)
            TextPart.WildernessDungeonLevels -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.WildernessDungeonLevels)
            TextPart.DungeonDepth -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.DungeonDepth)
            TextPart.ConversationNpcName -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.ConversationNpcName)
            TextPart.CurrentTownName -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.CurrentTownName)
            TextPart.TownTaxPercent -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.TownTaxPercent)
            TextPart.TownTaxAmount -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.TownTaxAmount)
            TextPart.CreationMemberNumber -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.CreationMemberNumber)
            TextPart.CreationMemberName -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.CreationMemberName)
            TextPart.CreationWeaponSkill -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.CreationWeaponSkill)
            TextPart.CreationHealth -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.CreationHealth)
            TextPart.CreationWisdom -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.CreationWisdom)
            TextPart.CreationCharisma -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.CreationCharisma)
            TextPart.BattleFoeDisplayName -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.BattleFoeDisplayName)
            TextPart.BattleAttackerName -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.BattleAttackerName)
            TextPart.BattleTargetName -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.BattleTargetName)
            TextPart.BattleDamage -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.BattleDamage)
            TextPart.BattleExperience -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.BattleExperience)
            TextPart.BattleGold -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.BattleGold)
            TextPart.BattleLootName -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.BattleLootName)
            TextPart.BattleLevelMemberName -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.BattleLevelMemberName)
            TextPart.BattleLevelsGained -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.BattleLevelsGained)
        }

    private fun viewCommand(command: ViewCommand): CompiledViewCommand =
        when (command) {
            ViewCommand.ClearText -> CompiledViewCommand.ClearText
            ViewCommand.ClearMapWindow -> CompiledViewCommand.ClearMapWindow
            ViewCommand.RenderMap -> CompiledViewCommand.RenderMap
            ViewCommand.StatusLine -> CompiledViewCommand.StatusLine
            is ViewCommand.ShowImage -> CompiledViewCommand.ShowImage(imageRef(command.image))
        }

    private fun binding(binding: InputBindingModel): CompiledInputBinding =
        CompiledInputBinding(
            primaryString = strings.id(binding.primary),
            aliasStrings = binding.aliases.map(strings::id),
            labelString = strings.id(binding.label),
        )

    private fun reward(reward: ScriptRewardModel): CompiledScriptReward =
        CompiledScriptReward(
            gold = reward.gold?.let(::intExpr),
            items = reward.items.map(::itemRef),
            application = reward.application,
            messages = reward.messages.messages(),
        )

    private fun RewardMessageModel.messages(): CompiledRewardMessages =
        CompiledRewardMessages(
            cantCarry = cantCarry?.let(::text),
            pickedUp = pickedUp?.let(::text),
        )

    private fun randomItemOffer(offer: RandomItemOfferModel): CompiledRandomItemOffer =
        CompiledRandomItemOffer(
            pool = itemPoolRef(offer.pool),
            offer = text(offer.offer),
            choicePrompt = text(offer.choicePrompt),
            broke = text(offer.broke),
            deal = text(offer.deal),
            declined = offer.declined?.let(::text),
            application = offer.application,
            messages = offer.messages.messages(),
            buy = binding(offer.buy),
            decline = binding(offer.decline),
            priceOverride = offer.priceOverride,
            pauseMillis =
                CompiledOfferPauses(
                    broke = offer.brokePauseMillis,
                    full = offer.fullPauseMillis,
                    deal = offer.dealPauseMillis,
                    declined = offer.declinedPauseMillis,
                ),
        )

    private fun shopService(service: ShopServiceModel): CompiledShopService =
        CompiledShopService(
            building = buildingRef(service.building),
            image = imageRef(service.image),
            closed = text(service.closed),
            intro = text(service.intro),
            menu = text(service.menu),
            keys =
                CompiledShopMenuKeys(
                    buy = binding(service.keys.buy),
                    sell = binding(service.keys.sell),
                    leave = binding(service.keys.leave),
                ),
            goodbye = text(service.goodbye),
            buy = service.buy?.let(::shopBuy),
            sell = service.sell?.let(::shopSell),
            interlude = service.interlude?.let(::shopInterlude),
        )

    private fun shopBuy(buy: ShopBuyModel): CompiledShopBuy =
        CompiledShopBuy(
            prompt = text(buy.prompt),
            catalogs = buy.catalogs.map(::shopCatalog),
            tooExpensive = text(buy.tooExpensive),
            cantCarry = text(buy.cantCarry),
            deal = text(buy.deal),
            unitPrice = buy.unitPrice?.let(::text),
            singlePrice = buy.singlePrice?.let(::text),
            payPrompt = text(buy.payPrompt),
            paymentKeys = CompiledShopPurchaseKeys(binding(buy.paymentKeys.pay), binding(buy.paymentKeys.decline)),
            inventoryApplication = buy.inventoryApplication,
        )

    private fun shopCatalog(catalog: ShopCatalogModel): CompiledShopCatalog =
        CompiledShopCatalog(
            binding = binding(catalog.binding),
            titleString = strings.id(catalog.title),
            intro = catalog.intro?.let(::text),
            pages =
                catalog.pages.map { page ->
                    CompiledShopCatalogPage(
                        entries = page.entries.map(::shopItemOffer),
                        footer = page.footer?.let(::text),
                        navigation =
                            CompiledShopCatalogPageNavigation(
                                previous = page.navigation.previous?.let(::binding),
                                next = page.navigation.next?.let(::binding),
                            ),
                    )
                },
        )

    private fun shopItemOffer(offer: ShopItemOfferModel): CompiledShopItemOffer =
        CompiledShopItemOffer(
            binding = binding(offer.binding),
            item = itemRef(offer.item),
            labelString = offer.label?.let(strings::id),
            description = offer.description?.let(::text),
            price = shopPrice(offer.price),
            purchase = shopPurchase(offer.purchase),
        )

    private fun shopPrice(price: ShopPriceModel): CompiledShopPrice =
        when (price) {
            ShopPriceModel.ItemCost -> CompiledShopPrice.ItemCost
            ShopPriceModel.HalfItemCost -> CompiledShopPrice.HalfItemCost
            is ShopPriceModel.Fixed -> CompiledShopPrice.Fixed(price.amount)
            is ShopPriceModel.LeaderLevelPlus -> CompiledShopPrice.LeaderLevelPlus(price.addend)
            is ShopPriceModel.ItemCostPlusLeaderLevelTimes ->
                CompiledShopPrice.ItemCostPlusLeaderLevelTimes(price.multiplier)
        }

    private fun shopPurchase(purchase: ShopPurchaseModel): CompiledShopPurchase =
        when (purchase) {
            ShopPurchaseModel.InventoryItem -> CompiledShopPurchase.InventoryItem
            is ShopPurchaseModel.Bulk ->
                CompiledShopPurchase.Bulk(
                    resource = purchase.resource,
                    quantityPrompt = text(purchase.quantityPrompt),
                    cap = purchase.cap,
                    tooMany = text(purchase.tooMany),
                    zeroQuantityDeal = purchase.zeroQuantityDeal,
                )
        }

    private fun shopSell(sell: ShopSellModel): CompiledShopSell =
        CompiledShopSell(
            prompt = text(sell.prompt),
            acceptedKinds = sell.acceptedKinds,
            acceptedItems = sell.acceptedItems.sortedBy { item -> item.slug }.map(::itemRef),
            rejectedItems = sell.rejectedItems.sortedBy { item -> item.slug }.map(::itemRef),
            wrongKind = text(sell.wrongKind),
            offer = text(sell.offer),
            declined = sell.declined?.let(::text),
            sold = sell.sold?.let(::text),
            price = shopPrice(sell.price),
            bargain = sell.bargain,
            saleKeys = CompiledShopSaleKeys(
                binding(sell.saleKeys.accept),
                binding(sell.saleKeys.decline),
                binding(sell.saleKeys.bargain),
            ),
        )

    private fun shopInterlude(interlude: ShopInterludeModel): CompiledShopInterlude =
        CompiledShopInterlude(
            chancePercent = interlude.chancePercent,
            offer = text(interlude.offer),
            accept = binding(interlude.accept),
            decline = binding(interlude.decline),
            accepted = interlude.accepted.map(::text),
            declined = text(interlude.declined),
            runsRumourAfterAccept = interlude.runsRumourAfterAccept,
            forceFirstUntil = interlude.forceFirstUntil?.let { state -> flagStateRef(state.flag.slug, state.native) },
        )

    private fun compileItem(item: ItemRecordModel): CompiledItemRecord =
        CompiledItemRecord(
            id = itemRef(item.id),
            kind = item.kind,
            displayNameString = strings.id(item.displayName),
            cost = item.cost,
            storage = item.storage,
            weaponDamage = item.weaponDamage,
            armorClass = item.armorClass,
            equippableBy = item.equippableBy,
            campImage = item.campImage?.let(::imageRef),
            campProtection = item.campProtection,
            resourceCapacityBonus = item.resourceCapacityBonus,
        )

    private fun compileItemPool(pool: ItemPoolId): CompiledItemPool = CompiledItemPool(itemPoolRef(pool), pool.items.map(::itemRef))

    private fun compileTerrain(terrain: TerrainSemanticsModel): CompiledTerrain =
        CompiledTerrain(
            id = terrainRef(terrain.id),
            mapCode = terrain.mapCode,
            passable = terrain.passable,
            movementCost = terrain.movementCost,
            allowsNpcPlacement = terrain.allowsNpcPlacement,
            settlement = terrain.settlement,
            tags = terrain.tags.map(::terrainTagRef),
        )

    private fun compileWorldMap(map: WorldMapModel): CompiledWorldMap =
        CompiledWorldMap(
            id = worldMapRef(map.id),
            width = map.size?.width,
            height = map.size?.height,
            storageCapacity = map.storageCapacity,
            layers =
                map.layers.map { layer ->
                    CompiledWorldMapLayer(mapLayerRef(layer.id), layer.rows.map(strings::id))
                },
        )

    private fun compileImage(image: ImageAssetModel): CompiledImageAsset =
        CompiledImageAsset(
            id = imageRef(image.id),
            kind = image.kind,
            resourceString = strings.id(image.resource),
            placement = image.placement,
        )

    private fun compileStaticScreen(screen: StaticScreenModel): CompiledStaticScreen =
        CompiledStaticScreen(
            id = staticScreenRef(screen.id),
            image = screen.image?.let(::imageRef),
            resourceString = screen.resource?.let(strings::id),
            commandHelp = screen.commandHelp,
            runtimeReport = screen.runtimeReport,
        )

    private fun compileRegion(region: ScreenRegionModel): CompiledScreenRegion =
        CompiledScreenRegion(screenRegionRef(region.id), region.x, region.y, region.width, region.height)

    private fun compileTown(town: TownModel): CompiledTown =
        CompiledTown(
            id = townRef(town.id),
            nameString = strings.id(town.name),
            position = town.position,
            taxPercent = town.taxPercent,
            kind = town.kind,
            services = town.services.map { service -> buildingRef(service.building) },
        )

    private fun compileBuildingService(service: BuildingServiceModel): CompiledBuildingService =
        CompiledBuildingService(
            building = buildingRef(service.building),
            action = keyActionRef(service.action),
            scene = sceneRef(service.scene),
            opensAt = service.opensAt,
            closesAt = service.closesAt,
            nativeServices = service.nativeServices,
        )

    private fun compileCharacter(character: CharacterModel): CompiledCharacter =
        CompiledCharacter(
            id = npcRef(character.id),
            nameString = strings.id(character.name),
            job = npcJobRef(character.job),
            gender = character.gender,
            placement =
                when (val placement = character.placement) {
                    is CharacterPlacementModel.Fixed -> CompiledCharacterPlacement.Fixed(position(placement.position))
                    CharacterPlacementModel.Randomized -> CompiledCharacterPlacement.Randomized
                    CharacterPlacementModel.RandomizedWilderness -> CompiledCharacterPlacement.RandomizedWilderness
                },
            roles = character.roles.map(::roleRef),
        )

    private fun compileMonster(monster: MonsterModel): CompiledMonster =
        CompiledMonster(
            id = monsterRef(monster.id),
            nameString = strings.id(monster.name),
            count = monster.count,
            countMaxLevelDivisor = monster.countMaxLevelDivisor,
            hitPoints = monster.hitPoints,
            weaponSkill = monster.weaponSkill,
            spellPower = monster.spellPower,
            tier = monster.tier?.let(::encounterTierRef),
            immune = monster.immune,
        )

    private fun compileMission(mission: MissionModel): CompiledMission =
        CompiledMission(
            id = missionRef(mission.id),
            trigger = trigger(mission.trigger),
            requires = mission.requires?.let(::boolExpr),
            action = sceneRef(mission.action),
        )

    private fun compileFlag(flag: FlagContentModel): CompiledFlag =
        CompiledFlag(
            id = flagRef(flag.ref.slug),
            scope = flag.ref.scope,
            states =
                flag.states.map { state ->
                    CompiledFlagState(
                        id = flagStateRef(state.flag.slug, state.native),
                        nativeString = strings.id(state.native),
                    )
                },
        )

    private fun compileFormula(formula: FormulaModel): CompiledFormula =
        CompiledFormula(
            id = formulaRef(formula.id),
            value = intExpr(formula.value),
        )

    private fun compileRewardBundle(reward: RewardBundleModel): CompiledRewardBundle =
        CompiledRewardBundle(
            id = rewardBundleRef(reward.id),
            experience = formulaRef(reward.experience),
            gold = formulaRef(reward.gold),
            application = reward.application,
            loot = lootTable(reward.loot),
        )

    private fun lootTable(loot: LootTableModel): CompiledLootTable =
        CompiledLootTable(
            id = lootTableRef(loot.id),
            dropChancePercent = loot.dropChancePercent,
            treasureChancePercent = loot.treasureChancePercent,
            treasure = itemPoolRef(loot.treasure),
            magic = itemPoolRef(loot.magic),
        )

    private fun compileSpell(spell: SpellModel): CompiledSpell =
        CompiledSpell(
            id = spellRef(spell.id),
            slot = spell.slot,
            baseCost = spell.baseCost,
            nameString = strings.id(spell.name),
            scriptRoot = scriptRootRef(ScriptRootRef.spell(spell.id)),
        )

    private fun compileItemUse(use: ItemUseModel): CompiledItemUse =
        CompiledItemUse(
            item = itemRef(use.id),
            contexts = use.contexts,
            consume = use.consume,
            scriptRoot = scriptRootRef(ScriptRootRef.itemUse(use.id)),
        )

    private fun compileDungeonEvent(event: DungeonEventModel): CompiledDungeonEvent =
        CompiledDungeonEvent(
            id = dungeonEventRef(event.id),
            disposition = event.disposition,
            scriptRoot = scriptRootRef(ScriptRootRef.dungeonEvent(event.id)),
            randomEncounterTier = event.randomEncounterTier?.let(::encounterTierRef),
            clearWhenFlag = event.clearWhenFlag?.let { flagStateRef(it.flag.slug, it.native) },
        )

    private fun compileDungeonScenario(scenario: DungeonScenarioModel): CompiledDungeonScenario =
        CompiledDungeonScenario(
            dungeon = dungeonRef(scenario.dungeon),
            nameString = scenario.name?.let(strings::id),
            levelFirst = scenario.levelRange?.first,
            levelLast = scenario.levelRange?.last,
            fixedLevelEvents = scenario.fixedLevelEvents.mapValues { (_, event) -> dungeonEventRef(event) },
            randomEvents =
                scenario.randomEvents.map { event ->
                    CompiledDungeonRandomEvent(dungeonEventRef(event.event), event.weight)
                },
        )

    private fun trigger(trigger: Trigger): CompiledTrigger =
        when (trigger) {
            is Trigger.RandomStep -> CompiledTrigger.RandomStep(trigger.percent)
            is Trigger.AtPosition -> CompiledTrigger.AtPosition(trigger.point)
            is Trigger.AtLocation -> CompiledTrigger.AtLocation(locationRef(trigger.location))
            is Trigger.OnTerrain -> CompiledTrigger.OnTerrain(terrainRef(trigger.terrain))
        }

    private fun compileState(snapshot: RuntimeSnapshotModel): CompiledStateImage = compileStateImage(snapshot)

    private fun compileStateImage(snapshot: RuntimeSnapshotModel): CompiledStateImage {
        val state = snapshot.state
        return CompiledStateImage(
            state = stateRef(state.id),
            kind = state.kind,
            metadata = snapshot.metadata,
            realm = realm(state.realm),
            locations = state.locations.map { CompiledLocationPlacement(locationRef(it.location), it.point) },
            npcs = state.npcs.map { CompiledNpcPlacement(npcRef(it.npc), position(it.position)) },
            parties = state.parties.map(::party),
            mode = snapshot.mode?.let(::mode),
        )
    }

    private fun realm(realm: RealmState): CompiledRealmImage =
        CompiledRealmImage(
            day = realm.day,
            hour = realm.hour,
            minute = realm.minute,
            rngState = realm.rngState,
            activeParty = partyRef(realm.activeParty),
            currentMap = worldMapRef(realm.currentMap),
            currentMode = realm.currentMode,
            currentTown = realm.currentTown?.let(::townRef),
            banished = realm.banished,
            lastPlayedDay = realm.lastPlayedDay,
            partyMemberMax = realm.partyMemberMax,
            lastTerrain = realm.lastTerrain?.let(::terrainRef),
        )

    private fun party(party: PartyState): CompiledPartyImage =
        CompiledPartyImage(
            id = partyRef(party.id),
            playerString = strings.id(party.player),
            nameString = strings.id(party.name),
            position = position(party.position),
            facing = party.facing,
            resources = party.resources,
            counters =
                CompiledPartyCounters(
                    memberCount = party.counters.memberCount,
                    kills = party.counters.kills,
                    waitingMail = party.counters.waitingMail,
                    attacked = party.counters.attacked,
                    attackedToday =
                        party.counters.attackedToday.map {
                            CompiledPartyAttackRecord(partyRef(it.party), it.attacked)
                        },
                ),
            flags = party.flags.map { flagStateRef(it.flag.slug, it.native) },
            milestones = party.milestones.map(::milestoneRef),
            inventory = party.inventory.map { slot -> CompiledInventorySlot(slot.slot, slot.item?.let(::itemRef)) },
            members = party.members.map(::member),
            visitedNpcs = party.visitedNpcs.map { CompiledNpcVisit(npcRef(it.npc), it.visited) },
            journal = party.journal.map(::journalEntry),
        )

    private fun member(member: PartyMemberState): CompiledPartyMember =
        CompiledPartyMember(
            nameString = strings.id(member.name),
            gender = member.gender,
            race = member.race,
            pcClass = member.pcClass,
            level = member.level,
            health = member.health,
            hitPoints = member.hitPoints,
            weaponSkill = member.weaponSkill,
            wisdom = member.wisdom,
            mana = member.mana,
            charisma = member.charisma,
            equipment = CompiledEquipment(itemRef(member.equipment.weapon), itemRef(member.equipment.armor)),
            experience = member.experience,
            dead = member.dead,
        )

    private fun journalEntry(entry: JournalEntryState): CompiledJournalEntry =
        when (entry) {
            is JournalEntryState.Text -> CompiledJournalEntry.Text(textRef(entry.id.id))
            is JournalEntryState.Line -> CompiledJournalEntry.Line(strings.id(entry.text))
        }

    private fun mode(mode: RuntimeModeSnapshot): CompiledRuntimeModeImage =
        when (mode) {
            is RuntimeModeSnapshot.Castle -> CompiledRuntimeModeImage.Castle(mode.session)
            is RuntimeModeSnapshot.Dungeon ->
                CompiledRuntimeModeImage.Dungeon(
                    session =
                        CompiledDungeonSessionImage(
                            dungeon = dungeonRef(mode.session.dungeon),
                            maxDepth = mode.session.maxDepth,
                            depth = mode.session.depth,
                            levelEvents = mode.session.levelEvents.mapValues { (_, event) -> dungeonEventRef(event) },
                            step = mode.session.step,
                        ),
                    postDungeon =
                        mode.postDungeon?.let {
                            CompiledMissionPostDungeonImage(
                                mission = missionRef(it.mission),
                                scene = sceneRef(it.scene),
                                continuation = it.continuation,
                            )
                        },
                )
        }

    private fun position(position: WorldPosition): CompiledWorldPosition =
        when (position) {
            is WorldPosition.Wilderness -> CompiledWorldPosition.Wilderness(position.point)
            is WorldPosition.Town -> CompiledWorldPosition.Town(townRef(position.town), buildingRef(position.building))
        }

    private fun itemRef(id: ItemId): CompiledHandleRef = handles.ref(CompiledHandleKind.Item, id.slug)

    private fun itemPoolRef(id: ItemPoolId): CompiledHandleRef = handles.ref(CompiledHandleKind.ItemPool, id.slug)

    private fun monsterRef(id: MonsterId): CompiledHandleRef = handles.ref(CompiledHandleKind.Monster, id.slug)

    private fun encounterTierRef(id: EncounterTier): CompiledHandleRef = handles.ref(CompiledHandleKind.EncounterTier, id.slug)

    private fun worldMapRef(id: WorldMapId): CompiledHandleRef = handles.ref(CompiledHandleKind.WorldMap, id.slug)

    private fun mapLayerRef(id: MapLayerId): CompiledHandleRef = handles.ref(CompiledHandleKind.MapLayer, id.slug)

    private fun terrainRef(id: TerrainId): CompiledHandleRef = handles.ref(CompiledHandleKind.Terrain, id.slug)

    private fun terrainTagRef(id: TerrainTagId): CompiledHandleRef = handles.ref(CompiledHandleKind.TerrainTag, id.slug)

    private fun imageRef(id: ImageId): CompiledHandleRef = handles.ref(CompiledHandleKind.Image, id.slug)

    private fun staticScreenRef(id: StaticScreenId): CompiledHandleRef = handles.ref(CompiledHandleKind.StaticScreen, id.slug)

    private fun screenRegionRef(id: ScreenRegionId): CompiledHandleRef = handles.ref(CompiledHandleKind.ScreenRegion, id.slug)

    private fun sceneRef(id: SceneId): CompiledHandleRef = handles.ref(CompiledHandleKind.Scene, id.slug)

    private fun spellRef(id: SpellId): CompiledHandleRef = handles.ref(CompiledHandleKind.Spell, id.slug)

    private fun formulaRef(id: FormulaId): CompiledHandleRef = handles.ref(CompiledHandleKind.Formula, id.slug)

    private fun rewardBundleRef(id: RewardBundleId): CompiledHandleRef = handles.ref(CompiledHandleKind.RewardBundle, id.slug)

    private fun lootTableRef(id: LootTableId): CompiledHandleRef = handles.ref(CompiledHandleKind.LootTable, id.slug)

    private fun missionRef(id: MissionId): CompiledHandleRef = handles.ref(CompiledHandleKind.Mission, id.slug)

    private fun npcJobRef(id: NpcJobId): CompiledHandleRef = handles.ref(CompiledHandleKind.NpcJob, id.slug)

    private fun roleRef(id: NpcRole): CompiledHandleRef = handles.ref(CompiledHandleKind.Role, id.slug)

    private fun stateRef(id: StateId): CompiledHandleRef = handles.ref(CompiledHandleKind.State, id.slug)

    private fun partyRef(id: PartyId): CompiledHandleRef = handles.ref(CompiledHandleKind.Party, id.slug)

    private fun npcRef(id: NpcId): CompiledHandleRef = handles.ref(CompiledHandleKind.Npc, id.slug)

    private fun locationRef(id: LocationId): CompiledHandleRef = handles.ref(CompiledHandleKind.Location, id.slug)

    private fun townRef(id: TownId): CompiledHandleRef = handles.ref(CompiledHandleKind.Town, id.slug)

    private fun buildingRef(id: BuildingId): CompiledHandleRef = handles.ref(CompiledHandleKind.Building, id.slug)

    private fun keyActionRef(id: KeyActionId): CompiledHandleRef = handles.ref(CompiledHandleKind.KeyAction, id.slug)

    private fun dungeonRef(id: DungeonId): CompiledHandleRef = handles.ref(CompiledHandleKind.Dungeon, id.slug)

    private fun dungeonEventRef(id: DungeonEventId): CompiledHandleRef = handles.ref(CompiledHandleKind.DungeonEvent, id.slug)

    private fun milestoneRef(id: GameMilestoneId): CompiledHandleRef = handles.ref(CompiledHandleKind.Milestone, id.slug)

    private fun rumourTableRef(id: RumourTableId): CompiledHandleRef = handles.ref(CompiledHandleKind.RumourTable, id.slug)

    private fun textRef(id: String): CompiledHandleRef = handles.ref(CompiledHandleKind.Text, id)

    private fun flagRef(slug: String): CompiledHandleRef = handles.ref(CompiledHandleKind.Flag, slug)

    private fun flagStateRef(
        flag: String,
        native: String,
    ): CompiledHandleRef = handles.ref(CompiledHandleKind.FlagState, "$flag.$native")

    private fun scriptRootRef(root: ScriptRootRef): CompiledHandleRef {
        val catalog = game.runtimeContent().scriptRootCatalog()
        val resolved = catalog.rootFor(root)
        return handles.ref(CompiledHandleKind.ScriptRoot, scriptRootSlug(resolved))
    }
}

private fun GameModel.locationsFromStatesAndScripts(): List<LocationId> =
    buildList {
        fun collectText(block: TextBlock) {
            when (block) {
                is TextBlock.Ref,
                is TextBlock.Inline,
                -> Unit
                is TextBlock.Structured ->
                    block.lines.forEach { line ->
                        line.parts.forEach { part ->
                            if (part is TextPart.LocationCoordinate) add(part.location)
                        }
                    }
            }
        }

        fun collectOps(ops: List<Op>) {
            ops.forEach { op ->
                when (op) {
                    is Op.Screen -> collectText(op.text)
                    is Op.AppendJournal -> collectText(op.text)
                    is Op.If -> {
                        collectOps(op.thenOps)
                        collectOps(op.elseOps)
                    }
                    is Op.Loop -> collectOps(op.body)
                    is Op.Switch -> op.cases.forEach { collectOps(it.body) }
                    is Op.Menu -> {
                        collectText(op.prompt)
                        op.options.forEach { collectOps(it.body) }
                        collectOps(op.defaultOps)
                    }
                    is Op.AskKey -> collectText(op.prompt)
                    is Op.AskMember -> collectText(op.prompt)
                    is Op.AskInventorySlot -> collectText(op.prompt)
                    is Op.AskText -> {
                        collectText(op.prompt)
                        collectOps(op.matchOps)
                        collectOps(op.missOps)
                    }
                    is Op.AskMonster -> collectText(op.prompt)
                    is Op.ForEachMember -> collectOps(op.body)
                    is Op.ForEachMonster -> collectOps(op.body)
                    is Op.GrantReward -> {
                        op.reward.messages.cantCarry
                            ?.let(::collectText)
                        op.reward.messages.pickedUp
                            ?.let(::collectText)
                    }
                    is Op.RandomItemOffer -> {
                        collectText(op.offer.offer)
                        collectText(op.offer.choicePrompt)
                        collectText(op.offer.broke)
                        collectText(op.offer.deal)
                        op.offer.declined?.let(::collectText)
                    }
                    else -> Unit
                }
            }
        }
        scenes.forEach { collectOps(it.ops) }
        dungeons.events.forEach { collectOps(it.ops) }
        spells.forEach { collectOps(it.ops) }
        itemUses.forEach { collectOps(it.ops) }
        dialogue.forEach { dialogue -> dialogue.branches.forEach { collectOps(it.ops) } }
        speak.questionActions.forEach { collectOps(it.ops) }
    }.distinctBy { it.slug }

private fun flagStateSlug(state: FlagState): String = "${state.flag.slug}.${state.native}"

private fun scriptRootSlug(root: ScriptRootRef): String = "${root.kind.name.lowercase()}.${root.slug}"

private class MutableStringInterner(
    initial: List<String> = emptyList(),
) {
    private val values = mutableListOf<String>()
    private val lookup = linkedMapOf<String, Int>()

    init {
        initial.forEach(::id)
    }

    fun id(value: String): Int =
        lookup.getOrPut(value) {
            values += value
            values.size
        }

    fun toTable(): CompiledStringTable = CompiledStringTable(values.toList())
}

private class MutableHandleTable(
    private val strings: MutableStringInterner,
) {
    private val entries = mutableListOf<CompiledHandle>()
    private val perKindCounts = mutableMapOf<CompiledHandleKind, Int>()
    private val lookup = linkedMapOf<Pair<CompiledHandleKind, String>, CompiledHandleRef>()

    fun add(
        kind: CompiledHandleKind,
        slug: String,
    ): CompiledHandleRef =
        lookup.getOrPut(kind to slug) {
            val ref = CompiledHandleRef(kind, (perKindCounts[kind] ?: 0) + 1)
            perKindCounts[kind] = ref.id
            entries += CompiledHandle(ref = ref, denseId = entries.size + 1, slugString = strings.id(slug))
            ref
        }

    fun ref(
        kind: CompiledHandleKind,
        slug: String,
    ): CompiledHandleRef = add(kind, slug)

    fun toTable(): CompiledHandleTable = CompiledHandleTable(entries.toList())
}
