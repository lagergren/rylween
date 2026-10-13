// Builds authored Rylween content and generated catalog handles.
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import rylween.gradle.GenerateGameCatalogHandlesTask

plugins {
    idea
    `java-library`
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
        generatedSourceDirs.add(
            layout.buildDirectory
                .dir("generated/gameCatalog/kotlin")
                .get()
                .asFile,
        )
    }
}

val generatedGameCatalogDir: Provider<Directory> = layout.buildDirectory.dir("generated/gameCatalog/kotlin")
val generatedGameCatalogFile: Provider<RegularFile> =
    layout.buildDirectory.file(
        "generated/gameCatalog/kotlin/rylween/game/RylweenGeneratedGameCatalog.kt",
    )
val gameDslSourceFile: RegularFile =
    layout.projectDirectory.file("src/main/kotlin/rylween/game/RylweenGame.kt")
val gameDslScenarioFile: RegularFile =
    layout.projectDirectory.file("src/main/kotlin/rylween/game/RylweenTestScenarios.kt")
val gameDslSourceLabel: String = gameDslSourceFile.asFile.relativeTo(projectDir).invariantSeparatorsPath

sourceSets {
    main {
        kotlin.srcDir(generatedGameCatalogDir)
    }
}

dependencies {
    api(project(":core"))
}

tasks.test {
    workingDir = rootProject.projectDir
}

val generateGameCatalogHandles: TaskProvider<GenerateGameCatalogHandlesTask> =
    tasks.register<GenerateGameCatalogHandlesTask>("generateGameCatalogHandles") {
        group = "generation"
        description = "Generates current-game item and item-pool handles from the authored RylweenCompiledGame.kt source."

        gameSource.set(gameDslSourceFile)
        gameSources.from(gameDslSourceFile, gameDslScenarioFile)
        sourceLabel.set(gameDslSourceLabel)
        outputFile.set(generatedGameCatalogFile)
    }

tasks.jar {
    archiveBaseName = "rylween-game"
}

tasks.named<KotlinCompile>("compileKotlin") {
    dependsOn(generateGameCatalogHandles)
}
