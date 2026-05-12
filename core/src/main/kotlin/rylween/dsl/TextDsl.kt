/**
 * Text blocks, structured text parts, and text helper builders.
 *
 * Runtime-facing text is represented as typed parts rather than ad hoc string
 * replacement. This file defines inline/ref/structured text blocks, dynamic
 * placeholders for runtime values and NPC/town references, and small builder
 * helpers used by content, scripts, prompts, and compiled text emission.
 */
package rylween.dsl

/** Stable id for authored text catalog entries. */
data class TextId(
    val id: String,
)

/** Pattern token used by authored text substitutions and matching helpers. */
data class TextPattern(
    val pattern: String,
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

    object EquipmentKindName : TextPart

    object ShopPrice : TextPart

    object ShopCarryCap : TextPart

    object HuntMemberName : TextPart

    object HuntMemberSubjectPronoun : TextPart

    object HuntFoodGained : TextPart

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
        TextPart.EquipmentKindName -> "{equipment_kind}"
        TextPart.ShopPrice -> "{price}"
        TextPart.ShopCarryCap -> "{cap}"
        TextPart.HuntMemberName -> "{who_name}"
        TextPart.HuntMemberSubjectPronoun -> "{pro_sub}"
        TextPart.HuntFoodGained -> "{fdplus}"
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

/** Authored text block, either by catalog ref, inline lines, or structured parts. */
sealed interface TextBlock {
    data class Ref(
        val id: TextId,
    ) : TextBlock

    data class Inline(
        val lines: List<String>,
    ) : TextBlock

    data class Structured(
        val lines: List<TextLine>,
    ) : TextBlock
}

/** Creates an inline literal text block. */
fun inlineText(vararg lines: String): TextBlock = TextBlock.Inline(lines.toList())

/** Creates a structured text block with dynamic parts. */
fun structuredText(body: StructuredTextBlockBuilder.() -> Unit): TextBlock = StructuredTextBlockBuilder().apply(body).build()

/** Creates a structured text block from literal lines. */
fun textLines(vararg lines: String): TextBlock = structuredText { lines(*lines) }

/** Builder sink for collecting structured text lines. */
interface TextLineSink {
    fun addTextLine(line: TextLine)
}

/** Adds one structured text line, coercing strings and expression wrappers into [TextPart] values. */
fun TextLineSink.line(vararg parts: Any) {
    addTextLine(TextLine(parts.map(::textPart)))
}

/** Adds multiple literal lines to a text-line sink. */
fun TextLineSink.lines(vararg lines: String) {
    lines.forEach { line(it) }
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
        is TextBlock.Ref -> listOf(TextLine(listOf(TextPart.Literal("[${id.id}]"))))
        is TextBlock.Structured -> lines
    }

private fun textPart(part: Any): TextPart =
    when (part) {
        is TextPart -> part
        is IntExpr -> TextPart.IntValue(part.ast)
        is StrExpr -> TextPart.StringValue(part.ast)
        is String -> TextPart.Literal(part)
        else -> TextPart.Literal(part.toString())
    }

@RylNextDsl
class StructuredTextBlockBuilder : TextLineSink {
    private val lines = mutableListOf<TextLine>()

    override fun addTextLine(line: TextLine) {
        lines += line
    }

    fun build(): TextBlock = TextBlock.Structured(lines.toList())
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
    private val location: LocationId,
) {
    val coordinates: TextPart = TextPart.LocationCoordinate(location)
}

/** Creates text placeholders for a [location]. */
fun location(location: LocationId): LocationTextRef = LocationTextRef(location)
