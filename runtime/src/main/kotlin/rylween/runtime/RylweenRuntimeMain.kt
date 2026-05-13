/**
 * Runtime entry point.
 *
 * This package is the parallel implementation track for running Rylween from
 * the J1/J2 compiled bundle without composing the existing Kotlin
 * `engine`, `runtime`, `wilderness`, `town`, `dungeon`, `castle`, `battle`, or
 * terminal adapter classes. It reads direct compiled tables from
 * `compiled/CompiledBundle.kt` plus stable terminal presentation rows from
 * `compiled/ExternalBundleArtifact.kt`, then owns its own small runtime loop.
 *
 * The current slice proves the boundary with title/static-screen rendering and
 * direct wilderness movement. Full town, dungeon, castle, battle, persistence,
 * and compiled-script host services should be added here depth first instead
 * of calling back into the legacy session layer.
 */
package rylween.runtime

import rylween.compiled.CompiledBuildingService
import rylween.compiled.CompiledCharacter
import rylween.compiled.CompiledCharacterPlacement
import rylween.compiled.CompiledContentBundle
import rylween.compiled.CompiledEquipment
import rylween.compiled.CompiledGameBundle
import rylween.compiled.CompiledHandleKind
import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledIntExpr
import rylween.compiled.CompiledInventorySlot
import rylween.compiled.CompiledJournalEntry
import rylween.compiled.CompiledLocationPlacement
import rylween.compiled.CompiledNpcPlacement
import rylween.compiled.CompiledNpcVisit
import rylween.compiled.CompiledPartyCounters
import rylween.compiled.CompiledPartyImage
import rylween.compiled.CompiledPartyMember
import rylween.compiled.CompiledRandomNpcPlacementOption
import rylween.compiled.CompiledRandomNpcPlacementPolicy
import rylween.compiled.CompiledRuntimeTextPart
import rylween.compiled.CompiledStateImage
import rylween.compiled.CompiledStringExpr
import rylween.compiled.CompiledTerrain
import rylween.compiled.CompiledTextBlock
import rylween.compiled.CompiledTextPart
import rylween.compiled.CompiledWorldPosition
import rylween.compiled.ExternalCharacterCreationClassOption
import rylween.compiled.ExternalCharacterCreationPrompts
import rylween.compiled.ExternalCharacterCreationStatRoll
import rylween.compiled.ExternalRuntimeCommand
import rylween.compiled.ExternalRuntimeInputBinding
import rylween.compiled.ExternalRuntimeSystems
import rylween.compiled.ExternalRuntimeTextBlock
import rylween.compiled.readExternalCompiledBundleContentHash
import rylween.compiled.readExternalCompiledGameBundle
import rylween.compiled.readExternalRuntimeSystems
import rylween.dsl.DEFAULT_TEXT_WINDOW_COLUMNS
import rylween.dsl.impl.CharacterCreationStat
import rylween.dsl.impl.Facing
import rylween.dsl.impl.Gender
import rylween.dsl.impl.GridPoint
import rylween.dsl.impl.PartyResources
import rylween.dsl.impl.PcClass
import rylween.dsl.impl.Race
import rylween.dsl.impl.Resource
import rylween.dsl.impl.RuntimeMode
import rylween.dsl.impl.StatPool
import rylween.terminal.AnsiTerminal
import rylween.terminal.AnsiTerminals
import java.io.Closeable
import java.io.File
import java.time.LocalDate
import kotlin.math.max
import kotlin.system.exitProcess

private const val TITLE_SCREEN = "title"
private const val TITLE_COMMAND_GROUP = "title"
private const val SCORE_SCREEN = "score"
private const val HEROES_SCREEN = "heroes"
private const val MANUAL_SCREEN = "manual"
private const val TEXT_WINDOW_PAGE_LINES = 9
private const val TOWN_MAIN_STREET_IMAGE = "blitgfx.towne_mainstreet"
private const val TOWN_COMMAND_GROUP = "town"
private const val TOWN_LEAVE_ACTION = "leave_town"

/** CLI entry point for the runtime. */
object RylweenRuntimeMain {
    @JvmStatic
    fun main(args: Array<String>) {
        runCatching {
            RuntimeCli.parse(args.toList()).run()
        }.getOrElse { error ->
            System.err.println("runtime failed: ${error.message ?: error::class.simpleName}")
            exitProcess(1)
        }
    }
}

/** Parsed command-line configuration for [GameRuntime]. */
data class RuntimeConfig(
    val bundleFile: File,
    val userId: String?,
    val stateSlug: String = "fresh-replay",
    val stateExplicit: Boolean = false,
    val loadFile: File? = null,
    val saveFile: File = File("build/saves/runtime.rsave"),
    val staticScreen: String? = null,
    val scriptedInput: String? = null,
    val seed: Long? = null,
    val logFile: File = RuntimeLog.defaultFile,
    val debugLogFlash: Boolean = false,
    val debugSecrets: Boolean = false,
    val showTitleMenu: Boolean = false,
    val skipTitleMenu: Boolean = false,
)

private object RuntimeCli {
    fun parse(args: List<String>): RuntimeConfig {
        if (args.any { it == "--help" || it == "-h" }) {
            printHelp()
            exitProcess(0)
        }

        var bundleFile: File? = null
        var userId: String? = null
        var stateSlug = "fresh-replay"
        var stateExplicit = false
        var loadFile: File? = null
        var saveFile = File("build/saves/runtime.rsave")
        var staticScreen: String? = null
        var scriptedInput: String? = null
        var seed: Long? = null
        var logFile = RuntimeLog.defaultFile
        var debugLogFlash = false
        var debugSecrets = false
        var showTitleMenu = false
        var skipTitleMenu = false
        var index = 0
        while (index < args.size) {
            when (val arg = args[index]) {
                "--bundle" -> {
                    bundleFile = File(args.valueAfter(index, arg))
                    index += 2
                }

                "--state" -> {
                    stateSlug = args.valueAfter(index, arg)
                    stateExplicit = true
                    index += 2
                }

                "--user" -> {
                    userId = args.valueAfter(index, arg).validatedRuntimeUserId()
                    index += 2
                }

                "--load" -> {
                    loadFile = File(args.valueAfter(index, arg))
                    index += 2
                }

                "--save" -> {
                    saveFile = File(args.valueAfter(index, arg))
                    index += 2
                }

                "--screen" -> {
                    staticScreen = args.valueAfter(index, arg).screenSlug()
                    index += 2
                }

                "--script" -> {
                    scriptedInput = args.valueAfter(index, arg)
                    index += 2
                }

                "--seed" -> {
                    seed = args.valueAfter(index, arg).toLong()
                    index += 2
                }

                "--log" -> {
                    logFile = File(args.valueAfter(index, arg))
                    index += 2
                }

                "--debug-log", "--debug-log-flash" -> {
                    debugLogFlash = true
                    index += 1
                }

                "--debug-secrets" -> {
                    debugSecrets = true
                    index += 1
                }

                "--title" -> {
                    showTitleMenu = true
                    index += 1
                }

                "--no-title" -> {
                    skipTitleMenu = true
                    index += 1
                }

                else -> error("unknown argument $arg")
            }
        }
        check(listOf(staticScreen != null, scriptedInput != null).count { it } <= 1) {
            "--screen and --script are mutually exclusive"
        }
        check(!(showTitleMenu && skipTitleMenu)) {
            "--title and --no-title are mutually exclusive"
        }
        if (staticScreen == null) {
            check(userId != null) { "--user is required for interactive runtime sessions" }
        }
        if (staticScreen != null && loadFile != null) {
            check(userId != null) { "--user is required when rendering a saved runtime screen" }
        }
        return RuntimeConfig(
            bundleFile = checkNotNull(bundleFile) { "--bundle is required" },
            userId = userId,
            stateSlug = stateSlug,
            stateExplicit = stateExplicit,
            loadFile = loadFile,
            saveFile = saveFile,
            staticScreen = staticScreen,
            scriptedInput = scriptedInput,
            seed = seed,
            logFile = logFile,
            debugLogFlash = debugLogFlash,
            debugSecrets = debugSecrets,
            showTitleMenu = showTitleMenu,
            skipTitleMenu = skipTitleMenu,
        )
    }

    private fun printHelp() {
        @Suppress("ReplacePrintlnWithLogging")
        println(
            """
            |Usage: rylween --bundle build/compiled/rylween-bundle.json [options]
            |
            |Options:
            |  --state <slug>    Boot compiled state image directly, default fresh-replay.
            |  --user <id>       Save/load user id inside the shared world save.
            |  --load <file>     Load a runtime save.
            |  --save <file>     Save target for the S command, default build/saves/runtime.rsave.
            |  --screen <slug>   Render one compiled static screen and exit.
            |  --script <chars>  Feed command characters, useful for smoke tests.
            |  --seed <long>     Override the active party RNG seed before play.
            |  --log <file>      Runtime forensic log, default build/logs/runtime/latest.log.
            |  --debug-log       Flash each forensic log line below the game frame.
            |  --debug-secrets   Enable hidden manual-test wilderness triggers.
            |  --title           Show title flow even when direct boot would skip it.
            |  --no-title        Skip title flow and boot directly.
            """.trimMargin(),
        )
    }
}

private fun List<String>.valueAfter(
    index: Int,
    option: String,
): String = getOrNull(index + 1) ?: error("$option requires a value")

private fun String.validatedRuntimeUserId(): String =
    trim()
        .also { userId -> require(userId.any(Char::isLetterOrDigit)) { "--user must contain a letter or digit" } }

internal fun String.screenSlug(): String =
    when (val normalized = lowercase().replace('-', '_')) {
        "vscore", "verbose", "verbose_score" -> "verbose_score"
        "hall", "hall_of_heroes" -> "heroes"
        else -> normalized
    }

private fun RuntimeState.logPosition(): String = "${party.position.x},${party.position.y}"

private fun RuntimeConfig.run() {
    val runtime = GameRuntime.load(bundleFile)
    RuntimeLog.open(logFile).use { log ->
        log.event(
            "run.start bundle=${bundleFile.path} state=$stateSlug load=${loadFile?.path.orEmpty()} " +
                "save=${saveFile.path} screen=${staticScreen.orEmpty()} script=${scriptedInput != null} " +
                "user=$userId seed=${seed ?: ""} title=$showTitleMenu noTitle=$skipTitleMenu debugLogFlash=$debugLogFlash " +
                "debugSecrets=$debugSecrets",
        )
        if (staticScreen != null) {
            runtime.renderStaticScreen(staticScreen, stateSlug, loadFile, userId, log)
            return
        }
        runtime.runWilderness(
            userId = checkNotNull(userId) { "--user is required for interactive runtime sessions" },
            stateSlug = stateSlug,
            loadFile = loadFile,
            saveFile = saveFile,
            scriptedInput = scriptedInput,
            seed = seed,
            showTitleMenu =
                loadFile == null &&
                    !skipTitleMenu &&
                    (showTitleMenu || scriptedInput == null && !stateExplicit),
            debugLogFlash = debugLogFlash,
            debugSecrets = debugSecrets,
            log = log,
        )
    }
}

/**
 * Direct runtime facade.
 *
 * This class owns bundle lookup, static resource rendering, and the first
 * independent wilderness loop. It deliberately depends on `rylween.compiled`
 * tables and stable runtime-system data only.
 */
class GameRuntime private constructor(
    private val bundle: CompiledGameBundle,
    private val systems: ExternalRuntimeSystems,
    private val bundleContentHash: String,
) {
    private val content: CompiledContentBundle = bundle.content

    /** Renders a compiled static screen route from the bundle and returns. */
    fun renderStaticScreen(
        screenSlug: String,
        stateSlug: String,
        loadFile: File?,
        userId: String?,
        log: RuntimeLog,
    ): RuntimeSessionResult =
        AnsiTerminals.stdout().use { terminal ->
            renderStaticScreen(
                terminal = terminal,
                screenSlug = screenSlug,
                stateSlug = stateSlug,
                loadFile = loadFile,
                userId = userId,
                log = log,
            )
        }

    /** Renders a compiled static screen route using a caller-owned terminal. */
    fun renderStaticScreen(
        terminal: AnsiTerminal,
        screenSlug: String,
        stateSlug: String,
        loadFile: File?,
        userId: String?,
        log: RuntimeLog,
    ): RuntimeSessionResult {
        log.event("screen.static slug=$screenSlug state=$stateSlug load=${loadFile?.path.orEmpty()}")
        val runtime =
            loadFile?.let { file ->
                SaveFile.read(
                    file = file,
                    expectedBundleHash = bundleContentHash,
                    content = content,
                    stateLookup = ::stateBySlug,
                    userId = checkNotNull(userId) { "--user is required when rendering a saved runtime screen" },
                )
            } ?: RuntimeState.from(stateBySlug(stateSlug), content)
        RuntimeScreen(bundle, terminal, systems, log).showStaticScreen(screenSlug, runtime)
        return RuntimeSessionResult(RuntimeSessionExitReason.StaticScreenRendered)
    }

    /** Runs the runtime wilderness loop from one compiled state image. */
    fun runWilderness(
        userId: String,
        stateSlug: String,
        loadFile: File?,
        saveFile: File,
        scriptedInput: String?,
        seed: Long? = null,
        showTitleMenu: Boolean = false,
        debugLogFlash: Boolean = false,
        debugSecrets: Boolean = false,
        sessionDate: LocalDate = LocalDate.now(),
        log: RuntimeLog,
    ): RuntimeSessionResult =
        AnsiTerminals.open(scripted = scriptedInput != null).use { terminal ->
            runWilderness(
                terminal = terminal,
                userId = userId,
                stateSlug = stateSlug,
                loadFile = loadFile,
                saveFile = saveFile,
                scriptedInput = scriptedInput,
                seed = seed,
                showTitleMenu = showTitleMenu,
                debugLogFlash = debugLogFlash,
                debugSecrets = debugSecrets,
                sessionDate = sessionDate,
                log = log,
            )
        }

    /** Runs the runtime wilderness loop using a caller-owned terminal. */
    fun runWilderness(
        terminal: AnsiTerminal,
        userId: String,
        stateSlug: String,
        loadFile: File?,
        saveFile: File,
        scriptedInput: String?,
        seed: Long? = null,
        showTitleMenu: Boolean = false,
        debugLogFlash: Boolean = false,
        debugSecrets: Boolean = false,
        sessionDate: LocalDate = LocalDate.now(),
        log: RuntimeLog,
    ): RuntimeSessionResult {
        val input = RuntimeInput(terminal, scriptedInput, log)
        val screen = RuntimeScreen(bundle, terminal, systems, log)
        return runWilderness(
            input = input,
            screen = screen,
            userId = userId,
            stateSlug = stateSlug,
            loadFile = loadFile,
            saveFile = saveFile,
            seed = seed,
            showTitleMenu = showTitleMenu,
            debugLogFlash = debugLogFlash,
            debugSecrets = debugSecrets,
            sessionDate = sessionDate,
            log = log,
        )
    }

    /** Runs the runtime loop through renderer-neutral ports. */
    fun runWilderness(
        input: RuntimeInputPort,
        screen: RuntimeScreenPort,
        userId: String,
        stateSlug: String,
        loadFile: File?,
        saveFile: File,
        seed: Long? = null,
        showTitleMenu: Boolean = false,
        debugLogFlash: Boolean = false,
        debugSecrets: Boolean = false,
        sessionDate: LocalDate = LocalDate.now(),
        log: RuntimeLog,
    ): RuntimeSessionResult {
        var runtime =
            loadFile?.let { file ->
                logSaveFileLines("runtime.restore", file, log)
                SaveFile.read(
                    file = file,
                    expectedBundleHash = bundleContentHash,
                    content = content,
                    stateLookup = ::stateBySlug,
                    userId = userId,
                )
            } ?: RuntimeState.from(stateBySlug(stateSlug), content)
        runtime = seed?.let(runtime::withSeed) ?: runtime
        val world = WorldMap.from(content, runtime.currentMap)
        val flashLogSink =
            if (debugLogFlash) {
                log.addSink(screen::flashLogLine)
            } else {
                Closeable {}
            }
        flashLogSink.use {
            log.event("wilderness.boot state=$stateSlug position=${runtime.logPosition()} mode=${runtime.currentMode}")
            val saveContext = RuntimeSaveContext(saveFile, bundleContentHash, userId)
            val loopContext = RuntimeLoopContext(content, systems, input, screen, saveContext, log)
            val hasUserSave = loadFile?.let { file -> SaveFile.hasUserParty(file, bundleContentHash, userId) } ?: false
            if (showTitleMenu || loadFile != null && !hasUserSave) {
                runtime =
                    runTitleMenu(runtime, stateSlug, seed, loopContext)
                        ?: return RuntimeSessionResult(RuntimeSessionExitReason.SessionEnded)
                log.event("wilderness.title_selected position=${runtime.logPosition()} mode=${runtime.currentMode}")
            }
            val sessionStartRuntime = runtime
            runtime = runtime.materializeMissingNpcPlacements(world, log)
            runtime = runtime.applySessionDate(sessionDate, log)
            persistSessionStartIfChanged(sessionStartRuntime, runtime, saveContext, log)
            logNpcPlacements(runtime, log)
            val commands = WildernessCommands.from(systems)
            val debugCommands = WildernessCommands.from(systems, "wilderness.debug")
            val actions =
                WildernessActions(
                    content = content,
                    systems = systems,
                    realm = systems.realm,
                    combat = systems.combat,
                    wilderness = systems.wilderness,
                    commands = systems.commands,
                    input = input,
                    screen = screen,
                    log = log,
                )
            val utility = UtilityActions(content, systems, input, screen, log)
            screen.bootFrame()
            screen.renderWilderness(runtime, world)
            while (true) {
                when (runtime.currentMode) {
                    RuntimeMode.Dungeon -> {
                        runtime =
                            DungeonRuntime(loopContext)
                                .run(runtime)
                        if (runtime.currentMode == RuntimeMode.GameOver) {
                            screen.renderGameOver(runtime)
                            return gameOverResult()
                        }
                        if (input.exhausted) return input.exitResult()
                        screen.renderWilderness(runtime, world)
                        continue
                    }

                    RuntimeMode.Castle -> {
                        runtime =
                            CastleRuntime(loopContext)
                                .run(runtime)
                        if (runtime.currentMode == RuntimeMode.GameOver) {
                            screen.renderGameOver(runtime)
                            return gameOverResult()
                        }
                        if (input.exhausted) return input.exitResult()
                        screen.renderWilderness(runtime, world)
                        continue
                    }

                    RuntimeMode.GameOver -> {
                        screen.renderGameOver(runtime)
                        return gameOverResult()
                    }
                    else -> Unit
                }
                val command = input.readCommand()
                log.event("wilderness.command key=${command ?: "null"} position=${runtime.logPosition()}")
                var moveEvent: MoveEvent? = null
                val advanceTurn =
                    when {
                        command == null -> return input.exitResult()
                        commands.matches("quit", command) -> return RuntimeSessionResult(RuntimeSessionExitReason.Quit)
                        commands.matches("moveNorth", command) -> {
                            val move = move(runtime.party, world, 0, -1, screen)
                            runtime =
                                runtime
                                    .copy(lastTerrain = move.event?.fromTerrain?.id ?: runtime.lastTerrain)
                                    .withParty(move.party)
                            moveEvent = move.event
                            move.moved
                        }

                        commands.matches("moveSouth", command) -> {
                            val move = move(runtime.party, world, 0, 1, screen)
                            runtime =
                                runtime
                                    .copy(lastTerrain = move.event?.fromTerrain?.id ?: runtime.lastTerrain)
                                    .withParty(move.party)
                            moveEvent = move.event
                            move.moved
                        }

                        commands.matches("moveWest", command) -> {
                            val move = move(runtime.party, world, -1, 0, screen)
                            runtime =
                                runtime
                                    .copy(lastTerrain = move.event?.fromTerrain?.id ?: runtime.lastTerrain)
                                    .withParty(move.party)
                            moveEvent = move.event
                            move.moved
                        }

                        commands.matches("moveEast", command) -> {
                            val move = move(runtime.party, world, 1, 0, screen)
                            runtime =
                                runtime
                                    .copy(lastTerrain = move.event?.fromTerrain?.id ?: runtime.lastTerrain)
                                    .withParty(move.party)
                            moveEvent = move.event
                            move.moved
                        }

                        commands.matches("pass", command) -> {
                            runtime = runtime.withParty(runtime.party.passTurn())
                            true
                        }

                        commands.matches("redraw", command) -> {
                            screen.renderWilderness(runtime, world)
                            false
                        }

                        commands.matches("help", command) -> {
                            printHelp(screen, input, runtime, world)
                            false
                        }

                        commands.matches("save", command) -> {
                            saveRuntime(runtime, saveContext, screen, log)
                            false
                        }

                        commands.matches("viewParty", command) -> {
                            if (
                                PartyPages.showPartyPageViews(
                                    content = content,
                                    systems = systems,
                                    input = input,
                                    screen = screen,
                                    runtime = runtime,
                                )
                            ) {
                                redrawWildernessIfCurrent(screen, runtime, world)
                            }
                            false
                        }

                        commands.matches("journal", command) -> {
                            val width = screen.textColumns ?: Int.MAX_VALUE
                            val wrapped = PartyPages.journalLines(runtime.party).flatMap { wrapTextLine(it, width) }
                            showWildernessPage(screen, input, runtime, world, wrapped)
                            false
                        }

                        commands.matches("enter", command) -> {
                            val entered = enterLocation(runtime, loopContext)
                            runtime = entered
                            entered.currentMode == RuntimeMode.Wilderness
                        }

                        commands.matches("readyWeapon", command) -> {
                            runtime = utility.readyWeapon(runtime)
                            false
                        }

                        commands.matches("readyArmor", command) -> {
                            runtime = utility.readyArmor(runtime)
                            false
                        }

                        commands.matches("removeWeapon", command) -> {
                            runtime = utility.removeWeapon(runtime)
                            redrawWildernessIfCurrent(screen, runtime, world)
                            false
                        }

                        commands.matches("removeArmor", command) -> {
                            runtime = utility.removeArmor(runtime)
                            redrawWildernessIfCurrent(screen, runtime, world)
                            false
                        }

                        commands.matches("drop", command) -> {
                            runtime = utility.drop(runtime)
                            redrawWildernessIfCurrent(screen, runtime, world)
                            false
                        }

                        commands.matches("camp", command) -> {
                            runtime = applyAction(actions.camp(runtime), screen, world)
                            false
                        }

                        commands.matches("hunt", command) -> {
                            runtime = applyAction(actions.hunt(runtime), screen, world)
                            false
                        }

                        commands.matches("castSpell", command) -> {
                            runtime = utility.castPriestSpell(runtime)
                            redrawWildernessIfCurrent(screen, runtime, world)
                            false
                        }

                        commands.matches("quaff", command) -> {
                            runtime = utility.quaff(runtime)
                            redrawWildernessIfCurrent(screen, runtime, world)
                            false
                        }

                        debugSecrets && debugCommands.matches("forceCave", command) -> {
                            runtime = applyAction(actions.forceCaveDiscovery(runtime), screen, world)
                            false
                        }

                        debugSecrets && debugCommands.matches("forceEncounter", command) -> {
                            runtime = applyAction(actions.forceEncounter(runtime), screen, world)
                            false
                        }

                        debugSecrets && debugCommands.matches("showMap", command) -> {
                            screen.showDebugWorldMap(runtime, world, input::readCommand)
                            screen.renderWilderness(runtime, world)
                            false
                        }

                        command == '\n' -> false
                        else -> {
                            screen.writeText(listOf("Unknown command '$command'.", "Press ? for commands."))
                            false
                        }
                    }
                if (advanceTurn) {
                    runtime = applyAction(actions.applyTurnUpkeep(runtime), screen, world)
                    if (runtime.currentMode == RuntimeMode.GameOver) return gameOverResult()
                    moveEvent?.let { event ->
                        runtime = applyAction(actions.applyMoveEvents(runtime, event), screen, world)
                        if (runtime.currentMode == RuntimeMode.GameOver) return gameOverResult()
                    }
                }
                if (input.exhausted) return input.exitResult()
            }
        }
    }

    private fun runTitleMenu(
        runtime: RuntimeState,
        stateSlug: String,
        seed: Long?,
        context: RuntimeLoopContext,
    ): RuntimeState? {
        val input = context.input
        val log = context.log
        val save = context.save
        val screen = context.screen
        val titleCommands = systems.commands.getValue(TITLE_COMMAND_GROUP)

        fun matches(
            action: String,
            command: Char,
        ): Boolean = titleCommands.firstOrNull { title -> title.action == action }?.binding?.matches(command) == true

        screen.showStaticScreen(TITLE_SCREEN, runtime)
        log.event("title.splash")
        if (input.readCommand() == null) return null
        while (true) {
            val savedRuntime = loadSavedRuntimeOrNull(save, log)
            screen.showTitleMenu(titleMenuLines(hasSavedGame = savedRuntime != null))
            val command = input.readCommand() ?: return null
            when {
                savedRuntime == null && matches("createNewParty", command) -> {
                    log.event("title.new state=$stateSlug")
                    val creationRuntime = seed?.let(runtime::withSeed) ?: runtime
                    return runCharacterCreation(creationRuntime, input, screen, log)
                }

                savedRuntime != null && matches("continueSavedGame", command) -> {
                    log.event("title.continue save=${save.file.path}")
                    return seed?.let(savedRuntime::withSeed) ?: savedRuntime
                }

                savedRuntime != null && matches("terminateParty", command) -> {
                    screen.writeText(
                        systems.prompts.titleMenu.terminateConfirm
                            .render { null },
                    )
                    if (input.readCommand() == 'Y') {
                        val deleted =
                            runCatching {
                                SaveFile.removeUser(save.file, save.bundleContentHash, save.userId)
                            }.getOrDefault(false)
                        log.event("title.terminate save=${save.file.path} deleted=$deleted")
                        screen.writeText(
                            titleTerminateResultLines(deleted) + save.file.path,
                        )
                        input.readCommand()
                    }
                }

                matches("scoreboard", command) -> {
                    log.event("title.score")
                    screen.showPagedStaticScreen(SCORE_SCREEN, savedRuntime ?: runtime, input::readCommand)
                }

                matches("hallOfFame", command) -> {
                    log.event("title.heroes")
                    screen.showPagedStaticScreen(HEROES_SCREEN, savedRuntime ?: runtime, input::readCommand)
                }

                matches("documentation", command) -> {
                    log.event("title.manual")
                    screen.showPagedStaticScreen(MANUAL_SCREEN, savedRuntime ?: runtime, input::readCommand)
                }

                matches("leave", command) -> {
                    log.event("title.exit")
                    return null
                }

                else -> {
                    log.event("title.invalid key=$command")
                    screen.writeText(
                        systems.prompts.titleMenu.invalidInput
                            .render { null } + "Key: $command",
                    )
                    input.readCommand()
                }
            }
        }
    }

    private fun loadSavedRuntimeOrNull(
        save: RuntimeSaveContext,
        log: RuntimeLog,
    ): RuntimeState? {
        if (!save.file.exists()) return null
        if (!SaveFile.hasUserParty(save.file, save.bundleContentHash, save.userId)) return null
        return runCatching {
            logSaveFileLines("title.restore", save.file, log)
            SaveFile.read(
                file = save.file,
                expectedBundleHash = save.bundleContentHash,
                content = content,
                stateLookup = ::stateBySlug,
                userId = save.userId,
            )
        }.onFailure { error ->
            log.event("title.save_load_failed save=${save.file.path} error=${error.message.orEmpty()}")
        }.getOrNull()
    }

    private fun titleMenuLines(hasSavedGame: Boolean): List<String> =
        systems.commands.getValue(TITLE_COMMAND_GROUP).let { commands ->
            buildList {
                addAll(
                    systems.prompts.titleMenu.header
                        .render { null },
                )
                if (hasSavedGame) {
                    add(commands.menuLine("continueSavedGame"))
                    add(commands.menuLine("terminateParty"))
                } else {
                    add(commands.menuLine("createNewParty"))
                }
                add(commands.menuLine("scoreboard"))
                add(commands.menuLine("hallOfFame"))
                add(commands.menuLine("documentation"))
                add(commands.menuLine("leave"))
            }
        }

    private fun titleTerminateResultLines(deleted: Boolean): List<String> {
        val prompt = systems.prompts.titleMenu
        if (deleted) return prompt.terminated.render { null }
        return prompt.terminateFailed.render { null }
    }

    private fun List<ExternalRuntimeCommand>.menuLine(action: String): String = first { command -> command.action == action }.mnemonicLine()

    private fun ExternalRuntimeCommand.mnemonicLine(): String = binding.mnemonicLine(label ?: binding.label)

    private fun ExternalRuntimeTextBlock.renderTown(town: rylween.compiled.CompiledTown): List<String> =
        render { kind ->
            when (kind) {
                "currentTownName" -> string(town.nameString)
                "townTaxPercent" -> town.taxPercent.toString()
                else -> null
            }
        }

    private fun runCharacterCreation(
        baseRuntime: RuntimeState,
        input: RuntimeInputPort,
        screen: RuntimeScreenPort,
        log: RuntimeLog,
    ): RuntimeState? {
        val rules = systems.characterCreation ?: error("compiled bundle has no character-creation rules")
        val prompts = systems.prompts.characterCreation
        screen.bootFrame()
        screen.writeText(prompts.header.renderCreation() + prompts.partyName.renderCreation())
        val partyName = input.readText(24)?.normalizedCreationName()?.takeIf(String::isNotBlank) ?: return null
        log.event("creation.party_name name=$partyName")
        var runtime = baseRuntime
        val members = mutableListOf<CompiledPartyMember>()
        val memberNames = mutableListOf<String>()
        while (members.size < rules.start.partyMemberMax) {
            val memberNumber = members.size + 1
            screen.showCharacterPortrait(rules.creationPortraitPreview(memberNumber, null, null, null, null))
            val statsResult = readAcceptedCreationStats(runtime, memberNumber, input, screen, log) ?: return null
            runtime = statsResult.runtime
            screen.writeText(prompts.memberName.renderCreation(memberNumber = memberNumber))
            val memberName = input.readText(24)?.normalizedCreationName()?.takeIf(String::isNotBlank) ?: return null
            log.event("creation.member_name member=$memberNumber name=$memberName")
            screen.showCharacterPortrait(rules.creationPortraitPreview(memberNumber, memberName, null, null, null))
            val gender =
                readCreationChoice(
                    screen,
                    input,
                    log,
                    prompts.gender.renderCreation(memberName = memberName),
                    creationGenderChoices(prompts),
                    "gender",
                )
                    ?: return null
            screen.showCharacterPortrait(rules.creationPortraitPreview(memberNumber, memberName, gender, null, null))
            val race =
                readCreationChoice(
                    screen,
                    input,
                    log,
                    prompts.race.renderCreation(memberName = memberName),
                    rules.races.mapIndexed { index, option ->
                        CreationChoice(
                            index.digitChoiceKey(),
                            option.label,
                            option.race,
                        )
                    },
                    "race",
                )
                    ?: return null
            screen.showCharacterPortrait(rules.creationPortraitPreview(memberNumber, memberName, gender, race, null))
            val pcClass =
                readCreationChoice(
                    screen,
                    input,
                    log,
                    prompts.classMenu.renderCreation(),
                    rules.classes.mapIndexed { index, option ->
                        CreationChoice(
                            index.digitChoiceKey(),
                            option.label,
                            option,
                        )
                    },
                    "class",
                )
                    ?: return null
            screen.showCharacterPortrait(rules.creationPortraitPreview(memberNumber, memberName, gender, race, pcClass.pcClass))
            val failure = pcClass.failureFor(statsResult.stats)
            if (failure != null) {
                log.event(
                    "creation.class_rejected member=$memberNumber class=${pcClass.pcClass} required=${pcClass.requiredStat} minimum=${pcClass.minimum}",
                )
                screen.writeText(failure.renderCreation())
                input.pause(systems.pacing.characterCreation.classRejectionPauseMs)
            } else {
                log.event("creation.member_accepted member=$memberNumber gender=$gender race=$race class=${pcClass.pcClass}")
                members += runtime.creationMember(memberNumber, gender, race, pcClass.pcClass, statsResult.stats)
                memberNames += memberName
            }
        }
        runtime = runtime.applyCreatedParty(partyName, members, memberNames)
        log.event(
            "creation.party_created name=${logValue(partyName)} members=${members.size} " +
                "position=${runtime.logPosition()} memberNames=${memberNames.joinToString(separator = ",", transform = ::logValue)}",
        )
        val savingPrompt = prompts.saving.renderCreation()
        screen.showCharacterPortrait(null)
        screen.writeText(savingPrompt)
        input.pause(systems.pacing.characterCreation.saveConfirmationPauseMs)
        screen.writeText(savingPrompt.withLastLineSuffix(" ${prompts.saveConfirmationSuffix}"))
        return runtime
    }

    private fun readAcceptedCreationStats(
        runtime: RuntimeState,
        memberNumber: Int,
        input: RuntimeInputPort,
        screen: RuntimeScreenPort,
        log: RuntimeLog,
    ): CreationStatsResult? {
        var current = runtime
        val prompts = systems.prompts.characterCreation
        val roll = checkNotNull(systems.characterCreation).statRoll
        while (true) {
            val draw = current.drawCreationStats(roll, memberNumber, log)
            current = draw.runtime
            screen.writeText(
                prompts.statsPage.renderCreation(memberNumber = memberNumber, stats = draw.stats) +
                    prompts.reroll.renderCreation(),
            )
            val command = input.readCommand() ?: return null
            val reroll = command == 'Y'
            log.event("creation.reroll member=$memberNumber key=$command reroll=$reroll")
            if (!reroll) return CreationStatsResult(draw.stats, current)
        }
    }

    private fun RuntimeState.drawCreationStats(
        roll: ExternalCharacterCreationStatRoll,
        memberNumber: Int,
        log: RuntimeLog,
    ): CreationStatsResult {
        var nextParty = party

        fun draw(stat: String): Int {
            val result = nextParty.drawRandom(roll.min, roll.max)
            nextParty = result.party
            log.event("rng.creation_stat member=$memberNumber stat=$stat range=${roll.min}..${roll.max} value=${result.value}")
            return result.value
        }

        val stats =
            CreationStats(
                weaponSkill = draw("weaponSkill"),
                wisdom = draw("wisdom"),
                health = draw("health"),
                charisma = draw("charisma"),
            )
        return CreationStatsResult(stats, withParty(nextParty))
    }

    private fun <T> readCreationChoice(
        screen: RuntimeScreenPort,
        input: RuntimeInputPort,
        log: RuntimeLog,
        prompt: List<String>,
        choices: List<CreationChoice<T>>,
        label: String,
    ): T? {
        while (true) {
            screen.writeText(prompt + choices.menuLines())
            val command = input.readCommand() ?: return null
            val choice = choices.firstOrNull { choice -> choice.key == command }
            if (choice != null) {
                log.event("creation.choice kind=$label key=$command label=${choice.label}")
                return choice.value
            }
            screen.writeText(listOf("Invalid input.", "Key: $command"))
        }
    }

    private fun creationGenderChoices(prompts: ExternalCharacterCreationPrompts): List<CreationChoice<Gender>> =
        listOf(
            CreationChoice('1', prompts.genderMaleLabel, Gender.Male),
            CreationChoice('2', prompts.genderFemaleLabel, Gender.Female),
        )

    private fun Int.digitChoiceKey(): Char = (this + 1).digitToChar()

    private fun <T> List<CreationChoice<T>>.menuLines(): List<String> =
        chunked(2).map { row ->
            row.joinToString("  ") { choice -> "${choice.key}. ${choice.label}".padEnd(12) }.trimEnd()
        }

    private fun ExternalCharacterCreationClassOption.failureFor(stats: CreationStats): ExternalRuntimeTextBlock? =
        requiredStat?.let { stat -> minimum?.let { min -> failureText.takeIf { stats.value(stat) < min } } }

    private fun RuntimeState.creationMember(
        memberNumber: Int,
        gender: Gender,
        race: Race,
        pcClass: PcClass,
        stats: CreationStats,
    ): CompiledPartyMember {
        val blank = content.ref(CompiledHandleKind.Item, "blank")
        return CompiledPartyMember(
            nameString = party.members.getOrNull(memberNumber - 1)?.nameString ?: party.members.first().nameString,
            gender = gender,
            race = race,
            pcClass = pcClass,
            level = checkNotNull(systems.characterCreation).start.level,
            health = stats.health,
            hitPoints = StatPool(stats.health, stats.health),
            weaponSkill = stats.weaponSkill,
            wisdom = stats.wisdom,
            mana = initialCreationMana(pcClass, stats.wisdom),
            charisma = stats.charisma,
            equipment = CompiledEquipment(blank, blank),
            experience = 0,
            dead = false,
        )
    }

    private fun initialCreationMana(
        pcClass: PcClass,
        wisdom: Int,
    ): StatPool =
        when (pcClass) {
            PcClass.Mage,
            PcClass.Priest,
            -> StatPool(wisdom, wisdom)

            PcClass.Fighter,
            PcClass.Thief,
            PcClass.Bard,
            PcClass.Ranger,
            -> StatPool(0, wisdom)
        }

    private fun RuntimeState.applyCreatedParty(
        partyName: String,
        members: List<CompiledPartyMember>,
        memberNames: List<String>,
    ): RuntimeState {
        val rules = checkNotNull(systems.characterCreation).start
        val memberMax = rules.partyMemberMax
        val nextParty =
            party.copy(
                name = partyName,
                position = rules.position,
                facing = rules.facing?.let { facing -> enumValueOf<Facing>(facing) },
                resources = rules.resources,
                counters =
                    CompiledPartyCounters(
                        memberCount = members.size,
                        kills = 0,
                        waitingMail = false,
                        attacked = false,
                        attackedToday = emptyList(),
                    ),
                flags = emptyList(),
                milestones = emptyList(),
                inventory = party.inventory.map { slot -> slot.copy(item = null) },
                members = members,
                memberNames = memberNames,
                visitedNpcs = party.visitedNpcs.map { visit -> visit.copy(visited = false) },
                journal = emptyList(),
            )
        return copy(
            hour = 9,
            minute = 0,
            currentMap = content.ref(CompiledHandleKind.WorldMap, rules.map),
            currentMode = RuntimeMode.Wilderness,
            currentTown = null,
            partyMemberMax = memberMax,
            dungeon = null,
            castle = null,
            party = nextParty,
        )
    }

    private fun applyAction(
        result: WildernessActionResult,
        screen: RuntimeScreenPort,
        world: WorldMap,
    ): RuntimeState {
        if (result.runtime.currentMode == RuntimeMode.GameOver) {
            screen.renderGameOver(result.runtime)
        } else {
            screen.renderWilderness(result.runtime, world)
        }
        if (result.messages.isNotEmpty()) screen.showMessage(result.messages)
        return result.runtime
    }

    private fun RuntimeScreenPort.renderGameOver(runtime: RuntimeState) {
        updateStatus(runtime)
        systems.combat.standardBattle?.defeatImage?.let { image ->
            writeViewCommand(RuntimeViewCommand.ClearMapWindow)
            writeViewCommand(RuntimeViewCommand.ShowImage(image))
        }
    }

    private fun move(
        party: PartyState,
        world: WorldMap,
        dx: Int,
        dy: Int,
        screen: RuntimeScreenPort,
    ): MoveResult {
        val source = party.position
        val target = GridPoint(source.x + dx, source.y + dy)
        val sourceTerrain = world.terrainAt(source)
        val targetTerrain = world.terrainAt(target)
        if (targetTerrain == null || !targetTerrain.passable) {
            screen.writeText(
                systems.prompts.wilderness.impassable
                    .render { null },
            )
            return MoveResult(party, moved = false)
        }
        return MoveResult(
            party =
                party.copy(
                    position = target,
                    resources =
                        party.resources.copy(
                            timeLeft = max(0, party.resources.timeLeft - targetTerrain.movementCost),
                        ),
                ),
            event = MoveEvent(source, target, sourceTerrain, targetTerrain),
            moved = true,
        )
    }

    private fun printHelp(
        screen: RuntimeScreenPort,
        input: RuntimeInputPort,
        runtime: RuntimeState,
        world: WorldMap,
    ) {
        val pages =
            systems.wilderness.helpPages
                .map { page -> page.render { null } }
                .ifEmpty { generatedWildernessHelpPages() }
        showWildernessPages(screen, input, runtime, world, pages)
    }

    private fun showWildernessPage(
        screen: RuntimeScreenPort,
        input: RuntimeInputPort,
        runtime: RuntimeState,
        world: WorldMap,
        lines: List<String>,
    ) {
        showWildernessPages(screen, input, runtime, world, lines.chunked(TEXT_WINDOW_PAGE_LINES))
    }

    private fun showWildernessPages(
        screen: RuntimeScreenPort,
        input: RuntimeInputPort,
        runtime: RuntimeState,
        world: WorldMap,
        pages: List<List<String>>,
    ) {
        val pausePrompts = PausePrompts(systems.prompts)
        for (page in pages) {
            screen.writeText(page.take(TEXT_WINDOW_PAGE_LINES) + "" + pausePrompts.line())
            if (input.readCommand() == null) return
        }
        redrawWildernessIfCurrent(screen, runtime, world)
    }

    private fun redrawWildernessIfCurrent(
        screen: RuntimeScreenPort,
        runtime: RuntimeState,
        world: WorldMap,
    ) {
        if (runtime.currentMode == RuntimeMode.Wilderness) screen.renderWilderness(runtime, world)
    }

    private fun generatedWildernessHelpPages(): List<List<String>> =
        systems.commands["wilderness"]
            .orEmpty()
            .map { command -> "${command.binding.label} = ${command.label ?: command.action.readableSlug()}" }
            .chunked(TEXT_WINDOW_PAGE_LINES)
            .ifEmpty { listOf(listOf("Press ? for commands.")) }

    private fun saveRuntime(
        runtime: RuntimeState,
        save: RuntimeSaveContext,
        screen: RuntimeScreenPort,
        log: RuntimeLog,
    ) {
        persistRuntime(runtime, save, log, "command")
        screen.writeText(listOf("Game saved.", save.file.path))
    }

    private fun persistSessionStartIfChanged(
        before: RuntimeState,
        after: RuntimeState,
        save: RuntimeSaveContext,
        log: RuntimeLog,
    ) {
        val hasUserSave = SaveFile.hasUserParty(save.file, save.bundleContentHash, save.userId)
        if (before == after && save.file.isFile && hasUserSave) return
        persistRuntime(after, save, log, "session_start")
    }

    private fun persistRuntime(
        runtime: RuntimeState,
        save: RuntimeSaveContext,
        log: RuntimeLog,
        reason: String,
    ) {
        save.write(runtime, content)
        log.event(
            "runtime.save reason=$reason user=${save.userId} file=${save.file.path} position=${runtime.logPosition()} " +
                "mode=${runtime.currentMode} lastPlayedDate=${runtime.lastPlayedDate ?: ""}",
        )
        logSaveFileLines("runtime.save", save.file, log)
    }

    private fun logSaveFileLines(
        context: String,
        saveFile: File,
        log: RuntimeLog,
    ) {
        if (!saveFile.isFile) {
            log.event("$context.file_missing file=${saveFile.path}")
            return
        }
        val lines = SaveFile.lines(saveFile)
        log.event("$context.file file=${saveFile.path} lines=${lines.size}")
        lines.forEachIndexed { index, line ->
            log.event("$context.line index=${index + 1} value=${logValue(line)}")
        }
    }

    private fun enterLocation(
        runtime: RuntimeState,
        context: RuntimeLoopContext,
    ): RuntimeState {
        val input = context.input
        val log = context.log
        val screen = context.screen
        log.event("wilderness.enter position=${runtime.logPosition()}")
        val town = content.towns.firstOrNull { it.position == runtime.party.position }
        if (town != null) return enterTown(runtime, input, screen, town, log)
        val castle = systems.castleRows.firstOrNull { castle -> castle.entry == runtime.party.position }
        if (castle != null) {
            log.event("wilderness.enter_castle castle=${castle.id}")
            return CastleRuntime(context)
                .startCastle(runtime, castle)
        }
        log.event("wilderness.enter_none")
        screen.writeText(
            systems.prompts.wilderness.nothingToEnter
                .render { null },
        )
        return runtime
    }

    private fun enterTown(
        runtime: RuntimeState,
        input: RuntimeInputPort,
        screen: RuntimeScreenPort,
        town: rylween.compiled.CompiledTown,
        log: RuntimeLog,
    ): RuntimeState {
        log.event("town.enter town=${slug(town.id)}")
        val services =
            town.services.mapNotNull { building ->
                content.buildingServices.firstOrNull { service -> service.building == building }
            }
        if (services.isEmpty()) {
            screen.writeText(listOf("${string(town.nameString)} has no compiled services."))
            return runtime
        }
        val townCommands = systems.commands.getValue(TOWN_COMMAND_GROUP)
        val serviceEntries =
            services.mapNotNull { service ->
                townCommands
                    .firstOrNull { command -> command.action == slug(service.action) }
                    ?.let { command -> TownMenuEntry(command, service, command.label ?: command.binding.label) }
            }
        val leaveCommand = townCommands.firstOrNull { command -> command.action == TOWN_LEAVE_ACTION }
        if (serviceEntries.isEmpty()) {
            screen.writeText(listOf("${string(town.nameString)} has no compiled service commands."))
            return runtime
        }

        fun menuLines(): List<String> =
            buildList {
                addAll(
                    systems.prompts.town.menuTitle
                        .renderTown(town),
                )
                add("")
                serviceEntries.forEach { entry -> add(entry.menuLine()) }
                leaveCommand?.let { command -> add(command.mnemonicLine()) }
            }

        var current = runtime.copy(currentMode = RuntimeMode.Town, currentTown = town.id)
        while (true) {
            val menu = menuLines()
            screen.showImage(TOWN_MAIN_STREET_IMAGE)
            screen.writeText(menu)
            var choice = input.readCommand() ?: return current
            while (choice == '!') {
                screen.redrawCurrentView(menu)
                choice = input.readCommand() ?: return current
            }
            val command = townCommands.firstOrNull { candidate -> candidate.binding.matches(choice) }
            if (command == null) {
                log.event("town.invalid_choice key=$choice")
                continue
            }
            if (command.action == TOWN_LEAVE_ACTION) return current.leaveTown()
            val service =
                serviceEntries.firstOrNull { entry -> entry.command.action == command.action }?.service ?: continue
            log.event("town.service town=${slug(town.id)} building=${slug(service.building)}")
            current =
                runService(
                    current.copy(currentMode = RuntimeMode.Town, currentTown = town.id),
                    service,
                    input,
                    screen,
                    log,
                )
            if (current.currentMode != RuntimeMode.Town || input.exhausted) return current
        }
    }

    private fun runService(
        runtime: RuntimeState,
        service: CompiledBuildingService,
        input: RuntimeInputPort,
        screen: RuntimeScreenPort,
        log: RuntimeLog,
    ): RuntimeState {
        val sceneSlug = slug(service.scene)
        val program =
            content.scripts.roots.firstOrNull { root -> root.root.kind.name == "Scene" && root.root.slug == sceneSlug }
        if (program == null) {
            screen.writeText(listOf("No compiled scene program for $sceneSlug."))
            return runtime
        }
        return ScriptExecutor(content, systems, input, screen, runtime, log = log).execute(program)
    }

    private fun stateBySlug(slug: String): CompiledStateImage {
        val stateRef = content.ref(CompiledHandleKind.State, slug)
        return bundle.states.firstOrNull { it.state == stateRef } ?: error("unknown compiled state $slug")
    }

    private fun RuntimeState.materializeMissingNpcPlacements(
        world: WorldMap,
        log: RuntimeLog,
    ): RuntimeState {
        var current = this
        var placementParty = current.party.copy(rngState = current.party.rngState ?: 1L)
        val placements = current.npcs.toMutableList()
        content.characters.forEach { character ->
            if (character.effectivePosition(current) != null) return@forEach
            if (character.placement == CompiledCharacterPlacement.Unplaced) {
                log.event(
                    "npc.placement.unplaced npc=${slug(character.id)} " +
                        "name=${logValue(string(character.nameString))} job=${slug(character.job)} reason=authored_unplaced",
                )
                return@forEach
            }
            val occupiedPositions = content.characters.mapNotNull { it.effectivePosition(current) }.toSet()
            val draw = placementParty.drawNpcPlacement(character.placement, world, occupiedPositions)
            if (draw == null) {
                log.event(
                    "npc.placement.unplaced npc=${slug(character.id)} " +
                        "name=${logValue(string(character.nameString))} job=${slug(character.job)} reason=no_candidates",
                )
                return@forEach
            }
            placementParty = draw.party
            val position = draw.position
            placements += CompiledNpcPlacement(character.id, position)
            current = current.copy(npcs = placements.toList())
            log.event(
                "npc.placement.generated npc=${slug(character.id)} " +
                    "name=${logValue(string(character.nameString))} job=${slug(character.job)} " +
                    "source=${character.placement.logSource()} location=${position.logLocation()}${position.townLogDetails()}",
            )
        }
        return current
    }

    private fun RuntimeState.applySessionDate(
        sessionDate: LocalDate,
        log: RuntimeLog,
    ): RuntimeState {
        val previous = lastPlayedDate
        if (previous == null) {
            log.event("realm.session_date.init date=$sessionDate timeLeft=${party.resources.timeLeft}")
            return copy(lastPlayedDate = sessionDate)
        }
        if (!sessionDate.isAfter(previous)) {
            log.event("realm.session_date.keep previous=$previous date=$sessionDate timeLeft=${party.resources.timeLeft}")
            return this
        }
        val dailyMoves = checkNotNull(systems.characterCreation).start.dailyTimeLeft
        val nextParty =
            party.copy(
                resources = party.resources.copy(timeLeft = dailyMoves),
                counters =
                    party.counters.copy(
                        attacked = false,
                        attackedToday = emptyList(),
                    ),
            )
        log.event(
            "realm.session_date.reset previous=$previous date=$sessionDate " +
                "oldTimeLeft=${party.resources.timeLeft} newTimeLeft=$dailyMoves",
        )
        return copy(lastPlayedDate = sessionDate, party = nextParty)
    }

    /**
     * Dispatches fixed, generated, and explicitly absent placement models.
     *
     * @param placement compiled placement model from the authored character row.
     * @param world current world map, used by generated wilderness placement.
     * @param occupiedPositions already materialized or fixed NPC positions.
     */
    private fun PartyState.drawNpcPlacement(
        placement: CompiledCharacterPlacement,
        world: WorldMap,
        occupiedPositions: Set<CompiledWorldPosition>,
    ): NpcPlacementDraw? =
        when (placement) {
            is CompiledCharacterPlacement.Fixed -> null
            is CompiledCharacterPlacement.Randomized ->
                drawRandomNpcPlacementPolicy(placement.policy, world, occupiedPositions)
            is CompiledCharacterPlacement.RandomizedWilderness ->
                drawRandomNpcPlacementPolicy(placement.policy, world, occupiedPositions)
            CompiledCharacterPlacement.Unplaced -> null
        }

    /**
     * Selects one authored placement bucket by relative chance shares, then
     * delegates to the bucket-specific draw. This consumes party RNG exactly
     * when a new shared-world NPC position must be materialized.
     *
     * @param policy compiled authored placement policy for the NPC.
     * @param world current world map, used for wilderness terrain checks.
     * @param occupiedPositions already materialized or fixed NPC positions.
     */
    private fun PartyState.drawRandomNpcPlacementPolicy(
        policy: CompiledRandomNpcPlacementPolicy,
        world: WorldMap,
        occupiedPositions: Set<CompiledWorldPosition>,
    ): NpcPlacementDraw? {
        if (policy.options.isEmpty()) return null
        val shareTotal = policy.options.sumOf(CompiledRandomNpcPlacementOption::chanceShares)
        if (shareTotal <= 0) return null
        val optionDraw = drawRandom(1, shareTotal)
        var remaining = optionDraw.value
        val option =
            policy.options.firstOrNull { candidate ->
                remaining -= candidate.chanceShares
                remaining <= 0
            } ?: return null
        return optionDraw.party.drawNpcPlacementOption(option, world, occupiedPositions)
    }

    /**
     * Draws a concrete position from one already-selected authored placement option.
     *
     * @param option placement bucket selected by [drawRandomNpcPlacementPolicy].
     * @param world current world map, used for wilderness terrain checks.
     * @param occupiedPositions already materialized or fixed NPC positions.
     */
    private fun PartyState.drawNpcPlacementOption(
        option: CompiledRandomNpcPlacementOption,
        world: WorldMap,
        occupiedPositions: Set<CompiledWorldPosition>,
    ): NpcPlacementDraw? =
        when (option) {
            is CompiledRandomNpcPlacementOption.TownServices -> drawTownNpcServicePlacement(option)
            is CompiledRandomNpcPlacementOption.WildernessRect ->
                drawWildernessNpcPlacement(option, world, occupiedPositions)
        }

    /**
     * Places an NPC in one of the option's named services for the selected town.
     * Services are handles such as `inn` or `tavern`, not legacy numeric slots.
     *
     * @param option compiled town-service bucket containing the town handle and
     * named service handles that may receive the NPC.
     */
    private fun PartyState.drawTownNpcServicePlacement(option: CompiledRandomNpcPlacementOption.TownServices): NpcPlacementDraw? {
        val town = content.towns.firstOrNull { row -> row.id == option.town } ?: return null
        val services = option.services.filter { service -> service in town.services }
        if (services.isEmpty()) return null
        val serviceDraw = drawRandom(0, services.lastIndex)
        return NpcPlacementDraw(CompiledWorldPosition.Town(town.id, services[serviceDraw.value]), serviceDraw.party)
    }

    /**
     * Builds eligible wilderness candidates from the authored rectangle and
     * chooses one uniformly. Terrain and occupied positions are checked here so
     * game content controls the rectangle while map semantics control validity.
     *
     * @param option compiled wilderness bucket containing the coordinate ranges
     * and optional coordinate rewrites.
     * @param world current world map, used to reject terrain that forbids NPCs.
     * @param occupiedPositions already materialized or fixed NPC positions.
     */
    private fun PartyState.drawWildernessNpcPlacement(
        option: CompiledRandomNpcPlacementOption.WildernessRect,
        world: WorldMap,
        occupiedPositions: Set<CompiledWorldPosition>,
    ): NpcPlacementDraw? {
        val candidates =
            option.y.flatMap { y ->
                option.x.mapNotNull { x ->
                    val point = GridPoint(option.xReplacements[x] ?: x, y)
                    val position = CompiledWorldPosition.Wilderness(point)
                    val terrain = world.terrainAt(point)
                    when {
                        terrain?.allowsNpcPlacement != true -> null
                        position in occupiedPositions -> null
                        else -> position
                    }
                }
            }
        if (candidates.isEmpty()) return null
        val positionDraw = drawRandom(0, candidates.lastIndex)
        return NpcPlacementDraw(candidates[positionDraw.value], positionDraw.party)
    }

    private fun logNpcPlacements(
        runtime: RuntimeState,
        log: RuntimeLog,
    ) {
        val rows =
            content.characters.mapNotNull { character ->
                val position = character.effectivePosition(runtime)
                if (position == null) {
                    log.event(
                        "npc.placement.missing npc=${slug(character.id)} " +
                            "name=${logValue(string(character.nameString))} job=${slug(character.job)}",
                    )
                    return@mapNotNull null
                }
                NpcPlacementLogRow(
                    id = character.id,
                    name = string(character.nameString),
                    job = slug(character.job),
                    source = character.positionSource(runtime),
                    location = position.logLocation(),
                    townDetails = position.townLogDetails(),
                )
            }
        val locationSummary =
            rows
                .groupingBy(NpcPlacementLogRow::location)
                .eachCount()
                .entries
                .sortedBy(Map.Entry<String, Int>::key)
                .joinToString(separator = ",") { (location, count) -> "$location:$count" }
        log.event(
            "npc.placement.summary placed=${rows.size} total=${content.characters.size} " +
                "missing=${content.characters.size - rows.size} locations=$locationSummary",
        )
        rows
            .sortedWith(compareBy(NpcPlacementLogRow::location, NpcPlacementLogRow::job, NpcPlacementLogRow::name))
            .forEach { row ->
                log.event(
                    "npc.placement npc=${slug(row.id)} name=${logValue(row.name)} " +
                        "job=${row.job} source=${row.source} location=${row.location}${row.townDetails}",
                )
            }
    }

    private fun CompiledCharacter.positionSource(runtime: RuntimeState): String {
        val runtimePlacement = runtime.npcs.firstOrNull { placement -> placement.npc == id }
        if (runtimePlacement != null) return placement.logSource()
        if (placement is CompiledCharacterPlacement.Fixed) return "fixed"
        return "missing"
    }

    private fun CompiledCharacterPlacement.logSource(): String =
        when (this) {
            is CompiledCharacterPlacement.Fixed -> "fixed"
            is CompiledCharacterPlacement.Randomized -> "randomized"
            is CompiledCharacterPlacement.RandomizedWilderness -> "randomized_wilderness"
            CompiledCharacterPlacement.Unplaced -> "unplaced"
        }

    private fun CompiledCharacter.effectivePosition(runtime: RuntimeState): CompiledWorldPosition? =
        runtime.npcs.firstOrNull { placement -> placement.npc == id }?.position
            ?: (placement as? CompiledCharacterPlacement.Fixed)?.position

    private fun CompiledWorldPosition.logLocation(): String =
        when (this) {
            is CompiledWorldPosition.Town -> "town.${slug(town)}.${slug(building)}"
            is CompiledWorldPosition.Wilderness -> "wilderness.${point.x},${point.y}"
        }

    private fun CompiledWorldPosition.townLogDetails(): String {
        if (this !is CompiledWorldPosition.Town) return ""
        val row = content.towns.firstOrNull { townRow -> townRow.id == town } ?: return ""
        return " townName=${logValue(string(row.nameString))} townCoordinate=${row.position.x},${row.position.y}"
    }

    private fun logValue(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun string(id: Int): String = content.strings.value(id)

    private fun slug(ref: CompiledHandleRef): String = content.slug(ref)

    companion object {
        /** Loads and validates the direct compiled bundle/runtime-system sections. */
        fun load(bundleFile: File): GameRuntime =
            GameRuntime(
                bundle = bundleFile.readExternalCompiledGameBundle(),
                systems = bundleFile.readExternalRuntimeSystems(),
                bundleContentHash = bundleFile.readExternalCompiledBundleContentHash(),
            )

        /** Creates a runtime facade from already-loaded bundle/runtime-system sections. */
        fun load(
            bundle: CompiledGameBundle,
            systems: ExternalRuntimeSystems,
            bundleContentHash: String,
        ): GameRuntime =
            GameRuntime(
                bundle = bundle,
                systems = systems,
                bundleContentHash = bundleContentHash,
            )
    }
}

private data class NpcPlacementLogRow(
    val id: CompiledHandleRef,
    val name: String,
    val job: String,
    val source: String,
    val location: String,
    val townDetails: String,
)

private data class NpcPlacementDraw(
    val position: CompiledWorldPosition,
    val party: PartyState,
)

data class RuntimeState(
    val stateSlug: String,
    val state: CompiledHandleRef,
    val day: Int,
    val hour: Int,
    val minute: Int,
    val currentMap: CompiledHandleRef,
    val currentMode: RuntimeMode,
    val currentTown: CompiledHandleRef?,
    val lastTerrain: CompiledHandleRef?,
    val lastPlayedDate: LocalDate?,
    val partyMemberMax: Int,
    val locations: List<CompiledLocationPlacement>,
    val npcs: List<CompiledNpcPlacement>,
    val completedMissions: List<CompiledHandleRef>,
    val dungeon: DungeonState?,
    val castle: CastleState?,
    val party: PartyState,
) {
    fun withParty(party: PartyState): RuntimeState = copy(party = party)

    fun withCompletedMission(mission: CompiledHandleRef): RuntimeState = copy(completedMissions = (completedMissions + mission).distinct())

    fun withSeed(seed: Long): RuntimeState = withParty(party.copy(rngState = seed))

    fun leaveTown(): RuntimeState = copy(currentMode = RuntimeMode.Wilderness, currentTown = null)

    companion object {
        fun from(
            state: CompiledStateImage,
            content: CompiledContentBundle,
        ): RuntimeState =
            RuntimeState(
                stateSlug = content.slug(state.state),
                state = state.state,
                day = state.realm.day,
                hour = state.realm.hour,
                minute = state.realm.minute,
                currentMap = state.realm.currentMap,
                currentMode = state.realm.currentMode,
                currentTown = state.realm.currentTown,
                lastTerrain = state.realm.lastTerrain,
                lastPlayedDate = null,
                partyMemberMax = state.realm.partyMemberMax,
                locations = state.locations,
                npcs = state.npcs,
                completedMissions = emptyList(),
                dungeon =
                    (state.mode as? rylween.compiled.CompiledRuntimeModeImage.Dungeon)
                        ?.session
                        ?.let { session ->
                            DungeonState(
                                dungeon = session.dungeon,
                                maxDepth = session.maxDepth,
                                depth = session.depth,
                                levelEvents = session.levelEvents,
                            )
                        },
                castle = null,
                party = PartyState.from(content, state.activeParty(), state.realm.rngState),
            )
    }
}

data class PartyState(
    val id: CompiledHandleRef,
    val playerString: Int,
    val name: String,
    val position: GridPoint,
    val facing: Facing?,
    val resources: PartyResources,
    val counters: CompiledPartyCounters,
    val flags: List<CompiledHandleRef>,
    val milestones: List<CompiledHandleRef>,
    val inventory: List<CompiledInventorySlot>,
    val members: List<CompiledPartyMember>,
    val memberNames: List<String>,
    val visitedNpcs: List<CompiledNpcVisit>,
    val journal: List<String>,
    val rngState: Long?,
) {
    fun passTurn(): PartyState = copy(resources = resources.copy(timeLeft = max(0, resources.timeLeft - 1)))

    fun withGold(value: Int): PartyState = copy(resources = resources.copy(gold = value.coerceAtLeast(0)))

    fun withResource(
        resource: Resource,
        amount: Int,
    ): PartyState =
        when (resource) {
            Resource.Food -> copy(resources = resources.copy(food = amount.coerceAtLeast(0)))
            Resource.Torches -> copy(resources = resources.copy(torches = amount.coerceAtLeast(0)))
        }

    fun inventoryItem(slot: Int): CompiledHandleRef? = inventory.firstOrNull { current -> current.slot == slot }?.item

    fun withInventoryItem(
        slot: Int,
        item: CompiledHandleRef?,
    ): PartyState =
        copy(
            inventory =
                inventory.map { current ->
                    if (current.slot == slot) current.copy(item = item) else current
                },
        )

    fun drawRandom(
        lo: Int,
        hi: Int,
    ): RandomDraw {
        require(lo <= hi) { "random range must be ascending: $lo..$hi" }
        val seed = rngState ?: return RandomDraw(lo, this)
        var state = seed.takeUnless { it == 0L } ?: 1L
        state = state xor (state ushr 12)
        state = state xor (state shl 25)
        state = state xor (state ushr 27)
        val mixed = state * REFERENCE_RANDOM_MULTIPLIER
        val span = hi.toLong() - lo.toLong() + 1L
        val offset = java.lang.Long.remainderUnsigned(mixed, span)
        return RandomDraw(
            value = (lo.toLong() + offset).toInt(),
            party = copy(rngState = state),
        )
    }

    companion object {
        fun from(
            content: CompiledContentBundle,
            party: CompiledPartyImage,
            rngState: Long?,
        ): PartyState {
            val position =
                when (val current = party.position) {
                    is CompiledWorldPosition.Wilderness -> current.point
                    is CompiledWorldPosition.Town -> GridPoint(0, 0)
                }
            return PartyState(
                id = party.id,
                playerString = party.playerString,
                name = content.strings.value(party.nameString),
                position = position,
                facing = party.facing,
                resources = party.resources,
                counters = party.counters,
                flags = party.flags,
                milestones = party.milestones,
                inventory = party.inventory.normalizedInventorySlots(),
                members = party.members,
                memberNames = party.members.map { member -> content.strings.value(member.nameString) },
                visitedNpcs = party.visitedNpcs,
                journal = party.journal.map { entry -> content.journalEntryText(entry) },
                rngState = rngState,
            )
        }
    }
}

internal fun PartyState.memberName(
    content: CompiledContentBundle,
    index: Int,
): String =
    memberNames
        .getOrNull(index)
        ?.takeIf(String::isNotBlank)
        ?: members.getOrNull(index)?.let { member -> content.strings.value(member.nameString) }.orEmpty()

private fun CompiledContentBundle.journalEntryText(entry: CompiledJournalEntry): String =
    when (entry) {
        is CompiledJournalEntry.Line -> strings.value(entry.textString)
        is CompiledJournalEntry.Text -> slug(entry.text).readableSlug()
    }

/**
 * Greedy word-wrap of a single source line to `width` columns. Preserves blank
 * input lines as blank output lines. Words longer than `width` are hard-split
 * via `chunked`.
 */
internal fun wrapTextLine(
    line: String,
    width: Int,
): List<String> {
    if (width <= 0 || line.length <= width) return listOf(line)
    val tokens = line.split(' ').flatMap { word -> if (word.length > width) word.chunked(width) else listOf(word) }
    return buildList {
        var current = ""
        for (token in tokens) {
            val candidate = if (current.isEmpty()) token else "$current $token"
            if (candidate.length <= width) {
                current = candidate
            } else {
                add(current)
                current = token
            }
        }
        add(current)
    }
}

data class RandomDraw(
    val value: Int,
    val party: PartyState,
)

private data class CreationStats(
    val weaponSkill: Int,
    val health: Int,
    val wisdom: Int,
    val charisma: Int,
) {
    fun value(stat: CharacterCreationStat): Int =
        when (stat) {
            CharacterCreationStat.WeaponSkill -> weaponSkill
            CharacterCreationStat.Health -> health
            CharacterCreationStat.Wisdom -> wisdom
            CharacterCreationStat.Charisma -> charisma
        }
}

private data class CreationStatsResult(
    val stats: CreationStats,
    val runtime: RuntimeState,
)

private data class CreationChoice<T>(
    val key: Char,
    val label: String,
    val value: T,
)

private data class TownMenuEntry(
    val command: ExternalRuntimeCommand,
    val service: CompiledBuildingService,
    val label: String,
) {
    fun menuLine(): String = command.binding.mnemonicLine(label)
}

private fun ExternalRuntimeInputBinding.mnemonicLine(text: String): String = "(${primary.uppercase()})${text.afterMnemonic(primary)}"

private fun String.afterMnemonic(key: String): String =
    removePrefix(key)
        .removePrefix(key.uppercase())
        .removePrefix(key.lowercase())

private fun List<String>.withLastLineSuffix(suffix: String): List<String> =
    when {
        isEmpty() -> listOf(suffix.trimStart())
        else -> dropLast(1) + (last() + suffix)
    }

internal data class MoveResult(
    val party: PartyState,
    val event: MoveEvent? = null,
    val moved: Boolean = event != null,
)

private const val REFERENCE_RANDOM_MULTIPLIER: Long = 0x2545F4914F6CDD1DL

internal class RuntimeInput(
    private val terminal: AnsiTerminal,
    scriptedInput: String?,
    private val log: RuntimeLog,
) : RuntimeInputPort {
    private val scripted = scriptedInput?.toList()?.let(::ArrayDeque)

    override val exhausted: Boolean get() = scripted != null && scripted.isEmpty()

    override fun pause(ms: Int) {
        if (scripted == null) terminal.pause(ms)
    }

    override fun readCommand(): Char? {
        val scriptedCommand = readScriptedCommand()
        if (scriptedCommand != null) {
            log.event("input.command key=$scriptedCommand scripted=true")
            return scriptedCommand
        }
        if (scripted != null) {
            log.event("input.command eof scripted=true")
            return null
        }
        while (true) {
            val token = terminal.readKeyToken() ?: return null
            val command = RuntimeInputTokens.commandChar(token) ?: continue
            return command.also {
                log.event("input.command key=$command scripted=false")
            }
        }
    }

    override fun readInt(maxDigits: Int): Int? {
        val scriptedValue = readScriptedUnsignedInt()
        if (scriptedValue != null) return scriptedValue
        if (scripted != null) return null
        return terminal.readTextLine(maxDigits)?.trim()?.toIntOrNull()
    }

    override fun readText(maxLength: Int?): String? {
        val scriptedValue = readScriptedText()
        if (scriptedValue != null) return scriptedValue
        if (scripted != null) return null
        return terminal.readTextLine(maxLength ?: DEFAULT_TEXT_INPUT_LIMIT)
    }

    private fun readScriptedCommand(): Char? {
        val chars = scripted ?: return null
        while (chars.isNotEmpty()) {
            val next = chars.removeFirst()
            if (!next.isWhitespace()) return next.uppercaseChar()
        }
        return null
    }

    private fun readScriptedUnsignedInt(): Int? {
        val chars = scripted ?: return null
        while (chars.firstOrNull()?.isWhitespace() == true) {
            chars.removeFirst()
        }
        val digits =
            buildString {
                while (chars.firstOrNull()?.isDigit() == true) {
                    append(chars.removeFirst())
                }
            }
        return digits.takeIf(String::isNotEmpty)?.toIntOrNull()
    }

    private fun readScriptedText(): String? {
        val chars = scripted ?: return null
        while (chars.firstOrNull()?.isWhitespace() == true) {
            chars.removeFirst()
        }
        if (chars.firstOrNull() == '"') {
            chars.removeFirst()
            val quoted =
                buildString {
                    while (chars.isNotEmpty() && chars.first() != '"') {
                        append(chars.removeFirst())
                    }
                }
            if (chars.firstOrNull() == '"') chars.removeFirst()
            return quoted
        }
        val value =
            buildString {
                while (chars.isNotEmpty() && !chars.first().isWhitespace()) {
                    append(chars.removeFirst())
                }
            }
        return value.takeIf(String::isNotEmpty)
    }
}

private fun RuntimeInputPort.exitResult(): RuntimeSessionResult = RuntimeSessionResult(exitReason())

private fun gameOverResult(): RuntimeSessionResult = RuntimeSessionResult(RuntimeSessionExitReason.GameOver)

private fun RuntimeInputPort.exitReason(): RuntimeSessionExitReason {
    if (exhausted) return RuntimeSessionExitReason.ScriptExhausted
    return RuntimeSessionExitReason.InputClosed
}

private const val DEFAULT_TEXT_INPUT_LIMIT = 80

private fun ExternalRuntimeTextBlock.renderCreation(
    memberNumber: Int? = null,
    memberName: String? = null,
    stats: CreationStats? = null,
): List<String> =
    render { kind ->
        when (kind) {
            "creationMemberNumber" -> memberNumber?.toString()
            "creationMemberName" -> memberName
            "creationWeaponSkill" -> stats?.weaponSkill?.toString()
            "creationHealth" -> stats?.health?.toString()
            "creationWisdom" -> stats?.wisdom?.toString()
            "creationCharisma" -> stats?.charisma?.toString()
            else -> null
        }
    }

private fun String.normalizedCreationName(): String = trim().replaceFirstChar(Char::uppercaseChar)

internal class CompiledTextRenderContext(
    val content: CompiledContentBundle,
    val flowColumns: Int? = DEFAULT_TEXT_WINDOW_COLUMNS,
    val intValue: (CompiledIntExpr) -> String = { "[int]" },
    val stringValue: (CompiledStringExpr) -> String = { "[string]" },
    val roleNameValue: (CompiledHandleRef) -> String = { role -> "[role:${content.slug(role)}]" },
    val roleJobValue: (CompiledHandleRef) -> String = { role -> "[role-job:${content.slug(role)}]" },
    val roleLocationValue: (CompiledHandleRef) -> String = { role -> "[role-location:${content.slug(role)}]" },
    val npcNameValue: (CompiledHandleRef) -> String = { npc -> "[npc:${content.slug(npc)}]" },
    val npcJobValue: (CompiledHandleRef) -> String = { npc -> "[npc-job:${content.slug(npc)}]" },
    val npcLocationValue: (CompiledHandleRef) -> String = { npc -> "[npc-location:${content.slug(npc)}]" },
    val locationValue: (CompiledHandleRef) -> String = { location -> "[location:${content.slug(location)}]" },
    val runtimeValue: (CompiledRuntimeTextPart) -> String = { part -> "[${part.name}]" },
)

internal fun CompiledTextBlock.renderTextLines(content: CompiledContentBundle): List<String> =
    renderTextLines(CompiledTextRenderContext(content))

internal fun CompiledTextBlock.renderTextLines(context: CompiledTextRenderContext): List<String> =
    when (this) {
        is CompiledTextBlock.Ref -> listOf("[text:${context.content.slug(text)}]")
        is CompiledTextBlock.Inline -> lineStrings.map(context.content.strings::value)
        is CompiledTextBlock.Structured ->
            lines.map { line ->
                line.parts.joinToString(separator = "") { part -> part.renderText(context) }
            }
        is CompiledTextBlock.Flow ->
            paragraphs
                .map { line -> line.parts.joinToString(separator = "") { part -> part.renderText(context) } }
                .flowWrapped(context.flowColumns)
    }

private fun List<String>.flowWrapped(width: Int?): List<String> =
    flatMapIndexed { index, paragraph ->
        val wrapped = width?.let { wrapTextLine(paragraph, it) } ?: listOf(paragraph)
        if (index < lastIndex) wrapped + "" else wrapped
    }

private fun CompiledTextPart.renderText(context: CompiledTextRenderContext): String =
    when (this) {
        is CompiledTextPart.Literal -> context.content.strings.value(string)
        is CompiledTextPart.IntValue -> context.intValue(value)
        is CompiledTextPart.StringValue -> context.stringValue(value)
        is CompiledTextPart.RoleName -> context.roleNameValue(role)
        is CompiledTextPart.RoleJob -> context.roleJobValue(role)
        is CompiledTextPart.RoleLocation -> context.roleLocationValue(role)
        is CompiledTextPart.NpcName -> context.npcNameValue(npc)
        is CompiledTextPart.NpcJob -> context.npcJobValue(npc)
        is CompiledTextPart.NpcLocation -> context.npcLocationValue(npc)
        is CompiledTextPart.TownName -> context.content.townName(town)
        is CompiledTextPart.FuzzyTownLocation -> "${context.content.townName(town)} area"
        is CompiledTextPart.LocationCoordinate -> context.locationValue(location)
        is CompiledTextPart.Runtime -> context.runtimeValue(kind)
    }

class WorldMap(
    private val rows: List<String>,
    private val terrainByCode: Map<Char, CompiledTerrain>,
) {
    val width: Int = rows.maxOfOrNull(String::length) ?: 0
    val height: Int = rows.size

    fun terrainAt(point: GridPoint): CompiledTerrain? = tileAt(point)?.let(terrainByCode::get)

    fun paddedRows(): List<String> = rows.map { row -> row.padEnd(width) }

    private fun tileAt(point: GridPoint): Char? = rows.getOrNull(point.y - 1)?.getOrNull(point.x)

    companion object {
        fun from(
            content: CompiledContentBundle,
            mapRef: CompiledHandleRef,
        ): WorldMap {
            val map =
                content.worldMaps.firstOrNull { it.id == mapRef }
                    ?: error("missing compiled world map ${content.slug(mapRef)}")
            val layer = map.layers.firstOrNull() ?: error("world map ${content.slug(mapRef)} has no layers")
            return WorldMap(
                rows = layer.rowStrings.map(content.strings::value),
                terrainByCode = content.terrain.associateBy { it.mapCode },
            )
        }
    }
}

internal fun CompiledContentBundle.ref(
    kind: CompiledHandleKind,
    slug: String,
): CompiledHandleRef {
    val slugString = strings.idOf(slug) ?: error("unknown $kind slug $slug")
    return handles.ref(kind, slugString)
}

private fun CompiledContentBundle.townName(ref: CompiledHandleRef): String =
    towns.firstOrNull { town -> town.id == ref }?.let { town -> strings.value(town.nameString) } ?: slug(ref)

private fun CompiledStateImage.activeParty(): CompiledPartyImage =
    parties.firstOrNull { it.id == realm.activeParty }
        ?: error("state ${state.kind} has no active party ${realm.activeParty}")
