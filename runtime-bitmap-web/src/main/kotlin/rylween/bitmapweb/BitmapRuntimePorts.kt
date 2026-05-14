package rylween.bitmapweb

import rylween.compiled.CompiledContentBundle
import rylween.compiled.CompiledGameBundle
import rylween.compiled.CompiledHandleRef
import rylween.compiled.CompiledTerrain
import rylween.compiled.ExternalRuntimeCastle
import rylween.compiled.ExternalRuntimeCommand
import rylween.compiled.ExternalRuntimeSystems
import rylween.compiled.ExternalStaticScreen
import rylween.dsl.impl.GridPoint
import rylween.dsl.impl.TerrainSettlement
import rylween.runtime.PausePrompts
import rylween.runtime.RuntimeCharacterPortraitPreview
import rylween.runtime.RuntimeInputPort
import rylween.runtime.RuntimeInputTokens
import rylween.runtime.RuntimeMapCell
import rylween.runtime.RuntimePartyPageView
import rylween.runtime.RuntimeScreenPort
import rylween.runtime.RuntimeState
import rylween.runtime.RuntimeViewCommand
import rylween.runtime.WorldMap
import rylween.runtime.runtimeScreenSlug
import rylween.runtime.runtimeStaticScreenText
import rylween.runtime.runtimeStatusLabel
import rylween.runtime.runtimeStatusLines
import rylween.runtime.runtimeWildernessContextLines
import rylween.runtime.viewport
import rylween.webcommon.WebSocketConnection
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

private const val BITMAP_TEXT_COLUMNS = 36
private const val BITMAP_DOCUMENT_COLUMNS = 88
private const val BITMAP_DOCUMENT_PAGE_LINES = 22
private const val CLOSED_INPUT = "\u0004"
private const val WATER_NORTH = 1
private const val WATER_EAST = 2
private const val WATER_SOUTH = 4
private const val WATER_WEST = 8

internal const val DEFAULT_BITMAP_VIEWPORT_TILES = 8
internal const val DEFAULT_BITMAP_TILE_SIZE = 64

internal class BitmapRuntimeInput : RuntimeInputPort {
    private val input = LinkedBlockingQueue<String>()
    private val closed = AtomicBoolean(false)
    var textInputChanged: ((BitmapTextInput?) -> Unit)? = null
    var logEvent: ((String) -> Unit)? = null

    override val exhausted: Boolean get() = closed.get() && input.isEmpty()

    override fun pause(ms: Int) {
        if (ms > 0) Thread.sleep(ms.toLong())
    }

    override fun readCommand(): Char? {
        while (true) {
            val token = input.take()
            if (token == CLOSED_INPUT) return null
            RuntimeInputTokens.commandChar(token)?.let { command ->
                logEvent?.invoke("input.command key=$command transport=bitmap")
                return command
            }
        }
    }

    override fun readInt(maxDigits: Int): Int? =
        readText(maxDigits)?.trim()?.toIntOrNull().also { value ->
            logEvent?.invoke("input.int value=${value ?: "null"} transport=bitmap")
        }

    override fun readText(maxLength: Int?): String? {
        val limit = (maxLength ?: 80).coerceAtLeast(0)
        val text = StringBuilder()
        logEvent?.invoke("input.text.start maxLength=$limit transport=bitmap")
        publishTextInput(text.toString(), limit)
        while (true) {
            when (val token = input.take()) {
                CLOSED_INPUT -> return closeTextInput(null, "closed")
                "ENTER", "RETURN" -> return closeTextInput(text.toString(), "submit")
                "ESCAPE" -> return closeTextInput(null, "escape")
                "BACKSPACE" -> {
                    if (text.isNotEmpty()) text.deleteAt(text.lastIndex)
                    publishTextInput(text.toString(), limit)
                }
                else -> {
                    val char = token.singleOrNull()?.takeIf { value -> !value.isISOControl() }
                    if (char != null && text.length < limit) text.append(char)
                    publishTextInput(text.toString(), limit)
                }
            }
        }
    }

    fun acceptClientMessage(message: String) {
        if (closed.get()) return
        input.offer(message)
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        input.offer(CLOSED_INPUT)
    }

    private fun publishTextInput(
        value: String,
        maxLength: Int,
    ) {
        textInputChanged?.invoke(BitmapTextInput(value, maxLength))
    }

    private fun closeTextInput(
        value: String?,
        reason: String,
    ): String? {
        logEvent?.invoke("input.text.end reason=$reason value=${logValue(value.orEmpty())} length=${value?.length ?: 0} transport=bitmap")
        textInputChanged?.invoke(null)
        return value
    }
}

private fun logValue(value: String): String = "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

internal class BitmapRuntimeScreen(
    private val bundle: CompiledGameBundle,
    private val systems: ExternalRuntimeSystems,
    private val connection: WebSocketConnection,
    mapTiles: Int = DEFAULT_BITMAP_VIEWPORT_TILES,
    tileSize: Int = DEFAULT_BITMAP_TILE_SIZE,
) : RuntimeScreenPort {
    private val content = bundle.content
    private val staticScreenById = systems.presentation.staticScreens.associateBy { screen -> screen.id }
    private val imageTransitionById = systems.presentation.images.associate { image -> image.id to image.transition }
    private val pausePrompts = PausePrompts(systems.prompts)
    private val viewportTiles = mapTiles.coerceIn(5, 12)
    private val mapTileSize = tileSize.coerceIn(40, 96)
    private var mode = "boot"
    private var backdrop = "wilderness"
    private var title = content.strings.value(content.universe.nameString)
    private var subtitle = content.strings.value(content.universe.versionString)
    private var status: BitmapStatus? = null
    private var statusLines = emptyList<String>()
    private var textLines = emptyList<String>()
    private var textInput: BitmapTextInput? = null
    private var portrait: BitmapPortrait? = null
    private var partyPage: BitmapPartyPage? = null
    private var visualTransition = "None"
    private var commands = emptyList<BitmapCommandHint>()
    private var map: BitmapMap? = null
    private var message: String? = null
    private var stickyMessageLines = emptyList<String>()

    override val textColumns: Int get() = BITMAP_TEXT_COLUMNS

    override fun bootFrame() {
        mode = "boot"
        backdrop = "wilderness"
        status = null
        textLines = emptyList()
        textInput = null
        portrait = null
        partyPage = null
        map = null
        commands = emptyList()
        message = null
        stickyMessageLines = emptyList()
        send()
    }

    override fun showStaticScreen(
        screenSlug: String,
        runtime: RuntimeState,
    ) {
        val screen = staticScreen(screenSlug)
        val lines =
            runtimeStaticScreenText(content, systems, screen, runtime)
                .flatMap { line -> line.wrap(BITMAP_DOCUMENT_COLUMNS) }
                .take(BITMAP_DOCUMENT_PAGE_LINES + 2)
        showStaticScreenFrame(screenSlug, runtime, lines, subtitleText = runtime.party.name)
    }

    override fun showPagedStaticScreen(
        screenSlug: String,
        runtime: RuntimeState,
        advance: () -> Char?,
    ) {
        val screen = staticScreen(screenSlug)
        val pages = bitmapDocumentPages(runtimeStaticScreenText(content, systems, screen, runtime))
        for ((index, page) in pages.withIndex()) {
            val lastPage = index == pages.lastIndex
            val prompt = pausePrompts.line(if (lastPage) "to return" else "for more")
            showStaticScreenFrame(screenSlug, runtime, page + "" + prompt, "Page ${index + 1}/${pages.size}")
            if (advance() == null) return
        }
    }

    override fun showTitleMenu(lines: List<String>) {
        mode = "title_menu"
        backdrop = "title_menu"
        title = content.strings.value(content.universe.nameString)
        subtitle = ""
        status = null
        statusLines = emptyList()
        textLines = lines
        textInput = null
        portrait = null
        partyPage = null
        commands = emptyList()
        map = null
        message = null
        stickyMessageLines = emptyList()
        send()
    }

    override fun renderWilderness(
        runtime: RuntimeState,
        world: WorldMap,
    ) {
        mode = "wilderness"
        backdrop = "wilderness"
        title = "Wilderness"
        subtitle = ""
        status = runtime.bitmapStatus()
        statusLines = runtime.runtimeStatusLines(content, systems)
        textLines = stickyMessageLines.takeIf(List<String>::isNotEmpty) ?: wildernessTextLines(runtime, world)
        textInput = null
        portrait = null
        partyPage = null
        commands = systems.bitmapCommands("wilderness")
        map = world.bitmapViewport(runtime.party.position, content, viewportTiles, mapTileSize)
        message = null
        send()
    }

    override fun showDebugWorldMap(
        runtime: RuntimeState,
        world: WorldMap,
        advance: () -> Char?,
    ) {
        mode = "debug_map"
        title = "Map"
        subtitle = "${runtime.party.position.x}, ${runtime.party.position.y}"
        status = runtime.bitmapStatus()
        textLines = world.paddedRows().take(18)
        textInput = null
        portrait = null
        partyPage = null
        map = null
        commands = emptyList()
        message = null
        stickyMessageLines = emptyList()
        send()
        advance()
    }

    override fun renderDungeon(runtime: RuntimeState) {
        mode = "dungeon"
        backdrop = systems.dungeon.backdrop ?: "dungeon"
        title = "Dungeon"
        subtitle = "Depth ${runtime.dungeon?.depth ?: 1}"
        status = runtime.bitmapStatus()
        statusLines = runtime.runtimeStatusLines(content, systems)
        textInput = null
        portrait = null
        partyPage = null
        commands = emptyList()
        map = null
        message = null
        stickyMessageLines = emptyList()
        send()
    }

    override fun renderCastle(
        runtime: RuntimeState,
        castle: ExternalRuntimeCastle,
    ) {
        mode = "castle"
        backdrop = castle.image ?: "castle"
        title = castle.id.readableSlug()
        subtitle = "Floor ${runtime.castle?.floor ?: 1}"
        status = runtime.bitmapStatus()
        statusLines = runtime.runtimeStatusLines(content, systems)
        textInput = null
        portrait = null
        partyPage = null
        commands = emptyList()
        map = null
        message = null
        stickyMessageLines = emptyList()
        send()
    }

    override fun writeText(lines: List<String>) {
        stickyMessageLines = emptyList()
        textLines = lines.flatMap { line -> line.wrap(BITMAP_TEXT_COLUMNS) }
        textInput = null
        partyPage = null
        commands = emptyList()
        message = null
        send()
    }

    override fun showMessage(lines: List<String>) {
        stickyMessageLines = lines.flatMap { line -> line.wrap(BITMAP_TEXT_COLUMNS) }
        textLines = stickyMessageLines
        textInput = null
        partyPage = null
        message = null
        send()
    }

    override fun updateStatus(runtime: RuntimeState) {
        status = runtime.bitmapStatus()
        statusLines = runtime.runtimeStatusLines(content, systems)
        send()
    }

    override fun writeViewCommand(command: RuntimeViewCommand) {
        when (command) {
            RuntimeViewCommand.ClearText -> {
                textLines = emptyList()
                message = null
                stickyMessageLines = emptyList()
            }
            RuntimeViewCommand.ClearMapWindow -> map = null
            RuntimeViewCommand.RenderMap -> Unit
            RuntimeViewCommand.StatusLine -> Unit
            is RuntimeViewCommand.ShowImage -> showImage(command.imageSlug)
        }
        if (command !is RuntimeViewCommand.ShowImage) send()
    }

    override fun showImage(imageSlug: String) {
        backdrop = imageSlug
        if (mode == "wilderness" || imageSlug.hasSceneTitle()) {
            mode = "scene"
            title = displayTitle(imageSlug)
            subtitle = ""
        }
        map = null
        textInput = null
        portrait = null
        partyPage = null
        visualTransition = imageTransitionById[imageSlug] ?: "None"
        commands = emptyList()
        message = null
        stickyMessageLines = emptyList()
        send()
    }

    override fun redrawCurrentView(lines: List<String>) {
        writeText(lines)
    }

    override fun flashLogLine(line: String) = Unit

    override fun showCharacterPortrait(preview: RuntimeCharacterPortraitPreview?) {
        portrait = preview?.toBitmapPortrait()
        partyPage = null
        if (preview != null) {
            mode = "character_portrait"
            backdrop = "character_portrait"
            map = null
            visualTransition = imageTransitionById[preview.imageSlug] ?: "None"
        }
        send()
    }

    override fun showPartyPage(
        page: RuntimePartyPageView,
        pauseLine: String,
    ) {
        mode = "party_page"
        backdrop = "party_page"
        title = page.member?.name ?: page.summary?.partyName ?: page.kind.name.readableSlug()
        subtitle = page.member?.let { member -> "${member.gender} ${member.race} ${member.pcClass}" }.orEmpty()
        textLines = page.lines
        textInput = null
        partyPage = page.toBitmapPartyPage(pauseLine)
        commands = emptyList()
        map = null
        message = null
        stickyMessageLines = emptyList()
        send()
    }

    fun setTextInput(input: BitmapTextInput?) {
        textInput = input
        if (input != null) commands = emptyList()
        send()
    }

    private fun wildernessTextLines(
        runtime: RuntimeState,
        world: WorldMap,
    ): List<String> =
        (
            runtimeWildernessContextLines(content, systems, runtime, world)
        ).flatMap { line -> line.wrap(BITMAP_TEXT_COLUMNS) }

    private fun send() {
        connection.sendText(
            BitmapFrame(
                mode = mode,
                backdrop = backdrop,
                title = title,
                subtitle = subtitle,
                status = status,
                statusLines = statusLines,
                textLines = textLines,
                textInput = textInput,
                portrait = portrait,
                partyPage = partyPage,
                visualTransition = visualTransition,
                commands = commands,
                map = map,
                message = message,
            ).toJson(),
        )
        visualTransition = "None"
    }

    private fun staticScreen(screenSlug: String): ExternalStaticScreen =
        staticScreenById[runtimeScreenSlug(screenSlug)] ?: error("unknown static screen $screenSlug")

    private fun showStaticScreenFrame(
        screenSlug: String,
        runtime: RuntimeState,
        lines: List<String>,
        subtitleText: String,
    ) {
        val document = lines.isNotEmpty()
        mode = if (document) "static_document" else "static"
        backdrop = runtimeScreenSlug(screenSlug)
        title = staticScreenTitle(screenSlug)
        subtitle = subtitleText
        status = runtime.bitmapStatus().takeUnless { document }
        statusLines = runtime.runtimeStatusLines(content, systems).takeUnless { document }.orEmpty()
        textLines = lines
        textInput = null
        portrait = null
        partyPage = null
        map = null
        commands = emptyList()
        message = null
        stickyMessageLines = emptyList()
        send()
    }

    private fun RuntimeState.bitmapStatus(): BitmapStatus =
        BitmapStatus(
            time = timeLabel(hour),
            condition = runtimeStatusLabel(systems),
            x = party.position.x,
            y = party.position.y,
            food = party.resources.food.coerceAtLeast(0),
            moves = party.resources.timeLeft.coerceAtLeast(0),
            gold = party.resources.gold.coerceAtLeast(0),
            members =
                party.members.take(4).mapIndexed { index, member ->
                    BitmapMemberStatus(
                        slot = index + 1,
                        name = content.strings.value(member.nameString),
                        hp = member.hitPoints.current.coerceAtLeast(0),
                        hpMax = member.hitPoints.max.coerceAtLeast(1),
                        mana =
                            member.mana.current
                                .takeIf { value -> member.mana.max > 0 || value > 0 },
                        manaMax =
                            member.mana.max
                                .takeIf { value -> value > 0 || member.mana.current > 0 },
                        dead = member.dead,
                    )
                },
        )

    private fun timeLabel(hour: Int): String {
        val normalizedHour = hour.mod(24)
        return systems.realm.timeOfDay
            .firstOrNull { band -> normalizedHour in band.firstHour..band.lastHour }
            ?.label
            ?: "${normalizedHour.toString().padStart(2, '0')}:00"
    }
}

private fun WorldMap.bitmapViewport(
    center: GridPoint,
    content: CompiledContentBundle,
    tiles: Int,
    tileSize: Int,
): BitmapMap =
    BitmapMap(
        tileSize = tileSize,
        rows =
            viewport(center, tiles, tiles).map { row ->
                row.map { cell -> cell.toBitmapTile(this, content) }
            },
    )

private fun RuntimeMapCell.toBitmapTile(
    world: WorldMap,
    content: CompiledContentBundle,
): BitmapTile {
    val slug = terrain?.let { content.slug(it.id) } ?: "void"
    val waterMask =
        terrain
            ?.takeIf { terrain -> terrain.isBitmapWater(content) }
            ?.let { waterMask(world, point, content) }
            ?: 0
    return BitmapTile(
        slug = slug,
        code = terrain?.mapCode?.toString().orEmpty(),
        x = point.x,
        y = point.y,
        variant = stableVariant(point, terrain?.mapCode?.toString().orEmpty(), terrain?.settlement?.runtimeName()),
        passable = terrain?.passable ?: false,
        settlement = terrain?.settlement?.runtimeName(),
        center = center,
        waterMask = waterMask,
        waterGlyph = waterMask.takeIf { terrain?.isBitmapWater(content) == true }?.waterGlyph(),
    )
}

private fun waterMask(
    world: WorldMap,
    point: GridPoint,
    content: CompiledContentBundle,
): Int {
    var mask = 0
    if (world.terrainAt(GridPoint(point.x, point.y - 1)).isBitmapWater(content)) mask = mask or WATER_NORTH
    if (world.terrainAt(GridPoint(point.x + 1, point.y)).isBitmapWater(content)) mask = mask or WATER_EAST
    if (world.terrainAt(GridPoint(point.x, point.y + 1)).isBitmapWater(content)) mask = mask or WATER_SOUTH
    if (world.terrainAt(GridPoint(point.x - 1, point.y)).isBitmapWater(content)) mask = mask or WATER_WEST
    return mask
}

private fun CompiledTerrain?.isBitmapWater(content: CompiledContentBundle): Boolean =
    this
        ?.let { terrain -> content.slug(terrain.id).lowercase() }
        ?.let { slug -> "water" in slug || "river" in slug || "sea" in slug }
        ?: false

private fun Int.waterGlyph(): String =
    when (this) {
        0 -> "•"
        WATER_NORTH -> "╵"
        WATER_EAST -> "╶"
        WATER_SOUTH -> "╷"
        WATER_WEST -> "╴"
        WATER_NORTH or WATER_SOUTH -> "│"
        WATER_EAST or WATER_WEST -> "─"
        WATER_NORTH or WATER_EAST -> "└"
        WATER_EAST or WATER_SOUTH -> "┌"
        WATER_SOUTH or WATER_WEST -> "┐"
        WATER_WEST or WATER_NORTH -> "┘"
        WATER_NORTH or WATER_EAST or WATER_SOUTH -> "├"
        WATER_EAST or WATER_SOUTH or WATER_WEST -> "┬"
        WATER_SOUTH or WATER_WEST or WATER_NORTH -> "┤"
        WATER_WEST or WATER_NORTH or WATER_EAST -> "┴"
        WATER_NORTH or WATER_EAST or WATER_SOUTH or WATER_WEST -> "┼"
        else -> "•"
    }

private fun stableVariant(
    point: GridPoint,
    code: String,
    settlement: String?,
): Int {
    var hash = point.x * 73_856_093 xor point.y * 19_349_663
    code.forEach { char -> hash = hash * 31 + char.code }
    settlement.orEmpty().forEach { char -> hash = hash * 31 + char.code }
    return hash.floorMod(4)
}

private fun Int.floorMod(modulus: Int): Int = ((this % modulus) + modulus) % modulus

private fun ExternalRuntimeSystems.bitmapCommands(group: String): List<BitmapCommandHint> =
    commands[group].orEmpty().map(ExternalRuntimeCommand::toBitmapCommand)

private fun bitmapDocumentPages(lines: List<String>): List<List<String>> =
    lines
        .flatMap { line -> line.wrap(BITMAP_DOCUMENT_COLUMNS) }
        .ifEmpty { listOf("") }
        .chunked(BITMAP_DOCUMENT_PAGE_LINES)

private fun staticScreenTitle(screenSlug: String): String =
    when (runtimeScreenSlug(screenSlug)) {
        "manual" -> "Documentation"
        "score" -> "Party Scores"
        "verbose_score" -> "Detailed Scores"
        "heroes" -> "Hall of Heroes"
        else -> screenSlug.readableSlug()
    }

private fun RuntimeCharacterPortraitPreview.toBitmapPortrait(): BitmapPortrait =
    BitmapPortrait(
        imageSlug = imageSlug,
        memberNumber = memberNumber,
        memberName = memberName,
        gender = genderLabel,
        race = raceLabel,
        pcClass = classLabel,
    )

private fun RuntimePartyPageView.toBitmapPartyPage(pauseLine: String): BitmapPartyPage =
    BitmapPartyPage(
        kind = kind.name.lowercase(),
        lines = lines,
        pauseLine = pauseLine,
        summary =
            summary?.let { summary ->
                BitmapPartySummary(
                    partyName = summary.partyName,
                    gold = summary.gold,
                    food = summary.food,
                    torches = summary.torches,
                    moves = summary.moves,
                    rest = summary.rest,
                    kills = summary.kills,
                    memberCount = summary.memberCount,
                )
            },
        member =
            member?.let { member ->
                BitmapPartyMember(
                    index = member.index,
                    name = member.name,
                    gender = member.gender,
                    race = member.race,
                    pcClass = member.pcClass,
                    weaponSkill = member.weaponSkill,
                    health = member.health,
                    wisdom = member.wisdom,
                    charisma = member.charisma,
                    hitPoints = member.hitPoints,
                    hitPointMax = member.hitPointMax,
                    mana = member.mana,
                    manaMax = member.manaMax,
                    level = member.level,
                    experience = member.experience,
                    weapon = member.weapon,
                    armor = member.armor,
                    status = member.status,
                )
            },
        inventory = inventory?.entries.orEmpty(),
    )

private fun ExternalRuntimeCommand.toBitmapCommand(): BitmapCommandHint =
    BitmapCommandHint(
        action = action,
        input = binding.primary,
        label = binding.primary.bitmapInputLabel(),
        caption = label ?: binding.label,
        topic = topic,
    )

private fun String.bitmapInputLabel(): String =
    takeUnless(String::isBlank)?.uppercase()?.let { token ->
        when (token) {
            "8" -> "8/↑"
            "2" -> "2/↓"
            "4" -> "4/←"
            "6" -> "6/→"
            "SPACE" -> "SPACE"
            else -> token
        }
    } ?: "SPACE"

private fun TerrainSettlement.runtimeName(): String = name.lowercase()

private fun CompiledContentBundle.slug(ref: CompiledHandleRef): String = strings.value(handles.handle(ref).slugString)

private fun displayTitle(slug: String): String =
    when (slug) {
        "blitgfx.towne_mainstreet" -> "Town Main Street"
        "blitgfx.death" -> "The Party Has Fallen"
        "blitgfx.monster_goblinoid" -> "Goblinoid Raiders"
        "blitgfx.monster_undead" -> "Restless Dead"
        "blitgfx.monster_beast" -> "Wild Beasts"
        "blitgfx.monster_flying" -> "Winged Threats"
        "blitgfx.monster_vermin" -> "Crawling Horrors"
        "blitgfx.monster_brute" -> "Brutes"
        "blitgfx.monster_humanoid_magic" -> "Hostile Travelers"
        "blitgfx.monster_boss" -> "Deadly Foe"
        "blitgfx.encounter_banner1",
        "blitgfx.encounter_banner2",
        "blitgfx.encounter_banner3",
        "blitgfx.encounter_banner4",
        "blitgfx.encounter_banner5",
        -> "Wilderness Encounter"
        else -> slug.removePrefix("blitgfx.").readableSlug()
    }

private fun String.hasSceneTitle(): Boolean = startsWith("blitgfx.monster_") || this == "blitgfx.death"

private fun String.readableSlug(): String =
    split('_', '-', '.')
        .filter(String::isNotEmpty)
        .joinToString(" ") { part -> part.replaceFirstChar(Char::uppercaseChar) }

private fun String.wrap(width: Int): List<String> {
    if (isEmpty() || width <= 0 || length <= width) return listOf(this)
    val tokens = split(' ').flatMap { word -> if (word.length > width) word.chunked(width) else listOf(word) }
    return buildList {
        var current = ""
        for (token in tokens) {
            val candidate = if (current.isEmpty()) token else "$current $token"
            if (candidate.length <= width) {
                current = candidate
            } else {
                if (current.isNotEmpty()) add(current)
                current = token
            }
        }
        add(current)
    }
}
