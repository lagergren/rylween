package rylween.bitmapweb

private const val VIRTUAL_WIDTH = 1280
private const val VIRTUAL_HEIGHT = 720

internal data class BitmapFrame(
    val mode: String,
    val width: Int = VIRTUAL_WIDTH,
    val height: Int = VIRTUAL_HEIGHT,
    val backdrop: String,
    val title: String,
    val subtitle: String,
    val status: BitmapStatus? = null,
    val statusLines: List<String>,
    val textLines: List<String>,
    val textInput: BitmapTextInput? = null,
    val portrait: BitmapPortrait? = null,
    val partyPage: BitmapPartyPage? = null,
    val visualTransition: String = "None",
    val commands: List<BitmapCommandHint>,
    val map: BitmapMap? = null,
    val message: String? = null,
    val artLines: List<String> = emptyList(),
) {
    fun toJson(): String =
        jsonObject(
            "type" to "frame".json(),
            "mode" to mode.json(),
            "width" to width.toString(),
            "height" to height.toString(),
            "backdrop" to backdrop.json(),
            "title" to title.json(),
            "subtitle" to subtitle.json(),
            "status" to (status?.toJson() ?: "null"),
            "statusLines" to statusLines.jsonArray(),
            "textLines" to textLines.jsonArray(),
            "textInput" to (textInput?.toJson() ?: "null"),
            "portrait" to (portrait?.toJson() ?: "null"),
            "partyPage" to (partyPage?.toJson() ?: "null"),
            "visualTransition" to visualTransition.json(),
            "commands" to commands.joinToString(prefix = "[", postfix = "]") { command -> command.toJson() },
            "map" to (map?.toJson() ?: "null"),
            "message" to (message?.json() ?: "null"),
            "artLines" to artLines.jsonArray(),
        )
}

internal data class BitmapPartyPage(
    val kind: String,
    val lines: List<String>,
    val pauseLine: String,
    val summary: BitmapPartySummary? = null,
    val member: BitmapPartyMember? = null,
    val inventory: List<String> = emptyList(),
) {
    fun toJson(): String =
        jsonObject(
            "kind" to kind.json(),
            "lines" to lines.jsonArray(),
            "pauseLine" to pauseLine.json(),
            "summary" to (summary?.toJson() ?: "null"),
            "member" to (member?.toJson() ?: "null"),
            "inventory" to inventory.jsonArray(),
        )
}

internal data class BitmapPartySummary(
    val partyName: String,
    val gold: Int,
    val food: Int,
    val torches: Int,
    val moves: Int,
    val rest: Int,
    val kills: Int,
    val memberCount: Int,
) {
    fun toJson(): String =
        jsonObject(
            "partyName" to partyName.json(),
            "gold" to gold.toString(),
            "food" to food.toString(),
            "torches" to torches.toString(),
            "moves" to moves.toString(),
            "rest" to rest.toString(),
            "kills" to kills.toString(),
            "memberCount" to memberCount.toString(),
        )
}

internal data class BitmapPartyMember(
    val index: Int,
    val name: String,
    val gender: String,
    val race: String,
    val pcClass: String,
    val weaponSkill: Int,
    val health: Int,
    val wisdom: Int,
    val charisma: Int,
    val hitPoints: Int,
    val hitPointMax: Int,
    val mana: Int,
    val manaMax: Int,
    val level: Int,
    val experience: Int,
    val weapon: String,
    val armor: String,
    val status: String,
) {
    fun toJson(): String =
        jsonObject(
            "index" to index.toString(),
            "name" to name.json(),
            "gender" to gender.json(),
            "race" to race.json(),
            "pcClass" to pcClass.json(),
            "weaponSkill" to weaponSkill.toString(),
            "health" to health.toString(),
            "wisdom" to wisdom.toString(),
            "charisma" to charisma.toString(),
            "hitPoints" to hitPoints.toString(),
            "hitPointMax" to hitPointMax.toString(),
            "mana" to mana.toString(),
            "manaMax" to manaMax.toString(),
            "level" to level.toString(),
            "experience" to experience.toString(),
            "weapon" to weapon.json(),
            "armor" to armor.json(),
            "status" to status.json(),
        )
}

internal data class BitmapPortrait(
    val imageSlug: String,
    val memberNumber: Int,
    val memberName: String?,
    val gender: String?,
    val race: String?,
    val pcClass: String?,
) {
    fun toJson(): String =
        jsonObject(
            "imageSlug" to imageSlug.json(),
            "memberNumber" to memberNumber.toString(),
            "memberName" to (memberName?.json() ?: "null"),
            "gender" to (gender?.json() ?: "null"),
            "race" to (race?.json() ?: "null"),
            "pcClass" to (pcClass?.json() ?: "null"),
        )
}

internal data class BitmapTextInput(
    val value: String,
    val maxLength: Int,
) {
    fun toJson(): String =
        jsonObject(
            "value" to value.json(),
            "maxLength" to maxLength.toString(),
        )
}

internal data class BitmapStatus(
    val time: String,
    val condition: String,
    val x: Int,
    val y: Int,
    val food: Int,
    val moves: Int,
    val gold: Int,
    val members: List<BitmapMemberStatus>,
) {
    fun toJson(): String =
        jsonObject(
            "time" to time.json(),
            "condition" to condition.json(),
            "x" to x.toString(),
            "y" to y.toString(),
            "food" to food.toString(),
            "moves" to moves.toString(),
            "gold" to gold.toString(),
            "members" to members.joinToString(prefix = "[", postfix = "]") { member -> member.toJson() },
        )
}

internal data class BitmapMemberStatus(
    val slot: Int,
    val name: String,
    val hp: Int,
    val hpMax: Int,
    val mana: Int?,
    val manaMax: Int?,
    val dead: Boolean,
) {
    fun toJson(): String =
        jsonObject(
            "slot" to slot.toString(),
            "name" to name.json(),
            "hp" to hp.toString(),
            "hpMax" to hpMax.toString(),
            "mana" to (mana?.toString() ?: "null"),
            "manaMax" to (manaMax?.toString() ?: "null"),
            "dead" to dead.toString(),
        )
}

internal data class BitmapCommandHint(
    val action: String,
    val input: String,
    val label: String,
    val caption: String,
    val topic: String? = null,
) {
    fun toJson(): String =
        jsonObject(
            "action" to action.json(),
            "input" to input.json(),
            "label" to label.json(),
            "caption" to caption.json(),
            "topic" to (topic?.json() ?: "null"),
        )
}

internal data class BitmapMap(
    val tileSize: Int,
    val rows: List<List<BitmapTile>>,
) {
    fun toJson(): String =
        jsonObject(
            "tileSize" to tileSize.toString(),
            "rows" to
                rows.joinToString(prefix = "[", postfix = "]") { row ->
                    row.joinToString(prefix = "[", postfix = "]") { tile -> tile.toJson() }
                },
        )
}

internal data class BitmapTile(
    val slug: String,
    val code: String,
    val x: Int,
    val y: Int,
    val variant: Int,
    val passable: Boolean,
    val settlement: String?,
    val center: Boolean,
) {
    fun toJson(): String =
        jsonObject(
            "slug" to slug.json(),
            "code" to code.json(),
            "x" to x.toString(),
            "y" to y.toString(),
            "variant" to variant.toString(),
            "passable" to passable.toString(),
            "settlement" to (settlement?.json() ?: "null"),
            "center" to center.toString(),
        )
}

private fun jsonObject(vararg entries: Pair<String, String>): String =
    entries.joinToString(prefix = "{", postfix = "}") { (name, value) -> "${name.json()}:$value" }

private fun List<String>.jsonArray(): String = joinToString(prefix = "[", postfix = "]") { value -> value.json() }

private fun String.json(): String =
    buildString {
        append('"')
        this@json.forEach { char ->
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (char.code < 0x20) {
                        append("\\u")
                        append(char.code.toString(16).padStart(4, '0'))
                    } else {
                        append(char)
                    }
                }
            }
        }
        append('"')
    }
