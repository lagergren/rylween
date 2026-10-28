/**
 * Evaluation of authored formulas, shared by the runtime systems that use them.
 *
 * A formula is a named integer expression from the compiled bundle. The system
 * that evaluates it binds the formula's variables: battle rewards bind the
 * encounter numbers, leveling binds the new health, hunting binds the hunter's
 * stats, a castle trap binds the disarming thief's stats, a spell binds its
 * caster's stats and base cost, and a shop haggle binds the party's best
 * charisma.
 */

package rylween.runtime

import rylween.compiled.CompiledBoolExpr
import rylween.compiled.CompiledContentBundle
import rylween.compiled.CompiledFormula
import rylween.compiled.CompiledHandleKind
import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledIntExpr
import rylween.compiled.CompiledPartyMember
import rylween.compiled.CompiledSpell
import rylween.compiled.applyCompiledBinary
import rylween.compiled.compareCompiled

/** A formula's value, and the party after any random draws the formula made. */
internal data class FormulaResult(
    val value: Int,
    val party: PartyState,
)

/** Evaluates the formula [formulaRef] against [party], with the variable [bindings] of the evaluating system. */
internal fun CompiledContentBundle.evalFormula(
    formulaRef: CompiledHandleRef,
    party: PartyState,
    bindings: Map<String, Int>,
): FormulaResult {
    val formula = formulas.firstOrNull { current -> current.id == formulaRef }
    return evalFormula(checkNotNull(formula) { "missing compiled formula ${slug(formulaRef)}" }, party, bindings)
}

/** Evaluates the formula named [formulaSlug] against [party], with the variable [bindings] of the evaluating system. */
internal fun CompiledContentBundle.evalFormula(
    formulaSlug: String,
    party: PartyState,
    bindings: Map<String, Int>,
): FormulaResult {
    val formulaRef = ref(CompiledHandleKind.Formula, formulaSlug)
    val formula = formulas.firstOrNull { current -> current.id == formulaRef }
    return evalFormula(checkNotNull(formula) { "missing compiled formula $formulaSlug" }, party, bindings)
}

/** Evaluates [formula] against [party], with the variable [bindings] of the evaluating system. */
internal fun CompiledContentBundle.evalFormula(
    formula: CompiledFormula,
    party: PartyState,
    bindings: Map<String, Int>,
): FormulaResult = FormulaEvaluator(this, party, bindings).eval(formula.value)

/** The stat variables a formula sees for the one member it is evaluated for, such as a hunter, a disarming thief or a caster. */
internal fun CompiledPartyMember.statBindings(): Map<String, Int> =
    mapOf(
        "charisma" to charisma,
        "dexterity" to dexterity,
        "level" to level,
    )

/**
 * What [spell] costs [caster] in spell points: the game's spell cost formula
 * [formulaSlug], evaluated with the caster's stats and the spell's base cost,
 * or the base cost alone when the game declares no formula.
 */
internal fun CompiledContentBundle.spellCost(
    formulaSlug: String?,
    spell: CompiledSpell,
    caster: CompiledPartyMember,
    party: PartyState,
): Int =
    formulaSlug
        ?.let { slug -> evalFormula(slug, party, caster.statBindings() + ("spell_base_cost" to spell.baseCost)).value }
        ?: spell.baseCost

private class FormulaEvaluator(
    private val content: CompiledContentBundle,
    private var party: PartyState,
    private val bindings: Map<String, Int>,
) {
    fun eval(expr: CompiledIntExpr): FormulaResult {
        val value = int(expr)
        return FormulaResult(value, party)
    }

    private fun int(expr: CompiledIntExpr): Int =
        when (expr) {
            is CompiledIntExpr.Literal -> expr.value
            is CompiledIntExpr.Local -> bindings[content.strings.value(expr.nameString)] ?: 0
            is CompiledIntExpr.Binary -> int(expr.left).applyCompiledBinary(expr.op, int(expr.right))
            is CompiledIntExpr.If ->
                when {
                    bool(expr.cond) -> int(expr.thenValue)
                    else -> int(expr.elseValue)
                }

            is CompiledIntExpr.Random -> random(int(expr.lo), int(expr.hi))
            is CompiledIntExpr.MemberField -> memberField(int(expr.member), content.strings.value(expr.fieldString))
            is CompiledIntExpr.MonsterField,
            is CompiledIntExpr.BattleFlag,
            is CompiledIntExpr.BattleQuery,
            is CompiledIntExpr.PartyMemberQuery,
            is CompiledIntExpr.RealmScalar,
            is CompiledIntExpr.PartyScalar,
            is CompiledIntExpr.InventoryPoolCount,
            is CompiledIntExpr.InventorySlotCost,
            -> 0
        }

    private fun bool(expr: CompiledBoolExpr): Boolean =
        when (expr) {
            is CompiledBoolExpr.Cmp -> int(expr.left).compareCompiled(expr.op, int(expr.right))
            is CompiledBoolExpr.Not -> !bool(expr.expr)
            is CompiledBoolExpr.And -> bool(expr.left) && bool(expr.right)
            is CompiledBoolExpr.Or -> bool(expr.left) || bool(expr.right)
            is CompiledBoolExpr.FlagIs -> expr.state in party.flags
            is CompiledBoolExpr.MilestoneAchieved -> expr.milestone in party.milestones
            is CompiledBoolExpr.MemberFlag,
            is CompiledBoolExpr.MonsterFlag,
            is CompiledBoolExpr.BattleFlag,
            is CompiledBoolExpr.RuntimeFlag,
            -> false
        }

    private fun random(
        lo: Int,
        hi: Int,
    ): Int {
        val draw = party.drawRandom(lo, hi)
        party = draw.party
        return draw.value
    }

    private fun memberField(
        index: Int,
        field: String,
    ): Int {
        val member = party.members.getOrNull(index) ?: return 0
        return when (field) {
            "level" -> member.level
            "health" -> member.health
            "hit_points" -> member.hitPoints.current
            "hit_point_max" -> member.hitPoints.max
            "weapon_skill" -> member.weaponSkill
            "wisdom" -> member.wisdom
            "mana" -> member.mana.current
            "mana_max" -> member.mana.max
            "charisma" -> member.charisma
            "dexterity" -> member.dexterity
            "experience" -> member.experience
            "dead" -> if (member.dead) 1 else 0
            else -> 0
        }
    }
}
