/**
 * Rylween-specific ANSI screen renderer for the runtime.
 *
 * The renderer consumes only compiled table rows and stable
 * `runtimeSystems.presentation` rows: image resources, screen regions, and
 * terminal terrain styles. It is the runtime counterpart to the old
 * ANSI screen code, but it remains in the `runtime` source set so the
 * product can evolve without legacy session or adapter dependencies.
 */

package rylween.runtime

import rylween.compiled.CompiledGameBundle
import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledPartyMember
import rylween.compiled.ExternalImageAsset
import rylween.compiled.ExternalRuntimeCastle
import rylween.compiled.ExternalRuntimeSystems
import rylween.compiled.ExternalRuntimeTextBlock
import rylween.compiled.ExternalScreenRegion
import rylween.compiled.ExternalStaticScreen
import rylween.compiled.ExternalTerminalTerrainStyle
import rylween.compiled.RuntimeCommandGroupId
import rylween.dsl.impl.GridPoint
import rylween.dsl.impl.HitPoints
import rylween.dsl.impl.PcClass
import rylween.dsl.impl.TerrainSettlement
import rylween.terminal.AnsiCodes
import rylween.terminal.AnsiTerminal
import kotlin.math.max
import kotlin.math.min

private const val TEXT_WINDOW = "text_window"
private const val MAP_WINDOW = "map_window"
private const val STATUS_LINE_TOP = "statusline_top"
private const val STATUS_LINE_BOTTOM = "statusline_bottom"
private const val WILDERNESS_MAP_TILES = 5
private const val WILDERNESS_MAP_ROW_STRIDE = 2
private const val FULL_SCREEN_WIDTH = 79
private const val FULL_SCREEN_HEIGHT = 24
private const val FULL_SCREEN_PAGE_LINES = FULL_SCREEN_HEIGHT - 1

/** Screen model owned by the runtime. */
internal class RuntimeScreen(
    bundle: CompiledGameBundle,
    private val terminal: AnsiTerminal,
    private val systems: ExternalRuntimeSystems,
    private val log: RuntimeLog,
) : RuntimeScreenPort {
    private val content = bundle.content
    private val regions: Map<String, ExternalScreenRegion> = systems.presentation.regions.associateBy { it.id }
    private val textWindow = regions.getValue(TEXT_WINDOW)

    /** Width of the classic text window in columns, or null if the region declares none. */
    override val textColumns: Int? get() = textWindow.width

    private val mapWindow = regions.getValue(MAP_WINDOW)
    private val statusTop = regions.getValue(STATUS_LINE_TOP)
    private val statusBottom = regions.getValue(STATUS_LINE_BOTTOM)
    private val imageById: Map<String, ExternalImageAsset> =
        systems.presentation.images
            .filter { image -> image.kind == "Ansi" }
            .associateBy { it.id }
    private val staticScreenById: Map<String, ExternalStaticScreen> = systems.presentation.staticScreens.associateBy { it.id }
    private val stylesByTerrain: Map<String, ExternalTerminalTerrainStyle> =
        systems.presentation.terminalTerrain.associateBy { it.terrain }

    private var classicFrameActive = false
    private var activeMapImage: String? = null
    private var stickyMessageLines: List<String> = emptyList()
    private var currentTextWindowLines: List<String> = emptyList()

    /** Clears the terminal and renders the compiled frame image, if present. */
    override fun bootFrame() {
        classicFrameActive = true
        activeMapImage = null
        stickyMessageLines = emptyList()
        log.event("screen.boot_frame")
        terminal.writeRaw("${AnsiCodes.CSI}?25l")
        staticScreenById["frame"]?.image?.let { image -> renderImage(image) }
            ?: terminal.writeRaw("${AnsiCodes.CSI}2J${AnsiCodes.CSI}1;1H")
        terminal.writeRaw("${AnsiCodes.FG_WHITE}${AnsiCodes.BG_BLACK}")
        terminal.flush()
    }

    /** Renders a compiled static screen route and returns without waiting. */
    override fun showStaticScreen(
        screenSlug: String,
        runtime: RuntimeState,
    ) {
        classicFrameActive = false
        log.event("screen.static slug=$screenSlug")
        val screen = staticScreenById[screenSlug.screenSlug()] ?: error("unknown static screen $screenSlug")
        screen.image?.let { image -> renderImage(image) }
        runtimeStaticScreenText(content, systems, screen, runtime)
            .takeIf(List<String>::isNotEmpty)
            ?.let(::writeFullScreenText)
    }

    /** Renders a compiled static screen route and pages long text with a key between pages. */
    override fun showPagedStaticScreen(
        screenSlug: String,
        runtime: RuntimeState,
        advance: () -> Char?,
    ) {
        classicFrameActive = false
        log.event("screen.static_paged slug=$screenSlug")
        val screen = staticScreenById[screenSlug.screenSlug()] ?: error("unknown static screen $screenSlug")
        screen.image?.let { image -> renderImage(image) }
        val lines = runtimeStaticScreenText(content, systems, screen, runtime)
        if (lines.isEmpty()) {
            advance()
            return
        }
        writePagedFullScreenText(lines, advance)
    }

    /** Renders wilderness HUD, map viewport, and text panel. */
    override fun renderWilderness(
        runtime: RuntimeState,
        world: WorldMap,
    ) {
        ensureClassicFrame()
        log.event("screen.render_wilderness position=${runtime.party.position.x},${runtime.party.position.y}")
        renderStatus(runtime)
        renderMap(runtime.party.position, world)
        writeTextWindow(
            stickyMessageLines.takeIf { lines -> lines.isNotEmpty() }
                ?: (runtimeWildernessContextLines(content, systems, runtime, world) + "" + systems.wilderness.commandHint.render(runtime)),
        )
    }

    /** Shows the whole wilderness map with debug annotations for authored settlements and the party. */
    override fun showDebugWorldMap(
        runtime: RuntimeState,
        world: WorldMap,
        advance: () -> Char?,
    ) {
        classicFrameActive = false
        val position = runtime.party.position
        log.event("screen.debug_world_map position=${position.x},${position.y}")
        writePagedFullScreenText(debugWorldMapLines(runtime, world), advance)
    }

    /** Renders the dungeon backdrop, status lines, and authored depth prompt. */
    override fun renderDungeon(runtime: RuntimeState) {
        ensureClassicFrame()
        log.event("screen.render_dungeon depth=${runtime.dungeon?.depth ?: 1}")
        renderStatus(runtime)
        systems.dungeon.backdrop?.let(::showImage)
        val depth = runtime.dungeon?.depth ?: 1
        val defaultLines =
            systems.dungeon.prompts.levelMenu
                .renderDungeon(depth) + systems.dungeon.commandHint.renderDungeon(depth)
        writeTextWindow(
            stickyMessageLines.takeIf { lines -> lines.isNotEmpty() } ?: defaultLines,
        )
    }

    /** Renders the active castle backdrop, status lines, and navigation prompt. */
    override fun renderCastle(
        runtime: RuntimeState,
        castle: ExternalRuntimeCastle,
    ) {
        ensureClassicFrame()
        log.event("screen.render_castle castle=${castle.id} floor=${runtime.castle?.floor ?: 0}")
        renderStatus(runtime)
        castle.image?.let(::showImage)
        val state = runtime.castle
        val floorName =
            state
                ?.floor
                ?.let { floor -> castle.text.floorNames.getOrNull(floor - 1) }
                .orEmpty()
        val lines =
            buildList {
                state?.let { current ->
                    add("${current.point.x}x${current.point.y}")
                    add(floorName)
                    add("")
                }
                addAll(
                    systems.commands[RuntimeCommandGroupId.castleNavigation(castle.id)].orEmpty().take(7).map { command ->
                        command.runtimeMnemonicLine()
                    },
                )
            }
        writeTextWindow(stickyMessageLines.takeIf { lines -> lines.isNotEmpty() } ?: lines.filter(String::isNotBlank))
    }

    /** Clears the classic text window and writes the supplied lines inside it. */
    override fun writeText(lines: List<String>) {
        stickyMessageLines = emptyList()
        writeTextWindow(lines)
    }

    /** Preserves the current text window lines and adds the supplied lines below them. */
    override fun appendText(lines: List<String>) {
        if (lines.isEmpty()) return
        stickyMessageLines = emptyList()
        writeTextWindow(currentTextWindowLines + lines)
    }

    private fun writeTextWindow(lines: List<String>) {
        ensureClassicFrame()
        log.event("screen.write_text lines=${lines.size} first=${lines.firstOrNull().orEmpty().take(40)}")
        clearRegion(textWindow)
        currentTextWindowLines = lines.take(textWindow.height ?: lines.size)
        currentTextWindowLines.forEachIndexed { index, line ->
            gotoTextRow(index)
            terminal.writeRaw(line.take(textWindow.width ?: line.length))
        }
        terminal.flush()
    }

    override fun showMessage(lines: List<String>) {
        stickyMessageLines = lines
        writeTextWindow(lines)
    }

    /** Rewrites just the status bar after service-side party mutations. */
    override fun updateStatus(runtime: RuntimeState) {
        ensureClassicFrame()
        renderStatus(runtime)
    }

    /** Applies one compiled view command to the ANSI screen. */
    override fun writeViewCommand(command: RuntimeViewCommand) {
        when (command) {
            RuntimeViewCommand.ClearText -> clearText()
            RuntimeViewCommand.ClearMapWindow -> clearMapWindow()
            RuntimeViewCommand.RenderMap -> Unit
            RuntimeViewCommand.StatusLine -> Unit
            is RuntimeViewCommand.ShowImage -> showImage(command.imageSlug)
        }
    }

    /** Clears only the text window region. */
    private fun clearText() {
        ensureClassicFrame()
        stickyMessageLines = emptyList()
        clearRegion(textWindow)
        terminal.flush()
    }

    /** Clears only the map/image region. */
    private fun clearMapWindow() {
        ensureClassicFrame()
        activeMapImage = null
        clearRegion(mapWindow)
        terminal.flush()
    }

    /** Renders a compiled image asset by slug. */
    override fun showImage(imageSlug: String) {
        ensureClassicFrame()
        renderImage(imageSlug, rememberMapImage = true)
        terminal.writeRaw("${AnsiCodes.FG_WHITE}${AnsiCodes.BG_BLACK}")
        terminal.flush()
    }

    /** Repaints the remembered map-window image and supplied text panel. */
    override fun redrawCurrentView(lines: List<String>) {
        activeMapImage?.let { imageSlug -> renderImage(imageSlug) }
        writeText(lines)
    }

    /** Shows a one-line forensic log mirror below the classic 24-line frame. */
    override fun flashLogLine(line: String) {
        val cleaned = line.replace(Regex("\\s+"), " ").take(FULL_SCREEN_WIDTH)
        terminal.writeRaw(
            "${AnsiCodes.CSI}s" +
                "${AnsiCodes.CSI}${FULL_SCREEN_HEIGHT + 1};1H" +
                "${AnsiCodes.RESET}${AnsiCodes.FG_CYAN}${AnsiCodes.BG_BLACK}" +
                cleaned.padEnd(FULL_SCREEN_WIDTH) +
                "${AnsiCodes.RESET}${AnsiCodes.FG_WHITE}${AnsiCodes.BG_BLACK}" +
                "${AnsiCodes.CSI}u",
        )
        terminal.flush()
    }

    private fun debugWorldMapLines(
        runtime: RuntimeState,
        world: WorldMap,
    ): List<String> {
        val rows = world.annotatedDebugRows(runtime.party.position)
        val towns = content.towns.sortedWith(compareBy({ it.position.y }, { it.position.x }))
        val huts = world.settlementPoints(TerrainSettlement.Hut)
        val castles = world.settlementPoints(TerrainSettlement.Castle)
        return buildList {
            add("Rylween debug wilderness map")
            add("Legend: @ party, T town, H hut, C castle")
            add("Party: ${runtime.party.position.x},${runtime.party.position.y}")
            add("")
            addAll(rows)
            add("")
            add("Towns")
            towns.forEach { town ->
                add("${content.strings.value(town.nameString)} ${town.position.x},${town.position.y}")
            }
            add("")
            add("Huts")
            huts.forEach { point -> add("Hut ${point.x},${point.y}") }
            if (castles.isNotEmpty()) {
                add("")
                add("Castles")
                castles.forEach { point -> add("Castle ${point.x},${point.y}") }
            }
        }
    }

    private fun WorldMap.annotatedDebugRows(partyPosition: GridPoint): List<String> {
        val annotated = paddedRows().map { row -> row.toCharArray() }
        settlementPoints(TerrainSettlement.Hut).forEach { point -> annotated.mark(point, 'H') }
        settlementPoints(TerrainSettlement.Castle).forEach { point -> annotated.mark(point, 'C') }
        content.towns.forEach { town -> annotated.mark(town.position, 'T') }
        annotated.mark(partyPosition, '@')
        return annotated.mapIndexed { index, row -> "${(index + 1).toString().padStart(2)} ${String(row)}" }
    }

    private fun WorldMap.settlementPoints(settlement: TerrainSettlement): List<GridPoint> =
        buildList {
            for (y in 1..height) {
                for (x in 0 until width) {
                    val point = GridPoint(x, y)
                    val terrain = terrainAt(point)
                    if (terrain?.settlement == settlement) add(point)
                }
            }
        }

    private fun List<CharArray>.mark(
        point: GridPoint,
        marker: Char,
    ) {
        getOrNull(point.y - 1)?.let { row ->
            if (point.x in row.indices) row[point.x] = marker
        }
    }

    private fun ExternalRuntimeTextBlock.render(runtime: RuntimeState): List<String> = renderRuntimeText(content, runtime, textColumns)

    private fun ExternalRuntimeTextBlock.renderDungeon(depth: Int): List<String> =
        render(
            values = { kind ->
                when (kind) {
                    "dungeonDepth" -> depth.toString()
                    else -> null
                }
            },
            wrapWidth = textColumns,
        )

    private fun renderImage(
        imageSlug: String,
        rememberMapImage: Boolean = false,
    ) {
        val image = imageById[imageSlug] ?: return
        if (image.placement == "MapWindow") {
            if (rememberMapImage) activeMapImage = imageSlug
            clearRegion(mapWindow)
            renderAnsiAt(image.resource, mapWindow.x + 1, mapWindow.y)
            goto(textWindow)
            terminal.flush()
            return
        }
        activeMapImage = null
        terminal.writeRaw("${AnsiCodes.CSI}2J${AnsiCodes.CSI}1;1H")
        terminal.writeRaw(runtimeResourceText(image.resource))
        terminal.flush()
    }

    private fun renderStatus(runtime: RuntimeState) {
        val party = runtime.party
        val food = party.resources.food.coerceAtLeast(0)
        val status = conditionStatus(runtime.runtimeStatusLabel(systems))
        val foodColor = foodStatusColor(party)
        val top =
            buildString {
                append(AnsiCodes.FG_GREEN)
                append(timeLabel(runtime.hour))
                append("ST: ")
                append(status)
                append("${AnsiCodes.FG_GREEN} X: ${AnsiCodes.FG_YELLOW}${party.position.x.toString().padStart(2)}   ")
                append("${AnsiCodes.FG_GREEN} Y: ${AnsiCodes.FG_YELLOW}${party.position.y.toString().padStart(2)}")
                append("${AnsiCodes.FG_GREEN}     ")
                append("FD: $foodColor${food.toString().padStart(4)}${AnsiCodes.NORMAL_INTENSITY}${AnsiCodes.FG_GREEN}  ")
                append("MV: ${AnsiCodes.FG_YELLOW}${party.resources.timeLeft.toString().padStart(3)}${AnsiCodes.FG_GREEN}  ")
                append("AU: ${AnsiCodes.FG_YELLOW}${party.resources.gold.toString().padStart(6)}${AnsiCodes.FG_GREEN}")
            }
        val bottom =
            (0 until 4).joinToString("   ") { index ->
                val member = party.members.getOrNull(index)
                member?.statusLineSegment(index) ?: "${index + 1} --"
            }
        goto(statusTop)
        terminal.writeRaw(top)
        goto(statusBottom)
        terminal.writeRaw("${AnsiCodes.FG_GREEN}$bottom${AnsiCodes.CSI}K${AnsiCodes.FG_WHITE}")
        terminal.flush()
    }

    private fun CompiledPartyMember.statusLineSegment(index: Int): String =
        buildString {
            append(index + 1)
            append(' ')
            append(hitPoints.statusPoolText("H"))
            if (pcClass == PcClass.Mage || pcClass == PcClass.Priest) {
                append(' ')
                append(mana.statusPoolText("M"))
            }
        }

    private fun HitPoints.statusPoolText(label: String): String =
        "$label${currentAnsiColor()}$current${AnsiCodes.FG_YELLOW}/$max${AnsiCodes.FG_GREEN}"

    private fun conditionStatus(label: String): String {
        val colorAndStyle =
            when (label) {
                "STARVING", "EXHAUSTED", "WEAK", "FAINT" -> AnsiCodes.BOLD + AnsiCodes.FG_RED
                "HUNGRY", "TIRED" -> AnsiCodes.FG_YELLOW
                else -> AnsiCodes.FG_YELLOW
            }
        return "$colorAndStyle${label.take(8).padEnd(8)}${AnsiCodes.NORMAL_INTENSITY}${AnsiCodes.FG_GREEN}"
    }

    private fun foodStatusColor(party: PartyState): String {
        val food = party.resources.food.coerceAtLeast(0)
        if (food == 0) return AnsiCodes.BOLD + AnsiCodes.FG_RED
        val livingMembers = party.members.count { member -> !member.dead }.coerceAtLeast(1)
        if (food < livingMembers * 3) return AnsiCodes.FG_YELLOW
        return AnsiCodes.FG_YELLOW
    }

    private fun HitPoints.currentAnsiColor(): String {
        val max = max.coerceAtLeast(1)
        return when {
            current * 4 <= max -> AnsiCodes.FG_RED
            current * 2 <= max -> AnsiCodes.FG_MAGENTA
            else -> AnsiCodes.FG_YELLOW
        }
    }

    private fun renderMap(
        center: GridPoint,
        world: WorldMap,
    ) {
        activeMapImage = null
        clearRegion(mapWindow)
        val viewport = world.viewport(center, rows = WILDERNESS_MAP_TILES, columns = WILDERNESS_MAP_TILES)
        log.event(
            "screen.render_map center=${center.x},${center.y} rows=${viewport.size} " +
                "columns=${viewport.firstOrNull()?.size ?: 0} " +
                "region=${mapWindow.width}x${mapWindow.height} rowStride=$WILDERNESS_MAP_ROW_STRIDE",
        )
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
        fullScreenTextFirstPage(lines).forEachIndexed { index, line ->
            terminal.writeRaw("${AnsiCodes.CSI}${index + 1};1H")
            terminal.writeRaw(line.take(FULL_SCREEN_WIDTH))
        }
        terminal.flush()
    }

    private fun writePagedFullScreenText(
        lines: List<String>,
        advance: () -> Char?,
    ) {
        val pages = fullScreenTextPages(lines)
        for ((index, page) in pages.withIndex()) {
            val lastPage = index == pages.lastIndex
            val prompt = documentPrompt(if (lastPage) "to return" else "for more")
            log.event("screen.more page=${index + 1}/${pages.size}")
            writeFullScreenText(page + prompt)
            when (readDocumentAdvance(advance)) {
                null,
                RuntimeInputTokens.ESCAPE_COMMAND,
                -> return
            }
        }
    }

    private fun renderAnsiAt(
        resource: String,
        x: Int,
        y: Int,
    ) {
        runtimeResourceText(resource)
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
        terminal.writeRaw("${AnsiCodes.FG_WHITE}${AnsiCodes.BG_BLACK}")
        repeat(height) { row ->
            goto(region, rowOffset = row)
            terminal.writeRaw(" ".repeat(width))
        }
        terminal.writeRaw("${AnsiCodes.FG_WHITE}${AnsiCodes.BG_BLACK}")
    }

    private fun gotoTextRow(row: Int) {
        goto(textWindow, rowOffset = row)
    }

    private fun gotoMapRow(row: Int) {
        terminal.writeRaw(mapWindowCursorSequence(mapWindow, row))
    }

    private fun goto(
        region: ExternalScreenRegion,
        rowOffset: Int = 0,
    ) {
        terminal.writeRaw("${AnsiCodes.CSI}${region.y + rowOffset};1H\r${AnsiCodes.CSI}${region.x}C")
    }

    private fun timeLabel(hour: Int): String {
        val normalizedHour = hour.mod(24)
        val label =
            systems.realm.timeOfDay
                .firstOrNull { band -> normalizedHour in band.firstHour..band.lastHour }
                ?.label
                ?: normalizedHour.toString().padStart(2, '0')
        return "TIME: ${AnsiCodes.FG_YELLOW}${label.padEnd(9)}${AnsiCodes.FG_GREEN}"
    }
}

/** Compiled-runtime view commands normalized away from DSL/runtime classes. */
sealed interface RuntimeViewCommand {
    data object ClearText : RuntimeViewCommand

    data object ClearMapWindow : RuntimeViewCommand

    data object RenderMap : RuntimeViewCommand

    data object StatusLine : RuntimeViewCommand

    data class ShowImage(
        val imageSlug: String,
    ) : RuntimeViewCommand
}

/** One rendered map cell in a runtime viewport. */
data class RuntimeMapCell(
    val point: GridPoint,
    val terrain: rylween.compiled.CompiledTerrain?,
    val center: Boolean,
)

fun WorldMap.viewport(
    center: GridPoint,
    rows: Int,
    columns: Int,
): List<List<RuntimeMapCell>> {
    val rowRadius = rows / 2
    val columnRadius = columns / 2
    val minY = max(1, center.y - rowRadius)
    val maxY = min(height, minY + rows - 1)
    val minX = max(0, center.x - columnRadius)
    val maxX = min(width - 1, minX + columns - 1)
    return (minY..maxY).map { y ->
        (minX..maxX).map { x ->
            val point = GridPoint(x, y)
            RuntimeMapCell(point, terrainAt(point), point == center)
        }
    }
}

internal fun ExternalTerminalTerrainStyle.ansiGlyph(): String = "${AnsiCodes.foreground(color)}$glyph${AnsiCodes.RESET}"

internal fun mapWindowCursorRow(
    region: ExternalScreenRegion,
    row: Int,
): Int = region.y + row * WILDERNESS_MAP_ROW_STRIDE

internal fun mapWindowCursorSequence(
    region: ExternalScreenRegion,
    row: Int,
): String = "${AnsiCodes.CSI}${mapWindowCursorRow(region, row)};1H\r${AnsiCodes.CSI}${region.x + 1}C"

internal fun fullScreenTextFirstPage(lines: List<String>): List<String> = lines.take(FULL_SCREEN_HEIGHT)

internal fun fullScreenTextPages(lines: List<String>): List<List<String>> =
    lines
        .ifEmpty { listOf("") }
        .chunked(FULL_SCREEN_PAGE_LINES)

internal fun documentPrompt(action: String): String = "(Press ENTER $action) (ESC to exit)"

internal fun readDocumentAdvance(advance: () -> Char?): Char? {
    while (true) {
        when (val command = advance()) {
            null,
            '\n',
            RuntimeInputTokens.ESCAPE_COMMAND,
            -> return command
        }
    }
}

internal fun String.readableSlug(): String =
    split('_', '-', '.')
        .filter(String::isNotEmpty)
        .joinToString(" ") { part -> part.replaceFirstChar(Char::uppercaseChar) }

internal fun String.readableJob(): String = readableSlug().replaceFirstChar(Char::lowercaseChar)

internal fun rylween.compiled.CompiledContentBundle.slug(ref: CompiledHandleRef): String = strings.value(handles.handle(ref).slugString)
