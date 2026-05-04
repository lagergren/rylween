package rylween.compiled

import java.io.File
import java.lang.reflect.Array
import java.lang.reflect.Modifier
import java.security.MessageDigest
import java.util.IdentityHashMap

private const val ExternalBundleFormat = "rylween.compiled.bundle"
private const val ExternalBundleVersion = 1

fun CompiledGameBundle.toExternalBundleArtifact(): ByteArray =
    ExternalBundleArtifactWriter().write(this)

fun CompiledGameBundle.writeExternalBundleArtifact(file: File) {
    file.parentFile?.mkdirs()
    file.writeBytes(toExternalBundleArtifact())
}

fun ByteArray.readExternalBundleArtifact(): ExternalBundleArtifactSummary =
    ExternalBundleArtifactReader(decodeToString()).read()

fun File.readExternalBundleArtifact(): ExternalBundleArtifactSummary =
    readBytes().readExternalBundleArtifact()

fun File.readVerifiedExternalBundleArtifact(expected: CompiledGameBundle): ExternalBundleArtifactSummary {
    val actual = readExternalBundleArtifact()
    val expectedSummary = expected.toExternalBundleArtifact().readExternalBundleArtifact()
    if (actual.bundleContentHash != expectedSummary.bundleContentHash) {
        error(
            "compiled bundle artifact ${path} does not match current compiled game content: " +
                "expected ${expectedSummary.bundleContentHash}, found ${actual.bundleContentHash}",
        )
    }
    if (actual.sections != expectedSummary.sections) {
        error("compiled bundle artifact ${path} section summaries do not match current compiled game content")
    }
    return actual
}

fun ByteArray.inspectExternalBundleArtifact(): String =
    readExternalBundleArtifact().renderExternalBundleInspection()

fun File.inspectExternalBundleArtifact(): String =
    readBytes().inspectExternalBundleArtifact()

data class ExternalBundleArtifactSummary(
    val format: String,
    val version: Int,
    val bundleContentHash: String,
    val universeName: String,
    val universeVersion: String,
    val universeCompatibilityId: String,
    val sections: List<ExternalBundleSectionSummary>,
) {
    fun section(id: ExternalBundleSectionId): ExternalBundleSectionSummary =
        sections.single { it.id == id }

    fun renderExternalBundleInspection(): String =
        buildString {
            appendLine("Rylween compiled bundle")
            appendLine("format: $format")
            appendLine("schemaVersion: $version")
            appendLine("bundleContentHash: $bundleContentHash")
            appendLine("universe: $universeName $universeVersion ($universeCompatibilityId)")
            appendLine("sections:")
            sections.forEach { section ->
                appendLine(
                    "  ${section.id.jsonName}: rows=${section.rowCount} bytes=${section.byteLength} " +
                        "fnv1a32=${section.checksumHex}",
                )
            }
        }
}

data class ExternalBundleSectionSummary(
    val id: ExternalBundleSectionId,
    val rowCount: Int,
    val byteLength: Int,
    val checksum: Int,
) {
    val checksumHex: String =
        checksum.toUInt().toString(radix = 16).padStart(length = 8, padChar = '0')
}

enum class ExternalBundleSectionId(val jsonName: String) {
    Universe("universe"),
    Limits("limits"),
    Strings("strings"),
    Handles("handles"),
    Flags("flags"),
    Items("items"),
    ItemPools("itemPools"),
    Formulas("formulas"),
    Rewards("rewards"),
    Terrain("terrain"),
    WorldMaps("worldMaps"),
    Images("images"),
    StaticScreens("staticScreens"),
    ScreenRegions("screenRegions"),
    Towns("towns"),
    BuildingServices("buildingServices"),
    Characters("characters"),
    Monsters("monsters"),
    Missions("missions"),
    Spells("spells"),
    ItemUses("itemUses"),
    DungeonEvents("dungeonEvents"),
    DungeonScenarios("dungeonScenarios"),
    Scripts("scripts"),
    States("states"),
}

private class ExternalBundleArtifactWriter {
    fun write(bundle: CompiledGameBundle): ByteArray {
        val sections = bundle.sectionsJson()
        val root = jsonObject(
            "format" to JsonString(ExternalBundleFormat),
            "schemaVersion" to JsonNumber(ExternalBundleVersion.toString()),
            "bundleContentHash" to JsonString(sections.compactBytes().sha256()),
            "sectionOrder" to JsonArray(ExternalBundleSectionId.entries.map { JsonString(it.jsonName) }),
            "sections" to sections,
        )
        return (root.render(pretty = true) + "\n").toByteArray(Charsets.UTF_8)
    }

    private fun CompiledGameBundle.sectionsJson(): JsonObject =
        with(content) {
            jsonObject(
                ExternalBundleSectionId.Universe.jsonName to universeJson(universe, strings),
                ExternalBundleSectionId.Limits.jsonName to compiledJson(limits),
                ExternalBundleSectionId.Strings.jsonName to JsonArray(
                    strings.entries.mapIndexed { index, value ->
                        jsonObject(
                            "id" to JsonNumber((index + 1).toString()),
                            "value" to JsonString(value),
                            "hash" to JsonNumber(value.hashCode().toString()),
                        )
                    },
                ),
                ExternalBundleSectionId.Handles.jsonName to JsonArray(
                    handles.handles.sortedBy { it.denseId }.map { handle ->
                        jsonObject(
                            "kind" to JsonString(handle.ref.kind.name),
                            "id" to JsonNumber(handle.ref.id.toString()),
                            "denseId" to JsonNumber(handle.denseId.toString()),
                            "slugString" to JsonNumber(handle.slugString.toString()),
                            "slug" to JsonString(strings.value(handle.slugString)),
                        )
                    },
                ),
                ExternalBundleSectionId.Flags.jsonName to compiledJson(flags),
                ExternalBundleSectionId.Items.jsonName to compiledJson(items),
                ExternalBundleSectionId.ItemPools.jsonName to compiledJson(itemPools),
                ExternalBundleSectionId.Formulas.jsonName to compiledJson(formulas),
                ExternalBundleSectionId.Rewards.jsonName to compiledJson(rewards),
                ExternalBundleSectionId.Terrain.jsonName to compiledJson(terrain),
                ExternalBundleSectionId.WorldMaps.jsonName to compiledJson(worldMaps),
                ExternalBundleSectionId.Images.jsonName to compiledJson(images),
                ExternalBundleSectionId.StaticScreens.jsonName to compiledJson(staticScreens),
                ExternalBundleSectionId.ScreenRegions.jsonName to compiledJson(screenRegions),
                ExternalBundleSectionId.Towns.jsonName to compiledJson(towns),
                ExternalBundleSectionId.BuildingServices.jsonName to compiledJson(buildingServices),
                ExternalBundleSectionId.Characters.jsonName to compiledJson(characters),
                ExternalBundleSectionId.Monsters.jsonName to compiledJson(monsters),
                ExternalBundleSectionId.Missions.jsonName to compiledJson(missions),
                ExternalBundleSectionId.Spells.jsonName to compiledJson(spells),
                ExternalBundleSectionId.ItemUses.jsonName to compiledJson(itemUses),
                ExternalBundleSectionId.DungeonEvents.jsonName to compiledJson(dungeonEvents),
                ExternalBundleSectionId.DungeonScenarios.jsonName to compiledJson(dungeonScenarios),
                ExternalBundleSectionId.Scripts.jsonName to compiledJson(
                    scripts.roots.sortedWith(compareBy({ it.rootHandle.kind.ordinal }, { it.rootHandle.id })),
                ),
                ExternalBundleSectionId.States.jsonName to compiledJson(
                    states.sortedWith(compareBy({ it.state.kind.ordinal }, { it.state.id })),
                ),
            )
        }

    private fun universeJson(universe: CompiledUniverse, strings: CompiledStringTable): JsonValue =
        jsonObject(
            "nameString" to JsonNumber(universe.nameString.toString()),
            "versionString" to JsonNumber(universe.versionString.toString()),
            "compatibilityIdString" to JsonNumber(universe.compatibilityIdString.toString()),
            "name" to JsonString(strings.value(universe.nameString)),
            "version" to JsonString(strings.value(universe.versionString)),
            "compatibilityId" to JsonString(strings.value(universe.compatibilityIdString)),
        )
}

private class ExternalBundleArtifactReader(
    private val source: String,
) {
    fun read(): ExternalBundleArtifactSummary {
        val root = JsonParser(source).parse().asObject("compiled bundle root")
        val format = root.requiredString("format")
        if (format != ExternalBundleFormat) fail("unsupported bundle format $format")
        val version = root.requiredInt("schemaVersion")
        if (version != ExternalBundleVersion) fail("unsupported bundle schema version $version")

        val order = root.requiredArray("sectionOrder").values.map { it.asString("sectionOrder entry") }
        val expectedOrder = ExternalBundleSectionId.entries.map { it.jsonName }
        if (order != expectedOrder) fail("section order does not match schema")

        val sectionsObject = root.requiredObject("sections")
        val sectionNames = sectionsObject.entries.map { it.first }
        if (sectionNames != expectedOrder) fail("sections object does not match schema order")

        val contentHash = root.requiredString("bundleContentHash")
        val actualHash = sectionsObject.compactBytes().sha256()
        if (contentHash != actualHash) fail("bundle content hash does not match sections")

        val universe = sectionsObject.requiredObject(ExternalBundleSectionId.Universe.jsonName)
        return ExternalBundleArtifactSummary(
            format = format,
            version = version,
            bundleContentHash = contentHash,
            universeName = universe.requiredString("name"),
            universeVersion = universe.requiredString("version"),
            universeCompatibilityId = universe.requiredString("compatibilityId"),
            sections = ExternalBundleSectionId.entries.map { id ->
                val value = sectionsObject.required(id.jsonName)
                val bytes = value.compactBytes()
                ExternalBundleSectionSummary(
                    id = id,
                    rowCount = value.rowCount(),
                    byteLength = bytes.size,
                    checksum = bytes.fnv1a32(),
                )
            },
        )
    }

    private fun fail(message: String): Nothing =
        error("compiled bundle artifact loader failed: $message")
}

private sealed interface JsonValue {
    fun render(pretty: Boolean): String =
        buildString {
            appendJson(this@JsonValue, pretty = pretty, indent = 0)
        }
}

private data object JsonNull : JsonValue
private data class JsonBoolean(val value: Boolean) : JsonValue
private data class JsonNumber(val value: String) : JsonValue
private data class JsonString(val value: String) : JsonValue
private data class JsonArray(val values: List<JsonValue>) : JsonValue
private data class JsonObject(val entries: List<Pair<String, JsonValue>>) : JsonValue

private fun jsonObject(vararg entries: Pair<String, JsonValue>): JsonObject =
    JsonObject(entries.toList())

private fun compiledJson(value: Any?): JsonValue =
    compiledJson(value, mutableIdentitySet())

private fun compiledJson(value: Any?, visiting: MutableSet<Any>): JsonValue =
    when (value) {
        null -> JsonNull
        is String -> JsonString(value)
        is Char -> JsonString(value.toString())
        is Boolean -> JsonBoolean(value)
        is Byte,
        is Short,
        is Int,
        is Long,
        is Float,
        is Double -> JsonNumber(value.toString())
        is Enum<*> -> JsonString(value.name)
        is IntRange -> jsonObject(
            "first" to JsonNumber(value.first.toString()),
            "last" to JsonNumber(value.last.toString()),
        )
        is Map<*, *> -> JsonArray(
            value.entries
                .map { entry -> compiledJson(entry.key, visiting).render(pretty = false) to entry }
                .sortedBy { it.first }
                .map { (_, entry) ->
                    jsonObject(
                        "key" to compiledJson(entry.key, visiting),
                        "value" to compiledJson(entry.value, visiting),
                    )
                },
        )
        is Set<*> -> JsonArray(
            value.map { item -> compiledJson(item, visiting) }
                .sortedBy { item -> item.render(pretty = false) },
        )
        is Iterable<*> -> JsonArray(value.map { item -> compiledJson(item, visiting) })
        else -> compiledObjectJson(value, visiting)
    }

private fun compiledObjectJson(value: Any, visiting: MutableSet<Any>): JsonValue {
    val type = value.javaClass
    if (type.isArray) {
        return JsonArray(
            (0 until Array.getLength(value)).map { index ->
                compiledJson(Array.get(value, index), visiting)
            },
        )
    }
    if (!visiting.add(value)) {
        return jsonObject("_type" to JsonString(type.simpleName), "_cycle" to JsonBoolean(true))
    }

    return try {
        val fields = type.declaredFields
            .filterNot { field -> field.isSynthetic || Modifier.isStatic(field.modifiers) }
            .sortedBy { it.name }
        JsonObject(
            buildList {
                add("_type" to JsonString(type.simpleName))
                fields.forEach { field ->
                    check(field.trySetAccessible()) { "cannot serialize ${type.name}.${field.name}" }
                    add(field.name to compiledJson(field.get(value), visiting))
                }
            },
        )
    } finally {
        visiting.remove(value)
    }
}

private fun mutableIdentitySet(): MutableSet<Any> =
    java.util.Collections.newSetFromMap(IdentityHashMap())

private fun JsonValue.rowCount(): Int =
    when (this) {
        is JsonArray -> values.size
        else -> 1
    }

private fun JsonValue.compactBytes(): ByteArray =
    render(pretty = false).toByteArray(Charsets.UTF_8)

private fun ByteArray.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(this)
    return "sha256:" + digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
}

private fun StringBuilder.appendJson(value: JsonValue, pretty: Boolean, indent: Int) {
    when (value) {
        JsonNull -> append("null")
        is JsonBoolean -> append(value.value)
        is JsonNumber -> append(value.value)
        is JsonString -> appendQuotedJson(value.value)
        is JsonArray -> appendJsonArray(value, pretty, indent)
        is JsonObject -> appendJsonObject(value, pretty, indent)
    }
}

private fun StringBuilder.appendJsonArray(value: JsonArray, pretty: Boolean, indent: Int) {
    append('[')
    value.values.forEachIndexed { index, item ->
        if (index > 0) append(',')
        if (pretty) append('\n').appendIndent(indent + 1)
        appendJson(item, pretty, indent + 1)
    }
    if (pretty && value.values.isNotEmpty()) append('\n').appendIndent(indent)
    append(']')
}

private fun StringBuilder.appendJsonObject(value: JsonObject, pretty: Boolean, indent: Int) {
    append('{')
    value.entries.forEachIndexed { index, entry ->
        if (index > 0) append(',')
        if (pretty) append('\n').appendIndent(indent + 1)
        appendQuotedJson(entry.first)
        append(if (pretty) ": " else ":")
        appendJson(entry.second, pretty, indent + 1)
    }
    if (pretty && value.entries.isNotEmpty()) append('\n').appendIndent(indent)
    append('}')
}

private fun StringBuilder.appendIndent(indent: Int) {
    repeat(indent) { append("  ") }
}

private fun StringBuilder.appendQuotedJson(value: String) {
    append('"')
    value.forEach { ch ->
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\b' -> append("\\b")
            '\u000c' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> {
                if (ch < ' ') {
                    append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                } else {
                    append(ch)
                }
            }
        }
    }
    append('"')
}

private class JsonParser(
    private val source: String,
) {
    private var pos = 0

    fun parse(): JsonValue {
        val value = value()
        skipWhitespace()
        if (pos != source.length) fail("trailing characters")
        return value
    }

    private fun value(): JsonValue {
        skipWhitespace()
        if (pos >= source.length) fail("unexpected end of file")
        return when (source[pos]) {
            '{' -> obj()
            '[' -> array()
            '"' -> JsonString(string())
            't' -> literal("true", JsonBoolean(true))
            'f' -> literal("false", JsonBoolean(false))
            'n' -> literal("null", JsonNull)
            '-', in '0'..'9' -> number()
            else -> fail("unexpected character ${source[pos]}")
        }
    }

    private fun obj(): JsonObject {
        expect('{')
        skipWhitespace()
        val entries = buildList {
            if (peek('}')) {
                expect('}')
                return JsonObject(emptyList())
            }
            while (true) {
                val key = string()
                skipWhitespace()
                expect(':')
                add(key to value())
                skipWhitespace()
                when {
                    peek(',') -> {
                        expect(',')
                        skipWhitespace()
                    }
                    peek('}') -> {
                        expect('}')
                        break
                    }
                    else -> fail("expected ',' or '}'")
                }
            }
        }
        if (entries.map { it.first }.toSet().size != entries.size) fail("duplicate object key")
        return JsonObject(entries)
    }

    private fun array(): JsonArray {
        expect('[')
        skipWhitespace()
        return JsonArray(
            buildList {
                if (peek(']')) {
                    expect(']')
                    return JsonArray(emptyList())
                }
                while (true) {
                    add(value())
                    skipWhitespace()
                    when {
                        peek(',') -> expect(',')
                        peek(']') -> {
                            expect(']')
                            break
                        }
                        else -> fail("expected ',' or ']'")
                    }
                }
            },
        )
    }

    private fun string(): String {
        skipWhitespace()
        expect('"')
        return buildString {
            while (pos < source.length) {
                val ch = source[pos++]
                when (ch) {
                    '"' -> return@buildString
                    '\\' -> append(escape())
                    else -> {
                        if (ch < ' ') fail("unescaped control character")
                        append(ch)
                    }
                }
            }
            fail("unterminated string")
        }
    }

    private fun escape(): Char {
        if (pos >= source.length) fail("unterminated escape")
        return when (val ch = source[pos++]) {
            '"', '\\', '/' -> ch
            'b' -> '\b'
            'f' -> '\u000c'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> unicodeEscape()
            else -> fail("bad escape")
        }
    }

    private fun unicodeEscape(): Char {
        if (pos + 4 > source.length) fail("bad unicode escape")
        val code = source.substring(pos, pos + 4).toIntOrNull(radix = 16)
            ?: fail("bad unicode escape")
        pos += 4
        return code.toChar()
    }

    private fun number(): JsonNumber {
        val start = pos
        if (peek('-')) pos++
        digits()
        if (peek('.')) {
            pos++
            digits()
        }
        if (peek('e') || peek('E')) {
            pos++
            if (peek('+') || peek('-')) pos++
            digits()
        }
        return JsonNumber(source.substring(start, pos))
    }

    private fun digits() {
        val start = pos
        while (pos < source.length && source[pos].isDigit()) pos++
        if (pos == start) fail("expected digit")
    }

    private fun literal(text: String, value: JsonValue): JsonValue {
        if (!source.startsWith(text, startIndex = pos)) fail("expected $text")
        pos += text.length
        return value
    }

    private fun skipWhitespace() {
        while (pos < source.length && source[pos].isWhitespace()) pos++
    }

    private fun expect(ch: Char) {
        if (!peek(ch)) fail("expected '$ch'")
        pos++
    }

    private fun peek(ch: Char): Boolean =
        pos < source.length && source[pos] == ch

    private fun fail(message: String): Nothing =
        error("JSON parse failed at byte $pos: $message")
}

private fun JsonValue.asObject(label: String): JsonObject =
    this as? JsonObject ?: error("expected object for $label")

private fun JsonValue.asString(label: String): String =
    (this as? JsonString)?.value ?: error("expected string for $label")

private fun JsonObject.required(name: String): JsonValue =
    entries.firstOrNull { it.first == name }?.second ?: error("missing JSON field $name")

private fun JsonObject.requiredObject(name: String): JsonObject =
    required(name).asObject(name)

private fun JsonObject.requiredArray(name: String): JsonArray =
    required(name) as? JsonArray ?: error("expected array for $name")

private fun JsonObject.requiredString(name: String): String =
    required(name).asString(name)

private fun JsonObject.requiredInt(name: String): Int =
    (required(name) as? JsonNumber)?.value?.toIntOrNull() ?: error("expected integer for $name")

internal fun ByteArray.fnv1a32(): Int {
    var hash = 0x811c9dc5.toInt()
    forEach { byte ->
        hash = hash xor (byte.toInt() and 0xff)
        hash *= 0x01000193
    }
    return hash
}
