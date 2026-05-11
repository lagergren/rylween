/**
 * Shared content records for resources, items, terrain, maps, visuals, and layout.
 *
 * These are immutable DSL model types consumed by validation, compiled-bundle
 * emission, and the runtime-system export. They are not the game content itself:
 * authored item rows, terrain catalogs, images, map rows, camp text, and runtime
 * balance values are supplied from `RylweenGame.dsl.kts` or another game source
 * and materialized into these reusable contracts.
 */
package rylween.dsl

/** Item categories understood by inventory, equipment, shops, rewards, and saves. */
enum class ItemKind { Empty, Weapon, Armor, Gear, Potion, Treasure, Special, MagicItem }

/** Equipment mutation requested by reusable runtime sessions. */
enum class EquipmentCommand { ReadyWeapon, ReadyArmor }

/** Runtime storage policy for an item record. */
enum class ItemStorageKind { EmptySentinel, SlottedInventory, SpecialMilestone }

/** Party resource tracked by wilderness and shop systems. */
enum class Resource { Food, Torches }

/** Temperature damage band used by camp/rest rules. */
enum class TemperatureBand { Low, VeryLow }

/** Authored item metadata consumed by inventory, shops, combat, camp, and rewards. */
data class ItemRecordModel(
    val id: ItemId,
    val kind: ItemKind,
    val displayName: String,
    val cost: Int,
    val storage: ItemStorageKind,
    val weaponDamage: Int? = null,
    val armorClass: Int? = null,
    val equippableBy: Set<PcClass> = emptySet(),
    val campImage: ImageId? = null,
    val campProtection: Set<TemperatureBand> = emptySet(),
    val resourceCapacityBonus: Map<Resource, Int> = emptyMap(),
)

/** Realm-level runtime systems shared by wilderness, camp, and hunt sessions. */
data class RealmContentModel(
    val baseResourceCapacity: Map<Resource, Int> = emptyMap(),
    val upkeep: WildernessUpkeepRules = WildernessUpkeepRules(),
    val camp: CampRulesModel = CampRulesModel(),
    val hunt: HuntRulesModel? = null,
)

/** Match mode for a wilderness fatigue rule threshold. */
enum class WildernessFatigueMatch { Equal, LessThan }

/** One authored fatigue threshold and message/drain effect. */
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
    val notTired: TextBlock = inlineText(),
    val notEnoughFood: TextBlock = inlineText(),
    val guardPrompt: TextBlock = inlineText(),
    val guardMemberPrompt: TextBlock = inlineText(),
    val guardMemberDead: TextBlock = inlineText(),
    val standsGuard: TextBlock = inlineText(),
    val sleeping: TextBlock = inlineText(),
    val rested: TextBlock = inlineText(),
    val ambush: TextBlock = inlineText(),
    val surpriseKill: TextBlock = inlineText(),
    val temperatureMessage: Map<TemperatureBand, TextBlock> = emptyMap(),
    val noShelterMessage: Map<TemperatureBand, TextBlock> = emptyMap(),
)

/** Inclusive temperature range belonging to one camp damage band. */
data class TemperatureBandRange(
    val band: TemperatureBand,
    val range: IntRange,
)

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

private fun String.compatibilityId(): String =
    lowercase()
        .map { char -> if (char.isLetterOrDigit()) char else '-' }
        .joinToString("")
        .trim('-')
        .replace(Regex("-+"), "-")

data class GameMetadataModel(
    val universe: GameUniverseModel = GameUniverseModel(),
)

data class GameUniverseModel(
    val name: String = "Rylween",
    val version: String = "kotlin-dsl",
    val compatibilityId: String = name.compatibilityId(),
)

data class GameLimitsModel(
    val maxWorldMaps: Int? = null,
    val maxMapCells: Int? = null,
    val screenSize: GridSize? = null,
)

enum class TerrainSettlement { Town, Hut, Castle }

data class TerrainCatalogModel(
    val terrain: List<TerrainSemanticsModel> = referenceDefaults(),
) {
    companion object {
        fun referenceDefaults(): List<TerrainSemanticsModel> =
            listOf(
                TerrainSemanticsModel(terrain("mountain", 'm'), mapCode = 'm', passable = false, movementCost = 0),
                TerrainSemanticsModel(terrain("water", 'w'), mapCode = 'w', passable = false, movementCost = 0),
                TerrainSemanticsModel(terrain("plain", 's'), mapCode = 's'),
                TerrainSemanticsModel(terrain("town", '0'), mapCode = '0', allowsNpcPlacement = false, settlement = TerrainSettlement.Town),
                TerrainSemanticsModel(terrain("hut", '1'), mapCode = '1', allowsNpcPlacement = false, settlement = TerrainSettlement.Hut),
                TerrainSemanticsModel(terrain("foothills", 'f'), mapCode = 'f'),
                TerrainSemanticsModel(terrain("desert", 'd'), mapCode = 'd'),
                TerrainSemanticsModel(terrain("swamp", 't'), mapCode = 't'),
                TerrainSemanticsModel(terrain("forest", 'g'), mapCode = 'g'),
                TerrainSemanticsModel(
                    terrain("castle", '2'),
                    mapCode = '2',
                    allowsNpcPlacement = false,
                    settlement = TerrainSettlement.Castle,
                ),
            )
    }
}

data class TerrainSemanticsModel(
    val id: TerrainId,
    val mapCode: Char,
    val passable: Boolean = true,
    val movementCost: Int = 1,
    val allowsNpcPlacement: Boolean = passable,
    val settlement: TerrainSettlement? = null,
    val tags: Set<TerrainTagId> = emptySet(),
)

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
)

data class GridSize(
    val width: Int,
    val height: Int,
)

fun cells(
    width: Int,
    height: Int,
): GridSize = GridSize(width, height)

data class LayoutModel(
    val regions: List<ScreenRegionModel> = emptyList(),
)

data class WorldMapModel(
    val id: WorldMapId,
    val size: GridSize? = null,
    val storageCapacity: Int? = null,
    val layers: List<WorldMapLayerModel>,
)

data class WorldMapLayerModel(
    val id: MapLayerId,
    val rows: List<String>,
)

data class VisualsModel(
    val images: List<ImageAssetModel> = emptyList(),
    val staticScreens: List<StaticScreenModel> = emptyList(),
)

enum class ImageAssetKind { Ansi, Bitmap }

enum class ImagePlacement { FullScreen, MapWindow }

data class ImageAssetModel(
    val id: ImageId,
    val kind: ImageAssetKind,
    val resource: String,
    val placement: ImagePlacement,
)

enum class StaticScreenRuntimeReport { Score, VerboseScore, HallOfHeroes }

data class StaticScreenModel(
    val id: StaticScreenId,
    val image: ImageId? = null,
    val resource: String? = null,
    val commandHelp: Boolean = false,
    val runtimeReport: StaticScreenRuntimeReport? = null,
)
