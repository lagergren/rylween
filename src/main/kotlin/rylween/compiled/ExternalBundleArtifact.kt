/**
 * Deterministic external J1 bundle artifact writer, reader, and inspector.
 *
 * The canonical artifact is strict JSON emitted from `CompiledBundle.kt`.
 * Kotlin and C/native readers validate the same section order, schema version,
 * hashes, runtime-system direct schemas, and runtime facade compatibility.
 */
package rylween.compiled

import rylween.dsl.BoolAst
import rylween.dsl.CampRulesModel
import rylween.dsl.CastleContentModel
import rylween.dsl.CastleTextModel
import rylween.dsl.DungeonSystemModel
import rylween.dsl.EngineServiceUse
import rylween.dsl.FlagRef
import rylween.dsl.FlagScope
import rylween.dsl.FlagState
import rylween.dsl.GameModel
import rylween.dsl.GamePromptsModel
import rylween.dsl.GridPoint
import rylween.dsl.HuntRulesModel
import rylween.dsl.InputBindingModel
import rylween.dsl.IntAst
import rylween.dsl.JobTextModel
import rylween.dsl.NativeStringArg
import rylween.dsl.RealmContentModel
import rylween.dsl.Resource
import rylween.dsl.RylweenModel
import rylween.dsl.SpeakSystemModel
import rylween.dsl.SpeakTextTablesModel
import rylween.dsl.StandardConversationModel
import rylween.dsl.StrAst
import rylween.dsl.TemperatureBand
import rylween.dsl.TextBlock
import rylween.dsl.TextPart
import rylween.dsl.TownSystemModel
import rylween.dsl.WildernessSystemModel
import rylween.dsl.WildernessUpkeepRules
import rylween.dsl.building
import rylween.dsl.castle
import rylween.dsl.dungeon
import rylween.dsl.image
import rylween.dsl.item
import rylween.dsl.line
import rylween.dsl.lines
import rylween.dsl.location
import rylween.dsl.milestone
import rylween.dsl.monster
import rylween.dsl.partyName
import rylween.dsl.render
import rylween.dsl.rumour
import rylween.dsl.terrain
import rylween.dsl.text
import rylween.dsl.town
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

private const val EXTERNAL_BUNDLE_FORMAT = "rylween.compiled.bundle"
private const val EXTERNAL_BUNDLE_VERSION = 3

/** Serializes this game into the canonical strict JSON J1 artifact bytes. */
fun RylweenModel.toExternalBundleArtifact(): ByteArray = ExternalBundleArtifactWriter().write(this)

/** Writes this game as the canonical strict JSON J1 artifact. */
fun RylweenModel.writeExternalBundleArtifact(file: File) {
    file.parentFile?.mkdirs()
    file.writeBytes(toExternalBundleArtifact())
}

/** Reads only the artifact summary and section metadata from strict JSON bytes. */
fun ByteArray.readExternalBundleArtifact(): ExternalBundleArtifactSummary = ExternalBundleArtifactReader(decodeToString()).read()

/** Reads only the artifact summary and section metadata from a strict JSON file. */
fun File.readExternalBundleArtifact(): ExternalBundleArtifactSummary = readBytes().readExternalBundleArtifact()

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
            "compiled bundle artifact $path does not match current compiled game content: " +
                "expected ${expectedSummary.bundleContentHash}, found ${actual.bundleContentHash}",
        )
    }
    if (actual.sections != expectedSummary.sections) {
        error("compiled bundle artifact $path section summaries do not match current compiled game content")
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

/** Reads the J1 strict JSON artifact file back into the typed compiled bundle model. */
fun File.readExternalCompiledGameBundle(): CompiledGameBundle = readBytes().readExternalCompiledGameBundle()

/** Reads and verifies only the J1 strict JSON artifact content hash. */
fun ByteArray.readExternalCompiledBundleContentHash(): String = readExternalBundleEnvelope(decodeToString()).contentHash

/** Reads and verifies only the J1 strict JSON artifact file content hash. */
fun File.readExternalCompiledBundleContentHash(): String = readBytes().readExternalCompiledBundleContentHash()

/**
 * Reads the J1 strict JSON artifact into the runtime-facing Kotlin game model.
 *
 * The `runtimeFacade` section carries the complete data graph the current
 * Kotlin session/runtime layer consumes: prompts, terminal presentation,
 * realm/upkeep balance, authored content, castle content, and start
 * states. The loader recompiles that facade back into `CompiledGameBundle` and
 * requires it to match the normalized compiled sections, so the facade cannot
 * silently drift from the dense table bundle. This is deliberately still a
 * Kotlin runtime facade, not the final stable row schema a non-Kotlin runtime
 * would implement directly.
 */
fun ByteArray.readExternalRuntimeGame(): RylweenModel = ExternalBundleArtifactMaterializer(decodeToString()).readRuntimeGame()

/** Reads the J1 strict JSON artifact file into the runtime-facing Kotlin game model. */
fun File.readExternalRuntimeGame(): RylweenModel = readBytes().readExternalRuntimeGame()

/**
 * Reads and validates the stable, non-reflective runtime-system section.
 *
 * This is the part of the JSON artifact meant to grow into a direct non-Kotlin
 * runtime contract. It is intentionally separate from `runtimeFacade`, which is
 * complete for the Kotlin runtime but follows Kotlin model shapes.
 */
fun ByteArray.readExternalRuntimeSystemsSummary(): ExternalRuntimeSystemsSummary = ExternalRuntimeSystemsReader(decodeToString()).read()

/** Reads the stable runtime-system summary from a strict JSON artifact file. */
fun File.readExternalRuntimeSystemsSummary(): ExternalRuntimeSystemsSummary = readBytes().readExternalRuntimeSystemsSummary()

/** Reads the stable runtime-system data needed by direct bundle runtimes. */
fun ByteArray.readExternalRuntimeSystems(): ExternalRuntimeSystems = ExternalRuntimeSystemsReader(decodeToString()).readSystems()

/** Reads the stable runtime-system data needed by direct bundle runtimes from a strict JSON artifact file. */
fun File.readExternalRuntimeSystems(): ExternalRuntimeSystems = readBytes().readExternalRuntimeSystems()

/** Renders a human-readable inspection report from strict JSON artifact bytes. */
fun ByteArray.inspectExternalBundleArtifact(): String = readExternalBundleArtifact().renderExternalBundleInspection()

/** Renders a human-readable inspection report from a strict JSON artifact file. */
fun File.inspectExternalBundleArtifact(): String = readBytes().inspectExternalBundleArtifact()

/** Summary and drift-check metadata for one external bundle artifact. */
data class ExternalBundleArtifactSummary(
    val format: String,
    val version: Int,
    val bundleContentHash: String,
    val universeName: String,
    val universeVersion: String,
    val universeCompatibilityId: String,
    val sections: List<ExternalBundleSectionSummary>,
) {
    /** Returns the summary for a required artifact section. */
    fun section(id: ExternalBundleSectionId): ExternalBundleSectionSummary = sections.single { it.id == id }

    /** Renders this summary as the CLI inspection text. */
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

/** Summary row for one external bundle section. */
data class ExternalBundleSectionSummary(
    val id: ExternalBundleSectionId,
    val rowCount: Int,
    val byteLength: Int,
    val checksum: Int,
) {
    val checksumHex: String =
        checksum.toUInt().toString(radix = 16).padStart(length = 8, padChar = '0')
}

/** Stable non-reflective runtime-system summary from the external artifact. */
data class ExternalRuntimeSystemsSummary(
    val schemaVersion: Int,
    val commandGroups: List<String>,
    val promptGroups: List<String>,
    val castles: List<String>,
)

/** Stable runtime-system section consumed by runtimes that do not use the Kotlin runtime facade. */
data class ExternalRuntimeSystems(
    val schemaVersion: Int,
    val commandGroups: List<String>,
    val promptGroups: List<String>,
    val castles: List<String>,
    val commands: Map<String, List<ExternalRuntimeCommand>>,
    val realm: ExternalRuntimeRealm,
    val presentation: ExternalRuntimePresentation,
)

/** One command binding row from the stable runtime-system section. */
data class ExternalRuntimeCommand(
    val action: String,
    val binding: ExternalRuntimeInputBinding,
)

/** Portable input binding row consumed by direct bundle runtimes. */
data class ExternalRuntimeInputBinding(
    val primary: String,
    val aliases: List<String>,
    val label: String,
)

/** Realm-wide runtime constants consumed by standalone bundle runtimes. */
data class ExternalRuntimeRealm(
    val baseResourceCapacity: Map<Resource, Int>,
)

/** Terminal presentation data in the stable runtime-system section. */
data class ExternalRuntimePresentation(
    val terminalTerrain: List<ExternalTerminalTerrainStyle>,
    val regions: List<ExternalScreenRegion>,
    val images: List<ExternalImageAsset>,
    val staticScreens: List<ExternalStaticScreen>,
)

/** Terminal glyph/color mapping for one terrain slug. */
data class ExternalTerminalTerrainStyle(
    val terrain: String,
    val glyph: String,
    val color: String,
)

/** Screen region row from the stable runtime-system presentation data. */
data class ExternalScreenRegion(
    val id: String,
    val x: Int,
    val y: Int,
    val width: Int?,
    val height: Int?,
)

/** ANSI or bitmap image resource row from the stable runtime-system presentation data. */
data class ExternalImageAsset(
    val id: String,
    val kind: String,
    val resource: String,
    val placement: String,
)

/** Static screen route from the stable runtime-system presentation data. */
data class ExternalStaticScreen(
    val id: String,
    val image: String?,
    val resource: String?,
    val commandHelp: Boolean,
    val runtimeReport: String?,
)

/** Ordered section ids emitted by the strict JSON artifact. */
enum class ExternalBundleSectionId(
    val jsonName: String,
) {
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
    RuntimeSystems("runtimeSystems"),
    RuntimeFacade("runtimeFacade"),
    States("states"),
}

private class ExternalBundleArtifactWriter {
    fun write(game: RylweenModel): ByteArray {
        val sections = game.toCompiledGameBundle().sectionsJson(game)
        val root =
            jsonObject(
                "format" to JsonString(EXTERNAL_BUNDLE_FORMAT),
                "schemaVersion" to JsonNumber(EXTERNAL_BUNDLE_VERSION.toString()),
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
                ExternalBundleSectionId.Strings.jsonName to
                    JsonArray(
                        strings.entries.mapIndexed { index, value ->
                            jsonObject(
                                "id" to JsonNumber((index + 1).toString()),
                                "value" to JsonString(value),
                                "hash" to JsonNumber(value.hashCode().toString()),
                            )
                        },
                    ),
                ExternalBundleSectionId.Handles.jsonName to
                    JsonArray(
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
                ExternalBundleSectionId.Scripts.jsonName to
                    compiledJson(
                        scripts.roots.sortedWith(compareBy({ it.rootHandle.kind.ordinal }, { it.rootHandle.id })),
                    ),
                ExternalBundleSectionId.RuntimeSystems.jsonName to runtimeSystemsJson(game.content),
                ExternalBundleSectionId.RuntimeFacade.jsonName to compiledJson(game),
                ExternalBundleSectionId.States.jsonName to
                    compiledJson(
                        states.sortedWith(compareBy({ it.state.kind.ordinal }, { it.state.id })),
                    ),
            )
        }

    private fun universeJson(
        universe: CompiledUniverse,
        strings: CompiledStringTable,
    ): JsonValue =
        jsonObject(
            "nameString" to JsonNumber(universe.nameString.toString()),
            "versionString" to JsonNumber(universe.versionString.toString()),
            "compatibilityIdString" to JsonNumber(universe.compatibilityIdString.toString()),
            "name" to JsonString(strings.value(universe.nameString)),
            "version" to JsonString(strings.value(universe.versionString)),
            "compatibilityId" to JsonString(strings.value(universe.compatibilityIdString)),
        )

    private fun runtimeSystemsJson(game: GameModel): JsonValue =
        jsonObject(
            "schemaVersion" to JsonNumber("1"),
            "realm" to realmJson(game.realm),
            "prompts" to promptsJson(game.prompts),
            "presentation" to presentationJson(game),
            "commands" to commandsJson(game),
            "wilderness" to wildernessJson(game.wilderness),
            "dungeon" to dungeonRuntimeJson(game.dungeons),
            "town" to townRuntimeJson(game.townSystem),
            "speak" to speakRuntimeJson(game.speak),
            "castles" to JsonArray(game.castles.map(::castleJson)),
        )

    private fun realmJson(realm: RealmContentModel): JsonValue =
        jsonObject(
            "baseResourceCapacity" to
                JsonArray(
                    realm.baseResourceCapacity.entries.sortedBy { it.key.name }.map { (resource, capacity) ->
                        jsonObject("resource" to JsonString(resource.name), "capacity" to JsonNumber(capacity.toString()))
                    },
                ),
            "upkeep" to upkeepJson(realm.upkeep),
            "camp" to campJson(realm.camp),
            "hunt" to realm.hunt?.let(::huntJson).orJsonNull(),
        )

    private fun upkeepJson(upkeep: WildernessUpkeepRules): JsonValue =
        jsonObject(
            "foodCostLeaderDiv" to JsonNumber(upkeep.foodCostLeaderDiv.toString()),
            "foodTickEvery" to JsonNumber(upkeep.foodTickEvery.toString()),
            "starveFoodThreshold" to JsonNumber(upkeep.starveFoodThreshold.toString()),
            "starveDamage" to JsonNumber(upkeep.starveDamage.toString()),
            "manaRegenChance" to JsonNumber(upkeep.manaRegenChance.toString()),
            "restedHoursCostPerTurn" to JsonNumber(upkeep.restedHoursCostPerTurn.toString()),
            "fatigue" to
                JsonArray(
                    upkeep.fatigue.map { rule ->
                        jsonObject(
                            "threshold" to JsonNumber(rule.threshold.toString()),
                            "match" to JsonString(rule.match.name),
                            "drain" to JsonBoolean(rule.drain),
                            "text" to textBlockJson(rule.text),
                        )
                    },
                ),
        )

    private fun campJson(camp: CampRulesModel): JsonValue =
        jsonObject(
            "healthGain" to JsonNumber(camp.healthGain.toString()),
            "foodCostPerMember" to JsonNumber(camp.foodCostPerMember.toString()),
            "hoursAdvanced" to JsonNumber(camp.hoursAdvanced.toString()),
            "hoursRestedReset" to JsonNumber(camp.hoursRestedReset.toString()),
            "temperatureMax" to JsonNumber(camp.temperatureMax.toString()),
            "temperatureBands" to
                JsonArray(
                    camp.temperatureBands.map { range ->
                        jsonObject(
                            "band" to JsonString(range.band.name),
                            "first" to JsonNumber(range.range.first.toString()),
                            "last" to JsonNumber(range.range.last.toString()),
                        )
                    },
                ),
            "temperatureDamage" to temperatureIntMapJson(camp.temperatureDamage),
            "ambushChancePercent" to JsonNumber(camp.ambushChancePercent.toString()),
            "ambushTier" to camp.ambushTier?.let { JsonString(it.slug) }.orJsonNull(),
            "text" to
                jsonObject(
                    "notTired" to textBlockJson(camp.notTired),
                    "notEnoughFood" to textBlockJson(camp.notEnoughFood),
                    "guardPrompt" to textBlockJson(camp.guardPrompt),
                    "guardMemberPrompt" to textBlockJson(camp.guardMemberPrompt),
                    "guardMemberDead" to textBlockJson(camp.guardMemberDead),
                    "standsGuard" to textBlockJson(camp.standsGuard),
                    "sleeping" to textBlockJson(camp.sleeping),
                    "rested" to textBlockJson(camp.rested),
                    "ambush" to textBlockJson(camp.ambush),
                    "surpriseKill" to textBlockJson(camp.surpriseKill),
                    "temperatureMessage" to temperatureTextMapJson(camp.temperatureMessage),
                    "noShelterMessage" to temperatureTextMapJson(camp.noShelterMessage),
                ),
        )

    private fun huntJson(hunt: HuntRulesModel): JsonValue =
        jsonObject(
            "successChancePercent" to JsonNumber(hunt.successChancePercent.toString()),
            "timeCost" to JsonNumber(hunt.timeCost.toString()),
            "foodMin" to JsonNumber(hunt.foodMin.toString()),
            "foodMax" to JsonNumber(hunt.foodMax.toString()),
            "text" to
                jsonObject(
                    "whoPrompt" to textBlockJson(hunt.whoPrompt),
                    "dead" to textBlockJson(hunt.dead),
                    "notRanger" to textBlockJson(hunt.notRanger),
                    "tooFewMoves" to textBlockJson(hunt.tooFewMoves),
                    "tries" to textBlockJson(hunt.tries),
                    "succeeds" to textBlockJson(hunt.succeeds),
                    "fails" to textBlockJson(hunt.fails),
                ),
        )

    private fun promptsJson(prompts: GamePromptsModel): JsonValue =
        jsonObject(
            "pauseVerbs" to stringArray(prompts.pauseVerbs),
            "returnKeyName" to JsonString(prompts.returnKeyName),
            "groups" to
                JsonArray(
                    listOf(
                        promptGroupJson(
                            "save",
                            textField("saving", prompts.save.saving),
                            textField("done", prompts.save.done),
                            textField("failed", prompts.save.failed),
                        ),
                        promptGroupJson(
                            "characterCreation",
                            textField("header", prompts.characterCreation.header),
                            textField("partyName", prompts.characterCreation.partyName),
                            textField("statsPage", prompts.characterCreation.statsPage),
                            textField("reroll", prompts.characterCreation.reroll),
                            textField("memberName", prompts.characterCreation.memberName),
                            textField("gender", prompts.characterCreation.gender),
                            stringField("genderMaleLabel", prompts.characterCreation.genderMaleLabel),
                            stringField("genderFemaleLabel", prompts.characterCreation.genderFemaleLabel),
                            textField("race", prompts.characterCreation.race),
                            textField("classMenu", prompts.characterCreation.classMenu),
                            textField("saving", prompts.characterCreation.saving),
                        ),
                        promptGroupJson(
                            "priest",
                            textField("who", prompts.priest.who),
                            textField("mageCombatOnly", prompts.priest.mageCombatOnly),
                            textField("wrongClass", prompts.priest.wrongClass),
                            textField("dead", prompts.priest.dead),
                            textField("invalidCaster", prompts.priest.invalidCaster),
                            textField("invalidSpell", prompts.priest.invalidSpell),
                            textField("noSpells", prompts.priest.noSpells),
                        ),
                        promptGroupJson(
                            "inventoryUse",
                            textField("use", prompts.inventoryUse.use),
                            textField("quaff", prompts.inventoryUse.quaff),
                            textField("noItems", prompts.inventoryUse.noItems),
                            textField("noItem", prompts.inventoryUse.noItem),
                            stringField("invalidSlotFirstPrefix", prompts.inventoryUse.invalidSlotFirstPrefix),
                            stringField("invalidSlotFirstSuffix", prompts.inventoryUse.invalidSlotFirstSuffix),
                            stringField("invalidSlotSecond", prompts.inventoryUse.invalidSlotSecond),
                            stringField("notUsableFirst", prompts.inventoryUse.notUsableFirst),
                            stringField("notUsableSecondPrefix", prompts.inventoryUse.notUsableSecondPrefix),
                            stringField("notUsableSecondSuffix", prompts.inventoryUse.notUsableSecondSuffix),
                        ),
                        promptGroupJson(
                            "equipment",
                            textField("inventory", prompts.equipment.inventory),
                            textField("who", prompts.equipment.who),
                            textField("readyWeapons", prompts.equipment.readyWeapons),
                            textField("readyArmors", prompts.equipment.readyArmors),
                        ),
                        promptGroupJson(
                            "town",
                            stringField("unavailableSuffix", prompts.town.unavailableSuffix),
                            stringField("closedSuffix", prompts.town.closedSuffix),
                            textField("entryWelcome", prompts.town.entryWelcome),
                            textField("entryTax", prompts.town.entryTax),
                            textField("entryTaxThanks", prompts.town.entryTaxThanks),
                        ),
                        promptGroupJson(
                            "party",
                            textField("viewWho", prompts.party.viewWho),
                            textField("journalHeader", prompts.party.journalHeader),
                            textField("dropPrompt", prompts.party.dropPrompt),
                            textField("dropEmptySlot", prompts.party.dropEmptySlot),
                            textField("dropAborted", prompts.party.dropAborted),
                            textField("dropDropped", prompts.party.dropDropped),
                            textField("dropCapacityReduced", prompts.party.dropCapacityReduced),
                        ),
                        promptGroupJson(
                            "wilderness",
                            textField("pass", prompts.wilderness.pass),
                            textField("noMoves", prompts.wilderness.noMoves),
                            textField("outOfMoves", prompts.wilderness.outOfMoves),
                            textField("notInWilderness", prompts.wilderness.notInWilderness),
                            textField("nothingToEnter", prompts.wilderness.nothingToEnter),
                            textField("quitConfirm", prompts.wilderness.quitConfirm),
                            textField("dungeonHandoff", prompts.wilderness.dungeonHandoff),
                        ),
                        promptGroupJson(
                            "dungeon",
                            textField("levelMenu", prompts.dungeon.levelMenu),
                            textField("enterLevel", prompts.dungeon.enterLevel),
                            textField("atMaxDepth", prompts.dungeon.atMaxDepth),
                            textField("noTorches", prompts.dungeon.noTorches),
                            textField("leaveCavern", prompts.dungeon.leaveCavern),
                        ),
                    ),
                ),
        )

    private fun presentationJson(game: GameModel): JsonValue =
        jsonObject(
            "terminalTerrain" to
                JsonArray(
                    game.terminal.terrain.sortedBy { it.terrain.slug }.map { style ->
                        jsonObject(
                            "terrain" to JsonString(style.terrain.slug),
                            "glyph" to JsonString(style.glyph),
                            "color" to JsonString(style.color.slug),
                        )
                    },
                ),
            "regions" to
                JsonArray(
                    game.layouts.regions.sortedBy { it.id.slug }.map { region ->
                        jsonObject(
                            "id" to JsonString(region.id.slug),
                            "x" to JsonNumber(region.x.toString()),
                            "y" to JsonNumber(region.y.toString()),
                            "width" to region.width?.let { JsonNumber(it.toString()) }.orJsonNull(),
                            "height" to region.height?.let { JsonNumber(it.toString()) }.orJsonNull(),
                        )
                    },
                ),
            "images" to
                JsonArray(
                    game.visuals.images.sortedBy { it.id.slug }.map { image ->
                        jsonObject(
                            "id" to JsonString(image.id.slug),
                            "kind" to JsonString(image.kind.name),
                            "resource" to JsonString(image.resource),
                            "placement" to JsonString(image.placement.name),
                        )
                    },
                ),
            "staticScreens" to
                JsonArray(
                    game.visuals.staticScreens.sortedBy { it.id.slug }.map { screen ->
                        jsonObject(
                            "id" to JsonString(screen.id.slug),
                            "image" to screen.image?.let { JsonString(it.slug) }.orJsonNull(),
                            "resource" to screen.resource?.let(::JsonString).orJsonNull(),
                            "commandHelp" to JsonBoolean(screen.commandHelp),
                            "runtimeReport" to screen.runtimeReport?.let { JsonString(it.name) }.orJsonNull(),
                        )
                    },
                ),
        )

    private fun commandsJson(game: GameModel): JsonValue =
        jsonObject(
            "wilderness" to
                commandListJson(
                    "moveNorth" to game.wilderness.keys.moveNorth,
                    "moveSouth" to game.wilderness.keys.moveSouth,
                    "moveWest" to game.wilderness.keys.moveWest,
                    "moveEast" to game.wilderness.keys.moveEast,
                    "pass" to game.wilderness.keys.pass,
                    "save" to game.wilderness.keys.save,
                    "readyWeapon" to game.wilderness.keys.readyWeapon,
                    "readyArmor" to game.wilderness.keys.readyArmor,
                    "redraw" to game.wilderness.keys.redraw,
                    "journal" to game.wilderness.keys.journal,
                    "help" to game.wilderness.keys.help,
                    "hunt" to game.wilderness.keys.hunt,
                    "viewParty" to game.wilderness.keys.viewParty,
                    "removeArmor" to game.wilderness.keys.removeArmor,
                    "removeWeapon" to game.wilderness.keys.removeWeapon,
                    "enter" to game.wilderness.keys.enter,
                    "drop" to game.wilderness.keys.drop,
                    "camp" to game.wilderness.keys.camp,
                    "castSpell" to game.wilderness.keys.castSpell,
                    "quaff" to game.wilderness.keys.quaff,
                    "quit" to game.wilderness.keys.quit,
                ),
            "wilderness.cave" to
                commandListJson(
                    "enter" to game.wilderness.keys.caveEnter,
                    "leave" to game.wilderness.keys.caveLeave,
                ),
            "wilderness.outOfMoves" to
                commandListJson(
                    "save" to game.wilderness.keys.outOfMovesSave,
                    "quit" to game.wilderness.keys.outOfMovesQuit,
                ),
            "wilderness.quitConfirm" to
                commandListJson(
                    "yes" to game.wilderness.keys.quitYes,
                    "no" to game.wilderness.keys.quitNo,
                ),
            "dungeon" to
                commandListJson(
                    "descend" to game.dungeons.keys.descend,
                    "ascend" to game.dungeons.keys.ascend,
                    "help" to game.dungeons.keys.help,
                    "drop" to game.dungeons.keys.drop,
                    "removeArmor" to game.dungeons.keys.removeArmor,
                    "removeWeapon" to game.dungeons.keys.removeWeapon,
                    "readyWeapon" to game.dungeons.keys.readyWeapon,
                    "readyArmor" to game.dungeons.keys.readyArmor,
                    "quaff" to game.dungeons.keys.quaff,
                    "castSpell" to game.dungeons.keys.castSpell,
                    "viewParty" to game.dungeons.keys.viewParty,
                    "save" to game.dungeons.keys.save,
                ),
            *castleCommandJsonEntries(game).toTypedArray(),
            "town" to
                game.townSystem
                    ?.menu
                    ?.let { menu ->
                        JsonArray(
                            menu.map { action ->
                                jsonObject(
                                    "action" to JsonString(action.action.slug),
                                    "building" to action.building?.let { JsonString(it.slug) }.orJsonNull(),
                                    "endsMenu" to JsonBoolean(action.endsMenu),
                                    "binding" to bindingJson(action.binding),
                                )
                            },
                        )
                    }.orJsonNull(),
            "speak" to
                game.speak.standardConversation
                    ?.menu
                    ?.actions
                    ?.let { actions ->
                        JsonArray(
                            actions.map { action ->
                                jsonObject(
                                    "topic" to JsonString(action.topic.name),
                                    "action" to JsonString(action.action.slug),
                                    "label" to JsonString(action.label),
                                    "binding" to bindingJson(action.binding),
                                )
                            },
                        )
                    }.orJsonNull(),
            "speak.questions" to
                game.speak.standardConversation
                    ?.menu
                    ?.questions
                    ?.let { questions ->
                        JsonArray(
                            questions.map { action ->
                                jsonObject(
                                    "topic" to JsonString(action.topic.name),
                                    "action" to JsonString(action.action.slug),
                                    "label" to JsonString(action.label),
                                    "binding" to bindingJson(action.binding),
                                )
                            },
                        )
                    }.orJsonNull(),
        )

    private fun wildernessJson(wilderness: WildernessSystemModel): JsonValue =
        jsonObject(
            "commandHint" to textBlockJson(wilderness.keys.commandHint),
            "helpPages" to JsonArray(wilderness.keys.helpPages.map(::textBlockJson)),
            "events" to
                jsonObject(
                    "caveChancePercent" to JsonNumber(wilderness.events.caveChancePercent.toString()),
                    "quietChancePercent" to JsonNumber(wilderness.events.quietChancePercent.toString()),
                    "encounterSplitPercent" to JsonNumber(wilderness.events.encounterSplitPercent.toString()),
                    "easyEncounterTier" to JsonString(wilderness.events.easyEncounterTier.slug),
                    "hardEncounterTier" to JsonString(wilderness.events.hardEncounterTier.slug),
                    "caveDungeon" to JsonString(wilderness.events.caveDungeon.slug),
                    "caveDepthRange" to intRangeJson(wilderness.events.caveDepthRange),
                    "caveEntryCost" to JsonNumber(wilderness.events.caveEntryCost.toString()),
                    "caveImage" to
                        wilderness.events.caveImage
                            ?.let { JsonString(it.slug) }
                            .orJsonNull(),
                    "caveFoundPrompt" to textBlockJson(wilderness.events.caveFoundPrompt),
                    "caveNoMoves" to textBlockJson(wilderness.events.caveNoMoves),
                ),
            "terrainTransitions" to
                jsonObject(
                    "chancePercent" to JsonNumber(wilderness.terrainTransitions.chancePercent.toString()),
                    "entries" to
                        JsonArray(
                            wilderness.terrainTransitions.entries.map { entry ->
                                jsonObject(
                                    "terrain" to JsonString(entry.terrain.slug),
                                    "text" to textBlockJson(entry.text),
                                )
                            },
                        ),
                ),
        )

    private fun castleCommandJsonEntries(game: GameModel): List<Pair<String, JsonValue>> =
        game.castles.flatMap { castle ->
            val prefix = "castle.${castle.id.slug}"
            val keys = castle.keys
            listOf(
                "$prefix.navigation" to
                    commandListJson(
                        "moveNorth" to keys.moveNorth,
                        "moveSouth" to keys.moveSouth,
                        "moveWest" to keys.moveWest,
                        "moveEast" to keys.moveEast,
                        "save" to keys.save,
                        "help" to keys.help,
                        "viewParty" to keys.viewParty,
                        "removeArmor" to keys.removeArmor,
                        "removeWeapon" to keys.removeWeapon,
                        "drop" to keys.drop,
                        "readyWeapon" to keys.readyWeapon,
                        "readyArmor" to keys.readyArmor,
                        "quaff" to keys.quaff,
                        "castSpell" to keys.castSpell,
                    ),
                "$prefix.treasure" to commandListJson("take" to keys.treasureTake, "leave" to keys.treasureLeave),
                "$prefix.stairs" to
                    commandListJson(
                        "teleportOut" to keys.stairsTeleport,
                        "giveItem" to keys.stairsGive,
                        "leave" to keys.stairsLeave,
                    ),
            )
        }

    private fun dungeonRuntimeJson(dungeons: DungeonSystemModel): JsonValue =
        jsonObject(
            "commandHint" to textBlockJson(dungeons.keys.commandHint),
            "helpPages" to JsonArray(dungeons.keys.helpPages.map(::textBlockJson)),
            "eventCount" to JsonNumber(dungeons.events.size.toString()),
            "scenarioCount" to JsonNumber(dungeons.scenarios.size.toString()),
        )

    private fun townRuntimeJson(town: TownSystemModel?): JsonValue =
        town
            ?.let {
                jsonObject(
                    "leavePrompts" to JsonArray(it.leavePrompts.map { command -> JsonString(command.name) }),
                    "entryTax" to
                        jsonObject(
                            "minimumLeaderLevel" to JsonNumber(it.entryTax.minimumLeaderLevel.toString()),
                            "pay" to bindingJson(it.entryTax.pay),
                            "leave" to bindingJson(it.entryTax.leave),
                        ),
                    "services" to
                        JsonArray(
                            it.buildingServices.sortedBy { service -> service.building.slug }.map { service ->
                                jsonObject(
                                    "building" to JsonString(service.building.slug),
                                    "action" to JsonString(service.action.slug),
                                    "scene" to JsonString(service.scene.slug),
                                    "opensAt" to service.opensAt?.let { hour -> JsonNumber(hour.toString()) }.orJsonNull(),
                                    "closesAt" to service.closesAt?.let { hour -> JsonNumber(hour.toString()) }.orJsonNull(),
                                    "nativeServices" to nativeServicesJson(service.nativeServices),
                                )
                            },
                        ),
                    "nativeServices" to nativeServicesJson(it.nativeServices),
                )
            }.orJsonNull()

    private fun speakRuntimeJson(speak: SpeakSystemModel): JsonValue =
        jsonObject(
            "standardConversation" to speak.standardConversation?.let(::standardConversationJson).orJsonNull(),
            "textTables" to speakTextTablesJson(speak.textTables),
            "questionActions" to
                JsonArray(
                    speak.questionActions.map { action ->
                        jsonObject(
                            "topic" to JsonString(action.topic.name),
                            "job" to JsonString(action.job.slug),
                            "scriptRoot" to JsonString("question.${action.topic.name.lowercase()}.${action.job.slug}"),
                        )
                    },
                ),
        )

    private fun standardConversationJson(conversation: StandardConversationModel): JsonValue =
        jsonObject(
            "npcSelection" to JsonString(conversation.npcSelection.name),
            "visitPolicy" to JsonString(conversation.visitPolicy.name),
            "branchSelection" to JsonString(conversation.branchSelection.name),
            "missionHints" to JsonString(conversation.missionHints.name),
            "chitchat" to
                jsonObject(
                    "subjects" to JsonArray(conversation.chitchat.subjects.map { JsonString(it.name) }),
                    "replies" to JsonString(conversation.chitchat.replies.name),
                ),
            "rumour" to
                jsonObject(
                    "usage" to JsonString(conversation.rumour.usage.name),
                    "source" to JsonString(conversation.rumour.source.name),
                ),
            "prompts" to
                jsonObject(
                    "chooseNpc" to textBlockJson(conversation.prompts.chooseNpc),
                    "mainMenu" to textBlockJson(conversation.prompts.mainMenu),
                ),
            "nativeServices" to nativeServicesJson(conversation.nativeServices),
        )

    private fun speakTextTablesJson(tables: SpeakTextTablesModel): JsonValue =
        jsonObject(
            "screens" to
                JsonArray(
                    tables.screens.map { screen ->
                        jsonObject("id" to JsonString(screen.id.id), "text" to textBlockJson(screen.text))
                    },
                ),
            "chitchatPrompts" to
                JsonArray(
                    tables.chitchatPrompts.map { prompt ->
                        jsonObject(
                            "subject" to JsonString(prompt.subject.name),
                            "variants" to
                                JsonArray(
                                    prompt.variants.map { variant ->
                                        jsonObject(
                                            "audience" to JsonString(variant.audience.name),
                                            "text" to textBlockJson(variant.text),
                                        )
                                    },
                                ),
                        )
                    },
                ),
            "chitchatReplies" to
                JsonArray(
                    tables.chitchatReplies.map { reply ->
                        jsonObject(
                            "subject" to JsonString(reply.subject.name),
                            "replies" to jobTextsJson(reply.replies),
                        )
                    },
                ),
            "questionReplies" to
                JsonArray(
                    tables.questionReplies.map { reply ->
                        jsonObject(
                            "topic" to JsonString(reply.topic.name),
                            "replies" to jobTextsJson(reply.replies),
                        )
                    },
                ),
            "rumourTables" to
                JsonArray(
                    tables.rumourTables.map { table ->
                        jsonObject(
                            "id" to JsonString(table.id.slug),
                            "entries" to
                                JsonArray(
                                    table.entries.map { entry ->
                                        jsonObject(
                                            "id" to entry.id?.let { JsonString(it.slug) }.orJsonNull(),
                                            "text" to textBlockJson(entry.text),
                                            "excludedBy" to JsonArray(entry.excludedBy.map(::flagStateJson)),
                                        )
                                    },
                                ),
                        )
                    },
                ),
        )

    private fun castleJson(castle: CastleContentModel): JsonValue =
        jsonObject(
            "id" to JsonString(castle.id.slug),
            "kind" to JsonString("gridCastle"),
            "entry" to gridPointJson(castle.entry),
            "image" to castle.image?.let { JsonString(it.slug) }.orJsonNull(),
            "entryRequirement" to castle.entryRequirement?.let { JsonString(it.slug) }.orJsonNull(),
            "completionMilestone" to castle.completionMilestone?.let { JsonString(it.slug) }.orJsonNull(),
            "mapSize" to JsonNumber(castle.mapSize.toString()),
            "roomTypeMax" to JsonNumber(castle.roomTypeMax.toString()),
            "roomEvents" to
                JsonArray(
                    castle.roomEvents.entries.sortedBy { it.key }.map { (roll, event) ->
                        jsonObject("roll" to JsonNumber(roll.toString()), "event" to JsonString(event.name))
                    },
                ),
            "monsterTier" to JsonString(castle.monsterTier.slug),
            "treasure" to
                jsonObject(
                    "gateMax" to JsonNumber(castle.treasure.gateMax.toString()),
                    "regularKinds" to
                        JsonArray(
                            castle.treasure.regularKinds.map { items -> JsonArray(items.map { JsonString(it.slug) }) },
                        ),
                    "magic" to JsonArray(castle.treasure.magic.map { JsonString(it.slug) }),
                ),
            "pitfall" to
                jsonObject(
                    "damageDivisor" to JsonNumber(castle.pitfall.damageDivisor.toString()),
                    "trapTypes" to JsonArray(castle.pitfall.trapTypes.map(::textBlockJson)),
                ),
            "stairs" to
                jsonObject(
                    "floorCount" to JsonNumber(castle.stairs.floorCount.toString()),
                    "items" to
                        JsonArray(
                            castle.stairs.items.sortedBy { it.digit }.map { item ->
                                jsonObject(
                                    "digit" to JsonNumber(item.digit.toString()),
                                    "milestone" to JsonString(item.milestone.slug),
                                    "itemName" to JsonString(item.itemName),
                                    "reject" to textBlockJson(item.reject),
                                )
                            },
                        ),
                ),
            "scripts" to
                jsonObject(
                    "alreadyCompleted" to
                        castle.scripts.alreadyCompleted
                            ?.let { JsonString(it.slug) }
                            .orJsonNull(),
                    "entryBlocked" to
                        castle.scripts.entryBlocked
                            ?.let { JsonString(it.slug) }
                            .orJsonNull(),
                    "entered" to
                        castle.scripts.entered
                            ?.let { JsonString(it.slug) }
                            .orJsonNull(),
                    "completed" to
                        castle.scripts.completed
                            ?.let { JsonString(it.slug) }
                            .orJsonNull(),
                ),
            "text" to castleTextJson(castle.text),
        )

    private fun castleTextJson(text: CastleTextModel): JsonValue =
        jsonObject(
            "roomMonster" to textBlockJson(text.roomMonster),
            "trapTriesDisarmSuffix" to textBlockJson(text.trapTriesDisarmSuffix),
            "trapDisarmSucceed" to textBlockJson(text.trapDisarmSucceed),
            "trapDisarmFail" to textBlockJson(text.trapDisarmFail),
            "treasureMenu" to textBlockJson(text.treasureMenu),
            "treasureFull" to textBlockJson(text.treasureFull),
            "treasureTaken" to textBlockJson(text.treasureTaken),
            "moveNorth" to textBlockJson(text.moveNorth),
            "moveSouth" to textBlockJson(text.moveSouth),
            "moveEast" to textBlockJson(text.moveEast),
            "moveWest" to textBlockJson(text.moveWest),
            "stairsIntro" to textBlockJson(text.stairsIntro),
            "stairsGuardianOffer" to textBlockJson(text.stairsGuardianOffer),
            "stairsMenu" to textBlockJson(text.stairsMenu),
            "stairsLeave" to textBlockJson(text.stairsLeave),
            "teleportVortex" to textBlockJson(text.teleportVortex),
            "teleportOutside" to textBlockJson(text.teleportOutside),
            "giveMenu" to textBlockJson(text.giveMenu),
            "stairsGuardianGrateful" to textBlockJson(text.stairsGuardianGrateful),
            "floorNames" to JsonArray(text.floorNames.map(::JsonString)),
        )

    private fun textBlockJson(block: TextBlock): JsonValue =
        when (block) {
            is TextBlock.Ref -> jsonObject("kind" to JsonString("ref"), "id" to JsonString(block.id.id))
            is TextBlock.Inline -> jsonObject("kind" to JsonString("inline"), "lines" to stringArray(block.lines))
            is TextBlock.Structured ->
                jsonObject(
                    "kind" to JsonString("structured"),
                    "lines" to
                        JsonArray(
                            block.lines.map { line ->
                                jsonObject("parts" to JsonArray(line.parts.map(::textPartJson)))
                            },
                        ),
                )
        }

    private fun textPartJson(part: TextPart): JsonValue =
        when (part) {
            is TextPart.Literal -> jsonObject("kind" to JsonString("literal"), "text" to JsonString(part.text))
            is TextPart.IntValue -> jsonObject("kind" to JsonString("intExpr"), "expr" to intAstJson(part.value))
            is TextPart.StringValue -> jsonObject("kind" to JsonString("stringExpr"), "expr" to strAstJson(part.value))
            is TextPart.RoleName -> jsonObject("kind" to JsonString("roleName"), "role" to JsonString(part.role.slug))
            is TextPart.RoleLocation ->
                jsonObject(
                    "kind" to JsonString("roleLocation"),
                    "role" to JsonString(part.role.slug),
                )
            is TextPart.TownName -> jsonObject("kind" to JsonString("townName"), "town" to JsonString(part.town.slug))
            is TextPart.FuzzyTownLocation ->
                jsonObject(
                    "kind" to JsonString("fuzzyTownLocation"),
                    "town" to JsonString(part.town.slug),
                    "radius" to JsonNumber(part.radius.toString()),
                )
            is TextPart.LocationCoordinate ->
                jsonObject(
                    "kind" to JsonString("locationCoordinate"),
                    "location" to JsonString(part.location.slug),
                )
            TextPart.OfferedItemName -> textTokenJson("offeredItemName")
            TextPart.OfferedItemPrice -> textTokenJson("offeredItemPrice")
            TextPart.SelectedItemName -> textTokenJson("selectedItemName")
            TextPart.ShopPrice -> textTokenJson("shopPrice")
            TextPart.ShopCarryCap -> textTokenJson("shopCarryCap")
            TextPart.HuntMemberName -> textTokenJson("huntMemberName")
            TextPart.HuntMemberSubjectPronoun -> textTokenJson("huntMemberSubjectPronoun")
            TextPart.HuntFoodGained -> textTokenJson("huntFoodGained")
            TextPart.PartyMemberMax -> textTokenJson("partyMemberMax")
            TextPart.WildernessDungeonName -> textTokenJson("wildernessDungeonName")
            TextPart.WildernessDungeonLevels -> textTokenJson("wildernessDungeonLevels")
            TextPart.DungeonDepth -> textTokenJson("dungeonDepth")
            TextPart.ConversationNpcName -> textTokenJson("conversationNpcName")
            TextPart.CurrentTownName -> textTokenJson("currentTownName")
            TextPart.TownTaxPercent -> textTokenJson("townTaxPercent")
            TextPart.TownTaxAmount -> textTokenJson("townTaxAmount")
            TextPart.CreationMemberNumber -> textTokenJson("creationMemberNumber")
            TextPart.CreationMemberName -> textTokenJson("creationMemberName")
            TextPart.CreationWeaponSkill -> textTokenJson("creationWeaponSkill")
            TextPart.CreationHealth -> textTokenJson("creationHealth")
            TextPart.CreationWisdom -> textTokenJson("creationWisdom")
            TextPart.CreationCharisma -> textTokenJson("creationCharisma")
        }

    private fun textTokenJson(kind: String): JsonValue = jsonObject("kind" to JsonString(kind))

    private fun intAstJson(ast: IntAst): JsonValue =
        when (ast) {
            is IntAst.Lit -> jsonObject("op" to JsonString("lit"), "value" to JsonNumber(ast.value.toString()))
            is IntAst.Var -> jsonObject("op" to JsonString("var"), "name" to JsonString(ast.name))
            is IntAst.Binary ->
                jsonObject(
                    "op" to JsonString("binary"),
                    "operator" to JsonString(ast.op),
                    "left" to intAstJson(ast.left),
                    "right" to intAstJson(ast.right),
                )
            is IntAst.If ->
                jsonObject(
                    "op" to JsonString("if"),
                    "condition" to boolAstJson(ast.cond),
                    "then" to intAstJson(ast.thenValue),
                    "else" to intAstJson(ast.elseValue),
                )
            is IntAst.Random ->
                jsonObject(
                    "op" to JsonString("random"),
                    "lo" to intAstJson(ast.lo),
                    "hi" to intAstJson(ast.hi),
                )
            is IntAst.MemberField ->
                jsonObject(
                    "op" to JsonString("memberField"),
                    "member" to intAstJson(ast.member),
                    "field" to JsonString(ast.field),
                )
            is IntAst.MonsterField ->
                jsonObject(
                    "op" to JsonString("monsterField"),
                    "monster" to intAstJson(ast.monster),
                    "field" to JsonString(ast.field),
                )
            is IntAst.BattleFlag -> jsonObject("op" to JsonString("battleFlag"), "name" to JsonString(ast.name))
            is IntAst.BattleQuery -> jsonObject("op" to JsonString("battleQuery"), "name" to JsonString(ast.name))
            is IntAst.RealmScalar -> jsonObject("op" to JsonString("realmScalar"), "name" to JsonString(ast.name))
            is IntAst.PartyScalar -> jsonObject("op" to JsonString("partyScalar"), "name" to JsonString(ast.name))
            is IntAst.InventoryPoolCount ->
                jsonObject(
                    "op" to JsonString("inventoryPoolCount"),
                    "pool" to JsonString(ast.pool.slug),
                )
            is IntAst.InventorySlotCost ->
                jsonObject(
                    "op" to JsonString("inventorySlotCost"),
                    "slot" to intAstJson(ast.slot),
                )
            is IntAst.NativeInt ->
                jsonObject(
                    "op" to JsonString("nativeInt"),
                    "path" to JsonString(ast.path),
                    "args" to stringArray(ast.args),
                )
        }

    private fun boolAstJson(ast: BoolAst): JsonValue =
        when (ast) {
            is BoolAst.Cmp ->
                jsonObject(
                    "op" to JsonString("cmp"),
                    "operator" to JsonString(ast.op),
                    "left" to intAstJson(ast.left),
                    "right" to intAstJson(ast.right),
                )
            is BoolAst.Not -> jsonObject("op" to JsonString("not"), "expr" to boolAstJson(ast.expr))
            is BoolAst.And ->
                jsonObject(
                    "op" to JsonString("and"),
                    "left" to boolAstJson(ast.left),
                    "right" to boolAstJson(ast.right),
                )
            is BoolAst.Or ->
                jsonObject(
                    "op" to JsonString("or"),
                    "left" to boolAstJson(ast.left),
                    "right" to boolAstJson(ast.right),
                )
            is BoolAst.FlagIs ->
                jsonObject(
                    "op" to JsonString("flagIs"),
                    "flag" to JsonString(ast.slug),
                    "native" to JsonString(ast.nativeValue),
                )
            is BoolAst.MilestoneAchieved ->
                jsonObject(
                    "op" to JsonString("milestoneAchieved"),
                    "milestone" to JsonString(ast.milestone.slug),
                )
            is BoolAst.MemberFlag ->
                jsonObject(
                    "op" to JsonString("memberFlag"),
                    "member" to intAstJson(ast.member),
                    "name" to JsonString(ast.name),
                )
            is BoolAst.MonsterFlag ->
                jsonObject(
                    "op" to JsonString("monsterFlag"),
                    "monster" to intAstJson(ast.monster),
                    "name" to JsonString(ast.name),
                )
            is BoolAst.BattleFlag -> jsonObject("op" to JsonString("battleFlag"), "name" to JsonString(ast.name))
            is BoolAst.RuntimeFlag -> jsonObject("op" to JsonString("runtimeFlag"), "name" to JsonString(ast.name))
        }

    private fun strAstJson(ast: StrAst): JsonValue =
        when (ast) {
            is StrAst.NativeString ->
                jsonObject(
                    "op" to JsonString("nativeString"),
                    "path" to JsonString(ast.path),
                    "args" to JsonArray(ast.args.map(::nativeStringArgJson)),
                )
        }

    private fun nativeStringArgJson(arg: NativeStringArg): JsonValue =
        when (arg) {
            is NativeStringArg.Literal -> jsonObject("kind" to JsonString("literal"), "value" to JsonString(arg.value))
            is NativeStringArg.IntValue -> jsonObject("kind" to JsonString("intValue"), "value" to intAstJson(arg.value))
        }

    private fun promptGroupJson(
        group: String,
        vararg fields: JsonValue,
    ): JsonValue = jsonObject("group" to JsonString(group), "fields" to JsonArray(fields.toList()))

    private fun textField(
        name: String,
        text: TextBlock,
    ): JsonValue = jsonObject("name" to JsonString(name), "kind" to JsonString("text"), "value" to textBlockJson(text))

    private fun stringField(
        name: String,
        value: String,
    ): JsonValue = jsonObject("name" to JsonString(name), "kind" to JsonString("string"), "value" to JsonString(value))

    private fun commandListJson(vararg commands: Pair<String, InputBindingModel>): JsonValue =
        JsonArray(
            commands.map { (action, binding) ->
                jsonObject("action" to JsonString(action), "binding" to bindingJson(binding))
            },
        )

    private fun bindingJson(binding: InputBindingModel): JsonValue =
        jsonObject(
            "primary" to JsonString(binding.primary),
            "aliases" to stringArray(binding.aliases),
            "label" to JsonString(binding.label),
        )

    private fun nativeServicesJson(services: List<EngineServiceUse>): JsonValue =
        JsonArray(
            services.map { service ->
                jsonObject(
                    "service" to JsonString(service.service.name),
                    "reason" to JsonString(service.reason),
                )
            },
        )

    private fun jobTextsJson(texts: List<JobTextModel>): JsonValue =
        JsonArray(
            texts.map { entry ->
                jsonObject("job" to JsonString(entry.job.slug), "text" to textBlockJson(entry.text))
            },
        )

    private fun flagStateJson(state: FlagState): JsonValue =
        jsonObject(
            "flag" to JsonString(state.flag.slug),
            "scope" to JsonString(state.flag.scope.name),
            "native" to JsonString(state.native),
        )

    private fun gridPointJson(point: GridPoint): JsonValue =
        jsonObject("x" to JsonNumber(point.x.toString()), "y" to JsonNumber(point.y.toString()))

    private fun intRangeJson(range: IntRange): JsonValue =
        jsonObject("first" to JsonNumber(range.first.toString()), "last" to JsonNumber(range.last.toString()))

    private fun temperatureIntMapJson(values: Map<TemperatureBand, Int>): JsonValue =
        JsonArray(
            values.entries.sortedBy { it.key.name }.map { (band, value) ->
                jsonObject("band" to JsonString(band.name), "value" to JsonNumber(value.toString()))
            },
        )

    private fun temperatureTextMapJson(values: Map<TemperatureBand, TextBlock>): JsonValue =
        JsonArray(
            values.entries.sortedBy { it.key.name }.map { (band, text) ->
                jsonObject("band" to JsonString(band.name), "text" to textBlockJson(text))
            },
        )

    private fun stringArray(values: Iterable<String>): JsonValue = JsonArray(values.map(::JsonString).toList())
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
            sections =
                ExternalBundleSectionId.entries.map { id ->
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

private class ExternalRuntimeSystemsReader(
    private val source: String,
) {
    fun read(): ExternalRuntimeSystemsSummary {
        val systems = readSystems()
        return ExternalRuntimeSystemsSummary(
            schemaVersion = systems.schemaVersion,
            commandGroups = systems.commandGroups,
            promptGroups = systems.promptGroups,
            castles = systems.castles,
        )
    }

    fun readSystems(): ExternalRuntimeSystems {
        val systems =
            readExternalBundleEnvelope(source)
                .sections
                .requiredObject(ExternalBundleSectionId.RuntimeSystems.jsonName)
        systems.rejectReflectiveTypeMarkers("runtimeSystems")
        val commands = systems.requiredObject("commands")
        val commandGroups = commands.entries.associate { (name, value) -> name to value.readCommandGroup(name) }
        val prompts = systems.requiredObject("prompts")
        val castles = systems.requiredArray("castles")
        return ExternalRuntimeSystems(
            schemaVersion = systems.requiredInt("schemaVersion"),
            commandGroups = commandGroups.keys.toList(),
            promptGroups =
                prompts.requiredArray("groups").values.map { group ->
                    group.asObject("prompt group").requiredString("group")
                },
            castles =
                castles.values.map { castle ->
                    castle.asObject("castle").requiredString("id")
                },
            commands = commandGroups,
            realm = systems.requiredObject("realm").readRealm(),
            presentation = systems.requiredObject("presentation").readPresentation(),
        )
    }

    private fun JsonValue.readCommandGroup(name: String): List<ExternalRuntimeCommand> =
        when (this) {
            is JsonNull -> emptyList()
            is JsonArray ->
                values.map { row ->
                    val obj = row.asObject("runtime command $name")
                    ExternalRuntimeCommand(
                        action = obj.requiredString("action"),
                        binding = obj.requiredObject("binding").readBinding(),
                    )
                }
            else -> error("expected command array for $name")
        }

    private fun JsonObject.readBinding(): ExternalRuntimeInputBinding =
        ExternalRuntimeInputBinding(
            primary = requiredString("primary"),
            aliases = requiredArray("aliases").values.map { value -> value.asString("binding alias") },
            label = requiredString("label"),
        )

    private fun JsonObject.readRealm(): ExternalRuntimeRealm =
        ExternalRuntimeRealm(
            baseResourceCapacity =
                requiredArray("baseResourceCapacity")
                    .values
                    .associate { row ->
                        val entry = row.asObject("base resource capacity")
                        enumValueOf<Resource>(entry.requiredString("resource")) to entry.requiredInt("capacity")
                    },
        )

    private fun JsonObject.readPresentation(): ExternalRuntimePresentation =
        ExternalRuntimePresentation(
            terminalTerrain =
                requiredArray("terminalTerrain").values.map { value ->
                    val row = value.asObject("terminal terrain")
                    ExternalTerminalTerrainStyle(
                        terrain = row.requiredString("terrain"),
                        glyph = row.requiredString("glyph"),
                        color = row.requiredString("color"),
                    )
                },
            regions =
                requiredArray("regions").values.map { value ->
                    val row = value.asObject("screen region")
                    ExternalScreenRegion(
                        id = row.requiredString("id"),
                        x = row.requiredInt("x"),
                        y = row.requiredInt("y"),
                        width = row.optionalNullableInt("width"),
                        height = row.optionalNullableInt("height"),
                    )
                },
            images =
                requiredArray("images").values.map { value ->
                    val row = value.asObject("image")
                    ExternalImageAsset(
                        id = row.requiredString("id"),
                        kind = row.requiredString("kind"),
                        resource = row.requiredString("resource"),
                        placement = row.requiredString("placement"),
                    )
                },
            staticScreens =
                requiredArray("staticScreens").values.map { value ->
                    val row = value.asObject("static screen")
                    ExternalStaticScreen(
                        id = row.requiredString("id"),
                        image = row.optionalNullableString("image"),
                        resource = row.optionalNullableString("resource"),
                        commandHelp = row.required("commandHelp").asBoolean("commandHelp"),
                        runtimeReport = row.optionalNullableString("runtimeReport"),
                    )
                },
        )

    private fun JsonValue.rejectReflectiveTypeMarkers(path: String) {
        when (this) {
            is JsonObject -> {
                require(entries.none { (name, _) -> name == "_type" }) {
                    "$path contains reflective _type marker"
                }
                entries.forEach { (name, value) -> value.rejectReflectiveTypeMarkers("$path.$name") }
            }
            is JsonArray -> values.forEachIndexed { index, value -> value.rejectReflectiveTypeMarkers("$path[$index]") }
            else -> Unit
        }
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
    if (format != EXTERNAL_BUNDLE_FORMAT) externalBundleFail("unsupported bundle format $format")
    val version = root.requiredInt("schemaVersion")
    if (version != EXTERNAL_BUNDLE_VERSION) externalBundleFail("unsupported bundle schema version $version")

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

private fun externalBundleFail(message: String): Nothing = error("compiled bundle artifact loader failed: $message")

private class ExternalBundleArtifactMaterializer(
    private val source: String,
) {
    private val decoder = ExternalBundleObjectDecoder()

    fun readCompiledBundle(): CompiledGameBundle = readCompiledBundle(readExternalBundleEnvelope(source).sections)

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
        val content =
            CompiledContentBundle(
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
        val bundle =
            CompiledGameBundle(
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
        val entries =
            value.values.mapIndexed { index, item ->
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

    private inline fun <reified T : Any> JsonObject.decodeSection(id: ExternalBundleSectionId): T = decoder.decode(required(id.jsonName))

    private inline fun <reified T : Any> JsonObject.decodeSectionList(id: ExternalBundleSectionId): List<T> =
        requiredArray(id.jsonName).values.map { item -> decoder.decode(item) }
}

private class ExternalBundleObjectDecoder {
    private val flags = linkedMapOf<Pair<String, FlagScope>, DecodedFlagRef>()

    inline fun <reified T : Any> decode(value: JsonValue): T = decode(value, typeOf<T>()) as T

    fun decode(
        value: JsonValue,
        type: KType,
    ): Any? {
        if (value == JsonNull) {
            require(type.isMarkedNullable) { "non-null $type cannot be loaded from null" }
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
            Set::class ->
                value
                    .asArray("set")
                    .values
                    .map { item -> decode(item, type.singleArgument("set")) }
                    .toSet()
            Map::class -> decodeMap(value, type)
            is KClass<*> -> decodeClass(value, classifier)
            else -> error("unsupported compiled JSON target type $type")
        }
    }

    private fun decodeFlagRef(value: JsonValue): FlagRef {
        val obj = value.asObject("flag ref")
        val slug = obj.requiredString("slug")
        val scope = obj.optionalString("scope")?.let { enumValueOf<FlagScope>(it) } ?: FlagScope.Party
        val states =
            obj
                .optionalArray("states")
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

    private fun flagRef(
        slug: String,
        scope: FlagScope,
        stateNatives: List<String>,
    ): DecodedFlagRef {
        val ref = flags.getOrPut(slug to scope) { DecodedFlagRef(slug, scope) }
        stateNatives.forEach(ref::value)
        return ref
    }

    private fun decodeMap(
        value: JsonValue,
        type: KType,
    ): Map<Any?, Any?> {
        val keyType = type.argument(index = 0, owner = "map key")
        val valueType = type.argument(index = 1, owner = "map value")
        return value.asArray("map").values.associate { item ->
            val row = item.asObject("map entry")
            decode(row.required("key"), keyType) to decode(row.required("value"), valueType)
        }
    }

    private fun decodeClass(
        value: JsonValue,
        klass: KClass<*>,
    ): Any {
        if (klass.java.isEnum) return value.asEnum(klass)
        if (klass.isSealed) return decodeSealed(value, klass)
        klass.objectInstance?.let { instance ->
            value.expectObjectType(klass)
            return instance
        }
        return decodeDataClass(value, klass)
    }

    private fun decodeSealed(
        value: JsonValue,
        klass: KClass<*>,
    ): Any {
        val encodedType = value.asObject("${klass.simpleName} value").requiredString("_type")
        val subtype =
            klass.sealedSubclasses.firstOrNull { candidate -> candidate.simpleName == encodedType }
                ?: error("unknown ${klass.simpleName} subtype $encodedType")
        return decode(value, subtype.createType(nullable = false))
            ?: error("sealed subtype $encodedType decoded to null")
    }

    private fun decodeDataClass(
        value: JsonValue,
        klass: KClass<*>,
    ): Any {
        val obj = value.asObject("${klass.simpleName} value")
        obj.optionalString("_type")?.let { encodedType ->
            require(encodedType == klass.simpleName) { "expected ${klass.simpleName}, found $encodedType" }
        }
        val constructor = klass.primaryConstructor ?: error("missing primary constructor for ${klass.qualifiedName}")
        constructor.isAccessible = true
        val args =
            buildMap {
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

    private fun KType.singleArgument(owner: String): KType = argument(index = 0, owner = owner)

    private fun KType.argument(
        index: Int,
        owner: String,
    ): KType = arguments.getOrNull(index)?.type ?: error("missing $owner type argument for $this")
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
    fun value(native: String): FlagState = states.firstOrNull { state -> state.native == native } ?: state(native)
}

private sealed interface JsonValue {
    fun render(pretty: Boolean): String =
        buildString {
            appendJson(this@JsonValue, pretty = pretty, indent = 0)
        }
}

private data object JsonNull : JsonValue

private data class JsonBoolean(
    val value: Boolean,
) : JsonValue

private data class JsonNumber(
    val value: String,
) : JsonValue

private data class JsonString(
    val value: String,
) : JsonValue

private data class JsonArray(
    val values: List<JsonValue>,
) : JsonValue

private data class JsonObject(
    val entries: List<Pair<String, JsonValue>>,
) : JsonValue

private fun jsonObject(vararg entries: Pair<String, JsonValue>): JsonObject = JsonObject(entries.toList())

private fun JsonValue?.orJsonNull(): JsonValue = this ?: JsonNull

private fun compiledJson(value: Any?): JsonValue = compiledJson(value, mutableIdentitySet())

private fun compiledJson(
    value: Any?,
    visiting: MutableSet<Any>,
): JsonValue =
    when (value) {
        null -> JsonNull
        is String -> JsonString(value)
        is Char -> JsonString(value.toString())
        is Boolean -> JsonBoolean(value)
        is FlagRef ->
            jsonObject(
                "_type" to JsonString("FlagRef"),
                "slug" to JsonString(value.slug),
                "scope" to JsonString(value.scope.name),
                "states" to JsonArray(value.states.map { state -> JsonString(state.native) }),
            )
        is FlagState ->
            jsonObject(
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
        is Double,
        -> JsonNumber(value.toString())
        is Enum<*> -> JsonString(value.name)
        is IntRange ->
            jsonObject(
                "first" to JsonNumber(value.first.toString()),
                "last" to JsonNumber(value.last.toString()),
            )
        is Map<*, *> ->
            JsonArray(
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
        is Set<*> ->
            JsonArray(
                value
                    .map { item -> compiledJson(item, visiting) }
                    .sortedBy { item -> item.render(pretty = false) },
            )
        is Iterable<*> -> JsonArray(value.map { item -> compiledJson(item, visiting) })
        else -> compiledObjectJson(value, visiting)
    }

private fun compiledObjectJson(
    value: Any,
    visiting: MutableSet<Any>,
): JsonValue {
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
        val fields =
            type.declaredFields
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

private fun mutableIdentitySet(): MutableSet<Any> = java.util.Collections.newSetFromMap(IdentityHashMap())

private fun JsonValue.rowCount(): Int =
    when (this) {
        is JsonArray -> values.size
        else -> 1
    }

private fun JsonValue.compactBytes(): ByteArray = render(pretty = false).toByteArray(Charsets.UTF_8)

private fun ByteArray.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(this)
    return "sha256:" + digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
}

private fun StringBuilder.appendJson(
    value: JsonValue,
    pretty: Boolean,
    indent: Int,
) {
    when (value) {
        JsonNull -> append("null")
        is JsonBoolean -> append(value.value)
        is JsonNumber -> append(value.value)
        is JsonString -> appendQuotedJson(value.value)
        is JsonArray -> appendJsonArray(value, pretty, indent)
        is JsonObject -> appendJsonObject(value, pretty, indent)
    }
}

private fun StringBuilder.appendJsonArray(
    value: JsonArray,
    pretty: Boolean,
    indent: Int,
) {
    append('[')
    value.values.forEachIndexed { index, item ->
        if (index > 0) append(',')
        if (pretty) append('\n').appendIndent(indent + 1)
        appendJson(item, pretty, indent + 1)
    }
    if (pretty && value.values.isNotEmpty()) append('\n').appendIndent(indent)
    append(']')
}

private fun StringBuilder.appendJsonObject(
    value: JsonObject,
    pretty: Boolean,
    indent: Int,
) {
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
        val entries =
            buildList {
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
        val code =
            source.substring(pos, pos + 4).toIntOrNull(radix = 16)
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

    private fun literal(
        text: String,
        value: JsonValue,
    ): JsonValue {
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

    private fun peek(ch: Char): Boolean = pos < source.length && source[pos] == ch

    private fun fail(message: String): Nothing = error("JSON parse failed at byte $pos: $message")
}

private fun JsonValue.asObject(label: String): JsonObject = this as? JsonObject ?: error("expected object for $label")

private fun JsonValue.asArray(label: String): JsonArray = this as? JsonArray ?: error("expected array for $label")

private fun JsonValue.asString(label: String): String = (this as? JsonString)?.value ?: error("expected string for $label")

private fun JsonValue.asNumber(label: String): String = (this as? JsonNumber)?.value ?: error("expected number for $label")

private fun JsonValue.asBoolean(label: String): Boolean = (this as? JsonBoolean)?.value ?: error("expected boolean for $label")

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

private fun JsonObject.optional(name: String): JsonValue? = entries.firstOrNull { it.first == name }?.second

private fun JsonObject.requiredObject(name: String): JsonObject = required(name).asObject(name)

private fun JsonObject.requiredArray(name: String): JsonArray = required(name) as? JsonArray ?: error("expected array for $name")

private fun JsonObject.optionalArray(name: String): JsonArray? = optional(name) as? JsonArray

private fun JsonObject.requiredString(name: String): String = required(name).asString(name)

private fun JsonObject.requiredInt(name: String): Int =
    (required(name) as? JsonNumber)?.value?.toIntOrNull() ?: error("expected integer for $name")

private fun JsonObject.optionalString(name: String): String? = optional(name)?.asString(name)

private fun JsonObject.optionalNullableString(name: String): String? = optional(name)?.takeUnless { it is JsonNull }?.asString(name)

private fun JsonObject.optionalNullableInt(name: String): Int? {
    val value = optional(name)?.takeUnless { it is JsonNull } ?: return null
    return (value as? JsonNumber)?.value?.toIntOrNull() ?: error("expected integer for $name")
}

private inline fun <reified T : Enum<T>> JsonObject.requiredEnum(name: String): T = enumValueOf(requiredString(name))

internal fun ByteArray.fnv1a32(): Int {
    var hash = 0x811c9dc5.toInt()
    forEach { byte ->
        hash = hash xor (byte.toInt() and 0xff)
        hash *= 0x01000193
    }
    return hash
}
