/**
 * Character, mission, progress, leveling, and creation authoring builders.
 *
 * These builders stay in the public `rylween.dsl` package because game sources
 * use them directly from `characters { ... }`, `missions { ... }`,
 * `gameProgress { ... }`, `leveling { ... }`, and `characterCreation { ... }`.
 * They live outside `Dsl.kt` so the root DSL file can focus on the script
 * recording kernel and top-level game assembly.
 */
package rylween.dsl

import rylween.dsl.impl.CharacterClassOptionModel
import rylween.dsl.impl.CharacterCreationModel
import rylween.dsl.impl.CharacterCreationStartModel
import rylween.dsl.impl.CharacterCreationStat
import rylween.dsl.impl.CharacterCreationStatRollModel
import rylween.dsl.impl.CharacterModel
import rylween.dsl.impl.CharacterPlacementModel
import rylween.dsl.impl.CharacterPortraitModel
import rylween.dsl.impl.CharacterRaceOptionModel
import rylween.dsl.impl.ClassLevelUpModel
import rylween.dsl.impl.Facing
import rylween.dsl.impl.GameMilestoneModel
import rylween.dsl.impl.GameProgressModel
import rylween.dsl.impl.Gender
import rylween.dsl.impl.GridOffset
import rylween.dsl.impl.GridPoint
import rylween.dsl.impl.LevelingModel
import rylween.dsl.impl.MissionModel
import rylween.dsl.impl.PartyResources
import rylween.dsl.impl.PartyResourcesBuilder
import rylween.dsl.impl.PcClass
import rylween.dsl.impl.Race
import rylween.dsl.impl.RandomNpcPlacementOptionModel
import rylween.dsl.impl.RandomNpcPlacementPolicyModel
import rylween.dsl.impl.SourceSpecialField
import rylween.dsl.impl.SourceSpecialMapping
import rylween.dsl.impl.Trigger
import rylween.dsl.impl.WinConditionModel
import rylween.dsl.impl.WorldPosition

/**
 * Receiver for the authored NPC catalog.
 *
 * Designers declare NPC identity, job, gender, roles, and placement policy
 * here. Runtime speak/town systems consume the generated character records.
 */
@RylNextDsl
class CharacterCatalogBuilder {
    private val characters = mutableListOf<CharacterModel>()
    private var randomPlacementPolicy = RandomNpcPlacementPolicyModel()

    /**
     * Defines the default generated placement policy for later `randomPlacement()` NPCs.
     *
     * @param body placement choices shared by NPCs that do not override their
     * own random placement policy.
     */
    fun randomPlacementPolicy(body: RandomNpcPlacementPolicyBuilder.() -> Unit) {
        randomPlacementPolicy = RandomNpcPlacementPolicyBuilder().apply(body).build()
    }

    /**
     * Adds an NPC using explicit assignments inside [body].
     *
     * @param id stable authored NPC id.
     * @param body builder body that supplies name, job, placement, roles, and
     * optional gender.
     */
    fun character(
        id: NpcId,
        body: CharacterBuilder.() -> Unit,
    ) {
        characters += CharacterBuilder(id, randomPlacementPolicy).apply(body).build()
    }

    /**
     * Adds an NPC with the common name/job fields supplied as parameters.
     *
     * @param id stable authored NPC id.
     * @param name display name used in text and logs.
     * @param job authored NPC job, used by conversation and town service text.
     * @param body optional builder body for placement, roles, and gender.
     */
    fun character(
        id: NpcId,
        name: String,
        job: NpcJobId,
        body: CharacterBuilder.() -> Unit = {},
    ) {
        characters +=
            CharacterBuilder(id, randomPlacementPolicy)
                .apply {
                    this.name = name
                    this.job = job
                    body()
                }.build()
    }

    fun build(): List<CharacterModel> = characters.toList()
}

/**
 * Receiver for generated NPC placement policy.
 *
 * Each entry adds a chance-share bucket for town-service or wilderness
 * placement. NPCs can use the catalog default or override it individually.
 */
@RylNextDsl
class RandomNpcPlacementPolicyBuilder {
    private val options = mutableListOf<RandomNpcPlacementOptionModel>()

    /**
     * Adds one town bucket whose result is one of the named services offered by [town].
     *
     * @param town generated town handle, such as `Towns.thord`.
     * @param services named services that exist in that town, such as `Buildings.inn`.
     * @param chanceShares relative share for choosing this town bucket. It is not a
     * percentage; `1` means one share among all options in the same policy.
     */
    fun townServices(
        town: TownHandle,
        vararg services: BuildingId,
        chanceShares: Int = 1,
    ) {
        townServices(town.id, *services, chanceShares = chanceShares)
    }

    /**
     * Adds one town bucket whose result is one of the named services offered by [town].
     *
     * @param town stable town id for DSL code that is not using generated handles.
     * @param services named services that exist in that town, such as `Buildings.inn`.
     * @param chanceShares relative share for choosing this town bucket. It is not a
     * percentage; `1` means one share among all options in the same policy.
     */
    fun townServices(
        town: TownId,
        vararg services: BuildingId,
        chanceShares: Int = 1,
    ) {
        options += RandomNpcPlacementOptionModel.TownServices(town, services.toList(), chanceShares)
    }

    /**
     * Adds one wilderness bucket over [x] and [y], restricted at runtime to terrain that allows NPCs.
     *
     * @param x inclusive x-coordinate range authored in map coordinates.
     * @param y inclusive y-coordinate range authored in map coordinates.
     * @param chanceShares relative share for choosing this wilderness bucket. It is
     * not a percentage; `1` means one share among all options in the same policy.
     * @param body optional coordinate rewrite rules for legacy placement quirks.
     */
    fun wilderness(
        x: IntRange,
        y: IntRange,
        chanceShares: Int = 1,
        body: RandomWildernessPlacementBuilder.() -> Unit = {},
    ) {
        val replacements = RandomWildernessPlacementBuilder().apply(body).build()
        options += RandomNpcPlacementOptionModel.WildernessRect(x, y, replacements, chanceShares)
    }

    fun build(): RandomNpcPlacementPolicyModel = RandomNpcPlacementPolicyModel(options.toList())
}

/**
 * Receiver for optional coordinate adjustments inside one wilderness placement bucket.
 *
 * Designers use this only for legacy placement quirks where a generated x
 * coordinate needs to be rewritten before terrain validation.
 */
@RylNextDsl
class RandomWildernessPlacementBuilder {
    private val xReplacements = linkedMapOf<Int, Int>()

    /**
     * Rewrites a drawn x coordinate before the runtime checks terrain and occupancy.
     *
     * @param from authored x coordinate selected from the wilderness range.
     * @param to x coordinate used instead when building the placement candidate.
     */
    fun replaceX(
        from: Int,
        to: Int,
    ) {
        xReplacements[from] = to
    }

    fun build(): Map<Int, Int> = xReplacements.toMap()
}

/**
 * Builder for one authored NPC.
 *
 * @param id stable authored NPC id.
 * @param randomPlacementPolicy current catalog-level default for `randomPlacement()`.
 */
@RylNextDsl
class CharacterBuilder(
    private val id: NpcId,
    private val randomPlacementPolicy: RandomNpcPlacementPolicyModel,
) {
    /** Display name used in text, save/log output, and NPC menus. */
    var name: String = ""

    /** NPC job used by conversation text, generated job names, and authored role logic. */
    var job: NpcJobId = DslDefaultNpcJobs.merchant

    /** NPC gender used by authored text and character metadata. */
    var gender: Gender = Gender.Male

    private var placementModel: CharacterPlacementModel = CharacterPlacementModel.Randomized(randomPlacementPolicy)
    private val roleModels = mutableListOf<NpcRole>()

    /**
     * Places this NPC at a fixed wilderness coordinate or named town service.
     *
     * @param position authored location, usually `Wilderness.xy(x, y)` or a
     * generated town service handle such as `Towns.thord.inn`.
     */
    fun fixed(position: WorldPosition) {
        placementModel = CharacterPlacementModel.Fixed(position)
    }

    /**
     * Places this NPC at a fixed wilderness coordinate relative to a generated town handle.
     *
     * @param town base town whose map coordinate anchors the placement.
     * @param offset coordinate delta from the town position.
     * @param requiredTerrain terrain required at the resolved wilderness tile.
     */
    fun fixedNear(
        town: TownHandle,
        offset: GridOffset,
        requiredTerrain: TerrainId,
    ) {
        fixedNear(town.id, offset, requiredTerrain)
    }

    /**
     * Places this NPC at a fixed wilderness coordinate relative to a town id.
     *
     * @param town base town whose map coordinate anchors the placement.
     * @param offset coordinate delta from the town position.
     * @param requiredTerrain terrain required at the resolved wilderness tile.
     */
    fun fixedNear(
        town: TownId,
        offset: GridOffset,
        requiredTerrain: TerrainId,
    ) {
        placementModel = CharacterPlacementModel.FixedNearTown(town, offset, requiredTerrain)
    }

    /** Uses the catalog-level default generated placement policy for this NPC. */
    fun randomPlacement() {
        placementModel = CharacterPlacementModel.Randomized(randomPlacementPolicy)
    }

    /**
     * Uses an NPC-specific generated placement policy.
     *
     * @param body placement choices used only by this NPC.
     */
    fun randomPlacement(body: RandomNpcPlacementPolicyBuilder.() -> Unit) {
        placementModel = CharacterPlacementModel.Randomized(RandomNpcPlacementPolicyBuilder().apply(body).build())
    }

    /** Keeps this NPC record out of the playable world. */
    fun unplaced() {
        placementModel = CharacterPlacementModel.Unplaced
    }

    /**
     * Assigns story roles to this NPC.
     *
     * @param roles stable role handles consumed by authored scripts and dynamic text.
     */
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

/**
 * Receiver for wilderness mission declarations.
 *
 * Designers add random-step, fixed-position, location, or terrain-triggered
 * mission hooks here.
 */
@RylNextDsl
class MissionCatalogBuilder {
    private val missions = mutableListOf<MissionModel>()

    fun mission(
        id: MissionId,
        body: MissionBuilder.() -> Unit,
    ) {
        missions += MissionBuilder(id).apply(body).build()
    }

    fun build(): List<MissionModel> = missions.toList()
}

/**
 * Receiver for one wilderness mission trigger.
 *
 * It declares when the mission checks, optional flag/expression requirements,
 * and the scene that runs when the trigger fires.
 */
@RylNextDsl
class MissionBuilder(
    private val id: MissionId,
) {
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
        val point =
            (position as? WorldPosition.Wilderness)?.point
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

/** Receiver for authored milestone declarations and the win condition. */
@RylNextDsl
class GameProgressBuilder {
    private val milestones = mutableListOf<GameMilestoneModel>()
    private var winBuilder: WinConditionBuilder? = null

    fun milestone(
        id: GameMilestoneId,
        body: GameMilestoneBuilder.() -> Unit,
    ) {
        milestones += GameMilestoneBuilder(id).apply(body).build()
    }

    fun winningConfiguration(body: WinConditionBuilder.() -> Unit) {
        winBuilder = WinConditionBuilder().apply(body)
    }

    fun build(): GameProgressModel = GameProgressModel(milestones.toList(), winBuilder?.build(milestones))
}

/** Builder for one milestone row, including special item projection metadata. */
@RylNextDsl
class GameMilestoneBuilder(
    private val id: GameMilestoneId,
) {
    private var specialInventoryItem: ItemId? = null
    private var requiredToWin: Boolean = false
    private var sourceSpecial: SourceSpecialMapping? = null

    fun showsAs(item: ItemId) {
        specialInventoryItem = item
    }

    fun requiredToWin() {
        requiredToWin = true
    }

    fun sourcePartyField(
        field: SourceSpecialField,
        achievedValue: Int = 1,
    ) {
        sourceSpecial = SourceSpecialMapping(field, achievedValue)
    }

    fun build(): GameMilestoneModel = GameMilestoneModel(id, specialInventoryItem, requiredToWin, sourceSpecial)
}

/** Builder for the current game completion rule and Hall-of-Heroes text. */
@RylNextDsl
class WinConditionBuilder {
    private var includeMarkedRequired = false
    private val explicitRequired = mutableListOf<GameMilestoneId>()
    private var completionMilestone: GameMilestoneId? = null
    private var hallOfHeroesEmptyText = "No party has completed the winning objective yet."
    private var hallOfHeroesEntryText = "{party} completed the winning objective."

    fun requiresAllMilestonesMarkedRequired() {
        includeMarkedRequired = true
    }

    fun requires(vararg milestones: GameMilestoneId) {
        explicitRequired += milestones
    }

    fun completedBy(milestone: GameMilestoneId) {
        completionMilestone = milestone
    }

    fun hallOfHeroes(
        empty: String,
        entry: String,
    ) {
        hallOfHeroesEmptyText = empty
        hallOfHeroesEntryText = entry
    }

    fun build(defined: List<GameMilestoneModel>): WinConditionModel {
        val markedRequired =
            if (includeMarkedRequired) {
                defined.filter { it.requiredToWin }.map { it.id }
            } else {
                emptyList()
            }

        return WinConditionModel(
            required = (explicitRequired + markedRequired).distinct(),
            completedBy = completionMilestone,
            hallOfHeroesEmptyText = hallOfHeroesEmptyText,
            hallOfHeroesEntryText = hallOfHeroesEntryText,
        )
    }
}

/** Receiver for level thresholds and class-specific level-up growth. */
@RylNextDsl
class LevelingBuilder {
    var maxLevel: Int = 28
    var hitPointFloor: FormulaId = DslDefaultFormulas.levelUpHitPointFloor
    private var thresholdModel: List<Int> = emptyList()
    private val classGrowthModel = mutableListOf<ClassLevelUpModel>()

    /**
     * Declares the exact experience required for each level.
     *
     * Levels are one-based in authored party/member state, so index `1` is the
     * threshold for level 1 and should be `0`; index `0` is deliberately unused
     * to keep the table readable against level numbers. Runtimes consume this
     * compiled table directly instead of deriving their own formula, because
     * classic D&D-style progressions often vary by class or by high-level band.
     * If a future game needs generated per-class progressions, add that as a
     * typed authoring model that still compiles to this explicit table shape.
     */
    fun xpThresholds(vararg thresholds: Int) {
        thresholdModel = thresholds.toList()
    }

    fun classLevelUp(
        klass: PcClass,
        body: ClassLevelUpBuilder.() -> Unit,
    ) {
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

/** Builder for one class's level-up stat growth. */
@RylNextDsl
class ClassLevelUpBuilder(
    private val klass: PcClass,
) {
    var weaponSkill: Int = 0
    var health: Int = 0
    var wisdom: Int = 0
    var charisma: Int = 0
    var dexterity: Int = 0

    fun build(): ClassLevelUpModel = ClassLevelUpModel(klass, weaponSkill, health, wisdom, charisma, dexterity)
}

/** Receiver for the player character creation table. */
@RylNextDsl
class CharacterCreationBuilder {
    private var statRollModel: CharacterCreationStatRollModel? = null
    private var startModel: CharacterCreationStartModel? = null
    private val raceOptions = mutableListOf<CharacterRaceOptionModel>()
    private val classOptions = mutableListOf<CharacterClassOptionModel>()
    private val portraitOptions = mutableListOf<CharacterPortraitModel>()

    fun statRoll(
        min: Int,
        max: Int,
    ) {
        statRollModel = CharacterCreationStatRollModel(min, max)
    }

    fun start(body: CharacterCreationStartBuilder.() -> Unit) {
        startModel = CharacterCreationStartBuilder().apply(body).build()
    }

    fun race(
        race: Race,
        label: String,
    ) {
        raceOptions += CharacterRaceOptionModel(race, label)
    }

    fun playerClass(
        klass: PcClass,
        label: String,
        body: CharacterCreationClassBuilder.() -> Unit = {},
    ) {
        classOptions += CharacterCreationClassBuilder(klass, label).apply(body).build()
    }

    fun portrait(
        image: ImageId,
        gender: Gender? = null,
        race: Race? = null,
        klass: PcClass? = null,
    ) {
        portraitOptions += CharacterPortraitModel(gender, race, klass, image)
    }

    fun build(): CharacterCreationModel =
        CharacterCreationModel(
            statRoll = checkNotNull(statRollModel) { "characterCreation requires statRoll(min, max)" },
            start = checkNotNull(startModel) { "characterCreation requires start { ... }" },
            races = raceOptions.toList(),
            classes = classOptions.toList(),
            portraits = portraitOptions.toList(),
        )
}

/** Builder for the initial party placement and resources used by character creation. */
@RylNextDsl
class CharacterCreationStartBuilder {
    var map: WorldMapId = DslDefaultMaps.wilderness
    var position: WorldPosition? = null
    var facing: Facing? = null
    var partyMemberMax: Int = 0
    var level: Int = 0
    var dailyTimeLeft: Int = 0
    private var resourcesState: PartyResources? = null

    fun resources(body: PartyResourcesBuilder.() -> Unit) {
        resourcesState = PartyResourcesBuilder().apply(body).build()
    }

    fun build(): CharacterCreationStartModel =
        CharacterCreationStartModel(
            map = map,
            position = checkNotNull(position) { "characterCreation start requires position" },
            facing = facing,
            resources = checkNotNull(resourcesState) { "characterCreation start requires resources { ... }" },
            partyMemberMax = partyMemberMax,
            level = level,
            dailyTimeLeft = dailyTimeLeft,
        )
}

/** Builder for one selectable character class and its optional stat gate. */
@RylNextDsl
class CharacterCreationClassBuilder(
    private val klass: PcClass,
    private val label: String,
) {
    private var requiredStat: CharacterCreationStat? = null
    private var minimum: Int? = null
    private var failureText: TextBlock? = null

    fun requires(
        stat: CharacterCreationStat,
        minimum: Int,
        failureText: TextBlock,
    ) {
        requiredStat = stat
        this.minimum = minimum
        this.failureText = failureText
    }

    fun build(): CharacterClassOptionModel =
        CharacterClassOptionModel(
            klass = klass,
            label = label,
            requiredStat = requiredStat,
            minimum = minimum,
            failureText = failureText,
        )
}
