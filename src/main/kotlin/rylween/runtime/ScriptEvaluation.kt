package rylween.runtime

import rylween.dsl.*

abstract class IntAstEvaluator {
    fun int(ast: IntAst): Int =
        when (ast) {
            is IntAst.Lit -> ast.value
            is IntAst.Var -> local(ast.name)
            is IntAst.Binary -> evalBinary(int(ast.left), ast.op, int(ast.right))
            is IntAst.If -> if (bool(ast.cond)) int(ast.thenValue) else int(ast.elseValue)
            is IntAst.Random -> random(int(ast.lo), int(ast.hi))
            is IntAst.MemberField -> memberField(int(ast.member), ast.field)
            is IntAst.MonsterField -> monsterField(int(ast.monster), ast.field)
            is IntAst.BattleFlag -> battleFlag(ast.name)
            is IntAst.BattleQuery -> battleQuery(ast.name)
            is IntAst.RealmScalar -> realmScalar(ast.name)
            is IntAst.PartyScalar -> partyScalar(ast.name)
            is IntAst.InventoryPoolCount -> inventoryPoolCount(ast.pool)
            is IntAst.InventorySlotCost -> inventorySlotCost(int(ast.slot))
            is IntAst.NativeInt -> nativeInt(ast.path, ast.args)
        }

    fun bool(ast: BoolAst): Boolean =
        when (ast) {
            is BoolAst.Cmp -> compare(int(ast.left), ast.op, int(ast.right))
            is BoolAst.Not -> !bool(ast.expr)
            is BoolAst.And -> bool(ast.left) && bool(ast.right)
            is BoolAst.Or -> bool(ast.left) || bool(ast.right)
            is BoolAst.FlagIs -> flagIs(ast.slug, ast.nativeValue)
            is BoolAst.MilestoneAchieved -> milestoneAchieved(ast.milestone)
            is BoolAst.MemberFlag -> memberFlag(int(ast.member), ast.name)
            is BoolAst.MonsterFlag -> monsterFlag(int(ast.monster), ast.name)
            is BoolAst.BattleFlag -> battleFlag(ast.name) != 0
            is BoolAst.RuntimeFlag -> runtimeFlag(ast.name)
        }

    protected open fun local(name: String): Int = unsupported("local $name")
    protected open fun random(lo: Int, hi: Int): Int = unsupported("random")
    protected open fun memberField(member: Int, field: String): Int = unsupported("member field $field")
    protected open fun monsterField(monster: Int, field: String): Int = unsupported("monster field $field")
    protected open fun battleFlag(name: String): Int = unsupported("battle flag $name")
    protected open fun battleQuery(name: String): Int = unsupported("battle query $name")
    protected open fun realmScalar(name: String): Int = unsupported("realm scalar $name")
    protected open fun partyScalar(name: String): Int = unsupported("party scalar $name")
    protected open fun inventoryPoolCount(pool: ItemPoolId): Int = unsupported("inventory pool ${pool.slug}")
    protected open fun inventorySlotCost(slot: Int): Int = unsupported("inventory slot cost $slot")
    protected open fun nativeInt(path: String, args: List<String>): Int = unsupported("native int $path")
    protected open fun flagIs(slug: String, native: String): Boolean = unsupported("flag $slug")
    protected open fun milestoneAchieved(milestone: GameMilestoneId): Boolean =
        unsupported("milestone ${milestone.slug}")
    protected open fun memberFlag(member: Int, name: String): Boolean = unsupported("member flag $name")
    protected open fun monsterFlag(monster: Int, name: String): Boolean = unsupported("monster flag $name")
    protected open fun runtimeFlag(name: String): Boolean = unsupported("runtime flag $name")

    private fun evalBinary(left: Int, op: String, right: Int): Int =
        when (op) {
            "add" -> left + right
            "sub" -> left - right
            "mul" -> left * right
            "div" -> if (right == 0) 0 else left / right
            else -> unsupported("binary op $op")
        }

    private fun compare(left: Int, op: String, right: Int): Boolean =
        when (op) {
            "eq" -> left == right
            "ne" -> left != right
            "lt" -> left < right
            "lte" -> left <= right
            "gt" -> left > right
            "gte" -> left >= right
            else -> unsupported("comparison op $op")
        }

    protected fun unsupported(what: String): Nothing =
        error("expression cannot read $what in this runtime context")
}
