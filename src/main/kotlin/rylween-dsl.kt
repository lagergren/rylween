// rylween-dsl.kt -- Kotlin implementation for the Rylween authoring DSL.
//
// This file is implementation/compiler scaffolding: typed handles, immutable
// models, builders, and recorded script ASTs. Game-specific content belongs in
// rylween-full-scratch.dsl.kts. Validation helpers live in
// rylween-dsl-validation.kt.
//
// Validate the split scratch source with:
//
//   ./gradlew check
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

private class RuntimeFlagRef(slug: String) : FlagRef(slug) {
    fun value(native: String): FlagState = state(native)
}

fun runtimeFlagState(flag: String, native: String): FlagState =
    declaredFlags
        .firstOrNull { it.slug == flag }
        ?.states
        ?.firstOrNull { it.native == native }
        ?: RuntimeFlagRef(flag).value(native)

data class TextId(val id: String)
data class TextPattern(val pattern: String)
sealed interface TextPart {
    data class Literal(val text: String) : TextPart
    data class IntValue(val value: IntAst) : TextPart
    data class StringValue(val value: StrAst) : TextPart
    data class RoleName(val role: NpcRole) : TextPart
    data class RoleLocation(val role: NpcRole) : TextPart
    data class TownName(val town: TownId) : TextPart
    data class FuzzyTownLocation(val town: TownId, val radius: Int) : TextPart
    object OfferedItemName : TextPart
    object OfferedItemPrice : TextPart
}
data class TextLine(val parts: List<TextPart>)
sealed interface TextBlock {
    data class Ref(val id: TextId) : TextBlock
    data class Inline(val lines: List<String>) : TextBlock
    data class Structured(val lines: List<TextLine>) : TextBlock
}
fun inlineText(vararg lines: String): TextBlock = TextBlock.Inline(lines.toList())
fun structuredText(body: StructuredTextBlockBuilder.() -> Unit): TextBlock =
    StructuredTextBlockBuilder().apply(body).build()

class CharacterTextRef(private val role: NpcRole) {
    val name: TextPart = TextPart.RoleName(role)
    val location: TextPart = TextPart.RoleLocation(role)
}
fun character(role: NpcRole) = CharacterTextRef(role)

class TownTextRef(private val town: TownId) {
    val name: TextPart = TextPart.TownName(town)
    fun near(radius: Int = 5): TextPart = TextPart.FuzzyTownLocation(town, radius)
}
fun town(town: TownRef) = TownTextRef(town.id)
class ItemId private constructor(val slug: String, val legacyId: Int? = null) {
    override fun equals(other: Any?): Boolean =
        other is ItemId && slug == other.slug

    override fun hashCode(): Int = slug.hashCode()

    override fun toString(): String =
        "ItemId(slug=$slug, legacyId=$legacyId)"

    companion object {
        fun authored(slug: String): ItemId = ItemId(slug)

        internal fun generated(slug: String, legacyId: Int): ItemId =
            ItemId(slug, legacyId)
    }
}
data class ItemPoolId(
    val slug: String,
    val items: List<ItemId> = emptyList(),
    val legacyItemIds: List<Int> = emptyList(),
)
enum class ItemKind { Empty, Weapon, Armor, Gear, Potion, Treasure, Special, MagicItem }
enum class ItemStorageKind { EmptySentinel, SlottedInventory, SpecialMilestone }
data class ItemRecordModel(
    val id: ItemId,
    val kind: ItemKind,
    val displayName: String,
    val cost: Int,
    val storage: ItemStorageKind,
    val weaponDamage: Int? = null,
    val armorClass: Int? = null,
    val potionEffect: PotionEffectModel? = null,
)
enum class PotionEffectKind { HealHitPoints, PoisonHitPoints, RestoreMana }
data class PotionEffectModel(val kind: PotionEffectKind, val amount: Int? = null)
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
data class NpcJobId(val slug: String)
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
data class LootTableId(val slug: String)
data class RewardBundleId(val slug: String)
data class RumourTableId(val slug: String)
data class RumourId(val slug: String)

fun text(id: String) = TextId(id)
fun textPattern(pattern: String) = TextPattern(pattern)
fun item(slug: String) = ItemId.authored(slug)
private fun generatedItem(slug: String, legacyId: Int) = ItemId.generated(slug, legacyId)
fun itemPool(slug: String, items: List<ItemId> = emptyList(), legacyItemIds: Iterable<Int> = emptyList()) =
    ItemPoolId(slug, items, legacyItemIds.toList())
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
fun npcJob(slug: String) = NpcJobId(slug)
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
fun lootTable(slug: String) = LootTableId(slug)
fun rewardBundle(slug: String) = RewardBundleId(slug)
fun rumourTable(slug: String) = RumourTableId(slug)
fun rumour(slug: String) = RumourId(slug)

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
        generatedItemRecord(
            Items.blank,
            ItemKind.Empty,
            displayName = "-",
            cost = 0,
            storage = ItemStorageKind.EmptySentinel,
            weaponDamage = 1,
        ),

        // Weapons.
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

        // Milestone-backed special items.
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
    val battleSpoils = itemPool(
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
    val magicItems = itemPool(
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
    val tavernScruffySpecials = itemPool(
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

class NativeOpRefs {
    val screenClearText = nativeOp("screen.clear_text")
    val screenClearMapWindow = nativeOp("screen.clear_map_window")
    val screenRedrawMap = nativeOp("screen.redraw_map")
    val screenShowImage = nativeOp("screen.show_image")
    val screenStatusLine = nativeOp("screen.status_line")
    val inventoryAdd = nativeOp("inventory.add")
    val townEnterService = nativeOp("town.enter_service")
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
    val townNpcName = nativeOp("town.npc.name")
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
    data class RealmScalar(val name: String) : IntAst
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
    data class RealmScalar(val name: String) : LValueAst
    data class PartyScalar(val name: String) : LValueAst
    data class MemberField(val member: IntAst, val field: String) : LValueAst
    data class MonsterField(val monster: IntAst, val field: String) : LValueAst
}

enum class ScriptControlSignal { Continue, BreakNearestLoop, StopScript }
enum class MemberChoiceFilter { Any, AliveOnly, DeadOnly, SpellcasterOnly }
enum class InventorySlotChoiceFilter { Any, NonEmpty, Empty }

sealed interface Op {
    data class Screen(val text: TextBlock) : Op
    data class Emit(val text: TextId) : Op
    data class EmitIndexed(val pattern: TextPattern, val value: IntAst) : Op
    data class WaitInput(val reason: String? = null) : Op
    data class Pause(val millis: Int) : Op
    data class LetInt(val name: String, val value: IntAst) : Op
    data class BindText(val name: String, val value: StrAst) : Op
    data class Set(val target: LValueAst, val value: IntAst) : Op
    data class Add(val target: LValueAst, val value: IntAst) : Op
    data class Sub(val target: LValueAst, val value: IntAst) : Op
    data class AddClamped(val target: LValueAst, val value: IntAst, val max: IntAst) : Op
    data class SetFlag(val slug: String, val nativeValue: String) : Op
    data class If(val cond: BoolAst, val thenOps: List<Op>, val elseOps: List<Op>) : Op
    data class Loop(val body: List<Op>) : Op
    data class Break(
        val reason: String? = null,
        val signal: ScriptControlSignal = ScriptControlSignal.BreakNearestLoop,
    ) : Op
    data class Stop(
        val reason: String? = null,
        val signal: ScriptControlSignal = ScriptControlSignal.StopScript,
    ) : Op
    data class Switch(val expr: IntAst, val cases: List<SwitchCase>) : Op
    data class Menu(val prompt: TextBlock, val options: List<MenuOption>, val defaultOps: List<Op>) : Op
    data class AskMember(val prompt: TextBlock, val varName: String, val filter: MemberChoiceFilter) : Op
    data class AskInventorySlot(
        val prompt: TextBlock,
        val varName: String,
        val firstKey: Char,
        val slots: IntRange,
        val filter: InventorySlotChoiceFilter,
    ) : Op
    data class AskMonster(val prompt: TextId, val varName: String) : Op
    data class ForEachMember(val varName: String, val includeDead: Boolean, val body: List<Op>) : Op
    data class ForEachMonster(val varName: String, val includeDead: Boolean, val body: List<Op>) : Op
    data class Speak(val building: BuildingId) : Op
    data class RunScene(val scene: SceneId, val bindings: Map<String, String>) : Op
    data class Encounter(val foe: MonsterId, val mandatory: Boolean, val tier: EncounterTier? = null) : Op
    object ConsumeActiveItem : Op
    data class GrantReward(val reward: ScriptRewardModel) : Op
    data class RandomItemOffer(val offer: RandomItemOfferModel) : Op
    data class AchieveMilestone(val milestone: GameMilestoneId) : Op
    data class NativeCall(val path: String, val args: List<String>) : Op
}

data class SwitchCase(val match: Int?, val body: List<Op>)
data class MenuOption(val key: Char, val body: List<Op>)

data class SceneModel(val id: SceneId, val ops: List<Op>)
data class DialogueModel(val npc: NpcId, val branches: List<DialogueBranchModel>)
data class DialogueBranchModel(val whenState: FlagState?, val ops: List<Op>)
data class SpellModel(val id: SpellId, val slot: Int, val baseCost: Int, val name: String, val ops: List<Op>)
data class MagicItemModel(
    val id: ItemId,
    val contexts: Set<ItemUseContext>,
    val consume: ItemConsumePolicy,
    val ops: List<Op>,
)

sealed interface CharacterPlacementModel {
    data class Fixed(val position: WorldPosition) : CharacterPlacementModel
    object Randomized : CharacterPlacementModel
    object RandomizedWilderness : CharacterPlacementModel
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
    val key: Char,
    val action: KeyActionId,
    val building: BuildingId?,
    val endsMenu: Boolean,
)

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
    val nativeServices: List<EngineServiceUse>,
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

enum class MandatoryRunRule { NoEscape }
enum class CombatActionKind { Attack, Defend, UseMagicItem, BattleCry, CastMageSpell }
enum class CombatActionRequirement { Always, Mage }
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

data class EncounterOpeningModel(
    val fleeChancePercent: Int,
    val desperateFleeChancePercent: Int,
    val mandatoryRun: MandatoryRunRule,
    val banners: List<ImageId>,
)

data class CombatActionModel(
    val key: KeyActionId,
    val kind: CombatActionKind,
    val requirement: CombatActionRequirement,
)

data class PlayerAttackRulesModel(
    val hitBonus: Int,
    val damageSpreadMax: Int,
    val confusionDivisor: Int,
)

data class DefendRulesModel(
    val weaponSkillPenalty: Int,
    val restThreshold: Int,
)

data class BattleCryRulesModel(
    val weaponSkillPenalty: Int,
)

data class PlayerRoundModel(
    val attack: PlayerAttackRulesModel,
    val defend: DefendRulesModel,
    val battleCry: BattleCryRulesModel,
)

data class MonsterMeleeRulesModel(
    val hitBonus: Int,
    val damageDivisor: Int,
    val damageSpreadMax: Int,
    val damageMin: Int,
    val fallback: SceneId,
)

data class MonsterRoundModel(
    val spellChanceBasePercent: Int,
    val spellPowerDivisor: Int,
    val melee: MonsterMeleeRulesModel,
)

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
    val declined: TextBlock,
    val application: RewardApplicationModel,
    val messages: RewardMessageModel,
    val buyKey: Char,
    val declineKey: Char,
    val brokePauseMillis: Int,
    val fullPauseMillis: Int,
    val dealPauseMillis: Int,
    val declinedPauseMillis: Int,
)

data class RewardBundleModel(
    val id: RewardBundleId,
    val experience: FormulaId,
    val gold: FormulaId,
    val application: RewardApplicationModel,
    val loot: LootTableModel,
)

enum class ItemUseContext { Combat, Inventory }
enum class ItemConsumePolicy { BeforeUse, ScriptControlled, NeverConsumes }

data class StandardBattleModel(
    val opening: EncounterOpeningModel,
    val actions: List<CombatActionModel>,
    val playerRound: PlayerRoundModel,
    val monsterRound: MonsterRoundModel,
    val rewards: RewardBundleId,
    val nativeServices: List<EngineServiceUse>,
)

data class CombatSystemModel(
    val standardBattle: StandardBattleModel?,
)

enum class ConversationNpcSelection { BuildingListOrWildernessLocation }
enum class NpcVisitPolicy { PerPartyVisitedBit }
enum class DialogueBranchSelection { FirstMatchingStateThenDefault }
enum class MissionHintPolicy { BeforeEachMenu }
enum class ConversationTopic { Chitchat, Question, Rumour, Bye }
enum class QuestionTopic { Lodging, Magic, People, Nothing }
enum class ChitchatSubject { Well, Weather, Health }
enum class ChitchatReplyPolicy { BySubjectAndNpcJob }
enum class RumourUsagePolicy { OncePerConversationThenFallback }
enum class RumourSourcePolicy { StandardTownRumours }
enum class ChitchatPromptAudience { Any, Female, Male }

data class ConversationMenuActionModel(
    val topic: ConversationTopic,
    val key: Char,
    val action: KeyActionId,
)

data class QuestionMenuActionModel(
    val topic: QuestionTopic,
    val key: Char,
    val action: KeyActionId,
)

data class ConversationMenuModel(
    val actions: List<ConversationMenuActionModel>,
    val questions: List<QuestionMenuActionModel>,
)

data class ChitchatModel(
    val subjects: List<ChitchatSubject>,
    val replies: ChitchatReplyPolicy,
)

data class ConversationRumourModel(
    val usage: RumourUsagePolicy,
    val source: RumourSourcePolicy,
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

data class RumourEntryModel(
    val id: RumourId?,
    val text: TextBlock,
    val excludedBy: List<FlagState>,
)

data class RumourTextTableModel(
    val id: RumourTableId,
    val entries: List<RumourEntryModel>,
)

data class SpeakTextTablesModel(
    val screens: List<ReusableScreenTextModel> = emptyList(),
    val chitchatPrompts: List<ChitchatPromptTextModel> = emptyList(),
    val chitchatReplies: List<ChitchatReplyTextModel> = emptyList(),
    val questionReplies: List<QuestionReplyTextModel> = emptyList(),
    val rumourTables: List<RumourTextTableModel> = emptyList(),
)

data class StandardConversationModel(
    val npcSelection: ConversationNpcSelection,
    val visitPolicy: NpcVisitPolicy,
    val branchSelection: DialogueBranchSelection,
    val missionHints: MissionHintPolicy,
    val menu: ConversationMenuModel,
    val chitchat: ChitchatModel,
    val rumour: ConversationRumourModel,
    val nativeServices: List<EngineServiceUse>,
)

data class SpeakSystemModel(
    val standardConversation: StandardConversationModel?,
    val textTables: SpeakTextTablesModel = SpeakTextTablesModel(),
)

data class GameModel(
    val flags: List<FlagRef>,
    val authoredFlags: List<FlagContentModel>,
    val progress: GameProgressModel,
    val rewards: List<RewardBundleModel>,
    val combat: CombatSystemModel,
    val speak: SpeakSystemModel,
    val towns: List<TownModel>,
    val townSystem: TownSystemModel?,
    val characters: List<CharacterModel>,
    val monsters: List<MonsterModel>,
    val scenes: List<SceneModel>,
    val dialogue: List<DialogueModel>,
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
val PcClass.legacyId: Int
    get() = when (this) {
        PcClass.Fighter -> 1
        PcClass.Thief -> 2
        PcClass.Mage -> 3
        PcClass.Priest -> 4
        PcClass.Bard -> 5
        PcClass.Ranger -> 6
    }
enum class RuntimeMode { TitleMenu, Wilderness, Town, Dungeon, Battle, YdargCastle, GameOver }
enum class Facing { North, East, South, West }
enum class StateKind { AuthoredFixture, RuntimeSnapshot }

const val InventorySaveSlotMax = 16

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

fun townPosition(town: TownId, building: BuildingId): WorldPosition =
    WorldPosition.Town(town, building)

object Wilderness {
    fun xy(x: Int, y: Int): WorldPosition = WorldPosition.Wilderness(GridPoint(x, y))
}

data class RealmState(
    val day: Int,
    val hour: Int,
    val minute: Int,
    val rngSeed: Long?,
    val activeParty: PartyId,
    val currentMap: WorldMapId,
    val currentMode: RuntimeMode,
    val currentTown: TownId?,
    val banished: Boolean,
    val lastPlayedDay: Int,
    val partyMemberMax: Int,
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

data class PartyCounters(
    val memberCount: Int?,
    val kills: Int,
    val waitingMail: Boolean,
    val attacked: Boolean,
    val attackedToday: List<PartyAttackRecord>,
)

data class PartyAttackRecord(val party: PartyId, val attacked: Boolean)
data class InventorySlotState(val slot: Int, val item: ItemId?)
data class EquipmentState(val weapon: ItemId, val armor: ItemId)
data class NpcVisitState(val npc: NpcId, val visited: Boolean)

sealed interface JournalEntryState {
    data class Text(val id: TextId) : JournalEntryState
    data class Line(val text: String) : JournalEntryState
}

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
    val equipment: EquipmentState,
    val experience: Int,
    val dead: Boolean,
) {
    val armor: ItemId get() = equipment.armor
    val weapon: ItemId get() = equipment.weapon
}

data class PartyState(
    val id: PartyId,
    val player: String,
    val name: String,
    val position: WorldPosition,
    val facing: Facing?,
    val resources: PartyResources,
    val counters: PartyCounters,
    val flags: List<FlagState>,
    val milestones: List<GameMilestoneId>,
    val inventory: List<InventorySlotState>,
    val members: List<PartyMemberState>,
    val visitedNpcs: List<NpcVisitState>,
    val journal: List<JournalEntryState>,
)

data class StateModel(
    val id: StateId,
    val kind: StateKind,
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
class LocalIntRef internal constructor(val name: String) : IntExpr() {
    override val ast: IntAst = IntAst.Var(name)
}

fun lit(value: Int): IntExpr = Expr(IntAst.Lit(value))
fun v(name: String): IntExpr = Expr(IntAst.Var(name))
fun random(range: IntRange): IntExpr = Expr(IntAst.Random(IntAst.Lit(range.first), IntAst.Lit(range.last)))
fun chance(percent: IntExpr): BoolExpr = random(0..99) lt percent
fun chance(percent: Int): BoolExpr = chance(lit(percent))

infix fun IntExpr.eq(other: IntExpr): BoolExpr = Bool(BoolAst.Cmp(ast, "eq", other.ast))
infix fun IntExpr.eq(other: Int): BoolExpr = this eq lit(other)
infix fun IntExpr.ne(other: IntExpr): BoolExpr = Bool(BoolAst.Cmp(ast, "ne", other.ast))
infix fun IntExpr.ne(other: Int): BoolExpr = this ne lit(other)
infix fun IntExpr.lt(other: IntExpr): BoolExpr = Bool(BoolAst.Cmp(ast, "lt", other.ast))
infix fun IntExpr.lt(other: Int): BoolExpr = this lt lit(other)
infix fun IntExpr.lte(other: IntExpr): BoolExpr = Bool(BoolAst.Cmp(ast, "lte", other.ast))
infix fun IntExpr.lte(other: Int): BoolExpr = this lte lit(other)
infix fun IntExpr.gt(other: IntExpr): BoolExpr = Bool(BoolAst.Cmp(ast, "gt", other.ast))
infix fun IntExpr.gt(other: Int): BoolExpr = this gt lit(other)
infix fun IntExpr.gte(other: IntExpr): BoolExpr = Bool(BoolAst.Cmp(ast, "gte", other.ast))
infix fun IntExpr.gte(other: Int): BoolExpr = this gte lit(other)

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
    fun addClamped(value: IntExpr, max: IntExpr) = script.record(Op.AddClamped(lvalue, value.ast, max.ast))
    fun addClamped(value: Int, max: IntExpr) = addClamped(lit(value), max)
}

class RealmRef(private val script: ScriptScope) {
    val hour = IntSlot(script, LValueAst.RealmScalar("hour"), IntAst.RealmScalar("hour"))
    val partyMemberMax = IntSlot(
        script,
        LValueAst.RealmScalar("party_member_max"),
        IntAst.RealmScalar("party_member_max"),
    )
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
    val torches = IntSlot(script, LValueAst.PartyScalar("torches"), IntAst.PartyScalar("torches"))
    val food = IntSlot(script, LValueAst.PartyScalar("food"), IntAst.PartyScalar("food"))
    val timeLeft = IntSlot(script, LValueAst.PartyScalar("time_left"), IntAst.PartyScalar("time_left"))
    val restedHours = IntSlot(script, LValueAst.PartyScalar("rested_hours"), IntAst.PartyScalar("rested_hours"))
    val memberCount = IntSlot(script, LValueAst.PartyScalar("member_count"), IntAst.PartyScalar("member_count"))
    val leader: MemberRef get() = member(lit(1))
    fun member(index: IntExpr): MemberRef = MemberRef(script, index)
}

class MemberRef(private val script: ScriptScope, val index: IntExpr) {
    val hp = intField("hit_points")
    val maxHp = intField("health")
    val mp = intField("magic")
    val level = intField("level")
    val wisdom = intField("wisdom")
    val charisma = intField("charisma")
    val pcClass = intField("class")
    val dead = BoolSlot(
        script,
        LValueAst.MemberField(index.ast, "dead"),
        BoolAst.MemberFlag(index.ast, "dead"),
    )
    val spellcaster: BoolExpr = Bool(BoolAst.MemberFlag(index.ast, "spellcaster"))

    fun isClass(klass: PcClass): BoolExpr = pcClass eq klass.legacyId

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
    val screenClearMapWindow = effect(NativeOps.screenClearMapWindow, "map-window clearing primitive")
    val screenRedrawMap = effect(NativeOps.screenRedrawMap, "map rendering primitive")
    val screenShowImage = effect(NativeOps.screenShowImage, "map-window image rendering primitive")
    val screenStatusLine = effect(NativeOps.screenStatusLine, "status-line rendering primitive")
    val inventoryAdd = effect(NativeOps.inventoryAdd, "legacy inventory slot mutation")
    val townEnterService = effect(NativeOps.townEnterService, "town building-service trampoline")
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
    val townNpcName = string(NativeOps.townNpcName, "current-town NPC display name binding")
    val npcRoleName = string(NativeOps.npcRoleName, "role-resolved NPC name binding")
    val npcRoleLocation = string(NativeOps.npcRoleLocation, "role-resolved NPC location binding")

    val all: List<NativeSpec> = listOf(
        screenClearText,
        screenClearMapWindow,
        screenRedrawMap,
        screenShowImage,
        screenStatusLine,
        inventoryAdd,
        townEnterService,
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
        townNpcName,
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

fun townNpcName(job: NpcJobId): StrExpr =
    Str(StrAst.NativeString(NativeAllowlist.townNpcName.path, listOf(render(job))))

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
    is ItemPoolId -> arg.slug
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
    is LootTableId -> arg.slug
    is RewardBundleId -> arg.slug
    is NpcJobId -> arg.slug
    is RumourTableId -> arg.slug
    is RumourId -> arg.slug
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
    val realm = RealmRef(this)
    val party = PartyRef(this)
    val ask = AskScope(this)
    val choice = ChoiceScope(this)
    val native = NativeScope(this)

    val currentCaster: MemberRef
        get() = party.member(v("caster_idx"))

    fun record(op: Op) {
        ops += op
    }

    fun fresh(prefix: String): String = ctx.fresh(prefix)

    fun screen(id: TextId) = record(Op.Screen(TextBlock.Ref(id)))
    fun screen(vararg lines: String) = record(Op.Screen(TextBlock.Inline(lines.toList())))
    fun screen(body: StructuredTextBlockBuilder.() -> Unit) =
        record(Op.Screen(StructuredTextBlockBuilder().apply(body).build()))

    fun say(id: TextId) = screen(id)
    fun say(vararg lines: String) = screen(*lines)
    fun say(body: StructuredTextBlockBuilder.() -> Unit) = screen(body)
    fun emit(id: TextId) = record(Op.Emit(id))
    fun emitIndexed(pattern: TextPattern, value: IntExpr) = record(Op.EmitIndexed(pattern, value.ast))
    fun waitInput(reason: String? = null) = record(Op.WaitInput(reason))
    fun pause(ms: Int) = record(Op.Pause(ms))

    fun let(name: String, value: IntExpr): IntExpr {
        record(Op.LetInt(name, value.ast))
        return v(name)
    }

    fun let(name: String, value: Int): IntExpr = let(name, lit(value))

    fun localInt(value: IntExpr, hint: String = "local"): LocalIntRef {
        val ref = LocalIntRef(fresh(hint))
        record(Op.LetInt(ref.name, value.ast))
        return ref
    }

    fun localInt(value: Int, hint: String = "local"): LocalIntRef = localInt(lit(value), hint)

    infix fun LocalIntRef.set(value: IntExpr) {
        record(Op.LetInt(name, value.ast))
    }

    infix fun LocalIntRef.set(value: Int) = set(lit(value))

    operator fun LocalIntRef.plusAssign(value: IntExpr) {
        record(Op.LetInt(name, (this + value).ast))
    }

    operator fun LocalIntRef.plusAssign(value: Int) = plusAssign(lit(value))

    operator fun LocalIntRef.minusAssign(value: IntExpr) {
        record(Op.LetInt(name, (this - value).ast))
    }

    operator fun LocalIntRef.minusAssign(value: Int) = minusAssign(lit(value))

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
        return ElseBranch(ctx) { elseOps -> ops[index] = Op.If(cond.ast, thenOps, elseOps) }
    }

    fun stop(reason: String? = null) = record(Op.Stop(reason))
    fun breakLoop(reason: String? = null) = record(Op.Break(reason))

    fun loop(body: ScriptScope.() -> Unit) {
        record(Op.Loop(capture(body)))
    }

    fun choose(expr: IntExpr, body: SwitchBuilder.() -> Unit) {
        val builder = SwitchBuilder(ctx)
        builder.body()
        record(Op.Switch(expr.ast, builder.cases))
    }

    fun menu(prompt: TextId, body: MenuBuilder.() -> Unit) {
        val builder = MenuBuilder(ctx)
        builder.body()
        record(builder.build(TextBlock.Ref(prompt)))
    }

    fun menu(vararg promptLines: String, body: MenuBuilder.() -> Unit) {
        val builder = MenuBuilder(ctx)
        builder.body()
        record(builder.build(TextBlock.Inline(promptLines.toList())))
    }

    fun menu(prompt: TextBlock, body: MenuBuilder.() -> Unit) {
        val builder = MenuBuilder(ctx)
        builder.body()
        record(builder.build(prompt))
    }

    fun forEachMonster(includeDead: Boolean = false, body: ScriptScope.(MonsterRef) -> Unit) {
        val name = ctx.fresh("monster")
        val childOps = capture {
            body(MonsterRef(this, v(name)))
        }
        record(Op.ForEachMonster(name, includeDead, childOps))
    }

    fun forEachMember(includeDead: Boolean = false, body: ScriptScope.(MemberRef) -> Unit) {
        val name = ctx.fresh("member")
        val childOps = capture {
            body(MemberRef(this, v(name)))
        }
        record(Op.ForEachMember(name, includeDead, childOps))
    }

    fun healLivingMembers(amount: IntExpr) {
        forEachMember {
            it.hp.addClamped(amount, it.maxHp)
        }
    }

    fun healLivingMembers(amount: Int) = healLivingMembers(lit(amount))

    fun run(scene: SceneId, vararg bindings: Pair<String, Any>) {
        record(Op.RunScene(scene, bindings.associate { it.first to render(it.second) }))
    }

    fun speak(building: BuildingId) {
        record(Op.Speak(building))
    }

    fun encounter(monster: MonsterId, mandatory: Boolean = false, tier: EncounterTier? = null) {
        record(Op.Encounter(monster, mandatory, tier))
    }

    fun consumeActiveItem() = record(Op.ConsumeActiveItem)

    fun reward(body: ScriptRewardBuilder.() -> Unit) {
        record(Op.GrantReward(ScriptRewardBuilder().apply(body).build()))
    }

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

@RylNextDsl
class ScriptRewardBuilder {
    private var goldAst: IntAst? = null
    private val itemIds = mutableListOf<ItemId>()
    private var applicationModel = RewardApplicationBuilder().build()
    private var messagesModel = RewardMessageModel()

    fun gold(amount: Int) {
        goldAst = IntAst.Lit(amount)
    }

    fun gold(amount: IntExpr) {
        goldAst = amount.ast
    }

    fun items(vararg items: ItemId) {
        itemIds += items
    }

    fun placement(body: RewardApplicationBuilder.() -> Unit) {
        applicationModel = RewardApplicationBuilder(applicationModel).apply(body).build()
    }

    fun promptDrop(cantCarry: TextBlock, pickedUp: TextBlock? = null) {
        applicationModel = applicationModel.copy(overflow = RewardOverflowPolicy.PromptDropThenPlace)
        messagesModel = RewardMessageModel(cantCarry, pickedUp)
    }

    fun promptDrop(cantCarry: String, pickedUp: String? = null) {
        promptDrop(
            TextBlock.Inline(listOf(cantCarry)),
            pickedUp?.let { TextBlock.Inline(listOf(it)) },
        )
    }

    fun promptDrop(cantCarry: TextId, pickedUp: TextId? = null) {
        promptDrop(TextBlock.Ref(cantCarry), pickedUp?.let(TextBlock::Ref))
    }

    fun build(): ScriptRewardModel =
        ScriptRewardModel(goldAst, itemIds.toList(), applicationModel, messagesModel)
}

@RylNextDsl
class RandomItemOfferTextBuilder {
    private val text = StructuredTextBlockBuilder()

    val itemName: TextPart = TextPart.OfferedItemName
    val itemPrice: TextPart = TextPart.OfferedItemPrice

    fun line(vararg parts: Any) {
        text.line(*parts)
    }

    fun lines(vararg lines: String) {
        text.lines(*lines)
    }

    fun lines(body: RandomItemOfferTextBuilder.() -> Unit) {
        body()
    }

    fun build(): TextBlock = text.build()
}

@RylNextDsl
class RandomItemOfferBuilder(private val pool: ItemPoolId) {
    private var offerText: TextBlock? = null
    private var choicePromptText: TextBlock = inlineText("(B)uy it", "(N)o thanks")
    private var brokeText: TextBlock? = null
    private var dealText: TextBlock? = null
    private var declinedText: TextBlock? = null
    private var applicationModel = RewardApplicationBuilder().build()
    private var messagesModel = RewardMessageModel()

    var buyKey: Char = 'B'
    var declineKey: Char = 'N'
    var brokePauseMillis: Int = 800
    var fullPauseMillis: Int = 1000
    var dealPauseMillis: Int = 800
    var declinedPauseMillis: Int = 1000

    fun offer(vararg lines: String) {
        offerText = inlineText(*lines)
    }

    fun offer(body: RandomItemOfferTextBuilder.() -> Unit) {
        offerText = RandomItemOfferTextBuilder().apply(body).build()
    }

    fun choicePrompt(vararg lines: String) {
        choicePromptText = inlineText(*lines)
    }

    fun choicePrompt(body: RandomItemOfferTextBuilder.() -> Unit) {
        choicePromptText = RandomItemOfferTextBuilder().apply(body).build()
    }

    fun broke(vararg lines: String) {
        brokeText = inlineText(*lines)
    }

    fun broke(body: RandomItemOfferTextBuilder.() -> Unit) {
        brokeText = RandomItemOfferTextBuilder().apply(body).build()
    }

    fun full(vararg lines: String) {
        messagesModel = messagesModel.copy(cantCarry = inlineText(*lines))
    }

    fun full(body: RandomItemOfferTextBuilder.() -> Unit) {
        messagesModel = messagesModel.copy(cantCarry = RandomItemOfferTextBuilder().apply(body).build())
    }

    fun promptDrop(pickedUp: TextBlock? = null) {
        applicationModel = applicationModel.copy(overflow = RewardOverflowPolicy.PromptDropThenPlace)
        messagesModel = messagesModel.copy(pickedUp = pickedUp)
    }

    fun promptDrop(pickedUp: String) {
        promptDrop(inlineText(pickedUp))
    }

    fun promptDrop(pickedUp: TextId) {
        promptDrop(TextBlock.Ref(pickedUp))
    }

    fun deal(vararg lines: String) {
        dealText = inlineText(*lines)
    }

    fun deal(body: RandomItemOfferTextBuilder.() -> Unit) {
        dealText = RandomItemOfferTextBuilder().apply(body).build()
    }

    fun declined(vararg lines: String) {
        declinedText = inlineText(*lines)
    }

    fun declined(body: RandomItemOfferTextBuilder.() -> Unit) {
        declinedText = RandomItemOfferTextBuilder().apply(body).build()
    }

    fun build(): RandomItemOfferModel =
        RandomItemOfferModel(
            pool = pool,
            offer = requireNotNull(offerText) { "random item offer requires offer text" },
            choicePrompt = choicePromptText,
            broke = requireNotNull(brokeText) { "random item offer requires broke text" },
            deal = requireNotNull(dealText) { "random item offer requires deal text" },
            declined = requireNotNull(declinedText) { "random item offer requires declined text" },
            application = applicationModel,
            messages = messagesModel.copy(
                cantCarry = requireNotNull(messagesModel.cantCarry) {
                    "random item offer requires full/cant-carry text"
                },
            ),
            buyKey = buyKey.uppercaseChar(),
            declineKey = declineKey.uppercaseChar(),
            brokePauseMillis = brokePauseMillis,
            fullPauseMillis = fullPauseMillis,
            dealPauseMillis = dealPauseMillis,
            declinedPauseMillis = declinedPauseMillis,
        )
}

class ElseBranch(
    private val ctx: ScriptContext,
    private val finish: (List<Op>) -> Unit,
) {
    infix fun otherwise(body: ScriptScope.() -> Unit) {
        finish(buildList {
            ScriptScope(ctx, this).body()
        })
    }
}

@RylNextDsl
class SwitchBuilder(private val ctx: ScriptContext) {
    val cases = mutableListOf<SwitchCase>()

    fun case(value: Int, body: ScriptScope.() -> Unit) {
        cases += SwitchCase(value, buildList {
            ScriptScope(ctx, this).body()
        })
    }

    fun default(body: ScriptScope.() -> Unit) {
        cases += SwitchCase(null, buildList {
            ScriptScope(ctx, this).body()
        })
    }
}

@RylNextDsl
class MenuBuilder(private val ctx: ScriptContext) {
    private val options = mutableListOf<MenuOption>()
    private var defaultOps = emptyList<Op>()

    fun option(key: Char, body: ScriptScope.() -> Unit) {
        options += MenuOption(key.uppercaseChar(), recordScript(ctx, body))
    }

    fun default(body: ScriptScope.() -> Unit) {
        defaultOps = recordScript(ctx, body)
    }

    fun build(prompt: TextBlock): Op.Menu = Op.Menu(prompt, options.toList(), defaultOps)
}

class AskScope(private val script: ScriptScope) {
    fun member(prompt: TextId, filter: MemberChoiceFilter = MemberChoiceFilter.Any): MemberRef =
        member(TextBlock.Ref(prompt), filter)

    fun member(prompt: TextBlock, filter: MemberChoiceFilter = MemberChoiceFilter.Any): MemberRef {
        val name = script.fresh("member")
        script.record(Op.AskMember(prompt, name, filter))
        return script.party.member(v(name))
    }

    fun inventorySlot(
        prompt: TextBlock,
        firstKey: Char = 'A',
        slots: IntRange = 1..InventorySaveSlotMax,
        filter: InventorySlotChoiceFilter = InventorySlotChoiceFilter.Any,
    ): InventorySlotRef {
        val name = script.fresh("inventory_slot")
        script.record(Op.AskInventorySlot(prompt, name, firstKey.uppercaseChar(), slots, filter))
        return InventorySlotRef(v(name))
    }

    fun monsterAlive(prompt: TextId): MonsterRef {
        val name = script.fresh("monster")
        script.record(Op.AskMonster(prompt, name))
        return MonsterRef(script, v(name))
    }
}

class ChoiceScope(private val script: ScriptScope) {
    fun yesNo(
        prompt: TextBlock,
        yes: ScriptScope.() -> Unit,
        no: ScriptScope.() -> Unit = {},
    ) {
        script.menu(prompt) {
            option('Y', yes)
            option('N', no)
            default(no)
        }
    }

    fun yesNo(
        vararg promptLines: String,
        yes: ScriptScope.() -> Unit,
        no: ScriptScope.() -> Unit = {},
    ) {
        yesNo(TextBlock.Inline(promptLines.toList()), yes, no)
    }

    fun takeOrLeave(prompt: TextBlock, take: ScriptScope.() -> Unit, leave: ScriptScope.() -> Unit) {
        script.menu(prompt) {
            option('T', take)
            option('L', leave)
            default(leave)
        }
    }

    fun payNoThanks(amount: IntExpr, broke: ScriptScope.() -> Unit, paid: ScriptScope.() -> Unit) {
        script.menu("(P)ay", "(N)o thanks") {
            option('P') {
                pay(amount, broke, paid)
            }
            option('N') {}
        }
    }

    fun randomItemOffer(pool: ItemPoolId, body: RandomItemOfferBuilder.() -> Unit) {
        script.record(Op.RandomItemOffer(RandomItemOfferBuilder(pool).apply(body).build()))
    }

    fun member(prompt: TextId, filter: MemberChoiceFilter = MemberChoiceFilter.Any): MemberRef =
        script.ask.member(prompt, filter)

    fun member(prompt: TextBlock, filter: MemberChoiceFilter = MemberChoiceFilter.Any): MemberRef =
        script.ask.member(prompt, filter)

    fun member(
        prompt: TextId,
        filter: MemberChoiceFilter = MemberChoiceFilter.Any,
        body: ScriptScope.(MemberRef) -> Unit,
    ) {
        script.body(member(prompt, filter))
    }

    fun inventorySlot(
        prompt: TextBlock,
        firstKey: Char = 'A',
        slots: IntRange = 1..InventorySaveSlotMax,
        filter: InventorySlotChoiceFilter = InventorySlotChoiceFilter.Any,
        body: ScriptScope.(InventorySlotRef) -> Unit,
    ) {
        script.body(script.ask.inventorySlot(prompt, firstKey, slots, filter))
    }
}

data class InventorySlotRef(val index: IntExpr)

class NativeScope(private val script: ScriptScope) {
    // Native is deliberately narrow: rendering, keyboard input, inventory slot
    // plumbing, legacy Ydarg state, and helper calls whose implementation is
    // genuinely engine-owned. Game rules should become typed DSL verbs before
    // they are allowed here.
    val screen = NativeScreen(script)
    val rumour = NativeRumour(script)
    val inventory = NativeInventory(script)
    val town = NativeTown(script)
    val ydarg = NativeYdarg(script)
    val input = NativeInput(script)
}

class NativeScreen(private val script: ScriptScope) {
    fun clearText() = script.record(Op.NativeCall(NativeAllowlist.screenClearText.path, emptyList()))
    fun clearMapWindow() = script.record(Op.NativeCall(NativeAllowlist.screenClearMapWindow.path, emptyList()))
    fun redrawMap() = script.record(Op.NativeCall(NativeAllowlist.screenRedrawMap.path, emptyList()))
    fun showImage(image: ImageId) = script.record(Op.NativeCall(NativeAllowlist.screenShowImage.path, listOf(render(image))))
    fun statusLine() = script.record(Op.NativeCall(NativeAllowlist.screenStatusLine.path, emptyList()))
}

class NativeRumour(private val script: ScriptScope) {
    fun roll(): IntExpr = Expr(IntAst.NativeInt(NativeAllowlist.rumourRoll.path, emptyList()))
}

class NativeInventory(private val script: ScriptScope) {
    fun add(itemId: IntExpr) =
        script.record(Op.NativeCall(NativeAllowlist.inventoryAdd.path, listOf(render(itemId))))
}

class NativeTown(private val script: ScriptScope) {
    fun enterService(building: BuildingId) =
        script.record(Op.NativeCall(NativeAllowlist.townEnterService.path, listOf(render(building))))
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

private fun recordScript(body: ScriptScope.() -> Unit): List<Op> =
    recordScript(ScriptContext(), body)

private fun recordScript(ctx: ScriptContext, body: ScriptScope.() -> Unit): List<Op> =
    buildList {
        ScriptScope(ctx, this).body()
    }

@RylNextDsl
class GameBuilder {
    private val authoredFlags = mutableListOf<FlagContentModel>()
    private var progressModel = GameProgressModel(emptyList(), null)
    private val rewardsModel = mutableListOf<RewardBundleModel>()
    private var combatModel = CombatSystemModel(null)
    private var speakModel = SpeakSystemModel(null)
    private val towns = mutableListOf<TownModel>()
    private var townSystemModel: TownSystemModel? = null
    private val characters = mutableListOf<CharacterModel>()
    private val monsters = mutableListOf<MonsterModel>()
    private val scenes = mutableListOf<SceneModel>()
    private val dialogue = mutableListOf<DialogueModel>()
    private val spells = mutableListOf<SpellModel>()
    private val magicItems = mutableListOf<MagicItemModel>()
    private val missions = mutableListOf<MissionModel>()

    fun flags(body: FlagContentBuilder.() -> Unit) {
        authoredFlags += FlagContentBuilder().apply(body).build()
    }

    fun gameProgress(body: GameProgressBuilder.() -> Unit) {
        progressModel = GameProgressBuilder().apply(body).build()
    }

    fun rewards(body: RewardCatalogBuilder.() -> Unit) {
        rewardsModel += RewardCatalogBuilder().apply(body).build()
    }

    fun combat(body: CombatBuilder.() -> Unit) {
        combatModel = CombatBuilder().apply(body).build()
    }

    fun speak(body: SpeakBuilder.() -> Unit) {
        speakModel = SpeakBuilder().apply(body).build()
    }

    fun towns(body: TownCatalogBuilder.() -> Unit) {
        towns += TownCatalogBuilder().apply(body).build()
    }

    fun townSystem(body: TownSystemBuilder.() -> Unit) {
        townSystemModel = TownSystemBuilder().apply(body).build()
    }

    fun characters(body: CharacterCatalogBuilder.() -> Unit) {
        characters += CharacterCatalogBuilder().apply(body).build()
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

    fun dialogue(body: DialogueCatalogBuilder.() -> Unit) {
        dialogue += DialogueCatalogBuilder().apply(body).build()
    }

    fun spell(id: SpellId, slot: Int, baseCost: Int, name: String, body: ScriptScope.() -> Unit) {
        spells += SpellModel(id, slot, baseCost, name, recordScript(body))
    }

    fun magicItem(id: ItemId, body: MagicItemUseBuilder.() -> Unit) {
        magicItems += MagicItemUseBuilder(id).apply(body).build()
    }

    fun build(): GameModel =
        GameModel(
            flags = declaredFlags.toList(),
            authoredFlags = authoredFlags.toList(),
            progress = progressModel,
            rewards = rewardsModel.toList(),
            combat = combatModel,
            speak = speakModel,
            towns = towns.toList(),
            townSystem = townSystemModel,
            characters = characters.toList(),
            monsters = monsters.toList(),
            scenes = scenes.toList(),
            dialogue = dialogue.toList(),
            spells = spells.toList(),
            magicItems = magicItems.toList(),
            missions = missions.toList(),
        )

}

@RylNextDsl
class DialogueCatalogBuilder {
    private val dialogue = mutableListOf<DialogueModel>()

    fun npc(id: NpcId, body: NpcDialogueBuilder.() -> Unit) {
        dialogue += NpcDialogueBuilder(id).apply(body).build()
    }

    fun build(): List<DialogueModel> = dialogue.toList()
}

@RylNextDsl
class NpcDialogueBuilder(private val id: NpcId) {
    private val branches = mutableListOf<DialogueBranchModel>()

    fun whenState(state: FlagState, body: ScriptScope.() -> Unit) {
        branches += DialogueBranchModel(state, recordScript(body))
    }

    fun default(body: ScriptScope.() -> Unit) {
        branches += DialogueBranchModel(null, recordScript(body))
    }

    fun build(): DialogueModel = DialogueModel(id, branches.toList())
}

@RylNextDsl
class TownCatalogBuilder {
    private val towns = mutableListOf<TownModel>()

    fun town(slug: String, body: TownBuilder.() -> Unit) {
        towns += TownBuilder(town(slug)).apply(body).build()
    }

    fun town(ref: TownRef, body: TownBuilder.() -> Unit) {
        towns += TownBuilder(ref.id).apply(body).build()
    }

    fun build(): List<TownModel> = towns.toList()
}

@RylNextDsl
class TownBuilder(private val id: TownId) {
    var name: String = ""
    var position: GridPoint = xy(0, 0)
    var taxPercent: Int = 0
    private var kindModel: TownKind = TownKind.Village
    private val serviceModels = mutableListOf<TownServiceModel>()

    fun village() {
        kindModel = TownKind.Village
    }

    fun city() {
        kindModel = TownKind.City
    }

    fun services(body: TownServicesBuilder.() -> Unit) {
        serviceModels += TownServicesBuilder().apply(body).build()
    }

    fun build(): TownModel =
        TownModel(id, name, position, taxPercent, kindModel, serviceModels.toList())
}

@RylNextDsl
class TownServicesBuilder {
    private val services = mutableListOf<TownServiceModel>()

    fun merchant() {
        service(Buildings.merchant)
    }

    fun inn() {
        service(Buildings.inn)
    }

    fun tavern() {
        service(Buildings.tavern)
    }

    fun smith() {
        service(Buildings.smith)
    }

    fun church() {
        service(Buildings.church)
    }

    fun service(building: BuildingId) {
        services += TownServiceModel(building)
    }

    fun build(): List<TownServiceModel> = services.toList()
}

@RylNextDsl
class TownSystemBuilder {
    private var menuModel = TownMenuBuilder().build()
    private var buildingServiceModels = BuildingServiceCatalogBuilder().build()

    fun menu(body: TownMenuBuilder.() -> Unit) {
        menuModel = TownMenuBuilder().apply(body).build()
    }

    fun buildingServices(body: BuildingServiceCatalogBuilder.() -> Unit) {
        buildingServiceModels = BuildingServiceCatalogBuilder().apply(body).build()
    }

    fun build(): TownSystemModel =
        TownSystemModel(
            menu = menuModel,
            buildingServices = buildingServiceModels,
            nativeServices = listOf(
                EngineServiceUse(EngineService.KeyboardInput, "town menu command selection"),
                EngineServiceUse(EngineService.Rendering, "town entry art, text panes, and status refresh"),
                EngineServiceUse(EngineService.TownStateStorage, "current town lookup and available-service checks"),
                EngineServiceUse(EngineService.TimeState, "service opening-hour checks"),
                EngineServiceUse(EngineService.PartyStateMutation, "tax, service costs, lodging, healing, and time passage"),
                EngineServiceUse(EngineService.InventoryStorage, "merchant, smith, and service inventory effects"),
                EngineServiceUse(EngineService.NpcStateStorage, "building NPC lookup and town conversations"),
                EngineServiceUse(EngineService.TextFormatting, "town, building, NPC, item, price, and tax bindings"),
            ),
        )
}

@RylNextDsl
class TownMenuBuilder {
    private val actions = mutableListOf<TownMenuActionModel>()

    fun church(key: Char = 'C') {
        service(key, Buildings.church, KeyActions.enterChurch)
    }

    fun smith(key: Char = 'S') {
        service(key, Buildings.smith, KeyActions.enterSmith)
    }

    fun inn(key: Char = 'I') {
        service(key, Buildings.inn, KeyActions.enterInn)
    }

    fun merchant(key: Char = 'M') {
        service(key, Buildings.merchant, KeyActions.enterMerchant)
    }

    fun tavern(key: Char = 'T') {
        service(key, Buildings.tavern, KeyActions.enterTavern)
    }

    fun leave(key: Char = 'L', action: KeyActionId = KeyActions.leaveTown) {
        actions += TownMenuActionModel(key, action, building = null, endsMenu = true)
    }

    fun service(key: Char, building: BuildingId, action: KeyActionId) {
        actions += TownMenuActionModel(key, action, building, endsMenu = false)
    }

    fun build(): List<TownMenuActionModel> = actions.toList()
}

@RylNextDsl
class BuildingServiceCatalogBuilder {
    private val services = mutableListOf<BuildingServiceModel>()

    fun church(body: BuildingServiceBuilder.() -> Unit = {}) {
        building(Buildings.church, KeyActions.enterChurch, Scenes.Towne.church, body)
    }

    fun smith(body: BuildingServiceBuilder.() -> Unit = {}) {
        building(Buildings.smith, KeyActions.enterSmith, Scenes.Towne.smith, body)
    }

    fun inn(body: BuildingServiceBuilder.() -> Unit = {}) {
        building(Buildings.inn, KeyActions.enterInn, Scenes.Towne.inn, body)
    }

    fun merchant(body: BuildingServiceBuilder.() -> Unit = {}) {
        building(Buildings.merchant, KeyActions.enterMerchant, Scenes.Towne.merchant, body)
    }

    fun tavern(body: BuildingServiceBuilder.() -> Unit = {}) {
        building(Buildings.tavern, KeyActions.enterTavern, Scenes.Towne.tavern, body)
    }

    fun building(
        building: BuildingId,
        action: KeyActionId,
        scene: SceneId,
        body: BuildingServiceBuilder.() -> Unit = {},
    ) {
        services += BuildingServiceBuilder(building, action, scene).apply(body).build()
    }

    fun build(): List<BuildingServiceModel> = services.toList()
}

@RylNextDsl
class BuildingServiceBuilder(
    private val building: BuildingId,
    private val defaultAction: KeyActionId,
    private val defaultScene: SceneId,
) {
    var action: KeyActionId = defaultAction
    var scene: SceneId = defaultScene
    var hours: IntRange? = null

    fun alwaysOpen() {
        hours = null
    }

    fun build(): BuildingServiceModel =
        BuildingServiceModel(
            building = building,
            action = action,
            scene = scene,
            opensAt = hours?.first,
            closesAt = hours?.last,
            nativeServices = listOf(
                EngineServiceUse(EngineService.Rendering, "${building.slug} service screens and map-window art"),
                EngineServiceUse(EngineService.KeyboardInput, "${building.slug} service menu input"),
                EngineServiceUse(EngineService.TimeState, "${building.slug} opening-hour checks"),
                EngineServiceUse(EngineService.PartyStateMutation, "${building.slug} cost, time, and resource effects"),
            ),
        )
}

@RylNextDsl
class CharacterCatalogBuilder {
    private val characters = mutableListOf<CharacterModel>()

    fun character(id: NpcId, body: CharacterBuilder.() -> Unit) {
        characters += CharacterBuilder(id).apply(body).build()
    }

    fun character(id: NpcId, name: String, job: NpcJobId, body: CharacterBuilder.() -> Unit = {}) {
        characters += CharacterBuilder(id).apply {
            this.name = name
            this.job = job
            body()
        }.build()
    }

    fun build(): List<CharacterModel> = characters.toList()
}

@RylNextDsl
class CharacterBuilder(private val id: NpcId) {
    var name: String = ""
    var job: NpcJobId = NpcJobs.merchant
    var gender: Gender = Gender.Male
    private var placementModel: CharacterPlacementModel = CharacterPlacementModel.Randomized
    private val roleModels = mutableListOf<NpcRole>()

    fun fixed(position: WorldPosition) {
        placementModel = CharacterPlacementModel.Fixed(position)
    }

    fun randomPlacement() {
        placementModel = CharacterPlacementModel.Randomized
    }

    fun randomWilderness() {
        placementModel = CharacterPlacementModel.RandomizedWilderness
    }

    fun role(vararg roles: NpcRole) {
        roleModels += roles
    }

    fun build(): CharacterModel =
        CharacterModel(
            id = id,
            name = name,
            job = job,
            gender = gender,
            placement = placementModel,
            roles = roleModels.toList(),
        )
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

@RylNextDsl
class MagicItemUseBuilder(private val id: ItemId) {
    private val contexts = mutableSetOf(ItemUseContext.Combat)
    var consume: ItemConsumePolicy = ItemConsumePolicy.BeforeUse
    private var ops = emptyList<Op>()

    fun usableIn(vararg contexts: ItemUseContext) {
        this.contexts.clear()
        this.contexts += contexts
    }

    fun effect(body: ScriptScope.() -> Unit) {
        ops = recordScript(body)
    }

    fun build(): MagicItemModel =
        MagicItemModel(id, contexts.toSet(), consume, ops)
}

@RylNextDsl
class RewardCatalogBuilder {
    private val rewards = mutableListOf<RewardBundleModel>()

    fun reward(id: RewardBundleId, body: RewardBundleBuilder.() -> Unit) {
        rewards += RewardBundleBuilder(id).apply(body).build()
    }

    fun build(): List<RewardBundleModel> = rewards.toList()
}

@RylNextDsl
class RewardBundleBuilder(private val id: RewardBundleId) {
    var experience: FormulaId = Formulas.rewardXpPerSurvivor
    var gold: FormulaId = Formulas.rewardGoldParty
    private var applicationModel = RewardApplicationBuilder().build()
    private var lootModel = LootTableBuilder(LootTables.standardBattle).build()

    fun itemPlacement(body: RewardApplicationBuilder.() -> Unit) {
        applicationModel = RewardApplicationBuilder(applicationModel).apply(body).build()
    }

    fun lootTable(id: LootTableId, body: LootTableBuilder.() -> Unit) {
        lootModel = LootTableBuilder(id).apply(body).build()
    }

    fun build(): RewardBundleModel = RewardBundleModel(id, experience, gold, applicationModel, lootModel)
}

@RylNextDsl
class RewardApplicationBuilder(initial: RewardApplicationModel? = null) {
    var regularItems: RewardItemPlacement =
        initial?.regularItems ?: RewardItemPlacement.FirstFreeInventorySlot
    var overflow: RewardOverflowPolicy =
        initial?.overflow ?: RewardOverflowPolicy.SkipIfInventoryFull
    var specialItems: RewardSpecialItemPolicy =
        initial?.specialItems ?: RewardSpecialItemPolicy.ProjectMilestoneState

    fun build(): RewardApplicationModel =
        RewardApplicationModel(regularItems, overflow, specialItems)
}

@RylNextDsl
class LootTableBuilder(private val id: LootTableId) {
    var dropChancePercent: Int = 5
    var treasureChancePercent: Int = 59
    var treasure: ItemPoolId = ItemPools.battleSpoils
    var magic: ItemPoolId = ItemPools.magicItems

    fun build(): LootTableModel =
        LootTableModel(id, dropChancePercent, treasureChancePercent, treasure, magic)
}

@RylNextDsl
class CombatBuilder {
    private var standardBattleModel: StandardBattleModel? = null

    fun standardBattle(body: StandardBattleBuilder.() -> Unit) {
        standardBattleModel = StandardBattleBuilder().apply(body).build()
    }

    fun build(): CombatSystemModel = CombatSystemModel(standardBattleModel)
}

@RylNextDsl
class StandardBattleBuilder {
    private var openingModel = EncounterOpeningBuilder().build()
    private var actionModels = PlayerCombatActionsBuilder().build()
    private var playerRoundModel = PlayerRoundBuilder().build()
    private var monsterRoundModel = MonsterRoundBuilder().build()
    var rewards: RewardBundleId = Rewards.standardBattle

    fun opening(body: EncounterOpeningBuilder.() -> Unit) {
        openingModel = EncounterOpeningBuilder(openingModel).apply(body).build()
    }

    fun playerActions(body: PlayerCombatActionsBuilder.() -> Unit) {
        actionModels = PlayerCombatActionsBuilder().apply(body).build()
    }

    fun playerRound(body: PlayerRoundBuilder.() -> Unit) {
        playerRoundModel = PlayerRoundBuilder(playerRoundModel).apply(body).build()
    }

    fun monsterRound(body: MonsterRoundBuilder.() -> Unit) {
        monsterRoundModel = MonsterRoundBuilder(monsterRoundModel).apply(body).build()
    }

    fun build(): StandardBattleModel =
        StandardBattleModel(
            opening = openingModel,
            actions = actionModels,
            playerRound = playerRoundModel,
            monsterRound = monsterRoundModel,
            rewards = rewards,
            nativeServices = defaultNativeServices,
        )

    companion object {
        private val defaultNativeServices = listOf(
            EngineServiceUse(EngineService.KeyboardInput, "fight/run, action, spell, item, and target selection"),
            EngineServiceUse(EngineService.Rendering, "combat banners, text panes, map-window art, and status refresh"),
            EngineServiceUse(EngineService.RandomNumbers, "encounter sizing, hit rolls, damage rolls, fleeing, loot, and AI"),
            EngineServiceUse(EngineService.CombatContextStorage, "live monster hit points, death bits, and round status effects"),
            EngineServiceUse(EngineService.InventoryStorage, "item use, consume, loot placement, and first-free-slot lookup"),
            EngineServiceUse(EngineService.PartyStateMutation, "HP, mana, XP, gold, kills, and death-state updates"),
            EngineServiceUse(EngineService.TextFormatting, "party, monster, item, pronoun, and reward bindings"),
        )
    }
}

@RylNextDsl
class SpeakBuilder {
    private var standardConversationModel: StandardConversationModel? = null
    private var textTablesModel = SpeakTextTablesModel()

    fun standardConversation(body: StandardConversationBuilder.() -> Unit) {
        standardConversationModel = StandardConversationBuilder().apply(body).build()
    }

    fun textTables(body: SpeakTextTablesBuilder.() -> Unit) {
        textTablesModel = SpeakTextTablesBuilder().apply(body).build()
    }

    fun build(): SpeakSystemModel = SpeakSystemModel(standardConversationModel, textTablesModel)
}

@RylNextDsl
class SpeakTextTablesBuilder {
    private val screens = mutableListOf<ReusableScreenTextModel>()
    private val chitchatPrompts = mutableListOf<ChitchatPromptTextModel>()
    private val chitchatReplies = mutableListOf<ChitchatReplyTextModel>()
    private val questionReplies = mutableListOf<QuestionReplyTextModel>()
    private val rumourTables = mutableListOf<RumourTextTableModel>()

    fun screen(id: TextId, body: StructuredTextBlockBuilder.() -> Unit) {
        screens += ReusableScreenTextModel(id, StructuredTextBlockBuilder().apply(body).build())
    }

    fun chitchatPrompt(subject: ChitchatSubject, body: ChitchatPromptTextBuilder.() -> Unit) {
        chitchatPrompts += ChitchatPromptTextBuilder(subject).apply(body).build()
    }

    fun chitchatReplies(subject: ChitchatSubject, body: JobTextTableBuilder.() -> Unit) {
        chitchatReplies += ChitchatReplyTextModel(subject, JobTextTableBuilder().apply(body).build())
    }

    fun questionReplies(topic: QuestionTopic, body: JobTextTableBuilder.() -> Unit) {
        questionReplies += QuestionReplyTextModel(topic, JobTextTableBuilder().apply(body).build())
    }

    fun rumourTable(id: RumourTableId, body: RumourTextTableBuilder.() -> Unit) {
        rumourTables += RumourTextTableBuilder(id).apply(body).build()
    }

    fun build(): SpeakTextTablesModel =
        SpeakTextTablesModel(
            screens = screens.toList(),
            chitchatPrompts = chitchatPrompts.toList(),
            chitchatReplies = chitchatReplies.toList(),
            questionReplies = questionReplies.toList(),
            rumourTables = rumourTables.toList(),
        )
}

@RylNextDsl
class ChitchatPromptTextBuilder(private val subject: ChitchatSubject) {
    private val variants = mutableListOf<ChitchatPromptVariantModel>()

    fun any(body: StructuredTextBlockBuilder.() -> Unit) {
        variants += ChitchatPromptVariantModel(
            ChitchatPromptAudience.Any,
            StructuredTextBlockBuilder().apply(body).build(),
        )
    }

    fun female(body: StructuredTextBlockBuilder.() -> Unit) {
        variants += ChitchatPromptVariantModel(
            ChitchatPromptAudience.Female,
            StructuredTextBlockBuilder().apply(body).build(),
        )
    }

    fun male(body: StructuredTextBlockBuilder.() -> Unit) {
        variants += ChitchatPromptVariantModel(
            ChitchatPromptAudience.Male,
            StructuredTextBlockBuilder().apply(body).build(),
        )
    }

    fun build(): ChitchatPromptTextModel = ChitchatPromptTextModel(subject, variants.toList())
}

@RylNextDsl
class StructuredTextBlockBuilder {
    private val lines = mutableListOf<TextLine>()

    fun line(vararg parts: Any) {
        lines += TextLine(parts.map(::toTextPart))
    }

    fun lines(vararg lines: String) {
        lines.forEach { line(it) }
    }

    fun lines(body: StructuredTextBlockBuilder.() -> Unit) {
        body()
    }

    fun build(): TextBlock = TextBlock.Structured(lines.toList())

    private fun toTextPart(part: Any): TextPart =
        when (part) {
            is TextPart -> part
            is IntExpr -> TextPart.IntValue(part.ast)
            is StrExpr -> TextPart.StringValue(part.ast)
            is String -> TextPart.Literal(part)
            else -> TextPart.Literal(part.toString())
        }
}

@RylNextDsl
class JobTextTableBuilder {
    private val replies = mutableListOf<JobTextModel>()

    fun job(id: NpcJobId, body: StructuredTextBlockBuilder.() -> Unit) {
        replies += JobTextModel(id, StructuredTextBlockBuilder().apply(body).build())
    }

    fun build(): List<JobTextModel> = replies.toList()
}

@RylNextDsl
class RumourTextTableBuilder(private val id: RumourTableId) {
    private val entries = mutableListOf<RumourEntryModel>()

    fun rumour(body: RumourEntryBuilder.() -> Unit) {
        entries += RumourEntryBuilder(null).apply(body).build()
    }

    fun rumour(id: RumourId, body: RumourEntryBuilder.() -> Unit) {
        entries += RumourEntryBuilder(id).apply(body).build()
    }

    fun build(): RumourTextTableModel = RumourTextTableModel(id, entries.toList())
}

@RylNextDsl
class RumourEntryBuilder(private val id: RumourId?) {
    private val text = StructuredTextBlockBuilder()
    private val excludedStates = mutableListOf<FlagState>()

    fun line(vararg parts: Any) {
        text.line(*parts)
    }

    fun lines(vararg lines: String) {
        text.lines(*lines)
    }

    fun lines(body: StructuredTextBlockBuilder.() -> Unit) {
        text.body()
    }

    fun excludeWhen(vararg states: FlagState) {
        excludedStates += states
    }

    fun build(): RumourEntryModel =
        RumourEntryModel(id, text.build(), excludedStates.toList())
}

@RylNextDsl
class StandardConversationBuilder {
    var npcSelection: ConversationNpcSelection = ConversationNpcSelection.BuildingListOrWildernessLocation
    var visitPolicy: NpcVisitPolicy = NpcVisitPolicy.PerPartyVisitedBit
    var branchSelection: DialogueBranchSelection = DialogueBranchSelection.FirstMatchingStateThenDefault
    var missionHints: MissionHintPolicy = MissionHintPolicy.BeforeEachMenu

    private var menuModel = ConversationMenuBuilder().apply {
        chitchat()
        question {
            lodging()
            magic()
            people()
            nothing()
        }
        rumour()
        bye()
    }.build()
    private var chitchatModel = ChitchatBuilder().build()
    private var rumourModel = ConversationRumourBuilder().build()

    fun menu(body: ConversationMenuBuilder.() -> Unit) {
        menuModel = ConversationMenuBuilder().apply(body).build()
    }

    fun chitchat(body: ChitchatBuilder.() -> Unit) {
        chitchatModel = ChitchatBuilder(chitchatModel).apply(body).build()
    }

    fun rumour(body: ConversationRumourBuilder.() -> Unit) {
        rumourModel = ConversationRumourBuilder(rumourModel).apply(body).build()
    }

    fun build(): StandardConversationModel =
        StandardConversationModel(
            npcSelection = npcSelection,
            visitPolicy = visitPolicy,
            branchSelection = branchSelection,
            missionHints = missionHints,
            menu = menuModel,
            chitchat = chitchatModel,
            rumour = rumourModel,
            nativeServices = defaultNativeServices,
        )

    companion object {
        private val defaultNativeServices = listOf(
            EngineServiceUse(EngineService.KeyboardInput, "NPC selection and conversation menu choices"),
            EngineServiceUse(EngineService.Rendering, "text-window clearing, menu rendering, and dialogue screens"),
            EngineServiceUse(EngineService.RandomNumbers, "chitchat subject and rumour selection"),
            EngineServiceUse(EngineService.NpcStateStorage, "NPC lookup, job/gender/role metadata, and visited bits"),
            EngineServiceUse(EngineService.PartyStateMutation, "dialogue scripts can advance flags, rewards, and journals"),
            EngineServiceUse(EngineService.TextFormatting, "NPC names, jobs, locations, and role-bound text placeholders"),
        )
    }
}

@RylNextDsl
class ConversationMenuBuilder {
    private val actions = mutableListOf<ConversationMenuActionModel>()
    private var questionActions = emptyList<QuestionMenuActionModel>()

    fun chitchat(key: Char = 'C', action: KeyActionId = KeyActions.speakChitchat) {
        actions += ConversationMenuActionModel(ConversationTopic.Chitchat, key, action)
    }

    fun question(
        key: Char = 'Q',
        action: KeyActionId = KeyActions.speakQuestion,
        body: QuestionMenuBuilder.() -> Unit,
    ) {
        actions += ConversationMenuActionModel(ConversationTopic.Question, key, action)
        questionActions = QuestionMenuBuilder().apply(body).build()
    }

    fun rumour(key: Char = 'R', action: KeyActionId = KeyActions.speakRumour) {
        actions += ConversationMenuActionModel(ConversationTopic.Rumour, key, action)
    }

    fun bye(key: Char = 'B', action: KeyActionId = KeyActions.speakBye) {
        actions += ConversationMenuActionModel(ConversationTopic.Bye, key, action)
    }

    fun build(): ConversationMenuModel =
        ConversationMenuModel(actions.toList(), questionActions)
}

@RylNextDsl
class QuestionMenuBuilder {
    private val actions = mutableListOf<QuestionMenuActionModel>()

    fun lodging(key: Char = 'L', action: KeyActionId = KeyActions.questionLodging) {
        actions += QuestionMenuActionModel(QuestionTopic.Lodging, key, action)
    }

    fun magic(key: Char = 'M', action: KeyActionId = KeyActions.questionMagic) {
        actions += QuestionMenuActionModel(QuestionTopic.Magic, key, action)
    }

    fun people(key: Char = 'P', action: KeyActionId = KeyActions.questionPeople) {
        actions += QuestionMenuActionModel(QuestionTopic.People, key, action)
    }

    fun nothing(key: Char = 'N', action: KeyActionId = KeyActions.questionNothing) {
        actions += QuestionMenuActionModel(QuestionTopic.Nothing, key, action)
    }

    fun build(): List<QuestionMenuActionModel> = actions.toList()
}

@RylNextDsl
class ChitchatBuilder(initial: ChitchatModel? = null) {
    private var subjectModels: List<ChitchatSubject> =
        initial?.subjects ?: listOf(ChitchatSubject.Well, ChitchatSubject.Weather, ChitchatSubject.Health)
    var replies: ChitchatReplyPolicy =
        initial?.replies ?: ChitchatReplyPolicy.BySubjectAndNpcJob

    fun subjects(vararg subjects: ChitchatSubject) {
        subjectModels = subjects.toList()
    }

    fun build(): ChitchatModel = ChitchatModel(subjectModels, replies)
}

@RylNextDsl
class ConversationRumourBuilder(initial: ConversationRumourModel? = null) {
    var usage: RumourUsagePolicy =
        initial?.usage ?: RumourUsagePolicy.OncePerConversationThenFallback
    var source: RumourSourcePolicy =
        initial?.source ?: RumourSourcePolicy.StandardTownRumours

    fun build(): ConversationRumourModel = ConversationRumourModel(usage, source)
}

@RylNextDsl
class EncounterOpeningBuilder(initial: EncounterOpeningModel? = null) {
    var fleeChancePercent: Int = initial?.fleeChancePercent ?: 95
    var desperateFleeChancePercent: Int = initial?.desperateFleeChancePercent ?: 31
    var mandatoryRun: MandatoryRunRule = initial?.mandatoryRun ?: MandatoryRunRule.NoEscape
    private val banners = initial?.banners?.toMutableList() ?: mutableListOf(
        Images.Scene.encounterBanner1,
        Images.Scene.encounterBanner2,
        Images.Scene.encounterBanner3,
        Images.Scene.encounterBanner4,
        Images.Scene.encounterBanner5,
    )

    fun encounterBanners(vararg images: ImageId) {
        banners.clear()
        banners += images
    }

    fun build(): EncounterOpeningModel =
        EncounterOpeningModel(fleeChancePercent, desperateFleeChancePercent, mandatoryRun, banners.toList())
}

@RylNextDsl
class PlayerCombatActionsBuilder {
    private val actions = mutableListOf<CombatActionModel>()

    fun attack(key: KeyActionId = KeyActions.attack) {
        action(key, CombatActionKind.Attack, CombatActionRequirement.Always)
    }

    fun defend(key: KeyActionId = KeyActions.defend) {
        action(key, CombatActionKind.Defend, CombatActionRequirement.Always)
    }

    fun useMagicItem(key: KeyActionId = KeyActions.useItem) {
        action(key, CombatActionKind.UseMagicItem, CombatActionRequirement.Always)
    }

    fun battleCry(key: KeyActionId = KeyActions.battleCry) {
        action(key, CombatActionKind.BattleCry, CombatActionRequirement.Always)
    }

    fun castMageSpell(key: KeyActionId = KeyActions.castSpell) {
        action(key, CombatActionKind.CastMageSpell, CombatActionRequirement.Mage)
    }

    fun build(): List<CombatActionModel> = actions.toList()

    private fun action(
        key: KeyActionId,
        kind: CombatActionKind,
        requirement: CombatActionRequirement,
    ) {
        actions += CombatActionModel(key, kind, requirement)
    }
}

@RylNextDsl
class PlayerRoundBuilder(initial: PlayerRoundModel? = null) {
    private var attackRules = initial?.attack ?: PlayerAttackRulesModel(
        hitBonus = 1,
        damageSpreadMax = 4,
        confusionDivisor = 3,
    )
    private var defendRules = initial?.defend ?: DefendRulesModel(
        weaponSkillPenalty = 2,
        restThreshold = 5,
    )
    private var battleCryRules = initial?.battleCry ?: BattleCryRulesModel(
        weaponSkillPenalty = 4,
    )

    fun attack(hitBonus: Int, damageSpreadMax: Int, confusionDivisor: Int) {
        attackRules = PlayerAttackRulesModel(hitBonus, damageSpreadMax, confusionDivisor)
    }

    fun defend(weaponSkillPenalty: Int, restThreshold: Int) {
        defendRules = DefendRulesModel(weaponSkillPenalty, restThreshold)
    }

    fun battleCry(weaponSkillPenalty: Int) {
        battleCryRules = BattleCryRulesModel(weaponSkillPenalty)
    }

    fun build(): PlayerRoundModel =
        PlayerRoundModel(attackRules, defendRules, battleCryRules)
}

@RylNextDsl
class MonsterRoundBuilder(initial: MonsterRoundModel? = null) {
    var spellChanceBasePercent: Int = initial?.spellChanceBasePercent ?: 15
    var spellPowerDivisor: Int = initial?.spellPowerDivisor ?: 2
    private var meleeRules = initial?.melee ?: MonsterMeleeRulesModel(
        hitBonus = 1,
        damageDivisor = 6,
        damageSpreadMax = 9,
        damageMin = 1,
        fallback = Scenes.Battle.monsterMelee,
    )

    fun spellChance(basePercent: Int, spellPowerDivisor: Int) {
        spellChanceBasePercent = basePercent
        this.spellPowerDivisor = spellPowerDivisor
    }

    fun melee(
        hitBonus: Int,
        damageDivisor: Int,
        damageSpreadMax: Int,
        damageMin: Int,
        fallback: SceneId,
    ) {
        meleeRules = MonsterMeleeRulesModel(hitBonus, damageDivisor, damageSpreadMax, damageMin, fallback)
    }

    fun build(): MonsterRoundModel =
        MonsterRoundModel(spellChanceBasePercent, spellPowerDivisor, meleeRules)
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
        stateFixture(id, body)
    }

    fun stateFixture(id: StateId, body: StateBuilder.() -> Unit) {
        states += StateBuilder(id, StateKind.AuthoredFixture).apply(body).build()
    }

    fun runtimeState(id: StateId, body: StateBuilder.() -> Unit) {
        states += StateBuilder(id, StateKind.RuntimeSnapshot).apply(body).build()
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
class StateBuilder(private val id: StateId, private val kind: StateKind) {
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
            kind = kind,
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
    var minute: Int = 0
    var rngSeed: Long? = null
    var activeParty: PartyId? = null
    var currentMap: WorldMapId = Maps.wilderness
    var currentMode: RuntimeMode = RuntimeMode.Wilderness
    var currentTown: TownId? = null
    var banished: Boolean = false
    var lastPlayedDay: Int? = null
    var partyMemberMax: Int = 4

    fun build(): RealmState =
        RealmState(
            day = day,
            hour = hour,
            minute = minute,
            rngSeed = rngSeed,
            activeParty = checkNotNull(activeParty) { "realm requires activeParty" },
            currentMap = currentMap,
            currentMode = currentMode,
            currentTown = currentTown,
            banished = banished,
            lastPlayedDay = lastPlayedDay ?: day,
            partyMemberMax = partyMemberMax,
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
    var facing: Facing? = null
    private var resourcesState = PartyResources(0, 0, 0, 0, 0)
    private var countersState = PartyCounters(null, 0, false, false, emptyList())
    private val flags = mutableListOf<FlagState>()
    private val milestones = mutableListOf<GameMilestoneId>()
    private val inventory = mutableListOf<InventorySlotState>()
    private val members = mutableListOf<PartyMemberState>()
    private val visitedNpcs = mutableMapOf<NpcId, NpcVisitState>()
    private val journal = mutableListOf<JournalEntryState>()

    fun resources(body: PartyResourcesBuilder.() -> Unit) {
        resourcesState = PartyResourcesBuilder().apply(body).build()
    }

    fun counters(body: PartyCountersBuilder.() -> Unit) {
        countersState = PartyCountersBuilder().apply(body).build()
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
        npcs.forEach { visitedNpcs[it] = NpcVisitState(it, true) }
    }

    fun npcVisits(body: NpcVisitBuilder.() -> Unit) {
        NpcVisitBuilder().apply(body).build().forEach { visitedNpcs[it.npc] = it }
    }

    fun journal(vararg entries: TextId) {
        journal += entries.map(JournalEntryState::Text)
    }

    fun journalLine(text: String) {
        journal += JournalEntryState.Line(text)
    }

    fun journal(body: JournalBuilder.() -> Unit) {
        journal += JournalBuilder().apply(body).build()
    }

    fun build(): PartyState =
        PartyState(
            id = id,
            player = player,
            name = name,
            position = position,
            facing = facing,
            resources = resourcesState,
            counters = countersState,
            flags = flags.toList(),
            milestones = milestones.toList(),
            inventory = inventory.sortedBy { it.slot },
            members = members.toList(),
            visitedNpcs = visitedNpcs.values.sortedBy { it.npc.slug },
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
class PartyCountersBuilder {
    var memberCount: Int? = null
    var kills: Int = 0
    var waitingMail: Boolean = false
    var attacked: Boolean = false
    private val attackedToday = mutableMapOf<PartyId, PartyAttackRecord>()

    fun attackToday(body: AttackTodayBuilder.() -> Unit) {
        AttackTodayBuilder().apply(body).build().forEach { attackedToday[it.party] = it }
    }

    fun build(): PartyCounters =
        PartyCounters(memberCount, kills, waitingMail, attacked, attackedToday.values.sortedBy { it.party.slug })
}

@RylNextDsl
class AttackTodayBuilder {
    private val records = mutableMapOf<PartyId, PartyAttackRecord>()

    fun party(party: PartyId, attacked: Boolean) {
        records[party] = PartyAttackRecord(party, attacked)
    }

    fun attacked(party: PartyId) {
        party(party, true)
    }

    fun clear(party: PartyId) {
        party(party, false)
    }

    fun build(): List<PartyAttackRecord> = records.values.sortedBy { it.party.slug }
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
class NpcVisitBuilder {
    private val visits = mutableMapOf<NpcId, NpcVisitState>()

    fun visited(npc: NpcId) {
        visits[npc] = NpcVisitState(npc, true)
    }

    fun notVisited(npc: NpcId) {
        visits[npc] = NpcVisitState(npc, false)
    }

    fun build(): List<NpcVisitState> = visits.values.sortedBy { it.npc.slug }
}

@RylNextDsl
class JournalBuilder {
    private val entries = mutableListOf<JournalEntryState>()

    fun entry(id: TextId) {
        entries += JournalEntryState.Text(id)
    }

    fun line(text: String) {
        entries += JournalEntryState.Line(text)
    }

    fun build(): List<JournalEntryState> = entries.toList()
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
    private var equipmentState: EquipmentState = EquipmentState(Items.none, Items.none)
    var armor: ItemId
        get() = equipmentState.armor
        set(value) {
            equipmentState = equipmentState.copy(armor = value)
        }
    var weapon: ItemId
        get() = equipmentState.weapon
        set(value) {
            equipmentState = equipmentState.copy(weapon = value)
        }
    var experience: Int = 0
    private var dead: Boolean = false

    fun equipped(body: EquipmentBuilder.() -> Unit) {
        equipmentState = EquipmentBuilder(equipmentState).apply(body).build()
    }

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
            equipment = equipmentState,
            experience = experience,
            dead = dead,
        )
}

@RylNextDsl
class EquipmentBuilder(initial: EquipmentState = EquipmentState(Items.none, Items.none)) {
    var weapon: ItemId = initial.weapon
    var armor: ItemId = initial.armor

    fun build(): EquipmentState = EquipmentState(weapon, armor)
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
            is IntAst.RealmScalar -> Unit
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

    fun text(block: TextBlock) {
        if (block is TextBlock.Structured) {
            block.lines.forEach { line ->
                line.parts.forEach { part ->
                    when (part) {
                        is TextPart.IntValue -> int(part.value)
                        is TextPart.StringValue -> str(part.value)
                        is TextPart.Literal,
                        is TextPart.RoleName,
                        is TextPart.RoleLocation,
                        is TextPart.TownName,
                        is TextPart.FuzzyTownLocation,
                        TextPart.OfferedItemName,
                        TextPart.OfferedItemPrice -> Unit
                    }
                }
            }
        }
    }

    fun ops(items: List<Op>) {
        items.forEach { op ->
            when (op) {
                is Op.Screen -> text(op.text)
                is Op.Emit -> Unit
                is Op.EmitIndexed -> int(op.value)
                is Op.WaitInput -> Unit
                is Op.Pause -> Unit
                is Op.LetInt -> int(op.value)
                is Op.BindText -> str(op.value)
                is Op.Set -> int(op.value)
                is Op.Add -> int(op.value)
                is Op.Sub -> int(op.value)
                is Op.AddClamped -> {
                    int(op.value)
                    int(op.max)
                }
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
                is Op.Menu -> {
                    text(op.prompt)
                    op.options.forEach { ops(it.body) }
                    ops(op.defaultOps)
                }
                is Op.AskMember -> text(op.prompt)
                is Op.AskInventorySlot -> text(op.prompt)
                is Op.AskMonster -> Unit
                is Op.ForEachMember -> ops(op.body)
                is Op.ForEachMonster -> ops(op.body)
                is Op.Speak -> Unit
                is Op.RunScene -> Unit
                is Op.Encounter -> Unit
                Op.ConsumeActiveItem -> Unit
                is Op.GrantReward -> op.reward.gold?.let(::int)
                is Op.RandomItemOffer -> {
                    text(op.offer.offer)
                    text(op.offer.choicePrompt)
                    text(op.offer.broke)
                    op.offer.messages.cantCarry?.let(::text)
                    op.offer.messages.pickedUp?.let(::text)
                    text(op.offer.deal)
                    text(op.offer.declined)
                }
                is Op.AchieveMilestone -> Unit
                is Op.NativeCall -> paths += op.path
            }
        }
    }

    model.scenes.forEach { ops(it.ops) }
    model.dialogue.forEach { dialogue ->
        dialogue.branches.forEach { ops(it.ops) }
    }
    model.spells.forEach { ops(it.ops) }
    model.magicItems.forEach { ops(it.ops) }
    model.missions.forEach {
        it.requires?.let(::bool)
    }
    return paths
}
