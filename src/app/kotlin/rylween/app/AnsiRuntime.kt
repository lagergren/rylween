@file:Suppress("ReplacePrintlnWithLogging", "UseOfSystemOutOrSystemErr")

package rylween.app

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.eagerOption
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import org.jline.terminal.Attributes
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import org.jline.utils.NonBlockingReader
import rylween.compiled.ExternalBundleArtifactSummary
import rylween.compiled.readExternalRuntimeGame
import rylween.compiled.readVerifiedExternalBundleArtifact
import rylween.compiled.toExternalBundleArtifact
import rylween.dsl.BuildingServiceModel
import rylween.dsl.GameModel
import rylween.dsl.GridPoint
import rylween.dsl.ImageId
import rylween.dsl.InputBindingModel
import rylween.dsl.LayoutModel
import rylween.dsl.MissionModel
import rylween.dsl.PaletteColorId
import rylween.dsl.RylweenModel
import rylween.dsl.ScreenRegionModel
import rylween.dsl.StateId
import rylween.dsl.StaticScreenId
import rylween.dsl.TerminalPresentationModel
import rylween.dsl.TerminalTerrainStyleModel
import rylween.dsl.TextBlock
import rylween.dsl.TownModel
import rylween.dsl.ViewCommand
import rylween.dsl.VisualsModel
import rylween.dsl.Wilderness
import rylween.dsl.WorldPosition
import rylween.dsl.building
import rylween.dsl.cells
import rylween.dsl.dungeon
import rylween.dsl.encounterTier
import rylween.dsl.image
import rylween.dsl.keyAction
import rylween.dsl.layout
import rylween.dsl.line
import rylween.dsl.lines
import rylween.dsl.matches
import rylween.dsl.runtimeContent
import rylween.dsl.stateId
import rylween.dsl.staticScreen
import rylween.dsl.terrain
import rylween.dsl.text
import rylween.dsl.town
import rylween.dsl.worldMap
import rylween.engine.RuntimeEvent
import rylween.engine.RuntimeEventLevel
import rylween.engine.RuntimeEventScope
import rylween.engine.RuntimeEventSink
import rylween.engine.RuntimeStartupTarget
import rylween.engine.RylweenKotlinEngine
import rylween.engine.TraceEnginePort
import rylween.engine.createInitialWorldSnapshot
import rylween.engine.runtimeEvent
import rylween.engine.selectStartupState
import rylween.engine.validateStartupState
import rylween.frontend.terminal.BattleSessionTerminalAdapter
import rylween.frontend.terminal.CampSessionTerminalAdapter
import rylween.frontend.terminal.DungeonSessionTerminalAdapter
import rylween.frontend.terminal.EquipmentPromptTerminalAdapter
import rylween.frontend.terminal.HuntSessionTerminalAdapter
import rylween.frontend.terminal.InventoryDropTerminalAdapter
import rylween.frontend.terminal.InventoryItemUseTerminalAdapter
import rylween.frontend.terminal.PartyJournalTerminalAdapter
import rylween.frontend.terminal.PartyViewTerminalAdapter
import rylween.frontend.terminal.PriestSpellTerminalAdapter
import rylween.frontend.terminal.ScriptBridgeStatus
import rylween.frontend.terminal.StatefulScriptBridge
import rylween.frontend.terminal.TerminalInputRejection
import rylween.frontend.terminal.TerminalPort
import rylween.frontend.terminal.TerminalRunStatus
import rylween.frontend.terminal.TerminalTextContext
import rylween.frontend.terminal.TerminalTextRenderer
import rylween.frontend.terminal.TownSessionTerminalAdapter
import rylween.frontend.terminal.YdargSessionTerminalAdapter
import rylween.frontend.terminal.map
import rylween.frontend.terminal.matches
import rylween.frontend.terminal.readChoice
import rylween.frontend.terminal.renderTerminalStaticScreen
import rylween.frontend.terminal.staticScreenRequiresRuntimeState
import rylween.frontend.terminal.terminalTextRenderer
import rylween.frontend.terminal.writeScriptEvents
import rylween.game.Buildings
import rylween.game.Maps
import rylween.game.PaletteColors
import rylween.game.RylweenCompiledGame
import rylween.game.ScreenRegions
import rylween.game.States
import rylween.game.StaticScreens
import rylween.runtime.NpcPlacementRealizerConfig
import rylween.runtime.RuntimeSessionState
import rylween.runtime.RuntimeSnapshotModel
import rylween.runtime.ScriptContinuation
import rylween.runtime.WildernessNpcPlacementPolicy
import rylween.runtime.inputForToken
import rylween.runtime.party
import rylween.runtime.prompt
import rylween.runtime.toRuntimeSessionState
import rylween.runtime.town.SpeakRuntimeContent
import rylween.runtime.wilderness.WildernessSession
import rylween.runtime.wilderness.WildernessSessionInput
import rylween.runtime.wilderness.WildernessSessionResultStatus
import rylween.runtime.wilderness.WildernessSessionStep
import rylween.runtime.wilderness.WorldMapGrid
import rylween.runtime.wilderness.effectiveHelpPages
import rylween.runtime.wilderness.exploreCommands
import rylween.runtime.wilderness.hutService
import rylween.runtime.wilderness.worldMapGrid
import rylween.save.FileRuntimeSavePort
import rylween.save.RuntimeSaveOutcome
import rylween.save.RuntimeSavePort
import rylween.save.loadRuntimeSnapshot
import rylween.save.runtimeSaveSlotFile
import rylween.save.runtimeSaveSlotStateId
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets

private const val ESC = "\u001B"
private const val CSI = "$ESC["
private const val RESET = "${CSI}0m"

@Suppress("unused")
private const val FG_BLACK = "${CSI}30m"
private const val FG_GREEN = "${CSI}32m"
private const val FG_WHITE = "${CSI}37m"
private const val BG_BLACK = "${CSI}40m"
private const val BG_BLUE = "${CSI}44m"

private data class AnsiRuntimeConfig(
    val state: StateId? = null,
    val seed: Long? = null,
    val hour: Int? = null,
    val bundleFile: File? = null,
    val loadFile: File? = null,
    val saveFile: File = runtimeSaveSlotFile("main"),
    val saveState: StateId = runtimeSaveSlotStateId("main"),
    val staticScreen: StaticScreenId? = null,
    val newGame: Boolean = false,
    val playerSlot: String? = null,
    val serve: String? = null,
    val logSpec: String = "",
    val logLevel: String = "",
    val dumpBundle: Boolean = false,
)

object RylweenAnsiMain {
    @JvmStatic
    fun main(args: Array<String>) {
        RylweenAnsiCommand().main(args)
    }
}

private fun runConfiguredAnsi(parsedConfig: AnsiRuntimeConfig): Unit =
    runRylweenCli {
        val bundleFile = parsedConfig.bundleFile
        val game = bundleFile?.readExternalRuntimeGame() ?: RylweenCompiledGame.nextRylween
        val config = resolveAnsiPlayerSlot(parsedConfig, game)
        val content = game.content
        val bundleSummary = bundleFile?.readVerifiedExternalBundleArtifact(game)
        val dumpBundle = config.dumpBundle
        val renderer = content.terminalTextRenderer()
        val worldMap =
            content.worldMapGrid(Maps.wilderness)
                ?: error("missing authored wilderness map")
        val engine =
            RylweenKotlinEngine(
                content = content,
                npcPlacementConfig =
                    NpcPlacementRealizerConfig(
                        wilderness = WildernessNpcPlacementPolicy(allowedPoints = worldMap.passableNpcPoints()),
                    ),
            )
        config.staticScreen?.takeUnless { screenId -> content.staticScreenRequiresRuntimeState(screenId) }?.let { screenId ->
            withConfiguredAnsiSession(config, bundleFile, bundleSummary, game.takeIf { dumpBundle }) { _, terminal ->
                ClassicAnsiScreen(terminal, content.visuals, content.terminal, content.layouts, content.towns)
                    .showStaticScreen(content, screenId, renderer)
            }
            return@runRylweenCli
        }
        config.staticScreen?.let { screenId ->
            val bootSnapshot =
                applyConfiguredHour(
                    config,
                    loadConfiguredOrSavedSnapshot(game, content, config),
                )
            withConfiguredAnsiSession(config, bundleFile, bundleSummary, game.takeIf { dumpBundle }) { _, terminal ->
                ClassicAnsiScreen(terminal, content.visuals, content.terminal, content.layouts, content.towns)
                    .showStaticScreen(
                        content = content,
                        screenId = screenId,
                        renderer = renderer,
                        context = TerminalTextContext(runtime = bootSnapshot.state.toRuntimeSessionState()),
                    )
            }
            return@runRylweenCli
        }

        withConfiguredAnsiSession(config, bundleFile, bundleSummary, game.takeIf { dumpBundle }) { log, terminal ->
            if (config.newGame || config.loadFile != null || config.state != null) {
                val snapshot =
                    if (config.newGame && config.loadFile == null && config.state == null) {
                        createInitialWorldSnapshot(game, content, States.freshReplay, config.seed)
                    } else {
                        loadConfiguredSnapshot(game, content, config)
                    }
                val session =
                    bootAnsiWildernessSession(
                        config = config,
                        content = content,
                        renderer = renderer,
                        engine = engine,
                        terminal = terminal,
                        log = log,
                        snapshot = applyConfiguredHour(config, snapshot),
                        worldMap = worldMap,
                        newGame = config.newGame,
                    )
                val savePort = FileRuntimeSavePort(config.saveFile, config.saveState, content)
                if (config.newGame) saveInitialRuntime(savePort, session.runtime, log)
                runAnsiWilderness(
                    terminal = terminal,
                    content = content,
                    renderer = renderer,
                    engine = engine,
                    savePort = savePort,
                    log = log,
                    session = session,
                )
            } else {
                runAnsiTitleMenu(
                    config = config,
                    game = game,
                    content = content,
                    renderer = renderer,
                    engine = engine,
                    terminal = terminal,
                    log = log,
                    worldMap = worldMap,
                )
            }
        }
    }

private fun openConfiguredAnsiTerminal(
    config: AnsiRuntimeConfig,
    log: RylweenLog,
): AnsiTerminal =
    config.serve
        ?.let { spec -> SocketAnsiTerminal.accept(spec, log) }
        ?: JLineAnsiTerminal.open(log)

private inline fun withConfiguredAnsiSession(
    config: AnsiRuntimeConfig,
    bundleFile: File?,
    bundleSummary: ExternalBundleArtifactSummary?,
    dumpGame: RylweenModel?,
    crossinline block: (RylweenLog, AnsiTerminal) -> Unit,
) {
    val effectiveSpec = config.logSpec.ifBlank { if (config.serve != null) "stderr" else "" }
    RylweenLog.open(spec = effectiveSpec, level = config.logLevel).use { log ->
        RuntimeEventScope.withSink(log) {
            if (bundleFile != null && bundleSummary != null) {
                log.audit("bundle", "verified ${bundleFile.path} ${bundleSummary.bundleContentHash}")
            }
            if (dumpGame != null) {
                val pretty = dumpGame.toExternalBundleArtifact().decodeToString().trimEnd('\n')
                log.emit(RuntimeEventLevel.Debug, "bundle", pretty)
            }
            openConfiguredAnsiTerminal(config, log).use { terminal ->
                block(log, terminal)
            }
        }
    }
}

private enum class TitleMenuAction {
    NewParty,
    Continue,
    Terminate,
    Scores,
    Heroes,
    Documentation,
    Exit,
}

private fun runAnsiTitleMenu(
    config: AnsiRuntimeConfig,
    game: RylweenModel,
    content: GameModel,
    renderer: TerminalTextRenderer,
    engine: RylweenKotlinEngine,
    terminal: AnsiTerminal,
    log: RylweenLog,
    worldMap: WorldMapGrid,
) {
    val screen = ClassicAnsiScreen(terminal, content.visuals, content.terminal, content.layouts, content.towns)
    screen.showStaticScreen(content, StaticScreens.title, renderer)
    val savePort = FileRuntimeSavePort(config.saveFile, config.saveState, content)
    while (true) {
        val snapshot = applyConfiguredHour(config, loadSavedOrInitialSnapshot(game, content, config))
        val hasSavedGame = config.saveFile.exists() && snapshot.state.parties.isNotEmpty()
        screen.bootFrame()
        screen.writeText(titleMenuLines(hasSavedGame))
        when (readTitleMenuAction(terminal, screen, hasSavedGame)) {
            null,
            TitleMenuAction.Exit,
            -> return
            TitleMenuAction.NewParty -> {
                val clean =
                    applyConfiguredHour(
                        config,
                        createInitialWorldSnapshot(game, content, States.freshReplay, config.seed),
                    )
                val session =
                    bootAnsiWildernessSession(
                        config = config,
                        content = content,
                        renderer = renderer,
                        engine = engine,
                        terminal = terminal,
                        log = log,
                        snapshot = clean,
                        worldMap = worldMap,
                        newGame = true,
                    )
                saveInitialRuntime(savePort, session.runtime, log)
                runAnsiWilderness(terminal, content, renderer, engine, savePort, log, session)
                return
            }
            TitleMenuAction.Continue -> {
                val session =
                    bootAnsiWildernessSession(
                        config = config,
                        content = content,
                        renderer = renderer,
                        engine = engine,
                        terminal = terminal,
                        log = log,
                        snapshot = snapshot,
                        worldMap = worldMap,
                        newGame = false,
                    )
                runAnsiWilderness(terminal, content, renderer, engine, savePort, log, session)
                return
            }
            TitleMenuAction.Terminate -> {
                screen.writeText(listOf("Terminate saved party?", "", "(Y)es", "(N)o"))
                if (readYesNoKey(terminal, screen) == true) {
                    val deleted =
                        runCatching { !config.saveFile.exists() || config.saveFile.delete() }
                            .getOrDefault(false)
                    if (deleted) {
                        log.audit("title", "deleted saved world ${config.saveFile.path}")
                        screen.writeText(listOf("Saved party terminated.", ""))
                    } else {
                        screen.writeText(listOf("Could not delete saved party.", config.saveFile.path))
                    }
                    terminal.readKeyToken()
                }
            }
            TitleMenuAction.Scores -> {
                screen.showStaticScreen(
                    content = content,
                    screenId = StaticScreens.score,
                    renderer = renderer,
                    context = TerminalTextContext(runtime = snapshot.state.toRuntimeSessionState()),
                )
                screen.bootFrame()
                screen.writeText(listOf("View verbose scores?", "", "(Y)es", "(N)o"))
                if (readYesNoKey(terminal, screen) == true) {
                    screen.showStaticScreen(
                        content = content,
                        screenId = StaticScreens.verboseScore,
                        renderer = renderer,
                        context = TerminalTextContext(runtime = snapshot.state.toRuntimeSessionState()),
                    )
                }
            }
            TitleMenuAction.Heroes -> {
                screen.showStaticScreen(
                    content = content,
                    screenId = StaticScreens.heroes,
                    renderer = renderer,
                    context = TerminalTextContext(runtime = snapshot.state.toRuntimeSessionState()),
                )
            }
            TitleMenuAction.Documentation -> {
                screen.showStaticScreen(content, StaticScreens.manual, renderer)
            }
        }
    }
}

private fun titleMenuLines(hasSavedGame: Boolean): List<String> =
    buildList {
        add("Rylween")
        add("")
        if (hasSavedGame) {
            add("C. Continue Saved Game")
            add("T. Terminate Party")
        } else {
            add("N. Create New Party")
        }
        add("S. View Current Scores")
        add("H. View Hall of Fame")
        add("D. View Documentation")
        add("X. Exit")
    }

private fun readTitleMenuAction(
    terminal: AnsiTerminal,
    screen: ClassicAnsiScreen,
    hasSavedGame: Boolean,
): TitleMenuAction? {
    while (true) {
        when (val token = terminal.readKeyToken()?.uppercase()) {
            null -> return null
            "N" if !hasSavedGame -> return TitleMenuAction.NewParty
            "C" if hasSavedGame -> return TitleMenuAction.Continue
            "T" if hasSavedGame -> return TitleMenuAction.Terminate
            "S" -> return TitleMenuAction.Scores
            "H" -> return TitleMenuAction.Heroes
            "D" -> return TitleMenuAction.Documentation
            "X" -> return TitleMenuAction.Exit
            else -> {
                screen.writeText(listOf("Invalid input.", "Key: $token", ""))
                terminal.readKeyToken()
                screen.writeText(titleMenuLines(hasSavedGame))
            }
        }
    }
}

private fun readYesNoKey(
    terminal: AnsiTerminal,
    screen: ClassicAnsiScreen,
): Boolean? {
    while (true) {
        when (terminal.readKeyToken()?.uppercase()) {
            null -> return null
            "Y" -> return true
            "N" -> return false
            else -> {
                screen.writeText(listOf("Invalid input.", "Expected Y or N.", ""))
                terminal.readKeyToken()
            }
        }
    }
}

private fun bootAnsiWildernessSession(
    config: AnsiRuntimeConfig,
    content: GameModel,
    renderer: TerminalTextRenderer,
    engine: RylweenKotlinEngine,
    terminal: AnsiTerminal,
    log: RylweenLog,
    snapshot: RuntimeSnapshotModel,
    worldMap: WorldMapGrid,
    newGame: Boolean,
): WildernessSession {
    log.info(
        "ansi",
        "boot state=${snapshot.state.id.slug} seed=${config.seed ?: snapshot.state.realm.rngState} new=$newGame",
    )
    val creationPort =
        if (newGame) {
            val creationScreen =
                ClassicAnsiScreen(
                    terminal,
                    content.visuals,
                    content.terminal,
                    content.layouts,
                    content.towns,
                )
            creationScreen.bootFrame()
            AnsiTerminalPort(terminal, creationScreen)
        } else {
            null
        }
    return bootWildernessSession(
        engine = engine,
        terminal = creationPort,
        renderer = renderer,
        snapshot = snapshot,
        worldMap = worldMap,
        rngState = config.seed ?: snapshot.state.realm.rngState,
        newGame = newGame,
    )
}

private fun runAnsiWilderness(
    terminal: AnsiTerminal,
    content: GameModel,
    renderer: TerminalTextRenderer,
    engine: RylweenKotlinEngine,
    savePort: RuntimeSavePort,
    log: RylweenLog,
    session: WildernessSession,
) {
    RylweenAnsiGame(
        terminal = terminal,
        content = content,
        renderer = renderer,
        speakContent = engine.speakContent(),
        savePort = savePort,
        log = log,
    ).run(session)
}

private fun saveInitialRuntime(
    savePort: RuntimeSavePort,
    runtime: RuntimeSessionState,
    log: RylweenLog,
) {
    val outcome =
        runCatching { savePort.save(runtime) }
            .getOrElse { error -> RuntimeSaveOutcome(saved = false, error = error.message) }
    if (outcome.saved) {
        log.audit("title", "saved new world ${outcome.target.orEmpty()}")
    } else {
        log.warn("title", "initial save failed: ${outcome.error ?: "unknown error"}")
    }
}

private fun loadConfiguredSnapshot(
    game: RylweenModel,
    content: GameModel,
    config: AnsiRuntimeConfig,
    target: RuntimeStartupTarget = RuntimeStartupTarget.Wilderness,
): RuntimeSnapshotModel =
    config.loadFile
        ?.let { file ->
            loadRuntimeSnapshot(file, content).also { snapshot ->
                validateStartupState(snapshot.state, content, target, "save file ${file.path}")
            }
        }
        ?: RuntimeSnapshotModel(
            selectStartupState(
                game = game,
                content = content,
                state = checkNotNull(config.state) { "no startup state configured" },
                target = target,
            ),
        )

private fun loadConfiguredOrSavedSnapshot(
    game: RylweenModel,
    content: GameModel,
    config: AnsiRuntimeConfig,
): RuntimeSnapshotModel =
    if (config.loadFile != null || config.state != null) {
        loadConfiguredSnapshot(game, content, config, RuntimeStartupTarget.StaticScreen)
    } else {
        loadSavedOrInitialSnapshot(game, content, config, RuntimeStartupTarget.StaticScreen)
    }

private fun loadSavedOrInitialSnapshot(
    game: RylweenModel,
    content: GameModel,
    config: AnsiRuntimeConfig,
    target: RuntimeStartupTarget = RuntimeStartupTarget.Wilderness,
): RuntimeSnapshotModel =
    if (config.saveFile.exists()) {
        loadRuntimeSnapshot(config.saveFile, content).also { snapshot ->
            validateStartupState(
                snapshot.state,
                content,
                target,
                "save file ${config.saveFile.path}",
            )
        }
    } else {
        createInitialWorldSnapshot(game, content, States.freshReplay, config.seed)
    }

private fun applyConfiguredHour(
    config: AnsiRuntimeConfig,
    snapshot: RuntimeSnapshotModel,
): RuntimeSnapshotModel =
    config.hour
        ?.let { hour -> snapshot.copy(state = snapshot.state.copy(realm = snapshot.state.realm.copy(hour = hour))) }
        ?: snapshot

private class RylweenAnsiGame(
    private val terminal: AnsiTerminal,
    private val content: GameModel,
    private val renderer: TerminalTextRenderer,
    private val speakContent: SpeakRuntimeContent,
    private val savePort: RuntimeSavePort,
    private val log: RylweenLog,
) {
    private val screen = ClassicAnsiScreen(terminal, content.visuals, content.terminal, content.layouts, content.towns)
    private val port = AnsiTerminalPort(terminal, screen)
    private val serviceBridge =
        StatefulScriptBridge(
            port,
            engine = TraceEnginePort(eventSink = log),
            speakContent = speakContent,
            battleContent = content.runtimeContent(),
            renderer = renderer,
        )

    fun run(initial: WildernessSession) {
        var session = initial
        var wasTownStep = false
        screen.bootFrame()
        while (true) {
            val step = session.step
            log.emit(RuntimeEventLevel.Debug, "realm", "step", "kind" to step.runtimeStepName())
            val enteringTown = step is WildernessSessionStep.Town && !wasTownStep
            session =
                when (step) {
                    WildernessSessionStep.TurnStart -> session.beginTurn().logged("turnStart", session).also(screen::renderHud)
                    is WildernessSessionStep.Messages -> {
                        screen.writeBlocks(step.blocks, session.runtime, renderer)
                        waitForReturn()
                        session.continueMessages().logged("messages", session)
                    }
                    is WildernessSessionStep.Save -> {
                        screen.writeBlock(content.prompts.save.saving, session.runtime, renderer)
                        val outcome =
                            runCatching { savePort.save(session.runtime) }
                                .getOrElse { error -> RuntimeSaveOutcome(saved = false, error = error.message) }
                        log.emit(
                            if (outcome.saved) RuntimeEventLevel.Audit else RuntimeEventLevel.Warn,
                            "save",
                            if (outcome.saved) "saved" else "failed",
                            "target" to outcome.target,
                            "error" to outcome.error,
                        )
                        screen.writeBlock(
                            if (outcome.saved) content.prompts.save.done else content.prompts.save.failed,
                            session.runtime,
                            renderer,
                        )
                        waitForReturn()
                        session.completeSave(outcome.saved).logged("save", session)
                    }
                    WildernessSessionStep.Explore -> explore(session)
                    is WildernessSessionStep.Notice -> {
                        screen.writeBlock(step.text, session.runtime, renderer)
                        waitForReturn()
                        session.continueNotice().logged("notice", session)
                    }
                    is WildernessSessionStep.CaveFound -> {
                        step.image?.let(screen::showImage)
                        screen.writeBlock(step.prompt, session.runtime, renderer)
                        when (val choice = readChoice(session.wilderness.keys.caveEnter, session.wilderness.keys.caveLeave)) {
                            is BooleanChoice.Answer ->
                                session
                                    .apply(WildernessSessionInput.CaveAnswer(choice.value))
                                    .logged("caveAnswer", session)
                            BooleanChoice.Rejected -> session
                            BooleanChoice.Unavailable -> return
                        }
                    }
                    is WildernessSessionStep.OutOfMoves -> {
                        screen.writeBlock(step.prompt, session.runtime, renderer)
                        when (val choice = readChoice(session.wilderness.keys.outOfMovesSave, session.wilderness.keys.outOfMovesQuit)) {
                            is BooleanChoice.Answer ->
                                session
                                    .apply(WildernessSessionInput.OutOfMovesAnswer(choice.value))
                                    .logged("outOfMoves", session)
                            BooleanChoice.Rejected -> session
                            BooleanChoice.Unavailable -> return
                        }
                    }
                    is WildernessSessionStep.QuitConfirm -> {
                        screen.writeBlock(step.prompt, session.runtime, renderer)
                        when (val choice = readChoice(session.wilderness.keys.quitYes, session.wilderness.keys.quitNo)) {
                            is BooleanChoice.Answer ->
                                session
                                    .apply(WildernessSessionInput.QuitAnswer(choice.value))
                                    .logged("quitAnswer", session)
                            BooleanChoice.Rejected -> session
                            BooleanChoice.Unavailable -> return
                        }
                    }
                    is WildernessSessionStep.DungeonHandoff -> {
                        screen.writeBlock(
                            session.prompts.renderDungeonHandoff(
                                session.dungeons.displayName(step.handoff.dungeon),
                                step.handoff.levels,
                            ),
                            session.runtime,
                            renderer,
                        )
                        waitForReturn()
                        session.startDungeonFromHandoff().logged("dungeonHandoff", session)
                    }
                    is WildernessSessionStep.Dungeon -> {
                        val result =
                            DungeonSessionTerminalAdapter(
                                terminal = port,
                                renderer = renderer,
                                savePort = savePort,
                                postDungeon = step.postDungeon,
                            ).runUntilBlocked(step.session)
                        val updated = session.replaceDungeonSession(result.session)
                        completeChildStep(updated, session, "dungeon", result.status, "dungeon step limit reached") ?: return
                    }
                    is WildernessSessionStep.Ydarg -> {
                        val result = YdargSessionTerminalAdapter(port, renderer, savePort).runUntilBlocked(step.session)
                        val updated = session.replaceYdargSession(result.session)
                        completeChildStep(updated, session, "castle", result.status, "Ydarg step limit reached") ?: return
                    }
                    is WildernessSessionStep.Town -> {
                        if (enteringTown) {
                            screen.writeText(listOf("Entering town...", ""))
                        }
                        val result = TownSessionTerminalAdapter(port, renderer, serviceBridge).runUntilBlocked(step.session)
                        val updated = session.replaceTownSession(result.session)
                        completeChildStep(updated, session, "town", result.status, "town step limit reached") ?: return
                    }
                    is WildernessSessionStep.Hut -> {
                        val continuation = step.continuation ?: ScriptContinuation.start(session.runtime)
                        val result = serviceBridge.run(step.scene.hutService(), step.scene, continuation)
                        port.writeScriptEvents(result.events, renderer, result.runtime)
                        when (result.status) {
                            ScriptBridgeStatus.Finished -> session.finishHut(result.runtime).logged("hut", session)
                            ScriptBridgeStatus.Waiting -> {
                                val nextContinuation =
                                    checkNotNull(result.continuation) {
                                        "waiting hut bridge did not return continuation"
                                    }
                                completeScriptWait(
                                    session.replaceHutContinuation(nextContinuation).logged("hutWait", session),
                                    result.rejection,
                                ) ?: return
                            }
                        }
                    }
                    is WildernessSessionStep.Battle -> {
                        val result = BattleSessionTerminalAdapter(port, renderer).runUntilBlocked(step.session)
                        val updated = session.replaceBattleSession(result.session)
                        completeChildStep(updated, session, "battle", result.status, "battle step limit reached") ?: return
                    }
                    is WildernessSessionStep.Camp -> {
                        val result = CampSessionTerminalAdapter(port, renderer).runUntilBlocked(step.session)
                        val updated = session.replaceCampSession(result.session)
                        completeChildStep(updated, session, "camp", result.status, "camp step limit reached") ?: return
                    }
                    is WildernessSessionStep.Hunt -> {
                        val result = HuntSessionTerminalAdapter(port, renderer).runUntilBlocked(step.session)
                        val updated = session.replaceHuntSession(result.session)
                        completeChildStep(updated, session, "hunt", result.status, "hunt step limit reached") ?: return
                    }
                    is WildernessSessionStep.InventoryItemUse -> {
                        val result = InventoryItemUseTerminalAdapter(port, renderer).runUntilBlocked(step.session)
                        val updated = session.replaceInventoryItemUseSession(result.session)
                        completeChildStep(updated, session, "inventoryUse", result.status, "inventory step limit reached") ?: return
                    }
                    is WildernessSessionStep.InventoryDrop -> {
                        val result = InventoryDropTerminalAdapter(port, renderer).runUntilBlocked(step.session)
                        val updated = session.replaceInventoryDropSession(result.session)
                        completeChildStep(updated, session, "inventoryDrop", result.status, "inventory drop step limit reached") ?: return
                    }
                    is WildernessSessionStep.PartyView -> {
                        val result = PartyViewTerminalAdapter(port, renderer).runUntilBlocked(step.session, session.runtime)
                        val updated = session.replacePartyViewSession(result.session)
                        completeChildStep(updated, session, "partyView", result.status, "party view step limit reached") ?: return
                    }
                    is WildernessSessionStep.PartyJournal -> {
                        val result = PartyJournalTerminalAdapter(port, renderer).runUntilBlocked(step.session, session.runtime)
                        val updated = session.replacePartyJournalSession(result.session)
                        completeChildStep(updated, session, "partyJournal", result.status, "party journal step limit reached") ?: return
                    }
                    is WildernessSessionStep.Equipment -> {
                        val result = EquipmentPromptTerminalAdapter(port, renderer).runUntilBlocked(step.session)
                        val updated = session.replaceEquipmentSession(result.session)
                        completeChildStep(updated, session, "equipment", result.status, "equipment step limit reached") ?: return
                    }
                    is WildernessSessionStep.PriestSpell -> {
                        val result =
                            PriestSpellTerminalAdapter(
                                port,
                                renderer,
                                content = session.content,
                                worldMap = session.worldMap,
                            ).runUntilBlocked(step.session)
                        val updated = session.replacePriestSpellSession(result.session)
                        completeChildStep(updated, session, "priestSpell", result.status, "priest spell step limit reached") ?: return
                    }
                    is WildernessSessionStep.Mission -> {
                        val continuation = step.continuation ?: ScriptContinuation.start(session.runtime)
                        val missionBridge =
                            StatefulScriptBridge(
                                port,
                                engine = TraceEnginePort(eventSink = log),
                                speakContent = speakContent,
                                battleContent = session.content,
                                worldMap = session.worldMap,
                                renderer = renderer,
                            )
                        val result = missionBridge.run(step.mission.service(), step.scene, continuation)
                        when (result.status) {
                            ScriptBridgeStatus.Finished ->
                                result.dungeonHandoff?.let { handoff ->
                                    session
                                        .handOffMissionDungeon(
                                            result.runtime,
                                            handoff,
                                            checkNotNull(result.continuation) {
                                                "mission dungeon handoff did not return post-dungeon continuation"
                                            },
                                        ).logged("missionDungeon", session)
                                }
                                    ?: session.finishMission(result.runtime).logged("mission", session)
                            ScriptBridgeStatus.Waiting -> {
                                val nextContinuation =
                                    checkNotNull(result.continuation) {
                                        "waiting mission bridge did not return continuation"
                                    }
                                val battle = nextContinuation.battleSession
                                if (battle == null) {
                                    completeScriptWait(
                                        session.replaceMissionContinuation(nextContinuation).logged("missionWait", session),
                                        result.rejection,
                                    ) ?: return
                                } else {
                                    val battleResult = BattleSessionTerminalAdapter(port, renderer).runUntilBlocked(battle)
                                    val updated =
                                        session.replaceMissionContinuation(
                                            nextContinuation.copy(
                                                runtime = battleResult.session.runtime,
                                                battleSession = battleResult.session,
                                            ),
                                        )
                                    completeChildStep(
                                        updated,
                                        session,
                                        "missionBattle",
                                        battleResult.status,
                                        "mission battle step limit reached",
                                    ) ?: return
                                }
                            }
                        }
                    }
                    is WildernessSessionStep.Finished -> {
                        screen.writeText(listOf("Fare thee well.", ""))
                        log.audit("realm", "ANSI session finished")
                        return
                    }
                }
            wasTownStep = step is WildernessSessionStep.Town
        }
    }

    private fun explore(session: WildernessSession): WildernessSession {
        screen.renderHud(session)
        return when (val token = terminal.readKeyToken()) {
            null ->
                session.copy(step = WildernessSessionStep.Finished(session.runtime)).also {
                    log.emit(RuntimeEventLevel.Audit, "realm", "inputClosed")
                }
            else -> {
                val input = session.exploreCommands().inputForToken(token)
                if (session.wilderness.keys.redraw
                        .matches(token)
                ) {
                    log.emit(RuntimeEventLevel.Debug, "input", "wilderness", "token" to token, "action" to "redraw")
                    screen.bootFrame()
                    screen.renderHud(session)
                    session.apply(WildernessSessionInput.Redraw).logged("redraw", session)
                } else if (session.wilderness.keys.help
                        .matches(token)
                ) {
                    log.emit(RuntimeEventLevel.Debug, "input", "wilderness", "token" to token, "action" to "help")
                    screen.writeHelp(session, renderer)
                    session
                } else if (input == null) {
                    log.emit(RuntimeEventLevel.Debug, "input", "unknown", "token" to token)
                    screen.writeText(listOf("Unknown command.", "Press ? for commands.", ""))
                    session
                } else {
                    log.emit(
                        RuntimeEventLevel.Info,
                        "input",
                        "wilderness",
                        "token" to token,
                        "action" to input.runtimeInputName(),
                    )
                    session.apply(input).logged("explore", session)
                }
            }
        }
    }

    private fun waitForReturn() {
        while (true) {
            when (terminal.readKeyToken()) {
                null, "" -> {
                    screen.completeTerminalInput()
                    return
                }
            }
        }
    }

    private sealed interface BooleanChoice {
        data class Answer(
            val value: Boolean,
        ) : BooleanChoice

        object Rejected : BooleanChoice

        object Unavailable : BooleanChoice
    }

    private fun completeChildStep(
        updated: WildernessSession,
        previous: WildernessSession,
        action: String,
        status: TerminalRunStatus,
        stepLimitWarning: String,
    ): WildernessSession? =
        when (status) {
            TerminalRunStatus.Finished -> updated.logged(action, previous)
            TerminalRunStatus.StepLimitReached -> updated.logged(action, previous).also { log.warn("ansi", stepLimitWarning) }
            is TerminalRunStatus.Waiting -> completeScriptWait(updated.logged(action, previous), status.rejection)
        }

    private fun completeScriptWait(
        updated: WildernessSession,
        rejection: TerminalInputRejection?,
    ): WildernessSession? = if (rejection == null) null else updated

    private fun readChoice(
        yes: InputBindingModel,
        no: InputBindingModel,
    ): BooleanChoice =
        when (val token = terminal.readKeyToken()) {
            null -> BooleanChoice.Unavailable
            else ->
                when {
                    yes.matches(token) -> BooleanChoice.Answer(true)
                    no.matches(token) -> BooleanChoice.Answer(false)
                    else -> {
                        screen.writeText(listOf("Invalid input.", "Expected: ${yes.label}/${no.label}", ""))
                        BooleanChoice.Rejected
                    }
                }
        }

    private fun WildernessSession.logged(
        action: String,
        previous: WildernessSession,
    ): WildernessSession {
        val result = lastResult ?: return this
        if (result == previous.lastResult) return this
        log.emit(
            RuntimeEventLevel.Info,
            "wilderness",
            "result",
            "action" to action,
            "status" to result.status,
            "from" to result.from,
            "to" to result.to,
            "terrain" to result.terrain,
            "town" to result.town?.slug,
            "dungeon" to result.dungeon?.slug,
            "levels" to result.dungeonLevels,
            "tier" to result.encounterTier?.slug,
            "mission" to result.mission?.slug,
        )
        return this
    }
}

private fun WildernessSessionStep.runtimeStepName(): String =
    when (this) {
        WildernessSessionStep.TurnStart -> "TurnStart"
        WildernessSessionStep.Explore -> "Explore"
        is WildernessSessionStep.Messages -> "Messages"
        is WildernessSessionStep.Save -> "Save"
        is WildernessSessionStep.Notice -> "Notice"
        is WildernessSessionStep.CaveFound -> "CaveFound"
        is WildernessSessionStep.OutOfMoves -> "OutOfMoves"
        is WildernessSessionStep.QuitConfirm -> "QuitConfirm"
        is WildernessSessionStep.DungeonHandoff -> "DungeonHandoff"
        is WildernessSessionStep.Dungeon -> "Dungeon"
        is WildernessSessionStep.Ydarg -> "Ydarg"
        is WildernessSessionStep.Town -> "Town"
        is WildernessSessionStep.Hut -> "Hut"
        is WildernessSessionStep.Battle -> "Battle"
        is WildernessSessionStep.Camp -> "Camp"
        is WildernessSessionStep.Hunt -> "Hunt"
        is WildernessSessionStep.InventoryItemUse -> "InventoryItemUse"
        is WildernessSessionStep.InventoryDrop -> "InventoryDrop"
        is WildernessSessionStep.PartyView -> "PartyView"
        is WildernessSessionStep.PartyJournal -> "PartyJournal"
        is WildernessSessionStep.Equipment -> "Equipment"
        is WildernessSessionStep.PriestSpell -> "PriestSpell"
        is WildernessSessionStep.Mission -> "Mission"
        is WildernessSessionStep.Finished -> "Finished"
    }

private fun WildernessSessionInput.runtimeInputName(): String =
    when (this) {
        is WildernessSessionInput.Move -> "move.${direction.name.lowercase()}"
        WildernessSessionInput.Pass -> "pass"
        WildernessSessionInput.Save -> "save"
        WildernessSessionInput.ReadyWeapon -> "readyWeapon"
        WildernessSessionInput.ReadyArmor -> "readyArmor"
        WildernessSessionInput.Redraw -> "redraw"
        WildernessSessionInput.Journal -> "journal"
        WildernessSessionInput.Help -> "help"
        WildernessSessionInput.Hunt -> "hunt"
        is WildernessSessionInput.HuntMember -> "hunt.member"
        WildernessSessionInput.HuntContinue -> "hunt.continue"
        WildernessSessionInput.ViewParty -> "viewParty"
        WildernessSessionInput.RemoveArmor -> "removeArmor"
        WildernessSessionInput.RemoveWeapon -> "removeWeapon"
        WildernessSessionInput.Enter -> "enter"
        WildernessSessionInput.Drop -> "drop"
        WildernessSessionInput.Camp -> "camp"
        is WildernessSessionInput.CampGuardAnswer -> "camp.guardAnswer"
        is WildernessSessionInput.CampGuardMember -> "camp.guardMember"
        WildernessSessionInput.CampContinue -> "camp.continue"
        WildernessSessionInput.CastSpell -> "castSpell"
        WildernessSessionInput.Quaff -> "quaff"
        WildernessSessionInput.Continue -> "continue"
        is WildernessSessionInput.CaveAnswer -> "cave.answer"
        WildernessSessionInput.Quit -> "quit"
        is WildernessSessionInput.QuitAnswer -> "quit.answer"
        is WildernessSessionInput.OutOfMovesAnswer -> "outOfMoves.answer"
        is WildernessSessionInput.SecretKey -> "secret.${action::class.simpleName.orEmpty()}"
    }

private fun MissionModel.service(): BuildingServiceModel =
    BuildingServiceModel(
        building = Buildings.none,
        action = keyAction("mission.${id.slug}"),
        scene = action,
        opensAt = null,
        closesAt = null,
        nativeServices = emptyList(),
    )

private class AnsiTerminalPort(
    private val terminal: AnsiTerminal,
    private val screen: ClassicAnsiScreen,
) : TerminalPort {
    override fun write(lines: List<String>) {
        screen.writeTerminalLines(lines)
    }

    override fun writeBlocks(
        blocks: List<TextBlock>,
        renderer: TerminalTextRenderer,
        context: TerminalTextContext,
    ) {
        screen.writeTerminalBlocks(blocks, renderer, context, pauseAfterLast = true)
    }

    override fun readLine(): String? =
        terminal.readKeyToken().also { token ->
            if (token != null) {
                screen.completeTerminalInput()
            }
        }

    override fun readTextLine(maxLength: Int?): String? =
        terminal.readTextLine(maxLength).also { line ->
            if (line != null) {
                screen.completeTerminalInput()
            }
        }

    override fun readReturn(): Boolean? =
        terminal.readReturn().also { accepted ->
            if (accepted != null) {
                screen.completeTerminalInput()
            }
        }

    override fun pause(ms: Int) {
        terminal.pause(ms)
    }

    override fun finishOutputFrame() {
        screen.completeTerminalInput()
    }

    override fun writeViewCommand(
        command: ViewCommand,
        renderer: TerminalTextRenderer,
        context: TerminalTextContext,
    ): Boolean =
        when (command) {
            ViewCommand.ClearText -> true.also { screen.clearText() }
            ViewCommand.ClearMapWindow -> true.also { screen.clearMapWindow() }
            ViewCommand.RenderMap -> true.also { screen.redrawMap() }
            is ViewCommand.ShowImage -> true.also { screen.showImage(command.image) }
            ViewCommand.StatusLine -> true.also { screen.renderStatusFor(context.runtime) }
        }
}

private class ClassicAnsiScreen(
    private val terminal: AnsiTerminal,
    private val visuals: VisualsModel,
    presentation: TerminalPresentationModel,
    layout: LayoutModel,
    private val towns: List<TownModel>,
) {
    private val regions = layout.regions.associateBy { it.id }
    private val terrain = TerrainGlyphs(presentation.terrain)
    private val textWindow = regions.getValue(ScreenRegions.textWindow)
    private val mapWindow = regions.getValue(ScreenRegions.mapWindow)
    private val statusTop = regions.getValue(ScreenRegions.statuslineTop)
    private val statusBottom = regions.getValue(ScreenRegions.statuslineBottom)
    private var lastWildernessSession: WildernessSession? = null
    private var terminalModalActive: Boolean = false
    private var terminalPendingLines: List<String> = emptyList()

    fun bootFrame() {
        terminalModalActive = false
        terminalPendingLines = emptyList()
        terminal.writeRaw("$CSI?25l")
        val frame = visuals.staticScreens.singleOrNull { it.id == StaticScreens.frame }?.image
        if (frame != null) {
            renderImage(frame)
        } else {
            terminal.writeRaw("${CSI}2J${CSI}1;1H")
        }
        terminal.writeRaw("$FG_WHITE$BG_BLACK")
        terminal.flush()
    }

    fun renderHud(session: WildernessSession) {
        lastWildernessSession = session
        terminalPendingLines = emptyList()
        if (terminalModalActive) {
            bootFrame()
        }
        renderStatus(session.runtime)
        renderMap(session)
        writeClassicText(wildernessContextLines(session))
    }

    fun clearText() {
        terminalPendingLines = emptyList()
        ensureClassicFrame()
        clearRegion(textWindow)
        terminal.flush()
    }

    fun clearMapWindow() {
        terminalPendingLines = emptyList()
        ensureClassicFrame()
        clearRegion(mapWindow)
        terminal.flush()
    }

    fun redrawMap() {
        terminalPendingLines = emptyList()
        ensureClassicFrame()
        lastWildernessSession?.let(::renderMap)
    }

    fun renderStatus() {
        lastWildernessSession?.let { renderStatus(it.runtime) }
    }

    fun renderStatusFor(runtime: RuntimeSessionState?) {
        terminalPendingLines = emptyList()
        ensureClassicFrame()
        if (runtime != null) {
            renderStatus(runtime)
        } else {
            renderStatus()
        }
    }

    fun showImage(image: ImageId) {
        renderImage(image)
    }

    fun writeHelp(
        session: WildernessSession,
        renderer: TerminalTextRenderer,
    ) {
        val context = TerminalTextContext(runtime = session.runtime)
        session.wilderness.keys.effectiveHelpPages().forEach { page ->
            writeText(renderer.lines(page, context))
            terminal.readKeyToken()
        }
        renderHud(session)
    }

    fun showStaticScreen(
        content: GameModel,
        screenId: StaticScreenId,
        renderer: TerminalTextRenderer,
        context: TerminalTextContext = TerminalTextContext(),
    ) {
        val rendered =
            content.renderTerminalStaticScreen(
                id = screenId,
                renderer = renderer,
                resourceText = RylweenAppAssets::readText,
                context = context,
            ) ?: error("unknown static screen ${screenId.slug}")
        rendered.image?.let(::showImage)
        if (rendered.pages.isEmpty()) {
            terminal.readKeyToken()
            return
        }
        rendered.pages.forEach { page ->
            writeFullScreenText(page)
            terminal.readKeyToken()
        }
    }

    fun writeBlocks(
        blocks: List<TextBlock>,
        runtime: RuntimeSessionState,
        renderer: TerminalTextRenderer,
    ) {
        writeBlocks(blocks, renderer, TerminalTextContext(runtime = runtime), pauseAfterLast = false)
    }

    fun writeTerminalBlocks(
        blocks: List<TextBlock>,
        renderer: TerminalTextRenderer,
        context: TerminalTextContext,
        pauseAfterLast: Boolean,
    ) {
        writeRenderedBlocks(blocks, renderer, context, pauseAfterLast, ::writeTerminalLines)
    }

    fun writeBlocks(
        blocks: List<TextBlock>,
        renderer: TerminalTextRenderer,
        context: TerminalTextContext,
        pauseAfterLast: Boolean,
    ) {
        writeRenderedBlocks(blocks, renderer, context, pauseAfterLast, ::writeText)
    }

    private fun writeRenderedBlocks(
        blocks: List<TextBlock>,
        renderer: TerminalTextRenderer,
        context: TerminalTextContext,
        pauseAfterLast: Boolean,
        writeLines: (List<String>) -> Unit,
    ) {
        if (blocks.isEmpty()) {
            writeLines(emptyList())
            return
        }
        blocks.forEachIndexed { index, block ->
            writeLines(renderer.lines(block, context))
            if (pauseAfterLast || index < blocks.lastIndex) {
                terminal.readKeyToken()
                completeTerminalInput()
            }
        }
    }

    fun writeBlock(
        block: TextBlock,
        runtime: RuntimeSessionState,
        renderer: TerminalTextRenderer,
    ) {
        writeText(renderer.lines(block, TerminalTextContext(runtime = runtime)))
    }

    fun writeTerminalLines(lines: List<String>) {
        terminalPendingLines += lines
        if (terminalModalActive || terminalPendingLines.needsFullScreenTerminal()) {
            terminalModalActive = true
            writeFullScreenText(terminalPendingLines)
        } else {
            writeClassicText(terminalPendingLines)
        }
    }

    fun completeTerminalInput() {
        terminalPendingLines = emptyList()
    }

    fun writeText(lines: List<String>) {
        terminalPendingLines = emptyList()
        ensureClassicFrame()
        writeClassicText(lines)
    }

    private fun writeClassicText(lines: List<String>) {
        clearRegion(textWindow)
        lines.take(textWindow.height ?: lines.size).forEachIndexed { index, line ->
            gotoTextRow(index)
            terminal.writeRaw(line.take(textWindow.width ?: line.length))
        }
        terminal.flush()
    }

    private fun writeFullScreenText(lines: List<String>) {
        terminal.writeRaw("${CSI}2J${CSI}1;1H")
        lines.takeLast(24).forEachIndexed { index, line ->
            terminal.writeRaw("${CSI}${index + 1};1H")
            terminal.writeRaw(line.take(79))
        }
        terminal.flush()
    }

    private fun List<String>.needsFullScreenTerminal(): Boolean = size > (textWindow.height ?: Int.MAX_VALUE)

    private fun ensureClassicFrame() {
        if (terminalModalActive) {
            bootFrame()
        }
    }

    private fun renderImage(image: ImageId) {
        terminalPendingLines = emptyList()
        when (val asset = AnsiImageAssets.resolve(visuals, image)) {
            is AnsiImageAsset.FullScreen -> {
                terminalModalActive = false
                terminalPendingLines = emptyList()
                terminal.writeRaw("${CSI}2J${CSI}1;1H")
                terminal.writeRaw(RylweenAppAssets.readText(asset.resource))
            }
            is AnsiImageAsset.MapWindow -> {
                ensureClassicFrame()
                clearRegion(mapWindow)
                renderAnsiAt(asset.resource, mapWindow.x, mapWindow.y)
                goto(textWindow)
            }
            null -> return
        }
        terminal.writeRaw("$FG_WHITE$BG_BLACK")
        terminal.flush()
    }

    private fun renderStatus(runtime: RuntimeSessionState) {
        val party = runtime.activeParty
        val point = (party.position as? WorldPosition.Wilderness)?.point ?: GridPoint(0, 0)
        val food = party.resources.food.coerceAtLeast(0)
        val status =
            when {
                food == 0 -> "${CSI}31mSTARVING$FG_GREEN "
                food < party.members.count { !it.dead } * 3 -> "${CSI}35mHUNGRY  "
                else -> "${CSI}33mOK    "
            }
        val top =
            buildString {
                append(FG_GREEN)
                append(timeLabel(runtime.realm.hour))
                append("ST: ")
                append(status)
                append("$FG_GREEN X: ${CSI}33m${point.x.toString().padStart(2)}   ")
                append("$FG_GREEN Y: ${CSI}33m${point.y.toString().padStart(2)}$FG_GREEN     ")
                append("FD: ${CSI}33m${food.toString().padStart(4)}$FG_GREEN  ")
                append("MV: ${CSI}33m${party.resources.timeLeft.toString().padStart(3)}$FG_GREEN  ")
                append("AU: ${CSI}33m${party.resources.gold.toString().padStart(6)}$FG_GREEN")
            }
        val bottom =
            (0 until 4).joinToString("   ") { index ->
                val member = party.members.getOrNull(index)
                if (member == null) {
                    "H${index + 1}:        "
                } else {
                    "H${index + 1}: ${CSI}33m${member.hitPoints.current.toString().padStart(3)}/" +
                        "${member.hitPoints.max.toString().padStart(3)}$FG_GREEN"
                }
            }
        goto(statusTop)
        terminal.writeRaw(top)
        goto(statusBottom)
        terminal.writeRaw("$FG_GREEN$bottom$FG_WHITE")
        terminal.flush()
    }

    private fun wildernessContextLines(session: WildernessSession): List<String> {
        val point = (session.party.position as? WorldPosition.Wilderness)?.point
        val terrainLine =
            point?.let { point ->
                terrain.name(session.worldMap.tileAt(point))?.let { "Terrain: $it" }
            }
        val town = point?.let { current -> towns.firstOrNull { it.position == current } }
        return buildList {
            add("Wilderness")
            terrainLine?.let(::add)
            town?.let {
                add("At: ${it.name}")
                add("Press E to enter.")
            }
            addAll(
                session.lastResult
                    ?.status
                    ?.statusLine()
                    .orEmpty(),
            )
            add("Press ? for commands.")
        }
    }

    private fun renderMap(session: WildernessSession) {
        val center = (session.party.position as? WorldPosition.Wilderness)?.point ?: return
        val viewport = session.worldMap.viewport(center)
        viewport.rows.forEachIndexed { rowIndex, cells ->
            gotoMapRow(rowIndex)
            val row =
                buildString {
                    cells.forEachIndexed { cellIndex, cell ->
                        val tile = cell.tile
                        val style = terrain.style(tile)
                        if (cell.isCenter) append(BG_BLUE)
                        append("${CSI}${style.ansiColor}m${style.glyph}$BG_BLACK")
                        if (cellIndex < cells.lastIndex) append(' ')
                    }
                    append(FG_WHITE)
                }
            terminal.writeRaw(row)
        }
        terminal.flush()
    }

    private fun renderAnsiAt(
        resource: String,
        x: Int,
        y: Int,
    ) {
        RylweenAppAssets
            .readText(resource)
            .lineSequence()
            .map { it.trimEnd('\r') }
            .forEachIndexed { row, line ->
                terminal.writeRaw("${CSI}${y + row};${x}H")
                terminal.writeRaw(line)
            }
        terminal.writeRaw(RESET)
    }

    private fun clearRegion(region: ScreenRegionModel) {
        val width = region.width ?: return
        val height = region.height ?: return
        repeat(height) { row ->
            goto(region, rowOffset = row)
            terminal.writeRaw(" ".repeat(width))
        }
    }

    private fun gotoTextRow(row: Int) {
        goto(textWindow, rowOffset = row)
    }

    private fun gotoMapRow(row: Int) {
        // The classic map draw starts one column inside the clearable map region.
        terminal.writeRaw("${CSI}${mapWindow.y + row * 2};1H\r${CSI}${mapWindow.x + 1}C")
    }

    private fun goto(
        region: ScreenRegionModel,
        rowOffset: Int = 0,
    ) {
        terminal.writeRaw("${CSI}${region.y + rowOffset};1H\r${CSI}${region.x}C")
    }

    private fun timeLabel(hour: Int): String =
        when (hour.mod(24)) {
            in 6..11 -> "TIME: ${CSI}33mDAWN$FG_GREEN      "
            in 12..17 -> "TIME: ${CSI}33mMIDDAY$FG_GREEN    "
            in 18..23 -> "TIME: ${CSI}33mTWILIGHT$FG_GREEN  "
            else -> "TIME: ${CSI}33mMIDNIGHT$FG_GREEN  "
        }
}

private fun WildernessSessionResultStatus.statusLine(): List<String> =
    when (this) {
        WildernessSessionResultStatus.Blocked -> listOf("Blocked.")
        WildernessSessionResultStatus.Saved -> emptyList()
        WildernessSessionResultStatus.SaveFailed -> emptyList()
        WildernessSessionResultStatus.HuntUnavailable -> listOf("Hunt is unavailable.")
        WildernessSessionResultStatus.JournalShown -> emptyList()
        WildernessSessionResultStatus.ViewPartyShown -> emptyList()
        WildernessSessionResultStatus.DropUsed -> listOf("Inventory updated.")
        WildernessSessionResultStatus.Camped -> emptyList()
        WildernessSessionResultStatus.NotTired -> emptyList()
        WildernessSessionResultStatus.CampNotEnoughFood -> listOf("Not enough food.")
        WildernessSessionResultStatus.CastSpellUnavailable -> listOf("No spell cast.")
        WildernessSessionResultStatus.CastSpellUsed -> listOf("Spell cast.")
        WildernessSessionResultStatus.EquipmentUsed -> listOf("Equipment updated.")
        WildernessSessionResultStatus.EquipmentUnavailable -> listOf("No equipment change.")
        WildernessSessionResultStatus.InventoryUsed -> listOf("Inventory updated.")
        WildernessSessionResultStatus.InventoryUnavailable -> listOf("Nothing useful.")
        else -> emptyList()
    }

private data class TerrainGlyph(
    val glyph: String,
    val ansiColor: Int,
)

private class TerrainGlyphs(
    styles: List<TerminalTerrainStyleModel>,
) {
    private val byTerrainCode =
        styles
            .mapNotNull { style ->
                style.terrain.sourceMapCode?.let { it to style.terrain.slug.readableSlug() }
            }.toMap()
    private val bySourceCode =
        styles
            .mapNotNull { style ->
                style.terrain.sourceMapCode?.let { it to TerrainGlyph(style.glyph, style.color.ansiForeground()) }
            }.toMap()

    fun style(tile: Char?): TerrainGlyph = tile?.let(bySourceCode::get) ?: TerrainGlyph("  ", 36)

    fun name(tile: Char?): String? = tile?.let(byTerrainCode::get)
}

private fun String.readableSlug(): String =
    split('_', '-')
        .filter { it.isNotEmpty() }
        .joinToString(" ") { part -> part.replaceFirstChar { it.uppercaseChar() } }

private fun PaletteColorId.ansiForeground(): Int =
    when (this) {
        PaletteColors.black -> 30
        PaletteColors.red -> 31
        PaletteColors.green -> 32
        PaletteColors.yellow -> 33
        PaletteColors.blue -> 34
        PaletteColors.magenta -> 35
        PaletteColors.cyan -> 36
        PaletteColors.white -> 37
        else -> 36
    }

private interface AnsiTerminal : Closeable {
    fun writeRaw(text: String)

    fun flush()

    fun readKeyToken(): String?

    fun readTextLine(maxLength: Int?): String?

    fun pause(ms: Int)

    fun readReturn(): Boolean? {
        while (true) {
            when (readKeyToken()) {
                null -> return null
                "" -> return true
            }
        }
    }
}

private fun decodeAnsiKeyToken(
    readCode: (timeoutMs: Int?) -> Int,
    consumeQueuedLf: () -> Unit = {},
): String? {
    val ch = readCode(null)
    if (ch < 0) return null
    if (ch == ESC.single().code) {
        val b1 = readCode(50)
        val b2 = if (b1 == '['.code) readCode(50) else -1
        return when (b2) {
            'A'.code -> "8"
            'B'.code -> "2"
            'C'.code -> "6"
            'D'.code -> "4"
            else -> ESC
        }
    }
    return when (ch) {
        '\u0003'.code, '\u0004'.code -> null
        '\n'.code, '\r'.code -> {
            consumeQueuedLf()
            ""
        }
        else -> ch.toChar().toString()
    }
}

private fun readAnsiTextLine(
    maxLength: Int?,
    readCode: (timeoutMs: Int?) -> Int,
    consumeQueuedLf: () -> Unit,
    writeRaw: (String) -> Unit,
    flush: () -> Unit,
): String? {
    val out = StringBuilder()
    while (true) {
        when (val ch = readCode(null)) {
            -1, '\u0003'.code, '\u0004'.code -> return null
            '\n'.code, '\r'.code -> {
                consumeQueuedLf()
                return out.toString()
            }
            '\b'.code, '\u007F'.code ->
                if (out.isNotEmpty()) {
                    out.deleteCharAt(out.lastIndex)
                    writeRaw("\b \b")
                    flush()
                }
            else -> {
                val cap = maxLength ?: Int.MAX_VALUE
                if (out.length < cap && ch >= ' '.code) {
                    val c = ch.toChar()
                    out.append(c)
                    writeRaw(c.toString())
                    flush()
                }
            }
        }
    }
}

private class JLineAnsiTerminal private constructor(
    private val terminal: Terminal,
    private val savedAttributes: Attributes,
    private val log: RylweenLog,
) : AnsiTerminal {
    private val reader: NonBlockingReader = terminal.reader()
    private val writer = terminal.writer()

    override fun writeRaw(text: String) {
        writer.print(text)
    }

    override fun flush() {
        writer.flush()
    }

    override fun readKeyToken(): String? = decodeAnsiKeyToken(::readCode)

    override fun readTextLine(maxLength: Int?): String? = readAnsiTextLine(maxLength, ::readCode, {}, ::writeRaw, ::flush)

    private fun readCode(timeoutMs: Int?): Int = timeoutMs?.let { reader.read(it.toLong()) } ?: reader.read()

    override fun pause(ms: Int) {
        if (ms > 0) {
            Thread.sleep(ms.toLong())
        }
    }

    override fun close() {
        writeRaw("$RESET$CSI?25h")
        flush()
        terminal.attributes = savedAttributes
        terminal.close()
        log.info("terminal", "closed")
    }

    companion object {
        fun open(log: RylweenLog): JLineAnsiTerminal {
            val terminal =
                TerminalBuilder
                    .builder()
                    .name("rylween-ansi")
                    .system(true)
                    .build()
            val saved = terminal.enterRawMode()
            log.info("terminal", "opened ${terminal.name} ${terminal.type}")
            return JLineAnsiTerminal(terminal, saved, log)
        }
    }
}

private class SocketAnsiTerminal private constructor(
    private val socket: Socket,
    private val log: RylweenLog,
) : AnsiTerminal {
    private val input = BufferedInputStream(socket.getInputStream())
    private val output = BufferedOutputStream(socket.getOutputStream())

    override fun writeRaw(text: String) {
        output.write(text.toByteArray(StandardCharsets.UTF_8))
    }

    override fun flush() {
        output.flush()
    }

    override fun readKeyToken(): String? = decodeAnsiKeyToken(::readInputByte, ::consumeQueuedLf)

    override fun readTextLine(maxLength: Int?): String? =
        readAnsiTextLine(maxLength, ::readInputByte, ::consumeQueuedLf, ::writeRaw, ::flush)

    override fun pause(ms: Int) {
        if (ms > 0) {
            Thread.sleep(ms.toLong())
        }
    }

    override fun close() {
        runCatching {
            writeRaw("$RESET$CSI?25h")
            flush()
        }
        socket.close()
        log.info("server", "client disconnected")
    }

    private fun readInputByte(timeoutMs: Int? = null): Int {
        val previousTimeout = socket.soTimeout
        if (timeoutMs != null) {
            socket.soTimeout = timeoutMs
        }
        return try {
            readInputByteBlocking()
        } catch (_: SocketTimeoutException) {
            -2
        } finally {
            if (timeoutMs != null) {
                socket.soTimeout = previousTimeout
            }
        }
    }

    private fun readInputByteBlocking(): Int {
        while (true) {
            val value = input.read()
            if (value == -1) return -1
            if (value == TELNET_IAC) {
                skipTelnetCommand()
            } else {
                return value
            }
        }
    }

    private fun skipTelnetCommand() {
        when (input.read()) {
            -1 -> return
            TELNET_IAC -> return
            TELNET_SB -> skipTelnetSubnegotiation()
            TELNET_WILL,
            TELNET_WONT,
            TELNET_DO,
            TELNET_DONT,
            -> input.read()
        }
    }

    private fun skipTelnetSubnegotiation() {
        var previous = 0
        while (true) {
            val value = input.read()
            if (value == -1 || previous == TELNET_IAC && value == TELNET_SE) {
                return
            }
            previous = value
        }
    }

    private fun consumeQueuedLf() {
        if (input.available() > 0) {
            input.mark(1)
            if (input.read() != '\n'.code) {
                input.reset()
            }
        }
    }

    companion object {
        private const val TELNET_SE = 240
        private const val TELNET_SB = 250
        private const val TELNET_WILL = 251
        private const val TELNET_WONT = 252
        private const val TELNET_DO = 253
        private const val TELNET_DONT = 254
        private const val TELNET_IAC = 255

        fun accept(
            spec: String,
            log: RylweenLog,
        ): SocketAnsiTerminal {
            val server = ServerSocket()
            server.reuseAddress = true
            server.bind(tcpAddress(spec))
            log.audit("server", "listening ${server.localSocketAddress}")
            val socket = server.accept()
            server.close()
            socket.tcpNoDelay = true
            log.audit("server", "client connected ${socket.remoteSocketAddress}")
            return SocketAnsiTerminal(socket, log)
        }
    }
}

private class RylweenLog private constructor(
    private val sinks: List<LogSink>,
    private val minLevel: RuntimeEventLevel,
) : RuntimeEventSink,
    Closeable {
    fun info(
        module: String,
        message: String,
    ) = emit(RuntimeEventLevel.Info, module, message)

    fun warn(
        module: String,
        message: String,
    ) = emit(RuntimeEventLevel.Warn, module, message)

    fun audit(
        module: String,
        message: String,
    ) = emit(RuntimeEventLevel.Audit, module, message)

    override fun emit(event: RuntimeEvent) {
        if (event.level != RuntimeEventLevel.Audit && event.level.priority < minLevel.priority) {
            return
        }
        writeLine(event.line())
    }

    fun emit(
        level: RuntimeEventLevel,
        module: String,
        action: String,
        vararg fields: Pair<String, Any?>,
    ) {
        emit(runtimeEvent(level, module, action, *fields))
    }

    private fun emit(
        level: RuntimeEventLevel,
        module: String,
        message: String,
    ) {
        emit(RuntimeEvent(level, module, message))
    }

    private fun writeLine(message: String) {
        val line = "$message\n"
        sinks.forEach { it.write(line) }
    }

    override fun close() {
        sinks.forEach(LogSink::close)
    }

    companion object {
        fun open(
            spec: String,
            level: String,
        ): RylweenLog =
            RylweenLog(
                sinks = parseLogSinks(spec),
                minLevel = parseRuntimeEventLevel(level),
            )
    }
}

private interface LogSink : Closeable {
    fun write(line: String)

    override fun close() {}
}

private fun parseLogSinks(spec: String): List<LogSink> {
    val tokens = spec.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    val sinks =
        tokens.mapNotNull { token ->
            when {
                token == "null" -> null
                token == "stderr" -> StderrLogSink
                token.startsWith("file:") -> FileLogSink(File(token.removePrefix("file:")))
                token.startsWith("tcp:") -> TcpLogSink.open(token.removePrefix("tcp:"))
                else -> StderrLogSink
            }
        }
    return sinks.ifEmpty { listOf(NoopLogSink) }
}

private fun parseRuntimeEventLevel(value: String): RuntimeEventLevel =
    when (value.lowercase()) {
        "debug", "dbg" -> RuntimeEventLevel.Debug
        "warn", "warning", "wrn" -> RuntimeEventLevel.Warn
        "error", "err" -> RuntimeEventLevel.Error
        "audit", "aud" -> RuntimeEventLevel.Audit
        else -> RuntimeEventLevel.Info
    }

private object NoopLogSink : LogSink {
    override fun write(line: String) = Unit
}

private object StderrLogSink : LogSink {
    override fun write(line: String) {
        System.err.print(line)
    }
}

private class FileLogSink(
    file: File,
) : LogSink {
    private val writer = file.apply { parentFile?.mkdirs() }.bufferedWriter()

    override fun write(line: String) {
        writer.write(line)
        writer.flush()
    }

    override fun close() {
        writer.close()
    }
}

private class TcpLogSink private constructor(
    private val server: ServerSocketChannel,
) : LogSink {
    private val clients = mutableListOf<SocketChannel>()

    override fun write(line: String) {
        acceptPending()
        val bytes = ByteBuffer.wrap(line.toByteArray(StandardCharsets.UTF_8))
        val dead = mutableListOf<SocketChannel>()
        clients.forEach { client ->
            try {
                bytes.rewind()
                client.write(bytes)
            } catch (_: Exception) {
                dead += client
            }
        }
        dead.forEach { client ->
            clients -= client
            client.close()
        }
    }

    override fun close() {
        clients.forEach(SocketChannel::close)
        server.close()
    }

    private fun acceptPending() {
        while (true) {
            val client = server.accept() ?: return
            client.configureBlocking(false)
            clients += client
        }
    }

    companion object {
        fun open(spec: String): TcpLogSink {
            val address = tcpAddress(spec)
            val server = ServerSocketChannel.open()
            server.configureBlocking(false)
            server.bind(address)
            return TcpLogSink(server)
        }
    }
}

private fun tcpAddress(spec: String): InetSocketAddress {
    val parts = spec.split(':')
    val host =
        when (parts.size) {
            1 -> "127.0.0.1"
            else -> parts.dropLast(1).joinToString(":").ifEmpty { "127.0.0.1" }
        }
    val port = parts.last().toInt()
    return InetSocketAddress(host, port)
}

private fun AnsiRuntimeConfig.withPlayerSlot(slot: String): AnsiRuntimeConfig =
    copy(
        playerSlot = slot,
        saveFile = runtimeSaveSlotFile(slot),
        saveState = runtimeSaveSlotStateId(slot),
    )

private fun resolveAnsiPlayerSlot(
    config: AnsiRuntimeConfig,
    game: RylweenModel,
): AnsiRuntimeConfig {
    val player = config.playerSlot ?: return config
    if (config.newGame || config.loadFile != null || config.state != null || config.staticScreen != null) {
        return config
    }
    if (config.saveFile.exists()) {
        return config.copy(loadFile = config.saveFile)
    }
    val fixture =
        game.states.firstOrNull { state ->
            state.parties.any { party -> party.player.equals(player, ignoreCase = true) }
        }
    return fixture?.let { config.copy(state = it.id) } ?: config
}

private const val ANSI_VERBOSE_HELP =
    """
Examples:
  rylween                          # Title menu, loads build/saves/main.kts if present
  rylween Player                   # Player-slot shortcut: loads build/saves/Player.kts or
                                   # boots a fixture whose active party player matches.
  rylween --new                    # Runs authored character creation, then plays new party.
  rylween --state strong-replay    # Boots the strong-party test fixture (skips title menu).
  rylween --state fresh-replay --seed 1
                                   # Deterministic boot from authored fixture.
  rylween --bundle build/compiled/rylween-bundle.json
                                   # Boots from compiled J1 bundle (hash-verified at startup).
  rylween --screen title           # Renders the authored title screen and exits.

Telnet/socket server mode:
  rylween --serve 9999             # Listens on 127.0.0.1:9999 for one client; play over telnet/nc.
  rylween --serve 0.0.0.0:9999     # Binds all interfaces (use with care).
  Then from another shell:
    nc 127.0.0.1 9999              # Or `telnet 127.0.0.1 9999`. ANSI renders to the client;
                                   # server diagnostics stay on the launcher's log sinks.

Logging:
  --log stderr                     # Or set RYL_LOG=stderr. Default in --serve mode.
  --log file:build/rylween.log     # Append to a file.
  --log stderr,file:build/rylween.log
                                   # Tee to stderr and a file (comma-separated sinks).
  --log tcp:9999                   # Broadcast log lines to TCP clients on 127.0.0.1:9999.
  --log null                       # Disable logging entirely.
  --log-level debug                # Or RYL_LOG_LEVEL=debug. Levels: debug, info, warn, error, audit.
                                   # debug includes formula evaluation, script ops, input dispatch.
                                   # audit always emits regardless of threshold (saves, flags, etc.).

Compiled-bundle dump:
  --dump-bundle                    # Or RYL_DUMP_BUNDLE=1. Emits the loaded compiled JSON bundle
                                   # as one Debug-level log event at startup. Combine with
                                   # --log-level debug --log stderr to see it.

Save slots:
  --save-slot dev                  # Writes build/saves/dev.kts; snapshot state id = runtime.dev.
  --load-slot dev                  # Reads that same file. Bare positional `<slot>` shortcut works too.

Verbose-help section:
  Startup:
    With no --state/--load/--new/--screen argument the launcher shows the authored title menu,
    then loads build/saves/main.kts if it exists or creates the initial world state.
    --state <slug> selects an authored fixture (fresh-replay, strong-replay, etc).
    --new runs character creation and saves the new world before play.

  Bundle boot:
    --bundle <file> reads a J1 compiled JSON bundle, verifies its hash and runtime facade,
    then boots the same Kotlin runtime/session stack from that artifact.

  Logging detail:
    RYL_LOG is a comma-separated sink list. stderr writes to standard error,
    file:<path> appends, tcp:[host:]port broadcasts to TCP clients, null disables output.
    RYL_LOG_LEVEL defaults to info. Audit events bypass lower thresholds.
"""

private class RylweenAnsiCommand : CliktCommand(name = "rylween") {
    override fun help(context: Context): String = "Rylween Kotlin ANSI runtime."

    init {
        eagerOption(
            "--help-verbose",
            "--verbose-help",
            help = "Show extended help with command-line examples and runtime details.",
        ) {
            echo(ANSI_VERBOSE_HELP.trim())
            throw ProgramResult(0)
        }
    }

    private val state by option("--state", help = "State slug; skips the title menu and boots a fixture/runtime state.")
        .convert { stateId(it) }
    private val seed by option("--seed", help = "Runtime RNG state; defaults to the selected state seed.").long()
    private val hour by option("--hour", help = "0..23, overrides the selected state hour.")
        .int()
        .convert { it.mod(24) }
    private val bundleFileOpt by option(
        "--bundle",
        envvar = "RYL_BUNDLE",
        help = "Compiled JSON bundle to verify before booting.",
    ).file(mustExist = true, canBeDir = false, mustBeReadable = true)
    private val loadFileOpt by option(
        "--load",
        help = "Runtime snapshot file to boot instead of --state.",
    ).file(mustExist = true, canBeDir = false)
    private val loadSlot by option("--load-slot", help = "Named runtime snapshot under build/saves.")
    private val saveFileOpt by option(
        "--save",
        help = "Runtime snapshot target; defaults to build/saves/main.kts.",
    ).file()
    private val saveSlot by option("--save-slot", help = "Named runtime snapshot target under build/saves.")
    private val staticScreen by option(
        "--screen",
        help = "title|help|manual|score|vscore|heroes; renders the static screen and exits.",
    ).convert { staticScreenArg(it) }
    private val newGame by option(
        "--new",
        "--new-game",
        help = "Create a fresh party through the authored character-creation flow.",
    ).flag()
    private val serve by option(
        "--serve",
        "--server",
        envvar = "RYL_SERVE",
        help = "[host:]port; wait for one socket/telnet client and render ANSI there.",
    )
    private val logSpec by option(
        "--log",
        envvar = "RYL_LOG",
        help = "Comma-separated log sinks: stderr, file:<path>, tcp:[host:]port, null. Default in --serve mode is stderr.",
    ).default("")
    private val logLevel by option(
        "--log-level",
        envvar = "RYL_LOG_LEVEL",
        help = "debug|info|warn|error|audit (default: info).",
    ).default("")
    private val dumpBundle by option(
        "--dump-bundle",
        envvar = "RYL_DUMP_BUNDLE",
        help = "Pretty-print the loaded compiled JSON bundle as one Debug log event at startup.",
    ).flag()
    private val playerSlot by argument(
        "PLAYER",
        help = "Optional save-slot/player shortcut; loads build/saves/<name>.kts or matches a fixture party.",
    ).optional()

    override fun run() {
        val effectiveLoadFile = loadFileOpt ?: loadSlot?.let(::runtimeSaveSlotFile)
        val resolvedSaveFile: File
        val resolvedSaveState: StateId
        when {
            saveFileOpt != null -> {
                resolvedSaveFile = saveFileOpt!!
                resolvedSaveState = runtimeSaveSlotStateId("main")
            }
            saveSlot != null -> {
                resolvedSaveFile = runtimeSaveSlotFile(saveSlot!!)
                resolvedSaveState = runtimeSaveSlotStateId(saveSlot!!)
            }
            else -> {
                resolvedSaveFile = runtimeSaveSlotFile("main")
                resolvedSaveState = runtimeSaveSlotStateId("main")
            }
        }
        var config =
            AnsiRuntimeConfig(
                state = state,
                seed = seed,
                hour = hour,
                bundleFile = bundleFileOpt,
                loadFile = effectiveLoadFile,
                saveFile = resolvedSaveFile,
                saveState = resolvedSaveState,
                staticScreen = staticScreen,
                newGame = newGame,
                serve = serve,
                logSpec = logSpec,
                logLevel = logLevel,
                dumpBundle = dumpBundle,
            )
        playerSlot?.let { config = config.withPlayerSlot(it) }
        runConfiguredAnsi(config)
    }
}
