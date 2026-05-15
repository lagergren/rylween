/**
 * Readable expression and mutable slot layer for authored scripts.
 *
 * `ScriptKernelDsl.kt` defines the raw AST nodes; this file defines the Kotlin
 * DSL facade that game scripts actually use: arithmetic/comparison operators,
 * `party`/`realm`/`battle` references, mutable integer and boolean slots, native
 * string bindings, and render helpers for stable diagnostic text. These types
 * record operations into `ScriptScope`; they do not execute gameplay directly.
 */
package rylween.dsl

import rylween.dsl.impl.*

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

class LocalIntRef internal constructor(
    val name: String,
) : IntExpr() {
    override val ast: IntAst = IntAst.Var(name)
}

fun lit(value: Int): IntExpr = Expr(IntAst.Lit(value))

fun v(name: String): IntExpr = Expr(IntAst.Var(name))

fun random(range: IntRange): IntExpr = Expr(IntAst.Random(IntAst.Lit(range.first), IntAst.Lit(range.last)))

fun chance(percent: IntExpr): BoolExpr = random(0..99) lt percent

fun chance(percent: Int): BoolExpr = chance(lit(percent))

infix fun IntExpr.eq(other: IntExpr): BoolExpr = Bool(BoolAst.Cmp(ast, "eq", other.ast))

infix fun IntExpr.eq(other: Int): BoolExpr = this eq lit(other)

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

data class Bool(
    override val ast: BoolAst,
) : BoolExpr()

fun achieved(milestone: GameMilestoneId): BoolExpr = Bool(BoolAst.MilestoneAchieved(milestone))

class IntSlot(
    private val script: ScriptScope,
    private val lvalue: LValueAst,
    override val ast: IntAst,
) : IntExpr() {
    infix fun set(value: IntExpr): Unit = script.record(Op.Set(lvalue, value.ast))

    infix fun set(value: Int): Unit = set(lit(value))

    operator fun plusAssign(value: IntExpr): Unit = script.record(Op.Add(lvalue, value.ast))

    operator fun plusAssign(value: Int): Unit = plusAssign(lit(value))

    operator fun minusAssign(value: IntExpr): Unit = script.record(Op.Sub(lvalue, value.ast))

    operator fun minusAssign(value: Int): Unit = minusAssign(lit(value))

    operator fun timesAssign(value: IntExpr): Unit = script.record(Op.Set(lvalue, (this * value).ast))

    operator fun timesAssign(value: Int): Unit = timesAssign(lit(value))

    operator fun divAssign(value: IntExpr): Unit = script.record(Op.Set(lvalue, (this / value).ast))

    operator fun divAssign(value: Int): Unit = divAssign(lit(value))

    fun addClamped(
        value: IntExpr,
        max: IntExpr,
    ): Unit = script.record(Op.AddClamped(lvalue, value.ast, max.ast))

    fun addClamped(
        value: Int,
        max: IntExpr,
    ): Unit = addClamped(lit(value), max)
}

class RealmRef(
    script: ScriptScope,
) {
    val hour: IntSlot = IntSlot(script, LValueAst.RealmScalar("hour"), IntAst.RealmScalar("hour"))
    val partyMemberMax: IntSlot =
        IntSlot(
            script,
            LValueAst.RealmScalar("party_member_max"),
            IntAst.RealmScalar("party_member_max"),
        )
    val banished: BoolSlot =
        BoolSlot(
            script,
            LValueAst.RealmScalar("banished"),
            BoolAst.Cmp(IntAst.RealmScalar("banished"), "ne", IntAst.Lit(0)),
        )
}

class RuntimeContextRef {
    val inDungeon: BoolExpr = Bool(BoolAst.RuntimeFlag("in_dungeon"))
    val inCastle: BoolExpr = Bool(BoolAst.RuntimeFlag("in_castle"))
    val dungeonLevel: IntExpr = v("dungeon_level")
}

class BoolSlot(
    private val script: ScriptScope,
    private val lvalue: LValueAst,
    read: BoolAst,
) : BoolExpr() {
    override val ast: BoolAst = read

    infix fun set(value: Boolean): Unit = script.record(Op.Set(lvalue, IntAst.Lit(if (value) 1 else 0)))
}

class PartyRef(
    private val script: ScriptScope,
) {
    val gold: IntSlot = IntSlot(script, LValueAst.PartyScalar("gold"), IntAst.PartyScalar("gold"))
    val torches: IntSlot = IntSlot(script, LValueAst.PartyScalar("torches"), IntAst.PartyScalar("torches"))
    val food: IntSlot = IntSlot(script, LValueAst.PartyScalar("food"), IntAst.PartyScalar("food"))
    val timeLeft: IntSlot = IntSlot(script, LValueAst.PartyScalar("time_left"), IntAst.PartyScalar("time_left"))
    val restedHours: IntSlot = IntSlot(script, LValueAst.PartyScalar("rested_hours"), IntAst.PartyScalar("rested_hours"))
    val memberCount: IntSlot = IntSlot(script, LValueAst.PartyScalar("member_count"), IntAst.PartyScalar("member_count"))
    val inventory: PartyInventoryRef = PartyInventoryRef(script)
    val leader: MemberRef get() = member(lit(1))

    fun member(index: Int): MemberRef = member(lit(index))

    fun member(index: IntExpr): MemberRef = MemberRef(script, index)
}

class PartyInventoryRef(
    private val script: ScriptScope,
) {
    fun count(pool: ItemPoolId): IntExpr = Expr(IntAst.InventoryPoolCount(pool))

    fun itemCost(slot: InventorySlotRef): IntExpr = Expr(IntAst.InventorySlotCost(slot.index.ast))

    fun consume(
        pool: ItemPoolId,
        quantity: IntExpr,
    ) {
        script.record(Op.ConsumeInventoryPool(pool, quantity.ast))
    }

    fun consume(
        pool: ItemPoolId,
        quantity: Int,
    ): Unit = consume(pool, lit(quantity))

    fun clear(slot: InventorySlotRef) {
        script.record(Op.ClearInventorySlot(slot.index.ast))
    }
}

class MemberRef(
    private val script: ScriptScope,
    val index: IntExpr,
) {
    val hp: IntSlot = intField("hit_points")
    val maxHp: IntSlot = intField("health")
    val mp: IntSlot = intField("magic")
    val maxMp: IntSlot = intField("magic_max")
    val mana: IntSlot = mp
    val level: IntSlot = intField("level")
    val experience: IntSlot = intField("experience")
    val wisdom: IntSlot = intField("wisdom")
    val charisma: IntSlot = intField("charisma")
    val weaponSkill: IntSlot = intField("weapon_skill")
    val pcClass: IntSlot = intField("class")
    val dead: BoolSlot =
        BoolSlot(
            script,
            LValueAst.MemberField(index.ast, "dead"),
            BoolAst.MemberFlag(index.ast, "dead"),
        )
    val spellcaster: BoolExpr = Bool(BoolAst.MemberFlag(index.ast, "spellcaster"))

    fun isClass(klass: PcClass): BoolExpr = pcClass eq klass.sourceId

    fun fullHeal() {
        script.record(Op.Set(LValueAst.MemberField(index.ast, "hit_points"), maxHp.ast))
    }

    private fun intField(field: String): IntSlot =
        IntSlot(script, LValueAst.MemberField(index.ast, field), IntAst.MemberField(index.ast, field))
}

class MonsterRef(
    script: ScriptScope,
    val index: IntExpr,
) {
    val hp: IntSlot = IntSlot(script, LValueAst.MonsterField(index.ast, "hit_points"), IntAst.MonsterField(index.ast, "hit_points"))
    val dead: BoolSlot =
        BoolSlot(
            script,
            LValueAst.MonsterField(index.ast, "dead"),
            BoolAst.MonsterFlag(index.ast, "dead"),
        )
}

class BattleRef(
    private val script: ScriptScope,
) {
    val monsterWeaponSkill: IntSlot =
        IntSlot(
            script,
            LValueAst.BattleFlag("monster_weapon_skill"),
            IntAst.BattleFlag("monster_weapon_skill"),
        )
    val encounterHitPointMax: IntExpr = Expr(IntAst.BattleFlag("encounter_hit_point_max"))
    val monsterConfused: BoolSlot = boolFlag("monster_confused")
    val monstersHeld: BoolSlot = boolFlag("monsters_held")
    val antimagic: BoolSlot = boolFlag("antimagic")
    val partyHeld: BoolSlot = boolFlag("party_held")
    val partyConfused: BoolSlot = boolFlag("party_confused")
    val actionRetry: BoolSlot = boolFlag("action_retry")

    fun firstAliveMonster(): MonsterRef = MonsterRef(script, script.localInt(Expr(IntAst.BattleQuery("first_alive_monster")), "monster"))

    fun randomAliveMember(): MemberRef = MemberRef(script, script.localInt(Expr(IntAst.BattleQuery("random_alive_member")), "member"))

    private fun boolFlag(name: String): BoolSlot = BoolSlot(script, LValueAst.BattleFlag(name), BoolAst.BattleFlag(name))
}

sealed interface StrExpr {
    val ast: StrAst
}

data class Str(
    override val ast: StrAst,
) : StrExpr

enum class PronounCase { Subject, Object, Possessive }

sealed class NativeSpec(
    val id: NativeOpId,
    val reason: String,
) {
    val path: String get() = id.slug
}

class NativeEffectSpec(
    id: NativeOpId,
    reason: String,
) : NativeSpec(id, reason)

class NativeIntSpec(
    id: NativeOpId,
    reason: String,
) : NativeSpec(id, reason)

class NativeStringSpec(
    id: NativeOpId,
    reason: String,
) : NativeSpec(id, reason)

class NativeAllowlistRefs {
    val inventoryAdd: NativeEffectSpec = effect(NativeOps.inventoryAdd, "inventory slot mutation")
    val townEnterService: NativeEffectSpec = effect(NativeOps.townEnterService, "town building-service trampoline")

    val inputKeyUpper: NativeIntSpec =
        NativeIntSpec(NativeOps.inputKeyUpper, "keyboard input normalization")

    val partyMemberName: NativeStringSpec = string(NativeOps.partyMemberName, "party-member display name binding")
    val combatMonsterName: NativeStringSpec = string(NativeOps.combatMonsterName, "combat monster display name binding")
    val partyPronounSubject: NativeStringSpec = string(NativeOps.partyPronounSubject, "party-member pronoun binding")
    val partyPronounObject: NativeStringSpec = string(NativeOps.partyPronounObject, "party-member pronoun binding")
    val partyPronounPossessive: NativeStringSpec = string(NativeOps.partyPronounPossessive, "party-member pronoun binding")
    val townNpcName: NativeStringSpec = string(NativeOps.townNpcName, "current-town NPC display name binding")
    val npcRoleName: NativeStringSpec = string(NativeOps.npcRoleName, "role-resolved NPC name binding")
    val npcRoleLocation: NativeStringSpec = string(NativeOps.npcRoleLocation, "role-resolved NPC location binding")

    val all: List<NativeSpec> =
        listOf(
            inventoryAdd,
            townEnterService,
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

    private fun effect(
        id: NativeOpId,
        reason: String,
    ) = NativeEffectSpec(id, reason)

    private fun string(
        id: NativeOpId,
        reason: String,
    ) = NativeStringSpec(id, reason)
}

val NativeAllowlist: NativeAllowlistRefs = NativeAllowlistRefs()

fun partyName(member: MemberRef): StrExpr =
    Str(StrAst.NativeString(NativeAllowlist.partyMemberName.path, listOf(NativeStringArg.IntValue(member.index.ast))))

fun monsterName(monster: MonsterRef): StrExpr =
    Str(StrAst.NativeString(NativeAllowlist.combatMonsterName.path, listOf(NativeStringArg.IntValue(monster.index.ast))))

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
    return Str(StrAst.NativeString(native.path, listOf(NativeStringArg.IntValue(member.index.ast))))
}

fun Gender.pronoun(case: PronounCase): String =
    when (case) {
        PronounCase.Subject -> if (this == Gender.Female) "she" else "he"
        PronounCase.Object -> if (this == Gender.Female) "her" else "him"
        PronounCase.Possessive -> if (this == Gender.Female) "her" else "his"
    }

fun townNpcName(job: NpcJobId): StrExpr =
    Str(StrAst.NativeString(NativeAllowlist.townNpcName.path, listOf(NativeStringArg.Literal(render(job)))))

fun npcName(role: NpcRole): StrExpr = Str(StrAst.NativeString(NativeAllowlist.npcRoleName.path, listOf(NativeStringArg.Literal(role.slug))))

fun render(ast: IntAst): String = ast.toString()

fun render(arg: Any): String =
    when (arg) {
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
        is RumorTableId -> arg.slug
        is RumorId -> arg.slug
        else -> arg.toString()
    }
