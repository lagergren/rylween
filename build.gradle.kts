import org.gradle.jvm.application.tasks.CreateStartScripts

plugins {
    alias(libs.plugins.kotlin.jvm)
}

abstract class GenerateCompiledGameHarnessTask : org.gradle.api.DefaultTask() {
    @get:org.gradle.api.tasks.InputFile
    abstract val gameSource: org.gradle.api.file.RegularFileProperty

    @get:org.gradle.api.tasks.OutputFile
    abstract val outputFile: org.gradle.api.file.RegularFileProperty

    @org.gradle.api.tasks.TaskAction
    fun generate() {
        val gameLines = gameSource.get().asFile.readLines()
        val importLines = gameLines.filter { it.trimStart().startsWith("import ") }
        val bodyLines = gameLines.filterNot { it.trimStart().startsWith("import ") }
        val indentedGame = bodyLines
            .joinToString(separator = "\n") { line -> "        $line" }
        val imports = (importLines + "import rylween.dsl.*")
            .distinct()
            .joinToString(separator = "\n")
            .let { if (it.isBlank()) "" else "$it\n\n" }
        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(
                """
                |// Generated from src/game/kotlin/RylweenGame.dsl.kts.
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

abstract class GenerateGameCatalogHandlesTask : org.gradle.api.DefaultTask() {
    @get:org.gradle.api.tasks.InputFile
    abstract val gameSource: org.gradle.api.file.RegularFileProperty

    @get:org.gradle.api.tasks.OutputFile
    abstract val outputFile: org.gradle.api.file.RegularFileProperty

    @org.gradle.api.tasks.TaskAction
    fun generate() {
        val source = gameSource.get().asFile.readText()
        val buildingNames = buildingNames(source)
        val townNames = townNames(source)
        val sections = listOf(
            generateFlagHandles(flagDeclarations(source)),
            generateItemHandles(itemHandles(source)),
            generateRefsClass(
                className = "MonsterIds",
                objectName = "Monsters",
                typeName = "MonsterId",
                factoryName = "monster",
                names = handleNames(declarationBlock(source, "monsters"), "Monsters", "monster"),
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
                names = handleNames(declarationBlock(source, "terminal"), "ScreenRegions", "region"),
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
                className = "GameMilestoneRefs",
                objectName = "Milestones",
                typeName = "GameMilestoneId",
                factoryName = "milestone",
                names = handleNames(declarationBlock(source, "gameProgress"), "Milestones", "milestone"),
            ),
            generateRefsClass(
                className = "FormulaRefs",
                objectName = "Formulas",
                typeName = "FormulaId",
                factoryName = "formula",
                names = handleNames(declarationBlock(source, "formulas"), "Formulas", "formula"),
                slugOverrides = mapOf("levelUpHitPointFloor" to "level_up_hp_floor"),
            ),
            generateRefsClass(
                className = "RewardBundleRefs",
                objectName = "Rewards",
                typeName = "RewardBundleId",
                factoryName = "rewardBundle",
                names = handleNames(declarationBlock(source, "rewards"), "Rewards", "reward"),
            ),
        ).joinToString(separator = "\n\n")

        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            parentFile.resolve("RylweenGeneratedItemCatalog.kt").takeIf { it != this }?.delete()
            writeText(
                """
                |// Generated from src/game/kotlin/RylweenGame.dsl.kts declarations.
                |// Do not edit directly.
                |
                |@file:Suppress("unused", "MemberVisibilityCanBePrivate", "PropertyName")
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

    private data class FlagDeclaration(
        val scope: String,
        val path: List<String>,
        val states: List<String>,
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

    private fun itemHandles(source: String): ItemHandles {
        val itemBlock = declarationBlock(source, "items")
        val emptyItemNames = Regex("""\bempty\s*\(\s*Items\.([A-Za-z][A-Za-z0-9_]*)""")
            .findAll(itemBlock)
            .map { it.groupValues[1] }
            .toList()
        val declaredItemNames = Regex(
            """\b(?:weapon|armor|gear|potion|treasure|special|magicItem)\s*\(\s*Items\.([A-Za-z][A-Za-z0-9_]*)""",
        )
            .findAll(itemBlock)
            .map { it.groupValues[1] }
            .toList()
        val itemNames = buildList {
            if (emptyItemNames.any { it == "none" || it == "blank" }) {
                add("blank")
            }
            addAll(emptyItemNames.filterNot { it == "none" || it == "blank" })
            addAll(declaredItemNames.filterNot { it == "none" || it == "blank" })
        }.distinctInOrder()
        val poolNames = Regex("""\bpool\s*\(\s*ItemPools\.([A-Za-z][A-Za-z0-9_]*)""")
            .findAll(itemBlock)
            .map { it.groupValues[1] }
            .toList()
            .distinctInOrder()

        require(itemNames.isNotEmpty()) {
            "No item declarations found in ${gameSource.get().asFile}."
        }
        require(poolNames.isNotEmpty()) {
            "No item-pool declarations found in ${gameSource.get().asFile}."
        }

        return ItemHandles(itemNames, emptyItemNames.contains("none"), poolNames)
    }

    private fun flagDeclarations(source: String): List<FlagDeclaration> {
        val flagsBlock = declarationBlock(source, "flags")
        return Regex("""\b(story|encounter)\s*\(\s*Story\.([A-Za-z][A-Za-z0-9_.]*)\s*\)""")
            .findAll(flagsBlock)
            .map { match ->
                val path = match.groupValues[2].split(".")
                val pathPattern = path.joinToString("""\.""") { Regex.escape(it) }
                val states = Regex("""\bStory\.$pathPattern\.([A-Za-z][A-Za-z0-9_]*)""")
                    .findAll(flagsBlock)
                    .map { it.groupValues[1] }
                    .toList()
                    .distinctInOrder()
                require(states.isNotEmpty()) {
                    "Flag Story.${path.joinToString(".")} must declare states in the flags block."
                }
                FlagDeclaration(match.groupValues[1], path, states)
            }
            .toList()
            .distinctBy { it.pathKey }
            .also {
                require(it.isNotEmpty()) {
                    "No flag declarations found in ${gameSource.get().asFile}."
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
            require(!(directFlag != null && nested)) {
                "Story.${childPrefix.joinToString(".")} cannot be both a flag and a flag group."
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
        val terminalBlock = declarationBlock(source, "terminal")
        return Regex("""\bterrain\s*\(\s*Terrain\.([A-Za-z][A-Za-z0-9_]*)\s*,\s*mapCode\s*=\s*'([^'\\]|\\.)'""")
            .findAll(terminalBlock)
            .map { TerrainDeclaration(it.groupValues[1], it.groupValues[2].parseCharLiteral()) }
            .toList()
            .distinctBy { it.name }
            .also {
                require(it.isNotEmpty()) {
                    "No terrain declarations found in ${gameSource.get().asFile} terminal block."
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

    private fun imageHandles(source: String): ImageHandles {
        val staticNames = mutableListOf<String>()
        val sceneNames = mutableListOf<String>()
        Regex("""\bImages\.(Static|Scene|Backdrop)\.([A-Za-z][A-Za-z0-9_]*)""")
            .findAll(source)
            .forEach { match ->
                when (match.groupValues[1]) {
                    "Static" -> staticNames += match.groupValues[2]
                    "Scene", "Backdrop" -> sceneNames += match.groupValues[2]
                }
            }
        return ImageHandles(
            staticNames = staticNames.distinctInOrder(),
            sceneNames = sceneNames.distinctInOrder(),
        ).also {
            require(it.staticNames.isNotEmpty() || it.sceneNames.isNotEmpty()) {
                "No image handle references found in ${gameSource.get().asFile}."
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
        fun nestedSpellNames(group: String): List<String> =
            Regex("""\bspell\s*\(\s*Spells\.${Regex.escape(group)}\.([A-Za-z][A-Za-z0-9_]*)""")
                .findAll(source)
                .map { it.groupValues[1] }
                .toList()
                .distinctInOrder()

        return SpellHandles(
            mageNames = nestedSpellNames("Mage"),
            priestNames = nestedSpellNames("Priest"),
            monsterNames = nestedSpellNames("Monster"),
        ).also {
            require(it.mageNames.isNotEmpty() || it.priestNames.isNotEmpty() || it.monsterNames.isNotEmpty()) {
                "No spell declarations found in ${gameSource.get().asFile}."
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
        require(names.isNotEmpty()) {
            "No $objectName declarations found in ${gameSource.get().asFile}."
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
        require(townNames.isNotEmpty()) {
            "No Towns declarations found in ${gameSource.get().asFile}."
        }
        val positionBuildings = buildingNames.filterNot { it == "none" }
        require(positionBuildings.isNotEmpty()) {
            "No town-position buildings found in ${gameSource.get().asFile}."
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
        val townPositionNames = Regex("""\bTowns\.[A-Za-z][A-Za-z0-9_]*\.([A-Za-z][A-Za-z0-9_]*)""")
            .findAll(source)
            .map { it.groupValues[1] }
            .toList()
        val defaultServiceNames = listOf("merchant", "inn", "tavern", "smith", "church").filter { service ->
            Regex("""\b${Regex.escape(service)}\s*(?:\(|\{)""").containsMatchIn(townAndSystemBlocks)
        }
        return orderedNames(
            (explicitNames + townPositionNames + defaultServiceNames).distinctInOrder(),
            listOf("none", "inn", "tavern", "church", "smith", "merchant", "hut", "lodge"),
        )
    }

    private fun townNames(source: String): List<String> {
        val townsBlock = declarationBlock(source, "towns")
        val literalNames = Regex("""\btown\s*\(\s*"([A-Za-z][A-Za-z0-9_]*)"""")
            .findAll(townsBlock)
            .map { it.groupValues[1] }
            .toList()
        val handleNames = Regex("""\btown\s*\(\s*Towns\.([A-Za-z][A-Za-z0-9_]*)""")
            .findAll(townsBlock)
            .map { it.groupValues[1] }
            .toList()
        return (literalNames + handleNames).distinctInOrder()
    }

    private fun handleNames(block: String, objectName: String, callName: String): List<String> =
        Regex("""\b${Regex.escape(callName)}\s*\(\s*${Regex.escape(objectName)}\.([A-Za-z][A-Za-z0-9_]*)""")
            .findAll(block)
            .map { it.groupValues[1] }
            .toList()
            .distinctInOrder()

    private fun handleNames(block: String, objectName: String): List<String> =
        Regex("""\b${Regex.escape(objectName)}\.([A-Za-z][A-Za-z0-9_]*)""")
            .findAll(block)
            .map { it.groupValues[1] }
            .toList()
            .distinctInOrder()

    private fun staticScreenNames(source: String): List<String> {
        val staticScreenBlock = declarationBlock(declarationBlock(source, "visuals"), "staticScreens")
        return Regex("""\b(?:screen|image|resource|commandHelp)\s*\(\s*StaticScreens\.([A-Za-z][A-Za-z0-9_]*)""")
            .findAll(staticScreenBlock)
            .map { it.groupValues[1] }
            .toList()
            .distinctInOrder()
    }

    private fun declarationBlock(source: String, name: String): String {
        val match = Regex("""\b${Regex.escape(name)}\s*\{""").find(source)
            ?: error("Could not find $name declaration block.")
        val openBrace = match.range.last
        var depth = 0
        for (index in openBrace until source.length) {
            when (source[index]) {
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) {
                        return source.substring(openBrace + 1, index)
                    }
                }
            }
        }
        error("Unclosed $name declaration block.")
    }

    private fun List<String>.distinctInOrder(): List<String> {
        val seen = linkedSetOf<String>()
        return filter { seen.add(it) }
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

    private fun String.toUpperCamel(): String =
        replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    private fun String.parseCharLiteral(): Char =
        when {
            length == 1 -> single()
            startsWith("\\") && length == 2 -> when (this[1]) {
                '\\' -> '\\'
                '\'' -> '\''
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                else -> error("Unsupported char escape '$this'.")
            }
            else -> error("Unsupported char literal '$this'.")
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
}

abstract class GenerateAnsiBuildWrapperTask : org.gradle.api.DefaultTask() {
    @get:org.gradle.api.tasks.OutputFile
    abstract val outputFile: org.gradle.api.file.RegularFileProperty

    @org.gradle.api.tasks.TaskAction
    fun generate() {
        val output = outputFile.get().asFile
        output.parentFile.mkdirs()
        output.writeText(
            """
            |#!/usr/bin/env bash
            |set -euo pipefail
            |
            |SCRIPT_DIR="$(cd "$(dirname "${'$'}{BASH_SOURCE[0]}")" && pwd -P)"
            |APP_HOME="$(cd "${'$'}SCRIPT_DIR/.." && pwd -P)"
            |PROJECT_DIR="$(cd "${'$'}APP_HOME/../../.." && pwd -P)"
            |
            |build=false
            |args=()
            |for arg in "${'$'}@"; do
            |  if [[ "${'$'}arg" == "--build" ]]; then
            |    build=true
            |  else
            |    args+=("${'$'}arg")
            |  fi
            |done
            |
            |if [[ "${'$'}build" == true ]]; then
            |  if [[ ! -x "${'$'}PROJECT_DIR/gradlew" ]]; then
            |    echo "rylween: cannot find Gradle wrapper at ${'$'}PROJECT_DIR/gradlew" >&2
            |    exit 1
            |  fi
            |  (cd "${'$'}PROJECT_DIR" && ./gradlew installKotlinAnsi --console=plain)
            |fi
            |
            |exec "${'$'}APP_HOME/bin/rylween-app" "${'$'}{args[@]}"
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

val generatedGameHarnessDir = layout.buildDirectory.dir("generated/gameHarness/kotlin")
val generatedGameHarnessFile = layout.buildDirectory.file("generated/gameHarness/kotlin/RylweenCompiledGame.kt")
val generatedGameCatalogDir = layout.buildDirectory.dir("generated/gameCatalog/kotlin")
val generatedGameCatalogFile = layout.buildDirectory.file(
    "generated/gameCatalog/kotlin/rylween/game/RylweenGeneratedGameCatalog.kt",
)

kotlin {
    jvmToolchain(25)
}

val gameSourceSet = sourceSets.create("game") {
    java.srcDir("src/game/kotlin")
    java.srcDir(generatedGameCatalogDir)
    compileClasspath += sourceSets.named("main").get().output
    runtimeClasspath += output + compileClasspath
}

val appSourceSet = sourceSets.create("app") {
    java.srcDir("src/app/kotlin")
    java.srcDir(generatedGameHarnessDir)
    resources.srcDir("data")
    resources.include("ansi/**", "tiles/**", "rylween.doc", "score.txt", "vscore.txt")
    compileClasspath += sourceSets.named("main").get().output + gameSourceSet.output
    runtimeClasspath += output + compileClasspath
}

sourceSets.named("test") {
    java.srcDir(generatedGameHarnessDir)
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

val generateCompiledGameHarness by tasks.registering(GenerateCompiledGameHarnessTask::class) {
    group = "generation"
    description = "Generates a test harness that compiles the game DSL as Kotlin and exposes nextRylween."

    gameSource.set(layout.projectDirectory.file("src/game/kotlin/RylweenGame.dsl.kts"))
    outputFile.set(generatedGameHarnessFile)
}

val generateGameCatalogHandles by tasks.registering(GenerateGameCatalogHandlesTask::class) {
    group = "generation"
    description = "Generates current-game item and item-pool handles from the game DSL declarations."

    gameSource.set(layout.projectDirectory.file("src/game/kotlin/RylweenGame.dsl.kts"))
    outputFile.set(generatedGameCatalogFile)
}

val kotlinCompiler by configurations.creating
val gameDslRuntime by configurations.creating

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
}

tasks.named("compileAppKotlin") {
    dependsOn(generateCompiledGameHarness)
    dependsOn(tasks.named(gameSourceSet.classesTaskName))
}

val runKotlinTown by tasks.registering(JavaExec::class) {
    group = "application"
    description = "Boots the compiled game in the pure Kotlin terminal town runner."

    dependsOn(tasks.named(appSourceSet.classesTaskName))
    classpath = appSourceSet.runtimeClasspath
    mainClass.set("rylween.app.RylweenTerminalMainKt")
    standardInput = System.`in`
}

val runKotlinGame by tasks.registering(JavaExec::class) {
    group = "application"
    description = "Boots the compiled game in the pure Kotlin terminal wilderness runner."

    dependsOn(tasks.named(appSourceSet.classesTaskName))
    classpath = appSourceSet.runtimeClasspath
    mainClass.set("rylween.app.RylweenGameTerminalMain")
    standardInput = System.`in`
}

val kotlinAnsiJar by tasks.registering(Jar::class) {
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

val kotlinAnsiRuntimeJars = appSourceSet.runtimeClasspath.filter { it.isFile && it.extension == "jar" }
val kotlinAnsiWrapper = layout.buildDirectory.file("scripts/kotlinAnsiWrapper/rylween")

val kotlinAnsiStartScripts by tasks.registering(CreateStartScripts::class) {
    group = "application"
    description = "Generates direct start scripts for the ANSI runtime."

    dependsOn(kotlinAnsiJar)
    applicationName = "rylween-app"
    mainClass.set("rylween.app.RylweenAnsiMain")
    outputDir = layout.buildDirectory.dir("scripts/kotlinAnsiApp").get().asFile
    classpath = files(kotlinAnsiJar) + kotlinAnsiRuntimeJars
}

val kotlinAnsiBuildWrapper by tasks.registering(GenerateAnsiBuildWrapperTask::class) {
    group = "application"
    description = "Generates the ANSI runtime wrapper with an optional --build refresh."

    outputFile.set(kotlinAnsiWrapper)
}

val installKotlinAnsi by tasks.registering(Sync::class) {
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

val validateGameDsl by tasks.registering(JavaExec::class) {
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
        layout.projectDirectory.file("src/game/kotlin/RylweenGame.dsl.kts").asFile.absolutePath,
    )
}

tasks.check {
    dependsOn(validateGameDsl)
    dependsOn(tasks.named(appSourceSet.classesTaskName))
}
