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
        val indentedGame = gameSource.get().asFile
            .readLines()
            .joinToString(separator = "\n") { line -> "        $line" }
        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(
                """
                |// Generated from src/game/kotlin/RylweenGame.dsl.kts.
                |// Do not edit directly.

                |@file:Suppress("UNNECESSARY_SAFE_CALL")

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

group = "org.rylween"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val generatedGameHarnessDir = layout.buildDirectory.dir("generated/gameHarness/kotlin")
val generatedGameHarnessFile = layout.buildDirectory.file("generated/gameHarness/kotlin/RylweenCompiledGame.kt")

kotlin {
    jvmToolchain(25)
}

val gameSourceSet = sourceSets.create("game") {
    java.srcDir("src/game/kotlin")
    compileClasspath += sourceSets.named("main").get().output
    runtimeClasspath += output + compileClasspath
}

val appSourceSet = sourceSets.create("app") {
    java.srcDir("src/app/kotlin")
    java.srcDir(generatedGameHarnessDir)
    resources.srcDir("data")
    resources.include("ansi/**", "tiles/**", "map.dat", "rylween.doc", "score.txt", "vscore.txt")
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

tasks.named("compileAppKotlin") {
    dependsOn(generateCompiledGameHarness)
    dependsOn(tasks.named(gameSourceSet.classesTaskName))
}

val runKotlinTown by tasks.registering(JavaExec::class) {
    group = "application"
    description = "Boots the compiled game in the pure Kotlin terminal town runner."

    dependsOn(tasks.named(appSourceSet.classesTaskName))
    classpath = appSourceSet.runtimeClasspath
    mainClass.set("RylweenTerminalMainKt")
    standardInput = System.`in`
}

val runKotlinGame by tasks.registering(JavaExec::class) {
    group = "application"
    description = "Boots the compiled game in the pure Kotlin terminal wilderness runner."

    dependsOn(tasks.named(appSourceSet.classesTaskName))
    classpath = appSourceSet.runtimeClasspath
    mainClass.set("RylweenGameTerminalMain")
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
        attributes["Main-Class"] = "RylweenAnsiMain"
    }
}

val kotlinAnsiRuntimeJars = appSourceSet.runtimeClasspath.filter { it.isFile && it.extension == "jar" }

val kotlinAnsiStartScripts by tasks.registering(CreateStartScripts::class) {
    group = "application"
    description = "Generates direct start scripts for the ANSI runtime."

    dependsOn(kotlinAnsiJar)
    applicationName = "rylween"
    mainClass.set("RylweenAnsiMain")
    outputDir = layout.buildDirectory.dir("scripts/kotlinAnsi").get().asFile
    classpath = files(kotlinAnsiJar) + kotlinAnsiRuntimeJars
}

val installKotlinAnsi by tasks.registering(Sync::class) {
    group = "application"
    description = "Installs the direct ANSI runtime launcher outside Gradle's interactive console."

    dependsOn(kotlinAnsiStartScripts)
    into(layout.buildDirectory.dir("install/rylween-kotlin"))
    from(kotlinAnsiStartScripts) {
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
