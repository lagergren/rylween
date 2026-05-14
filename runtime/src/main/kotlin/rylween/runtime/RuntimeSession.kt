package rylween.runtime

import rylween.compiled.CompiledContentBundle
import rylween.compiled.CompiledGameBundle
import rylween.compiled.ExternalRuntimeSystems
import rylween.terminal.AnsiTerminal
import java.io.File
import java.time.LocalDate

internal data class RuntimeSaveContext(
    val file: File,
    val bundleContentHash: String,
    val userId: String,
    val hallOfHeroesCompletedBy: String?,
) {
    fun write(
        runtime: RuntimeState,
        content: CompiledContentBundle,
    ) {
        SaveFile.write(file, runtime, bundleContentHash, content, userId, hallOfHeroesCompletedBy)
    }
}

internal data class RuntimeLoopContext(
    val content: CompiledContentBundle,
    val systems: ExternalRuntimeSystems,
    val input: RuntimeInputPort,
    val screen: RuntimeScreenPort,
    val save: RuntimeSaveContext,
    val log: RuntimeLog,
)

/** Programmatic configuration for one interactive runtime session. */
data class RuntimeSessionConfig(
    val userId: String,
    val stateSlug: String,
    val loadFile: File? = null,
    val saveFile: File = File("build/saves/runtime.rsave"),
    val scriptedInput: String? = null,
    val seed: Long? = null,
    val showTitleMenu: Boolean = false,
    val debugLogFlash: Boolean = false,
    val debugSecrets: Boolean = false,
    val sessionDate: LocalDate = LocalDate.now(),
)

/** Programmatic configuration for rendering a one-shot static screen. */
data class RuntimeStaticScreenConfig(
    val screenSlug: String,
    val stateSlug: String,
    val loadFile: File? = null,
    val userId: String? = null,
)

/** Coarse reason a runtime session returned control to its caller. */
enum class RuntimeSessionExitReason {
    StaticScreenRendered,
    SessionEnded,
    InputClosed,
    ScriptExhausted,
    Quit,
    GameOver,
}

/** Result returned by the programmatic runtime API. */
data class RuntimeSessionResult(
    val reason: RuntimeSessionExitReason,
)

/**
 * Programmatic entry point for embedding the compiled-bundle runtime.
 *
 * The factory owns loaded bundle/runtime-system data, while callers own the
 * terminal transport, logs, save locations, and session lifetime.
 */
class RuntimeSessionFactory private constructor(
    private val runtime: GameRuntime,
) {
    fun start(
        terminal: AnsiTerminal,
        config: RuntimeSessionConfig,
        log: RuntimeLog,
    ): RuntimeSessionResult =
        runtime.runWilderness(
            terminal = terminal,
            userId = config.userId,
            stateSlug = config.stateSlug,
            loadFile = config.loadFile,
            saveFile = config.saveFile,
            scriptedInput = config.scriptedInput,
            seed = config.seed,
            showTitleMenu = config.showTitleMenu,
            debugLogFlash = config.debugLogFlash,
            debugSecrets = config.debugSecrets,
            sessionDate = config.sessionDate,
            log = log,
        )

    fun start(
        input: RuntimeInputPort,
        screen: RuntimeScreenPort,
        config: RuntimeSessionConfig,
        log: RuntimeLog,
    ): RuntimeSessionResult =
        runtime.runWilderness(
            input = input,
            screen = screen,
            userId = config.userId,
            stateSlug = config.stateSlug,
            loadFile = config.loadFile,
            saveFile = config.saveFile,
            seed = config.seed,
            showTitleMenu = config.showTitleMenu,
            debugLogFlash = config.debugLogFlash,
            debugSecrets = config.debugSecrets,
            sessionDate = config.sessionDate,
            log = log,
        )

    fun hasUserSave(
        file: File,
        userId: String,
    ): Boolean = runtime.hasUserSave(file, userId)

    fun renderStaticScreen(
        terminal: AnsiTerminal,
        config: RuntimeStaticScreenConfig,
        log: RuntimeLog,
    ): RuntimeSessionResult =
        runtime.renderStaticScreen(
            terminal = terminal,
            screenSlug = config.screenSlug,
            stateSlug = config.stateSlug,
            loadFile = config.loadFile,
            userId = config.userId,
            log = log,
        )

    companion object {
        /** Loads and validates the compiled bundle once for future sessions. */
        fun load(bundleFile: File): RuntimeSessionFactory = RuntimeSessionFactory(GameRuntime.load(bundleFile))

        /** Creates a runtime factory from already-loaded bundle/runtime-system tables. */
        fun load(
            bundle: CompiledGameBundle,
            systems: ExternalRuntimeSystems,
            bundleContentHash: String,
        ): RuntimeSessionFactory = RuntimeSessionFactory(GameRuntime.load(bundle, systems, bundleContentHash))
    }
}
