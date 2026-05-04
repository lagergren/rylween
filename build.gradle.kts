import org.gradle.jvm.application.tasks.CreateStartScripts
import org.gradle.process.ExecOperations
import javax.inject.Inject

plugins {
    alias(libs.plugins.kotlin.jvm)
}

abstract class CompileNativeCExecutableTask @Inject constructor(
    private val execOperations: ExecOperations,
) : DefaultTask() {
    @get:Input
    abstract val compiler: Property<String>

    @get:Input
    abstract val flags: ListProperty<String>

    @get:InputFile
    abstract val sourceFile: RegularFileProperty

    @get:OutputFile
    abstract val executableFile: RegularFileProperty

    @TaskAction
    fun compile() {
        val source = sourceFile.get().asFile
        val executable = executableFile.get().asFile
        executable.parentFile.mkdirs()
        execOperations.exec {
            commandLine(listOf(compiler.get()) + flags.get() + listOf("-o", executable.path, source.path))
        }
    }
}

abstract class GenerateCompiledGameHarnessTask : DefaultTask() {
    @get:InputFile
    abstract val gameSource: RegularFileProperty

    @get:Input
    abstract val sourceLabel: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val sourcePath = sourceLabel.get()
        val gameLines = gameSource.get().asFile.readLines()
        val importLines = gameLines.filter { it.trimStart().startsWith("import ") }
        val bodyLines = gameLines.filterNot { it.trimStart().startsWith("import ") }
        val indentedGame = bodyLines
            .joinToString(separator = "\n") { line -> "        $line" }
        val imports = (importLines + "import rylween.dsl.*")
            .distinct()
            .joinToString(separator = "\n")
            .let { if (it.isBlank()) "" else "$it\n\n" }
        val output = outputFile.get().asFile
        output.parentFile.parentFile?.parentFile
            ?.resolve(output.name)
            ?.takeIf { it != output }
            ?.delete()
        output.apply {
            parentFile.mkdirs()
            writeText(
                """
                |// Generated from $sourcePath.
                |// Do not edit directly.

                |@file:Suppress("UNNECESSARY_SAFE_CALL")

                |package rylween.game

                |$imports

                |object RylweenCompiledGame {
                |    val nextRylween: RylweenModel
                |    val nextDsl: GameModel

                |    init {
                |$indentedGame

                |        this.nextRylween = nextRylween
                |        this.nextDsl = nextDsl
                |    }
                |}
                """.trimMargin() + "\n",
            )
        }
    }
}

abstract class GenerateGameCatalogHandlesTask : DefaultTask() {
    @get:InputFile
    abstract val gameSource: RegularFileProperty

    @get:Input
    abstract val sourceLabel: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    private var activeSource: String = ""

    @TaskAction
    fun generate() {
        val sourcePath = sourceLabel.get()
        val source = gameSource.get().asFile.readText()
        activeSource = source
        val buildingNames = buildingNames(source)
        val townNames = townNames(source)
        val missionNames = handleNames(source, "Missions", "mission")
        val dungeonNames = orderedNames(
            handleNames(source, "Dungeons"),
            listOf(
                "randomCave",
                "fireShieldCave",
                "thunTemple",
                "reebarTomb",
                "masterCrownDungeon",
                "silverSanctum",
                "hobgoblinStronghold",
                "ogreCave",
                "erethorTemple",
            ),
        )
        val dungeonEventNames = orderedNames(
            handleNames(source, "DungeonEvents"),
            listOf(
                "findObject",
                "nothingSpecial",
                "emptyLevel",
                "spectrumMessage",
                "gold",
                "goldChest",
                "torches",
                "weakEncounter",
                "deepEncounter",
                "thunRiddle1",
                "thunRiddle2",
                "thunRiddle3",
                "colourRiddle",
                "fireShieldGuardian",
                "fireShield",
                "bracelets",
                "nolrimTomb",
                "masterCrown",
                "silverSanctum",
                "hobgoblinPatrol",
                "royalGuard1",
                "royalGuard2",
                "hobgoblinChief",
                "orcNest",
                "tammysPrison",
                "erethorPriests",
                "luteChamber",
            ),
        )
        val sections = listOf(
            generateFlagHandles(flagDeclarations(source)),
            generateItemHandles(itemHandles(source)),
            generateRefsClass(
                className = "MonsterIds",
                objectName = "Monsters",
                typeName = "MonsterId",
                factoryName = "monster",
                names = handleNames(declarationSlice(source, "monsters"), "Monsters", "monster"),
            ),
            generateRefsClass(
                className = "EncounterTierRefs",
                objectName = "EncounterTiers",
                typeName = "EncounterTier",
                factoryName = "encounterTier",
                names = orderedNames(handleNames(source, "EncounterTiers"), listOf("weak", "wild", "deep")),
            ),
            generateSpellHandles(spellHandles(source)),
            generateRefsClass(
                className = "LootTableRefs",
                objectName = "LootTables",
                typeName = "LootTableId",
                factoryName = "lootTable",
                names = handleNames(source, "LootTables", "lootTable"),
            ),
            generateRefsClass(
                className = "MapRefs",
                objectName = "Maps",
                typeName = "WorldMapId",
                factoryName = "worldMap",
                names = handleNames(source, "Maps", "worldMap"),
            ),
            generateRefsClass(
                className = "MapLayerRefs",
                objectName = "MapLayers",
                typeName = "MapLayerId",
                factoryName = "mapLayer",
                names = handleNames(source, "MapLayers", "layer"),
            ),
            generateTerrainHandles(terrainDeclarations(source)),
            generateRefsClass(
                className = "PaletteColorRefs",
                objectName = "PaletteColors",
                typeName = "PaletteColorId",
                factoryName = "paletteColor",
                names = paletteColorNames(source),
            ),
            generateImageHandles(imageHandles(source)),
            generateRefsClass(
                className = "StaticScreenRefs",
                objectName = "StaticScreens",
                typeName = "StaticScreenId",
                factoryName = "staticScreen",
                names = staticScreenNames(source),
            ),
            generateRefsClass(
                className = "ScreenRegionRefs",
                objectName = "ScreenRegions",
                typeName = "ScreenRegionId",
                factoryName = "screenRegion",
                names = handleNames(declarationSlice(source, "layouts"), "ScreenRegions", "region"),
            ),
            generateSceneHandles(sceneHandles(source)),
            generateRefsClass(
                className = "MissionRefs",
                objectName = "Missions",
                typeName = "MissionId",
                factoryName = "missionId",
                names = missionNames,
                slugOverrides = missionNames.associateWith { "missions.${it.toSlug()}" },
            ),
            generateRefsClass(
                className = "BuildingRefs",
                objectName = "Buildings",
                typeName = "BuildingId",
                factoryName = "building",
                names = buildingNames,
                allList = true,
            ),
            generateTownHandles(townNames, buildingNames),
            generateRefsClass(
                className = "NpcJobRefs",
                objectName = "NpcJobs",
                typeName = "NpcJobId",
                factoryName = "npcJob",
                names = handleNames(source, "NpcJobs"),
                allList = true,
            ),
            generateRefsClass(
                className = "RoleRefs",
                objectName = "NpcRoles",
                typeName = "NpcRole",
                factoryName = "npcRole",
                names = handleNames(source, "Roles"),
                allList = true,
                alias = "Roles",
            ),
            generateRefsClass(
                className = "RumourTableRefs",
                objectName = "RumourTables",
                typeName = "RumourTableId",
                factoryName = "rumourTable",
                names = handleNames(source, "RumourTables"),
            ),
            generateRefsClass(
                className = "RumourHandleRefs",
                objectName = "Rumours",
                typeName = "RumourId",
                factoryName = "rumour",
                names = handleNames(source, "Rumours"),
                allList = true,
            ),
            generateRefsClass(
                className = "NpcRefs",
                objectName = "Npcs",
                typeName = "NpcId",
                factoryName = "npc",
                names = handleNames(source, "Npcs"),
            ),
            generateRefsClass(
                className = "LocationRefs",
                objectName = "Locations",
                typeName = "LocationId",
                factoryName = "location",
                names = handleNames(source, "Locations"),
            ),
            generateRefsClass(
                className = "DungeonRefs",
                objectName = "Dungeons",
                typeName = "DungeonId",
                factoryName = "dungeon",
                names = dungeonNames,
            ),
            generateRefsClass(
                className = "DungeonEventRefs",
                objectName = "DungeonEvents",
                typeName = "DungeonEventId",
                factoryName = "dungeonEvent",
                names = dungeonEventNames,
            ),
            generateRefsClass(
                className = "StateRefs",
                objectName = "States",
                typeName = "StateId",
                factoryName = "stateId",
                names = stateNames(source),
                slugOverrides = stateNames(source).associateWith { it.toKebab() },
            ),
            generateRefsClass(
                className = "PartyRefs",
                objectName = "Parties",
                typeName = "PartyId",
                factoryName = "partyId",
                names = partyNames(source),
                slugOverrides = partyNames(source).associateWith { it.toKebab() },
            ),
            generateRefsClass(
                className = "GameMilestoneRefs",
                objectName = "Milestones",
                typeName = "GameMilestoneId",
                factoryName = "milestone",
                names = handleNames(declarationSlice(source, "gameProgress"), "Milestones", "milestone"),
            ),
            generateRefsClass(
                className = "FormulaRefs",
                objectName = "Formulas",
                typeName = "FormulaId",
                factoryName = "formula",
                names = handleNames(declarationSlice(source, "formulas"), "Formulas", "formula"),
                slugOverrides = mapOf("levelUpHitPointFloor" to "level_up_hp_floor"),
            ),
            generateRefsClass(
                className = "RewardBundleRefs",
                objectName = "Rewards",
                typeName = "RewardBundleId",
                factoryName = "rewardBundle",
                names = handleNames(declarationSlice(source, "rewards"), "Rewards", "reward"),
            ),
            generateTextHandles(textHandles(source)),
        ).joinToString(separator = "\n\n")

        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            parentFile.resolve("RylweenGeneratedItemCatalog.kt").takeIf { it != this }?.delete()
            writeText(
                """
                |// Generated from $sourcePath declarations.
                |// Do not edit directly.
                |
                |@file:Suppress("unused", "MemberVisibilityCanBePrivate", "PropertyName", "KotlinExplicitApi")
                |
                |package rylween.game
                |
                |import rylween.dsl.*
                |
                |$sections
                """.trimMargin() + "\n",
            )
        }
    }

    private data class ItemHandles(
        val itemNames: List<String>,
        val includeNoneAlias: Boolean,
        val poolNames: List<String>,
    )

    private data class SourceSlice(
        val fullSource: String,
        val text: String,
        val startOffset: Int,
    )

    private data class SourceHandle(
        val name: String,
        val offset: Int,
    )

    private data class SourceDiagnostic(
        val offset: Int,
        val message: String,
    )

    private data class SourceLocation(
        val line: Int,
        val column: Int,
    )

    private data class FlagDeclaration(
        val scope: String,
        val path: List<String>,
        val states: List<String>,
        val offset: Int,
    ) {
        val pathKey: String = path.joinToString(".")
    }

    private data class TerrainDeclaration(
        val name: String,
        val mapCode: Char,
    )

    private data class ImageHandles(
        val staticNames: List<String>,
        val sceneNames: List<String>,
    )

    private data class SpellHandles(
        val mageNames: List<String>,
        val priestNames: List<String>,
        val monsterNames: List<String>,
    )

    private data class SceneGroupHandles(
        val groupName: String,
        val className: String,
        val slugPrefix: String,
        val names: List<String>,
    )

    private class TextNode(
        val path: List<String>,
    ) {
        val children: LinkedHashMap<String, TextNode> = linkedMapOf()
        var leaf: TextLeaf? = null
    }

    private data class TextLeaf(
        val id: String,
        val isPattern: Boolean,
        val offset: Int,
    )

    private fun itemHandles(source: String): ItemHandles {
        val itemBlock = declarationSlice(source, "items")
        val emptyItemNames = Regex("""\bempty\s*\(\s*Items\.([A-Za-z][A-Za-z0-9_]*)""")
            .findAll(itemBlock.text)
            .map { match ->
                SourceHandle(
                    name = match.groupValues[1],
                    offset = itemBlock.startOffset + match.groupOffset(1),
                )
            }
            .toList()
        val declaredItemHandles = Regex(
            """\b(?:weapon|armor|gear|potion|treasure|special|magicItem)\s*\(\s*Items\.([A-Za-z][A-Za-z0-9_]*)""",
        )
            .findAll(itemBlock.text)
            .map { match ->
                SourceHandle(
                    name = match.groupValues[1],
                    offset = itemBlock.startOffset + match.groupOffset(1),
                )
            }
            .toList()
        val itemDeclarationHandles = buildList {
            addAll(
                emptyItemNames.map { handle ->
                    handle.copy(name = if (handle.name == "none" || handle.name == "blank") "blank" else handle.name)
                },
            )
            addAll(declaredItemHandles.filterNot { it.name == "none" || it.name == "blank" })
        }
        validateNoDuplicateDeclarations(source, "item", itemDeclarationHandles)
        validateNoCatalogIdCollisions(source, "item", itemDeclarationHandles)
        val itemNames = buildList {
            if (emptyItemNames.any { it.name == "none" || it.name == "blank" }) {
                add("blank")
            }
            addAll(emptyItemNames.map { it.name }.filterNot { it == "none" || it == "blank" })
            addAll(declaredItemHandles.map { it.name }.filterNot { it == "none" || it == "blank" })
        }.distinctInOrder()
        val poolHandles = Regex("""\bpool\s*\(\s*ItemPools\.([A-Za-z][A-Za-z0-9_]*)""")
            .findAll(itemBlock.text)
            .map { match ->
                SourceHandle(
                    name = match.groupValues[1],
                    offset = itemBlock.startOffset + match.groupOffset(1),
                )
            }
            .toList()
        validateNoDuplicateDeclarations(source, "item pool", poolHandles)
        validateNoCatalogIdCollisions(source, "item pool", poolHandles)
        val poolNames = poolHandles.distinctNamesInOrder()

        if (itemNames.isEmpty()) {
            sourceFailure(source, itemBlock.startOffset, "The items block must declare at least one item.")
        }
        if (poolNames.isEmpty()) {
            sourceFailure(source, itemBlock.startOffset, "The items block must declare at least one item pool.")
        }

        return ItemHandles(itemNames, emptyItemNames.any { it.name == "none" }, poolNames)
    }

    private fun flagDeclarations(source: String): List<FlagDeclaration> {
        val flagsBlock = declarationSlice(source, "flags")
        val flagMatches = Regex("""\b(story|encounter)\s*\(\s*Story\.([A-Za-z][A-Za-z0-9_.]*)\s*\)""")
            .findAll(flagsBlock.text)
            .toList()
        val flagHandles = flagMatches.map { match ->
            SourceHandle(
                name = match.groupValues[2],
                offset = flagsBlock.startOffset + match.groupOffset(2),
            )
        }
        validateNoDuplicateDeclarations(source, "story flag", flagHandles)
        validateNoCatalogIdCollisions(source, "story flag", flagHandles) { pathKey ->
            legacyFlagSlugs[pathKey] ?: pathKey.split(".").joinToString(".") { it.toSlug() }
        }
        return flagMatches
            .map { match ->
                val path = match.groupValues[2].split(".")
                val pathPattern = path.joinToString("""\.""") { Regex.escape(it) }
                val stateHandles = Regex("""\bStory\.$pathPattern\.([A-Za-z][A-Za-z0-9_]*)""")
                    .findAll(flagsBlock.text)
                    .map { stateMatch ->
                        SourceHandle(
                            name = stateMatch.groupValues[1],
                            offset = flagsBlock.startOffset + stateMatch.groupOffset(1),
                        )
                    }
                    .toList()
                validateNoDuplicateDeclarations(source, "state for Story.${path.joinToString(".")}", stateHandles)
                validateNoCatalogIdCollisions(source, "state for Story.${path.joinToString(".")}", stateHandles) { state ->
                    flagStateSlug(path.joinToString("."), state)
                }
                val states = stateHandles.distinctNamesInOrder()
                if (states.isEmpty()) {
                    sourceFailure(
                        source,
                        flagsBlock.startOffset + match.groupOffset(2),
                        "Story.${path.joinToString(".")} must declare at least one state.",
                    )
                }
                FlagDeclaration(
                    scope = match.groupValues[1],
                    path = path,
                    states = states,
                    offset = flagsBlock.startOffset + match.groupOffset(2),
                )
            }
            .toList()
            .distinctBy { it.pathKey }
            .also {
                if (it.isEmpty()) {
                    sourceFailure(source, flagsBlock.startOffset, "The flags block must declare at least one story flag.")
                }
            }
    }

    private fun generateFlagHandles(flags: List<FlagDeclaration>): String {
        val flagClasses = flags.joinToString(separator = "\n\n") { flag -> generateFlagClass(flag) }
        val containerPrefixes = flags
            .flatMap { flag -> (1 until flag.path.size).map { size -> flag.path.take(size) } }
            .distinctBy { it.joinToString(".") }
        val containers = (listOf(emptyList<String>()) + containerPrefixes)
            .joinToString(separator = "\n\n") { prefix ->
                generateFlagContainer(
                    className = if (prefix.isEmpty()) "StoryFlags" else flagContainerClassName(prefix),
                    flags = flags,
                    prefix = prefix,
                )
            }
        return """
            |$flagClasses
            |
            |$containers
            |
            |val Story: StoryFlags = StoryFlags()
            |val Flags: StoryFlags = Story
        """.trimMargin()
    }

    private fun generateFlagClass(flag: FlagDeclaration): String {
        val scopeArgument = if (flag.scope == "encounter") {
            ", scope = FlagScope.Encounter"
        } else {
            ""
        }
        val stateLines = flag.states.joinToString(separator = "\n") { state ->
            """    val $state: FlagState = state("${flagStateSlug(flag.pathKey, state)}")"""
        }
        val derivedProperties = derivedFlagProperties(flag)
        val body = if (derivedProperties.isBlank()) {
            stateLines
        } else {
            "$stateLines\n\n$derivedProperties"
        }
        return """
            |class ${flagClassName(flag.path)} : FlagRef("${flagSlug(flag.path)}"$scopeArgument) {
            |$body
            |}
        """.trimMargin()
    }

    private fun generateFlagContainer(
        className: String,
        flags: List<FlagDeclaration>,
        prefix: List<String>,
    ): String {
        val children = flags
            .filter { it.path.size > prefix.size && it.path.take(prefix.size) == prefix }
            .map { it.path[prefix.size] }
            .distinctInOrder()
        val properties = children.joinToString(separator = "\n") { child ->
            val childPrefix = prefix + child
            val directFlag = flags.singleOrNull { it.path == childPrefix }
            val nested = flags.any { it.path.size > childPrefix.size && it.path.take(childPrefix.size) == childPrefix }
            if (directFlag != null && nested) {
                sourceFailure(
                    activeSource,
                    directFlag.offset,
                    "Story.${childPrefix.joinToString(".")} cannot be both a flag and a flag group.",
                )
            }
            if (directFlag != null) {
                val flagClass = flagClassName(directFlag.path)
                "    val $child: $flagClass = $flagClass().declareFlag()"
            } else {
                val nestedClass = flagContainerClassName(childPrefix)
                "    val $child: $nestedClass = $nestedClass()"
            }
        }
        return """
            |class $className {
            |$properties
            |}
        """.trimMargin()
    }

    private fun generateItemHandles(handles: ItemHandles): String {
        val itemLines = handles.itemNames.joinToString(separator = "\n") { name ->
            """    val $name: ItemId = item("${name.toSlug()}")"""
        }
        val noneAlias = if (handles.includeNoneAlias) {
            "\n    val none: ItemId = blank"
        } else {
            ""
        }
        val poolLines = handles.poolNames.joinToString(separator = "\n") { name ->
            """    val $name: ItemPoolId = itemPool("${name.toSlug()}")"""
        }
        return """
            |class ItemRefs {
            |$itemLines$noneAlias
            |}
            |
            |val Items: ItemRefs = ItemRefs()
            |
            |class ItemPoolRefs {
            |$poolLines
            |}
            |
            |val ItemPools: ItemPoolRefs = ItemPoolRefs()
        """.trimMargin()
    }

    private fun terrainDeclarations(source: String): List<TerrainDeclaration> {
        val terminalBlock = declarationSlice(source, "terrain")
        val terrainMatches = Regex("""\bterrain\s*\(\s*Terrain\.([A-Za-z][A-Za-z0-9_]*)\s*,\s*mapCode\s*=\s*'([^'\\]|\\.)'""")
            .findAll(terminalBlock.text)
            .toList()
        val terrainHandles = terrainMatches.map { match ->
            SourceHandle(
                name = match.groupValues[1],
                offset = terminalBlock.startOffset + match.groupOffset(1),
            )
        }
        validateNoDuplicateDeclarations(source, "terrain", terrainHandles)
        validateNoCatalogIdCollisions(source, "terrain", terrainHandles)
        val mapCodeDiagnostics = terrainMatches
            .map { match ->
                SourceHandle(
                    name = match.groupValues[2],
                    offset = terminalBlock.startOffset + match.groupOffset(2),
                )
            }
            .groupBy { it.name }
            .flatMap { (mapCode, declarations) ->
                val first = declarations.first()
                val firstLocation = source.sourceLocation(first.offset)
                declarations.drop(1).map { duplicate ->
                    SourceDiagnostic(
                        duplicate.offset,
                        "terrain map code '$mapCode' is used more than once; first use is at line ${firstLocation.line}.",
                    )
                }
            }
        if (mapCodeDiagnostics.isNotEmpty()) {
            sourceFailures(source, mapCodeDiagnostics)
        }
        return terrainMatches
            .map { match ->
                TerrainDeclaration(
                    match.groupValues[1],
                    match.groupValues[2].parseCharLiteral(source, terminalBlock.startOffset + match.groupOffset(2)),
                )
            }
            .toList()
            .distinctBy { it.name }
            .also {
                if (it.isEmpty()) {
                    sourceFailure(source, terminalBlock.startOffset, "The terminal block must declare at least one terrain tile.")
                }
            }
    }

    private fun generateTerrainHandles(terrain: List<TerrainDeclaration>): String {
        val lines = terrain.joinToString(separator = "\n") { entry ->
            """    val ${entry.name}: TerrainId = terrain("${entry.name.toSlug()}", ${entry.mapCode.charLiteral()})"""
        }
        return """
            |class TerrainRefs {
            |$lines
            |}
            |
            |val Terrain: TerrainRefs = TerrainRefs()
        """.trimMargin()
    }

    private fun paletteColorNames(source: String): List<String> {
        val terminalColors = listOf("black", "red", "green", "yellow", "blue", "magenta", "cyan", "white")
        return orderedNames((handleNames(source, "PaletteColors") + terminalColors).distinctInOrder(), terminalColors)
    }

    private fun imageHandles(source: String): ImageHandles {
        val handles = Regex("""\bImages\.(Static|Scene|Backdrop)\.([A-Za-z][A-Za-z0-9_]*)""")
            .findAll(source)
            .map { match ->
                match.groupValues[1] to SourceHandle(
                    name = match.groupValues[2],
                    offset = match.groupOffset(2),
                )
            }
            .toList()
        val staticHandles = handles.filter { it.first == "Static" }.map { it.second }
        val sceneHandles = handles.filter { it.first == "Scene" || it.first == "Backdrop" }.map { it.second }
        validateNoCatalogIdCollisions(source, "static image", staticHandles)
        validateNoCatalogIdCollisions(source, "scene image", sceneHandles) { name -> sceneImageSlug(name) }
        return ImageHandles(
            staticNames = staticHandles.distinctNamesInOrder(),
            sceneNames = sceneHandles.distinctNamesInOrder(),
        ).also {
            if (it.staticNames.isEmpty() && it.sceneNames.isEmpty()) {
                sourceFailure(source, 0, "The game source must reference at least one image handle.")
            }
        }
    }

    private fun generateImageHandles(handles: ImageHandles): String {
        val staticLines = handles.staticNames.joinToString(separator = "\n") { name ->
            """    val $name: ImageId = image("ansi.${name.toSlug()}")"""
        }
        val sceneLines = handles.sceneNames.joinToString(separator = "\n") { name ->
            """    val $name: ImageId = image("${sceneImageSlug(name)}")"""
        }
        return """
            |class ImageRefs {
            |    val Static: StaticAnsiImageRefs = StaticAnsiImageRefs()
            |    val Scene: SceneImageRefs = SceneImageRefs()
            |    val Backdrop: SceneImageRefs = Scene
            |}
            |
            |class StaticAnsiImageRefs {
            |$staticLines
            |}
            |
            |class SceneImageRefs {
            |$sceneLines
            |}
            |
            |val Images: ImageRefs = ImageRefs()
        """.trimMargin()
    }

    private fun spellHandles(source: String): SpellHandles {
        fun nestedSpellNames(group: String): List<String> {
            val handles = Regex("""\bspell\s*\(\s*Spells\.${Regex.escape(group)}\.([A-Za-z][A-Za-z0-9_]*)""")
                .findAll(source)
                .map { match ->
                    SourceHandle(
                        name = match.groupValues[1],
                        offset = match.groupOffset(1),
                    )
                }
                .toList()
            validateNoCatalogIdCollisions(source, "$group spell", handles)
            return handles.distinctNamesInOrder()
        }

        return SpellHandles(
            mageNames = nestedSpellNames("Mage"),
            priestNames = nestedSpellNames("Priest"),
            monsterNames = nestedSpellNames("Monster"),
        ).also {
            if (it.mageNames.isEmpty() && it.priestNames.isEmpty() && it.monsterNames.isEmpty()) {
                sourceFailure(source, 0, "The game source must declare at least one spell.")
            }
        }
    }

    private fun generateSpellHandles(handles: SpellHandles): String {
        val mageLines = handles.mageNames.joinToString(separator = "\n") { name ->
            """    val $name: SpellId = spellId("mage.${name.toSlug()}")"""
        }
        val priestLines = handles.priestNames.joinToString(separator = "\n") { name ->
            """    val $name: SpellId = spellId("priest.${name.toSlug()}")"""
        }
        val monsterLines = handles.monsterNames.joinToString(separator = "\n") { name ->
            """    val $name: SpellId = spellId("monster.${name.toSlug()}")"""
        }
        return """
            |class SpellRefs {
            |    val Mage: MageSpellRefs = MageSpellRefs()
            |    val Priest: PriestSpellRefs = PriestSpellRefs()
            |    val Monster: MonsterSpellRefs = MonsterSpellRefs()
            |}
            |
            |class MageSpellRefs {
            |$mageLines
            |}
            |
            |class PriestSpellRefs {
            |$priestLines
            |}
            |
            |class MonsterSpellRefs {
            |$monsterLines
            |}
            |
            |val Spells: SpellRefs = SpellRefs()
        """.trimMargin()
    }

    private fun sceneHandles(source: String): List<SceneGroupHandles> {
        val sceneReferences = Regex("""\bScenes\.([A-Z][A-Za-z0-9_]*)\.([A-Za-z][A-Za-z0-9_]*)""")
            .findAll(source)
            .map { match ->
                match.groupValues[1] to SourceHandle(
                    name = match.groupValues[2],
                    offset = match.groupOffset(2),
                )
            }
            .toList()
        val knownGroups = setOf("Towne", "Battle", "Missions", "Terrain", "Dungeon", "Ydarg")
        val unknownGroupDiagnostics = sceneReferences
            .filterNot { (group, _) -> group in knownGroups }
            .map { (group, handle) ->
                SourceDiagnostic(
                    handle.offset,
                    "scene group '$group' is not declared; use one of ${knownGroups.joinToString()}.",
                )
            }
        if (unknownGroupDiagnostics.isNotEmpty()) {
            sourceFailures(source, unknownGroupDiagnostics)
        }
        val flatSceneMatch = Regex("""\bScenes\.([a-z][A-Za-z0-9_]*)""").find(source)
        if (flatSceneMatch != null) {
            sourceFailure(
                source,
                flatSceneMatch.groupOffset(1),
                "scene handles must use grouped names such as Scenes.Towne.rumour.",
            )
        }
        val groupedNames = sceneReferences
            .filter { (group, _) -> group in knownGroups }
            .groupBy(
                keySelector = { it.first },
                valueTransform = { it.second },
            )
            .mapValues { (_, handles) -> handles.distinctNamesInOrder() }
        fun group(groupName: String, className: String, slugPrefix: String): SceneGroupHandles {
            val handles = groupedNames[groupName].orEmpty()
            val sourceHandles = sceneReferences
                .filter { it.first == groupName }
                .map { it.second }
            validateNoCatalogIdCollisions(source, "scene in Scenes.$groupName", sourceHandles) { name ->
                sceneSlug(SceneGroupHandles(groupName, className, slugPrefix, emptyList()), name)
            }
            return SceneGroupHandles(groupName, className, slugPrefix, handles)
        }
        return listOf(
            group("Towne", "TowneSceneRefs", "towne"),
            group("Battle", "BattleSceneRefs", "battle"),
            group("Missions", "MissionSceneRefs", "missions"),
            group("Terrain", "TerrainSceneRefs", "terrain"),
            group("Dungeon", "DungeonSceneRefs", "dungeon"),
            group("Ydarg", "YdargSceneRefs", "ydarg"),
        ).filter { it.names.isNotEmpty() }
            .also {
                if (it.isEmpty()) {
                    sourceFailure(source, 0, "The game source must reference at least one scene handle.")
                }
            }
    }

    private fun generateSceneHandles(groups: List<SceneGroupHandles>): String {
        val groupProperties = groups.joinToString(separator = "\n") { group ->
            """    val ${group.groupName}: ${group.className} = ${group.className}()"""
        }
        val groupClasses = groups.joinToString(separator = "\n\n") { group ->
            val lines = group.names.joinToString(separator = "\n") { name ->
                """    val $name: SceneId = sceneId("${sceneSlug(group, name)}")"""
            }
            """
                |class ${group.className} {
                |$lines
                |}
            """.trimMargin()
        }
        return """
            |class SceneRefs {
            |$groupProperties
            |}
            |
            |$groupClasses
            |
            |val Scenes: SceneRefs = SceneRefs()
        """.trimMargin()
    }

    private fun textHandles(source: String): TextNode {
        val root = TextNode(emptyList())
        val textHandles = Regex("""\b(?:T|Texts)\.([A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+)""")
            .findAll(source)
            .map { match ->
                SourceHandle(
                    name = match.groupValues[1],
                    offset = match.groupOffset(1),
                )
            }
            .toList()
            .distinctBy { it.name }
        if (textHandles.isEmpty()) {
            sourceFailure(source, 0, "The game source must reference at least one text handle.")
        }
        validateNoCatalogIdCollisions(source, "text handle", textHandles) { pathKey ->
            textId(pathKey.split("."))
        }
        val diagnostics = buildList {
            textHandles.forEach { handle ->
                val path = handle.name.split(".")
                var node = root
                path.forEachIndexed { index, part ->
                    if (node.leaf != null) {
                        val leafLocation = source.sourceLocation((node.leaf ?: return@forEachIndexed).offset)
                        add(
                            SourceDiagnostic(
                                handle.offset,
                                "text path T.${handle.name} uses T.${node.path.joinToString(".")} as a group, " +
                                    "but that path is already a text leaf at line ${leafLocation.line}.",
                            ),
                        )
                    }
                    node = node.children.getOrPut(part) { TextNode(node.path + part) }
                    if (index == path.lastIndex && node.children.isNotEmpty()) {
                        add(
                            SourceDiagnostic(
                                handle.offset,
                                "text path T.${handle.name} is both a text leaf and a text group.",
                            ),
                        )
                    }
                }
                val key = path.joinToString(".")
                val patternOverride = textPatternOverrides[key]
                node.leaf = TextLeaf(
                    id = patternOverride ?: textId(path),
                    isPattern = patternOverride != null,
                    offset = handle.offset,
                )
            }
        }
        if (diagnostics.isNotEmpty()) {
            sourceFailures(source, diagnostics)
        }
        return root
    }

    private fun generateTextHandles(root: TextNode): String {
        val classes = buildList {
            fun emitClass(node: TextNode, className: String) {
                val lines = node.children.values.joinToString(separator = "\n") { child ->
                    val leaf = child.leaf
                    if (leaf != null && child.children.isEmpty()) {
                        if (leaf.isPattern) {
                            """    val ${child.path.last()}: TextPattern = textPattern("${leaf.id}")"""
                        } else {
                            """    val ${child.path.last()}: TextId = text("${leaf.id}")"""
                        }
                    } else {
                        val childClassName = textClassName(child.path)
                        """    val ${child.path.last()}: $childClassName = $childClassName()"""
                    }
                }
                add(
                    """
                    |class $className {
                    |$lines
                    |}
                    """.trimMargin(),
                )
                node.children.values
                    .filterNot { it.leaf != null && it.children.isEmpty() }
                    .forEach { child -> emitClass(child, textClassName(child.path)) }
            }
            emitClass(root, "TextRefs")
        }
        return """
            |${classes.joinToString(separator = "\n\n")}
            |
            |val Texts: TextRefs = TextRefs()
            |val T: TextRefs = Texts
        """.trimMargin()
    }

    private fun generateRefsClass(
        className: String,
        objectName: String,
        typeName: String,
        factoryName: String,
        names: List<String>,
        slugOverrides: Map<String, String> = emptyMap(),
        allList: Boolean = false,
        alias: String? = null,
    ): String {
        if (names.isEmpty()) {
            sourceFailure(activeSource, 0, "No $objectName handles were found in the game source.")
        }
        val lines = names.joinToString(separator = "\n") { name ->
            """    val $name: $typeName = $factoryName("${slugOverrides[name] ?: name.toSlug()}")"""
        }
        val allBlock = if (allList) {
            val allLines = names.joinToString(separator = "\n") { name -> "        $name," }
            """

                |    val all: List<$typeName> = listOf(
                |$allLines
                |    )
            """.trimMargin()
        } else {
            ""
        }
        val aliasLine = alias?.let { "\nval $it: $className = $objectName" } ?: ""
        return """
            |class $className {
            |$lines$allBlock
            |}
            |
            |val $objectName: $className = $className()$aliasLine
        """.trimMargin()
    }

    private fun generateTownHandles(townNames: List<String>, buildingNames: List<String>): String {
        if (townNames.isEmpty()) {
            sourceFailure(activeSource, 0, "No town declarations were found in the towns block.")
        }
        val positionBuildings = buildingNames.filterNot { it == "none" }
        if (positionBuildings.isEmpty()) {
            sourceFailure(activeSource, 0, "No town-position buildings were found.")
        }
        val buildingLines = positionBuildings.joinToString(separator = "\n") { building ->
            """    val $building: WorldPosition get() = WorldPosition.Town(id, Buildings.$building)"""
        }
        val townLines = townNames.joinToString(separator = "\n") { town ->
            """    val $town: TownRef = TownRef(town("${town.toSlug()}"))"""
        }
        return """
            |class TownRef(override val id: TownId) : TownHandle {
            |$buildingLines
            |}
            |
            |class TownRefs {
            |$townLines
            |}
            |
            |val Towns: TownRefs = TownRefs()
        """.trimMargin()
    }

    private fun buildingNames(source: String): List<String> {
        val townAndSystemBlocks = declarationBlock(source, "towns") + "\n" + declarationBlock(source, "townSystem")
        val explicitNames = handleNames(source, "Buildings")
        val townPositionHandles = Regex("""\bTowns\.[A-Za-z][A-Za-z0-9_]*\.([A-Za-z][A-Za-z0-9_]*)""")
            .findAll(source)
            .map { match ->
                SourceHandle(
                    name = match.groupValues[1],
                    offset = match.groupOffset(1),
                )
            }
            .toList()
        validateNoCatalogIdCollisions(source, "town building", townPositionHandles)
        val defaultServiceNames = listOf("merchant", "inn", "tavern", "smith", "church").filter { service ->
            Regex("""\b${Regex.escape(service)}\s*(?:\(|\{)""").containsMatchIn(townAndSystemBlocks)
        }
        return orderedNames(
            (explicitNames + townPositionHandles.distinctNamesInOrder() + defaultServiceNames).distinctInOrder(),
            listOf("none", "inn", "tavern", "church", "smith", "merchant", "hut", "lodge"),
        )
    }

    private fun townNames(source: String): List<String> {
        val townsBlock = declarationSlice(source, "towns")
        val literalHandles = Regex("""\btown\s*\(\s*"([A-Za-z][A-Za-z0-9_]*)"""")
            .findAll(townsBlock.text)
            .map { match ->
                SourceHandle(
                    name = match.groupValues[1],
                    offset = townsBlock.startOffset + match.groupOffset(1),
                )
            }
            .toList()
        val townHandles = Regex("""\btown\s*\(\s*Towns\.([A-Za-z][A-Za-z0-9_]*)""")
            .findAll(townsBlock.text)
            .map { match ->
                SourceHandle(
                    name = match.groupValues[1],
                    offset = townsBlock.startOffset + match.groupOffset(1),
                )
            }
            .toList()
        val declarations = literalHandles + townHandles
        validateNoDuplicateDeclarations(source, "town", declarations)
        validateNoCatalogIdCollisions(source, "town", declarations)
        return declarations.distinctNamesInOrder()
    }

    private fun stateNames(source: String): List<String> {
        val handles = Regex("""\b(?:stateFixture|runtimeState)\s*\(\s*States\.([A-Za-z][A-Za-z0-9_]*)""")
            .findAll(source)
            .map { match ->
                SourceHandle(
                    name = match.groupValues[1],
                    offset = match.groupOffset(1),
                )
            }
            .toList()
        validateNoCatalogIdCollisions(source, "runtime state", handles) { it.toKebab() }
        return handles.distinctNamesInOrder()
    }

    private fun partyNames(source: String): List<String> =
        handleNames(source, "Parties")

    private fun handleNames(source: String, objectName: String, callName: String): List<String> =
        handleNames(sourceSlice(source), objectName, callName)

    private fun handleNames(slice: SourceSlice, objectName: String, callName: String): List<String> {
        val handles = Regex(
            """\b${Regex.escape(callName)}\s*\(\s*${Regex.escape(objectName)}\.([A-Za-z][A-Za-z0-9_]*)""",
        )
            .findAll(slice.text)
            .map { match ->
                SourceHandle(
                    name = match.groupValues[1],
                    offset = slice.startOffset + match.groupOffset(1),
                )
            }
            .toList()
        validateNoCatalogIdCollisions(slice.fullSource, objectName, handles)
        return handles.distinctNamesInOrder()
    }

    private fun handleNames(source: String, objectName: String): List<String> =
        handleNames(sourceSlice(source), objectName)

    private fun handleNames(slice: SourceSlice, objectName: String): List<String> {
        val handles = Regex("""\b${Regex.escape(objectName)}\.([A-Za-z][A-Za-z0-9_]*)""")
            .findAll(slice.text)
            .map { match ->
                SourceHandle(
                    name = match.groupValues[1],
                    offset = slice.startOffset + match.groupOffset(1),
                )
            }
            .toList()
        validateNoCatalogIdCollisions(slice.fullSource, objectName, handles)
        return handles.distinctNamesInOrder()
    }

    private fun staticScreenNames(source: String): List<String> {
        val visuals = declarationSlice(sourceSlice(source), "visuals")
        val staticScreens = declarationSlice(visuals, "staticScreens")
        val handles = Regex("""\b(?:screen|image|resource|commandHelp)\s*\(\s*StaticScreens\.([A-Za-z][A-Za-z0-9_]*)""")
            .findAll(staticScreens.text)
            .map { match ->
                SourceHandle(
                    name = match.groupValues[1],
                    offset = staticScreens.startOffset + match.groupOffset(1),
                )
            }
            .toList()
        validateNoDuplicateDeclarations(source, "static screen", handles)
        validateNoCatalogIdCollisions(source, "static screen", handles)
        return handles.distinctNamesInOrder()
    }

    private fun sourceSlice(source: String): SourceSlice =
        SourceSlice(fullSource = source, text = source, startOffset = 0)

    private fun declarationBlock(source: String, name: String): String =
        declarationSlice(sourceSlice(source), name).text

    private fun declarationSlice(source: String, name: String): SourceSlice =
        declarationSlice(sourceSlice(source), name)

    private fun declarationSlice(slice: SourceSlice, name: String): SourceSlice {
        val match = Regex("""\b${Regex.escape(name)}\s*\{""").find(slice.text)
            ?: sourceFailure(
                slice.fullSource,
                slice.startOffset,
                "Could not find the '$name' declaration block in the game source.",
            )
        val openBrace = match.range.last
        var depth = 0
        for (index in openBrace until slice.text.length) {
            when (slice.text[index]) {
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) {
                        return SourceSlice(
                            fullSource = slice.fullSource,
                            text = slice.text.substring(openBrace + 1, index),
                            startOffset = slice.startOffset + openBrace + 1,
                        )
                    }
                }
            }
        }
        sourceFailure(
            slice.fullSource,
            slice.startOffset + match.range.first,
            "The '$name' declaration block is not closed.",
        )
    }

    private fun List<String>.distinctInOrder(): List<String> {
        val seen = linkedSetOf<String>()
        return filter { seen.add(it) }
    }

    private fun List<SourceHandle>.distinctNamesInOrder(): List<String> {
        val seen = linkedSetOf<String>()
        return mapNotNull { handle -> handle.name.takeIf { seen.add(it) } }
    }

    private fun MatchResult.groupOffset(index: Int): Int =
        groups[index]?.range?.first ?: range.first

    private fun sourceFailure(source: String, offset: Int, message: String): Nothing =
        sourceFailures(source, listOf(SourceDiagnostic(offset, message)))

    private fun sourceFailures(source: String, diagnostics: List<SourceDiagnostic>): Nothing {
        val file = gameSource.get().asFile.relativeToOrSelf(project.projectDir).path
        val details = diagnostics.joinToString(separator = "\n") { diagnostic ->
            val location = source.sourceLocation(diagnostic.offset)
            "$file:${location.line}:${location.column}: ${diagnostic.message}"
        }
        throw GradleException(
            "Game catalog handle generation failed:\n$details",
        )
    }

    private fun String.sourceLocation(offset: Int): SourceLocation {
        val boundedOffset = offset.coerceIn(0, length)
        var line = 1
        var lineStart = 0
        for (index in 0 until boundedOffset) {
            if (this[index] == '\n') {
                line += 1
                lineStart = index + 1
            }
        }
        return SourceLocation(line, boundedOffset - lineStart + 1)
    }

    private fun validateNoDuplicateDeclarations(
        source: String,
        concept: String,
        handles: List<SourceHandle>,
    ) {
        val diagnostics = handles
            .groupBy { it.name }
            .flatMap { (name, declarations) ->
                val first = declarations.first()
                val firstLocation = source.sourceLocation(first.offset)
                declarations.drop(1).map { duplicate ->
                    SourceDiagnostic(
                        duplicate.offset,
                        "$concept '$name' is declared more than once; first declaration is at line ${firstLocation.line}.",
                    )
                }
            }
        if (diagnostics.isNotEmpty()) {
            sourceFailures(source, diagnostics)
        }
    }

    private fun validateNoCatalogIdCollisions(
        source: String,
        concept: String,
        handles: List<SourceHandle>,
        idOf: (String) -> String = { it.toSlug() },
    ) {
        val diagnostics = handles
            .distinctBy { it.name }
            .groupBy { idOf(it.name) }
            .flatMap { (catalogId, declarations) ->
                val first = declarations.first()
                val firstLocation = source.sourceLocation(first.offset)
                declarations.drop(1).map { colliding ->
                    SourceDiagnostic(
                        colliding.offset,
                        "$concept '${colliding.name}' normalizes to catalog id '$catalogId', " +
                            "which is already used by '${first.name}' at line ${firstLocation.line}.",
                    )
                }
            }
        if (diagnostics.isNotEmpty()) {
            sourceFailures(source, diagnostics)
        }
    }

    private fun orderedNames(names: List<String>, preferredOrder: List<String>): List<String> =
        (preferredOrder.filter { it in names } + names.filterNot { it in preferredOrder }).distinctInOrder()

    private fun flagClassName(path: List<String>): String =
        "Story${path.joinToString(separator = "") { it.toUpperCamel() }}Flag"

    private fun flagContainerClassName(path: List<String>): String =
        "Story${path.joinToString(separator = "") { it.toUpperCamel() }}Flags"

    private fun flagSlug(path: List<String>): String =
        legacyFlagSlugs[path.joinToString(".")] ?: path.joinToString(".") { it.toSlug() }

    private fun flagStateSlug(pathKey: String, stateName: String): String =
        legacyFlagStateSlugs["$pathKey.$stateName"] ?: stateName.toSlug()

    private fun sceneImageSlug(name: String): String =
        sceneImageSlugOverrides[name] ?: "blitgfx.${name.toSlug()}"

    private fun sceneSlug(group: SceneGroupHandles, name: String): String =
        legacySceneSlugs["${group.groupName}.$name"] ?: "${group.slugPrefix}.${name.toSlug()}"

    private fun textId(path: List<String>): String =
        textIdOverrides[path.joinToString(".")]
            ?: path.joinToString(separator = ".") { part ->
                when (part) {
                    "MissionsText" -> "missions"
                    else -> part.toSlug()
                }
            }

    private fun textClassName(path: List<String>): String =
        "${path.joinToString(separator = "") { it.toUpperCamel() }}TextRefs"

    private fun derivedFlagProperties(flag: FlagDeclaration): String =
        when (flag.pathKey) {
            "silverKey" -> """
                |    val trailStarted: BoolExpr
                |        get() = mapBought or noteFound or thiefSqueezed or smithConfessed or caveLocated
            """.trimMargin()
            "masterCrown" -> """
                |    val activeTask: BoolExpr
                |        get() = task1Assigned or task2Assigned or task3Assigned
            """.trimMargin()
            else -> ""
        }

    private fun String.toSlug(): String =
        replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").lowercase()

    private fun String.toKebab(): String =
        toSlug().replace('_', '-')

    private fun String.toUpperCamel(): String =
        replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    private fun String.parseCharLiteral(source: String, offset: Int): Char =
        when {
            length == 1 -> single()
            startsWith("\\") && length == 2 -> when (this[1]) {
                '\\' -> '\\'
                '\'' -> '\''
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                else -> sourceFailure(source, offset, "Unsupported terrain map-code escape '$this'.")
            }
            else -> sourceFailure(source, offset, "Unsupported terrain map-code literal '$this'.")
        }

    private fun Char.charLiteral(): String =
        when (this) {
            '\\' -> "'\\\\'"
            '\'' -> "'\\''"
            '\n' -> "'\\n'"
            '\r' -> "'\\r'"
            '\t' -> "'\\t'"
            else -> "'$this'"
        }

    private val legacyFlagSlugs = mapOf(
        "church.donationOnce" to "church.donation.once",
        "peasant.wife" to "peasant_wife",
        "jazzy.ogre" to "jazzy_ogre",
        "masterCrown" to "mastercrown",
    )

    private val legacyFlagStateSlugs = mapOf(
        "silverKey.thiefSqueezed" to "thief2_squeezed",
    )

    private val sceneImageSlugOverrides = mapOf(
        "encounterBanner1" to "blitgfx.encounter.1",
        "encounterBanner2" to "blitgfx.encounter.2",
        "encounterBanner3" to "blitgfx.encounter.3",
        "encounterBanner4" to "blitgfx.encounter.4",
        "encounterBanner5" to "blitgfx.encounter.5",
    )

    private val legacySceneSlugs = mapOf(
        "Battle.monsterMelee" to "battle.monster.melee",
    )

    private val textIdOverrides = mapOf(
        "Battle.Prompt.monster" to "battle.prompt.who",
    )

    private val textPatternOverrides = mapOf(
        "Towne.Rumour.indexed" to "towne.rumour.r{}",
    )
}

abstract class GenerateAnsiBuildWrapperTask : DefaultTask() {
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun generate() {
        val output = outputFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(
            $$"""
            |#!/usr/bin/env bash
            |set -euo pipefail
            |
            |SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
            |APP_HOME="$(cd "$SCRIPT_DIR/.." && pwd -P)"
            |PROJECT_DIR="$(cd "$APP_HOME/../../.." && pwd -P)"
            |
            |build=false
            |args=()
            |for arg in "$@"; do
            |  if [[ "$arg" == "--build" ]]; then
            |    build=true
            |  else
            |    args+=("$arg")
            |  fi
            |done
            |
            |if [[ "$build" == true ]]; then
            |  if [[ ! -x "$PROJECT_DIR/gradlew" ]]; then
            |    echo "rylween: cannot find Gradle wrapper at $PROJECT_DIR/gradlew" >&2
            |    exit 1
            |  fi
            |  (cd "$PROJECT_DIR" && ./gradlew installKotlinAnsi --console=plain)
            |fi
            |
            |exec "$APP_HOME/bin/rylween-app" "${args[@]}"
            """.trimMargin() + "\n",
        )
        output.setExecutable(true, false)
    }
}

group = "org.rylween"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val generatedGameHarnessDir: Provider<Directory> = layout.buildDirectory.dir("generated/gameHarness/kotlin")
val generatedGameHarnessFile: Provider<RegularFile> = layout.buildDirectory.file("generated/gameHarness/kotlin/rylween/game/RylweenCompiledGame.kt")
val generatedGameCatalogDir: Provider<Directory> = layout.buildDirectory.dir("generated/gameCatalog/kotlin")
val generatedGameCatalogFile: Provider<RegularFile> = layout.buildDirectory.file(
    "generated/gameCatalog/kotlin/rylween/game/RylweenGeneratedGameCatalog.kt",
)
val gameDslSourceFile: RegularFile = layout.projectDirectory.file("src/game/dsl/RylweenGame.dsl.kts")
val gameDslSourceLabel: String = gameDslSourceFile.asFile.relativeTo(projectDir).invariantSeparatorsPath

kotlin {
    jvmToolchain(25)
}

val gameSourceSet: SourceSet = sourceSets.create("game") {
    java.srcDir("src/game/kotlin")
    java.srcDir(generatedGameHarnessDir)
    java.srcDir(generatedGameCatalogDir)
    compileClasspath += sourceSets.named("main").get().output
    runtimeClasspath += output + compileClasspath
}

val appSourceSet: SourceSet = sourceSets.create("app") {
    java.srcDir("src/app/kotlin")
    resources.srcDir("data")
    resources.include("ansi/**", "tiles/**", "rylween.doc", "score.txt", "vscore.txt")
    compileClasspath += sourceSets.named("main").get().output + gameSourceSet.output
    runtimeClasspath += output + compileClasspath
}

sourceSets.named("test") {
    compileClasspath += gameSourceSet.output
    runtimeClasspath += gameSourceSet.output
}

configurations.named(gameSourceSet.implementationConfigurationName) {
    extendsFrom(configurations.implementation.get())
}

configurations.named(appSourceSet.implementationConfigurationName) {
    extendsFrom(configurations.implementation.get())
}

dependencies {
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    add(appSourceSet.implementationConfigurationName, libs.jline)
}

tasks.test {
    useJUnitPlatform()
}

val generateCompiledGameHarness: TaskProvider<GenerateCompiledGameHarnessTask> by tasks.registering(GenerateCompiledGameHarnessTask::class) {
    group = "generation"
    description = "Generates a test harness that compiles the game DSL as Kotlin and exposes nextRylween."

    gameSource.set(gameDslSourceFile)
    sourceLabel.set(gameDslSourceLabel)
    outputFile.set(generatedGameHarnessFile)
}

val generateGameCatalogHandles: TaskProvider<GenerateGameCatalogHandlesTask> by tasks.registering(GenerateGameCatalogHandlesTask::class) {
    group = "generation"
    description = "Generates current-game item and item-pool handles from the game DSL declarations."

    gameSource.set(gameDslSourceFile)
    sourceLabel.set(gameDslSourceLabel)
    outputFile.set(generatedGameCatalogFile)
}

val kotlinCompiler: Configuration by configurations.creating
val gameDslRuntime: Configuration by configurations.creating

dependencies {
    val kotlinVersion = libs.versions.kotlin.get()
    kotlinCompiler("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion")
    kotlinCompiler("org.jetbrains.kotlin:kotlin-scripting-compiler-embeddable:$kotlinVersion")
    kotlinCompiler("org.jetbrains.kotlin:kotlin-scripting-jvm:$kotlinVersion")
    gameDslRuntime("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    gameDslRuntime("org.jetbrains.kotlin:kotlin-script-runtime:$kotlinVersion")
    gameDslRuntime("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
}

tasks.jar {
    archiveBaseName = "rylween-dsl"
}
tasks.named("compileTestKotlin") {
    dependsOn(generateCompiledGameHarness)
    dependsOn(tasks.named(gameSourceSet.classesTaskName))
}

tasks.named("compileGameKotlin") {
    dependsOn(generateGameCatalogHandles)
    dependsOn(generateCompiledGameHarness)
}

tasks.named("compileAppKotlin") {
    dependsOn(tasks.named(gameSourceSet.classesTaskName))
}

val runKotlinTown: TaskProvider<JavaExec> by tasks.registering(JavaExec::class) {
    group = "application"
    description = "Boots the compiled game in the pure Kotlin terminal town runner."

    dependsOn(tasks.named(appSourceSet.classesTaskName))
    classpath = appSourceSet.runtimeClasspath
    mainClass.set("rylween.app.RylweenTerminalMainKt")
    standardInput = System.`in`
}

val runKotlinGame: TaskProvider<JavaExec> by tasks.registering(JavaExec::class) {
    group = "application"
    description = "Boots the compiled game in the pure Kotlin terminal wilderness runner."

    dependsOn(tasks.named(appSourceSet.classesTaskName))
    classpath = appSourceSet.runtimeClasspath
    mainClass.set("rylween.app.RylweenGameTerminalMain")
    standardInput = System.`in`
}

val nativeSmokeBundle: Provider<RegularFile> = layout.buildDirectory.file("compiled/rylween-native-smoke.rsmoke")
val nativeSmokeSource: RegularFile = layout.projectDirectory.file("src/native/compiled-smoke/rylween_bundle_smoke.c")
val nativeSmokeExecutable: Provider<RegularFile> = layout.buildDirectory.file("native/compiled-smoke/rylween-bundle-smoke")
val externalBundleArtifact: Provider<RegularFile> = layout.buildDirectory.file("compiled/rylween-bundle.json")
val externalBundleInspection: Provider<RegularFile> = layout.buildDirectory.file("compiled/rylween-bundle.inspect.txt")
val nativeCCompiler: Provider<String> = providers.environmentVariable("CC").orElse("cc")
val nativeCWarningFlags: List<String> = listOf("-std=c17", "-Wall", "-Wextra", "-Werror", "-pedantic")
val nativeSmokeBundlePath: String = nativeSmokeBundle.get().asFile.path
val nativeSmokeExecutablePath: String = nativeSmokeExecutable.get().asFile.path
val externalBundleArtifactPath: String = externalBundleArtifact.get().asFile.path
val externalBundleInspectionPath: String = externalBundleInspection.get().asFile.path

fun registerNativeCExecutable(
    taskName: String,
    taskDescription: String,
    source: RegularFile,
    executable: Provider<RegularFile>,
): TaskProvider<CompileNativeCExecutableTask> =
    tasks.register<CompileNativeCExecutableTask>(taskName) {
        group = "verification"
        description = taskDescription
        compiler.set(nativeCCompiler)
        flags.set(nativeCWarningFlags)
        sourceFile.set(source)
        executableFile.set(executable)
    }

val emitNativeSmokeBundle: TaskProvider<JavaExec> by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Emits the deterministic compiled-content/script artifact consumed by the native smoke loader."

    dependsOn(tasks.named(appSourceSet.classesTaskName))
    classpath = appSourceSet.runtimeClasspath
    mainClass.set("rylween.app.RylweenCompiledBundleArtifactMain")
    args(nativeSmokeBundlePath)
    inputs.files(appSourceSet.runtimeClasspath)
    outputs.file(nativeSmokeBundle)
}

val compileNativeSmokeLoader: TaskProvider<CompileNativeCExecutableTask> =
    registerNativeCExecutable(
        taskName = "compileNativeSmokeLoader",
        taskDescription = "Compiles the minimal C loader for the compiled VM smoke artifact.",
        source = nativeSmokeSource,
        executable = nativeSmokeExecutable,
    )

val checkNativeSmokeBundle: TaskProvider<Exec> by tasks.registering(Exec::class) {
    group = "verification"
    description = "Runs the C loader over the emitted compiled VM smoke artifact."

    dependsOn(emitNativeSmokeBundle)
    dependsOn(compileNativeSmokeLoader)
    inputs.file(nativeSmokeBundle)
    inputs.file(nativeSmokeExecutable)
    commandLine(
        nativeSmokeExecutablePath,
        nativeSmokeBundlePath,
    )
}

val emitExternalCompiledBundle: TaskProvider<JavaExec> by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Emits the deterministic JSON compiled bundle artifact."

    dependsOn(tasks.named(appSourceSet.classesTaskName))
    classpath = appSourceSet.runtimeClasspath
    mainClass.set("rylween.app.RylweenExternalBundleArtifactMain")
    args(externalBundleArtifactPath)
    inputs.files(appSourceSet.runtimeClasspath)
    outputs.file(externalBundleArtifact)
}

val checkExternalCompiledBundle: TaskProvider<JavaExec> by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Validates the deterministic JSON compiled bundle artifact."

    dependsOn(emitExternalCompiledBundle)
    classpath = appSourceSet.runtimeClasspath
    mainClass.set("rylween.app.RylweenExternalBundleCheckMain")
    args(externalBundleArtifactPath)
    inputs.file(externalBundleArtifact)
}

val inspectExternalCompiledBundle: TaskProvider<JavaExec> by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Writes a readable inspection summary for the deterministic JSON compiled bundle artifact."

    dependsOn(emitExternalCompiledBundle)
    classpath = appSourceSet.runtimeClasspath
    mainClass.set("rylween.app.RylweenExternalBundleInspectMain")
    args(externalBundleArtifactPath, externalBundleInspectionPath)
    inputs.file(externalBundleArtifact)
    outputs.file(externalBundleInspection)
}

val kotlinAnsiJar: TaskProvider<Jar> by tasks.registering(Jar::class) {
    group = "build"
    description = "Packages the compiled Kotlin DSL game and ANSI app resources into one launcher jar."

    dependsOn(tasks.named(appSourceSet.classesTaskName))
    archiveBaseName.set("rylween-kotlin")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.named("main").get().output)
    from(gameSourceSet.output)
    from(appSourceSet.output)
    manifest {
        attributes["Main-Class"] = "rylween.app.RylweenAnsiMain"
    }
}

val kotlinAnsiRuntimeJars: FileCollection = appSourceSet.runtimeClasspath.filter { it.isFile && it.extension == "jar" }
val kotlinAnsiWrapper: Provider<RegularFile> = layout.buildDirectory.file("scripts/kotlinAnsiWrapper/rylween")

val kotlinAnsiStartScripts: TaskProvider<CreateStartScripts> by tasks.registering(CreateStartScripts::class) {
    group = "application"
    description = "Generates direct start scripts for the ANSI runtime."

    dependsOn(kotlinAnsiJar)
    applicationName = "rylween-app"
    mainClass.set("rylween.app.RylweenAnsiMain")
    outputDir = layout.buildDirectory.dir("scripts/kotlinAnsiApp").get().asFile
    classpath = files(kotlinAnsiJar) + kotlinAnsiRuntimeJars
}

val kotlinAnsiBuildWrapper: TaskProvider<GenerateAnsiBuildWrapperTask> by tasks.registering(GenerateAnsiBuildWrapperTask::class) {
    group = "application"
    description = "Generates the ANSI runtime wrapper with an optional --build refresh."

    outputFile.set(kotlinAnsiWrapper)
}

val installKotlinAnsi: TaskProvider<Sync> by tasks.registering(Sync::class) {
    group = "application"
    description = "Installs the direct ANSI runtime launcher outside Gradle's interactive console."

    dependsOn(kotlinAnsiStartScripts)
    dependsOn(kotlinAnsiBuildWrapper)
    into(layout.buildDirectory.dir("install/rylween-kotlin"))
    from(kotlinAnsiStartScripts) {
        into("bin")
        filePermissions {
            unix("rwxr-xr-x")
        }
    }
    from(kotlinAnsiWrapper) {
        into("bin")
        filePermissions {
            unix("rwxr-xr-x")
        }
    }
    from(files(kotlinAnsiJar) + kotlinAnsiRuntimeJars) {
        into("lib")
    }
}

val validateGameDsl: TaskProvider<JavaExec> by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Compiles and executes the game Kotlin DSL source against the built DSL jar."

    dependsOn(tasks.jar)
    dependsOn(tasks.named(gameSourceSet.classesTaskName))
    classpath = kotlinCompiler
    mainClass.set("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
    val gameKotlinClasses = layout.buildDirectory.dir("classes/kotlin/${gameSourceSet.name}")
    val gameDslClasspath = files(tasks.jar.flatMap { it.archiveFile }, gameKotlinClasses) + gameDslRuntime
    args(
        "-no-stdlib",
        "-no-reflect",
        "-classpath",
        gameDslClasspath.asPath,
        "-script",
        gameDslSourceFile.asFile.absolutePath,
    )
}

tasks.check {
    dependsOn(validateGameDsl)
    dependsOn(tasks.named(appSourceSet.classesTaskName))
    dependsOn(checkNativeSmokeBundle)
    dependsOn(checkExternalCompiledBundle)
    dependsOn(inspectExternalCompiledBundle)
}
