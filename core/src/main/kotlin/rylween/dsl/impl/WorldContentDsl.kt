@file:Suppress("ktlint:standard:no-wildcard-imports")

/**
 * World, character, progress, reward, conversation, and secret content records.
 *
 * This file contains immutable model types for authored entities and systems
 * that are shared by validation, bundle compilation, and runtimes: towns, NPCs,
 * placement policies, monsters, missions, milestones, rewards, character
 * creation, conversations, and sysop/debug secrets. Builders for these records
 * live in `Dsl.kt` and specialized DSL files; Rylween-specific rows live in the
 * game DSL.
 */

package rylween.dsl.impl

import rylween.dsl.*

/**
 * Authored NPC placement rule used when the shared world state is first created.
 *
 * The runtime does not re-roll randomized placements on every session. It
 * materializes a missing world state once, writes the generated positions into
 * the save state, and all later players on that runtime read those positions.
 */
sealed interface CharacterPlacementModel {
    /**
     * Places the NPC at one named wilderness coordinate or town service forever.
     *
     * @property position authored location, usually `Wilderness.xy(x, y)` or a
     * generated town service handle such as `Towns.thord.inn`.
     */
    data class Fixed(
        val position: WorldPosition,
    ) : CharacterPlacementModel

    /**
     * Places the NPC at a fixed wilderness coordinate derived from a named town.
     *
     * This keeps prose such as "near Ollk" tied to the same authored town row
     * used to calculate the coordinate. Validation resolves [town] plus [offset]
     * and fails the game build unless the target map tile is [requiredTerrain].
     *
     * @property town base town whose wilderness map coordinate anchors the placement.
     * @property offset coordinate delta from the town position to the NPC position.
     * @property requiredTerrain terrain that must exist at the resolved wilderness tile.
     */
    data class FixedNearTown(
        val town: TownId,
        val offset: GridOffset,
        val requiredTerrain: TerrainId,
    ) : CharacterPlacementModel

    /**
     * Places the NPC once using [policy], then persists the generated position in world state.
     *
     * @property policy chance-share placement choices for this NPC.
     */
    data class Randomized(
        val policy: RandomNpcPlacementPolicyModel,
    ) : CharacterPlacementModel

    /**
     * Places the NPC once using a wilderness-only [policy], then persists the generated position in world state.
     *
     * @property policy chance-share placement choices for this NPC. The DSL author
     * should normally include only wilderness options here.
     */
    data class RandomizedWilderness(
        val policy: RandomNpcPlacementPolicyModel,
    ) : CharacterPlacementModel

    /** Keeps an imported or reserved NPC record out of the playable world. */
    data object Unplaced : CharacterPlacementModel
}

/**
 * Share-based choices for generated NPC placement.
 *
 * `chanceShares` is not a percentage and `1` does not mean "always". It is a
 * relative share within this policy: five options with `chanceShares = 1` each
 * have equal 20% odds; two options with shares `3` and `1` have 75% and 25% odds.
 * Rylween's original "one wilderness bucket and four town buckets" is authored
 * as five options with the default `chanceShares` value of `1`.
 *
 * @property options choices the runtime may select from when it generates a
 * missing NPC position for the shared world state.
 */
data class RandomNpcPlacementPolicyModel(
    val options: List<RandomNpcPlacementOptionModel> = emptyList(),
)

/** One generated NPC placement choice within a [RandomNpcPlacementPolicyModel]. */
sealed interface RandomNpcPlacementOptionModel {
    /**
     * Relative odds for this option compared with the other options in the same policy.
     *
     * This is a share count, not a percentage. An option with `chanceShares = 2`
     * is twice as likely as an option with `chanceShares = 1`.
     */
    val chanceShares: Int

    /**
     * Randomly place the NPC in one of the named services offered by [town].
     *
     * @property town authored town handle, stored as a stable id.
     * @property services named town services that can receive this NPC, such as
     * `Buildings.tavern` or `Buildings.inn`; these must exist in [town].
     * @property chanceShares relative chance of choosing this town-service bucket
     * compared with the other options in the same policy. This is a share
     * count, not a percentage; the default `1` means one share.
     */
    data class TownServices(
        val town: TownId,
        val services: List<BuildingId>,
        override val chanceShares: Int = 1,
    ) : RandomNpcPlacementOptionModel

    /**
     * Randomly place the NPC on an eligible wilderness tile inside the authored rectangle.
     *
     * @property x inclusive authored x-coordinate range.
     * @property y inclusive authored y-coordinate range.
     * @property xReplacements optional coordinate rewrites applied before terrain
     * and occupancy checks; this models legacy data quirks without hiding them
     * in runtime constants.
     * @property chanceShares relative chance of choosing this wilderness bucket
     * compared with the other options in the same policy. This is a share
     * count, not a percentage; the default `1` means one share.
     */
    data class WildernessRect(
        val x: IntRange,
        val y: IntRange,
        val xReplacements: Map<Int, Int> = emptyMap(),
        override val chanceShares: Int = 1,
    ) : RandomNpcPlacementOptionModel
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
    val entryTax: TownEntryTaxModel,
    val nativeServices: List<EngineServiceUse>,
)

data class TownEntryTaxModel(
    val minimumLeaderLevel: Int = 5,
    val pay: InputBindingModel = inputKey("P", label = "Pay to Enter"),
    val leave: InputBindingModel = inputKey("L", label = "Leave"),
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
    val encounterImage: ImageId? = null,
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
    val hallOfHeroesEmptyText: String = "No party has completed the winning objective yet.",
    val hallOfHeroesEntryText: String = "{party} completed the winning objective.",
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

enum class CharacterCreationStat { WeaponSkill, Health, Wisdom, Charisma }

data class CharacterCreationStatRollModel(
    val min: Int,
    val max: Int,
) {
    init {
        require(min >= 0) { "character creation stat roll minimum must be non-negative" }
        require(max >= min) { "character creation stat roll range must be ascending: $min..$max" }
    }
}

data class CharacterCreationStartModel(
    val map: WorldMapId,
    val position: WorldPosition,
    val facing: Facing?,
    val resources: PartyResources,
    val partyMemberMax: Int,
    val level: Int,
    val dailyTimeLeft: Int,
) {
    init {
        require(partyMemberMax > 0) { "character creation party member max must be positive" }
        require(level > 0) { "character creation starting level must be positive" }
        require(dailyTimeLeft >= 0) { "character creation daily time left must be non-negative" }
    }
}

data class CharacterRaceOptionModel(
    val race: Race,
    val label: String,
)

data class CharacterClassOptionModel(
    val klass: PcClass,
    val label: String,
    val requiredStat: CharacterCreationStat?,
    val minimum: Int?,
    val failureText: TextBlock?,
) {
    init {
        require((requiredStat == null) == (minimum == null && failureText == null)) {
            "character creation class ${klass.name} must declare requiredStat, minimum, and failureText together"
        }
        minimum?.let { require(it >= 0) { "character creation class ${klass.name} minimum must be non-negative" } }
    }
}

data class CharacterPortraitModel(
    val gender: Gender?,
    val race: Race?,
    val klass: PcClass?,
    val image: ImageId,
)

data class CharacterCreationModel(
    val statRoll: CharacterCreationStatRollModel,
    val start: CharacterCreationStartModel,
    val races: List<CharacterRaceOptionModel>,
    val classes: List<CharacterClassOptionModel>,
    val portraits: List<CharacterPortraitModel>,
)

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

data class YesNoKeysModel(
    val yes: InputBindingModel = inputKey('Y'),
    val no: InputBindingModel = inputKey('N'),
)

val StandardYesNoKeys: YesNoKeysModel = YesNoKeysModel()

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
    val declined: TextBlock?,
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

object HuntText {
    val memberName: TextPart = TextPart.HuntMemberName
    val memberSubjectPronoun: TextPart = TextPart.HuntMemberSubjectPronoun
    val foodGained: TextPart = TextPart.HuntFoodGained
    val partyMemberCount: TextPart = TextPart.PartyMemberCount
    val partyMemberMax: TextPart = TextPart.PartyMemberMax
}

object TownText {
    val name: TextPart = TextPart.CurrentTownName
    val taxPercent: TextPart = TextPart.TownTaxPercent
    val taxAmount: TextPart = TextPart.TownTaxAmount
}

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

enum class ConversationNpcSelection { BuildingListOrWildernessLocation }

enum class NpcVisitPolicy { PerPartyVisitedBit }

enum class DialogueBranchSelection { FirstMatchingStateThenDefault }

enum class MissionHintPolicy { BeforeEachMenu }

enum class ConversationTopic { Chitchat, Question, Rumor, Bye }

enum class QuestionTopic { Lodging, Magic, People, Nothing }

enum class ChitchatSubject { Well, Weather, Health }

enum class ChitchatReplyPolicy { BySubjectAndNpcJob }

enum class RumorUsagePolicy { OncePerConversationThenFallback }

enum class RumorSourcePolicy { StandardTownRumors }

enum class ChitchatPromptAudience { Any, Female, Male }

data class ConversationMenuActionModel(
    val topic: ConversationTopic,
    val binding: InputBindingModel,
    val action: KeyActionId,
    val label: String = topic.defaultLabel(),
) {
    val key: Char get() = binding.primaryChar
}

data class QuestionMenuActionModel(
    val topic: QuestionTopic,
    val binding: InputBindingModel,
    val action: KeyActionId,
    val label: String = topic.defaultLabel(),
) {
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

data class ConversationRumorModel(
    val usage: RumorUsagePolicy,
    val source: RumorSourcePolicy,
)

data class ConversationPromptTextModel(
    val chooseNpc: TextBlock = inlineText(),
    val mainMenu: TextBlock = inlineText(),
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

data class RumorEntryModel(
    val id: RumorId?,
    val text: TextBlock,
    val excludedBy: List<FlagState>,
)

data class RumorTextTableModel(
    val id: RumorTableId,
    val entries: List<RumorEntryModel>,
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
    val rumorTables: List<RumorTextTableModel> = emptyList(),
)

data class StandardConversationModel(
    val npcSelection: ConversationNpcSelection,
    val visitPolicy: NpcVisitPolicy,
    val branchSelection: DialogueBranchSelection,
    val missionHints: MissionHintPolicy,
    val menu: ConversationMenuModel,
    val chitchat: ChitchatModel,
    val rumor: ConversationRumorModel,
    val prompts: ConversationPromptTextModel,
    val nativeServices: List<EngineServiceUse>,
)

data class SpeakSystemModel(
    val standardConversation: StandardConversationModel?,
    val textTables: SpeakTextTablesModel = SpeakTextTablesModel(),
    val questionActions: List<QuestionActionScriptModel> = emptyList(),
)

data class SecretSystemModel(
    val players: List<SecretPlayerModel> = emptyList(),
)

/**
 * Matcher for hidden player-specific authoring hooks.
 *
 * `Any` applies to every player on a runtime. `Named` applies only when the
 * runtime player identity matches one of the authored names; the DSL exposes
 * this as `playerNamed("...")` or `playersNamed("...", "...")`.
 */
sealed interface SecretPlayerMatcherModel {
    data object Any : SecretPlayerMatcherModel

    data class Named(
        val names: List<String>,
    ) : SecretPlayerMatcherModel
}

/** Hidden key and step-trigger actions gated by one [SecretPlayerMatcherModel]. */
data class SecretPlayerModel(
    val matcher: SecretPlayerMatcherModel,
    val keyActions: List<SecretKeyActionModel> = emptyList(),
    val stepTrigger: SecretStepTriggerModel? = null,
    val alwaysRunFromNonMandatoryEncounters: Boolean = false,
) {
    val playerNames: List<String>
        get() =
            when (matcher) {
                SecretPlayerMatcherModel.Any -> emptyList()
                is SecretPlayerMatcherModel.Named -> matcher.names
            }
}

data class SecretKeyActionModel(
    val binding: InputBindingModel,
    val action: SecretActionModel,
)

data class SecretStepTriggerModel(
    val everySteps: Int,
    val action: SecretActionModel,
)

sealed interface SecretActionModel {
    data class AddTimeLeft(
        val amount: Int,
        val refreshStatus: Boolean = false,
    ) : SecretActionModel

    data class AddGold(
        val amount: Int,
        val refreshStatus: Boolean = false,
    ) : SecretActionModel

    data class SetInventory(
        val slot: Int,
        val item: ItemId,
    ) : SecretActionModel

    data class SpawnEncounter(
        val monster: MonsterId,
    ) : SecretActionModel
}
