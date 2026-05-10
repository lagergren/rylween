/**
 * Text codecs for the runtime save boundary.
 *
 * This is persistence syntax, not terminal protocol. ANSI colors and key
 * decoding live in `:terminal`; these helpers stay next to the direct
 * runtime save adapter because they encode save-file keys, scalar
 * values, and compact continuation fields.
 */

package rylween.runtime

/** Encodes and decodes the runtime's sorted key/value save file. */
internal object SaveCodec {
    fun encodeValues(values: Map<String, String>): String =
        values
            .toSortedMap()
            .entries
            .joinToString(separator = "\n", postfix = "\n") { (key, value) ->
                "${escapeLineField(key)}=${escapeLineField(value)}"
            }

    fun decodeValues(lines: List<String>): Map<String, String> =
        lines
            .asSequence()
            .map(String::trim)
            .filter { line -> line.isNotEmpty() && !line.startsWith("#") }
            .associate { line ->
                val separator = line.indexOf('=')
                require(separator > 0) { "invalid save line: $line" }
                unescapeLineField(line.substring(0, separator)) to unescapeLineField(line.substring(separator + 1))
            }

    fun encodeFrames(frames: List<ScriptFrameState>): String =
        frames.joinToString("|") { frame ->
            "${frame.blockPath.joinToString(",")}:${frame.nextIndex}:${frame.insideLoop}"
        }

    fun decodeFrames(value: String): List<ScriptFrameState> =
        value
            .split('|')
            .filter(String::isNotEmpty)
            .map { token ->
                val parts = token.split(':')
                ScriptFrameState(
                    blockPath =
                        parts[0]
                            .takeIf(String::isNotEmpty)
                            ?.split(',')
                            ?.map(String::toInt)
                            .orEmpty(),
                    nextIndex = parts[1].toInt(),
                    insideLoop = parts[2].toBooleanStrict(),
                )
            }

    fun encodeIntMap(values: Map<String, Int>): String =
        values
            .entries
            .sortedBy(Map.Entry<String, Int>::key)
            .joinToString("|") { (key, value) -> "${escapeSegment(key)}:$value" }

    fun decodeIntMap(value: String): Map<String, Int> = decodeStringMap(value).mapValues { (_, mapValue) -> mapValue.toInt() }

    fun encodeStringMap(values: Map<String, String>): String =
        values
            .entries
            .sortedBy(Map.Entry<String, String>::key)
            .joinToString("|") { (key, value) -> "${escapeSegment(key)}:${escapeSegment(value)}" }

    fun decodeStringMap(value: String): Map<String, String> {
        if (value.isEmpty()) return emptyMap()
        return value
            .split('|')
            .filter(String::isNotEmpty)
            .associate { token ->
                val separator = token.indexOf(':')
                require(separator >= 0) { "invalid compiled continuation map token: $token" }
                unescapeSegment(token.substring(0, separator)) to unescapeSegment(token.substring(separator + 1))
            }
    }

    private fun escapeLineField(value: String): String =
        buildString {
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '=' -> append("\\=")
                    else -> append(char)
                }
            }
        }

    private fun unescapeLineField(value: String): String =
        unescape(value) { marker ->
            when (marker) {
                'n' -> '\n'
                'r' -> '\r'
                else -> marker
            }
        }

    private fun escapeSegment(value: String): String =
        buildString {
            value.forEach { char ->
                when (char) {
                    '\\' -> append("\\\\")
                    '|' -> append("\\p")
                    ':' -> append("\\c")
                    else -> append(char)
                }
            }
        }

    private fun unescapeSegment(value: String): String =
        unescape(value) { marker ->
            when (marker) {
                'p' -> '|'
                'c' -> ':'
                else -> marker
            }
        }

    private fun unescape(
        value: String,
        mapped: (Char) -> Char,
    ): String {
        val out = StringBuilder()
        var escaped = false
        value.forEach { char ->
            if (escaped) {
                out.append(mapped(char))
                escaped = false
            } else if (char == '\\') {
                escaped = true
            } else {
                out.append(char)
            }
        }
        if (escaped) out.append('\\')
        return out.toString()
    }
}
