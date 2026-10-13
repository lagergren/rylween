/**
 * Runtime-facing content aggregate and prompt/key contracts.
 *
 * This file groups the immutable records that direct runtimes consume after the
 * authored game DSL is validated: `GameModel`, `RuntimeContent`, wilderness and
 * dungeon key tables, reusable prompt models, trigger declarations, and
 * wilderness event/terrain-transition configuration. It is the boundary between
 * authoring builders and runtime/bundle readers, not a place for one-off game
 * scripts or Rylween-specific prose.
 */
package rylween.dsl.impl

import rylween.dsl.*

/**
 * Bundled view of [GameModel] content the reusable sessions actually consume:
 * combat tables, monster catalog, formulas, rewards, leveling rules, spells,
 * item-use scripts, item records, realm-level balance, dungeon/wilderness
 * subsystem config, town list, town menu, scenes, missions, characters.
 * Optional debug/secret hooks are included here as content-gated runtime
 * behavior rather than frontend globals.
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
    val pacing: GamePacingModel = GamePacingModel(),
    val progress: GameProgressModel = GameProgressModel(emptyList(), null),
    val characterCreation: CharacterCreationModel? = null,
    val dungeons: DungeonSystemModel = DungeonSystemModel(),
    val castles: List<CastleContentModel> = emptyList(),
    val wilderness: WildernessSystemModel = WildernessSystemModel(),
    val towns: List<TownModel> = emptyList(),
    val townSystem: TownSystemModel? = null,
    val scenes: List<SceneModel> = emptyList(),
    val missions: List<MissionModel> = emptyList(),
    val characters: List<CharacterModel> = emptyList(),
    val speak: SpeakSystemModel = SpeakSystemModel(null),
    val dialogue: List<DialogueModel> = emptyList(),
    val secrets: SecretSystemModel = SecretSystemModel(),
) {
    init {
        require(castles.map { it.id }.toSet().size == castles.size) { "duplicate castle content" }
    }
}

data class GameModel(
    val metadata: GameMetadataModel = GameMetadataModel(),
    val limits: GameLimitsModel = GameLimitsModel(),
    val itemCatalog: List<ItemRecordModel>,
    val itemPools: List<ItemPoolId>,
    val realm: RealmContentModel = RealmContentModel(),
    val prompts: GamePromptsModel,
    val pacing: GamePacingModel = GamePacingModel(),
    val visuals: VisualsModel,
    val audio: AudioModel = AudioModel(),
    val worldMaps: List<WorldMapModel>,
    val terrain: TerrainCatalogModel,
    val terminal: TerminalPresentationModel,
    val layouts: LayoutModel,
    val flags: List<FlagRef>,
    val authoredFlags: List<FlagContentModel>,
    val progress: GameProgressModel,
    val characterCreation: CharacterCreationModel?,
    val leveling: LevelingModel?,
    val formulas: List<FormulaModel>,
    val rewards: List<RewardBundleModel>,
    val combat: CombatSystemModel,
    val wilderness: WildernessSystemModel,
    val dungeons: DungeonSystemModel,
    val castles: List<CastleContentModel>,
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
    val secrets: SecretSystemModel = SecretSystemModel(),
) {
    init {
        require(castles.map { it.id }.toSet().size == castles.size) { "duplicate castle content" }
    }
}

fun GameModel.itemPool(pool: ItemPoolId): ItemPoolId = itemPools.firstOrNull { it == pool } ?: pool

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
        pacing = pacing,
        progress = progress,
        characterCreation = characterCreation,
        dungeons = dungeons,
        castles = castles,
        wilderness = wilderness,
        towns = towns,
        townSystem = townSystem,
        scenes = scenes,
        missions = missions,
        characters = characters,
        speak = speak,
        dialogue = dialogue,
        secrets = secrets,
    )

sealed interface Trigger {
    data class RandomStep(
        val percent: Int,
    ) : Trigger

    data class AtPosition(
        val point: GridPoint,
    ) : Trigger

    data class AtLocation(
        val location: LocationId,
    ) : Trigger

    data class OnTerrain(
        val terrain: TerrainId,
    ) : Trigger
}

data class WildernessSystemModel(
    val keys: WildernessKeysModel = WildernessKeysModel(),
    val events: WildernessEventModel = WildernessEventModel(),
    val terrainTransitions: TerrainTransitionModel = TerrainTransitionModel(),
)

/** Keypad and arrow-key movement bindings shared by wilderness and castle navigation. */
object MovementKeys {
    val north: InputBindingModel = inputKey("8", InputAliases.ARROW_UP, "UP", "ARROW_UP", label = "8/↑")
    val south: InputBindingModel = inputKey("2", InputAliases.ARROW_DOWN, "DOWN", "ARROW_DOWN", label = "2/↓")
    val west: InputBindingModel = inputKey("4", InputAliases.ARROW_LEFT, "LEFT", "ARROW_LEFT", label = "4/←")
    val east: InputBindingModel = inputKey("6", InputAliases.ARROW_RIGHT, "RIGHT", "ARROW_RIGHT", label = "6/→")
}

data class WildernessKeysModel(
    val moveNorth: InputBindingModel = MovementKeys.north,
    val moveSouth: InputBindingModel = MovementKeys.south,
    val moveWest: InputBindingModel = MovementKeys.west,
    val moveEast: InputBindingModel = MovementKeys.east,
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
    val debugForceCave: InputBindingModel = inputKey("[", label = "["),
    val debugForceEncounter: InputBindingModel = inputKey("]", label = "]"),
    val debugShowMap: InputBindingModel = inputKey("M"),
    val caveEnter: InputBindingModel = inputKey("E"),
    val caveLeave: InputBindingModel = inputKey("L"),
    val outOfMovesSave: InputBindingModel = inputKey("S"),
    val outOfMovesQuit: InputBindingModel = inputKey("Q"),
    val quitYes: InputBindingModel = inputKey("Y"),
    val quitNo: InputBindingModel = inputKey("N"),
    val commandHint: TextBlock = literalText("Press ? for commands."),
    val helpPages: List<TextBlock> = emptyList(),
    val commandLabels: WildernessCommandLabelsModel = WildernessCommandLabelsModel(),
)

data class WildernessCommandLabelsModel(
    val moveNorth: String = "Move North",
    val moveSouth: String = "Move South",
    val moveWest: String = "Move West",
    val moveEast: String = "Move East",
    val pass: String = "Pass",
    val save: String = "Save Game",
    val readyWeapon: String = "Wield Weapon",
    val readyArmor: String = "Ready Armor",
    val redraw: String = "Redraw Screen",
    val journal: String = "View Journal",
    val help: String = "View this help",
    val hunt: String = "Hunt for Food",
    val viewParty: String = "View Party",
    val removeArmor: String = "Take off armor",
    val removeWeapon: String = "Put down weapon",
    val enter: String = "Enter",
    val drop: String = "Leave (Discard) Item",
    val camp: String = "Camp",
    val castSpell: String = "Cast Spell",
    val quaff: String = "Quaff Potion",
    val quit: String = "Quit Game (No Save)",
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

fun WildernessKeysModel.debugKeys(): List<InputBindingModel> = listOf(debugForceCave, debugForceEncounter, debugShowMap)

fun WildernessKeysModel.caveKeys(): List<InputBindingModel> = listOf(caveEnter, caveLeave)

fun WildernessKeysModel.outOfMovesKeys(): List<InputBindingModel> = listOf(outOfMovesSave, outOfMovesQuit)

fun WildernessKeysModel.quitConfirmKeys(): List<InputBindingModel> = listOf(quitYes, quitNo)

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
    val commandHint: TextBlock = literalText("Press ? for commands."),
    val helpPages: List<TextBlock> = emptyList(),
    val commandLabels: DungeonCommandLabelsModel = DungeonCommandLabelsModel(),
)

data class DungeonCommandLabelsModel(
    val descend: String = "Descend",
    val ascend: String = "Ascend",
    val help: String = "View this help",
    val drop: String = "Leave (Discard) Item",
    val removeArmor: String = "Take off armor",
    val removeWeapon: String = "Put down weapon",
    val readyWeapon: String = "Wield Weapon",
    val readyArmor: String = "Ready Armor",
    val quaff: String = "Quaff Potion",
    val castSpell: String = "Cast Spell",
    val viewParty: String = "View Party",
    val save: String = "Save Game",
)

data class GamePromptsModel(
    val pauseVerbs: List<String> = emptyList(),
    val returnKeyName: String = "RETURN key",
    val titleMenu: TitleMenuPromptsModel = TitleMenuPromptsModel(),
    val priest: PriestSpellPromptsModel = PriestSpellPromptsModel(),
    val inventoryUse: InventoryUsePromptsModel = InventoryUsePromptsModel(),
    val equipment: EquipmentPromptsModel = EquipmentPromptsModel(),
    val save: SavePromptsModel = SavePromptsModel(),
    val town: TownPromptsModel = TownPromptsModel(),
    val wilderness: WildernessPromptsModel = WildernessPromptsModel(),
    val dungeon: DungeonPromptsModel = DungeonPromptsModel(),
    val party: PartyPromptsModel = PartyPromptsModel(),
    val characterCreation: CharacterCreationPromptsModel = CharacterCreationPromptsModel(),
)

/**
 * Authored runtime pacing for reusable systems that are not inside a script
 * body. Script-authored pauses still use [rylween.dsl.ScriptScope.pause].
 */
data class GamePacingModel(
    val battle: BattlePacingModel = BattlePacingModel(),
    val characterCreation: CharacterCreationPacingModel = CharacterCreationPacingModel(),
    val shop: ShopPacingModel = ShopPacingModel(),
    val speak: SpeakPacingModel = SpeakPacingModel(),
    val wilderness: WildernessPacingModel = WildernessPacingModel(),
)

/** Millisecond pauses between generated battle message pages. */
data class BattlePacingModel(
    val messagePauseMs: Int = 900,
    val openingPauseMs: Int = 1_200,
    val victoryPauseMs: Int = 4_000,
    val battleCryPauseMs: Int = 450,
)

/** Millisecond pauses used by the reusable character-creation flow. */
data class CharacterCreationPacingModel(
    val classRejectionPauseMs: Int = 1_000,
    val saveConfirmationPauseMs: Int = 2_000,
)

/** Millisecond pauses used by reusable shop interlude pages. */
data class ShopPacingModel(
    val interludePagePauseMs: Int = 2_000,
)

/** Millisecond pauses used by reusable NPC conversation beats. */
data class SpeakPacingModel(
    val promptReplyPauseMs: Int = 650,
    val responsePauseMs: Int = 1_600,
)

/** Millisecond pauses used by reusable wilderness subsystem beats. */
data class WildernessPacingModel(
    val campMessagePauseMs: Int = 1_300,
)

data class TitleMenuPromptsModel(
    val header: TextBlock = literalText("Rylween", ""),
    val createNewParty: InputBindingModel = inputKey("N", label = "New Party"),
    val continueSavedGame: InputBindingModel = inputKey("L", label = "Load Game"),
    val terminateParty: InputBindingModel = inputKey("D", label = "Delete Save"),
    val scoreboard: InputBindingModel = inputKey("S", label = "Scores"),
    val hallOfFame: InputBindingModel = inputKey("H", label = "Heroes"),
    val documentation: InputBindingModel = inputKey("M", label = "Manual"),
    val leave: InputBindingModel = inputKey("E", label = "Exit"),
    val continueUnavailableReasonLabel: String = "no save",
    val continueUnavailable: TextBlock = literalText("No saved party exists for this user."),
    val overwriteConfirm: TextBlock =
        literalText(
            "Creating a new party will overwrite the saved party for this user.",
            "",
            "(Y)es",
            "(N)o",
        ),
    val terminateConfirm: TextBlock = literalText("Terminate saved party?", "", "(Y)es", "(N)o"),
    val terminated: TextBlock = literalText("Saved party terminated."),
    val terminateFailed: TextBlock = literalText("Could not delete saved party."),
    val invalidInput: TextBlock = literalText("Invalid input."),
)

data class SavePromptsModel(
    val saving: TextBlock = missingPrompt("save.saving"),
    val done: TextBlock = missingPrompt("save.done"),
    val failed: TextBlock = missingPrompt("save.failed"),
)

data class CharacterCreationPromptsModel(
    val header: TextBlock = missingPrompt("character_creation.header"),
    val method: TextBlock = missingPrompt("character_creation.method"),
    val manualLabel: String = "Roll your own",
    val prerolledLabel: String = "Pre-rolled",
    val partyName: TextBlock = missingPrompt("character_creation.party_name"),
    val statsPage: TextBlock = missingPrompt("character_creation.stats_page"),
    val reroll: TextBlock = missingPrompt("character_creation.reroll"),
    val memberName: TextBlock = missingPrompt("character_creation.member_name"),
    val gender: TextBlock = missingPrompt("character_creation.gender"),
    val genderMaleLabel: String = "Male",
    val genderFemaleLabel: String = "Female",
    val race: TextBlock = missingPrompt("character_creation.race"),
    val classMenu: TextBlock = missingPrompt("character_creation.class_menu"),
    val invalidInput: TextBlock = literalText("Invalid input."),
    val saving: TextBlock = missingPrompt("character_creation.saving"),
    val saveConfirmation: TextBlock = literalText("OK"),
)

data class PriestSpellPromptsModel(
    val who: TextBlock = structuredText { line("Who? (1-", HuntText.partyMemberCount, ")") },
    val none: TextBlock = literalText("(N)one"),
    val noneKey: Char = 'N',
    val firstSpellKey: Char = 'A',
    val mageCombatOnly: TextBlock = flowText("Sorry. Mage spells are combat-only."),
    val wrongClass: TextBlock = literalText("Wrong class."),
    val dead: TextBlock = literalText("Dead."),
    val invalidCaster: TextBlock = literalText("Not a party member."),
    val invalidSpell: TextBlock = literalText("Not possible."),
    val noSpells: TextBlock = flowText("No priest spells are declared."),
)

data class InventoryUsePromptsModel(
    val usePrompt: TextBlock = literalText("Use what?"),
    val quaff: TextBlock = literalText("Quaff what?"),
    val noItems: TextBlock = flowText("You are not carrying anything."),
    val noItem: TextBlock = flowText("There is nothing in that slot."),
    val invalidSlot: TextBlock =
        structuredText {
            line("Inventory slot ", InventoryText.slotName)
            line("does not exist.")
        },
    val notUsable: TextBlock =
        structuredText {
            line("Nothing happens")
            line("with ", InventoryText.itemName, ".")
        },
) {
    fun choose(
        prompt: TextBlock,
        choiceLines: List<String>,
    ): TextBlock = combineTextBlocks(prompt, literalText(*choiceLines.toTypedArray()))
}

data class EquipmentPromptsModel(
    val inventory: TextBlock = literalText("Ready what?"),
    val who: TextBlock = literalText("Who? (1-N)"),
    val noAvailable: TextBlock =
        structuredText {
            line("No ", TextPart.EquipmentKindName, " available.")
        },
    val equipped: TextBlock =
        structuredText {
            line("Equipped ", TextPart.SelectedItemName, ".")
        },
    val readyWeapons: TextBlock = literalText("Do you want to ready some weapons before leaving?", "", "(Y)es", "(N)o"),
    val readyArmors: TextBlock = literalText("Do you want to ready some armors before leaving?", "", "(Y)es", "(N)o"),
)

data class TownPromptsModel(
    val menuTitle: TextBlock = structuredText { line("Entering ", TownText.name) },
    val entryWelcome: TextBlock = missingPrompt("town.entry_welcome"),
    val entryTax: TextBlock = missingPrompt("town.entry_tax"),
    val entryTaxThanks: TextBlock = missingPrompt("town.entry_tax_thanks"),
)

data class PartyPromptsModel(
    val journalHeader: TextBlock = missingPrompt("party.journal_header"),
    val journalEmpty: TextBlock = literalText("No journal entries."),
    val inventoryHeader: TextBlock = literalText("Inventory:"),
    val killsHeader: TextBlock = literalText("Monsters slain:"),
    val killsEmpty: TextBlock = literalText("No monsters slain yet."),
    val dropPrompt: TextBlock = literalText("Drop what?"),
    val dropEmptySlot: TextBlock = literalText("Nothing to drop."),
    val dropAborted: TextBlock = literalText("Aborted."),
    val dropDropped: TextBlock =
        structuredText {
            line("You drop the")
            line(TextPart.SelectedItemName, ".")
        },
    val dropCapacityReduced: TextBlock = flowText("You are forced to leave some of your food behind..."),
    val dropOverflowConfirm: TextBlock = literalText("Drop an item? (Y/N)"),
    val dropOverflowPrompt: TextBlock = literalText("Drop which item?", "RETURN leaves."),
)

data class WildernessPromptsModel(
    val pass: TextBlock = missingPrompt("wilderness.pass"),
    val noMoves: TextBlock = missingPrompt("wilderness.no_moves"),
    val outOfMoves: TextBlock = missingPrompt("wilderness.out_of_moves"),
    val notInWilderness: TextBlock = missingPrompt("wilderness.not_in_wilderness"),
    val nothingToEnter: TextBlock = missingPrompt("wilderness.nothing_to_enter"),
    val impassable: TextBlock = literalText("Impassable."),
    val quitConfirm: TextBlock = missingPrompt("wilderness.quit_confirm"),
    val dungeonHandoff: TextBlock = missingPrompt("wilderness.dungeon_handoff"),
)

data class DungeonPromptsModel(
    val levelMenu: TextBlock = missingPrompt("dungeon.level_menu"),
    val enterLevel: TextBlock = missingPrompt("dungeon.enter_level"),
    val atMaxDepth: TextBlock = missingPrompt("dungeon.at_max_depth"),
    val noTorches: TextBlock = missingPrompt("dungeon.no_torches"),
    val leaveCavern: TextBlock = missingPrompt("dungeon.leave_cavern"),
)

private fun missingPrompt(name: String): TextBlock = literalText("[missing prompt: $name]")

internal fun ConversationTopic.defaultLabel(): String =
    when (this) {
        ConversationTopic.Chitchat -> "Chitchat"
        ConversationTopic.Question -> "Question"
        ConversationTopic.Rumor -> "Rumors"
        ConversationTopic.Bye -> "Bye"
    }

internal fun QuestionTopic.defaultLabel(): String =
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
    val caveFoundPrompt: TextBlock = missingPrompt("wilderness.cave_found"),
    val caveNoMoves: TextBlock = missingPrompt("wilderness.cave_no_moves"),
)

data class TerrainTransitionModel(
    val chancePercent: Int = 9,
    val entries: List<TerrainTransitionTextModel> = emptyList(),
)

data class TerrainTransitionTextModel(
    val terrain: TerrainId,
    val text: TextBlock,
)
