package rylween.runtime

import rylween.terminal.AnsiTerminal
import java.io.File

/** Programmatic configuration for one interactive runtime session. */
data class RuntimeSessionConfig(
    val stateSlug: String = "fresh-replay",
    val loadFile: File? = null,
    val saveFile: File = File("build/saves/runtime.rsave"),
    val scriptedInput: String? = null,
    val seed: Long? = null,
    val showTitleMenu: Boolean = false,
    val debugLogFlash: Boolean = false,
    val debugSecrets: Boolean = false,
)

/** Programmatic configuration for rendering a one-shot static screen. */
data class RuntimeStaticScreenConfig(
    val screenSlug: String,
    val stateSlug: String = "fresh-replay",
    val loadFile: File? = null,
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
            stateSlug = config.stateSlug,
            loadFile = config.loadFile,
            saveFile = config.saveFile,
            scriptedInput = config.scriptedInput,
            seed = config.seed,
            showTitleMenu = config.showTitleMenu,
            debugLogFlash = config.debugLogFlash,
            debugSecrets = config.debugSecrets,
            log = log,
        )

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
            log = log,
        )

    companion object {
        /** Loads and validates the compiled bundle once for future sessions. */
        fun load(bundleFile: File): RuntimeSessionFactory = RuntimeSessionFactory(GameRuntime.load(bundleFile))
    }
}
