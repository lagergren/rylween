package rylween.save

internal fun dslQuotedString(value: String): String =
    buildString {
        appendDslQuotedString(value)
    }

internal fun StringBuilder.appendDslQuotedString(value: String) {
    append('"')
    value.forEach { char ->
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(char)
        }
    }
    append('"')
}
