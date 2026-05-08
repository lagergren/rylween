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

data class RoutePoint(
    val x: Int,
    val y: Int,
)

val compiledRuntimeWildernessRows: List<String> by lazy {
    val source =
        rootProject.layout.projectDirectory
            .file("src/game/dsl/RylweenGame.dsl.kts")
            .asFile
            .readText()
    val rowsBlock =
        Regex("""layer\(MapLayers\.terrain\)\s*\{\s*rows\((.*?)\)\s*\}""", RegexOption.DOT_MATCHES_ALL)
            .find(source)
            ?.groupValues
            ?.get(1)
            ?: error("could not find authored wilderness terrain rows")
    Regex(""""([^"]+)"""")
        .findAll(rowsBlock)
        .map { match -> match.groupValues[1] }
        .toList()
}

fun RoutePoint.neighbors(): List<Pair<Char, RoutePoint>> =
    listOf(
        '8' to RoutePoint(x, y - 1),
        '2' to RoutePoint(x, y + 1),
        '4' to RoutePoint(x - 1, y),
        '6' to RoutePoint(x + 1, y),
    )

fun RoutePoint.passable(): Boolean =
    compiledRuntimeWildernessRows
        .getOrNull(y)
        ?.getOrNull(x)
        ?.let { tile -> tile != 'm' && tile != 'w' }
        ?: false

fun RoutePoint.terrainCode(): Char? = compiledRuntimeWildernessRows.getOrNull(y)?.getOrNull(x)

fun routeFrom(
    start: RoutePoint,
    target: (RoutePoint) -> Boolean,
): Pair<String, RoutePoint> {
    val queue = ArrayDeque<RoutePoint>()
    val previous = linkedMapOf<RoutePoint, Pair<RoutePoint, Char>?>()
    queue.add(start)
    previous[start] = null
    while (queue.isNotEmpty()) {
        val point = queue.removeFirst()
        if (target(point)) {
            val commands = ArrayDeque<Char>()
            var cursor = point
            while (true) {
                val step = previous[cursor] ?: break
                commands.addFirst(step.second)
                cursor = step.first
            }
            return commands.joinToString(separator = "") to point
        }
        point.neighbors().forEach { (command, next) ->
            if (next.passable() && next !in previous) {
                previous[next] = point to command
                queue.add(next)
            }
        }
    }
    error("no compiled-runtime route from $start")
}

fun routeTo(
    start: RoutePoint,
    target: RoutePoint,
): Pair<String, RoutePoint> = routeFrom(start) { point -> point == target }

fun routeToTerrain(
    start: RoutePoint,
    terrainCode: Char,
): Pair<String, RoutePoint> = routeFrom(start) { point -> point != start && point.terrainCode() == terrainCode }

fun compiledRuntimeDungeonRoute(
    levels: Int,
    descendEvents: Map<Int, String>,
    ascendRecurringEvents: Map<Int, String> = emptyMap(),
): String =
    buildString {
        (2..levels).forEach { level ->
            append('D')
            append(descendEvents[level].orEmpty())
        }
        (levels - 1 downTo 1).forEach { level ->
            append('A')
            append(ascendRecurringEvents[level].orEmpty())
        }
        append('A')
    }

fun compiledRuntimeAcceptanceBattle(attacks: Int): String = "F" + "A".repeat(attacks)

fun compiledRuntimeAcceptanceWait(count: Int = 1): String = "A".repeat(count)

fun compiledRuntimeOgreCaveRoute(): String =
    "E" +
        compiledRuntimeAcceptanceWait() +
        compiledRuntimeAcceptanceBattle(attacks = 4) +
        compiledRuntimeAcceptanceWait() +
        compiledRuntimeAcceptanceBattle(attacks = 1) +
        compiledRuntimeAcceptanceWait(4)

fun compiledRuntimeJazzyRoute(): String =
    compiledRuntimeAcceptanceWait(2) +
        compiledRuntimeAcceptanceBattle(attacks = 7) +
        compiledRuntimeAcceptanceWait(2) +
        compiledRuntimeAcceptanceBattle(attacks = 1) +
        compiledRuntimeAcceptanceWait()

fun compiledRuntimeFireShieldDungeonRoute(): String =
    "E" +
        compiledRuntimeDungeonRoute(
            levels = 20,
            descendEvents =
                mapOf(
                    19 to compiledRuntimeAcceptanceWait() + compiledRuntimeAcceptanceBattle(attacks = 1),
                    20 to compiledRuntimeAcceptanceWait(),
                ),
        )

fun compiledRuntimeThunTempleRoute(): String =
    compiledRuntimeAcceptanceWait(2) +
        "Y" +
        compiledRuntimeAcceptanceWait() +
        compiledRuntimeDungeonRoute(
            levels = 18,
            descendEvents =
                mapOf(
                    4 to compiledRuntimeAcceptanceWait() + "water " + compiledRuntimeAcceptanceWait(),
                    8 to compiledRuntimeAcceptanceWait() + "night " + compiledRuntimeAcceptanceWait(),
                    12 to compiledRuntimeAcceptanceWait() + "\"a well\" " + compiledRuntimeAcceptanceWait(),
                    18 to compiledRuntimeAcceptanceWait(),
                ),
        )

fun compiledRuntimeMasterCrownRoute(): String =
    "E" +
        compiledRuntimeDungeonRoute(
            levels = 10,
            descendEvents = mapOf(10 to compiledRuntimeAcceptanceWait(2) + compiledRuntimeAcceptanceBattle(attacks = 1)),
        )

fun compiledRuntimeHobgoblinPatrolRoute(): String =
    compiledRuntimeAcceptanceWait() + compiledRuntimeAcceptanceBattle(attacks = 2) + compiledRuntimeAcceptanceWait()

fun compiledRuntimeHobgoblinChiefRoute(): String =
    compiledRuntimeAcceptanceWait() +
        "\"RISH EKKIN\" " +
        compiledRuntimeAcceptanceWait() +
        compiledRuntimeAcceptanceBattle(attacks = 1) +
        compiledRuntimeAcceptanceWait()

fun compiledRuntimeHobgoblinStrongholdRoute(): String =
    "E" +
        compiledRuntimeDungeonRoute(
            levels = 22,
            descendEvents =
                mapOf(
                    3 to compiledRuntimeHobgoblinPatrolRoute(),
                    6 to compiledRuntimeHobgoblinPatrolRoute(),
                    9 to compiledRuntimeHobgoblinPatrolRoute(),
                    14 to compiledRuntimeHobgoblinPatrolRoute(),
                    17 to compiledRuntimeHobgoblinPatrolRoute(),
                    19 to compiledRuntimeHobgoblinPatrolRoute(),
                    20 to compiledRuntimeAcceptanceWait() + compiledRuntimeAcceptanceBattle(attacks = 5),
                    21 to compiledRuntimeAcceptanceWait() + compiledRuntimeAcceptanceBattle(attacks = 5),
                    22 to compiledRuntimeHobgoblinChiefRoute(),
                ),
            ascendRecurringEvents =
                mapOf(
                    19 to compiledRuntimeHobgoblinPatrolRoute(),
                    17 to compiledRuntimeHobgoblinPatrolRoute(),
                    14 to compiledRuntimeHobgoblinPatrolRoute(),
                    9 to compiledRuntimeHobgoblinPatrolRoute(),
                    6 to compiledRuntimeHobgoblinPatrolRoute(),
                    3 to compiledRuntimeHobgoblinPatrolRoute(),
                ),
        )

fun compiledRuntimeAcceptanceRoute(): String =
    buildString {
        var point = RoutePoint(12, 9)

        fun moveTo(target: RoutePoint) {
            val route = routeTo(point, target)
            append(route.first)
            point = route.second
        }

        fun moveToTerrain(terrainCode: Char) {
            val route = routeToTerrain(point, terrainCode)
            append(route.first)
            point = route.second
        }

        moveTo(RoutePoint(11, 23))
        append(compiledRuntimeOgreCaveRoute())

        moveToTerrain('f')
        append(compiledRuntimeJazzyRoute())

        moveTo(RoutePoint(45, 13))
        append(compiledRuntimeFireShieldDungeonRoute())
        moveTo(RoutePoint(46, 13))
        append(compiledRuntimeHobgoblinStrongholdRoute())

        moveTo(RoutePoint(41, 30))
        append(compiledRuntimeThunTempleRoute())
        append("Z16AA49")
        point = RoutePoint(point.x - 18, point.y)

        moveTo(RoutePoint(23, 32))
        append(compiledRuntimeMasterCrownRoute())

        moveTo(RoutePoint(14, 30))
        append('E')
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
    script.set("22E1BBA12PQBCPQS1YQLE4BBWN7PQLX")
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
    script.set("VJX")
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
    stateSlug.set("acceptance-replay")
    script.set(compiledRuntimeAcceptanceRoute())
    saveFile.set(compiledRuntimeAcceptanceSaveFile)
    expectedOutputContains.set(
        listOf(
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
    dependsOn(checkCompiledRuntimeTownScript)
    dependsOn(checkCompiledRuntimeLoadScript)
    dependsOn(checkCompiledRuntimePartyJournalScript)
    dependsOn(checkCompiledRuntimeWildernessActionsScript)
    dependsOn(checkCompiledRuntimeDungeonScript)
    dependsOn(checkCompiledRuntimeCastleScript)
    dependsOn(checkCompiledRuntimeHeroesScreen)
    dependsOn(checkCompiledRuntimeFullPlaythroughHeroesScreen)
    dependsOn(checkCompiledRuntimeAcceptanceHeroesScreen)
}
