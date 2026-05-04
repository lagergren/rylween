@file:Suppress("ReplacePrintlnWithLogging", "UseOfSystemOutOrSystemErr")

package rylween.app

import org.jline.terminal.Attributes
import org.jline.terminal.Terminal
import org.jline.terminal.TerminalBuilder
import org.jline.utils.NonBlockingReader
import rylween.dsl.*
import rylween.engine.*
import rylween.frontend.terminal.*
import rylween.game.*
import rylween.runtime.*
import rylween.runtime.town.SpeakRuntimeContent
import rylween.runtime.wilderness.*
import rylween.save.*
import java.io.Closeable
import java.io.File
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets
import kotlin.system.exitProcess

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
    val loadFile: File? = null,
    val saveFile: File = runtimeSaveSlotFile("main"),
    val saveState: StateId = runtimeSaveSlotStateId("main"),
    val staticScreen: StaticScreenId? = null,
    val newGame: Boolean = false,
)

object RylweenAnsiMain {
    @JvmStatic
    fun main(args: Array<String>): Unit = runRylweenCli {
        val config = parseAnsiConfig(args.toList())
        val game = RylweenCompiledGame.nextRylween
        val content = RylweenCompiledGame.nextDsl
        val renderer = content.terminalTextRenderer()
        val worldMap = content.worldMapGrid(Maps.wilderness)
            ?: error("missing authored wilderness map")
        val engine = RylweenKotlinEngine(
            content = content,
            npcPlacementConfig = NpcPlacementRealizerConfig(
                wilderness = WildernessNpcPlacementPolicy(allowedPoints = worldMap.passableNpcPoints()),
            ),
        )
        config.staticScreen?.takeUnless { screenId -> content.staticScreenRequiresRuntimeState(screenId) }?.let { screenId ->
            RylweenLog.open().use { log ->
                JLineAnsiTerminal.open(log).use { terminal ->
                    ClassicAnsiScreen(terminal, content.visuals, content.terminal, content.layouts, content.towns)
                        .showStaticScreen(content, screenId, renderer)
                }
            }
            return@runRylweenCli
        }
        config.staticScreen?.let { screenId ->
            val bootSnapshot = applyConfiguredHour(
                config,
                loadConfiguredOrSavedSnapshot(game, content, config, RuntimeStartupTarget.StaticScreen),
            )
            RylweenLog.open().use { log ->
                JLineAnsiTerminal.open(log).use { terminal ->
                    ClassicAnsiScreen(terminal, content.visuals, content.terminal, content.layouts, content.towns)
                        .showStaticScreen(
                            content = content,
                            screenId = screenId,
                            renderer = renderer,
                            context = TerminalTextContext(runtime = bootSnapshot.state.toRuntimeSessionState()),
                        )
                }
            }
            return@runRylweenCli
        }

        RylweenLog.open().use { log ->
            JLineAnsiTerminal.open(log).use { terminal ->
                if (config.newGame || config.loadFile != null || config.state != null) {
                    val snapshot = if (config.newGame && config.loadFile == null && config.state == null) {
                        createInitialWorldSnapshot(game, content, States.freshReplay, config.seed)
                    } else {
                        loadConfiguredSnapshot(game, content, config)
                    }
                    val session = bootAnsiWildernessSession(
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
    terminal: JLineAnsiTerminal,
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
            TitleMenuAction.Exit -> return
            TitleMenuAction.NewParty -> {
                val clean = applyConfiguredHour(
                    config,
                    createInitialWorldSnapshot(game, content, States.freshReplay, config.seed),
                )
                val session = bootAnsiWildernessSession(
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
                val session = bootAnsiWildernessSession(
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
                    val deleted = runCatching { !config.saveFile.exists() || config.saveFile.delete() }
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
    terminal: JLineAnsiTerminal,
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

private fun readYesNoKey(terminal: JLineAnsiTerminal, screen: ClassicAnsiScreen): Boolean? {
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
    terminal: JLineAnsiTerminal,
    log: RylweenLog,
    snapshot: RuntimeSnapshotModel,
    worldMap: WorldMapGrid,
    newGame: Boolean,
): WildernessSession {
    log.info(
        "ansi",
        "boot state=${snapshot.state.id.slug} seed=${config.seed ?: snapshot.state.realm.rngState} new=$newGame",
    )
    val creationPort = if (newGame) {
        val creationScreen = ClassicAnsiScreen(
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
    terminal: JLineAnsiTerminal,
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

private fun saveInitialRuntime(savePort: RuntimeSavePort, runtime: RuntimeSessionState, log: RylweenLog) {
    val outcome = runCatching { savePort.save(runtime) }
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
    target: RuntimeStartupTarget = RuntimeStartupTarget.Wilderness,
): RuntimeSnapshotModel =
    if (config.loadFile != null || config.state != null) {
        loadConfiguredSnapshot(game, content, config, target)
    } else {
        loadSavedOrInitialSnapshot(game, content, config, target)
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

private fun applyConfiguredHour(config: AnsiRuntimeConfig, snapshot: RuntimeSnapshotModel): RuntimeSnapshotModel =
    config.hour
        ?.let { hour -> snapshot.copy(state = snapshot.state.copy(realm = snapshot.state.realm.copy(hour = hour))) }
        ?: snapshot

private class RylweenAnsiGame(
    private val terminal: JLineAnsiTerminal,
    private val content: GameModel,
    private val renderer: TerminalTextRenderer,
    private val speakContent: SpeakRuntimeContent,
    private val savePort: RuntimeSavePort,
    private val log: RylweenLog,
) {
    private val screen = ClassicAnsiScreen(terminal, content.visuals, content.terminal, content.layouts, content.towns)
    private val port = AnsiTerminalPort(terminal, screen)
    private val serviceBridge = StatefulScriptBridge(
        port,
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
            val enteringTown = step is WildernessSessionStep.Town && !wasTownStep
            session = when (step) {
                WildernessSessionStep.TurnStart -> session.beginTurn().also(screen::renderHud)
                is WildernessSessionStep.Messages -> {
                    screen.writeBlocks(step.blocks, session.runtime, renderer)
                    waitForReturn()
                    session.continueMessages()
                }
                is WildernessSessionStep.Save -> {
                    screen.writeBlock(content.prompts.save.saving, session.runtime, renderer)
                    val outcome = runCatching { savePort.save(session.runtime) }
                        .getOrElse { error -> RuntimeSaveOutcome(saved = false, error = error.message) }
                    screen.writeBlock(
                        if (outcome.saved) content.prompts.save.done else content.prompts.save.failed,
                        session.runtime,
                        renderer,
                    )
                    waitForReturn()
                    session.completeSave(outcome.saved)
                }
                WildernessSessionStep.Explore -> explore(session)
                is WildernessSessionStep.Notice -> {
                    screen.writeBlock(step.text, session.runtime, renderer)
                    waitForReturn()
                    session.continueNotice()
                }
                is WildernessSessionStep.CaveFound -> {
                    step.image?.let(screen::showImage)
                    screen.writeBlock(step.prompt, session.runtime, renderer)
                    when (readChoice(session.wilderness.keys.caveEnter, session.wilderness.keys.caveLeave)) {
                        true -> session.apply(WildernessSessionInput.CaveAnswer(true))
                        false -> session.apply(WildernessSessionInput.CaveAnswer(false))
                        null -> session
                    }
                }
                is WildernessSessionStep.OutOfMoves -> {
                    screen.writeBlock(step.prompt, session.runtime, renderer)
                    when (readChoice(session.wilderness.keys.outOfMovesSave, session.wilderness.keys.outOfMovesQuit)) {
                        true -> session.apply(WildernessSessionInput.OutOfMovesAnswer(true))
                        false -> session.apply(WildernessSessionInput.OutOfMovesAnswer(false))
                        null -> session
                    }
                }
                is WildernessSessionStep.QuitConfirm -> {
                    screen.writeBlock(step.prompt, session.runtime, renderer)
                    when (readChoice(session.wilderness.keys.quitYes, session.wilderness.keys.quitNo)) {
                        true -> session.apply(WildernessSessionInput.QuitAnswer(true))
                        false -> session.apply(WildernessSessionInput.QuitAnswer(false))
                        null -> session
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
                    session.startDungeonFromHandoff()
                }
                is WildernessSessionStep.Dungeon -> {
                    val result = DungeonSessionTerminalAdapter(
                        terminal = port,
                        renderer = renderer,
                        savePort = savePort,
                        postDungeon = step.postDungeon,
                    ).runUntilBlocked(step.session)
                    val updated = session.replaceDungeonSession(result.session)
                    when (result.status) {
                        TerminalRunStatus.Finished -> updated
                        TerminalRunStatus.StepLimitReached -> updated.also { log.warn("ansi", "dungeon step limit reached") }
                        is TerminalRunStatus.Waiting -> updated
                    }
                }
                is WildernessSessionStep.Ydarg -> {
                    val result = YdargSessionTerminalAdapter(port, renderer, savePort).runUntilBlocked(step.session)
                    val updated = session.replaceYdargSession(result.session)
                    when (result.status) {
                        TerminalRunStatus.Finished -> updated
                        TerminalRunStatus.StepLimitReached -> updated.also { log.warn("ansi", "Ydarg step limit reached") }
                        is TerminalRunStatus.Waiting -> updated
                    }
                }
                is WildernessSessionStep.Town -> {
                    if (enteringTown) {
                        screen.writeText(listOf("Entering town...", ""))
                    }
                    val result = TownSessionTerminalAdapter(port, renderer, serviceBridge).runUntilBlocked(step.session)
                    val updated = session.replaceTownSession(result.session)
                    when (val status = result.status) {
                        TerminalRunStatus.Finished -> updated
                        TerminalRunStatus.StepLimitReached -> updated.also { log.warn("ansi", "town step limit reached") }
                        is TerminalRunStatus.Waiting -> {
                            if (status.rejection == null) return
                            updated
                        }
                    }
                }
                is WildernessSessionStep.Hut -> {
                    val continuation = step.continuation ?: ScriptContinuation.start(session.runtime)
                    val result = serviceBridge.run(step.scene.hutService(), step.scene, continuation)
                    port.writeScriptEvents(result.events, renderer, result.runtime)
                    when (result.status) {
                        ScriptBridgeStatus.Finished -> session.finishHut(result.runtime)
                        ScriptBridgeStatus.Waiting -> {
                            val nextContinuation = checkNotNull(result.continuation) {
                                "waiting hut bridge did not return continuation"
                            }
                            session.replaceHutContinuation(nextContinuation)
                        }
                    }
                }
                is WildernessSessionStep.Battle -> {
                    val result = BattleSessionTerminalAdapter(port, renderer).runUntilBlocked(step.session)
                    val updated = session.replaceBattleSession(result.session)
                    when (result.status) {
                        TerminalRunStatus.Finished -> updated
                        TerminalRunStatus.StepLimitReached -> updated.also { log.warn("ansi", "battle step limit reached") }
                        is TerminalRunStatus.Waiting -> updated
                    }
                }
                is WildernessSessionStep.Camp -> {
                    val result = CampSessionTerminalAdapter(port, renderer).runUntilBlocked(step.session)
                    val updated = session.replaceCampSession(result.session)
                    when (result.status) {
                        TerminalRunStatus.Finished -> updated
                        TerminalRunStatus.StepLimitReached -> updated.also { log.warn("ansi", "camp step limit reached") }
                        is TerminalRunStatus.Waiting -> updated
                    }
                }
                is WildernessSessionStep.Hunt -> {
                    val result = HuntSessionTerminalAdapter(port, renderer).runUntilBlocked(step.session)
                    val updated = session.replaceHuntSession(result.session)
                    when (val status = result.status) {
                        TerminalRunStatus.Finished -> updated
                        TerminalRunStatus.StepLimitReached -> updated.also { log.warn("ansi", "hunt step limit reached") }
                        is TerminalRunStatus.Waiting -> {
                            if (status.rejection == null) return
                            updated
                        }
                    }
                }
                is WildernessSessionStep.InventoryItemUse -> {
                    val result = InventoryItemUseTerminalAdapter(port, renderer).runUntilBlocked(step.session)
                    val updated = session.replaceInventoryItemUseSession(result.session)
                    when (result.status) {
                        TerminalRunStatus.Finished -> updated
                        TerminalRunStatus.StepLimitReached -> updated.also { log.warn("ansi", "inventory step limit reached") }
                        is TerminalRunStatus.Waiting -> updated
                    }
                }
                is WildernessSessionStep.InventoryDrop -> {
                    val result = InventoryDropTerminalAdapter(port, renderer).runUntilBlocked(step.session)
                    val updated = session.replaceInventoryDropSession(result.session)
                    when (result.status) {
                        TerminalRunStatus.Finished -> updated
                        TerminalRunStatus.StepLimitReached -> updated.also { log.warn("ansi", "inventory drop step limit reached") }
                        is TerminalRunStatus.Waiting -> updated
                    }
                }
                is WildernessSessionStep.PartyView -> {
                    val result = PartyViewTerminalAdapter(port, renderer).runUntilBlocked(step.session, session.runtime)
                    val updated = session.replacePartyViewSession(result.session)
                    when (result.status) {
                        TerminalRunStatus.Finished -> updated
                        TerminalRunStatus.StepLimitReached -> updated.also { log.warn("ansi", "party view step limit reached") }
                        is TerminalRunStatus.Waiting -> updated
                    }
                }
                is WildernessSessionStep.PartyJournal -> {
                    val result = PartyJournalTerminalAdapter(port, renderer).runUntilBlocked(step.session, session.runtime)
                    val updated = session.replacePartyJournalSession(result.session)
                    when (val status = result.status) {
                        TerminalRunStatus.Finished -> updated
                        TerminalRunStatus.StepLimitReached -> updated.also { log.warn("ansi", "party journal step limit reached") }
                        is TerminalRunStatus.Waiting -> {
                            if (status.rejection == null) return
                            updated
                        }
                    }
                }
                is WildernessSessionStep.Equipment -> {
                    val result = EquipmentPromptTerminalAdapter(port, renderer).runUntilBlocked(step.session)
                    val updated = session.replaceEquipmentSession(result.session)
                    when (result.status) {
                        TerminalRunStatus.Finished -> updated
                        TerminalRunStatus.StepLimitReached -> updated.also { log.warn("ansi", "equipment step limit reached") }
                        is TerminalRunStatus.Waiting -> updated
                    }
                }
                is WildernessSessionStep.PriestSpell -> {
                    val result = PriestSpellTerminalAdapter(
                        port,
                        renderer,
                        content = session.content,
                        worldMap = session.worldMap,
                    )
                        .runUntilBlocked(step.session)
                    val updated = session.replacePriestSpellSession(result.session)
                    when (result.status) {
                        TerminalRunStatus.Finished -> updated
                        TerminalRunStatus.StepLimitReached -> updated.also { log.warn("ansi", "priest spell step limit reached") }
                        is TerminalRunStatus.Waiting -> updated
                    }
                }
                is WildernessSessionStep.Mission -> {
                    val continuation = step.continuation ?: ScriptContinuation.start(session.runtime)
                    val missionBridge = StatefulScriptBridge(
                        port,
                        speakContent = speakContent,
                        battleContent = session.content,
                        worldMap = session.worldMap,
                        renderer = renderer,
                    )
                    val result = missionBridge.run(step.mission.service(), step.scene, continuation)
                    when (result.status) {
                        ScriptBridgeStatus.Finished ->
                            result.dungeonHandoff?.let { handoff ->
                                session.handOffMissionDungeon(
                                    result.runtime,
                                    handoff,
                                    checkNotNull(result.continuation) {
                                        "mission dungeon handoff did not return post-dungeon continuation"
                                    },
                                )
                            }
                                ?: session.finishMission(result.runtime)
                        ScriptBridgeStatus.Waiting -> {
                            val nextContinuation = checkNotNull(result.continuation) {
                                "waiting mission bridge did not return continuation"
                            }
                            val battle = nextContinuation.battleSession
                            if (battle == null) {
                                session.replaceMissionContinuation(nextContinuation)
                            } else {
                                val battleResult = BattleSessionTerminalAdapter(port, renderer).runUntilBlocked(battle)
                                session.replaceMissionContinuation(
                                    nextContinuation.copy(
                                        runtime = battleResult.session.runtime,
                                        battleSession = battleResult.session,
                                    ),
                                )
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
            null -> session.copy(step = WildernessSessionStep.Finished(session.runtime))
            else -> {
                val input = session.exploreCommands().inputForToken(token)
                if (session.wilderness.keys.redraw.matches(token)) {
                    screen.bootFrame()
                    screen.renderHud(session)
                    session.apply(WildernessSessionInput.Redraw)
                } else if (session.wilderness.keys.help.matches(token)) {
                    screen.writeHelp(session, renderer)
                    session
                } else if (input == null) {
                    screen.writeText(listOf("Unknown command.", "Press ? for commands.", ""))
                    session
                } else {
                    session.apply(input)
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

    private fun readChoice(yes: InputBindingModel, no: InputBindingModel): Boolean? =
        when (val token = terminal.readKeyToken()) {
            null -> null
            else -> when {
                yes.matches(token) -> true
                no.matches(token) -> false
                else -> {
                    screen.writeText(listOf("Invalid input.", "Expected: ${yes.label}/${no.label}", ""))
                    null
                }
            }
        }
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
    private val terminal: JLineAnsiTerminal,
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

    override fun writeViewCommand(
        command: ViewCommand,
        renderer: TerminalTextRenderer,
        context: TerminalTextContext,
    ): Boolean {
        return when (command) {
            ViewCommand.ClearText -> true.also { screen.clearText() }
            ViewCommand.ClearMapWindow -> true.also { screen.clearMapWindow() }
            ViewCommand.RenderMap -> true.also { screen.redrawMap() }
            is ViewCommand.ShowImage -> true.also { screen.showImage(command.image) }
            ViewCommand.StatusLine -> true.also { screen.renderStatusFor(context.runtime) }
        }
    }
}

private class ClassicAnsiScreen(
    private val terminal: JLineAnsiTerminal,
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

    fun writeHelp(session: WildernessSession, renderer: TerminalTextRenderer) {
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
        val rendered = content.renderTerminalStaticScreen(
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

    fun writeBlocks(blocks: List<TextBlock>, runtime: RuntimeSessionState, renderer: TerminalTextRenderer) {
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

    fun writeBlock(block: TextBlock, runtime: RuntimeSessionState, renderer: TerminalTextRenderer) {
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

    private fun List<String>.needsFullScreenTerminal(): Boolean =
        size > (textWindow.height ?: Int.MAX_VALUE)

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
        val status = when {
            food == 0 -> "${CSI}31mSTARVING$FG_GREEN "
            food < party.members.count { !it.dead } * 3 -> "${CSI}35mHUNGRY  "
            else -> "${CSI}33mOK    "
        }
        val top = buildString {
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
        val bottom = (0 until 4).joinToString("   ") { index ->
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
        val terrainLine = point?.let { point ->
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
            addAll(session.lastResult?.status?.statusLine().orEmpty())
            add("Press ? for commands.")
        }
    }

    private fun renderMap(session: WildernessSession) {
        val center = (session.party.position as? WorldPosition.Wilderness)?.point ?: return
        val viewport = session.worldMap.viewport(center)
        viewport.rows.forEachIndexed { rowIndex, cells ->
            gotoMapRow(rowIndex)
            val row = buildString {
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

    private fun renderAnsiAt(resource: String, x: Int, y: Int) {
        RylweenAppAssets.readText(resource)
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

    private fun goto(region: ScreenRegionModel, rowOffset: Int = 0) {
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

private data class TerrainGlyph(val glyph: String, val ansiColor: Int)

private class TerrainGlyphs(styles: List<TerminalTerrainStyleModel>) {
    private val byTerrainCode = styles.mapNotNull { style ->
        style.terrain.sourceMapCode?.let { it to style.terrain.slug.readableSlug() }
    }.toMap()
    private val bySourceCode = styles.mapNotNull { style ->
        style.terrain.sourceMapCode?.let { it to TerrainGlyph(style.glyph, style.color.ansiForeground()) }
    }.toMap()

    fun style(tile: Char?): TerrainGlyph =
        tile?.let(bySourceCode::get) ?: TerrainGlyph("  ", 36)

    fun name(tile: Char?): String? =
        tile?.let(byTerrainCode::get)
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

private class JLineAnsiTerminal private constructor(
    private val terminal: Terminal,
    private val savedAttributes: Attributes,
    private val log: RylweenLog,
) : Closeable {
    private val reader: NonBlockingReader = terminal.reader()
    private val writer = terminal.writer()

    fun writeRaw(text: String) {
        writer.print(text)
    }

    fun flush() {
        writer.flush()
    }

    fun readKeyToken(): String? {
        val ch = reader.read()
        if (ch == -1) return null
        if (ch == 27) {
            val b1 = reader.read(50)
            val b2 = if (b1 == '['.code) reader.read(50) else -1
            return when (b2) {
                'A'.code -> "8"
                'B'.code -> "2"
                'C'.code -> "6"
                'D'.code -> "4"
                else -> ESC
            }
        }
        return when (ch) {
            3 -> null
            4 -> null
            10, 13 -> ""
            else -> ch.toChar().toString()
        }
    }

    fun readTextLine(maxLength: Int?): String? {
        val out = StringBuilder()
        while (true) {
            when (val ch = reader.read()) {
                -1, 3, 4 -> return null
                10, 13 -> return out.toString()
                8, 127 -> if (out.isNotEmpty()) {
                    out.deleteCharAt(out.lastIndex)
                    writeRaw("\b \b")
                    flush()
                }
                else -> {
                    val cap = maxLength ?: Int.MAX_VALUE
                    if (out.length < cap && ch >= 32) {
                        val c = ch.toChar()
                        out.append(c)
                        writeRaw(c.toString())
                        flush()
                    }
                }
            }
        }
    }

    fun readReturn(): Boolean? {
        while (true) {
            when (readKeyToken()) {
                null -> return null
                "" -> return true
            }
        }
    }

    fun pause(ms: Int) {
        if (ms > 0) {
            Thread.sleep(ms.toLong())
        }
    }

    override fun close() {
        writeRaw("$RESET${CSI}?25h")
        flush()
        terminal.attributes = savedAttributes
        terminal.close()
        log.info("terminal", "closed")
    }

    companion object {
        fun open(log: RylweenLog): JLineAnsiTerminal {
            val terminal = TerminalBuilder.builder()
                .name("rylween-ansi")
                .system(true)
                .build()
            val saved = terminal.enterRawMode()
            log.info("terminal", "opened ${terminal.name} ${terminal.type}")
            return JLineAnsiTerminal(terminal, saved, log)
        }
    }
}

private class RylweenLog private constructor(private val sinks: List<LogSink>) : Closeable {
    fun info(module: String, message: String) = emit("INF", module, message)
    fun warn(module: String, message: String) = emit("WRN", module, message)
    fun audit(module: String, message: String) = emit("AUD", module, message)

    private fun emit(level: String, module: String, message: String) {
        val line = "[$level][$module] $message\n"
        sinks.forEach { it.write(line) }
    }

    override fun close() {
        sinks.forEach(LogSink::close)
    }

    companion object {
        fun open(): RylweenLog =
            RylweenLog(parseLogSinks(System.getenv("RYL_LOG").orEmpty()))
    }
}

private interface LogSink : Closeable {
    fun write(line: String)
    override fun close() {}
}

private fun parseLogSinks(spec: String): List<LogSink> {
    val tokens = spec.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    val sinks = tokens.mapNotNull { token ->
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

private object NoopLogSink : LogSink {
    override fun write(line: String) = Unit
}

private object StderrLogSink : LogSink {
    override fun write(line: String) {
        System.err.print(line)
    }
}

private class FileLogSink(file: File) : LogSink {
    private val writer = file.apply { parentFile?.mkdirs() }.bufferedWriter()

    override fun write(line: String) {
        writer.write(line)
        writer.flush()
    }

    override fun close() {
        writer.close()
    }
}

private class TcpLogSink private constructor(private val server: ServerSocketChannel) : LogSink {
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
    val host = when (parts.size) {
        1 -> "127.0.0.1"
        else -> parts.dropLast(1).joinToString(":").ifEmpty { "127.0.0.1" }
    }
    val port = parts.last().toInt()
    return InetSocketAddress(host, port)
}

private fun parseAnsiConfig(args: List<String>): AnsiRuntimeConfig {
    var config = AnsiRuntimeConfig()
    var index = 0
    while (index < args.size) {
        when (val arg = args[index]) {
            "--state" -> {
                config = config.copy(state = stateId(args.valueAfter(index, arg)))
                index += 2
            }
            "--seed" -> {
                config = config.copy(seed = args.valueAfter(index, arg).toLong())
                index += 2
            }
            "--hour" -> {
                config = config.copy(hour = args.valueAfter(index, arg).toInt().mod(24))
                index += 2
            }
            "--load" -> {
                config = config.copy(loadFile = File(args.valueAfter(index, arg)))
                index += 2
            }
            "--load-slot" -> {
                config = config.copy(loadFile = runtimeSaveSlotFile(args.valueAfter(index, arg)))
                index += 2
            }
            "--save" -> {
                config = config.copy(saveFile = File(args.valueAfter(index, arg)))
                index += 2
            }
            "--save-slot" -> {
                val slot = args.valueAfter(index, arg)
                config = config.copy(
                    saveFile = runtimeSaveSlotFile(slot),
                    saveState = runtimeSaveSlotStateId(slot),
                )
                index += 2
            }
            "--screen" -> {
                config = config.copy(staticScreen = staticScreenArg(args.valueAfter(index, arg)))
                index += 2
            }
            "--new", "--new-game" -> {
                config = config.copy(newGame = true)
                index += 1
            }
            "--help", "-h" -> {
                printAnsiUsage()
                exitProcess(0)
            }
            else -> error("unknown argument $arg")
        }
    }
    return config
}

private fun List<String>.valueAfter(index: Int, option: String): String =
    getOrNull(index + 1) ?: error("$option requires a value")

private fun printAnsiUsage() {
    println(
        """
        |Usage: build/install/rylween-kotlin/bin/rylween
        |
        |Options:
        |  --state  state slug; skips the title menu and boots a fixture/runtime state
        |  --seed   runtime RNG state, defaults to the selected state seed
        |  --hour   0..23, overrides the selected state hour
        |  --load   runtime snapshot file to boot instead of --state
        |  --load-slot named runtime snapshot under build/saves
        |  --save   runtime snapshot target, defaults to build/saves/main.kts
        |  --save-slot named runtime snapshot target under build/saves
        |  --new    create a fresh party through the authored character-creation flow
        |  --screen title, help, manual, score, vscore, or heroes; displays the authored static screen and exits
        |
        |Logging:
        |  RYL_LOG=stderr
        |  RYL_LOG=file:build/rylween.log
        |  RYL_LOG=tcp:9999,file:build/rylween.log
        """.trimMargin(),
    )
}
