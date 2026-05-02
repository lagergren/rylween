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

fun validateWildernessSystem(wilderness: WildernessSystemModel) {
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
        check(entry.terrain.sourceMapCode != null) {
            "wilderness terrain transition ${entry.terrain.slug} needs a map tile code"
        }
    }
}

fun validateDungeonSystem(dungeons: DungeonSystemModel) {
    check(dungeons.events.map { it.id }.toSet().size == dungeons.events.size) {
        "duplicate dungeon event"
    }
    val eventIds = dungeons.events.map { it.id }.toSet()
    dungeons.events.forEach { event ->
        check(event.id.slug.isNotBlank()) { "dungeon event references blank id" }
        event.randomEncounterTier?.let { tier ->
            check(tier.slug.isNotBlank()) { "dungeon event ${event.id.slug} references blank encounter tier" }
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
    check(keys.helpPages.isNotEmpty()) { "wilderness help pages must not be empty" }
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
            check(service.building in Buildings.all) {
                "town ${town.id.slug} references unknown service ${service.building.slug}"
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
    check(menu.any { it.action == KeyActions.leaveTown && it.endsMenu }) {
        "town entry menu needs a leave action that exits the menu"
    }

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
        check(service.building in Buildings.all && service.building != Buildings.none) {
            "building service references unknown building ${service.building.slug}"
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
        check(character.job in NpcJobs.all) { "character ${character.id.slug} has unknown job ${character.job.slug}" }
        check(character.roles.toSet().size == character.roles.size) {
            "character ${character.id.slug} has duplicate roles"
        }
        character.roles.forEach { role ->
            check(role in NpcRoles.all) { "character ${character.id.slug} has unknown role ${role.slug}" }
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
            TextPart.ShopPrice,
            TextPart.ShopCarryCap -> Unit
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
) {
    validateSpeakTextTables(speak.textTables, characters, towns)

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

private fun validateSpeakTextTables(
    tables: SpeakTextTablesModel,
    characters: List<CharacterModel>,
    towns: List<TownModel>,
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
        check(reply.job in NpcJobs.all) { "$label references unknown NPC job ${reply.job.slug}" }
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

fun validateDialogue(dialogue: List<DialogueModel>, characters: List<CharacterModel> = emptyList()) {
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
) {
    val itemRecordById = itemRecords.associateBy { it.id }

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
            is BoolAst.FlagIs -> Unit
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
        validateTextBlock(offer.offer, "random item offer ${offer.pool.slug} offer")
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
        validateTextBlock(offer.declined, "random item offer ${offer.pool.slug} declined")
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
        check(shop.building in Buildings.all) { "shop service references unknown building ${shop.building.slug}" }
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
            buy.unitPrice?.let { validateTextBlock(it, "shop ${shop.building.slug} unit-price") }
            buy.singlePrice?.let { validateTextBlock(it, "shop ${shop.building.slug} single-price") }
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
                            is ShopPurchaseModel.Resource -> {
                                validateTextBlock(
                                    purchase.quantityPrompt,
                                    "shop ${shop.building.slug} ${entry.item.slug} quantity prompt",
                                )
                                validateTextBlock(
                                    purchase.tooMany,
                                    "shop ${shop.building.slug} ${entry.item.slug} too-many",
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
            validateTextBlock(sell.offer, "shop ${shop.building.slug} sell offer")
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

    fun visit(ops: List<Op>) {
        fun visitOps(items: List<Op>, loopDepth: Int) {
            items.forEach { op ->
                when (op) {
                    is Op.Screen -> validateTextBlock(op.text, "screen")
                    is Op.EmitIndexed -> validateIntExpression(op.value, "indexed text emit")
                    is Op.LetInt -> validateIntExpression(op.value, "local int")
                    is Op.Set -> validateIntExpression(op.value, "state assignment")
                    is Op.Add -> validateIntExpression(op.value, "state add")
                    is Op.Sub -> validateIntExpression(op.value, "state subtract")
                    is Op.AddClamped -> {
                        validateIntExpression(op.value, "state clamped add")
                        validateIntExpression(op.max, "state clamped add max")
                    }
                    is Op.AppendJournal -> validateTextBlock(op.text, "journal append")
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
                    is Op.GrantReward -> validateReward(op.reward)
                    is Op.RandomItemOffer -> validateRandomItemOffer(op.offer)
                    is Op.ShopService -> validateShop(op.shop)
                    is Op.ConsumeInventoryPool -> {
                        validateSlottedItemPool(op.pool, "consume inventory")
                        validateIntExpression(op.quantity, "consume inventory quantity")
                        (op.quantity as? IntAst.Lit)?.let { quantity ->
                            check(quantity.value > 0) { "consume inventory quantity must be positive" }
                        }
                    }
                    is Op.EnterDungeon -> {
                        check(op.dungeon.slug.isNotBlank()) { "script dungeon handoff references blank dungeon" }
                        check(op.levels > 0) { "script dungeon handoff must use positive level count" }
                    }
                    is Op.TeleportParty -> {
                        validateIntExpression(op.directionKey, "teleport direction key")
                        validateIntExpression(op.distance, "teleport distance")
                    }
                    is Op.DiscoverCave -> {
                        check(op.dungeon.slug.isNotBlank()) { "discover cave references blank dungeon" }
                        validateIntExpression(op.levels, "discover cave level count")
                        validateIntExpression(op.moveCost, "discover cave move cost")
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

private fun validateTerminalPresentation(model: TerminalPresentationModel) {
    check(model.terrain.map { it.terrain }.toSet().size == model.terrain.size) {
        "duplicate terminal terrain style"
    }
    model.terrain.forEach { style ->
        check(style.terrain.sourceMapCode != null) {
            "terminal terrain ${style.terrain.slug} has no source map code"
        }
        check(style.glyph.isNotEmpty()) { "terminal terrain ${style.terrain.slug} has blank glyph" }
    }

    check(model.regions.map { it.id }.toSet().size == model.regions.size) {
        "duplicate terminal screen region"
    }
    model.regions.forEach { region ->
        check(region.x >= 0 && region.y >= 0) { "screen region ${region.id.slug} has negative coordinates" }
        region.width?.let { check(it > 0) { "screen region ${region.id.slug} has non-positive width" } }
        region.height?.let { check(it > 0) { "screen region ${region.id.slug} has non-positive height" } }
    }
}

fun validateAuthoredContent(content: GameModel) {
    validateTerminalPresentation(content.terminal)
    validateItemCatalog(content.itemCatalog)
    check(content.itemPools.map { it.slug }.toSet().size == content.itemPools.size) {
        "duplicate item pool"
    }
    content.itemPools.forEach { validateItemPool(it, content.itemCatalog) }
    validateGameProgress(content.progress, content.itemCatalog)

    check(content.authoredFlags.map { it.ref.slug }.toSet().size == content.authoredFlags.size) {
        "duplicate authored flag"
    }
    content.authoredFlags.forEach { flag ->
        check(flag.states.all { it.flag == flag.ref }) {
            "authored flag ${flag.ref.slug} includes a state from another flag"
        }
    }

    check(content.monsters.map { it.id }.toSet().size == content.monsters.size) {
        "duplicate authored monster"
    }
    content.monsters.forEach { monster ->
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

    check(content.missions.map { it.id }.toSet().size == content.missions.size) {
        "duplicate authored mission"
    }
    content.scenes.map { it.id }.toSet().let { scenes ->
        content.missions.forEach { mission ->
            check(mission.action in scenes) {
                "mission ${mission.id.slug} references missing action scene ${mission.action.slug}"
            }
            when (val trigger = mission.trigger) {
                is Trigger.RandomStep -> checkPercent(trigger.percent, "mission ${mission.id.slug} random step")
                is Trigger.AtPosition -> {
                    check(trigger.point.x >= 0 && trigger.point.y >= 0) {
                        "mission ${mission.id.slug} position trigger must use non-negative world coordinates"
                    }
                }
                is Trigger.AtLocation,
                is Trigger.OnTerrain -> Unit
            }
        }
    }

    validateFormulas(content.formulas)
    validateLeveling(content.leveling, content.formulas)
    validateRewardBundles(content.rewards, content.formulas, content.itemCatalog)
    validateCombatSystem(content.combat, content.rewards)
    validateWildernessSystem(content.wilderness)
    validateDungeonSystem(content.dungeons)
    validateTowns(content.towns)
    validateTownSystem(content.townSystem, content.towns, content.scenes)
    validateCharacters(content.characters, content.towns)
    validateSpeakSystem(content.speak, content.characters, content.towns)
    validateItemUses(content.itemUses, content.itemCatalog)
    validateDialogue(content.dialogue, content.characters)
    validateScriptRewards(content, content.itemCatalog)
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
        state.realm.lastTerrain?.let { terrain ->
            check(terrain.sourceMapCode != null) {
                "state ${state.id.slug}: lastTerrain ${terrain.slug} needs a map tile code"
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
            check(party.flags.map { it.flag }.toSet().size == party.flags.size) {
                "party ${party.id.slug}: duplicate party flag state"
            }
            check(party.visitedNpcs.map { it.npc }.toSet().size == party.visitedNpcs.size) {
                "party ${party.id.slug}: duplicate NPC visit record"
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
