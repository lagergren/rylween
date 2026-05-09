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

kotlin {
    jvmToolchain(25)
}

dependencies {
    implementation(project(":"))
    implementation(project(":ansi-terminal"))
    implementation(libs.jline)
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

sourceSets.named("main") {
    resources.srcDir(rootProject.layout.projectDirectory.dir("data"))
    resources.include("ansi/**", "tiles/**", "adventurers-of-rylween.md", "ALTAIR.displayme")
}

val javaToolchains: JavaToolchainService = extensions.getByType()
val kotlinRuntimeJavaLauncher: Provider<JavaLauncher> =
    javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
val externalBundleArtifact: Provider<RegularFile> =
    rootProject.layout.buildDirectory.file("compiled/rylween-bundle.json")
val compiledRuntimeExecutable: Provider<RegularFile> =
    layout.buildDirectory.file("install/rylween-compiled-runtime/bin/rylween-compiled")
val compiledRuntimeSaveFile: Provider<RegularFile> =
    layout.buildDirectory.file("tmp/checkCompiledRuntimeSaveLoad/compiled-runtime.rsave")
val compiledRuntimeFullPlaythroughSaveFile: Provider<RegularFile> =
    layout.buildDirectory.file("tmp/checkCompiledRuntimeFullPlaythrough/compiled-runtime-win.rsave")
val compiledRuntimeAcceptanceSaveFile: Provider<RegularFile> =
    layout.buildDirectory.file("tmp/checkCompiledRuntimeAcceptancePlaythrough/compiled-runtime-acceptance.rsave")
val compiledRuntimeNewPartySaveFile: Provider<RegularFile> =
    layout.buildDirectory.file("tmp/checkCompiledRuntimeNewPartyCreation/compiled-runtime-new-party.rsave")
val compiledRuntimeJar: TaskProvider<Jar> = tasks.named<Jar>("jar")
val compiledRuntimeJarFile: Provider<RegularFile> = compiledRuntimeJar.flatMap { it.archiveFile }
val rootRuntimeJar: TaskProvider<Jar> = rootProject.tasks.named<Jar>("jar")
val rootRuntimeJarFile: Provider<RegularFile> = rootRuntimeJar.flatMap { it.archiveFile }
val compiledRuntimeRuntimeClasspath: Configuration = configurations.runtimeClasspath.get()
val compiledRuntimeRuntimeJars: FileCollection =
    compiledRuntimeRuntimeClasspath.filter { file -> file.isFile && file.extension == "jar" }

tasks.jar {
    archiveBaseName.set("rylween-compiled-runtime")
    manifest {
        attributes["Main-Class"] = "rylween.compiledruntime.RylweenCompiledRuntimeMain"
    }
}

fun compiledRuntimeBattleMacro(): String = "F" + "A".repeat(5)

fun compiledRuntimeCastleStep(command: Char): String = command.toString() + compiledRuntimeBattleMacro()

fun compiledRuntimeCastleFloor(itemDigit: Char): String =
    buildString {
        repeat(4) { append(compiledRuntimeCastleStep('6')) }
        repeat(3) { append(compiledRuntimeCastleStep('2')) }
        append("2G")
        append(itemDigit)
    }

fun compiledRuntimeWinningRoute(): String =
    buildString {
        repeat(21) { append('2') }
        append("66E")
        append(compiledRuntimeBattleMacro())
        append(compiledRuntimeCastleFloor('1'))
        append(compiledRuntimeCastleFloor('2'))
        append(compiledRuntimeCastleFloor('3'))
        append(compiledRuntimeCastleFloor('4'))
        repeat(5) { append(compiledRuntimeBattleMacro()) }
        append('S')
    }

val compiledRuntimeStartScripts: TaskProvider<CreateStartScripts> by tasks.registering(CreateStartScripts::class) {
    group = "application"
    description = "Generates direct start scripts for the standalone compiled-bundle runtime."

    dependsOn(compiledRuntimeJar)
    dependsOn(rootRuntimeJar)
    applicationName = "rylween-compiled"
    mainClass.set("rylween.compiledruntime.RylweenCompiledRuntimeMain")
    outputDir =
        layout.buildDirectory
            .dir("scripts/compiledRuntimeApp")
            .get()
            .asFile
    classpath = files(compiledRuntimeJarFile, rootRuntimeJarFile) + compiledRuntimeRuntimeJars
}

val installCompiledRuntime: TaskProvider<Sync> by tasks.registering(Sync::class) {
    group = "application"
    description = "Installs the standalone compiled-bundle runtime."

    dependsOn(compiledRuntimeStartScripts)
    into(layout.buildDirectory.dir("install/rylween-compiled-runtime"))
    from(compiledRuntimeStartScripts) {
        into("bin")
        filePermissions {
            unix("rwxr-xr-x")
        }
    }
    from(files(compiledRuntimeJarFile, rootRuntimeJarFile) + compiledRuntimeRuntimeJars) {
        into("lib")
    }
}

val checkCompiledRuntimeBundleBoot: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Boots the standalone compiled-bundle runtime without old session composition."

    dependsOn(installCompiledRuntime)
    dependsOn(":emitExternalCompiledBundle")
    executableFile.set(compiledRuntimeExecutable)
    bundleFile.set(externalBundleArtifact)
    javaHomePath.set(
        kotlinRuntimeJavaLauncher.map { launcher ->
            launcher.metadata.installationPath.asFile.absolutePath
        },
    )
    inheritedPath.set(providers.environmentVariable("PATH").orElse(""))
    screen.set("title")
}

val checkCompiledRuntimeNewPartyCreation: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Runs title-menu party creation through reroll, gender, race, and class selection."

    dependsOn(installCompiledRuntime)
    dependsOn(":emitExternalCompiledBundle")
    executableFile.set(compiledRuntimeExecutable)
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
    saveFile.set(compiledRuntimeNewPartySaveFile)
    resetSaveFile.set(true)
    expectedOutputContains.set(
        listOf(
            "Party Creation",
            "Stats for char: 1",
            "Give Character 4 a name:",
            "Wisdom must be > 50.",
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

val checkCompiledRuntimeTownScript: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Runs the standalone compiled-bundle runtime through direct wilderness/town/service dispatch."

    dependsOn(installCompiledRuntime)
    dependsOn(":emitExternalCompiledBundle")
    executableFile.set(compiledRuntimeExecutable)
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
            "compiled replay: town-social-smoke",
            "compiled replay component 1: travel.to_thord",
            "compiled replay component 2: town.thord_service_social_circuit",
            "Entering Thord",
            "The smith welcomes you",
            "The smith offers you some",
            "Do you want supper,",
            "The innkeeper leaves.",
            "Welcome! Have a seat",
        ),
    )
}

val checkCompiledRuntimeTownDialogueReplay: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Runs a named compiled-runtime replay through Thord tavern NPC dialogue and reward scripting."

    dependsOn(installCompiledRuntime)
    dependsOn(":emitExternalCompiledBundle")
    executableFile.set(compiledRuntimeExecutable)
    bundleFile.set(externalBundleArtifact)
    javaHomePath.set(
        kotlinRuntimeJavaLauncher.map { launcher ->
            launcher.metadata.installationPath.asFile.absolutePath
        },
    )
    inheritedPath.set(providers.environmentVariable("PATH").orElse(""))
    replay.set("town-garth-reward-smoke")
    expectedOutputContains.set(
        listOf(
            "compiled replay: town-garth-reward-smoke",
            "compiled replay component 1: travel.to_thord",
            "compiled replay component 2: town.thord_garth_reward_dialogue",
            "The merchant is GRATEFUL!",
            "You pick up the gem.",
        ),
    )
}

val checkCompiledRuntimeTownMissionIntroReplay: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Runs fixed NPC town mission-intro dialogue branches through the standalone compiled runtime."

    dependsOn(installCompiledRuntime)
    dependsOn(":emitExternalCompiledBundle")
    executableFile.set(compiledRuntimeExecutable)
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
            "compiled replay: town-mission-intro-route",
            "compiled replay component 2: town.thord_mission_dialogues",
            "compiled replay component 4: town.wilor_mission_dialogues",
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

val checkCompiledRuntimeTownSilverKeyReplay: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Runs the Silver Key post-note town dialogue chain through the standalone compiled runtime."

    dependsOn(installCompiledRuntime)
    dependsOn(":emitExternalCompiledBundle")
    executableFile.set(compiledRuntimeExecutable)
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
            "compiled replay: town-silver-key-chain-route",
            "compiled replay component 2: town.ollk_silver_key_chain",
            "sold the Key",
            "Nah, sorry",
            "Find them and",
        ),
    )
}

val checkCompiledRuntimeSilverKeyDiscoveryReplay: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Runs the Silver Key map, dungeon note, and smith confession chain through the standalone compiled runtime."

    dependsOn(installCompiledRuntime)
    dependsOn(":emitExternalCompiledBundle")
    executableFile.set(compiledRuntimeExecutable)
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
            "compiled replay: silver-key-discovery-route",
            "compiled replay component 2: town.wilor_buy_silver_key_map",
            "compiled replay component 4: mission.silver_sanctum_note",
            "compiled replay component 6: town.ollk_smith_confession",
            "precious map",
            "This room shines like",
            "Master Thief",
            "sold the Key",
            "Find them and",
        ),
    )
}

val checkCompiledRuntimeHobgoblinCaravanReplay: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Runs the seeded hobgoblin caravan random-step mission through the standalone compiled runtime."

    dependsOn(installCompiledRuntime)
    dependsOn(":emitExternalCompiledBundle")
    executableFile.set(compiledRuntimeExecutable)
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
            "compiled replay: hobgoblin-caravan-route",
            "compiled replay component 1: mission.hobgoblin_caravan_random_step",
            "caravan of hobgoblins",
            "Boss has it",
            "carotid artery",
        ),
    )
}

val checkCompiledRuntimeMasterCrownDiscoveryReplay: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Runs the Master Crown prerequisite chain through town dialogue, missions, and Nolrim's crypt."

    dependsOn(installCompiledRuntime)
    dependsOn(":emitExternalCompiledBundle")
    executableFile.set(compiledRuntimeExecutable)
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
            "compiled replay: master-crown-discovery-route",
            "compiled replay component 2: town.wilor_master_crown_intro",
            "compiled replay component 4: mission.kobold_village",
            "compiled replay component 14: mission.nolrim_crypt",
            "compiled replay component 18: town.wilor_master_crown_location",
            "You are better fit",
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

val checkCompiledRuntimeSaveScript: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Saves wilderness state from the standalone compiled runtime without old save/session code."

    dependsOn(installCompiledRuntime)
    dependsOn(":emitExternalCompiledBundle")
    executableFile.set(compiledRuntimeExecutable)
    bundleFile.set(externalBundleArtifact)
    javaHomePath.set(
        kotlinRuntimeJavaLauncher.map { launcher ->
            launcher.metadata.installationPath.asFile.absolutePath
        },
    )
    inheritedPath.set(providers.environmentVariable("PATH").orElse(""))
    script.set("22SX")
    saveFile.set(compiledRuntimeSaveFile)
}

val checkCompiledRuntimeLoadScript: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Loads a standalone compiled-runtime save and boots the saved wilderness state."

    dependsOn(checkCompiledRuntimeSaveScript)
    executableFile.set(compiledRuntimeExecutable)
    bundleFile.set(externalBundleArtifact)
    javaHomePath.set(
        kotlinRuntimeJavaLauncher.map { launcher ->
            launcher.metadata.installationPath.asFile.absolutePath
        },
    )
    inheritedPath.set(providers.environmentVariable("PATH").orElse(""))
    script.set("X")
    loadFile.set(compiledRuntimeSaveFile)
}

val checkCompiledRuntimePartyJournalScript: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Views direct party and journal pages through the standalone compiled runtime."

    dependsOn(installCompiledRuntime)
    dependsOn(":emitExternalCompiledBundle")
    executableFile.set(compiledRuntimeExecutable)
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

val checkCompiledRuntimeTownPromptScript: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Checks shared town member-choice and shop confirmation prompts."

    dependsOn(installCompiledRuntime)
    dependsOn(":emitExternalCompiledBundle")
    executableFile.set(compiledRuntimeExecutable)
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

val checkCompiledRuntimeTownCommerceReplay: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Runs Thord merchant and smith buy/sell paths, including deterministic smith haggling."

    dependsOn(installCompiledRuntime)
    dependsOn(":emitExternalCompiledBundle")
    executableFile.set(compiledRuntimeExecutable)
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
            "compiled replay: town-commerce-paths",
            "compiled replay component 1: town.thord_merchant_buy_sell_matrix",
            "compiled replay component 2: town.thord_smith_buy_sell_haggle",
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

val checkCompiledRuntimeTownChurchReplay: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Runs Thord church healing, no-need, raise-dead, energize, and invalid target paths."

    dependsOn(installCompiledRuntime)
    dependsOn(":emitExternalCompiledBundle")
    executableFile.set(compiledRuntimeExecutable)
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
            "compiled replay: town-church-paths",
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

val checkCompiledRuntimeTownSocialReplay: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Runs Thord tavern and inn drinks, rumours, chitchat, bargirl, story, supper, and lodging paths."

    dependsOn(installCompiledRuntime)
    dependsOn(":emitExternalCompiledBundle")
    executableFile.set(compiledRuntimeExecutable)
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
            "compiled replay: town-social-paths",
            "compiled replay component 1: town.thord_tavern_drinks_rumour_hassan_story",
            "compiled replay component 2: town.thord_inn_bargirl_drinks_lodging",
            "Welcome! Have a seat",
            "AAH!",
            "laughing",
            "After listening",
            "Chatting with",
            "Hassan",
            "TRAVELLER'S STORY",
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

val checkCompiledRuntimeTownRandomOfferReplay: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Runs a seeded Thord tavern random-item offer path with debug-log flash enabled."

    dependsOn(installCompiledRuntime)
    dependsOn(":emitExternalCompiledBundle")
    executableFile.set(compiledRuntimeExecutable)
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
            "compiled replay: town-random-offer-path",
            "scruffy",
            "I will sell you this",
            "Deal!!!",
            "rng.draw context=script.chance",
            "rng.draw context=script.random_item_offer.item",
            "script.random_item_offer.bought",
        ),
    )
}

val checkCompiledRuntimeWildernessActionsScript: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Runs direct hunt, camp, and wilderness upkeep paths in the standalone compiled runtime."

    dependsOn(installCompiledRuntime)
    dependsOn(":emitExternalCompiledBundle")
    executableFile.set(compiledRuntimeExecutable)
    bundleFile.set(externalBundleArtifact)
    javaHomePath.set(
        kotlinRuntimeJavaLauncher.map { launcher ->
            launcher.metadata.installationPath.asFile.absolutePath
        },
    )
    inheritedPath.set(providers.environmentVariable("PATH").orElse(""))
    script.set("H1C2X")
}

val checkCompiledRuntimeDungeonScript: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Runs direct priest-spell cave discovery into the standalone dungeon loop."

    dependsOn(installCompiledRuntime)
    dependsOn(":emitExternalCompiledBundle")
    executableFile.set(compiledRuntimeExecutable)
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

val checkCompiledRuntimeCastleScript: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Runs direct wilderness entry into the generic compiled castle loop."

    dependsOn(installCompiledRuntime)
    dependsOn(":emitExternalCompiledBundle")
    executableFile.set(compiledRuntimeExecutable)
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

val checkCompiledRuntimeHeroesScreen: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Renders the runtime-backed Hall-of-Heroes static screen from direct compiled state."

    dependsOn(installCompiledRuntime)
    dependsOn(":emitExternalCompiledBundle")
    executableFile.set(compiledRuntimeExecutable)
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

val checkCompiledRuntimeFullPlaythrough: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Completes the winning castle route in the standalone compiled runtime and saves the result."

    dependsOn(installCompiledRuntime)
    dependsOn(":emitExternalCompiledBundle")
    executableFile.set(compiledRuntimeExecutable)
    bundleFile.set(externalBundleArtifact)
    javaHomePath.set(
        kotlinRuntimeJavaLauncher.map { launcher ->
            launcher.metadata.installationPath.asFile.absolutePath
        },
    )
    inheritedPath.set(providers.environmentVariable("PATH").orElse(""))
    stateSlug.set("near-winning-replay")
    script.set(compiledRuntimeWinningRoute())
    saveFile.set(compiledRuntimeFullPlaythroughSaveFile)
    expectedOutputContains.set(
        listOf(
            "CONGRATULATIONS!",
            "You have banished Ydarg",
            "See Ya!!!",
            "Game saved.",
        ),
    )
}

val checkCompiledRuntimeFullPlaythroughHeroesScreen: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Renders Hall-of-Heroes from the standalone compiled runtime's saved winning route."

    dependsOn(checkCompiledRuntimeFullPlaythrough)
    executableFile.set(compiledRuntimeExecutable)
    bundleFile.set(externalBundleArtifact)
    javaHomePath.set(
        kotlinRuntimeJavaLauncher.map { launcher ->
            launcher.metadata.installationPath.asFile.absolutePath
        },
    )
    inheritedPath.set(providers.environmentVariable("PATH").orElse(""))
    screen.set("heroes")
    loadFile.set(compiledRuntimeFullPlaythroughSaveFile)
    expectedOutputContains.set(
        listOf(
            "HALL OF NATIONAL HEROES",
            "Replay defeated Baron Ydarg",
        ),
    )
}

val checkCompiledRuntimeAcceptancePlaythrough: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Runs a normal-start compiled-runtime route through the power-item dungeons and castle victory."

    dependsOn(installCompiledRuntime)
    dependsOn(":emitExternalCompiledBundle")
    executableFile.set(compiledRuntimeExecutable)
    bundleFile.set(externalBundleArtifact)
    javaHomePath.set(
        kotlinRuntimeJavaLauncher.map { launcher ->
            launcher.metadata.installationPath.asFile.absolutePath
        },
    )
    inheritedPath.set(providers.environmentVariable("PATH").orElse(""))
    replay.set("acceptance-power-route")
    saveFile.set(compiledRuntimeAcceptanceSaveFile)
    expectedOutputContains.set(
        listOf(
            "compiled replay: acceptance-power-route",
            "compiled replay component",
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

val checkCompiledRuntimeAcceptanceHeroesScreen: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Loads the compiled-runtime acceptance save into Hall-of-Heroes."

    dependsOn(checkCompiledRuntimeAcceptancePlaythrough)
    executableFile.set(compiledRuntimeExecutable)
    bundleFile.set(externalBundleArtifact)
    javaHomePath.set(
        kotlinRuntimeJavaLauncher.map { launcher ->
            launcher.metadata.installationPath.asFile.absolutePath
        },
    )
    inheritedPath.set(providers.environmentVariable("PATH").orElse(""))
    screen.set("heroes")
    loadFile.set(compiledRuntimeAcceptanceSaveFile)
    expectedOutputContains.set(
        listOf(
            "HALL OF NATIONAL HEROES",
            "Playtest defeated Baron Ydarg",
        ),
    )
}

tasks.check {
    dependsOn(checkCompiledRuntimeBundleBoot)
    dependsOn(checkCompiledRuntimeNewPartyCreation)
    dependsOn(checkCompiledRuntimeTownScript)
    dependsOn(checkCompiledRuntimeTownDialogueReplay)
    dependsOn(checkCompiledRuntimeTownMissionIntroReplay)
    dependsOn(checkCompiledRuntimeTownPromptScript)
    dependsOn(checkCompiledRuntimeTownCommerceReplay)
    dependsOn(checkCompiledRuntimeTownChurchReplay)
    dependsOn(checkCompiledRuntimeTownSocialReplay)
    dependsOn(checkCompiledRuntimeTownRandomOfferReplay)
    dependsOn(checkCompiledRuntimeTownSilverKeyReplay)
    dependsOn(checkCompiledRuntimeSilverKeyDiscoveryReplay)
    dependsOn(checkCompiledRuntimeHobgoblinCaravanReplay)
    dependsOn(checkCompiledRuntimeMasterCrownDiscoveryReplay)
    dependsOn(checkCompiledRuntimeLoadScript)
    dependsOn(checkCompiledRuntimePartyJournalScript)
    dependsOn(checkCompiledRuntimeWildernessActionsScript)
    dependsOn(checkCompiledRuntimeDungeonScript)
    dependsOn(checkCompiledRuntimeCastleScript)
    dependsOn(checkCompiledRuntimeHeroesScreen)
    dependsOn(checkCompiledRuntimeFullPlaythroughHeroesScreen)
    dependsOn(checkCompiledRuntimeAcceptanceHeroesScreen)
}
