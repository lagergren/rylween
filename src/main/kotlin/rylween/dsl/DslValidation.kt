package rylween.dsl

data class ValidationDiagnostic(
    val path: String,
    val message: String,
) {
    override fun toString(): String =
        if (path.isBlank()) message else "$path: $message"
}

class RylweenValidationException(
    val diagnostics: List<ValidationDiagnostic>,
) : IllegalStateException(
    diagnostics.joinToString(separator = "\n") { it.toString() },
)

fun RylweenValidationException.withDiagnosticPathPrefix(prefix: String): RylweenValidationException =
    RylweenValidationException(
        diagnostics.map { diagnostic ->
            diagnostic.copy(
                path = if (diagnostic.path.isBlank()) prefix else "$prefix.${diagnostic.path}",
            )
        },
    )

private inline fun MutableList<ValidationDiagnostic>.captureValidation(path: String, block: () -> Unit) {
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

private class FlagStateIndex(flags: List<FlagRef>) {
    private val bySlug: Map<String, FlagRef> = flags.associateBy { it.slug }
    private val states: Set<Pair<String, String>> =
        flags.flatMap { flag -> flag.states.map { state -> flag.slug to state.native } }.toSet()
    private val stateByKey: Map<Pair<String, String>, FlagState> =
        flags.flatMap { flag -> flag.states.map { state -> (flag.slug to state.native) to state } }.toMap()
    private val orderByKey: Map<Pair<String, String>, Int> =
        flags.flatMap { flag -> flag.states.mapIndexed { index, state -> (flag.slug to state.native) to index } }
            .toMap()

    fun contains(state: FlagState): Boolean =
        state.flag.slug to state.native in states

    fun contains(slug: String, native: String): Boolean =
        slug to native in states

    fun flag(slug: String): FlagRef? =
        bySlug[slug]

    fun state(slug: String, native: String): FlagState? =
        stateByKey[slug to native]

    fun order(state: FlagState): Int? =
        orderByKey[state.flag.slug to state.native]

    fun order(slug: String, native: String): Int? =
        orderByKey[slug to native]
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

    fun requireStyled(terrain: TerrainId, label: String) {
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

    fun requireMapReachable(terrain: TerrainId, label: String) {
        requireStyled(terrain, label)
        terrainBySlug[terrain.slug]?.let { semantics ->
            check(semantics.mapCode in mapCodes) {
                "$label references terrain ${terrain.slug} tile '${semantics.mapCode}', but no authored world map uses it"
            }
        }
    }
}

private val EmptyTextBindings: Set<TextPart> = emptySet()
private val HuntTextBindings: Set<TextPart> = setOf(
    TextPart.HuntMemberName,
    TextPart.HuntMemberSubjectPronoun,
    TextPart.HuntFoodGained,
    TextPart.PartyMemberMax,
)
private val RandomItemOfferTextBindings: Set<TextPart> = setOf(
    TextPart.OfferedItemName,
    TextPart.OfferedItemPrice,
)
private val SelectedItemTextBindings: Set<TextPart> = setOf(TextPart.SelectedItemName)
private val ShopPriceTextBindings: Set<TextPart> = setOf(TextPart.ShopPrice)
private val ShopCapacityTextBindings: Set<TextPart> = setOf(TextPart.ShopCarryCap)
private val WildernessDungeonHandoffTextBindings: Set<TextPart> = setOf(
    TextPart.WildernessDungeonName,
    TextPart.WildernessDungeonLevels,
)
private val DungeonDepthTextBindings: Set<TextPart> = setOf(TextPart.DungeonDepth)
private val ConversationTextBindings: Set<TextPart> = setOf(TextPart.ConversationNpcName)
private val TownEntryTaxTextBindings: Set<TextPart> = setOf(
    TextPart.CurrentTownName,
    TextPart.TownTaxPercent,
    TextPart.TownTaxAmount,
)
private val CharacterCreationStatsTextBindings: Set<TextPart> = setOf(
    TextPart.CreationMemberNumber,
    TextPart.CreationWeaponSkill,
    TextPart.CreationHealth,
    TextPart.CreationWisdom,
    TextPart.CreationCharisma,
)
private val CharacterCreationMemberNumberTextBindings: Set<TextPart> = setOf(TextPart.CreationMemberNumber)
private val CharacterCreationMemberNameTextBindings: Set<TextPart> = setOf(TextPart.CreationMemberName)

private fun FlagStateIndex.requireKnown(state: FlagState, label: String) {
    check(contains(state)) {
        "$label references unknown story state ${state.flag.slug}.${state.native}"
    }
}

private fun FlagStateIndex.requireKnown(slug: String, native: String, label: String) {
    check(contains(slug, native)) {
        "$label references unknown story state $slug.$native"
    }
}

fun validateFlagCatalog(flags: List<FlagRef>) {
    check(flags.map { it.slug }.toSet().size == flags.size) { "duplicate flag slug" }

    flags.forEach { flag ->
        val nativeStates = flag.states.map { it.native }
        check(nativeStates.isNotEmpty()) { "flag ${flag.slug} has no states" }
        check(nativeStates.toSet().size == nativeStates.size) { "duplicate state in flag ${flag.slug}" }
    }
}

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
            ItemKind.MagicItem -> {
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
    }
}

fun validateFormulas(formulas: List<FormulaModel>) {
    check(formulas.map { it.id }.toSet().size == formulas.size) { "duplicate formula" }
}

fun validateLeveling(leveling: LevelingModel?, formulas: List<FormulaModel>) {
    leveling ?: return
    check(leveling.maxLevel > 0) { "leveling max level must be positive" }
    check(leveling.xpThresholds.size > leveling.maxLevel) {
        "leveling XP thresholds must include indexes through max level ${leveling.maxLevel}"
    }
    check(leveling.xpThresholds.all { it >= 0 }) { "leveling XP thresholds must be non-negative" }
    check(leveling.hitPointFloor in formulas.map { it.id }.toSet()) {
        "leveling hit point floor references missing formula ${leveling.hitPointFloor.slug}"
    }
    check(leveling.classGrowth.map { it.klass }.toSet().size == leveling.classGrowth.size) {
        "duplicate class level-up declaration"
    }
}

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

fun validateCombatSystem(combat: CombatSystemModel, rewards: List<RewardBundleModel>) {
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

        check(battle.playerRound.attack.hitBonus >= 0) {
            "standard battle attack hit bonus must be non-negative"
        }
        check(battle.playerRound.attack.damageSpreadMax >= 0) {
            "standard battle attack damage spread must be non-negative"
        }
        check(battle.playerRound.attack.confusionDivisor > 0) {
            "standard battle attack confusion divisor must be positive"
        }
        check(battle.playerRound.attack.hpDefenseDivisor > 0) {
            "standard battle attack HP defense divisor must be positive"
        }
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

        checkPercent(battle.monsterRound.spellChanceBasePercent, "standard battle monster spell base chance")
        check(battle.monsterRound.spellPowerDivisor > 0) {
            "standard battle monster spell-power divisor must be positive"
        }
        check(battle.monsterRound.melee.hitBonus >= 0) {
            "standard battle monster melee hit bonus must be non-negative"
        }
        check(battle.monsterRound.melee.damageDivisor > 0) {
            "standard battle monster melee damage divisor must be positive"
        }
        check(battle.monsterRound.melee.damageSpreadMax >= 0) {
            "standard battle monster melee damage spread must be non-negative"
        }
        check(battle.monsterRound.melee.damageMin >= 0) {
            "standard battle monster melee damage minimum must be non-negative"
        }
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
        "killed" to text.killed,
        "party held" to text.partyHeld,
        "victory bested" to text.victoryBested,
        "victory each survivor" to text.victoryEachSurvivor,
        "victory found object" to text.victoryFoundObject,
    ).forEach { (label, block) ->
        validateTextBlock(block, "standard battle text $label")
    }
}

private fun validateWildernessSystem(wilderness: WildernessSystemModel, terrainBindings: TerrainBindingIndex) {
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

fun validateDungeonSystem(dungeons: DungeonSystemModel, monsters: List<MonsterModel>? = null) {
    check(dungeons.events.map { it.id }.toSet().size == dungeons.events.size) {
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
        check(event.ops.isNotEmpty() || event.randomEncounterTier != null) {
            "dungeon event ${event.id.slug} must declare script or random encounter"
        }
    }

    check(dungeons.scenarios.map { it.dungeon }.toSet().size == dungeons.scenarios.size) {
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

fun validateYdargSystem(
    ydarg: YdargContentModel?,
    itemCatalog: List<ItemRecordModel>,
    progress: GameProgressModel,
    monsters: List<MonsterModel>,
    worldMaps: List<WorldMapModel> = emptyList(),
) {
    if (ydarg == null) return

    check(ydarg.entry.x >= 0 && ydarg.entry.y >= 0) { "Ydarg entry must use non-negative world coordinates" }
    validateYdargEntryTile(ydarg, worldMaps)
    check(ydarg.mapSize > 1) { "Ydarg map size must be greater than one" }
    check(ydarg.roomTypeMax >= 3) { "Ydarg room type max must allow monster, pitfall, and treasure rooms" }
    check(ydarg.roomEvents.keys.all { it in 1..ydarg.roomTypeMax }) {
        "Ydarg room event rolls must fit inside 1..${ydarg.roomTypeMax}"
    }
    val milestones = progress.milestones.map { it.id }.toSet()
    check(ydarg.requiredKey in milestones) { "Ydarg required key references unknown milestone ${ydarg.requiredKey.slug}" }
    check(ydarg.victoryMilestone in milestones) {
        "Ydarg victory marker references unknown milestone ${ydarg.victoryMilestone.slug}"
    }
    val monsterIds = monsters.map { it.id }.toSet()
    check(monsters.any { it.tier == ydarg.monsterTier }) {
        "Ydarg monster tier ${ydarg.monsterTier.slug} has no authored monsters"
    }
    ydarg.confrontation.sequence.forEach { monster ->
        check(monster in monsterIds) { "Ydarg confrontation references missing monster ${monster.slug}" }
    }
    ydarg.confrontation.baron?.let { baron ->
        check(baron in monsterIds) { "Ydarg baron references missing monster ${baron.slug}" }
    }

    val itemIds = itemCatalog.map { it.id }.toSet()
    check(ydarg.treasure.gateMax >= 0) { "Ydarg treasure gate max must be non-negative" }
    check(ydarg.treasure.regularKinds.isNotEmpty()) { "Ydarg treasure needs at least one regular kind table" }
    ydarg.treasure.regularKinds.forEachIndexed { index, items ->
        check(items.isNotEmpty()) { "Ydarg regular treasure kind $index is empty" }
        items.forEach { item -> check(item in itemIds) { "Ydarg treasure references missing item ${item.slug}" } }
    }
    check(ydarg.treasure.magic.isNotEmpty()) { "Ydarg magic treasure table is empty" }
    ydarg.treasure.magic.forEach { item ->
        check(item in itemIds) { "Ydarg magic treasure references missing item ${item.slug}" }
    }

    check(ydarg.pitfall.damageDivisor > 0) { "Ydarg pitfall damage divisor must be positive" }
    check(ydarg.pitfall.trapTypes.isNotEmpty()) { "Ydarg pitfall needs at least one trap type" }
    ydarg.pitfall.trapTypes.forEachIndexed { index, block ->
        validateTextBlock(block, "Ydarg pitfall trap ${index + 1}")
    }

    check(ydarg.stairs.floorCount > 0) { "Ydarg floor count must be positive" }
    check(ydarg.stairs.items.map { it.digit }.toSet().size == ydarg.stairs.items.size) {
        "Ydarg stairs contain duplicate give digits"
    }
    ydarg.stairs.items.forEach { item ->
        check(item.digit in 1..9) { "Ydarg stair item digit must be 1..9" }
        check(item.milestone in milestones) {
            "Ydarg stair item ${item.itemName} references unknown milestone ${item.milestone.slug}"
        }
        check(item.itemName.isNotBlank()) { "Ydarg stair item ${item.digit} has blank display name" }
        validateTextBlock(item.reject, "Ydarg stair item ${item.itemName} rejection")
    }

    validateYdargKeys(ydarg.keys)
    validateYdargText(ydarg.text)
}

private fun validateYdargEntryTile(ydarg: YdargContentModel, worldMaps: List<WorldMapModel>) {
    if (worldMaps.isEmpty()) return
    val worldMap = worldMaps.singleOrNull { map -> map.id == DslDefaultMaps.wilderness } ?: return
    val terrainRows = worldMap.layers.singleOrNull { layer -> layer.id == DslDefaultMapLayers.terrain }?.rows
        ?: return
    val row = if (ydarg.entry.y > 0) terrainRows.getOrNull(ydarg.entry.y - 1) else null
    val tile = row?.getOrNull(ydarg.entry.x)
    val castleTile = DslDefaultTerrain.castle.sourceMapCode
    check(tile == castleTile) {
        "Ydarg entry ${ydarg.entry.x}x${ydarg.entry.y} must point at castle tile '$castleTile' " +
            "on world map ${worldMap.id.slug}"
    }
}

fun validateSecretSystem(
    secrets: SecretSystemModel,
    itemCatalog: List<ItemRecordModel>,
    monsters: List<MonsterModel>,
) {
    val playerNames = secrets.players.map { it.playerName }
    check(playerNames.toSet().size == playerNames.size) { "duplicate secret player name" }

    val itemIds = itemCatalog.map { it.id }.toSet()
    val monsterIds = monsters.map { it.id }.toSet()
    secrets.players.forEach { player ->
        check(player.playerName.isNotBlank()) { "secret player name is blank" }
        player.stepTrigger?.let { trigger ->
            check(trigger.everySteps > 0) {
                "secret player ${player.playerName} step trigger must be positive"
            }
            validateSecretAction(trigger.action, "secret player ${player.playerName} step trigger", itemIds, monsterIds)
        }
        player.keyActions.forEach { keyAction ->
            validateInputChoiceBinding(keyAction.binding, "secret player ${player.playerName} key")
            validateSecretAction(keyAction.action, "secret player ${player.playerName} key", itemIds, monsterIds)
        }
        checkUniqueBindings(
            player.keyActions.map { it.binding },
            "secret player ${player.playerName}",
        )
    }
}

private fun validateSecretAction(
    action: SecretActionModel,
    label: String,
    itemIds: Set<ItemId>,
    monsterIds: Set<MonsterId>,
) {
    when (action) {
        is SecretActionModel.AddGold -> check(action.amount >= 0) { "$label adds negative gold" }
        is SecretActionModel.AddTimeLeft -> check(action.amount >= 0) { "$label adds negative time left" }
        is SecretActionModel.SetInventory -> {
            check(action.slot in 1..InventorySaveSlotMax) {
                "$label inventory slot must be in 1..$InventorySaveSlotMax"
            }
            check(action.item in itemIds) { "$label references missing item ${action.item.slug}" }
        }
        is SecretActionModel.SpawnEncounter ->
            check(action.monster in monsterIds) { "$label references missing monster ${action.monster.slug}" }
    }
}

private fun validateYdargKeys(keys: YdargKeysModel) {
    val navigate = listOf(
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
    val stairs = listOf(
        "teleport" to keys.stairsTeleport,
        "give" to keys.stairsGive,
        "leave" to keys.stairsLeave,
    )
    (navigate + treasure + stairs).forEach { (label, binding) ->
        validateInputBinding(binding, "Ydarg key $label")
    }
    checkUniqueBindings(navigate.map { it.second }, "Ydarg navigation")
    checkUniqueBindings(treasure.map { it.second }, "Ydarg treasure")
    checkUniqueBindings(stairs.map { it.second }, "Ydarg stairs")
}

private fun validateYdargText(text: YdargTextModel) {
    listOf(
        "ruin" to text.ruin,
        "approach" to text.approach,
        "Jaara form" to text.jaaraForm,
        "Jaara speech" to text.jaaraSpeech,
        "no key" to text.noKey,
        "key accepted" to text.keyAccepted,
        "enter" to text.enter,
        "door slams" to text.doorSlams,
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
        "stairs mage offer" to text.stairsMageOffer,
        "stairs menu" to text.stairsMenu,
        "stairs leave" to text.stairsLeave,
        "teleport vortex" to text.teleportVortex,
        "teleport outside" to text.teleportOutside,
        "give menu" to text.giveMenu,
        "mage grateful" to text.mageGrateful,
    ).forEach { (label, block) -> validateTextBlock(block, "Ydarg $label") }
    listOf(
        "act1" to text.confrontationAct1,
        "act2" to text.confrontationAct2,
        "act3" to text.confrontationAct3,
        "act4" to text.confrontationAct4,
    ).forEach { (label, blocks) ->
        blocks.forEachIndexed { index, block ->
            validateTextBlock(block, "Ydarg confrontation $label screen ${index + 1}")
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
    checkUniqueBindings(keys.caveKeys(), "wilderness cave")
    checkUniqueBindings(keys.outOfMovesKeys(), "wilderness out-of-moves")
    checkUniqueBindings(keys.quitConfirmKeys(), "wilderness quit-confirm")
    validateTextBlock(keys.commandHint, "wilderness command hint")
    keys.helpPages.forEachIndexed { index, page ->
        validateTextBlock(page, "wilderness help page ${index + 1}")
    }
}

private fun validateInputBinding(binding: InputBindingModel, label: String) {
    check(binding.primary.isNotEmpty()) { "$label has blank primary input" }
    check(binding.label.isNotBlank()) { "$label has blank display label" }
    check(binding.tokens.none { it.isEmpty() }) { "$label has blank input alias" }
    val tokens = binding.tokens.flatMap { inputTokenVariants(it).toSet() }
    check(tokens.toSet().size == tokens.size) { "$label has duplicate input aliases" }
}

private fun validateInputChoiceBinding(binding: InputBindingModel, label: String) {
    validateInputBinding(binding, label)
    check(binding.primary.length == 1) { "$label primary input must be one character for the current terminal adapter" }
}

private fun checkUniqueBindings(bindings: List<InputBindingModel>, label: String) {
    val tokens = bindings.flatMap { binding -> binding.tokens.flatMap(::inputTokenVariants).toSet() }
    check(tokens.toSet().size == tokens.size) {
        "$label keys must be unique"
    }
}

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
        check(town.services.map { it.building }.toSet().size == town.services.size) {
            "town ${town.id.slug} has duplicate services"
        }
        town.services.forEach { service ->
            check(service.building.slug.isNotBlank()) {
                "town ${town.id.slug} references blank service"
            }
        }
    }
}

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
    check(townSystem.entryTax.minimumLeaderLevel >= 0) {
        "town entry tax minimum leader level must be non-negative"
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

fun validateCharacters(characters: List<CharacterModel>, towns: List<TownModel> = emptyList()) {
    check(characters.map { it.id }.toSet().size == characters.size) { "duplicate character id" }
    val townsById = towns.associateBy { it.id }

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
            is CharacterPlacementModel.Fixed -> when (val position = placement.position) {
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
            CharacterPlacementModel.Randomized,
            CharacterPlacementModel.RandomizedWilderness -> Unit
        }
    }
}

private fun validateTextBlock(
    block: TextBlock,
    label: String,
    knownRoles: Set<NpcRole>? = null,
    knownTowns: Set<TownId>? = null,
    allowedRuntimeBindings: Set<TextPart> = EmptyTextBindings,
) {
    fun validatePart(part: TextPart) {
        when (part) {
            is TextPart.Literal -> Unit
            is TextPart.IntValue -> Unit
            is TextPart.StringValue -> Unit
            is TextPart.RoleName -> {
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
            TextPart.ConversationNpcName,
            TextPart.CurrentTownName,
            TextPart.TownTaxPercent,
            TextPart.TownTaxAmount,
            TextPart.CreationMemberNumber,
            TextPart.CreationMemberName,
            TextPart.CreationWeaponSkill,
            TextPart.CreationHealth,
            TextPart.CreationWisdom,
            TextPart.CreationCharisma -> check(part in allowedRuntimeBindings) {
                "$label uses runtime text binding ${part.fallbackText()}, but this text is not rendered with that binding"
            }
        }
    }

    when (block) {
        is TextBlock.Ref -> check(block.id.id.isNotBlank()) { "$label text id is blank" }
        is TextBlock.Inline -> {
            check(block.lines.isNotEmpty()) { "$label inline text has no lines" }
            check(block.lines.any { it.isNotBlank() }) { "$label inline text is blank" }
        }
        is TextBlock.Structured -> {
            check(block.lines.isNotEmpty()) { "$label structured text has no lines" }
            check(block.lines.any { line ->
                line.parts.any { it !is TextPart.Literal || it.text.isNotBlank() }
            }) {
                "$label structured text is blank"
            }
            block.lines.forEachIndexed { index, line ->
                check(line.parts.isNotEmpty()) { "$label line $index has no parts" }
                line.parts.forEach(::validatePart)
            }
        }
    }
}

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

        val expectedTopics = listOf(
            ConversationTopic.Chitchat,
            ConversationTopic.Question,
            ConversationTopic.Rumour,
            ConversationTopic.Bye,
        )
        check(conversation.menu.actions.map { it.topic } == expectedTopics) {
            "standard conversation top-level menu must be C/Q/R/B chitchat, question, rumour, bye"
        }

        val expectedQuestionTopics = listOf(
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

        check(conversation.chitchat.subjects == listOf(
            ChitchatSubject.Well,
            ChitchatSubject.Weather,
            ChitchatSubject.Health,
        )) {
            "standard conversation chitchat subjects must match well/weather/health roll"
        }
        check(conversation.chitchat.replies == ChitchatReplyPolicy.BySubjectAndNpcJob) {
            "standard conversation chitchat replies must dispatch by subject and NPC job"
        }

        check(conversation.rumour.usage == RumourUsagePolicy.OncePerConversationThenFallback) {
            "standard conversation rumours must be once per conversation with fallback text"
        }
        check(conversation.rumour.source == RumourSourcePolicy.StandardTownRumours) {
            "standard conversation rumours must use the standard town rumour source"
        }
        validateTextBlock(conversation.prompts.chooseNpc, "speak choose-npc prompt")
        validateTextBlock(
            conversation.prompts.mainMenu,
            "speak main-menu prompt",
            allowedRuntimeBindings = ConversationTextBindings,
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
        check(action.ops.isNotEmpty()) {
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
    val knownTowns = towns.map { it.id }.toSet()

    check(tables.screens.map { it.id }.toSet().size == tables.screens.size) {
        "duplicate reusable speak screen"
    }
    tables.screens.forEach { screen ->
        validateTextBlock(screen.text, "reusable speak screen ${screen.id.id}", knownTowns = knownTowns)
    }

    check(tables.chitchatPrompts.map { it.subject }.toSet().size == tables.chitchatPrompts.size) {
        "duplicate chitchat prompt table"
    }
    tables.chitchatPrompts.forEach { prompt ->
        check(prompt.variants.isNotEmpty()) { "chitchat prompt ${prompt.subject} has no variants" }
        check(prompt.variants.map { it.audience }.toSet().size == prompt.variants.size) {
            "chitchat prompt ${prompt.subject} has duplicate audience variants"
        }
        prompt.variants.forEach { variant ->
            validateTextBlock(
                variant.text,
                "chitchat prompt ${prompt.subject}.${variant.audience}",
                knownTowns = knownTowns,
            )
        }
    }

    check(tables.chitchatReplies.map { it.subject }.toSet().size == tables.chitchatReplies.size) {
        "duplicate chitchat reply table"
    }
    tables.chitchatReplies.forEach { replies ->
        validateJobReplies(replies.replies, "chitchat reply ${replies.subject}", knownRoles, knownTowns)
    }

    check(tables.questionReplies.map { it.topic }.toSet().size == tables.questionReplies.size) {
        "duplicate question reply table"
    }
    tables.questionReplies.forEach { replies ->
        check(replies.topic != QuestionTopic.Nothing) {
            "question reply table Nothing should not have canned replies"
        }
        validateJobReplies(replies.replies, "question reply ${replies.topic}", knownRoles, knownTowns)
    }

    check(tables.rumourTables.map { it.id }.toSet().size == tables.rumourTables.size) {
        "duplicate rumour table"
    }
    tables.rumourTables.forEach { table ->
        check(table.entries.isNotEmpty()) { "rumour table ${table.id.slug} has no entries" }
        val namedEntries = table.entries.mapNotNull { it.id }
        check(namedEntries.toSet().size == namedEntries.size) {
            "rumour table ${table.id.slug} has duplicate rumours"
        }
        table.entries.forEach { entry ->
            val label = entry.id?.slug ?: "anonymous"
            check(entry.excludedBy.toSet().size == entry.excludedBy.size) {
                "rumour $label has duplicate exclusion states"
            }
            entry.excludedBy.forEach { state ->
                check(state.flag.scope == FlagScope.Party) {
                    "rumour $label exclusion ${state.flag.slug}.${state.native} must be a party state"
                }
                flagStates?.requireKnown(state, "rumour $label exclusion")
            }
            validateTextBlock(entry.text, "rumour table ${table.id.slug}.$label", knownRoles, knownTowns)
        }
    }
}

private fun validateJobReplies(
    replies: List<JobTextModel>,
    label: String,
    knownRoles: Set<NpcRole>? = null,
    knownTowns: Set<TownId>? = null,
) {
    check(replies.isNotEmpty()) { "$label has no job replies" }
    check(replies.map { it.job }.toSet().size == replies.size) { "$label has duplicate job replies" }
    replies.forEach { reply ->
        check(reply.job.slug.isNotBlank()) { "$label references blank NPC job" }
        validateTextBlock(reply.text, "$label ${reply.job.slug}", knownRoles, knownTowns)
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
        check(itemUse.ops.isNotEmpty()) { "item use ${itemUse.id.slug} has no effect body" }

        val consumesInBody = itemUse.ops.containsConsumeActiveItem()
        when (itemUse.consume) {
            ItemConsumePolicy.BeforeUse -> check(!consumesInBody) {
                "item use ${itemUse.id.slug} consumes before use and should not consume in script"
            }
            ItemConsumePolicy.ScriptControlled -> check(consumesInBody) {
                "item use ${itemUse.id.slug} is script-controlled but never consumes active item"
            }
            ItemConsumePolicy.NeverConsumes -> check(!consumesInBody) {
                "item use ${itemUse.id.slug} is non-consuming but consumes active item in script"
            }
        }
    }
}

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
            check(branch.ops.isNotEmpty()) {
                "dialogue for ${npcDialogue.npc.slug} has an empty branch"
            }
        }
    }
}

private fun List<Op>.containsConsumeActiveItem(): Boolean =
    any { op ->
        when (op) {
            Op.ConsumeActiveItem -> true
            is Op.If -> op.thenOps.containsConsumeActiveItem() || op.elseOps.containsConsumeActiveItem()
            is Op.Loop -> op.body.containsConsumeActiveItem()
            is Op.Switch -> op.cases.any { it.body.containsConsumeActiveItem() }
            is Op.Menu -> op.options.any { it.body.containsConsumeActiveItem() } ||
                    op.defaultOps.containsConsumeActiveItem()
            is Op.ForEachMember -> op.body.containsConsumeActiveItem()
            is Op.ForEachMonster -> op.body.containsConsumeActiveItem()
            else -> false
        }
    }

fun validateScriptRewards(
    content: GameModel,
    itemRecords: List<ItemRecordModel>,
    flags: List<FlagRef>? = null,
) {
    val itemRecordById = itemRecords.associateBy { it.id }
    val flagStates = flags?.let(::FlagStateIndex)
    val knownRoles = content.characters.flatMap { it.roles }.toSet()
    val knownTowns = content.towns.map { it.id }.toSet()
    val knownScenes = content.scenes.map { it.id }.toSet()
    val knownMonsters = content.monsters.map { it.id }.toSet()
    val knownEncounterTiers = content.monsters.mapNotNull { it.tier }.toSet()
    val knownMilestones = content.progress.milestones.map { it.id }.toSet()
    val dungeonScenarios = content.dungeons.scenarios.associateBy { it.dungeon }

    fun validateSlottedItemPool(pool: ItemPoolId, label: String) {
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

    validateIntExpression = fun(ast: IntAst, label: String) {
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
            is IntAst.RealmScalar -> Unit
            is IntAst.PartyScalar -> Unit
            is IntAst.InventoryPoolCount -> validateSlottedItemPool(ast.pool, label)
            is IntAst.InventorySlotCost -> validateIntExpression(ast.slot, label)
            is IntAst.NativeInt -> Unit
        }
    }

    validateBoolExpression = fun(ast: BoolAst, label: String) {
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
            allowedRuntimeBindings = RandomItemOfferTextBindings,
        )
        validateTextBlock(offer.choicePrompt, "random item offer ${offer.pool.slug} choice prompt")
        validateTextBlock(offer.broke, "random item offer ${offer.pool.slug} broke")
        check(offer.application.regularItems == RewardItemPlacement.FirstFreeInventorySlot) {
            "random item offer ${offer.pool.slug} only supports first-free-slot placement for now"
        }
        val cantCarry = checkNotNull(offer.messages.cantCarry) {
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

    fun validateShopPrice(price: ShopPriceModel, label: String) {
        when (price) {
            ShopPriceModel.ItemCost,
            ShopPriceModel.HalfItemCost -> Unit
            is ShopPriceModel.Fixed -> check(price.amount >= 0) { "$label fixed price is negative" }
            is ShopPriceModel.LeaderLevelPlus -> check(price.addend >= 0) {
                "$label leader-level addend is negative"
            }
            is ShopPriceModel.ItemCostPlusLeaderLevelTimes -> check(price.multiplier >= 0) {
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
        val shopMenuKeys = buildList {
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
        }

        shop.buy?.let { buy ->
            validateTextBlock(buy.prompt, "shop ${shop.building.slug} buy prompt")
            validateTextBlock(buy.tooExpensive, "shop ${shop.building.slug} too-expensive")
            validateTextBlock(buy.cantCarry, "shop ${shop.building.slug} cant-carry")
            validateTextBlock(buy.deal, "shop ${shop.building.slug} deal")
            buy.unitPrice?.let {
                validateTextBlock(
                    it,
                    "shop ${shop.building.slug} unit-price",
                    allowedRuntimeBindings = ShopPriceTextBindings,
                )
            }
            buy.singlePrice?.let {
                validateTextBlock(
                    it,
                    "shop ${shop.building.slug} single-price",
                    allowedRuntimeBindings = ShopPriceTextBindings,
                )
            }
            validateTextBlock(buy.payPrompt, "shop ${shop.building.slug} pay prompt")
            validateInputChoiceBinding(buy.paymentKeys.pay, "shop ${shop.building.slug} pay key")
            validateInputChoiceBinding(buy.paymentKeys.decline, "shop ${shop.building.slug} decline-purchase key")
            checkUniqueBindings(
                listOf(buy.paymentKeys.pay, buy.paymentKeys.decline),
                "shop ${shop.building.slug} purchase confirmation",
            )
            check(buy.catalogs.isNotEmpty()) { "shop ${shop.building.slug} buy section has no catalogs" }
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
                    page.entries.forEach { entry ->
                        validateInputChoiceBinding(
                            entry.binding,
                            "shop ${shop.building.slug} catalog ${catalog.title} item ${entry.item.slug} key",
                        )
                    }
                    checkUniqueBindings(
                        page.entries.map { it.binding },
                        "shop ${shop.building.slug} catalog ${catalog.title} page $pageIndex",
                    )
                    page.footer?.let {
                        validateTextBlock(it, "shop ${shop.building.slug} ${catalog.title} page $pageIndex footer")
                    }
                    page.entries.forEach { entry ->
                        val record = checkNotNull(itemRecordById[entry.item]) {
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
                            ShopPurchaseModel.InventoryItem -> check(record.storage == ItemStorageKind.SlottedInventory) {
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
                                    allowedRuntimeBindings = ShopCapacityTextBindings,
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
                allowedRuntimeBindings = ShopPriceTextBindings,
            )
            sell.sold?.let { validateTextBlock(it, "shop ${shop.building.slug} sold") }
            validateShopPrice(sell.price, "shop ${shop.building.slug} sell")
            validateInputChoiceBinding(sell.saleKeys.accept, "shop ${shop.building.slug} sale accept key")
            validateInputChoiceBinding(sell.saleKeys.decline, "shop ${shop.building.slug} sale decline key")
            checkUniqueBindings(
                listOf(sell.saleKeys.accept, sell.saleKeys.decline),
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

    fun requireEncounterTier(tier: EncounterTier, label: String) {
        check(tier.slug.isNotBlank()) { "$label references blank encounter tier" }
        check(tier in knownEncounterTiers) { "$label tier ${tier.slug} has no authored monsters" }
    }

    fun requireDungeonScenario(dungeon: DungeonId, label: String, levels: Int? = null) {
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

    fun visit(ops: List<Op>) {
        fun visitOps(items: List<Op>, loopDepth: Int) {
            items.forEach { op ->
                when (op) {
                    is Op.Screen -> validateTextBlock(op.text, "screen", knownRoles, knownTowns)
                    is Op.EmitIndexed -> validateIntExpression(op.value, "indexed text emit")
                    is Op.LetInt -> validateIntExpression(op.value, "local int")
                    is Op.Set -> validateIntExpression(op.value, "state assignment")
                    is Op.Add -> validateIntExpression(op.value, "state add")
                    is Op.Sub -> validateIntExpression(op.value, "state subtract")
                    is Op.AddClamped -> {
                        validateIntExpression(op.value, "state clamped add")
                        validateIntExpression(op.max, "state clamped add max")
                    }
                    is Op.AppendJournal -> validateTextBlock(op.text, "journal append", knownRoles, knownTowns)
                    is Op.Menu -> {
                        validateTextBlock(op.prompt, "menu prompt")
                        op.options.forEach { option ->
                            validateInputChoiceBinding(option.binding, "script menu option ${option.key}")
                        }
                        checkUniqueBindings(op.options.map { it.binding }, "script menu")
                        op.options.forEach { visitOps(it.body, loopDepth) }
                        visitOps(op.defaultOps, loopDepth)
                    }
                    is Op.AskKey -> {
                        validateTextBlock(op.prompt, "key choice prompt")
                        check(op.bindings.isNotEmpty()) { "key choice prompt must accept at least one key" }
                        op.bindings.forEach { binding ->
                            validateInputChoiceBinding(binding, "key choice ${binding.label}")
                        }
                        checkUniqueBindings(op.bindings, "key choice prompt")
                    }
                    is Op.AskMember -> validateTextBlock(op.prompt, "member choice prompt")
                    is Op.AskInventorySlot -> {
                        validateTextBlock(op.prompt, "inventory-slot choice prompt")
                        check(op.firstKey.isLetter()) { "inventory-slot choice first key must be a letter" }
                        check(op.slots.first >= 0) { "inventory-slot choice starts before slot 0" }
                        check(op.slots.last <= InventorySaveSlotMax) {
                            "inventory-slot choice ends after slot $InventorySaveSlotMax"
                        }
                    }
                    is Op.AskText -> {
                        validateTextBlock(op.prompt, "text answer prompt")
                        check(op.accepted.isNotEmpty()) { "text answer prompt must accept at least one value" }
                        check(op.accepted.none { it.isEmpty() }) { "text answer prompt cannot accept an empty value" }
                        op.maxLength?.let { max ->
                            check(max > 0) { "text answer max length must be positive" }
                        }
                        visitOps(op.matchOps, loopDepth)
                        visitOps(op.missOps, loopDepth)
                    }
                    is Op.GrantReward -> validateReward(op.reward)
                    is Op.RandomItemOffer -> validateRandomItemOffer(op.offer)
                    is Op.ShopService -> validateShop(op.shop)
                    is Op.SetFlag -> flagStates?.requireKnown(op.slug, op.nativeValue, "script state change")
                    is Op.ConsumeInventoryPool -> {
                        validateSlottedItemPool(op.pool, "consume inventory")
                        validateIntExpression(op.quantity, "consume inventory quantity")
                        (op.quantity as? IntAst.Lit)?.let { quantity ->
                            check(quantity.value > 0) { "consume inventory quantity must be positive" }
                        }
                    }
                    is Op.ClearInventorySlot -> validateIntExpression(op.slot, "clear inventory slot")
                    is Op.EnterDungeon -> {
                        check(op.levels > 0) { "script dungeon handoff must use positive level count" }
                        requireDungeonScenario(op.dungeon, "script dungeon handoff", op.levels)
                    }
                    is Op.TeleportParty -> {
                        validateIntExpression(op.directionKey, "teleport direction key")
                        validateIntExpression(op.distance, "teleport distance")
                    }
                    is Op.DiscoverCave -> {
                        validateIntExpression(op.levels, "discover cave level count")
                        validateIntExpression(op.moveCost, "discover cave move cost")
                        val literalLevels = (op.levels as? IntAst.Lit)?.value
                        literalLevels?.let { check(it > 0) { "discover cave level count must be positive" } }
                        requireDungeonScenario(op.dungeon, "discover cave", literalLevels)
                    }
                    is Op.If -> {
                        validateBoolExpression(op.cond, "script condition")
                        visitOps(op.thenOps, loopDepth)
                        visitOps(op.elseOps, loopDepth)
                    }
                    is Op.Loop -> visitOps(op.body, loopDepth + 1)
                    is Op.Break -> check(loopDepth > 0) {
                        "breakLoop(${op.reason ?: ""}) must be inside loop { ... }"
                    }
                    is Op.Switch -> {
                        validateIntExpression(op.expr, "script switch")
                        op.cases.forEach { visitOps(it.body, loopDepth) }
                    }
                    is Op.RunScene -> check(op.scene in knownScenes) {
                        "script run references missing scene ${op.scene.slug}"
                    }
                    is Op.Encounter -> {
                        check(op.foe in knownMonsters) {
                            "script encounter references unknown monster ${op.foe.slug}"
                        }
                        op.tier?.let { tier -> requireEncounterTier(tier, "script encounter ${op.foe.slug}") }
                    }
                    is Op.AchieveMilestone -> check(op.milestone in knownMilestones) {
                        "script achieve references unknown milestone ${op.milestone.slug}"
                    }
                    is Op.ForEachMember -> visitOps(op.body, loopDepth)
                    is Op.ForEachMonster -> visitOps(op.body, loopDepth)
                    else -> Unit
                }
            }
        }

        visitOps(ops, loopDepth = 0)
    }

    content.scenes.forEach { visit(it.ops) }
    content.dungeons.events.forEach { visit(it.ops) }
    content.dialogue.forEach { dialogue ->
        dialogue.branches.forEach { visit(it.ops) }
    }
    content.speak.questionActions.forEach { visit(it.ops) }
    content.spells.forEach { visit(it.ops) }
    content.itemUses.forEach { visit(it.ops) }
}

private fun validateItemPool(pool: ItemPoolId, itemRecords: List<ItemRecordModel>) {
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

private fun checkPercent(value: Int, label: String) {
    check(value in 0..100) { "$label must be 0..100" }
}

private fun validateRealmContent(realm: RealmContentModel) {
    realm.baseResourceCapacity.forEach { (resource, amount) ->
        check(amount >= 0) { "base ${resource.name.lowercase()} capacity must be non-negative" }
    }
    checkPercent(realm.upkeep.manaRegenChance, "wilderness mana regen chance")
    realm.upkeep.fatigue.forEachIndexed { index, rule ->
        validateTextBlock(rule.text, "wilderness fatigue rule $index")
    }
    validateCampRules(realm.camp)
    realm.hunt?.let(::validateHuntRules)
}

private fun validateCharacterCreation(model: CharacterCreationModel?, worldMaps: List<WorldMapModel>) {
    if (model == null) return

    check(model.races.isNotEmpty()) { "character creation needs at least one race option" }
    check(model.races.map { it.race }.toSet().size == model.races.size) {
        "character creation has duplicate race options"
    }
    model.races.forEach { option ->
        check(option.label.isNotBlank()) { "character creation race ${option.race.name} has blank label" }
    }

    check(model.classes.isNotEmpty()) { "character creation needs at least one class option" }
    check(model.classes.map { it.klass }.toSet().size == model.classes.size) {
        "character creation has duplicate class options"
    }
    model.classes.forEach { option ->
        check(option.label.isNotBlank()) { "character creation class ${option.klass.name} has blank label" }
        option.failureText?.let { validateTextBlock(it, "character creation ${option.klass.name} failure text") }
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
        "guard member prompt" to camp.guardMemberPrompt,
        "guard member dead" to camp.guardMemberDead,
        "stands guard" to camp.standsGuard,
        "sleeping" to camp.sleeping,
        "rested" to camp.rested,
        "ambush" to camp.ambush,
        "surprise kill" to camp.surpriseKill,
    ).forEach { (label, block) ->
        validateTextBlock(block, "camp $label")
    }
    camp.temperatureMessage.forEach { (band, block) ->
        validateTextBlock(block, "camp ${band.name.lowercase()} temperature message")
    }
    camp.noShelterMessage.forEach { (band, block) ->
        validateTextBlock(block, "camp ${band.name.lowercase()} no-shelter message")
    }
}

private fun validateHuntRules(hunt: HuntRulesModel) {
    validateTextBlock(hunt.whoPrompt, "hunt who prompt", allowedRuntimeBindings = HuntTextBindings)
    validateTextBlock(hunt.dead, "hunt dead")
    validateTextBlock(hunt.notRanger, "hunt not-ranger", allowedRuntimeBindings = HuntTextBindings)
    validateTextBlock(hunt.tooFewMoves, "hunt too-few-moves")
    validateTextBlock(hunt.tries, "hunt tries", allowedRuntimeBindings = HuntTextBindings)
    validateTextBlock(hunt.succeeds, "hunt succeeds", allowedRuntimeBindings = HuntTextBindings)
    validateTextBlock(hunt.fails, "hunt fails", allowedRuntimeBindings = HuntTextBindings)
}

private fun validateTerminalPresentation(model: TerminalPresentationModel, terrain: TerrainCatalogModel) {
    check(model.terrain.map { it.terrain }.toSet().size == model.terrain.size) {
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
    check(model.terrain.map { it.id }.toSet().size == model.terrain.size) {
        "duplicate terrain declaration"
    }
    check(model.terrain.map { it.mapCode }.toSet().size == model.terrain.size) {
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
    check(layouts.regions.map { it.id }.toSet().size == layouts.regions.size) {
        "duplicate screen region"
    }
    layouts.regions.forEach { region ->
        check(region.x >= 0 && region.y >= 0) { "screen region ${region.id.slug} has negative coordinates" }
        region.width?.let { check(it > 0) { "screen region ${region.id.slug} has non-positive width" } }
        region.height?.let { check(it > 0) { "screen region ${region.id.slug} has non-positive height" } }
    }
}

private fun validateVisuals(visuals: VisualsModel) {
    check(visuals.images.map { it.id }.toSet().size == visuals.images.size) {
        "duplicate visual image asset"
    }
    visuals.images.forEach { image ->
        check(image.id.slug.isNotBlank()) { "visual image has blank id" }
        validateRelativeResource(image.resource, "visual image ${image.id.slug}")
    }
    check(visuals.staticScreens.map { it.id }.toSet().size == visuals.staticScreens.size) {
        "duplicate static screen"
    }
    val imageIds = visuals.images.map { it.id }.toSet()
    visuals.staticScreens.forEach { screen ->
        val sourceCount = listOf(
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

private fun validateRelativeResource(resource: String, label: String) {
    check(resource.isNotBlank()) { "$label has blank resource" }
    check(!resource.startsWith("/")) { "$label resource must be relative" }
    check(resource.split('/').none { it == ".." }) {
        "$label resource must stay inside packaged resources"
    }
}

private fun validateImageReferences(content: GameModel) {
    val declared = content.visuals.images.map { it.id }.toSet()
    val references = buildList {
        content.visuals.staticScreens.forEach { screen ->
            screen.image?.let { add(it to "static screen ${screen.id.slug}") }
        }
        content.itemCatalog.forEach { item ->
            item.campImage?.let { add(it to "item ${item.id.slug} camp image") }
        }
        content.wilderness.events.caveImage?.let { add(it to "wilderness cave image") }
        content.dungeons.backdrop?.let { add(it to "dungeon backdrop") }
        content.ydarg?.image?.let { add(it to "Ydarg backdrop") }
        content.combat.standardBattle?.let { battle ->
            battle.opening.banners.forEach { add(it to "battle opening banner") }
            battle.victoryImage?.let { add(it to "battle victory image") }
        }
        content.scenes.forEach { scene -> addAll(scene.ops.imageReferences("scene ${scene.id.slug}")) }
        content.dungeons.events.forEach { event -> addAll(event.ops.imageReferences("dungeon event ${event.id.slug}")) }
        content.dialogue.forEach { dialogue ->
            dialogue.branches.forEach { branch ->
                addAll(branch.ops.imageReferences("dialogue ${dialogue.npc.slug}"))
            }
        }
        content.spells.forEach { spell -> addAll(spell.ops.imageReferences("spell ${spell.id.slug}")) }
        content.itemUses.forEach { itemUse -> addAll(itemUse.ops.imageReferences("item use ${itemUse.id.slug}")) }
    }
    references.forEach { (image, label) ->
        check(image in declared) { "$label references undeclared image ${image.slug}" }
    }
}

private fun List<Op>.imageReferences(label: String): List<Pair<ImageId, String>> =
    buildList {
        fun visit(ops: List<Op>, nestedLabel: String) {
            ops.forEach { op ->
                when (op) {
                    is Op.View -> {
                        val image = (op.command as? ViewCommand.ShowImage)?.image
                        if (image != null) add(image to nestedLabel)
                    }
                    is Op.If -> {
                        visit(op.thenOps, nestedLabel)
                        visit(op.elseOps, nestedLabel)
                    }
                    is Op.Loop -> visit(op.body, nestedLabel)
                    is Op.Switch -> op.cases.forEach { visit(it.body, nestedLabel) }
                    is Op.Menu -> {
                        op.options.forEach { option -> visit(option.body, nestedLabel) }
                        visit(op.defaultOps, nestedLabel)
                    }
                    is Op.AskText -> {
                        visit(op.matchOps, nestedLabel)
                        visit(op.missOps, nestedLabel)
                    }
                    is Op.ForEachMember -> visit(op.body, nestedLabel)
                    is Op.ForEachMonster -> visit(op.body, nestedLabel)
                    is Op.ShopService -> add(op.shop.image to "$nestedLabel shop")
                    is Op.Screen,
                    is Op.Emit,
                    is Op.EmitIndexed,
                    is Op.WaitInput,
                    is Op.Pause,
                    is Op.LetInt,
                    is Op.BindText,
                    is Op.Set,
                    is Op.Add,
                    is Op.Sub,
                    is Op.AddClamped,
                    is Op.SetFlag,
                    is Op.AppendJournal,
                    is Op.Break,
                    is Op.Stop,
                    is Op.AskKey,
                    is Op.AskMember,
                    is Op.AskInventorySlot,
                    is Op.AskMonster,
                    is Op.Speak,
                    is Op.TellRumour,
                    is Op.RunScene,
                    is Op.Encounter,
                    is Op.EnterDungeon,
                    Op.ConsumeActiveItem,
                    is Op.ClearInventorySlot,
                    is Op.ConsumeInventoryPool,
                    Op.ConsumeTorch,
                    is Op.TeleportParty,
                    is Op.DiscoverCave,
                    is Op.GrantReward,
                    is Op.RandomItemOffer,
                    is Op.AchieveMilestone,
                    is Op.NativeCall -> Unit
                }
            }
        }
        visit(this@imageReferences, label)
    }

private fun validateWorldMaps(worldMaps: List<WorldMapModel>, terrain: TerrainCatalogModel) {
    check(worldMaps.map { it.id }.toSet().size == worldMaps.size) {
        "duplicate world map"
    }
    val terrainCodes = terrain.terrain.map { it.mapCode }.toSet()
    worldMaps.forEach { map ->
        check(map.layers.map { it.id }.toSet().size == map.layers.size) {
            "world map ${map.id.slug} has duplicate layers"
        }
        val terrainLayer = map.layers.singleOrNull { it.id == DslDefaultMapLayers.terrain }
            ?: error("world map ${map.id.slug} requires terrain layer")
        check(terrainLayer.rows.isNotEmpty()) { "world map ${map.id.slug} terrain layer has no rows" }
        val width = terrainLayer.rows.first().length
        check(width > 0) { "world map ${map.id.slug} terrain layer has blank first row" }
        val height = terrainLayer.rows.size
        map.size?.let { size ->
            check(size.width > 0 && size.height > 0) {
                "world map ${map.id.slug} size must be positive"
            }
            check(size.width == width && size.height == height) {
                "world map ${map.id.slug} declares size ${size.width}x${size.height}, " +
                    "but terrain layer is ${width}x$height"
            }
        }
        map.storageCapacity?.let { capacity ->
            check(capacity >= 0) { "world map ${map.id.slug} storage capacity must be non-negative" }
            check(capacity >= width * height) {
                "world map ${map.id.slug} storage capacity $capacity is smaller than terrain layer ${width * height}"
            }
        }
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

private fun validateFlagReferences(ast: BoolAst, label: String, flagStates: FlagStateIndex) {
    lateinit var bool: (BoolAst) -> Unit
    lateinit var int: (IntAst) -> Unit

    int = fun(value: IntAst) {
        when (value) {
            is IntAst.Lit,
            is IntAst.Var,
            is IntAst.BattleFlag,
            is IntAst.BattleQuery,
            is IntAst.RealmScalar,
            is IntAst.PartyScalar,
            is IntAst.InventoryPoolCount,
            is IntAst.NativeInt -> Unit
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

    bool = fun(value: BoolAst) {
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
            is BoolAst.RuntimeFlag -> Unit
        }
    }

    bool(ast)
}

private fun validateFlagTransitions(content: GameModel, flagStates: FlagStateIndex) {
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

    fun validateTransition(
        slug: String,
        native: String,
        context: Map<String, FlagState>,
        label: String,
    ) {
        flagStates.requireKnown(slug, native, label)
        val source = context[slug] ?: return
        if (source.native == native) return

        val sourceOrder = checkNotNull(flagStates.order(source)) {
            "flag transition source ${source.flag.slug}.${source.native} is not declared"
        }
        val targetOrder = checkNotNull(flagStates.order(slug, native)) {
            "flag transition target $slug.$native is not declared"
        }
        check(targetOrder >= sourceOrder) {
            "$label moves ${source.flag.slug} backward from ${source.native} to $native"
        }
    }

    fun visitOps(
        ops: List<Op>,
        context: Map<String, FlagState>,
        label: String,
    ) {
        ops.forEach { op ->
            when (op) {
                is Op.SetFlag -> validateTransition(op.slug, op.nativeValue, context, label)
                is Op.If -> {
                    visitOps(op.thenOps, combineStates(context, statesRequiredBy(op.cond)), "$label if-then")
                    visitOps(op.elseOps, context, "$label if-else")
                }
                is Op.Loop -> visitOps(op.body, context, "$label loop")
                is Op.Switch -> op.cases.forEachIndexed { index, case ->
                    visitOps(case.body, context, "$label switch case ${index + 1}")
                }
                is Op.Menu -> {
                    op.options.forEach { option ->
                        visitOps(option.body, context, "$label menu ${option.key}")
                    }
                    visitOps(op.defaultOps, context, "$label menu default")
                }
                is Op.AskText -> {
                    visitOps(op.matchOps, context, "$label text-answer match")
                    visitOps(op.missOps, context, "$label text-answer miss")
                }
                is Op.ForEachMember -> visitOps(op.body, context, "$label member loop")
                is Op.ForEachMonster -> visitOps(op.body, context, "$label monster loop")
                else -> Unit
            }
        }
    }

    content.scenes.forEach { scene ->
        visitOps(scene.ops, emptyMap(), "scene ${scene.id.slug}")
    }
    content.dungeons.events.forEach { event ->
        visitOps(event.ops, emptyMap(), "dungeon event ${event.id.slug}")
    }
    content.dialogue.forEach { dialogue ->
        dialogue.branches.forEach { branch ->
            val context = branch.whenState?.let { mapOf(it.flag.slug to it) }.orEmpty()
            val label = branch.whenState
                ?.let { "dialogue ${dialogue.npc.slug} when ${it.flag.slug}.${it.native}" }
                ?: "dialogue ${dialogue.npc.slug} default"
            visitOps(branch.ops, context, label)
        }
    }
    content.speak.questionActions.forEach { action ->
        visitOps(action.ops, emptyMap(), "speak ${action.topic} ${action.job.slug}")
    }
    content.spells.forEach { spell ->
        visitOps(spell.ops, emptyMap(), "spell ${spell.id.slug}")
    }
    content.itemUses.forEach { itemUse ->
        visitOps(itemUse.ops, emptyMap(), "item use ${itemUse.id.slug}")
    }
    content.missions.forEach { mission ->
        val scene = scenesById[mission.action] ?: return@forEach
        visitOps(scene.ops, statesRequiredBy(mission.requires), "mission ${mission.id.slug}")
    }
}

fun collectAuthoredContentDiagnostics(content: GameModel): List<ValidationDiagnostic> {
    val flagStates = FlagStateIndex(content.flags)
    val terrainBindings = TerrainBindingIndex(content.terrain, content.terminal, content.worldMaps)
    return buildList {
        captureValidation("metadata") { validateGameMetadata(content.metadata) }
        captureValidation("limits") { validateGameLimits(content.limits, content.worldMaps, content.layouts) }
        captureValidation("flags.catalog") { validateFlagCatalog(content.flags) }
        captureValidation("prompts") { validateGamePrompts(content.prompts) }
        captureValidation("realm") { validateRealmContent(content.realm) }
        captureValidation("visuals") { validateVisuals(content.visuals) }
        captureValidation("visuals.references") { validateImageReferences(content) }
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
        captureValidation("rewards") { validateRewardBundles(content.rewards, content.formulas, content.itemCatalog) }
        captureValidation("combat") { validateCombatSystem(content.combat, content.rewards) }
        captureValidation("wilderness") { validateWildernessSystem(content.wilderness, terrainBindings) }
        captureValidation("dungeons") { validateDungeonSystem(content.dungeons, content.monsters) }
        captureValidation("ydarg") {
            validateYdargSystem(content.ydarg, content.itemCatalog, content.progress, content.monsters, content.worldMaps)
        }
        captureValidation("secrets") { validateSecretSystem(content.secrets, content.itemCatalog, content.monsters) }
        captureValidation("towns") { validateTowns(content.towns) }
        captureValidation("townSystem") { validateTownSystem(content.townSystem, content.towns, content.scenes) }
        captureValidation("characters") { validateCharacters(content.characters, content.towns) }
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

private fun validateGameLimits(limits: GameLimitsModel, worldMaps: List<WorldMapModel>, layouts: LayoutModel) {
    limits.maxWorldMaps?.let { max ->
        check(max > 0) { "max world maps must be positive" }
        check(worldMaps.size <= max) { "declares ${worldMaps.size} world maps, but limit is $max" }
    }
    limits.maxMapCells?.let { max ->
        check(max > 0) { "max map cells must be positive" }
        worldMaps.forEach { map ->
            val rows = map.layers.singleOrNull { it.id == DslDefaultMapLayers.terrain }?.rows.orEmpty()
            val cells = rows.size * (rows.firstOrNull()?.length ?: 0)
            check(cells <= max) { "world map ${map.id.slug} uses $cells cells, but limit is $max" }
        }
    }
    limits.screenSize?.let { screen ->
        check(screen.width > 0 && screen.height > 0) { "screen size must be positive" }
        layouts.regions.forEach { region ->
            check(region.x < screen.width && region.y < screen.height) {
                "screen region ${region.id.slug} starts outside screen ${screen.width}x${screen.height}"
            }
            region.width?.let { width ->
                check(region.x + width <= screen.width) {
                    "screen region ${region.id.slug} exceeds screen width ${screen.width}"
                }
            }
            region.height?.let { height ->
                check(region.y + height <= screen.height) {
                    "screen region ${region.id.slug} exceeds screen height ${screen.height}"
                }
            }
        }
    }
}

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
    validateTextBlock(prompts.priest.who, "priest who prompt", allowedRuntimeBindings = HuntTextBindings)
    validateTextBlock(prompts.priest.mageCombatOnly, "priest mage-combat-only prompt")
    validateTextBlock(prompts.priest.wrongClass, "priest wrong-class prompt")
    validateTextBlock(prompts.priest.dead, "priest dead prompt")
    validateTextBlock(prompts.priest.invalidCaster, "priest invalid-caster prompt")
    validateTextBlock(prompts.priest.invalidSpell, "priest invalid-spell prompt")
    validateTextBlock(prompts.priest.noSpells, "priest no-spells prompt")
    validateTextBlock(prompts.inventoryUse.use, "inventory use prompt")
    validateTextBlock(prompts.inventoryUse.quaff, "inventory quaff prompt")
    validateTextBlock(prompts.inventoryUse.noItems, "inventory no-items prompt")
    validateTextBlock(prompts.inventoryUse.noItem, "inventory no-item prompt")
    validateTextBlock(prompts.equipment.inventory, "equipment inventory prompt")
    validateTextBlock(prompts.equipment.who, "equipment who prompt")
    validateTextBlock(prompts.equipment.readyWeapons, "equipment ready-weapons prompt")
    validateTextBlock(prompts.equipment.readyArmors, "equipment ready-armors prompt")
    validateTextBlock(prompts.save.saving, "save saving prompt")
    validateTextBlock(prompts.save.done, "save done prompt")
    validateTextBlock(prompts.save.failed, "save failed prompt")
    validateTextBlock(prompts.characterCreation.header, "character creation header prompt")
    validateTextBlock(prompts.characterCreation.partyName, "character creation party-name prompt")
    validateTextBlock(
        prompts.characterCreation.statsPage,
        "character creation stats-page prompt",
        allowedRuntimeBindings = CharacterCreationStatsTextBindings,
    )
    validateTextBlock(prompts.characterCreation.reroll, "character creation reroll prompt")
    validateTextBlock(
        prompts.characterCreation.memberName,
        "character creation member-name prompt",
        allowedRuntimeBindings = CharacterCreationMemberNumberTextBindings,
    )
    validateTextBlock(
        prompts.characterCreation.gender,
        "character creation gender prompt",
        allowedRuntimeBindings = CharacterCreationMemberNameTextBindings,
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
        allowedRuntimeBindings = CharacterCreationMemberNameTextBindings,
    )
    validateTextBlock(prompts.characterCreation.classMenu, "character creation class-menu prompt")
    validateTextBlock(prompts.characterCreation.saving, "character creation saving prompt")
    validateTextBlock(prompts.town.entryWelcome, "town entry welcome prompt", allowedRuntimeBindings = TownEntryTaxTextBindings)
    validateTextBlock(prompts.town.entryTax, "town entry tax prompt", allowedRuntimeBindings = TownEntryTaxTextBindings)
    validateTextBlock(prompts.town.entryTaxThanks, "town entry tax thanks prompt")
    validateTextBlock(prompts.wilderness.pass, "wilderness pass prompt")
    validateTextBlock(prompts.wilderness.noMoves, "wilderness no-moves prompt")
    validateTextBlock(prompts.wilderness.outOfMoves, "wilderness out-of-moves prompt")
    validateTextBlock(prompts.wilderness.notInWilderness, "wilderness not-in-wilderness prompt")
    validateTextBlock(prompts.wilderness.nothingToEnter, "wilderness nothing-to-enter prompt")
    validateTextBlock(prompts.wilderness.quitConfirm, "wilderness quit-confirm prompt")
    validateTextBlock(
        prompts.wilderness.dungeonHandoff,
        "wilderness dungeon-handoff prompt",
        allowedRuntimeBindings = WildernessDungeonHandoffTextBindings,
    )
    validateTextBlock(prompts.dungeon.levelMenu, "dungeon level-menu prompt", allowedRuntimeBindings = DungeonDepthTextBindings)
    validateTextBlock(prompts.dungeon.enterLevel, "dungeon enter-level prompt", allowedRuntimeBindings = DungeonDepthTextBindings)
    validateTextBlock(prompts.dungeon.atMaxDepth, "dungeon at-max-depth prompt")
    validateTextBlock(prompts.dungeon.noTorches, "dungeon no-torches prompt")
    validateTextBlock(prompts.dungeon.leaveCavern, "dungeon leave-cavern prompt")
    validateTextBlock(prompts.party.viewWho, "party view-who prompt", allowedRuntimeBindings = HuntTextBindings)
    validateTextBlock(prompts.party.journalHeader, "party journal-header prompt")
    validateTextBlock(prompts.party.dropPrompt, "party drop prompt")
    validateTextBlock(prompts.party.dropEmptySlot, "party drop-empty-slot prompt")
    validateTextBlock(prompts.party.dropAborted, "party drop-aborted prompt")
    validateTextBlock(prompts.party.dropDropped, "party drop-dropped prompt", allowedRuntimeBindings = SelectedItemTextBindings)
    validateTextBlock(prompts.party.dropCapacityReduced, "party drop-capacity-reduced prompt")
}

private fun validateItemPools(itemPools: List<ItemPoolId>, itemCatalog: List<ItemRecordModel>) {
    check(itemPools.map { it.slug }.toSet().size == itemPools.size) {
        "duplicate item pool"
    }
    itemPools.forEach { validateItemPool(it, itemCatalog) }
}

private fun validateAuthoredFlags(content: GameModel, flagStates: FlagStateIndex) {
    val declaredFlags = content.flags.toSet()
    check(content.authoredFlags.map { it.ref.slug }.toSet().size == content.authoredFlags.size) {
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
    check(content.missions.map { it.id }.toSet().size == content.missions.size) {
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
            is Trigger.AtLocation -> check(trigger.location.slug.isNotBlank()) {
                "mission ${mission.id.slug} location trigger references blank location"
            }
            is Trigger.OnTerrain -> terrainBindings.requireMapReachable(
                trigger.terrain,
                "mission ${mission.id.slug} terrain trigger",
            )
        }
    }
}

fun specialInventoryItems(content: GameModel, party: PartyState): List<ItemId> =
    party.milestones.toSet().let { achieved ->
        content.progress.milestones
            .filter { it.id in achieved }
            .mapNotNull { it.specialInventoryItem }
    }

fun isWinningConfiguration(content: GameModel, party: PartyState): Boolean =
    content.progress.win?.let { win ->
        party.milestones.toSet().let { achieved ->
            (win.completedBy?.let { it in achieved } ?: true) &&
                    win.required.all { it in achieved }
        }
    } ?: false

fun validateStates(states: List<StateModel>, content: GameModel? = null) {
    check(states.map { it.id }.toSet().size == states.size) { "duplicate state id" }

    val itemIds = content?.itemCatalog?.map { it.id }?.toSet().orEmpty()
    val milestoneIds = content?.progress?.milestones?.map { it.id }?.toSet().orEmpty()
    val flagStates = content?.flags?.let(::FlagStateIndex)
    val npcIds = content?.characters?.map { it.id }?.toSet().orEmpty()
    val mapIds = content?.worldMaps?.map { it.id }?.toSet().orEmpty()
    val terrainBindings = content?.let { TerrainBindingIndex(it.terrain, it.terminal, it.worldMaps) }
    val specialItems = content
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
            check(slots.all { it in 0..InventorySaveSlotMax }) {
                "party ${party.id.slug}: inventory slot outside 0..$InventorySaveSlotMax"
            }
            check(slots.toSet().size == slots.size) { "party ${party.id.slug}: duplicate inventory slot" }
            party.counters.memberCount?.let { count ->
                check(count == party.members.size) {
                    "party ${party.id.slug}: memberCount $count does not match ${party.members.size} member records"
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
            check(party.flags.map { it.flag }.toSet().size == party.flags.size) {
                "party ${party.id.slug}: duplicate party flag state"
            }
            flagStates?.let { statesByFlag ->
                party.flags.forEach { state ->
                    statesByFlag.requireKnown(state, "party ${party.id.slug}")
                }
            }
            check(party.visitedNpcs.map { it.npc }.toSet().size == party.visitedNpcs.size) {
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
                game.progress.win?.completedBy
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
                check(slots == (0..InventorySaveSlotMax).toList()) {
                    "runtime state ${state.id.slug}: party ${party.id.slug} must list inventory slots 0..$InventorySaveSlotMax"
                }
                check(party.counters.attackedToday.map { it.party }.toSet() == partyIds.toSet()) {
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
                check(party.journal.all { entry ->
                    entry !is JournalEntryState.Line || entry.text.isNotBlank()
                }) {
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
