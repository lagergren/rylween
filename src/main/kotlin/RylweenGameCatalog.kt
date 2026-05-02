// RylweenGameCatalog.kt -- current Rylween game handles and catalog records.
//
// This is ordinary Kotlin source for now, not generated metadata. It is kept in
// main only because some reusable runtime helpers still reference current-game
// handles directly; moving those dependencies behind content/runtime parameters
// is the next catalog cleanup step.

@file:Suppress(
    "unused",
    "MemberVisibilityCanBePrivate",
    "RedundantVisibilityModifier",
    "UNUSED_PARAMETER",
)

class GarthBanditsFlag : FlagRef("garth.bandits") {
    val none = state("none")
    val accepted = state("accepted")
    val recovered = state("recovered")
    val closed = state("closed")
}

class GarthFlags {
    val bandits = GarthBanditsFlag().declareFlag()
}

class RangerLetterFlag : FlagRef("ranger.letter") {
    val none = state("none")
    val accepted = state("accepted")
    val delivered = state("delivered")
}

class RangerFlags {
    val letter = RangerLetterFlag().declareFlag()
}

class FighterPriestBookFlag : FlagRef("fighter.priest_book") {
    val none = state("none")
    val priestTold = state("priest_told")
    val priestVisited = state("priest_visited")
    val wizardRobbed = state("wizard_robbed")
    val fighterGrateful = state("fighter_grateful")
}

class FighterFlags {
    val priestBook = FighterPriestBookFlag().declareFlag()
}

class ChurchDonationFlag : FlagRef("church.donation.once") {
    val no = state("no")
    val yes = state("yes")
}

class ChurchFlags {
    val donationOnce = ChurchDonationFlag().declareFlag()
}

class SilverKeyFlag : FlagRef("silver_key") {
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

class ErethorLuteFlag : FlagRef("erethor.lute") {
    val none = state("none")
    val rumourTold = state("rumour_told")
    val luteTaken = state("lute_taken")
    val noteReceived = state("note_received")
    val cloakReceived = state("cloak_received")
}

class ErethorFlags {
    val lute = ErethorLuteFlag().declareFlag()
}

class BraceletsThunFlag : FlagRef("bracelets.thun") {
    val none = state("none")
    val mapBought = state("map_bought")
    val braceletsTaken = state("bracelets_taken")
}

class BraceletsFlags {
    val thun = BraceletsThunFlag().declareFlag()
}

class NymphSeekerFlag : FlagRef("nymph.seeker") {
    val none = state("none")
    val told = state("told")
    val wizard3tActive = state("wizard3t_active")
}

class NymphFlags {
    val seeker = NymphSeekerFlag().declareFlag()
}

class PeasantWifeFlag : FlagRef("peasant_wife") {
    val none = state("none")
    val accepted = state("accepted")
    val rescued = state("rescued")
    val paid = state("paid")
}

class PeasantFlags {
    val wife = PeasantWifeFlag().declareFlag()
}

class JazzyOgreFlag : FlagRef("jazzy_ogre") {
    val none = state("none")
    val accepted = state("accepted")
    val ogresCleared = state("ogres_cleared")
    val jazzyDefeated = state("jazzy_defeated")
}

class JazzyFlags {
    val ogre = JazzyOgreFlag().declareFlag()
}

class MasterCrownFlag : FlagRef("mastercrown") {
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

class HermitTrollToothFlag : FlagRef("hermit.troll_tooth") {
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
// Current game item handles.
// -----------------------------------------------------------------------------

class ItemRefs {
    // Weapons.
    val blank = item("blank")
    val none = blank
    val sword = item("sword")
    val axe = item("axe")
    val bow = item("bow")
    val club = item("club")
    val dart = item("dart")
    val sling = item("sling")
    val longsword = item("longsword")
    val trident = item("trident")
    val halbard = item("halbard")
    val poleArm = item("pole_arm")
    val crossbow = item("crossbow")
    val scythe = item("scythe")
    val scimitar = item("scimitar")
    val twoHandSword = item("two_hand_sword")
    val magicDagger = item("magic_dagger")
    val magicSword = item("magic_sword")
    val magicAxe = item("magic_axe")
    val dancingSword = item("dancing_sword")
    val deathBlade = item("death_blade")

    // Armor.
    val leather = item("leather")
    val chainMail = item("chain_mail")
    val plateMail = item("plate_mail")
    val ringMail = item("ring_mail")
    val elvinCloak = item("elvin_cloak")
    val mithrilMail = item("mithril_mail")
    val admtMail = item("admt_mail")
    val protSphere = item("prot_sphere")
    val cloakOfGods = item("cloak_of_gods")

    // Gear.
    val torch = item("torch")
    val ration = item("ration")
    val blanket = item("blanket")
    val tent = item("tent")
    val rope = item("rope")

    // Potions.
    val redPotion = item("red_potion")
    val bluePotion = item("blue_potion")
    val amberPotion = item("amber_potion")

    // Treasures.
    val redGem = item("red_gem")
    val blueGem = item("blue_gem")
    val greenGem = item("green_gem")
    val chalice = item("chalice")
    val pendant = item("pendant")
    val broche = item("broche")
    val crystalBowl = item("crystal_bowl")
    val necklace = item("necklace")
    val donkey = item("donkey")

    // Flag-backed special items project from milestones and do not occupy slots.
    val fireshield = item("fireshield")
    val bracelets = item("bracelets")
    val mastercrown = item("mastercrown")
    val jazzyfoot = item("jazzyfoot")
    val silverkey = item("silverkey")

    // Magic items.
    val frostWand = item("frost_wand")
    val fireWand = item("fire_wand")
    val gemOfConfusion = item("gem_of_confusion")
    val gemOfBlindness = item("gem_of_blindness")
    val drumsOfDeath = item("drums_of_death")
    val staffOfHealing = item("staff_of_healing")
    val blessingScroll = item("blessing_scroll")
    val staffOfCuring = item("staff_of_curing")
    val gemOfResurrection = item("gem_of_resurrection")
    val kaelsAxe = item("kaels_axe")
    val holyHandgrenade = item("holy_handgrenade")
    val fluteOfMadness = item("flute_of_madness")
}
val Items = ItemRefs()

class ItemPoolRefs {
    val battleSpoils = itemPool("battle_spoils")
    val magicItems = itemPool("magic_items")
    val tavernScruffySpecials = itemPool("tavern_scruffy_specials")
    val thunAltarGems = itemPool("thun_altar_gems")
    val dungeonWeaponFinds = itemPool("dungeon_weapon_finds")
    val dungeonPotionGemFinds = itemPool("dungeon_potion_gem_finds")
    val dungeonTreasureFinds = itemPool("dungeon_treasure_finds")
}
val ItemPools = ItemPoolRefs()

// -----------------------------------------------------------------------------
// Current game-state milestone handles.
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

    // The 1992 C source encoded "castle cleared / game won" as silverkey == 2. The DSL
    // keeps that as a separate milestone from owning the Silver Key item.
    val ydargVictory = milestone("ydarg_victory")
}
val Milestones = GameMilestoneRefs()

// -----------------------------------------------------------------------------
// Current combat, map, visual, layout, text, scene, and native handles.
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
    val plainland = plain
    val town = terrain("town", '0')
    val hut = terrain("hut", '1')
    val foothills = terrain("foothills", 'f')
    val desert = terrain("desert", 'd')
    val swamp = terrain("swamp", 't')
    val forest = terrain("forest", 'g')
    val grass = forest
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

class StaticScreenRefs {
    val frame = staticScreen("frame")
    val title = staticScreen("title")
    val help = staticScreen("help")
    val manual = staticScreen("manual")
    val score = staticScreen("score")
    val verboseScore = staticScreen("verbose_score")
}
val StaticScreens = StaticScreenRefs()

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

class DungeonEventRefs {
    val findObject = dungeonEvent("find_object")
    val nothingSpecial = dungeonEvent("nothing_special")
    val emptyLevel = dungeonEvent("empty_level")
    val spectrumMessage = dungeonEvent("spectrum_message")
    val gold = dungeonEvent("gold")
    val goldChest = dungeonEvent("gold_chest")
    val torches = dungeonEvent("torches")
    val weakEncounter = dungeonEvent("weak_encounter")
    val deepEncounter = dungeonEvent("deep_encounter")
    val thunRiddle1 = dungeonEvent("thun_riddle1")
    val thunRiddle2 = dungeonEvent("thun_riddle2")
    val thunRiddle3 = dungeonEvent("thun_riddle3")
    val colourRiddle = dungeonEvent("colour_riddle")
    val fireShieldGuardian = dungeonEvent("fire_shield_guardian")
    val fireShield = dungeonEvent("fire_shield")
    val bracelets = dungeonEvent("bracelets")
    val nolrimTomb = dungeonEvent("nolrim_tomb")
    val masterCrown = dungeonEvent("master_crown")
    val silverSanctum = dungeonEvent("silver_sanctum")
    val hobgoblinPatrol = dungeonEvent("hobgoblin_patrol")
    val royalGuard1 = dungeonEvent("royal_guard1")
    val royalGuard2 = dungeonEvent("royal_guard2")
    val hobgoblinChief = dungeonEvent("hobgoblin_chief")
    val orcNest = dungeonEvent("orc_nest")
    val tammysPrison = dungeonEvent("tammys_prison")
    val erethorPriests = dungeonEvent("erethor_priests")
    val luteChamber = dungeonEvent("lute_chamber")
}
val DungeonEvents = DungeonEventRefs()

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
