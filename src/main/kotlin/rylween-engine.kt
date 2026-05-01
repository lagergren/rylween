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
    data class Menu(val prompt: TextBlock, val keys: List<Char>) : EngineTraceEvent
    data class AskMember(val prompt: TextBlock, val filter: MemberChoiceFilter) : EngineTraceEvent
    data class AskInventorySlot(
        val prompt: TextBlock,
        val firstKey: Char,
        val slots: IntRange,
        val filter: InventorySlotChoiceFilter,
    ) : EngineTraceEvent
    data class GrantReward(val reward: ScriptRewardModel) : EngineTraceEvent
    data class AchieveMilestone(val milestone: GameMilestoneId) : EngineTraceEvent
    data class RunScene(val scene: SceneId, val bindings: Map<String, String>) : EngineTraceEvent
    data class Encounter(val monster: MonsterId, val mandatory: Boolean, val tier: EncounterTier?) : EngineTraceEvent
    data class Stop(val reason: String?, val signal: ScriptControlSignal) : EngineTraceEvent
    data class Break(val reason: String?, val signal: ScriptControlSignal) : EngineTraceEvent
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
        runAll(ops)
        return (engine as? TraceEnginePort)?.events.orEmpty()
    }

    private fun run(op: Op): ScriptControlSignal =
        when (op) {
            is Op.Screen -> ScriptControlSignal.Continue.also { trace(EngineTraceEvent.Screen(op.text)) }
            is Op.Emit -> ScriptControlSignal.Continue.also { trace(EngineTraceEvent.Emit(op.text)) }
            is Op.EmitIndexed -> ScriptControlSignal.Continue.also {
                trace(EngineTraceEvent.EmitIndexed(op.pattern, eval(op.value)))
            }
            is Op.WaitInput -> ScriptControlSignal.Continue.also { trace(EngineTraceEvent.WaitInput(op.reason)) }
            is Op.Pause -> ScriptControlSignal.Continue.also { trace(EngineTraceEvent.Pause(op.millis)) }
            is Op.LetInt -> ScriptControlSignal.Continue.also { intVars[op.name] = eval(op.value) }
            is Op.BindText -> ScriptControlSignal.Continue.also { stringVars[op.name] = eval(op.value) }
            is Op.Set -> ScriptControlSignal.Continue
            is Op.Add -> ScriptControlSignal.Continue
            is Op.Sub -> ScriptControlSignal.Continue
            is Op.SetFlag -> ScriptControlSignal.Continue.also {
                trace(EngineTraceEvent.SetFlag(op.slug, op.nativeValue))
            }
            is Op.If -> runAll(if (eval(op.cond)) op.thenOps else op.elseOps)
            is Op.Loop -> ScriptControlSignal.StopScript.also {
                trace(EngineTraceEvent.Stop("trace runner does not execute loops", ScriptControlSignal.StopScript))
            }
            is Op.Break -> op.signal.also { trace(EngineTraceEvent.Break(op.reason, op.signal)) }
            is Op.Stop -> op.signal.also { trace(EngineTraceEvent.Stop(op.reason, op.signal)) }
            is Op.Switch ->
                op.cases.firstOrNull { it.match == eval(op.expr) || it.match == null }?.let { runAll(it.body) }
                    ?: ScriptControlSignal.Continue
            is Op.Menu -> ScriptControlSignal.Continue.also {
                trace(EngineTraceEvent.Menu(op.prompt, op.options.map { option -> option.key }))
            }
            is Op.AskMember -> ScriptControlSignal.Continue.also {
                trace(EngineTraceEvent.AskMember(op.prompt, op.filter))
            }
            is Op.AskInventorySlot -> ScriptControlSignal.Continue.also {
                trace(EngineTraceEvent.AskInventorySlot(op.prompt, op.firstKey, op.slots, op.filter))
            }
            is Op.AskMonster -> ScriptControlSignal.Continue
            is Op.ForEachMonster -> ScriptControlSignal.StopScript.also {
                trace(EngineTraceEvent.Stop("trace runner does not iterate monsters", ScriptControlSignal.StopScript))
            }
            is Op.RunScene -> ScriptControlSignal.Continue.also {
                trace(EngineTraceEvent.RunScene(op.scene, op.bindings))
            }
            is Op.Encounter -> ScriptControlSignal.Continue.also {
                trace(EngineTraceEvent.Encounter(op.foe, op.mandatory, op.tier))
            }
            Op.ConsumeActiveItem -> ScriptControlSignal.Continue
            is Op.GrantReward -> ScriptControlSignal.Continue.also {
                trace(EngineTraceEvent.GrantReward(op.reward))
            }
            is Op.AchieveMilestone -> ScriptControlSignal.Continue.also {
                trace(EngineTraceEvent.AchieveMilestone(op.milestone))
            }
            is Op.NativeCall -> ScriptControlSignal.Continue.also {
                engine.effect(EngineRequest(op.path, op.args))
            }
        }

    private fun runAll(ops: List<Op>): ScriptControlSignal {
        ops.forEach { op ->
            val signal = run(op)
            if (signal != ScriptControlSignal.Continue) return signal
        }
        return ScriptControlSignal.Continue
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
