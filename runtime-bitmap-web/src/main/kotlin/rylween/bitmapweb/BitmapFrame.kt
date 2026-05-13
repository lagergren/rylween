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
            "commands" to commands.joinToString(prefix = "[", postfix = "]") { command -> command.toJson() },
            "map" to (map?.toJson() ?: "null"),
            "message" to (message?.json() ?: "null"),
            "artLines" to artLines.jsonArray(),
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
