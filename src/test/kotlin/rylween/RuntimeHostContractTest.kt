package rylween

import rylween.compiled.readExternalRuntimeGame
import rylween.compiled.toExternalBundleArtifact
import rylween.engine.RuntimeHostDiagnosticKind
import rylween.engine.RuntimeHostStatus
import rylween.engine.RuntimeScriptExecutionStrategy
import rylween.engine.RylweenKotlinEngine
import rylween.engine.RylweenRuntimeHostContract
import rylween.frontend.terminal.TerminalRunStatus
import rylween.frontend.terminal.WildernessSessionTerminalAdapter
import rylween.frontend.terminal.terminalTextRenderer
import rylween.game.Maps
import rylween.game.RylweenCompiledGame
import rylween.game.States
import rylween.runtime.wilderness.WildernessSession
import rylween.runtime.wilderness.WildernessSessionResultStatus
import rylween.runtime.wilderness.worldMapGrid
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RuntimeHostContractTest {
    @Test
    fun kotlinAnsiDeclaresThePlayableJ3Surface() {
        val surface = RylweenRuntimeHostContract.kotlinAnsi

        RylweenRuntimeHostContract.standard.requirePlayable(surface)

        assertEquals(RuntimeHostStatus.PlayableRuntime, surface.status)
        assertNotEquals(RuntimeScriptExecutionStrategy.CompiledVm, surface.scriptExecution)
    }

    @Test
    fun nativeJsonReaderIsOnlyABoundaryProbeUntilItImplementsTheHostSurface() {
        val probe = RylweenRuntimeHostContract.nativeJsonBundleReader
        assertEquals(emptyList(), RylweenRuntimeHostContract.standard.validate(probe))

        val diagnostics = RylweenRuntimeHostContract.standard.validate(
            probe.copy(status = RuntimeHostStatus.PlayableRuntime),
        )

        assertTrue(diagnostics.any { it.kind == RuntimeHostDiagnosticKind.MissingRequirement })
        assertTrue(diagnostics.any { it.kind == RuntimeHostDiagnosticKind.MissingScriptExecution })
        assertTrue(diagnostics.any { it.kind == RuntimeHostDiagnosticKind.MissingViewCommand })
        assertTrue(diagnostics.any { it.kind == RuntimeHostDiagnosticKind.MissingConformanceSuite })
    }

    @Test
    fun bundleLoadedKotlinHostRunsAConformanceTerminalScenario() {
        val loaded = RylweenCompiledGame.nextRylween.toExternalBundleArtifact().readExternalRuntimeGame()
        val content = loaded.content
        val state = loaded.states.single { it.id == States.freshReplay }
        val worldMap = checkNotNull(content.worldMapGrid(Maps.wilderness))
        val engine = RylweenKotlinEngine(content)

        TerminalScenario(
            name = "j3-bundle-loaded-wilderness-help",
            fixture = ScenarioFixture(startState = States.freshReplay, seed = state.realm.rngState),
            inputs = listOf("?", ""),
            start = {
                engine.wildernessSession(
                    state = state,
                    worldMap = worldMap,
                    rngState = state.realm.rngState,
                    realizeNpcs = false,
                )
            },
            drive = { terminal, session: WildernessSession ->
                WildernessSessionTerminalAdapter(
                    terminal = terminal,
                    renderer = content.terminalTextRenderer(),
                ).runUntilBlocked(session)
            },
            checkpoints = listOf(
                checkpoint("bundle host consumes semantic input and renders generated help") {
                    assertTrue(result.status is TerminalRunStatus.Waiting)
                    assertEquals(WildernessSessionResultStatus.HelpShown, result.session.lastResult?.status)
                    assertOutputContains("Move North")
                    assertOutputContains("Save Game")
                    assertOutputDoesNotContain("not wired")
                    assertOutputDoesNotContain("not ported")
                },
            ),
        ).run()
    }
}
