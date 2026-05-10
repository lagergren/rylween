import org.gradle.api.file.ConfigurableFileCollection
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import rylween.gradle.GenerateCompiledGameHarnessTask
import rylween.gradle.GenerateGameCatalogHandlesTask

plugins {
    idea
    alias(libs.plugins.kotlin.jvm)
    id("rylween.qa-conventions")
}

group = "org.rylween"
version = rootProject.version

repositories {
    mavenCentral()
}

val rylweenJvmToolchainVersion: Int =
    libs.versions.jdk
        .get()
        .toInt()

kotlin {
    jvmToolchain(rylweenJvmToolchainVersion)
}

idea {
    module {
        name = "rylween.game"
    }
}

val generatedGameHarnessDir: Provider<Directory> = layout.buildDirectory.dir("generated/gameHarness/kotlin")
val generatedGameHarnessFile: Provider<RegularFile> =
    layout.buildDirectory.file(
        "generated/gameHarness/kotlin/rylween/game/RylweenCompiledGame.kt",
    )
val generatedGameCatalogDir: Provider<Directory> = layout.buildDirectory.dir("generated/gameCatalog/kotlin")
val generatedGameCatalogFile: Provider<RegularFile> =
    layout.buildDirectory.file(
        "generated/gameCatalog/kotlin/rylween/game/RylweenGeneratedGameCatalog.kt",
    )
val gameDslSourceFile: RegularFile = layout.projectDirectory.file("src/main/dsl/RylweenGame.dsl.kts")
val gameDslScenarioModuleFile: RegularFile = layout.projectDirectory.file("src/main/dsl/RylweenTestScenarios.dsl.kts")
val includeTestScenarioDsl: Provider<Boolean> =
    providers
        .gradleProperty("rylween.includeTestScenarios")
        .map(String::toBoolean)
        .orElse(true)
val gameDslModuleFiles: ConfigurableFileCollection =
    objects.fileCollection().from(gameDslSourceFile).also { modules ->
        if (includeTestScenarioDsl.get()) {
            modules.from(gameDslScenarioModuleFile)
        }
    }
val gameDslSourceLabel: String = gameDslSourceFile.asFile.relativeTo(projectDir).invariantSeparatorsPath

sourceSets.named("main") {
    java.srcDir(generatedGameHarnessDir)
    java.srcDir(generatedGameCatalogDir)
}

dependencies {
    implementation(project(":core"))
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    workingDir = rootProject.projectDir
    useJUnitPlatform()
}

val generateCompiledGameHarness: TaskProvider<GenerateCompiledGameHarnessTask> by tasks.registering(
    GenerateCompiledGameHarnessTask::class,
) {
    group = "generation"
    description = "Generates a test harness that compiles the game DSL as Kotlin and exposes nextRylween."

    gameSource.set(gameDslSourceFile)
    gameSources.from(gameDslModuleFiles)
    sourceLabel.set(gameDslSourceLabel)
    outputFile.set(generatedGameHarnessFile)
}

val generateGameCatalogHandles: TaskProvider<GenerateGameCatalogHandlesTask> by tasks.registering(GenerateGameCatalogHandlesTask::class) {
    group = "generation"
    description = "Generates current-game item and item-pool handles from the game DSL declarations."

    gameSource.set(gameDslSourceFile)
    gameSources.from(gameDslModuleFiles)
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
    archiveBaseName = "rylween-game"
}

tasks.named<KotlinCompile>("compileKotlin") {
    dependsOn(generateGameCatalogHandles)
    dependsOn(generateCompiledGameHarness)
}

val validateGameDsl: TaskProvider<JavaExec> by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Compiles and executes the game Kotlin DSL source against the built DSL jar."

    val coreJar = project(":core").tasks.named<Jar>("jar")
    dependsOn(coreJar)
    dependsOn(tasks.named("classes"))
    classpath = kotlinCompiler
    mainClass.set("org.jetbrains.kotlin.cli.jvm.K2JVMCompiler")
    val gameKotlinClasses = layout.buildDirectory.dir("classes/kotlin/main")
    val gameDslClasspath = files(coreJar.flatMap { it.archiveFile }, gameKotlinClasses) + gameDslRuntime
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
}
