package rylween.bitmapweb

import rylween.compiled.CompiledContentBundle
import rylween.compiled.CompiledGameBundle
import rylween.compiled.CompiledHandleRef
import rylween.compiled.ExternalRuntimeCastle
import rylween.compiled.ExternalRuntimeCommand
import rylween.compiled.ExternalRuntimeSystems
import rylween.dsl.impl.GridPoint
import rylween.dsl.impl.TerrainSettlement
import rylween.runtime.RuntimeCharacterPortraitPreview
import rylween.runtime.RuntimeInputPort
import rylween.runtime.RuntimeInputTokens
import rylween.runtime.RuntimeMapCell
import rylween.runtime.RuntimePartyPageView
import rylween.runtime.RuntimeScreenPort
import rylween.runtime.RuntimeState
import rylween.runtime.RuntimeViewCommand
import rylween.runtime.WorldMap
import rylween.runtime.runtimeFoodStatusLabel
import rylween.runtime.runtimeStatusLines
import rylween.runtime.runtimeWildernessContextLines
import rylween.runtime.viewport
import rylween.webcommon.WebSocketConnection
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

private const val BITMAP_TEXT_COLUMNS = 36
private const val CLOSED_INPUT = "\u0004"

internal const val DEFAULT_BITMAP_VIEWPORT_TILES = 8
internal const val DEFAULT_BITMAP_TILE_SIZE = 64

internal class BitmapRuntimeInput : RuntimeInputPort {
    private val input = LinkedBlockingQueue<String>()
    private val closed = AtomicBoolean(false)
    var textInputChanged: ((BitmapTextInput?) -> Unit)? = null

    override val exhausted: Boolean get() = closed.get() && input.isEmpty()

    override fun pause(ms: Int) {
        if (ms > 0) Thread.sleep(ms.toLong())
    }

    override fun readCommand(): Char? {
        while (true) {
            val token = input.take()
            if (token == CLOSED_INPUT) return null
            RuntimeInputTokens.commandChar(token)?.let { command -> return command }
        }
    }

    override fun readInt(maxDigits: Int): Int? = readText(maxDigits)?.trim()?.toIntOrNull()

    override fun readText(maxLength: Int?): String? {
        val limit = (maxLength ?: 80).coerceAtLeast(0)
        val text = StringBuilder()
        publishTextInput(text.toString(), limit)
        while (true) {
            when (val token = input.take()) {
                CLOSED_INPUT -> return closeTextInput(null)
                "ENTER", "RETURN" -> return closeTextInput(text.toString())
                "ESCAPE" -> return closeTextInput(null)
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

    private fun closeTextInput(value: String?): String? {
        textInputChanged?.invoke(null)
        return value
    }
}

internal class BitmapRuntimeScreen(
    private val bundle: CompiledGameBundle,
    private val systems: ExternalRuntimeSystems,
    private val connection: WebSocketConnection,
    mapTiles: Int = DEFAULT_BITMAP_VIEWPORT_TILES,
    tileSize: Int = DEFAULT_BITMAP_TILE_SIZE,
) : RuntimeScreenPort {
    private val content = bundle.content
    private val imageTransitionById = systems.presentation.images.associate { image -> image.id to image.transition }
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
        send()
    }

    override fun showStaticScreen(
        screenSlug: String,
        runtime: RuntimeState,
    ) {
        mode = "static"
        backdrop = screenSlug
        title = screenSlug.readableSlug()
        subtitle = runtime.party.name
        status = runtime.bitmapStatus()
        statusLines = runtime.runtimeStatusLines(content)
        textLines = emptyList()
        textInput = null
        portrait = null
        partyPage = null
        map = null
        commands = emptyList()
        message = null
        send()
    }

    override fun showPagedStaticScreen(
        screenSlug: String,
        runtime: RuntimeState,
        advance: () -> Char?,
    ) {
        showStaticScreen(screenSlug, runtime)
        advance()
    }

    override fun showTitleMenu(lines: List<String>) {
        mode = "title_menu"
        backdrop = "title_menu"
        title = content.strings.value(content.universe.nameString)
        subtitle = ""
        status = null
        statusLines = emptyList()
        textLines = lines.flatMap { line -> line.wrap(BITMAP_TEXT_COLUMNS) }
        textInput = null
        portrait = null
        partyPage = null
        commands = emptyList()
        map = null
        message = null
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
        statusLines = runtime.runtimeStatusLines(content)
        textLines = wildernessTextLines(runtime, world)
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
        send()
        advance()
    }

    override fun renderDungeon(runtime: RuntimeState) {
        mode = "dungeon"
        backdrop = systems.dungeon.backdrop ?: "dungeon"
        title = "Dungeon"
        subtitle = "Depth ${runtime.dungeon?.depth ?: 1}"
        status = runtime.bitmapStatus()
        statusLines = runtime.runtimeStatusLines(content)
        textInput = null
        portrait = null
        partyPage = null
        commands = emptyList()
        map = null
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
        statusLines = runtime.runtimeStatusLines(content)
        textInput = null
        portrait = null
        partyPage = null
        commands = emptyList()
        map = null
        send()
    }

    override fun writeText(lines: List<String>) {
        textLines = lines.flatMap { line -> line.wrap(BITMAP_TEXT_COLUMNS) }
        textInput = null
        partyPage = null
        commands = emptyList()
        message = null
        send()
    }

    override fun showMessage(lines: List<String>) {
        message = lines.joinToString(" ").takeIf(String::isNotBlank)
        send()
    }

    override fun updateStatus(runtime: RuntimeState) {
        status = runtime.bitmapStatus()
        statusLines = runtime.runtimeStatusLines(content)
        send()
    }

    override fun writeViewCommand(command: RuntimeViewCommand) {
        when (command) {
            RuntimeViewCommand.ClearText -> textLines = emptyList()
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

    private fun RuntimeState.bitmapStatus(): BitmapStatus =
        BitmapStatus(
            time = timeLabel(hour),
            condition = runtimeFoodStatusLabel(),
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
                row.map { cell -> cell.toBitmapTile(content) }
            },
    )

private fun RuntimeMapCell.toBitmapTile(content: CompiledContentBundle): BitmapTile =
    BitmapTile(
        slug = terrain?.let { content.slug(it.id) } ?: "void",
        code = terrain?.mapCode?.toString().orEmpty(),
        x = point.x,
        y = point.y,
        variant = stableVariant(point, terrain?.mapCode?.toString().orEmpty(), terrain?.settlement?.runtimeName()),
        passable = terrain?.passable ?: false,
        settlement = terrain?.settlement?.runtimeName(),
        center = center,
    )

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
