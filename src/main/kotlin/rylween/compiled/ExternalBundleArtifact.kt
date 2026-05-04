package rylween.compiled

import rylween.dsl.*
import java.io.File
import java.lang.reflect.Array
import java.lang.reflect.Modifier
import java.security.MessageDigest
import java.util.IdentityHashMap
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.typeOf

private const val ExternalBundleFormat = "rylween.compiled.bundle"
private const val ExternalBundleVersion = 2

fun RylweenModel.toExternalBundleArtifact(): ByteArray =
    ExternalBundleArtifactWriter().write(this)

fun RylweenModel.writeExternalBundleArtifact(file: File) {
    file.parentFile?.mkdirs()
    file.writeBytes(toExternalBundleArtifact())
}

fun ByteArray.readExternalBundleArtifact(): ExternalBundleArtifactSummary =
    ExternalBundleArtifactReader(decodeToString()).read()

fun File.readExternalBundleArtifact(): ExternalBundleArtifactSummary =
    readBytes().readExternalBundleArtifact()

/**
 * Artifact startup drift guard used by the current ANSI `--bundle` flag.
 *
 * The launcher reads the JSON bundle into a runtime facade and verifies that
 * re-emitting that loaded model would produce the same hash and section summary.
 * This catches corrupt or nondeterministic bundle files before any runtime
 * session is started.
 */
fun File.readVerifiedExternalBundleArtifact(expected: RylweenModel): ExternalBundleArtifactSummary {
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

/**
 * Reads the J1 strict JSON artifact back into the typed compiled bundle model.
 *
 * This is the next boundary after the summary/drift guard: the file is no
 * longer only inspectable, it materializes the same `CompiledGameBundle` shape
 * that `CompiledBundleLoader` validates.
 */
fun ByteArray.readExternalCompiledGameBundle(): CompiledGameBundle =
    ExternalBundleArtifactMaterializer(decodeToString()).readCompiledBundle()

fun File.readExternalCompiledGameBundle(): CompiledGameBundle =
    readBytes().readExternalCompiledGameBundle()

/**
 * Reads the J1 strict JSON artifact into the runtime-facing Kotlin game model.
 *
 * The `runtimeFacade` section carries the complete data graph the current
 * Kotlin session/runtime layer consumes: prompts, terminal presentation,
 * realm/upkeep balance, authored content, Ydarg castle content, and start
 * states. The loader recompiles that facade back into `CompiledGameBundle` and
 * requires it to match the normalized compiled sections, so the facade cannot
 * silently drift from the dense table bundle. This is deliberately still a
 * Kotlin runtime facade, not the final stable row schema a non-Kotlin runtime
 * would implement directly.
 */
fun ByteArray.readExternalRuntimeGame(): RylweenModel =
    ExternalBundleArtifactMaterializer(decodeToString()).readRuntimeGame()

fun File.readExternalRuntimeGame(): RylweenModel =
    readBytes().readExternalRuntimeGame()

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
    RuntimeFacade("runtimeFacade"),
    States("states"),
}

private class ExternalBundleArtifactWriter {
    fun write(game: RylweenModel): ByteArray {
        val sections = game.toCompiledGameBundle().sectionsJson(game)
        val root = jsonObject(
            "format" to JsonString(ExternalBundleFormat),
            "schemaVersion" to JsonNumber(ExternalBundleVersion.toString()),
            "bundleContentHash" to JsonString(sections.compactBytes().sha256()),
            "sectionOrder" to JsonArray(ExternalBundleSectionId.entries.map { JsonString(it.jsonName) }),
            "sections" to sections,
        )
        return (root.render(pretty = true) + "\n").toByteArray(Charsets.UTF_8)
    }

    private fun CompiledGameBundle.sectionsJson(game: RylweenModel): JsonObject =
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
                ExternalBundleSectionId.RuntimeFacade.jsonName to compiledJson(game),
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
        val envelope = readExternalBundleEnvelope(source)
        val universe = envelope.sections.requiredObject(ExternalBundleSectionId.Universe.jsonName)
        return ExternalBundleArtifactSummary(
            format = envelope.format,
            version = envelope.version,
            bundleContentHash = envelope.contentHash,
            universeName = universe.requiredString("name"),
            universeVersion = universe.requiredString("version"),
            universeCompatibilityId = universe.requiredString("compatibilityId"),
            sections = ExternalBundleSectionId.entries.map { id ->
                val value = envelope.sections.required(id.jsonName)
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
}

private data class ExternalBundleEnvelope(
    val format: String,
    val version: Int,
    val contentHash: String,
    val sections: JsonObject,
)

private fun readExternalBundleEnvelope(source: String): ExternalBundleEnvelope {
    val root = JsonParser(source).parse().asObject("compiled bundle root")
    val format = root.requiredString("format")
    if (format != ExternalBundleFormat) externalBundleFail("unsupported bundle format $format")
    val version = root.requiredInt("schemaVersion")
    if (version != ExternalBundleVersion) externalBundleFail("unsupported bundle schema version $version")

    val order = root.requiredArray("sectionOrder").values.map { it.asString("sectionOrder entry") }
    val expectedOrder = ExternalBundleSectionId.entries.map { it.jsonName }
    if (order != expectedOrder) externalBundleFail("section order does not match schema")

    val sections = root.requiredObject("sections")
    val sectionNames = sections.entries.map { it.first }
    if (sectionNames != expectedOrder) externalBundleFail("sections object does not match schema order")

    val contentHash = root.requiredString("bundleContentHash")
    val actualHash = sections.compactBytes().sha256()
    if (contentHash != actualHash) externalBundleFail("bundle content hash does not match sections")

    return ExternalBundleEnvelope(
        format = format,
        version = version,
        contentHash = contentHash,
        sections = sections,
    )
}

private fun externalBundleFail(message: String): Nothing =
    error("compiled bundle artifact loader failed: $message")

private class ExternalBundleArtifactMaterializer(
    private val source: String,
) {
    private val decoder = ExternalBundleObjectDecoder()

    fun readCompiledBundle(): CompiledGameBundle =
        readCompiledBundle(readExternalBundleEnvelope(source).sections)

    fun readRuntimeGame(): RylweenModel {
        val sections = readExternalBundleEnvelope(source).sections
        val runtimeGame = decoder.decode<RylweenModel>(sections.required(ExternalBundleSectionId.RuntimeFacade.jsonName))
        val compiledFromSections = readCompiledBundle(sections)
        val compiledFromRuntimeFacade = runtimeGame.toCompiledGameBundle()
        require(compiledFromRuntimeFacade == compiledFromSections) {
            "runtime facade does not match compiled bundle sections: " +
                compiledFromRuntimeFacade.firstDifference(compiledFromSections)
        }
        return runtimeGame
    }

    private fun readCompiledBundle(sections: JsonObject): CompiledGameBundle {
        val strings = readStrings(sections.requiredArray(ExternalBundleSectionId.Strings.jsonName))
        val handles = readHandles(sections.requiredArray(ExternalBundleSectionId.Handles.jsonName))
        val content = CompiledContentBundle(
            universe = readUniverse(sections.requiredObject(ExternalBundleSectionId.Universe.jsonName)),
            limits = sections.decodeSection(ExternalBundleSectionId.Limits),
            strings = strings,
            handles = handles,
            flags = sections.decodeSectionList(ExternalBundleSectionId.Flags),
            items = sections.decodeSectionList(ExternalBundleSectionId.Items),
            itemPools = sections.decodeSectionList(ExternalBundleSectionId.ItemPools),
            formulas = sections.decodeSectionList(ExternalBundleSectionId.Formulas),
            rewards = sections.decodeSectionList(ExternalBundleSectionId.Rewards),
            terrain = sections.decodeSectionList(ExternalBundleSectionId.Terrain),
            worldMaps = sections.decodeSectionList(ExternalBundleSectionId.WorldMaps),
            images = sections.decodeSectionList(ExternalBundleSectionId.Images),
            staticScreens = sections.decodeSectionList(ExternalBundleSectionId.StaticScreens),
            screenRegions = sections.decodeSectionList(ExternalBundleSectionId.ScreenRegions),
            towns = sections.decodeSectionList(ExternalBundleSectionId.Towns),
            buildingServices = sections.decodeSectionList(ExternalBundleSectionId.BuildingServices),
            characters = sections.decodeSectionList(ExternalBundleSectionId.Characters),
            monsters = sections.decodeSectionList(ExternalBundleSectionId.Monsters),
            missions = sections.decodeSectionList(ExternalBundleSectionId.Missions),
            spells = sections.decodeSectionList(ExternalBundleSectionId.Spells),
            itemUses = sections.decodeSectionList(ExternalBundleSectionId.ItemUses),
            dungeonEvents = sections.decodeSectionList(ExternalBundleSectionId.DungeonEvents),
            dungeonScenarios = sections.decodeSectionList(ExternalBundleSectionId.DungeonScenarios),
            scripts = CompiledScriptBundle(sections.decodeSectionList(ExternalBundleSectionId.Scripts)),
        )
        val bundle = CompiledGameBundle(
            content = content,
            states = sections.decodeSectionList(ExternalBundleSectionId.States),
        )
        bundle.loadCompiledGame()
        return bundle
    }

    private fun readUniverse(value: JsonObject): CompiledUniverse =
        CompiledUniverse(
            nameString = value.requiredInt("nameString"),
            versionString = value.requiredInt("versionString"),
            compatibilityIdString = value.requiredInt("compatibilityIdString"),
        )

    private fun readStrings(value: JsonArray): CompiledStringTable {
        val entries = value.values.mapIndexed { index, item ->
            val row = item.asObject("strings row")
            val id = row.requiredInt("id")
            require(id == index + 1) { "string rows must be dense and one-based; found $id at row ${index + 1}" }
            val text = row.requiredString("value")
            val hash = row.requiredInt("hash")
            require(hash == text.hashCode()) { "string row $id hash does not match value" }
            text
        }
        return CompiledStringTable(entries)
    }

    private fun readHandles(value: JsonArray): CompiledHandleTable =
        CompiledHandleTable(
            value.values.map { item ->
                val row = item.asObject("handles row")
                val kind = row.requiredEnum<CompiledHandleKind>("kind")
                val id = row.requiredInt("id")
                val denseId = row.requiredInt("denseId")
                val slugString = row.requiredInt("slugString")
                CompiledHandle(
                    ref = CompiledHandleRef(kind, id),
                    denseId = denseId,
                    slugString = slugString,
                )
            },
        )

    private inline fun <reified T : Any> JsonObject.decodeSection(id: ExternalBundleSectionId): T =
        decoder.decode(required(id.jsonName))

    private inline fun <reified T : Any> JsonObject.decodeSectionList(id: ExternalBundleSectionId): List<T> =
        requiredArray(id.jsonName).values.map { item -> decoder.decode(item) }
}

private class ExternalBundleObjectDecoder {
    private val flags = linkedMapOf<Pair<String, FlagScope>, DecodedFlagRef>()

    inline fun <reified T : Any> decode(value: JsonValue): T =
        decode(value, typeOf<T>()) as T

    fun decode(value: JsonValue, type: KType): Any? {
        if (value == JsonNull) {
            require(type.isMarkedNullable) { "non-null ${type} cannot be loaded from null" }
            return null
        }
        return when (val classifier = type.classifier) {
            String::class -> value.asString("string")
            Char::class -> value.asString("char").single()
            Boolean::class -> value.asBoolean("boolean")
            Int::class -> value.asNumber("int").toInt()
            Long::class -> value.asNumber("long").toLong()
            Float::class -> value.asNumber("float").toFloat()
            Double::class -> value.asNumber("double").toDouble()
            IntRange::class -> value.asIntRange()
            FlagRef::class -> decodeFlagRef(value)
            FlagState::class -> decodeFlagState(value)
            List::class -> value.asArray("list").values.map { item -> decode(item, type.singleArgument("list")) }
            Set::class -> value.asArray("set").values.map { item -> decode(item, type.singleArgument("set")) }.toSet()
            Map::class -> decodeMap(value, type)
            is KClass<*> -> decodeClass(value, classifier)
            else -> error("unsupported compiled JSON target type $type")
        }
    }

    private fun decodeFlagRef(value: JsonValue): FlagRef {
        val obj = value.asObject("flag ref")
        val slug = obj.requiredString("slug")
        val scope = obj.optionalString("scope")?.let { enumValueOf<FlagScope>(it) } ?: FlagScope.Party
        val states = obj.optionalArray("states")
            ?.values
            ?.map { state -> state.asString("flag state native") }
            .orEmpty()
        return flagRef(slug, scope, states)
    }

    private fun decodeFlagState(value: JsonValue): FlagState {
        val obj = value.asObject("flag state")
        val slug = obj.requiredString("flag")
        val scope = obj.optionalString("scope")?.let { enumValueOf<FlagScope>(it) } ?: FlagScope.Party
        return flagRef(slug, scope, listOf(obj.requiredString("native"))).value(obj.requiredString("native"))
    }

    private fun flagRef(slug: String, scope: FlagScope, stateNatives: List<String>): DecodedFlagRef {
        val ref = flags.getOrPut(slug to scope) { DecodedFlagRef(slug, scope) }
        stateNatives.forEach(ref::value)
        return ref
    }

    private fun decodeMap(value: JsonValue, type: KType): Map<Any?, Any?> {
        val keyType = type.argument(index = 0, owner = "map key")
        val valueType = type.argument(index = 1, owner = "map value")
        return value.asArray("map").values.associate { item ->
            val row = item.asObject("map entry")
            decode(row.required("key"), keyType) to decode(row.required("value"), valueType)
        }
    }

    private fun decodeClass(value: JsonValue, klass: KClass<*>): Any {
        if (klass.java.isEnum) return value.asEnum(klass)
        if (klass.isSealed) return decodeSealed(value, klass)
        klass.objectInstance?.let { instance ->
            value.expectObjectType(klass)
            return instance
        }
        return decodeDataClass(value, klass)
    }

    private fun decodeSealed(value: JsonValue, klass: KClass<*>): Any {
        val encodedType = value.asObject("${klass.simpleName} value").requiredString("_type")
        val subtype = klass.sealedSubclasses.firstOrNull { candidate -> candidate.simpleName == encodedType }
            ?: error("unknown ${klass.simpleName} subtype $encodedType")
        return decode(value, subtype.createType(nullable = false))
            ?: error("sealed subtype $encodedType decoded to null")
    }

    private fun decodeDataClass(value: JsonValue, klass: KClass<*>): Any {
        val obj = value.asObject("${klass.simpleName} value")
        obj.optionalString("_type")?.let { encodedType ->
            require(encodedType == klass.simpleName) { "expected ${klass.simpleName}, found $encodedType" }
        }
        val constructor = klass.primaryConstructor ?: error("missing primary constructor for ${klass.qualifiedName}")
        constructor.isAccessible = true
        val args = buildMap {
            constructor.parameters.forEach { parameter ->
                val field = parameter.name?.let(obj::optional)
                when {
                    field != null -> put(parameter, decode(field, parameter.type))
                    parameter.isOptional -> Unit
                    parameter.type.isMarkedNullable -> put(parameter, null)
                    else -> error("missing ${klass.simpleName}.${parameter.name}")
                }
            }
        }
        return constructor.callBy(args)
    }

    private fun KType.singleArgument(owner: String): KType =
        argument(index = 0, owner = owner)

    private fun KType.argument(index: Int, owner: String): KType =
        arguments.getOrNull(index)?.type ?: error("missing $owner type argument for $this")
}

private fun CompiledGameBundle.firstDifference(other: CompiledGameBundle): String =
    when {
        content.universe != other.content.universe -> "universe"
        content.limits != other.content.limits -> "limits"
        content.strings != other.content.strings -> "strings"
        content.handles != other.content.handles -> "handles"
        content.flags != other.content.flags -> "flags"
        content.items != other.content.items -> "items"
        content.itemPools != other.content.itemPools -> "itemPools"
        content.formulas != other.content.formulas -> "formulas"
        content.rewards != other.content.rewards -> "rewards"
        content.terrain != other.content.terrain -> "terrain"
        content.worldMaps != other.content.worldMaps -> "worldMaps"
        content.images != other.content.images -> "images"
        content.staticScreens != other.content.staticScreens -> "staticScreens"
        content.screenRegions != other.content.screenRegions -> "screenRegions"
        content.towns != other.content.towns -> "towns"
        content.buildingServices != other.content.buildingServices -> "buildingServices"
        content.characters != other.content.characters -> "characters"
        content.monsters != other.content.monsters -> "monsters"
        content.missions != other.content.missions -> "missions"
        content.spells != other.content.spells -> "spells"
        content.itemUses != other.content.itemUses -> "itemUses"
        content.dungeonEvents != other.content.dungeonEvents -> "dungeonEvents"
        content.dungeonScenarios != other.content.dungeonScenarios -> "dungeonScenarios"
        content.scripts != other.content.scripts -> content.scripts.firstDifference(other.content.scripts)
        states != other.states -> "states"
        else -> "unknown"
    }

private fun CompiledScriptBundle.firstDifference(other: CompiledScriptBundle): String {
    if (roots.size != other.roots.size) return "scripts size ${roots.size} != ${other.roots.size}"
    roots.zip(other.roots).forEachIndexed { index, (left, right) ->
        if (left != right) {
            return when {
                left.root != right.root -> "script[$index] root ${left.root} != ${right.root}"
                left.rootHandle != right.rootHandle -> "script[$index] handle ${left.rootHandle} != ${right.rootHandle}"
                left.instructions.size != right.instructions.size ->
                    "script[$index] ${left.root.slug} instruction size ${left.instructions.size} != ${right.instructions.size}"
                else -> {
                    val opIndex = left.instructions.zip(right.instructions).indexOfFirst { (l, r) -> l != r }
                    "script[$index] ${left.root.slug} instruction[$opIndex] " +
                        "${left.instructions.getOrNull(opIndex)} != ${right.instructions.getOrNull(opIndex)}"
                }
            }
        }
    }
    return "scripts"
}

private class DecodedFlagRef(
    slug: String,
    scope: FlagScope,
) : FlagRef(slug, scope) {
    fun value(native: String): FlagState =
        states.firstOrNull { state -> state.native == native } ?: state(native)
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
        is FlagRef -> jsonObject(
            "_type" to JsonString("FlagRef"),
            "slug" to JsonString(value.slug),
            "scope" to JsonString(value.scope.name),
            "states" to JsonArray(value.states.map { state -> JsonString(state.native) }),
        )
        is FlagState -> jsonObject(
            "_type" to JsonString("FlagState"),
            "flag" to JsonString(value.flag.slug),
            "scope" to JsonString(value.flag.scope.name),
            "native" to JsonString(value.native),
        )
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

private fun JsonValue.asArray(label: String): JsonArray =
    this as? JsonArray ?: error("expected array for $label")

private fun JsonValue.asString(label: String): String =
    (this as? JsonString)?.value ?: error("expected string for $label")

private fun JsonValue.asNumber(label: String): String =
    (this as? JsonNumber)?.value ?: error("expected number for $label")

private fun JsonValue.asBoolean(label: String): Boolean =
    (this as? JsonBoolean)?.value ?: error("expected boolean for $label")

private fun JsonValue.asIntRange(): IntRange {
    val obj = asObject("int range")
    return obj.requiredInt("first")..obj.requiredInt("last")
}

private fun JsonValue.expectObjectType(klass: KClass<*>) {
    val obj = asObject("${klass.simpleName} value")
    obj.optionalString("_type")?.let { encodedType ->
        require(encodedType == klass.simpleName) { "expected ${klass.simpleName}, found $encodedType" }
    }
}

@Suppress("UNCHECKED_CAST")
private fun JsonValue.asEnum(klass: KClass<*>): Any {
    val name = asString("${klass.simpleName} enum")
    val enumClass = klass.java as Class<out Enum<*>>
    return enumClass.enumConstants.firstOrNull { constant -> constant.name == name }
        ?: error("unknown ${klass.simpleName} enum value $name")
}

private fun JsonObject.required(name: String): JsonValue =
    entries.firstOrNull { it.first == name }?.second ?: error("missing JSON field $name")

private fun JsonObject.optional(name: String): JsonValue? =
    entries.firstOrNull { it.first == name }?.second

private fun JsonObject.requiredObject(name: String): JsonObject =
    required(name).asObject(name)

private fun JsonObject.requiredArray(name: String): JsonArray =
    required(name) as? JsonArray ?: error("expected array for $name")

private fun JsonObject.optionalArray(name: String): JsonArray? =
    optional(name) as? JsonArray

private fun JsonObject.requiredString(name: String): String =
    required(name).asString(name)

private fun JsonObject.requiredInt(name: String): Int =
    (required(name) as? JsonNumber)?.value?.toIntOrNull() ?: error("expected integer for $name")

private fun JsonObject.optionalString(name: String): String? =
    optional(name)?.asString(name)

private inline fun <reified T : Enum<T>> JsonObject.requiredEnum(name: String): T =
    enumValueOf(requiredString(name))

internal fun ByteArray.fnv1a32(): Int {
    var hash = 0x811c9dc5.toInt()
    forEach { byte ->
        hash = hash xor (byte.toInt() and 0xff)
        hash *= 0x01000193
    }
    return hash
}
