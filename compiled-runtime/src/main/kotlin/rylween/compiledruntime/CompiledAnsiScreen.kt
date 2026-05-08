/**
 * Classic ANSI screen renderer for the standalone compiled-bundle runtime.
 *
 * The renderer consumes only compiled table rows and stable
 * `runtimeSystems.presentation` rows: image resources, screen regions, and
 * terminal terrain styles. It is the compiled-runtime counterpart to the old
 * ANSI screen code, but it remains in the `compiledRuntime` source set so the
 * product can evolve without old session or terminal-adapter dependencies.
 */

package rylween.compiledruntime

import rylween.compiled.CompiledGameBundle
import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledPartyMember
import rylween.compiled.ExternalImageAsset
import rylween.compiled.ExternalRuntimeSystems
import rylween.compiled.ExternalRuntimeTextBlock
import rylween.compiled.ExternalScreenRegion
import rylween.compiled.ExternalStaticScreen
import rylween.compiled.ExternalTerminalTerrainStyle
import rylween.dsl.GridPoint
import rylween.frontend.ansi.AnsiCodes
import kotlin.math.max
import kotlin.math.min

private const val TEXT_WINDOW = "text_window"
private const val MAP_WINDOW = "map_window"
private const val STATUSLINE_TOP = "statusline_top"
private const val STATUSLINE_BOTTOM = "statusline_bottom"

/** ANSI screen model owned by the compiled runtime. */
internal class CompiledAnsiScreen(
    private val terminal: CompiledAnsiTerminal,
    private val bundle: CompiledGameBundle,
    private val systems: ExternalRuntimeSystems,
) {
    private val content = bundle.content
    private val regions: Map<String, ExternalScreenRegion> = systems.presentation.regions.associateBy { it.id }
    private val textWindow = regions.getValue(TEXT_WINDOW)
    private val mapWindow = regions.getValue(MAP_WINDOW)
    private val statusTop = regions.getValue(STATUSLINE_TOP)
    private val statusBottom = regions.getValue(STATUSLINE_BOTTOM)
    private val imageById: Map<String, ExternalImageAsset> = systems.presentation.images.associateBy { it.id }
    private val staticScreenById: Map<String, ExternalStaticScreen> = systems.presentation.staticScreens.associateBy { it.id }
    private val stylesByTerrain: Map<String, ExternalTerminalTerrainStyle> =
        systems.presentation.terminalTerrain.associateBy { it.terrain }

    private var classicFrameActive = false

    /** Clears the terminal and renders the compiled frame image, if present. */
    fun bootFrame() {
        classicFrameActive = true
        terminal.writeRaw("${AnsiCodes.CSI}?25l")
        staticScreenById["frame"]?.image?.let(::renderImage)
            ?: terminal.writeRaw("${AnsiCodes.CSI}2J${AnsiCodes.CSI}1;1H")
        terminal.writeRaw("${AnsiCodes.FG_WHITE}${AnsiCodes.BG_BLACK}")
        terminal.flush()
    }

    /** Renders a compiled static screen route and returns without waiting. */
    fun showStaticScreen(screenSlug: String) {
        classicFrameActive = false
        val screen = staticScreenById[screenSlug.screenSlug()] ?: error("unknown static screen $screenSlug")
        screen.image?.let(::renderImage)
        screen.resource?.let { resource -> writeFullScreenText(resourceText(resource).lines()) }
        if (screen.commandHelp) {
            writeFullScreenText(listOf("Compiled command groups: ${systems.commandGroups.joinToString()}"))
        }
    }

    /** Renders wilderness HUD, map viewport, and text panel. */
    fun renderWilderness(
        runtime: StandaloneRuntimeState,
        world: StandaloneWorldMap,
    ) {
        ensureClassicFrame()
        renderStatus(runtime)
        renderMap(runtime.party.position, world)
        writeText(wildernessContextLines(runtime, world) + systems.wilderness.commandHint.render(runtime))
    }

    /** Clears the classic text window and writes the supplied lines inside it. */
    fun writeText(lines: List<String>) {
        ensureClassicFrame()
        clearRegion(textWindow)
        lines.take(textWindow.height ?: lines.size).forEachIndexed { index, line ->
            gotoTextRow(index)
            terminal.writeRaw(line.take(textWindow.width ?: line.length))
        }
        terminal.flush()
    }

    /** Applies one compiled view command to the ANSI screen. */
    fun writeViewCommand(command: CompiledRuntimeViewCommand) {
        when (command) {
            CompiledRuntimeViewCommand.ClearText -> clearText()
            CompiledRuntimeViewCommand.ClearMapWindow -> clearMapWindow()
            CompiledRuntimeViewCommand.RenderMap -> Unit
            CompiledRuntimeViewCommand.StatusLine -> Unit
            is CompiledRuntimeViewCommand.ShowImage -> showImage(command.imageSlug)
        }
    }

    /** Clears only the text window region. */
    fun clearText() {
        ensureClassicFrame()
        clearRegion(textWindow)
        terminal.flush()
    }

    /** Clears only the map/image region. */
    fun clearMapWindow() {
        ensureClassicFrame()
        clearRegion(mapWindow)
        terminal.flush()
    }

    /** Renders a compiled image asset by slug. */
    fun showImage(imageSlug: String) {
        ensureClassicFrame()
        renderImage(imageSlug)
        terminal.writeRaw("${AnsiCodes.FG_WHITE}${AnsiCodes.BG_BLACK}")
        terminal.flush()
    }

    private fun wildernessContextLines(
        runtime: StandaloneRuntimeState,
        world: StandaloneWorldMap,
    ): List<String> =
        buildList {
            add("Wilderness")
            world.terrainAt(runtime.party.position)?.let { terrain -> add("Terrain: ${content.slug(terrain.id).readableSlug()}") }
            content.towns.firstOrNull { town -> town.position == runtime.party.position }?.let { town ->
                add("At: ${content.strings.value(town.nameString)}")
                add("Press E to enter.")
            }
        }

    private fun ExternalRuntimeTextBlock.render(runtime: StandaloneRuntimeState): List<String> =
        render { kind ->
            when (kind) {
                "partyMemberMax" -> runtime.partyMemberMax.toString()
                else -> null
            }
        }

    private fun renderImage(imageSlug: String) {
        val image = imageById[imageSlug] ?: return
        if (image.placement == "MapWindow") {
            clearRegion(mapWindow)
            renderAnsiAt(image.resource, mapWindow.x, mapWindow.y)
            goto(textWindow)
            return
        }
        terminal.writeRaw("${AnsiCodes.CSI}2J${AnsiCodes.CSI}1;1H")
        terminal.writeRaw(resourceText(image.resource))
    }

    private fun renderStatus(runtime: StandaloneRuntimeState) {
        val party = runtime.party
        val food = party.resources.food.coerceAtLeast(0)
        val status = foodStatus(food, party.members)
        val top =
            buildString {
                append(AnsiCodes.FG_GREEN)
                append(timeLabel(runtime.hour))
                append("ST: ")
                append(status)
                append("${AnsiCodes.FG_GREEN} X: ${AnsiCodes.FG_YELLOW}${party.position.x.toString().padStart(2)}   ")
                append("${AnsiCodes.FG_GREEN} Y: ${AnsiCodes.FG_YELLOW}${party.position.y.toString().padStart(2)}")
                append("${AnsiCodes.FG_GREEN}     ")
                append("FD: ${AnsiCodes.FG_YELLOW}${food.toString().padStart(4)}${AnsiCodes.FG_GREEN}  ")
                append("MV: ${AnsiCodes.FG_YELLOW}${party.resources.timeLeft.toString().padStart(3)}${AnsiCodes.FG_GREEN}  ")
                append("AU: ${AnsiCodes.FG_YELLOW}${party.resources.gold.toString().padStart(6)}${AnsiCodes.FG_GREEN}")
            }
        val bottom =
            (0 until 4).joinToString("   ") { index ->
                val member = party.members.getOrNull(index)
                member
                    ?.let { current ->
                        "H${index + 1}: ${AnsiCodes.FG_YELLOW}${current.hitPoints.current.toString().padStart(3)}/" +
                            "${current.hitPoints.max.toString().padStart(3)}${AnsiCodes.FG_GREEN}"
                    }
                    ?: "H${index + 1}:        "
            }
        goto(statusTop)
        terminal.writeRaw(top)
        goto(statusBottom)
        terminal.writeRaw("${AnsiCodes.FG_GREEN}$bottom${AnsiCodes.FG_WHITE}")
        terminal.flush()
    }

    private fun foodStatus(
        food: Int,
        members: List<CompiledPartyMember>,
    ): String {
        if (food == 0) return "${AnsiCodes.FG_RED}STARVING${AnsiCodes.FG_GREEN} "
        if (food < members.count { member -> !member.dead } * 3) return "${AnsiCodes.FG_MAGENTA}HUNGRY  "
        return "${AnsiCodes.FG_YELLOW}OK    "
    }

    private fun renderMap(
        center: GridPoint,
        world: StandaloneWorldMap,
    ) {
        val viewport = world.viewport(center, rows = 9, columns = 9)
        viewport.forEachIndexed { rowIndex, cells ->
            gotoMapRow(rowIndex)
            val row =
                buildString {
                    cells.forEachIndexed { cellIndex, cell ->
                        val style = cell.terrain?.let { terrain -> stylesByTerrain[content.slug(terrain.id)] }
                        if (cell.center) append(AnsiCodes.BG_BLUE)
                        append(style?.ansiGlyph() ?: "  ")
                        append(AnsiCodes.BG_BLACK)
                        if (cellIndex < cells.lastIndex) append(' ')
                    }
                    append(AnsiCodes.FG_WHITE)
                }
            terminal.writeRaw(row)
        }
        terminal.flush()
    }

    private fun ensureClassicFrame() {
        if (!classicFrameActive) bootFrame()
    }

    private fun writeFullScreenText(lines: List<String>) {
        terminal.writeRaw("${AnsiCodes.CSI}2J${AnsiCodes.CSI}1;1H")
        lines.takeLast(24).forEachIndexed { index, line ->
            terminal.writeRaw("${AnsiCodes.CSI}${index + 1};1H")
            terminal.writeRaw(line.take(79))
        }
        terminal.flush()
    }

    private fun renderAnsiAt(
        resource: String,
        x: Int,
        y: Int,
    ) {
        resourceText(resource)
            .lineSequence()
            .map { line -> line.trimEnd('\r').replace("\t", "") }
            .forEachIndexed { row, line ->
                terminal.writeRaw("${AnsiCodes.CSI}${y + row};${x}H")
                terminal.writeRaw(line)
            }
        terminal.writeRaw(AnsiCodes.RESET)
    }

    private fun clearRegion(region: ExternalScreenRegion) {
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
        terminal.writeRaw("${AnsiCodes.CSI}${mapWindow.y + row * 2};1H\r${AnsiCodes.CSI}${mapWindow.x + 1}C")
    }

    private fun goto(
        region: ExternalScreenRegion,
        rowOffset: Int = 0,
    ) {
        terminal.writeRaw("${AnsiCodes.CSI}${region.y + rowOffset};1H\r${AnsiCodes.CSI}${region.x}C")
    }

    private fun resourceText(resource: String): String {
        val stream =
            Thread.currentThread().contextClassLoader.getResourceAsStream(resource)
                ?: error("missing compiled runtime resource $resource")
        return stream.bufferedReader().readText()
    }

    private fun timeLabel(hour: Int): String =
        when (hour.mod(24)) {
            in 6..11 -> "TIME: ${AnsiCodes.FG_YELLOW}DAWN${AnsiCodes.FG_GREEN}      "
            in 12..17 -> "TIME: ${AnsiCodes.FG_YELLOW}MIDDAY${AnsiCodes.FG_GREEN}    "
            in 18..23 -> "TIME: ${AnsiCodes.FG_YELLOW}TWILIGHT${AnsiCodes.FG_GREEN}  "
            else -> "TIME: ${AnsiCodes.FG_YELLOW}MIDNIGHT${AnsiCodes.FG_GREEN}  "
        }
}

/** Compiled-runtime view commands normalized away from DSL/runtime classes. */
internal sealed interface CompiledRuntimeViewCommand {
    data object ClearText : CompiledRuntimeViewCommand

    data object ClearMapWindow : CompiledRuntimeViewCommand

    data object RenderMap : CompiledRuntimeViewCommand

    data object StatusLine : CompiledRuntimeViewCommand

    data class ShowImage(
        val imageSlug: String,
    ) : CompiledRuntimeViewCommand
}

/** One rendered map cell in a compiled-runtime viewport. */
internal data class CompiledRuntimeMapCell(
    val terrain: rylween.compiled.CompiledTerrain?,
    val center: Boolean,
)

internal fun StandaloneWorldMap.viewport(
    center: GridPoint,
    rows: Int,
    columns: Int,
): List<List<CompiledRuntimeMapCell>> {
    val rowRadius = rows / 2
    val columnRadius = columns / 2
    val minY = max(0, center.y - rowRadius)
    val maxY = min(height - 1, minY + rows - 1)
    val minX = max(0, center.x - columnRadius)
    val maxX = min(width - 1, minX + columns - 1)
    return (minY..maxY).map { y ->
        (minX..maxX).map { x ->
            val point = GridPoint(x, y)
            CompiledRuntimeMapCell(terrainAt(point), point == center)
        }
    }
}

internal fun ExternalTerminalTerrainStyle.ansiGlyph(): String = "${AnsiCodes.foreground(color)}$glyph${AnsiCodes.RESET}"

internal fun String.readableSlug(): String =
    split('_', '-', '.')
        .filter(String::isNotEmpty)
        .joinToString(" ") { part -> part.replaceFirstChar(Char::uppercaseChar) }

internal fun rylween.compiled.CompiledContentBundle.slug(ref: CompiledHandleRef): String = strings.value(handles.handle(ref).slugString)
