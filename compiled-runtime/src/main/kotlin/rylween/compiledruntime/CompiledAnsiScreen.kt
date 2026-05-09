/**
 * Classic ANSI screen renderer for the standalone compiled-bundle runtime.
 *
 * The renderer consumes only compiled table rows and stable
 * `runtimeSystems.presentation` rows: image resources, screen regions, and
 * terminal terrain styles. It is the compiled-runtime counterpart to the old
 * ANSI screen code, but it remains in the `compiledRuntime` source set so the
 * product can evolve without legacy session or adapter dependencies.
 */

package rylween.compiledruntime

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
import rylween.dsl.GridPoint
import rylween.dsl.PcClass
import rylween.frontend.ansi.AnsiCodes
import kotlin.math.max
import kotlin.math.min

private const val TEXT_WINDOW = "text_window"
private const val MAP_WINDOW = "map_window"
private const val STATUSLINE_TOP = "statusline_top"
private const val STATUSLINE_BOTTOM = "statusline_bottom"
private const val WILDERNESS_MAP_TILES = 5
private const val WILDERNESS_MAP_ROW_STRIDE = 2
private const val SCORE_WIDTH = 76
private const val SCORE_REPORT = "Score"
private const val VERBOSE_SCORE_REPORT = "VerboseScore"
private const val HALL_OF_HEROES_REPORT = "HallOfHeroes"

/** ANSI screen model owned by the compiled runtime. */
internal class CompiledAnsiScreen(
    private val terminal: CompiledAnsiTerminal,
    private val bundle: CompiledGameBundle,
    private val systems: ExternalRuntimeSystems,
    private val log: StandaloneRuntimeLog? = null,
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
        log?.event("screen.boot_frame")
        terminal.writeRaw("${AnsiCodes.CSI}?25l")
        staticScreenById["frame"]?.image?.let(::renderImage)
            ?: terminal.writeRaw("${AnsiCodes.CSI}2J${AnsiCodes.CSI}1;1H")
        terminal.writeRaw("${AnsiCodes.FG_WHITE}${AnsiCodes.BG_BLACK}")
        terminal.flush()
    }

    /** Renders a compiled static screen route and returns without waiting. */
    fun showStaticScreen(
        screenSlug: String,
        runtime: StandaloneRuntimeState,
    ) {
        classicFrameActive = false
        log?.event("screen.static slug=$screenSlug")
        val screen = staticScreenById[screenSlug.screenSlug()] ?: error("unknown static screen $screenSlug")
        screen.image?.let(::renderImage)
        screen.resource?.let { resource -> writeFullScreenText(resourceText(resource).lines()) }
        screen.runtimeReport?.let { report -> writeFullScreenText(runtime.runtimeReportLines(report)) }
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
        log?.event("screen.render_wilderness position=${runtime.party.position.x},${runtime.party.position.y}")
        renderStatus(runtime)
        renderMap(runtime.party.position, world)
        writeText(wildernessContextLines(runtime, world) + "" + systems.wilderness.commandHint.render(runtime))
    }

    /** Renders the dungeon backdrop, status lines, and authored depth prompt. */
    fun renderDungeon(runtime: StandaloneRuntimeState) {
        ensureClassicFrame()
        log?.event("screen.render_dungeon depth=${runtime.dungeon?.depth ?: 1}")
        renderStatus(runtime)
        systems.dungeon.backdrop?.let(::showImage)
        val depth = runtime.dungeon?.depth ?: 1
        writeText(
            systems.dungeon.prompts.levelMenu
                .renderDungeon(depth) + systems.dungeon.commandHint.renderDungeon(depth),
        )
    }

    /** Renders the active castle backdrop, status lines, and navigation prompt. */
    fun renderCastle(
        runtime: StandaloneRuntimeState,
        castle: ExternalRuntimeCastle,
    ) {
        ensureClassicFrame()
        log?.event("screen.render_castle castle=${castle.id} floor=${runtime.castle?.floor ?: 0}")
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
                    systems.commands["castle.${castle.id}.navigation"].orEmpty().take(7).map { command ->
                        "${command.binding.primary}. ${command.label ?: command.binding.label}"
                    },
                )
            }
        writeText(lines.filter(String::isNotBlank))
    }

    /** Clears the classic text window and writes the supplied lines inside it. */
    fun writeText(lines: List<String>) {
        ensureClassicFrame()
        log?.event("screen.write_text lines=${lines.size} first=${lines.firstOrNull().orEmpty().take(40)}")
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

    private fun ExternalRuntimeTextBlock.renderDungeon(depth: Int): List<String> =
        render { kind ->
            when (kind) {
                "dungeonDepth" -> depth.toString()
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
        val viewport = world.viewport(center, rows = WILDERNESS_MAP_TILES, columns = WILDERNESS_MAP_TILES)
        log?.event(
            "screen.render_map center=${center.x},${center.y} rows=${viewport.size} " +
                "columns=${viewport.firstOrNull()?.size ?: 0} region=${mapWindow.width}x${mapWindow.height} rowStride=$WILDERNESS_MAP_ROW_STRIDE",
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
        lines.takeLast(24).forEachIndexed { index, line ->
            terminal.writeRaw("${AnsiCodes.CSI}${index + 1};1H")
            terminal.writeRaw(line.take(79))
        }
        terminal.flush()
    }

    private fun StandaloneRuntimeState.runtimeReportLines(report: String): List<String> =
        when (report) {
            SCORE_REPORT -> scoreEntries().summaryScoreLines()
            VERBOSE_SCORE_REPORT -> scoreEntries().verboseScoreLines()
            HALL_OF_HEROES_REPORT -> hallOfHeroesLines()
            else -> emptyList()
        }

    private fun StandaloneRuntimeState.scoreEntries(): List<StandaloneScoreEntry> =
        listOf(
            StandaloneScoreEntry(
                rank = 1,
                partyName = party.name,
                leaderName =
                    party.members
                        .firstOrNull()
                        ?.let { member -> content.strings.value(member.nameString) }
                        .orEmpty(),
                classes = party.members.map(CompiledPartyMember::pcClass),
                kills = party.counters.kills,
                averageExperience = party.members.averageOf(CompiledPartyMember::experience),
                averageLevel = party.members.averageOf(CompiledPartyMember::level),
                powerItems =
                    systems.score.powerItems
                        .filter { item -> party.milestones.any { milestone -> content.slug(milestone) == item.milestone } }
                        .sortedWith(compareBy({ it.sourceOrder ?: Int.MAX_VALUE }, { it.itemName }))
                        .map { item -> item.itemName },
            ),
        )

    private fun List<StandaloneScoreEntry>.summaryScoreLines(): List<String> =
        buildList {
            addAll(scoreHeaderLines("ADVENTURERS OF RYLWEEN - PARTY STATUS AND SCORES"))
            add("")
            add("        #     Party Name                    Experience       Level")
            add("")
            this@summaryScoreLines.forEach { entry ->
                add(
                    "        ${entry.rank.toString().padStart(2)}.    " +
                        "${entry.partyName.padEnd(30)}  " +
                        "${entry.averageExperience.toString().padStart(6)}          " +
                        entry.averageLevel.toString().padStart(3),
                )
            }
            add("")
        }

    private fun List<StandaloneScoreEntry>.verboseScoreLines(): List<String> =
        buildList {
            addAll(scoreHeaderLines("ADVENTURERS OF RYLWEEN - PARTY STATUS AND SCORES"))
            add("")
            this@verboseScoreLines.forEach { entry ->
                add("${entry.rank.toString().padStart(2)}. ${entry.partyName.padEnd(30)}")
                add(" +${"-".repeat(SCORE_WIDTH - 2)}+")
                add("  Leader             : ${entry.leaderName}")
                add("  Classes            : ${entry.classes.joinToString(", ") { it.scoreName() }}")
                add("  Confirmed Kills    : ${entry.kills.toString().padEnd(4)}")
                add(
                    "  Average Experience : ${entry.averageExperience.toString().padEnd(
                        6,
                    )} (level ${entry.averageLevel.toString().padEnd(3)})",
                )
                add("")
                add("  Power Items        :")
                add("")
                if (entry.powerItems.isEmpty()) add("  None.") else entry.powerItems.forEach { item -> add("  $item") }
                add(" +${"-".repeat(SCORE_WIDTH - 2)}+")
                add("")
            }
        }

    private fun StandaloneRuntimeState.hallOfHeroesLines(): List<String> =
        buildList {
            addAll(scoreHeaderLines("ADVENTURERS OF RYLWEEN - HALL OF NATIONAL HEROES"))
            add("")
            val completedBy = systems.score.completedBy
            val hasCompleted = completedBy != null && party.milestones.any { milestone -> content.slug(milestone) == completedBy }
            if (hasCompleted) {
                add("  ${systems.score.hallOfHeroesEntryText.replace("{party}", party.name)}")
            } else {
                add("  ${systems.score.hallOfHeroesEmptyText}")
            }
            add("")
        }

    private fun scoreHeaderLines(title: String): List<String> =
        listOf(
            " +${"=".repeat(SCORE_WIDTH - 2)}+",
            " I${title.centered(SCORE_WIDTH - 2)}I",
            " +${"=".repeat(SCORE_WIDTH - 2)}+",
        )

    private fun List<CompiledPartyMember>.averageOf(value: (CompiledPartyMember) -> Int): Int {
        val divisor = size.coerceAtLeast(1)
        return sumOf(value) / divisor
    }

    private fun String.centered(width: Int): String {
        if (length >= width) return take(width)
        val left = (width - length) / 2
        val right = width - length - left
        return " ".repeat(left) + this + " ".repeat(right)
    }

    private fun PcClass.scoreName(): String =
        when (this) {
            PcClass.Fighter -> "Fighter"
            PcClass.Thief -> "Thief"
            PcClass.Mage -> "Mage"
            PcClass.Priest -> "Priest"
            PcClass.Bard -> "Bard"
            PcClass.Ranger -> "Ranger"
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
        terminal.writeRaw(mapWindowCursorSequence(mapWindow, row))
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

private data class StandaloneScoreEntry(
    val rank: Int,
    val partyName: String,
    val leaderName: String,
    val classes: List<PcClass>,
    val kills: Int,
    val averageExperience: Int,
    val averageLevel: Int,
    val powerItems: List<String>,
)

internal fun StandaloneWorldMap.viewport(
    center: GridPoint,
    rows: Int,
    columns: Int,
): List<List<CompiledRuntimeMapCell>> {
    val rowRadius = rows / 2
    val columnRadius = columns / 2
    val minY = max(1, center.y - rowRadius)
    val maxY = min(height, minY + rows - 1)
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

internal fun mapWindowCursorRow(
    region: ExternalScreenRegion,
    row: Int,
): Int = region.y + row * WILDERNESS_MAP_ROW_STRIDE

internal fun mapWindowCursorSequence(
    region: ExternalScreenRegion,
    row: Int,
): String = "${AnsiCodes.CSI}${mapWindowCursorRow(region, row)};1H\r${AnsiCodes.CSI}${region.x + 1}C"

internal fun String.readableSlug(): String =
    split('_', '-', '.')
        .filter(String::isNotEmpty)
        .joinToString(" ") { part -> part.replaceFirstChar(Char::uppercaseChar) }

internal fun rylween.compiled.CompiledContentBundle.slug(ref: CompiledHandleRef): String = strings.value(handles.handle(ref).slugString)
