package rylween.runtime

import rylween.compiled.ExternalRuntimeCastle

/**
 * Renderer-neutral input consumed by the compiled-bundle runtime loop.
 *
 * The ANSI runtime backs this with a terminal. Browser runtimes can back it
 * with WebSocket queues without reimplementing command semantics.
 */
interface RuntimeInputPort {
    val exhausted: Boolean

    fun pause(ms: Int)

    fun readCommand(): Char?

    fun readInt(maxDigits: Int = 6): Int?

    fun readText(maxLength: Int? = null): String?
}

/**
 * Renderer-neutral screen consumed by the compiled-bundle runtime loop.
 *
 * Implementations choose how to turn the compiled runtime state into pixels,
 * ANSI cells, logs, or test snapshots.
 */
interface RuntimeScreenPort {
    val textColumns: Int?

    fun bootFrame()

    fun showStaticScreen(
        screenSlug: String,
        runtime: RuntimeState,
    )

    fun showPagedStaticScreen(
        screenSlug: String,
        runtime: RuntimeState,
        advance: () -> Char?,
    )

    fun renderWilderness(
        runtime: RuntimeState,
        world: WorldMap,
    )

    fun showDebugWorldMap(
        runtime: RuntimeState,
        world: WorldMap,
        advance: () -> Char?,
    )

    fun renderDungeon(runtime: RuntimeState)

    fun renderCastle(
        runtime: RuntimeState,
        castle: ExternalRuntimeCastle,
    )

    fun writeText(lines: List<String>)

    fun showMessage(lines: List<String>)

    fun updateStatus(runtime: RuntimeState)

    fun writeViewCommand(command: RuntimeViewCommand)

    fun showImage(imageSlug: String)

    fun redrawCurrentView(lines: List<String>)

    fun flashLogLine(line: String)
}
