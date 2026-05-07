/**
 * Line-terminal launcher and manual shell entry points.
 *
 * This app layer wires command-line flags into `engine/RylweenKotlinEngine`,
 * terminal adapters from `frontend/terminal`, and save/load helpers from
 * `save`. It intentionally contains frontend orchestration, not gameplay rules.
 */
@file:Suppress("ReplacePrintlnWithLogging", "UseOfSystemOutOrSystemErr")

package rylween.app

import rylween.compiled.toExternalBundleArtifact
import rylween.dsl.GameModel
import rylween.dsl.RylweenModel
import rylween.dsl.StateId
import rylween.dsl.StaticScreenId
import rylween.dsl.TownId
import rylween.dsl.Wilderness
import rylween.dsl.image
import rylween.dsl.lines
import rylween.dsl.stateId
import rylween.dsl.staticScreen
import rylween.dsl.town
import rylween.dsl.worldMap
import rylween.engine.KotlinHeadlessHost
import rylween.engine.RuntimeStartupTarget
import rylween.engine.RylweenKotlinEngine
import rylween.engine.requireStartupTown
import rylween.engine.selectStartupState
import rylween.engine.validateStartupState
import rylween.frontend.terminal.ScriptBridge
import rylween.frontend.terminal.TerminalAdapterResult
import rylween.frontend.terminal.TerminalInputRejection
import rylween.frontend.terminal.TerminalPort
import rylween.frontend.terminal.TerminalRunStatus
import rylween.frontend.terminal.TerminalTextContext
import rylween.frontend.terminal.TerminalTextRenderer
import rylween.frontend.terminal.TownSessionTerminalAdapter
import rylween.frontend.terminal.WildernessSessionTerminalAdapter
import rylween.frontend.terminal.compiledVmTerminalScriptBridge
import rylween.frontend.terminal.renderTerminalStaticScreen
import rylween.frontend.terminal.staticScreenRequiresRuntimeState
import rylween.frontend.terminal.terminalTextRenderer
import rylween.game.Maps
import rylween.game.RylweenCompiledGame
import rylween.game.States
import rylween.game.Towns
import rylween.runtime.NpcPlacementRealizerConfig
import rylween.runtime.RuntimeSnapshotModel
import rylween.runtime.WildernessNpcPlacementPolicy
import rylween.runtime.party
import rylween.runtime.toRuntimeSessionState
import rylween.runtime.wilderness.worldMapGrid
import rylween.save.FileRuntimeSavePort
import rylween.save.loadRuntimeSnapshot
import rylween.save.loadRuntimeStateSnapshot
import java.io.File
import kotlin.system.exitProcess

private data class TerminalConfig(
    val town: TownId = Towns.thord.id,
    val hour: Int? = null,
    val seed: Long? = null,
    val state: StateId = States.freshReplay,
    val loadFile: File? = null,
    val saveFile: File = File("build/rylween-save.kts"),
    val saveState: StateId = stateId("saved-game"),
    val staticScreen: StaticScreenId? = null,
    val newGame: Boolean = false,
)

private fun requireCompiledScriptVmMode(value: String) {
    when (val normalized = value.lowercase()) {
        "compiled",
        "compiled-vm",
        "vm",
        -> Unit
        "authored",
        "interpreter",
        "op",
        -> error("authored Op execution is a test-only oracle; production launchers run the compiled VM")
        else -> error("unknown script VM mode '$normalized'; expected compiled")
    }
}

private class StdioTerminalPort : TerminalPort {
    override fun write(lines: List<String>) {
        lines.forEach(::println)
    }

    override fun readLine(): String? {
        print("> ")
        return readlnOrNull()
    }
}

fun main(args: Array<String>): Unit =
    runRylweenCli {
        val config = parseTerminalConfig(args.toList(), ::printTownUsage)
        require(!config.newGame) { "--new is only supported by runKotlinGame" }
        val game = RylweenCompiledGame.nextRylween
        val content = RylweenCompiledGame.nextDsl
        val terminal = StdioTerminalPort()
        val renderer = content.terminalTextRenderer()
        config.staticScreen?.takeUnless { screenId -> content.staticScreenRequiresRuntimeState(screenId) }?.let { screenId ->
            terminal.writeStaticScreen(content, screenId)
            return@runRylweenCli
        }
        val startupTarget = if (config.staticScreen != null) RuntimeStartupTarget.StaticScreen else RuntimeStartupTarget.Town
        val state =
            config.loadFile
                ?.let {
                    validateStartupState(
                        state = loadRuntimeStateSnapshot(it, content),
                        content = content,
                        target = startupTarget,
                        sourceName = "save file ${it.path}",
                    )
                }
                ?: selectStartupState(game, content, config.state, startupTarget)
        val bootState =
            config.hour
                ?.let { hour -> state.copy(realm = state.realm.copy(hour = hour)) }
                ?: state
        config.staticScreen?.let { screenId ->
            terminal.writeStaticScreen(content, screenId, TerminalTextContext(runtime = bootState.toRuntimeSessionState()))
            return@runRylweenCli
        }
        val town = requireStartupTown(content, config.town)
        val party =
            state.parties.firstOrNull { it.id == state.realm.activeParty }
                ?: error("state ${state.id.slug} is missing active party ${state.realm.activeParty.slug}")
        val engine =
            RylweenKotlinEngine(
                content = content,
                npcPlacementConfig =
                    NpcPlacementRealizerConfig(
                        wilderness = wildernessPlacementPolicy(content),
                    ),
            )
        val session =
            engine.townSession(
                party = party,
                town = town.id,
                hour = config.hour ?: state.realm.hour,
                rngState = config.seed ?: state.realm.rngState,
            )
        val adapter =
            TownSessionTerminalAdapter(
                terminal = terminal,
                renderer = renderer,
                serviceBridge =
                    buildTerminalScriptBridge(
                        terminal = terminal,
                        game = game,
                        renderer = renderer,
                    ),
            )

        terminal.write(
            listOf(
                "Rylween Kotlin town runtime",
                "State: ${state.id.slug}",
                "Town: ${town.name}",
                "Ctrl-D exits.",
                "",
            ),
        )

        terminal.runAdapterLoop(session, "Town session finished.", adapter::runUntilBlocked)
    }

object RylweenGameTerminalMain {
    @JvmStatic
    fun main(args: Array<String>): Unit =
        runRylweenCli {
            val config = parseTerminalConfig(args.toList(), ::printGameUsage)
            val game = RylweenCompiledGame.nextRylween
            val content = RylweenCompiledGame.nextDsl
            val terminal = StdioTerminalPort()
            val renderer = content.terminalTextRenderer()
            config.staticScreen?.takeUnless { screenId -> content.staticScreenRequiresRuntimeState(screenId) }?.let { screenId ->
                terminal.writeStaticScreen(content, screenId)
                return@runRylweenCli
            }
            val startupTarget =
                if (config.staticScreen != null) {
                    RuntimeStartupTarget.StaticScreen
                } else {
                    RuntimeStartupTarget.Wilderness
                }
            val snapshot =
                config.loadFile
                    ?.let {
                        loadRuntimeSnapshot(it, content)
                    }
                    ?: RuntimeSnapshotModel(selectStartupState(game, content, config.state, startupTarget))
            validateStartupState(
                snapshot.state,
                content,
                startupTarget,
                config.loadFile?.let { "save file ${it.path}" } ?: "startup.state.${snapshot.state.id.slug}",
            )
            val bootSnapshot =
                config.hour
                    ?.let { hour -> snapshot.copy(state = snapshot.state.copy(realm = snapshot.state.realm.copy(hour = hour))) }
                    ?: snapshot
            config.staticScreen?.let { screenId ->
                terminal.writeStaticScreen(
                    content,
                    screenId,
                    TerminalTextContext(runtime = bootSnapshot.state.toRuntimeSessionState()),
                )
                return@runRylweenCli
            }
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
            val session =
                bootWildernessSession(
                    engine = engine,
                    terminal = terminal,
                    renderer = renderer,
                    snapshot = bootSnapshot,
                    worldMap = worldMap,
                    rngState = config.seed ?: bootSnapshot.state.realm.rngState,
                    newGame = config.newGame,
                )
            val adapter =
                WildernessSessionTerminalAdapter(
                    terminal = terminal,
                    renderer = renderer,
                    serviceBridge =
                        buildTerminalScriptBridge(
                            terminal = terminal,
                            game = game,
                            renderer = renderer,
                        ),
                    savePort = FileRuntimeSavePort(config.saveFile, config.saveState, content),
                )

            terminal.write(
                listOf(
                    "Rylween Kotlin wilderness runtime",
                    "State: ${bootSnapshot.state.id.slug}",
                    "Ctrl-D exits.",
                ),
            )

            terminal.runAdapterLoop(session, "Wilderness session finished.", adapter::runUntilBlocked)
        }
}

private fun buildTerminalScriptBridge(
    terminal: TerminalPort,
    game: RylweenModel,
    renderer: TerminalTextRenderer,
): ScriptBridge =
    KotlinHeadlessHost
        .fromExternalBundleCompiledVm(game.toExternalBundleArtifact())
        .compiledVmTerminalScriptBridge(terminal, renderer)

private fun <S> TerminalPort.runAdapterLoop(
    initial: S,
    finishedMessage: String,
    runUntilBlocked: (S) -> TerminalAdapterResult<S>,
) {
    var session = initial
    while (true) {
        val result = runUntilBlocked(session)
        session = result.session
        when (val status = result.status) {
            TerminalRunStatus.Finished -> {
                write(listOf("", finishedMessage))
                return
            }
            TerminalRunStatus.StepLimitReached -> {
                write(listOf("", "Step limit reached."))
                return
            }
            is TerminalRunStatus.Waiting -> {
                if (!writeRejection(status.rejection)) return
            }
        }
    }
}

private fun TerminalPort.writeRejection(rejection: TerminalInputRejection?): Boolean {
    if (rejection == null) {
        write(listOf("", "Input unavailable."))
        return false
    }
    write(
        listOf(
            "Invalid input '${rejection.input}'.",
            "Expected: ${rejection.expected.joinToString(", ")}",
            "",
        ),
    )
    return true
}

private fun TerminalPort.writeStaticScreen(
    content: GameModel,
    screenId: StaticScreenId,
    context: TerminalTextContext = TerminalTextContext(),
) {
    val rendered =
        content.renderTerminalStaticScreen(
            id = screenId,
            renderer = content.terminalTextRenderer(),
            resourceText = RylweenAppAssets::readText,
            context = context,
        ) ?: error("unknown static screen ${screenId.slug}")
    rendered.image?.let { image ->
        val asset = AnsiImageAssets.resolve(content.visuals, image)
        if (asset == null) {
            write(listOf("[image: ${image.slug}]"))
        } else {
            write(RylweenAppAssets.readText(asset.resource).lineSequence().toList())
        }
    }
    rendered.pages.forEachIndexed { index, page ->
        if (index > 0 || rendered.image != null) write(listOf(""))
        write(page)
    }
}

private fun parseTerminalConfig(
    args: List<String>,
    printUsage: () -> Unit,
): TerminalConfig {
    var config = TerminalConfig()
    // CPD-OFF
    val handler =
        commonCliFlagHandler(
            setHour = { config = config.copy(hour = it) },
            setSeed = { config = config.copy(seed = it) },
            setState = { config = config.copy(state = it) },
            setLoadFile = { config = config.copy(loadFile = it) },
            setSaveFile = { file, state ->
                config =
                    if (state == null) {
                        config.copy(saveFile = file)
                    } else {
                        config.copy(saveFile = file, saveState = state)
                    }
            },
            setStaticScreen = { config = config.copy(staticScreen = it) },
            setNewGame = { config = config.copy(newGame = true) },
        )
    // CPD-ON
    var index = 0
    while (index < args.size) {
        val advance = args.consumeCommonCliFlag(index, handler)
        if (advance != null) {
            index = advance
            continue
        }
        when (val arg = args[index]) {
            "--town" -> {
                config = config.copy(town = townArg(args.valueAfter(index, arg)))
                index += 2
            }
            "--script-vm" -> {
                requireCompiledScriptVmMode(args.valueAfter(index, arg))
                index += 2
            }
            "--help", "-h" -> {
                printUsage()
                exitProcess(0)
            }
            else -> error("unknown argument $arg")
        }
    }
    return config
}

private fun townArg(value: String): TownId =
    when (value.lowercase()) {
        "thord" -> Towns.thord.id
        "ollk" -> Towns.ollk.id
        "wilor" -> Towns.wilor.id
        "murn" -> Towns.murn.id
        else -> town(value)
    }

private fun wildernessPlacementPolicy(content: GameModel): WildernessNpcPlacementPolicy =
    WildernessNpcPlacementPolicy(
        allowedPoints =
            content
                .worldMapGrid(Maps.wilderness)
                ?.passableNpcPoints(),
    )

private fun printTownUsage() {
    println(
        """
        |Usage: ./gradlew runKotlinTown --args="--town thord --hour 12 --seed 1"
        |
        |Options:
        |  --town   thord, ollk, wilor, murn, or a raw town slug
        |  --hour   0..23, defaults to the selected state hour
        |  --seed   runtime RNG state; omitted means deterministic fixture placement/events
        |  --state  state slug, defaults to fresh-replay
        |  --load   runtime snapshot file to boot instead of --state
        |  --load-slot named runtime snapshot under build/saves
        |  --save   runtime snapshot target, defaults to build/rylween-save.kts
        |  --save-slot named runtime snapshot target under build/saves
        |  --script-vm compiled, compiled-vm, or vm; production launchers only run the compiled VM
        |  --screen title, help, manual, score, vscore, or heroes; displays the authored static screen and exits
        """.trimMargin(),
    )
}

private fun printGameUsage() {
    println(
        """
        |Usage: ./gradlew runKotlinGame --args="--state fresh-replay --seed 1"
        |
        |Options:
        |  --state  state slug, defaults to fresh-replay
        |  --seed   runtime RNG state; omitted means deterministic fixture placement/events
        |  --hour   0..23, overrides the selected state hour
        |  --load   runtime snapshot file to boot instead of --state
        |  --load-slot named runtime snapshot under build/saves
        |  --save   runtime snapshot target, defaults to build/rylween-save.kts
        |  --save-slot named runtime snapshot target under build/saves
        |  --script-vm compiled, compiled-vm, or vm; production launchers only run the compiled VM
        |  --new    create a fresh party through the authored character-creation flow
        |  --screen title, help, manual, score, vscore, or heroes; displays the authored static screen and exits
        """.trimMargin(),
    )
}
