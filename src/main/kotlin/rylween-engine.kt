// Backend-neutral engine boundary for executing or tracing DSL scripts.
//
// The Kotlin DSL should not care whether the implementation behind this port is
// the current C engine, a pure Kotlin runtime, a server process, or a test
// tracer. Keep requests typed and small so every backend can implement the same
// contract.

data class EngineRequest(
    val path: String,
    val args: List<String> = emptyList(),
)

interface RylweenEnginePort {
    fun effect(request: EngineRequest)
    fun readInt(request: EngineRequest): Int
    fun readString(request: EngineRequest): String
}

sealed interface EngineTraceEvent {
    data class NativeEffect(val request: EngineRequest) : EngineTraceEvent
    data class NativeInt(val request: EngineRequest, val result: Int) : EngineTraceEvent
    data class NativeString(val request: EngineRequest, val result: String) : EngineTraceEvent
    data class Screen(val text: TextBlock) : EngineTraceEvent
    data class Emit(val id: TextId) : EngineTraceEvent
    data class EmitIndexed(val pattern: TextPattern, val value: Int) : EngineTraceEvent
    data class WaitInput(val reason: String?) : EngineTraceEvent
    data class Pause(val ms: Int) : EngineTraceEvent
    data class SetFlag(val flag: String, val state: String) : EngineTraceEvent
    data class GrantReward(val reward: ScriptRewardModel) : EngineTraceEvent
    data class AchieveMilestone(val milestone: GameMilestoneId) : EngineTraceEvent
    data class RunScene(val scene: SceneId, val bindings: Map<String, String>) : EngineTraceEvent
    data class Encounter(val monster: MonsterId, val mandatory: Boolean, val tier: EncounterTier?) : EngineTraceEvent
    data class Stop(val reason: String?) : EngineTraceEvent
    data class Break(val reason: String?) : EngineTraceEvent
}

class TraceEnginePort(
    private val ints: Map<EngineRequest, Int> = emptyMap(),
    private val strings: Map<EngineRequest, String> = emptyMap(),
) : RylweenEnginePort {
    private val mutableEvents = mutableListOf<EngineTraceEvent>()
    val events: List<EngineTraceEvent> get() = mutableEvents.toList()

    override fun effect(request: EngineRequest) {
        mutableEvents += EngineTraceEvent.NativeEffect(request)
    }

    override fun readInt(request: EngineRequest): Int =
        ints.getOrElse(request) { 0 }.also { result ->
            mutableEvents += EngineTraceEvent.NativeInt(request, result)
        }

    override fun readString(request: EngineRequest): String =
        strings.getOrElse(request) { "" }.also { result ->
            mutableEvents += EngineTraceEvent.NativeString(request, result)
        }

    internal fun trace(event: EngineTraceEvent) {
        mutableEvents += event
    }
}

class ScriptTraceRunner(
    private val engine: RylweenEnginePort,
) {
    private val intVars = mutableMapOf<String, Int>()
    private val stringVars = mutableMapOf<String, String>()

    fun run(ops: List<Op>): List<EngineTraceEvent> {
        ops.forEach(::run)
        return (engine as? TraceEnginePort)?.events.orEmpty()
    }

    private fun run(op: Op) {
        when (op) {
            is Op.Screen -> trace(EngineTraceEvent.Screen(op.text))
            is Op.Emit -> trace(EngineTraceEvent.Emit(op.text))
            is Op.EmitIndexed -> trace(EngineTraceEvent.EmitIndexed(op.pattern, eval(op.value)))
            is Op.WaitInput -> trace(EngineTraceEvent.WaitInput(op.reason))
            is Op.Pause -> trace(EngineTraceEvent.Pause(op.millis))
            is Op.LetInt -> intVars[op.name] = eval(op.value)
            is Op.BindText -> stringVars[op.name] = eval(op.value)
            is Op.Set -> Unit
            is Op.Add -> Unit
            is Op.Sub -> Unit
            is Op.SetFlag -> trace(EngineTraceEvent.SetFlag(op.slug, op.nativeValue))
            is Op.If -> runAll(if (eval(op.cond)) op.thenOps else op.elseOps)
            is Op.Loop -> trace(EngineTraceEvent.Stop("trace runner does not execute loops"))
            is Op.Break -> trace(EngineTraceEvent.Break(op.reason))
            is Op.Stop -> trace(EngineTraceEvent.Stop(op.reason))
            is Op.Switch -> op.cases.firstOrNull { it.match == eval(op.expr) || it.match == null }?.let { runAll(it.body) }
            is Op.AskMember -> Unit
            is Op.AskMonster -> Unit
            is Op.ForEachMonster -> trace(EngineTraceEvent.Stop("trace runner does not iterate monsters"))
            is Op.RunScene -> trace(EngineTraceEvent.RunScene(op.scene, op.bindings))
            is Op.Encounter -> trace(EngineTraceEvent.Encounter(op.foe, op.mandatory, op.tier))
            Op.ConsumeActiveItem -> Unit
            is Op.GrantReward -> trace(EngineTraceEvent.GrantReward(op.reward))
            is Op.AchieveMilestone -> trace(EngineTraceEvent.AchieveMilestone(op.milestone))
            is Op.NativeCall -> engine.effect(EngineRequest(op.path, op.args))
        }
    }

    private fun runAll(ops: List<Op>) {
        ops.forEach(::run)
    }

    private fun eval(ast: IntAst): Int =
        when (ast) {
            is IntAst.Lit -> ast.value
            is IntAst.Var -> intVars[ast.name] ?: 0
            is IntAst.Binary -> evalBinary(eval(ast.left), ast.op, eval(ast.right))
            is IntAst.Random -> eval(ast.lo)
            is IntAst.MemberField -> 0
            is IntAst.MonsterField -> 0
            is IntAst.PartyScalar -> 0
            is IntAst.NativeInt -> engine.readInt(EngineRequest(ast.path, ast.args))
        }

    private fun eval(ast: BoolAst): Boolean =
        when (ast) {
            is BoolAst.Cmp -> compare(eval(ast.left), ast.op, eval(ast.right))
            is BoolAst.Not -> !eval(ast.expr)
            is BoolAst.And -> eval(ast.left) && eval(ast.right)
            is BoolAst.Or -> eval(ast.left) || eval(ast.right)
            is BoolAst.FlagIs -> false
            is BoolAst.MemberFlag -> false
            is BoolAst.MonsterFlag -> false
        }

    private fun eval(ast: StrAst): String =
        when (ast) {
            is StrAst.NativeString -> engine.readString(EngineRequest(ast.path, ast.args))
        }

    private fun evalBinary(left: Int, op: String, right: Int): Int =
        when (op) {
            "add" -> left + right
            "sub" -> left - right
            "mul" -> left * right
            "div" -> if (right == 0) 0 else left / right
            else -> 0
        }

    private fun compare(left: Int, op: String, right: Int): Boolean =
        when (op) {
            "eq" -> left == right
            "ne" -> left != right
            "lt" -> left < right
            "lte" -> left <= right
            "gt" -> left > right
            "gte" -> left >= right
            else -> false
        }

    private fun trace(event: EngineTraceEvent) {
        (engine as? TraceEnginePort)?.trace(event)
    }
}
