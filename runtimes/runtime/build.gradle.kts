// Builds the direct terminal runtime plus deterministic runtime verification tasks.
import org.gradle.jvm.application.tasks.CreateStartScripts
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import rylween.gradle.RunInstalledLauncherWithJavaTask

plugins {
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

dependencies {
    implementation(project(":core"))
    implementation(project(":terminal"))
    runtimeOnly(project(":game"))
}

val javaToolchains: JavaToolchainService = extensions.getByType()
val kotlinRuntimeJavaLauncher: Provider<JavaLauncher> =
    javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(rylweenJvmToolchainVersion))
    }
val externalBundleArtifact: Provider<RegularFile> =
    rootProject.layout.buildDirectory.file("compiled/rylween-bundle.json")
val runtimeExecutable: Provider<RegularFile> =
    layout.buildDirectory.file("install/rylween/bin/rylween")
val runtimeSaveFile: Provider<RegularFile> =
    layout.buildDirectory.file("tmp/checkRuntimeSaveLoad/runtime.rsave")
val runtimeFullPlaythroughSaveFile: Provider<RegularFile> =
    layout.buildDirectory.file("tmp/checkRuntimeFullPlaythrough/runtime-win.rsave")
val runtimeNewPartySaveFile: Provider<RegularFile> =
    layout.buildDirectory.file("tmp/checkRuntimeNewPartyCreation/runtime-new-party.rsave")
val runtimeJar: TaskProvider<Jar> = tasks.named<Jar>("jar")
val runtimeJarFile: Provider<RegularFile> = runtimeJar.flatMap { it.archiveFile }
val runtimeClasspathConfig: Configuration = configurations.runtimeClasspath.get()
val runtimeJars: FileCollection =
    runtimeClasspathConfig.filter { file -> file.isFile && file.extension == "jar" }

tasks.processResources {
    // Stamps rylween.version into the runtime, which shows it on the title menu and in every log.
    val rylweenVersion = project.version.toString()
    inputs.property("rylweenVersion", rylweenVersion)
    filesMatching("rylween/runtime/version.txt") {
        expand("version" to rylweenVersion)
    }
}

tasks.jar {
    archiveBaseName.set("rylween-runtime")
    manifest {
        attributes["Main-Class"] = "rylween.runtime.RylweenRuntimeMain"
    }
}

fun runtimeBattleMacro(attacks: Int = 5): String =
    buildString {
        "ABCDEFGH".take(attacks).chunked(4).forEach { slots ->
            append('F')
            slots.forEach { slot -> append('A').append(slot) }
        }
        append('\n')
    }

fun runtimeCastleStep(command: Char): String = command.toString() + runtimeBattleMacro(attacks = 2)

fun runtimeCastleFloor(itemDigit: Char): String =
    buildString {
        repeat(4) { append(runtimeCastleStep('6')) }
        repeat(3) { append(runtimeCastleStep('2')) }
        append("2G")
        append(itemDigit)
    }

// Camping without a guard lets the ambush kill a sleeping member, so post member 1 and fight off the ambush.
fun runtimeGuardedCamp(): String = "CY1" + runtimeBattleMacro(attacks = 2)

fun runtimeWinningRoute(): String =
    buildString {
        // The near-winning party has 14 rested hours and each move costs one, so it camps when it gets tired.
        repeat(14) { append('2') }
        append(runtimeGuardedCamp())
        repeat(7) { append('2') }
        append("66E")
        append("AAAA")
        append(runtimeBattleMacro())
        append(runtimeCastleFloor('1'))
        append(runtimeCastleFloor('2'))
        append(runtimeCastleFloor('3'))
        append(runtimeCastleFloor('4'))
        append(runtimeBattleMacro(attacks = 3))
        repeat(4) { append(runtimeBattleMacro(attacks = 1)) }
        append('S')
    }

val runtimeStartScripts: TaskProvider<CreateStartScripts> =
    tasks.register<CreateStartScripts>("runtimeStartScripts") {
        group = "application"
        description = "Generates direct start scripts for the runtime."

        dependsOn(runtimeJar)
        applicationName = "rylween"
        mainClass.set("rylween.runtime.RylweenRuntimeMain")
        outputDir =
            layout.buildDirectory
                .dir("scripts/runtimeApp")
                .get()
                .asFile
        classpath = files(runtimeJarFile) + runtimeJars
    }

val installRuntime: TaskProvider<Sync> =
    tasks.register<Sync>("installRuntime") {
        group = "application"
        description = "Installs the runtime."

        dependsOn(runtimeStartScripts)
        into(layout.buildDirectory.dir("install/rylween"))
        from(runtimeStartScripts) {
            into("bin")
            filePermissions {
                unix("rwxr-xr-x")
            }
        }
        from(files(runtimeJarFile) + runtimeJars) {
            into("lib")
        }
    }

tasks.withType<RunInstalledLauncherWithJavaTask>().configureEach {
    dependsOn(":tools:emitExternalCompiledBundle")
    executableFile.set(runtimeExecutable)
    launcherFiles.from(installRuntime)
    bundleFile.set(externalBundleArtifact)
    javaLauncher.set(kotlinRuntimeJavaLauncher)
    userId.set("gradle-smoke")
}

tasks.test {
    dependsOn(installRuntime)
    dependsOn(":tools:emitExternalCompiledBundle")
    systemProperty("rylween.runtime.executable", runtimeExecutable.get().asFile.path)
    systemProperty("rylween.runtime.bundle", externalBundleArtifact.get().asFile.path)
    // Full-game autopilot seeds for balance work: ./gradlew :runtime:test -Prylween.autopilot.seeds=1,2,3
    providers.gradleProperty("rylween.autopilot.seeds").orNull?.let { seeds -> systemProperty("rylween.autopilot.seeds", seeds) }
    systemProperty(
        "rylween.runtime.testTmp",
        layout.buildDirectory
            .dir("tmp/runtimeReplayTests")
            .get()
            .asFile
            .path,
    )
}

val checkRuntimeBundleBoot: TaskProvider<RunInstalledLauncherWithJavaTask> =
    tasks.register<RunInstalledLauncherWithJavaTask>("checkRuntimeBundleBoot") {
        group = "verification"
        description = "Boots the runtime without old session composition."

        screen.set("title")
    }

val checkRuntimeNewPartyCreation: TaskProvider<RunInstalledLauncherWithJavaTask> =
    tasks.register<RunInstalledLauncherWithJavaTask>("checkRuntimeNewPartyCreation") {
        group = "verification"
        description = "Runs title-menu party creation through reroll, gender, race, and class selection."

        script.set(
            listOf(
                "x",
                "N",
                "1",
                "\"Debuggers\"",
                "Y",
                "Y",
                "N",
                "\"Ada\"",
                "1",
                "1",
                "2",
                "N",
                "\"Bryn\"",
                "2",
                "2",
                "6",
                "N",
                "\"Cato\"",
                "1",
                "3",
                "3",
                "N",
                "\"Cato\"",
                "1",
                "3",
                "2",
                "N",
                "\"Dina\"",
                "2",
                "4",
                "6",
                "Z",
                "S",
                "V",
                "6",
                "6",
                "6",
                "6",
                "\u001B",
                "X",
            ).joinToString(" "),
        )
        title.set(true)
        saveFile.set(runtimeNewPartySaveFile)
        resetSaveFile.set(true)
        expectedOutputContains.set(
            listOf(
                "Party Creation",
                "Stats for char: 1",
                "Give Character 4 a name:",
                "Wisdom must be at least",
                "50.",
                "1. Human",
                "6. Ranger",
                "Game saved.",
                "Party: Debuggers",
                "1. Ada",
                "Human Thief",
                "4. Dina",
                "Halfling Ranger",
            ),
        )
    }

val checkRuntimeSaveScript: TaskProvider<RunInstalledLauncherWithJavaTask> =
    tasks.register<RunInstalledLauncherWithJavaTask>("checkRuntimeSaveScript") {
        group = "verification"
        description = "Saves wilderness state from the runtime without old save/session code."

        script.set("22SX")
        saveFile.set(runtimeSaveFile)
    }

val checkRuntimeLoadScript: TaskProvider<RunInstalledLauncherWithJavaTask> =
    tasks.register<RunInstalledLauncherWithJavaTask>("checkRuntimeLoadScript") {
        group = "verification"
        description = "Loads a runtime save and boots the saved wilderness state."

        dependsOn(checkRuntimeSaveScript)
        script.set("X")
        loadFile.set(runtimeSaveFile)
    }

val checkRuntimePartyJournalScript: TaskProvider<RunInstalledLauncherWithJavaTask> =
    tasks.register<RunInstalledLauncherWithJavaTask>("checkRuntimePartyJournalScript") {
        group = "verification"
        description = "Views direct party and journal pages through the runtime."

        stateSlug.set("strong-replay")
        script.set("V666666\u001B?1J\u001BX")
        expectedOutputContains.set(
            listOf(
                "Party: Strong",
                "1. Aragorn",
                "4. Mira",
                "(Space) Pass",
                "(Z) Cast Spell",
                "Journal",
            ),
        )
    }

val checkRuntimeTownPromptScript: TaskProvider<RunInstalledLauncherWithJavaTask> =
    tasks.register<RunInstalledLauncherWithJavaTask>("checkRuntimeTownPromptScript") {
        group = "verification"
        description = "Checks shared town member-choice and shop confirmation prompts."

        stateSlug.set("strong-replay")
        script.set("22EMSANXLCH2LL")
        expectedOutputContains.set(
            listOf(
                "(Y)es / (N)o",
                "No thanks.",
                "Who? (1-4)",
                "2. Legolas",
                "Legolas needs no healing.",
            ),
        )
    }

val checkRuntimeWildernessActionsScript: TaskProvider<RunInstalledLauncherWithJavaTask> =
    tasks.register<RunInstalledLauncherWithJavaTask>("checkRuntimeWildernessActionsScript") {
        group = "verification"
        description = "Runs direct hunt, camp, and wilderness upkeep paths in the runtime."

        script.set("H1C2X")
    }

val checkRuntimeWildernessEquipmentScript: TaskProvider<RunInstalledLauncherWithJavaTask> =
    tasks.register<RunInstalledLauncherWithJavaTask>("checkRuntimeWildernessEquipmentScript") {
        group = "verification"
        description = "Runs wilderness ready-weapon and ready-armor utility pickers in the runtime."

        stateSlug.set("strong-replay")
        script.set("W1HR1IX")
        expectedOutputContains.set(
            listOf(
                "Who?",
                "Ready what?",
                "H. Magic Sword",
                "Equipped Magic Sword.",
                "I. Mithril Mail",
                "Equipped Mithril Mail.",
            ),
        )
    }

val checkRuntimeWildernessDebugSecretsScript: TaskProvider<RunInstalledLauncherWithJavaTask> =
    tasks.register<RunInstalledLauncherWithJavaTask>("checkRuntimeWildernessDebugSecretsScript") {
        group = "verification"
        description = "Runs hidden manual-test wilderness cave and encounter triggers in the runtime."

        stateSlug.set("strong-replay")
        script.set("[L]" + runtimeBattleMacro() + "X")
        seed.set(31)
        debugLog.set(true)
        debugSecrets.set(true)
        expectedOutputContains.set(
            listOf(
                "debug.secret.force_cave",
                "You find a cave!",
                "debug.secret.force_encounter",
                "rng.draw context=wilderness.encounter_split",
                "ENCOUNTER!",
            ),
        )
    }

val checkRuntimeDungeonScript: TaskProvider<RunInstalledLauncherWithJavaTask> =
    tasks.register<RunInstalledLauncherWithJavaTask>("checkRuntimeDungeonScript") {
        group = "verification"
        description = "Runs direct priest-spell cave discovery into the runtime dungeon loop."

        stateSlug.set("strong-replay")
        script.set("Z47ELAX")
    }

val checkRuntimeCastleScript: TaskProvider<RunInstalledLauncherWithJavaTask> =
    tasks.register<RunInstalledLauncherWithJavaTask>("checkRuntimeCastleScript") {
        group = "verification"
        description = "Runs direct wilderness entry into the generic compiled castle loop."

        stateSlug.set("near-winning-replay")
        script.set("2".repeat(21) + "66EAAAA")
    }

val checkRuntimeHeroesScreen: TaskProvider<RunInstalledLauncherWithJavaTask> =
    tasks.register<RunInstalledLauncherWithJavaTask>("checkRuntimeHeroesScreen") {
        group = "verification"
        description = "Renders the runtime-backed Hall-of-Heroes static screen from direct compiled state."

        stateSlug.set("winning-replay")
        screen.set("heroes")
    }

val checkRuntimeFullPlaythrough: TaskProvider<RunInstalledLauncherWithJavaTask> =
    tasks.register<RunInstalledLauncherWithJavaTask>("checkRuntimeFullPlaythrough") {
        group = "verification"
        description = "Completes the winning castle route in the runtime and saves the result."

        stateSlug.set("near-winning-replay")
        script.set(runtimeWinningRoute())
        saveFile.set(runtimeFullPlaythroughSaveFile)
        expectedOutputContains.set(
            listOf(
                "CONGRATULATIONS!",
                "You have banished Ydarg",
                "See Ya!!!",
                "Game saved.",
            ),
        )
    }

val checkRuntimeFullPlaythroughHeroesScreen: TaskProvider<RunInstalledLauncherWithJavaTask> =
    tasks.register<RunInstalledLauncherWithJavaTask>("checkRuntimeFullPlaythroughHeroesScreen") {
        group = "verification"
        description = "Renders Hall-of-Heroes from the runtime's saved winning route."

        dependsOn(checkRuntimeFullPlaythrough)
        screen.set("heroes")
        loadFile.set(runtimeFullPlaythroughSaveFile)
        expectedOutputContains.set(
            listOf(
                "HALL OF NATIONAL HEROES",
                "Replay defeated Baron Ydarg",
            ),
        )
    }

tasks.check {
    dependsOn(checkRuntimeBundleBoot)
    dependsOn(checkRuntimeNewPartyCreation)
    dependsOn(checkRuntimeTownPromptScript)
    dependsOn(checkRuntimeLoadScript)
    dependsOn(checkRuntimePartyJournalScript)
    dependsOn(checkRuntimeWildernessActionsScript)
    dependsOn(checkRuntimeWildernessEquipmentScript)
    dependsOn(checkRuntimeWildernessDebugSecretsScript)
    dependsOn(checkRuntimeDungeonScript)
    dependsOn(checkRuntimeCastleScript)
    dependsOn(checkRuntimeHeroesScreen)
    dependsOn(checkRuntimeFullPlaythroughHeroesScreen)
}
