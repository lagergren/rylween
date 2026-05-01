// rylween-dsl.kt -- Kotlin implementation for the Rylween authoring DSL.
//
// This file is implementation/compiler scaffolding: typed handles, immutable
// models, builders, recorded script ASTs, and validators. Game-specific content
// belongs in rylween-full-scratch.dsl.kts.
//
// Validate the split scratch source with:
//
//   kotlinc rylween-dsl.kt -d /tmp/rylween-dsl.jar
//   kotlinc -classpath /tmp/rylween-dsl.jar -script rylween-full-scratch.dsl.kts
//
// Current authoring goals:
//
//   1. Kotlin is the source format.
//   2. Scripts record a real AST instead of being Unit-shaped notes.
//   3. Flags, monsters, items, scenes, text ids, NPC roles, and spells are
//      typed handles.
//   4. A script author cannot write arbitrary flag or encounter strings at use
//      sites. The implementor declares the native slug once; script bodies use
//      typed state markers and catalog handles.
//   5. Game scripts mostly use game verbs. Engine-native calls are explicit
//      under `native.*`.
//   6. Kotlin implementation code should be idiomatic Kotlin.
//   7. Game-specific declarations should read like game data, not Kotlin
//      programming. The generated handle classes in this scratch file are
//      migration scaffolding; a real content compiler should generate them from
//      the author's declarations.

@file:Suppress(
    "unused",
    "MemberVisibilityCanBePrivate",
    "RedundantVisibilityModifier",
    "UNUSED_PARAMETER",
)

// =============================================================================
// 01. DSL kernel: typed handles and generated handle catalogs
// =============================================================================
//
// A content compiler should generate this section from the canonical content
// source. During migration it is hand-written from data/world.json5 and the
// imported data side files so authors can write Items.fireWand, not strings.

enum class FlagScope { Party, Encounter }

abstract class FlagRef(
    val slug: String,
    val scope: FlagScope = FlagScope.Party,
    val legacySlot: Int? = null,
) {
    private val declaredStates = mutableListOf<FlagState>()
    val states: List<FlagState> get() = declaredStates

    protected fun state(native: String): FlagState =
        FlagState(this, native).also { declaredStates += it }
}

open class FlagState internal constructor(
    val flag: FlagRef,
    val native: String,
) : BoolExpr() {
    override val ast: BoolAst = BoolAst.FlagIs(flag.slug, native)
}

val declaredFlags = mutableListOf<FlagRef>()

fun <F : FlagRef> F.declareFlag(): F = also { declaredFlags += it }

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

data class TextId(val id: String)
data class TextPattern(val pattern: String)
data class ItemId(val slug: String)
data class MonsterId(val slug: String)
data class EncounterTier(val slug: String)
data class WorldMapId(val slug: String)
data class MapLayerId(val slug: String)
data class TerrainId(val slug: String, val legacyMapCode: Char? = null)
data class TerrainTagId(val slug: String)
data class ImageId(val slug: String)
data class PaletteId(val slug: String)
data class PaletteColorId(val slug: String)
data class LayoutId(val slug: String)
data class ScreenRegionId(val slug: String)
data class SceneId(val slug: String)
data class SpellId(val slug: String)
data class MissionId(val slug: String)
data class NpcRole(val slug: String)
data class NativeOpId(val slug: String)
data class StateId(val slug: String)
data class PartyId(val slug: String)
data class NpcId(val slug: String)
data class LocationId(val slug: String)
data class TownId(val slug: String)
data class BuildingId(val slug: String)
data class FormulaId(val slug: String)
data class KeyActionId(val slug: String)
data class DungeonId(val slug: String)
data class GameMilestoneId(val slug: String)

fun text(id: String) = TextId(id)
fun textPattern(pattern: String) = TextPattern(pattern)
fun item(slug: String) = ItemId(slug)
fun monster(slug: String) = MonsterId(slug)
fun encounterTier(slug: String) = EncounterTier(slug)
fun worldMap(slug: String) = WorldMapId(slug)
fun mapLayer(slug: String) = MapLayerId(slug)
fun terrain(slug: String, legacyMapCode: Char? = null) = TerrainId(slug, legacyMapCode)
fun terrainTag(slug: String) = TerrainTagId(slug)
fun image(slug: String) = ImageId(slug)
fun palette(slug: String) = PaletteId(slug)
fun paletteColor(slug: String) = PaletteColorId(slug)
fun layout(slug: String) = LayoutId(slug)
fun screenRegion(slug: String) = ScreenRegionId(slug)
fun sceneId(slug: String) = SceneId(slug)
fun spellId(slug: String) = SpellId(slug)
fun missionId(slug: String) = MissionId(slug)
fun npcRole(slug: String) = NpcRole(slug)
fun nativeOp(slug: String) = NativeOpId(slug)
fun stateId(slug: String) = StateId(slug)
fun partyId(slug: String) = PartyId(slug)
fun npc(slug: String) = NpcId(slug)
fun location(slug: String) = LocationId(slug)
fun town(slug: String) = TownId(slug)
fun building(slug: String) = BuildingId(slug)
fun formula(slug: String) = FormulaId(slug)
fun keyAction(slug: String) = KeyActionId(slug)
fun dungeon(slug: String) = DungeonId(slug)
fun milestone(slug: String) = GameMilestoneId(slug)

// -----------------------------------------------------------------------------
// Generated item handles from data/world/items/*.json5.
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

    // Flag-backed special items. These need a B7 decision before save-state
    // modeling: story flag, inventory item, capability, or party status field.
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

    val churchHeal = Towne.churchHeal
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
    val churchHeal = sceneId("towne.church.heal")
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
}
val KeyActions = KeyActionRefs()

class StateRefs {
    val freshReplay = stateId("fresh-replay")
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

class BuildingRefs {
    val none = building("none")
    val inn = building("inn")
    val tavern = building("tavern")
    val church = building("church")
    val smith = building("smith")
    val merchant = building("merchant")
    val hut = building("hut")
    val lodge = building("lodge")
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
    val who = text("towne.church.who")
    val corpse = text("towne.church.corpse")
    val noHealing = text("towne.church.no_healing")
    val cost = text("towne.church.cost")
    val noGold = text("towne.church.no_gold2")
    val healed = text("towne.church.healed")
}

class RumourRefs {
    val r12 = text("towne.rumour.r12")
    val r13 = text("towne.rumour.r13")
    val r25 = text("towne.rumour.r25")
    val r27 = text("towne.rumour.r27")
    val indexed = textPattern("towne.rumour.r{}")
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

class NativeOpRefs {
    val screenClearText = nativeOp("screen.clear_text")
    val screenRedrawMap = nativeOp("screen.redraw_map")
    val inventoryAdd = nativeOp("inventory.add")
    val ydargInit = nativeOp("ydarg.init")
    val ydargClearRoomAtCurrent = nativeOp("ydarg.clear_room_at_current")
    val ydargTryMove = nativeOp("ydarg.try_move")
    val rumourRoll = nativeOp("rumour.roll")
    val ydargRoomAtCurrent = nativeOp("ydarg.room_at_current")
    val ydargExitRequested = nativeOp("ydarg.exit_requested")
    val inputKeyUpper = nativeOp("input.key_upper")
    val partyMemberName = nativeOp("party.member.name")
    val combatMonsterName = nativeOp("combat.monster.name")
    val partyPronounSubject = nativeOp("party.member.pronoun.subject")
    val partyPronounObject = nativeOp("party.member.pronoun.object")
    val partyPronounPossessive = nativeOp("party.member.pronoun.possessive")
    val npcRoleName = nativeOp("npc.role.name")
    val npcRoleLocation = nativeOp("npc.role.location")
}
val NativeOps = NativeOpRefs()

// =============================================================================
// 02. DSL kernel: recorded script AST
// =============================================================================

sealed interface IntAst {
    data class Lit(val value: Int) : IntAst
    data class Var(val name: String) : IntAst
    data class Binary(val left: IntAst, val op: String, val right: IntAst) : IntAst
    data class Random(val lo: IntAst, val hi: IntAst) : IntAst
    data class MemberField(val member: IntAst, val field: String) : IntAst
    data class MonsterField(val monster: IntAst, val field: String) : IntAst
    data class PartyScalar(val name: String) : IntAst
    data class NativeInt(val path: String, val args: List<String>) : IntAst
}

sealed interface BoolAst {
    data class Cmp(val left: IntAst, val op: String, val right: IntAst) : BoolAst
    data class Not(val expr: BoolAst) : BoolAst
    data class And(val left: BoolAst, val right: BoolAst) : BoolAst
    data class Or(val left: BoolAst, val right: BoolAst) : BoolAst
    data class FlagIs(val slug: String, val nativeValue: String) : BoolAst
    data class MemberFlag(val member: IntAst, val name: String) : BoolAst
    data class MonsterFlag(val monster: IntAst, val name: String) : BoolAst
}

sealed interface StrAst {
    data class NativeString(val path: String, val args: List<String>) : StrAst
}

sealed interface LValueAst {
    data class PartyScalar(val name: String) : LValueAst
    data class MemberField(val member: IntAst, val field: String) : LValueAst
    data class MonsterField(val monster: IntAst, val field: String) : LValueAst
}

sealed interface Op {
    data class Screen(val text: TextId) : Op
    data class Emit(val text: TextId) : Op
    data class EmitIndexed(val pattern: TextPattern, val value: IntAst) : Op
    data class WaitInput(val reason: String? = null) : Op
    data class Pause(val millis: Int) : Op
    data class LetInt(val name: String, val value: IntAst) : Op
    data class BindText(val name: String, val value: StrAst) : Op
    data class Set(val target: LValueAst, val value: IntAst) : Op
    data class Add(val target: LValueAst, val value: IntAst) : Op
    data class Sub(val target: LValueAst, val value: IntAst) : Op
    data class SetFlag(val slug: String, val nativeValue: String) : Op
    data class If(val cond: BoolAst, val thenOps: List<Op>, val elseOps: List<Op>) : Op
    data class Loop(val body: List<Op>) : Op
    data class Break(val reason: String? = null) : Op
    data class Stop(val reason: String? = null) : Op
    data class Switch(val expr: IntAst, val cases: List<SwitchCase>) : Op
    data class AskMember(val prompt: TextId, val varName: String) : Op
    data class AskMonster(val prompt: TextId, val varName: String) : Op
    data class ForEachMonster(val varName: String, val includeDead: Boolean, val body: List<Op>) : Op
    data class RunScene(val scene: SceneId, val bindings: Map<String, String>) : Op
    data class Encounter(val foe: MonsterId, val mandatory: Boolean, val tier: EncounterTier? = null) : Op
    data class TakeItem(val item: ItemId) : Op
    data class AchieveMilestone(val milestone: GameMilestoneId) : Op
    data class NativeCall(val path: String, val args: List<String>) : Op
}

data class SwitchCase(val match: Int?, val body: List<Op>)

data class SceneModel(val id: SceneId, val ops: List<Op>)
data class SpellModel(val id: SpellId, val slot: Int, val baseCost: Int, val name: String, val ops: List<Op>)
data class MagicItemModel(val id: ItemId, val ops: List<Op>)

data class FlagContentModel(
    val ref: FlagRef,
    val states: List<FlagState>,
)

data class MonsterModel(
    val id: MonsterId,
    val name: String,
    val count: IntRange,
    val hitPoints: IntRange,
    val weaponSkill: Int,
    val spellPower: Int,
    val tier: EncounterTier?,
    val immune: Boolean,
)

data class MissionModel(
    val id: MissionId,
    val trigger: Trigger,
    val requires: BoolAst?,
    val action: SceneId,
)

enum class LegacySpecialField { Fireshield, Bracelets, Mastercrown, Jazzyfoot, Silverkey }

data class LegacySpecialMapping(
    val field: LegacySpecialField,
    val achievedValue: Int,
)

data class GameMilestoneModel(
    val id: GameMilestoneId,
    val specialInventoryItem: ItemId?,
    val requiredToWin: Boolean,
    val legacySpecial: LegacySpecialMapping?,
)

data class WinConditionModel(
    val required: List<GameMilestoneId>,
    val completedBy: GameMilestoneId?,
)

data class GameProgressModel(
    val milestones: List<GameMilestoneModel>,
    val win: WinConditionModel?,
)

data class GameModel(
    val flags: List<FlagRef>,
    val authoredFlags: List<FlagContentModel>,
    val progress: GameProgressModel,
    val monsters: List<MonsterModel>,
    val scenes: List<SceneModel>,
    val spells: List<SpellModel>,
    val magicItems: List<MagicItemModel>,
    val missions: List<MissionModel>,
)

sealed interface Trigger {
    data class RandomStep(val percent: Int) : Trigger
}

// =============================================================================
// 03. DSL kernel: exact state model
// =============================================================================

enum class Gender { Male, Female }
enum class Race { Human, Dwarf, Elf, Halfling }
enum class PcClass { Fighter, Thief, Mage, Priest, Bard, Ranger }

data class HitPoints(val current: Int, val max: Int)
infix fun Int.of(max: Int): HitPoints = HitPoints(this, max)
data class Mana(val current: Int, val max: Int)
infix fun Int.manaOf(max: Int): Mana = Mana(this, max)

data class GridPoint(val x: Int, val y: Int)
fun xy(x: Int, y: Int): GridPoint = GridPoint(x, y)

sealed interface WorldPosition {
    data class Wilderness(val point: GridPoint) : WorldPosition
    data class Town(val town: TownId, val building: BuildingId) : WorldPosition
}

object Wilderness {
    fun xy(x: Int, y: Int): WorldPosition = WorldPosition.Wilderness(GridPoint(x, y))
}

data class RealmState(
    val day: Int,
    val hour: Int,
    val activeParty: PartyId,
)

data class LocationPlacement(val location: LocationId, val point: GridPoint)
data class NpcPlacement(val npc: NpcId, val position: WorldPosition)

data class PartyResources(
    val gold: Int,
    val torches: Int,
    val food: Int,
    val timeLeft: Int,
    val restedHours: Int,
)

data class InventorySlotState(val slot: Int, val item: ItemId?)

data class PartyMemberState(
    val name: String,
    val gender: Gender,
    val race: Race,
    val pcClass: PcClass,
    val level: Int,
    val health: Int,
    val hitPoints: HitPoints,
    val weaponSkill: Int,
    val wisdom: Int,
    val mana: Mana,
    val charisma: Int,
    val armor: ItemId,
    val weapon: ItemId,
    val experience: Int,
    val dead: Boolean,
)

data class PartyState(
    val id: PartyId,
    val player: String,
    val name: String,
    val position: WorldPosition,
    val resources: PartyResources,
    val flags: List<FlagState>,
    val milestones: List<GameMilestoneId>,
    val inventory: List<InventorySlotState>,
    val members: List<PartyMemberState>,
    val visitedNpcs: List<NpcId>,
    val journal: List<TextId>,
)

data class StateModel(
    val id: StateId,
    val realm: RealmState,
    val locations: List<LocationPlacement>,
    val npcs: List<NpcPlacement>,
    val parties: List<PartyState>,
)

data class RylweenModel(
    val content: GameModel,
    val states: List<StateModel>,
)

// =============================================================================
// 04. DSL kernel: readable expressions and mutable slots
// =============================================================================

abstract class IntExpr {
    abstract val ast: IntAst

    operator fun plus(other: IntExpr): IntExpr = Expr(IntAst.Binary(ast, "add", other.ast))
    operator fun plus(other: Int): IntExpr = this + lit(other)
    operator fun minus(other: IntExpr): IntExpr = Expr(IntAst.Binary(ast, "sub", other.ast))
    operator fun minus(other: Int): IntExpr = this - lit(other)
    operator fun times(other: IntExpr): IntExpr = Expr(IntAst.Binary(ast, "mul", other.ast))
    operator fun times(other: Int): IntExpr = this * lit(other)
    operator fun div(other: IntExpr): IntExpr = Expr(IntAst.Binary(ast, "div", other.ast))
    operator fun div(other: Int): IntExpr = this / lit(other)
}

data class Expr(override val ast: IntAst) : IntExpr()

fun lit(value: Int): IntExpr = Expr(IntAst.Lit(value))
fun v(name: String): IntExpr = Expr(IntAst.Var(name))
fun random(range: IntRange): IntExpr = Expr(IntAst.Random(IntAst.Lit(range.first), IntAst.Lit(range.last)))

infix fun IntExpr.eq(other: IntExpr): BoolExpr = Bool(BoolAst.Cmp(ast, "eq", other.ast))
infix fun IntExpr.eq(other: Int): BoolExpr = this eq lit(other)
infix fun IntExpr.ne(other: IntExpr): BoolExpr = Bool(BoolAst.Cmp(ast, "ne", other.ast))
infix fun IntExpr.lt(other: IntExpr): BoolExpr = Bool(BoolAst.Cmp(ast, "lt", other.ast))
infix fun IntExpr.lt(other: Int): BoolExpr = this lt lit(other)
infix fun IntExpr.lte(other: Int): BoolExpr = Bool(BoolAst.Cmp(ast, "lte", IntAst.Lit(other)))
infix fun IntExpr.gt(other: Int): BoolExpr = Bool(BoolAst.Cmp(ast, "gt", IntAst.Lit(other)))
infix fun IntExpr.gte(other: Int): BoolExpr = Bool(BoolAst.Cmp(ast, "gte", IntAst.Lit(other)))

abstract class BoolExpr {
    abstract val ast: BoolAst

    infix fun and(other: BoolExpr): BoolExpr = Bool(BoolAst.And(ast, other.ast))
    infix fun or(other: BoolExpr): BoolExpr = Bool(BoolAst.Or(ast, other.ast))
    operator fun not(): BoolExpr = Bool(BoolAst.Not(ast))
}

data class Bool(override val ast: BoolAst) : BoolExpr()

class IntSlot(
    private val script: ScriptScope,
    private val lvalue: LValueAst,
    override val ast: IntAst,
) : IntExpr() {
    infix fun set(value: IntExpr) = script.record(Op.Set(lvalue, value.ast))
    infix fun set(value: Int) = set(lit(value))
    operator fun plusAssign(value: IntExpr) = script.record(Op.Add(lvalue, value.ast))
    operator fun plusAssign(value: Int) = plusAssign(lit(value))
    operator fun minusAssign(value: IntExpr) = script.record(Op.Sub(lvalue, value.ast))
    operator fun minusAssign(value: Int) = minusAssign(lit(value))
}

class BoolSlot(
    private val script: ScriptScope,
    private val lvalue: LValueAst,
    private val read: BoolAst,
) : BoolExpr() {
    override val ast: BoolAst = read
    infix fun set(value: Boolean) = script.record(Op.Set(lvalue, IntAst.Lit(if (value) 1 else 0)))
}

class PartyRef(private val script: ScriptScope) {
    val gold = IntSlot(script, LValueAst.PartyScalar("gold"), IntAst.PartyScalar("gold"))
    fun member(index: IntExpr): MemberRef = MemberRef(script, index)
}

class MemberRef(private val script: ScriptScope, val index: IntExpr) {
    val hp = intField("hit_points")
    val maxHp = intField("health")
    val mp = intField("magic")
    val level = intField("level")
    val wisdom = intField("wisdom")
    val dead = BoolSlot(
        script,
        LValueAst.MemberField(index.ast, "dead"),
        BoolAst.MemberFlag(index.ast, "dead"),
    )

    private fun intField(field: String): IntSlot =
        IntSlot(script, LValueAst.MemberField(index.ast, field), IntAst.MemberField(index.ast, field))
}

class MonsterRef(private val script: ScriptScope, val index: IntExpr) {
    val hp = IntSlot(script, LValueAst.MonsterField(index.ast, "hit_points"), IntAst.MonsterField(index.ast, "hit_points"))
    val dead = BoolSlot(
        script,
        LValueAst.MonsterField(index.ast, "dead"),
        BoolAst.MonsterFlag(index.ast, "dead"),
    )
}

sealed interface StrExpr {
    val ast: StrAst
}

data class Str(override val ast: StrAst) : StrExpr

enum class PronounCase { Subject, Object, Possessive }

sealed class NativeSpec(val id: NativeOpId, val reason: String) {
    val path: String get() = id.slug
}
class NativeEffectSpec(id: NativeOpId, reason: String) : NativeSpec(id, reason)
class NativeIntSpec(id: NativeOpId, reason: String) : NativeSpec(id, reason)
class NativeStringSpec(id: NativeOpId, reason: String) : NativeSpec(id, reason)

class NativeAllowlistRefs {
    val screenClearText = effect(NativeOps.screenClearText, "text-window rendering primitive")
    val screenRedrawMap = effect(NativeOps.screenRedrawMap, "map rendering primitive")
    val inventoryAdd = effect(NativeOps.inventoryAdd, "legacy inventory slot mutation")
    val ydargInit = effect(NativeOps.ydargInit, "legacy Ydarg dungeon state setup")
    val ydargClearRoomAtCurrent = effect(NativeOps.ydargClearRoomAtCurrent, "legacy Ydarg room state mutation")
    val ydargTryMove = effect(NativeOps.ydargTryMove, "legacy Ydarg movement and collision")

    val rumourRoll = int(NativeOps.rumourRoll, "legacy rumour distribution")
    val ydargRoomAtCurrent = int(NativeOps.ydargRoomAtCurrent, "legacy Ydarg room table lookup")
    val ydargExitRequested = int(NativeOps.ydargExitRequested, "legacy Ydarg exit flag")
    val inputKeyUpper = int(NativeOps.inputKeyUpper, "keyboard input normalization")

    val partyMemberName = string(NativeOps.partyMemberName, "party-member display name binding")
    val combatMonsterName = string(NativeOps.combatMonsterName, "combat monster display name binding")
    val partyPronounSubject = string(NativeOps.partyPronounSubject, "party-member pronoun binding")
    val partyPronounObject = string(NativeOps.partyPronounObject, "party-member pronoun binding")
    val partyPronounPossessive = string(NativeOps.partyPronounPossessive, "party-member pronoun binding")
    val npcRoleName = string(NativeOps.npcRoleName, "role-resolved NPC name binding")
    val npcRoleLocation = string(NativeOps.npcRoleLocation, "role-resolved NPC location binding")

    val all: List<NativeSpec> = listOf(
        screenClearText,
        screenRedrawMap,
        inventoryAdd,
        ydargInit,
        ydargClearRoomAtCurrent,
        ydargTryMove,
        rumourRoll,
        ydargRoomAtCurrent,
        ydargExitRequested,
        inputKeyUpper,
        partyMemberName,
        combatMonsterName,
        partyPronounSubject,
        partyPronounObject,
        partyPronounPossessive,
        npcRoleName,
        npcRoleLocation,
    )

    val byPath: Map<String, NativeSpec> = all.associateBy { it.path }

    init {
        check(byPath.size == all.size) { "duplicate native allowlist path" }
    }

    private fun effect(id: NativeOpId, reason: String) = NativeEffectSpec(id, reason)
    private fun int(id: NativeOpId, reason: String) = NativeIntSpec(id, reason)
    private fun string(id: NativeOpId, reason: String) = NativeStringSpec(id, reason)
}
val NativeAllowlist = NativeAllowlistRefs()

fun partyName(member: MemberRef): StrExpr =
    Str(StrAst.NativeString(NativeAllowlist.partyMemberName.path, listOf(render(member.index.ast))))

fun monsterName(monster: MonsterRef): StrExpr =
    Str(StrAst.NativeString(NativeAllowlist.combatMonsterName.path, listOf(render(monster.index.ast))))

fun pronoun(member: MemberRef, case: PronounCase): StrExpr {
    val native = when (case) {
        PronounCase.Subject -> NativeAllowlist.partyPronounSubject
        PronounCase.Object -> NativeAllowlist.partyPronounObject
        PronounCase.Possessive -> NativeAllowlist.partyPronounPossessive
    }
    return Str(StrAst.NativeString(native.path, listOf(render(member.index.ast))))
}

fun npcName(role: NpcRole): StrExpr =
    Str(StrAst.NativeString(NativeAllowlist.npcRoleName.path, listOf(role.slug)))

fun npcLocation(role: NpcRole): StrExpr =
    Str(StrAst.NativeString(NativeAllowlist.npcRoleLocation.path, listOf(role.slug)))

fun render(ast: IntAst): String = ast.toString()
fun render(arg: Any): String = when (arg) {
    is IntExpr -> render(arg.ast)
    is IntAst -> render(arg)
    is TextId -> arg.id
    is ItemId -> arg.slug
    is MonsterId -> arg.slug
    is EncounterTier -> arg.slug
    is WorldMapId -> arg.slug
    is MapLayerId -> arg.slug
    is TerrainId -> arg.slug
    is TerrainTagId -> arg.slug
    is ImageId -> arg.slug
    is PaletteId -> arg.slug
    is PaletteColorId -> arg.slug
    is LayoutId -> arg.slug
    is ScreenRegionId -> arg.slug
    is SceneId -> arg.slug
    is SpellId -> arg.slug
    is MissionId -> arg.slug
    is NpcRole -> arg.slug
    is NativeOpId -> arg.slug
    is FlagState -> "${arg.flag.slug}.${arg.native}"
    is StateId -> arg.slug
    is PartyId -> arg.slug
    is NpcId -> arg.slug
    is LocationId -> arg.slug
    is TownId -> arg.slug
    is BuildingId -> arg.slug
    is FormulaId -> arg.slug
    is KeyActionId -> arg.slug
    is DungeonId -> arg.slug
    is GameMilestoneId -> arg.slug
    else -> arg.toString()
}

// =============================================================================
// 05. DSL kernel: builders and validation
// =============================================================================

class ScriptContext {
    private var nextVar = 0

    fun fresh(prefix: String): String = "${prefix}_${nextVar++}"
}

@DslMarker
annotation class RylNextDsl

@RylNextDsl
class ScriptScope(
    private val ctx: ScriptContext,
    private val ops: MutableList<Op>,
) {
    val party = PartyRef(this)
    val ask = AskScope(this)
    val native = NativeScope(this)

    val currentCaster: MemberRef
        get() = party.member(v("caster_idx"))

    fun record(op: Op) {
        ops += op
    }

    fun screen(id: TextId) = record(Op.Screen(id))
    fun emit(id: TextId) = record(Op.Emit(id))
    fun emitIndexed(pattern: TextPattern, value: IntExpr) = record(Op.EmitIndexed(pattern, value.ast))
    fun waitInput(reason: String? = null) = record(Op.WaitInput(reason))
    fun pause(ms: Int) = record(Op.Pause(ms))

    fun let(name: String, value: IntExpr): IntExpr {
        record(Op.LetInt(name, value.ast))
        return v(name)
    }

    fun bind(name: String, value: StrExpr) {
        record(Op.BindText(name, value.ast))
    }

    fun mark(state: FlagState) {
        record(Op.SetFlag(state.flag.slug, state.native))
    }

    fun when_(cond: BoolExpr, then: ScriptScope.() -> Unit): ElseBranch {
        val thenOps = capture(then)
        val index = ops.size
        ops += Op.If(cond.ast, thenOps, emptyList())
        return ElseBranch { elseOps -> ops[index] = Op.If(cond.ast, thenOps, elseOps) }
    }

    fun stop(reason: String? = null) = record(Op.Stop(reason))
    fun breakLoop(reason: String? = null) = record(Op.Break(reason))

    fun loop(body: ScriptScope.() -> Unit) {
        record(Op.Loop(capture(body)))
    }

    fun choose(expr: IntExpr, body: SwitchBuilder.() -> Unit) {
        val builder = SwitchBuilder(this)
        builder.body()
        record(Op.Switch(expr.ast, builder.cases))
    }

    fun forEachMonster(includeDead: Boolean = false, body: ScriptScope.(MonsterRef) -> Unit) {
        val name = ctx.fresh("monster")
        val childOps = capture {
            body(MonsterRef(this, v(name)))
        }
        record(Op.ForEachMonster(name, includeDead, childOps))
    }

    fun run(scene: SceneId, vararg bindings: Pair<String, Any>) {
        record(Op.RunScene(scene, bindings.associate { it.first to render(it.second) }))
    }

    fun encounter(monster: MonsterId, mandatory: Boolean = false, tier: EncounterTier? = null) {
        record(Op.Encounter(monster, mandatory, tier))
    }

    fun take(item: ItemId) = record(Op.TakeItem(item))

    fun achieve(milestone: GameMilestoneId) {
        record(Op.AchieveMilestone(milestone))
    }

    fun pay(amount: IntExpr, broke: ScriptScope.() -> Unit, ok: ScriptScope.() -> Unit) {
        when_(party.gold lt amount) {
            broke()
        } otherwise {
            party.gold -= amount
            ok()
        }
    }

    private fun capture(body: ScriptScope.() -> Unit): List<Op> {
        return buildList {
            ScriptScope(ctx, this).body()
        }
    }
}

class ElseBranch(private val finish: (List<Op>) -> Unit) {
    infix fun otherwise(body: ScriptScope.() -> Unit) {
        finish(buildList {
            ScriptScope(ScriptContext(), this).body()
        })
    }
}

@RylNextDsl
class SwitchBuilder(private val parent: ScriptScope) {
    val cases = mutableListOf<SwitchCase>()

    fun case(value: Int, body: ScriptScope.() -> Unit) {
        cases += SwitchCase(value, buildList {
            ScriptScope(ScriptContext(), this).body()
        })
    }

    fun default(body: ScriptScope.() -> Unit) {
        cases += SwitchCase(null, buildList {
            ScriptScope(ScriptContext(), this).body()
        })
    }
}

class AskScope(private val script: ScriptScope) {
    fun member(prompt: TextId): MemberRef {
        val name = "member_pick"
        script.record(Op.AskMember(prompt, name))
        return script.party.member(v(name))
    }

    fun monsterAlive(prompt: TextId): MonsterRef {
        val name = "monster_pick"
        script.record(Op.AskMonster(prompt, name))
        return MonsterRef(script, v(name))
    }
}

class NativeScope(private val script: ScriptScope) {
    // Native is deliberately narrow: rendering, keyboard input, inventory slot
    // plumbing, legacy Ydarg state, and helper calls whose implementation is
    // genuinely engine-owned. Game rules should become typed DSL verbs before
    // they are allowed here.
    val screen = NativeScreen(script)
    val rumour = NativeRumour(script)
    val inventory = NativeInventory(script)
    val ydarg = NativeYdarg(script)
    val input = NativeInput(script)
}

class NativeScreen(private val script: ScriptScope) {
    fun clearText() = script.record(Op.NativeCall(NativeAllowlist.screenClearText.path, emptyList()))
    fun redrawMap() = script.record(Op.NativeCall(NativeAllowlist.screenRedrawMap.path, emptyList()))
}

class NativeRumour(private val script: ScriptScope) {
    fun roll(): IntExpr = Expr(IntAst.NativeInt(NativeAllowlist.rumourRoll.path, emptyList()))
}

class NativeInventory(private val script: ScriptScope) {
    fun add(itemId: IntExpr) =
        script.record(Op.NativeCall(NativeAllowlist.inventoryAdd.path, listOf(render(itemId))))
}

class NativeYdarg(private val script: ScriptScope) {
    fun init() = script.record(Op.NativeCall(NativeAllowlist.ydargInit.path, emptyList()))
    fun roomAtCurrent(): IntExpr = Expr(IntAst.NativeInt(NativeAllowlist.ydargRoomAtCurrent.path, emptyList()))
    fun clearRoomAtCurrent() =
        script.record(Op.NativeCall(NativeAllowlist.ydargClearRoomAtCurrent.path, emptyList()))

    fun exitRequested(): BoolExpr =
        Bool(BoolAst.Cmp(IntAst.NativeInt(NativeAllowlist.ydargExitRequested.path, emptyList()), "ne", IntAst.Lit(0)))

    fun tryMove(key: IntExpr) =
        script.record(Op.NativeCall(NativeAllowlist.ydargTryMove.path, listOf(render(key))))
}

class NativeInput(private val script: ScriptScope) {
    fun keyUpper(): IntExpr = Expr(IntAst.NativeInt(NativeAllowlist.inputKeyUpper.path, emptyList()))
}

@RylNextDsl
class GameBuilder {
    private val authoredFlags = mutableListOf<FlagContentModel>()
    private var progressModel = GameProgressModel(emptyList(), null)
    private val monsters = mutableListOf<MonsterModel>()
    private val scenes = mutableListOf<SceneModel>()
    private val spells = mutableListOf<SpellModel>()
    private val magicItems = mutableListOf<MagicItemModel>()
    private val missions = mutableListOf<MissionModel>()

    fun flags(body: FlagContentBuilder.() -> Unit) {
        authoredFlags += FlagContentBuilder().apply(body).build()
    }

    fun gameProgress(body: GameProgressBuilder.() -> Unit) {
        progressModel = GameProgressBuilder().apply(body).build()
    }

    fun monsters(body: MonsterCatalogBuilder.() -> Unit) {
        monsters += MonsterCatalogBuilder().apply(body).build()
    }

    fun missions(body: MissionCatalogBuilder.() -> Unit) {
        missions += MissionCatalogBuilder().apply(body).build()
    }

    fun scene(id: SceneId, body: ScriptScope.() -> Unit) {
        scenes += SceneModel(id, recordScript(body))
    }

    fun spell(id: SpellId, slot: Int, baseCost: Int, name: String, body: ScriptScope.() -> Unit) {
        spells += SpellModel(id, slot, baseCost, name, recordScript(body))
    }

    fun magicItem(id: ItemId, body: ScriptScope.() -> Unit) {
        magicItems += MagicItemModel(id, recordScript(body))
    }

    fun build(): GameModel =
        GameModel(
            flags = declaredFlags.toList(),
            authoredFlags = authoredFlags.toList(),
            progress = progressModel,
            monsters = monsters.toList(),
            scenes = scenes.toList(),
            spells = spells.toList(),
            magicItems = magicItems.toList(),
            missions = missions.toList(),
        )

    private fun recordScript(body: ScriptScope.() -> Unit): List<Op> {
        return buildList {
            ScriptScope(ScriptContext(), this).body()
        }
    }
}

@RylNextDsl
class FlagContentBuilder {
    private val flags = mutableListOf<FlagContentModel>()

    fun story(flag: FlagRef, body: FlagContentEntryBuilder.() -> Unit = {}) {
        require(flag.scope == FlagScope.Party) { "story flag ${flag.slug} must have party scope" }
        flags += FlagContentEntryBuilder(flag).apply(body).build()
    }

    fun encounter(flag: FlagRef, body: FlagContentEntryBuilder.() -> Unit = {}) {
        require(flag.scope == FlagScope.Encounter) { "encounter flag ${flag.slug} must have encounter scope" }
        flags += FlagContentEntryBuilder(flag).apply(body).build()
    }

    fun build(): List<FlagContentModel> = flags.toList()
}

@RylNextDsl
class FlagContentEntryBuilder(private val flag: FlagRef) {
    private val explicitStates = mutableListOf<FlagState>()

    fun states(vararg states: FlagState) {
        explicitStates += states
    }

    fun build(): FlagContentModel =
        FlagContentModel(flag, explicitStates.ifEmpty { flag.states })
}

@RylNextDsl
class MonsterCatalogBuilder {
    private val monsters = mutableListOf<MonsterModel>()

    fun monster(id: MonsterId, body: MonsterBuilder.() -> Unit) {
        monsters += MonsterBuilder(id).apply(body).build()
    }

    fun build(): List<MonsterModel> = monsters.toList()
}

@RylNextDsl
class MonsterBuilder(private val id: MonsterId) {
    var name: String = ""
    var count: IntRange = 1..1
    var hitPoints: IntRange = 1..1
    var weaponSkill: Int = 0
    var spellPower: Int = 0
    var tier: EncounterTier? = null
    var immune: Boolean = false

    fun build(): MonsterModel =
        MonsterModel(id, name, count, hitPoints, weaponSkill, spellPower, tier, immune)
}

@RylNextDsl
class MissionCatalogBuilder {
    private val missions = mutableListOf<MissionModel>()

    fun mission(id: MissionId, body: MissionBuilder.() -> Unit) {
        missions += MissionBuilder(id).apply(body).build()
    }

    fun build(): List<MissionModel> = missions.toList()
}

@RylNextDsl
class MissionBuilder(private val id: MissionId) {
    private var trigger: Trigger? = null
    private var requires: BoolAst? = null
    private var action: SceneId? = null

    fun randomStep(percent: Int) {
        trigger = Trigger.RandomStep(percent)
    }

    fun requires(state: FlagState) {
        requires = state.ast
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

@RylNextDsl
class GameProgressBuilder {
    private val milestones = mutableListOf<GameMilestoneModel>()
    private var winBuilder: WinConditionBuilder? = null

    fun milestone(id: GameMilestoneId, body: GameMilestoneBuilder.() -> Unit) {
        milestones += GameMilestoneBuilder(id).apply(body).build()
    }

    fun winningConfiguration(body: WinConditionBuilder.() -> Unit) {
        winBuilder = WinConditionBuilder().apply(body)
    }

    fun build(): GameProgressModel =
        GameProgressModel(milestones.toList(), winBuilder?.build(milestones))
}

@RylNextDsl
class GameMilestoneBuilder(private val id: GameMilestoneId) {
    private var specialInventoryItem: ItemId? = null
    private var requiredToWin: Boolean = false
    private var legacySpecial: LegacySpecialMapping? = null

    fun showsAs(item: ItemId) {
        specialInventoryItem = item
    }

    fun requiredToWin() {
        requiredToWin = true
    }

    fun legacyPartyField(field: LegacySpecialField, achievedValue: Int = 1) {
        legacySpecial = LegacySpecialMapping(field, achievedValue)
    }

    fun build(): GameMilestoneModel =
        GameMilestoneModel(id, specialInventoryItem, requiredToWin, legacySpecial)
}

@RylNextDsl
class WinConditionBuilder {
    private var includeMarkedRequired = false
    private val explicitRequired = mutableListOf<GameMilestoneId>()
    private var completionMilestone: GameMilestoneId? = null

    fun requiresAllMilestonesMarkedRequired() {
        includeMarkedRequired = true
    }

    fun requires(vararg milestones: GameMilestoneId) {
        explicitRequired += milestones
    }

    fun completedBy(milestone: GameMilestoneId) {
        completionMilestone = milestone
    }

    fun build(defined: List<GameMilestoneModel>): WinConditionModel {
        val markedRequired = if (includeMarkedRequired) {
            defined.filter { it.requiredToWin }.map { it.id }
        } else {
            emptyList()
        }

        return WinConditionModel((explicitRequired + markedRequired).distinct(), completionMilestone)
    }
}

fun rylweenNext(body: GameBuilder.() -> Unit): GameModel =
    GameBuilder().apply(body).build()

@RylNextDsl
class RylweenBuilder {
    private var contentModel: GameModel? = null
    private val states = mutableListOf<StateModel>()

    fun content(body: GameBuilder.() -> Unit) {
        contentModel = GameBuilder().apply(body).build()
    }

    fun state(id: StateId, body: StateBuilder.() -> Unit) {
        states += StateBuilder(id).apply(body).build()
    }

    fun build(): RylweenModel =
        RylweenModel(
            content = checkNotNull(contentModel) { "rylween requires content { ... }" },
            states = states.toList(),
        )
}

fun rylween(body: RylweenBuilder.() -> Unit): RylweenModel =
    RylweenBuilder().apply(body).build()

@RylNextDsl
class StateBuilder(private val id: StateId) {
    private var realmState: RealmState? = null
    private val locationPlacements = mutableListOf<LocationPlacement>()
    private val npcPlacements = mutableListOf<NpcPlacement>()
    private val parties = mutableListOf<PartyState>()

    fun realm(body: RealmStateBuilder.() -> Unit) {
        realmState = RealmStateBuilder().apply(body).build()
    }

    fun locations(body: LocationStateBuilder.() -> Unit) {
        locationPlacements += LocationStateBuilder().apply(body).build()
    }

    fun npcs(body: NpcStateBuilder.() -> Unit) {
        npcPlacements += NpcStateBuilder().apply(body).build()
    }

    fun party(id: PartyId, body: PartyStateBuilder.() -> Unit) {
        parties += PartyStateBuilder(id).apply(body).build()
    }

    fun build(): StateModel =
        StateModel(
            id = id,
            realm = checkNotNull(realmState) { "state ${id.slug} requires realm { ... }" },
            locations = locationPlacements.toList(),
            npcs = npcPlacements.toList(),
            parties = parties.toList(),
        )
}

@RylNextDsl
class RealmStateBuilder {
    var day: Int = 0
    var hour: Int = 0
    var activeParty: PartyId? = null

    fun build(): RealmState =
        RealmState(
            day = day,
            hour = hour,
            activeParty = checkNotNull(activeParty) { "realm requires activeParty" },
        )
}

@RylNextDsl
class LocationStateBuilder {
    private val placements = mutableListOf<LocationPlacement>()

    infix fun LocationId.at(point: GridPoint) {
        placements += LocationPlacement(this, point)
    }

    fun build(): List<LocationPlacement> = placements.toList()
}

@RylNextDsl
class NpcStateBuilder {
    private val placements = mutableListOf<NpcPlacement>()

    infix fun NpcId.at(position: WorldPosition) {
        placements += NpcPlacement(this, position)
    }

    fun build(): List<NpcPlacement> = placements.toList()
}

@RylNextDsl
class PartyStateBuilder(private val id: PartyId) {
    var player: String = ""
    var name: String = ""
    var position: WorldPosition = Wilderness.xy(0, 0)
    private var resourcesState = PartyResources(0, 0, 0, 0, 0)
    private val flags = mutableListOf<FlagState>()
    private val milestones = mutableListOf<GameMilestoneId>()
    private val inventory = mutableListOf<InventorySlotState>()
    private val members = mutableListOf<PartyMemberState>()
    private val visitedNpcs = mutableListOf<NpcId>()
    private val journal = mutableListOf<TextId>()

    fun resources(body: PartyResourcesBuilder.() -> Unit) {
        resourcesState = PartyResourcesBuilder().apply(body).build()
    }

    fun flags(body: StateFlagBuilder.() -> Unit) {
        flags += StateFlagBuilder().apply(body).build()
    }

    fun milestones(body: PartyMilestonesBuilder.() -> Unit) {
        milestones += PartyMilestonesBuilder().apply(body).build()
    }

    fun inventory(body: InventoryStateBuilder.() -> Unit) {
        inventory += InventoryStateBuilder().apply(body).build()
    }

    fun members(body: PartyMembersBuilder.() -> Unit) {
        members += PartyMembersBuilder().apply(body).build()
    }

    fun visited(vararg npcs: NpcId) {
        visitedNpcs += npcs
    }

    fun journal(vararg entries: TextId) {
        journal += entries
    }

    fun build(): PartyState =
        PartyState(
            id = id,
            player = player,
            name = name,
            position = position,
            resources = resourcesState,
            flags = flags.toList(),
            milestones = milestones.toList(),
            inventory = inventory.sortedBy { it.slot },
            members = members.toList(),
            visitedNpcs = visitedNpcs.toList(),
            journal = journal.toList(),
        )
}

@RylNextDsl
class PartyResourcesBuilder {
    var gold: Int = 0
    var torches: Int = 0
    var food: Int = 0
    var timeLeft: Int = 0
    var restedHours: Int = 0

    fun build(): PartyResources = PartyResources(gold, torches, food, timeLeft, restedHours)
}

@RylNextDsl
class StateFlagBuilder {
    private val flags = mutableListOf<FlagState>()

    fun mark(state: FlagState) {
        flags += state
    }

    fun build(): List<FlagState> = flags.toList()
}

@RylNextDsl
class PartyMilestonesBuilder {
    private val milestones = mutableListOf<GameMilestoneId>()

    fun achieved(milestone: GameMilestoneId) {
        milestones += milestone
    }

    fun build(): List<GameMilestoneId> = milestones.toList()
}

@RylNextDsl
class InventoryStateBuilder {
    private val slots = mutableMapOf<Int, InventorySlotState>()

    fun slot(index: Int): InventorySlotBuilder =
        InventorySlotBuilder(index) { slot -> slots[index] = slot }

    fun build(): List<InventorySlotState> = slots.values.sortedBy { it.slot }
}

@RylNextDsl
class InventorySlotBuilder(
    private val index: Int,
    private val record: (InventorySlotState) -> Unit,
) {
    fun empty() {
        record(InventorySlotState(index, null))
    }

    fun item(item: ItemId) {
        record(InventorySlotState(index, item))
    }
}

@RylNextDsl
class PartyMembersBuilder {
    private val members = mutableListOf<PartyMemberState>()

    fun member(name: String, body: PartyMemberBuilder.() -> Unit) {
        members += PartyMemberBuilder(name).apply(body).build()
    }

    fun build(): List<PartyMemberState> = members.toList()
}

@RylNextDsl
class PartyMemberBuilder(private val name: String) {
    var gender: Gender = Gender.Male
    var race: Race = Race.Human
    var klass: PcClass = PcClass.Thief
    var level: Int = 1
    var health: Int = 1
    var hitPoints: HitPoints = 1 of 1
    var weaponSkill: Int = 0
    var wisdom: Int = 0
    var mana: Mana = 0 manaOf 0
    var charisma: Int = 0
    var armor: ItemId = Items.none
    var weapon: ItemId = Items.none
    var experience: Int = 0
    private var dead: Boolean = false

    fun alive() {
        dead = false
    }

    fun dead() {
        dead = true
    }

    fun build(): PartyMemberState =
        PartyMemberState(
            name = name,
            gender = gender,
            race = race,
            pcClass = klass,
            level = level,
            health = health,
            hitPoints = hitPoints,
            weaponSkill = weaponSkill,
            wisdom = wisdom,
            mana = mana,
            charisma = charisma,
            armor = armor,
            weapon = weapon,
            experience = experience,
            dead = dead,
        )
}

fun nativePathsIn(model: GameModel): Set<String> {
    val paths = mutableSetOf<String>()

    fun int(ast: IntAst) {
        when (ast) {
            is IntAst.Lit -> Unit
            is IntAst.Var -> Unit
            is IntAst.Binary -> {
                int(ast.left)
                int(ast.right)
            }
            is IntAst.Random -> {
                int(ast.lo)
                int(ast.hi)
            }
            is IntAst.MemberField -> int(ast.member)
            is IntAst.MonsterField -> int(ast.monster)
            is IntAst.PartyScalar -> Unit
            is IntAst.NativeInt -> paths += ast.path
        }
    }

    fun bool(ast: BoolAst) {
        when (ast) {
            is BoolAst.Cmp -> {
                int(ast.left)
                int(ast.right)
            }
            is BoolAst.Not -> bool(ast.expr)
            is BoolAst.And -> {
                bool(ast.left)
                bool(ast.right)
            }
            is BoolAst.Or -> {
                bool(ast.left)
                bool(ast.right)
            }
            is BoolAst.FlagIs -> Unit
            is BoolAst.MemberFlag -> int(ast.member)
            is BoolAst.MonsterFlag -> int(ast.monster)
        }
    }

    fun str(ast: StrAst) {
        when (ast) {
            is StrAst.NativeString -> paths += ast.path
        }
    }

    fun ops(items: List<Op>) {
        items.forEach { op ->
            when (op) {
                is Op.Screen -> Unit
                is Op.Emit -> Unit
                is Op.EmitIndexed -> int(op.value)
                is Op.WaitInput -> Unit
                is Op.Pause -> Unit
                is Op.LetInt -> int(op.value)
                is Op.BindText -> str(op.value)
                is Op.Set -> int(op.value)
                is Op.Add -> int(op.value)
                is Op.Sub -> int(op.value)
                is Op.SetFlag -> Unit
                is Op.If -> {
                    bool(op.cond)
                    ops(op.thenOps)
                    ops(op.elseOps)
                }
                is Op.Loop -> ops(op.body)
                is Op.Break -> Unit
                is Op.Stop -> Unit
                is Op.Switch -> {
                    int(op.expr)
                    op.cases.forEach { ops(it.body) }
                }
                is Op.AskMember -> Unit
                is Op.AskMonster -> Unit
                is Op.ForEachMonster -> ops(op.body)
                is Op.RunScene -> Unit
                is Op.Encounter -> Unit
                is Op.TakeItem -> Unit
                is Op.AchieveMilestone -> Unit
                is Op.NativeCall -> paths += op.path
            }
        }
    }

    model.scenes.forEach { ops(it.ops) }
    model.spells.forEach { ops(it.ops) }
    model.magicItems.forEach { ops(it.ops) }
    model.missions.forEach {
        it.requires?.let(::bool)
    }
    return paths
}

fun validateFlagCatalog(flags: List<FlagRef>) {
    check(flags.map { it.slug }.toSet().size == flags.size) { "duplicate flag slug" }

    flags.forEach { flag ->
        val nativeStates = flag.states.map { it.native }
        check(nativeStates.isNotEmpty()) { "flag ${flag.slug} has no states" }
        check(nativeStates.toSet().size == nativeStates.size) { "duplicate state in flag ${flag.slug}" }
    }

    flags
        .groupBy { it.scope }
        .forEach { (scope, scopedFlags) ->
            val slots = scopedFlags.mapNotNull { it.legacySlot }
            check(slots.toSet().size == slots.size) { "duplicate $scope legacy flag slot" }
        }
}

fun validateGameProgress(progress: GameProgressModel) {
    val ids = progress.milestones.map { it.id }
    check(ids.toSet().size == ids.size) { "duplicate game milestone" }

    val specialItems = progress.milestones.mapNotNull { it.specialInventoryItem }
    check(specialItems.toSet().size == specialItems.size) {
        "duplicate special inventory projection"
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

fun validateAuthoredContent(content: GameModel) {
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
    val specialItems = content
        ?.progress
        ?.milestones
        ?.mapNotNull { it.specialInventoryItem }
        ?.toSet()
        ?: emptySet()

    states
        .flatMap { it.parties }
        .forEach { party ->
            check(party.milestones.toSet().size == party.milestones.size) {
                "party ${party.id.slug}: duplicate game milestone"
            }
            check(party.inventory.none { it.item in specialItems }) {
                val special = party.inventory.first { it.item in specialItems }.item
                "party ${party.id.slug}: ${special?.slug} is special state, not a slotted inventory item"
            }
            content
                ?.takeIf { it.progress.win?.completedBy in party.milestones }
                ?.let {
                    check(isWinningConfiguration(it, party)) {
                        "party ${party.id.slug}: completed win marker without all required milestones"
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
