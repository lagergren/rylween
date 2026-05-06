// full.dsl.kts -- Rylween adventure, real-Kotlin DSL experiment.
//
// The file has two halves:
//
//   1. DSL framework  -- @DslMarker-tagged builder types + opcode shells.
//      Every body is `Unit` or `TODO()`; nothing here actually executes.
//      The point is that this file *compiles* against itself, and any
//      future engine could interpret the recorded structure.
//
//   2. The adventure  -- one big `adventure("rylween") { ... }` block at
//      the bottom assembling the full game from the world + scripts +
//      texts JSON5 currently in data/.
//
// The DSL choices are deliberate:
//
//   * Slug-keyed everything. Every cross-reference (item, monster, spell,
//     NPC, town, mission, scene, flag) is a string slug. No int slot tables,
//     no enum-by-position. State is a slug -> value map at runtime.
//
//   * Inline narration (`say "..."`) is allowed at any call site for
//     mission flavour. Shared / long text uses `text("speak.merchant.foo")`
//     and the engine resolves the id. Scenes that the legacy game used
//     `screen:` for stay as `screen("speak.merchant.offer")` -- those carry
//     their own clw + waitInput so the call site stays one line.
//
//   * Logic ops (`on`, `loop`, `forEachMember`, `menu`, `els`) are real
//     Kotlin builders, not strings. Conditions are typed `Cond`, integer
//     expressions are typed `IntExpr`. Mutations are operator-overload
//     property writes (`state.gold -= cost`).
//
//   * Random/procedural content uses generators declared once and reused.
//     Dungeons declare a `randomFill { ... }` table; quest dungeons spell
//     out specific floors with `floor(N) { run("scene") }` and let the
//     fill cover the rest.
//
//   * Items, monsters and spells live in tables at the top. Special
//     encounters can inline a unique monster (`monster("baron_ydarg") { ... }`
//     inside the encounter scope) -- the engine treats it as an anonymous
//     monster slug local to that scene.
//
// This file is an experiment; it is not wired into the engine. Treat it as
// a target shape to grow toward.
//
// =============================================================================

@file:Suppress(
    "unused", "FunctionName", "PropertyName",
    "MemberVisibilityCanBePrivate", "UnusedReceiverParameter",
    "RedundantSuspendModifier", "UNUSED_PARAMETER",
)

// -----------------------------------------------------------------------------
// SECTION 1 -- DSL framework (real Kotlin; bodies stubbed).
// -----------------------------------------------------------------------------

@DslMarker
annotation class RylDsl

// ---- expressions / conditions ---------------------------------------------

sealed interface IntExpr {
    operator fun plus(other: IntExpr): IntExpr = Bin(this, "+", other)
    operator fun plus(c: Int): IntExpr = Bin(this, "+", Lit(c))
    operator fun minus(other: IntExpr): IntExpr = Bin(this, "-", other)
    operator fun minus(c: Int): IntExpr = Bin(this, "-", Lit(c))
    operator fun times(other: IntExpr): IntExpr = Bin(this, "*", other)
    operator fun times(c: Int): IntExpr = Bin(this, "*", Lit(c))
    operator fun div(other: IntExpr): IntExpr = Bin(this, "/", other)
    operator fun div(c: Int): IntExpr = Bin(this, "/", Lit(c))
}

data class Lit(val v: Int) : IntExpr
data class Bin(val l: IntExpr, val op: String, val r: IntExpr) : IntExpr
data class VarI(val name: String) : IntExpr
data class FieldI(val target: String, val field: String) : IntExpr
data class IdxFieldI(val target: String, val idx: IntExpr, val field: String) : IntExpr
data class RandRange(val lo: IntExpr, val hi: IntExpr) : IntExpr
data class CallI(val fn: String, val args: List<Any>) : IntExpr

fun int(v: Int): IntExpr = Lit(v)
fun v(name: String): IntExpr = VarI(name)
fun random(range: IntRange): IntExpr = RandRange(Lit(range.first), Lit(range.last))
fun random(lo: IntExpr, hi: IntExpr): IntExpr = RandRange(lo, hi)
fun engineInt(fn: String, vararg args: Any): IntExpr = CallI(fn, args.toList())

sealed interface Cond
data class CmpC(val l: IntExpr, val op: String, val r: IntExpr) : Cond
data class FlagC(val slug: String, val value: String) : Cond
data class ChanceC(val pct: IntExpr) : Cond
data class AndC(val l: Cond, val r: Cond) : Cond
data class OrC(val l: Cond, val r: Cond) : Cond
data class NotC(val c: Cond) : Cond
data class CallC(val fn: String, val args: List<Any>) : Cond

infix fun IntExpr.lt(r: IntExpr): Cond = CmpC(this, "<", r)
infix fun IntExpr.lt(r: Int): Cond = CmpC(this, "<", Lit(r))
infix fun IntExpr.gt(r: IntExpr): Cond = CmpC(this, ">", r)
infix fun IntExpr.gt(r: Int): Cond = CmpC(this, ">", Lit(r))
infix fun IntExpr.eq(r: IntExpr): Cond = CmpC(this, "==", r)
infix fun IntExpr.eq(r: Int): Cond = CmpC(this, "==", Lit(r))
infix fun IntExpr.ne(r: Int): Cond = CmpC(this, "!=", Lit(r))
infix fun IntExpr.gte(r: Int): Cond = CmpC(this, ">=", Lit(r))
infix fun IntExpr.lte(r: Int): Cond = CmpC(this, "<=", Lit(r))
infix fun Cond.and(r: Cond): Cond = AndC(this, r)
infix fun Cond.or(r: Cond): Cond = OrC(this, r)
operator fun Cond.not(): Cond = NotC(this)

fun chance(pct: Int): Cond = ChanceC(Lit(pct))
fun chance(pct: IntExpr): Cond = ChanceC(pct)
fun engineCheck(fn: String, vararg args: Any): Cond = CallC(fn, args.toList())

// ---- references -----------------------------------------------------------

class FlagRef(val slug: String) {
    infix fun eq(value: String): Cond = FlagC(slug, value)
    infix fun set(value: String) { /* recorded by enclosing Script */ }
}
fun flag(slug: String): FlagRef = FlagRef(slug)

class IntStateRef(val name: String) : IntExpr {
    operator fun minusAssign(amount: Int) = Unit
    operator fun minusAssign(amount: IntExpr) = Unit
    operator fun plusAssign(amount: Int) = Unit
    operator fun plusAssign(amount: IntExpr) = Unit
    fun set(value: IntExpr) = Unit
    fun set(value: Int) = Unit
}

// Member field. Reads as IntExpr (so it composes in expressions); writes
// via `+=`, `-=`, and `set`. Replaces the old `member.setField("hp", v)`
// catch-all with a typed handle per known field.
class IntField(val ownerIdx: IntExpr, val name: String) : IntExpr {
    operator fun plusAssign(amount: Int) = Unit
    operator fun plusAssign(amount: IntExpr) = Unit
    operator fun minusAssign(amount: Int) = Unit
    operator fun minusAssign(amount: IntExpr) = Unit
    infix fun set(value: Int) = Unit
    infix fun set(value: IntExpr) = Unit
}
// Boolean field. Stored as 0/1 in the engine but expressed as `set true`
// / `set false` here.
class BoolField(val ownerIdx: IntExpr, val name: String) {
    infix fun set(value: Boolean) = Unit
    fun isTrue(): Cond = engineCheck("field_true", ownerIdx, name)
    fun isFalse(): Cond = engineCheck("field_false", ownerIdx, name)
}

class MemberRef(val idx: IntExpr) {
    val hp     = IntField(idx, "hit_points")
    val maxHp  = IntField(idx, "health")
    val mp     = IntField(idx, "magic")
    val wis    = IntField(idx, "wisdom")
    val cha    = IntField(idx, "charisma")
    val ws     = IntField(idx, "weapon_skill")
    val level  = IntField(idx, "level")
    val xp     = IntField(idx, "experience")
    val dead   = BoolField(idx, "dead")
    val weapon = IntField(idx, "weapon")
    val armor  = IntField(idx, "armor")
    fun isClass(slug: String): Cond = engineCheck("pa_is_class", idx, slug)
    fun isAlive(): Cond = engineCheck("pa_is_alive", idx)
    fun isDead(): Cond = engineCheck("pa_is_dead", idx)
    fun isSpellcaster(): Cond = engineCheck("pa_is_spellcaster", idx)
    fun name(): String = "<bound:pa_name($idx)>"
    operator fun get(field: String): IntExpr = IdxFieldI("member", idx, field)
    // Escape hatch for fields not promoted to typed handles yet.
    fun setField(field: String, value: IntExpr) = Unit
    fun setField(field: String, value: Int) = Unit
}

@RylDsl
class State {
    val gold = IntStateRef("gold")
    val food = IntStateRef("food")
    val torches = IntStateRef("torches")
    val timeLeft = IntStateRef("time_left")
    val hourOfDay = IntStateRef("hour_of_day")
    val hoursRested = IntStateRef("hours_rested")
    val pl = IntStateRef("pl")
    val cpl = IntStateRef("cpl")
    val cl = IntStateRef("cl")
    val partyMemberMax: IntExpr = engineInt("pa_party_member_max")
    val firstLevel: IntExpr = engineInt("pa_first_level")
    val aliveCount: IntExpr = engineInt("pa_alive_count")
    fun member(idx: IntExpr): MemberRef = MemberRef(idx)
    fun member(idx: Int): MemberRef = MemberRef(Lit(idx))
    fun inv(slot: Int): IntExpr = IdxFieldI("inventory", Lit(slot), "id")
    fun npcInTown(role: String): IntExpr = engineInt("npc_in_town", role)
    fun npcNameAtRole(role: String): String = "<bound:$role.name>"
    fun npcLocAtRole(role: String): String = "<bound:$role.loc>"
    fun pronoun(idx: IntExpr, kind: String): String = "<bound:pronoun($idx,$kind)>"
    fun objectName(itemIdExpr: IntExpr): String = "<bound:object($itemIdExpr)>"
    fun resEnum(table: String, key: IntExpr): String = "<bound:$table[$key]>"
    fun resEnumNameBySym(table: String, sym: String): String = "<bound:$table[$sym].name>"
}
val state = State()

// ---- Typed bindings (Pattern B from dsl-improvements.md) ------------------
//
// Engine binding helpers. Each function returns a `BoundExpr` that names
// an engine call + args; `bound(slug, expr)` records the binding and
// returns the placeholder `"{slug}"`, so Kotlin string interpolation can
// reach it. See dsl-improvements.md §B for the rationale.

class BoundExpr(val fn: String, val args: List<Any>)
enum class PronounKind { SUBJECT, OBJECT, POSSESSIVE }

fun paName(idx: IntExpr): BoundExpr = BoundExpr("pa_name", listOf(idx))
fun paName(idx: Int):     BoundExpr = paName(Lit(idx))
fun pronoun(idx: IntExpr, kind: PronounKind): BoundExpr =
    BoundExpr("pronoun", listOf(idx, kind.name.lowercase()))
fun pronoun(idx: Int, kind: PronounKind): BoundExpr = pronoun(Lit(idx), kind)
fun objectName(itemId: IntExpr): BoundExpr = BoundExpr("object", listOf(itemId))
fun resEnum(table: String, key: IntExpr): BoundExpr =
    BoundExpr("res_enum", listOf(table, key))
fun resEnumNameBySym(table: String, sym: String): BoundExpr =
    BoundExpr("res_enum_name_by_sym", listOf(table, sym))
fun resNpcRole(role: String): BoundExpr = BoundExpr("res_npc_role", listOf(role))
fun npcNameAtRole(role: String): BoundExpr = BoundExpr("npc_name_at_role", listOf(role))
fun npcLocAtRole(role: String):  BoundExpr = BoundExpr("npc_loc_at_role",  listOf(role))
fun wldLocationX(name: String):  BoundExpr = BoundExpr("wld_location_x",   listOf(name))
fun wldLocationY(name: String):  BoundExpr = BoundExpr("wld_location_y",   listOf(name))

// ---- script body ----------------------------------------------------------

@RylDsl
open class Script {
    // Output / I/O
    fun say(line: String) = Unit                           // inline narration; emits as text
    fun text(id: String) = Unit                            // emit by text id
    fun screen(id: String) = Unit                          // full screen: text + waitInput + clw
    fun pause(ms: Int) = Unit
    fun clw() = Unit
    fun clearMap() = Unit
    fun redrawMap() = Unit
    fun waitInput() = Unit
    fun statusLine() = Unit
    fun setBackdrop(picSlug: String) = Unit                // engine_call blitgfx
    fun journal(line: String) = Unit                       // appended to player journal
    fun narrate(actId: String) = Unit                      // emits a list of texts with pauses

    // Two primitives for sequential narration. Pick by intent:
    //
    //   * `chain(""" a """, """ b """, ...)` -- press-a-key narration.
    //     Each block emits, then waits for the player to press a key,
    //     then clears the text window, then the next block plays.
    //     Identical to the legacy `screen("id")` chain. This is the
    //     usual form for dialogue / mission text the player reads at
    //     their own pace.
    //
    //   * `cinematic(""" a """, """ b """, ..., pauseMs = 1500)` --
    //     timed narration. Each block emits, then pauses N ms (default
    //     1500), then clears, then the next block plays. No keypress.
    //     Use this for dramatic / atmospheric beats where interrupting
    //     for input would break the pacing (slamming doors, summoning
    //     spheres, mages emerging from mist).
    //
    // Both clear the text window between blocks by default. Pass
    // `clearBetween = false` to make text accumulate (e.g. the holy-
    // hand-grenade "Three. Four. ...FIVE." pattern, where each line
    // stacks on top of the previous instead of replacing it).
    //
    // For per-gap variation (different pause lengths, engine calls
    // mid-chain), don't use these -- write primitives inline:
    // `say(...); pause(N); clw(); say(...); waitInput()`.
    fun chain(
        vararg blocks: String,
        clearBetween: Boolean = true,
    ) = Unit
    fun cinematic(
        vararg blocks: String,
        pauseMs: Int = 1500,
        clearBetween: Boolean = true,
    ) = Unit

    // Same two primitives for playing a strings-block chain by id.
    fun playChain(prefix: String, clearBetween: Boolean = true) = Unit
    fun playCinematic(
        prefix: String,
        pauseMs: Int = 1500,
        clearBetween: Boolean = true,
    ) = Unit
    fun engineCall(name: String, vararg args: Any) = Unit
    fun emitIndexed(fmt: String, value: IntExpr) = Unit
    fun letInt(name: String, value: IntExpr): IntExpr { return VarI(name) }
    fun letInt(name: String, value: Int): IntExpr = letInt(name, Lit(value))
    fun letBound(name: String, fn: String, vararg args: Any) = Unit
    // Typed binding -- records (slug -> BoundExpr) for the engine and
    // returns the placeholder string `"{slug}"`. Kotlin string templates
    // turn `say("$name")` into `say("{slug}")`, which is what the engine
    // resolves at emit time.
    fun bound(slug: String, expr: BoundExpr): String = "{$slug}"

    // Control flow
    fun loop(block: Script.() -> Unit) = Unit
    fun breakOut() = Unit
    fun returnFromScript() = Unit

    // `on(cond) { ... } els { ... }`
    fun on(cond: Cond, then: Script.() -> Unit): ElseBranch = ElseBranch()
    class ElseBranch {
        infix fun els(block: Script.() -> Unit) = Unit
    }

    // Member iteration
    fun forEachMember(block: Script.(MemberRef) -> Unit) = Unit
    fun forEachMember(includeDead: Boolean, block: Script.(MemberRef) -> Unit) = Unit

    // Mutation primitives that don't fit operator overloads
    fun mutate(target: String, op: String, value: IntExpr) = Unit
    fun setFlag(slug: String, value: String) = Unit
    fun setFlag(ref: FlagRef, value: String) = Unit
    fun flagValue(slug: String): IntExpr = engineInt("flag_value", slug)

    // Menus
    fun menu(prompt: String? = null, block: MenuBuilder.() -> Unit) = Unit

    // World / scene calls
    fun runScene(slug: String, vararg bindings: Pair<String, Any>) = Unit
    fun playMission(slug: String, vararg bindings: Pair<String, Any>) = Unit
    fun enterDungeon(slug: String) = Unit
    fun encounter(foe: String, mandatory: Boolean = false, tier: String? = null) = Unit
    fun reward(block: RewardBuilder.() -> Unit) = Unit

    // Inventory
    fun give(item: String, count: Int = 1) = Unit
    fun take(item: String) = Unit
    fun invAdd(itemId: IntExpr, added: Script.() -> Unit, full: Script.() -> Unit) = Unit
    fun invList() = Unit
    fun setInventory(slot: IntExpr, item: String) = Unit
    fun setInventory(slot: Int, item: String) = Unit

    // Input
    fun inputString(maxLen: Int, accept: List<String>, match: Script.() -> Unit, miss: Script.() -> Unit) = Unit
    fun inputString(maxLen: Int, accept: String, match: Script.() -> Unit, miss: Script.() -> Unit) =
        inputString(maxLen, listOf(accept), match, miss)
    fun inputItem(slotVar: String, itemVar: String) = Unit
    fun inputNumber(varName: String, max: IntExpr) = Unit

    // Casting + spell support
    fun cast(spell: String) = Unit

    // Utility for inline expressions referring to a previously-bound `let`.
    operator fun String.unaryPlus(): IntExpr = VarI(this)
}

@RylDsl
class MenuBuilder {
    fun option(key: String, label: String? = null, then: Script.() -> Unit) = Unit
    fun digitRange(lo: Int, hi: IntExpr, indexVar: String = "who", then: Script.(IntExpr) -> Unit) = Unit
    fun keyRange(lo: String, hi: String, then: Script.(IntExpr) -> Unit) = Unit
    fun letterRange(lo: String, hi: String, then: Script.(IntExpr) -> Unit) = Unit
    fun default(then: Script.() -> Unit) = Unit
}

@RylDsl
class RewardBuilder {
    var gold: Int = 0
    fun gold(amount: Int) { gold = amount }
    fun items(vararg slugs: String) = Unit
    fun cantCarryText(id: String) = Unit
    fun pickedUpText(id: String) = Unit
}

// ---- top-level adventure builder ------------------------------------------

@RylDsl
class Adventure(val slug: String) {
    fun meta(block: MetaBuilder.() -> Unit) = Unit
    fun balance(block: BalanceBuilder.() -> Unit) = Unit
    fun stateModel(block: StateModelBuilder.() -> Unit) = Unit
    fun enums(block: EnumsBuilder.() -> Unit) = Unit
    fun items(block: ItemsBuilder.() -> Unit) = Unit
    fun monsters(block: MonstersBuilder.() -> Unit) = Unit
    fun spells(block: SpellsBuilder.() -> Unit) = Unit
    fun npcs(block: NpcsBuilder.() -> Unit) = Unit
    fun world(block: WorldBuilder.() -> Unit) = Unit
    fun strings(block: StringsBuilder.() -> Unit) = Unit
    fun scenes(block: ScenesBuilder.() -> Unit) = Unit
    fun missions(block: MissionsBuilder.() -> Unit) = Unit
    fun secrets(block: SecretsBuilder.() -> Unit) = Unit
    fun keymap(block: KeymapBuilder.() -> Unit) = Unit
    fun intro(block: Script.() -> Unit) = Unit
    fun mainLoop(block: Script.() -> Unit) = Unit
}

@RylDsl class MetaBuilder {
    var version: Int = 1
    var schema: String = ""
    var textWindow: Pair<Int, Int> = 0 to 0
}

@RylDsl class BalanceBuilder {
    fun upkeep(block: UpkeepBuilder.() -> Unit) = Unit
    fun rest(block: RestBuilder.() -> Unit) = Unit
    fun hunt(block: HuntBuilder.() -> Unit) = Unit
    fun fatigue(block: FatigueBuilder.() -> Unit) = Unit
    fun combat(block: CombatBuilder.() -> Unit) = Unit
    fun driv(block: DrivBuilder.() -> Unit) = Unit
    fun map(block: MapBuilder.() -> Unit) = Unit
    fun wilderness(block: WildernessBuilder.() -> Unit) = Unit
    var maxLevel: Int = 28
    fun xpThresholds(vararg amounts: Int) = Unit
    fun formulas(block: FormulasBuilder.() -> Unit) = Unit
}
@RylDsl class UpkeepBuilder {
    var foodCostLeaderDiv = 4
    var foodTickEvery = 5
    var starveDamage = 2
    var manaRegenChance = 50
}
@RylDsl class RestBuilder {
    var ambushChance = 19
    var healthGain = 15
    var foodCostPerMember = 4
    var hoursAdvanced = 8
    var hoursRestedReset = 14
    var temperatureMax = 15
    var tempLowLo = 6
    var tempLowHi = 10
    var tempVeryLowLo = 10
    var tempLowDamage = 5
    var tempVeryLowDamage = 5
}
@RylDsl class HuntBuilder {
    var successChance = 45
    var timeCost = 5
    var foodMin = 10
    var foodMax = 19
}
@RylDsl class FatigueBuilder {
    fun level(threshold: Int, textId: String, drain: Boolean = false, pauseAfter: Boolean = false, lt: Boolean = false) = Unit
}
@RylDsl class CombatBuilder {
    var fleeChance = 95
    var defendCap = 250
    var hpDefenseDiv = 12
    var encounterFoeCountMax = 8
    var encounterPicCount = 5
    fun attack(block: AttackBuilder.() -> Unit) = Unit
    fun defend(block: DefendBuilder.() -> Unit) = Unit
    fun monsterAttack(block: MonsterAttackBuilder.() -> Unit) = Unit
    fun run(block: RunBuilder.() -> Unit) = Unit
    fun battleCry(block: BattleCryBuilder.() -> Unit) = Unit
    fun dropTable(name: String, block: DropTableBuilder.() -> Unit) = Unit
}
@RylDsl class AttackBuilder { var hitBonus = 1; var damageSpreadMax = 4; var confusionDiv = 3 }
@RylDsl class DefendBuilder { var wsPenalty = 2; var restThreshold = 5 }
@RylDsl class MonsterAttackBuilder { var hitBonus = 1; var damageDiv = 6; var damageSpreadMax = 9; var damageMin = 1 }
@RylDsl class RunBuilder { var desperateChance = 31 }
@RylDsl class BattleCryBuilder { var wsPenalty = 4 }
@RylDsl class DropTableBuilder {
    var dropChance = 5; var treasureChance = 59
    var treasureLo = 36; var treasureHi = 46
    var magicLo = 100; var magicHi = 111
}
@RylDsl class DrivBuilder {
    fun statRoll(min: Int, max: Int) = Unit
    fun start(block: StartBuilder.() -> Unit) = Unit
    var dailyTimeLeft = 175
}
@RylDsl class StartBuilder {
    var x = 12; var y = 9; var gold = 1000; var torches = 5
    var food = 100; var timeLeft = 200; var hoursRested = 14
}
@RylDsl class MapBuilder {
    var rows = 40; var rowsMax = 42; var colsMax = 82
    var locationBounds = (5..74) to (5..34)
    var dungeonAvoid = "wm012"
}
@RylDsl class WildernessBuilder {
    var caveChance = 3
    var quietChance = 91
    var encounterSplit = 50
    var encounterEasyTier = "weak"
    var encounterHardTier = "wild"
    var caveDungeonRange = 3..16
    var caveEntryCost = 10
}
@RylDsl class FormulasBuilder {
    fun formula(name: String, block: () -> IntExpr) = Unit
}

@RylDsl class StateModelBuilder {
    fun flag(slug: String, vararg states: String, scope: String = "global") = Unit
    fun counter(slug: String, initial: Int = 0) = Unit
}

@RylDsl class EnumsBuilder {
    fun enum(name: String, block: EnumBuilder.() -> Unit) = Unit
}
@RylDsl class EnumBuilder {
    fun entry(slug: String, name: String? = null, block: (EnumEntryBuilder.() -> Unit)? = null) = Unit
}
@RylDsl class EnumEntryBuilder {
    var name: String = ""
    fun prop(key: String, value: Any) = Unit
    fun minStat(name: String, value: Int, failText: String) = Unit
    fun spells(vararg slugs: String) = Unit
    fun castsIn(vararg modes: String) = Unit
    fun levelup(block: LevelupBuilder.() -> Unit) = Unit
}
@RylDsl class LevelupBuilder {
    var weaponSkill = 0; var health = 0; var wisdom = 0; var charisma = 0
}

@RylDsl class ItemsBuilder {
    fun weapons(block: WeaponsBuilder.() -> Unit) = Unit
    fun armor(block: ArmorBuilder.() -> Unit) = Unit
    fun gear(block: GearBuilder.() -> Unit) = Unit
    fun potions(block: PotionsBuilder.() -> Unit) = Unit
    fun treasures(block: TreasuresBuilder.() -> Unit) = Unit
    fun magic(block: MagicItemsBuilder.() -> Unit) = Unit
    fun specials(block: SpecialsBuilder.() -> Unit) = Unit
}
@RylDsl class WeaponsBuilder { fun weapon(slug: String, name: String, cost: Int, damage: Int) = Unit }
@RylDsl class ArmorBuilder   { fun armor(slug: String, name: String, cost: Int, ac: Int) = Unit }
@RylDsl class GearBuilder    { fun gear(slug: String, name: String, cost: Int) = Unit }
@RylDsl class PotionsBuilder {
    fun potion(slug: String, name: String, cost: Int, kind: String, amount: Int = 0) = Unit
}
@RylDsl class TreasuresBuilder { fun treasure(slug: String, name: String, cost: Int) = Unit }
@RylDsl class MagicItemsBuilder {
    fun magic(slug: String, name: String, cost: Int, effect: Script.() -> Unit) = Unit
}
@RylDsl class SpecialsBuilder { fun special(slug: String, name: String, tag: String = slug) = Unit }

@RylDsl class MonstersBuilder {
    fun monster(slug: String, block: MonsterBuilder.() -> Unit) = Unit
}
@RylDsl class MonsterBuilder {
    var name: String = ""
    var group: IntRange = 1..1
    var hp: IntRange = 1..1
    var ws: Int = 0                   // weapon skill %
    var sp: Int = 0                   // special-attack %
    var immune: Boolean = false
    var tier: String? = null
    var levelDivisor: Int? = null     // n2_lv_div for some tiers
}

@RylDsl class SpellsBuilder {
    fun mage(slug: String, slot: Int, baseCost: Int, name: String, effect: Script.() -> Unit) = Unit
    fun priest(slug: String, slot: Int, baseCost: Int, name: String, effect: Script.() -> Unit) = Unit
    fun monsterSpell(name: String, effect: Script.() -> Unit) = Unit
    fun monsterMelee(effect: Script.() -> Unit) = Unit
}

@RylDsl class NpcsBuilder {
    fun npc(slug: String, block: NpcBuilder.() -> Unit) = Unit
    fun role(slug: String, npc: String) = Unit
}
@RylDsl class NpcBuilder {
    var name: String = ""
    var job: String = ""
    var fixed: Boolean = false
    var towne: String? = null
    var building: String? = null
    var x: Int? = null
    var y: Int? = null
    var gender: String? = null
}

@RylDsl class WorldBuilder {
    fun towns(block: TownsBuilder.() -> Unit) = Unit
    fun buildings(block: BuildingsBuilder.() -> Unit) = Unit
    fun terrain(block: TerrainBuilder.() -> Unit) = Unit
    fun dungeons(block: DungeonsBuilder.() -> Unit) = Unit
    fun ydarg(block: YdargBuilder.() -> Unit) = Unit
}
@RylDsl class TownsBuilder { fun town(slug: String, block: TownBuilder.() -> Unit) = Unit }
@RylDsl class TownBuilder {
    var name: String = ""
    var x: Int = 0; var y: Int = 0
    var tax: Int = 0
    var hasChurch: Boolean = false
    var townClass: String = "village"
}
@RylDsl class BuildingsBuilder {
    fun building(slug: String, opensAt: Int, closesAt: Int) = Unit
}
@RylDsl class TerrainBuilder {
    fun tile(char: Char, glyph: String, color: String, klass: String? = null) = Unit
}
@RylDsl class DungeonsBuilder {
    fun dungeon(slug: String, block: DungeonBuilder.() -> Unit) = Unit
    fun randomFill(name: String = "default", block: RandomFillBuilder.() -> Unit) = Unit
}
@RylDsl class RandomFillBuilder {
    fun use(scene: String, weight: Int = 1) = Unit
}
@RylDsl class DungeonBuilder {
    var name: String = ""
    var levels: IntExpr = Lit(20)
    var fill: String = "default"
    fun floor(n: Int, block: FloorBuilder.() -> Unit) = Unit
    fun floors(vararg ns: Int, block: FloorBuilder.() -> Unit) = Unit
    fun floors(range: IntRange, block: FloorBuilder.() -> Unit) = Unit
    fun bossFloor(scene: String) = Unit
    fun gating(flag: String, value: String) = Unit
}
@RylDsl class FloorBuilder {
    fun run(scene: String, vararg bindings: Pair<String, Any>) = Unit
    fun untilFlag(slug: String, value: String) = Unit
}
@RylDsl class YdargBuilder {
    var entry = 0 to 0
    var mapSize = 5
    fun confrontationSequence(vararg foes: String) = Unit
    fun roomDropTable(block: YdargDropBuilder.() -> Unit) = Unit
    fun pitfallTraps(vararg ids: String) = Unit
    fun stairItem(digit: Int, special: String, rejectMsg: String) = Unit
    fun narration(act: String, vararg textIds: String) = Unit
}
@RylDsl class YdargDropBuilder {
    var gateMax = 4
    var kindMax = 2
    var weaponsRange = 1..14
    var armorRange = 36..42
    var gearRange = 39..46
    var magicRange = 100..111
}

// Strings. The framework normalizes the body so the data file never
// mentions `.trimIndent()`, `\n`, or margin chars: leading/trailing blank
// lines from the `"""` boundaries are dropped and the common leading
// whitespace is stripped. Each remaining source line is one screen line;
// the engine handles wrapping and \r\n at emit time.
//
// `chain(prefix, body1, body2, ...)` groups a numbered sequence of
// screens that always play in order. Each block is auto-named
// `prefix.0`, `prefix.1`, ...; scenes can play the whole chain with
// `playChain("prefix")` (or address one beat by index if they need to).
// Same indent normalization as `str`. Use this only for sequences that
// genuinely play as a unit; if narration is unique to one scene, prefer
// inlining it as `chain(""" ... """, ...)` directly in the scene body.
@RylDsl class StringsBuilder {
    fun str(id: String, body: String) = Unit
    fun chain(prefix: String, vararg blocks: String) = Unit
}

@RylDsl class ScenesBuilder {
    fun scene(slug: String, vararg expects: String, body: Script.() -> Unit) = Unit
}

@RylDsl class MissionsBuilder {
    fun mission(slug: String, block: MissionBuilder.() -> Unit) = Unit
}
@RylDsl class MissionBuilder {
    var triggerKind: String = "position"     // position | random | terrain | dungeon_loc | enter_key
    var x: Int? = null
    var y: Int? = null
    var randomChance: Int? = null
    var terrainChar: Char? = null
    var dungeonSlot: Int? = null
    var returnsOnSkip: Boolean = false
    var requires: Cond? = null
    fun bindings(vararg pairs: Pair<String, String>) = Unit
    fun body(block: Script.() -> Unit) = Unit
}

@RylDsl class SecretsBuilder {
    fun backdoor(slug: String, block: BackdoorBuilder.() -> Unit) = Unit
}
@RylDsl class BackdoorBuilder {
    var name: String = ""
    fun key(char: Char, action: String, vararg args: Pair<String, Any>) = Unit
    fun stepTrigger(everyN: Int, action: String, vararg args: Pair<String, Any>) = Unit
}

@RylDsl class KeymapBuilder {
    fun town(key: Char, action: String, requires: String? = null) = Unit
    fun combat(key: Char, action: String, requires: String? = null) = Unit
    fun wilderness(key: Char, action: String) = Unit
    fun pauseVerbs(vararg verbs: String) = Unit
}

fun adventure(slug: String, block: Adventure.() -> Unit): Adventure =
    Adventure(slug).apply(block)

// ---- Colors ---------------------------------------------------------------
//
// The legacy texts had inline ANSI byte sequences (e.g. `[31m...[37m`).
// We expose them as values + wrapper functions instead so the data file
// reads `${red("DIES!")}` or `${YELLOW}DAWN${GREEN}` rather than raw escape
// codes. The string the engine receives is identical -- just lexically
// nicer at the call site.
//
//   * `red(s)` / `yellow(s)` / `green(s)` / `magenta(s)` -- wrap `s` in the
//     color and reset to white (the most common pattern).
//
//   * `RED` / `YELLOW` / `GREEN` / `MAGENTA` / `WHITE` -- bare color
//     switches. Use these when the legacy text resets to a non-default
//     color (e.g. statusline yellow → green) or when the switch has no
//     pair (e.g. magenta HUNGRY hangs until next text).
const val RED     = "[31m"
const val GREEN   = "[32m"
const val YELLOW  = "[33m"
const val MAGENTA = "[35m"
const val WHITE   = "[37m"

fun red(s: String):     String = "$RED$s$WHITE"
fun green(s: String):   String = "$GREEN$s$WHITE"
fun yellow(s: String):  String = "$YELLOW$s$WHITE"
fun magenta(s: String): String = "$MAGENTA$s$WHITE"

// =============================================================================
// SECTION 2 -- The full Rylween adventure.
// =============================================================================

val rylween = adventure("rylween") {

    meta {
        version = 1
        schema = "rylween-world-v0"
        textWindow = 26 to 15
    }

    // -------------------------------------------------------------------------
    // Balance: every numeric tuning constant lives here.
    // -------------------------------------------------------------------------
    balance {
        upkeep {
            foodCostLeaderDiv = 4
            foodTickEvery = 5
            starveDamage = 2
            manaRegenChance = 50
        }
        rest {
            ambushChance = 19
            healthGain = 15
            foodCostPerMember = 4
            hoursAdvanced = 8
            hoursRestedReset = 14
            temperatureMax = 15
            tempLowLo = 6;       tempLowHi = 10
            tempVeryLowLo = 10
            tempLowDamage = 5;   tempVeryLowDamage = 5
        }
        hunt {
            successChance = 45
            timeCost = 5
            foodMin = 10; foodMax = 19
        }
        fatigue {
            level(threshold = 0,   textId = "subs.food_handler.tired")
            level(threshold = -4,  textId = "subs.food_handler.exhausted", drain = true)
            level(threshold = -8,  textId = "subs.food_handler.weak",      drain = true, pauseAfter = true)
            level(threshold = -8,  textId = "subs.faint",                  drain = true, lt = true)
        }
        combat {
            fleeChance = 95
            defendCap = 250
            hpDefenseDiv = 12
            encounterFoeCountMax = 8
            encounterPicCount = 5
            attack       { hitBonus = 1; damageSpreadMax = 4; confusionDiv = 3 }
            defend       { wsPenalty = 2; restThreshold = 5 }
            monsterAttack{ hitBonus = 1; damageDiv = 6; damageSpreadMax = 9; damageMin = 1 }
            run          { desperateChance = 31 }
            battleCry    { wsPenalty = 4 }
            dropTable("default") {
                dropChance = 5; treasureChance = 59
                treasureLo = 36; treasureHi = 46
                magicLo = 100;   magicHi = 111
            }
        }
        driv {
            statRoll(min = 15, max = 70)
            start { x = 12; y = 9; gold = 1000; torches = 5; food = 100; timeLeft = 200; hoursRested = 14 }
            dailyTimeLeft = 175
        }
        map {
            rows = 40; rowsMax = 42; colsMax = 82
            locationBounds = (5..74) to (5..34)
            dungeonAvoid = "wm012"
        }
        wilderness {
            caveChance = 3
            quietChance = 91
            encounterSplit = 50
            encounterEasyTier = "weak"
            encounterHardTier = "wild"
            caveDungeonRange = 3..16
            caveEntryCost = 10
        }
        maxLevel = 28
        xpThresholds(
            0,       0,       2000,    4000,    8000,    12000,   20000,   28000,
            36000,   48000,   54000,   64000,   74000,   86000,   98000,   110000,
            125000,  150000,  180000,  240000,  300000,  400000,  550000,  700000,
            900000,  1100000, 1500000, 2000000, 2800000,
        )
        formulas {
            // donkey food cap = 150 + 150 * y  (where y is donkey count)
            formula("donkey_food_cap") { int(150) + (int(150) * v("y")) }
            // encounter_h1_scaled = h1_base + 2*party_avg + (first_level>2 ? first_level*2 : 0)
            formula("encounter_h1_scaled") {
                v("h1_base") + (v("party_avg") * 2) + (v("first_level") * 2) // bonus folded; engine handles gate
            }
            formula("encounter_h2_scaled") { v("h2_base") + (v("first_level") * 3) }
            formula("reward_xp_per_survivor") {
                (v("noc") * (v("h2") / 4) * (v("weapon_skill") / 4)) + (v("sp") * 3)
            }
            formula("reward_gold_party") {
                v("noc") * (v("h2") / 7) * (v("weapon_skill") / 4)
            }
            formula("level_up_hp_floor") { (v("health") * 2) / 3 }
        }
    }

    // -------------------------------------------------------------------------
    // State model. Every persistent flag/counter is declared here.
    // -------------------------------------------------------------------------
    stateModel {
        // Mission state machines (slug -> ordered states; "none" is implicit start).
        flag("garth.bandits",        "none", "accepted", "recovered", "closed")
        flag("ranger.letter",        "none", "accepted", "delivered")
        flag("fighter.priest_book",  "none", "priest_told", "priest_visited", "wizard_robbed", "fighter_grateful")
        flag("church.donation.once", "no",   "yes")
        flag("silver_key",           "none", "map_bought", "note_found", "thief2_squeezed",
                                     "smith_confessed", "cave_located", "key_taken")
        flag("erethor.lute",         "none", "rumour_told", "lute_taken", "note_received", "cloak_received")
        flag("bracelets.thun",       "none", "map_bought", "bracelets_taken")
        flag("nymph.seeker",         "none", "told", "wizard3t_active")
        flag("peasant_wife",         "none", "accepted", "rescued", "paid")
        flag("jazzy_ogre",           "none", "accepted", "ogres_cleared", "jazzy_defeated")
        flag("mastercrown",
             "none", "task1_assigned", "task1_done", "task2_assigned", "task2_done",
             "task3_assigned", "borin_met", "dagger_taken", "borin_paid",
             "crown_told", "crown_taken")
        flag("hermit.troll_tooth",   "none", "accepted", "extracted", "delivered", "fire_shield_acquired")
        flag("encounter.immune",     "no", "yes", scope = "encounter")

        counter("gold",        initial = 1000)
        counter("food",        initial = 100)
        counter("torches",     initial = 5)
        counter("time_left",   initial = 200)
        counter("hour_of_day", initial = 5)
        counter("hours_rested",initial = 14)
        counter("pl",          initial = 4)
    }

    // -------------------------------------------------------------------------
    // Enums. Slug-keyed; `id` from the legacy json5 is dropped -- callers refer
    // to entries by slug only.
    // -------------------------------------------------------------------------
    enums {
        enum("race") {
            entry("human")
            entry("dwarf")
            entry("elf")
            entry("halfling")
        }
        enum("pc_class") {
            entry("fighter") {
                name = "Fighter"
                minStat("weapon_skill", 50, "driv.create.weapon_skill_low")
                levelup { weaponSkill = 3; health = 8; wisdom = 1; charisma = 1 }
            }
            entry("thief") {
                name = "Thief"
                levelup { weaponSkill = 1; health = 5; wisdom = 1; charisma = 2 }
            }
            entry("mage") {
                name = "Mage"
                minStat("wisdom", 50, "driv.create.wisdom_low")
                spells("mage.frost", "mage.fire", "mage.confusion",
                       "mage.death", "mage.hold", "mage.antimagic")
                castsIn("combat")
                levelup { weaponSkill = 1; health = 4; wisdom = 7; charisma = 2 }
            }
            entry("priest") {
                name = "Priest"
                minStat("wisdom", 50, "driv.create.wisdom_low")
                spells("priest.heal", "priest.restoration", "priest.rebirth",
                       "priest.banish", "priest.revelation", "priest.teleport",
                       "priest.earthquake")
                castsIn("combat")
                levelup { weaponSkill = 1; health = 5; wisdom = 8; charisma = 3 }
            }
            entry("bard") {
                name = "Bard"
                minStat("charisma", 50, "driv.create.charisma_low")
                levelup { weaponSkill = 2; health = 7; wisdom = 2; charisma = 6 }
            }
            entry("ranger") {
                name = "Ranger"
                levelup { weaponSkill = 2; health = 8; wisdom = 3; charisma = 3 }
            }
        }
        enum("gender") { entry("male"); entry("female") }
        enum("job") {
            for (j in listOf(
                "merchant", "ranger", "fighter", "wizard", "priest", "thief",
                "peasant", "bard", "drifter", "half_orc", "half_elf",
                "smith", "bargirl", "innkeeper", "tavernkeeper", "hermit",
            )) entry(j)
        }
        enum("towne") {
            entry("thord");  entry("ollk");  entry("wilor");  entry("murn")
        }
        enum("building") {
            entry("none"); entry("inn"); entry("tavern")
        }
        enum("month") {
            for (m in listOf("January","February","March","April","May","June",
                             "July","August","September","October","November","December"))
                entry(m.lowercase()) { name = m }
        }
        enum("pic") {
            for (p in listOf("inn","tavern","smith","church","merchant",
                             "insidecave","cave","death","hut","ydarg","city",
                             "wonbattle","tent","notent","guild")) entry(p)
        }
        enum("dungeon_encounter") {
            // Same scenes the dungeon-script shard implements; one entry per
            // encounter slot the random fill / floor-wiring layer can pick.
            for (e in listOf(
                "find_object","gold","encounter_weak_a","encounter_weak_b",
                "encounter_deep","nothing_a","nothing_b","gold_chest","torches",
                "fire_shield_guardian","fire_shield",
                "thun_riddle1","thun_riddle2","thun_riddle3","bracelets",
                "spectrum_message","colour_riddle","nolrim_tomb",
                "master_crown","silver_sanctum","hobgoblin_patrol",
                "royal_guard1","royal_guard2","hobgoblin_chief",
                "orc_nest","tammys_prison","erethor_priests","lute_chamber",
            )) entry(e)
        }
        enum("world_locations") {
            entry("hermit_dungeon")       { name = "Hermit dungeon" }
            entry("thun_temple")          { name = "Temple of Thun" }
            entry("kobold_village")       { name = "Kobold village" }
            entry("borin_camp")           { name = "Borin camp" }
            entry("nolrim_crypt")         { name = "Nolrim's crypt" }
            entry("master_crown_dungeon") { name = "Master Crown dungeon" }
            entry("beast_cave")           { name = "Beast Cave" }
            entry("silver_key_pit")       { name = "Pit of the Silver Key" }
            entry("hobgoblin_cave")       { name = "Hobgoblin cave" }
        }
    }

    // -------------------------------------------------------------------------
    // Items. Tables; magic items and potions carry effect bodies.
    // -------------------------------------------------------------------------
    items {
        weapons {
            weapon("blank",          "-",              0,     1)
            weapon("sword",          "Sword",          160,   6)
            weapon("axe",            "Axe",            240,  10)
            weapon("bow",            "Bow",            300,  12)
            weapon("club",           "Club",            40,   3)
            weapon("dart",           "Dart",            20,   2)
            weapon("sling",          "Sling",           60,   4)
            weapon("longsword",      "Longsword",      400,  14)
            weapon("trident",        "Trident",        500,  16)
            weapon("halbard",        "Halbard",        600,  18)
            weapon("pole_arm",       "Pole Arm",       700,  20)
            weapon("crossbow",       "Crossbow",       900,  22)
            weapon("scythe",         "Scythe",        1000,  24)
            weapon("scimitar",       "Scimitar",      1200,  26)
            weapon("two_hand_sword", "Two-hand Sword",1400,  30)
            weapon("magic_dagger",   "Magic Dagger",  1800,  24)
            weapon("magic_sword",    "Magic Sword",   2400,  36)
            weapon("magic_axe",      "Magic Axe",     3000,  42)
            weapon("dancing_sword",  "Dancing Sword", 5000,  52)
            weapon("death_blade",    "Death Blade",  10000,  68)
        }
        armor {
            armor("leather",       "Leather",        260,    2)
            armor("chain_mail",    "Chain Mail",     520,    4)
            armor("plate_mail",    "Plate Mail",     980,    7)
            armor("ring_mail",     "Ring Mail",      720,    5)
            armor("elvin_cloak",   "Elvin Cloak",   1300,   10)
            armor("mithril_mail",  "Mithril Mail",  2400,   14)
            armor("admt_mail",     "Adamantine Mail",4400,  18)
            armor("prot_sphere",   "Protection Sphere",8800,22)
            armor("cloak_of_gods", "Cloak of Gods", 28000,  30)
        }
        gear {
            gear("torch",   "Torch",    1)
            gear("ration",  "Ration",   2)
            gear("blanket", "Blanket",  6)
            gear("tent",    "Tent",    10)
            gear("rope",    "Rope",    15)
        }
        potions {
            potion("red_potion",   "Red Potion",   30,  kind = "heal",         amount = 15)
            potion("blue_potion",  "Blue Potion", 100,  kind = "poison",       amount = 15)
            potion("amber_potion", "Amber Potion",500,  kind = "restore_mana")
        }
        treasures {
            treasure("red_gem",      "Red Gem",      1000)
            treasure("blue_gem",     "Blue Gem",     1500)
            treasure("green_gem",    "Green Gem",    2000)
            treasure("chalice",      "Chalice",      2340)
            treasure("pendant",      "Pendant",      1200)
            treasure("broche",       "Broche",        700)
            treasure("crystal_bowl", "Crystal Bowl", 1800)
            treasure("necklace",     "Necklace",     2200)
            treasure("donkey",       "Donkey",        800)
        }

        // Magic items each carry a self-contained effect script. The bodies
        // here are lossy summaries -- the original DSL had 5..30 ops per item;
        // I record the *shape* of what each does.
        magic {
            magic("frost_wand", "Frost Wand", 1000) {
                // wand shoots a cold beam at one foe, computing damage like
                // mage.frost; consumed after use.
                say("The wand crackles with frost.")
                cast("mage.frost")
                take("frost_wand")
            }
            magic("fire_wand", "Fire Wand", 1500) {
                say("Flames erupt from the wand.")
                cast("mage.fire")
                take("fire_wand")
            }
            magic("gem_of_confusion", "Gem of Confusion", 800) {
                say("The gem flares. Foes look bewildered.")
                cast("mage.confusion")
                take("gem_of_confusion")
            }
            magic("gem_of_blindness", "Gem of Blindness", 800) {
                say("Light explodes from the gem.")
                cast("mage.confusion")  // blindness routes through confusion path
                take("gem_of_blindness")
            }
            magic("drums_of_death", "Drums of Death", 9000) {
                say("A funereal drumbeat pounds. The earth trembles.")
                cast("priest.earthquake")
                take("drums_of_death")
            }
            magic("staff_of_healing", "Staff of Healing", 670) {
                menu("Heal whom?") {
                    digitRange(1, state.partyMemberMax) { who ->
                        on(state.member(who).isAlive()) {
                            state.member(who).setField("hit_points",
                                state.member(who)["health"])
                            say("The staff bathes ${state.member(who).name()} in gold light.")
                            pause(800)
                        } els {
                            say("They are beyond a healing.")
                            pause(800)
                        }
                    }
                }
            }
            magic("blessing_scroll", "Blessing Scroll", 250) {
                say("You read the blessing aloud. Hearts strengthen.")
                forEachMember { m -> m.setField("hit_points", m["health"]) }
                take("blessing_scroll")
            }
            magic("staff_of_curing", "Staff of Curing", 480) {
                forEachMember { m -> m.setField("hit_points", m["health"]) }
                say("A wave of warmth fills the party.")
            }
            magic("gem_of_resurrection", "Gem of Resurrection", 12000) {
                menu("Whom to revive?") {
                    digitRange(1, state.partyMemberMax) { who ->
                        on(state.member(who).isDead()) {
                            state.member(who).setField("dead", 0)
                            state.member(who).setField("hit_points", 1)
                            state.pl += 1
                            say("Life flows back into ${state.member(who).name()}.")
                            pause(1200)
                        } els {
                            say("They live still.")
                        }
                    }
                }
                take("gem_of_resurrection")
            }
            magic("kaels_axe", "Kael's Axe", 6000) {
                // Throw-and-return: hits all foes for moderate damage.
                say("Kael's Axe whirls through the enemy ranks.")
                // Effect handled via tagged combat hook.
                engineCall("kaels_axe_strike")
            }
            magic("holy_handgrenade", "Holy Hand Grenade", 4500) {
                say("Three. Four. ...")
                pause(800)
                say("FIVE.")
                pause(400)
                engineCall("holy_handgrenade_strike")
                take("holy_handgrenade")
            }
            magic("flute_of_madness", "Flute of Madness", 9000) {
                // Pure narration -- a curse item the player should not use.
                say("You raise the flute and play.")
                pause(1000)
                say("Foes look puzzled. Your friends look terrified.")
                pause(1500)
                say("Then everything goes quiet.")
                engineCall("flute_of_madness_strike")
                take("flute_of_madness")
            }
        }

        specials {
            // Quest items. `tag` is the special-flag name the engine bumps
            // (silverkey: 0=none, 1=have, 2=castle-ruined; mastercrown etc.
            // similar). Cost 0 -> never sold.
            special("fireshield",  "Fire Shield")
            special("bracelets",   "Bracelets of Thun")
            special("mastercrown", "Master Crown")
            special("jazzyfoot",   "Jazzy's Foot")
            special("silverkey",   "Silver Key")
        }
    }

    // -------------------------------------------------------------------------
    // Monsters. Slug-keyed table. Special-encounter monsters that are unique
    // to one quest can also be inlined inside the encounter scene; the four
    // ydarg bosses and `skagerberg_killer` (sysop backdoor) live here for
    // discoverability.
    // -------------------------------------------------------------------------
    monsters {
        // ---- weak tier (random fill) -----------------------------------------
        monster("kobold")             { name = "Kobold";          group = 2..8; hp = 30..50;  ws = 35; tier = "weak" }
        monster("orc")                { name = "Orc";             group = 2..5; hp = 50..99;  ws = 50; tier = "weak" }
        monster("goblin")             { name = "Goblin";          group = 2..6; hp = 30..70;  ws = 40; tier = "weak" }
        monster("wild_dog")           { name = "Wild Dog";        group = 2..5; hp = 25..60;  ws = 35; tier = "weak" }
        monster("hobgoblin")          { name = "Hobgoblin";       group = 1..4; hp = 80..150; ws = 60; tier = "weak" }
        monster("zombie")             { name = "Zombie";          group = 2..5; hp = 60..120; ws = 50; tier = "weak" }
        monster("bandit")             { name = "Bandit";          group = 1..3; hp = 70..130; ws = 65; tier = "weak" }
        monster("knight")             { name = "Knight";          group = 1..2; hp =100..180; ws = 75; tier = "weak" }
        monster("troll")              { name = "Troll";           group = 1..1; hp =120..220; ws = 70; tier = "weak" }
        monster("warrior")            { name = "Warrior";         group = 1..2; hp = 90..160; ws = 70; tier = "weak" }

        // ---- wild tier (40..53 slot range) -----------------------------------
        monster("gnoll")              { name = "Gnoll";           group = 2..6; hp = 80..160; ws = 55; tier = "wild" }
        monster("stirge")             { name = "Stirge";          group = 3..7; hp = 40..100; ws = 50; tier = "wild" }
        monster("hill_giant")         { name = "Hill Giant";      group = 1..2; hp =200..360; ws = 75; tier = "wild" }
        monster("wizard")             { name = "Wizard";          group = 1..1; hp =110..180; ws = 60; sp = 30; tier = "wild" }
        monster("druid")              { name = "Druid";           group = 1..2; hp =100..160; ws = 55; sp = 20; tier = "wild" }
        monster("sabre_tooth_tiger")  { name = "Sabre-Tooth Tiger";group= 1..3; hp =140..240; ws = 70; tier = "wild" }
        monster("head_hunter")        { name = "Head Hunter";     group = 2..4; hp =100..170; ws = 65; tier = "wild" }
        monster("skeleton")           { name = "Skeleton";        group = 3..7; hp = 60..120; ws = 50; tier = "wild" }
        monster("vampire_bat")        { name = "Vampire Bat";     group = 4..8; hp = 50..100; ws = 60; sp = 10; tier = "wild" }
        monster("giant_snake")        { name = "Giant Snake";     group = 1..2; hp =140..240; ws = 70; sp = 15; tier = "wild" }
        monster("ghoul")              { name = "Ghoul";           group = 2..5; hp = 90..160; ws = 60; sp = 10; tier = "wild" }
        monster("spider")             { name = "Spider";          group = 2..6; hp = 70..140; ws = 55; sp = 15; tier = "wild" }
        monster("huge_lizard")        { name = "Huge Lizard";     group = 1..3; hp =160..280; ws = 70; tier = "wild" }
        monster("dryder")             { name = "Dryder";          group = 1..2; hp =180..320; ws = 75; sp = 20; tier = "wild" }

        // ---- deep tier (60..79) ----------------------------------------------
        monster("xvart")              { name = "Xvart";           group = 4..8; hp = 80..140; ws = 55; tier = "deep" }
        monster("jelly_cube")         { name = "Jelly Cube";      group = 1..2; hp =200..380; ws = 60; sp = 20; tier = "deep" }
        monster("troglodyte")         { name = "Troglodyte";      group = 2..5; hp =110..180; ws = 65; tier = "deep" }
        monster("bugbear")            { name = "Bugbear";         group = 2..4; hp =140..220; ws = 70; tier = "deep" }
        monster("drow_elf")           { name = "Drow Elf";        group = 1..3; hp =120..200; ws = 80; sp = 25; tier = "deep" }
        monster("phantom")            { name = "Phantom";         group = 1..2; hp =160..260; ws = 70; sp = 30; tier = "deep" }
        monster("worg")               { name = "Worg";            group = 2..5; hp =130..210; ws = 70; tier = "deep" }
        monster("rust_monster")       { name = "Rust Monster";    group = 1..2; hp =140..240; ws = 65; sp = 35; tier = "deep" }
        monster("owlbear")            { name = "Owlbear";         group = 1..2; hp =220..360; ws = 75; tier = "deep" }
        monster("werewolf")           { name = "Werewolf";        group = 1..3; hp =180..280; ws = 75; sp = 20; tier = "deep" }
        monster("hook_horror")        { name = "Hook Horror";     group = 1..2; hp =200..340; ws = 75; tier = "deep" }
        monster("minotaur")           { name = "Minotaur";        group = 1..1; hp =260..420; ws = 80; tier = "deep" }
        monster("imp")                { name = "Imp";             group = 2..5; hp =100..160; ws = 70; sp = 30; tier = "deep" }
        monster("svirfneblin")        { name = "Svirfneblin";     group = 2..4; hp =110..170; ws = 65; sp = 20; tier = "deep" }
        monster("yeti")               { name = "Yeti";            group = 1..2; hp =220..360; ws = 75; tier = "deep" }
        monster("wight")              { name = "Wight";           group = 1..3; hp =160..260; ws = 70; sp = 30; tier = "deep" }
        monster("wraith")             { name = "Wraith";          group = 1..2; hp =200..320; ws = 75; sp = 35; tier = "deep" }
        monster("basilisk")           { name = "Basilisk";        group = 1..1; hp =280..440; ws = 80; sp = 40; tier = "deep" }
        monster("wyvern")             { name = "Wyvern";          group = 1..1; hp =340..520; ws = 80; sp = 25; tier = "deep" }
        monster("tunnel_worm")        { name = "Tunnel Worm";     group = 1..1; hp =400..620; ws = 75; tier = "deep" }

        // ---- quest / boss (story-tagged) -------------------------------------
        monster("guardian")           { name = "Fire Shield Guardian"; group = 1..1; hp =250..400; ws = 85; sp = 10 }
        monster("norker")             { name = "Norker";          group = 1..1; hp = 90..150; ws = 60 }
        monster("voodoo_lord")        { name = "Voodoo Lord";     group = 1..1; hp =180..280; ws = 75; sp = 25 }
        monster("archdevil")          { name = "Archdevil";       group = 1..1; hp =320..480; ws = 90; sp = 35 }
        monster("kobold_horde")       { name = "Kobold Horde";    group = 6..8; hp = 30..50;  ws = 40 }
        monster("rainbow_demon")      { name = "Rainbow Demon";   group = 1..1; hp =150..250; ws = 95; sp = 10 }
        monster("long_dead_warrior")  { name = "Long-Dead Warrior";group= 1..1; hp =280..420; ws = 85; sp = 15 }
        monster("ogre")               { name = "Ogre";            group = 1..3; hp =180..280; ws = 70 }
        monster("ogre_lord")          { name = "Ogre Lord";       group = 1..1; hp =320..480; ws = 80 }
        monster("winged_thing")       { name = "Winged Thing";    group = 2..4; hp =120..200; ws = 70; sp = 20 }
        monster("jazzy")              { name = "Jazzy";           group = 1..1; hp =400..600; ws = 90; sp = 25 }
        monster("riding_hobgoblin")   { name = "Riding Hobgoblin";group = 2..4; hp =150..240; ws = 75 }
        monster("hobgoblin_chief")    { name = "Hobgoblin Chief"; group = 1..1; hp =300..460; ws = 85; sp = 10 }
        monster("hobgoblin_skeleton") { name = "Hobgoblin Skeleton";group=2..5; hp =100..170; ws = 65; sp = 10 }
        monster("undead_hobgoblin")   { name = "Undead Hobgoblin";group = 2..4; hp =140..220; ws = 70; sp = 15 }
        monster("erethor_priest")     { name = "Erethor Priest";  group = 1..3; hp =160..260; ws = 70; sp = 20 }
        monster("erethor")            { name = "Erethor";         group = 1..1; hp =600..900; ws = 80; sp = 10 }
        monster("wander_mage")        { name = "Wandering Mage";  group = 1..1; hp =200..320; ws = 70; sp = 30 }

        // ---- Castle Ydarg confrontation order --------------------------------
        monster("crypt_horror")       { name = "Crypt Horror";    group = 1..1; hp =400..600; ws = 85; sp = 25 }
        monster("night_gaunt")        { name = "Night Gaunt";     group = 1..1; hp =500..720; ws = 88; sp = 30 }
        monster("thyraga")            { name = "Thyraga";         group = 1..1; hp =640..900; ws = 90; sp = 35 }
        monster("liche")              { name = "Liche";           group = 1..1; hp =800..1100;ws = 92; sp = 40 }
        monster("baron_ydarg")        { name = "Baron Ydarg";     group = 1..1; hp =1200..1700;ws = 95; sp = 45 }

        // ---- Sysop backdoor (Karl's stalker) ---------------------------------
        monster("skagerberg_killer")  {
            name = "Skagerberg Killer"
            group = 1..1; hp = 2000..3500; ws = 100; sp = 20; immune = true
        }
    }

    // -------------------------------------------------------------------------
    // Spells. Each effect is a self-contained Script body. The legacy DSL had
    // 30..70 ops per spell; the bodies below capture the *shape* and key
    // mechanics, with verbatim mana / target / damage rules where possible.
    // -------------------------------------------------------------------------
    spells {
        mage("frost", slot = 1, baseCost = 8, name = "Frost Finger") {
            // Single-target cold beam. Damage = caster.level * (random 1..wis/3).
            engineCall("ask_target")  // -> binds `target`
            val dmg = letInt("dmg", state.member(v("caster")).level *
                                    random(int(1), state.member(v("caster")).wis / 3))
            text("spell.frost.beam")
            pause(800)
            mutate("monster.hp", "sub", v("dmg"))
            on(engineCheck("monster_dead", v("target"))) {
                text("spell.kill")
            }
        }
        mage("fire", slot = 2, baseCost = 25, name = "Fire Cloud") {
            // AoE: rolls per-monster damage.
            text("spell.fire.cloud")
            pause(1000)
            engineCall("for_each_monster",
                "body" to "spell.fire.tick")
        }
        mage("confusion", slot = 3, baseCost = 17, name = "Confusion") {
            text("spell.confusion.sweeps")
            pause(800)
            engineCall("set_battle_flag", "confusion")
        }
        mage("death", slot = 4, baseCost = 78, name = "Death") {
            engineCall("ask_target")
            text("spell.death.invoke")
            pause(1500)
            on(chance(60)) {
                engineCall("monster_kill", v("target"))
                text("spell.kill")
            } els {
                text("spell.death.resisted")
            }
        }
        mage("hold", slot = 5, baseCost = 43, name = "Hold") {
            engineCall("ask_target")
            text("spell.hold.bind")
            pause(800)
            engineCall("set_monster_flag", v("target"), "held")
        }
        mage("antimagic", slot = 6, baseCost = 28, name = "Anti-Magic") {
            text("spell.antimagic.shimmer")
            pause(800)
            engineCall("set_battle_flag", "antimagic")
        }

        priest("heal", slot = 1, baseCost = 8, name = "Healing") {
            engineCall("ask_party_target")  // binds `who`
            on(state.member(v("who")).isDead()) {
                text("spell.cant_heal_dead")
            } els {
                val gain = letInt("gain", random(int(8), state.member(v("caster")).wis / 4))
                state.member(v("who")).setField("hit_points",
                    state.member(v("who")).hp + v("gain"))
                engineCall("clamp_hp", v("who"))
                text("spell.heal.glow")
                pause(800)
            }
        }
        priest("restoration", slot = 2, baseCost = 20, name = "Restoration") {
            engineCall("ask_party_target")
            forEachMember { m -> m.setField("hit_points", m["health"]) }
            text("spell.restoration.full")
            pause(1000)
        }
        priest("rebirth", slot = 3, baseCost = 90, name = "Rebirth") {
            engineCall("ask_party_target")
            on(!state.member(v("who")).isDead()) {
                text("spell.cant_rebirth_alive")
            } els {
                state.member(v("who")).setField("dead", 0)
                state.member(v("who")).setField("hit_points",
                    state.member(v("who")).maxHp / 2)
                state.pl += 1
                text("spell.rebirth.return")
                pause(1500)
            }
        }
        priest("banish", slot = 4, baseCost = 85, name = "Banish") {
            engineCall("ask_target")
            text("spell.banish.invoke")
            pause(1500)
            on(chance(70)) {
                engineCall("monster_kill", v("target"))
                text("spell.banish.gone")
            } els {
                text("spell.banish.resisted")
            }
        }
        priest("revelation", slot = 5, baseCost = 40, name = "Revelation") {
            text("spell.revelation.eyes")
            pause(1000)
            engineCall("reveal_map_segment")
        }
        priest("teleport", slot = 6, baseCost = 35, name = "Teleport") {
            text("spell.teleport.shimmer")
            pause(800)
            engineCall("teleport_to_last_town")
        }
        priest("earthquake", slot = 7, baseCost = 55, name = "Earthquake") {
            text("spell.earthquake.rumble")
            pause(2000)
            on(engineCheck("in_dungeon")) {
                engineCall("collapse_dungeon")
                text("spell.earthquake.cave_in")
            } els {
                text("spell.earthquake.surface")
                engineCall("aoe_damage_all_foes", "amount" to int(99))
            }
        }

        // ---- Monster spells (4 + a melee fallback) ---------------------------
        monsterSpell("Frost Finger") {
            engineCall("pick_random_alive_player")
            val dmg = letInt("dmg", random(int(1), int(20)))
            text("spell.monster.frost")
            pause(600)
            mutate("player.hp", "sub", v("dmg"))
        }
        monsterSpell("Cloud of Fire") {
            text("spell.monster.fire_cloud")
            pause(1000)
            forEachMember { m ->
                val dmg = letInt("dmg", random(int(1), int(15)))
                m.setField("hit_points", m.hp - v("dmg"))
            }
        }
        monsterSpell("Hold Creature") {
            text("spell.monster.hold")
            pause(800)
            engineCall("set_player_flag", "held")
        }
        monsterSpell("Confusion") {
            text("spell.monster.confusion")
            pause(800)
            engineCall("set_player_flag", "confused")
        }
        monsterMelee {
            engineCall("pick_random_alive_player")
            on(engineCheck("monster_hit_roll")) {
                val dmg = letInt("dmg", random(int(1), int(9)))
                text("spell.monster.melee.hit")
                pause(400)
                mutate("player.hp", "sub", v("dmg"))
                on(engineCheck("player_dead")) { text("spell.monster.melee.kill") }
            } els {
                text("spell.monster.melee.miss")
            }
        }
    }

    // -------------------------------------------------------------------------
    // NPCs. Slot index from the legacy table is dropped; placement is by slug.
    // -------------------------------------------------------------------------
    npcs {
        // Random adventurers (slots 1..23 in legacy). They get assigned to
        // empty town huts at cold-start; the engine fills `npc_in_town` based
        // on the npc_slack budget (see balance).
        npc("nrim_to") { name = "Nrim'to"; job = "ranger" }
        npc("areth")   { name = "Areth";   job = "fighter" }
        npc("nevaak")  { name = "Nevaak";  job = "wizard" }
        npc("gorm")    { name = "Gorm";    job = "priest" }
        npc("bernak")  { name = "Bernak";  job = "thief" }
        npc("matias")  { name = "Matias";  job = "peasant" }
        npc("tybalt")  { name = "Tybalt";  job = "bard" }
        npc("romeo")   { name = "Romeo";   job = "drifter" }
        npc("werra")   { name = "Werra";   job = "half_orc" }
        npc("arenjun") { name = "Arenjun"; job = "half_elf" }
        npc("oskar")   { name = "Oskar";   job = "smith" }
        npc("harry")   { name = "Harry";   job = "merchant" }
        npc("marraga") { name = "Marraga"; job = "ranger" }
        npc("broth")   { name = "Broth";   job = "fighter" }
        npc("ebond")   { name = "Ebond";   job = "wizard" }
        npc("bolph")   { name = "Bolph";   job = "priest" }
        npc("gotharp") { name = "Gotharp"; job = "thief" }
        npc("ron")     { name = "Ron";     job = "peasant" }
        npc("kaal")    { name = "Kaal";    job = "bard" }
        npc("daniel")  { name = "Daniel";  job = "drifter" }
        npc("leander") { name = "Leander"; job = "half_orc" }
        npc("ithienn") { name = "Ithienn"; job = "half_elf" }
        npc("azaaza")  { name = "Azaaza";  job = "smith" }

        // Fixed placements -- one merchant, four innkeepers, four tavernkeepers,
        // two bargirls, one hermit, four wilderness merchants.
        npc("garth")    { name = "Garth";    job = "merchant";    fixed = true; towne = "thord"; building = "inn" }
        npc("hassan")   { name = "Hassan";   job = "innkeeper";   fixed = true; towne = "thord"; building = "inn" }
        npc("luqas")    { name = "Luqas";    job = "innkeeper";   fixed = true; towne = "ollk";  building = "inn" }
        npc("darth")    { name = "Darth";    job = "innkeeper";   fixed = true; towne = "wilor"; building = "inn" }
        npc("yog")      { name = "Yog";      job = "innkeeper";   fixed = true; towne = "murn";  building = "inn" }
        npc("niklas")   { name = "Niklas";   job = "tavernkeeper";fixed = true; towne = "thord"; building = "tavern" }
        npc("tokawa")   { name = "Tokawa";   job = "tavernkeeper";fixed = true; towne = "ollk";  building = "tavern" }
        npc("miyaki")   { name = "Miyaki";   job = "tavernkeeper";fixed = true; towne = "wilor"; building = "tavern" }
        npc("dargoth")  { name = "Dargoth";  job = "tavernkeeper";fixed = true; towne = "murn";  building = "tavern" }
        npc("deanna")   { name = "Deanna";   job = "bargirl";     fixed = true; towne = "thord"; building = "tavern"; gender = "female" }
        npc("jeanne")   { name = "Jeanne";   job = "bargirl";     fixed = true; towne = "wilor"; building = "tavern"; gender = "female" }
        npc("marcus")   { name = "Marcus";   job = "hermit";      fixed = true; x = 48; y = 7 }   // wilderness
        npc("alex")     { name = "Alex";     job = "merchant";    fixed = true /* wilderness vendor */ }
        npc("galdon")   { name = "Galdon";   job = "merchant";    fixed = true }
        npc("kosch")    { name = "Kosch";    job = "merchant";    fixed = true }
        npc("ufarsin")  { name = "Ufarsin";  job = "merchant";    fixed = true }

        // Quest-role -> npc bindings. The legacy game stored these as a map
        // and resolved with `npc_name_at_role` / `npc_loc_at_role`. Renaming
        // an NPC slug ripples here only.
        role("bandits_quest_giver",   "garth")
        role("rangers_quest_giver",   "nrim_to")
        role("crown_seer",            "broth")
        role("temple_of_thun_seer",   "harry")
        role("master_thief",          "gotharp")
        role("fighter_intro",         "areth")
        role("priest_with_book",      "gorm")
        role("silver_key_vendor",     "bernak")
        role("jazzy_ogre_trader",     "leander")
        role("wizard_robbed",         "nevaak")
        role("erethor_lute_bard",     "tybalt")
        role("ranger_brother",        "marraga")
        role("peasant_husband",       "ron")
        role("minstrel_cloak",        "kaal")
        role("wizard_three_tasks",    "ithienn")
        role("silver_key_smith",      "azaaza")
        role("hermit_troll_tooth",    "marcus")
    }

    // -------------------------------------------------------------------------
    // World: towns, buildings, terrain map, dungeons, ydarg.
    // -------------------------------------------------------------------------
    world {
        towns {
            town("thord") { name = "Thord"; x = 12; y = 11; tax = 7;  hasChurch = true;  townClass = "village" }
            town("ollk")  { name = "Ollk";  x = 48; y = 12; tax = 10; hasChurch = false; townClass = "village" }
            town("wilor") { name = "Wilor"; x = 43; y = 20; tax = 9;  hasChurch = true;  townClass = "city"    }
            town("murn")  { name = "Murn";  x = 55; y = 29; tax = 8;  hasChurch = false; townClass = "village" }
        }

        buildings {
            building("smith",    opensAt = 6, closesAt = 18)
            building("tavern",   opensAt = 6, closesAt = 1)   // wraps midnight
            building("merchant", opensAt = 6, closesAt = 18)
        }

        terrain {
            tile('m', "/\\", "white",   klass = "mountain")
            tile('w', "~~", "blue",     klass = "water")
            tile('s', "..", "yellow")
            tile('0', "TT", "white",    klass = "town")
            tile('1', "HH", "white",    klass = "hut")
            tile('f', "^^", "white")
            tile('d', "::", "red")
            tile('t', "%%", "magenta")
            tile('g', "gg", "green")
            tile('2', "CC", "white",    klass = "castle")
        }

        // -------- Dungeons. Random fill is declared once; quest dungeons use --
        // -------- explicit floors and let the fill cover the rest. ----------
        dungeons {
            // Generic random fill the legacy game used. Matches the legacy
            // ryl_random(1,9) bias by repeating weak_a/weak_b twice each; the
            // engine treats `weight` as the bias.
            randomFill("default") {
                use("encounter_weak_a", weight = 2)
                use("encounter_weak_b", weight = 2)
                use("encounter_deep")
                use("nothing_a")
                use("nothing_b")
                use("gold")
                use("gold_chest")
                use("torches")
                use("find_object")
            }

            // Hermit's quest. Quest-floor at the bottom; everything else random.
            dungeon("fire_shield_cave") {
                name = "Fire Shield Cave"
                levels = int(20)
                floor(19) { run("fire_shield_guardian") }
                floor(20) { run("fire_shield") }
            }

            // Three riddles spread across 18 floors; bracelets at the bottom.
            dungeon("thun_temple") {
                name = "Temple of Thun"
                levels = int(18)
                floor(4)  { run("thun_riddle1") }
                floor(8)  { run("thun_riddle2") }
                floor(12) { run("thun_riddle3") }
                floor(18) { run("bracelets") }
            }

            // Reebar's tomb -- the colour-square gauntlet 13..19, then nolrim.
            dungeon("reebar_tomb") {
                name = "Reebar's Tomb"
                levels = int(20)
                floor(12) { run("spectrum_message") }
                floors(13..19) { run("colour_riddle") }   // recurring per floor
                floor(20) { run("nolrim_tomb") }
            }

            // Master Crown -- 10 floors, boss at the bottom.
            dungeon("master_crown_dungeon") {
                name = "Master Crown Dungeon"
                levels = int(10)
                bossFloor("master_crown")
            }

            // Silver Sanctum -- 20 floors, planted note at the bottom.
            dungeon("silver_sanctum") {
                name = "The Silver Sanctum"
                levels = int(20)
                bossFloor("silver_sanctum")
            }

            // Hobgoblin stronghold -- six patrols on the way down, royal guards
            // and the chief at the bottom.
            dungeon("hobgoblin_stronghold") {
                name = "Hobgoblin Stronghold"
                levels = int(22)
                floors(3, 6, 9, 14, 17, 19) { run("hobgoblin_patrol") }
                floor(20) { run("royal_guard1") }
                floor(21) { run("royal_guard2") }
                floor(22) { run("hobgoblin_chief") }
            }

            // Ogre cave -- random level count (legacy spelled out 7 floors).
            // Tammy's prison stays armed until the rescue flag flips.
            dungeon("ogre_cave") {
                name = "Ogre Cave"
                levels = random(5..8)
                floor(6) { run("orc_nest") }
                floor(7) {
                    run("tammys_prison")
                    untilFlag("peasant_wife", "rescued")
                }
                gating("jazzy_ogre", "accepted")
            }

            // Erethor temple -- two priest ambushes plus the lute chamber.
            dungeon("erethor_temple") {
                name = "Erethor Temple"
                levels = int(12)
                floor(3)  { run("erethor_priests") }
                floor(7)  { run("erethor_priests") }
                floor(12) { run("lute_chamber") }
            }
        }

        ydarg {
            entry = 14 to 30
            mapSize = 5
            confrontationSequence("crypt_horror", "night_gaunt", "thyraga", "liche")
            roomDropTable {
                gateMax = 4; kindMax = 2
                weaponsRange = 1..14
                armorRange  = 36..42
                gearRange   = 39..46
                magicRange  = 100..111
            }
            pitfallTraps("ydarg.room.trap.arrows",
                         "ydarg.room.trap.poison",
                         "ydarg.room.trap.shurikens")
            stairItem(1, "fireshield",  rejectMsg = "no_have_sing")
            stairItem(2, "mastercrown", rejectMsg = "no_have_sing")
            stairItem(3, "jazzyfoot",   rejectMsg = "no_have_sing")
            stairItem(4, "bracelets",   rejectMsg = "no_have_plur")
            narration("conf_act1",
                "ydarg.conf.trapdoor_slam", "ydarg.conf.fools")
            narration("conf_act2",
                "ydarg.conf.corpses", "ydarg.conf.impress",
                "ydarg.conf.sphere",  "ydarg.conf.mages",
                "ydarg.conf.curses",  "ydarg.conf.trident",
                "ydarg.conf.commence")
            narration("conf_act3",
                "ydarg.conf.trident_falls", "ydarg.conf.not_over",
                "ydarg.conf.cloak",          "ydarg.conf.dust",
                "ydarg.conf.congrats")
            narration("conf_act4",
                "ydarg.conf.heroes", "ydarg.conf.signoff")
        }
    }

    // -------------------------------------------------------------------------
    // Strings. Shared text shards stay imported by id; use `text("foo.bar")`.
    // -------------------------------------------------------------------------
    strings {

        // ----- common.json5 -----
        str("common.yes", "(Y)es")
        str("common.no", "(N)o")

        // ----- cave.json5 -----
        str("cave.found_prompt", """
            You find a cave!

            (E)nter
            (L)eave
        """)
        str("cave.no_moves", """
            You don't have enough
            moves left to explore
            the cave...
        """)

        // ----- title.json5 -----
        str("title.header", "ADVENTURERS of RYLWEEN")
        str("title.prog_by", "Programming & Design by:")
        str("title.prog_name", "Marcus Lagergren")
        str("title.created_by", "      Created by:")
        str("title.creator_name", "Niklas Blomqvist")
        str("title.graphics_by", "      Graphics by:")
        str("title.graphics_name", "     Matias Larsson   v3")

        // ----- hunt.json5 -----
        str("hunt.who", "Who? (1-{party_max})")
        str("hunt.dead", "Dead.")
        str("hunt.not_ranger", "{pro_sub} is not a ranger.")
        str("hunt.too_few_moves", "Too few moves for that.")
        str("hunt.tries", """
            {who_name} tries
            to trap a rabbit...
        """)
        str("hunt.succeeds", """
            ...and succeeds.
            The party gains
            {fdplus} food rations.
        """)
        str("hunt.fails", "...but fails.")

        // ----- rest.json5 -----
        str("rest.not_tired", "Not tired.")
        str("rest.camp_intro", """
            Camp.

            Post a guard?
        """)
        str("rest.who", "Who? (1-{party_max})")
        str("rest.dead", "Dead.")
        str("rest.not_valid", "Not valid!")
        str("rest.stands_guard", """
            {guard_name} stands guard.
            {pro_sub} won't get any
            health back during
            this rest.
        """)
        str("rest.zzz", "Zzz...")
        str("rest.temp_low", "Low Temperature.")
        str("rest.temp_very_low", "Very low temperature.")
        str("rest.no_gear_5", """
            No good equipment,
            Party loses 5 health.
        """)
        str("rest.no_gear_10", """
            No good equipment,
            Party loses 10 health.
        """)
        str("rest.ambush", "Ambush!!!")
        str("rest.surprise_kill", """
            {victim_name} is dead
            before you can
            react!!!
        """)
        str("rest.well_rested", """
            You wake up 8 hours
            later. Well rested.
        """)

        // ----- terrain.json5 -----
        str("terrain.plainland", """
            The rich green plainland
            of Rylween stretches far
            in all the directions.
        """)
        str("terrain.hut", "You have found a hut.")
        str("terrain.towne", "You are close to a towne.")
        str("terrain.foothills", """
            Small foothills make your
            progress more arduous.
        """)
        str("terrain.desert", """
            You are in a hot and dry
            desert. The sun fries
            you mercilessly.
        """)
        str("terrain.swamp", """
            You are in the swampy
            marshlands of Rylween.
            Bubbles of gas rise to
            the surface where they
            burst and emit a foul
            smelling odour.
        """)
        str("terrain.forest", """
            The ancient forest is
            very tranquil. A soft
            breeze rustles the
            leaves of mighty oaks.
        """)
        str("terrain.castle", """
            You stand outside the
            castle Zadraz. The
            black steel building
            seems to want to swallow
            you whole.
        """)
        str("terrain.blank", """
        """)

        // ----- subs.json5 -----
        str("subs.faint", """
            You faint from lack
            of well needed sleep.
        """)
        str("subs.who_prompt", "Who? (1-{party_max})")
        str("subs.dead", "Dead.")
        str("subs.no_room", "No room!")
        str("subs.lose_game.flavor", """
            Alas, your party of
            adventurers have all
            gone to meet their
            maker...
        """)
        str("subs.food_handler.tired", "You begin to feel tired")
        str("subs.food_handler.exhausted", "You are exhausted!!!")
        str("subs.food_handler.weak", """
            You begin to feel weak
            from lack of sleep.
        """)
        str("subs.food_handler.starved", """
            {name} starved
            to death!!!
        """)
        str("subs.equipment.no_weapon", "{pronoun} has no weapon!")
        str("subs.equipment.no_armor", "{pronoun} has no armor!")
        str("subs.equipment.removed", "{object} removed.")
        str("subs.equipment.invalid_weapon", "Not a valid weapon.")
        str("subs.equipment.invalid_armor", "Not a valid armor.")
        str("subs.equipment.changed_weapon", "Weapon changed.")
        str("subs.equipment.changed_armor", "Armor changed.")
        str("subs.drop.you_drop", """
            You drop the
            {object}.
        """)
        str("subs.drop.donkey_food_cap", """
            You are forced to
            leave some of your
            food behind...
        """)
        str("subs.drop.nothing", "Nothing to drop.")
        str("subs.drop.aborted", "Aborted.")
        str("subs.quaff.prompt", "Quaff what?")
        str("subs.quaff.invalid", "Not valid.")
        str("subs.quaff.gulp", "Gulp!")
        str("subs.spell.mage_combat_only", """
            Sorry. Mage spells are
            combat-only.
        """)
        str("subs.spell.wrong_class", "Wrong class.")

        // ----- spell.json5 -----
        str("spell.not_enough_pts", "Not enough spell pts!")
        str("spell.priest.heal.target_dead", "Dead.")
        str("spell.priest.heal.heals", "{caster} heals")
        str("spell.priest.heal.target", "{target}")
        str("spell.priest.heal.points", "for {x} hit points...")
        str("spell.priest.restoration.heals", "{caster} heals")
        str("spell.priest.restoration.target", "{target}")
        str("spell.priest.restoration.fully", "fully...")
        str("spell.priest.rebirth.alive", "{target} is alive and")
        str("spell.priest.rebirth.healthy", "healthy.")
        str("spell.priest.rebirth.eyelids", "{target}'s eyelids")
        str("spell.priest.rebirth.flicker", "flicker, and finally")
        str("spell.priest.rebirth.opens_eyes", "{tgt_subj} opens {tgt_poss} eyes!")
        str("spell.priest.revelation.nothing1", "There is nothing")
        str("spell.priest.revelation.nothing2", "special near.")
        str("spell.priest.revelation.special1", "Something special is")
        str("spell.priest.revelation.special2", "near!")
        str("spell.priest.revelation.you_find", "You find a")
        str("spell.priest.revelation.object_dot", "{oc_name}.")
        str("spell.priest.revelation.take_prompt", "(T)ake it")
        str("spell.priest.revelation.leave_prompt", "(L)eave it")
        str("spell.priest.revelation.added_blank", """
        """)
        str("spell.priest.revelation.added_ok", "Ok.")
        str("spell.priest.revelation.cant_carry", "You can't carry more.")
        str("spell.priest.revelation.dungeon_only1", "This spell can be used in")
        str("spell.priest.revelation.dungeon_only2", "dungeons only, where there")
        str("spell.priest.revelation.dungeon_only3", "is a limited radius for")
        str("spell.priest.revelation.dungeon_only4", "the caster to enchant.")
        str("spell.priest.banish.thunder", """
            There is a deafening clap
            of thunder, and you hear
            beings scream in agony
            in the dungeon below...
        """)
        str("spell.priest.banish.raises_hands", "{caster} raises {csr_poss} hands.")
        str("spell.priest.banish.divine_power", "{csr_subj} gathers divine power")
        str("spell.priest.banish.between_palms", "between {csr_poss} palms, and")
        str("spell.priest.banish.suddenly_clasps", "suddenly clasps them.")
        str("spell.priest.banish.over_head", "together over {csr_poss} head.")
        str("spell.priest.banish.no_target1", "There is no particular")
        str("spell.priest.banish.no_target2", "target for this banishing.")
        str("spell.priest.banish.no_target3", "This spell is meant for")
        str("spell.priest.banish.no_target4", "use in dungeons and caves.")
        str("spell.priest.teleport.satchel", """
            from his satchel and
            uses a special instrument
            to scratch the direction
        """)
        str("spell.priest.teleport.compass", """
            Teleport :

               8
             4-+-6
               2
        """)
        str("spell.priest.teleport.how_far", """
            How many miles do you
            want to travel? (1-9)
        """)
        str("spell.priest.teleport.arrived", """
            You appear to be
            somewhere else.
        """)
        str("spell.priest.teleport.carefully", "{caster} carefully")
        str("spell.priest.teleport.prepares", "prepares the ritual of")
        str("spell.priest.teleport.spends", "Teleportation. {csr_subj} spends")
        str("spell.priest.teleport.chanting", "an hour chanting mantras")
        str("spell.priest.teleport.candles", "and burning blue candles.")
        str("spell.priest.teleport.picks_slab", "{csr_subj} picks up a stone slab")
        str("spell.priest.teleport.wants_you", "{csr_subj} wants you to teleport")
        str("spell.priest.teleport.to", "to.")
        str("spell.priest.earthquake.incantation", """
            three syllabels in an
            archaic tongue...
        """)
        str("spell.priest.earthquake.eruption", """
            and the ground cracks
            open!
        """)
        str("spell.priest.earthquake.utters", "{caster} utters")
        str("spell.priest.earthquake.roof_cave1", "The roof would cave")
        str("spell.priest.earthquake.roof_cave2", "in on you!")

        // ----- driv.json5 -----
        str("driv.moves.out_of_moves", """
            You are out of moves
            for today.
        """)
        str("driv.menu.out_of_moves", """
            (S)ave and Quit
            (Q)uit (no save)
        """)
        str("driv.menu.owner", "{name}'s copy.")
        str("driv.menu.new_party", "N. Create New Party")
        str("driv.menu.continue", "C. Continue Saved Game")
        str("driv.menu.terminate", "T. Terminate Party")
        str("driv.menu.common_s", "S. View Currect Scores")
        str("driv.menu.common_h", "H. View Hall of Fame")
        str("driv.menu.common_d", "D. View Documentation")
        str("driv.menu.common_x", "X. Exit")
        str("driv.menu.no_game", "No game available.")
        str("driv.save.saving", "Saving position...")
        str("driv.save.done", "Done.")
        str("driv.quit.farewell", "Fare thee well.")
        str("driv.quit.confirm", """
            Do you really want
            to quit?
        """)
        str("driv.move.pass", "Pass...")
        str("driv.move.no_moves", "No moves left!")
        str("driv.view.who", "Who? (1-{party_max}/Q/A)")
        str("driv.drop.another", """
            Do you want to drop
            another object? (y/N)
        """)
        str("driv.journal.header", """
            ============================= TRAVELLER'S JOURNAL ==============================
        """)
        str("driv.more", """
            ${WHITE}<MORE>
        """)
        str("driv.init.no_map", "Bloody 'ell! Unable to find map!!!")
        str("driv.init.first_day", "First Game of the Day... Updating... Please Wait...")
        str("driv.statusline.time.dawn", "TIME: ${YELLOW}DAWN${GREEN}      ")
        str("driv.statusline.time.midday", "TIME: ${YELLOW}MIDDAY${GREEN}    ")
        str("driv.statusline.time.twilight", "TIME: ${YELLOW}TWILIGHT${GREEN}  ")
        str("driv.statusline.time.midnight", "TIME: ${YELLOW}MIDNIGHT${GREEN}  ")
        str("driv.statusline.status.hungry", "ST: ${MAGENTA}HUNGRY  ")
        str("driv.statusline.status.starving", "ST: ${RED}STARVING${GREEN} ")
        str("driv.statusline.status.ok", "ST: ${YELLOW}OK    ")
        str("driv.struggle.over", """
            The struggle is over.
            ${red("{party_name}")} have
        """)
        str("driv.struggle.conclusion", """
            defeated Baron Ydarg.
            Please ask the SysOp
            to reset Rylween
        """)
        str("driv.scores.verbose_q", """
            Do you want to see the
            verbose listing? (y/N)
        """)
        str("driv.docs.capture", """
            You may want to
            turn on your capture
            buffer now...
        """)
        str("driv.create_new.manual_reminder", """
            Please read the manual
            before starting play.
            You will understand
            _very_ little of the
            game otherwise...
        """)
        str("driv.entry.exhausted", """
            Sorry! You can't enter
            the realm again today.
            You only have {turns} turns
            per day to use your
            moves on.
        """)
        str("driv.terminate.ugh", """
            Really? C'mon now!
            It can't be that bad!
            Your attitude sucks,
            my friend!
        """)
        str("driv.terminate.confirm", "Kill Party? (y/N)")
        str("driv.terminate.editing_realm", "Editing Realm...")
        str("driv.terminate.editing_journal", "Editing Journal...")
        str("driv.terminate.deleted", """
            Your party has been
            deleted. Please play
            again.
        """)
        str("driv.create.header", "Party Creation")
        str("driv.create.full", """
            Sorry. Maximum number of
            players reached. Play
            again later on.
        """)
        str("driv.create.name_q", """
            What is the name of your
            party?
        """)
        str("driv.create.reroll", "Reroll (y/N) ")
        str("driv.create.gender_q", """
            What gender is
            {name}?
        """)
        str("driv.create.gender_opts", """
            1. Male
            2. Female
        """)
        str("driv.create.race_q", """
            What race is
            {name}?
        """)
        str("driv.create.race_opts", """
            1. Human  2. Dwarf
            3. Elf    4. Halfling
        """)
        str("driv.create.class_menu", """
            Pick a class for this
            character...

            1. Fighter 2. Thief
            3. Mage    4. Priest
            5. Bard    6. Ranger
        """)
        str("driv.create.weapon_skill_low", "Weap. skill must be > 50.")
        str("driv.create.wisdom_low", "Wisdom must be > 50.")
        str("driv.create.charisma_low", "Charisma must be > 50.")
        str("driv.create.saving", "Saving...")
        str("driv.create.stats_page", """
            Stats for char: {char_num}

            Weapon Skill : {weapon_skill}
            Health       : {health}
            Wisdom       : {wisdom}
            Charisma     : {charisma}
        """)
        str("driv.create.name_prompt", "Give Character {char_num} a name!")
        str("driv.party.counts", """
            {torches} torches.
            {rations} rations.
            {gold} gold.
        """)
        str("driv.party.has_shield", "You have the Shield.")
        str("driv.party.has_bracelets", "You have the Bracelets.")
        str("driv.party.has_crown", "You have the Crown.")
        str("driv.party.has_foot", "You have the Foot.")
        str("driv.party.has_key", "You have the Key.")
        str("driv.show.first_page", """
            Name      : {name}
            Race      : {race}
            Class     : {class}

            Skill     : {skill}
            Hp/H      : {hp}/{health}
            Wisdom    : {wisdom}
            Charisma  : {charisma}
            Magic     : {magic}
        """)
        str("driv.show.second_page", """
            Level     : {level}
            Weapon    : {weapon}
            Armor     : {armor}

            Status    : {status}

            Exp       : {exp}
        """)
        str("driv.show.status_dead", "Dead.")
        str("driv.show.status_alive", "Alive.")
        str("driv.help.p1", """
            C = Camp
            E = Enter
            H = Hunt for Food
            J = View Journal
            L = Leave (Discard) Item
            P = Put down weapon
        """)
        str("driv.help.p2", """
            Q = Quaff Potion
            R = Ready Armor
            S = Save Game
            T = Take off armor
            V = View Party
            W = Wield Weapon
        """)
        str("driv.help.p3", """
            X = Quit Game (No Save)
            Z = Cast Spell
            ! = Redraw Screen
            ? = View this help
                8     Move North,
              4-+-6   South, East &
                2     West
        """)

        // ----- battle.json5 -----
        str("battle.encounter.banner", "ENCOUNTER!")
        str("battle.encounter.you_face", "You face")
        str("battle.encounter.still_face", "You still face")
        str("battle.run.coward", """
            Coward. You won't get
            any EXP for this battle.
        """)
        str("battle.run.desperate_fail", """
            You desperately try to
            get away, but fail!
        """)
        str("battle.run.fail_away", "You fail to get away!")
        str("battle.run.escaped", "You escaped...")
        str("battle.item.magic_immune", """
            The magic item doesn't seem
            to work on this enemy!!!
        """)
        str("battle.item.throws_the", "{caster} throws the")
        str("battle.item.sparks_bump", """
            at the enemy. It
            breaks before them in
            a rain of sparks.
            They bump into each
            other as they try to
            see you.
        """)
        str("battle.item.light_blind", """
            gem at the enemy.
            It breaks, releasing
            a brilliant cloud
            of light which completely
            surrounds them. As the
            light fades, your enemies
            are blind.
        """)
        str("battle.item.battle_cry", """
            With a raging battle
            cry, you attack the
            mortally wounded foes!
        """)
        str("battle.item.ghost_axe.wounds", """
            It wounds them severely
            as it spins around,
            wielded by hands you
            cannot see!
        """)
        str("battle.item.flute_of_madness.played", """
            {caster} plays the
            Flute of Madness.
            It produces a shrill,
            fragile sound, and breaks.
            Nothing happens.
        """)
        str("battle.item.frost_wand.chills", "{caster} chills {monster_name}")
        str("battle.item.frost_wand.damage_pts", "for {dmg} damage pts.")
        str("battle.item.drums_of_death.summons_death", """
            {caster} summons death
            to take a {monster_name}
            away...
        """)
        str("battle.item.fire_wand.burns", "{caster} burns {monster_name}")
        str("battle.item.fire_wand.points", "for {dmg} pts!")
        str("battle.item.kaels_axe.thrown", """
            {caster} throws Kael's
            axe at the enemy!
        """)
        str("battle.item.holy_handgrenade.lobbed", """
            {caster} pulls the
            holy pin, and lobbeth
            the holy handgrenade
            at the foes...
        """)
        str("battle.item.holy_handgrenade.ka_blam", "KA-BLAM!")
        str("battle.item.staff_of_healing.fully_healed", """
            {target_name} is fully
            healed.
        """)
        str("battle.item.gem_of_resurrection.opens_eyes", "{target_name} opens {target_possessive} eyes!")
        str("battle.item.gem_of_resurrection.not_dead", "{target_name} is not dead.")
        str("battle.prompt.on_whom", "On Whom? (1-{party_max})")
        str("battle.prompt.who", "Who?")
        str("battle.prompt.fight", "(F)ight")
        str("battle.prompt.run", "(R)un")
        str("battle.prompt.blood_flow", "Blood shall flow!")
        str("battle.prompt.what_now", "What now?")
        str("battle.prompt.attack", "(A)ttack")
        str("battle.prompt.defend", "(D)efend")
        str("battle.prompt.use_item", "(U)se Item")
        str("battle.prompt.battle_cry", "(B)attle Cry")
        str("battle.prompt.cast_spell", "(C)ast Spell")
        str("battle.prompt.use_what", "Use what?")
        str("battle.prompt.not_possible", "Not possible.")
        str("battle.spell.morale_quest", """
            The party feels their
            spirits grow. You are
            all stronger than
            before.
        """)
        str("battle.spell.party_healed", """
            The party is fully
            healed!
        """)
        str("battle.spell.fizzle", """
            The spell fizzles!
            this creature is immune
            to ordinary magic!!!
        """)
        str("battle.spell.not_enough_magic", "Not enough magic!")
        str("battle.spell.utters_word", "{caster} utters a word.")
        str("battle.spell.mirror", """
            Your images wavers and
            blurs in front of your
            enemies
        """)
        str("battle.spell.hold_enemy", """
            The enemies freeze dead
            in their tracks.
        """)
        str("battle.spell.silence", """
            The enemy is now
            stopped from using magic.
            against you this round.
        """)
        str("battle.spell.abort", "7. Abort")
        str("battle.spell.annihilate.resist", """
            Through INCREDIBLE
            resistance, the enemy
            is still alive, badly
            wounded.
        """)
        str("battle.spell.annihilate.kill", """
            The enemy is wiped
            straigt off the surface
            of the earth!
        """)
        str("battle.spell.frost.chills", "{caster} chills the")
        str("battle.spell.frost.damage", "opponent for {dmg}")
        str("battle.spell.frost.damage_pts", "damage pts.")
        str("battle.spell.fire.burns", "{caster} burns {monster_name}")
        str("battle.spell.fire.points", "for {dmg} pts!")
        str("battle.cry.off_guard", """
            (You manage to scare your
             enemies enough to get
             them off guard.)
        """)
        str("battle.cry.hi_yaaaa", "HI-YAAAA!")
        str("battle.rest.gain_hits", """
            You gain some hits pts
            as you rest this round.
        """)
        str("battle.defend.stance", """
            You put up a defensive
            stance...
        """)
        str("battle.attack.hits_for", "{attacker} hits for")
        str("battle.attack.damage_pts", "{dmg} damage pts!")
        str("battle.attack.misses", "{attacker} misses!")
        str("battle.victory.bested", """
            Thou hast bested
            thy foes.
        """)
        str("battle.victory.each_survivor", "Each survivor gets")
        str("battle.victory.exp_pts", "{experience} experience pts")
        str("battle.victory.and_gold", "and {gold} gold pieces.")
        str("battle.victory.you_find_a", "You find a")
        str("battle.victory.found_obj", "{object_name}.")
        str("battle.monster.casts", "{monster_name} casts")
        str("battle.monster.hold_player", """
            You realize with horror
            that you cannot move!
        """)
        str("battle.monster.confusion", """
            You are almost blinded
            by a colorful haze.
        """)
        str("battle.monster.player_dies", "${red("{target_name} DIES!")}")
        str("battle.monster.attacks", "{monster_name} attacks")
        str("battle.monster.target_excl", "{target_name}!")
        str("battle.monster.and_hits_for", "And hits for")
        str("battle.monster.damage_pts", "{dmg} damage pts!!!")
        str("battle.monster.melee_dies", "${red("{target_name} DIES!!!")}")
        str("battle.monster.blow_misses", "Blow misses!")
        str("battle.monster.frost.name", "Frost Finger")
        str("battle.monster.frost.on_target", "on {target_name}!")
        str("battle.monster.frost.does_pts", "and does {dmg} pts")
        str("battle.monster.frost.of_damage", "of damage!")
        str("battle.monster.fire.name", "Cloud of Fire!")
        str("battle.monster.fire.burnt_for", "{target_name} is burnt for")
        str("battle.monster.fire.dmg_pts", "{dmg} damage pts!")
        str("battle.monster.hold.name", "Hold Creature!")
        str("battle.monster.confuse.name", "Confusion!")
        str("battle.party.held", "PARTY IS HELD!")
        str("battle.expgain.gains", "{name} gains >{levels}<")
        str("battle.expgain.exp_levels", "experience levels.")
        str("battle.killed", "...KILLED!")

        // ----- ydarg.json5 -----
        str("ydarg.ruin", """
            The once so mighty
            castle Zadraz is now
            nothing but a ruin.
            You have nothing else
            to do here.
        """)
        str("ydarg.approach", """
            A black and foreboding
            castle looms over the
            horizon. Its cold, black
            stone walls are decorated
            with complex symbols and
            archaic runes of long
            forgotten language.
        """)
        str("ydarg.jaara_form", """
            A shape of white mist
            slowly thickens to human
            shape. A transparent image
            of a beautiful woman
            forms in front of you.
        """)
        str("ydarg.jaara_speech", """
            - Do not be afraid,
            adventurers. I am Jaara,
            guardian of the gate.
            Baron Ydarg has imprisoned
            me here forever. I can't
            hurt you, just stop you
            from entering if you don't
            have the Silver Key!
        """)
        str("ydarg.no_key", """
            - You don't have the
            Key. The gate will
            remain sealed.
        """)
        str("ydarg.key_accepted", """
            You show the Silver Key to
            the ghost. Her eyes widen,
            as she sees it. She smiles
            and waves her hand towards
            the drawbridge. It falls
            to the ground with a
            resounding KRASH. Jaara
            slowly fades away until
            nothing remains.
        """)
        str("ydarg.enter", "You enter.")
        str("ydarg.door_slams", """
            The first door you enter
            slams shut behind you!
        """)
        str("ydarg.room.monster", """
            You are spotted by
            monsters!
        """)
        str("ydarg.room.trap.springs", """
            {victim} springs a
            trap!!!
        """)
        str("ydarg.room.trap.tries_disarm", """
            {thief} tries to
            disarm it...
        """)
        str("ydarg.room.trap.disarm_succeed", "AND SUCCEEDS!")
        str("ydarg.room.trap.disarm_fail", "BUT FAILS!")
        str("ydarg.room.trap.arrows", """
            Arrows speed through
            the air and hits the
            party!
        """)
        str("ydarg.room.trap.poison", """
            Poison needles shoot
            out from the floor!
        """)
        str("ydarg.room.trap.shurikens", """
            A volley of shurikens
            is hurled from a hatch
            in the wall!
        """)
        str("ydarg.room.treasure.find", """
            You find a
            {object}.
        """)
        str("ydarg.room.treasure.menu", """
            (T)ake it
            (L)eave it
        """)
        str("ydarg.room.treasure.full", "You can't carry more.")
        str("ydarg.room.treasure.taken", "Ok.")
        str("ydarg.room.stairs.intro", """
            You stand before the
            stairs to the next level.
        """)
        str("ydarg.room.stairs.mage_offer", """
            A mage imprisoned in a
            fiery sphere offers to
            open trapdoor at the top
            of the stairs if you
            give him an item of power!
            He also offers to teleport
            you out of the castle,
            for free.
        """)
        str("ydarg.room.stairs.menu", """
            Choose:

            (T)eleport Out
            (G)ive him an item
            (L)eave
        """)
        str("ydarg.room.stairs.leave", "You take the nearest exit!")
        str("ydarg.room.stairs.teleport_vortex", """
            The world fades into a
            vortex of agonizing
            vertigo...
        """)
        str("ydarg.room.stairs.teleport_outside", "And you are outside...")
        str("ydarg.room.stairs.give_menu", """
            Give him what?

            1. The Fire Shield
            2. The Master Crown
            3. The Foot of Jazzy
            4. The Bracelets of Thun
            5. Nothing
        """)
        str("ydarg.room.stairs.no_have_sing", "You don't have it!")
        str("ydarg.room.stairs.no_have_plur", "You don't have them!")
        str("ydarg.room.stairs.mage_grateful", """
            The sphere fades away
            and the mage steps out.
            He is grateful. Before
            he teleports away, he
            casts an OPEN spell on the
            trapdoor above and speaks:
            - I will aid thee in the
            final battle!
        """)
        str("ydarg.nav.coords", "You are at {xl}x{yl}.")
        str("ydarg.nav.level", "You are on level {lvl}.")
        str("ydarg.nav.floor_1", "(Ground Level)")
        str("ydarg.nav.floor_2", "(First Floor)")
        str("ydarg.nav.floor_3", "(Base of Tower)")
        str("ydarg.nav.floor_4", "(Top of Tower)")
        str("ydarg.nav.north", """
            8  North  
            |
        """)
        str("ydarg.nav.no_north", """
        """)
        str("ydarg.nav.east_west", " 4 -+- 6  East/West")
        str("ydarg.nav.east", "    +- 6  East")
        str("ydarg.nav.west", " 4 -+     West")
        str("ydarg.nav.south", """
            |
            2  South  
        """)
        str("ydarg.nav.go_north", "North.")
        str("ydarg.nav.go_south", "South.")
        str("ydarg.nav.go_east", "East.")
        str("ydarg.nav.go_west", "West.")
        str("ydarg.nav.view_who", "Who? (1-{party_max}/Q/A)")
        str("ydarg.conf.trapdoor_slam", """
            The last trapdoor
            slams shut behind you.
            You are high above the
            forest, on the roof
            of this fortress.
            Suddenly, a voice speaks:
        """)
        str("ydarg.conf.fools", """
            - Fools! You have gone
            long to come this far, but
            here ends your journey!

            The night sky above lights
            up with green flashes, and
            Ydargs minions rush
            to attack you!
        """)
        str("ydarg.conf.corpses", """
            Corpses of the nightmare
            terrors are strewn all
            around. A dark cloaked
            figure appears before you.
            He takes a broad step over
            the dead and dying, and
            gazes at you with cold
            glowing green eyes.
        """)
        str("ydarg.conf.impress", """
            - So. You have completed
            the battle test. I must
            confess that you impress
            me. But now...

            (He waves a pointed stick
            and energy begins to form
            in the air before you.)
        """)
        str("ydarg.conf.sphere", """
            A glowing energy sphere
            appears, and at a gesture
            from Ydarg's stick, it
            hurtles towards you!!!
        """)
        str("ydarg.conf.mages", """
            Suddenly! You feel someone
            else near. {p1_name} turns
            around and discovers the
            four grey-clad mages!
            They simultaneously raise
            their hands, and the
            sphere bounces over you
            and is absorbed by their
            hands!
        """)
        str("ydarg.conf.curses", """
            - CURSES! Ydarg growls.
            - So this is what has come
            to! Well, you got here by
            aid of cold steel, and
            that is the way you shall
            perish!
        """)
        str("ydarg.conf.trident", """
            He pulls a huge trident
            from his robe and utters
            a single syllable. Without
            warning he appears a foot
            before you!!!
        """)
        str("ydarg.conf.commence", "Let the battle commence!")
        str("ydarg.conf.trident_falls", """
            Ydarg's trident clatters
            to the ground before you.
            He shudders and falls over.
            As you rush to kill him
            he screams:
        """)
        str("ydarg.conf.not_over", """
            - It is not over! I shall
            see you in hell! In
            another world! A time
            to come! You have tainted
            Zadraz with your ancient
            items, but I will triumph
            again!!! Anew...
        """)
        str("ydarg.conf.cloak", """
            His cloak collapses, and
            when you tear it away,
            you reveal nothing but...
        """)
        str("ydarg.conf.dust", "dust...")
        str("ydarg.conf.congrats", """
            ${red("CONGRATULATIONS!")}

            You have banished Ydarg
            from this dimension.
            Please ask your SysOp to
            reset the game.
        """)
        str("ydarg.conf.heroes", """
            Be ready for:
            ${red("HEROES OF RYLWEEN")}
            - The second coming.
            Soon online at a BBS
            near you!
        """)
        str("ydarg.conf.signoff", """
            See Ya!!!

                 /Marcus
        """)

        // ----- speak.json5 -----
        str("speak.no_npc", "Noone is here!")
        str("speak.recognize_pre", "{npc_name} nods as")
        str("speak.recognize_he", "he recognizes you.")
        str("speak.recognize_she", "she recognizes you.")
        str("speak.tells_you_pre", "{npc_name} tells you:")
        str("speak.chatting", """
            Chatting with
            {npc_name}:
        """)
        str("speak.menu", """
            (C)hitchat
            (Q)uestion
            (R)umors
            (B)ye
        """)
        str("speak.no_rumour", """
            - Sorry. There's
            nothing new I can
            tell you.
        """)
        str("speak.question", """
            (L)odging
            (M)agic
            (P)eople
            (N)othing
        """)
        str("speak.buy_nothanks", """
            (B)uy it
            (N)o thanks
        """)
        str("speak.chit.added_blank", """
        """)
        str("speak.chit.well_f", """
            - You look well today,
              miss...
        """)
        str("speak.chit.well_m", """
            - You look well today,
              Sir...
        """)
        str("speak.chit.weather", """
            - Nice weather we're
              having...
        """)
        str("speak.chit.health_f", """
            - Is all well with
              you, miss?
        """)
        str("speak.chit.health_m", """
            - Is all well with
              you, Sir?
        """)
        str("speak.chit.reply.well.jmerchant", """
            - Thanks, and the same
            to you!
        """)
        str("speak.chit.reply.well.jranger", """
            - I do? Really? Are
            you gay or smth'n?
        """)
        str("speak.chit.reply.well.jfighter", """
            - Thank you. My health
            is slowly returning
            after a hard battle.
        """)
        str("speak.chit.reply.well.jwizard", "- Aha?")
        str("speak.chit.reply.well.jpriest", "- You too, my son.")
        str("speak.chit.reply.well.jthief", """
            - What? No! Sccch!
            They can hear you.
        """)
        str("speak.chit.reply.well.jpeasant", "- Thank you! You too.")
        str("speak.chit.reply.well.jbard", """
            - That inspires me.
            I'll compose a song
            about you!
        """)
        str("speak.chit.reply.well.jdrifter", "- YEAH? Take that!")
        str("speak.chit.reply.well.jhalf_orc", """
            - ME NOT LOOK WELL!
            ME BIG AND UGLY!
        """)
        str("speak.chit.reply.well.jhalf_elf", "- Oh...")
        str("speak.chit.reply.well.jsmith", "- I thank thee, my lad!")
        str("speak.chit.reply.well.jbargirl", "- You too, honey!")
        str("speak.chit.reply.well.jinnkeeper", "- The same to you.")
        str("speak.chit.reply.well.jtavernkeeper", """
            - You've had to much
            to drink, boy?
        """)
        str("speak.chit.reply.well.jhermit", """
            - It is written in the
            scrolls of Skelos...
        """)
        str("speak.chit.reply.weather.jmerchant", "- I think it will rain.")
        str("speak.chit.reply.weather.jranger", """
            - Not a fine weather for
            tracking, mind you!
        """)
        str("speak.chit.reply.weather.jfighter", """
            - Yeah! But my armor
            gets quite rusty.
        """)
        str("speak.chit.reply.weather.jwizard", "- Really?")
        str("speak.chit.reply.weather.jpriest", """
            - The gods have been
            kind to us, my son!
        """)
        str("speak.chit.reply.weather.jthief", """
            - Perfect weather
            for stealing!
        """)
        str("speak.chit.reply.weather.jpeasant", """
            - Yeah, the crops grow
            better than ever.
        """)
        str("speak.chit.reply.weather.jbard", """
            - This rain really
            inspires me. (He
            starts singing).
        """)
        str("speak.chit.reply.weather.jdrifter", "- Hehehehe...")
        str("speak.chit.reply.weather.jhalf_orc", "- YEAH! NICE'N SLIMY!")
        str("speak.chit.reply.weather.jhalf_elf", "- I find it depressing.")
        str("speak.chit.reply.weather.jsmith", "- Well, kinda good.")
        str("speak.chit.reply.weather.jbargirl", """
            - Let's not talk about
            the weather, honey?
        """)
        str("speak.chit.reply.weather.jinnkeeper", "- Well. Can't complain.")
        str("speak.chit.reply.weather.jtavernkeeper", "- Want a drink, sonny?")
        str("speak.chit.reply.weather.jhermit", """
            - Yes, a day like this
            is destined to come!
        """)
        str("speak.chit.reply.health.jmerchant", "- All's well, thanks.")
        str("speak.chit.reply.health.jranger", "- I'm fine.")
        str("speak.chit.reply.health.jfighter", "- Yep.")
        str("speak.chit.reply.health.jwizard", "- Health... yes.")
        str("speak.chit.reply.health.jpriest", "- All is well, my son.")
        str("speak.chit.reply.health.jthief", "- Yeh, quite nice.")
        str("speak.chit.reply.health.jpeasant", "- Are you a fag?")
        str("speak.chit.reply.health.jbard", """
            - Yes. I will sing
            a song of health
            for you!
        """)
        str("speak.chit.reply.health.jdrifter", """
            (He chuckles under
            his breath).
        """)
        str("speak.chit.reply.health.jhalf_orc", "- ME JUST FINE!")
        str("speak.chit.reply.health.jhalf_elf", "- Very well, thanks.")
        str("speak.chit.reply.health.jsmith", "- Fine, laddie!")
        str("speak.chit.reply.health.jbargirl", "- Sure, honey!")
        str("speak.chit.reply.health.jinnkeeper", "- Well...")
        str("speak.chit.reply.health.jtavernkeeper", """
            - Just fine. Do you
            want something to
            drink?
        """)
        str("speak.chit.reply.health.jhermit", "- Oh. Meneth Sabaoth.")
        str("speak.people.jmerchant", """
            - Well, I don't know
            that many people.
        """)
        str("speak.people.jranger", "- No I'm a lone ranger.")
        str("speak.people.jfighter", """
            - Well, I met this
            merchant who seeks
            revenge on a band of
            bandits.
        """)
        str("speak.people.jwizard", "- OH! Well... not really.")
        str("speak.people.jpriest", """
            - I've heard of a hermit
            who lives in the swamps
            by Ollk. Perhaps he can
            help you.
        """)
        str("speak.people.jthief", "- No. Sorry. Dunno.")
        str("speak.people.jpeasant", """
            - I think it's time
            someone found the
            Foot of Jazzy and
            defeated Ydarg!
        """)
        str("speak.people.jbard", """
            - Rumour has it that a
            fighter called
            {fighter_name}
            has a mission for you.
        """)
        str("speak.people.jdrifter", "- Don't know anyone.")
        str("speak.people.jhalf_orc", "- ME ALONE AND STRONG!")
        str("speak.people.jhalf_elf", """
            - There are many huts
            in the Wilderness.
        """)
        str("speak.people.jsmith", "- Sorry, can't help ya.")
        str("speak.people.jbargirl", "- Well...")
        str("speak.people.jinnkeeper", """
            - Want to meet interesting
            travellers? Try the
            inn or the tavern.
        """)
        str("speak.people.jtavernkeeper", """
            - Don't know about
            anyone particular.
        """)
        str("speak.people.jhermit", """
            - Many faces shine
            through the mist.
        """)
        str("speak.magic.jmerchant", """
            - It is said that the
            Master Crown is the
            most powerful object
            in the realm.
        """)
        str("speak.magic.jranger", """
            - You will need five
            magical items to
            defeat baron Ydarg.
        """)
        str("speak.magic.jfighter", """
            - What? I don't believe
            in magic...
        """)
        str("speak.magic.jwizard", "- Magic... Yes, Magic.")
        str("speak.magic.jpriest", """
            - Yes my son, but we
            are all puny beings
            compared to the gods.
        """)
        str("speak.magic.jthief", "- I know nothing of it.")
        str("speak.magic.jpeasant", """
            - I don't know much about
            magic. Gems often have
            magical properties,
            though.
        """)
        str("speak.magic.jbard", """
            - Magic is the subject
            of many of my songs.
            Ballad of the Fire
            Shield, for instance.
        """)
        str("speak.magic.jdrifter", "- GO AWAY!")
        str("speak.magic.jhalf_orc", """
            - ME HAVE TRUSTY SWORD!
            ME NOT CARE 'BOUT
            MAGIK!
        """)
        str("speak.magic.jhalf_elf", """
            - Yes, my people were
            created from the magic
            of mother earth.
        """)
        str("speak.magic.jsmith", """
            - Rumour has it, that
            some ogres have the foot
            of Jazzy, a powerful
            item.
        """)
        str("speak.magic.jbargirl", """
            - I know nothing about
            that.
        """)
        str("speak.magic.jinnkeeper", """
            - Magic? Well, they say
            that baron Ydarg can
            be defeated only by
            aid of the Silver Key
        """)
        str("speak.magic.jtavernkeeper", "- Sorry. Can't help you.")
        str("speak.magic.jhermit", """
            - The Crown, the Shield,
            the Bracelets and the Foot
            is all you need.
        """)
        str("speak.lodge.jmerchant", """
            - Well. The city inn is
            a good try.
        """)
        str("speak.lodge.jranger", """
            - I sleep under the open
            skies. Just beware of
            bandits if you do this!
        """)
        str("speak.lodge.jfighter", """
            - Well. I usually stay at
            the inn when I'm in town.
        """)
        str("speak.lodge.jwizard", "- Oh??? Eternal sleep!")
        str("speak.lodge.jpriest", """
            - I hire a room at
            the inn to meditate!
        """)
        str("speak.lodge.jthief", "- I sleep in the shadows.")
        str("speak.lodge.jpeasant", """
            - I sleep in my own
            room at my farm.
        """)
        str("speak.lodge.jbard", """
            - Imagine the beauty
            and danger to camp in
            the Wilderness these days.
        """)
        str("speak.lodge.jdrifter", """
            - Look, why don't you
            just _GO AWAY_???
        """)
        str("speak.lodge.jhalf_orc", "- ME NEVER GET TIRED!")
        str("speak.lodge.jhalf_elf", "- I rent a room at the inn!")
        str("speak.lodge.jsmith", """
            - Well, sleeping at the
            inn is often nicer than
            in the Wilderness.
        """)
        str("speak.lodge.jinnkeeper", """
            - Well. My inn is the
            best in the realm!
        """)
        str("speak.lodge.jtavernkeeper", """
            - I dunno, try my
            brother's inn.
        """)
        str("speak.lodge.jhermit", """
            - Yes. You are indeed
            the one...
        """)
        str("speak.lodge.jbargirl.offer", """
            - Would you like to,
              spend an hour
              upstairs for {price}
              gold coins?
        """)
        str("speak.lodge.jbargirl.broke", """
            - No money, eh?
              don't bother
              to come back!
        """)
        str("speak.lodge.jbargirl.spent", """
            You spend a very
            exhausting hour
            upstairs.
            (PERVERT!) ;-)
        """)
        str("speak.merchant.offer", """
            In fact, I have a mission
            for you. It will bring
            you much gold!
        """)
        str("speak.merchant.bandits", """
            I was robbed by bandits
            just to the east of here.
            If you find these bandits,
            kill them, and return
            my cargo here, I'll pay
            you 2000 gold and a ruby
            my grandfather gave me.
        """)
        str("speak.merchant.grateful", """
            The merchant is GRATEFUL!
            He pays you 2000 gold and
            hands you a red gem.
        """)
        str("speak.merchant.cant_carry", "You can't carry the gem.")
        str("speak.merchant.picked_up", "You pick up the gem.")
        str("speak.ranger.intro", """
            - Greetings, adventurers.
            I have a mission for you.
            He hands you a letter.
        """)
        str("speak.ranger.deliver", """
            - If you deliver this
            to my brother, {ranger_name},
            who lives {ranger_loc},
            he will give you
            a great weapon!
        """)
        str("speak.fighter.intro", """
            - Greetings. Once upon
            a time, I was a mighty
            fighter. Before I retired
            I managed to slay over
            two hundred orcs. All
            of this, because of my
            Dancing Blade. I have
            no use for this weapon
        """)
        str("speak.fighter.favour", """
            anymore, and I will give
            it to you, if you do me a
            small favour. A priest
            named {priest_name} has got
            one of my books. We are
            old friends, and I lended
            it to him. However, I
            haven't heard much from
            him lately.
        """)
        str("speak.fighter.blade", """
            - Oh! Thank you!!! He
            removes a floor board
            and gives you a WONDERFUL
            blade. It sparkles in
            the light!
        """)
        str("speak.fighter.cant_carry", "You can't carry it!!!.")
        str("speak.fighter.take_blade", "You take the blade.")
        str("speak.wizard.robbed", """
            You persuade him to
            give you the book.
            After showing him your
            sharp weapons, he
            chickens out and pulls
            it out of his robe.
            He throws it at you and
            flees...
        """)
        str("speak.priest.intro", """
            When you mention the
            book, he nods and looks
            a bit embarrased.
            - Yes, I know. I happened
            to lose it. A wizard
            called {wizard_name} stole it
            from my bedroom a week
            ago...
        """)
        str("speak.priest.fled", """
            He quickly fled, but I
            know it's him, because
            he made inquieries about
            it, and tried to steal
            it from me when I was
            having dinner.
        """)
        str("speak.priest.please", """
            I don't know his
            whereabouts, but please
            find the book and
            take it back to {giver_name}
            if you can.
        """)
        str("speak.silver_key.hiss", """
            - There is an item of
            great power, he hisses.
        """)
        str("speak.silver_key.research", """
            - It is called the
            SILVER KEY. It has taken
            me many years of research
            to find its location, and
            this only to be stopped
            by a guardian telling me
            that 'only those of pure
            heart can enter'.
        """)
        str("speak.silver_key.chased", """
            - I fled for my life as
            horned creatures chased
            me through the woods.
        """)
        str("speak.silver_key.offer", """
            I have an offer to make
            thee. I will sell you
            a map to the location of
            the SILVER KEY for 50000
            gold coins. The price is
            resonable. 10000 gold for
            each of those fruitless
            years.
        """)
        str("speak.silver_key.deal", "- Deal? ")
        str("speak.silver_key.broke", """
            - Sorry! 50000! No more
            no less. Come back when
            you have the gold.
        """)
        str("speak.silver_key.map", """
            He quietly slips you the
            precious map. It
            tells you that the Pit
            of the Key is at
            {pit_x}x{pit_y}!
        """)
        str("speak.erethor.rumour", """
            - Rumor has it that
            Erethor's Lute, a
            musical instrument with
            magical properties
            exists somewhere in
            the realm.
        """)
        str("speak.erethor.demon", """
            - Erethor is a mighty
            demon...
        """)
        str("speak.erethor.poor_man", """
            - I am a very poor man
            myself, but I know of
            the Cloak of Gods,
            the most powerful of
            all armors. I will
        """)
        str("speak.erethor.trade", """
            trade this information
            if you find me Erethor's
            Lute. One of Erethor's
            temples, where mad
            priests practise
            blasphemous magic is
            located at 6x20, near
            the western peaks.
        """)
        str("speak.erethor.dangerous", """
            - This may be a dangerous
            mission for such a
            small reward, but the
            Cloak will make it's
            bearer almost
            invulnerable.
        """)
        str("speak.erethor.hand_lute", """
            You hand him the
            golden lute...
        """)
        str("speak.erethor.note", """
            - I can't believe you
            actually made it!!!
            - Now for my part of
            the deal. Listen closely,
            for I shall say this only
            once.
        """)
        str("speak.erethor.note_deliver", """
            - Take this note here
            and seek out my friend
            {minstrel_name}. He is also a
            minstrel.
            {minstrel_name} lives {minstrel_loc}.
        """)
        str("speak.erethor.together", """
            - We used to travel
            together and he found
            the Cloak in a long dead
            hero's tomb. Now that
            we have the Lute, the
            Cloak is yours. Just
            deliver this explaining
            note!
        """)
        str("speak.erethor.paper", """
            He hands you a piece of
            paper and bids you
            farewell.
        """)
        str("speak.thun.legend", """
            - I have a map of the
            road to the temple of
            Thun! The legend says
            it keeps the Bracelets
            of Thun, which is one of
            the objects that can
            destroy baron Ydarg!
        """)
        str("speak.thun.offer", """
            - I will sell it to you
            for 10000 gold pieces!
        """)
        str("speak.thun.broke", """
            - Sorry! 10000! No more
            no less. Come back when
            you have the gold.
        """)
        str("speak.thun.map", """
            - Ok, here. He slowly
            hands you an old map. It
            tells you that the temple
            of Thun is situated at
            {temple_x}x{temple_y}!
        """)
        str("speak.brother.give_sword", """
            - OH! A message from my
            brother! This is indeed
            great news. I will give
            you this sword for
            delivering the letter!
        """)
        str("speak.brother.produces", """
            He produces a sparkling
            sword and wishes you a
            happy journey.
        """)
        str("speak.brother.cant_carry", "You can't carry it!!!.")
        str("speak.brother.take_sword", "You take the sword.")
        str("speak.nymph.know", """
            I heard that a man called
            {seeker_name} knows of the
            Crown. I have roamed the
            land in search for him,
            since I want this item
            to be found. Here! Take
            this ring and show it
            to him. You are better fit
            for this mission than me.
        """)
        str("speak.nymph.location", """
            {seeker_name} lives
            {seeker_loc}
        """)
        str("speak.thief2.push", """
            You push him up against
            a wall and start asking
            him questions. After
            some 'mild' persuation
            he tells you that he
            sold the Key to a
            travelling blacksmith
            who was interested in
            its metalurgic structure.
        """)
        str("speak.thief2.smith", """
            After breaking a few
            of his teeth, he also
            willinly reveals that
            the smith went by the
            name of {smith_name}...
        """)
        str("speak.thief2.let_go", """
            You let go of your grip
            of his collar, and he
            falls to the floor.
        """)
        str("speak.husband.depressed", """
            - Oh, I'm so depressed.
            Orcs have kidnapped my
            wife, Tammy, and demand
            a ransom for her. I will
            give you my entire fortune
            if you retrieve her.
        """)
        str("speak.husband.fortune", """
            About 3000 gold coins,
            a fine armor, and
            some strange items my
            father left me, will be
            yours if you rescue her.
        """)
        str("speak.husband.cave", """
            The orcs have a cave
            at 42x23 in Lonewood.
        """)
        str("speak.husband.reunion", """
            When he sees Tammy enter
            the room, he cries tears
            of happiness and kisses
            her. When they have had
            this happy reunion, he
            motions to you to follow.
        """)
        str("speak.husband.gives", """
            He hands you 3000 gold,
            an Adamantium Armor of
            advanced craftmanship
            and some other assorted
            objects. (An amber potion
            a holy handgrenade, a
            fire wand and a crystal.
        """)
        str("speak.husband.cant_carry", """
            You can't carry all the
            items...
        """)
        str("speak.minstrel.reads", """
            After reading the note
            {minstrel_name} cries tears
            of happiness. He
            shakes all your hands
            and produces a purple
            cloak.
        """)
        str("speak.minstrel.cant_carry", "You can't carry it!")
        str("speak.minstrel.take_cloak", "You take the cloak.")
        str("speak.minstrel.travel", """
            He bids you farewell
            and tells you that
            he'll travel to
            {next_town} the very
            next day...
        """)
        str("speak.jazzy_ogre.hear", """
            - ME HEAR YOU LOOK
            FOR POWER ITEMS!

            - ME VERY POOR! SELL
            YOU INFORMATION ABOUT
            FOOT OF JAZZY, YES?
        """)
        str("speak.jazzy_ogre.trade", """
            - LET US TRADE! OFFER
            ITEM AND I'LL TELL YA!

            (You offer him something).
        """)
        str("speak.jazzy_ogre.refuse", "- NAH, THINK NOT!.")
        str("speak.jazzy_ogre.accept", """
            - YEEES! NICE NIICE!
            GO TO CAVE AT {cave_x}x{cave_y}!
            BEAST HAS THE FOOT.
        """)
        str("speak.wizard3t.intro", """
            - Ah, a seeker! Yes, I
            know about the Master
            Crown, but since I'm
            neither good nor evil,
            I have no interest in it.
            I will give you the
            information if you do
            three tasks for me.
        """)
        str("speak.wizard3t.agree", """
            (Since there is nothing
            else you can do,you agree
            to complete the three
            tasks).
        """)
        str("speak.wizard3t.task1", """
            - Firstly, I want you to
            go to the kobold lair at
            {kobold_x}x{kobold_y}, and kill all life
            there. It is close to my
            home village, and the foul
            creatures have many times
            attacked us. Farewell.
        """)
        str("speak.wizard3t.pendant", """
            You hand him the pendant.
            He stares at you, almost
            admiring.
            - WELL DONE! I will now
            grant you the second
            quest.
        """)
        str("speak.wizard3t.task2", """
            - Being a magician, I'm
            always in search for
            powerful spell reagents.
            There is one plant I need
            can't find. It is
            called Skullweed, and is
            purple with small green
            twigs. Find this plant
            plant for me!
        """)
        str("speak.wizard3t.task3_intro", """
            - Well done again,
            adventurers. The third
            of my tasks will perhaps
            be the toughest one
            of them all! Find me a
            DWARF STONE. It is a
            stone of magnetic iron
            which gives the wielder
            the power of levitation.
        """)
        str("speak.wizard3t.task3", """
            - Begin your search in
            the dwarven colony, near
            {dwarf_x}x{dwarf_y}. Be sure to treat
            the dwarves with respect,
            for they are swift to
            anger.
        """)
        str("speak.wizard3t.grateful", """
            - I JUST CAN'T TELL YOU
            HOW GRATEFUL I AM!
            He takes the stone and
            smiles broadly at you.
            - Very well, seekers! The
            Master Crown is hidden in
            a dungeon near {crown_x}x{crown_y}!
            Fare thee well.
        """)
        str("speak.smith.ask", """
            You ask him if he still
            has the Key, prepared
            for the worst...
        """)
        str("speak.smith.sorry", """
            - Nah, sorry. I kinda
            studied it enough and
            was gonna bring it to
            Ollk to sell it. However
            on the road between Wilor
            and Ollk I was caught by
            filthy hobgoblins!
        """)
        str("speak.smith.pursued", """
            - I managed to get away,
            but they followed me! Just
            to the south-west of here
            one of their arrows hit
            my horse.
        """)
        str("speak.smith.clubbed", """
            - They clubbed me
            unconcious and took all
            my belonings!!! Including
            that key. Find them and
            take it back. It's yours
            if you get it.
        """)
        str("speak.hermit.welcome", """
            - Welcome, chosen ones!
            the hermit cackles.
            - I know of the Fire
            Shield, an item of
            great power.
            Go find me teeth of
            a troll, and I will
            tell you about it.
        """)
        str("speak.hermit.tooth", """
            You hand him the troll
            tooth.
            - Oh YES! He cackles.
            At areas uncharted, near
            {cave_x}x{cave_y} is the cave you
            seek. Beware of the
            guardian the ancients
            gave birth too...
        """)

        // ----- towne.json5 -----
        str("towne.rumour.r0", """
            Baron Ydarg's castle
            Zadraz is hidden in a
            forest to the soutwest.
        """)
        str("towne.rumour.r1", """
            The Silver Key unlocks
            the gate to Ydarg's
            dimension.
        """)
        str("towne.rumour.r2", """
            You will need the Fire
            shield to defeat baron
            Ydarg.
        """)
        str("towne.rumour.r3", """
            You will need the
            Master Crown to
            defeat baron Ydarg.
        """)
        str("towne.rumour.r4", """
            You will need the
            Bracelets of Thun
            to resist the most
            evil of all spells...
        """)
        str("towne.rumour.r5", """
            The scythe is a
            useless weapon.
        """)
        str("towne.rumour.r6", """
            Some say that you
            need gems to enter
            the old Temple of
            Thun.
        """)
        str("towne.rumour.r7", """
            Seek the Hermit if
            you want to know
            of the Fire Shield
        """)
        str("towne.rumour.r8", """
            The Hermit lives in
            the swamp.
        """)
        str("towne.rumour.r9", """
            Always post a guard
            when you camp in the
            Wilderness.
        """)
        str("towne.rumour.r10", """
            The Foot of Jazzy is
            one of the objects
            Baron Ydarg can't
            control!
        """)
        str("towne.rumour.r11", """
            Skullweed plant grows
            deep in the swamp. It is
            a powerful reagent.
        """)
        str("towne.rumour.r12", """
            A man named {name1}
            will pay you for
            killing some bandits.
        """)
        str("towne.rumour.r13", """
            A hunter called {name2}
            seeks a brave party
            of adventurers.
        """)
        str("towne.rumour.r14", """
            It is always wise to
            haggle.
        """)
        str("towne.rumour.r15", "Cthulhu will rise again!")
        str("towne.rumour.r16", """
            Baron Ydarg's castle
            in this dimension lies
            far to the southwest.
        """)
        str("towne.rumour.r17", """
            Exploring caves can
            be profitable.
        """)
        str("towne.rumour.r18", """
            Flee from all beasts
            that you are uncertain
            of.
        """)
        str("towne.rumour.r19", """
            Before tempering in
            the business of a God,
            be a demi god yourself.
        """)
        str("towne.rumour.r20", """
            Jazzy is a mighty
            lesser god or a demon.
        """)
        str("towne.rumour.r21", """
            Baron Ydargs castle
            Zadraz is a gate to
            another dimension.
        """)
        str("towne.rumour.r22", """
            Never meddle in the
            affairs of wizards, for
            they are subtle, and
            swift to anger.
        """)
        str("towne.rumour.r23", """
            Magical objects can be
            a great aid in battle.
        """)
        str("towne.rumour.r24", "Seek the holy handgrenade!")
        str("towne.rumour.r25", """
            A man named {name15}
            knows of the Crown.
            He lives {loc15}!
        """)
        str("towne.rumour.r26", """
            Murn is situated
            somewhere near 50x30!
        """)
        str("towne.rumour.r27", """
            Seek {name13} if you
            search the Temple of
            Thun! He lives {loc13}
        """)
        str("towne.rumour.r28", """
            The more powerful
            creatures, the more
            experience.
        """)
        str("towne.rumour.r29", """
            A good sword beats
            four aces!
        """)
        str("towne.rumour.r30", """
            You need FIVE items,
            no less, no more
            to kill Ydarg!
        """)
        str("towne.rumour.r31", """
            Wilor is situated
            at the coordinates
            43x20!
        """)
        str("towne.rumour.r32", """
            Ollk is situated
            somewhere near 50x10!
        """)
        str("towne.enter.nothing", "Nothing to enter here!")
        str("towne.enter.welcome", "Welcome to {twn}")
        str("towne.enter.tax", """
            Our current tax rate
            is {pct}% - In order to
            enter, we must request
            you pay {amount} au.
        """)
        str("towne.enter.tax_menu", """
            (P)ay to Enter
            (L)eave
        """)
        str("towne.enter.thanks", "Thank you!")
        str("towne.enter.village", "Village of {twn}")
        str("towne.enter.city", "City of {twn}")
        str("towne.enter.divider", "=======================")
        str("towne.enter.menu_core", """
            (M)erchant  (I)nn
            (T)avern    (G)uild
        """)
        str("towne.enter.menu_smith", "(S)mith")
        str("towne.enter.menu_smith_ch", "(S)mith     (C)hurch")
        str("towne.enter.menu_leave", "(L)eave")
        str("towne.enter.prompt", ": ")
        str("towne.enter.weapons_q", """
            Do you want to ready
            some weapons before
            leaving?
        """)
        str("towne.enter.armors_q", """
            Do you want to ready
            some armors before
            leaving?
        """)
        str("towne.hut.enter", "You enter the hut.")
        str("towne.hut.greeter", """
            A {job} who
            introduces himself as
            {name}, greets you.
        """)
        str("towne.hut.menu", """
            (T)alk
            (L)eave
        """)
        str("towne.church.intro", """
            You enter the church
            of Dalina, a beautiful
            goddess. This is one of
            her two temples.
        """)
        str("towne.church.menu", """
            (H)eal person
            (R)aise dead
            (E)nergize
            (L)eave
        """)
        str("towne.church.who", "Who? (1-{party_max})")
        str("towne.church.not_spellcaster", "Not a spellcaster!")
        str("towne.church.no_need_sp", """
            That character needs
            no spell points
            to be restored.
        """)
        str("towne.church.cost", """
            - That'll be {cost}
            gold coins, please.
        """)
        str("towne.church.cost2", """
            - That will be {cost}
            gold coins, please.
        """)
        str("towne.church.pn_menu", """
            (P)ay
            (N)o thanks
        """)
        str("towne.church.no_gold", "You don't have the gold!")
        str("towne.church.no_gold2", """
            You don't have enough
            gold!
        """)
        str("towne.church.restored", """
            The priestess
            restores his energy.
        """)
        str("towne.church.alive", """
            - {pro_sub} seems very alive
            at the moment.
        """)
        str("towne.church.raised", """
            The priestess raises
            {pro_obj}.
        """)
        str("towne.church.corpse", """
            - GET THAT CORPSE
            OUTTA HERE!
            (You are thrown out).
        """)
        str("towne.church.no_healing", "{pro_sub} needs no healing.")
        str("towne.church.healed", """
            The priestess heals
            {pro_obj} fully.
        """)
        str("towne.smith.closed", "Shop Closed.")
        str("towne.smith.intro", """
            Smith.

            The smith welcomes you
            to his small shop.
        """)
        str("towne.smith.beer_offer", """
            The smith offers you some
            beer, and the latest news
            from the realm.
        """)
        str("towne.smith.beer_menu", """
            (A)ccept
            (D)ecline
        """)
        str("towne.smith.accept_1", "You accept the invitation.")
        str("towne.smith.accept_2", """
            After a while the smith
            leaves to get more beer.
        """)
        str("towne.smith.accept_3", """
            When he returns, you ask
            him for the latest news...
        """)
        str("towne.smith.decline", """
            The smith is disappointed.
            - Maybe another time,
            you tell him.
            - Yeah, he answers.
        """)
        str("towne.smith.shop_menu", """
            (B)uy
            (S)ell
            (L)eave
        """)
        str("towne.smith.bye", "Well, bye then...")
        str("towne.smith.buy_menu", """
            Armor or Weapon?

            (A)rmor
            (W)eapon
            (N)othing
        """)
        str("towne.smith.we_have", "We have...")
        str("towne.smith.armor_menu", """
            1. Leather     ({cost:leather} Au)
            2. Chain Mail  ({cost:chain_mail} Au)
            3. Plate Mail  ({cost:plate_mail} Au)
            4. Ring Mail   ({cost:ring_mail} Au)
            5. Elvin Cloak ({cost:elvin_cloak} Au)
            6. Mith. Mail  ({cost:mithril_mail} Au)
        """)
        str("towne.smith.weapon_page1", """
            1. Sword     ({cost:sword} Au)
            2. Axe       ({cost:axe} Au)
            3. Bow       ({cost:bow} Au)
            4. Club      ({cost:club} Au)
            5. Halbard   ({cost:halbard} Au)
            6. Crossbow  ({cost:crossbow} Au)

            N. Next Page
        """)
        str("towne.smith.weapon_page2", """
            7. Longsword ({cost:longsword} Au)
            8. 2h-Sword  ({cost:two_hand_sword} Au)
            9. Pole Arm  ({cost:pole_arm} Au)
            A. Trident   ({cost:trident} Au)
            B. Mgk Dagger({cost:magic_dagger} Au)
            C. Mgk Sword ({cost:magic_sword} Au)

            P. Previous Page
        """)
        str("towne.smith.too_expensive", "Too expensive...")
        str("towne.smith.cant_carry", "You cannot carry more.")
        str("towne.smith.deal", "Deal!")
        str("towne.smith.sell_prompt", "Sell what?")
        str("towne.smith.not_w_or_a", "Not weapon or armor.")
        str("towne.smith.offer", "Ok! Give ya {price}!")
        str("towne.smith.bargain_opt", "(B)argain")
        str("towne.smith.no_thanks", "No thanks.")
        str("towne.tavern.closed", "Tavern Closed.")
        str("towne.tavern.scruffy_1", """
            Just as you are about
            to sit down at a,
            table, a scruffy
            little man, pulls
            at your sleeve...
        """)
        str("towne.tavern.scruffy_2", """
            - Hey, hey, hey! You!
              yes... you! I have
              and offer that may
              interest you...
        """)
        str("towne.tavern.scruffy_offer", """
            - I will sell you this
              {item}
              for the ridiculously
              low price of {price}
              Aurum!
        """)
        str("towne.tavern.buy_nothanks", """
            (B)uy it
            (N)o thanks
        """)
        str("towne.tavern.scruffy_broke", """
            - You don't have that
              much gold!
        """)
        str("towne.tavern.scruffy_deal", "- Deal!!!")
        str("towne.tavern.scruffy_full", "- You can't carry it!")
        str("towne.tavern.scruffy_no", """
            - Fine, I can live with
              that.
        """)
        str("towne.tavern.greeter", """
            - Welcome! Have a seat
            {name}, the keeper
            of the tavern greets
            you...
        """)
        str("towne.tavern.main_menu", """
            (O)rder drink
            (S)peak
            (T)ell story
            (R)umors
            (L)eave
        """)
        str("towne.tavern.story_who", """
            Who wants to tell
            a story? (1-{party_max})
        """)
        str("towne.tavern.dead_story", """
            Dead men do not
            tell any great
            stories...
        """)
        str("towne.tavern.stands_up", """
            {who} stands up
            and screams:
            - ANYONE WHO WANNA HEAR
              A TRAVELLER'S STORY?
        """)
        str("towne.tavern.yeah", "- YEAAH! Roars the crowd!")
        str("towne.tavern.story_menu", """
            (T)rue story
            (B)oast
        """)
        str("towne.tavern.liar_kicked", "- Out with the liar!")
        str("towne.tavern.thrown_out", "You are thrown out.")
        str("towne.tavern.st_superb", """
            Superb Story! Let's
            give 'em some ale, lads!
            You are given a whole
            tray of ale!
        """)
        str("towne.tavern.st_vgood", """
            Very good! You've been
            through much!
        """)
        str("towne.tavern.st_nice", "Nice story, Sir!")
        str("towne.tavern.st_bored", "The mob is bored.")
        str("towne.tavern.st_asleep", """
            Most of the crowd
            falls asleep. The rest
            beg you to stop.
        """)
        str("towne.tavern.quiet", """
            The tavern has suddenly
            become very quiet.
        """)
        str("towne.tavern.rum_old", """
            After listening a while
            you hear an old man say
            to another:
        """)
        str("towne.tavern.rum_bard", "A drunken bard bellows:")
        str("towne.tavern.rum_halfling", """
            A halfling you rescued
            from a brawl whispers:
        """)
        str("towne.tavern.rum_keeper", """
            The tavern keeper
            motions to you to
            come closer, and
            whispers :
        """)
        str("towne.tavern.rum_bargirl", "A bargirl tells you:")
        str("towne.tavern.order_header", "Order:")
        str("towne.tavern.order_menu", """
            A. Ale       2 au
            B. Mead      3 au
            C. Wine      4 au
            D. Fanta     1 au
            E. Nothing
        """)
        str("towne.tavern.no_drink", "No money - No drink!")
        str("towne.tavern.aah", """
            AAH! (your health
            returns).
        """)
        str("towne.tavern.laughed_at", """
            You are the laughing
            stock of the entire
            tavern...
        """)
        str("towne.inn.greeter", """
            {name}, the Innkeeper
            welcomes you.

            Do you want supper,
            travellers?
        """)
        str("towne.inn.supper_cost", """
            Sorry, supper is {cost}
            gold.
        """)
        str("towne.inn.supper_served", """
            You are served a well
            tasting dinner. Some
            of your health returns.
        """)
        str("towne.inn.drink_q", "Anything to drink?")
        str("towne.inn.drink_menu", """
            What'll it be?

            A. Ale         2 au
            B. Mead        3 au
            C. Wine        5 au
            D. Whiskey     7 au
            E. Fanta       1 au
        """)
        str("towne.inn.ahh", "Ahh...!")
        str("towne.inn.giggle", "The other guests giggle.")
        str("towne.inn.lodging_q", """
            Are you in need
            of lodging?
        """)
        str("towne.inn.room_cost", "A room is {cost} gold/night.")
        str("towne.inn.room_menu", """
            (T)ake it
            (L)eave
        """)
        str("towne.inn.no_gold_kick", """
            - AHA! No gold!
            You are thrown out.
        """)
        str("towne.inn.bedtime", "You go to bed...")
        str("towne.inn.zzz", "Zzzz...")
        str("towne.inn.morning", """
            You wake up a little
            before dawn, well
            rested.

            You have breakfast
            and leave the inn.
        """)
        str("towne.inn.leaves", "The innkeeper leaves.")
        str("towne.inn.chit_menu", """
            (C)hitchat with guests
            (L)eave
        """)
        str("towne.merchant.closed", "Shop Closed.")
        str("towne.merchant.intro", """
            Merchant

            Can I help you? I
            trade in the finest
            items in the realm!
        """)
        str("towne.merchant.menu", """
            (B)uy item
            (S)ell item
            (L)eave
        """)
        str("towne.merchant.bye", "Well, bye then...")
        str("towne.merchant.wrong_kind", """
            - I don't deal in that
              kind of stuff!
        """)
        str("towne.merchant.sell_offer", "- Ok. Give ya {price}!")
        str("towne.merchant.sold", "Sold!")
        str("towne.merchant.buy_menu", """
            (A) Torch           1 au
            (B) Food Ration    {food_price} au
            (C) Blanket        30 au
            (D) Tent           80 au
            (E) Rope           15 au
            (F) Healing Potion {potion_price} au
            (G) Donkey        800 au
            (H) Nothing
        """)
        str("towne.merchant.each", "{price} gold each, sir.")
        str("towne.merchant.how_many", "How many? ")
        str("towne.merchant.too_many_torches", """
            You can carry no
            more than 80
            torches...
        """)
        str("towne.merchant.too_many_food", """
            You can carry no
            more than {cap} units!
        """)
        str("towne.merchant.too_expensive", "Too expensive!")
        str("towne.merchant.cant_carry", "You cannot carry more.")
        str("towne.merchant.deal", "Deal!")
        str("towne.merchant.price", "Price: {price}")
        str("towne.merchant.pn_menu", """
            (P)ay
            (N)one
        """)
        str("towne.merchant.desc.torch", """
            Ah, the finest torches
            in the realm.
        """)
        str("towne.merchant.desc.food", """
            Better food is not
            availible anywhere else
            in the entire realm.
        """)
        str("towne.merchant.desc.blanket", """
            Our blankets are very
            warm and soft.
        """)
        str("towne.merchant.desc.tent", """
            Noone manufactures
            better tents than
            we do!
        """)
        str("towne.merchant.desc.rope", """
            The finest hemp
            ropes in the realm.
        """)
        str("towne.merchant.desc.potion", """
            The best healing
            potions availible
            for money!
        """)
        str("towne.merchant.desc.donkey", """
            Our stout donkeys
            are bred just for
            carrying your food
            and equipment.
        """)

        // ----- missions.json5 -----
        str("missions.erethor_entry.prompt", """
            Here is the temple of
            the chaos lord Erethor.
            A winding staircase
            spirals down between
            broken pillars.

            (E)nter
            (L)eave
        """)
        str("missions.husband_cave.prompt", """
            There is a cave entrance
            in the foothills to the
            east. Is this the place
            {name19} talked about?

            (E)nter
            (L)eave
        """)
        str("missions.bandits.approach", """
            You come across the
            camp of the bandits.
            One of them spots you
            and they attack!
        """)
        str("missions.bandits.cargo_found", """
            You find the cargo
            the merchant wanted
            you to bring him.
        """)
        str("missions.troll_tooth.after_troll", """
            You break off a troll
            tooth to give the hermit.
            After wiping the slime of
            it, you put it in your
            backpack.
        """)
        str("missions.hermit_dungeon.prompt", """
            You have found a dungeon.
            The entrance has been
            cut out of some huge
            animal's skull.

            (E)nter
            (L)eave
        """)
        str("missions.thun_temple.temple_arrival", """
            You stand before the
            ancient temple of Thun.
            Around you are the ruins
            of the once proud city
            of Thun...
        """)
        str("missions.thun_temple.altar_inscription", """
            You see an altar
            with five sockets before
            you. Each is roughly
            gem-sized. An inscription
            says: 'Insert the gems
            and your path shall open'
        """)
        str("missions.thun_temple.gem_prompt", """
            Do you want to put
            five gems in the sockets?

            (Y)es
            (L)eave
        """)
        str("missions.thun_temple.no_gems", """
            You don't have five
            different gems.
        """)
        str("missions.thun_temple.altar_opens", """
            With a resounding 'CRUNG'
            the altar slides away
            to reveal a dark pit.
            You enter...
        """)
        str("missions.kobold_village.prompt", """
            The kobold village lies
            ahead. You can hear the
            noise from some kind of
            party even at a distance
            of 200 yards.

            (A)ttack now
            (L)eave
        """)
        str("missions.kobold_village.charge", """
            You run screaming into
            a large celebration of
            some sort. There are
            about forty kobolds here.
            At first, they are very
            surprised, and you manage
            to slay many, before they
            attack...
        """)
        str("missions.kobold_village.dead_piles", """
            Piles of dead kobolds are
            lying all around. Still,
            they keep coming!
        """)
        str("missions.kobold_village.orcs_ambush", """
            You have annihilated most
            of the village. You pause
            to wipe the blood of your
            weapons, when suddenly,
            some ORCS rush out to
            attack you from nowhere.
        """)
        str("missions.kobold_village.pendant", """
            The entire village is
            wiped out. You snatch
            an ugly pendant from the
            head of the kobold chief
            to prove the bloody deed.
        """)
        str("missions.skullweed.pickup", """
            On the ground before you
            grows the skullweed plant.
            You break off a few plants
            and put in your backpack.
        """)
        // Option-2 demo: pure narration sequence; the borin_meet mission
        // plays all 6 beats as a unit, so they share an auto-numbered
        // chain rather than 6 individual str() ids.
        chain("missions.borin_meet",
            """
            You arrive at a village
            which looks typically
            dwarfish. As you approach
            the village square, an
            old dwarf who introduces
            himself as Borin greets
            you.
            """,
            """
            You explain why you are
            here, and he frowns.
            - Well, he says, you
            actually COULD have a
            stone of that type
            if you do our tribe a
            favour...
            """,
            """
            - Years have passed, and
            much of our knowledge
            about the making of
            arms is gone. We haven't
            practised it, generation
            after generation, and the
            arms we produce nowadays
            are not as unbeatable as
            they once were...
            """,
            """
            - When our king Nolrim
            died, nine hundred years
            ago we buried him with his
            trusty dagger Reebar. If
            we had this to study
            we could once again become
            the greatest smiths that
            there are...
            """,
            """
            - If you go to Nolrim's
            crypt, breach the traps my
            ancestors set to protect
            him against looters, and
            return Reebar, I will not
            only give you the stone,
            but also lots of gold,and
            some dwarven treasures.
            """,
            """
            - Nolrim's crypt is at
            {dx5}x{dy5}. Be careful, and
            fare the well.
            """,
        )
        str("missions.nolrim_crypt.prompt", """
            Before you is the crypt
            of Nolrim. After levering
            a stone slab away, a dark
            forboding passage leads
            down to unknown depths.

            (E)nter the Crypt
            (L)eave
        """)
        str("missions.borin_gift.greet_and_follow", """
            Borin greets you. He
            can hardly believe his
            eyes when you show him
            the dagger. He motions
            to you to follow him,
            and guides you to his
            hut.
        """)
        str("missions.borin_gift.rewards_presented", """
            Smiling, he offers you
            3578 gold coins and 3
            gems. You greedily accept
            the gift. He also hands
            you a purple dwarf stone.
        """)
        str("missions.master_crown.prompt", """
            Before you is the dungeon
            where the ancients hid the
            Master Crown. Your long
            trek has come to an end.

            (E)nter
            (L)eave
        """)
        str("missions.ogre_cave.prompt", """
            You find a the cave the
            half-orc told you about.
            As you get closer, you
            become aware of a hideous
            smell, emnating from the
            cavern depths...

            (E)nter
            (L)eave
        """)
        str("missions.ogre_cave.enter_cave", """
            You enter the cave.
            piles of treasure are
            thrown all around.
            You note with terror
            that some OGRES sit
            among the gold and
            and jewels! They spot
            you and run screaming
            to attack!
        """)
        str("missions.ogre_cave.defeat_ogres", """
            You defeat the ogres and
            start to search for the
            fabled Foot of Jazzy.
            Just as you begin to
            wonder if the half-orc
            lied to you, you hear a
            sound and quickly turn
            around. An OGRE LORD has
            quietly entered the cave!
        """)
        str("missions.ogre_cave.defeat_ogre_lord", """
            You defeat the Ogre Lord.
            You take all the gold
            you find, 5645 gold coins!
            You also find a Mithril
            Mail some unfortunate
            warrior has left here.
        """)
        str("missions.ogre_cave.no_trace", """
            ...There is however no
            trace of the Foot. Just
            as you are about to give
            up, {p2_name} finds a note!
            It is written in shaky
            hand with a strange
            brown ink...
        """)
        str("missions.ogre_cave.kastagir_note", """
            'THEY SHALL NOT HAVE THE
            FOOT! I'm dying and I'll
            soon be dead, but I will
            use my last strength to
            teleport it away from
            here to the eastern peaks,
            Jazzy's regions.'
                     - Kastagir.
        """)
        str("missions.ogre_cave.leave_cave", "You leave the cave.")
        str("missions.jazzy_foot.discovered", """
            Something about this whole
            area is weird. The air
            seems to be loaded with
            electricity. Suddenly you
            spot an object on the
            ground. It is a delicate
            ivory statue in the shape
            of a human foot. THIS IS
            THE FOOT OF JAZZY!
        """)
        str("missions.jazzy_foot.minions_attack", """
            Just as you touch it,
            the sky splits open and
            at the wailing of hundreds
            of tortured souls, Jazzy's
            minions attack you, for
            the heresy they find you
            guilty of...
        """)
        str("missions.jazzy_foot.quiet", """
            Quiet settles again, as
            you destroy the foul
            creatures, one by one.
            You reach for the foot
            again, when suddenly,
            something EVEN MORE
            horrible happens...
        """)
        str("missions.jazzy_foot.jazzy_appears", """
            A twenty foot tall
            being materializes
            in front of you! You will
            have to fight Jazzy,
            himself!
        """)
        str("missions.jazzy_foot.defeated", """
            Today, you have killed a
            god. You watch silently as
            the huge body starts to
            melt. It fades away into
            Nothingness. This time,
            when you pick up the foot,
            nothing happens.
        """)
        str("missions.silver_key.prompt", """
            Here lies the dungeon
            of the Silver Key.
            It is merely a pit in the
            ground. You _hope_ that
            that despicable thief
            didn't trick you.

            (E)nter dungeon
            (L)eave
        """)
        str("missions.hobgoblin_caravan.approach", """
            Just before you is a
            caravan of hobgoblins on
            white, well-bred horses.
        """)
        str("missions.hobgoblin_caravan.attack_pitch", """
            It is unusual that these
            foul creatures have such
            beautiful animals. Hoping
            that they can give you a
            clue about the key, you
            emit a loud battle cry
            and attack!
        """)
        str("missions.hobgoblin_caravan.interrogate", """
            Before you kill the last
            hobgoblin you ask him
            about the key. The foul
            creature blurts:
            - Boss has it! In ze cave!
            at home, yess, {dx9}x{dy9}...
            pleeez sirs, don't kill
            me...
        """)
        str("missions.hobgoblin_caravan.carotid", """
            (at this point you sever
             his carotid artery).
        """)
        str("missions.hobgoblin_cave.prompt", """
            This is a hobgoblin cave.
            Smoke drifts out of a pit
            in the ground. A staircase
            spirals around the inside
            of the pit. When you see
            the impaled human skulls
            decorating the entrance,
            you feel uneasy.

            (E)nter pit
            (L)eave
        """)

        // ----- dungeon.json5 -----
        str("dungeon.nothing_a", """
            You find nothing
            special on this level.
        """)
        str("dungeon.nothing_b", "This level is empty.")
        str("dungeon.spectrum_message", """
            A message on the wall
            reads:

            'M U R T C E P S'
            THiNgs aren't aLways iN
            theIR naTuRAl oRDeR...
        """)
        str("dungeon.fire_shield", """
            After you have bested
            the foul thing, the
            dungeon is strangely
            quiet. Upon entering
            this level, you find the
            marvelous FIRE SHIELD on
            a pedestal! You take it.
        """)
        str("dungeon.bracelets", """
            This whole chamber
            shines of a bright
            white light! On a
            table before you,
            lie the fabled
            Bracelets of Thun!
            The glow fades as
            you put them in your
            backpack.
        """)
        str("dungeon.nolrim_tomb", """
            Nolrim's body rests on
            a stone slab in this room.
            On his chest lies the
            dagger you seek. You put
            it in your backpack.
        """)
        str("dungeon.gold", """
            You find {amount}
            gold pieces.
        """)
        str("dungeon.torches", "You find {n} torches.")
        str("dungeon.fire_shield_guardian", """
            As you enter this level
            you hear a horrible
            evil laughter.
            Suddenly!
            Out of the shadows,
            something HIDEOUS
            emerges.
        """)
        str("dungeon.royal_guard1", """
            As you enter this room
            an army of hobgoblin
            skeletons attack you!
        """)
        str("dungeon.royal_guard2", """
            As you enter this room
            an army of undead
            hobgoblins, smelling
            of rotting flesh and
            brandising gleaming
            tridents attack you.
        """)
        str("dungeon.erethor_priests", """
            You are spotted by
            some priests. They
            attack you with blind
            fury.
        """)
        str("dungeon.tammys_prison", """
            The peasant's wife is in
            this room. You untie
            her bonds and let her
            join your party!
        """)
        str("dungeon.master_crown.intro", """
            FINALLY! In the hands of
            a long dead warrior, who
            is placed like a statue
            by the east wall of this
            chamber, is the Master
            Crown. The most powerful
            object in the realm.
            It shines with an inner
            light.
        """)
        str("dungeon.master_crown.warrior_alive", """
            ...but as you take it,
            THE WARRIOR COMES
            TO LIFE!!!
        """)
        str("dungeon.hobgoblin_patrol.attack", """
            You bump into a hobgoblin
            patrol. With shiny and
            well used scimitars, they
            attack you!
        """)
        str("dungeon.hobgoblin_patrol.password", """
            You find a scribbled
            note on the leader:
            Password: 'RISH EKKIN'
        """)
        str("dungeon.orc_nest.guarding", """
            Some orcs watch a trapdoor
            in the floor here. As
            you enter, they attack!
        """)
        str("dungeon.orc_nest.more", """
            The last of the guardians
            lie dead. Suddenly, more
            orcs rush out of their
            sleeping quarters,
            disturbed by the noise.
        """)
        str("dungeon.orc_nest.slain", "You have slain the orcs.")
        str("dungeon.thun_riddle.prompt", "Answer: ")
        str("dungeon.thun_riddle.pass", "- Well done. Pass.")
        str("dungeon.thun_riddle.fail", """
            - FOOLS! Meet thy doom!
            at a gesture from him,
            something evil appears.
        """)
        str("dungeon.thun_riddle1.intro", """
            You stand before a
            mage dressed entirenly
            in red.

            - Answer this, fools, or
              die where you stand!
        """)
        str("dungeon.thun_riddle1.riddle", """
            'Between fire and ice,
            who ends the fight?
            Who stands between those
            two? Not red, not white
            but blue!
        """)
        str("dungeon.thun_riddle2.intro", """
            You stand before a
            mage dressed entirenly
            in green.

            - Answer this, fools, or
              die where you stand!
        """)
        str("dungeon.thun_riddle2.riddle", """
            'Siblings we are,
            I'm black and she's
            white. Each dreary
            evening, I swallow her
            light.'
        """)
        str("dungeon.thun_riddle3.intro", """
            You stand before a
            mage dressed entirenly
            in blue.

            - Answer this, fools, or
              die where you stand!
        """)
        str("dungeon.thun_riddle3.riddle", """
            'Tall as a house,
            round as a cup, and
            all the king's horses
            can't draw me up! What
            am I?'
        """)
        str("dungeon.find_object.found", """
            You find a
            {name}.

            (T)ake it
            (L)eave it
        """)
        str("dungeon.find_object.ok", "Ok.")
        str("dungeon.find_object.cant_carry", "You can't carry more.")
        str("dungeon.gold_chest.found", """
            You find a chest!

            (O)pen chest
            (L)eave
        """)
        str("dungeon.gold_chest.thief_check", """
            {thief_name} checks the
            chest for traps.
        """)
        str("dungeon.gold_chest.trap", "A TRAP!")
        str("dungeon.gold_chest.trap_gas_a", """
            Poisonous gas
            hisses out through
            hidden holes.
        """)
        str("dungeon.gold_chest.trap_gas_b", """
            The party coughs
            and retches.
        """)
        str("dungeon.gold_chest.trap_gas_c", "Finally. The gas clears.")
        str("dungeon.gold_chest.trap_pitfall_a", """
            A spiked pitfall
            opens in the floor
            beneath you.
        """)
        str("dungeon.gold_chest.trap_pitfall_b", """
            You climb out of it,
            badly wounded.
        """)
        str("dungeon.gold_chest.trap_boulder_a", """
            A large boulder
            drops from above!!!
        """)
        str("dungeon.gold_chest.trap_boulder_b", """
            Badly bruised, you
            try to stand up again.
        """)
        str("dungeon.gold_chest.contained", """
            The chest contained
            {gold_amount} pieces of gold.
        """)
        str("dungeon.colour_riddle.plaque", """
            A plaque here reads:
            ANTECHAMBER #{antechamber}
        """)
        str("dungeon.colour_riddle.squares", """
            Before you are seven
            squares of 20x20 feet.
            To get across this room
            you will need to step
            on one of them.
        """)
        str("dungeon.colour_riddle.menu", """
            1. Red Square
            2. Orange Square
            3. Yellow Square
            4. Green Square
            5. Blue Square
            6. Indigo Square
            7. Violet Square
        """)
        str("dungeon.colour_riddle.tingle", """
            You feel a slight
            tingle in your bodies,
            but otherwise, nothing
            happens.
        """)
        str("dungeon.colour_riddle.demon_appears", """
            With a horrible scream
            a shape of pure colour
            forms in thin air before
            you!
        """)
        str("dungeon.silver_sanctum.disappointment", """
            This room shines like
            silver. You can see your
            mirror image in the
            walls. After searching
            the room thorougly, you
            find nothing. No Silver
            Key, no treasure.
        """)
        str("dungeon.silver_sanctum.about_to_leave", """
            You curse the rouge who
            tricked you and are just
            about to leave...
        """)
        str("dungeon.silver_sanctum.find_paper", """
            ...when you spot a paper
            of some kind in a crack
            in the wall... You pull
            it out and read...
        """)
        str("dungeon.silver_sanctum.note", """
            'Guild Master: This is
            the proof that I, {mt_name}
            am worthy of the title
            Master Thief.'
                  - {mt_name}
        """)
        str("dungeon.hobgoblin_chief.curtain", """
            You stand before a
            silk curtain. You hear
            someone moving behind
            it. A voice calls:
            - What's the password?
        """)
        str("dungeon.hobgoblin_chief.password_prompt", "Password: ")
        str("dungeon.hobgoblin_chief.step_inside", """
            - Step inside and identify
            yourself. You do so. The
            hobgoblin chief eyes you
            suspiciously for a few
            seconds. Not hestitating
            a moment you attack.
        """)
        str("dungeon.hobgoblin_chief.guards", """
            - GUARDS! GUARDS!

            (Hobgoblin guards rush
             to confront you.)
        """)
        str("dungeon.hobgoblin_chief.silver_key", """
            You have killed the chief
            of the hobgoblin tribe.
            The precious Silver Key
            hangs around his neck.
            With trembling hands
            you pick it up!!!
        """)
        str("dungeon.loop.level_menu", """
            You are on level {level}.

            (D) Descend
            (A) Ascend
        """)
        str("dungeon.loop.enter_level", "You enter level {level}.")
        str("dungeon.loop.at_max_depth", """
            The cave doesn't
            go deeper.
        """)
        str("dungeon.loop.no_torches", """
            You have used your last
            torch. You can't go
            any further.
        """)
        str("dungeon.loop.leave_cavern", """
            You leave the
            hideous cavern!
        """)
        str("dungeon.loop.who_prompt", "Who? (1-{max}/Q/A)")
    }

    // -------------------------------------------------------------------------
    // Reusable scenes. Town services first, then dungeon encounters, then
    // dialog dispatchers. Bindings the C wrappers used (open_lo, who, ...)
    // come in via runScene().
    // -------------------------------------------------------------------------
    scenes {
        // -------- Town: hut greeter (T)alk / (L)eave -------------------------
        scene("hut", expects = arrayOf("who", "npc_job_sym")) {
            loop {
                clw()
                letBound("job",  "res_enum_name_by_sym", "job", v("npc_job_sym"))
                letBound("name", "res_enum",            "name", v("who"))
                screen("towne.hut.greeter")
                menu("menu") {
                    option("T") { runScene("speak.dispatch", "building" to "none", "town" to 0) }
                    option("L") { clw(); clearMap(); redrawMap(); breakOut() }
                }
            }
        }

        // -------- Town: rumour roll. 33 rumours with four NPC-tagged cases. --
        scene("rumour") {
            val pick = letInt("pick", engineInt("rumour_roll"))
            on(pick eq 12) {
                letBound("name1", "npc_name_at_role", "bandits_quest_giver"); text("towne.rumour.r12")
            } els {
                on(pick eq 13) {
                    letBound("name2", "npc_name_at_role", "rangers_quest_giver"); text("towne.rumour.r13")
                } els {
                    on(pick eq 25) {
                        letBound("name15", "npc_name_at_role", "crown_seer")
                        letBound("loc15",  "npc_loc_at_role",  "crown_seer")
                        text("towne.rumour.r25")
                    } els {
                        on(pick eq 27) {
                            letBound("name13", "npc_name_at_role", "temple_of_thun_seer")
                            letBound("loc13",  "npc_loc_at_role",  "temple_of_thun_seer")
                            text("towne.rumour.r27")
                        } els {
                            emitIndexed("towne.rumour.r{}", v("pick"))
                        }
                    }
                }
            }
        }

        // -------- Town: church (Heal/Raise/Energize/Leave) -------------------
        scene("church") {
            clearMap()
            setBackdrop("church")
            loop {
                clw(); statusLine()
                screen("towne.church.intro")
                menu("menu") {
                    option("L") { clw(); clearMap(); breakOut() }
                    option("E") { runScene("church.energize") }
                    option("R") { runScene("church.raise") }
                    option("H") { runScene("church.heal") }
                }
            }
        }
        scene("church.energize") {
            clw(); screen("towne.church.who")
            menu {
                digitRange(1, state.partyMemberMax) { who ->
                    on(!state.member(who).isSpellcaster()) {
                        clw(); screen("towne.church.not_spellcaster"); pause(1000)
                    } els {
                        on(state.member(who).mp eq state.member(who).wis) {
                            clw(); screen("towne.church.no_need_sp"); pause(2000)
                        } els {
                            // cost = lv * (wi - ma) * 2
                            val cost = letInt("cost",
                                state.member(who).level * (state.member(who).wis - state.member(who).mp) * 2)
                            screen("towne.church.cost2")
                            menu("pn_menu") {
                                option("P") {
                                    clw()
                                    on(state.gold lt v("cost")) {
                                        screen("towne.church.no_gold"); pause(1000)
                                    } els {
                                        state.gold -= v("cost")
                                        state.member(who).setField("magic", state.member(who).wis)
                                        clw(); screen("towne.church.restored"); pause(1000)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        scene("church.raise") {
            clw(); screen("towne.church.who")
            menu {
                digitRange(1, state.partyMemberMax) { who ->
                    on(state.member(who).isAlive()) {
                        clw()
                        letBound("pro_sub", "pronoun", who, "subject")
                        screen("towne.church.alive"); pause(1200)
                    } els {
                        clw()
                        val cost = letInt("cost", int(650) * state.member(who).level)
                        screen("towne.church.cost2")
                        menu("pn_menu") {
                            option("P") {
                                clw()
                                on(state.gold lt v("cost")) {
                                    screen("towne.church.no_gold"); pause(1000)
                                } els {
                                    state.gold -= v("cost")
                                    state.member(who).setField("hit_points", 1)
                                    state.member(who).setField("dead", 0)
                                    state.pl += 1
                                    clw()
                                    letBound("pro_obj", "pronoun", who, "object")
                                    screen("towne.church.raised"); pause(1000)
                                }
                            }
                        }
                    }
                }
            }
        }
        // Experiment 1 + 2: typed member field writes + typed bindings.
        // Compare to the old form (still used in earlier scenes):
        //   state.member(who).setField("hit_points", state.member(who).maxHp)
        //   letBound("pro_sub", "pronoun", who, "subject")
        //   screen("towne.church.no_healing")
        // becomes:
        //   m.hp set m.maxHp
        //   val proSub = bound("pro_sub", pronoun(who, SUBJECT))
        //   screen("towne.church.no_healing")  // still references {pro_sub}
        scene("church.heal") {
            clw(); screen("towne.church.who")
            menu {
                digitRange(1, state.partyMemberMax) { who ->
                    val m = state.member(who)
                    on(m.isDead()) {
                        clw(); screen("towne.church.corpse"); pause(1200); breakOut()
                    }
                    on(m.hp eq m.maxHp) {
                        clw()
                        bound("pro_sub", pronoun(who, PronounKind.SUBJECT))
                        screen("towne.church.no_healing"); pause(1000)
                    } els {
                        val cost = letInt("cost", (m.maxHp - m.hp) * 7)
                        clw(); screen("towne.church.cost")
                        menu("pn_menu") {
                            option("P") {
                                on(state.gold lt v("cost")) {
                                    screen("towne.church.no_gold2"); pause(1000)
                                } els {
                                    clw()
                                    state.gold -= v("cost")
                                    bound("pro_obj", pronoun(who, PronounKind.OBJECT))
                                    screen("towne.church.healed"); pause(1000)
                                    m.hp set m.maxHp
                                }
                            }
                        }
                    }
                }
            }
        }

        // -------- Town: smith --------------------------------------------------
        scene("smith", expects = arrayOf("open_lo", "open_hi")) {
            on((state.hourOfDay lt v("open_lo")) or (state.hourOfDay gt v("open_hi"))) {
                clw(); screen("towne.smith.closed"); pause(1000)
            } els {
                clearMap(); setBackdrop("smith")
                loop {
                    clw(); statusLine(); screen("towne.smith.intro")
                    on(chance(9)) {
                        // 9% beer-offer side path: A=accept (3 tx + rumour),
                        // anything else = decline.
                        screen("towne.smith.beer_offer")
                        menu("beer_menu") {
                            option("A") {
                                clw(); screen("towne.smith.accept_1"); pause(2000)
                                screen("towne.smith.accept_2"); pause(2000)
                                screen("towne.smith.accept_3"); pause(2000)
                                clw(); engineCall("rumour")
                            }
                            default { screen("towne.smith.decline"); pause(1500) }
                        }
                    } els {
                        // Main shop loop: B(uy)/S(ell)/L(eave).
                        menu("shop_menu") {
                            option("L") { screen("towne.smith.bye"); clearMap(); breakOut() }
                            option("B") { runScene("smith.buy") }
                            option("S") { runScene("smith.sell") }
                        }
                    }
                }
            }
        }
        scene("smith.buy") {
            // A=armor (1..6), W=weapons paginated, default -> back.
            menu("buy_menu") {
                option("A") {
                    screen("towne.smith.armor_menu")
                    menu {
                        keyRange("1", "6") { pick ->
                            // pick=1..6 maps to leather..mithril_mail (ids 20..25).
                            val itemId = letInt("item_id", v("pick") + 19)
                            runScene("smith.purchase", "item_id" to v("item_id"))
                        }
                    }
                }
                option("W") { runScene("smith.weapons_paginated") }
                default { /* fall back to outer */ }
            }
        }
        scene("smith.weapons_paginated") {
            // Pagination loop. N/P flip page; any other key tries to map onto
            // a weapon id. Order matches legacy: 1,2,3,4,9,11,7,14,10,8,15,16.
            loop {
                clw(); screen("towne.smith.we_have")
                menu {
                    option("N") { engineCall("smith_weapon_page", "delta" to 1) }
                    option("P") { engineCall("smith_weapon_page", "delta" to -1) }
                    default {
                        val itemId = letInt("item_id", engineInt("smith_weapon_pick"))
                        on(v("item_id") gt 0) {
                            runScene("smith.purchase", "item_id" to v("item_id"))
                        }
                        breakOut()
                    }
                }
            }
        }
        scene("smith.purchase", expects = arrayOf("item_id")) {
            val price = letInt("price", engineInt("obcost", v("item_id")))
            screen("towne.smith.price")
            menu("pn_menu") {
                option("P") {
                    on(state.gold lt v("price")) {
                        screen("towne.smith.too_expensive"); pause(1000)
                    } els {
                        on(engineCheck("inv_full")) {
                            screen("towne.smith.cant_carry"); pause(1000)
                        } els {
                            state.gold -= v("price")
                            engineCall("inv_add", v("item_id"))
                            screen("towne.smith.deal"); pause(800)
                        }
                    }
                }
            }
        }
        scene("smith.sell") {
            invList()
            screen("towne.smith.sell_prompt")
            engineCall("input_inv_letter")  // -> binds slot, item_id
            on(engineCheck("smith_sell_invalid")) {
                screen("towne.smith.wrong_kind"); pause(1000)
            } els {
                // Bargain loop: Y sells at half-price; B rolls 0..99 with 76/90
                // thresholds; N walks; any other key re-prompts at same price.
                loop {
                    clw(); screen("towne.smith.offer")
                    menu("bargain_opt") {
                        option("Y") {
                            state.gold += engineInt("smith_sell_price")
                            engineCall("inv_remove", v("slot"))
                            screen("towne.smith.sold"); pause(1000); breakOut()
                        }
                        option("B") {
                            val roll = letInt("roll", random(0..99))
                            on(v("roll") gte 90) {
                                screen("towne.smith.deal")
                                state.gold += engineInt("smith_sell_price_high")
                                engineCall("inv_remove", v("slot")); breakOut()
                            } els {
                                on(v("roll") gt 75) {
                                    screen("towne.smith.no_thanks"); pause(1000); breakOut()
                                } els {
                                    /* loop again at same price */
                                }
                            }
                        }
                        option("N") { screen("towne.smith.no_thanks"); pause(800); breakOut() }
                    }
                }
            }
        }

        // -------- Town: tavern -------------------------------------------------
        scene("tavern", expects = arrayOf("open_lo", "open_hi")) {
            on((state.hourOfDay lt v("open_lo")) or (state.hourOfDay gt v("open_hi"))) {
                clw(); screen("towne.tavern.closed"); pause(1000)
            } els {
                clearMap(); setBackdrop("tavern")
                // priest_bonus = sum over party of (charisma/10) for each priest.
                val priestBonus = letInt("priest_bonus", 0)
                forEachMember { m ->
                    on(m.isClass("priest")) {
                        mutate("priest_bonus", "add", m.cha / 10)
                    }
                }
                // One-shot scruffy dealer: 5%+priest_bonus chance, picks a magic
                // item and offers it for sale.
                on(chance(int(5) + v("priest_bonus"))) {
                    runScene("tavern.scruffy")
                }
                loop {
                    clw(); statusLine(); screen("towne.tavern.intro")
                    menu("menu") {
                        option("L") { clw(); clearMap(); breakOut() }
                        option("S") { runScene("speak.dispatch", "building" to "tavern", "town" to 0) }
                        option("T") { runScene("tavern.story") }
                        option("R") { runScene("tavern.rumours") }
                        option("O") { runScene("tavern.order") }
                    }
                }
            }
        }
        scene("tavern.scruffy") {
            screen("towne.tavern.scruffy_1")
            screen("towne.tavern.scruffy_2")
            val itemId = letInt("item_id", random(0..16) + 1)
            // Item ids 1..17 in legacy table; switch maps onto magic items.
            engineCall("scruffy_offer", v("item_id"))
            screen("towne.tavern.scruffy.offer")
            menu("pn_menu") {
                option("P") {
                    on(state.gold lt engineInt("obcost", v("item_id"))) {
                        screen("towne.tavern.scruffy.broke"); pause(1000)
                    } els {
                        on(engineCheck("inv_full")) {
                            screen("towne.tavern.scruffy.full"); pause(1000)
                        } els {
                            state.gold -= engineInt("obcost", v("item_id"))
                            engineCall("inv_add", v("item_id"))
                            screen("towne.tavern.scruffy.deal")
                        }
                    }
                }
                default { screen("towne.tavern.scruffy.no") }
            }
        }
        scene("tavern.story") {
            // T=truthful, B=boastful. Boast roll vs charisma; failed boast
            // means the patrons throw you out (charisma -10, breaks tavern).
            screen("towne.tavern.story_who")
            menu {
                digitRange(1, state.partyMemberMax) { who ->
                    on(state.member(who).isDead()) {
                        screen("towne.tavern.dead_story"); pause(1500)
                    } els {
                        screen("towne.tavern.stands_up"); pause(800)
                        screen("towne.tavern.story_menu")
                        menu("story_menu") {
                            option("T") { runScene("tavern.story.tell", "who" to who, "is_boast" to 0) }
                            option("B") { runScene("tavern.story.tell", "who" to who, "is_boast" to 1) }
                        }
                    }
                }
            }
        }
        scene("tavern.story.tell", expects = arrayOf("who", "is_boast")) {
            val score = letInt("score", state.member(v("who")).cha + (v("is_boast") * random(0..40)))
            on(v("score") gt 90) { screen("towne.tavern.st_superb")  } els {
            on(v("score") gt 70) { screen("towne.tavern.st_vgood")  } els {
            on(v("score") gt 50) { screen("towne.tavern.st_nice")   } els {
            on(v("score") gt 30) { screen("towne.tavern.st_bored")  } els {
                on(v("is_boast") eq 1) {
                    screen("towne.tavern.liar_kicked")
                    state.member(v("who")).setField("charisma",
                        state.member(v("who")).cha - 10)
                    screen("towne.tavern.thrown_out"); breakOut()
                } els {
                    screen("towne.tavern.st_asleep")
                }
            } } } }
        }
        scene("tavern.rumours") {
            // Up to 5 rumour rolls in a session; 0..4 prelude flavour, then quiet.
            val count = letInt("count", engineInt("tavern_rumour_count"))
            on(v("count") gte 5) {
                screen("towne.tavern.quiet")
            } els {
                val intro = letInt("intro", random(0..4))
                emitIndexed("towne.tavern.rum.{}", v("intro"))   // rum_old/bard/halfling/keeper/bargirl
                engineCall("rumour")
                engineCall("tavern_rumour_inc")
            }
        }
        scene("tavern.order") {
            screen("towne.tavern.order_header")
            menu("order_menu") {
                option("A") { runScene("tavern.order.serve", "price" to 2) }
                option("B") { runScene("tavern.order.serve", "price" to 3) }
                option("C") { runScene("tavern.order.serve", "price" to 4) }
                option("D") { runScene("tavern.order.serve", "price" to 1) }
                default    { screen("towne.tavern.no_drink") }
            }
        }
        scene("tavern.order.serve", expects = arrayOf("price")) {
            on(state.gold lt v("price")) {
                screen("towne.tavern.no_gold"); pause(1000)
            } els {
                state.gold -= v("price")
                forEachMember { m ->
                    val gain = letInt("gain", v("price") / 2 + 1)
                    m.setField("hit_points", m.hp + v("gain"))
                    engineCall("clamp_hp", m.idx)
                }
                on(chance(50)) { screen("towne.tavern.aah") } els { screen("towne.tavern.laughed_at") }
            }
        }

        // -------- Town: inn ---------------------------------------------------
        scene("inn") {
            clearMap(); setBackdrop("inn")
            loop {
                clw(); statusLine(); screen("towne.inn.greeter")
                // cost = 15 * level[1]^2 (shared supper/lodging price).
                val cost = letInt("cost", int(15) * state.member(1).level * state.member(1).level)

                screen("towne.inn.supper_cost")
                menu {
                    option("Y") {
                        on(state.gold gte v("cost")) {
                            state.gold -= v("cost"); screen("towne.inn.supper_served")
                        } els { screen("towne.inn.no_gold_kick") }
                    }
                    option("N") { /* skip */ }
                }

                screen("towne.inn.drink_q")
                menu {
                    option("Y") { runScene("inn.drink") }
                    option("N") { /* skip */ }
                }

                screen("towne.inn.lodging_q")
                menu("room_menu") {
                    option("T") {
                        on(state.gold lt v("cost")) {
                            screen("towne.inn.no_gold_kick"); pause(1000); breakOut()
                        } els {
                            state.gold -= v("cost")
                            screen("towne.inn.bedtime"); pause(2500)
                            screen("towne.inn.zzz");     pause(2500)
                            state.hourOfDay.set(5)
                            state.hoursRested.set(14)
                            forEachMember { m ->
                                val gain = letInt("gain", m.maxHp / 4)
                                m.setField("hit_points", m.hp + v("gain"))
                                engineCall("clamp_hp", m.idx)
                            }
                            screen("towne.inn.morning"); pause(1500); breakOut()
                        }
                    }
                    default { runScene("inn.chitchat"); breakOut() }
                }
            }
        }
        scene("inn.drink") {
            screen("towne.inn.drink_menu")
            menu {
                option("A") { runScene("tavern.order.serve", "price" to 2) }
                option("B") { runScene("tavern.order.serve", "price" to 3) }
                option("C") { runScene("tavern.order.serve", "price" to 4) }
                option("D") { runScene("tavern.order.serve", "price" to 1) }
                option("E") { runScene("tavern.order.serve", "price" to 5) }
                default    { screen("towne.tavern.no_drink") }
            }
        }
        scene("inn.chitchat") {
            loop {
                screen("towne.inn.chit_menu")
                menu("chit_menu") {
                    option("C") { runScene("speak.dispatch", "building" to "inn", "town" to 0) }
                    option("L") { screen("towne.inn.leaves"); breakOut() }
                    default    { screen("towne.inn.leaves"); breakOut() }
                }
            }
        }

        // -------- Town: merchant ---------------------------------------------
        scene("merchant", expects = arrayOf("open_lo", "open_hi")) {
            on((state.hourOfDay lt v("open_lo")) or (state.hourOfDay gt v("open_hi"))) {
                clw(); screen("towne.merchant.closed"); pause(1000)
            } els {
                clearMap(); setBackdrop("merchant")
                loop {
                    clw(); statusLine(); screen("towne.merchant.intro")
                    menu("menu") {
                        option("L") { clw(); clearMap(); breakOut() }
                        option("S") { runScene("merchant.sell") }
                        option("B") { runScene("merchant.buy") }
                    }
                }
            }
        }
        scene("merchant.sell") {
            invList()
            screen("towne.merchant.sell_prompt")
            engineCall("input_inv_letter")
            on(engineCheck("merchant_sell_invalid")) {
                screen("towne.merchant.wrong_kind"); pause(1000)
            } els {
                screen("towne.merchant.sell_offer")
                menu {
                    option("Y") {
                        state.gold += engineInt("merchant_sell_price")
                        engineCall("inv_remove", v("slot"))
                        screen("towne.merchant.sold"); pause(800)
                    }
                    option("N") { /* walk */ }
                }
            }
        }
        scene("merchant.buy") {
            loop {
                clw(); screen("towne.merchant.buy_menu")
                menu {
                    // A..F = torch/food/blanket/tent/rope/potion (item ids 31..36)
                    keyRange("A", "F") { pick ->
                        val itemId = letInt("item_id", v("pick") + 30)
                        runScene("merchant.buy.item", "item_id" to v("item_id"))
                    }
                    option("G") { runScene("merchant.buy.donkey") }
                    option("L") { breakOut() }
                }
            }
        }
        scene("merchant.buy.item", expects = arrayOf("item_id")) {
            // Torch/food multi-buy with caps; everything else single-purchase.
            on((v("item_id") eq 31) or (v("item_id") eq 32)) {
                runScene("merchant.buy.bulk", "item_id" to v("item_id"))
            } els {
                val price = letInt("price", engineInt("obcost", v("item_id")))
                screen("towne.merchant.price")
                menu("pn_menu") {
                    option("P") {
                        on(state.gold lt v("price")) {
                            screen("towne.merchant.too_expensive"); pause(1000)
                        } els {
                            on(engineCheck("inv_full")) {
                                screen("towne.merchant.cant_carry"); pause(1000)
                            } els {
                                state.gold -= v("price")
                                engineCall("inv_add", v("item_id"))
                                screen("towne.merchant.deal"); pause(800)
                            }
                        }
                    }
                }
            }
        }
        scene("merchant.buy.bulk", expects = arrayOf("item_id")) {
            // item_id = 31 (torch, cap 80) or 32 (food, cap 150*(donkeys+1))
            val cap = letInt("cap", engineInt("merchant_bulk_cap", v("item_id")))
            screen("towne.merchant.how_many")
            inputNumber("qty", v("cap"))
            val price = letInt("price", engineInt("obcost", v("item_id")) * v("qty"))
            screen("towne.merchant.price")
            menu("pn_menu") {
                option("P") {
                    on(state.gold lt v("price")) {
                        screen("towne.merchant.too_expensive"); pause(1000)
                    } els {
                        state.gold -= v("price")
                        engineCall("merchant_bulk_apply", v("item_id"), v("qty"))
                        screen("towne.merchant.deal"); pause(800)
                    }
                }
            }
        }
        scene("merchant.buy.donkey") {
            // Donkey extends food carry-cap; only one (or whatever) per buy.
            val price = letInt("price", engineInt("obcost", "donkey"))
            screen("towne.merchant.desc.donkey")
            screen("towne.merchant.price")
            menu("pn_menu") {
                option("P") {
                    on(state.gold lt v("price")) {
                        screen("towne.merchant.too_expensive"); pause(1000)
                    } els {
                        on(engineCheck("inv_full")) {
                            screen("towne.merchant.cant_carry"); pause(1000)
                        } els {
                            state.gold -= v("price")
                            engineCall("inv_add", "donkey")
                            screen("towne.merchant.deal"); pause(800)
                        }
                    }
                }
            }
        }

        // -------- Hunt --------------------------------------------------------
        scene("hunt") {
            // Member-pick with dead retry; ranger-only; time gate; chance roll.
            loop {
                clw(); screen("hunt.who")
                menu {
                    digitRange(1, state.partyMemberMax) { who ->
                        on(state.member(who).isDead()) {
                            clw(); screen("hunt.dead"); pause(1000)
                        } els {
                            on(!state.member(who).isClass("ranger")) {
                                clw(); screen("hunt.not_ranger"); pause(1000); breakOut()
                            } els {
                                on(state.timeLeft lt 5) {
                                    clw(); screen("hunt.too_few_moves"); pause(1000); breakOut()
                                } els {
                                    state.timeLeft -= 5
                                    letBound("who_name", "pa_name", who)
                                    screen("hunt.tries"); pause(800)
                                    on(chance(45)) {
                                        val gain = letInt("fdplus",
                                            random(int(10), int(19)) + state.member(who).level)
                                        state.food += v("fdplus")
                                        screen("hunt.succeeds")
                                    } els {
                                        screen("hunt.fails")
                                    }
                                    waitInput(); clw(); breakOut()
                                }
                            }
                        }
                    }
                }
            }
        }

        // -------- Lodge -- bargirl (per-charge service) and per-job text ----
        scene("lodge.bargirl", expects = arrayOf("price")) {
            clw(); screen("speak.lodge.jbargirl.offer")
            menu {
                option("Y") {
                    on(state.gold lt v("price")) {
                        screen("speak.lodge.jbargirl.broke"); pause(1000)
                    } els {
                        state.gold -= v("price")
                        screen("speak.lodge.jbargirl.spent")
                        // Lose 1 hp per member who's above 1 hp (legacy quirk).
                        forEachMember { m ->
                            on(m.hp gt 1) { m.setField("hit_points", m.hp - 1) }
                        }
                    }
                }
                option("N") { /* walk */ }
            }
        }
        scene("lodge.nonbargirl", expects = arrayOf("job_sym")) {
            // Per-job emit dispatch -- speak.lodge.j{merchant,ranger,...}
            emitIndexed("speak.lodge.j{}", v("job_sym"))
        }

        // -------- Subs: equip / unequip --------------------------------------
        scene("subs.unequip", expects = arrayOf("kind")) {
            // kind = "weapon" or "armor"
            clw(); screen("subs.who_prompt")
            menu {
                digitRange(1, state.partyMemberMax) { who ->
                    val cur = letInt("cur", state.member(who)[v("kind").toString()])
                    on(v("cur") eq 0) {
                        letBound("pro_sub", "pronoun", who, "subject")
                        emitIndexed("subs.equipment.no_{}", v("kind"))
                    } els {
                        val slot = letInt("slot", engineInt("inv_find_empty"))
                        on(v("slot") eq 0) {
                            screen("subs.no_room")
                        } els {
                            letBound("name", "object", v("cur"))
                            screen("subs.equipment.removed")
                            engineCall("pa_set_inventory", v("slot"), v("cur"))
                            state.member(who).setField(v("kind").toString(), 0)
                        }
                    }
                }
                default { clw() }
            }
        }
        scene("subs.equip", expects = arrayOf("kind", "kind_id")) {
            clw(); invList()
            engineCall("input_inv_letter")  // -> binds slot, oc
            on(v("slot") eq 0) { clw() } els {
                on(!engineCheck("item_is_equip_kind", v("oc"), v("kind_id"))) {
                    clw(); emitIndexed("subs.equipment.invalid_{}", v("kind"))
                    pause(1000); clw()
                } els {
                    clw(); screen("subs.who_prompt")
                    menu {
                        digitRange(1, state.partyMemberMax) { who ->
                            on(state.member(who).isDead()) { clw(); screen("subs.dead") } els {
                                val temp = letInt("temp", state.member(who)[v("kind").toString()])
                                state.member(who).setField(v("kind").toString(), v("oc"))
                                engineCall("pa_set_inventory", v("slot"), v("temp"))
                                clw(); emitIndexed("subs.equipment.changed_{}", v("kind")); pause(1000); clw()
                            }
                        }
                        default { clw() }
                    }
                }
            }
        }

        // -------- Rest --------------------------------------------------------
        scene("rest") {
            // Early gates.
            on(state.hoursRested gt 0) {
                clw(); screen("rest.not_tired"); returnFromScript()
            }
            on(state.aliveCount eq 0) { returnFromScript() }
            on(engineCheck("inv_has", "tent")) { setBackdrop("tent") } els { setBackdrop("notent") }

            // Pick a guard (or no guard).
            screen("rest.camp_intro")
            val guard = letInt("guard", -1)
            menu {
                option("Y") {
                    loop {
                        clw(); screen("rest.who")
                        menu {
                            digitRange(1, state.partyMemberMax) { who ->
                                on(state.member(who).isDead()) {
                                    clw(); screen("rest.dead"); pause(1000)
                                } els {
                                    mutate("guard", "set", who); breakOut()
                                }
                            }
                            default { clw(); screen("rest.not_valid"); pause(1000) }
                        }
                    }
                }
                option("N") { /* no guard */ }
            }
            on(v("guard") gte 1) { screen("rest.stands_guard") }
            screen("rest.zzz"); pause(2000)

            // Temperature roll.
            val temp = letInt("temp", random(int(1), int(15)))
            on((v("temp") gte 6) and (v("temp") lte 10)) {
                on(!engineCheck("inv_has", "blanket") and !engineCheck("inv_has", "tent")) {
                    screen("rest.temp_low"); screen("rest.no_gear_5")
                    forEachMember { m -> m.setField("hit_points", m.hp - 5); engineCall("clamp_hp", m.idx) }
                }
            }
            on(v("temp") gt 10) {
                on(!engineCheck("inv_has", "tent")) {
                    screen("rest.temp_very_low"); screen("rest.no_gear_10")
                    forEachMember { m -> m.setField("hit_points", m.hp - 5); engineCall("clamp_hp", m.idx) }
                }
            }

            // Hours pass.
            engineCall("advance_hour", "amount" to int(8))
            state.hoursRested.set(14)
            mutate("food", "sub", v("food_per_member") * state.pl)
            engineCall("clamp_food_min", 0)

            // Healing per non-guard non-dead member.
            forEachMember { m ->
                on(m.idx ne v("guard") and !m.isDead()) {
                    m.setField("hit_points", m.hp + 15); engineCall("clamp_hp", m.idx)
                }
            }

            // Ambush vs well-rested.
            on(chance(19)) {
                screen("rest.ambush")
                on(v("guard") eq -1) {
                    val victim = letInt("victim", random(int(1), state.partyMemberMax))
                    letBound("victim_name", "pa_name", v("victim"))
                    screen("rest.surprise_kill")
                    state.member(v("victim")).setField("hit_points", 0)
                    state.member(v("victim")).setField("dead", 1)
                    state.pl -= 1
                }
                encounter("kobold", tier = "weak")
            } els {
                screen("rest.well_rested"); clearMap()
            }
            redrawMap()
        }

        // -------- Terrain transition narration -------------------------------
        scene("terrain.transition", expects = arrayOf("old", "new")) {
            on((v("old") ne v("new")) and chance(9)) {
                clw()
                // Tile-byte switch onto narration emit.
                emitIndexed("terrain.{}", v("new"))     // plainland/hut/towne/foothills/desert/swamp/forest/castle
                screen("terrain.blank")
            }
        }

        // -------- Chitchat (subject x gender x job) --------------------------
        scene("speak.chitchat", expects = arrayOf("female", "job_sym")) {
            val subj = letInt("subj", random(0..2))
            clw()
            on(v("subj") eq 0) {
                on(v("female") eq 1) { screen("speak.chit.well_f") } els { screen("speak.chit.well_m") }
            }
            on(v("subj") eq 1) { screen("speak.chit.weather") }
            on(v("subj") eq 2) {
                on(v("female") eq 1) { screen("speak.chit.health_f") } els { screen("speak.chit.health_m") }
            }
            pause(800); screen("speak.chit.added_blank")
            runScene("speak.chit_reply", "subj" to v("subj"), "job_sym" to v("job_sym"))
            screen("speak.chit.added_blank"); waitInput()
        }
        scene("speak.chit_reply", expects = arrayOf("subj", "job_sym")) {
            on(v("subj") eq 0) { emitIndexed("speak.chit.reply.well.j{}",    v("job_sym")) }
            on(v("subj") eq 1) { emitIndexed("speak.chit.reply.weather.j{}", v("job_sym")) }
            on(v("subj") eq 2) { emitIndexed("speak.chit.reply.health.j{}",  v("job_sym")) }
        }
        scene("speak.people", expects = arrayOf("job_sym")) {
            emitIndexed("speak.people.j{}", v("job_sym"))
        }
        scene("speak.magic", expects = arrayOf("job_sym")) {
            emitIndexed("speak.magic.j{}", v("job_sym"))
        }

        // ---------------------------------------------------------------------
        // Dungeon encounter scenes (the 28 cases the random fill or per-floor
        // wiring can `run`). Bodies follow the legacy script shard verbatim
        // where the data shape is mechanical; mission-flavour scenes inline
        // their narration.
        // ---------------------------------------------------------------------

        scene("nothing_a") { screen("dungeon.nothing_a") }
        scene("nothing_b") { screen("dungeon.nothing_b") }

        scene("spectrum_message") {
            clw(); screen("dungeon.spectrum_message")
        }

        scene("fire_shield") {
            clw(); screen("dungeon.fire_shield"); clw()
            reward { items("fireshield") }     // routes via party_award_special
            flag("hermit.troll_tooth") set "fire_shield_acquired"
        }
        scene("bracelets") {
            clw(); screen("dungeon.bracelets"); clw()
            reward { items("bracelets") }
            flag("bracelets.thun") set "bracelets_taken"
        }
        scene("nolrim_tomb") {
            clw(); screen("dungeon.nolrim_tomb"); clw()
            flag("mastercrown") set "dagger_taken"
        }

        scene("encounter_weak_a") {
            encounter("kobold", tier = "weak"); clearMap(); setBackdrop("insidecave"); clw()
        }
        scene("encounter_weak_b") {
            encounter("kobold", tier = "weak"); clearMap(); setBackdrop("insidecave"); clw()
        }
        scene("encounter_deep") {
            encounter("xvart", tier = "deep"); clearMap(); setBackdrop("insidecave"); clw()
        }

        scene("gold") {
            val n = letInt("amount", random(40..339))
            screen("dungeon.gold")
            state.gold += v("amount")
        }
        scene("torches") {
            clw(); val n = letInt("n", random(2..4))
            screen("dungeon.torches")
            state.torches += v("n")
        }

        scene("fire_shield_guardian") {
            clw(); screen("dungeon.fire_shield_guardian"); clw()
            encounter("guardian", mandatory = true)
            clearMap(); setBackdrop("insidecave"); clw(); waitInput()
        }
        scene("master_crown") {
            clw(); screen("dungeon.master_crown.intro"); clw()
            screen("dungeon.master_crown.warrior_alive"); clw()
            encounter("long_dead_warrior", mandatory = true)
            clearMap(); setBackdrop("insidecave"); clw()
            flag("mastercrown") set "crown_taken"
            reward { items("mastercrown") }
        }
        scene("hobgoblin_patrol") {
            clw(); screen("dungeon.hobgoblin_patrol.attack"); clw()
            encounter("hobgoblin", mandatory = true)
            clearMap(); setBackdrop("insidecave"); clw()
            on(chance(39)) {
                clw(); screen("dungeon.hobgoblin_patrol.password"); clw()
            }
        }
        scene("royal_guard1") {
            clw(); screen("dungeon.royal_guard1")
            encounter("hobgoblin_skeleton", mandatory = true)
            clearMap(); setBackdrop("insidecave"); clw()
        }
        scene("royal_guard2") {
            clw(); screen("dungeon.royal_guard2")
            encounter("undead_hobgoblin", mandatory = true)
            clearMap(); setBackdrop("insidecave"); clw()
        }
        scene("orc_nest") {
            clw(); screen("dungeon.orc_nest.guarding")
            encounter("orc", mandatory = true)
            clearMap(); setBackdrop("insidecave"); clw()
            screen("dungeon.orc_nest.more")
            encounter("orc", mandatory = true)
            encounter("orc", mandatory = true)
            clearMap(); setBackdrop("insidecave"); clw()
            screen("dungeon.orc_nest.slain")
        }
        scene("erethor_priests") {
            clw(); screen("dungeon.erethor_priests")
            encounter("erethor_priest", mandatory = true)
            clearMap(); setBackdrop("insidecave"); clw()
        }

        // -------- Thun riddles. Three near-identical mage-and-riddle scenes --
        // -------- with different answers and miss monsters. -------------------
        scene("thun_riddle1") {
            clw(); screen("dungeon.thun_riddle1.intro"); clw()
            screen("dungeon.thun_riddle1.riddle")
            text("dungeon.thun_riddle.prompt")
            inputString(maxLen = 10, accept = "water",
                match = { clw(); screen("dungeon.thun_riddle.pass") },
                miss  = {
                    clw(); screen("dungeon.thun_riddle.fail")
                    encounter("norker", mandatory = true)
                    clw(); clearMap(); setBackdrop("insidecave")
                })
        }
        scene("thun_riddle2") {
            clw(); screen("dungeon.thun_riddle2.intro"); clw()
            screen("dungeon.thun_riddle2.riddle")
            text("dungeon.thun_riddle.prompt")
            inputString(maxLen = 10, accept = listOf("night", "darkness"),
                match = { clw(); screen("dungeon.thun_riddle.pass") },
                miss  = { clw(); screen("dungeon.thun_riddle.fail")
                          encounter("voodoo_lord", mandatory = true); clw() })
        }
        scene("thun_riddle3") {
            clw(); screen("dungeon.thun_riddle3.intro"); clw()
            screen("dungeon.thun_riddle3.riddle")
            text("dungeon.thun_riddle.prompt")
            inputString(maxLen = 10, accept = listOf("well", "a well"),
                match = { clw(); screen("dungeon.thun_riddle.pass") },
                miss  = { clw(); screen("dungeon.thun_riddle.fail")
                          encounter("archdevil", mandatory = true); clw() })
        }

        // -------- find_object: 4-tier sub-roll then Take/Leave menu ----------
        scene("find_object") {
            val roll = letInt("roll", random(0..4))
            on(v("roll") gt 0) {
                val tier = letInt("tier", random(0..2))
                on(v("tier") eq 0) { mutate("item_id", "set", random(0..13) + 1) }
                on(v("tier") eq 1) { mutate("item_id", "set", random(0..6)  + 36) }
                on(v("tier") eq 2) { mutate("item_id", "set", random(0..7)  + 39) }
            } els {
                mutate("item_id", "set", random(0..11) + 100)
            }
            letBound("name", "object", v("item_id"))
            screen("dungeon.found")
            menu {
                option("T") {
                    invAdd(v("item_id"),
                        added = { screen("dungeon.ok"); pause(800) },
                        full  = { clw(); screen("dungeon.cant_carry"); pause(800) })
                }
            }
        }

        // -------- gold_chest: 4/10 trap; thief halves it --------------------
        scene("gold_chest") {
            screen("dungeon.gold_chest.found")
            menu {
                option("O") {
                    val trapChance = letInt("trap_chance", 4)
                    forEachMember { m ->
                        on(m.isClass("thief")) {
                            clw()
                            letBound("thief_name", "pa_name", m.idx)
                            screen("dungeon.gold_chest.thief_check"); pause(1000)
                            mutate("trap_chance", "set", 2); breakOut()
                        }
                    }
                    on(random(0..9) lt v("trap_chance")) {
                        clw(); screen("dungeon.gold_chest.trap"); pause(600)
                        val kind = letInt("kind", random(0..2))
                        on(v("kind") eq 0) {
                            screen("dungeon.gold_chest.trap_gas_a"); pause(1400)
                            screen("dungeon.gold_chest.trap_gas_b"); pause(600)
                            screen("dungeon.gold_chest.trap_gas_c")
                        }
                        on(v("kind") eq 1) {
                            screen("dungeon.gold_chest.trap_pitfall_a"); pause(1400)
                            screen("dungeon.gold_chest.trap_pitfall_b")
                        }
                        on(v("kind") eq 2) {
                            screen("dungeon.gold_chest.trap_boulder_a"); pause(1400)
                            screen("dungeon.gold_chest.trap_boulder_b")
                        }
                        forEachMember { m ->
                            engineCall("pm_add_hit_points", m.idx, m.level * -4)
                            engineCall("pm_hit_points_floor", m.idx, 1)
                        }
                        waitInput()
                    }
                    clw()
                    val amt = letInt("gold_amount",
                        random(0..99) + 320 + state.member(1).xp / 500)
                    screen("dungeon.gold_chest.contained")
                    state.gold += v("gold_amount")
                    waitInput()
                }
            }
        }

        // -------- Colour squares (Reebar levels 13..19) ----------------------
        scene("colour_riddle") {
            // Plaque shows ANTECHAMBER #(cl-12); pick one of 1..7 colours;
            // pass when cl + pick == 20; miss spawns a rainbow demon.
            clw()
            val ante = letInt("antechamber", state.cl - 12)
            screen("dungeon.colour_riddle.plaque"); clw()
            screen("dungeon.colour_riddle.squares"); clw()
            screen("dungeon.colour_riddle.menu")
            loop {
                menu {
                    keyRange("1", "7") { pick ->
                        on((state.cl + v("pick")) eq 20) {
                            clw(); screen("dungeon.colour_riddle.tingle")
                        } els {
                            clw(); screen("dungeon.colour_riddle.demon_appears")
                            encounter("rainbow_demon", mandatory = true)
                            clearMap(); setBackdrop("insidecave"); clw()
                        }
                        breakOut()
                    }
                }
            }
        }

        // -------- Silver Sanctum: planted "Master Thief was here" note -------
        // Experiment 2 demo with chained bindings -- the mt_slot binding
        // (an int from `res_npc_role`) feeds the mt_name lookup. The old
        // form passed `v("mt_slot")` from one letBound to the next via
        // string variable name; the new form uses Kotlin variable scoping
        // and types both engine calls.
        scene("silver_sanctum") {
            clw(); screen("dungeon.silver_sanctum.disappointment")
            clw(); screen("dungeon.silver_sanctum.about_to_leave")
            clw(); screen("dungeon.silver_sanctum.find_paper"); clw()
            val mtSlot = bound("mt_slot", resNpcRole("master_thief"))
            val mtName = bound("mt_name", resEnum("name", v("mt_slot")))
            screen("dungeon.silver_sanctum.note"); clw()  // text refers to {mt_name}
            flag("silver_key") set "note_found"
        }

        // -------- Tammy's prison (recurring until rescue) --------------------
        scene("tammys_prison") {
            clw()
            on(flag("peasant_wife") eq "accepted") {
                screen("dungeon.tammys_prison")
                flag("peasant_wife") set "rescued"
            }
        }

        // -------- Hobgoblin chief: password gate -----------------------------
        scene("hobgoblin_chief") {
            clw(); screen("dungeon.hobgoblin_chief.curtain"); clw()
            screen("dungeon.hobgoblin_chief.password_prompt")
            inputString(maxLen = 15, accept = "RISH EKKIN",
                match = {
                    clw(); screen("dungeon.hobgoblin_chief.step_inside")
                    encounter("hobgoblin_chief", mandatory = true)
                    clearMap(); setBackdrop("insidecave"); clw()
                },
                miss = {
                    clw(); screen("dungeon.hobgoblin_chief.guards"); pause(2000)
                    encounter("hobgoblin", mandatory = true)
                    encounter("hobgoblin_chief", mandatory = true)
                    clearMap(); setBackdrop("insidecave"); clw()
                })
            clw(); screen("dungeon.hobgoblin_chief.silver_key"); clw()
            reward { items("silverkey") }
            flag("silver_key") set "key_taken"
        }

        // -------- Lute Chamber finale ----------------------------------------
        // Option-1 demo: narration is unique to this scene and only ever
        // played here, so it lives inline as `chain(""" ... """, ...)`
        // blocks. Each block is one screen. No ids, no strings-block
        // entries -- the screenplay reads top-to-bottom in scene order.
        scene("lute_chamber") {
            chain(
                """
                This is the altar chamber
                of the temple. Hooded
                shapes kneel at the
                blood stained altar at
                the end of this chamber.
                """,
                """
                You rush to attack the
                priests...
                """,
                """
                They quickly turn around
                and draw their daggers.
                """,
                """
                The acolyte kneels before
                the altar in silent prayer
                while the others form a
                circle around him to
                protect him.
                """,
                """
                Suddenly there is an
                ear splitting bang
                and a ghastly creature
                emerges from the shadows
                of this uncanny sanctuary.
                """,
            )
            encounter("erethor",        mandatory = true)
            encounter("erethor_priest", mandatory = true)
            clearMap(); setBackdrop("insidecave"); clw()
            chain("""
                More priests rush to
                attack you...
            """)
            encounter("erethor_priest", mandatory = true)
            clearMap(); setBackdrop("insidecave"); clw()
            chain("""
                The last priest lies
                dead. You stand still
                for a moment, and then
                start searching the
                hall for the Lute.
            """)
            // Experiment 2: typed bindings. Compare to the old form
            // (`letBound("m3_name", "pa_name", 3)`) -- now both the engine
            // function and the kind argument are type-checked. The
            // returned placeholder strings ($m3Name, $m3Pronoun) feed
            // Kotlin string interpolation in the chain bodies below.
            val m3Name    = bound("m3_name",    paName(3))
            val m3Pronoun = bound("m3_pronoun", pronoun(3, PronounKind.OBJECT))
            // The pivot beat: pause-driven, no keypress -- the player
            // doesn't get to "press to continue" in the middle of Erethor
            // rising from the dead.
            cinematic(
                """
                $m3Name suddenly
                hears something behind
                $m3Pronoun!
                """,
                """
                You scream in terror
                when you see the source
                of that sound! ERETHOR
                HAS COME TO LIFE ONCE
                AGAIN!!!
                """,
                pauseMs = 1800,
            )
            encounter("erethor", mandatory = true)
            clearMap(); setBackdrop("insidecave"); clw()
            chain("""
                This time Erethor
                remains dead. His flesh
                blisters and bursts.
                A rotten smell spreads
                throughout the hall.
            """)
            val m4Name = bound("m4_name", paName(4))
            chain("""
                $m4Name finds the
                Lute on a pedestal,
                and you are all glad
                to leave this place.
            """)
            flag("erethor.lute") set "lute_taken"
            clw()
        }

        // ---------------------------------------------------------------------
        // Speak scenes (NPC dialogue). Most are short narration triggered by
        // a quest-flag dispatcher; some carry rewards.
        // ---------------------------------------------------------------------

        scene("speak.dispatch", expects = arrayOf("building", "town")) {
            // The C wrapper calls into here from hut/inn/tavern; it routes by
            // the resolved NPC role to the right speak.* sub-scene. For now
            // delegate to the engine to pick the sub-scene and chain into
            // speak_missions.* dispatchers.
            engineCall("speak_resolve", "building" to v("building"), "town" to v("town"))
        }

        scene("speak.merchant.first_visit") {
            screen("speak.merchant.offer"); screen("speak.merchant.bandits")
            flag("garth.bandits") set "accepted"
        }
        scene("speak.merchant.grateful") {
            screen("speak.merchant.grateful")
            reward {
                gold(2000); items("red_gem")
                cantCarryText("speak.merchant.cant_carry")
                pickedUpText("speak.merchant.picked_up")
            }
            flag("garth.bandits") set "closed"
        }

        scene("speak.fighter.grateful") {
            screen("speak.fighter.blade")
            flag("fighter.priest_book") set "fighter_grateful"
            reward {
                items("dancing_sword")
                cantCarryText("speak.fighter.cant_carry")
                pickedUpText("speak.fighter.picked_up")
            }
        }

        scene("speak.wizard.robbed_flow") {
            screen("speak.wizard.robbed")
            flag("fighter.priest_book") set "wizard_robbed"
        }

        scene("speak.erethor.first_visit") {
            screen("speak.erethor.rumour")
            screen("speak.erethor.demon")
            screen("speak.erethor.poor_man")
            screen("speak.erethor.trade")
            screen("speak.erethor.dangerous")
            journal("Erethor's temple is at 6x20")
            flag("erethor.lute") set "rumour_told"
        }
        scene("speak.erethor.delivered_lute") {
            screen("speak.erethor.hand_lute")
            screen("speak.erethor.note")
            screen("speak.erethor.note_deliver")
            journal("{minstrel_name} lives {minstrel_loc}")
            screen("speak.erethor.together")
            screen("speak.erethor.paper")
            flag("erethor.lute") set "note_received"
        }

        scene("speak.thief2.squeezed") {
            flag("silver_key") set "thief2_squeezed"
            screen("speak.thief2.push")
            screen("speak.thief2.smith")
            screen("speak.thief2.let_go")
        }

        scene("speak.husband.first_visit") {
            screen("speak.husband.depressed")
            screen("speak.husband.fortune")
            screen("speak.husband.cave")
            journal("orc cavern at 42x23")
            flag("peasant_wife") set "accepted"
        }
        scene("speak.husband.rescued") {
            screen("speak.husband.reunion"); screen("speak.husband.gives")
            flag("peasant_wife") set "paid"
            reward {
                gold(3000)
                items("admt_mail", "holy_handgrenade", "amber_potion", "fire_wand", "crystal_bowl")
                cantCarryText("speak.husband.cant_carry")
                pickedUpText("speak.husband.picked_up")
            }
        }

        scene("speak.brother.give_sword") {
            screen("speak.brother.give_sword")
            screen("speak.brother.produces")
            reward {
                items("magic_sword")
                cantCarryText("speak.brother.cant_carry")
                pickedUpText("speak.brother.picked_up")
            }
            flag("ranger.letter") set "delivered"
        }

        scene("speak.minstrel.gives_cloak") {
            screen("speak.minstrel.reads")
            reward {
                items("cloak_of_gods")
                cantCarryText("speak.minstrel.cant_carry")
                pickedUpText("speak.minstrel.picked_up")
            }
            screen("speak.minstrel.travel")
            flag("erethor.lute") set "cloak_received"
        }

        scene("speak.smith.after_thief2") {
            screen("speak.smith.ask")
            screen("speak.smith.sorry")
            screen("speak.smith.pursued")
            screen("speak.smith.clubbed")
            flag("silver_key") set "smith_confessed"
        }

        scene("speak.hermit.welcome_visit") {
            screen("speak.hermit.welcome")
            flag("hermit.troll_tooth") set "accepted"
        }
        scene("speak.hermit.tooth_trade") {
            screen("speak.hermit.tooth")
            journal("Fire Shield Dungeon at {cave_x}x{cave_y}")
            flag("hermit.troll_tooth") set "delivered"
        }

        scene("speak.ranger.first_visit") {
            screen("speak.ranger.intro"); screen("speak.ranger.deliver")
            journal("{ranger_name} lives {ranger_loc}")
            flag("ranger.letter") set "accepted"
        }

        scene("speak.priest.intro_flow") {
            screen("speak.priest.intro")
            screen("speak.priest.fled")
            screen("speak.priest.please")
            flag("fighter.priest_book") set "priest_visited"
        }

        scene("speak.nymph.know_flow") {
            screen("speak.nymph.know")
            screen("speak.nymph.location")
            journal("{seeker_name} lives {seeker_loc}")
            flag("nymph.seeker") set "told"
        }

        scene("speak.wizard3t.task1_flow") {
            screen("speak.wizard3t.intro"); screen("speak.wizard3t.agree"); screen("speak.wizard3t.task1")
            journal("kobold lair at {kobold_x}x{kobold_y}")
            flag("nymph.seeker") set "wizard3t_active"
            flag("mastercrown")  set "task1_assigned"
        }
        scene("speak.wizard3t.task2_flow") {
            screen("speak.wizard3t.pendant"); screen("speak.wizard3t.task2")
            flag("mastercrown") set "task2_assigned"
        }
        scene("speak.wizard3t.task3_flow") {
            screen("speak.wizard3t.task3_intro"); screen("speak.wizard3t.task3")
            journal("dwarf village at {dwarf_x}x{dwarf_y}")
            flag("mastercrown") set "task3_assigned"
        }
        scene("speak.wizard3t.grateful_flow") {
            screen("speak.wizard3t.grateful")
            journal("Master Crown dungeon at {crown_x}x{crown_y}")
            flag("mastercrown") set "crown_told"
        }

        scene("speak.jazzy_ogre.trade") {
            screen("speak.jazzy_ogre.hear")
            screen("speak.jazzy_ogre.trade")
            invList()
            inputItem("slot", "item_id")
            on(v("item_id") lt 35) {
                screen("speak.jazzy_ogre.refuse"); pause(1500)
            } els {
                screen("speak.jazzy_ogre.accept")
                letBound("cave_x", "wld_location_x", "ogre_cave")
                letBound("cave_y", "wld_location_y", "ogre_cave")
                journal("ogre cave at {cave_x}x{cave_y}")
                flag("jazzy_ogre") set "accepted"
                waitInput(); clw()
                setInventory(v("slot"), "blank")
            }
        }

        // ---------------------------------------------------------------------
        // Speak-mission dispatchers. These are flag -> scene routers; the C
        // wrapper called them once the player initiated a chat with an NPC
        // bound to a particular role.
        // ---------------------------------------------------------------------

        scene("dispatch.bandits_quest_giver") {
            on(flag("garth.bandits") eq "none") {
                clw(); runScene("speak.merchant.first_visit")
            }
            on(flag("garth.bandits") eq "recovered") {
                clw(); runScene("speak.merchant.grateful")
            }
        }
        scene("dispatch.rangers_quest_giver") {
            on(flag("ranger.letter") eq "none") {
                letBound("ranger_name", "npc_name_at_role", "ranger_brother")
                letBound("ranger_loc",  "npc_loc_at_role",  "ranger_brother")
                runScene("speak.ranger.first_visit")
            }
        }
        scene("dispatch.fighter_intro") {
            on(flag("fighter.priest_book") eq "wizard_robbed") {
                clw(); runScene("speak.fighter.grateful")
            }
        }
        scene("dispatch.wizard_robbed") {
            on(flag("fighter.priest_book") eq "priest_visited") {
                clw(); runScene("speak.wizard.robbed_flow")
            }
        }
        scene("dispatch.priest_with_book") {
            on(flag("fighter.priest_book") eq "priest_told") {
                clw()
                letBound("wizard_name", "npc_name_at_role", "wizard_robbed")
                letBound("giver_name",  "npc_name_at_role", "fighter_intro")
                runScene("speak.priest.intro_flow")
            }
        }
        scene("dispatch.erethor_lute_bard") {
            on(flag("erethor.lute") eq "none") {
                runScene("speak.erethor.first_visit")
            }
            on(flag("erethor.lute") eq "lute_taken") {
                clw()
                letBound("minstrel_name", "npc_name_at_role", "minstrel_cloak")
                letBound("minstrel_loc",  "npc_loc_at_role",  "minstrel_cloak")
                runScene("speak.erethor.delivered_lute")
            }
        }
        scene("dispatch.ranger_brother") {
            on(flag("ranger.letter") eq "accepted") {
                clw(); runScene("speak.brother.give_sword")
            }
        }
        scene("dispatch.crown_seer") {
            on(flag("nymph.seeker") eq "none") {
                clw()
                letBound("seeker_name", "npc_name_at_role", "wizard_three_tasks")
                letBound("seeker_loc",  "npc_loc_at_role",  "wizard_three_tasks")
                runScene("speak.nymph.know_flow")
            }
        }
        scene("dispatch.master_thief") {
            on(flag("silver_key") eq "note_found") {
                clw()
                letBound("smith_name", "npc_name_at_role", "silver_key_smith")
                runScene("speak.thief2.squeezed")
            }
        }
        scene("dispatch.peasant_husband") {
            on(flag("peasant_wife") eq "none")     { clw(); runScene("speak.husband.first_visit") }
            on(flag("peasant_wife") eq "rescued")  { clw(); runScene("speak.husband.rescued") }
        }
        scene("dispatch.minstrel_cloak") {
            on(flag("erethor.lute") eq "note_received") {
                clw()
                letBound("minstrel_name", "npc_name_at_role", "minstrel_cloak")
                letBound("next_town",     "npc_loc_at_role",  "erethor_lute_bard")
                runScene("speak.minstrel.gives_cloak")
            }
        }
        scene("dispatch.wizard_three_tasks") {
            on((flag("nymph.seeker") eq "told") and (flag("mastercrown") eq "none")) {
                clw()
                letBound("kobold_x", "wld_location_x", "kobold_village")
                letBound("kobold_y", "wld_location_y", "kobold_village")
                runScene("speak.wizard3t.task1_flow")
            }
            on(flag("mastercrown") eq "task1_done") { clw(); runScene("speak.wizard3t.task2_flow") }
            on(flag("mastercrown") eq "task2_done") {
                clw()
                letBound("dwarf_x", "wld_location_x", "borin_camp")
                letBound("dwarf_y", "wld_location_y", "borin_camp")
                runScene("speak.wizard3t.task3_flow")
            }
            on(flag("mastercrown") eq "borin_paid") {
                clw()
                letBound("crown_x", "wld_location_x", "master_crown_dungeon")
                letBound("crown_y", "wld_location_y", "master_crown_dungeon")
                runScene("speak.wizard3t.grateful_flow")
            }
        }
        scene("dispatch.silver_key_smith") {
            on(flag("silver_key") eq "thief2_squeezed") {
                clw(); runScene("speak.smith.after_thief2")
            }
        }
        scene("dispatch.hermit_troll_tooth") {
            on(flag("hermit.troll_tooth") eq "none")      { clw(); runScene("speak.hermit.welcome_visit") }
            on(flag("hermit.troll_tooth") eq "extracted") {
                clw()
                letBound("cave_x", "wld_location_x", "hermit_dungeon")
                letBound("cave_y", "wld_location_y", "hermit_dungeon")
                runScene("speak.hermit.tooth_trade")
            }
        }
    }

    // -------------------------------------------------------------------------
    // Missions. Each mission is: (1) a trigger gate (position / random / etc),
    // (2) a flag prerequisite, (3) a body. Most bodies are short narration
    // around an encounter or dungeon entry; longer bodies inline the screens.
    // -------------------------------------------------------------------------
    missions {
        mission("erethor_entry") {
            triggerKind = "position"; x = 6; y = 20
            requires = flag("erethor.lute") eq "rumour_told"
            body {
                clw(); screen("missions.erethor_entry.prompt")
                menu {
                    option("E") { enterDungeon("erethor_temple"); clearMap(); redrawMap() }
                    option("L") { /* no exit clw, mirrors legacy returns_on_skip=0 */ }
                }
            }
        }

        mission("husband_cave") {
            triggerKind = "position"; x = 42; y = 23
            requires = flag("peasant_wife") eq "accepted"
            bindings("name19" to "npc_name:peasant_husband")
            body {
                clw(); screen("missions.husband_cave.prompt")
                menu {
                    option("E") { enterDungeon("ogre_cave"); clearMap(); redrawMap() }
                    option("L") { }
                }
            }
        }

        mission("bandits") {
            triggerKind = "random"; randomChance = 9
            requires = flag("garth.bandits") eq "accepted"
            body {
                clw()
                say("You spot a band of armoured riders ahead, blocking the road.")
                say("They are the bandits Garth warned you about.")
                waitInput()
                encounter("bandit", mandatory = true)
                redrawMap(); clw()
                say("You search the wreckage and find Garth's missing cargo.")
                waitInput(); clw()
                flag("garth.bandits") set "recovered"
            }
        }

        mission("troll_tooth") {
            triggerKind = "random"; randomChance = 9
            requires = flag("hermit.troll_tooth") eq "accepted"
            body {
                // Note: no opening clw -- the troll attacks on the move.
                encounter("troll", mandatory = true)
                redrawMap(); clw()
                screen("missions.troll_tooth.after_troll")
                clw()
                flag("hermit.troll_tooth") set "extracted"
            }
        }

        mission("hermit_dungeon") {
            triggerKind = "dungeon_loc"; dungeonSlot = 1
            requires = flag("hermit.troll_tooth") eq "delivered"
            body {
                clw(); screen("missions.hermit_dungeon.prompt")
                menu {
                    option("E") { enterDungeon("fire_shield_cave"); clearMap(); redrawMap() }
                    option("L") { /* default arm empty -- legacy returns_on_skip=1 */ }
                }
            }
        }

        mission("kobold_village") {
            triggerKind = "dungeon_loc"; dungeonSlot = 3
            requires = flag("mastercrown") eq "task1_assigned"
            body {
                clw(); screen("missions.kobold_village.prompt")
                menu {
                    option("A") {
                        clw(); screen("missions.kobold_village.charge")
                        encounter("kobold_horde", mandatory = true)
                        clw(); screen("missions.kobold_village.dead_piles")
                        encounter("kobold_horde", mandatory = true)
                        encounter("orc", mandatory = true)
                        redrawMap(); clw()
                        screen("missions.kobold_village.pendant"); clw()
                        flag("mastercrown") set "task1_done"
                    }
                    default { clw() }
                }
            }
        }

        mission("skullweed") {
            triggerKind = "terrain"; terrainChar = 't'
            requires = flag("mastercrown") eq "task2_assigned"
            body {
                clw(); screen("missions.skullweed.pickup")
                flag("mastercrown") set "task2_done"
            }
        }

        mission("borin_meet") {
            triggerKind = "dungeon_loc"; dungeonSlot = 4
            requires = flag("mastercrown") eq "task3_assigned"
            bindings("dx5" to "dx_str:5", "dy5" to "dy_str:5")
            body {
                clw()
                playChain("missions.borin_meet")
                journal("Nolrim's crypt at {dx5}x{dy5}")
                flag("mastercrown") set "borin_met"
            }
        }

        mission("nolrim_crypt") {
            triggerKind = "dungeon_loc"; dungeonSlot = 5
            returnsOnSkip = true
            requires = flag("mastercrown") eq "borin_met"
            body {
                clw(); screen("missions.nolrim_crypt.prompt")
                menu {
                    option("E") { enterDungeon("reebar_tomb"); clearMap(); redrawMap() }
                    option("L") { clw() }   // returns_on_skip default arm
                }
            }
        }

        mission("borin_gift") {
            triggerKind = "dungeon_loc"; dungeonSlot = 4
            requires = flag("mastercrown") eq "dagger_taken"
            body {
                clw()
                screen("missions.borin_gift.greet_and_follow")
                screen("missions.borin_gift.rewards_presented")
                reward {
                    gold(3578)
                    items("red_gem", "blue_gem", "green_gem")
                }
                flag("mastercrown") set "borin_paid"
            }
        }

        mission("master_crown") {
            triggerKind = "dungeon_loc"; dungeonSlot = 6
            returnsOnSkip = true
            requires = flag("mastercrown") eq "crown_told"
            body {
                clw(); screen("missions.master_crown.prompt")
                menu {
                    option("E") { enterDungeon("master_crown_dungeon"); clearMap(); redrawMap() }
                    option("L") { clw() }
                }
            }
        }

        mission("ogre_cave") {
            triggerKind = "dungeon_loc"; dungeonSlot = 7
            returnsOnSkip = true
            requires = flag("jazzy_ogre") eq "accepted"
            bindings("p2_name" to "player_name:2")
            body {
                clw(); screen("missions.ogre_cave.prompt")
                menu {
                    option("A") {
                        clw(); screen("missions.ogre_cave.enter_cave")
                        encounter("ogre", mandatory = true)
                        redrawMap(); clw()
                        screen("missions.ogre_cave.defeat_ogres")
                        encounter("ogre_lord", mandatory = true)
                        redrawMap(); clw()
                        screen("missions.ogre_cave.defeat_ogre_lord"); clw()
                        reward { gold(5645); items("two_hand_sword") }
                        screen("missions.ogre_cave.no_trace"); clw()
                        screen("missions.ogre_cave.kastagir_note"); clw()
                        screen("missions.ogre_cave.leave_cave"); clw()
                        flag("jazzy_ogre") set "ogres_cleared"
                    }
                    default { clw() }
                }
            }
        }

        mission("jazzy_foot") {
            triggerKind = "terrain"; terrainChar = 'f'
            requires = flag("jazzy_ogre") eq "ogres_cleared"
            body {
                clw()
                screen("missions.jazzy_foot.discovered")
                screen("missions.jazzy_foot.minions_attack")
                encounter("winged_thing", mandatory = true)
                redrawMap(); clw()
                screen("missions.jazzy_foot.quiet")
                screen("missions.jazzy_foot.jazzy_appears")
                encounter("jazzy", mandatory = true)
                redrawMap(); clw()
                screen("missions.jazzy_foot.defeated")
                reward { items("jazzyfoot") }
                flag("jazzy_ogre") set "jazzy_defeated"
            }
        }

        mission("silver_key") {
            triggerKind = "dungeon_loc"; dungeonSlot = 8
            returnsOnSkip = true
            requires = flag("silver_key") eq "map_bought"
            body {
                clw(); screen("missions.silver_key.prompt")
                menu {
                    option("E") { enterDungeon("silver_sanctum"); clearMap(); redrawMap() }
                    option("L") { clw() }
                }
            }
        }

        mission("hobgoblin_caravan") {
            triggerKind = "random"; randomChance = 9
            requires = flag("silver_key") eq "smith_confessed"
            bindings("dx9" to "dx_str:9", "dy9" to "dy_str:9")
            body {
                clw()
                screen("missions.hobgoblin_caravan.approach")
                screen("missions.hobgoblin_caravan.attack_pitch")
                encounter("riding_hobgoblin", mandatory = true)
                redrawMap(); clw()
                screen("missions.hobgoblin_caravan.interrogate")
                screen("missions.hobgoblin_caravan.carotid")
                journal("hobgoblin cave at {dx9}x{dy9}")
                flag("silver_key") set "cave_located"
            }
        }

        mission("hobgoblin_cave") {
            triggerKind = "dungeon_loc"; dungeonSlot = 9
            returnsOnSkip = true
            requires = flag("silver_key") eq "cave_located"
            body {
                clw(); screen("missions.hobgoblin_cave.prompt")
                menu {
                    option("E") { enterDungeon("hobgoblin_stronghold"); clearMap(); redrawMap() }
                    option("L") { clw() }
                }
            }
        }

        // ------- Castle Ydarg (3 entry-key arms). The castle interior is its
        // ------- own loop, captured under world.ydarg. Here we just route the
        // ------- E-key press at the castle tile.
        mission("ydarg_ruin") {
            triggerKind = "enter_key"; x = 14; y = 30
            // Implicit: silverkey special == 2 (castle ruined post-victory).
            body {
                clearMap(); setBackdrop("ydarg"); clw()
                screen("ydarg.ruin")
                clearMap(); redrawMap(); clw()
            }
        }
        mission("ydarg_no_key") {
            triggerKind = "enter_key"; x = 14; y = 30
            // Implicit: silverkey special == 0 (no key yet).
            body {
                clearMap(); setBackdrop("ydarg"); clw()
                screen("ydarg.no_key.approach")
                screen("ydarg.no_key.jaara_form")
                screen("ydarg.no_key.jaara_speech")
                text("ydarg.no_key.no_key"); waitInput()
                clearMap(); redrawMap()
            }
        }
        mission("ydarg_main") {
            triggerKind = "enter_key"; x = 14; y = 30
            // Implicit: silverkey special == 1 (have key, castle intact).
            body {
                runScene("ydarg.intro")
                engineCall("ydarg_state_init"); waitInput()
                loop {
                    clw()
                    val room = letInt("room", engineInt("ydarg_get_room_at_cur"))
                    on(v("room") eq 1) {
                        engineCall("ydarg_clear_room_at_cur")
                        runScene("ydarg.room_monster")
                    }
                    on(v("room") eq 2) { runScene("ydarg.case_pitfall") }
                    on(v("room") eq 3) { runScene("ydarg.case_treasure") }
                    on(v("room") eq 50) {
                        runScene("ydarg.case_stairs")
                        on(engineCheck("ydarg_get_exit_requested")) { breakOut() }
                    }
                    runScene("ydarg.nav_render")
                    val c = letInt("c", engineInt("key_upper"))
                    engineCall("ydarg_try_move", v("c"))
                    runScene("ydarg.cmd_dispatch", "c" to v("c"))
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Sysop backdoors. Both signed by author handles; not exposed in normal
    // menus.
    // -------------------------------------------------------------------------
    secrets {
        backdoor("marcus") {
            name = "Marcus Lagergren"
            key('/', "add_time_left", "amount" to 50, "refresh_status" to true)
            key('(', "set_inventory",  "slot" to 12, "item" to "drums_of_death")
            key(')', "set_inventory",  "slot" to 11, "item" to "gem_of_resurrection")
            key('&', "set_inventory",  "slot" to 10, "item" to "kaels_axe")
            key('*', "set_inventory",  "slot" to 9,  "item" to "holy_handgrenade")
            key('+', "add_gold",       "amount" to 1000, "refresh_status" to true)
        }
        backdoor("karl") {
            name = "Karl Skagerberg"
            stepTrigger(everyN = 101, action = "spawn_encounter", "monster" to "skagerberg_killer")
        }
    }

    // -------------------------------------------------------------------------
    // Keymaps. Town/combat/wilderness dispatchers.
    // -------------------------------------------------------------------------
    keymap {
        town('C', "enter_church", requires = "has_church")
        town('S', "enter_smith")
        town('I', "enter_inn")
        town('M', "enter_merchant")
        town('T', "enter_tavern")
        town('L', "leave_town")

        combat('U', "use_item")
        combat('D', "defend")
        combat('A', "attack")
        combat('B', "battle_cry")
        combat('C', "cast_spell", requires = "is_mage")

        wilderness(' ', "pass")
        wilderness('S', "save")
        wilderness('W', "weapon_ready")
        wilderness('R', "armor_ready")
        wilderness('!', "redraw")
        wilderness('J', "journal")
        wilderness('?', "help")
        wilderness('H', "hunt")
        wilderness('V', "view_party")
        wilderness('T', "armor_off")
        wilderness('P', "weapon_off")
        wilderness('E', "enter")
        wilderness('L', "drop")
        wilderness('C', "camp")
        wilderness('Z', "cast_spell")
        wilderness('Q', "quaff")
        wilderness('X', "quit_confirm")

        pauseVerbs(
            "Smash", "Bash", "Slash", "Lunge at", "Crush", "Destroy", "Mutilate",
            "Thrash", "Stab", "Brain", "Pummel", "Whack", "Pulverize", "Annihilate",
            "Obliterate", "Demolish", "Hammer", "Pound", "Maul", "Wallop",
            "Cleave", "Mangle", "Skewer", "Eviscerate", "Vanquish", "Trample",
            "Batter", "Slam", "Smite", "Decapitate",
        )
    }

    // -------------------------------------------------------------------------
    // Intro: title screen + character creation handoff. The legacy game has
    // a long C-side opening; this is the shape of it.
    // -------------------------------------------------------------------------
    intro {
        clw(); setBackdrop("title")
        screen("title.intro")
        screen("title.story_a")
        screen("title.story_b")
        screen("title.story_c")
        clw(); engineCall("driv_create_party")
        screen("title.adventure_begins")
        engineCall("driv_drop_into_world")
    }

    // -------------------------------------------------------------------------
    // Main loop: wilderness step + per-tile events + per-step triggers.
    // The actual driver is in driv.c; this captures the conceptual shape so
    // the DSL covers the full playable surface.
    // -------------------------------------------------------------------------
    mainLoop {
        loop {
            engineCall("driv_render_status")
            val c = letInt("c", engineInt("driv_get_key_upper"))

            // Per-step ambient events first.
            on(chance(3))  { engineCall("wilderness_cave_check") }
            on(chance(91)) { /* quiet step */ } els {
                on(chance(50)) { encounter("kobold", tier = "weak") } els {
                    encounter("hill_giant", tier = "wild")
                }
            }

            // Mission triggers consult the missions table; the engine fires
            // the first matching mission for the current tile / key / random
            // gate, respecting `requires`.
            engineCall("missions_dispatch_step", v("c"))

            // Karl's stalker.
            engineCall("secrets_step_check")
            engineCall("driv_advance_clock")
        }
    }
}
