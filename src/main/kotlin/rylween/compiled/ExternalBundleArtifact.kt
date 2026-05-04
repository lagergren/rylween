package rylween.compiled

import java.io.ByteArrayOutputStream
import java.io.File
import java.lang.reflect.Modifier

private const val ExternalBundleMagic = "RYLBNDL1"
private const val ExternalBundleVersion = 1

fun CompiledGameBundle.toExternalBundleArtifact(): ByteArray =
    ExternalBundleArtifactWriter().write(this)

fun CompiledGameBundle.writeExternalBundleArtifact(file: File) {
    file.parentFile?.mkdirs()
    file.writeBytes(toExternalBundleArtifact())
}

fun ByteArray.readExternalBundleArtifact(): ExternalBundleArtifactSummary =
    ExternalBundleArtifactReader(this).read()

fun File.readExternalBundleArtifact(): ExternalBundleArtifactSummary =
    readBytes().readExternalBundleArtifact()

data class ExternalBundleArtifactSummary(
    val version: Int,
    val sections: List<ExternalBundleSectionSummary>,
) {
    fun section(id: ExternalBundleSectionId): ExternalBundleSectionSummary =
        sections.single { it.id == id }
}

data class ExternalBundleSectionSummary(
    val id: ExternalBundleSectionId,
    val rowCount: Int,
    val byteLength: Int,
    val checksum: Int,
)

enum class ExternalBundleSectionId(val code: Int) {
    Universe(0),
    Limits(1),
    Strings(2),
    Handles(3),
    Flags(4),
    Items(5),
    ItemPools(6),
    Formulas(7),
    Rewards(8),
    Terrain(9),
    WorldMaps(10),
    Images(11),
    StaticScreens(12),
    ScreenRegions(13),
    Towns(14),
    BuildingServices(15),
    Characters(16),
    Monsters(17),
    Missions(18),
    Spells(19),
    ItemUses(20),
    DungeonEvents(21),
    DungeonScenarios(22),
    Scripts(23),
    States(24),
}

private data class ExternalBundleSection(
    val id: ExternalBundleSectionId,
    val rowCount: Int,
    val payload: ByteArray,
) {
    val checksum: Int = payload.fnv1a32()
}

private class ExternalBundleArtifactWriter {
    private val out = ByteArrayOutputStream()

    fun write(bundle: CompiledGameBundle): ByteArray {
        val sections = sections(bundle)
        bytes(ExternalBundleMagic.toByteArray(Charsets.US_ASCII))
        u32(ExternalBundleVersion)
        u32(sections.size)
        sections.forEach { section ->
            u32(section.id.code)
            u32(section.rowCount)
            u32(section.payload.size)
            u32(section.checksum)
            bytes(section.payload)
        }
        return out.toByteArray()
    }

    private fun sections(bundle: CompiledGameBundle): List<ExternalBundleSection> =
        with(bundle.content) {
            listOf(
                section(ExternalBundleSectionId.Universe, universe),
                section(ExternalBundleSectionId.Limits, limits),
                section(ExternalBundleSectionId.Strings, strings.entries),
                section(ExternalBundleSectionId.Handles, handles.handles.sortedBy { it.denseId }),
                section(ExternalBundleSectionId.Flags, flags),
                section(ExternalBundleSectionId.Items, items),
                section(ExternalBundleSectionId.ItemPools, itemPools),
                section(ExternalBundleSectionId.Formulas, formulas),
                section(ExternalBundleSectionId.Rewards, rewards),
                section(ExternalBundleSectionId.Terrain, terrain),
                section(ExternalBundleSectionId.WorldMaps, worldMaps),
                section(ExternalBundleSectionId.Images, images),
                section(ExternalBundleSectionId.StaticScreens, staticScreens),
                section(ExternalBundleSectionId.ScreenRegions, screenRegions),
                section(ExternalBundleSectionId.Towns, towns),
                section(ExternalBundleSectionId.BuildingServices, buildingServices),
                section(ExternalBundleSectionId.Characters, characters),
                section(ExternalBundleSectionId.Monsters, monsters),
                section(ExternalBundleSectionId.Missions, missions),
                section(ExternalBundleSectionId.Spells, spells),
                section(ExternalBundleSectionId.ItemUses, itemUses),
                section(ExternalBundleSectionId.DungeonEvents, dungeonEvents),
                section(ExternalBundleSectionId.DungeonScenarios, dungeonScenarios),
                section(
                    ExternalBundleSectionId.Scripts,
                    scripts.roots.sortedWith(compareBy({ it.rootHandle.kind.ordinal }, { it.rootHandle.id })),
                ),
                section(
                    ExternalBundleSectionId.States,
                    bundle.states.sortedWith(compareBy({ it.state.kind.ordinal }, { it.state.id })),
                ),
            )
        }

    private fun section(id: ExternalBundleSectionId, value: Any): ExternalBundleSection =
        section(id, listOf(value))

    private fun section(id: ExternalBundleSectionId, values: List<*>): ExternalBundleSection =
        ExternalBundleSection(
            id = id,
            rowCount = values.size,
            payload = values.joinToString(separator = "\n", postfix = "\n") { value -> canonical(value) }
                .toByteArray(Charsets.UTF_8),
        )

    private fun u32(value: Int) {
        out.write(value and 0xff)
        out.write((value ushr 8) and 0xff)
        out.write((value ushr 16) and 0xff)
        out.write((value ushr 24) and 0xff)
    }

    private fun bytes(value: ByteArray) {
        out.write(value)
    }
}

private class ExternalBundleArtifactReader(
    private val data: ByteArray,
) {
    private var pos = 0

    fun read(): ExternalBundleArtifactSummary {
        magic()
        val version = u32()
        if (version != ExternalBundleVersion) fail("unsupported bundle artifact version")

        val sections = buildList {
            repeatCount(u32()) {
                val id = sectionId(u32())
                val rowCount = u32()
                val byteLength = u32()
                val checksum = u32()
                val payload = bytes(byteLength)
                if (payload.fnv1a32() != checksum) fail("checksum mismatch for section ${id.name}")
                add(
                    ExternalBundleSectionSummary(
                        id = id,
                        rowCount = rowCount,
                        byteLength = byteLength,
                        checksum = checksum,
                    ),
                )
            }
        }
        if (pos != data.size) fail("trailing bytes")
        val ids = sections.map { it.id }
        if (ids.toSet().size != ids.size) fail("duplicate bundle artifact section")
        val missing = ExternalBundleSectionId.entries - ids.toSet()
        if (missing.isNotEmpty()) fail("missing bundle artifact sections ${missing.joinToString { it.name }}")
        return ExternalBundleArtifactSummary(version, sections)
    }

    private fun magic() {
        val expected = ExternalBundleMagic.toByteArray(Charsets.US_ASCII)
        requireAvailable(expected.size)
        if (!data.copyOfRange(pos, pos + expected.size).contentEquals(expected)) fail("bad magic")
        pos += expected.size
    }

    private fun sectionId(code: Int): ExternalBundleSectionId =
        ExternalBundleSectionId.entries.firstOrNull { it.code == code }
            ?: fail("unknown section id $code")

    private fun repeatCount(count: Int, body: () -> Unit) {
        if (count < 0) fail("count is out of range")
        repeat(count) { body() }
    }

    private fun bytes(count: Int): ByteArray {
        if (count < 0) fail("byte count is out of range")
        requireAvailable(count)
        return data.copyOfRange(pos, pos + count).also {
            pos += count
        }
    }

    private fun u32(): Int {
        requireAvailable(4)
        val value = (data[pos].toInt() and 0xff) or
            ((data[pos + 1].toInt() and 0xff) shl 8) or
            ((data[pos + 2].toInt() and 0xff) shl 16) or
            ((data[pos + 3].toInt() and 0xff) shl 24)
        pos += 4
        return value
    }

    private fun requireAvailable(count: Int) {
        if (count < 0 || pos > data.size || count > data.size - pos) fail("unexpected end of file")
    }

    private fun fail(message: String): Nothing =
        error("compiled bundle artifact loader failed at byte $pos: $message")
}

private fun canonical(value: Any?): String =
    when (value) {
        null -> "null"
        is String -> value.quoteCanonical()
        is Char -> value.toString().quoteCanonical()
        is Number,
        is Boolean -> value.toString()
        is Enum<*> -> "${value.javaClass.simpleName}.${value.name}"
        is Set<*> -> value.map(::canonical).sorted().joinToString(prefix = "{", postfix = "}")
        is Collection<*> -> value.joinToString(prefix = "[", postfix = "]") { canonical(it) }
        is Map<*, *> -> value.entries
            .map { entry -> canonical(entry.key) to canonical(entry.value) }
            .sortedBy { it.first }
            .joinToString(prefix = "{", postfix = "}") { (key, entryValue) -> "$key=$entryValue" }
        else -> canonicalObject(value)
    }

private fun canonicalObject(value: Any): String {
    val javaClass = value.javaClass
    val fields = javaClass.declaredFields
        .filterNot { field -> field.isSynthetic || Modifier.isStatic(field.modifiers) }
        .sortedBy { it.name }
    if (fields.isEmpty()) return javaClass.simpleName
    return fields.joinToString(
        prefix = "${javaClass.simpleName}(",
        postfix = ")",
    ) { field ->
        field.isAccessible = true
        "${field.name}=${canonical(field.get(value))}"
    }
}

private fun String.quoteCanonical(): String =
    buildString {
        append('"')
        this@quoteCanonical.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
        append('"')
    }

internal fun ByteArray.fnv1a32(): Int {
    var hash = 0x811c9dc5.toInt()
    forEach { byte ->
        hash = hash xor (byte.toInt() and 0xff)
        hash *= 0x01000193
    }
    return hash
}
