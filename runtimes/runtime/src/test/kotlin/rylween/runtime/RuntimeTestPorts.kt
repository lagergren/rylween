/**
 * Input and screen fakes shared by runtime tests that drive runtime actions
 * directly instead of through the installed launcher.
 */

package rylween.runtime

import rylween.compiled.ExternalRuntimeCastle

/** Reads a system property the runtime test task sets, failing when it is missing. */
internal fun requiredRuntimeTestProperty(name: String): String =
    System.getProperty(name)
        ?: error("missing required test system property $name")

/** Input that replays [commands] one key at a time and is [exhausted] once every key has been read. */
internal class ScriptedInput(
    commands: String,
) : RuntimeInputPort {
    private val commands = ArrayDeque(commands.toList())

    override val exhausted: Boolean get() = commands.isEmpty()

    override fun pause(ms: Int) = Unit

    override fun readCommand(): Char? = commands.removeFirstOrNull()

    override fun readInt(maxDigits: Int): Int? = readCommand()?.digitToIntOrNull()

    override fun readText(maxLength: Int?): String? = null
}

/**
 * Screen that draws nothing. Tests that record what the runtime shows subclass
 * it and override only the calls they capture.
 */
internal open class NoOpRuntimeScreen(
    override val textColumns: Int = 36,
) : RuntimeScreenPort {
    override fun bootFrame() = Unit

    override fun showStaticScreen(
        screenSlug: String,
        runtime: RuntimeState,
    ) = Unit

    override fun showPagedStaticScreen(
        screenSlug: String,
        runtime: RuntimeState,
        advance: () -> Char?,
    ) = Unit

    override fun renderWilderness(
        runtime: RuntimeState,
        world: WorldMap,
    ) = Unit

    override fun showDebugWorldMap(
        runtime: RuntimeState,
        world: WorldMap,
        advance: () -> Char?,
    ) = Unit

    override fun renderDungeon(runtime: RuntimeState) = Unit

    override fun renderCastle(
        runtime: RuntimeState,
        castle: ExternalRuntimeCastle,
    ) = Unit

    override fun writeText(lines: List<String>) = Unit

    override fun showMessage(lines: List<String>) = Unit

    override fun updateStatus(runtime: RuntimeState) = Unit

    override fun writeViewCommand(command: RuntimeViewCommand) = Unit

    override fun showImage(imageSlug: String) = Unit

    override fun redrawCurrentView(lines: List<String>) = Unit

    override fun flashLogLine(line: String) = Unit
}
