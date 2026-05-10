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
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
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
val runtimeAcceptanceSaveFile: Provider<RegularFile> =
    layout.buildDirectory.file("tmp/checkRuntimeAcceptancePlaythrough/runtime-acceptance.rsave")
val runtimeNewPartySaveFile: Provider<RegularFile> =
    layout.buildDirectory.file("tmp/checkRuntimeNewPartyCreation/runtime-new-party.rsave")
val runtimeJar: TaskProvider<Jar> = tasks.named<Jar>("jar")
val runtimeJarFile: Provider<RegularFile> = runtimeJar.flatMap { it.archiveFile }
val runtimeClasspathConfig: Configuration = configurations.runtimeClasspath.get()
val runtimeJars: FileCollection =
    runtimeClasspathConfig.filter { file -> file.isFile && file.extension == "jar" }

tasks.jar {
    archiveBaseName.set("rylween-runtime")
    manifest {
        attributes["Main-Class"] = "rylween.runtime.RylweenRuntimeMain"
    }
}

fun runtimeBattleMacro(): String = "F" + "A".repeat(5)

fun runtimeCastleStep(command: Char): String = command.toString() + runtimeBattleMacro()

fun runtimeCastleFloor(itemDigit: Char): String =
    buildString {
        repeat(4) { append(runtimeCastleStep('6')) }
        repeat(3) { append(runtimeCastleStep('2')) }
        append("2G")
        append(itemDigit)
    }

fun runtimeWinningRoute(): String =
    buildString {
        repeat(21) { append('2') }
        append("66E")
        append(runtimeBattleMacro())
        append(runtimeCastleFloor('1'))
        append(runtimeCastleFloor('2'))
        append(runtimeCastleFloor('3'))
        append(runtimeCastleFloor('4'))
        repeat(5) { append(runtimeBattleMacro()) }
        append('S')
    }

val runtimeStartScripts: TaskProvider<CreateStartScripts> by tasks.registering(CreateStartScripts::class) {
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

val installRuntime: TaskProvider<Sync> by tasks.registering(Sync::class) {
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

val checkRuntimeBundleBoot: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Boots the runtime without old session composition."

    dependsOn(installRuntime)
    dependsOn(":tools:emitExternalCompiledBundle")
    executableFile.set(runtimeExecutable)
    bundleFile.set(externalBundleArtifact)
    javaHomePath.set(
        kotlinRuntimeJavaLauncher.map { launcher ->
            launcher.metadata.installationPath.asFile.absolutePath
        },
    )
    inheritedPath.set(providers.environmentVariable("PATH").orElse(""))
    screen.set("title")
}

val checkRuntimeNewPartyCreation: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Runs title-menu party creation through reroll, gender, race, and class selection."

    dependsOn(installRuntime)
    dependsOn(":tools:emitExternalCompiledBundle")
    executableFile.set(runtimeExecutable)
    bundleFile.set(externalBundleArtifact)
    javaHomePath.set(
        kotlinRuntimeJavaLauncher.map { launcher ->
            launcher.metadata.installationPath.asFile.absolutePath
        },
    )
    inheritedPath.set(providers.environmentVariable("PATH").orElse(""))
    script.set(
        listOf(
            "x",
            "N",
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
            "1",
            "2",
            "3",
            "4",
            "5",
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
            "Wisdom must be",
            "at least 50.",
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

val checkRuntimeTownScript: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Runs the runtime through direct wilderness/town/service dispatch."

    dependsOn(installRuntime)
    dependsOn(":tools:emitExternalCompiledBundle")
    executableFile.set(runtimeExecutable)
    bundleFile.set(externalBundleArtifact)
    javaHomePath.set(
        kotlinRuntimeJavaLauncher.map { launcher ->
            launcher.metadata.installationPath.asFile.absolutePath
        },
    )
    inheritedPath.set(providers.environmentVariable("PATH").orElse(""))
    replay.set("town-social-smoke")
    expectedOutputContains.set(
        listOf(
            "runtime replay: town-social-smoke",
            "runtime replay component 1: travel.to_thord",
            "runtime replay component 2: town.thord_service_social_circuit",
            "Entering Thord",
            "The smith welcomes you",
            "The smith offers you some",
            "Do you want supper,",
            "The innkeeper leaves.",
            "Welcome! Have a seat",
        ),
    )
}

val checkRuntimeTownDialogueReplay: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Runs a named runtime replay through Thord tavern NPC dialogue and reward scripting."

    dependsOn(installRuntime)
    dependsOn(":tools:emitExternalCompiledBundle")
    executableFile.set(runtimeExecutable)
    bundleFile.set(externalBundleArtifact)
    javaHomePath.set(
        kotlinRuntimeJavaLauncher.map { launcher ->
            launcher.metadata.installationPath.asFile.absolutePath
        },
    )
    inheritedPath.set(providers.environmentVariable("PATH").orElse(""))
    replay.set("town-garth-reward-smoke")
    seed.set(2L)
    expectedOutputContains.set(
        listOf(
            "runtime replay: town-garth-reward-smoke",
            "runtime replay component 1: travel.to_thord",
            "runtime replay component 2: town.thord_garth_reward_dialogue",
            "The merchant is GRATEFUL!",
            "You pick up the gem.",
        ),
    )
}

val checkRuntimeTownMissionIntroReplay: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Runs fixed NPC town mission-intro dialogue branches through the runtime."

    dependsOn(installRuntime)
    dependsOn(":tools:emitExternalCompiledBundle")
    executableFile.set(runtimeExecutable)
    bundleFile.set(externalBundleArtifact)
    javaHomePath.set(
        kotlinRuntimeJavaLauncher.map { launcher ->
            launcher.metadata.installationPath.asFile.absolutePath
        },
    )
    inheritedPath.set(providers.environmentVariable("PATH").orElse(""))
    replay.set("town-mission-intro-route")
    expectedOutputContains.set(
        listOf(
            "runtime replay: town-mission-intro-route",
            "runtime replay component 2: town.thord_mission_dialogues",
            "runtime replay component 4: town.wilor_mission_dialogues",
            "In fact, I have a mission",
            "You persuade him to",
            "You take the blade.",
            "- OH! A message from my",
            "precious map",
            "Ah, a seeker",
            "Erethor's Lute",
        ),
    )
}

val checkRuntimeTownSilverKeyReplay: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Runs the Silver Key post-note town dialogue chain through the runtime."

    dependsOn(installRuntime)
    dependsOn(":tools:emitExternalCompiledBundle")
    executableFile.set(runtimeExecutable)
    bundleFile.set(externalBundleArtifact)
    javaHomePath.set(
        kotlinRuntimeJavaLauncher.map { launcher ->
            launcher.metadata.installationPath.asFile.absolutePath
        },
    )
    inheritedPath.set(providers.environmentVariable("PATH").orElse(""))
    replay.set("town-silver-key-chain-route")
    expectedOutputContains.set(
        listOf(
            "runtime replay: town-silver-key-chain-route",
            "runtime replay component 2: town.ollk_silver_key_chain",
            "sold the Key",
            "Nah, sorry",
            "Find them and",
        ),
    )
}

val checkRuntimeSilverKeyDiscoveryReplay: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Runs the Silver Key map, dungeon note, and smith confession chain through the runtime."

    dependsOn(installRuntime)
    dependsOn(":tools:emitExternalCompiledBundle")
    executableFile.set(runtimeExecutable)
    bundleFile.set(externalBundleArtifact)
    javaHomePath.set(
        kotlinRuntimeJavaLauncher.map { launcher ->
            launcher.metadata.installationPath.asFile.absolutePath
        },
    )
    inheritedPath.set(providers.environmentVariable("PATH").orElse(""))
    replay.set("silver-key-discovery-route")
    expectedOutputContains.set(
        listOf(
            "runtime replay: silver-key-discovery-route",
            "runtime replay component 2: town.wilor_buy_silver_key_map",
            "runtime replay component 4: mission.silver_sanctum_note",
            "runtime replay component 6: town.ollk_smith_confession",
            "precious map",
            "This room shines like",
            "Master Thief",
            "sold the Key",
            "Find them and",
        ),
    )
}

val checkRuntimeHobgoblinCaravanReplay: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Runs the seeded hobgoblin caravan random-step mission through the runtime."

    dependsOn(installRuntime)
    dependsOn(":tools:emitExternalCompiledBundle")
    executableFile.set(runtimeExecutable)
    bundleFile.set(externalBundleArtifact)
    javaHomePath.set(
        kotlinRuntimeJavaLauncher.map { launcher ->
            launcher.metadata.installationPath.asFile.absolutePath
        },
    )
    inheritedPath.set(providers.environmentVariable("PATH").orElse(""))
    replay.set("hobgoblin-caravan-route")
    seed.set(21L)
    expectedOutputContains.set(
        listOf(
            "runtime replay: hobgoblin-caravan-route",
            "runtime replay component 1: mission.hobgoblin_caravan_random_step",
            "caravan of hobgoblins",
            "Boss has it",
            "carotid artery",
        ),
    )
}

val checkRuntimeMasterCrownDiscoveryReplay: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Runs the Master Crown prerequisite chain through town dialogue, missions, and Nolrim's crypt."

    dependsOn(installRuntime)
    dependsOn(":tools:emitExternalCompiledBundle")
    executableFile.set(runtimeExecutable)
    bundleFile.set(externalBundleArtifact)
    javaHomePath.set(
        kotlinRuntimeJavaLauncher.map { launcher ->
            launcher.metadata.installationPath.asFile.absolutePath
        },
    )
    inheritedPath.set(providers.environmentVariable("PATH").orElse(""))
    replay.set("master-crown-discovery-route")
    expectedOutputContains.set(
        listOf(
            "runtime replay: master-crown-discovery-route",
            "runtime replay component 2: town.wilor_master_crown_intro",
            "runtime replay component 4: mission.kobold_village",
            "runtime replay component 14: mission.nolrim_crypt",
            "runtime replay component 18: town.wilor_master_crown_location",
            "suited for this than I am",
            "ugly pendant",
            "grant you the second",
            "skullweed plant",
            "DWARF STONE",
            "Nolrim's crypt",
            "dagger you seek",
            "purple dwarf stone",
            "Master Crown is hidden",
        ),
    )
}

val checkRuntimeSaveScript: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Saves wilderness state from the runtime without old save/session code."

    dependsOn(installRuntime)
    dependsOn(":tools:emitExternalCompiledBundle")
    executableFile.set(runtimeExecutable)
    bundleFile.set(externalBundleArtifact)
    javaHomePath.set(
        kotlinRuntimeJavaLauncher.map { launcher ->
            launcher.metadata.installationPath.asFile.absolutePath
        },
    )
    inheritedPath.set(providers.environmentVariable("PATH").orElse(""))
    script.set("22SX")
    saveFile.set(runtimeSaveFile)
}

val checkRuntimeLoadScript: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Loads a runtime save and boots the saved wilderness state."

    dependsOn(checkRuntimeSaveScript)
    executableFile.set(runtimeExecutable)
    bundleFile.set(externalBundleArtifact)
    javaHomePath.set(
        kotlinRuntimeJavaLauncher.map { launcher ->
            launcher.metadata.installationPath.asFile.absolutePath
        },
    )
    inheritedPath.set(providers.environmentVariable("PATH").orElse(""))
    script.set("X")
    loadFile.set(runtimeSaveFile)
}

val checkRuntimePartyJournalScript: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Views direct party and journal pages through the runtime."

    dependsOn(installRuntime)
    dependsOn(":tools:emitExternalCompiledBundle")
    executableFile.set(runtimeExecutable)
    bundleFile.set(externalBundleArtifact)
    javaHomePath.set(
        kotlinRuntimeJavaLauncher.map { launcher ->
            launcher.metadata.installationPath.asFile.absolutePath
        },
    )
    inheritedPath.set(providers.environmentVariable("PATH").orElse(""))
    stateSlug.set("strong-replay")
    script.set("V1234567?123J1X")
    expectedOutputContains.set(
        listOf(
            "Party: Playtest",
            "1. Aragorn",
            "4. Mira",
            "Space = Pass",
            "Z = Cast Spell",
            "Journal",
        ),
    )
}

val checkRuntimeTownPromptScript: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Checks shared town member-choice and shop confirmation prompts."

    dependsOn(installRuntime)
    dependsOn(":tools:emitExternalCompiledBundle")
    executableFile.set(runtimeExecutable)
    bundleFile.set(externalBundleArtifact)
    javaHomePath.set(
        kotlinRuntimeJavaLauncher.map { launcher ->
            launcher.metadata.installationPath.asFile.absolutePath
        },
    )
    inheritedPath.set(providers.environmentVariable("PATH").orElse(""))
    stateSlug.set("strong-replay")
    script.set("22E1SANXL5H2LL")
    expectedOutputContains.set(
        listOf(
            "Accept? (Y)es / (N)o",
            "No thanks.",
            "Who? (1-4)",
            "2. Legolas",
            "Legolas needs no healing.",
        ),
    )
}

val checkRuntimeTownCommerceReplay: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Runs Thord merchant and smith buy/sell paths, including deterministic smith haggling."

    dependsOn(installRuntime)
    dependsOn(":tools:emitExternalCompiledBundle")
    executableFile.set(runtimeExecutable)
    bundleFile.set(externalBundleArtifact)
    javaHomePath.set(
        kotlinRuntimeJavaLauncher.map { launcher ->
            launcher.metadata.installationPath.asFile.absolutePath
        },
    )
    inheritedPath.set(providers.environmentVariable("PATH").orElse(""))
    replay.set("town-commerce-paths")
    seed.set(3)
    debugLog.set(true)
    expectedOutputContains.set(
        listOf(
            "runtime replay: town-commerce-paths",
            "runtime replay component 1: town.thord_merchant_buy_sell_matrix",
            "runtime replay component 2: town.thord_smith_buy_sell_haggle",
            "Merchant",
            "You can carry no",
            "Accept? (Y)es / (N)o",
            "No thanks.",
            "Sold!",
            "- I don't deal in that",
            "The smith welcomes you",
            "The smith offers you some",
            "The smith is disappointed.",
            "Armor or Weapon?",
            "2h-Sword",
            "Not weapon or armor.",
            "Ok! Give ya 2900!",
            "Ok! Give ya 450!",
            "rng.draw context=shop.sell.haggle",
        ),
    )
}

val checkRuntimeTownChurchReplay: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Runs Thord church healing, no-need, raise-dead, energize, and invalid target paths."

    dependsOn(installRuntime)
    dependsOn(":tools:emitExternalCompiledBundle")
    executableFile.set(runtimeExecutable)
    bundleFile.set(externalBundleArtifact)
    javaHomePath.set(
        kotlinRuntimeJavaLauncher.map { launcher ->
            launcher.metadata.installationPath.asFile.absolutePath
        },
    )
    inheritedPath.set(providers.environmentVariable("PATH").orElse(""))
    replay.set("town-church-paths")
    expectedOutputContains.set(
        listOf(
            "runtime replay: town-church-paths",
            "You enter the church",
            "Who? (1-4)",
            "1. Aragorn",
            "Mira needs no healing.",
            "The priestess raises",
            "The priestess",
            "Not a spellcaster!",
            "seems very alive",
        ),
    )
}

val checkRuntimeTownSocialReplay: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Runs Thord tavern and inn drinks, rumors, chitchat, bargirl, story, supper, and lodging paths."

    dependsOn(installRuntime)
    dependsOn(":tools:emitExternalCompiledBundle")
    executableFile.set(runtimeExecutable)
    bundleFile.set(externalBundleArtifact)
    javaHomePath.set(
        kotlinRuntimeJavaLauncher.map { launcher ->
            launcher.metadata.installationPath.asFile.absolutePath
        },
    )
    inheritedPath.set(providers.environmentVariable("PATH").orElse(""))
    replay.set("town-social-paths")
    seed.set(20)
    debugLog.set(true)
    expectedOutputContains.set(
        listOf(
            "runtime replay: town-social-paths",
            "runtime replay component 1: town.thord_tavern_drinks_rumor_hassan_story",
            "runtime replay component 2: town.thord_inn_bargirl_drinks_lodging",
            "Welcome! Have a seat",
            "AAH!",
            "laughing",
            "After listening",
            "Chatting with",
            "Hassan",
            "TRAVELER'S STORY",
            "Deanna",
            "Ahh...!",
            "The other guests giggle.",
            "You are served a well",
            "You go to bed",
            "You wake up",
            "rng.draw context=speak.chitchat.subject",
        ),
    )
}

val checkRuntimeTownRandomOfferReplay: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Runs a seeded Thord tavern random-item offer path with debug-log flash enabled."

    dependsOn(installRuntime)
    dependsOn(":tools:emitExternalCompiledBundle")
    executableFile.set(runtimeExecutable)
    bundleFile.set(externalBundleArtifact)
    javaHomePath.set(
        kotlinRuntimeJavaLauncher.map { launcher ->
            launcher.metadata.installationPath.asFile.absolutePath
        },
    )
    inheritedPath.set(providers.environmentVariable("PATH").orElse(""))
    replay.set("town-random-offer-path")
    seed.set(21)
    debugLog.set(true)
    expectedOutputContains.set(
        listOf(
            "runtime replay: town-random-offer-path",
            "scruffy",
            "I will sell you this",
            "Deal!!!",
            "rng.draw context=script.chance",
            "rng.draw context=script.random_item_offer.item",
            "script.random_item_offer.bought",
        ),
    )
}

val checkRuntimeWildernessActionsScript: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Runs direct hunt, camp, and wilderness upkeep paths in the runtime."

    dependsOn(installRuntime)
    dependsOn(":tools:emitExternalCompiledBundle")
    executableFile.set(runtimeExecutable)
    bundleFile.set(externalBundleArtifact)
    javaHomePath.set(
        kotlinRuntimeJavaLauncher.map { launcher ->
            launcher.metadata.installationPath.asFile.absolutePath
        },
    )
    inheritedPath.set(providers.environmentVariable("PATH").orElse(""))
    script.set("H1C2X")
}

val checkRuntimeWildernessEquipmentScript: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Runs wilderness ready-weapon and ready-armor utility pickers in the runtime."

    dependsOn(installRuntime)
    dependsOn(":tools:emitExternalCompiledBundle")
    executableFile.set(runtimeExecutable)
    bundleFile.set(externalBundleArtifact)
    javaHomePath.set(
        kotlinRuntimeJavaLauncher.map { launcher ->
            launcher.metadata.installationPath.asFile.absolutePath
        },
    )
    inheritedPath.set(providers.environmentVariable("PATH").orElse(""))
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

val checkRuntimeWildernessDebugSecretsScript: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Runs hidden manual-test wilderness cave and encounter triggers in the runtime."

    dependsOn(installRuntime)
    dependsOn(":tools:emitExternalCompiledBundle")
    executableFile.set(runtimeExecutable)
    bundleFile.set(externalBundleArtifact)
    javaHomePath.set(
        kotlinRuntimeJavaLauncher.map { launcher ->
            launcher.metadata.installationPath.asFile.absolutePath
        },
    )
    inheritedPath.set(providers.environmentVariable("PATH").orElse(""))
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

val checkRuntimeDungeonScript: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Runs direct priest-spell cave discovery into the runtime dungeon loop."

    dependsOn(installRuntime)
    dependsOn(":tools:emitExternalCompiledBundle")
    executableFile.set(runtimeExecutable)
    bundleFile.set(externalBundleArtifact)
    javaHomePath.set(
        kotlinRuntimeJavaLauncher.map { launcher ->
            launcher.metadata.installationPath.asFile.absolutePath
        },
    )
    inheritedPath.set(providers.environmentVariable("PATH").orElse(""))
    stateSlug.set("strong-replay")
    script.set("Z47ELAX")
}

val checkRuntimeCastleScript: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Runs direct wilderness entry into the generic compiled castle loop."

    dependsOn(installRuntime)
    dependsOn(":tools:emitExternalCompiledBundle")
    executableFile.set(runtimeExecutable)
    bundleFile.set(externalBundleArtifact)
    javaHomePath.set(
        kotlinRuntimeJavaLauncher.map { launcher ->
            launcher.metadata.installationPath.asFile.absolutePath
        },
    )
    inheritedPath.set(providers.environmentVariable("PATH").orElse(""))
    stateSlug.set("near-winning-replay")
    script.set("2".repeat(21) + "66E")
}

val checkRuntimeHeroesScreen: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Renders the runtime-backed Hall-of-Heroes static screen from direct compiled state."

    dependsOn(installRuntime)
    dependsOn(":tools:emitExternalCompiledBundle")
    executableFile.set(runtimeExecutable)
    bundleFile.set(externalBundleArtifact)
    javaHomePath.set(
        kotlinRuntimeJavaLauncher.map { launcher ->
            launcher.metadata.installationPath.asFile.absolutePath
        },
    )
    inheritedPath.set(providers.environmentVariable("PATH").orElse(""))
    stateSlug.set("winning-replay")
    screen.set("heroes")
}

val checkRuntimeFullPlaythrough: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Completes the winning castle route in the runtime and saves the result."

    dependsOn(installRuntime)
    dependsOn(":tools:emitExternalCompiledBundle")
    executableFile.set(runtimeExecutable)
    bundleFile.set(externalBundleArtifact)
    javaHomePath.set(
        kotlinRuntimeJavaLauncher.map { launcher ->
            launcher.metadata.installationPath.asFile.absolutePath
        },
    )
    inheritedPath.set(providers.environmentVariable("PATH").orElse(""))
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

val checkRuntimeFullPlaythroughHeroesScreen: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Renders Hall-of-Heroes from the runtime's saved winning route."

    dependsOn(checkRuntimeFullPlaythrough)
    executableFile.set(runtimeExecutable)
    bundleFile.set(externalBundleArtifact)
    javaHomePath.set(
        kotlinRuntimeJavaLauncher.map { launcher ->
            launcher.metadata.installationPath.asFile.absolutePath
        },
    )
    inheritedPath.set(providers.environmentVariable("PATH").orElse(""))
    screen.set("heroes")
    loadFile.set(runtimeFullPlaythroughSaveFile)
    expectedOutputContains.set(
        listOf(
            "HALL OF NATIONAL HEROES",
            "Replay defeated Baron Ydarg",
        ),
    )
}

val checkRuntimeAcceptancePlaythrough: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Runs a normal-start runtime route through the power-item dungeons and castle victory."

    dependsOn(installRuntime)
    dependsOn(":tools:emitExternalCompiledBundle")
    executableFile.set(runtimeExecutable)
    bundleFile.set(externalBundleArtifact)
    javaHomePath.set(
        kotlinRuntimeJavaLauncher.map { launcher ->
            launcher.metadata.installationPath.asFile.absolutePath
        },
    )
    inheritedPath.set(providers.environmentVariable("PATH").orElse(""))
    replay.set("acceptance-power-route")
    saveFile.set(runtimeAcceptanceSaveFile)
    expectedOutputContains.set(
        listOf(
            "runtime replay: acceptance-power-route",
            "runtime replay component",
            "FIRE SHIELD",
            "Bracelets of Thun",
            "Master Crown",
            "Today, you have killed",
            "precious Silver Key",
            "CONGRATULATIONS!",
            "You have banished Ydarg",
            "Game saved.",
        ),
    )
}

val checkRuntimeAcceptanceHeroesScreen: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Loads the runtime acceptance save into Hall-of-Heroes."

    dependsOn(checkRuntimeAcceptancePlaythrough)
    executableFile.set(runtimeExecutable)
    bundleFile.set(externalBundleArtifact)
    javaHomePath.set(
        kotlinRuntimeJavaLauncher.map { launcher ->
            launcher.metadata.installationPath.asFile.absolutePath
        },
    )
    inheritedPath.set(providers.environmentVariable("PATH").orElse(""))
    screen.set("heroes")
    loadFile.set(runtimeAcceptanceSaveFile)
    expectedOutputContains.set(
        listOf(
            "HALL OF NATIONAL HEROES",
            "Playtest defeated Baron Ydarg",
        ),
    )
}

tasks.check {
    dependsOn(checkRuntimeBundleBoot)
    dependsOn(checkRuntimeNewPartyCreation)
    dependsOn(checkRuntimeTownScript)
    dependsOn(checkRuntimeTownDialogueReplay)
    dependsOn(checkRuntimeTownMissionIntroReplay)
    dependsOn(checkRuntimeTownPromptScript)
    dependsOn(checkRuntimeTownCommerceReplay)
    dependsOn(checkRuntimeTownChurchReplay)
    dependsOn(checkRuntimeTownSocialReplay)
    dependsOn(checkRuntimeTownRandomOfferReplay)
    dependsOn(checkRuntimeTownSilverKeyReplay)
    dependsOn(checkRuntimeSilverKeyDiscoveryReplay)
    dependsOn(checkRuntimeHobgoblinCaravanReplay)
    dependsOn(checkRuntimeMasterCrownDiscoveryReplay)
    dependsOn(checkRuntimeLoadScript)
    dependsOn(checkRuntimePartyJournalScript)
    dependsOn(checkRuntimeWildernessActionsScript)
    dependsOn(checkRuntimeWildernessEquipmentScript)
    dependsOn(checkRuntimeWildernessDebugSecretsScript)
    dependsOn(checkRuntimeDungeonScript)
    dependsOn(checkRuntimeCastleScript)
    dependsOn(checkRuntimeHeroesScreen)
    dependsOn(checkRuntimeFullPlaythroughHeroesScreen)
    dependsOn(checkRuntimeAcceptanceHeroesScreen)
}
