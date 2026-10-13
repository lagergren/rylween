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
val gameDslSourceDir: Directory = layout.projectDirectory.dir("src/main/kotlin/rylween/game")
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
        description = "Generates current-game catalog handles from the authored game sources."

        gameSource.set(gameDslSourceFile)
        // Every game source file, so sections pulled in with include(...) contribute handles too.
        gameSources.from(fileTree(gameDslSourceDir) { include("**/*.kt") })
        sourceLabel.set(gameDslSourceLabel)
        outputFile.set(generatedGameCatalogFile)
    }

tasks.jar {
    archiveBaseName = "rylween-game"
}

tasks.named<KotlinCompile>("compileKotlin") {
    dependsOn(generateGameCatalogHandles)
}
