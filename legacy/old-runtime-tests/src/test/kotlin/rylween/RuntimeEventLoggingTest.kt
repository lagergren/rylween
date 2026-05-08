package rylween

import rylween.dsl.BuildingServiceModel
import rylween.dsl.FormulaModel
import rylween.dsl.IntAst
import rylween.dsl.Op
import rylween.dsl.SceneId
import rylween.dsl.SceneModel
import rylween.dsl.building
import rylween.dsl.formula
import rylween.dsl.inlineText
import rylween.dsl.keyAction
import rylween.engine.RuntimeEvent
import rylween.engine.RuntimeEventScope
import rylween.engine.RuntimeEventSink
import rylween.engine.TraceEnginePort
import rylween.frontend.terminal.StatefulScriptBridge
import rylween.frontend.terminal.TerminalPort
import rylween.game.RylweenCompiledGame
import rylween.runtime.ScriptContinuation
import rylween.runtime.battle.evalFormula
import rylween.runtime.toRuntimeSessionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuntimeEventLoggingTest {
    @Test
    fun formulaEvaluationEmitsDebugRuntimeEvent() {
        val sink = CapturingRuntimeEventSink()
        val formulas =
            listOf(
                FormulaModel(
                    formula("diagnostic_formula"),
                    IntAst.Binary(IntAst.Var("base"), "add", IntAst.Lit(7)),
                ),
            )

        val value =
            RuntimeEventScope.withSink(sink) {
                formulas.evalFormula(formula("diagnostic_formula"), mapOf("base" to 35))
            }

        assertEquals(42, value)
        val event = sink.events.single()
        assertEquals("formula", event.module)
        assertEquals("eval", event.action)
        assertEquals("diagnostic_formula", event.fields["id"])
        assertEquals("42", event.fields["value"])
        assertEquals("base:35", event.fields["bindings"])
    }

    @Test
    fun liveScriptBridgeEmitsVmOpDiagnosticsWithoutAddingTraceEvents() {
        val sink = CapturingRuntimeEventSink()
        val engine = TraceEnginePort(eventSink = sink)
        val service =
            BuildingServiceModel(
                building = building("diagnostic"),
                action = keyAction("diagnostic"),
                scene = SceneId("diagnostic"),
                opensAt = null,
                closesAt = null,
                nativeServices = emptyList(),
            )
        val scene = SceneModel(SceneId("diagnostic"), listOf(Op.Screen(inlineText("hello"))))
        val runtime =
            RylweenCompiledGame.nextRylween.states
                .first()
                .toRuntimeSessionState()

        StatefulScriptBridge(NoopTerminalPort, engine = engine).run(
            service = service,
            scene = scene,
            continuation = ScriptContinuation.start(runtime),
        )

        assertTrue(engine.events.any { it::class.simpleName == "Screen" })
        assertTrue(engine.events.none { event -> event::class.simpleName == "ScriptOp" })
        val vmEvent = sink.events.single { it.module == "vm" && it.action == "op" }
        assertEquals("Screen", vmEvent.fields["opcode"])
        assertEquals("0", vmEvent.fields["index"])
    }
}

private class CapturingRuntimeEventSink : RuntimeEventSink {
    private val mutableEvents = mutableListOf<RuntimeEvent>()
    val events: List<RuntimeEvent> get() = mutableEvents.toList()

    override fun emit(event: RuntimeEvent) {
        mutableEvents += event
    }
}

private object NoopTerminalPort : TerminalPort {
    override fun write(lines: List<String>) = Unit

    override fun readLine(): String? = null
}
