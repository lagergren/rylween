import java.io.File

plugins {
    alias(libs.plugins.kotlin.jvm)
}

abstract class ValidateGeneratedMetadataTask : org.gradle.api.DefaultTask() {
    @get:org.gradle.api.tasks.InputFile
    abstract val templateFile: org.gradle.api.file.RegularFileProperty

    @get:org.gradle.api.tasks.InputDirectory
    @get:org.gradle.api.tasks.PathSensitive(org.gradle.api.tasks.PathSensitivity.RELATIVE)
    abstract val itemDirectory: org.gradle.api.file.DirectoryProperty

    @get:org.gradle.api.tasks.InputFile
    abstract val outputFile: org.gradle.api.file.RegularFileProperty

    @org.gradle.api.tasks.TaskAction
    fun validate() {
        val sourceText = RylweenMetadataGenerator.render(
            templateFile.get().asFile,
            itemDirectory.get().asFile,
        )
        val outputText = outputFile.get().asFile.readText()
        if (sourceText != outputText) {
            throw org.gradle.api.GradleException(
                "Generated metadata is out of sync. Run ./gradlew generateRylweenMetadata and commit the result.",
            )
        }
    }
}

abstract class GenerateRylweenMetadataTask : org.gradle.api.DefaultTask() {
    @get:org.gradle.api.tasks.InputFile
    abstract val templateFile: org.gradle.api.file.RegularFileProperty

    @get:org.gradle.api.tasks.InputDirectory
    @get:org.gradle.api.tasks.PathSensitive(org.gradle.api.tasks.PathSensitivity.RELATIVE)
    abstract val itemDirectory: org.gradle.api.file.DirectoryProperty

    @get:org.gradle.api.tasks.OutputFile
    abstract val outputFile: org.gradle.api.file.RegularFileProperty

    @org.gradle.api.tasks.TaskAction
    fun generate() {
        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(RylweenMetadataGenerator.render(templateFile.get().asFile, itemDirectory.get().asFile))
        }
    }
}

abstract class GenerateCompiledScratchHarnessTask : org.gradle.api.DefaultTask() {
    @get:org.gradle.api.tasks.InputFile
    abstract val scratchSource: org.gradle.api.file.RegularFileProperty

    @get:org.gradle.api.tasks.OutputFile
    abstract val outputFile: org.gradle.api.file.RegularFileProperty

    @org.gradle.api.tasks.TaskAction
    fun generate() {
        val indentedScratch = scratchSource.get().asFile
            .readLines()
            .joinToString(separator = "\n") { line -> "        $line" }
        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(
                """
                |// Generated from src/scratch/kotlin/rylween-full-scratch.dsl.kts.
                |// Do not edit directly.

                |@file:Suppress("UNNECESSARY_SAFE_CALL")

                |object RylweenCompiledScratch {
                |    val nextRylween: RylweenModel
                |    val nextDsl: GameModel

                |    init {
                |$indentedScratch

                |        this.nextRylween = nextRylween
                |        this.nextDsl = nextDsl
                |    }
                |}
                """.trimMargin() + "\n",
            )
        }
    }
}

private object RylweenMetadataGenerator {
    private const val itemSectionStart = "// -----------------------------------------------------------------------------\n" +
            "// Generated item handles from data/world/items/*.json5.\n" +
            "// -----------------------------------------------------------------------------"
    private const val itemSectionEnd = "// -----------------------------------------------------------------------------\n" +
            "// Generated game-state milestone handles.\n" +
            "// -----------------------------------------------------------------------------"

    private val shards = listOf(
        ItemShard("weapons.json5", "Weapons", "Weapon"),
        ItemShard("armor.json5", "Armor", "Armor"),
        ItemShard("gear.json5", "Gear", "Gear"),
        ItemShard("potions.json5", "Potions", "Potion"),
        ItemShard("treasures.json5", "Treasures", "Treasure"),
        ItemShard("specials.json5", "Flag-backed special items project from milestones and do not occupy slots.", "Special"),
        ItemShard("magic_items.json5", "Magic items", "MagicItem"),
    )

    fun render(templateFile: File, itemDirectory: File): String {
        val template = templateFile.readText()
        val sectionStart = template.indexOf(itemSectionStart)
        check(sectionStart >= 0) { "missing generated item metadata section start in ${templateFile.path}" }
        val sectionEnd = template.indexOf(itemSectionEnd, startIndex = sectionStart)
        check(sectionEnd >= 0) { "missing generated item metadata section end in ${templateFile.path}" }
        return template.substring(0, sectionStart) +
                renderItemSection(itemDirectory) +
                "\n\n" +
                template.substring(sectionEnd)
    }

    private fun renderItemSection(itemDirectory: File): String {
        val items = shards.flatMap { shard ->
            parseShard(File(itemDirectory, shard.fileName), shard)
        }
        val byId = items.associateBy { it.legacyId }
        check(byId.size == items.size) { "duplicate item ids in data/world/items" }
        check(items.map { it.slug }.toSet().size == items.size) { "duplicate item slugs in data/world/items" }
        check(items.firstOrNull()?.slug == "blank") { "first generated item must be blank sentinel" }

        return buildString {
            appendLine(itemSectionStart)
            appendLine()
            appendLine("class ItemRefs {")
            shards.forEachIndexed { index, shard ->
                val shardItems = items.filter { it.shard == shard }
                appendLine("    // ${shard.comment}${shard.comment.sentenceSuffix()}")
                shardItems.forEach { item ->
                    appendLine("    val ${item.propertyName} = generatedItem(\"${item.slug}\", legacyId = ${item.legacyId})")
                    if (item.slug == "blank") {
                        appendLine("    val none = blank")
                    }
                }
                if (index != shards.lastIndex) appendLine()
            }
            appendLine("}")
            appendLine("val Items = ItemRefs()")
            appendLine()
            appendLine("private fun generatedItemRecord(")
            appendLine("    id: ItemId,")
            appendLine("    kind: ItemKind,")
            appendLine("    displayName: String,")
            appendLine("    cost: Int,")
            appendLine("    storage: ItemStorageKind = ItemStorageKind.SlottedInventory,")
            appendLine("    weaponDamage: Int? = null,")
            appendLine("    armorClass: Int? = null,")
            appendLine("    potionEffect: PotionEffectModel? = null,")
            appendLine("): ItemRecordModel =")
            appendLine("    ItemRecordModel(id, kind, displayName, cost, storage, weaponDamage, armorClass, potionEffect)")
            appendLine()
            appendLine("class GeneratedItemCatalog {")
            appendLine("    val all = listOf(")
            items.forEachIndexed { index, item ->
                val previous = items.getOrNull(index - 1)?.shard
                if (previous != item.shard) {
                    if (index > 0) appendLine()
                    appendLine("        // ${item.shard.comment}${item.shard.comment.sentenceSuffix()}")
                }
                append(renderItemRecord(item).prependIndent("        "))
                appendLine(",")
            }
            appendLine("    )")
            appendLine("}")
            appendLine("val ItemCatalog = GeneratedItemCatalog()")
            appendLine()
            appendLine(renderItemPools(items))
        }.trimEnd()
    }

    private fun parseShard(file: File, shard: ItemShard): List<GeneratedItem> =
        parseRecords(file.readText()).map { record ->
            val body = record.body
            val effectKind = stringField(body, "kind")
            GeneratedItem(
                shard = shard,
                slug = record.slug,
                propertyName = slugToProperty(record.slug),
                legacyId = intField(body, "id"),
                displayName = stringField(body, "name") ?: error("missing name for ${record.slug}"),
                cost = intField(body, "cost"),
                kind = when {
                    record.slug == "blank" -> "Empty"
                    else -> shard.itemKind
                },
                storage = when {
                    record.slug == "blank" -> "EmptySentinel"
                    shard.itemKind == "Special" -> "SpecialMilestone"
                    else -> "SlottedInventory"
                },
                weaponDamage = intFieldOrNull(body, "damage"),
                armorClass = intFieldOrNull(body, "ac"),
                potionEffect = effectKind?.let { PotionEffect(it, intFieldOrNull(body, "amount")) },
            )
        }

    private fun parseRecords(source: String): List<RawItemRecord> {
        val text = stripLineComments(source)
        val start = text.indexOf('{')
        check(start >= 0) { "missing item shard object" }
        val result = mutableListOf<RawItemRecord>()
        var index = start + 1
        while (index < text.length) {
            index = skipSpaceAndCommas(text, index)
            if (index >= text.length || text[index] == '}') break
            val keyStart = index
            while (index < text.length && (text[index].isLetterOrDigit() || text[index] == '_')) index++
            val slug = text.substring(keyStart, index)
            index = skipWhitespace(text, index)
            check(index < text.length && text[index] == ':') { "expected ':' after item key $slug" }
            index = skipWhitespace(text, index + 1)
            check(index < text.length && text[index] == '{') { "expected object body for item $slug" }
            val bodyEnd = findMatchingBrace(text, index)
            result += RawItemRecord(slug, text.substring(index + 1, bodyEnd))
            index = bodyEnd + 1
        }
        return result
    }

    private fun renderItemRecord(item: GeneratedItem): String {
        val args = buildList {
            add("Items.${item.propertyName}")
            add("ItemKind.${item.kind}")
            add("\"${kotlinString(item.displayName)}\"")
            add("cost = ${item.cost}")
            if (item.storage != "SlottedInventory") add("storage = ItemStorageKind.${item.storage}")
            item.weaponDamage?.let { add("weaponDamage = $it") }
            item.armorClass?.let { add("armorClass = $it") }
            item.potionEffect?.let { add("potionEffect = ${renderPotionEffect(it)}") }
        }
        return if (args.size <= 4 || (item.storage == "SlottedInventory" && item.potionEffect == null && args.size <= 5)) {
            "generatedItemRecord(${args.joinToString()})"
        } else {
            buildString {
                appendLine("generatedItemRecord(")
                args.forEach { appendLine("    $it,") }
                append(")")
            }
        }
    }

    private fun renderPotionEffect(effect: PotionEffect): String {
        val kind = when (effect.kind) {
            "heal" -> "HealHitPoints"
            "poison" -> "PoisonHitPoints"
            "restore_mana" -> "RestoreMana"
            else -> error("unknown potion effect kind ${effect.kind}")
        }
        return if (effect.amount == null) {
            "PotionEffectModel(PotionEffectKind.$kind)"
        } else {
            "PotionEffectModel(PotionEffectKind.$kind, amount = ${effect.amount})"
        }
    }

    private fun renderItemPools(items: List<GeneratedItem>): String {
        fun item(slug: String): GeneratedItem =
            items.singleOrNull { it.slug == slug } ?: error("missing generated item $slug")

        fun renderItemList(poolItems: List<GeneratedItem>): String =
            poolItems.joinToString(separator = "\n") { item -> "            Items.${item.propertyName}," }

        fun renderPool(name: String, slug: String, poolItems: List<GeneratedItem>): String =
            """
            |    val $name = generatedItemPool(
            |        "$slug",
            |        items = listOf(
            |${renderItemList(poolItems)}
            |        ),
            |    )
            """.trimMargin()

        val battleSpoils = items.filter { it.kind == "Potion" || it.kind == "Treasure" }
            .filterNot { it.slug == "donkey" }
        val magicItems = items.filter { it.kind == "MagicItem" }
        val tavernScruffySpecials = listOf(
            item("magic_dagger"),
            item("magic_axe"),
            item("admt_mail"),
            item("amber_potion"),
            item("green_gem"),
        ) + magicItems
        return """
            |class ItemPoolRefs {
            |    // Potions plus ordinary treasure, excluding donkey and milestone specials.
            |${renderPool("battleSpoils", "battle_spoils", battleSpoils)}
            |
            |${renderPool("magicItems", "magic_items", magicItems)}
            |
            |${renderPool(
                "tavernScruffySpecials",
                "tavern_scruffy_specials",
                tavernScruffySpecials,
            )}
            |}
            |val ItemPools = ItemPoolRefs()
        """.trimMargin()
    }

    private fun stripLineComments(source: String): String =
        buildString {
            var quote: Char? = null
            var index = 0
            while (index < source.length) {
                val char = source[index]
                val next = source.getOrNull(index + 1)
                if (quote == null && char == '/' && next == '/') {
                    while (index < source.length && source[index] != '\n') index++
                    if (index < source.length) append(source[index++])
                } else {
                    append(char)
                    quote = when {
                        quote == null && (char == '\'' || char == '"') -> char
                        quote == char && source.getOrNull(index - 1) != '\\' -> null
                        else -> quote
                    }
                    index++
                }
            }
        }

    private fun findMatchingBrace(text: String, start: Int): Int {
        var depth = 0
        var quote: Char? = null
        var index = start
        while (index < text.length) {
            val char = text[index]
            quote = when {
                quote == null && (char == '\'' || char == '"') -> char
                quote == char && text.getOrNull(index - 1) != '\\' -> null
                else -> quote
            }
            if (quote == null) {
                when (char) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return index
                    }
                }
            }
            index++
        }
        error("unclosed item object")
    }

    private fun skipSpaceAndCommas(text: String, start: Int): Int {
        var index = start
        while (index < text.length && (text[index].isWhitespace() || text[index] == ',')) index++
        return index
    }

    private fun skipWhitespace(text: String, start: Int): Int {
        var index = start
        while (index < text.length && text[index].isWhitespace()) index++
        return index
    }

    private fun intField(body: String, field: String): Int =
        intFieldOrNull(body, field) ?: error("missing integer field $field")

    private fun intFieldOrNull(body: String, field: String): Int? =
        Regex("""\b${Regex.escape(field)}\s*:\s*(-?\d+)""")
            .find(body)
            ?.groupValues
            ?.get(1)
            ?.toInt()

    private fun stringField(body: String, field: String): String? {
        val single = Regex("""\b${Regex.escape(field)}\s*:\s*'((?:\\'|[^'])*)'""")
            .find(body)
            ?.groupValues
            ?.get(1)
            ?.replace("\\'", "'")
        if (single != null) return single
        return Regex("""\b${Regex.escape(field)}\s*:\s*"((?:\\"|[^"])*)"""")
            .find(body)
            ?.groupValues
            ?.get(1)
            ?.replace("\\\"", "\"")
    }

    private fun slugToProperty(slug: String): String =
        slug.split("_").let { parts ->
            parts.first() + parts.drop(1).joinToString("") { part -> part.replaceFirstChar(Char::uppercaseChar) }
        }

    private fun kotlinString(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("$", "\${'$'}")

    private fun String.sentenceSuffix(): String =
        if (endsWith(".")) "" else "."

    private data class ItemShard(val fileName: String, val comment: String, val itemKind: String)
    private data class RawItemRecord(val slug: String, val body: String)
    private data class PotionEffect(val kind: String, val amount: Int?)
    private data class GeneratedItem(
        val shard: ItemShard,
        val slug: String,
        val propertyName: String,
        val legacyId: Int,
        val displayName: String,
        val cost: Int,
        val kind: String,
        val storage: String,
        val weaponDamage: Int?,
        val armorClass: Int?,
        val potionEffect: PotionEffect?,
    )
}

group = "org.rylween"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val generatedMetadataSource = layout.projectDirectory.file("src/generator/metadata/RylweenGeneratedMetadata.kt")
val generatedMetadataOutput = layout.projectDirectory.file("src/generated/kotlin/RylweenGeneratedMetadata.kt")
val generatedMetadataItemDirectory = layout.projectDirectory.dir("data/world/items")
val generatedScratchHarnessDir = layout.buildDirectory.dir("generated/scratchHarness/kotlin")
val generatedScratchHarnessFile = layout.buildDirectory.file("generated/scratchHarness/kotlin/RylweenCompiledScratch.kt")

kotlin {
    jvmToolchain(25)
}

sourceSets.named("main") {
    java.srcDir("src/generated/kotlin")
}

val gameSourceSet = sourceSets.create("game") {
    java.srcDir("src/game/kotlin")
    compileClasspath += sourceSets.named("main").get().output
    runtimeClasspath += output + compileClasspath
}

val appSourceSet = sourceSets.create("app") {
    java.srcDir("src/app/kotlin")
    java.srcDir(generatedScratchHarnessDir)
    compileClasspath += sourceSets.named("main").get().output + gameSourceSet.output
    runtimeClasspath += output + compileClasspath
}

sourceSets.named("test") {
    java.srcDir(generatedScratchHarnessDir)
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
}

tasks.test {
    useJUnitPlatform()
}

val generateRylweenMetadata by tasks.registering(GenerateRylweenMetadataTask::class) {
    group = "generation"
    description = "Generates checked-in Kotlin metadata from the migration template and item shards."

    templateFile.set(generatedMetadataSource)
    itemDirectory.set(generatedMetadataItemDirectory)
    outputFile.set(generatedMetadataOutput)
}

val validateGeneratedMetadata by tasks.registering(ValidateGeneratedMetadataTask::class) {
    group = "verification"
    description = "Verifies checked-in generated metadata matches the migration template and item shards."

    templateFile.set(generatedMetadataSource)
    itemDirectory.set(generatedMetadataItemDirectory)
    outputFile.set(generatedMetadataOutput)
}

val generateCompiledScratchHarness by tasks.registering(GenerateCompiledScratchHarnessTask::class) {
    group = "generation"
    description = "Generates a test harness that compiles the scratch DSL as Kotlin and exposes nextRylween."

    scratchSource.set(layout.projectDirectory.file("src/scratch/kotlin/rylween-full-scratch.dsl.kts"))
    outputFile.set(generatedScratchHarnessFile)
}

val kotlinCompiler by configurations.creating
val scratchRuntime by configurations.creating

dependencies {
    val kotlinVersion = libs.versions.kotlin.get()
    kotlinCompiler("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion")
    kotlinCompiler("org.jetbrains.kotlin:kotlin-scripting-compiler-embeddable:$kotlinVersion")
    kotlinCompiler("org.jetbrains.kotlin:kotlin-scripting-jvm:$kotlinVersion")
    scratchRuntime("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    scratchRuntime("org.jetbrains.kotlin:kotlin-script-runtime:$kotlinVersion")
    scratchRuntime("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
}

tasks.jar {
    archiveBaseName = "rylween-dsl"
}

tasks.named("compileKotlin") {
    dependsOn(validateGeneratedMetadata)
}

tasks.named("compileTestKotlin") {
    dependsOn(generateCompiledScratchHarness)
    dependsOn(tasks.named(gameSourceSet.classesTaskName))
}

tasks.named("compileAppKotlin") {
    dependsOn(generateCompiledScratchHarness)
    dependsOn(tasks.named(gameSourceSet.classesTaskName))
}

val runKotlinTown by tasks.registering(JavaExec::class) {
    group = "application"
    description = "Boots the compiled scratch game in the pure Kotlin terminal town runner."

    dependsOn(tasks.named(appSourceSet.classesTaskName))
    classpath = appSourceSet.runtimeClasspath
    mainClass.set("RylweenTerminalMainKt")
    standardInput = System.`in`
}

val validateScratch by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Compiles and executes the scratch Kotlin DSL source against the built DSL jar."

    dependsOn(tasks.jar)
    dependsOn(tasks.named(gameSourceSet.classesTaskName))
    classpath = kotlinCompiler
    mainClass.set("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
    val gameKotlinClasses = layout.buildDirectory.dir("classes/kotlin/${gameSourceSet.name}")
    val scratchClasspath = files(tasks.jar.flatMap { it.archiveFile }, gameKotlinClasses) + scratchRuntime
    args(
        "-no-stdlib",
        "-no-reflect",
        "-classpath",
        scratchClasspath.asPath,
        "-script",
        layout.projectDirectory.file("src/scratch/kotlin/rylween-full-scratch.dsl.kts").asFile.absolutePath,
    )
}

tasks.check {
    dependsOn(validateGeneratedMetadata)
    dependsOn(validateScratch)
    dependsOn(tasks.named(appSourceSet.classesTaskName))
}
