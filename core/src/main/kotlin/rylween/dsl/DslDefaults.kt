/**
 * Internal default handles shared by DSL builders and runtime adapters.
 *
 * These values preserve the current authored slugs while avoiding direct
 * coupling from runtime code to generated game-specific handle catalogs in
 * `game/RylweenGame.dsl.kts`.
 */
package rylween.dsl

internal object DslDefaultItems {
    val none = item("blank")
}

internal object DslDefaultItemPools {
    val battleSpoils = itemPool("battle_spoils")
    val magicItems = itemPool("magic_items")
}

internal object DslDefaultEncounterTiers {
    val weak = encounterTier("weak")
    val wild = encounterTier("wild")
}

internal object DslDefaultMaps {
    val wilderness = worldMap("wilderness")
}

internal object DslDefaultMapLayers {
    val terrain = mapLayer("terrain")
}

internal object DslDefaultTerrain {
    val mountain = terrain("mountain", 'm')
    val water = terrain("water", 'w')
    val town = terrain("town", '0')
    val hut = terrain("hut", '1')
    val castle = terrain("castle", '2')
}

internal object DslDefaultDungeons {
    val randomCave = dungeon("random_cave")
}

/** Built-in building handles used by runtime adapters when no authored building handle is active. */
object DslDefaultBuildings {
    val none = building("none")
    val inn = building("inn")
    val tavern = building("tavern")
    val church = building("church")
    val smith = building("smith")
    val merchant = building("merchant")
    val guild = building("guild")
    val hut = building("hut")
}

internal object DslDefaultKeyActions {
    val enter = keyAction("enter")
    val enterChurch = keyAction("enter_church")
    val enterSmith = keyAction("enter_smith")
    val enterInn = keyAction("enter_inn")
    val enterMerchant = keyAction("enter_merchant")
    val enterTavern = keyAction("enter_tavern")
    val enterGuild = keyAction("enter_guild")
    val leaveTown = keyAction("leave_town")
    val useItem = keyAction("use_item")
    val defend = keyAction("defend")
    val attack = keyAction("attack")
    val battleCry = keyAction("battle_cry")
    val castSpell = keyAction("cast_spell")
    val speakChitchat = keyAction("speak_chitchat")
    val speakQuestion = keyAction("speak_question")
    val speakRumor = keyAction("speak_rumor")
    val speakBye = keyAction("speak_bye")
    val questionLodging = keyAction("question_lodging")
    val questionMagic = keyAction("question_magic")
    val questionPeople = keyAction("question_people")
    val questionNothing = keyAction("question_nothing")
}

internal object DslDefaultNpcJobs {
    val merchant = npcJob("merchant")
    val bargirl = npcJob("bargirl")
}

internal object DslDefaultFormulas {
    val encounterH1Scaled = formula("encounter_h1_scaled")
    val encounterH2Scaled = formula("encounter_h2_scaled")
    val rewardXpPerSurvivor = formula("reward_xp_per_survivor")
    val rewardGoldParty = formula("reward_gold_party")
    val levelUpHitPointFloor = formula("level_up_hp_floor")
}

internal object DslDefaultLootTables {
    val standardBattle = lootTable("standard_battle")
}

internal object DslDefaultRewards {
    val standardBattle = rewardBundle("standard_battle")
}

internal object DslDefaultSpells {
    object Monster {
        val melee = spellId("monster.melee")
    }
}

internal object DslDefaultRumorTables {
    val standardTown = rumorTable("standard_town")
}

internal object DslDefaultText {
    object Speak {
        val noNpc = text("speak.no_npc")
        val noRumor = text("speak.no_rumor")
    }
}

internal object DslDefaultImages {
    object Scene {
        val encounterBanner1 = image("blitgfx.encounter.1")
        val encounterBanner2 = image("blitgfx.encounter.2")
        val encounterBanner3 = image("blitgfx.encounter.3")
        val encounterBanner4 = image("blitgfx.encounter.4")
        val encounterBanner5 = image("blitgfx.encounter.5")
    }
}

internal object DslDefaultScenes {
    object Towne {
        val church = sceneId("towne.church")
        val smith = sceneId("towne.smith")
        val inn = sceneId("towne.inn")
        val merchant = sceneId("towne.merchant")
        val tavern = sceneId("towne.tavern")
        val guild = sceneId("towne.guild")
        val hut = sceneId("towne.hut")
    }

    object Battle {
        val monsterMelee = sceneId("battle.monster.melee")
    }
}
