plugins {
    alias(libs.plugins.kotlin.jvm)
}

abstract class ValidateGeneratedMetadataTask : org.gradle.api.DefaultTask() {
    @get:org.gradle.api.tasks.InputFile
    abstract val sourceFile: org.gradle.api.file.RegularFileProperty

    @get:org.gradle.api.tasks.InputFile
    abstract val outputFile: org.gradle.api.file.RegularFileProperty

    @org.gradle.api.tasks.TaskAction
    fun validate() {
        val sourceText = sourceFile.get().asFile.readText()
        val outputText = outputFile.get().asFile.readText()
        if (sourceText != outputText) {
            throw org.gradle.api.GradleException(
                "Generated metadata is out of sync. Run ./gradlew generateRylweenMetadata and commit the result.",
            )
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

group = "org.rylween"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val generatedMetadataSource = layout.projectDirectory.file("src/generator/metadata/RylweenGeneratedMetadata.kt")
val generatedMetadataOutput = layout.projectDirectory.file("src/generated/kotlin/RylweenGeneratedMetadata.kt")
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

sourceSets.named("test") {
    java.srcDir(generatedScratchHarnessDir)
    compileClasspath += gameSourceSet.output
    runtimeClasspath += gameSourceSet.output
}

configurations.named(gameSourceSet.implementationConfigurationName) {
    extendsFrom(configurations.implementation.get())
}

dependencies {
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

val generateRylweenMetadata by tasks.registering(Copy::class) {
    group = "generation"
    description = "Copies the migration metadata fixture into the checked-in generated Kotlin source."

    from(generatedMetadataSource)
    into(generatedMetadataOutput.asFile.parentFile)
}

val validateGeneratedMetadata by tasks.registering(ValidateGeneratedMetadataTask::class) {
    group = "verification"
    description = "Verifies checked-in generated metadata matches the migration source fixture."

    sourceFile.set(generatedMetadataSource)
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
}
