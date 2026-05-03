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
        val includeNoneAlias = emptyItemNames.contains("none")

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

        val itemLines = itemNames.joinToString(separator = "\n") { name ->
            """    val $name: ItemId = item("${name.toSlug()}")"""
        }
        val noneAlias = if (includeNoneAlias) {
            "\n    val none: ItemId = blank"
        } else {
            ""
        }
        val poolLines = poolNames.joinToString(separator = "\n") { name ->
            """    val $name: ItemPoolId = itemPool("${name.toSlug()}")"""
        }

        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(
                """
                |// Generated from src/game/kotlin/RylweenGame.dsl.kts item declarations.
                |// Do not edit directly.
                |
                |@file:Suppress("unused", "MemberVisibilityCanBePrivate")
                |
                |package rylween.game
                |
                |import rylween.dsl.ItemId
                |import rylween.dsl.ItemPoolId
                |import rylween.dsl.item
                |import rylween.dsl.itemPool
                |
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
                """.trimMargin() + "\n",
            )
        }
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

    private fun String.toSlug(): String =
        replace(Regex("([a-z0-9])([A-Z])"), "$1_$2").lowercase()
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
    "generated/gameCatalog/kotlin/rylween/game/RylweenGeneratedItemCatalog.kt",
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
