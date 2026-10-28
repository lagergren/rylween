@file:Suppress("ktlint:standard:no-wildcard-imports")

/**
 * Author-facing validation for the Kotlin DSL model.
 *
 * `Dsl.kt` defines the content and script model; this file checks cross-table
 * references, startup states, script handles, save/load compatibility, and
 * game-specific invariants before compiled loaders or runtime sessions see the
 * data.
 */

package rylween.dsl

import rylween.dsl.impl.*

/** One author-facing validation failure with a stable dotted content path. */
data class ValidationDiagnostic(
    val path: String,
    val message: String,
) {
    override fun toString(): String = if (path.isBlank()) message else "$path: $message"
}

/** Exception carrying one or more structured authoring diagnostics. */
class RylweenValidationException(
    val diagnostics: List<ValidationDiagnostic>,
) : IllegalStateException(
        diagnostics.joinToString(separator = "\n") { it.toString() },
    )

private inline fun MutableList<ValidationDiagnostic>.captureValidation(
    path: String,
    block: () -> Unit,
) {
    try {
        block()
    } catch (error: RylweenValidationException) {
        error.diagnostics.forEach { diagnostic ->
            add(
                if (diagnostic.path.isBlank()) {
                    diagnostic.copy(path = path)
                } else {
                    diagnostic.copy(path = "$path.${diagnostic.path}")
                },
            )
        }
    } catch (error: IllegalStateException) {
        add(ValidationDiagnostic(path, error.message ?: error::class.simpleName.orEmpty()))
    }
}

private class FlagStateIndex(
    flags: List<FlagRef>,
) {
    private val bySlug: Map<String, FlagRef> = flags.associateBy { it.slug }
    private val states: Set<Pair<String, String>> =
        flags.flatMap { flag -> flag.states.map { state -> flag.slug to state.native } }.toSet()
    private val stateByKey: Map<Pair<String, String>, FlagState> =
        flags.flatMap { flag -> flag.states.map { state -> (flag.slug to state.native) to state } }.toMap()
    private val orderByKey: Map<Pair<String, String>, Int> =
        flags
            .flatMap { flag -> flag.states.mapIndexed { index, state -> (flag.slug to state.native) to index } }
            .toMap()

    fun contains(state: FlagState): Boolean = state.flag.slug to state.native in states

    fun contains(
        slug: String,
        native: String,
    ): Boolean = slug to native in states

    fun flag(slug: String): FlagRef? = bySlug[slug]

    fun state(
        slug: String,
        native: String,
    ): FlagState? = stateByKey[slug to native]

    fun order(state: FlagState): Int? = orderByKey[state.flag.slug to state.native]

    fun order(
        slug: String,
        native: String,
    ): Int? = orderByKey[slug to native]
}

private class TerrainBindingIndex(
    terrain: TerrainCatalogModel,
    terminal: TerminalPresentationModel,
    worldMaps: List<WorldMapModel>,
) {
    private val terrainBySlug: Map<String, TerrainSemanticsModel> =
        terrain.terrain.associateBy { it.id.slug }
    private val stylesBySlug: Map<String, TerminalTerrainStyleModel> =
        terminal.terrain.associateBy { it.terrain.slug }
    private val mapCodes: Set<Char> =
        worldMaps
            .flatMap { map -> map.layers }
            .filter { layer -> layer.id == DslDefaultMapLayers.terrain }
            .flatMap { layer -> layer.rows.flatMap { row -> row.toList() } }
            .toSet()

    fun requireStyled(
        terrain: TerrainId,
        label: String,
    ) {
        check(terrain.slug.isNotBlank()) { "$label references blank terrain" }
        val semantics = terrainBySlug[terrain.slug]
        check(semantics != null) {
            "$label references terrain ${terrain.slug}, but terrain { ... } does not declare it"
        }
        val style = stylesBySlug[terrain.slug]
        check(style != null) {
            "$label references terrain ${terrain.slug}, but terminal { terrain(...) } does not declare it"
        }
        terrain.sourceMapCode?.let { referencedCode ->
            check(semantics.mapCode == referencedCode) {
                "$label references terrain ${terrain.slug} with map tile '$referencedCode', " +
                    "but terrain declaration uses '${semantics.mapCode}'"
            }
        }
    }

    fun requireMapReachable(
        terrain: TerrainId,
        label: String,
    ) {
        requireStyled(terrain, label)
        terrainBySlug[terrain.slug]?.let { semantics ->
            check(semantics.mapCode in mapCodes) {
                "$label references terrain ${terrain.slug} tile '${semantics.mapCode}', but no authored world map uses it"
            }
        }
    }
}

private val emptyTextBindings: Set<TextPart> = emptySet()
private val speakNoRumorScreen = text("speak.no_rumor")
private val memberChoiceTextBindings: Set<TextPart> =
    setOf(
        TextPart.PartyMemberCount,
        TextPart.PartyMemberMax,
    )
private val huntTextBindings: Set<TextPart> =
    setOf(
        TextPart.HuntMemberName,
        TextPart.HuntMemberSubjectPronoun,
        TextPart.HuntFoodGained,
        TextPart.PartyMemberCount,
        TextPart.PartyMemberMax,
    )
private val upkeepTextBindings: Set<TextPart> =
    setOf(
        TextPart.UpkeepMemberName,
    )
private val randomItemOfferTextBindings: Set<TextPart> =
    setOf(
        TextPart.OfferedItemName,
        TextPart.OfferedItemPrice,
    )
private val inventoryUseTextBindings: Set<TextPart> =
    setOf(
        TextPart.InventorySlotName,
        TextPart.SelectedItemName,
    )
private val selectedItemTextBindings: Set<TextPart> = setOf(TextPart.SelectedItemName)
private val equipmentTextBindings: Set<TextPart> = setOf(TextPart.EquipmentKindName)
private val shopPriceTextBindings: Set<TextPart> = setOf(TextPart.ShopPrice)
private val shopCapacityTextBindings: Set<TextPart> = setOf(TextPart.ShopCarryCap)
private val wildernessDungeonEntryTextBindings: Set<TextPart> =
    setOf(
        TextPart.WildernessDungeonName,
        TextPart.WildernessDungeonLevels,
    )
private val dungeonDepthTextBindings: Set<TextPart> = setOf(TextPart.DungeonDepth)
private val conversationTextBindings: Set<TextPart> = setOf(TextPart.ConversationNpcName)
private val townEntryTaxTextBindings: Set<TextPart> =
    setOf(
        TextPart.CurrentTownName,
        TextPart.TownTaxPercent,
        TextPart.TownTaxAmount,
    )
private val battleTextBindings: Set<TextPart> =
    setOf(
        TextPart.BattleFoeDisplayName,
        TextPart.BattleAttackerName,
        TextPart.BattleTargetName,
        TextPart.BattleDamage,
        TextPart.BattleExperience,
        TextPart.BattleGold,
        TextPart.BattleLootName,
        TextPart.BattleLevelMemberName,
        TextPart.BattleLevelsGained,
    )
private val characterCreationStatsTextBindings: Set<TextPart> =
    setOf(
        TextPart.CreationMemberNumber,
        TextPart.CreationWeaponSkill,
        TextPart.CreationHealth,
        TextPart.CreationWisdom,
        TextPart.CreationCharisma,
        TextPart.CreationDexterity,
    )
private val characterCreationMemberNumberTextBindings: Set<TextPart> = setOf(TextPart.CreationMemberNumber)
private val characterCreationMemberNameTextBindings: Set<TextPart> = setOf(TextPart.CreationMemberName)

private fun FlagStateIndex.requireKnown(
    state: FlagState,
    label: String,
) {
    check(contains(state)) {
        "$label references unknown story state ${state.flag.slug}.${state.native}"
    }
}

private fun FlagStateIndex.requireKnown(
    slug: String,
    native: String,
    label: String,
) {
    check(contains(slug, native)) {
        "$label references unknown story state $slug.$native"
    }
}

/** Validates flag declarations and their native state lists. */
fun validateFlagCatalog(flags: List<FlagRef>) {
    check(flags.map { it.slug }.toSet().size == flags.size) { "duplicate flag slug" }

    flags.forEach { flag ->
        val nativeStates = flag.states.map { it.native }
        check(nativeStates.isNotEmpty()) { "flag ${flag.slug} has no states" }
        check(nativeStates.toSet().size == nativeStates.size) { "duplicate state in flag ${flag.slug}" }
    }
}

/** Validates item metadata, storage policy, and equipment-specific fields. */
fun validateItemCatalog(records: List<ItemRecordModel>) {
    check(records.isNotEmpty()) { "item catalog is empty" }
    check(records.map { it.id }.toSet().size == records.size) { "duplicate item handle" }

    records.forEach { record ->
        check(record.displayName.isNotBlank()) { "item ${record.id.slug} requires a display name" }
        check(record.cost >= 0) { "item ${record.id.slug} has negative cost" }
        check(record.equippableBy.isEmpty() || record.kind == ItemKind.Weapon || record.kind == ItemKind.Armor) {
            "item ${record.id.slug} can restrict equipment classes only when it is a weapon or armor"
        }
        when (record.kind) {
            ItemKind.Empty -> {
                check(record.storage == ItemStorageKind.EmptySentinel) {
                    "empty item ${record.id.slug} must use empty-sentinel storage"
                }
            }
            ItemKind.Weapon -> {
                check(record.weaponDamage != null && record.weaponDamage > 0) {
                    "weapon ${record.id.slug} requires positive damage"
                }
                check(record.armorClass == null) { "weapon ${record.id.slug} cannot carry armor class" }
            }
            ItemKind.Armor -> {
                check(record.armorClass != null && record.armorClass > 0) {
                    "armor ${record.id.slug} requires positive armor class"
                }
                check(record.weaponDamage == null) { "armor ${record.id.slug} cannot carry weapon damage" }
            }
            ItemKind.Special -> {
                check(record.storage == ItemStorageKind.SpecialMilestone) {
                    "special item ${record.id.slug} must project from milestone state"
                }
            }
            ItemKind.Gear,
            ItemKind.Treasure,
            ItemKind.MagicItem,
            -> {
                check(record.storage == ItemStorageKind.SlottedInventory) {
                    "item ${record.id.slug} must use slotted-inventory storage"
                }
            }
            ItemKind.Potion -> {
                check(record.storage == ItemStorageKind.SlottedInventory) {
                    "potion ${record.id.slug} must use slotted-inventory storage"
                }
            }
        }
    }
}

/** Validates milestone uniqueness, special-item projections, and win conditions. */
fun validateGameProgress(
    progress: GameProgressModel,
    itemRecords: List<ItemRecordModel>,
) {
    val ids = progress.milestones.map { it.id }
    check(ids.toSet().size == ids.size) { "duplicate game milestone" }

    val itemRecordById = itemRecords.associateBy { it.id }
    val specialItems = progress.milestones.mapNotNull { it.specialInventoryItem }
    check(specialItems.toSet().size == specialItems.size) {
        "duplicate special inventory projection"
    }
    specialItems.forEach { item ->
        val record = itemRecordById[item]
        check(record?.storage == ItemStorageKind.SpecialMilestone) {
            "special inventory projection ${item.slug} must be milestone-backed item metadata"
        }
    }

    val defined = ids.toSet()
    progress.win?.let { win ->
        win.required.forEach { required ->
            check(required in defined) { "win condition references unknown milestone ${required.slug}" }
        }
        win.completedBy?.let {
            check(it in defined) { "win completion references unknown milestone ${it.slug}" }
        }
        check(win.hallOfHeroesEmptyText.isNotBlank()) { "hall of heroes empty text is blank" }
        check("{party}" in win.hallOfHeroesEntryText) { "hall of heroes entry text must include {party}" }
    }
}

/** Validates authored formula catalog uniqueness. */
fun validateFormulas(formulas: List<FormulaModel>) {
    check(formulas.map { it.id }.toSet().size == formulas.size) { "duplicate formula" }
}

/** Validates that the hunt and castle trap chances, the spell cost and the battle arithmetic name authored formulas. */
private fun validateSystemFormulaReferences(content: GameModel) {
    val formulaIds = content.formulas.map { it.id }.toSet()
    content.realm.hunt?.let { hunt ->
        check(hunt.successChance in formulaIds) {
            "hunt success chance references missing formula ${hunt.successChance.slug}"
        }
    }
    content.realm.spellCost?.let { spellCost ->
        check(spellCost in formulaIds) { "realm spell cost references missing formula ${spellCost.slug}" }
    }
    content.realm.upkeep.foodCost?.let { foodCost ->
        check(foodCost in formulaIds) { "realm upkeep food cost references missing formula ${foodCost.slug}" }
    }
    content.townSystem?.entryTax?.amount?.let { tax ->
        check(tax in formulaIds) { "town entry tax references missing formula ${tax.slug}" }
    }
    content.combat.standardBattle?.let { battle ->
        listOf(
            "attack hit chance" to battle.playerRound.attack.hitChance,
            "attack damage" to battle.playerRound.attack.damage,
            "defend healing" to battle.playerRound.defend.healing,
            "monster spell chance" to battle.monsterRound.spellChance,
            "monster melee hit chance" to battle.monsterRound.melee.hitChance,
            "monster melee damage" to battle.monsterRound.melee.damage,
            "encounter minimum hit points" to battle.encounterHitPointMinFormula,
            "encounter maximum hit points" to battle.encounterHitPointMaxFormula,
            "encounter foe count maximum" to battle.encounterFoeCountMaxFormula,
            "encounter weapon skill" to battle.encounterWeaponSkillFormula,
        ).forEach { (rule, formula) ->
            check(formula in formulaIds) { "standard battle $rule references missing formula ${formula.slug}" }
        }
    }
    content.castles.forEach { castle ->
        val disarmChance =
            checkNotNull(castle.pitfall.disarmChance) { "castle ${castle.id.slug} pitfall needs a disarm chance formula" }
        check(disarmChance in formulaIds) {
            "castle ${castle.id.slug} pitfall disarm chance references missing formula ${disarmChance.slug}"
        }
    }
}

/** Validates optional leveling rules and formula references. */
fun validateLeveling(
    leveling: LevelingModel?,
    formulas: List<FormulaModel>,
) {
    leveling ?: return
    check(leveling.maxLevel > 0) { "leveling max level must be positive" }
    check(leveling.xpThresholds.size > leveling.maxLevel) {
        "leveling XP thresholds must include indexes through max level ${leveling.maxLevel}"
    }
    check(leveling.xpThresholds.all { it >= 0 }) { "leveling XP thresholds must be non-negative" }
    check(leveling.xpThresholds[1] == 0) {
        "leveling XP threshold for level 1 must be 0"
    }
    (2..leveling.maxLevel).forEach { level ->
        val previous = leveling.xpThresholds[level - 1]
        val current = leveling.xpThresholds[level]
        check(current > previous) {
            "leveling XP thresholds must strictly increase from level ${level - 1} ($previous) to level $level ($current)"
        }
    }
    check(leveling.hitPointFloor in formulas.map { it.id }.toSet()) {
        "leveling hit point floor references missing formula ${leveling.hitPointFloor.slug}"
    }
    check(
        leveling.classGrowth
            .map { it.klass }
            .toSet()
            .size == leveling.classGrowth.size,
    ) {
        "duplicate class level-up declaration"
    }
}

/** Validates reward bundles, loot references, and item placement metadata. */
fun validateRewardBundles(
    rewards: List<RewardBundleModel>,
    formulas: List<FormulaModel>,
    itemRecords: List<ItemRecordModel>,
) {
    check(rewards.map { it.id }.toSet().size == rewards.size) { "duplicate reward bundle" }
    val formulaIds = formulas.map { it.id }.toSet()

    rewards.forEach { reward ->
        check(reward.experience in formulaIds) {
            "reward ${reward.id.slug} references missing experience formula ${reward.experience.slug}"
        }
        check(reward.gold in formulaIds) {
            "reward ${reward.id.slug} references missing gold formula ${reward.gold.slug}"
        }
        checkPercent(reward.loot.dropChancePercent, "reward ${reward.id.slug} loot drop chance")
        checkPercent(reward.loot.treasureChancePercent, "reward ${reward.id.slug} loot treasure chance")
        validateItemPool(reward.loot.treasure, itemRecords)
        validateItemPool(reward.loot.magic, itemRecords)

        val itemRecordById = itemRecords.associateBy { it.id }
        val rewardItems = reward.loot.treasure.items + reward.loot.magic.items
        val hasSpecialItems = rewardItems.any { itemRecordById[it]?.storage == ItemStorageKind.SpecialMilestone }
        check(!hasSpecialItems || reward.application.specialItems == RewardSpecialItemPolicy.ProjectMilestoneState) {
            "reward ${reward.id.slug} special items must project through milestone state"
        }
    }
}

/** Validates combat rules, action scripts, and battle reward references. */
fun validateCombatSystem(
    combat: CombatSystemModel,
    rewards: List<RewardBundleModel>,
) {
    val rewardIds = rewards.map { it.id }.toSet()

    combat.standardBattle?.let { battle ->
        checkPercent(battle.opening.fleeChancePercent, "standard battle flee chance")
        checkPercent(battle.opening.desperateFleeChancePercent, "standard battle desperate flee chance")
        check(battle.opening.banners.isNotEmpty()) { "standard battle requires at least one encounter banner" }
        validateInputChoiceBinding(battle.openingKeys.fight, "standard battle fight key")
        validateInputChoiceBinding(battle.openingKeys.run, "standard battle run key")
        checkUniqueBindings(listOf(battle.openingKeys.fight, battle.openingKeys.run), "standard battle opening")

        val actionKinds = battle.actions.map { it.kind }
        battle.actions.forEach { action ->
            validateInputChoiceBinding(action.binding, "standard battle action ${action.kind} key")
        }
        checkUniqueBindings(battle.actions.map { it.binding }, "standard battle actions")
        check(actionKinds.toSet().size == actionKinds.size) { "standard battle has duplicate action kind" }
        check(CombatActionKind.Attack in actionKinds) { "standard battle requires an attack action" }
        check(CombatActionKind.Defend in actionKinds) { "standard battle requires a defend action" }
        check(CombatActionKind.UseMagicItem in actionKinds) { "standard battle requires a use-magic-item action" }
        battle.actions.forEach { action ->
            validateTextBlock(action.prompt, "standard battle action ${action.kind} prompt")
        }
        validateBattleText(battle.text)

        check(battle.playerRound.defend.weaponSkillPenalty >= 0) {
            "standard battle defend weapon-skill penalty must be non-negative"
        }
        check(battle.playerRound.defend.restThreshold >= 0) {
            "standard battle defend rest threshold must be non-negative"
        }
        check(battle.playerRound.defend.cap > 0) {
            "standard battle defend cap must be positive"
        }
        check(battle.playerRound.battleCry.weaponSkillPenalty >= 0) {
            "standard battle battle-cry weapon-skill penalty must be non-negative"
        }

        check(battle.roundLimit > 0) { "standard battle round limit must be positive" }
        check(battle.encounterFoeCountMax > 0) { "standard battle foe count max must be positive" }

        check(battle.rewards in rewardIds) {
            "standard battle references missing reward bundle ${battle.rewards.slug}"
        }

        val services = battle.nativeServices.map { it.service }
        check(services.toSet().size == services.size) { "standard battle has duplicate native service" }
    }
}

private fun validateBattleText(text: BattleTextModel) {
    listOf(
        "encounter banner" to text.encounterBanner,
        "you-face" to text.youFace,
        "foe display name" to text.foeDisplayName,
        "still-face" to text.stillFace,
        "fight prompt" to text.fight,
        "run prompt" to text.run,
        "blood-flow" to text.bloodFlow,
        "what-now" to text.whatNow,
        "target prompt" to text.targetPrompt,
        "use-what" to text.useWhat,
        "spell abort" to text.spellAbort,
        "run coward" to text.runCoward,
        "run desperate fail" to text.runDesperateFail,
        "run fail away" to text.runFailAway,
        "run escaped" to text.runEscaped,
        "not possible" to text.notPossible,
        "magic immune" to text.magicImmune,
        "spell fizzle" to text.spellFizzle,
        "not enough magic" to text.notEnoughMagic,
        "rest gain hits" to text.restGainHits,
        "defend stance" to text.defendStance,
        "battle cry" to text.battleCry,
        "player hit" to text.playerHit,
        "player miss" to text.playerMiss,
        "monster hit" to text.monsterHit,
        "monster miss" to text.monsterMiss,
        "member death" to text.memberDeath,
        "killed" to text.killed,
        "party held" to text.partyHeld,
        "party defeated" to text.partyDefeated,
        "victory bested" to text.victoryBested,
        "victory each survivor" to text.victoryEachSurvivor,
        "victory reward" to text.victoryReward,
        "victory found object" to text.victoryFoundObject,
        "victory loot" to text.victoryLoot,
        "level gain" to text.levelGain,
    ).forEach { (label, block) ->
        validateTextBlock(block, "standard battle text $label", allowedRuntimeBindings = battleTextBindings)
    }
}

private fun validateWildernessSystem(
    wilderness: WildernessSystemModel,
    terrainBindings: TerrainBindingIndex,
) {
    validateWildernessKeys(wilderness.keys)

    val events = wilderness.events
    checkPercent(events.caveChancePercent, "wilderness cave chance")
    checkPercent(events.quietChancePercent, "wilderness quiet chance")
    check(events.caveChancePercent + events.quietChancePercent <= 100) {
        "wilderness cave chance + quiet chance must be <= 100"
    }
    checkPercent(events.encounterSplitPercent, "wilderness encounter split")
    check(events.caveDepthRange.first > 0 && events.caveDepthRange.last >= events.caveDepthRange.first) {
        "wilderness cave depth range must be positive and ascending"
    }
    check(events.caveEntryCost >= 0) { "wilderness cave entry cost must be non-negative" }

    val transition = wilderness.terrainTransitions
    checkPercent(transition.chancePercent, "wilderness terrain transition chance")
    val terrains = transition.entries.map { it.terrain }
    check(terrains.toSet().size == terrains.size) { "wilderness terrain transitions have duplicate terrain entries" }
    transition.entries.forEach { entry ->
        terrainBindings.requireStyled(entry.terrain, "wilderness terrain transition")
        check(entry.terrain.sourceMapCode != null) {
            "wilderness terrain transition ${entry.terrain.slug} needs a map tile code"
        }
        validateTextBlock(entry.text, "wilderness terrain transition ${entry.terrain.slug}")
    }
}

/** Validates dungeon events, scenarios, random draws, and monster references. */
fun validateDungeonSystem(
    dungeons: DungeonSystemModel,
    monsters: List<MonsterModel>? = null,
) {
    check(
        dungeons.events
            .map { it.id }
            .toSet()
            .size == dungeons.events.size,
    ) {
        "duplicate dungeon event"
    }
    val eventIds = dungeons.events.map { it.id }.toSet()
    val encounterTiers = monsters?.mapNotNull { it.tier }?.toSet()
    dungeons.events.forEach { event ->
        check(event.id.slug.isNotBlank()) { "dungeon event references blank id" }
        event.clearWhenFlag?.let { flag ->
            check(flag.flag.slug.isNotBlank() && flag.native.isNotBlank()) {
                "dungeon event ${event.id.slug} has invalid conditional clear flag"
            }
        }
        event.randomEncounterTier?.let { tier ->
            check(tier.slug.isNotBlank()) { "dungeon event ${event.id.slug} references blank encounter tier" }
            encounterTiers?.let { tiers ->
                check(tier in tiers) {
                    "dungeon event ${event.id.slug} random encounter tier ${tier.slug} has no authored monsters"
                }
            }
        }
        check(event.statementNodes.isNotEmpty() || event.randomEncounterTier != null) {
            "dungeon event ${event.id.slug} must declare script or random encounter"
        }
    }

    check(
        dungeons.scenarios
            .map { it.dungeon }
            .toSet()
            .size == dungeons.scenarios.size,
    ) {
        "duplicate dungeon scenario"
    }
    dungeons.scenarios.forEach { scenario ->
        check(scenario.dungeon.slug.isNotBlank()) { "dungeon scenario references blank dungeon" }
        scenario.name?.let { name ->
            check(name.isNotBlank()) { "dungeon scenario ${scenario.dungeon.slug} has blank display name" }
        }
        scenario.levelRange?.let { range ->
            check(range.first > 0 && range.last >= range.first) {
                "dungeon scenario ${scenario.dungeon.slug} has invalid level range"
            }
        }
        scenario.fixedLevelEvents.forEach { (level, event) ->
            check(level > 0) { "dungeon scenario ${scenario.dungeon.slug} uses invalid level $level" }
            scenario.levelRange?.let { range ->
                check(level <= range.last) {
                    "dungeon scenario ${scenario.dungeon.slug} level $level is deeper than ${range.last}"
                }
            }
            check(event in eventIds) {
                "dungeon scenario ${scenario.dungeon.slug} references missing event ${event.slug}"
            }
        }
        scenario.randomEvents.forEach { random ->
            check(random.weight > 0) {
                "dungeon scenario ${scenario.dungeon.slug} random event ${random.event.slug} has invalid weight"
            }
            check(random.event in eventIds) {
                "dungeon scenario ${scenario.dungeon.slug} references missing random event ${random.event.slug}"
            }
        }
    }
}

/** Validates authored castle content, room events, rewards, and references. */
fun validateCastleSystem(
    castle: CastleContentModel,
    itemCatalog: List<ItemRecordModel>,
    progress: GameProgressModel,
    monsters: List<MonsterModel>,
    scenes: List<SceneModel> = emptyList(),
    worldMaps: List<WorldMapModel> = emptyList(),
) {
    val label = "castle ${castle.id.slug}"
    check(castle.entry.x >= 0 && castle.entry.y >= 0) { "$label entry must use non-negative world coordinates" }
    validateCastleEntryTile(castle, worldMaps)
    check(castle.mapSize > 1) { "$label map size must be greater than one" }
    check(castle.roomTypeMax >= 3) { "$label room type max must allow monster, pitfall, and treasure rooms" }
    check(castle.roomEvents.keys.all { it in 1..castle.roomTypeMax }) {
        "$label room event rolls must fit inside 1..${castle.roomTypeMax}"
    }
    val milestones = progress.milestones.map { it.id }.toSet()
    castle.entryRequirement?.let { requirement ->
        check(requirement in milestones) { "$label entry requirement references unknown milestone ${requirement.slug}" }
    }
    castle.completionMilestone?.let { completion ->
        check(completion in milestones) {
            "$label completion marker references unknown milestone ${completion.slug}"
        }
    }
    check(monsters.any { it.tier == castle.monsterTier }) {
        "$label monster tier ${castle.monsterTier.slug} has no authored monsters"
    }
    validateCastleScripts(castle.scripts, scenes, label)

    val itemIds = itemCatalog.map { it.id }.toSet()
    check(castle.treasure.gateMax >= 0) { "$label treasure gate max must be non-negative" }
    check(castle.treasure.regularKinds.isNotEmpty()) { "$label treasure needs at least one regular kind table" }
    castle.treasure.regularKinds.forEachIndexed { index, items ->
        check(items.isNotEmpty()) { "$label regular treasure kind $index is empty" }
        items.forEach { item -> check(item in itemIds) { "$label treasure references missing item ${item.slug}" } }
    }
    check(castle.treasure.magic.isNotEmpty()) { "$label magic treasure table is empty" }
    castle.treasure.magic.forEach { item ->
        check(item in itemIds) { "$label magic treasure references missing item ${item.slug}" }
    }

    check(castle.pitfall.damageDivisor > 0) { "$label pitfall damage divisor must be positive" }
    check(castle.pitfall.trapTypes.isNotEmpty()) { "$label pitfall needs at least one trap type" }
    castle.pitfall.trapTypes.forEachIndexed { index, block ->
        validateTextBlock(block, "$label pitfall trap ${index + 1}")
    }

    check(castle.stairs.floorCount > 0) { "$label floor count must be positive" }
    check(
        castle.stairs.items
            .map { it.digit }
            .toSet()
            .size == castle.stairs.items.size,
    ) {
        "$label stairs contain duplicate give digits"
    }
    castle.stairs.items.forEach { item ->
        check(item.digit in 1..9) { "$label stair item digit must be 1..9" }
        check(item.milestone in milestones) {
            "$label stair item ${item.itemName} references unknown milestone ${item.milestone.slug}"
        }
        check(item.itemName.isNotBlank()) { "$label stair item ${item.digit} has blank display name" }
        validateTextBlock(item.reject, "$label stair item ${item.itemName} rejection")
    }

    validateCastleKeys(castle.keys, label)
    validateCastleText(castle.text, castle.stairs.floorCount, label)
}

private fun validateCastleScripts(
    scripts: CastleScriptsModel,
    scenes: List<SceneModel>,
    label: String,
) {
    val sceneIds = scenes.map { it.id }.toSet()
    listOf(
        "already completed" to scripts.alreadyCompleted,
        "entry blocked" to scripts.entryBlocked,
        "entered" to scripts.entered,
        "completed" to scripts.completed,
    ).forEach { (hook, scene) ->
        scene?.let {
            check(it in sceneIds) { "$label $hook script references missing scene ${it.slug}" }
        }
    }
}

private fun validateCastleEntryTile(
    castle: CastleContentModel,
    worldMaps: List<WorldMapModel>,
) {
    if (worldMaps.isEmpty()) return
    val worldMap = worldMaps.singleOrNull { map -> map.id == DslDefaultMaps.wilderness } ?: return
    val terrainRows =
        worldMap.layers.singleOrNull { layer -> layer.id == DslDefaultMapLayers.terrain }?.rows
            ?: return
    val row = if (castle.entry.y > 0) terrainRows.getOrNull(castle.entry.y - 1) else null
    val tile = row?.getOrNull(castle.entry.x)
    val castleTile = DslDefaultTerrain.castle.sourceMapCode
    check(tile == castleTile) {
        "castle ${castle.id.slug} entry ${castle.entry.x}x${castle.entry.y} must point at castle tile '$castleTile' " +
            "on world map ${worldMap.id.slug}"
    }
}

/** Validates per-role secret actions and their referenced content. */
fun validateSecretSystem(
    secrets: SecretSystemModel,
    itemCatalog: List<ItemRecordModel>,
    itemPools: List<ItemPoolId>,
    monsters: List<MonsterModel>,
) {
    val roles = secrets.players.map(SecretPlayerModel::role)
    check(roles.toSet().size == roles.size) { "duplicate secret role block" }

    val itemIds = itemCatalog.map { it.id }.toSet()
    val monsterIds = monsters.map { it.id }.toSet()
    secrets.players.forEach { player ->
        val roleLabel = player.role.name.lowercase()
        player.stepTrigger?.let { trigger ->
            check(trigger.everySteps > 0) {
                "secret $roleLabel step trigger must be positive"
            }
            validateSecretAction(trigger.action, "secret $roleLabel step trigger", itemIds, itemPools, monsterIds)
        }
        player.keyActions.forEach { keyAction ->
            validateInputChoiceBinding(keyAction.binding, "secret $roleLabel key")
            validateSecretAction(keyAction.action, "secret $roleLabel key", itemIds, itemPools, monsterIds)
        }
        checkUniqueBindings(
            player.keyActions.map { it.binding },
            "secret $roleLabel",
        )
    }
}

private fun validateSecretAction(
    action: SecretActionModel,
    label: String,
    itemIds: Set<ItemId>,
    itemPools: List<ItemPoolId>,
    monsterIds: Set<MonsterId>,
) {
    when (action) {
        is SecretActionModel.AddGold -> check(action.amount >= 0) { "$label adds negative gold" }
        is SecretActionModel.AddTimeLeft -> check(action.amount >= 0) { "$label adds negative time left" }
        is SecretActionModel.GrantItems -> {
            val pool = itemPools.firstOrNull { pool -> pool == action.pool }
            checkNotNull(pool) { "$label references missing item pool ${action.pool.slug}" }
            check(pool.items.isNotEmpty()) { "$label item pool ${action.pool.slug} is empty" }
            pool.items.forEach { item -> check(item in itemIds) { "$label references missing item ${item.slug}" } }
        }
        is SecretActionModel.SpawnEncounter ->
            check(action.monster in monsterIds) { "$label references missing monster ${action.monster.slug}" }
    }
}

private fun validateCastleKeys(
    keys: CastleKeysModel,
    label: String,
) {
    val navigate =
        listOf(
            "move north" to keys.moveNorth,
            "move south" to keys.moveSouth,
            "move west" to keys.moveWest,
            "move east" to keys.moveEast,
            "save" to keys.save,
            "help" to keys.help,
            "view party" to keys.viewParty,
            "remove armor" to keys.removeArmor,
            "remove weapon" to keys.removeWeapon,
            "drop" to keys.drop,
            "ready weapon" to keys.readyWeapon,
            "ready armor" to keys.readyArmor,
            "quaff" to keys.quaff,
            "cast spell" to keys.castSpell,
        )
    val treasure = listOf("take treasure" to keys.treasureTake, "leave treasure" to keys.treasureLeave)
    val stairs =
        listOf(
            "teleport" to keys.stairsTeleport,
            "give" to keys.stairsGive,
            "leave" to keys.stairsLeave,
        )
    (navigate + treasure + stairs).forEach { (inputLabel, binding) ->
        validateInputBinding(binding, "$label key $inputLabel")
    }
    checkUniqueBindings(navigate.map { it.second }, "$label navigation")
    checkUniqueBindings(treasure.map { it.second }, "$label treasure")
    checkUniqueBindings(stairs.map { it.second }, "$label stairs")
}

private fun validateCastleText(
    text: CastleTextModel,
    floorCount: Int,
    label: String,
) {
    listOf(
        "room monster" to text.roomMonster,
        "trap disarm suffix" to text.trapTriesDisarmSuffix,
        "trap disarm succeeds" to text.trapDisarmSucceed,
        "trap disarm fails" to text.trapDisarmFail,
        "treasure menu" to text.treasureMenu,
        "treasure full" to text.treasureFull,
        "treasure taken" to text.treasureTaken,
        "move north" to text.moveNorth,
        "move south" to text.moveSouth,
        "move east" to text.moveEast,
        "move west" to text.moveWest,
        "stairs intro" to text.stairsIntro,
        "stairs guardian offer" to text.stairsGuardianOffer,
        "stairs menu" to text.stairsMenu,
        "stairs leave" to text.stairsLeave,
        "teleport vortex" to text.teleportVortex,
        "teleport outside" to text.teleportOutside,
        "give menu" to text.giveMenu,
        "stairs guardian grateful" to text.stairsGuardianGrateful,
    ).forEach { (textLabel, block) -> validateTextBlock(block, "$label $textLabel") }
    text.floorNames.forEachIndexed { index, floor ->
        check(floor.isNotBlank()) { "$label floor name ${index + 1} is blank" }
    }
    if (text.floorNames.isNotEmpty()) {
        check(text.floorNames.size >= floorCount) {
            "$label floor names must cover $floorCount floors"
        }
    }
}

private fun validateWildernessKeys(keys: WildernessKeysModel) {
    listOf(
        "move north" to keys.moveNorth,
        "move south" to keys.moveSouth,
        "move west" to keys.moveWest,
        "move east" to keys.moveEast,
        "pass" to keys.pass,
        "save" to keys.save,
        "ready weapon" to keys.readyWeapon,
        "ready armor" to keys.readyArmor,
        "redraw" to keys.redraw,
        "journal" to keys.journal,
        "help" to keys.help,
        "hunt" to keys.hunt,
        "view party" to keys.viewParty,
        "remove armor" to keys.removeArmor,
        "remove weapon" to keys.removeWeapon,
        "enter" to keys.enter,
        "drop" to keys.drop,
        "camp" to keys.camp,
        "cast spell" to keys.castSpell,
        "quaff" to keys.quaff,
        "quit" to keys.quit,
        "debug force cave" to keys.debugForceCave,
        "debug force encounter" to keys.debugForceEncounter,
        "debug show map" to keys.debugShowMap,
        "cave enter" to keys.caveEnter,
        "cave leave" to keys.caveLeave,
        "out-of-moves save" to keys.outOfMovesSave,
        "out-of-moves quit" to keys.outOfMovesQuit,
        "quit yes" to keys.quitYes,
        "quit no" to keys.quitNo,
    ).forEach { (label, binding) ->
        validateInputBinding(binding, "wilderness key $label")
    }

    checkUniqueBindings(keys.exploreKeys(), "wilderness explore")
    checkUniqueBindings(keys.exploreKeys() + keys.debugKeys(), "wilderness explore/debug")
    checkUniqueBindings(keys.caveKeys(), "wilderness cave")
    checkUniqueBindings(keys.outOfMovesKeys(), "wilderness out-of-moves")
    checkUniqueBindings(keys.quitConfirmKeys(), "wilderness quit-confirm")
    validateTextBlock(keys.commandHint, "wilderness command hint")
    keys.helpPages.forEachIndexed { index, page ->
        validateTextBlock(page, "wilderness help page ${index + 1}")
    }
}

private fun validateInputBinding(
    binding: InputBindingModel,
    label: String,
) {
    check(binding.primary.isNotEmpty()) { "$label has blank primary input" }
    check(binding.label.isNotBlank()) { "$label has blank display label" }
    check(binding.tokens.none { it.isEmpty() }) { "$label has blank input alias" }
    val tokens = binding.tokens.flatMap { inputTokenVariants(it).toSet() }
    check(tokens.toSet().size == tokens.size) { "$label has duplicate input aliases" }
}

private fun validateInputChoiceBinding(
    binding: InputBindingModel,
    label: String,
) {
    validateInputBinding(binding, label)
    check(binding.primary.length == 1) { "$label primary input must be one character for the current terminal adapter" }
}

private fun checkUniqueBindings(
    bindings: List<InputBindingModel>,
    label: String,
) {
    val tokens = bindings.flatMap { binding -> binding.tokens.flatMap(::inputTokenVariants).toSet() }
    check(tokens.toSet().size == tokens.size) {
        "$label keys must be unique"
    }
}

/** Validates town declarations and service/building consistency. */
fun validateTowns(towns: List<TownModel>) {
    check(towns.isNotEmpty()) { "content must declare towns" }
    check(towns.map { it.id }.toSet().size == towns.size) { "duplicate town id" }
    check(towns.map { it.name }.toSet().size == towns.size) { "duplicate town name" }

    towns.forEach { town ->
        check(town.name.isNotBlank()) { "town ${town.id.slug} requires a display name" }
        check(town.position.x >= 0 && town.position.y >= 0) {
            "town ${town.id.slug} has invalid coordinate ${town.position}"
        }
        checkPercent(town.taxPercent, "town ${town.id.slug} tax")
        check(town.services.isNotEmpty()) { "town ${town.id.slug} needs at least one service" }
        check(
            town.services
                .map { it.building }
                .toSet()
                .size == town.services.size,
        ) {
            "town ${town.id.slug} has duplicate services"
        }
        town.services.forEach { service ->
            check(service.building.slug.isNotBlank()) {
                "town ${town.id.slug} references blank service"
            }
        }
    }
}

/** Validates town-system menus, entry tax, building services, and scene references. */
fun validateTownSystem(
    townSystem: TownSystemModel?,
    towns: List<TownModel>,
    scenes: List<SceneModel>,
) {
    check(townSystem != null) { "content must declare a town system" }

    val menu = townSystem.menu
    check(menu.isNotEmpty()) { "town system needs a town entry menu" }
    menu.forEach { action ->
        validateInputChoiceBinding(action.binding, "town entry menu ${action.action.slug} key")
    }
    checkUniqueBindings(menu.map { it.binding }, "town entry menu")
    check(menu.any { it.endsMenu }) {
        "town entry menu needs a leave action that exits the menu"
    }
    check(townSystem.leavePrompts.toSet().size == townSystem.leavePrompts.size) {
        "town leave prompts contain duplicate equipment commands"
    }
    townSystem.leavePrompts.forEach { command ->
        check(command in setOf(EquipmentCommand.ReadyWeapon, EquipmentCommand.ReadyArmor)) {
            "town leave prompt ${command.name} is not a ready command"
        }
    }
    validateInputChoiceBinding(townSystem.entryTax.pay, "town entry tax pay key")
    validateInputChoiceBinding(townSystem.entryTax.leave, "town entry tax leave key")
    checkUniqueBindings(listOf(townSystem.entryTax.pay, townSystem.entryTax.leave), "town entry tax")

    val buildingServices = townSystem.buildingServices
    check(buildingServices.isNotEmpty()) { "town system needs building services" }
    check(buildingServices.map { it.building }.toSet().size == buildingServices.size) {
        "town system has duplicate building services"
    }

    val buildingServiceIds = buildingServices.map { it.building }.toSet()
    val knownScenes = scenes.map { it.id }.toSet()
    val townServiceIds = towns.flatMap { town -> town.services.map { it.building } }.toSet()

    townServiceIds.forEach { building ->
        check(building in buildingServiceIds) {
            "towns offer ${building.slug}, but town system has no building service for it"
        }
    }

    menu.forEach { action ->
        check(action.action.slug.isNotBlank()) { "town entry menu has blank action" }
        action.building?.let { building ->
            check(building in buildingServiceIds) {
                "town entry menu action ${action.action.slug} references missing building service ${building.slug}"
            }
            check(!action.endsMenu) {
                "town entry service action ${action.action.slug} must stay in the town menu"
            }
        }
    }

    buildingServices.forEach { service ->
        check(service.building.slug.isNotBlank() && service.building != DslDefaultBuildings.none) {
            "building service references invalid building ${service.building.slug}"
        }
        check(service.action.slug.isNotBlank()) {
            "building service ${service.building.slug} has blank action"
        }
        check(service.scene in knownScenes) {
            "building service ${service.building.slug} references missing scene ${service.scene.slug}"
        }

        val opensAt = service.opensAt
        val closesAt = service.closesAt
        if (opensAt != null || closesAt != null) {
            check(opensAt != null && closesAt != null) {
                "building service ${service.building.slug} must declare both open and close hour"
            }
            check(opensAt in 0..23 && closesAt in 0..23) {
                "building service ${service.building.slug} hours must be in 0..23"
            }
        }

        val services = service.nativeServices.map { it.service }
        check(services.toSet().size == services.size) {
            "building service ${service.building.slug} has duplicate native service"
        }
    }

    val nativeServices = townSystem.nativeServices.map { it.service }
    check(nativeServices.toSet().size == nativeServices.size) {
        "town system has duplicate native service"
    }
}

/** Validates NPC declarations, placements, and town/building references. */
fun validateCharacters(
    characters: List<CharacterModel>,
    towns: List<TownModel> = emptyList(),
    terrain: TerrainCatalogModel? = null,
    worldMaps: List<WorldMapModel> = emptyList(),
) {
    check(characters.map { it.id }.toSet().size == characters.size) { "duplicate character id" }
    val townsById = towns.associateBy { it.id }
    val terrainById = terrain?.terrain?.associateBy { it.id }
    val wildernessTerrainRows =
        worldMaps
            .singleOrNull { map -> map.id == DslDefaultMaps.wilderness }
            ?.layers
            ?.singleOrNull { layer -> layer.id == DslDefaultMapLayers.terrain }
            ?.rows

    val roles = characters.flatMap { character -> character.roles.map { it to character.id } }
    check(roles.map { it.first }.toSet().size == roles.size) {
        "duplicate character role binding"
    }

    characters.forEach { character ->
        check(character.name.isNotBlank()) { "character ${character.id.slug} requires a display name" }
        check(character.job.slug.isNotBlank()) { "character ${character.id.slug} has blank job" }
        check(character.roles.toSet().size == character.roles.size) {
            "character ${character.id.slug} has duplicate roles"
        }
        character.roles.forEach { role ->
            check(role.slug.isNotBlank()) { "character ${character.id.slug} has blank role" }
        }
        when (val placement = character.placement) {
            is CharacterPlacementModel.Fixed ->
                when (val position = placement.position) {
                    is WorldPosition.Wilderness -> {
                        check(position.point.x >= 0 && position.point.y >= 0) {
                            "character ${character.id.slug} has invalid wilderness coordinate ${position.point}"
                        }
                    }
                    is WorldPosition.Town -> {
                        townsById[position.town]?.let { town ->
                            check(position.building in town.services.map { it.building }) {
                                "character ${character.id.slug} is placed in ${position.town.slug}.${position.building.slug}, but that town does not offer the service"
                            }
                        }
                    }
                }
            is CharacterPlacementModel.FixedNearTown ->
                validateFixedNearTownPlacement(
                    character.id,
                    placement,
                    townsById,
                    terrainById,
                    wildernessTerrainRows,
                )
            is CharacterPlacementModel.Randomized ->
                validateRandomNpcPlacementPolicy(character.id, placement.policy, townsById)
            CharacterPlacementModel.Unplaced -> Unit
        }
    }
}

private fun validateFixedNearTownPlacement(
    character: NpcId,
    placement: CharacterPlacementModel.FixedNearTown,
    townsById: Map<TownId, TownModel>,
    terrainById: Map<TerrainId, TerrainSemanticsModel>?,
    wildernessTerrainRows: List<String>?,
) {
    val town =
        checkNotNull(townsById[placement.town]) {
            "character ${character.slug} is placed near unknown town ${placement.town.slug}"
        }
    val point = town.position + placement.offset
    check(point.x >= 0 && point.y >= 0) {
        "character ${character.slug} fixed-near-town placement resolves to invalid wilderness coordinate $point"
    }
    val terrain =
        checkNotNull(terrainById?.get(placement.requiredTerrain)) {
            "character ${character.slug} fixed-near-town placement requires unknown terrain ${placement.requiredTerrain.slug}"
        }
    val tile =
        checkNotNull(wildernessTerrainRows?.getOrNull(point.y - 1)?.getOrNull(point.x)) {
            "character ${character.slug} fixed-near-town placement resolves outside the wilderness map at $point"
        }
    check(tile == terrain.mapCode) {
        "character ${character.slug} fixed-near-town placement resolves to ${point.x}x${point.y} " +
            "on tile '$tile', but requires ${placement.requiredTerrain.slug} tile '${terrain.mapCode}'"
    }
}

private fun validateRandomNpcPlacementPolicy(
    character: NpcId,
    policy: RandomNpcPlacementPolicyModel,
    townsById: Map<TownId, TownModel>,
) {
    check(policy.options.isNotEmpty()) {
        "character ${character.slug} has a random placement policy with no options"
    }
    policy.options.forEach { option ->
        check(option.chanceShares > 0) {
            "character ${character.slug} has a random placement option with non-positive chanceShares ${option.chanceShares}"
        }
        when (option) {
            is RandomNpcPlacementOptionModel.TownServices ->
                validateRandomNpcTownServices(character, option, townsById)
            is RandomNpcPlacementOptionModel.WildernessRect ->
                validateRandomNpcWildernessRect(character, option)
        }
    }
}

private fun validateRandomNpcTownServices(
    character: NpcId,
    option: RandomNpcPlacementOptionModel.TownServices,
    townsById: Map<TownId, TownModel>,
) {
    val town = townsById[option.town]
    check(town != null) {
        "character ${character.slug} random placement references unknown town ${option.town.slug}"
    }
    check(option.services.isNotEmpty()) {
        "character ${character.slug} random placement in ${option.town.slug} has no town services"
    }
    val townServices = town.services.map(TownServiceModel::building).toSet()
    option.services.forEach { service ->
        check(service in townServices) {
            "character ${character.slug} random placement references ${option.town.slug}.${service.slug}, but that town does not offer the service"
        }
    }
}

private fun validateRandomNpcWildernessRect(
    character: NpcId,
    option: RandomNpcPlacementOptionModel.WildernessRect,
) {
    check(!option.x.isEmpty() && !option.y.isEmpty()) {
        "character ${character.slug} random wilderness placement has an empty coordinate range"
    }
    check(option.x.first >= 0 && option.y.first >= 0) {
        "character ${character.slug} random wilderness placement has a negative coordinate range"
    }
    option.xReplacements.forEach { (from, to) ->
        check(from >= 0 && to >= 0) {
            "character ${character.slug} random wilderness placement has a negative x replacement"
        }
    }
}

private const val TERMINAL_ESCAPE = '\u001B'

// A color marker colors its whole line, so it can only start one.
private fun checkLineColorStartsLine(
    line: TextLine,
    label: String,
) {
    check(line.parts.drop(1).none { part -> part is TextPart.LineColor }) {
        "$label has a color marker after its start; {red} and the other colors go at the start of a line or paragraph"
    }
}

private fun validateTextBlock(
    block: TextBlock,
    label: String,
    knownRoles: Set<NpcRole>? = null,
    knownNpcs: Set<NpcId>? = null,
    knownTowns: Set<TownId>? = null,
    allowedRuntimeBindings: Set<TextPart> = emptyTextBindings,
) {
    fun validatePart(part: TextPart) {
        when (part) {
            is TextPart.Literal ->
                check(
                    TERMINAL_ESCAPE !in part.text,
                ) { "$label has a terminal escape code; color a line with {red} or another color at its start" }
            is TextPart.IntValue -> Unit
            is TextPart.StringValue -> Unit
            is TextPart.Pause -> error("$label has a {pause} marker, but only writeText and showTextAndWait show text in beats")
            is TextPart.LineColor -> Unit
            is TextPart.RoleName -> {
                check(part.role.slug.isNotBlank()) { "$label references blank character role" }
                check(knownRoles == null || part.role in knownRoles) {
                    "$label references unbound character role ${part.role.slug}"
                }
            }
            is TextPart.RoleJob -> {
                check(part.role.slug.isNotBlank()) { "$label references blank character role" }
                check(knownRoles == null || part.role in knownRoles) {
                    "$label references unbound character role ${part.role.slug}"
                }
            }
            is TextPart.RoleLocation -> {
                check(part.role.slug.isNotBlank()) { "$label references blank character role" }
                check(knownRoles == null || part.role in knownRoles) {
                    "$label references unbound character role ${part.role.slug}"
                }
            }
            is TextPart.NpcName -> {
                check(part.npc.slug.isNotBlank()) { "$label references blank NPC" }
                check(knownNpcs == null || part.npc in knownNpcs) {
                    "$label references unknown NPC ${part.npc.slug}"
                }
            }
            is TextPart.NpcJob -> {
                check(part.npc.slug.isNotBlank()) { "$label references blank NPC" }
                check(knownNpcs == null || part.npc in knownNpcs) {
                    "$label references unknown NPC ${part.npc.slug}"
                }
            }
            is TextPart.NpcLocation -> {
                check(part.npc.slug.isNotBlank()) { "$label references blank NPC" }
                check(knownNpcs == null || part.npc in knownNpcs) {
                    "$label references unknown NPC ${part.npc.slug}"
                }
            }
            is TextPart.TownName -> {
                check(part.town.slug.isNotBlank()) { "$label references blank town" }
                check(knownTowns == null || part.town in knownTowns) {
                    "$label references unknown town ${part.town.slug}"
                }
            }
            is TextPart.FuzzyTownLocation -> {
                check(part.town.slug.isNotBlank()) { "$label references blank town" }
                check(part.radius >= 0) { "$label references negative fuzz radius ${part.radius}" }
                check(knownTowns == null || part.town in knownTowns) {
                    "$label references unknown town ${part.town.slug}"
                }
            }
            is TextPart.LocationCoordinate -> {
                check(part.location.slug.isNotBlank()) { "$label references blank location" }
            }
            // CPD-OFF
            TextPart.OfferedItemName,
            TextPart.OfferedItemPrice,
            TextPart.SelectedItemName,
            TextPart.InventorySlotName,
            TextPart.EquipmentKindName,
            TextPart.ShopPrice,
            TextPart.ShopCarryCap,
            TextPart.HuntMemberName,
            TextPart.HuntMemberSubjectPronoun,
            TextPart.HuntFoodGained,
            TextPart.UpkeepMemberName,
            TextPart.CampGuardName,
            TextPart.PartyMemberCount,
            TextPart.PartyMemberMax,
            TextPart.WildernessDungeonName,
            TextPart.WildernessDungeonLevels,
            TextPart.DungeonDepth,
            TextPart.ConversationNpcName,
            TextPart.CurrentTownName,
            TextPart.TownTaxPercent,
            TextPart.TownTaxAmount,
            TextPart.CreationMemberNumber,
            TextPart.CreationMemberName,
            TextPart.CreationWeaponSkill,
            TextPart.CreationHealth,
            TextPart.CreationWisdom,
            TextPart.CreationCharisma,
            TextPart.CreationDexterity,
            TextPart.BattleFoeDisplayName,
            TextPart.BattleAttackerName,
            TextPart.BattleTargetName,
            TextPart.BattleDamage,
            TextPart.BattleExperience,
            TextPart.BattleGold,
            TextPart.BattleLootName,
            TextPart.BattleLevelMemberName,
            TextPart.BattleLevelsGained,
            ->
                check(part in allowedRuntimeBindings) {
                    "$label uses runtime text binding ${part.fallbackText()}, but this text is not rendered with that binding"
                }
            // CPD-ON
        }
    }

    when (block) {
        is TextBlock.Inline -> {
            check(block.lines.isNotEmpty()) { "$label inline text has no lines" }
            check(block.lines.any { it.isNotBlank() }) { "$label inline text is blank" }
            check(block.lines.none { line -> TERMINAL_ESCAPE in line }) {
                "$label has a terminal escape code; color a line with {red} or another color at its start"
            }
        }
        is TextBlock.Structured -> {
            check(block.lines.isNotEmpty()) { "$label structured text has no lines" }
            check(
                block.lines.any { line ->
                    line.parts.any { it !is TextPart.Literal || it.text.isNotBlank() }
                },
            ) {
                "$label structured text is blank"
            }
            block.lines.forEachIndexed { index, line ->
                check(line.parts.isNotEmpty()) { "$label line $index has no parts" }
                checkLineColorStartsLine(line, "$label line $index")
                line.parts.forEach(::validatePart)
            }
        }
        is TextBlock.Flow -> {
            check(block.paragraphs.isNotEmpty()) { "$label flow text has no paragraphs" }
            check(
                block.paragraphs.any { line ->
                    line.parts.any { it !is TextPart.Literal || it.text.isNotBlank() }
                },
            ) {
                "$label flow text is blank"
            }
            block.paragraphs.forEachIndexed { index, line ->
                check(line.parts.isNotEmpty()) { "$label paragraph $index has no parts" }
                checkLineColorStartsLine(line, "$label paragraph $index")
                line.parts.forEach(::validatePart)
            }
        }
    }
}

/** Validates conversation menus, rumor tables, prompts, and NPC dialogue references. */
fun validateSpeakSystem(
    speak: SpeakSystemModel,
    characters: List<CharacterModel> = emptyList(),
    towns: List<TownModel> = emptyList(),
    flags: List<FlagRef>? = null,
) {
    val flagStates = flags?.let(::FlagStateIndex)
    validateSpeakTextTables(speak.textTables, characters, towns, flagStates)
    validateSpeakQuestionActions(speak.questionActions, characters)

    speak.standardConversation?.let { conversation ->
        check(conversation.npcSelection == ConversationNpcSelection.BuildingListOrWildernessLocation) {
            "standard conversation NPC selection must match building-list or wilderness-location lookup"
        }
        check(conversation.visitPolicy == NpcVisitPolicy.PerPartyVisitedBit) {
            "standard conversation visit policy must use per-party NPC visited bits"
        }
        check(conversation.branchSelection == DialogueBranchSelection.FirstMatchingStateThenDefault) {
            "standard conversation dialogue branch selection must be first matching state then default"
        }
        check(conversation.missionHints == MissionHintPolicy.BeforeEachMenu) {
            "standard conversation mission hints must run before each menu loop"
        }

        check(conversation.menu.actions.isNotEmpty()) { "standard conversation requires a top-level menu" }
        check(conversation.menu.questions.isNotEmpty()) { "standard conversation requires a question menu" }

        checkUniqueConversationActions(conversation.menu.actions)
        checkUniqueQuestionActions(conversation.menu.questions)

        val expectedTopics =
            listOf(
                ConversationTopic.Chitchat,
                ConversationTopic.Question,
                ConversationTopic.Rumor,
                ConversationTopic.Bye,
            )
        check(conversation.menu.actions.map { it.topic } == expectedTopics) {
            "standard conversation top-level menu must be C/Q/R/B chitchat, question, rumor, bye"
        }

        val expectedQuestionTopics =
            listOf(
                QuestionTopic.Lodging,
                QuestionTopic.Magic,
                QuestionTopic.People,
                QuestionTopic.Nothing,
            )
        check(conversation.menu.questions.map { it.topic } == expectedQuestionTopics) {
            "standard conversation question menu must be L/M/P/N lodging, magic, people, nothing"
        }

        check(conversation.menu.actions.map { it.key } == listOf('C', 'Q', 'R', 'B')) {
            "standard conversation top-level menu keys must match C/Q/R/B"
        }
        check(conversation.menu.questions.map { it.key } == listOf('L', 'M', 'P', 'N')) {
            "standard conversation question menu keys must match L/M/P/N"
        }

        check(
            conversation.chitchat.subjects ==
                listOf(
                    ChitchatSubject.Well,
                    ChitchatSubject.Weather,
                    ChitchatSubject.Health,
                ),
        ) {
            "standard conversation chitchat subjects must match well/weather/health roll"
        }
        check(conversation.chitchat.replies == ChitchatReplyPolicy.BySubjectAndNpcJob) {
            "standard conversation chitchat replies must dispatch by subject and NPC job"
        }

        check(conversation.rumor.usage == RumorUsagePolicy.OncePerConversationThenFallback) {
            "standard conversation rumors must be once per conversation with fallback text"
        }
        check(conversation.rumor.source == RumorSourcePolicy.StandardTownRumors) {
            "standard conversation rumors must use the standard town rumor source"
        }
        validateTextBlock(conversation.prompts.chooseNpc, "speak choose-npc prompt")
        validateTextBlock(
            conversation.prompts.mainMenu,
            "speak main-menu prompt",
            allowedRuntimeBindings = conversationTextBindings,
        )

        val services = conversation.nativeServices.map { it.service }
        check(services.toSet().size == services.size) {
            "standard conversation has duplicate native service"
        }
        listOf(
            EngineService.KeyboardInput,
            EngineService.Rendering,
            EngineService.RandomNumbers,
            EngineService.NpcStateStorage,
            EngineService.PartyStateMutation,
            EngineService.TextFormatting,
        ).forEach { service ->
            check(service in services) {
                "standard conversation missing native service $service"
            }
        }
    }
}

private fun validateSpeakQuestionActions(
    actions: List<QuestionActionScriptModel>,
    characters: List<CharacterModel>,
) {
    val knownJobs = characters.map { it.job }.toSet()
    check(actions.map { it.topic to it.job }.toSet().size == actions.size) {
        "duplicate speak question action"
    }
    actions.forEach { action ->
        check(action.job in knownJobs) {
            "speak question action ${action.topic} references unknown NPC job ${action.job.slug}"
        }
        check(action.statementNodes.isNotEmpty()) {
            "speak question action ${action.topic} for ${action.job.slug} has an empty body"
        }
    }
}

private fun validateSpeakTextTables(
    tables: SpeakTextTablesModel,
    characters: List<CharacterModel>,
    towns: List<TownModel>,
    flagStates: FlagStateIndex? = null,
) {
    val knownRoles = characters.flatMap { it.roles }.toSet()
    val knownNpcs = characters.map { it.id }.toSet()
    val knownTowns = towns.map { it.id }.toSet()

    check(
        tables.screens
            .map { it.id }
            .toSet()
            .size == tables.screens.size,
    ) {
        "duplicate reusable speak screen"
    }
    tables.screens.forEach { screen ->
        validateTextBlock(
            screen.text,
            "reusable speak screen ${screen.id.id}",
            knownRoles = knownRoles,
            knownNpcs = knownNpcs,
            knownTowns = knownTowns,
        )
    }

    check(
        tables.chitchatPrompts
            .map { it.subject }
            .toSet()
            .size == tables.chitchatPrompts.size,
    ) {
        "duplicate chitchat prompt table"
    }
    tables.chitchatPrompts.forEach { prompt ->
        check(prompt.variants.isNotEmpty()) { "chitchat prompt ${prompt.subject} has no variants" }
        check(
            prompt.variants
                .map { it.audience }
                .toSet()
                .size == prompt.variants.size,
        ) {
            "chitchat prompt ${prompt.subject} has duplicate audience variants"
        }
        prompt.variants.forEach { variant ->
            validateTextBlock(
                variant.text,
                "chitchat prompt ${prompt.subject}.${variant.audience}",
                knownRoles = knownRoles,
                knownNpcs = knownNpcs,
                knownTowns = knownTowns,
            )
        }
    }

    check(
        tables.chitchatReplies
            .map { it.subject }
            .toSet()
            .size == tables.chitchatReplies.size,
    ) {
        "duplicate chitchat reply table"
    }
    tables.chitchatReplies.forEach { replies ->
        validateJobReplies(replies.replies, "chitchat reply ${replies.subject}", knownRoles, knownNpcs, knownTowns)
    }

    check(
        tables.questionReplies
            .map { it.topic }
            .toSet()
            .size == tables.questionReplies.size,
    ) {
        "duplicate question reply table"
    }
    tables.questionReplies.forEach { replies ->
        check(replies.topic != QuestionTopic.Nothing) {
            "question reply table Nothing should not have canned replies"
        }
        validateJobReplies(replies.replies, "question reply ${replies.topic}", knownRoles, knownNpcs, knownTowns)
    }

    check(
        tables.rumorTables
            .map { it.id }
            .toSet()
            .size == tables.rumorTables.size,
    ) {
        "duplicate rumor table"
    }
    tables.rumorTables.forEach { table ->
        check(table.entries.isNotEmpty()) { "rumor table ${table.id.slug} has no entries" }
        val namedEntries = table.entries.mapNotNull { it.id }
        check(namedEntries.toSet().size == namedEntries.size) {
            "rumor table ${table.id.slug} has duplicate rumors"
        }
        table.entries.forEach { entry ->
            val label = entry.id?.slug ?: "anonymous"
            check(entry.excludedBy.toSet().size == entry.excludedBy.size) {
                "rumor $label has duplicate exclusion states"
            }
            entry.excludedBy.forEach { state ->
                check(state.flag.scope == FlagScope.Party) {
                    "rumor $label exclusion ${state.flag.slug}.${state.native} must be a party state"
                }
                flagStates?.requireKnown(state, "rumor $label exclusion")
            }
            validateTextBlock(entry.text, "rumor table ${table.id.slug}.$label", knownRoles, knownNpcs, knownTowns)
        }
    }
}

private fun validateJobReplies(
    replies: List<JobTextModel>,
    label: String,
    knownRoles: Set<NpcRole>? = null,
    knownNpcs: Set<NpcId>? = null,
    knownTowns: Set<TownId>? = null,
) {
    check(replies.isNotEmpty()) { "$label has no job replies" }
    check(replies.map { it.job }.toSet().size == replies.size) { "$label has duplicate job replies" }
    replies.forEach { reply ->
        check(reply.job.slug.isNotBlank()) { "$label references blank NPC job" }
        validateTextBlock(reply.text, "$label ${reply.job.slug}", knownRoles, knownNpcs, knownTowns)
    }
}

private fun checkUniqueConversationActions(actions: List<ConversationMenuActionModel>) {
    check(actions.map { it.topic }.toSet().size == actions.size) {
        "standard conversation has duplicate top-level menu topics"
    }
    actions.forEach { action ->
        validateInputChoiceBinding(action.binding, "standard conversation ${action.topic} key")
    }
    checkUniqueBindings(actions.map { it.binding }, "standard conversation top-level menu")
    check(actions.map { it.action }.toSet().size == actions.size) {
        "standard conversation has duplicate top-level menu actions"
    }
}

private fun checkUniqueQuestionActions(actions: List<QuestionMenuActionModel>) {
    check(actions.map { it.topic }.toSet().size == actions.size) {
        "standard conversation has duplicate question menu topics"
    }
    actions.forEach { action ->
        validateInputChoiceBinding(action.binding, "standard conversation question ${action.topic} key")
    }
    checkUniqueBindings(actions.map { it.binding }, "standard conversation question menu")
    check(actions.map { it.action }.toSet().size == actions.size) {
        "standard conversation has duplicate question menu actions"
    }
}

/** Validates authored item-use scripts against the item catalog. */
fun validateItemUses(
    itemUses: List<ItemUseModel>,
    itemRecords: List<ItemRecordModel>,
) {
    check(itemUses.map { it.id }.toSet().size == itemUses.size) { "duplicate item use" }

    val itemRecordById = itemRecords.associateBy { it.id }
    itemUses.forEach { itemUse ->
        val record = itemRecordById[itemUse.id]
        check(record?.storage == ItemStorageKind.SlottedInventory) {
            "item use ${itemUse.id.slug} must reference a slotted inventory item"
        }
        check(itemUse.contexts.isNotEmpty()) { "item use ${itemUse.id.slug} has no contexts" }
        check(itemUse.statementNodes.isNotEmpty()) { "item use ${itemUse.id.slug} has no effect body" }

        val consumesInBody = itemUse.containsConsumeActiveItem()
        when (itemUse.consume) {
            ItemConsumePolicy.BeforeUse ->
                check(!consumesInBody) {
                    "item use ${itemUse.id.slug} consumes before use and should not consume in script"
                }
            ItemConsumePolicy.ScriptControlled ->
                check(consumesInBody) {
                    "item use ${itemUse.id.slug} is script-controlled but never consumes active item"
                }
            ItemConsumePolicy.NeverConsumes ->
                check(!consumesInBody) {
                    "item use ${itemUse.id.slug} is non-consuming but consumes active item in script"
                }
        }
    }
}

/** Validates dialogue branches and their flag/NPC references. */
fun validateDialogue(
    dialogue: List<DialogueModel>,
    characters: List<CharacterModel> = emptyList(),
    flags: List<FlagRef>? = null,
) {
    val flagStates = flags?.let(::FlagStateIndex)
    check(dialogue.map { it.npc }.toSet().size == dialogue.size) { "duplicate authored NPC dialogue" }
    val characterIds = characters.map { it.id }.toSet()

    dialogue.forEach { npcDialogue ->
        check(characters.isEmpty() || npcDialogue.npc in characterIds) {
            "dialogue for ${npcDialogue.npc.slug} has no character definition"
        }
        check(npcDialogue.branches.isNotEmpty()) {
            "dialogue for ${npcDialogue.npc.slug} requires at least one branch"
        }

        val states = npcDialogue.branches.mapNotNull { it.whenState }
        check(states.toSet().size == states.size) {
            "dialogue for ${npcDialogue.npc.slug} has duplicate state branches"
        }

        check(npcDialogue.branches.count { it.whenState == null } <= 1) {
            "dialogue for ${npcDialogue.npc.slug} has duplicate default branches"
        }

        npcDialogue.branches.forEach { branch ->
            branch.whenState?.let { state ->
                check(state.flag.scope == FlagScope.Party) {
                    "dialogue for ${npcDialogue.npc.slug} uses non-party state ${state.flag.slug}.${state.native}"
                }
                flagStates?.requireKnown(state, "dialogue for ${npcDialogue.npc.slug}")
            }
            check(branch.statementNodes.isNotEmpty()) {
                "dialogue for ${npcDialogue.npc.slug} has an empty branch"
            }
        }
    }
}

private fun ItemUseModel.containsConsumeActiveItem(): Boolean =
    ScriptSourceRoot(
        id = ScriptSourceRootId(ScriptSourceRootKind.ItemUse, id.slug),
        script = script,
        label = "item use ${id.slug}",
    ).anyScriptStatement { statement -> statement == ScriptStatementNode.ConsumeActiveItem }

/** Validates reward and native-service use inside authored script bodies. */
fun validateScriptRewards(
    content: GameModel,
    itemRecords: List<ItemRecordModel>,
    flags: List<FlagRef>? = null,
) {
    val itemRecordById = itemRecords.associateBy { it.id }
    val flagStates = flags?.let(::FlagStateIndex)
    val knownRoles = content.characters.flatMap { it.roles }.toSet()
    val knownNpcs = content.characters.map { it.id }.toSet()
    val knownTowns = content.towns.map { it.id }.toSet()
    val knownScenes = content.scenes.map { it.id }.toSet()
    val knownMonsters = content.monsters.map { it.id }.toSet()
    val knownEncounterTiers = content.monsters.mapNotNull { it.tier }.toSet()
    val knownMilestones =
        content.progress.milestones
            .map { it.id }
            .toSet()
    val dungeonScenarios = content.dungeons.scenarios.associateBy { it.dungeon }

    fun validateSlottedItemPool(
        pool: ItemPoolId,
        label: String,
    ) {
        validateItemPool(pool, itemRecords)
        pool.items.forEach { item ->
            val record = itemRecordById[item]
            check(record?.storage == ItemStorageKind.SlottedInventory) {
                "$label item pool ${pool.slug} can only include slotted-inventory items"
            }
        }
    }

    lateinit var validateIntExpression: (IntAst, String) -> Unit
    lateinit var validateBoolExpression: (BoolAst, String) -> Unit

    validateIntExpression =

        fun(
            ast: IntAst,
            label: String,
        ) {
            when (ast) {
                is IntAst.Lit -> Unit
                is IntAst.Var -> Unit
                is IntAst.Binary -> {
                    validateIntExpression(ast.left, label)
                    validateIntExpression(ast.right, label)
                }
                is IntAst.If -> {
                    validateBoolExpression(ast.cond, label)
                    validateIntExpression(ast.thenValue, label)
                    validateIntExpression(ast.elseValue, label)
                }
                is IntAst.Random -> {
                    validateIntExpression(ast.lo, label)
                    validateIntExpression(ast.hi, label)
                }
                is IntAst.MemberField -> validateIntExpression(ast.member, label)
                is IntAst.MonsterField -> validateIntExpression(ast.monster, label)
                is IntAst.BattleFlag -> Unit
                is IntAst.BattleQuery -> Unit
                is IntAst.PartyMemberQuery -> Unit
                is IntAst.RealmScalar -> Unit
                is IntAst.PartyScalar -> Unit
                is IntAst.InventoryPoolCount -> validateSlottedItemPool(ast.pool, label)
                is IntAst.InventorySlotCost -> validateIntExpression(ast.slot, label)
            }
        }

    validateBoolExpression =

        fun(
            ast: BoolAst,
            label: String,
        ) {
            when (ast) {
                is BoolAst.Cmp -> {
                    validateIntExpression(ast.left, label)
                    validateIntExpression(ast.right, label)
                }
                is BoolAst.Not -> validateBoolExpression(ast.expr, label)
                is BoolAst.And -> {
                    validateBoolExpression(ast.left, label)
                    validateBoolExpression(ast.right, label)
                }
                is BoolAst.Or -> {
                    validateBoolExpression(ast.left, label)
                    validateBoolExpression(ast.right, label)
                }
                is BoolAst.FlagIs ->
                    flagStates?.requireKnown(ast.slug, ast.nativeValue, "$label condition")
                is BoolAst.MilestoneAchieved -> {
                    check(ast.milestone.slug.isNotBlank()) { "$label references blank milestone" }
                    check(ast.milestone in knownMilestones) {
                        "$label references unknown milestone ${ast.milestone.slug}"
                    }
                }
                is BoolAst.MemberFlag -> validateIntExpression(ast.member, label)
                is BoolAst.MonsterFlag -> validateIntExpression(ast.monster, label)
                is BoolAst.BattleFlag -> Unit
                is BoolAst.RuntimeFlag -> check(ast.name.isNotBlank()) { "$label references blank runtime flag" }
            }
        }

    fun validateReward(reward: ScriptRewardModel) {
        check(reward.gold != null || reward.items.isNotEmpty()) { "script reward has no gold or items" }
        reward.gold?.let { validateIntExpression(it, "script reward gold") }
        reward.items.forEach { item ->
            val record = itemRecordById[item]
            check(record != null) { "script reward references unknown item ${item.slug}" }
            check(record.storage != ItemStorageKind.EmptySentinel) {
                "script reward cannot grant the empty-slot sentinel"
            }
            check(record.storage == ItemStorageKind.SlottedInventory) {
                "script reward ${item.slug} must be a slotted inventory item; use achieve(...) for milestone-special items"
            }
        }
        if (reward.application.overflow == RewardOverflowPolicy.PromptDropThenPlace) {
            check(reward.messages.cantCarry != null) { "drop-prompt reward requires cant-carry text" }
        }
        reward.messages.cantCarry?.let { validateTextBlock(it, "reward cant-carry") }
        reward.messages.pickedUp?.let { validateTextBlock(it, "reward picked-up") }
        if (reward.messages.pickedUp != null) {
            check(reward.application.overflow == RewardOverflowPolicy.PromptDropThenPlace) {
                "picked-up reward text only applies to drop-prompt rewards"
            }
        }
    }

    fun validateRandomItemOffer(offer: RandomItemOfferModel) {
        validateSlottedItemPool(offer.pool, "random item offer")
        validateTextBlock(
            offer.offer,
            "random item offer ${offer.pool.slug} offer",
            allowedRuntimeBindings = randomItemOfferTextBindings,
        )
        validateTextBlock(offer.choicePrompt, "random item offer ${offer.pool.slug} choice prompt")
        validateTextBlock(offer.broke, "random item offer ${offer.pool.slug} broke")
        check(offer.application.regularItems == RewardItemPlacement.FirstFreeInventorySlot) {
            "random item offer ${offer.pool.slug} only supports first-free-slot placement for now"
        }
        val cantCarry =
            checkNotNull(offer.messages.cantCarry) {
                "random item offer ${offer.pool.slug} requires full/cant-carry text"
            }
        validateTextBlock(cantCarry, "random item offer ${offer.pool.slug} full")
        offer.messages.pickedUp?.let { validateTextBlock(it, "random item offer ${offer.pool.slug} picked-up") }
        if (offer.messages.pickedUp != null) {
            check(offer.application.overflow == RewardOverflowPolicy.PromptDropThenPlace) {
                "random item offer ${offer.pool.slug} picked-up text only applies to drop-prompt overflow"
            }
        }
        validateTextBlock(offer.deal, "random item offer ${offer.pool.slug} deal")
        offer.declined?.let { validateTextBlock(it, "random item offer ${offer.pool.slug} declined") }
        offer.priceOverride?.let { price ->
            check(price >= 0) { "random item offer ${offer.pool.slug} price override must be non-negative" }
        }
        validateInputChoiceBinding(offer.buy, "random item offer ${offer.pool.slug} buy key")
        validateInputChoiceBinding(offer.decline, "random item offer ${offer.pool.slug} decline key")
        check(offer.buyKey.isLetter()) { "random item offer ${offer.pool.slug} buy key must be a letter" }
        check(offer.declineKey.isLetter()) { "random item offer ${offer.pool.slug} decline key must be a letter" }
        checkUniqueBindings(listOf(offer.buy, offer.decline), "random item offer ${offer.pool.slug}")
        listOf(
            "broke" to offer.brokePauseMillis,
            "full" to offer.fullPauseMillis,
            "deal" to offer.dealPauseMillis,
            "declined" to offer.declinedPauseMillis,
        ).forEach { (name, millis) ->
            check(millis >= 0) { "random item offer ${offer.pool.slug} $name pause is negative" }
        }
    }

    fun validateShopPrice(
        price: ShopPriceModel,
        label: String,
    ) {
        when (price) {
            ShopPriceModel.ItemCost,
            ShopPriceModel.HalfItemCost,
            -> Unit
            is ShopPriceModel.Fixed -> check(price.amount >= 0) { "$label fixed price is negative" }
            is ShopPriceModel.LeaderLevelPlus ->
                check(price.addend >= 0) {
                    "$label leader-level addend is negative"
                }
            is ShopPriceModel.ItemCostPlusLeaderLevelTimes ->
                check(price.multiplier >= 0) {
                    "$label leader-level multiplier is negative"
                }
        }
    }

    fun validateShop(shop: ShopServiceModel) {
        check(shop.building.slug.isNotBlank()) { "shop service references blank building" }
        validateTextBlock(shop.closed, "shop ${shop.building.slug} closed")
        validateTextBlock(shop.intro, "shop ${shop.building.slug} intro")
        validateTextBlock(shop.menu, "shop ${shop.building.slug} menu")
        validateTextBlock(shop.goodbye, "shop ${shop.building.slug} goodbye")
        val shopMenuKeys =
            buildList {
                if (shop.buy != null) add(shop.keys.buy)
                if (shop.sell != null) add(shop.keys.sell)
                add(shop.keys.leave)
            }
        shopMenuKeys.forEach { binding -> validateInputChoiceBinding(binding, "shop ${shop.building.slug} menu key") }
        checkUniqueBindings(shopMenuKeys, "shop ${shop.building.slug} menu")

        shop.interlude?.let { interlude ->
            checkPercent(interlude.chancePercent, "shop ${shop.building.slug} interlude chance")
            validateInputChoiceBinding(interlude.accept, "shop ${shop.building.slug} interlude accept key")
            validateInputChoiceBinding(interlude.decline, "shop ${shop.building.slug} interlude decline key")
            checkUniqueBindings(listOf(interlude.accept, interlude.decline), "shop ${shop.building.slug} interlude")
            validateTextBlock(interlude.offer, "shop ${shop.building.slug} interlude offer")
            check(interlude.accepted.isNotEmpty()) {
                "shop ${shop.building.slug} interlude accepted branch is empty"
            }
            interlude.accepted.forEachIndexed { index, text ->
                validateTextBlock(text, "shop ${shop.building.slug} interlude accepted $index")
            }
            validateTextBlock(interlude.declined, "shop ${shop.building.slug} interlude declined")
            if (interlude.runsRumorAfterAccept) {
                check(content.speak.standardConversation != null) {
                    "shop ${shop.building.slug} interlude runs rumor but standard conversation is not authored"
                }
                check(
                    content.speak.textTables.rumorTables
                        .any { table -> table.id == DslDefaultRumorTables.standardTown },
                ) {
                    "shop ${shop.building.slug} interlude runs rumor but standard town rumor table is not authored"
                }
                check(
                    content.speak.textTables.screens
                        .any { screen -> screen.id == speakNoRumorScreen },
                ) {
                    "shop ${shop.building.slug} interlude runs rumor but no-rumor screen is not authored"
                }
            }
            interlude.forceFirstUntil?.let { state ->
                flagStates?.requireKnown(state, "shop ${shop.building.slug} interlude first-offer flag")
            }
        }

        shop.buy?.let { buy ->
            validateTextBlock(buy.prompt, "shop ${shop.building.slug} buy prompt")
            validateTextBlock(buy.tooExpensive, "shop ${shop.building.slug} too-expensive")
            validateTextBlock(buy.cantCarry, "shop ${shop.building.slug} cant-carry")
            validateTextBlock(buy.deal, "shop ${shop.building.slug} deal")
            buy.bought?.let {
                validateTextBlock(
                    it,
                    "shop ${shop.building.slug} bought",
                    allowedRuntimeBindings = selectedItemTextBindings,
                )
            }
            buy.unitPrice?.let {
                validateTextBlock(
                    it,
                    "shop ${shop.building.slug} unit-price",
                    allowedRuntimeBindings = shopPriceTextBindings,
                )
            }
            buy.singlePrice?.let {
                validateTextBlock(
                    it,
                    "shop ${shop.building.slug} single-price",
                    allowedRuntimeBindings = shopPriceTextBindings,
                )
            }
            validateTextBlock(buy.payPrompt, "shop ${shop.building.slug} pay prompt")
            validateInputChoiceBinding(buy.paymentKeys.pay, "shop ${shop.building.slug} pay key")
            check(buy.catalogs.isNotEmpty()) { "shop ${shop.building.slug} buy section has no catalogs" }
            val buyOffers = buy.catalogs.flatMap { catalog -> catalog.pages.flatMap { page -> page.entries } }
            check(buy.unitPrice != null || buyOffers.none { offer -> offer.purchase is ShopPurchaseModel.Bulk }) {
                "shop ${shop.building.slug} bulk purchases must author unit-price text"
            }
            check(buy.singlePrice != null || buyOffers.none { offer -> offer.purchase == ShopPurchaseModel.InventoryItem }) {
                "shop ${shop.building.slug} inventory purchases must author single-price text"
            }
            check(buy.bought != null || buyOffers.none { offer -> offer.purchase == ShopPurchaseModel.InventoryItem }) {
                "shop ${shop.building.slug} inventory purchases must author bought text"
            }
            buy.catalogs.forEach { catalog ->
                validateInputChoiceBinding(catalog.binding, "shop ${shop.building.slug} catalog ${catalog.title} key")
            }
            checkUniqueBindings(buy.catalogs.map { it.binding }, "shop ${shop.building.slug} buy catalogs")
            buy.catalogs.forEach { catalog ->
                check(catalog.title.isNotBlank()) {
                    "shop ${shop.building.slug} catalog ${catalog.key} has blank title"
                }
                catalog.intro?.let { validateTextBlock(it, "shop ${shop.building.slug} ${catalog.title} intro") }
                check(catalog.pages.isNotEmpty()) {
                    "shop ${shop.building.slug} catalog ${catalog.title} has no pages"
                }
                catalog.pages.forEachIndexed { pageIndex, page ->
                    check(page.entries.isNotEmpty()) {
                        "shop ${shop.building.slug} catalog ${catalog.title} page $pageIndex has no entries"
                    }
                    page.navigation.previous?.let { binding ->
                        validateInputChoiceBinding(
                            binding,
                            "shop ${shop.building.slug} catalog ${catalog.title} page $pageIndex previous key",
                        )
                        check(pageIndex > 0) {
                            "shop ${shop.building.slug} catalog ${catalog.title} first page cannot have previous navigation"
                        }
                    }
                    page.navigation.next?.let { binding ->
                        validateInputChoiceBinding(
                            binding,
                            "shop ${shop.building.slug} catalog ${catalog.title} page $pageIndex next key",
                        )
                        check(pageIndex < catalog.pages.lastIndex) {
                            "shop ${shop.building.slug} catalog ${catalog.title} last page cannot have next navigation"
                        }
                    }
                    page.entries.forEach { entry ->
                        validateInputChoiceBinding(
                            entry.binding,
                            "shop ${shop.building.slug} catalog ${catalog.title} item ${entry.item.slug} key",
                        )
                    }
                    checkUniqueBindings(
                        page.entries.map { it.binding } +
                            listOfNotNull(page.navigation.previous, page.navigation.next),
                        "shop ${shop.building.slug} catalog ${catalog.title} page $pageIndex",
                    )
                    page.footer?.let {
                        validateTextBlock(it, "shop ${shop.building.slug} ${catalog.title} page $pageIndex footer")
                    }
                    page.entries.forEach { entry ->
                        val record =
                            checkNotNull(itemRecordById[entry.item]) {
                                "shop ${shop.building.slug} catalog ${catalog.title} references unknown item ${entry.item.slug}"
                            }
                        check(record.storage != ItemStorageKind.EmptySentinel) {
                            "shop ${shop.building.slug} cannot sell the empty-slot sentinel"
                        }
                        entry.description?.let {
                            validateTextBlock(it, "shop ${shop.building.slug} ${entry.item.slug} description")
                        }
                        validateShopPrice(entry.price, "shop ${shop.building.slug} ${entry.item.slug}")
                        when (val purchase = entry.purchase) {
                            ShopPurchaseModel.InventoryItem ->
                                check(record.storage == ItemStorageKind.SlottedInventory) {
                                    "shop ${shop.building.slug} inventory item ${entry.item.slug} must be slotted"
                                }
                            is ShopPurchaseModel.Bulk -> {
                                validateTextBlock(
                                    purchase.quantityPrompt,
                                    "shop ${shop.building.slug} ${entry.item.slug} quantity prompt",
                                )
                                validateTextBlock(
                                    purchase.tooMany,
                                    "shop ${shop.building.slug} ${entry.item.slug} too-many",
                                    allowedRuntimeBindings = shopCapacityTextBindings,
                                )
                            }
                        }
                    }
                }
            }
        }

        shop.sell?.let { sell ->
            validateTextBlock(sell.prompt, "shop ${shop.building.slug} sell prompt")
            validateTextBlock(sell.wrongKind, "shop ${shop.building.slug} wrong-kind")
            validateTextBlock(
                sell.offer,
                "shop ${shop.building.slug} sell offer",
                allowedRuntimeBindings = shopPriceTextBindings,
            )
            sell.declined?.let { validateTextBlock(it, "shop ${shop.building.slug} sell declined") }
            sell.sold?.let { validateTextBlock(it, "shop ${shop.building.slug} sold") }
            check(sell.declined != null) { "shop ${shop.building.slug} sell section must author declined text" }
            check(sell.sold != null) { "shop ${shop.building.slug} sell section must author sold text" }
            validateShopPrice(sell.price, "shop ${shop.building.slug} sell")
            sell.haggle?.rollBonus?.let { formula ->
                check(formula in content.formulas.map { it.id }) {
                    "shop ${shop.building.slug} haggle roll bonus references missing formula ${formula.slug}"
                }
            }
            validateInputChoiceBinding(sell.saleKeys.accept, "shop ${shop.building.slug} sale accept key")
            validateInputChoiceBinding(sell.saleKeys.decline, "shop ${shop.building.slug} sale decline key")
            sell.haggle?.let {
                validateInputChoiceBinding(sell.saleKeys.haggle, "shop ${shop.building.slug} sale haggle key")
            }
            checkUniqueBindings(
                listOf(sell.saleKeys.accept, sell.saleKeys.decline) +
                    listOfNotNull(sell.saleKeys.haggle.takeIf { sell.haggle != null }),
                "shop ${shop.building.slug} sale confirmation",
            )
            check(sell.acceptedKinds.isNotEmpty() || sell.acceptedItems.isNotEmpty()) {
                "shop ${shop.building.slug} sell policy accepts nothing"
            }
            sell.acceptedItems.plus(sell.rejectedItems).forEach { item ->
                check(item in itemRecordById) {
                    "shop ${shop.building.slug} sell policy references unknown item ${item.slug}"
                }
            }
            check(ItemKind.Empty !in sell.acceptedKinds) {
                "shop ${shop.building.slug} sell policy cannot accept empty items"
            }
            check(ItemKind.Special !in sell.acceptedKinds) {
                "shop ${shop.building.slug} sell policy cannot accept milestone-special items"
            }
        }
    }

    fun requireEncounterTier(
        tier: EncounterTier,
        label: String,
    ) {
        check(tier.slug.isNotBlank()) { "$label references blank encounter tier" }
        check(tier in knownEncounterTiers) { "$label tier ${tier.slug} has no authored monsters" }
    }

    fun requireDungeonScenario(
        dungeon: DungeonId,
        label: String,
        levels: Int? = null,
    ) {
        check(dungeon.slug.isNotBlank()) { "$label references blank dungeon" }
        val scenario = dungeonScenarios[dungeon]
        check(scenario != null) { "$label references missing dungeon scenario ${dungeon.slug}" }
        levels?.let { requested ->
            scenario.levelRange?.let { range ->
                check(requested <= range.last) {
                    "$label requests $requested levels, but dungeon ${dungeon.slug} only declares through level ${range.last}"
                }
            }
        }
    }

    content.scriptSourceRoots().forEach { root ->
        root.visitScriptStatements { sourceStatement ->
            when (val statement = sourceStatement.statement) {
                is ScriptStatementNode.Screen -> validateTextBlock(statement.text, "screen", knownRoles, knownNpcs, knownTowns)
                is ScriptStatementNode.LetInt -> validateIntExpression(statement.value, "local int")
                is ScriptStatementNode.Set -> validateIntExpression(statement.value, "state assignment")
                is ScriptStatementNode.Add -> validateIntExpression(statement.value, "state add")
                is ScriptStatementNode.Sub -> validateIntExpression(statement.value, "state subtract")
                is ScriptStatementNode.AddClamped -> {
                    validateIntExpression(statement.value, "state clamped add")
                    validateIntExpression(statement.max, "state clamped add max")
                }
                is ScriptStatementNode.AppendJournal ->
                    validateTextBlock(
                        statement.text,
                        "journal append",
                        knownRoles,
                        knownNpcs,
                        knownTowns,
                    )
                is ScriptStatementNode.Menu -> {
                    validateTextBlock(statement.prompt, "menu prompt")
                    statement.options.forEach { option ->
                        validateInputChoiceBinding(option.binding, "script menu option ${option.key}")
                    }
                    checkUniqueBindings(statement.options.map { it.binding }, "script menu")
                }
                is ScriptStatementNode.AskKey -> {
                    validateTextBlock(statement.prompt, "key choice prompt")
                    check(statement.bindings.isNotEmpty()) { "key choice prompt must accept at least one key" }
                    statement.bindings.forEach { binding ->
                        validateInputChoiceBinding(binding, "key choice ${binding.label}")
                    }
                    checkUniqueBindings(statement.bindings, "key choice prompt")
                }
                is ScriptStatementNode.AskMember ->
                    validateTextBlock(
                        statement.prompt,
                        "member choice prompt",
                        allowedRuntimeBindings = memberChoiceTextBindings,
                    )
                is ScriptStatementNode.AskInventorySlot -> {
                    validateTextBlock(statement.prompt, "inventory-slot choice prompt")
                    check(statement.firstKey.isLetter()) { "inventory-slot choice first key must be a letter" }
                    check(statement.slots.first >= 0) { "inventory-slot choice starts before slot 0" }
                    check(statement.slots.last <= INVENTORY_SAVE_SLOT_MAX) {
                        "inventory-slot choice ends after slot $INVENTORY_SAVE_SLOT_MAX"
                    }
                }
                is ScriptStatementNode.AskText -> {
                    validateTextBlock(statement.prompt, "text input prompt")
                    check(statement.accepted.isNotEmpty()) { "text input prompt must accept at least one value" }
                    check(statement.accepted.none { it.isEmpty() }) { "text input prompt cannot accept an empty value" }
                    statement.maxLength?.let { max ->
                        check(max > 0) { "text input max length must be positive" }
                    }
                }
                is ScriptStatementNode.GrantReward -> validateReward(statement.reward)
                is ScriptStatementNode.RandomItemOffer -> validateRandomItemOffer(statement.offer)
                is ScriptStatementNode.ShopService -> validateShop(statement.shop)
                is ScriptStatementNode.SetFlag -> flagStates?.requireKnown(statement.slug, statement.nativeValue, "script state change")
                is ScriptStatementNode.ConsumeInventoryPool -> {
                    validateSlottedItemPool(statement.pool, "consume inventory")
                    validateIntExpression(statement.quantity, "consume inventory quantity")
                    (statement.quantity as? IntAst.Lit)?.let { quantity ->
                        check(quantity.value > 0) { "consume inventory quantity must be positive" }
                    }
                }
                is ScriptStatementNode.ClearInventorySlot -> validateIntExpression(statement.slot, "clear inventory slot")
                is ScriptStatementNode.EnterDungeon -> {
                    check(statement.levels > 0) { "script dungeon handoff must use positive level count" }
                    requireDungeonScenario(statement.dungeon, "script dungeon handoff", statement.levels)
                }
                is ScriptStatementNode.TeleportParty -> {
                    validateIntExpression(statement.directionKey, "teleport direction key")
                    validateIntExpression(statement.distance, "teleport distance")
                }
                is ScriptStatementNode.DiscoverCave -> {
                    validateIntExpression(statement.levels, "discover cave level count")
                    validateIntExpression(statement.moveCost, "discover cave move cost")
                    val literalLevels = (statement.levels as? IntAst.Lit)?.value
                    literalLevels?.let { check(it > 0) { "discover cave level count must be positive" } }
                    requireDungeonScenario(statement.dungeon, "discover cave", literalLevels)
                }
                is ScriptStatementNode.If -> validateBoolExpression(statement.cond, "script condition")
                is ScriptStatementNode.Break ->
                    check(sourceStatement.loopDepth > 0) {
                        "breakLoop(${statement.reason ?: ""}) must be inside loop { ... }"
                    }
                is ScriptStatementNode.Switch -> validateIntExpression(statement.expr, "script switch")
                is ScriptStatementNode.RunScene ->
                    check(statement.scene in knownScenes) {
                        "script run references missing scene ${statement.scene.slug}"
                    }
                is ScriptStatementNode.Encounter -> {
                    check(statement.foe in knownMonsters) {
                        "script encounter references unknown monster ${statement.foe.slug}"
                    }
                    statement.tier?.let { tier -> requireEncounterTier(tier, "script encounter ${statement.foe.slug}") }
                }
                is ScriptStatementNode.AchieveMilestone ->
                    check(statement.milestone in knownMilestones) {
                        "script achieve references unknown milestone ${statement.milestone.slug}"
                    }
                else -> Unit
            }
        }
    }

    val unread = content.unreadScriptTexts()
    check(unread.isEmpty()) {
        "scripts show text the player never gets to read; hold it with waitInput, a pause or a menu first, " +
            "or keep it on screen with appendText:\n" + unread.joinToString(separator = "\n")
    }
}

private fun validateItemPool(
    pool: ItemPoolId,
    itemRecords: List<ItemRecordModel>,
) {
    check(pool.items.isNotEmpty()) { "item pool ${pool.slug} has no items" }
    check(pool.items.toSet().size == pool.items.size) { "item pool ${pool.slug} has duplicate items" }

    val itemRecordById = itemRecords.associateBy { it.id }
    pool.items.forEach { item ->
        val record = itemRecordById[item]
        check(record != null) { "item pool ${pool.slug} references unknown item ${item.slug}" }
        check(record.storage != ItemStorageKind.EmptySentinel) {
            "item pool ${pool.slug} cannot include the empty-slot sentinel"
        }
    }
}

private fun checkPercent(
    value: Int,
    label: String,
) {
    check(value in 0..100) { "$label must be 0..100" }
}

private fun validateRealmContent(realm: RealmContentModel) {
    realm.baseResourceCapacity.forEach { (resource, amount) ->
        check(amount >= 0) { "base ${resource.name.lowercase()} capacity must be non-negative" }
    }
    checkPercent(realm.upkeep.manaRegenChance, "wilderness mana regenerate chance")
    validateTextBlock(realm.upkeep.starvationDeath, "wilderness starvation death", allowedRuntimeBindings = upkeepTextBindings)
    realm.upkeep.fatigue.forEachIndexed { index, rule ->
        validateTextBlock(rule.text, "wilderness fatigue rule $index")
    }
    check(realm.timeOfDay.isNotEmpty()) { "realm time-of-day bands must not be empty" }
    realm.timeOfDay.forEachIndexed { index, band ->
        check(band.hours.first in 0..23 && band.hours.last in 0..23) {
            "realm time-of-day band $index hours must be within 0..23"
        }
        check(band.hours.first <= band.hours.last) { "realm time-of-day band $index has reversed hours" }
        check(band.label.isNotBlank()) { "realm time-of-day band $index label must not be blank" }
    }
    validateCampRules(realm.camp)
    val hunt = checkNotNull(realm.hunt) { "realm hunt rules are required by the exported wilderness hunt command" }
    validateHuntRules(hunt)
}

private fun validateCharacterCreation(
    model: CharacterCreationModel?,
    worldMaps: List<WorldMapModel>,
) {
    if (model == null) return

    check(model.races.isNotEmpty()) { "character creation needs at least one race option" }
    check(
        model.races
            .map { it.race }
            .toSet()
            .size == model.races.size,
    ) {
        "character creation has duplicate race options"
    }
    model.races.forEach { option ->
        check(option.label.isNotBlank()) { "character creation race ${option.race.name} has blank label" }
    }

    check(model.classes.isNotEmpty()) { "character creation needs at least one class option" }
    check(
        model.classes
            .map { it.klass }
            .toSet()
            .size == model.classes.size,
    ) {
        "character creation has duplicate class options"
    }
    model.classes.forEach { option ->
        check(option.label.isNotBlank()) { "character creation class ${option.klass.name} has blank label" }
        option.failureText?.let { validateTextBlock(it, "character creation ${option.klass.name} failure text") }
    }
    if (model.prerolledMembers.isNotEmpty()) {
        check(model.prerolledMembers.size == model.start.partyMemberMax) {
            "pre-rolled party must contain exactly ${model.start.partyMemberMax} members"
        }
        model.prerolledMembers.forEach { member ->
            check(member.name.isNotBlank() && member.name.length <= 24 && member.name.none(Char::isISOControl)) {
                "pre-rolled member name must contain 1 to 24 printable characters"
            }
            check(model.races.any { it.race == member.race }) {
                "pre-rolled member ${member.name} uses an unavailable race"
            }
            val option =
                checkNotNull(model.classes.find { it.klass == member.klass }) {
                    "pre-rolled member ${member.name} uses an unavailable class"
                }
            check(option.minimum == null || option.minimum <= model.statRoll.max) {
                "pre-rolled member ${member.name} cannot meet the class stat requirement within the roll range"
            }
        }
    }
    check(
        model.portraits
            .map { portrait -> Triple(portrait.gender, portrait.race, portrait.klass) }
            .toSet()
            .size == model.portraits.size,
    ) {
        "character creation has duplicate portrait selectors"
    }

    check(model.start.resources.gold >= 0) { "character creation starting gold must be non-negative" }
    check(model.start.resources.torches >= 0) { "character creation starting torches must be non-negative" }
    check(model.start.resources.food >= 0) { "character creation starting food must be non-negative" }
    check(model.start.resources.timeLeft >= 0) { "character creation starting time left must be non-negative" }
    check(model.start.resources.restedHours >= 0) { "character creation starting rested hours must be non-negative" }
    check(model.start.map in worldMaps.map { it.id }.toSet()) {
        "character creation start map ${model.start.map.slug} is not declared"
    }
}

private fun validateCampRules(camp: CampRulesModel) {
    check(camp.healthGain >= 0) { "camp health gain must be non-negative" }
    check(camp.foodCostPerMember >= 0) { "camp food cost must be non-negative" }
    check(camp.hoursAdvanced >= 0) { "camp hours advanced must be non-negative" }
    check(camp.hoursRestedReset >= 0) { "camp rested-hours reset must be non-negative" }
    checkPercent(camp.ambushChancePercent, "camp ambush chance")
    listOf(
        "not tired" to camp.notTired,
        "not enough food" to camp.notEnoughFood,
        "guard prompt" to camp.guardPrompt,
        "guard member dead" to camp.guardMemberDead,
        "sleeping" to camp.sleeping,
        "rested" to camp.rested,
        "ambush" to camp.ambush,
        "surprise kill" to camp.surpriseKill,
    ).forEach { (label, block) ->
        validateTextBlock(block, "camp $label")
    }
    validateTextBlock(
        camp.guardMemberPrompt,
        "camp guard member prompt",
        allowedRuntimeBindings = memberChoiceTextBindings,
    )
    validateTextBlock(camp.standsGuard, "camp stands guard", allowedRuntimeBindings = setOf(TextPart.CampGuardName))
    camp.temperatureMessage.forEach { (band, block) ->
        validateTextBlock(block, "camp ${band.name.lowercase()} temperature message")
    }
    camp.noShelterMessage.forEach { (band, block) ->
        validateTextBlock(block, "camp ${band.name.lowercase()} no-shelter message")
    }
}

private fun validateHuntRules(hunt: HuntRulesModel) {
    validateTextBlock(hunt.whoPrompt, "hunt who prompt", allowedRuntimeBindings = huntTextBindings)
    validateTextBlock(hunt.dead, "hunt dead")
    validateTextBlock(hunt.notRanger, "hunt not-ranger", allowedRuntimeBindings = huntTextBindings)
    validateTextBlock(hunt.tooFewMoves, "hunt too-few-moves")
    validateTextBlock(hunt.tries, "hunt tries", allowedRuntimeBindings = huntTextBindings)
    validateTextBlock(hunt.succeeds, "hunt succeeds", allowedRuntimeBindings = huntTextBindings)
    validateTextBlock(hunt.fails, "hunt fails", allowedRuntimeBindings = huntTextBindings)
}

private fun validateTerminalPresentation(
    model: TerminalPresentationModel,
    terrain: TerrainCatalogModel,
) {
    check(
        model.terrain
            .map { it.terrain }
            .toSet()
            .size == model.terrain.size,
    ) {
        "duplicate terminal terrain style"
    }
    val sourceMapCodes = model.terrain.mapNotNull { it.terrain.sourceMapCode }
    check(sourceMapCodes.toSet().size == sourceMapCodes.size) {
        "duplicate terminal terrain map tile code"
    }
    model.terrain.forEach { style ->
        check(style.terrain.slug.isNotBlank()) { "terminal terrain has blank id" }
        check(style.terrain.sourceMapCode != null) {
            "terminal terrain ${style.terrain.slug} has no source map code"
        }
        check(terrain.terrain.any { it.id.slug == style.terrain.slug }) {
            "terminal terrain ${style.terrain.slug} is not declared in terrain { ... }"
        }
        check(style.glyph.isNotEmpty()) { "terminal terrain ${style.terrain.slug} has blank glyph" }
        check(style.color.slug.isNotBlank()) { "terminal terrain ${style.terrain.slug} has blank color" }
    }
}

private fun validateTerrainCatalog(model: TerrainCatalogModel) {
    check(
        model.terrain
            .map { it.id }
            .toSet()
            .size == model.terrain.size,
    ) {
        "duplicate terrain declaration"
    }
    check(
        model.terrain
            .map { it.mapCode }
            .toSet()
            .size == model.terrain.size,
    ) {
        "duplicate terrain map tile code"
    }
    model.terrain.forEach { terrain ->
        check(terrain.id.slug.isNotBlank()) { "terrain has blank id" }
        check(terrain.id.sourceMapCode == null || terrain.id.sourceMapCode == terrain.mapCode) {
            "terrain ${terrain.id.slug} handle map tile '${terrain.id.sourceMapCode}' does not match declaration '${terrain.mapCode}'"
        }
        check(terrain.movementCost >= 0) { "terrain ${terrain.id.slug} movement cost must be non-negative" }
        check(terrain.passable || terrain.movementCost == 0) {
            "blocked terrain ${terrain.id.slug} must have movement cost 0"
        }
        check(terrain.settlement == null || !terrain.allowsNpcPlacement) {
            "settlement terrain ${terrain.id.slug} cannot allow random NPC placement"
        }
    }
}

private fun validateLayouts(layouts: LayoutModel) {
    check(
        layouts.regions
            .map { it.id }
            .toSet()
            .size == layouts.regions.size,
    ) {
        "duplicate screen region"
    }
    layouts.regions.forEach { region ->
        check(region.x >= 0 && region.y >= 0) { "screen region ${region.id.slug} has negative coordinates" }
        region.width?.let { check(it > 0) { "screen region ${region.id.slug} has non-positive width" } }
        region.height?.let { check(it > 0) { "screen region ${region.id.slug} has non-positive height" } }
    }
}

private fun validateVisuals(visuals: VisualsModel) {
    check(
        visuals.images
            .map { it.id to it.kind }
            .toSet()
            .size == visuals.images.size,
    ) {
        "duplicate visual image asset"
    }
    visuals.images.forEach { image ->
        check(image.id.slug.isNotBlank()) { "visual image has blank id" }
        validateRelativeResource(image.resource, "visual image ${image.id.slug}")
    }
    check(
        visuals.staticScreens
            .map { it.id }
            .toSet()
            .size == visuals.staticScreens.size,
    ) {
        "duplicate static screen"
    }
    val imageIds = visuals.images.map { it.id }.toSet()
    visuals.staticScreens.forEach { screen ->
        val sourceCount =
            listOf(
                screen.image != null,
                screen.resource != null,
                screen.commandHelp,
                screen.runtimeReport != null,
            ).count { it }
        check(sourceCount == 1) {
            "static screen ${screen.id.slug} must have exactly one source"
        }
        screen.image?.let { image ->
            check(image in imageIds) { "static screen ${screen.id.slug} references undeclared image ${image.slug}" }
        }
        screen.resource?.let { resource ->
            validateRelativeResource(resource, "static screen ${screen.id.slug}")
        }
    }
}

private fun validateRelativeResource(
    resource: String,
    label: String,
) {
    check(resource.isNotBlank()) { "$label has blank resource" }
    check(!resource.startsWith("/")) { "$label resource must be relative" }
    check(resource.split('/').none { it == ".." }) {
        "$label resource must stay inside packaged resources"
    }
}

private fun validateImageReferences(content: GameModel) {
    val declared =
        content.visuals.images
            .map { it.id }
            .toSet()
    val references =
        buildList {
            content.visuals.staticScreens.forEach { screen ->
                screen.image?.let { add(it to "static screen ${screen.id.slug}") }
            }
            content.itemCatalog.forEach { item ->
                item.campImage?.let { add(it to "item ${item.id.slug} camp image") }
            }
            content.wilderness.events.caveImage
                ?.let { add(it to "wilderness cave image") }
            content.dungeons.backdrop?.let { add(it to "dungeon backdrop") }
            content.castles.forEach { castle ->
                castle.image?.let { add(it to "castle ${castle.id.slug} backdrop") }
                castle.text.pitfallIllustration?.let { add(it to "castle ${castle.id.slug} pitfall illustration") }
                castle.text.treasureIllustration?.let { add(it to "castle ${castle.id.slug} treasure illustration") }
                castle.text.stairsIllustration?.let { add(it to "castle ${castle.id.slug} stairs illustration") }
            }
            content.combat.standardBattle?.let { battle ->
                battle.opening.banners.forEach { add(it to "battle opening banner") }
                battle.victoryImage?.let { add(it to "battle victory image") }
                battle.defeatImage?.let { add(it to "battle defeat image") }
            }
            content.monsters.forEach { monster ->
                monster.encounterImage?.let { add(it to "monster ${monster.id.slug} encounter image") }
            }
            content.characterCreation?.portraits?.forEach { portrait ->
                add(portrait.image to "character creation portrait")
            }
            content.scriptSourceRoots().forEach { root -> addAll(root.imageReferences()) }
        }
    references.forEach { (image, label) ->
        check(image in declared) { "$label references undeclared image ${image.slug}" }
    }
}

private val audioResourceExtensions = setOf("mp3", "ogg", "wav", "m4a")

private fun validateAudio(content: GameModel) {
    val audio = content.audio

    fun <T> checkUnique(
        values: List<T>,
        label: String,
    ) {
        val duplicates =
            values
                .groupingBy { it }
                .eachCount()
                .filterValues { it > 1 }
                .keys
        check(duplicates.isEmpty()) { "duplicate $label: ${duplicates.joinToString()}" }
    }
    checkUnique(audio.sounds.map { it.id.slug }, "sound")
    checkUnique(audio.music.map { it.id.slug }, "music track")
    audio.sounds.forEach { sound -> validateAudioResource(sound.resource, "sound ${sound.id.slug}") }
    audio.music.forEach { track -> validateAudioResource(track.resource, "music track ${track.id.slug}") }

    val sounds = audio.sounds.map { it.id }.toSet()
    val music = audio.music.map { it.id }.toSet()

    fun checkSounds(
        ids: List<SoundId>,
        label: String,
    ) {
        ids.forEach { id -> check(id in sounds) { "$label references undeclared sound ${id.slug}" } }
    }

    fun checkMusic(
        id: MusicId,
        label: String,
    ) {
        check(id in music) { "$label references undeclared music track ${id.slug}" }
    }

    checkUnique(audio.cues.map { it.cue }, "audio cue")
    audio.cues.forEach { cue ->
        check(cue.sounds.isNotEmpty()) { "audio cue ${cue.cue} has no sounds" }
        checkSounds(cue.sounds, "audio cue ${cue.cue}")
    }
    val items = content.itemCatalog.map { it.id }.toSet()
    checkUnique(audio.items.map { it.item.slug }, "item sounds")
    audio.items.forEach { item ->
        check(item.item in items) { "item sounds reference unknown item ${item.item.slug}" }
        checkSounds(item.hit + item.miss + item.ready + item.unready + item.struck, "item ${item.item.slug} sounds")
    }
    val monsters = content.monsters.map { it.id }.toSet()
    checkUnique(audio.monsters.map { it.monster.slug }, "monster sounds")
    audio.monsters.forEach { monster ->
        check(monster.monster in monsters) { "monster sounds reference unknown monster ${monster.monster.slug}" }
        checkSounds(monster.attack + monster.death, "monster ${monster.monster.slug} sounds")
    }
    val spells = content.spells.map { it.id }.toSet()
    checkUnique(audio.spells.map { it.spell.slug }, "spell sound")
    audio.spells.forEach { spell ->
        check(spell.spell in spells) { "spell sound references unknown spell ${spell.spell.slug}" }
        checkSounds(spell.sounds, "spell ${spell.spell.slug} sound")
    }
    checkUnique(audio.itemUses.map { it.item.slug }, "item use sound")
    audio.itemUses.forEach { use ->
        check(use.item in items) { "item use sound references unknown item ${use.item.slug}" }
        checkSounds(use.sounds, "item use ${use.item.slug} sound")
    }
    val terrain =
        content.terrain.terrain
            .map { it.id.slug }
            .toSet()
    checkUnique(audio.steps.map { it.terrain.slug }, "step sound")
    audio.steps.forEach { step ->
        check(step.terrain.slug in terrain) { "step sound references unknown terrain ${step.terrain.slug}" }
        checkSounds(step.sounds, "terrain ${step.terrain.slug} step sound")
    }
    checkUnique(audio.areas.filter { it.fromHour == null }.map { it.area }, "untimed area music")
    audio.areas.forEach { area ->
        checkMusic(area.music, "area ${area.area} music")
        check((area.fromHour == null) == (area.toHour == null)) { "area ${area.area} music needs both hours or none" }
        listOfNotNull(area.fromHour, area.toHour).forEach { hour ->
            check(hour in 0..23) { "area ${area.area} music hour $hour is outside 0..23" }
        }
    }
    val scenes = content.scenes.map { it.id }.toSet()
    checkUnique(audio.scenes.map { it.scene.slug }, "scene music")
    audio.scenes.forEach { scene ->
        check(scene.scene in scenes) { "scene music references unknown scene ${scene.scene.slug}" }
        checkMusic(scene.music, "scene ${scene.scene.slug} music")
    }
    val dungeons =
        content.dungeons.scenarios
            .map { it.dungeon }
            .toSet()
    checkUnique(audio.dungeons.map { it.dungeon.slug }, "dungeon music")
    audio.dungeons.forEach { dungeon ->
        check(dungeon.dungeon in dungeons) { "dungeon music references unknown dungeon ${dungeon.dungeon.slug}" }
        checkMusic(dungeon.music, "dungeon ${dungeon.dungeon.slug} music")
    }
    val castles = content.castles.map { it.id }.toSet()
    checkUnique(audio.castles.map { it.castle.slug }, "castle music")
    audio.castles.forEach { castle ->
        check(castle.castle in castles) { "castle music references unknown castle ${castle.castle.slug}" }
        checkMusic(castle.music, "castle ${castle.castle.slug} music")
    }
    content.scriptSourceRoots().forEach { root ->
        root.visitScriptStatements { sourceStatement ->
            val command = (sourceStatement.statement as? ScriptStatementNode.View)?.command
            when (command) {
                is ViewCommand.PlaySound -> checkSounds(listOf(command.sound), sourceStatement.label)
                is ViewCommand.PlayMusic -> checkMusic(command.music, sourceStatement.label)
                else -> Unit
            }
        }
    }
}

private fun validateAudioResource(
    resource: String,
    label: String,
) {
    validateRelativeResource(resource, label)
    check(resource.substringAfterLast('.', "").lowercase() in audioResourceExtensions) {
        "$label resource must be one of ${audioResourceExtensions.joinToString()}"
    }
}

private fun ScriptSourceRoot.imageReferences(): List<Pair<ImageId, String>> =
    buildList {
        visitScriptStatements { sourceStatement ->
            when (val statement = sourceStatement.statement) {
                is ScriptStatementNode.View -> {
                    val image = (statement.command as? ViewCommand.ShowImage)?.image
                    if (image != null) add(image to sourceStatement.label)
                }
                is ScriptStatementNode.ShopService -> add(statement.shop.image to "${sourceStatement.label} shop")
                else -> Unit
            }
        }
    }

private fun validateWorldMaps(
    worldMaps: List<WorldMapModel>,
    terrain: TerrainCatalogModel,
) {
    check(worldMaps.map { it.id }.toSet().size == worldMaps.size) {
        "duplicate world map"
    }
    val terrainCodes = terrain.terrain.map { it.mapCode }.toSet()
    worldMaps.forEach { map ->
        check(
            map.layers
                .map { it.id }
                .toSet()
                .size == map.layers.size,
        ) {
            "world map ${map.id.slug} has duplicate layers"
        }
        val terrainLayer =
            map.layers.singleOrNull { it.id == DslDefaultMapLayers.terrain }
                ?: error("world map ${map.id.slug} requires terrain layer")
        check(terrainLayer.rows.isNotEmpty()) { "world map ${map.id.slug} terrain layer has no rows" }
        val width = terrainLayer.rows.first().length
        check(width > 0) { "world map ${map.id.slug} terrain layer has blank first row" }
        terrainLayer.rows.forEachIndexed { index, row ->
            check(row.length == width) {
                "world map ${map.id.slug} terrain row ${index + 1} width ${row.length} differs from $width"
            }
            row.forEach { tile ->
                check(tile in terrainCodes) {
                    "world map ${map.id.slug} terrain row ${index + 1} uses unknown tile '$tile'"
                }
            }
        }
    }
}

private fun validateFlagReferences(
    ast: BoolAst,
    label: String,
    flagStates: FlagStateIndex,
) {
    lateinit var bool: (BoolAst) -> Unit
    lateinit var int: (IntAst) -> Unit

    int =

        fun(value: IntAst) {
            when (value) {
                is IntAst.Lit,
                is IntAst.Var,
                is IntAst.BattleFlag,
                is IntAst.BattleQuery,
                is IntAst.PartyMemberQuery,
                is IntAst.RealmScalar,
                is IntAst.PartyScalar,
                is IntAst.InventoryPoolCount,
                -> Unit
                is IntAst.Binary -> {
                    int(value.left)
                    int(value.right)
                }
                is IntAst.If -> {
                    bool(value.cond)
                    int(value.thenValue)
                    int(value.elseValue)
                }
                is IntAst.Random -> {
                    int(value.lo)
                    int(value.hi)
                }
                is IntAst.MemberField -> int(value.member)
                is IntAst.MonsterField -> int(value.monster)
                is IntAst.InventorySlotCost -> int(value.slot)
            }
        }

    bool =

        fun(value: BoolAst) {
            when (value) {
                is BoolAst.Cmp -> {
                    int(value.left)
                    int(value.right)
                }
                is BoolAst.Not -> bool(value.expr)
                is BoolAst.And -> {
                    bool(value.left)
                    bool(value.right)
                }
                is BoolAst.Or -> {
                    bool(value.left)
                    bool(value.right)
                }
                is BoolAst.FlagIs -> flagStates.requireKnown(value.slug, value.nativeValue, label)
                is BoolAst.MilestoneAchieved -> Unit
                is BoolAst.MemberFlag -> int(value.member)
                is BoolAst.MonsterFlag -> int(value.monster)
                is BoolAst.BattleFlag,
                is BoolAst.RuntimeFlag,
                -> Unit
            }
        }

    bool(ast)
}

private fun validateFlagTransitions(
    content: GameModel,
    flagStates: FlagStateIndex,
) {
    val scenesById = content.scenes.associateBy { it.id }

    fun combineStates(
        left: Map<String, FlagState>,
        right: Map<String, FlagState>,
    ): Map<String, FlagState> {
        val conflicts = left.keys.intersect(right.keys).filter { left[it]?.native != right[it]?.native }
        return if (conflicts.isEmpty()) left + right else emptyMap()
    }

    fun statesRequiredBy(ast: BoolAst?): Map<String, FlagState> {
        ast ?: return emptyMap()
        return when (ast) {
            is BoolAst.FlagIs -> {
                val state = flagStates.state(ast.slug, ast.nativeValue)
                if (state == null) emptyMap() else mapOf(ast.slug to state)
            }
            is BoolAst.And -> combineStates(statesRequiredBy(ast.left), statesRequiredBy(ast.right))
            else -> emptyMap()
        }
    }

    fun statesRequiredBy(conditions: List<BoolAst>): Map<String, FlagState> =
        conditions.fold(emptyMap()) { context, condition ->
            combineStates(context, statesRequiredBy(condition))
        }

    fun validateTransition(
        slug: String,
        native: String,
        context: Map<String, FlagState>,
        label: String,
    ) {
        flagStates.requireKnown(slug, native, label)
        val source = context[slug] ?: return
        if (source.native == native) return

        val sourceOrder =
            checkNotNull(flagStates.order(source)) {
                "flag transition source ${source.flag.slug}.${source.native} is not declared"
            }
        val targetOrder =
            checkNotNull(flagStates.order(slug, native)) {
                "flag transition target $slug.$native is not declared"
            }
        check(targetOrder >= sourceOrder) {
            "$label moves ${source.flag.slug} backward from ${source.native} to $native"
        }
    }

    fun validateMissionTransitions(
        mission: MissionModel,
        scene: SceneModel,
    ) {
        visitScriptStatements(scene.script, "mission ${mission.id.slug}", listOfNotNull(mission.requires)) { sourceStatement ->
            val statement = sourceStatement.statement as? ScriptStatementNode.SetFlag ?: return@visitScriptStatements
            validateTransition(
                slug = statement.slug,
                native = statement.nativeValue,
                context = statesRequiredBy(sourceStatement.branchConditions),
                label = sourceStatement.label,
            )
        }
    }

    content.scriptSourceRoots().forEach { root ->
        root.visitScriptStatements { sourceStatement ->
            val statement = sourceStatement.statement as? ScriptStatementNode.SetFlag ?: return@visitScriptStatements
            validateTransition(
                slug = statement.slug,
                native = statement.nativeValue,
                context = statesRequiredBy(sourceStatement.branchConditions),
                label = sourceStatement.label,
            )
        }
    }
    content.missions.forEach { mission ->
        val scene = scenesById[mission.action] ?: return@forEach
        validateMissionTransitions(mission, scene)
    }
}

/** Runs all authored-content validators and returns diagnostics without throwing. */
fun collectAuthoredContentDiagnostics(content: GameModel): List<ValidationDiagnostic> {
    val flagStates = FlagStateIndex(content.flags)
    val terrainBindings = TerrainBindingIndex(content.terrain, content.terminal, content.worldMaps)
    return buildList {
        captureValidation("metadata") { validateGameMetadata(content.metadata) }
        captureValidation("limits") { validateGameLimits(content.limits, content.worldMaps) }
        captureValidation("flags.catalog") { validateFlagCatalog(content.flags) }
        captureValidation("prompts") { validateGamePrompts(content.prompts) }
        captureValidation("pacing") { validateGamePacing(content.pacing) }
        captureValidation("realm") { validateRealmContent(content.realm) }
        captureValidation("visuals") { validateVisuals(content.visuals) }
        captureValidation("visuals.references") { validateImageReferences(content) }
        captureValidation("audio") { validateAudio(content) }
        captureValidation("terrain") { validateTerrainCatalog(content.terrain) }
        captureValidation("terminal") { validateTerminalPresentation(content.terminal, content.terrain) }
        captureValidation("layouts") { validateLayouts(content.layouts) }
        captureValidation("worldMaps") { validateWorldMaps(content.worldMaps, content.terrain) }
        captureValidation("items.catalog") { validateItemCatalog(content.itemCatalog) }
        captureValidation("items.pools") { validateItemPools(content.itemPools, content.itemCatalog) }
        captureValidation("progress") { validateGameProgress(content.progress, content.itemCatalog) }
        captureValidation("characterCreation") { validateCharacterCreation(content.characterCreation, content.worldMaps) }
        captureValidation("flags.authored") { validateAuthoredFlags(content, flagStates) }
        captureValidation("flags.transitions") { validateFlagTransitions(content, flagStates) }
        captureValidation("monsters") { validateMonsters(content.monsters) }
        captureValidation("missions") { validateMissions(content, flagStates, terrainBindings) }
        captureValidation("formulas") { validateFormulas(content.formulas) }
        captureValidation("leveling") { validateLeveling(content.leveling, content.formulas) }
        captureValidation("formulas.references") { validateSystemFormulaReferences(content) }
        captureValidation("rewards") { validateRewardBundles(content.rewards, content.formulas, content.itemCatalog) }
        captureValidation("combat") { validateCombatSystem(content.combat, content.rewards) }
        captureValidation("wilderness") { validateWildernessSystem(content.wilderness, terrainBindings) }
        captureValidation("dungeons") { validateDungeonSystem(content.dungeons, content.monsters) }
        content.castles.forEach { castle ->
            captureValidation("castles.${castle.id.slug}") {
                validateCastleSystem(castle, content.itemCatalog, content.progress, content.monsters, content.scenes, content.worldMaps)
            }
        }
        captureValidation("secrets") { validateSecretSystem(content.secrets, content.itemCatalog, content.itemPools, content.monsters) }
        captureValidation("towns") { validateTowns(content.towns) }
        captureValidation("townSystem") { validateTownSystem(content.townSystem, content.towns, content.scenes) }
        captureValidation("characters") { validateCharacters(content.characters, content.towns, content.terrain, content.worldMaps) }
        captureValidation("speak") { validateSpeakSystem(content.speak, content.characters, content.towns, content.flags) }
        captureValidation("itemUses") { validateItemUses(content.itemUses, content.itemCatalog) }
        captureValidation("dialogue") { validateDialogue(content.dialogue, content.characters, content.flags) }
        captureValidation("scripts") { validateScriptRewards(content, content.itemCatalog, content.flags) }
    }
}

private fun validateGameMetadata(metadata: GameMetadataModel) {
    check(metadata.universe.name.isNotBlank()) { "universe name must not be blank" }
    check(metadata.universe.version.isNotBlank()) { "universe version must not be blank" }
    check(metadata.universe.compatibilityId.isNotBlank()) { "universe compatibility id must not be blank" }
}

private fun validateGameLimits(
    limits: GameLimitsModel,
    worldMaps: List<WorldMapModel>,
) {
    limits.maxWorldMaps?.let { max ->
        check(max > 0) { "max world maps must be positive" }
        check(worldMaps.size <= max) { "declares ${worldMaps.size} world maps, but limit is $max" }
    }
}

/** Runs all authored-content validators and throws when any diagnostic is produced. */
fun validateAuthoredContent(content: GameModel) {
    val diagnostics = collectAuthoredContentDiagnostics(content)
    if (diagnostics.isNotEmpty()) {
        throw RylweenValidationException(diagnostics)
    }
}

private fun validateGamePrompts(prompts: GamePromptsModel) {
    check(prompts.pauseVerbs.all { it.isNotBlank() }) {
        "pause prompt verbs must not be blank"
    }
    check(prompts.returnKeyName.isNotBlank()) {
        "pause prompt return key name must not be blank"
    }
    validateTextBlock(prompts.titleMenu.header, "title menu header prompt")
    validateTextBlock(prompts.titleMenu.continueUnavailable, "title menu continue-unavailable prompt")
    validateTextBlock(prompts.titleMenu.overwriteConfirm, "title menu overwrite-confirm prompt")
    validateTextBlock(prompts.titleMenu.terminateConfirm, "title menu terminate-confirm prompt")
    validateTextBlock(prompts.titleMenu.terminated, "title menu terminated prompt")
    validateTextBlock(prompts.titleMenu.terminateFailed, "title menu terminate-failed prompt")
    validateTextBlock(prompts.titleMenu.invalidInput, "title menu invalid-input prompt")
    validateTextBlock(prompts.priest.who, "priest who prompt", allowedRuntimeBindings = huntTextBindings)
    check(prompts.priest.firstSpellKey.isLetterOrDigit()) { "priest first-spell key must be a visible letter or digit" }
    validateTextBlock(prompts.priest.mageCombatOnly, "priest mage-combat-only prompt")
    validateTextBlock(prompts.priest.wrongClass, "priest wrong-class prompt")
    validateTextBlock(prompts.priest.dead, "priest dead prompt")
    validateTextBlock(prompts.priest.invalidCaster, "priest invalid-caster prompt")
    validateTextBlock(prompts.priest.invalidSpell, "priest invalid-spell prompt")
    validateTextBlock(prompts.priest.noSpells, "priest no-spells prompt")
    validateTextBlock(prompts.inventoryUse.usePrompt, "inventory use prompt")
    validateTextBlock(prompts.inventoryUse.quaff, "inventory quaff prompt")
    validateTextBlock(prompts.inventoryUse.noItems, "inventory no-items prompt")
    validateTextBlock(prompts.inventoryUse.noItem, "inventory no-item prompt")
    validateTextBlock(prompts.inventoryUse.invalidSlot, "inventory invalid-slot prompt", allowedRuntimeBindings = inventoryUseTextBindings)
    validateTextBlock(prompts.inventoryUse.notUsable, "inventory not-usable prompt", allowedRuntimeBindings = inventoryUseTextBindings)
    validateTextBlock(prompts.equipment.inventory, "equipment inventory prompt")
    validateTextBlock(prompts.equipment.who, "equipment who prompt")
    validateTextBlock(prompts.equipment.noAvailable, "equipment no-available prompt", allowedRuntimeBindings = equipmentTextBindings)
    validateTextBlock(prompts.equipment.equipped, "equipment equipped prompt", allowedRuntimeBindings = selectedItemTextBindings)
    validateTextBlock(prompts.equipment.readyWeapons, "equipment ready-weapons prompt")
    validateTextBlock(prompts.equipment.readyArmors, "equipment ready-armors prompt")
    validateTextBlock(prompts.save.saving, "save saving prompt")
    validateTextBlock(prompts.save.done, "save done prompt")
    validateTextBlock(prompts.save.failed, "save failed prompt")
    validateTextBlock(prompts.characterCreation.header, "character creation header prompt")
    validateTextBlock(prompts.characterCreation.method, "character creation method prompt")
    check(prompts.characterCreation.manualLabel.isNotBlank() && prompts.characterCreation.prerolledLabel.isNotBlank()) {
        "character creation method labels must not be blank"
    }
    validateTextBlock(prompts.characterCreation.partyName, "character creation party-name prompt")
    validateTextBlock(
        prompts.characterCreation.statsPage,
        "character creation stats-page prompt",
        allowedRuntimeBindings = characterCreationStatsTextBindings,
    )
    validateTextBlock(prompts.characterCreation.reroll, "character creation reroll prompt")
    validateTextBlock(
        prompts.characterCreation.memberName,
        "character creation member-name prompt",
        allowedRuntimeBindings = characterCreationMemberNumberTextBindings,
    )
    validateTextBlock(
        prompts.characterCreation.gender,
        "character creation gender prompt",
        allowedRuntimeBindings = characterCreationMemberNameTextBindings,
    )
    check(prompts.characterCreation.genderMaleLabel.isNotBlank()) {
        "character creation male label must not be blank"
    }
    check(prompts.characterCreation.genderFemaleLabel.isNotBlank()) {
        "character creation female label must not be blank"
    }
    validateTextBlock(
        prompts.characterCreation.race,
        "character creation race prompt",
        allowedRuntimeBindings = characterCreationMemberNameTextBindings,
    )
    validateTextBlock(prompts.characterCreation.classMenu, "character creation class-menu prompt")
    validateTextBlock(prompts.characterCreation.invalidInput, "character creation invalid-input prompt")
    validateTextBlock(prompts.characterCreation.saving, "character creation saving prompt")
    validateTextBlock(prompts.characterCreation.saveConfirmation, "character creation save-confirmation prompt")
    check(prompts.characterCreation.saveConfirmation.isSingleLinePrompt()) {
        "character creation save-confirmation prompt must be one line because it is appended to the saving prompt"
    }
    validateTextBlock(prompts.town.menuTitle, "town menu-title prompt", allowedRuntimeBindings = townEntryTaxTextBindings)
    validateTextBlock(prompts.town.entryWelcome, "town entry welcome prompt", allowedRuntimeBindings = townEntryTaxTextBindings)
    validateTextBlock(prompts.town.entryTax, "town entry tax prompt", allowedRuntimeBindings = townEntryTaxTextBindings)
    validateTextBlock(prompts.town.entryTaxThanks, "town entry tax thanks prompt")
    validateTextBlock(prompts.wilderness.pass, "wilderness pass prompt")
    validateTextBlock(prompts.wilderness.noMoves, "wilderness no-moves prompt")
    validateTextBlock(prompts.wilderness.outOfMoves, "wilderness out-of-moves prompt")
    validateTextBlock(prompts.wilderness.notInWilderness, "wilderness not-in-wilderness prompt")
    validateTextBlock(prompts.wilderness.nothingToEnter, "wilderness nothing-to-enter prompt")
    validateTextBlock(prompts.wilderness.impassable, "wilderness impassable prompt")
    validateTextBlock(prompts.wilderness.quitConfirm, "wilderness quit-confirm prompt")
    validateTextBlock(
        prompts.wilderness.dungeonHandoff,
        "wilderness dungeon-handoff prompt",
        allowedRuntimeBindings = wildernessDungeonEntryTextBindings,
    )
    validateTextBlock(prompts.dungeon.levelMenu, "dungeon level-menu prompt", allowedRuntimeBindings = dungeonDepthTextBindings)
    validateTextBlock(prompts.dungeon.enterLevel, "dungeon enter-level prompt", allowedRuntimeBindings = dungeonDepthTextBindings)
    validateTextBlock(prompts.dungeon.atMaxDepth, "dungeon at-max-depth prompt")
    validateTextBlock(prompts.dungeon.noTorches, "dungeon no-torches prompt")
    validateTextBlock(prompts.dungeon.leaveCavern, "dungeon leave-cavern prompt")
    validateTextBlock(prompts.party.journalHeader, "party journal-header prompt")
    validateTextBlock(prompts.party.journalEmpty, "party journal-empty prompt")
    validateTextBlock(prompts.party.inventoryHeader, "party inventory-header prompt")
    validateTextBlock(prompts.party.killsHeader, "party kills-header prompt")
    validateTextBlock(prompts.party.killsEmpty, "party kills-empty prompt")
    validateTextBlock(prompts.party.dropPrompt, "party drop prompt")
    validateTextBlock(prompts.party.dropEmptySlot, "party drop-empty-slot prompt")
    validateTextBlock(prompts.party.dropAborted, "party drop-aborted prompt")
    validateTextBlock(prompts.party.dropDropped, "party drop-dropped prompt", allowedRuntimeBindings = selectedItemTextBindings)
    validateTextBlock(prompts.party.dropCapacityReduced, "party drop-capacity-reduced prompt")
    validateTextBlock(prompts.party.dropOverflowConfirm, "party drop-overflow-confirm prompt")
    validateTextBlock(prompts.party.dropOverflowPrompt, "party drop-overflow-prompt prompt")
}

private fun TextBlock.isSingleLinePrompt(): Boolean =
    when (this) {
        is TextBlock.Inline -> lines.size == 1
        is TextBlock.Structured -> lines.size == 1
        is TextBlock.Flow -> false
    }

private fun validateGamePacing(pacing: GamePacingModel) {
    check(pacing.battle.messagePauseMs >= 0) { "battle message pause must not be negative" }
    check(pacing.battle.openingPauseMs >= 0) { "battle opening pause must not be negative" }
    check(pacing.battle.victoryPauseMs >= 0) { "battle victory pause must not be negative" }
    check(pacing.characterCreation.classRejectionPauseMs >= 0) {
        "character creation class-rejection pause must not be negative"
    }
    check(pacing.characterCreation.saveConfirmationPauseMs >= 0) {
        "character creation save-confirmation pause must not be negative"
    }
    check(pacing.shop.interludePagePauseMs >= 0) { "shop interlude-page pause must not be negative" }
    check(pacing.shop.noticePauseMs >= 0) { "shop notice pause must not be negative" }
    check(pacing.speak.promptReplyPauseMs >= 0) { "speak prompt-reply pause must not be negative" }
    check(pacing.speak.responsePauseMs >= 0) { "speak response pause must not be negative" }
    check(pacing.wilderness.campMessagePauseMs >= 0) { "wilderness camp-message pause must not be negative" }
}

private fun validateItemPools(
    itemPools: List<ItemPoolId>,
    itemCatalog: List<ItemRecordModel>,
) {
    check(itemPools.map { it.slug }.toSet().size == itemPools.size) {
        "duplicate item pool"
    }
    itemPools.forEach { validateItemPool(it, itemCatalog) }
}

private fun validateAuthoredFlags(
    content: GameModel,
    flagStates: FlagStateIndex,
) {
    val declaredFlags = content.flags.toSet()
    check(
        content.authoredFlags
            .map { it.ref.slug }
            .toSet()
            .size == content.authoredFlags.size,
    ) {
        "duplicate authored flag"
    }
    content.authoredFlags.forEach { flag ->
        check(flag.ref in declaredFlags || flagStates.flag(flag.ref.slug) != null) {
            "authored flag ${flag.ref.slug} was not declared"
        }
        check(flag.states.all { it.flag == flag.ref }) {
            "authored flag ${flag.ref.slug} includes a state from another flag"
        }
        check(flag.states.isNotEmpty()) {
            "authored flag ${flag.ref.slug} must expose at least one state"
        }
        flag.states.forEach { state ->
            flagStates.requireKnown(state, "authored flag ${flag.ref.slug}")
        }
    }
}

private fun validateMonsters(monsters: List<MonsterModel>) {
    check(monsters.map { it.id }.toSet().size == monsters.size) {
        "duplicate authored monster"
    }
    monsters.forEach { monster ->
        check(monster.name.isNotBlank()) { "monster ${monster.id.slug} requires a display name" }
        check(monster.count.first > 0 && monster.count.last >= monster.count.first) {
            "monster ${monster.id.slug} has invalid count range"
        }
        monster.countMaxLevelDivisor?.let { divisor ->
            check(divisor > 0) { "monster ${monster.id.slug} has invalid count level divisor" }
        }
        check(monster.hitPoints.first > 0 && monster.hitPoints.last >= monster.hitPoints.first) {
            "monster ${monster.id.slug} has invalid hit point range"
        }
    }
}

private fun validateMissions(
    content: GameModel,
    flagStates: FlagStateIndex,
    terrainBindings: TerrainBindingIndex,
) {
    check(
        content.missions
            .map { it.id }
            .toSet()
            .size == content.missions.size,
    ) {
        "duplicate authored mission"
    }
    val scenes = content.scenes.map { it.id }.toSet()
    content.missions.forEach { mission ->
        check(mission.action in scenes) {
            "mission ${mission.id.slug} references missing action scene ${mission.action.slug}"
        }
        mission.requires?.let { requirement ->
            validateFlagReferences(requirement, "mission ${mission.id.slug} requirement", flagStates)
        }
        when (val trigger = mission.trigger) {
            is Trigger.RandomStep -> checkPercent(trigger.percent, "mission ${mission.id.slug} random step")
            is Trigger.AtPosition -> {
                check(trigger.point.x >= 0 && trigger.point.y >= 0) {
                    "mission ${mission.id.slug} position trigger must use non-negative world coordinates"
                }
            }
            is Trigger.AtLocation ->
                check(trigger.location.slug.isNotBlank()) {
                    "mission ${mission.id.slug} location trigger references blank location"
                }
            is Trigger.OnTerrain ->
                terrainBindings.requireMapReachable(
                    trigger.terrain,
                    "mission ${mission.id.slug} terrain trigger",
                )
        }
    }
}

/** Returns milestone-projected special inventory items currently owned by [party]. */
fun specialInventoryItems(
    content: GameModel,
    party: PartyState,
): List<ItemId> =
    party.milestones.toSet().let { achieved ->
        content.progress.milestones
            .filter { it.id in achieved }
            .mapNotNull { it.specialInventoryItem }
    }

/** Returns true when [party] satisfies the authored win condition. */
fun isWinningConfiguration(
    content: GameModel,
    party: PartyState,
): Boolean =
    content.progress.win?.let { win ->
        party.milestones.toSet().let { achieved ->
            (win.completedBy?.let { it in achieved } ?: true) &&
                win.required.all { it in achieved }
        }
    } ?: false

/** Validates authored or saved runtime states against optional current [content]. */
fun validateStates(
    states: List<StateModel>,
    content: GameModel? = null,
) {
    check(states.map { it.id }.toSet().size == states.size) { "duplicate state id" }

    val itemIds =
        content
            ?.itemCatalog
            ?.map { it.id }
            ?.toSet()
            .orEmpty()
    val milestoneIds =
        content
            ?.progress
            ?.milestones
            ?.map { it.id }
            ?.toSet()
            .orEmpty()
    val flagStates = content?.flags?.let(::FlagStateIndex)
    val npcIds =
        content
            ?.characters
            ?.map { it.id }
            ?.toSet()
            .orEmpty()
    val mapIds =
        content
            ?.worldMaps
            ?.map { it.id }
            ?.toSet()
            .orEmpty()
    val terrainBindings = content?.let { TerrainBindingIndex(it.terrain, it.terminal, it.worldMaps) }
    val specialItems =
        content
            ?.progress
            ?.milestones
            ?.mapNotNull { it.specialInventoryItem }
            ?.toSet()
            ?: emptySet()

    states.forEach { state ->
        val partyIds = state.parties.map { it.id }
        check(partyIds.toSet().size == partyIds.size) { "state ${state.id.slug}: duplicate party id" }
        check(state.realm.activeParty in partyIds) {
            "state ${state.id.slug}: active party ${state.realm.activeParty.slug} is not present"
        }
        check(state.realm.hour in 0..24) { "state ${state.id.slug}: realm hour must be 0..24" }
        check(state.realm.minute in 0..59) { "state ${state.id.slug}: realm minute must be 0..59" }
        check(state.realm.partyMemberMax > 0) { "state ${state.id.slug}: partyMemberMax must be positive" }
        if (content != null) {
            check(state.realm.currentMap in mapIds) {
                "state ${state.id.slug}: current map ${state.realm.currentMap.slug} is not declared"
            }
        }
        state.realm.lastTerrain?.let { terrain ->
            terrainBindings?.requireStyled(terrain, "state ${state.id.slug}: lastTerrain")
                ?: check(terrain.sourceMapCode != null) {
                    "state ${state.id.slug}: lastTerrain ${terrain.slug} needs a map tile code"
                }
        }
        if (content != null) {
            state.npcs.forEach { placement ->
                check(placement.npc in npcIds) {
                    "state ${state.id.slug}: NPC placement references unknown NPC ${placement.npc.slug}"
                }
            }
        }

        state.parties.forEach { party ->
            val slots = party.inventory.map { it.slot }
            check(slots.all { it in 0..INVENTORY_SAVE_SLOT_MAX }) {
                "party ${party.id.slug}: inventory slot outside 0..$INVENTORY_SAVE_SLOT_MAX"
            }
            check(slots.toSet().size == slots.size) { "party ${party.id.slug}: duplicate inventory slot" }
            party.counters.memberCount?.let { count ->
                val livingMembers = party.members.count { !it.dead }
                check(count == livingMembers) {
                    "party ${party.id.slug}: memberCount $count does not match $livingMembers living member records"
                }
            }
            check(party.milestones.toSet().size == party.milestones.size) {
                "party ${party.id.slug}: duplicate game milestone"
            }
            if (content != null) {
                party.milestones.forEach { milestone ->
                    check(milestone in milestoneIds) {
                        "party ${party.id.slug}: unknown milestone ${milestone.slug}"
                    }
                }
            }
            check(
                party.flags
                    .map { it.flag }
                    .toSet()
                    .size == party.flags.size,
            ) {
                "party ${party.id.slug}: duplicate party flag state"
            }
            flagStates?.let { statesByFlag ->
                party.flags.forEach { state ->
                    statesByFlag.requireKnown(state, "party ${party.id.slug}")
                }
            }
            check(
                party.visitedNpcs
                    .map { it.npc }
                    .toSet()
                    .size == party.visitedNpcs.size,
            ) {
                "party ${party.id.slug}: duplicate NPC visit record"
            }
            if (content != null) {
                party.visitedNpcs.forEach { visit ->
                    check(visit.npc in npcIds) {
                        "party ${party.id.slug}: visit record references unknown NPC ${visit.npc.slug}"
                    }
                }
                party.inventory.mapNotNull { it.item }.forEach { item ->
                    check(item in itemIds) {
                        "party ${party.id.slug}: inventory references unknown item ${item.slug}"
                    }
                }
            }
            check(party.inventory.none { it.item in specialItems }) {
                val special = party.inventory.first { it.item in specialItems }.item
                "party ${party.id.slug}: ${special?.slug} is special state, not a slotted inventory item"
            }
            content?.let { game ->
                game.progress.win
                    ?.completedBy
                    ?.takeIf { it in party.milestones }
                    ?.let {
                        check(isWinningConfiguration(game, party)) {
                            "party ${party.id.slug}: completed win marker without all required milestones"
                        }
                    }
            }
            if (state.kind == StateKind.RuntimeSnapshot) {
                check(state.realm.rngState != null) {
                    "runtime state ${state.id.slug}: realm requires rngState"
                }
                check(party.counters.memberCount != null) {
                    "runtime state ${state.id.slug}: party ${party.id.slug} requires counters.memberCount"
                }
                check(slots == (0..INVENTORY_SAVE_SLOT_MAX).toList()) {
                    "runtime state ${state.id.slug}: party ${party.id.slug} must list inventory slots 0..$INVENTORY_SAVE_SLOT_MAX"
                }
                check(
                    party.counters.attackedToday
                        .map { it.party }
                        .toSet() == partyIds.toSet(),
                ) {
                    "runtime state ${state.id.slug}: party ${party.id.slug} must list attackToday for every party"
                }
                state.npcs
                    .map { it.npc }
                    .takeIf { it.isNotEmpty() }
                    ?.toSet()
                    ?.let { declaredNpcs ->
                        check(party.visitedNpcs.map { it.npc }.toSet() == declaredNpcs) {
                            "runtime state ${state.id.slug}: party ${party.id.slug} must list every declared NPC visit bit"
                        }
                    }
                check(
                    party.journal.all { entry ->
                        entry !is JournalEntryState.Line || entry.text.isNotBlank()
                    },
                ) {
                    "runtime state ${state.id.slug}: party ${party.id.slug} has a blank journal line"
                }
            }
            party.members.forEach { member ->
                check(member.hitPoints.max == member.health) {
                    "member ${member.name}: hit point max must match health"
                }
                check(member.mana.max == member.wisdom) {
                    "member ${member.name}: mana max must match wisdom"
                }
            }
        }
    }
}
