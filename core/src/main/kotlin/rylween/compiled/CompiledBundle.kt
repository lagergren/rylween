/**
 * In-memory compiled game bundle model and compiler.
 *
 * `Dsl.kt` records authored content and script ASTs; this file lowers that
 * model into J1 compiled tables, handles, string ids, script roots, operands,
 * and runtime state images. `CompiledLoader.kt` validates and materializes the
 * resulting bundle for hosts and the future J2 VM.
 */
package rylween.compiled

import rylween.dsl.AuthoredScript
import rylween.dsl.BoolAst
import rylween.dsl.BuildingId
import rylween.dsl.DungeonEventDisposition
import rylween.dsl.DungeonEventId
import rylween.dsl.DungeonEventModel
import rylween.dsl.DungeonId
import rylween.dsl.DungeonScenarioModel
import rylween.dsl.EncounterTier
import rylween.dsl.FormulaId
import rylween.dsl.GameMilestoneId
import rylween.dsl.ImageId
import rylween.dsl.InputBindingModel
import rylween.dsl.IntAst
import rylween.dsl.ItemId
import rylween.dsl.ItemPoolId
import rylween.dsl.ItemUseModel
import rylween.dsl.KeyActionId
import rylween.dsl.LValueAst
import rylween.dsl.LocationId
import rylween.dsl.LootTableId
import rylween.dsl.MapLayerId
import rylween.dsl.MemberChoiceFilter
import rylween.dsl.MissionId
import rylween.dsl.MonsterId
import rylween.dsl.NativeStringArg
import rylween.dsl.NpcId
import rylween.dsl.NpcJobId
import rylween.dsl.NpcRole
import rylween.dsl.PartyId
import rylween.dsl.PartyMemberSelection
import rylween.dsl.RewardBundleId
import rylween.dsl.RumorTableId
import rylween.dsl.SceneId
import rylween.dsl.ScreenRegionId
import rylween.dsl.ScriptStatement
import rylween.dsl.ScriptStatementNode
import rylween.dsl.SpellId
import rylween.dsl.SpellModel
import rylween.dsl.StateId
import rylween.dsl.StaticScreenId
import rylween.dsl.StrAst
import rylween.dsl.TerrainId
import rylween.dsl.TerrainTagId
import rylween.dsl.TextBlock
import rylween.dsl.TextPart
import rylween.dsl.TownId
import rylween.dsl.ViewCommand
import rylween.dsl.WorldMapId
import rylween.dsl.impl.BuildingServiceModel
import rylween.dsl.impl.CharacterModel
import rylween.dsl.impl.CharacterPlacementModel
import rylween.dsl.impl.EngineServiceUse
import rylween.dsl.impl.Facing
import rylween.dsl.impl.FlagContentModel
import rylween.dsl.impl.FlagScope
import rylween.dsl.impl.FlagState
import rylween.dsl.impl.FormulaModel
import rylween.dsl.impl.GameModel
import rylween.dsl.impl.GameUniverseModel
import rylween.dsl.impl.Gender
import rylween.dsl.impl.GridPoint
import rylween.dsl.impl.HitPoints
import rylween.dsl.impl.ImageAssetKind
import rylween.dsl.impl.ImageAssetModel
import rylween.dsl.impl.ImagePlacement
import rylween.dsl.impl.ImageTransition
import rylween.dsl.impl.ItemConsumePolicy
import rylween.dsl.impl.ItemKind
import rylween.dsl.impl.ItemRecordModel
import rylween.dsl.impl.ItemStorageKind
import rylween.dsl.impl.ItemUseContext
import rylween.dsl.impl.JournalEntryState
import rylween.dsl.impl.LootTableModel
import rylween.dsl.impl.Mana
import rylween.dsl.impl.MissionModel
import rylween.dsl.impl.MonsterModel
import rylween.dsl.impl.PartyMemberState
import rylween.dsl.impl.PartyResources
import rylween.dsl.impl.PartyState
import rylween.dsl.impl.PcClass
import rylween.dsl.impl.Race
import rylween.dsl.impl.RandomItemOfferModel
import rylween.dsl.impl.RandomNpcPlacementOptionModel
import rylween.dsl.impl.RandomNpcPlacementPolicyModel
import rylween.dsl.impl.RealmState
import rylween.dsl.impl.Resource
import rylween.dsl.impl.RewardApplicationModel
import rylween.dsl.impl.RewardBundleModel
import rylween.dsl.impl.RewardMessageModel
import rylween.dsl.impl.RuntimeMode
import rylween.dsl.impl.RylweenModel
import rylween.dsl.impl.ScreenRegionModel
import rylween.dsl.impl.ScriptRewardModel
import rylween.dsl.impl.ShopBuyModel
import rylween.dsl.impl.ShopCatalogModel
import rylween.dsl.impl.ShopInterludeModel
import rylween.dsl.impl.ShopItemOfferModel
import rylween.dsl.impl.ShopPriceModel
import rylween.dsl.impl.ShopPurchaseModel
import rylween.dsl.impl.ShopQuantityCap
import rylween.dsl.impl.ShopSellHaggleModel
import rylween.dsl.impl.ShopSellModel
import rylween.dsl.impl.ShopServiceModel
import rylween.dsl.impl.ShopZeroQuantityDealPolicy
import rylween.dsl.impl.StateKind
import rylween.dsl.impl.StateModel
import rylween.dsl.impl.StaticScreenModel
import rylween.dsl.impl.StaticScreenRuntimeReport
import rylween.dsl.impl.TemperatureBand
import rylween.dsl.impl.TerrainSemanticsModel
import rylween.dsl.impl.TerrainSettlement
import rylween.dsl.impl.TownKind
import rylween.dsl.impl.TownModel
import rylween.dsl.impl.Trigger
import rylween.dsl.impl.WorldMapModel
import rylween.dsl.impl.WorldPosition
import rylween.dsl.impl.runtimeContent
import rylween.runtime.CastleSessionSnapshot
import rylween.runtime.DungeonSavedStep
import rylween.runtime.RuntimeModeSnapshot
import rylween.runtime.RuntimeSnapshotMetadata
import rylween.runtime.RuntimeSnapshotModel
import rylween.runtime.ScriptContinuationSnapshot
import rylween.runtime.ScriptRootRef
import rylween.runtime.scriptRootCatalog

/**
 * Complete compiled J1 bundle: content tables plus save/start state images.
 *
 * @property content immutable compiled content shared by all runtime states in the artifact.
 * @property states compiled authored fixtures and runtime snapshots that can seed or verify a runtime.
 */
data class CompiledGameBundle(
    val content: CompiledContentBundle,
    val states: List<CompiledStateImage>,
)

/**
 * Dense compiled content tables emitted from [GameModel].
 *
 * This is the main host-facing bundle payload. Handles and strings are interned once, and every other section refers to
 * those tables through compact integer ids. For example, [characters] contains NPC rows whose `nameString`, `job`, roles,
 * and placement references point into [strings] and [handles] rather than carrying repeated slugs.
 *
 * @property universe display/version metadata for compatibility checks and runtime banners.
 * @property limits optional authored limits used by validators and hosts.
 * @property strings interned strings shared by text, handles, names, and scripts.
 * @property handles typed handle table for ids such as items, towns, scenes, NPCs, terrain, and script roots.
 * @property flags authored flag catalog.
 * @property items item catalog rows.
 * @property itemPools resolved item-pool rows.
 * @property formulas compiled integer formulas.
 * @property rewards battle/script reward bundles.
 * @property terrain terrain semantics keyed by compiled terrain handles.
 * @property worldMaps authored world-map layers.
 * @property images visual assets, including ANSI art and later bitmap assets.
 * @property staticScreens static full-screen resources such as title/help/report screens.
 * @property screenRegions named terminal/browser screen regions.
 * @property towns town records and their service lists.
 * @property buildingServices engine-backed town service declarations.
 * @property characters NPC records, including authored or generated placement policy.
 * @property monsters monster catalog rows.
 * @property missions mission records and state transitions.
 * @property spells spell scripts and metadata.
 * @property itemUses usable item scripts and context flags.
 * @property dungeonEvents authored dungeon event scripts.
 * @property dungeonScenarios dungeon layout/scenario definitions.
 * @property scripts compiled script roots for scenes, dialogue, services, spells, item use, and dungeon events.
 */
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

/** Runtime limits used by hosts and artifact validators. */
data class CompiledLimits(
    val maxWorldMaps: Int?,
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
    Music,
    NativeOp,
    Npc,
    NpcJob,
    PaletteColor,
    Party,
    RewardBundle,
    Role,
    Rumor,
    RumorTable,
    Scene,
    ScreenRegion,
    ScriptRoot,
    Sound,
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

/** Compiled authored flag and its valid state handles. */
data class CompiledFlag(
    val id: CompiledHandleRef,
    val scope: FlagScope,
    val states: List<CompiledFlagState>,
)

/** One compiled state belonging to a [CompiledFlag]. */
data class CompiledFlagState(
    val id: CompiledHandleRef,
    val nativeString: Int,
)

/** Compiled item catalog row used by shops, inventory, rewards, combat equipment, and save/load. */
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

/** Named list of item handles used by random rewards and random item offers. */
data class CompiledItemPool(
    val id: CompiledHandleRef,
    val items: List<CompiledHandleRef>,
)

/** Compiled integer formula referenced by rules such as rewards and battle calculations. */
data class CompiledFormula(
    val id: CompiledHandleRef,
    val value: CompiledIntExpr,
)

/** Reward package applied after battles or scripts, including formulas and loot table. */
data class CompiledRewardBundle(
    val id: CompiledHandleRef,
    val experience: CompiledHandleRef,
    val gold: CompiledHandleRef,
    val application: RewardApplicationModel,
    val loot: CompiledLootTable,
)

/** Random loot-table policy for treasure and magic item drops. */
data class CompiledLootTable(
    val id: CompiledHandleRef,
    val dropChancePercent: Int,
    val treasureChancePercent: Int,
    val treasure: CompiledHandleRef,
    val magic: CompiledHandleRef,
)

/**
 * Terrain semantics for one map tile.
 *
 * [mapCode] is the authored character used in map resources. [allowsNpcPlacement] is precomputed so runtime NPC
 * placement does not have to reinterpret settlement/passability rules.
 */
data class CompiledTerrain(
    val id: CompiledHandleRef,
    val mapCode: Char,
    val passable: Boolean,
    val movementCost: Int,
    val allowsNpcPlacement: Boolean,
    val settlement: TerrainSettlement?,
    val tags: List<CompiledHandleRef>,
)

/** Compiled world-map header and ordered map layers. */
data class CompiledWorldMap(
    val id: CompiledHandleRef,
    val layers: List<CompiledWorldMapLayer>,
)

/** One compiled world-map layer whose rows are interned strings. */
data class CompiledWorldMapLayer(
    val id: CompiledHandleRef,
    val rowStrings: List<Int>,
)

/** Visual asset referenced by runtime view commands and service screens. */
data class CompiledImageAsset(
    val id: CompiledHandleRef,
    val kind: ImageAssetKind,
    val resourceString: Int,
    val placement: ImagePlacement,
    val transition: ImageTransition,
)

/** Static full-screen resource such as title, help, score, or hall-of-heroes screens. */
data class CompiledStaticScreen(
    val id: CompiledHandleRef,
    val image: CompiledHandleRef?,
    val resourceString: Int?,
    val commandHelp: Boolean,
    val runtimeReport: StaticScreenRuntimeReport?,
)

/** Named terminal/browser screen rectangle used by host renderers. */
data class CompiledScreenRegion(
    val id: CompiledHandleRef,
    val x: Int,
    val y: Int,
    val width: Int?,
    val height: Int?,
)

/** Compiled town record with map position, tax rule, kind, and service buildings. */
data class CompiledTown(
    val id: CompiledHandleRef,
    val nameString: Int,
    val position: GridPoint,
    val taxPercent: Int,
    val kind: TownKind,
    val services: List<CompiledHandleRef>,
)

/** Town-service entry point that maps a building/action to a script and engine service requirements. */
data class CompiledBuildingService(
    val building: CompiledHandleRef,
    val action: CompiledHandleRef,
    val scene: CompiledHandleRef,
    val opensAt: Int?,
    val closesAt: Int?,
    val nativeServices: List<EngineServiceUse>,
)

/** Compiled NPC record, including display name, job, roles, and placement policy. */
data class CompiledCharacter(
    val id: CompiledHandleRef,
    val nameString: Int,
    val job: CompiledHandleRef,
    val gender: Gender,
    val placement: CompiledCharacterPlacement,
    val roles: List<CompiledHandleRef>,
)

/** Placement strategy for an NPC at world creation or fixture loading. */
sealed interface CompiledCharacterPlacement {
    data class Fixed(
        val position: CompiledWorldPosition,
    ) : CompiledCharacterPlacement

    data class Randomized(
        val policy: CompiledRandomNpcPlacementPolicy,
    ) : CompiledCharacterPlacement

    data object Unplaced : CompiledCharacterPlacement
}

/** Weighted placement policy used when a runtime generates an NPC position. */
data class CompiledRandomNpcPlacementPolicy(
    val options: List<CompiledRandomNpcPlacementOption>,
)

/** One weighted bucket inside [CompiledRandomNpcPlacementPolicy]. */
sealed interface CompiledRandomNpcPlacementOption {
    val chanceShares: Int

    data class TownServices(
        val town: CompiledHandleRef,
        val services: List<CompiledHandleRef>,
        override val chanceShares: Int,
    ) : CompiledRandomNpcPlacementOption

    data class WildernessRect(
        val x: IntRange,
        val y: IntRange,
        val xReplacements: Map<Int, Int>,
        override val chanceShares: Int,
    ) : CompiledRandomNpcPlacementOption
}

/** Compiled monster stats and optional encounter-tier membership. */
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
    val encounterImage: CompiledHandleRef? = null,
)

/** Mission trigger and action binding evaluated by runtime mission logic. */
data class CompiledMission(
    val id: CompiledHandleRef,
    val trigger: CompiledTrigger,
    val requires: CompiledBoolExpr?,
    val action: CompiledHandleRef,
)

/** Spell metadata and script root executed when the spell is cast. */
data class CompiledSpell(
    val id: CompiledHandleRef,
    val slot: Int,
    val baseCost: Int,
    val nameString: Int,
    val scriptRoot: CompiledHandleRef,
)

/** Item-use metadata and script root executed in combat or inventory contexts. */
data class CompiledItemUse(
    val item: CompiledHandleRef,
    val contexts: Set<ItemUseContext>,
    val consume: ItemConsumePolicy,
    val scriptRoot: CompiledHandleRef,
)

/** Dungeon event script plus clearing/random-encounter metadata. */
data class CompiledDungeonEvent(
    val id: CompiledHandleRef,
    val disposition: DungeonEventDisposition,
    val scriptRoot: CompiledHandleRef,
    val randomEncounterTier: CompiledHandleRef?,
    val clearWhenFlag: CompiledHandleRef?,
)

/** Dungeon scenario definition: level range, fixed events, and weighted random event pool. */
data class CompiledDungeonScenario(
    val dungeon: CompiledHandleRef,
    val nameString: Int?,
    val levelFirst: Int?,
    val levelLast: Int?,
    val fixedLevelEvents: Map<Int, CompiledHandleRef>,
    val randomEvents: List<CompiledDungeonRandomEvent>,
)

/** Weighted random event entry inside a dungeon scenario. */
data class CompiledDungeonRandomEvent(
    val event: CompiledHandleRef,
    val weight: Int,
)

/** Runtime trigger expression for wilderness missions and events. */
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

/** All compiled script roots addressable by runtime systems. */
data class CompiledScriptBundle(
    val roots: List<CompiledScriptProgram>,
) {
    fun root(root: ScriptRootRef): CompiledScriptProgram? = roots.firstOrNull { it.root.sameRoot(root) }
}

/** One compiled script root and its instruction stream. */
data class CompiledScriptProgram(
    val root: ScriptRootRef,
    val rootHandle: CompiledHandleRef,
    val instructions: List<CompiledInstruction>,
)

/** VM opcode set emitted from authored script AST operations. */
enum class CompiledOpcode {
    Screen,
    AppendScreen,
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
    TellRumor,
    RunScene,
    Encounter,
    EnterDungeon,
    ConsumeActiveItem,
    ClearInventorySlot,
    ConsumeInventoryPool,
    TeleportParty,
    DiscoverCave,
    GrantReward,
    RandomItemOffer,
    ShopService,
    AchieveMilestone,
}

/** One VM instruction: opcode plus typed operands validated by [CompiledOpcodeContract]. */
data class CompiledInstruction(
    val opcode: CompiledOpcode,
    val operands: List<CompiledOperand> = emptyList(),
)

/** Typed operand payload used by [CompiledInstruction]. */
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

/** One compiled switch arm; `null` [match] is the default branch. */
data class CompiledSwitchCase(
    val match: Int?,
    val body: List<CompiledInstruction>,
)

/** One compiled menu binding and body executed when the binding is selected. */
data class CompiledMenuOption(
    val binding: CompiledInputBinding,
    val body: List<CompiledInstruction>,
)

/** Compiled keyboard/input binding with primary input, aliases, and display label string ids. */
data class CompiledInputBinding(
    val primaryString: Int,
    val aliasStrings: List<Int>,
    val labelString: Int,
)

/** Text-input answer matcher used by ask-text script instructions. */
data class CompiledTextAnswer(
    val acceptedStrings: List<Int>,
    val matchOps: List<CompiledInstruction>,
    val missOps: List<CompiledInstruction>,
    val caseSensitive: Boolean,
    val trim: Boolean,
    val maxLength: Int?,
)

/** Compiled text block, either a handle ref, inline literal lines, structured lines, or deferred-wrap prose. */
sealed interface CompiledTextBlock {
    data class Inline(
        val lineStrings: List<Int>,
    ) : CompiledTextBlock

    data class Structured(
        val lines: List<CompiledTextLine>,
    ) : CompiledTextBlock

    data class Flow(
        val paragraphs: List<CompiledTextLine>,
    ) : CompiledTextBlock
}

/** One structured line made of literal and dynamic text parts. */
data class CompiledTextLine(
    val parts: List<CompiledTextPart>,
)

/** Dynamic part of a structured text line. */
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

    data class RoleJob(
        val role: CompiledHandleRef,
    ) : CompiledTextPart

    data class RoleLocation(
        val role: CompiledHandleRef,
    ) : CompiledTextPart

    data class NpcName(
        val npc: CompiledHandleRef,
    ) : CompiledTextPart

    data class NpcJob(
        val npc: CompiledHandleRef,
    ) : CompiledTextPart

    data class NpcLocation(
        val npc: CompiledHandleRef,
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

/** Runtime-provided text placeholder values that cannot be resolved at compile time. */
enum class CompiledRuntimeTextPart {
    OfferedItemName,
    OfferedItemPrice,
    SelectedItemName,
    InventorySlotName,
    EquipmentKindName,
    ShopPrice,
    ShopCarryCap,
    HuntMemberName,
    HuntMemberSubjectPronoun,
    HuntFoodGained,
    UpkeepMemberName,
    CampGuardName,
    PartyMemberCount,
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
    CreationDexterity,
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

/** Screen/view command emitted from scripts and interpreted by runtime renderers. */
sealed interface CompiledViewCommand {
    data object ClearText : CompiledViewCommand

    data object ClearMapWindow : CompiledViewCommand

    data object RenderMap : CompiledViewCommand

    data object StatusLine : CompiledViewCommand

    data class ShowImage(
        val image: CompiledHandleRef,
    ) : CompiledViewCommand

    data class PlaySound(
        val sound: CompiledHandleRef,
    ) : CompiledViewCommand

    data class PlayMusic(
        val music: CompiledHandleRef,
    ) : CompiledViewCommand
}

/** Compiled integer expression tree evaluated by the script executor. */
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

    data class PartyMemberQuery(
        val selection: PartyMemberSelection,
        val filter: MemberChoiceFilter,
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
}

/** Arithmetic operator for [CompiledIntExpr.Binary]. */
enum class CompiledBinaryOp { Add, Sub, Mul, Div, Mod }

/** Applies a compiled integer binary operator using runtime-compatible divide-by-zero semantics. */
fun Int.applyCompiledBinary(
    op: CompiledBinaryOp,
    right: Int,
): Int =
    when (op) {
        CompiledBinaryOp.Add -> this + right
        CompiledBinaryOp.Sub -> this - right
        CompiledBinaryOp.Mul -> this * right
        CompiledBinaryOp.Div -> this / (right.takeUnless { value -> value == 0 } ?: 1)
        CompiledBinaryOp.Mod -> this % (right.takeUnless { value -> value == 0 } ?: 1)
    }

/** Compiled boolean expression tree evaluated by scripts and runtime conditions. */
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

/** Integer comparison operator for [CompiledBoolExpr.Cmp]. */
enum class CompiledComparisonOp { Eq, Ne, Lt, Lte, Gt, Gte }

/** Applies a compiled integer comparison operator. */
fun Int.compareCompiled(
    op: CompiledComparisonOp,
    right: Int,
): Boolean =
    when (op) {
        CompiledComparisonOp.Eq -> this == right
        CompiledComparisonOp.Ne -> this != right
        CompiledComparisonOp.Lt -> this < right
        CompiledComparisonOp.Lte -> this <= right
        CompiledComparisonOp.Gt -> this > right
        CompiledComparisonOp.Gte -> this >= right
    }

/** Compiled string expression, currently limited to engine-native string functions. */
sealed interface CompiledStringExpr {
    data class NativeString(
        val pathString: Int,
        val args: List<CompiledNativeStringArg>,
    ) : CompiledStringExpr
}

/** Argument supplied to a compiled native string expression. */
sealed interface CompiledNativeStringArg {
    data class Literal(
        val string: Int,
    ) : CompiledNativeStringArg

    data class IntValue(
        val value: CompiledIntExpr,
    ) : CompiledNativeStringArg
}

/** Assignable runtime location for script set/add/sub instructions. */
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

/** Script-authored reward payload applied by `GrantReward`. */
data class CompiledScriptReward(
    val gold: CompiledIntExpr?,
    val items: List<CompiledHandleRef>,
    val application: RewardApplicationModel,
    val messages: CompiledRewardMessages,
)

/** Optional reward messages shown when inventory placement needs player interaction. */
data class CompiledRewardMessages(
    val cantCarry: CompiledTextBlock?,
    val pickedUp: CompiledTextBlock?,
)

/** Wilderness random item offer shown by scripts, with price, choice bindings, reward placement, and delays. */
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

/** Millisecond pauses used after random-offer outcomes to preserve original pacing. */
data class CompiledOfferPauses(
    val broke: Int,
    val full: Int,
    val deal: Int,
    val declined: Int,
)

/** Complete compiled shop/service screen definition for one town building. */
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

/** Top-level shop menu bindings. */
data class CompiledShopMenuKeys(
    val buy: CompiledInputBinding,
    val sell: CompiledInputBinding,
    val leave: CompiledInputBinding,
)

/** Buy-mode configuration for a shop, including catalogs, payment prompts, and inventory placement. */
data class CompiledShopBuy(
    val prompt: CompiledTextBlock,
    val catalogs: List<CompiledShopCatalog>,
    val tooExpensive: CompiledTextBlock,
    val cantCarry: CompiledTextBlock,
    val deal: CompiledTextBlock,
    val bought: CompiledTextBlock?,
    val unitPrice: CompiledTextBlock?,
    val singlePrice: CompiledTextBlock?,
    val payPrompt: CompiledTextBlock,
    val paymentKeys: CompiledShopPurchaseKeys,
    val inventoryApplication: RewardApplicationModel,
)

/** The binding that confirms a purchase; RETURN declines it. */
data class CompiledShopPurchaseKeys(
    val pay: CompiledInputBinding,
)

/** One selectable buy catalog, such as weapons, armor, or general goods. */
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

/** Optional previous/next bindings consumed by runtimes. */
data class CompiledShopCatalogPageNavigation(
    val previous: CompiledInputBinding? = null,
    val next: CompiledInputBinding? = null,
)

/** One purchasable row in a shop catalog page. */
data class CompiledShopItemOffer(
    val binding: CompiledInputBinding,
    val item: CompiledHandleRef,
    val labelString: Int?,
    val description: CompiledTextBlock?,
    val price: CompiledShopPrice,
    val purchase: CompiledShopPurchase,
)

/** Price policy for buying or selling an item/resource. */
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

/** Purchase effect applied after payment succeeds. */
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

/** Sell-mode configuration for shops that buy party inventory items. */
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
    val haggle: ShopSellHaggleModel?,
    val saleKeys: CompiledShopSaleKeys,
)

/** Bindings used when accepting, declining, or haggling over a sale offer. */
data class CompiledShopSaleKeys(
    val accept: CompiledInputBinding,
    val decline: CompiledInputBinding,
    val haggle: CompiledInputBinding,
)

/**
 * Optional scripted interruption shown during a shop visit.
 *
 * Rylween uses this for service flavor such as gossip or special shopkeeper offers before returning to the normal shop
 * flow. [chancePercent] controls how often it appears, [accepted] contains the lines shown after accepting, and
 * [runsRumorAfterAccept] lets the runtime immediately follow the accepted text with a rumor-table draw.
 */
data class CompiledShopInterlude(
    val chancePercent: Int,
    val offer: CompiledTextBlock,
    val accept: CompiledInputBinding,
    val decline: CompiledInputBinding,
    val accepted: List<CompiledTextBlock>,
    val declined: CompiledTextBlock,
    val runsRumorAfterAccept: Boolean,
    val forceFirstUntil: CompiledHandleRef? = null,
)

/** Compiled authored fixture or runtime snapshot image. */
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

/** Realm-wide state shared by all parties in one game world. */
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
    val partyMemberMax: Int,
    val lastTerrain: CompiledHandleRef?,
)

/** Fixed authored placement for a named world location. */
data class CompiledLocationPlacement(
    val location: CompiledHandleRef,
    val point: GridPoint,
)

/** Saved or fixture placement for one NPC. */
data class CompiledNpcPlacement(
    val npc: CompiledHandleRef,
    val position: CompiledWorldPosition,
)

/** Compiled world position, either wilderness coordinates or a town building/service. */
sealed interface CompiledWorldPosition {
    data class Wilderness(
        val point: GridPoint,
    ) : CompiledWorldPosition

    data class Town(
        val town: CompiledHandleRef,
        val building: CompiledHandleRef,
    ) : CompiledWorldPosition
}

/** Complete saved/fixture party state inside a compiled state image. */
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

/** Mutable counters persisted for a party. */
data class CompiledPartyCounters(
    val memberCount: Int?,
    val kills: Int,
    val waitingMail: Boolean,
    val attacked: Boolean,
    val attackedToday: List<CompiledPartyAttackRecord>,
)

/** Per-party attack-today record used by player-vs-player attack limits. */
data class CompiledPartyAttackRecord(
    val party: CompiledHandleRef,
    val attacked: Boolean,
)

/** One saved inventory slot; empty slots have `null` [item]. */
data class CompiledInventorySlot(
    val slot: Int,
    val item: CompiledHandleRef?,
)

/** Per-party NPC visit bit. */
data class CompiledNpcVisit(
    val npc: CompiledHandleRef,
    val visited: Boolean,
)

/** Journal entry stored in a party state image. */
sealed interface CompiledJournalEntry {
    data class Text(
        val text: CompiledHandleRef,
    ) : CompiledJournalEntry

    data class Line(
        val textString: Int,
    ) : CompiledJournalEntry
}

/** Saved/fixture party-member stats, equipment, experience, and death state. */
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
    val dexterity: Int,
    val equipment: CompiledEquipment,
    val experience: Int,
    val dead: Boolean,
)

/** Equipped weapon and armor item refs. */
data class CompiledEquipment(
    val weapon: CompiledHandleRef,
    val armor: CompiledHandleRef,
)

/** Extra mode-specific runtime state needed when saving inside a sub-runtime. */
sealed interface CompiledRuntimeModeImage {
    data class Castle(
        val session: CastleSessionSnapshot,
    ) : CompiledRuntimeModeImage

    data class Dungeon(
        val session: CompiledDungeonSessionImage,
        val postDungeon: CompiledMissionPostDungeonImage?,
    ) : CompiledRuntimeModeImage
}

/** Saved dungeon session state for restoring inside a dungeon. */
data class CompiledDungeonSessionImage(
    val dungeon: CompiledHandleRef,
    val maxDepth: Int,
    val depth: Int,
    val levelEvents: Map<Int, CompiledHandleRef>,
    val step: DungeonSavedStep,
)

/** Continuation run after a dungeon mission completes and returns to script flow. */
data class CompiledMissionPostDungeonImage(
    val mission: CompiledHandleRef,
    val scene: CompiledHandleRef,
    val continuation: ScriptContinuationSnapshot,
)

/**
 * Lowers an authored [RylweenModel] into the compiled J1 bundle consumed by runtime hosts and artifact writers.
 *
 * @return compiled content tables and compiled start/save state images.
 */
fun RylweenModel.toCompiledGameBundle(): CompiledGameBundle = CompiledBundleCompiler(content, states).compile()

private data class CompiledContentSections(
    val universe: CompiledUniverse,
    val limits: CompiledLimits,
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
)

private class CompiledBundleCompiler(
    private val game: GameModel,
    private val states: List<StateModel>,
) {
    private val strings = MutableStringInterner()
    private val handles = MutableHandleTable(strings)

    /**
     * Compiles the complete authored game into a runtime-ready bundle.
     *
     * The compile order is intentional:
     * 1. Declare content and state handles first so every later reference can resolve to a stable [CompiledHandleRef].
     * 2. Compile scripts before freezing the string table, because script operands intern text, variable names, and handles.
     * 3. Compile typed content sections from [game], again interning strings as each row is lowered.
     * 4. Compile authored fixtures/runtime snapshots from [states].
     * 5. Freeze [strings] and [handles] into immutable tables and return the final [CompiledGameBundle].
     *
     * @return immutable compiled content plus compiled state images.
     */
    fun compile(): CompiledGameBundle {
        declareHandles()
        val scripts = compileScriptBundle()
        val sections = compileContentSections()
        val stateImages = compileList(states) { state -> compileState(RuntimeSnapshotModel(state)) }
        val content =
            CompiledContentBundle(
                universe = sections.universe,
                limits = sections.limits,
                strings = strings.toTable(),
                handles = handles.toTable(),
                flags = sections.flags,
                items = sections.items,
                itemPools = sections.itemPools,
                formulas = sections.formulas,
                rewards = sections.rewards,
                terrain = sections.terrain,
                worldMaps = sections.worldMaps,
                images = sections.images,
                staticScreens = sections.staticScreens,
                screenRegions = sections.screenRegions,
                towns = sections.towns,
                buildingServices = sections.buildingServices,
                characters = sections.characters,
                monsters = sections.monsters,
                missions = sections.missions,
                spells = sections.spells,
                itemUses = sections.itemUses,
                dungeonEvents = sections.dungeonEvents,
                dungeonScenarios = sections.dungeonScenarios,
                scripts = scripts,
            )
        return CompiledGameBundle(
            content = content,
            states = stateImages,
        )
    }

    private fun declareHandles() {
        declareContentHandles()
        declareStateHandles()
    }

    private fun compileScriptBundle(): CompiledScriptBundle = compileScripts()

    private fun compileContentSections(): CompiledContentSections =
        CompiledContentSections(
            universe = compileUniverse(game.metadata.universe),
            limits = compileLimits(),
            flags = compileList(game.authoredFlags, ::compileFlag),
            items = compileList(game.itemCatalog, ::compileItem),
            itemPools = compileList(game.itemPools, ::compileItemPool),
            formulas = compileList(game.formulas, ::compileFormula),
            rewards = compileList(game.rewards, ::compileRewardBundle),
            terrain = compileList(game.terrain.terrain, ::compileTerrain),
            worldMaps = compileList(game.worldMaps, ::compileWorldMap),
            images = compileList(game.visuals.images, ::compileImage),
            staticScreens = compileList(game.visuals.staticScreens, ::compileStaticScreen),
            screenRegions = compileList(game.layouts.regions, ::compileRegion),
            towns = compileList(game.towns, ::compileTown),
            buildingServices =
                compileList(
                    game.townSystem?.buildingServices.orEmpty(),
                    ::compileBuildingService,
                ),
            characters = compileList(game.characters, ::compileCharacter),
            monsters = compileList(game.monsters, ::compileMonster),
            missions = compileList(game.missions, ::compileMission),
            spells = compileList(game.spells, ::compileSpell),
            itemUses = compileList(game.itemUses, ::compileItemUse),
            dungeonEvents = compileList(game.dungeons.events, ::compileDungeonEvent),
            dungeonScenarios = compileList(game.dungeons.scenarios, ::compileDungeonScenario),
        )

    private fun compileLimits(): CompiledLimits =
        CompiledLimits(
            maxWorldMaps = game.limits.maxWorldMaps,
        )

    private fun <Source, Target> compileList(
        source: List<Source>,
        compile: (Source) -> Target,
    ): List<Target> = source.map(compile)

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
                is CharacterPlacementModel.FixedNearTown -> Unit
                is CharacterPlacementModel.Randomized -> declareRandomNpcPlacementPolicy(placement.policy)
                CharacterPlacementModel.Unplaced -> Unit
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
        game.speak.textTables.rumorTables.forEach { table ->
            handles.add(CompiledHandleKind.RumorTable, table.id.slug)
            table.entries.mapNotNull { rumor -> rumor.id }.forEach { rumor ->
                handles.add(CompiledHandleKind.Rumor, rumor.slug)
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

    private fun declareRandomNpcPlacementPolicy(policy: RandomNpcPlacementPolicyModel) {
        policy.options.forEach { option ->
            when (option) {
                is RandomNpcPlacementOptionModel.TownServices -> {
                    handles.add(CompiledHandleKind.Town, option.town.slug)
                    option.services.forEach { service -> handles.add(CompiledHandleKind.Building, service.slug) }
                }
                is RandomNpcPlacementOptionModel.WildernessRect -> Unit
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

        fun MutableList<CompiledScriptProgram>.addProgram(
            root: ScriptRootRef,
            script: AuthoredScript,
        ) {
            add(CompiledScriptProgram(root, rootHandle(root), compileScript(script)))
        }

        return CompiledScriptBundle(
            buildList {
                game.scenes.forEach { scene ->
                    addProgram(catalog.rootFor(ScriptRootRef.scene(scene.id)), scene.script)
                }
                game.dungeons.events.forEach { event ->
                    addProgram(catalog.rootFor(ScriptRootRef.dungeonEvent(event.id)), event.script)
                }
                game.spells.forEach { spell ->
                    addProgram(catalog.rootFor(ScriptRootRef.spell(spell.id)), spell.script)
                }
                game.itemUses.forEach { use ->
                    addProgram(catalog.rootFor(ScriptRootRef.itemUse(use.id)), use.script)
                }
                game.dialogue.forEach { dialogue ->
                    dialogue.branches.forEach { branch ->
                        addProgram(catalog.rootFor(ScriptRootRef.dialogueBranch(dialogue.npc, branch)), branch.script)
                    }
                }
                game.speak.questionActions.forEach { action ->
                    addProgram(catalog.rootFor(ScriptRootRef.questionAction(action)), action.script)
                }
            },
        )
    }

    private fun compileScript(script: AuthoredScript): List<CompiledInstruction> = compileStatements(script.block.statements)

    private fun compileBranch(statements: List<ScriptStatementNode>): List<CompiledInstruction> = compileStatements(statements)

    private fun compileStatements(statements: List<ScriptStatement>): List<CompiledInstruction> = statements.map(::compileStatement)

    private fun compileStatement(statement: ScriptStatement): CompiledInstruction =
        when (val op = statement.asStatementNode()) {
            is ScriptStatementNode.Screen ->
                instruction(
                    if (op.append) CompiledOpcode.AppendScreen else CompiledOpcode.Screen,
                    CompiledOperand.TextBlockValue(text(op.text)),
                )
            is ScriptStatementNode.WaitInput ->
                instruction(
                    CompiledOpcode.WaitInput,
                    op.reason?.let { CompiledOperand.StringId(strings.id(it)) },
                )
            is ScriptStatementNode.Pause -> instruction(CompiledOpcode.Pause, CompiledOperand.Integer(op.millis))
            is ScriptStatementNode.View -> instruction(CompiledOpcode.View, CompiledOperand.ViewCommandValue(viewCommand(op.command)))
            is ScriptStatementNode.LetInt ->
                instruction(
                    CompiledOpcode.LetInt,
                    CompiledOperand.StringId(strings.id(op.name)),
                    CompiledOperand.IntExprValue(intExpr(op.value)),
                )

            is ScriptStatementNode.BindText ->
                instruction(
                    CompiledOpcode.BindText,
                    CompiledOperand.StringId(strings.id(op.name)),
                    CompiledOperand.StringExprValue(stringExpr(op.value)),
                )

            is ScriptStatementNode.Set ->
                instruction(
                    CompiledOpcode.Set,
                    CompiledOperand.LValueValue(lvalue(op.target)),
                    CompiledOperand.IntExprValue(intExpr(op.value)),
                )

            is ScriptStatementNode.Add ->
                instruction(
                    CompiledOpcode.Add,
                    CompiledOperand.LValueValue(lvalue(op.target)),
                    CompiledOperand.IntExprValue(intExpr(op.value)),
                )

            is ScriptStatementNode.Sub ->
                instruction(
                    CompiledOpcode.Sub,
                    CompiledOperand.LValueValue(lvalue(op.target)),
                    CompiledOperand.IntExprValue(intExpr(op.value)),
                )

            is ScriptStatementNode.AddClamped ->
                instruction(
                    CompiledOpcode.AddClamped,
                    CompiledOperand.LValueValue(lvalue(op.target)),
                    CompiledOperand.IntExprValue(intExpr(op.value)),
                    CompiledOperand.IntExprValue(intExpr(op.max)),
                )

            is ScriptStatementNode.SetFlag ->
                instruction(
                    CompiledOpcode.SetFlag,
                    CompiledOperand.Handle(flagRef(op.slug)),
                    CompiledOperand.Handle(flagStateRef(op.slug, op.nativeValue)),
                )

            is ScriptStatementNode.AppendJournal -> instruction(CompiledOpcode.AppendJournal, CompiledOperand.TextBlockValue(text(op.text)))
            is ScriptStatementNode.If ->
                instruction(
                    CompiledOpcode.If,
                    CompiledOperand.BoolExprValue(boolExpr(op.cond)),
                    CompiledOperand.Instructions(compileBranch(op.thenStatements)),
                    CompiledOperand.Instructions(compileBranch(op.elseStatements)),
                )

            is ScriptStatementNode.Loop -> instruction(CompiledOpcode.Loop, CompiledOperand.Instructions(compileBranch(op.body)))
            is ScriptStatementNode.Break ->
                signalInstruction(CompiledOpcode.Break, op.reason, op.signal.name)

            is ScriptStatementNode.Stop ->
                signalInstruction(CompiledOpcode.Stop, op.reason, op.signal.name)

            is ScriptStatementNode.Switch ->
                instruction(
                    CompiledOpcode.Switch,
                    CompiledOperand.IntExprValue(intExpr(op.expr)),
                    CompiledOperand.SwitchCases(op.cases.map { case -> CompiledSwitchCase(case.match, compileBranch(case.body)) }),
                )

            is ScriptStatementNode.Menu ->
                instruction(
                    CompiledOpcode.Menu,
                    CompiledOperand.TextBlockValue(text(op.prompt)),
                    CompiledOperand.MenuOptions(
                        op.options.map { option ->
                            CompiledMenuOption(binding(option.binding), compileBranch(option.body))
                        },
                    ),
                    CompiledOperand.Instructions(compileBranch(op.defaultStatements)),
                )

            is ScriptStatementNode.AskKey ->
                instruction(
                    CompiledOpcode.AskKey,
                    CompiledOperand.TextBlockValue(text(op.prompt)),
                    CompiledOperand.StringId(strings.id(op.varName)),
                    CompiledOperand.InputBindings(op.bindings.map(::binding)),
                )

            is ScriptStatementNode.AskMember ->
                instruction(
                    CompiledOpcode.AskMember,
                    CompiledOperand.TextBlockValue(text(op.prompt)),
                    CompiledOperand.StringId(strings.id(op.varName)),
                    CompiledOperand.EnumName(op.filter.name),
                    CompiledOperand.EnumName(op.accepting.name),
                )

            is ScriptStatementNode.AskInventorySlot ->
                instruction(
                    CompiledOpcode.AskInventorySlot,
                    CompiledOperand.TextBlockValue(text(op.prompt)),
                    CompiledOperand.StringId(strings.id(op.varName)),
                    CompiledOperand.Character(op.firstKey),
                    CompiledOperand.Integer(op.slots.first),
                    CompiledOperand.Integer(op.slots.last),
                    CompiledOperand.EnumName(op.filter.name),
                )

            is ScriptStatementNode.AskText ->
                instruction(
                    CompiledOpcode.AskText,
                    CompiledOperand.TextBlockValue(text(op.prompt)),
                    CompiledOperand.TextAnswer(
                        CompiledTextAnswer(
                            acceptedStrings = op.accepted.map(strings::id),
                            matchOps = compileBranch(op.matchStatements),
                            missOps = compileBranch(op.missStatements),
                            caseSensitive = op.caseSensitive,
                            trim = op.trim,
                            maxLength = op.maxLength,
                        ),
                    ),
                )

            is ScriptStatementNode.AskMonster ->
                instruction(
                    CompiledOpcode.AskMonster,
                    CompiledOperand.TextBlockValue(text(op.prompt)),
                    CompiledOperand.StringId(strings.id(op.varName)),
                )

            is ScriptStatementNode.ForEachMember ->
                forEachInstruction(CompiledOpcode.ForEachMember, op.varName, op.includeDead, op.body)

            is ScriptStatementNode.ForEachMonster ->
                forEachInstruction(CompiledOpcode.ForEachMonster, op.varName, op.includeDead, op.body)

            is ScriptStatementNode.Speak -> instruction(CompiledOpcode.Speak, CompiledOperand.Handle(buildingRef(op.building)))
            is ScriptStatementNode.TellRumor -> instruction(CompiledOpcode.TellRumor, CompiledOperand.Handle(rumorTableRef(op.table)))
            is ScriptStatementNode.RunScene ->
                instruction(
                    CompiledOpcode.RunScene,
                    CompiledOperand.Handle(sceneRef(op.scene)),
                    CompiledOperand.StringIdList(op.bindings.flatMap { (key, value) -> listOf(strings.id(key), strings.id(value)) }),
                )

            is ScriptStatementNode.Encounter ->
                instruction(
                    CompiledOpcode.Encounter,
                    CompiledOperand.Handle(monsterRef(op.foe)),
                    CompiledOperand.BooleanValue(op.mandatory),
                    op.tier?.let { CompiledOperand.Handle(encounterTierRef(it)) },
                )

            is ScriptStatementNode.EnterDungeon ->
                instruction(
                    CompiledOpcode.EnterDungeon,
                    CompiledOperand.Handle(dungeonRef(op.dungeon)),
                    CompiledOperand.Integer(op.levels),
                )

            ScriptStatementNode.ConsumeActiveItem -> instruction(CompiledOpcode.ConsumeActiveItem)
            is ScriptStatementNode.ClearInventorySlot ->
                instruction(
                    CompiledOpcode.ClearInventorySlot,
                    CompiledOperand.IntExprValue(intExpr(op.slot)),
                )

            is ScriptStatementNode.ConsumeInventoryPool ->
                instruction(
                    CompiledOpcode.ConsumeInventoryPool,
                    CompiledOperand.Handle(itemPoolRef(op.pool)),
                    CompiledOperand.IntExprValue(intExpr(op.quantity)),
                )

            is ScriptStatementNode.TeleportParty ->
                instruction(
                    CompiledOpcode.TeleportParty,
                    CompiledOperand.IntExprValue(intExpr(op.directionKey)),
                    CompiledOperand.IntExprValue(intExpr(op.distance)),
                )

            is ScriptStatementNode.DiscoverCave ->
                instruction(
                    CompiledOpcode.DiscoverCave,
                    CompiledOperand.Handle(dungeonRef(op.dungeon)),
                    CompiledOperand.IntExprValue(intExpr(op.levels)),
                    CompiledOperand.IntExprValue(intExpr(op.moveCost)),
                )

            is ScriptStatementNode.GrantReward -> instruction(CompiledOpcode.GrantReward, CompiledOperand.Reward(reward(op.reward)))
            is ScriptStatementNode.RandomItemOffer ->
                instruction(
                    CompiledOpcode.RandomItemOffer,
                    CompiledOperand.RandomItemOffer(randomItemOffer(op.offer)),
                )

            is ScriptStatementNode.ShopService -> instruction(CompiledOpcode.ShopService, CompiledOperand.ShopService(shopService(op.shop)))
            is ScriptStatementNode.AchieveMilestone ->
                instruction(
                    CompiledOpcode.AchieveMilestone,
                    CompiledOperand.Handle(milestoneRef(op.milestone)),
                )
        }

    private fun instruction(
        opcode: CompiledOpcode,
        vararg operands: CompiledOperand?,
    ): CompiledInstruction = CompiledInstruction(opcode, operands.filterNotNull())

    private fun signalInstruction(
        opcode: CompiledOpcode,
        reason: String?,
        signal: String,
    ): CompiledInstruction =
        instruction(
            opcode,
            reason?.let { CompiledOperand.StringId(strings.id(it)) },
            CompiledOperand.EnumName(signal),
        )

    private fun forEachInstruction(
        opcode: CompiledOpcode,
        varName: String,
        includeDead: Boolean,
        body: List<ScriptStatementNode>,
    ): CompiledInstruction =
        instruction(
            opcode,
            CompiledOperand.StringId(strings.id(varName)),
            CompiledOperand.BooleanValue(includeDead),
            CompiledOperand.Instructions(compileBranch(body)),
        )

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
            is IntAst.PartyMemberQuery -> CompiledIntExpr.PartyMemberQuery(expr.selection, expr.filter)
            is IntAst.RealmScalar -> CompiledIntExpr.RealmScalar(strings.id(expr.name))
            is IntAst.PartyScalar -> CompiledIntExpr.PartyScalar(strings.id(expr.name))
            is IntAst.InventoryPoolCount -> CompiledIntExpr.InventoryPoolCount(itemPoolRef(expr.pool))
            is IntAst.InventorySlotCost -> CompiledIntExpr.InventorySlotCost(intExpr(expr.slot))
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
            is TextBlock.Inline -> CompiledTextBlock.Inline(block.lines.map(strings::id))
            is TextBlock.Structured ->
                CompiledTextBlock.Structured(
                    block.lines.map { line -> CompiledTextLine(line.parts.map(::textPart)) },
                )

            is TextBlock.Flow ->
                CompiledTextBlock.Flow(
                    block.paragraphs.map { line -> CompiledTextLine(line.parts.map(::textPart)) },
                )
        }

    private fun textPart(part: TextPart): CompiledTextPart =
        when (part) {
            is TextPart.Literal -> CompiledTextPart.Literal(strings.id(part.text))
            is TextPart.IntValue -> CompiledTextPart.IntValue(intExpr(part.value))
            is TextPart.StringValue -> CompiledTextPart.StringValue(stringExpr(part.value))
            is TextPart.Pause -> error("{pause} markers only work in writeText and showTextAndWait")
            is TextPart.LineColor -> CompiledTextPart.Literal(strings.id(part.color.escape))
            is TextPart.RoleName -> CompiledTextPart.RoleName(roleRef(part.role))
            is TextPart.RoleJob -> CompiledTextPart.RoleJob(roleRef(part.role))
            is TextPart.RoleLocation -> CompiledTextPart.RoleLocation(roleRef(part.role))
            is TextPart.NpcName -> CompiledTextPart.NpcName(npcRef(part.npc))
            is TextPart.NpcJob -> CompiledTextPart.NpcJob(npcRef(part.npc))
            is TextPart.NpcLocation -> CompiledTextPart.NpcLocation(npcRef(part.npc))
            is TextPart.TownName -> CompiledTextPart.TownName(townRef(part.town))
            is TextPart.FuzzyTownLocation -> CompiledTextPart.FuzzyTownLocation(townRef(part.town), part.radius)
            is TextPart.LocationCoordinate -> CompiledTextPart.LocationCoordinate(locationRef(part.location))
            TextPart.OfferedItemName -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.OfferedItemName)
            TextPart.OfferedItemPrice -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.OfferedItemPrice)
            TextPart.SelectedItemName -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.SelectedItemName)
            TextPart.InventorySlotName -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.InventorySlotName)
            TextPart.EquipmentKindName -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.EquipmentKindName)
            TextPart.ShopPrice -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.ShopPrice)
            TextPart.ShopCarryCap -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.ShopCarryCap)
            TextPart.HuntMemberName -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.HuntMemberName)
            TextPart.HuntMemberSubjectPronoun -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.HuntMemberSubjectPronoun)
            TextPart.HuntFoodGained -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.HuntFoodGained)
            TextPart.UpkeepMemberName -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.UpkeepMemberName)
            TextPart.CampGuardName -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.CampGuardName)
            TextPart.PartyMemberCount -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.PartyMemberCount)
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
            TextPart.CreationDexterity -> CompiledTextPart.Runtime(CompiledRuntimeTextPart.CreationDexterity)
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
            is ViewCommand.PlaySound -> CompiledViewCommand.PlaySound(handles.ref(CompiledHandleKind.Sound, command.sound.slug))
            is ViewCommand.PlayMusic -> CompiledViewCommand.PlayMusic(handles.ref(CompiledHandleKind.Music, command.music.slug))
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
            bought = buy.bought?.let(::text),
            unitPrice = buy.unitPrice?.let(::text),
            singlePrice = buy.singlePrice?.let(::text),
            payPrompt = text(buy.payPrompt),
            paymentKeys = CompiledShopPurchaseKeys(binding(buy.paymentKeys.pay)),
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
            haggle = sell.haggle,
            saleKeys =
                CompiledShopSaleKeys(
                    binding(sell.saleKeys.accept),
                    binding(sell.saleKeys.decline),
                    binding(sell.saleKeys.haggle),
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
            runsRumorAfterAccept = interlude.runsRumorAfterAccept,
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
            transition = image.transition,
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
                    is CharacterPlacementModel.FixedNearTown -> CompiledCharacterPlacement.Fixed(fixedNearTownPosition(placement))
                    is CharacterPlacementModel.Randomized ->
                        CompiledCharacterPlacement.Randomized(randomNpcPlacementPolicy(placement.policy))
                    CharacterPlacementModel.Unplaced -> CompiledCharacterPlacement.Unplaced
                },
            roles = character.roles.map(::roleRef),
        )

    private fun randomNpcPlacementPolicy(policy: RandomNpcPlacementPolicyModel): CompiledRandomNpcPlacementPolicy =
        CompiledRandomNpcPlacementPolicy(policy.options.map(::randomNpcPlacementOption))

    private fun randomNpcPlacementOption(option: RandomNpcPlacementOptionModel): CompiledRandomNpcPlacementOption =
        when (option) {
            is RandomNpcPlacementOptionModel.TownServices ->
                CompiledRandomNpcPlacementOption.TownServices(
                    town = townRef(option.town),
                    services = option.services.map(::buildingRef),
                    chanceShares = option.chanceShares,
                )
            is RandomNpcPlacementOptionModel.WildernessRect ->
                CompiledRandomNpcPlacementOption.WildernessRect(
                    x = option.x,
                    y = option.y,
                    xReplacements = option.xReplacements,
                    chanceShares = option.chanceShares,
                )
        }

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
            encounterImage = monster.encounterImage?.let(::imageRef),
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
            dexterity = member.dexterity,
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

    private fun fixedNearTownPosition(placement: CharacterPlacementModel.FixedNearTown): CompiledWorldPosition {
        val town = game.towns.first { town -> town.id == placement.town }
        val point =
            GridPoint(
                x = town.position.x + placement.offset.x,
                y = town.position.y + placement.offset.y,
            )
        return CompiledWorldPosition.Wilderness(point)
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

    private fun rumorTableRef(id: RumorTableId): CompiledHandleRef = handles.ref(CompiledHandleKind.RumorTable, id.slug)

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
                is TextBlock.Inline -> Unit

                is TextBlock.Structured ->
                    block.lines.forEach { line ->
                        line.parts.forEach { part ->
                            if (part is TextPart.LocationCoordinate) add(part.location)
                        }
                    }

                is TextBlock.Flow ->
                    block.paragraphs.forEach { line ->
                        line.parts.forEach { part ->
                            if (part is TextPart.LocationCoordinate) add(part.location)
                        }
                    }
            }
        }

        fun collectStatements(statements: List<ScriptStatement>) {
            statements.forEach { statement ->
                when (val op = statement.asStatementNode()) {
                    is ScriptStatementNode.Screen -> collectText(op.text)
                    is ScriptStatementNode.AppendJournal -> collectText(op.text)
                    is ScriptStatementNode.If -> {
                        collectStatements(op.thenStatements)
                        collectStatements(op.elseStatements)
                    }

                    is ScriptStatementNode.Loop -> collectStatements(op.body)
                    is ScriptStatementNode.Switch -> op.cases.forEach { collectStatements(it.body) }
                    is ScriptStatementNode.Menu -> {
                        collectText(op.prompt)
                        op.options.forEach { collectStatements(it.body) }
                        collectStatements(op.defaultStatements)
                    }

                    is ScriptStatementNode.AskKey -> collectText(op.prompt)
                    is ScriptStatementNode.AskMember -> collectText(op.prompt)
                    is ScriptStatementNode.AskInventorySlot -> collectText(op.prompt)
                    is ScriptStatementNode.AskText -> {
                        collectText(op.prompt)
                        collectStatements(op.matchStatements)
                        collectStatements(op.missStatements)
                    }

                    is ScriptStatementNode.AskMonster -> collectText(op.prompt)
                    is ScriptStatementNode.ForEachMember -> collectStatements(op.body)
                    is ScriptStatementNode.ForEachMonster -> collectStatements(op.body)
                    is ScriptStatementNode.GrantReward -> {
                        op.reward.messages.cantCarry
                            ?.let(::collectText)
                        op.reward.messages.pickedUp
                            ?.let(::collectText)
                    }

                    is ScriptStatementNode.RandomItemOffer -> {
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

        fun collectScript(script: AuthoredScript) {
            collectStatements(script.block.statements)
        }

        scenes.forEach { collectScript(it.script) }
        dungeons.events.forEach { collectScript(it.script) }
        spells.forEach { collectScript(it.script) }
        itemUses.forEach { collectScript(it.script) }
        dialogue.forEach { dialogue -> dialogue.branches.forEach { collectScript(it.script) } }
        speak.questionActions.forEach { collectScript(it.script) }
    }.distinctBy { it.slug }

private fun ScriptStatement.asStatementNode(): ScriptStatementNode =
    this as? ScriptStatementNode ?: error("unsupported script statement implementation: ${this::class.simpleName}")

private fun flagStateSlug(state: FlagState): String = "${state.flag.slug}.${state.native}"

private fun scriptRootSlug(root: ScriptRootRef): String = "${root.kind.name.lowercase()}.${root.slug}"

/**
 * Mutable string-id allocator used only while compiling a bundle.
 *
 * Compiled rows store one-based string ids instead of repeated Kotlin strings. The compiler discovers strings while it
 * walks metadata, content rows, script operands, and state snapshots, so allocation has to be append-only during the
 * compile pass. [toTable] freezes the accumulated values into the immutable [CompiledStringTable] stored in the bundle.
 */
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

/**
 * Mutable typed-handle allocator used only while compiling a bundle.
 *
 * Handles are declared before most content is lowered, but late script/state traversal can still introduce references
 * that must resolve to the same compact ids. The mutable table interns `(kind, slug)` pairs, assigns ids per
 * [CompiledHandleKind], and keeps a dense global order for artifact serialization. [toTable] freezes the final rows into
 * [CompiledHandleTable], which is immutable and safe for runtime/loading code.
 */
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
