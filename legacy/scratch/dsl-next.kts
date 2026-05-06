// dsl-next.kts -- second Kotlin DSL experiment for Rylween.
//
// This file is intentionally small and opinionated. It is not wired into the
// C runtime. Its purpose is to test the authoring shape we want before another
// large full-game transcription:
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
//   6. The six hard cases below are the readability test:
//      church heal, mage frost, fire wand, rumour, Ydarg main loop, bandits.

@file:Suppress(
    "unused",
    "MemberVisibilityCanBePrivate",
    "RedundantVisibilityModifier",
    "UNUSED_PARAMETER",
)

// -----------------------------------------------------------------------------
// References declared by implementors.
// -----------------------------------------------------------------------------

enum class FlagScope { Party, Encounter }

abstract class FlagRef(
    val slug: String,
    val scope: FlagScope = FlagScope.Party,
    val legacySlot: Int? = null,
) {
    private val declaredStates = mutableListOf<FlagState>()
    val states: List<FlagState> get() = declaredStates

    protected fun state(native: String): FlagState = FlagState(this, native).also { declaredStates += it }
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

class EncounterImmuneFlag :
    FlagRef(
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

data class TextId(
    val id: String,
)

data class TextPattern(
    val pattern: String,
)

data class ItemId(
    val slug: String,
)

data class MonsterId(
    val slug: String,
)

data class EncounterTier(
    val slug: String,
)

data class SceneId(
    val slug: String,
)

data class SpellId(
    val slug: String,
)

data class MissionId(
    val slug: String,
)

data class NpcRole(
    val slug: String,
)

data class StateId(
    val slug: String,
)

data class PartyId(
    val slug: String,
)

data class NpcId(
    val slug: String,
)

data class LocationId(
    val slug: String,
)

data class TownId(
    val slug: String,
)

data class BuildingId(
    val slug: String,
)

fun text(id: String) = TextId(id)

fun textPattern(pattern: String) = TextPattern(pattern)

fun item(slug: String) = ItemId(slug)

fun monster(slug: String) = MonsterId(slug)

fun encounterTier(slug: String) = EncounterTier(slug)

fun sceneId(slug: String) = SceneId(slug)

fun spellId(slug: String) = SpellId(slug)

fun missionId(slug: String) = MissionId(slug)

fun npcRole(slug: String) = NpcRole(slug)

fun stateId(slug: String) = StateId(slug)

fun partyId(slug: String) = PartyId(slug)

fun npc(slug: String) = NpcId(slug)

fun location(slug: String) = LocationId(slug)

fun town(slug: String) = TownId(slug)

fun building(slug: String) = BuildingId(slug)

class ItemRefs {
    val none = item("none")
    val frostWand = item("frost_wand")
    val fireWand = item("fire_wand")
    val garthCargo = item("garth_cargo")
}
val Items = ItemRefs()

class MonsterIds {
    val bandit = monster("bandit")
    val kobold = monster("kobold")
    val troll = monster("troll")
    val guardian = monster("guardian")
    val hobgoblinChief = monster("hobgoblin_chief")
}
val Monsters = MonsterIds()

class EncounterTierRefs {
    val weak = encounterTier("weak")
    val wild = encounterTier("wild")
    val deep = encounterTier("deep")
}
val EncounterTiers = EncounterTierRefs()

class RoleRefs {
    val banditsQuestGiver = npcRole("bandits_quest_giver")
    val rangersQuestGiver = npcRole("rangers_quest_giver")
    val crownSeer = npcRole("crown_seer")
    val templeOfThunSeer = npcRole("temple_of_thun_seer")
}
val Roles = RoleRefs()

class SceneRefs {
    val churchHeal = sceneId("church.heal")
    val rumour = sceneId("towne.rumour")
    val ydargMain = sceneId("ydarg.main")
    val ydargRoomMonster = sceneId("ydarg.room_monster")
    val ydargPitfall = sceneId("ydarg.case_pitfall")
    val ydargTreasure = sceneId("ydarg.case_treasure")
    val ydargStairs = sceneId("ydarg.case_stairs")
    val ydargNavRender = sceneId("ydarg.nav_render")
    val ydargCommandDispatch = sceneId("ydarg.cmd_dispatch")
}
val Scenes = SceneRefs()

class SpellRefs {
    val mageFrost = spellId("mage.frost")
}
val Spells = SpellRefs()

class MissionRefs {
    val bandits = missionId("missions.bandits")
}
val Missions = MissionRefs()

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
    val bernak = npc("bernak")
    val azaaza = npc("azaaza")
}
val Npcs = NpcRefs()

class LocationRefs {
    val silverKeyPit = location("silver_key_pit")
    val beastCave = location("beast_cave")
}
val Locations = LocationRefs()

class BuildingRefs {
    val none = building("none")
    val inn = building("inn")
    val tavern = building("tavern")
}
val Buildings = BuildingRefs()

class TownRef(
    val id: TownId,
) {
    val inn: WorldPosition get() = WorldPosition.Town(id, Buildings.inn)
    val tavern: WorldPosition get() = WorldPosition.Town(id, Buildings.tavern)
}

class TownRefs {
    val thord = TownRef(town("thord"))
    val ollk = TownRef(town("ollk"))
    val wilor = TownRef(town("wilor"))
    val murn = TownRef(town("murn"))
}
val Towns = TownRefs()

class TextRefs {
    val Towne = TowneRefs()
    val Battle = BattleRefs()
    val MissionsText = MissionTextRefs()
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
val T = TextRefs()

// -----------------------------------------------------------------------------
// Recorded AST.
// -----------------------------------------------------------------------------

sealed interface IntAst {
    data class Lit(
        val value: Int,
    ) : IntAst

    data class Var(
        val name: String,
    ) : IntAst

    data class Binary(
        val left: IntAst,
        val op: String,
        val right: IntAst,
    ) : IntAst

    data class Random(
        val lo: IntAst,
        val hi: IntAst,
    ) : IntAst

    data class MemberField(
        val member: IntAst,
        val field: String,
    ) : IntAst

    data class MonsterField(
        val monster: IntAst,
        val field: String,
    ) : IntAst

    data class PartyScalar(
        val name: String,
    ) : IntAst

    data class NativeInt(
        val path: String,
        val args: List<String>,
    ) : IntAst
}

sealed interface BoolAst {
    data class Cmp(
        val left: IntAst,
        val op: String,
        val right: IntAst,
    ) : BoolAst

    data class Not(
        val expr: BoolAst,
    ) : BoolAst

    data class And(
        val left: BoolAst,
        val right: BoolAst,
    ) : BoolAst

    data class Or(
        val left: BoolAst,
        val right: BoolAst,
    ) : BoolAst

    data class FlagIs(
        val slug: String,
        val nativeValue: String,
    ) : BoolAst

    data class MemberFlag(
        val member: IntAst,
        val name: String,
    ) : BoolAst

    data class MonsterFlag(
        val monster: IntAst,
        val name: String,
    ) : BoolAst
}

sealed interface StrAst {
    data class NativeString(
        val path: String,
        val args: List<String>,
    ) : StrAst
}

sealed interface LValueAst {
    data class PartyScalar(
        val name: String,
    ) : LValueAst

    data class MemberField(
        val member: IntAst,
        val field: String,
    ) : LValueAst

    data class MonsterField(
        val monster: IntAst,
        val field: String,
    ) : LValueAst
}

sealed interface Op {
    data class Screen(
        val text: TextId,
    ) : Op

    data class Emit(
        val text: TextId,
    ) : Op

    data class EmitIndexed(
        val pattern: TextPattern,
        val value: IntAst,
    ) : Op

    data class WaitInput(
        val reason: String? = null,
    ) : Op

    data class Pause(
        val millis: Int,
    ) : Op

    data class LetInt(
        val name: String,
        val value: IntAst,
    ) : Op

    data class BindText(
        val name: String,
        val value: StrAst,
    ) : Op

    data class Set(
        val target: LValueAst,
        val value: IntAst,
    ) : Op

    data class Add(
        val target: LValueAst,
        val value: IntAst,
    ) : Op

    data class Sub(
        val target: LValueAst,
        val value: IntAst,
    ) : Op

    data class SetFlag(
        val slug: String,
        val nativeValue: String,
    ) : Op

    data class If(
        val cond: BoolAst,
        val thenOps: List<Op>,
        val elseOps: List<Op>,
    ) : Op

    data class Loop(
        val body: List<Op>,
    ) : Op

    data class Break(
        val reason: String? = null,
    ) : Op

    data class Stop(
        val reason: String? = null,
    ) : Op

    data class Switch(
        val expr: IntAst,
        val cases: List<SwitchCase>,
    ) : Op

    data class AskMember(
        val prompt: TextId,
        val varName: String,
    ) : Op

    data class AskMonster(
        val prompt: TextId,
        val varName: String,
    ) : Op

    data class ForEachMonster(
        val varName: String,
        val includeDead: Boolean,
        val body: List<Op>,
    ) : Op

    data class RunScene(
        val scene: SceneId,
        val bindings: Map<String, String>,
    ) : Op

    data class Encounter(
        val foe: MonsterId,
        val mandatory: Boolean,
        val tier: EncounterTier? = null,
    ) : Op

    data class TakeItem(
        val item: ItemId,
    ) : Op

    data class NativeCall(
        val path: String,
        val args: List<String>,
    ) : Op
}

data class SwitchCase(
    val match: Int?,
    val body: List<Op>,
)

data class SceneModel(
    val id: SceneId,
    val ops: List<Op>,
)

data class SpellModel(
    val id: SpellId,
    val slot: Int,
    val baseCost: Int,
    val name: String,
    val ops: List<Op>,
)

data class MagicItemModel(
    val id: ItemId,
    val ops: List<Op>,
)

data class MissionModel(
    val id: MissionId,
    val trigger: Trigger,
    val requires: BoolAst?,
    val ops: List<Op>,
)

data class GameModel(
    val flags: List<FlagRef>,
    val scenes: List<SceneModel>,
    val spells: List<SpellModel>,
    val magicItems: List<MagicItemModel>,
    val missions: List<MissionModel>,
)

sealed interface Trigger {
    data class RandomStep(
        val percent: Int,
    ) : Trigger
}

// -----------------------------------------------------------------------------
// State DSL model.
// -----------------------------------------------------------------------------

enum class Gender { Male, Female }

enum class Race { Human, HalfElf, HalfOrc }

enum class PcClass { Thief, Fighter, Wizard, Priest, Ranger, Bard }

data class StatPool(
    val current: Int,
    val max: Int,
)
typealias HitPoints = StatPool
typealias Mana = StatPool

infix fun Int.of(max: Int): StatPool = StatPool(this, max)

data class GridPoint(
    val x: Int,
    val y: Int,
)

fun xy(
    x: Int,
    y: Int,
): GridPoint = GridPoint(x, y)

sealed interface WorldPosition {
    data class Wilderness(
        val point: GridPoint,
    ) : WorldPosition

    data class Town(
        val town: TownId,
        val building: BuildingId,
    ) : WorldPosition
}

object Wilderness {
    fun xy(
        x: Int,
        y: Int,
    ): WorldPosition = WorldPosition.Wilderness(GridPoint(x, y))
}

data class RealmState(
    val day: Int,
    val hour: Int,
    val activeParty: PartyId,
)

data class LocationPlacement(
    val location: LocationId,
    val point: GridPoint,
)

data class NpcPlacement(
    val npc: NpcId,
    val position: WorldPosition,
)

data class PartyResources(
    val gold: Int,
    val torches: Int,
    val food: Int,
    val timeLeft: Int,
    val restedHours: Int,
)

data class InventorySlotState(
    val slot: Int,
    val item: ItemId?,
)

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

// -----------------------------------------------------------------------------
// Expressions and mutable slots.
// -----------------------------------------------------------------------------

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

data class Expr(
    override val ast: IntAst,
) : IntExpr()

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

data class Bool(
    override val ast: BoolAst,
) : BoolExpr()

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

class PartyRef(
    private val script: ScriptScope,
) {
    val gold = IntSlot(script, LValueAst.PartyScalar("gold"), IntAst.PartyScalar("gold"))

    fun member(index: IntExpr): MemberRef = MemberRef(script, index)
}

class MemberRef(
    private val script: ScriptScope,
    val index: IntExpr,
) {
    val hp = intField("hit_points")
    val maxHp = intField("health")
    val mp = intField("magic")
    val level = intField("level")
    val wisdom = intField("wisdom")
    val dead =
        BoolSlot(
            script,
            LValueAst.MemberField(index.ast, "dead"),
            BoolAst.MemberFlag(index.ast, "dead"),
        )

    private fun intField(field: String): IntSlot =
        IntSlot(script, LValueAst.MemberField(index.ast, field), IntAst.MemberField(index.ast, field))
}

class MonsterRef(
    private val script: ScriptScope,
    val index: IntExpr,
) {
    val hp = IntSlot(script, LValueAst.MonsterField(index.ast, "hit_points"), IntAst.MonsterField(index.ast, "hit_points"))
    val dead =
        BoolSlot(
            script,
            LValueAst.MonsterField(index.ast, "dead"),
            BoolAst.MonsterFlag(index.ast, "dead"),
        )
}

sealed interface StrExpr {
    val ast: StrAst
}

data class Str(
    override val ast: StrAst,
) : StrExpr

enum class PronounCase { Subject, Object, Possessive }

sealed class NativeSpec(
    val path: String,
    val reason: String,
)

class NativeEffectSpec(
    path: String,
    reason: String,
) : NativeSpec(path, reason)

class NativeIntSpec(
    path: String,
    reason: String,
) : NativeSpec(path, reason)

class NativeStringSpec(
    path: String,
    reason: String,
) : NativeSpec(path, reason)

object NativeAllowlist {
    val screenClearText = effect("screen.clear_text", "text-window rendering primitive")
    val screenRedrawMap = effect("screen.redraw_map", "map rendering primitive")
    val inventoryAdd = effect("inventory.add", "legacy inventory slot mutation")
    val ydargInit = effect("ydarg.init", "legacy Ydarg dungeon state setup")
    val ydargClearRoomAtCurrent = effect("ydarg.clear_room_at_current", "legacy Ydarg room state mutation")
    val ydargTryMove = effect("ydarg.try_move", "legacy Ydarg movement and collision")

    val rumourRoll = int("rumour.roll", "legacy rumour distribution")
    val ydargRoomAtCurrent = int("ydarg.room_at_current", "legacy Ydarg room table lookup")
    val ydargExitRequested = int("ydarg.exit_requested", "legacy Ydarg exit flag")
    val inputKeyUpper = int("input.key_upper", "keyboard input normalization")

    val partyMemberName = string("party.member.name", "party-member display name binding")
    val combatMonsterName = string("combat.monster.name", "combat monster display name binding")
    val partyPronounSubject = string("party.member.pronoun.subject", "party-member pronoun binding")
    val partyPronounObject = string("party.member.pronoun.object", "party-member pronoun binding")
    val partyPronounPossessive = string("party.member.pronoun.possessive", "party-member pronoun binding")
    val npcRoleName = string("npc.role.name", "role-resolved NPC name binding")
    val npcRoleLocation = string("npc.role.location", "role-resolved NPC location binding")

    val all: List<NativeSpec> =
        listOf(
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

    private fun effect(
        path: String,
        reason: String,
    ) = NativeEffectSpec(path, reason)

    private fun int(
        path: String,
        reason: String,
    ) = NativeIntSpec(path, reason)

    private fun string(
        path: String,
        reason: String,
    ) = NativeStringSpec(path, reason)
}

fun partyName(member: MemberRef): StrExpr = Str(StrAst.NativeString(NativeAllowlist.partyMemberName.path, listOf(render(member.index.ast))))

fun monsterName(monster: MonsterRef): StrExpr =
    Str(StrAst.NativeString(NativeAllowlist.combatMonsterName.path, listOf(render(monster.index.ast))))

fun pronoun(
    member: MemberRef,
    case: PronounCase,
): StrExpr {
    val native =
        when (case) {
            PronounCase.Subject -> NativeAllowlist.partyPronounSubject
            PronounCase.Object -> NativeAllowlist.partyPronounObject
            PronounCase.Possessive -> NativeAllowlist.partyPronounPossessive
        }
    return Str(StrAst.NativeString(native.path, listOf(render(member.index.ast))))
}

fun npcName(role: NpcRole): StrExpr = Str(StrAst.NativeString(NativeAllowlist.npcRoleName.path, listOf(role.slug)))

fun npcLocation(role: NpcRole): StrExpr = Str(StrAst.NativeString(NativeAllowlist.npcRoleLocation.path, listOf(role.slug)))

fun render(ast: IntAst): String = ast.toString()

fun render(arg: Any): String =
    when (arg) {
        is IntExpr -> render(arg.ast)
        is IntAst -> render(arg)
        is TextId -> arg.id
        is ItemId -> arg.slug
        is MonsterId -> arg.slug
        is EncounterTier -> arg.slug
        is SceneId -> arg.slug
        is FlagState -> "${arg.flag.slug}.${arg.native}"
        else -> arg.toString()
    }

// -----------------------------------------------------------------------------
// DSL builders.
// -----------------------------------------------------------------------------

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

    fun emitIndexed(
        pattern: TextPattern,
        value: IntExpr,
    ) = record(Op.EmitIndexed(pattern, value.ast))

    fun waitInput(reason: String? = null) = record(Op.WaitInput(reason))

    fun pause(ms: Int) = record(Op.Pause(ms))

    fun let(
        name: String,
        value: IntExpr,
    ): IntExpr {
        record(Op.LetInt(name, value.ast))
        return v(name)
    }

    fun bind(
        name: String,
        value: StrExpr,
    ) {
        record(Op.BindText(name, value.ast))
    }

    fun mark(state: FlagState) {
        record(Op.SetFlag(state.flag.slug, state.native))
    }

    fun when_(
        cond: BoolExpr,
        then: ScriptScope.() -> Unit,
    ): ElseBranch {
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

    fun choose(
        expr: IntExpr,
        body: SwitchBuilder.() -> Unit,
    ) {
        val builder = SwitchBuilder(this)
        builder.body()
        record(Op.Switch(expr.ast, builder.cases))
    }

    fun forEachMonster(
        includeDead: Boolean = false,
        body: ScriptScope.(MonsterRef) -> Unit,
    ) {
        val name = ctx.fresh("monster")
        val childOps =
            capture {
                body(MonsterRef(this, v(name)))
            }
        record(Op.ForEachMonster(name, includeDead, childOps))
    }

    fun run(
        scene: SceneId,
        vararg bindings: Pair<String, Any>,
    ) {
        record(Op.RunScene(scene, bindings.associate { it.first to render(it.second) }))
    }

    fun encounter(
        monster: MonsterId,
        mandatory: Boolean = false,
        tier: EncounterTier? = null,
    ) {
        record(Op.Encounter(monster, mandatory, tier))
    }

    fun take(item: ItemId) = record(Op.TakeItem(item))

    fun pay(
        amount: IntExpr,
        broke: ScriptScope.() -> Unit,
        ok: ScriptScope.() -> Unit,
    ) {
        when_(party.gold lt amount) {
            broke()
        } otherwise {
            party.gold -= amount
            ok()
        }
    }

    private fun capture(body: ScriptScope.() -> Unit): List<Op> {
        val childOps = mutableListOf<Op>()
        ScriptScope(ctx, childOps).body()
        return childOps
    }
}

class ElseBranch(
    private val finish: (List<Op>) -> Unit,
) {
    infix fun otherwise(body: ScriptScope.() -> Unit) {
        val ops = mutableListOf<Op>()
        ScriptScope(ScriptContext(), ops).body()
        finish(ops)
    }
}

@RylNextDsl
class SwitchBuilder(
    private val parent: ScriptScope,
) {
    val cases = mutableListOf<SwitchCase>()

    fun case(
        value: Int,
        body: ScriptScope.() -> Unit,
    ) {
        val ops = mutableListOf<Op>()
        ScriptScope(ScriptContext(), ops).body()
        cases += SwitchCase(value, ops)
    }

    fun default(body: ScriptScope.() -> Unit) {
        val ops = mutableListOf<Op>()
        ScriptScope(ScriptContext(), ops).body()
        cases += SwitchCase(null, ops)
    }
}

class AskScope(
    private val script: ScriptScope,
) {
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

class NativeScope(
    private val script: ScriptScope,
) {
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

class NativeScreen(
    private val script: ScriptScope,
) {
    fun clearText() = script.record(Op.NativeCall(NativeAllowlist.screenClearText.path, emptyList()))

    fun redrawMap() = script.record(Op.NativeCall(NativeAllowlist.screenRedrawMap.path, emptyList()))
}

class NativeRumour(
    private val script: ScriptScope,
) {
    fun roll(): IntExpr = Expr(IntAst.NativeInt(NativeAllowlist.rumourRoll.path, emptyList()))
}

class NativeInventory(
    private val script: ScriptScope,
) {
    fun add(itemId: IntExpr) = script.record(Op.NativeCall(NativeAllowlist.inventoryAdd.path, listOf(render(itemId))))
}

class NativeYdarg(
    private val script: ScriptScope,
) {
    fun init() = script.record(Op.NativeCall(NativeAllowlist.ydargInit.path, emptyList()))

    fun roomAtCurrent(): IntExpr = Expr(IntAst.NativeInt(NativeAllowlist.ydargRoomAtCurrent.path, emptyList()))

    fun clearRoomAtCurrent() = script.record(Op.NativeCall(NativeAllowlist.ydargClearRoomAtCurrent.path, emptyList()))

    fun exitRequested(): BoolExpr =
        Bool(BoolAst.Cmp(IntAst.NativeInt(NativeAllowlist.ydargExitRequested.path, emptyList()), "ne", IntAst.Lit(0)))

    fun tryMove(key: IntExpr) = script.record(Op.NativeCall(NativeAllowlist.ydargTryMove.path, listOf(render(key))))
}

class NativeInput(
    private val script: ScriptScope,
) {
    fun keyUpper(): IntExpr = Expr(IntAst.NativeInt(NativeAllowlist.inputKeyUpper.path, emptyList()))
}

@RylNextDsl
class GameBuilder {
    private val scenes = mutableListOf<SceneModel>()
    private val spells = mutableListOf<SpellModel>()
    private val magicItems = mutableListOf<MagicItemModel>()
    private val missions = mutableListOf<MissionModel>()

    fun scene(
        id: SceneId,
        body: ScriptScope.() -> Unit,
    ) {
        scenes += SceneModel(id, recordScript(body))
    }

    fun spell(
        id: SpellId,
        slot: Int,
        baseCost: Int,
        name: String,
        body: ScriptScope.() -> Unit,
    ) {
        spells += SpellModel(id, slot, baseCost, name, recordScript(body))
    }

    fun magicItem(
        id: ItemId,
        body: ScriptScope.() -> Unit,
    ) {
        magicItems += MagicItemModel(id, recordScript(body))
    }

    fun mission(
        id: MissionId,
        trigger: Trigger,
        requires: BoolExpr? = null,
        body: ScriptScope.() -> Unit,
    ) {
        missions += MissionModel(id, trigger, requires?.ast, recordScript(body))
    }

    fun build(): GameModel = GameModel(declaredFlags.toList(), scenes, spells, magicItems, missions)

    private fun recordScript(body: ScriptScope.() -> Unit): List<Op> {
        val ops = mutableListOf<Op>()
        ScriptScope(ScriptContext(), ops).body()
        return ops
    }
}

fun rylweenNext(body: GameBuilder.() -> Unit): GameModel {
    val builder = GameBuilder()
    builder.body()
    return builder.build()
}

@RylNextDsl
class RylweenBuilder {
    private var contentModel: GameModel? = null
    private val states = mutableListOf<StateModel>()

    fun content(body: GameBuilder.() -> Unit) {
        val builder = GameBuilder()
        builder.body()
        contentModel = builder.build()
    }

    fun state(
        id: StateId,
        body: StateBuilder.() -> Unit,
    ) {
        val builder = StateBuilder(id)
        builder.body()
        states += builder.build()
    }

    fun build(): RylweenModel =
        RylweenModel(
            content = checkNotNull(contentModel) { "rylween requires content { ... }" },
            states = states.toList(),
        )
}

fun rylween(body: RylweenBuilder.() -> Unit): RylweenModel {
    val builder = RylweenBuilder()
    builder.body()
    return builder.build()
}

@RylNextDsl
class StateBuilder(
    private val id: StateId,
) {
    private var realmState: RealmState? = null
    private val locationPlacements = mutableListOf<LocationPlacement>()
    private val npcPlacements = mutableListOf<NpcPlacement>()
    private val parties = mutableListOf<PartyState>()

    fun realm(body: RealmStateBuilder.() -> Unit) {
        val builder = RealmStateBuilder()
        builder.body()
        realmState = builder.build()
    }

    fun locations(body: LocationStateBuilder.() -> Unit) {
        val builder = LocationStateBuilder()
        builder.body()
        locationPlacements += builder.build()
    }

    fun npcs(body: NpcStateBuilder.() -> Unit) {
        val builder = NpcStateBuilder()
        builder.body()
        npcPlacements += builder.build()
    }

    fun party(
        id: PartyId,
        body: PartyStateBuilder.() -> Unit,
    ) {
        val builder = PartyStateBuilder(id)
        builder.body()
        parties += builder.build()
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
class PartyStateBuilder(
    private val id: PartyId,
) {
    var player: String = ""
    var name: String = ""
    var position: WorldPosition = Wilderness.xy(0, 0)
    private var resourcesState = PartyResources(0, 0, 0, 0, 0)
    private val flags = mutableListOf<FlagState>()
    private val inventory = mutableListOf<InventorySlotState>()
    private val members = mutableListOf<PartyMemberState>()
    private val visitedNpcs = mutableListOf<NpcId>()
    private val journal = mutableListOf<TextId>()

    fun resources(body: PartyResourcesBuilder.() -> Unit) {
        val builder = PartyResourcesBuilder()
        builder.body()
        resourcesState = builder.build()
    }

    fun flags(body: StateFlagBuilder.() -> Unit) {
        val builder = StateFlagBuilder()
        builder.body()
        flags += builder.build()
    }

    fun inventory(body: InventoryStateBuilder.() -> Unit) {
        val builder = InventoryStateBuilder()
        builder.body()
        inventory += builder.build()
    }

    fun members(body: PartyMembersBuilder.() -> Unit) {
        val builder = PartyMembersBuilder()
        builder.body()
        members += builder.build()
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
class InventoryStateBuilder {
    private val slots = mutableMapOf<Int, InventorySlotState>()

    fun slot(index: Int): InventorySlotBuilder = InventorySlotBuilder(index) { slot -> slots[index] = slot }

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

    fun member(
        name: String,
        body: PartyMemberBuilder.() -> Unit,
    ) {
        val builder = PartyMemberBuilder(name)
        builder.body()
        members += builder.build()
    }

    fun build(): List<PartyMemberState> = members.toList()
}

@RylNextDsl
class PartyMemberBuilder(
    private val name: String,
) {
    var gender: Gender = Gender.Male
    var race: Race = Race.Human
    var klass: PcClass = PcClass.Thief
    var level: Int = 1
    var health: Int = 1
    var hitPoints: HitPoints = 1 of 1
    var weaponSkill: Int = 0
    var wisdom: Int = 0
    var mana: Mana = 0 of 0
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
        for (op in items) {
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
                is Op.NativeCall -> paths += op.path
            }
        }
    }

    model.scenes.forEach { ops(it.ops) }
    model.spells.forEach { ops(it.ops) }
    model.magicItems.forEach { ops(it.ops) }
    model.missions.forEach {
        it.requires?.let(::bool)
        ops(it.ops)
    }
    return paths
}

fun validateFlagCatalog(flags: List<FlagRef>) {
    check(flags.map { it.slug }.toSet().size == flags.size) { "duplicate flag slug" }

    for (flag in flags) {
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

fun validateStates(states: List<StateModel>) {
    for (state in states) {
        for (party in state.parties) {
            for (member in party.members) {
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

// -----------------------------------------------------------------------------
// Six hard-case readability probe.
// -----------------------------------------------------------------------------

val nextRylween =
    rylween {
        content {
            // 1. Church heal: typed member fields, typed flagless flow, no engine names.
            scene(Scenes.churchHeal) {
                native.screen.clearText()
                val m = ask.member(T.Towne.Church.who)

                when_(m.dead) {
                    screen(T.Towne.Church.corpse)
                    pause(1200)
                    stop("corpse exits the heal service")
                }

                when_(m.hp eq m.maxHp) {
                    bind("pro_sub", pronoun(m, PronounCase.Subject))
                    screen(T.Towne.Church.noHealing)
                    pause(1000)
                    stop("already fully healed")
                }

                val price = let("cost", (m.maxHp - m.hp) * 7)
                screen(T.Towne.Church.cost)

                pay(
                    price,
                    broke = {
                        screen(T.Towne.Church.noGold)
                        pause(1000)
                    },
                    ok = {
                        m.hp set m.maxHp
                        bind("pro_obj", pronoun(m, PronounCase.Object))
                        screen(T.Towne.Church.healed)
                        pause(1000)
                    },
                )
            }

            // 2. Mage frost: spell body is normal script, combat target is typed.
            spell(Spells.mageFrost, slot = 1, baseCost = 8, name = "Frost Finger") {
                val caster = currentCaster
                val target = ask.monsterAlive(T.Battle.Prompt.monster)
                val damage = let("dmg", caster.level * random(2..8))

                native.screen.clearText()
                bind("caster", partyName(caster))
                emit(T.Battle.Frost.chills)
                emit(T.Battle.Frost.damage)
                emit(T.Battle.Frost.damagePts)
                pause(1000)

                target.hp -= damage

                when_(target.hp lte 0) {
                    target.hp set 0
                    target.dead set true
                    emit(T.Battle.killed)
                    pause(600)
                }
            }

            // 3. Fire wand: body owns item consumption and preserves per-slot RNG.
            magicItem(Items.fireWand) {
                val caster = currentCaster
                take(Items.fireWand)
                native.screen.clearText()
                bind("caster", partyName(caster))

                forEachMonster(includeDead = true) { foe ->
                    val damage = let("dmg", caster.level * random(3..8))

                    when_(!foe.dead) {
                        bind("monster_name", monsterName(foe))
                        emit(T.Battle.FireWand.burns)
                        emit(T.Battle.FireWand.points)
                        pause(1000)

                        foe.hp -= damage

                        when_(foe.hp lte 0) {
                            foe.hp set 0
                            emit(T.Battle.killed)
                            pause(600)
                            foe.dead set true
                        }
                    }
                }
            }

            // 4. Rumour: switch is readable, role bindings are typed handles.
            scene(Scenes.rumour) {
                val pick = let("pick", native.rumour.roll())

                choose(pick) {
                    case(12) {
                        bind("name1", npcName(Roles.banditsQuestGiver))
                        emit(T.Towne.Rumour.r12)
                    }
                    case(13) {
                        bind("name2", npcName(Roles.rangersQuestGiver))
                        emit(T.Towne.Rumour.r13)
                    }
                    case(25) {
                        bind("name15", npcName(Roles.crownSeer))
                        bind("loc15", npcLocation(Roles.crownSeer))
                        emit(T.Towne.Rumour.r25)
                    }
                    case(27) {
                        bind("name13", npcName(Roles.templeOfThunSeer))
                        bind("loc13", npcLocation(Roles.templeOfThunSeer))
                        emit(T.Towne.Rumour.r27)
                    }
                    default {
                        emitIndexed(T.Towne.Rumour.indexed, pick)
                    }
                }
            }

            // 5. Ydarg main: the native island is explicit and narrow.
            scene(Scenes.ydargMain) {
                native.ydarg.init()
                waitInput("after castle intro")

                loop {
                    native.screen.clearText()

                    val room = let("room", native.ydarg.roomAtCurrent())
                    choose(room) {
                        case(1) {
                            native.ydarg.clearRoomAtCurrent()
                            run(Scenes.ydargRoomMonster)
                        }
                        case(2) {
                            run(Scenes.ydargPitfall)
                        }
                        case(3) {
                            run(Scenes.ydargTreasure)
                        }
                        case(50) {
                            run(Scenes.ydargStairs)
                            when_(native.ydarg.exitRequested()) {
                                breakLoop("stairs requested exit")
                            }
                        }
                    }

                    run(Scenes.ydargNavRender)
                    val key = let("key", native.input.keyUpper())
                    native.ydarg.tryMove(key)
                    run(Scenes.ydargCommandDispatch, "key" to key)
                }
            }

            // 6. Mission trigger: flag states are fluent typed markers, not strings.
            mission(
                Missions.bandits,
                trigger = Trigger.RandomStep(percent = 9),
                requires = Story.garth.bandits.accepted,
            ) {
                native.screen.clearText()
                screen(T.MissionsText.Bandits.spotted)
                screen(T.MissionsText.Bandits.warned)
                encounter(Monsters.bandit, mandatory = true)
                native.screen.redrawMap()
                native.screen.clearText()
                screen(T.MissionsText.Bandits.recovered)
                mark(Story.garth.bandits.recovered)
            }
        }

        state(States.freshReplay) {
            realm {
                day = 14
                hour = 9
                activeParty = Parties.replay
            }

            locations {
                Locations.silverKeyPit at xy(45, 13)
                Locations.beastCave at xy(11, 23)
            }

            npcs {
                Npcs.garth at Towns.thord.inn
                Npcs.bernak at Towns.wilor.inn
                Npcs.azaaza at Towns.ollk.inn
            }

            party(Parties.replay) {
                player = "Replay"
                name = "Replay"
                position = Wilderness.xy(12, 9)

                resources {
                    gold = 1000
                    torches = 5
                    food = 100
                    timeLeft = 200
                    restedHours = 14
                }

                flags {
                    mark(Story.garth.bandits.recovered)
                    mark(Story.silverKey.noteFound)
                }

                inventory {
                    slot(1).empty()
                    slot(2).item(Items.fireWand)
                    slot(3).item(Items.garthCargo)
                }

                members {
                    member("A") {
                        gender = Gender.Male
                        race = Race.Human
                        klass = PcClass.Thief
                        level = 1
                        health = 68
                        hitPoints = 68 of 68
                        weaponSkill = 27
                        wisdom = 27
                        mana = 0 of 27
                        charisma = 49
                        armor = Items.none
                        weapon = Items.none
                        experience = 0
                        alive()
                    }

                    member("B") {
                        gender = Gender.Male
                        race = Race.Human
                        klass = PcClass.Thief
                        level = 1
                        health = 46
                        hitPoints = 46 of 46
                        weaponSkill = 42
                        wisdom = 48
                        mana = 0 of 48
                        charisma = 64
                        armor = Items.none
                        weapon = Items.none
                        experience = 0
                        alive()
                    }
                }

                visited(Npcs.garth, Npcs.bernak)
                journal(T.MissionsText.Bandits.recovered)
            }
        }
    }

val nextDsl = nextRylween.content

check(nextDsl.flags.size == 13)
check(nextDsl.scenes.size == 3)
check(nextDsl.spells.size == 1)
check(nextDsl.magicItems.size == 1)
check(nextDsl.missions.size == 1)
check(nextRylween.states.size == 1)
check(
    nextRylween.states
        .single()
        .parties
        .single()
        .members.size == 2,
)
validateFlagCatalog(nextDsl.flags)
validateStates(nextRylween.states)
check((nativePathsIn(nextDsl) - NativeAllowlist.byPath.keys).isEmpty())
