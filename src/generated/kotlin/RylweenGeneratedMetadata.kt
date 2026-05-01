// RylweenGeneratedMetadata.kt -- generated content metadata for the Rylween DSL.
//
// This file represents generated output from game content. During migration,
// ./gradlew generateRylweenMetadata renders the checked-in output from the
// template in src/generator/metadata plus generated slices such as
// data/world/items/*.json5. Do not put DSL kernel behavior or engine runtime
// code here.

@file:Suppress(
    "unused",
    "MemberVisibilityCanBePrivate",
    "RedundantVisibilityModifier",
    "UNUSED_PARAMETER",
)

class GarthBanditsFlag : FlagRef("garth.bandits", legacySlot = 1) {
    val none = state("none")
    val accepted = state("accepted")
    val recovered = state("recovered")
    val closed = state("closed")
}

class GarthFlags {
    val bandits = GarthBanditsFlag().declareFlag()
}

class RangerLetterFlag : FlagRef("ranger.letter", legacySlot = 2) {
    val none = state("none")
    val accepted = state("accepted")
    val delivered = state("delivered")
}

class RangerFlags {
    val letter = RangerLetterFlag().declareFlag()
}

class FighterPriestBookFlag : FlagRef("fighter.priest_book", legacySlot = 3) {
    val none = state("none")
    val priestTold = state("priest_told")
    val priestVisited = state("priest_visited")
    val wizardRobbed = state("wizard_robbed")
    val fighterGrateful = state("fighter_grateful")
}

class FighterFlags {
    val priestBook = FighterPriestBookFlag().declareFlag()
}

class ChurchDonationFlag : FlagRef("church.donation.once", legacySlot = 5) {
    val no = state("no")
    val yes = state("yes")
}

class ChurchFlags {
    val donationOnce = ChurchDonationFlag().declareFlag()
}

class SilverKeyFlag : FlagRef("silver_key", legacySlot = 6) {
    val none = state("none")
    val mapBought = state("map_bought")
    val noteFound = state("note_found")
    val thiefSqueezed = state("thief2_squeezed")
    val smithConfessed = state("smith_confessed")
    val caveLocated = state("cave_located")
    val keyTaken = state("key_taken")

    val trailStarted: BoolExpr
        get() = mapBought or noteFound or thiefSqueezed or smithConfessed or caveLocated
}

class ErethorLuteFlag : FlagRef("erethor.lute", legacySlot = 8) {
    val none = state("none")
    val rumourTold = state("rumour_told")
    val luteTaken = state("lute_taken")
    val noteReceived = state("note_received")
    val cloakReceived = state("cloak_received")
}

class ErethorFlags {
    val lute = ErethorLuteFlag().declareFlag()
}

class BraceletsThunFlag : FlagRef("bracelets.thun", legacySlot = 13) {
    val none = state("none")
    val mapBought = state("map_bought")
    val braceletsTaken = state("bracelets_taken")
}

class BraceletsFlags {
    val thun = BraceletsThunFlag().declareFlag()
}

class NymphSeekerFlag : FlagRef("nymph.seeker", legacySlot = 15) {
    val none = state("none")
    val told = state("told")
    val wizard3tActive = state("wizard3t_active")
}

class NymphFlags {
    val seeker = NymphSeekerFlag().declareFlag()
}

class PeasantWifeFlag : FlagRef("peasant_wife", legacySlot = 19) {
    val none = state("none")
    val accepted = state("accepted")
    val rescued = state("rescued")
    val paid = state("paid")
}

class PeasantFlags {
    val wife = PeasantWifeFlag().declareFlag()
}

class JazzyOgreFlag : FlagRef("jazzy_ogre", legacySlot = 22) {
    val none = state("none")
    val accepted = state("accepted")
    val ogresCleared = state("ogres_cleared")
    val jazzyDefeated = state("jazzy_defeated")
}

class JazzyFlags {
    val ogre = JazzyOgreFlag().declareFlag()
}

class MasterCrownFlag : FlagRef("mastercrown", legacySlot = 23) {
    val none = state("none")
    val task1Assigned = state("task1_assigned")
    val task1Done = state("task1_done")
    val task2Assigned = state("task2_assigned")
    val task2Done = state("task2_done")
    val task3Assigned = state("task3_assigned")
    val borinMet = state("borin_met")
    val daggerTaken = state("dagger_taken")
    val borinPaid = state("borin_paid")
    val crownTold = state("crown_told")
    val crownTaken = state("crown_taken")

    val activeTask: BoolExpr
        get() = task1Assigned or task2Assigned or task3Assigned
}

class HermitTrollToothFlag : FlagRef("hermit.troll_tooth", legacySlot = 33) {
    val none = state("none")
    val accepted = state("accepted")
    val extracted = state("extracted")
    val delivered = state("delivered")
    val fireShieldAcquired = state("fire_shield_acquired")
}

class HermitFlags {
    val trollTooth = HermitTrollToothFlag().declareFlag()
}

class EncounterImmuneFlag : FlagRef(
        "encounter.immune",
        scope = FlagScope.Encounter,
        legacySlot = 0,
    ) {
    val no = state("no")
    val yes = state("yes")
}

class EncounterFlags {
    val immune = EncounterImmuneFlag().declareFlag()
}

class StoryFlags {
    val garth = GarthFlags()
    val ranger = RangerFlags()
    val fighter = FighterFlags()
    val church = ChurchFlags()
    val silverKey = SilverKeyFlag().declareFlag()
    val erethor = ErethorFlags()
    val bracelets = BraceletsFlags()
    val nymph = NymphFlags()
    val peasant = PeasantFlags()
    val jazzy = JazzyFlags()
    val masterCrown = MasterCrownFlag().declareFlag()
    val hermit = HermitFlags()
    val encounter = EncounterFlags()
}
val Story = StoryFlags()
val Flags = Story


// -----------------------------------------------------------------------------
// Generated item handles from data/world/items/*.json5.
// -----------------------------------------------------------------------------

class ItemRefs {
    // Weapons.
    val blank = generatedItem("blank", legacyId = 0)
    val none = blank
    val sword = generatedItem("sword", legacyId = 1)
    val axe = generatedItem("axe", legacyId = 2)
    val bow = generatedItem("bow", legacyId = 3)
    val club = generatedItem("club", legacyId = 4)
    val dart = generatedItem("dart", legacyId = 5)
    val sling = generatedItem("sling", legacyId = 6)
    val longsword = generatedItem("longsword", legacyId = 7)
    val trident = generatedItem("trident", legacyId = 8)
    val halbard = generatedItem("halbard", legacyId = 9)
    val poleArm = generatedItem("pole_arm", legacyId = 10)
    val crossbow = generatedItem("crossbow", legacyId = 11)
    val scythe = generatedItem("scythe", legacyId = 12)
    val scimitar = generatedItem("scimitar", legacyId = 13)
    val twoHandSword = generatedItem("two_hand_sword", legacyId = 14)
    val magicDagger = generatedItem("magic_dagger", legacyId = 15)
    val magicSword = generatedItem("magic_sword", legacyId = 16)
    val magicAxe = generatedItem("magic_axe", legacyId = 17)
    val dancingSword = generatedItem("dancing_sword", legacyId = 18)
    val deathBlade = generatedItem("death_blade", legacyId = 19)

    // Armor.
    val leather = generatedItem("leather", legacyId = 20)
    val chainMail = generatedItem("chain_mail", legacyId = 21)
    val plateMail = generatedItem("plate_mail", legacyId = 22)
    val ringMail = generatedItem("ring_mail", legacyId = 23)
    val elvinCloak = generatedItem("elvin_cloak", legacyId = 24)
    val mithrilMail = generatedItem("mithril_mail", legacyId = 25)
    val admtMail = generatedItem("admt_mail", legacyId = 26)
    val protSphere = generatedItem("prot_sphere", legacyId = 27)
    val cloakOfGods = generatedItem("cloak_of_gods", legacyId = 28)

    // Gear.
    val torch = generatedItem("torch", legacyId = 31)
    val ration = generatedItem("ration", legacyId = 32)
    val blanket = generatedItem("blanket", legacyId = 33)
    val tent = generatedItem("tent", legacyId = 34)
    val rope = generatedItem("rope", legacyId = 35)

    // Potions.
    val redPotion = generatedItem("red_potion", legacyId = 36)
    val bluePotion = generatedItem("blue_potion", legacyId = 37)
    val amberPotion = generatedItem("amber_potion", legacyId = 38)

    // Treasures.
    val redGem = generatedItem("red_gem", legacyId = 39)
    val blueGem = generatedItem("blue_gem", legacyId = 40)
    val greenGem = generatedItem("green_gem", legacyId = 41)
    val chalice = generatedItem("chalice", legacyId = 42)
    val pendant = generatedItem("pendant", legacyId = 43)
    val broche = generatedItem("broche", legacyId = 44)
    val crystalBowl = generatedItem("crystal_bowl", legacyId = 45)
    val necklace = generatedItem("necklace", legacyId = 46)
    val donkey = generatedItem("donkey", legacyId = 47)

    // Flag-backed special items project from milestones and do not occupy slots.
    val fireshield = generatedItem("fireshield", legacyId = 48)
    val bracelets = generatedItem("bracelets", legacyId = 49)
    val mastercrown = generatedItem("mastercrown", legacyId = 50)
    val jazzyfoot = generatedItem("jazzyfoot", legacyId = 51)
    val silverkey = generatedItem("silverkey", legacyId = 52)

    // Magic items.
    val frostWand = generatedItem("frost_wand", legacyId = 100)
    val fireWand = generatedItem("fire_wand", legacyId = 101)
    val gemOfConfusion = generatedItem("gem_of_confusion", legacyId = 102)
    val gemOfBlindness = generatedItem("gem_of_blindness", legacyId = 103)
    val drumsOfDeath = generatedItem("drums_of_death", legacyId = 104)
    val staffOfHealing = generatedItem("staff_of_healing", legacyId = 105)
    val blessingScroll = generatedItem("blessing_scroll", legacyId = 106)
    val staffOfCuring = generatedItem("staff_of_curing", legacyId = 107)
    val gemOfResurrection = generatedItem("gem_of_resurrection", legacyId = 108)
    val kaelsAxe = generatedItem("kaels_axe", legacyId = 109)
    val holyHandgrenade = generatedItem("holy_handgrenade", legacyId = 110)
    val fluteOfMadness = generatedItem("flute_of_madness", legacyId = 111)
}
val Items = ItemRefs()

private fun generatedItemRecord(
    id: ItemId,
    kind: ItemKind,
    displayName: String,
    cost: Int,
    storage: ItemStorageKind = ItemStorageKind.SlottedInventory,
    weaponDamage: Int? = null,
    armorClass: Int? = null,
    potionEffect: PotionEffectModel? = null,
): ItemRecordModel =
    ItemRecordModel(id, kind, displayName, cost, storage, weaponDamage, armorClass, potionEffect)

class GeneratedItemCatalog {
    val all = listOf(
        // Weapons.
        generatedItemRecord(
            Items.blank,
            ItemKind.Empty,
            "-",
            cost = 0,
            storage = ItemStorageKind.EmptySentinel,
            weaponDamage = 1,
        ),
        generatedItemRecord(Items.sword, ItemKind.Weapon, "Sword", cost = 160, weaponDamage = 6),
        generatedItemRecord(Items.axe, ItemKind.Weapon, "Axe", cost = 240, weaponDamage = 10),
        generatedItemRecord(Items.bow, ItemKind.Weapon, "Bow", cost = 160, weaponDamage = 7),
        generatedItemRecord(Items.club, ItemKind.Weapon, "Club", cost = 140, weaponDamage = 4),
        generatedItemRecord(Items.dart, ItemKind.Weapon, "Dart", cost = 40, weaponDamage = 3),
        generatedItemRecord(Items.sling, ItemKind.Weapon, "Sling", cost = 165, weaponDamage = 5),
        generatedItemRecord(Items.longsword, ItemKind.Weapon, "Longsword", cost = 280, weaponDamage = 9),
        generatedItemRecord(Items.trident, ItemKind.Weapon, "Trident", cost = 350, weaponDamage = 11),
        generatedItemRecord(Items.halbard, ItemKind.Weapon, "Halbard", cost = 500, weaponDamage = 14),
        generatedItemRecord(Items.poleArm, ItemKind.Weapon, "Pole arm", cost = 670, weaponDamage = 18),
        generatedItemRecord(Items.crossbow, ItemKind.Weapon, "Crossbow", cost = 900, weaponDamage = 21),
        generatedItemRecord(Items.scythe, ItemKind.Weapon, "Scythe", cost = 870, weaponDamage = 14),
        generatedItemRecord(Items.scimitar, ItemKind.Weapon, "Scimitar", cost = 600, weaponDamage = 8),
        generatedItemRecord(Items.twoHandSword, ItemKind.Weapon, "2H-Sword", cost = 1200, weaponDamage = 19),
        generatedItemRecord(Items.magicDagger, ItemKind.Weapon, "Magic Dagger", cost = 1800, weaponDamage = 24),
        generatedItemRecord(Items.magicSword, ItemKind.Weapon, "Magic Sword", cost = 3000, weaponDamage = 30),
        generatedItemRecord(Items.magicAxe, ItemKind.Weapon, "Magic Axe", cost = 4000, weaponDamage = 38),
        generatedItemRecord(Items.dancingSword, ItemKind.Weapon, "Dancing Sword", cost = 6000, weaponDamage = 50),
        generatedItemRecord(Items.deathBlade, ItemKind.Weapon, "Death Blade", cost = 10000, weaponDamage = 68),

        // Armor.
        generatedItemRecord(Items.leather, ItemKind.Armor, "Leather", cost = 260, armorClass = 2),
        generatedItemRecord(Items.chainMail, ItemKind.Armor, "Chain Mail", cost = 600, armorClass = 5),
        generatedItemRecord(Items.plateMail, ItemKind.Armor, "Plate Mail", cost = 980, armorClass = 7),
        generatedItemRecord(Items.ringMail, ItemKind.Armor, "Ring Mail", cost = 1950, armorClass = 9),
        generatedItemRecord(Items.elvinCloak, ItemKind.Armor, "Elvin Cloak", cost = 3600, armorClass = 12),
        generatedItemRecord(Items.mithrilMail, ItemKind.Armor, "Mithril Mail", cost = 5800, armorClass = 14),
        generatedItemRecord(Items.admtMail, ItemKind.Armor, "Admt. Mail", cost = 9400, armorClass = 16),
        generatedItemRecord(Items.protSphere, ItemKind.Armor, "Prot. Sphere", cost = 15000, armorClass = 20),
        generatedItemRecord(Items.cloakOfGods, ItemKind.Armor, "Cloak of Gods", cost = 28000, armorClass = 30),

        // Gear.
        generatedItemRecord(Items.torch, ItemKind.Gear, "Torch", cost = 1),
        generatedItemRecord(Items.ration, ItemKind.Gear, "Ration", cost = 2),
        generatedItemRecord(Items.blanket, ItemKind.Gear, "Blanket", cost = 30),
        generatedItemRecord(Items.tent, ItemKind.Gear, "Tent", cost = 80),
        generatedItemRecord(Items.rope, ItemKind.Gear, "Rope", cost = 15),

        // Potions.
        generatedItemRecord(
            Items.redPotion,
            ItemKind.Potion,
            "Red Potion",
            cost = 30,
            potionEffect = PotionEffectModel(PotionEffectKind.HealHitPoints, amount = 15),
        ),
        generatedItemRecord(
            Items.bluePotion,
            ItemKind.Potion,
            "Blue Potion",
            cost = 100,
            potionEffect = PotionEffectModel(PotionEffectKind.PoisonHitPoints, amount = 15),
        ),
        generatedItemRecord(
            Items.amberPotion,
            ItemKind.Potion,
            "Amber Potion",
            cost = 500,
            potionEffect = PotionEffectModel(PotionEffectKind.RestoreMana),
        ),

        // Treasures.
        generatedItemRecord(Items.redGem, ItemKind.Treasure, "Red Gem", cost = 1000),
        generatedItemRecord(Items.blueGem, ItemKind.Treasure, "Blue Gem", cost = 1200),
        generatedItemRecord(Items.greenGem, ItemKind.Treasure, "Green Gem", cost = 1900),
        generatedItemRecord(Items.chalice, ItemKind.Treasure, "Chalice", cost = 2340),
        generatedItemRecord(Items.pendant, ItemKind.Treasure, "Pendant", cost = 5000),
        generatedItemRecord(Items.broche, ItemKind.Treasure, "Broche", cost = 1234),
        generatedItemRecord(Items.crystalBowl, ItemKind.Treasure, "Crystal Bowl", cost = 4567),
        generatedItemRecord(Items.necklace, ItemKind.Treasure, "Necklace", cost = 987),
        generatedItemRecord(Items.donkey, ItemKind.Treasure, "Donkey", cost = 800),

        // Flag-backed special items project from milestones and do not occupy slots.
        generatedItemRecord(
            Items.fireshield,
            ItemKind.Special,
            "Fire Shield",
            cost = 0,
            storage = ItemStorageKind.SpecialMilestone,
        ),
        generatedItemRecord(
            Items.bracelets,
            ItemKind.Special,
            "Bracelets of Thun",
            cost = 0,
            storage = ItemStorageKind.SpecialMilestone,
        ),
        generatedItemRecord(
            Items.mastercrown,
            ItemKind.Special,
            "Master Crown",
            cost = 0,
            storage = ItemStorageKind.SpecialMilestone,
        ),
        generatedItemRecord(
            Items.jazzyfoot,
            ItemKind.Special,
            "Jazzyfoot",
            cost = 0,
            storage = ItemStorageKind.SpecialMilestone,
        ),
        generatedItemRecord(
            Items.silverkey,
            ItemKind.Special,
            "Silver Key",
            cost = 0,
            storage = ItemStorageKind.SpecialMilestone,
        ),

        // Magic items.
        generatedItemRecord(Items.frostWand, ItemKind.MagicItem, "Frost Wand", cost = 1000),
        generatedItemRecord(Items.fireWand, ItemKind.MagicItem, "Fire Wand", cost = 3000),
        generatedItemRecord(Items.gemOfConfusion, ItemKind.MagicItem, "Gem of Confusion", cost = 1560),
        generatedItemRecord(Items.gemOfBlindness, ItemKind.MagicItem, "Gem of Blindness", cost = 4900),
        generatedItemRecord(Items.drumsOfDeath, ItemKind.MagicItem, "Drums of Death", cost = 4500),
        generatedItemRecord(Items.staffOfHealing, ItemKind.MagicItem, "Staff of Healing", cost = 670),
        generatedItemRecord(Items.blessingScroll, ItemKind.MagicItem, "Blessing Scroll", cost = 2000),
        generatedItemRecord(Items.staffOfCuring, ItemKind.MagicItem, "Staff of Curing", cost = 4343),
        generatedItemRecord(Items.gemOfResurrection, ItemKind.MagicItem, "Gem of Resurrection", cost = 9000),
        generatedItemRecord(Items.kaelsAxe, ItemKind.MagicItem, "Kael's Axe", cost = 14000),
        generatedItemRecord(Items.holyHandgrenade, ItemKind.MagicItem, "Holy Handgrenade", cost = 5600),
        generatedItemRecord(Items.fluteOfMadness, ItemKind.MagicItem, "Flute of Madness", cost = 9000),
    )
}
val ItemCatalog = GeneratedItemCatalog()

class ItemPoolRefs {
    // Legacy battle.c currently rolls ids 36..46 here: potions plus ordinary
    // treasure, excluding donkey and all milestone-projected special items.
    val battleSpoils = generatedItemPool(
        "battle_spoils",
        items = listOf(
            Items.redPotion,
            Items.bluePotion,
            Items.amberPotion,
            Items.redGem,
            Items.blueGem,
            Items.greenGem,
            Items.chalice,
            Items.pendant,
            Items.broche,
            Items.crystalBowl,
            Items.necklace,
        ),
        legacyItemIds = 36..46,
    )

    val magicItems = generatedItemPool(
        "magic_items",
        items = listOf(
            Items.frostWand,
            Items.fireWand,
            Items.gemOfConfusion,
            Items.gemOfBlindness,
            Items.drumsOfDeath,
            Items.staffOfHealing,
            Items.blessingScroll,
            Items.staffOfCuring,
            Items.gemOfResurrection,
            Items.kaelsAxe,
            Items.holyHandgrenade,
            Items.fluteOfMadness,
        ),
        legacyItemIds = 100..111,
    )

    val tavernScruffySpecials = generatedItemPool(
        "tavern_scruffy_specials",
        items = listOf(
            Items.magicDagger,
            Items.magicAxe,
            Items.admtMail,
            Items.amberPotion,
            Items.greenGem,
            Items.frostWand,
            Items.fireWand,
            Items.gemOfConfusion,
            Items.gemOfBlindness,
            Items.drumsOfDeath,
            Items.staffOfHealing,
            Items.blessingScroll,
            Items.staffOfCuring,
            Items.gemOfResurrection,
            Items.kaelsAxe,
            Items.holyHandgrenade,
            Items.fluteOfMadness,
        ),
        legacyItemIds = listOf(15, 17, 26, 38, 41) + (100..111).toList(),
    )
}
val ItemPools = ItemPoolRefs()

// -----------------------------------------------------------------------------
// Generated game-state milestone handles.
// -----------------------------------------------------------------------------
//
// These are persisted party state. Some milestones also project into the
// special-inventory view, and some are required for a valid winning state.

class GameMilestoneRefs {
    val fireShield = milestone("fire_shield")
    val braceletsOfThun = milestone("bracelets_of_thun")
    val masterCrown = milestone("master_crown")
    val footOfJazzy = milestone("foot_of_jazzy")
    val silverKey = milestone("silver_key")

    // Legacy C encoded "castle cleared / game won" as silverkey == 2. The DSL
    // keeps that as a separate milestone from owning the Silver Key item.
    val ydargVictory = milestone("ydarg_victory")
}
val Milestones = GameMilestoneRefs()

// -----------------------------------------------------------------------------
// Generated combat, map, visual, layout, text, scene, and native handles.
// -----------------------------------------------------------------------------

class MonsterIds {
    val kobold = monster("kobold")
    val orc = monster("orc")
    val troll = monster("troll")
    val warrior = monster("warrior")
    val goblin = monster("goblin")
    val wildDog = monster("wild_dog")
    val knight = monster("knight")
    val bandit = monster("bandit")
    val hobgoblin = monster("hobgoblin")
    val zombie = monster("zombie")
    val guardian = monster("guardian")
    val norker = monster("norker")
    val voodooLord = monster("voodoo_lord")
    val archdevil = monster("archdevil")
    val koboldHorde = monster("kobold_horde")
    val rainbowDemon = monster("rainbow_demon")
    val longDeadWarrior = monster("long_dead_warrior")
    val ogre = monster("ogre")
    val ogreLord = monster("ogre_lord")
    val wingedThing = monster("winged_thing")
    val jazzy = monster("jazzy")
    val ridingHobgoblin = monster("riding_hobgoblin")
    val hobgoblinChief = monster("hobgoblin_chief")
    val hobgoblinSkeleton = monster("hobgoblin_skeleton")
    val undeadHobgoblin = monster("undead_hobgoblin")
    val cryptHorror = monster("crypt_horror")
    val nightGaunt = monster("night_gaunt")
    val thyraga = monster("thyraga")
    val liche = monster("liche")
    val baronYdarg = monster("baron_ydarg")
    val gnoll = monster("gnoll")
    val erethorPriest = monster("erethor_priest")
    val erethor = monster("erethor")
    val wanderMage = monster("wander_mage")
    val stirge = monster("stirge")
    val hillGiant = monster("hill_giant")
    val wizard = monster("wizard")
    val druid = monster("druid")
    val sabreToothTiger = monster("sabre_tooth_tiger")
    val headHunter = monster("head_hunter")
    val skeleton = monster("skeleton")
    val vampireBat = monster("vampire_bat")
    val giantSnake = monster("giant_snake")
    val ghoul = monster("ghoul")
    val spider = monster("spider")
    val hugeLizard = monster("huge_lizard")
    val dryder = monster("dryder")
    val xvart = monster("xvart")
    val jellyCube = monster("jelly_cube")
    val troglodyte = monster("troglodyte")
    val bugbear = monster("bugbear")
    val drowElf = monster("drow_elf")
    val phantom = monster("phantom")
    val worg = monster("worg")
    val rustMonster = monster("rust_monster")
    val owlbear = monster("owlbear")
    val werewolf = monster("werewolf")
    val hookHorror = monster("hook_horror")
    val minotaur = monster("minotaur")
    val imp = monster("imp")
    val svirfneblin = monster("svirfneblin")
    val yeti = monster("yeti")
    val wight = monster("wight")
    val wraith = monster("wraith")
    val basilisk = monster("basilisk")
    val wyvern = monster("wyvern")
    val tunnelWorm = monster("tunnel_worm")
    val skagerbergKiller = monster("skagerberg_killer")
}
val Monsters = MonsterIds()

class EncounterTierRefs {
    val weak = encounterTier("weak")
    val wild = encounterTier("wild")
    val deep = encounterTier("deep")
}
val EncounterTiers = EncounterTierRefs()

class MapRefs {
    val wilderness = worldMap("wilderness")
}
val Maps = MapRefs()

class MapLayerRefs {
    val terrain = mapLayer("terrain")
}
val MapLayers = MapLayerRefs()

class TerrainRefs {
    val mountain = terrain("mountain", 'm')
    val water = terrain("water", 'w')
    val plain = terrain("plain", 's')
    val town = terrain("town", '0')
    val hut = terrain("hut", '1')
    val forest = terrain("forest", 'f')
    val desert = terrain("desert", 'd')
    val swamp = terrain("swamp", 't')
    val grass = terrain("grass", 'g')
    val castle = terrain("castle", '2')
}
val Terrain = TerrainRefs()

class TerrainTagRefs {
    val mountain = terrainTag("mountain")
    val water = terrainTag("water")
    val town = terrainTag("town")
    val hut = terrainTag("hut")
    val castle = terrainTag("castle")
    val settlement = terrainTag("settlement")
    val impassable = terrainTag("impassable")
}
val TerrainTags = TerrainTagRefs()

class PaletteRefs {
    val terminal16 = palette("terminal16")
}
val Palettes = PaletteRefs()

class PaletteColorRefs {
    val black = paletteColor("black")
    val red = paletteColor("red")
    val green = paletteColor("green")
    val yellow = paletteColor("yellow")
    val blue = paletteColor("blue")
    val magenta = paletteColor("magenta")
    val cyan = paletteColor("cyan")
    val white = paletteColor("white")
}
val PaletteColors = PaletteColorRefs()

class ImageRefs {
    val Static = StaticAnsiImageRefs()
    val Scene = SceneImageRefs()

    // Backdrop is the author-facing name for pictures rendered into the classic
    // map window by blitgfx. Each handle maps back to data/tiles/<pic-id>.ans.
    val Backdrop = Scene
}

class StaticAnsiImageRefs {
    val screen = image("ansi.screen")
    val cave = image("ansi.cave")
    val merchant = image("ansi.merchant")
    val guild = image("ansi.guild")
    val cahsut = image("ansi.cahsut")
    val wonbat = image("ansi.wonbat")
    val death = image("ansi.death")
    val tent = image("ansi.tent")
    val title = image("ansi.title")
    val inside = image("ansi.inside")
    val castle = image("ansi.castle")
    val smith = image("ansi.smith")
    val notent = image("ansi.notent")
    val tavern = image("ansi.tavern")
    val inn = image("ansi.inn")
    val encounter = image("ansi.enc")
    val encounter2 = image("ansi.enc2")
    val encounter3 = image("ansi.enc3")
    val encounter4 = image("ansi.enc4")
    val encounter5 = image("ansi.enc5")
    val church = image("ansi.church")
}

class SceneImageRefs {
    val inn = image("blitgfx.inn")                         // data/tiles/01.ans
    val tavern = image("blitgfx.tavern")                   // data/tiles/02.ans
    val smith = image("blitgfx.smith")                     // data/tiles/03.ans
    val church = image("blitgfx.church")                   // data/tiles/04.ans
    val merchant = image("blitgfx.merchant")               // data/tiles/05.ans
    val insideCave = image("blitgfx.inside_cave")          // data/tiles/10.ans
    val cave = image("blitgfx.cave")                       // data/tiles/11.ans
    val death = image("blitgfx.death")                     // data/tiles/12.ans
    val hut = image("blitgfx.hut")                         // data/tiles/13.ans
    val ydargCastle = image("blitgfx.ydarg_castle")        // data/tiles/14.ans
    val city = image("blitgfx.city")                       // data/tiles/15.ans
    val battleVictory = image("blitgfx.battle_victory")    // data/tiles/16.ans
    val tent = image("blitgfx.tent")                       // data/tiles/17.ans
    val noTent = image("blitgfx.no_tent")                  // data/tiles/18.ans
    val guild = image("blitgfx.guild")                     // data/tiles/19.ans
    val encounterBanner1 = image("blitgfx.encounter.1")    // data/tiles/40.ans
    val encounterBanner2 = image("blitgfx.encounter.2")    // data/tiles/41.ans
    val encounterBanner3 = image("blitgfx.encounter.3")    // data/tiles/42.ans
    val encounterBanner4 = image("blitgfx.encounter.4")    // data/tiles/43.ans
    val encounterBanner5 = image("blitgfx.encounter.5")    // data/tiles/44.ans
}
val Images = ImageRefs()

class LayoutRefs {
    val classicTerminal = layout("classic_terminal")
}
val Layouts = LayoutRefs()

class ScreenRegionRefs {
    val textWindow = screenRegion("text_window")
    val topLeft = screenRegion("top_left")
    val statuslineTop = screenRegion("statusline_top")
    val statuslineBottom = screenRegion("statusline_bottom")
    val mapWindow = screenRegion("map_window")
}
val ScreenRegions = ScreenRegionRefs()

class RoleRefs {
    val banditsQuestGiver = npcRole("bandits_quest_giver")
    val rangersQuestGiver = npcRole("rangers_quest_giver")
    val crownSeer = npcRole("crown_seer")
    val templeOfThunSeer = npcRole("temple_of_thun_seer")
    val masterThief = npcRole("master_thief")
    val fighterIntro = npcRole("fighter_intro")
    val priestWithBook = npcRole("priest_with_book")
    val silverKeyVendor = npcRole("silver_key_vendor")
    val jazzyOgreTrader = npcRole("jazzy_ogre_trader")
    val wizardRobbed = npcRole("wizard_robbed")
    val erethorLuteBard = npcRole("erethor_lute_bard")
    val rangerBrother = npcRole("ranger_brother")
    val peasantHusband = npcRole("peasant_husband")
    val minstrelCloak = npcRole("minstrel_cloak")
    val wizardThreeTasks = npcRole("wizard_three_tasks")
    val silverKeySmith = npcRole("silver_key_smith")
    val hermitTrollTooth = npcRole("hermit_troll_tooth")

    val all = listOf(
        banditsQuestGiver,
        rangersQuestGiver,
        crownSeer,
        templeOfThunSeer,
        masterThief,
        fighterIntro,
        priestWithBook,
        silverKeyVendor,
        jazzyOgreTrader,
        wizardRobbed,
        erethorLuteBard,
        rangerBrother,
        peasantHusband,
        minstrelCloak,
        wizardThreeTasks,
        silverKeySmith,
        hermitTrollTooth,
    )
}
val NpcRoles = RoleRefs()
val Roles = NpcRoles

class SceneRefs {
    val Towne = TowneSceneRefs()
    val Battle = BattleSceneRefs()
    val Missions = MissionSceneRefs()
    val Terrain = TerrainSceneRefs()
    val Dungeon = DungeonSceneRefs()
    val Ydarg = YdargSceneRefs()

    val rumour = Towne.rumour
    val ydargMain = Ydarg.main
    val ydargRoomMonster = Ydarg.roomMonster
    val ydargPitfall = Ydarg.pitfall
    val ydargTreasure = Ydarg.treasure
    val ydargStairs = Ydarg.stairs
    val ydargNavRender = Ydarg.navRender
    val ydargCommandDispatch = Ydarg.commandDispatch
}

class TowneSceneRefs {
    val church = sceneId("towne.church")
    val rumour = sceneId("towne.rumour")
    val smith = sceneId("towne.smith")
    val inn = sceneId("towne.inn")
    val merchant = sceneId("towne.merchant")
    val tavern = sceneId("towne.tavern")
    val hut = sceneId("towne.hut")
    val lodge = sceneId("towne.lodge")
}

class BattleSceneRefs {
    val victory = sceneId("battle.victory")
    val flee = sceneId("battle.flee")
    val monsterMelee = sceneId("battle.monster.melee")
}

class MissionSceneRefs {
    val erethorEntry = sceneId("missions.erethor_entry")
    val husbandCave = sceneId("missions.husband_cave")
    val bandits = sceneId("missions.bandits")
    val trollTooth = sceneId("missions.troll_tooth")
    val hermitDungeon = sceneId("missions.hermit_dungeon")
    val koboldVillage = sceneId("missions.kobold_village")
    val skullweed = sceneId("missions.skullweed")
    val borinMeet = sceneId("missions.borin_meet")
    val nolrimCrypt = sceneId("missions.nolrim_crypt")
    val borinGift = sceneId("missions.borin_gift")
    val masterCrown = sceneId("missions.master_crown")
    val ogreCave = sceneId("missions.ogre_cave")
    val jazzyFoot = sceneId("missions.jazzy_foot")
    val silverKey = sceneId("missions.silver_key")
    val hobgoblinCaravan = sceneId("missions.hobgoblin_caravan")
    val hobgoblinCave = sceneId("missions.hobgoblin_cave")
}

class TerrainSceneRefs {
    val transition = sceneId("terrain.transition")
}

class DungeonSceneRefs {
    val findObject = sceneId("dungeon.find_object")
    val gold = sceneId("dungeon.gold")
    val goldChest = sceneId("dungeon.gold_chest")
    val torches = sceneId("dungeon.torches")
    val fireShieldGuardian = sceneId("dungeon.fire_shield_guardian")
    val fireShield = sceneId("dungeon.fire_shield")
    val bracelets = sceneId("dungeon.bracelets")
    val masterCrown = sceneId("dungeon.master_crown")
    val silverSanctum = sceneId("dungeon.silver_sanctum")
    val hobgoblinChief = sceneId("dungeon.hobgoblin_chief")
    val tammysPrison = sceneId("dungeon.tammys_prison")
    val luteChamber = sceneId("dungeon.lute_chamber")
}

class YdargSceneRefs {
    val main = sceneId("ydarg.main")
    val roomMonster = sceneId("ydarg.room_monster")
    val pitfall = sceneId("ydarg.case_pitfall")
    val treasure = sceneId("ydarg.case_treasure")
    val stairs = sceneId("ydarg.case_stairs")
    val navRender = sceneId("ydarg.nav_render")
    val commandDispatch = sceneId("ydarg.cmd_dispatch")
    val noKey = sceneId("ydarg.no_key")
    val ruin = sceneId("ydarg.ruin")
}
val Scenes = SceneRefs()

class SpellRefs {
    val Mage = MageSpellRefs()
    val Priest = PriestSpellRefs()
    val Monster = MonsterSpellRefs()

    val mageFrost = Mage.frost
}

class MageSpellRefs {
    val frost = spellId("mage.frost")
    val fire = spellId("mage.fire")
    val confusion = spellId("mage.confusion")
    val death = spellId("mage.death")
    val hold = spellId("mage.hold")
    val antimagic = spellId("mage.antimagic")
}

class PriestSpellRefs {
    val heal = spellId("priest.heal")
    val restoration = spellId("priest.restoration")
    val rebirth = spellId("priest.rebirth")
    val banish = spellId("priest.banish")
    val revelation = spellId("priest.revelation")
    val teleport = spellId("priest.teleport")
    val earthquake = spellId("priest.earthquake")
}

class MonsterSpellRefs {
    val frost = spellId("monster.frost")
    val fire = spellId("monster.fire")
    val hold = spellId("monster.hold")
    val confusion = spellId("monster.confusion")
    val melee = spellId("monster.melee")
}
val Spells = SpellRefs()

class MissionRefs {
    val erethorEntry = missionId("missions.erethor_entry")
    val husbandCave = missionId("missions.husband_cave")
    val bandits = missionId("missions.bandits")
    val trollTooth = missionId("missions.troll_tooth")
    val hermitDungeon = missionId("missions.hermit_dungeon")
    val koboldVillage = missionId("missions.kobold_village")
    val skullweed = missionId("missions.skullweed")
    val borinMeet = missionId("missions.borin_meet")
    val nolrimCrypt = missionId("missions.nolrim_crypt")
    val borinGift = missionId("missions.borin_gift")
    val masterCrown = missionId("missions.master_crown")
    val ogreCave = missionId("missions.ogre_cave")
    val jazzyFoot = missionId("missions.jazzy_foot")
    val silverKey = missionId("missions.silver_key")
    val hobgoblinCaravan = missionId("missions.hobgoblin_caravan")
    val hobgoblinCave = missionId("missions.hobgoblin_cave")
    val ydargRuin = missionId("ydarg.ruin")
    val ydargNoKey = missionId("ydarg.no_key")
    val ydargEntry = missionId("ydarg.entry")
}
val Missions = MissionRefs()

class KeyActionRefs {
    val pass = keyAction("pass")
    val save = keyAction("save")
    val weaponReady = keyAction("weapon_ready")
    val armorReady = keyAction("armor_ready")
    val redraw = keyAction("redraw")
    val journal = keyAction("journal")
    val help = keyAction("help")
    val hunt = keyAction("hunt")
    val viewParty = keyAction("view_party")
    val armorOff = keyAction("armor_off")
    val weaponOff = keyAction("weapon_off")
    val enter = keyAction("enter")
    val drop = keyAction("drop")
    val camp = keyAction("camp")
    val castSpell = keyAction("cast_spell")
    val quaff = keyAction("quaff")
    val quitConfirm = keyAction("quit_confirm")
    val enterChurch = keyAction("enter_church")
    val enterSmith = keyAction("enter_smith")
    val enterInn = keyAction("enter_inn")
    val enterMerchant = keyAction("enter_merchant")
    val enterTavern = keyAction("enter_tavern")
    val leaveTown = keyAction("leave_town")
    val useItem = keyAction("use_item")
    val defend = keyAction("defend")
    val attack = keyAction("attack")
    val battleCry = keyAction("battle_cry")
    val speakChitchat = keyAction("speak_chitchat")
    val speakQuestion = keyAction("speak_question")
    val speakRumour = keyAction("speak_rumour")
    val speakBye = keyAction("speak_bye")
    val questionLodging = keyAction("question_lodging")
    val questionMagic = keyAction("question_magic")
    val questionPeople = keyAction("question_people")
    val questionNothing = keyAction("question_nothing")
}
val KeyActions = KeyActionRefs()

class NpcJobRefs {
    val merchant = npcJob("merchant")
    val ranger = npcJob("ranger")
    val fighter = npcJob("fighter")
    val wizard = npcJob("wizard")
    val priest = npcJob("priest")
    val thief = npcJob("thief")
    val peasant = npcJob("peasant")
    val bard = npcJob("bard")
    val drifter = npcJob("drifter")
    val halfOrc = npcJob("half_orc")
    val halfElf = npcJob("half_elf")
    val smith = npcJob("smith")
    val bargirl = npcJob("bargirl")
    val innkeeper = npcJob("innkeeper")
    val tavernkeeper = npcJob("tavernkeeper")
    val hermit = npcJob("hermit")

    val all = listOf(
        merchant,
        ranger,
        fighter,
        wizard,
        priest,
        thief,
        peasant,
        bard,
        drifter,
        halfOrc,
        halfElf,
        smith,
        bargirl,
        innkeeper,
        tavernkeeper,
        hermit,
    )
}
val NpcJobs = NpcJobRefs()

class RumourTableRefs {
    val standardTown = rumourTable("standard_town")
}
val RumourTables = RumourTableRefs()

class RumourHandleRefs {
    val banditsQuestGiver = rumour("bandits_quest_giver")
    val rangerQuestGiver = rumour("ranger_quest_giver")
    val crownSeer = rumour("crown_seer")
    val templeOfThunSeer = rumour("temple_of_thun_seer")

    val all = listOf(
        banditsQuestGiver,
        rangerQuestGiver,
        crownSeer,
        templeOfThunSeer,
    )
}
val Rumours = RumourHandleRefs()

class StateRefs {
    val freshReplay = stateId("fresh-replay")
    val nearWinningReplay = stateId("near-winning-replay")
    val winningReplay = stateId("winning-replay")
}
val States = StateRefs()

class PartyRefs {
    val replay = partyId("replay")
}
val Parties = PartyRefs()

class NpcRefs {
    val garth = npc("garth")
    val nrimTo = npc("nrim_to")
    val areth = npc("areth")
    val nevaak = npc("nevaak")
    val gorm = npc("gorm")
    val bernak = npc("bernak")
    val matias = npc("matias")
    val tybalt = npc("tybalt")
    val romeo = npc("romeo")
    val werra = npc("werra")
    val arenjun = npc("arenjun")
    val oskar = npc("oskar")
    val harry = npc("harry")
    val marraga = npc("marraga")
    val broth = npc("broth")
    val ebond = npc("ebond")
    val bolph = npc("bolph")
    val gotharp = npc("gotharp")
    val ron = npc("ron")
    val kaal = npc("kaal")
    val daniel = npc("daniel")
    val leander = npc("leander")
    val ithienn = npc("ithienn")
    val azaaza = npc("azaaza")
    val hassan = npc("hassan")
    val luqas = npc("luqas")
    val darth = npc("darth")
    val yog = npc("yog")
    val niklas = npc("niklas")
    val tokawa = npc("tokawa")
    val miyaki = npc("miyaki")
    val dargoth = npc("dargoth")
    val marcus = npc("marcus")
    val deanna = npc("deanna")
    val jeanne = npc("jeanne")
    val alex = npc("alex")
    val galdon = npc("galdon")
    val kosch = npc("kosch")
    val ufarsin = npc("ufarsin")
}
val Npcs = NpcRefs()

class LocationRefs {
    val hermitDungeon = location("hermit_dungeon")
    val thunTemple = location("thun_temple")
    val koboldVillage = location("kobold_village")
    val borinCamp = location("borin_camp")
    val nolrimCrypt = location("nolrim_crypt")
    val masterCrownDungeon = location("master_crown_dungeon")
    val beastCave = location("beast_cave")
    val silverKeyPit = location("silver_key_pit")
    val hobgoblinCave = location("hobgoblin_cave")
}
val Locations = LocationRefs()

class DungeonRefs {
    val randomCave = dungeon("random_cave")
    val fireShieldCave = dungeon("fire_shield_cave")
    val thunTemple = dungeon("thun_temple")
    val reebarTomb = dungeon("reebar_tomb")
    val masterCrownDungeon = dungeon("master_crown_dungeon")
    val silverSanctum = dungeon("silver_sanctum")
    val hobgoblinStronghold = dungeon("hobgoblin_stronghold")
    val ogreCave = dungeon("ogre_cave")
    val erethorTemple = dungeon("erethor_temple")
}
val Dungeons = DungeonRefs()

class FormulaRefs {
    val encounterH1Scaled = formula("encounter_h1_scaled")
    val encounterH2Scaled = formula("encounter_h2_scaled")
    val rewardXpPerSurvivor = formula("reward_xp_per_survivor")
    val rewardGoldParty = formula("reward_gold_party")
    val levelUpHitPointFloor = formula("level_up_hp_floor")
}
val Formulas = FormulaRefs()

class LootTableRefs {
    val standardBattle = lootTable("standard_battle")
}
val LootTables = LootTableRefs()

class CombatLootTableRefs {
    val default = LootTables.standardBattle
}
val CombatLootTables = CombatLootTableRefs()

class RewardBundleRefs {
    val standardBattle = rewardBundle("standard_battle")
}
val Rewards = RewardBundleRefs()

class BuildingRefs {
    val none = building("none")
    val inn = building("inn")
    val tavern = building("tavern")
    val church = building("church")
    val smith = building("smith")
    val merchant = building("merchant")
    val hut = building("hut")
    val lodge = building("lodge")

    val all = listOf(none, inn, tavern, church, smith, merchant, hut, lodge)
}
val Buildings = BuildingRefs()

class TownRef(val id: TownId) {
    val inn: WorldPosition get() = WorldPosition.Town(id, Buildings.inn)
    val tavern: WorldPosition get() = WorldPosition.Town(id, Buildings.tavern)
    val church: WorldPosition get() = WorldPosition.Town(id, Buildings.church)
    val smith: WorldPosition get() = WorldPosition.Town(id, Buildings.smith)
    val merchant: WorldPosition get() = WorldPosition.Town(id, Buildings.merchant)
}

class TownRefs {
    val thord = TownRef(town("thord"))
    val ollk = TownRef(town("ollk"))
    val wilor = TownRef(town("wilor"))
    val murn = TownRef(town("murn"))
}
val Towns = TownRefs()

class TextRefs {
    val Title = TitleTextRefs()
    val Driv = DrivTextRefs()
    val Towne = TowneRefs()
    val Speak = SpeakRefs()
    val Battle = BattleRefs()
    val MissionsText = MissionTextRefs()
    val Ydarg = YdargTextRefs()
    val Dungeon = DungeonTextRefs()
    val Terrain = TerrainTextRefs()
    val Common = CommonTextRefs()
}

class TitleTextRefs {
    val root = text("title")
}

class DrivTextRefs {
    val statusline = text("driv.statusline")
}

class TowneRefs {
    val Church = ChurchRefs()
    val Rumour = RumourRefs()
}

class ChurchRefs {
    val intro = text("towne.church.intro")
    val menu = text("towne.church.menu")
    val who = text("towne.church.who")
}

class RumourRefs {
    val r12 = text("towne.rumour.r12")
    val r13 = text("towne.rumour.r13")
    val r25 = text("towne.rumour.r25")
    val r27 = text("towne.rumour.r27")
    val indexed = textPattern("towne.rumour.r{}")
}

class SpeakRefs {
    val Lodge = SpeakLodgeRefs()
    val noNpc = text("speak.no_npc")
    val recognizePre = text("speak.recognize_pre")
    val recognizeHe = text("speak.recognize_he")
    val recognizeShe = text("speak.recognize_she")
    val tellsYouPre = text("speak.tells_you_pre")
    val chatting = text("speak.chatting")
    val menu = text("speak.menu")
    val noRumour = text("speak.no_rumour")
    val question = text("speak.question")
}

class SpeakLodgeRefs {
    val bargirlOffer = text("speak.lodge.jbargirl.offer")
    val bargirlBroke = text("speak.lodge.jbargirl.broke")
    val bargirlSpent = text("speak.lodge.jbargirl.spent")
}

class BattleRefs {
    val Prompt = BattlePromptRefs()
    val Frost = FrostRefs()
    val FireWand = FireWandRefs()
    val killed = text("battle.killed")
}

class BattlePromptRefs {
    val monster = text("battle.prompt.who")
}

class FrostRefs {
    val chills = text("battle.spell.frost.chills")
    val damage = text("battle.spell.frost.damage")
    val damagePts = text("battle.spell.frost.damage_pts")
}

class FireWandRefs {
    val burns = text("battle.item.fire_wand.burns")
    val points = text("battle.item.fire_wand.points")
}

class MissionTextRefs {
    val Bandits = BanditsTextRefs()
}

class BanditsTextRefs {
    val spotted = text("missions.bandits.spotted")
    val warned = text("missions.bandits.warned")
    val recovered = text("missions.bandits.recovered")
}

class YdargTextRefs {
    val noKey = text("ydarg.no_key")
    val ruin = text("ydarg.ruin")
}

class DungeonTextRefs {
    val findObject = text("dungeon.find_object")
}

class TerrainTextRefs {
    val transition = text("terrain.transition")
}

class CommonTextRefs {
    val pause = text("common.pause")
}
val Texts = TextRefs()
val T = Texts
