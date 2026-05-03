// RylweenGameCatalog.kt -- transitional current-game handles.
//
// This is ordinary Kotlin source for now, not generated metadata. It is kept in
// main only for handle families that have not yet moved to generated,
// declaration-derived current-game handles.

@file:Suppress(
    "unused",
    "MemberVisibilityCanBePrivate",
    "RedundantVisibilityModifier",
    "UNUSED_PARAMETER",
    "CanBeParameter",
    "KotlinExplicitApi",
    "PropertyName",
)

package rylween.game

import rylween.dsl.*

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

class LootTableRefs {
    val standardBattle = lootTable("standard_battle")
}
val LootTables = LootTableRefs()

class CombatLootTableRefs {
    val default = LootTables.standardBattle
}
val CombatLootTables = CombatLootTableRefs()

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

class TownRef(override val id: TownId) : TownHandle {
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
    val noNpc = text("speak.no_npc")
    val noRumour = text("speak.no_rumour")
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
