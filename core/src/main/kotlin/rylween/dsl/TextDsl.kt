/**
 * Text blocks, structured text parts, and text helper builders.
 *
 * Runtime-facing text is represented as typed parts rather than ad hoc string
 * replacement. This file defines the two kinds of authored text, [flowText]
 * prose and [literalText] lines, the dynamic placeholders for runtime values
 * and NPC/town references both can hold, and small builder helpers used by
 * content, scripts, prompts, and compiled text emission.
 */
package rylween.dsl

/** Stable id for authored text catalog entries. */
data class TextId(
    val id: String,
)

/** Part of a structured text line, including literals and runtime-bound placeholders. */
sealed interface TextPart {
    data class Literal(
        val text: String,
    ) : TextPart

    data class IntValue(
        val value: IntAst,
    ) : TextPart

    data class StringValue(
        val value: StrAst,
    ) : TextPart

    /**
     * A beat in script text, written `{pause}`, `{pause 1.5s}` or `{pause 600ms}`
     * inside a string. [ScriptScope.writeText] and [ScriptScope.showTextAndWait]
     * show the text up to the beat, pause, then show the page again with more of
     * it; no other text accepts one.
     */
    data class Pause(
        val millis: Int,
    ) : TextPart

    data class RoleName(
        val role: NpcRole,
    ) : TextPart

    data class RoleJob(
        val role: NpcRole,
    ) : TextPart

    data class RoleLocation(
        val role: NpcRole,
    ) : TextPart

    data class NpcName(
        val npc: NpcId,
    ) : TextPart

    data class NpcJob(
        val npc: NpcId,
    ) : TextPart

    data class NpcLocation(
        val npc: NpcId,
    ) : TextPart

    data class TownName(
        val town: TownId,
    ) : TextPart

    data class FuzzyTownLocation(
        val town: TownId,
        val radius: Int,
    ) : TextPart

    data class LocationCoordinate(
        val location: LocationId,
    ) : TextPart

    object OfferedItemName : TextPart

    object OfferedItemPrice : TextPart

    object SelectedItemName : TextPart

    object InventorySlotName : TextPart

    object EquipmentKindName : TextPart

    object ShopPrice : TextPart

    object ShopCarryCap : TextPart

    object HuntMemberName : TextPart

    object HuntMemberSubjectPronoun : TextPart

    object HuntFoodGained : TextPart

    /** Name of the wilderness-party member affected by an upkeep event such as starvation. */
    object UpkeepMemberName : TextPart

    /** Name of the party member who stands guard while the party camps. */
    object CampGuardName : TextPart

    object PartyMemberCount : TextPart

    object PartyMemberMax : TextPart

    object WildernessDungeonName : TextPart

    object WildernessDungeonLevels : TextPart

    object DungeonDepth : TextPart

    object ConversationNpcName : TextPart

    object CurrentTownName : TextPart

    object TownTaxPercent : TextPart

    object TownTaxAmount : TextPart

    object CreationMemberNumber : TextPart

    object CreationMemberName : TextPart

    object CreationWeaponSkill : TextPart

    object CreationHealth : TextPart

    object CreationWisdom : TextPart

    object CreationCharisma : TextPart

    object CreationDexterity : TextPart

    object BattleFoeDisplayName : TextPart

    object BattleAttackerName : TextPart

    object BattleTargetName : TextPart

    object BattleDamage : TextPart

    object BattleExperience : TextPart

    object BattleGold : TextPart

    object BattleLootName : TextPart

    object BattleLevelMemberName : TextPart

    object BattleLevelsGained : TextPart
}

/** Returns a deterministic placeholder used when no runtime renderer is available. */
fun TextPart.fallbackText(): String =
    when (this) {
        is TextPart.Literal -> text
        is TextPart.IntValue -> "{int}"
        is TextPart.StringValue -> "{string}"
        is TextPart.Pause -> ""
        is TextPart.RoleName -> "{${role.slug}.name}"
        is TextPart.RoleJob -> "{${role.slug}.job}"
        is TextPart.RoleLocation -> "{${role.slug}.location}"
        is TextPart.NpcName -> "{${npc.slug}.name}"
        is TextPart.NpcJob -> "{${npc.slug}.job}"
        is TextPart.NpcLocation -> "{${npc.slug}.location}"
        is TextPart.TownName -> town.slug
        is TextPart.FuzzyTownLocation -> "${town.slug}.near"
        is TextPart.LocationCoordinate -> "{${location.slug}.coordinates}"
        TextPart.OfferedItemName -> "{item_name}"
        TextPart.OfferedItemPrice -> "{item_price}"
        TextPart.SelectedItemName -> "{selected_item_name}"
        TextPart.InventorySlotName -> "{inventory_slot}"
        TextPart.EquipmentKindName -> "{equipment_kind}"
        TextPart.ShopPrice -> "{price}"
        TextPart.ShopCarryCap -> "{cap}"
        TextPart.HuntMemberName -> "{who_name}"
        TextPart.HuntMemberSubjectPronoun -> "{pro_sub}"
        TextPart.HuntFoodGained -> "{hunt_food_gained}"
        TextPart.UpkeepMemberName -> "{upkeep_member_name}"
        TextPart.CampGuardName -> "{camp_guard_name}"
        TextPart.PartyMemberCount -> "{party_count}"
        TextPart.PartyMemberMax -> "{party_max}"
        TextPart.WildernessDungeonName -> "{dungeon}"
        TextPart.WildernessDungeonLevels -> "{levels}"
        TextPart.DungeonDepth -> "{level}"
        TextPart.ConversationNpcName -> "{npc_name}"
        TextPart.CurrentTownName -> "{town}"
        TextPart.TownTaxPercent -> "{pct}"
        TextPart.TownTaxAmount -> "{amount}"
        TextPart.CreationMemberNumber -> "{char_num}"
        TextPart.CreationMemberName -> "{name}"
        TextPart.CreationWeaponSkill -> "{weapon_skill}"
        TextPart.CreationHealth -> "{health}"
        TextPart.CreationWisdom -> "{wisdom}"
        TextPart.CreationCharisma -> "{charisma}"
        TextPart.CreationDexterity -> "{dexterity}"
        TextPart.BattleFoeDisplayName -> "{battle_foe}"
        TextPart.BattleAttackerName -> "{battle_attacker}"
        TextPart.BattleTargetName -> "{battle_target}"
        TextPart.BattleDamage -> "{battle_damage}"
        TextPart.BattleExperience -> "{battle_experience}"
        TextPart.BattleGold -> "{battle_gold}"
        TextPart.BattleLootName -> "{battle_loot}"
        TextPart.BattleLevelMemberName -> "{battle_level_member}"
        TextPart.BattleLevelsGained -> "{battle_levels}"
    }

/** Structured text line made of literal and dynamic parts. */
data class TextLine(
    val parts: List<TextPart>,
)

/**
 * Authored text block. There are two kinds: [flowText] prose, which renderers
 * wrap to their own width, and [literalText] lines, which every renderer shows
 * as rows. `Inline` and `Structured` are both literal lines, without and with
 * runtime values; the names stay for compiled-bundle compatibility.
 */
sealed interface TextBlock {
    data class Inline(
        val lines: List<String>,
    ) : TextBlock

    data class Structured(
        val lines: List<TextLine>,
    ) : TextBlock

    data class Flow(
        val paragraphs: List<TextLine>,
    ) : TextBlock
}

/**
 * Creates literal text: each line starts a row of its own, and a line longer
 * than the window is wrapped there. Use it for layout, such as menus, prompts,
 * tables, signs and verse, with each sentence on one line rather than broken
 * by hand; prose belongs in [flowText].
 */
fun literalText(vararg lines: String): TextBlock {
    val rows = expandMultiline(lines)
    return if (rows.none(pauseMarker::containsMatchIn)) {
        TextBlock.Inline(rows)
    } else {
        TextBlock.Structured(rows.map { row -> TextLine(literalParts(row)) })
    }
}

/** Creates literal text whose lines mix fixed text with runtime values, one [line] per row. */
fun literalText(body: StructuredTextBlockBuilder.() -> Unit): TextBlock = StructuredTextBlockBuilder().apply(body).build()

/**
 * Creates flow text: prose the renderer wraps to its own column or pixel
 * width. Single newlines in the source are spaces; a blank line starts a new
 * paragraph, which renderers show with a blank line above it.
 */
fun flowText(text: String): TextBlock = TextBlock.Flow(flowParagraphs(text))

/** Creates a single deferred-wrap paragraph from literal and dynamic parts. */
fun flowText(vararg parts: Any): TextBlock = TextBlock.Flow(listOf(TextLine(parts.flatMap(::textParts))))

/** Creates deferred-wrap text with multiple paragraphs and optional dynamic parts. */
fun flowText(body: FlowTextBlockBuilder.() -> Unit): TextBlock = FlowTextBlockBuilder().apply(body).build()

/**
 * Detection rule for triple-quoted multi-line authoring: if the caller
 * passed exactly one string, and it contains a newline, treat it as
 * `trimIndent().split("\n")`; otherwise behave like a normal vararg. This
 * keeps `literalText("a", "b")` and `literalText("simple sentence.")` unchanged.
 */
private fun expandMultiline(lines: Array<out String>): List<String> =
    if (lines.size == 1 && '\n' in lines[0]) {
        lines[0].trimIndent().split('\n')
    } else {
        lines.toList()
    }

/** Builder sink for collecting structured text lines. */
interface TextLineSink {
    fun addTextLine(line: TextLine)
}

/** Adds one structured text line, coercing strings and expression wrappers into [TextPart] values. */
fun TextLineSink.line(vararg parts: Any) {
    addTextLine(TextLine(parts.flatMap(::textParts)))
}

/** Adds multiple literal lines to a text-line sink. */
fun TextLineSink.lines(vararg lines: String) {
    expandMultiline(lines).forEach { line(it) }
}

/** Executes a nested structured-text builder block against this sink. */
fun <T : TextLineSink> T.lines(body: T.() -> Unit) {
    body()
}

/** Concatenates text blocks into one structured block. */
fun combineTextBlocks(vararg blocks: TextBlock): TextBlock = combineTextBlocks(blocks.toList())

/** Concatenates text blocks into one structured block. */
fun combineTextBlocks(blocks: List<TextBlock>): TextBlock =
    TextBlock.Structured(
        blocks.flatMap { block -> block.asStructuredLines() },
    )

private fun TextBlock.asStructuredLines(): List<TextLine> =
    when (this) {
        is TextBlock.Inline -> lines.map { line -> TextLine(listOf(TextPart.Literal(line))) }
        is TextBlock.Structured -> lines
        is TextBlock.Flow ->
            paragraphs.flatMapIndexed { index, line ->
                if (index < paragraphs.lastIndex) listOf(line, TextLine(listOf(TextPart.Literal("")))) else listOf(line)
            }
    }

private fun textParts(part: Any): List<TextPart> =
    when (part) {
        is TextPart -> listOf(part)
        is IntExpr -> listOf(TextPart.IntValue(part.ast))
        is StrExpr -> listOf(TextPart.StringValue(part.ast))
        is String -> literalParts(part)
        else -> listOf(TextPart.Literal(part.toString()))
    }

private const val DEFAULT_PAUSE_MILLIS = 1_500
private val pauseMarker = Regex("""\{pause(?:\s+(\d+(?:\.\d+)?)\s*(ms|s))?\}""")

/** Splits literal text at its `{pause}` markers; text without one stays a single literal. */
private fun literalParts(text: String): List<TextPart> {
    if (!pauseMarker.containsMatchIn(text)) return listOf(TextPart.Literal(text))
    val parts = mutableListOf<TextPart>()
    var rest = 0
    pauseMarker.findAll(text).forEach { marker ->
        text.substring(rest, marker.range.first).takeIf(String::isNotEmpty)?.let { before -> parts += TextPart.Literal(before) }
        parts += TextPart.Pause(marker.pauseMillis())
        rest = marker.range.last + 1
    }
    text.substring(rest).takeIf(String::isNotEmpty)?.let { after -> parts += TextPart.Literal(after) }
    return parts
}

private fun MatchResult.pauseMillis(): Int {
    val amount = groupValues[1].takeIf(String::isNotEmpty)?.toDouble() ?: return DEFAULT_PAUSE_MILLIS
    return if (groupValues[2] == "s") (amount * 1_000).toInt() else amount.toInt()
}

/** One step of text shown with `{pause}` beats: the text so far, then the pause after it; the last step has none. */
internal data class TextBeat(
    val text: TextBlock,
    val pauseMillis: Int?,
)

/**
 * The steps [ScriptScope.writeText] shows this text in: one per `{pause}`
 * marker, each with everything up to that marker, and a last one with the
 * whole text. Text without markers is a single step.
 */
internal fun TextBlock.beats(): List<TextBeat> {
    val rows =
        when (this) {
            is TextBlock.Inline -> return listOf(TextBeat(this, null))
            is TextBlock.Structured -> lines
            is TextBlock.Flow -> paragraphs
        }
    if (rows.none { row -> row.parts.any { part -> part is TextPart.Pause } }) return listOf(TextBeat(this, null))
    val beats = mutableListOf<TextBeat>()
    rows.forEachIndexed { rowIndex, row ->
        row.parts.forEachIndexed { partIndex, part ->
            if (part is TextPart.Pause) {
                val partial = TextLine(row.parts.take(partIndex)).withoutPauses()
                val shown = rows.take(rowIndex).map(TextLine::withoutPauses) + listOfNotNull(partial.takeIf { it.parts.isNotEmpty() })
                beats += TextBeat(withRows(shown), part.millis)
            }
        }
    }
    return beats + TextBeat(withRows(rows.map(TextLine::withoutPauses)), null)
}

/**
 * The line as shown once its beats are over. Where a marker stood between two
 * words, the spaces around it become one, so "but... {pause 2s} dust..." reads
 * "but... dust..."; a line that ends at a beat drops the space before it.
 */
private fun TextLine.withoutPauses(): TextLine {
    val joined = mutableListOf<TextPart>()
    var afterPause = false
    parts.forEach { part ->
        val previous = joined.lastOrNull() as? TextPart.Literal
        when {
            part is TextPart.Pause -> afterPause = true
            afterPause && previous != null && part is TextPart.Literal -> {
                val spaced = previous.text.endsWith(' ') || part.text.startsWith(' ')
                joined[joined.lastIndex] = TextPart.Literal(previous.text.trimEnd() + (if (spaced) " " else "") + part.text.trimStart())
                afterPause = false
            }
            else -> {
                joined += part
                afterPause = false
            }
        }
    }
    val last = joined.lastOrNull() as? TextPart.Literal
    if (last != null) joined[joined.lastIndex] = TextPart.Literal(last.text.trimEnd())
    return TextLine(joined)
}

private fun TextBlock.withRows(rows: List<TextLine>): TextBlock =
    when (this) {
        is TextBlock.Inline -> this
        is TextBlock.Structured -> TextBlock.Structured(rows)
        is TextBlock.Flow -> TextBlock.Flow(rows)
    }

@RylNextDsl
class StructuredTextBlockBuilder : TextLineSink {
    private val lines = mutableListOf<TextLine>()

    override fun addTextLine(line: TextLine) {
        lines += line
    }

    fun build(): TextBlock = TextBlock.Structured(lines.toList())
}

/** Builder for deferred-wrap prose paragraphs that may include dynamic text parts. */
@RylNextDsl
class FlowTextBlockBuilder {
    private val paragraphs = mutableListOf<TextLine>()

    /**
     * Adds one paragraph. String newlines inside [parts] are normalized by the
     * renderer, so use another [paragraph] call for intentional paragraph breaks.
     */
    fun paragraph(vararg parts: Any) {
        paragraphs += TextLine(parts.flatMap(::textParts))
    }

    /** Adds one or more literal paragraphs from triple-quoted prose. */
    fun paragraphs(text: String) {
        flowParagraphs(text).forEach(paragraphs::add)
    }

    fun build(): TextBlock = TextBlock.Flow(paragraphs.toList())
}

private fun flowParagraphs(text: String): List<TextLine> {
    val trimmed = text.trimIndent()
    return trimmed
        .split(Regex("\\n\\s*\\n"))
        .map { paragraph -> paragraph.trim().replace('\n', ' ') }
        .filter(String::isNotEmpty)
        .map { paragraph -> TextLine(literalParts(paragraph)) }
}

/** Text-placeholder helper for NPC name, job, and location. */
class CharacterTextRef private constructor(
    val name: TextPart,
    val job: TextPart,
    val location: TextPart,
) {
    companion object {
        fun role(role: NpcRole): CharacterTextRef =
            CharacterTextRef(
                TextPart.RoleName(role),
                TextPart.RoleJob(role),
                TextPart.RoleLocation(role),
            )

        fun npc(npc: NpcId): CharacterTextRef =
            CharacterTextRef(
                TextPart.NpcName(npc),
                TextPart.NpcJob(npc),
                TextPart.NpcLocation(npc),
            )
    }
}

/** Creates text placeholders for the NPC bound to [role]. */
fun character(role: NpcRole): CharacterTextRef = CharacterTextRef.role(role)

/** Creates text placeholders for a concrete [npc]. */
fun character(npc: NpcId): CharacterTextRef = CharacterTextRef.npc(npc)

/** Text-placeholder helper for town name and nearby-location text. */
class TownTextRef(
    private val town: TownId,
) {
    val name: TextPart = TextPart.TownName(town)

    fun near(radius: Int = 5): TextPart = TextPart.FuzzyTownLocation(town, radius)
}

/** Minimal interface implemented by generated town handles. */
interface TownHandle {
    val id: TownId
}

/** Creates text placeholders for a generated [town] handle. */
fun town(town: TownHandle): TownTextRef = TownTextRef(town.id)

/** Text-placeholder helper for location coordinates. */
class LocationTextRef(
    location: LocationId,
) {
    val coordinates: TextPart = TextPart.LocationCoordinate(location)
}

/** Creates text placeholders for a [location]. */
fun location(location: LocationId): LocationTextRef = LocationTextRef(location)
