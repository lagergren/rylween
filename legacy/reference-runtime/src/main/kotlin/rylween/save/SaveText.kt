/**
 * Shared string escaping for save DSL rendering.
 *
 * `StateSnapshot.kt` and `SaveCompatibility.kt` both need the same quoted text
 * rules so persisted saves and content fingerprints serialize strings
 * consistently.
 */
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
