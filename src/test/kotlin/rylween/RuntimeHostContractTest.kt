package rylween

import rylween.compiled.readExternalRuntimeGame
import rylween.compiled.toExternalBundleArtifact
import rylween.dsl.GameModel
import rylween.dsl.DungeonScenarioModel
import rylween.dsl.RuntimeContent
import rylween.dsl.StateModel
import rylween.dsl.runtimeContent
import rylween.dsl.stateId
import rylween.engine.RuntimeConformanceSuite
import rylween.engine.RuntimeHostDiagnosticKind
import rylween.engine.RuntimeHostStatus
import rylween.engine.RuntimeScriptExecutionStrategy
import rylween.engine.RylweenKotlinEngine
import rylween.engine.RylweenRuntimeHostContract
import rylween.frontend.terminal.BattleSessionTerminalAdapter
import rylween.frontend.terminal.DungeonSessionTerminalAdapter
import rylween.frontend.terminal.TerminalRunStatus
import rylween.frontend.terminal.TerminalTextContext
import rylween.frontend.terminal.TownSessionTerminalAdapter
import rylween.frontend.terminal.WildernessSessionTerminalAdapter
import rylween.frontend.terminal.YdargSessionTerminalAdapter
import rylween.frontend.terminal.renderTerminalStaticScreen
import rylween.frontend.terminal.terminalTextRenderer
import rylween.game.Dungeons
import rylween.game.EncounterTiers
import rylween.game.Maps
import rylween.game.RylweenCompiledGame
import rylween.game.StaticScreens
import rylween.game.States
import rylween.game.Towns
import rylween.runtime.RuntimeSessionState
import rylween.runtime.battle.BattleSession
import rylween.runtime.dungeon.DungeonSession
import rylween.runtime.dungeon.DungeonSessionResultStatus
import rylween.runtime.toRuntimeSessionState
import rylween.runtime.town.TownSession
import rylween.runtime.wilderness.WildernessSession
import rylween.runtime.wilderness.WildernessSessionResultStatus
import rylween.runtime.wilderness.WorldMapGrid
import rylween.runtime.wilderness.worldMapGrid
import rylween.runtime.ydarg.YdargSession
import rylween.runtime.ydarg.YdargSessionStatus
import rylween.save.RuntimeSaveOutcome
import rylween.save.RuntimeSavePort
import rylween.save.renderRuntimeStateSnapshot
import rylween.save.toRuntimeStateSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @Test
    fun bundleLoadedKotlinHostCoversTheNamedJ3ConformanceSuites() {
        val loaded = RylweenCompiledGame.nextRylween.toExternalBundleArtifact().readExternalRuntimeGame()
        val content = loaded.content
        val state = loaded.states.single { it.id == States.freshReplay }
        val worldMap = checkNotNull(content.worldMapGrid(Maps.wilderness))
        val runtime = state.toRuntimeSessionState(state.realm.rngState)
        val runtimeContent = content.runtimeContent()
        val engine = RylweenKotlinEngine(content)
        val covered = mutableSetOf<RuntimeConformanceSuite>()

        covered += staticScreenScenario(content, runtime).run().suites
        covered += wildernessBundleBootScenario(content, state, worldMap, engine).run().suites
        covered += townScenario(content, runtime, engine).run().suites
        covered += dungeonScenario(content, runtime, runtimeContent, worldMap).run().suites
        covered += castleScenario(content, runtime, runtimeContent).run().suites
        covered += battleScenario(content, runtime, runtimeContent).run().suites
        covered += saveLoadScenario(content, state, worldMap, engine).run().suites

        assertEquals(RuntimeConformanceSuite.entries.toSet(), covered)
    }

    private fun staticScreenScenario(
        content: GameModel,
        runtime: RuntimeSessionState,
    ): HeadlessScenario<GameModel> =
        HeadlessScenario(
            name = "j3-static-screen-help",
            fixture = ScenarioFixture(startState = States.freshReplay),
            start = { content },
            steps = listOf(headlessStep("render help") { it }),
            checkpoints = listOf(
                headlessCheckpoint("help screen is rendered from authored command bindings") {
                    val screen = checkNotNull(
                        result.renderTerminalStaticScreen(
                            id = StaticScreens.help,
                            resourceText = { "" },
                            context = TerminalTextContext(runtime = runtime),
                        ),
                    )
                    assertTrue(screen.pages.flatten().any { "Move North" in it })
                },
            ),
            suites = setOf(RuntimeConformanceSuite.StaticScreens),
        )

    private fun wildernessBundleBootScenario(
        content: GameModel,
        state: StateModel,
        worldMap: WorldMapGrid,
        engine: RylweenKotlinEngine,
    ): TerminalScenario<WildernessSession> =
        TerminalScenario(
            name = "j3-wilderness-bundle-boot-help",
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
            drive = { terminal, session ->
                WildernessSessionTerminalAdapter(
                    terminal = terminal,
                    renderer = content.terminalTextRenderer(),
                ).runUntilBlocked(session)
            },
            checkpoints = listOf(
                checkpoint("semantic input and generated help") {
                    assertTrue(result.status is TerminalRunStatus.Waiting)
                    assertEquals(WildernessSessionResultStatus.HelpShown, result.session.lastResult?.status)
                    assertOutputContains("Move North")
                },
            ),
            suites = setOf(
                RuntimeConformanceSuite.BundleBoot,
                RuntimeConformanceSuite.WildernessNavigation,
            ),
        )

    private fun townScenario(
        content: GameModel,
        runtime: RuntimeSessionState,
        engine: RylweenKotlinEngine,
    ): TerminalScenario<TownSession> =
        TerminalScenario(
            name = "j3-town-menu",
            fixture = ScenarioFixture(startState = States.freshReplay, seed = runtime.realm.rngState),
            inputs = emptyList(),
            start = {
                engine.townSession(
                    party = runtime.activeParty,
                    town = Towns.thord.id,
                    hour = 12,
                    rngState = runtime.realm.rngState,
                    realizeNpcs = false,
                )
            },
            drive = { terminal, session ->
                TownSessionTerminalAdapter(
                    terminal = terminal,
                    renderer = content.terminalTextRenderer(),
                ).runUntilBlocked(session)
            },
            checkpoints = listOf(
                checkpoint("town choices render through bindings") {
                    assertTrue(result.status is TerminalRunStatus.Waiting)
                    assertOutputContains("(M)erchant")
                    assertOutputContains("(L)eave")
                },
            ),
            suites = setOf(RuntimeConformanceSuite.TownServices),
        )

    private fun dungeonScenario(
        content: GameModel,
        runtime: RuntimeSessionState,
        runtimeContent: RuntimeContent,
        worldMap: WorldMapGrid,
    ): TerminalScenario<DungeonSession> =
        TerminalScenario(
            name = "j3-dungeon-help",
            fixture = ScenarioFixture(startState = States.freshReplay, seed = runtime.realm.rngState),
            inputs = listOf("?", ""),
            start = {
                val dungeons = content.dungeons.copy(
                    scenarios = listOf(
                        DungeonScenarioModel(
                            dungeon = Dungeons.randomCave,
                            levelRange = 1..1,
                            fixedLevelEvents = emptyMap(),
                            randomEvents = emptyList(),
                        ),
                    ),
                )
                DungeonSession.start(
                    runtime = runtime,
                    worldMap = worldMap,
                    dungeon = Dungeons.randomCave,
                    levels = 1,
                    content = runtimeContent,
                    dungeons = dungeons,
                )
            },
            drive = { terminal, session ->
                DungeonSessionTerminalAdapter(
                    terminal = terminal,
                    renderer = content.terminalTextRenderer(),
                ).runUntilBlocked(session)
            },
            checkpoints = listOf(
                checkpoint("dungeon help renders authored dungeon bindings") {
                    assertTrue(result.status is TerminalRunStatus.Waiting)
                    assertEquals(DungeonSessionResultStatus.HelpShown, result.session.lastResult?.status)
                    assertOutputContains("Descend")
                },
            ),
            suites = setOf(RuntimeConformanceSuite.DungeonFlow),
        )

    private fun castleScenario(
        content: GameModel,
        runtime: RuntimeSessionState,
        runtimeContent: RuntimeContent,
    ): TerminalScenario<YdargSession> =
        TerminalScenario(
            name = "j3-castle-gate",
            fixture = ScenarioFixture(startState = States.freshReplay, seed = runtime.realm.rngState),
            inputs = emptyList(),
            start = { YdargSession.start(runtime, runtimeContent) },
            drive = { terminal, session ->
                YdargSessionTerminalAdapter(
                    terminal = terminal,
                    renderer = content.terminalTextRenderer(),
                ).runUntilBlocked(session)
            },
            checkpoints = listOf(
                checkpoint("castle flow emits typed image view command") {
                    assertTrue(result.status is TerminalRunStatus.Finished)
                    assertEquals(YdargSessionStatus.MissingKey, result.session.lastResult?.status)
                    assertFalse(terminal.viewCommands.isEmpty())
                    assertImageShown("blitgfx.ydarg_castle")
                },
            ),
            suites = setOf(RuntimeConformanceSuite.CastleFlow, RuntimeConformanceSuite.ViewCommands),
        )

    private fun battleScenario(
        content: GameModel,
        runtime: RuntimeSessionState,
        runtimeContent: RuntimeContent,
    ): TerminalScenario<BattleSession> =
        TerminalScenario(
            name = "j3-battle-opening",
            fixture = ScenarioFixture(startState = States.freshReplay, seed = runtime.realm.rngState),
            inputs = emptyList(),
            start = {
                BattleSession.start(
                    runtime = runtime,
                    tier = EncounterTiers.weak,
                    content = runtimeContent,
                )
            },
            drive = { terminal, session ->
                BattleSessionTerminalAdapter(
                    terminal = terminal,
                    renderer = content.terminalTextRenderer(),
                ).runUntilBlocked(session)
            },
            checkpoints = listOf(
                checkpoint("battle opening blocks on authored fight-run prompt") {
                    assertTrue(result.status is TerminalRunStatus.Waiting)
                    assertOutputContains("(F)ight")
                    assertOutputContains("(R)un")
                },
            ),
            suites = setOf(RuntimeConformanceSuite.BattleFlow),
        )

    private fun saveLoadScenario(
        content: GameModel,
        state: StateModel,
        worldMap: WorldMapGrid,
        engine: RylweenKotlinEngine,
    ): TerminalScenario<WildernessSession> {
        var renderedSave: String? = null
        return TerminalScenario(
            name = "j3-save-load",
            fixture = ScenarioFixture(startState = States.freshReplay, seed = state.realm.rngState),
            inputs = listOf("S"),
            start = {
                engine.wildernessSession(
                    state = state,
                    worldMap = worldMap,
                    rngState = state.realm.rngState,
                    realizeNpcs = false,
                )
            },
            drive = { terminal, session ->
                WildernessSessionTerminalAdapter(
                    terminal = terminal,
                    renderer = content.terminalTextRenderer(),
                    savePort = RuntimeSavePort { runtime ->
                        renderedSave = renderRuntimeStateSnapshot(runtime.toRuntimeStateSnapshot(stateId("j3-save")))
                        RuntimeSaveOutcome(saved = true, target = "memory")
                    },
                ).runUntilBlocked(session)
            },
            checkpoints = listOf(
                checkpoint("save port receives parseable runtime state") {
                    assertTrue(result.status is TerminalRunStatus.Waiting)
                    assertTrue(checkNotNull(renderedSave).contains("runtimeState"))
                    assertOutputContains("Saving position")
                    assertOutputContains("Done.")
                },
            ),
            suites = setOf(RuntimeConformanceSuite.SaveLoad),
        )
    }
}
