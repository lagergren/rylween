/** Serializable status snapshot returned by web runtime status endpoints. */

package rylween.webcommon

data class WebServerStatus(
    val serverType: String,
    /** The release version, such as `0.9.1`, which the pages show next to the game's name. */
    val version: String,
    val authRequired: Boolean,
    val currentUser: String?,
    val activeUsers: List<String>,
) {
    fun toJson(): String =
        buildString {
            append('{')
            append("\"serverType\":")
            append(serverType.json())
            append(",\"version\":")
            append(version.json())
            append(",\"authRequired\":")
            append(authRequired)
            append(",\"currentUser\":")
            append(currentUser?.json() ?: "null")
            append(",\"connectedUsers\":")
            append(activeUsers.size)
            append(",\"activeUsers\":[")
            append(activeUsers.joinToString(",") { user -> user.json() })
            append("]}")
        }
}

private fun String.json(): String =
    buildString(length + 2) {
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
                else -> if (char < ' ') append("\\u%04x".format(char.code)) else append(char)
            }
        }
        append('"')
    }
