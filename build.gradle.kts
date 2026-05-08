import org.gradle.jvm.application.tasks.CreateStartScripts
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import rylween.gradle.AnsiScriptVmMode
import rylween.gradle.CheckAnsiServerLauncherTask
import rylween.gradle.CheckRustMudBundleInspectTask
import rylween.gradle.CompileNativeCExecutableTask
import rylween.gradle.GenerateAnsiBuildWrapperTask
import rylween.gradle.GenerateCompiledGameHarnessTask
import rylween.gradle.GenerateGameCatalogHandlesTask
import rylween.gradle.RunInstalledLauncherWithJavaTask

plugins {
    alias(libs.plugins.kotlin.jvm)
    id("rylween.qa-conventions")
}

group = "org.rylween"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
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
val gameDslSourceFile: RegularFile = layout.projectDirectory.file("src/game/dsl/RylweenGame.dsl.kts")
val gameDslSourceLabel: String = gameDslSourceFile.asFile.relativeTo(projectDir).invariantSeparatorsPath

kotlin {
    jvmToolchain(25)
}

val javaToolchains: JavaToolchainService = extensions.getByType()
val kotlinRuntimeJavaLauncher: Provider<JavaLauncher> =
    javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    }

val main: SourceSet by sourceSets.getting

val game: SourceSet by sourceSets.creating {
    java.srcDir("src/game/kotlin")
    java.srcDir(generatedGameHarnessDir)
    java.srcDir(generatedGameCatalogDir)
    compileClasspath += main.output
    runtimeClasspath += output + compileClasspath
}

val app: SourceSet by sourceSets.creating {
    java.srcDir("src/app/kotlin")
    resources.srcDir("data")
    resources.include("ansi/**", "tiles/**", "rylween-doc.txt", "ALTAIR.displayme")
    compileClasspath += main.output + game.output
    runtimeClasspath += output + compileClasspath
}

val bundleApp: SourceSet by sourceSets.creating {
    java.srcDir("src/app/kotlin")
    java.include(
        "rylween/app/AppAssets.kt",
        "rylween/app/CliDiagnostics.kt",
        "rylween/app/CommonCliFlags.kt",
        "rylween/app/GameBoot.kt",
        "rylween/app/RylweenAnsiMain.kt",
    )
    resources.srcDir("data")
    resources.include("ansi/**", "tiles/**", "rylween-doc.txt", "ALTAIR.displayme")
    compileClasspath += main.output
    runtimeClasspath += output + compileClasspath
}

val compiledRuntime: SourceSet by sourceSets.creating {
    java.srcDir("src/compiledRuntime/kotlin")
    resources.srcDir("data")
    resources.include("ansi/**", "tiles/**", "rylween-doc.txt", "ALTAIR.displayme")
    compileClasspath += main.output
    runtimeClasspath += output + compileClasspath
}

val gameSourceSet: SourceSet = game
val appSourceSet: SourceSet = app
val bundleAppSourceSet: SourceSet = bundleApp
val compiledRuntimeSourceSet: SourceSet = compiledRuntime

val test: SourceSet by sourceSets.getting
test.apply {
    compileClasspath += game.output
    runtimeClasspath += game.output
}

configurations.named(gameSourceSet.implementationConfigurationName) {
    extendsFrom(configurations.implementation.get())
}

configurations.named(appSourceSet.implementationConfigurationName) {
    extendsFrom(configurations.implementation.get())
}

configurations.named(bundleAppSourceSet.implementationConfigurationName) {
    extendsFrom(configurations.implementation.get())
}

configurations.named(compiledRuntimeSourceSet.implementationConfigurationName) {
    extendsFrom(configurations.implementation.get())
}

dependencies {
    implementation(kotlin("reflect"))
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    add(appSourceSet.implementationConfigurationName, libs.jline)
    add(appSourceSet.implementationConfigurationName, libs.clikt)
    add(bundleAppSourceSet.implementationConfigurationName, libs.jline)
    add(bundleAppSourceSet.implementationConfigurationName, libs.clikt)
    add(compiledRuntimeSourceSet.implementationConfigurationName, libs.jline)
}

tasks.test {
    useJUnitPlatform()
}

val generateCompiledGameHarness: TaskProvider<GenerateCompiledGameHarnessTask> by tasks.registering(
    GenerateCompiledGameHarnessTask::class,
) {
    group = "generation"
    description = "Generates a test harness that compiles the game DSL as Kotlin and exposes nextRylween."

    gameSource.set(gameDslSourceFile)
    sourceLabel.set(gameDslSourceLabel)
    outputFile.set(generatedGameHarnessFile)
}

val generateGameCatalogHandles: TaskProvider<GenerateGameCatalogHandlesTask> by tasks.registering(GenerateGameCatalogHandlesTask::class) {
    group = "generation"
    description = "Generates current-game item and item-pool handles from the game DSL declarations."

    gameSource.set(gameDslSourceFile)
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
    archiveBaseName = "rylween-dsl"
}
tasks.compileTestKotlin.configure {
    dependsOn(generateCompiledGameHarness)
    dependsOn(tasks.named(gameSourceSet.classesTaskName))
}

tasks.named<KotlinCompile>(gameSourceSet.getCompileTaskName("kotlin")) {
    dependsOn(generateGameCatalogHandles)
    dependsOn(generateCompiledGameHarness)
}

tasks.named<KotlinCompile>(appSourceSet.getCompileTaskName("kotlin")) {
    dependsOn(tasks.named(gameSourceSet.classesTaskName))
}

val runKotlinTown: TaskProvider<JavaExec> by tasks.registering(JavaExec::class) {
    group = "application"
    description = "Boots the compiled game in the pure Kotlin terminal town runner."

    dependsOn(tasks.named(appSourceSet.classesTaskName))
    classpath = appSourceSet.runtimeClasspath
    mainClass.set("rylween.app.RylweenTerminalMainKt")
    standardInput = System.`in`
}

val runKotlinGame: TaskProvider<JavaExec> by tasks.registering(JavaExec::class) {
    group = "application"
    description = "Boots the compiled game in the pure Kotlin terminal wilderness runner."

    dependsOn(tasks.named(appSourceSet.classesTaskName))
    classpath = appSourceSet.runtimeClasspath
    mainClass.set("rylween.app.RylweenGameTerminalMain")
    standardInput = System.`in`
}

val nativeSmokeBundle: Provider<RegularFile> = layout.buildDirectory.file("compiled/rylween-native-smoke.rsmoke")
val nativeSmokeSource: RegularFile = layout.projectDirectory.file("src/native/compiled-smoke/rylween_bundle_smoke.c")
val nativeSmokeExecutable: Provider<RegularFile> = layout.buildDirectory.file("native/compiled-smoke/rylween-bundle-smoke")
val nativeJsonSource: RegularFile = layout.projectDirectory.file("src/native/bundle-json/rylween_bundle_json.c")
val nativeJsonShimSource: RegularFile = layout.projectDirectory.file("src/native/bundle-json/json5_compat.c")
val legacyJson5Source: RegularFile = layout.projectDirectory.file("legacy/c-json5/src/json5.c")
val legacyJson5IncludeDir: String =
    layout.projectDirectory
        .dir("legacy/c-json5/src")
        .asFile.path
val nativeJsonExecutable: Provider<RegularFile> = layout.buildDirectory.file("native/bundle-json/rylween-bundle-json")
val externalBundleArtifact: Provider<RegularFile> = layout.buildDirectory.file("compiled/rylween-bundle.json")
val externalBundleInspection: Provider<RegularFile> = layout.buildDirectory.file("compiled/rylween-bundle.inspect.txt")
val rustMudManifest: RegularFile = layout.projectDirectory.file("rust/rylween-mud/Cargo.toml")
val rustMudBundleInspection: Provider<RegularFile> = layout.buildDirectory.file("rust/rylween-mud/inspect.txt")
val requireRustMud: Provider<Boolean> =
    providers
        .gradleProperty("rylween.requireRust")
        .orElse(providers.environmentVariable("RYL_REQUIRE_RUST"))
        .map(String::toBoolean)
        .orElse(false)
val nativeCCompiler: Provider<String> = providers.environmentVariable("CC").orElse("cc")
val nativeCWarningFlags: List<String> = listOf("-std=c17", "-Wall", "-Wextra", "-Werror", "-pedantic")
val nativeSmokeBundlePath: String = nativeSmokeBundle.get().asFile.path
val nativeSmokeExecutablePath: String = nativeSmokeExecutable.get().asFile.path
val nativeJsonExecutablePath: String = nativeJsonExecutable.get().asFile.path
val externalBundleArtifactPath: String = externalBundleArtifact.get().asFile.path
val externalBundleInspectionPath: String = externalBundleInspection.get().asFile.path
val kotlinAnsiExecutable: Provider<RegularFile> = layout.buildDirectory.file("install/rylween-kotlin/bin/rylween")
val kotlinAnsiExecutablePath: String = kotlinAnsiExecutable.get().asFile.path
val bundleAnsiExecutable: Provider<RegularFile> = layout.buildDirectory.file("install/rylween-bundle-ansi/bin/rylween-bundle")
val compiledRuntimeExecutable: Provider<RegularFile> =
    layout.buildDirectory.file("install/rylween-compiled-runtime/bin/rylween-compiled")

fun registerNativeCExecutable(
    taskName: String,
    taskDescription: String,
    source: RegularFile,
    executable: Provider<RegularFile>,
    configure: CompileNativeCExecutableTask.() -> Unit = {},
): TaskProvider<CompileNativeCExecutableTask> =
    tasks.register<CompileNativeCExecutableTask>(taskName) {
        group = "verification"
        description = taskDescription
        compiler.set(nativeCCompiler)
        flags.set(nativeCWarningFlags)
        sourceFile.set(source)
        executableFile.set(executable)
        configure()
    }

val emitNativeSmokeBundle: TaskProvider<JavaExec> by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Emits the deterministic compiled-content/script artifact consumed by the native smoke loader."

    dependsOn(tasks.named(appSourceSet.classesTaskName))
    classpath = appSourceSet.runtimeClasspath
    mainClass.set("rylween.app.RylweenCompiledBundleArtifactMain")
    args(nativeSmokeBundlePath)
    inputs.files(appSourceSet.runtimeClasspath)
    outputs.file(nativeSmokeBundle)
}

val compileNativeSmokeLoader: TaskProvider<CompileNativeCExecutableTask> =
    registerNativeCExecutable(
        taskName = "compileNativeSmokeLoader",
        taskDescription = "Compiles the minimal C loader for the compiled VM smoke artifact.",
        source = nativeSmokeSource,
        executable = nativeSmokeExecutable,
    )

val compileNativeJsonBundleReader: TaskProvider<CompileNativeCExecutableTask> =
    registerNativeCExecutable(
        taskName = "compileNativeJsonBundleReader",
        taskDescription = "Compiles the minimal C reader for the J1 JSON bundle artifact.",
        source = nativeJsonSource,
        executable = nativeJsonExecutable,
    ) {
        includeDirs.add(legacyJson5IncludeDir)
        additionalSourceFiles.from(nativeJsonShimSource, legacyJson5Source)
    }

val checkNativeSmokeBundle: TaskProvider<Exec> by tasks.registering(Exec::class) {
    group = "verification"
    description = "Runs the C loader over the emitted compiled VM smoke artifact."

    dependsOn(emitNativeSmokeBundle)
    dependsOn(compileNativeSmokeLoader)
    inputs.file(nativeSmokeBundle)
    inputs.file(nativeSmokeExecutable)
    commandLine(
        nativeSmokeExecutablePath,
        nativeSmokeBundlePath,
    )
}

val checkNativeJsonBundle: TaskProvider<Exec> by tasks.registering(Exec::class) {
    group = "verification"
    description = "Runs the C JSON reader over the emitted J1 bundle artifact."

    dependsOn(emitExternalCompiledBundle)
    dependsOn(compileNativeJsonBundleReader)
    inputs.file(externalBundleArtifact)
    inputs.file(nativeJsonExecutable)
    commandLine(
        nativeJsonExecutablePath,
        externalBundleArtifactPath,
    )
}

val checkNativeCompiledVmProbe: TaskProvider<Exec> by tasks.registering(Exec::class) {
    group = "verification"
    description = "Runs the narrow C compiled-VM probe over the emitted J1 bundle artifact."

    dependsOn(checkNativeJsonBundle)
    inputs.file(externalBundleArtifact)
    inputs.file(nativeJsonExecutable)
    commandLine(
        nativeJsonExecutablePath,
        externalBundleArtifactPath,
        "--vm-probe",
    )
}

val emitExternalCompiledBundle: TaskProvider<JavaExec> by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Emits the deterministic JSON compiled bundle artifact."

    dependsOn(tasks.named(appSourceSet.classesTaskName))
    classpath = appSourceSet.runtimeClasspath
    mainClass.set("rylween.app.RylweenExternalBundleArtifactMain")
    args(externalBundleArtifactPath)
    inputs.files(appSourceSet.runtimeClasspath)
    outputs.file(externalBundleArtifact)
}

val checkExternalCompiledBundle: TaskProvider<JavaExec> by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Validates the deterministic JSON compiled bundle artifact."

    dependsOn(emitExternalCompiledBundle)
    classpath = appSourceSet.runtimeClasspath
    mainClass.set("rylween.app.RylweenExternalBundleCheckMain")
    args(externalBundleArtifactPath)
    inputs.file(externalBundleArtifact)
}

val inspectExternalCompiledBundle: TaskProvider<JavaExec> by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Writes a readable inspection summary for the deterministic JSON compiled bundle artifact."

    dependsOn(emitExternalCompiledBundle)
    classpath = appSourceSet.runtimeClasspath
    mainClass.set("rylween.app.RylweenExternalBundleInspectMain")
    args(externalBundleArtifactPath, externalBundleInspectionPath)
    inputs.file(externalBundleArtifact)
    outputs.file(externalBundleInspection)
}

val checkRustMudBundleInspect: TaskProvider<CheckRustMudBundleInspectTask> by tasks.registering(
    CheckRustMudBundleInspectTask::class,
) {
    group = "verification"
    description = "Runs the experimental Rust MUD target over the deterministic JSON compiled bundle when Cargo is available."

    dependsOn(emitExternalCompiledBundle)
    manifestFile.set(rustMudManifest)
    sourceDirectory.set(layout.projectDirectory.dir("rust/rylween-mud/src"))
    bundleFile.set(externalBundleArtifact)
    requireRust.set(requireRustMud)
    outputFile.set(rustMudBundleInspection)
}

val kotlinAnsiJar: TaskProvider<Jar> by tasks.registering(Jar::class) {
    group = "build"
    description = "Packages the compiled Kotlin DSL game and ANSI app resources into one launcher jar."

    dependsOn(tasks.named(appSourceSet.classesTaskName))
    archiveBaseName.set("rylween-kotlin")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(main.output)
    from(gameSourceSet.output)
    from(appSourceSet.output)
    manifest {
        attributes["Main-Class"] = "rylween.app.RylweenAnsiMain"
    }
}

val kotlinAnsiRuntimeJars: FileCollection = appSourceSet.runtimeClasspath.filter { it.isFile && it.extension == "jar" }
val kotlinAnsiWrapper: Provider<RegularFile> = layout.buildDirectory.file("scripts/kotlinAnsiWrapper/rylween")

val kotlinAnsiStartScripts: TaskProvider<CreateStartScripts> by tasks.registering(CreateStartScripts::class) {
    group = "application"
    description = "Generates direct start scripts for the ANSI runtime."

    dependsOn(kotlinAnsiJar)
    applicationName = "rylween-app"
    mainClass.set("rylween.app.RylweenAnsiMain")
    outputDir =
        layout.buildDirectory
            .dir("scripts/kotlinAnsiApp")
            .get()
            .asFile
    classpath = files(kotlinAnsiJar) + kotlinAnsiRuntimeJars
}

val kotlinAnsiBuildWrapper: TaskProvider<GenerateAnsiBuildWrapperTask> by tasks.registering(GenerateAnsiBuildWrapperTask::class) {
    group = "application"
    description = "Generates the ANSI runtime wrapper with an optional --build refresh."

    outputFile.set(kotlinAnsiWrapper)
}

val installKotlinAnsi: TaskProvider<Sync> by tasks.registering(Sync::class) {
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

val bundleAnsiJar: TaskProvider<Jar> by tasks.registering(Jar::class) {
    group = "build"
    description = "Packages the bundle-only ANSI runtime without the generated game source set."

    dependsOn(tasks.named(bundleAppSourceSet.classesTaskName))
    archiveBaseName.set("rylween-bundle-ansi")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(main.output)
    from(bundleAppSourceSet.output)
    manifest {
        attributes["Main-Class"] = "rylween.app.RylweenAnsiMain"
    }
}

val bundleAnsiRuntimeJars: FileCollection = bundleAppSourceSet.runtimeClasspath.filter { it.isFile && it.extension == "jar" }

val bundleAnsiStartScripts: TaskProvider<CreateStartScripts> by tasks.registering(CreateStartScripts::class) {
    group = "application"
    description = "Generates direct start scripts for the bundle-only ANSI runtime."

    dependsOn(bundleAnsiJar)
    applicationName = "rylween-bundle"
    mainClass.set("rylween.app.RylweenAnsiMain")
    outputDir =
        layout.buildDirectory
            .dir("scripts/bundleAnsiApp")
            .get()
            .asFile
    classpath = files(bundleAnsiJar) + bundleAnsiRuntimeJars
}

val installBundleAnsi: TaskProvider<Sync> by tasks.registering(Sync::class) {
    group = "application"
    description = "Installs the bundle-only ANSI runtime launcher."

    dependsOn(bundleAnsiStartScripts)
    into(layout.buildDirectory.dir("install/rylween-bundle-ansi"))
    from(bundleAnsiStartScripts) {
        into("bin")
        filePermissions {
            unix("rwxr-xr-x")
        }
    }
    from(files(bundleAnsiJar) + bundleAnsiRuntimeJars) {
        into("lib")
    }
}

val compiledRuntimeJar: TaskProvider<Jar> by tasks.registering(Jar::class) {
    group = "build"
    description = "Packages the standalone compiled-bundle runtime prototype."

    dependsOn(tasks.named(compiledRuntimeSourceSet.classesTaskName))
    archiveBaseName.set("rylween-compiled-runtime")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(main.output)
    from(compiledRuntimeSourceSet.output)
    manifest {
        attributes["Main-Class"] = "rylween.compiledruntime.RylweenCompiledRuntimeMain"
    }
}

val compiledRuntimeRuntimeJars: FileCollection =
    compiledRuntimeSourceSet.runtimeClasspath.filter { it.isFile && it.extension == "jar" }

val compiledRuntimeStartScripts: TaskProvider<CreateStartScripts> by tasks.registering(CreateStartScripts::class) {
    group = "application"
    description = "Generates direct start scripts for the standalone compiled-bundle runtime."

    dependsOn(compiledRuntimeJar)
    applicationName = "rylween-compiled"
    mainClass.set("rylween.compiledruntime.RylweenCompiledRuntimeMain")
    outputDir =
        layout.buildDirectory
            .dir("scripts/compiledRuntimeApp")
            .get()
            .asFile
    classpath = files(compiledRuntimeJar) + compiledRuntimeRuntimeJars
}

val installCompiledRuntime: TaskProvider<Sync> by tasks.registering(Sync::class) {
    group = "application"
    description = "Installs the standalone compiled-bundle runtime prototype."

    dependsOn(compiledRuntimeStartScripts)
    into(layout.buildDirectory.dir("install/rylween-compiled-runtime"))
    from(compiledRuntimeStartScripts) {
        into("bin")
        filePermissions {
            unix("rwxr-xr-x")
        }
    }
    from(files(compiledRuntimeJar) + compiledRuntimeRuntimeJars) {
        into("lib")
    }
}

val checkKotlinAnsiBundleBoot: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Boots the ANSI launcher through --bundle to verify artifact-backed startup gating."

    dependsOn(installKotlinAnsi)
    dependsOn(emitExternalCompiledBundle)
    executableFile.set(kotlinAnsiExecutable)
    bundleFile.set(externalBundleArtifact)
    javaHomePath.set(
        kotlinRuntimeJavaLauncher.map { launcher ->
            launcher.metadata.installationPath.asFile.absolutePath
        },
    )
    inheritedPath.set(providers.environmentVariable("PATH").orElse(""))
    screen.set("title")
}

val checkBundleAnsiBundleBoot: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Boots the bundle-only ANSI launcher to verify generated-game-free startup."

    dependsOn(installBundleAnsi)
    dependsOn(emitExternalCompiledBundle)
    executableFile.set(bundleAnsiExecutable)
    bundleFile.set(externalBundleArtifact)
    javaHomePath.set(
        kotlinRuntimeJavaLauncher.map { launcher ->
            launcher.metadata.installationPath.asFile.absolutePath
        },
    )
    inheritedPath.set(providers.environmentVariable("PATH").orElse(""))
    screen.set("title")
}

val checkCompiledRuntimeBundleBoot: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Boots the standalone compiled-bundle runtime without old session composition."

    dependsOn(installCompiledRuntime)
    dependsOn(emitExternalCompiledBundle)
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
    dependsOn(emitExternalCompiledBundle)
    executableFile.set(compiledRuntimeExecutable)
    bundleFile.set(externalBundleArtifact)
    javaHomePath.set(
        kotlinRuntimeJavaLauncher.map { launcher ->
            launcher.metadata.installationPath.asFile.absolutePath
        },
    )
    inheritedPath.set(providers.environmentVariable("PATH").orElse(""))
    script.set("22E1X")
}

val checkKotlinAnsiServerBoot: TaskProvider<CheckAnsiServerLauncherTask> by tasks.registering(
    CheckAnsiServerLauncherTask::class,
) {
    group = "verification"
    description = "Boots the ANSI launcher in --serve socket mode with the compiled VM and verifies a town/shop route."

    dependsOn(installKotlinAnsi)
    dependsOn(emitExternalCompiledBundle)
    executableFile.set(kotlinAnsiExecutable)
    bundleFile.set(externalBundleArtifact)
    javaHomePath.set(
        kotlinRuntimeJavaLauncher.map { launcher ->
            launcher.metadata.installationPath.asFile.absolutePath
        },
    )
    inheritedPath.set(providers.environmentVariable("PATH").orElse(""))
    stateSlug.set("strong-replay")
    scriptVmMode.set(AnsiScriptVmMode.Compiled)
    workingDirectoryPath.set(layout.projectDirectory.asFile.absolutePath)
    logFile.set(layout.buildDirectory.file("logs/check-kotlin-ansi-server.log"))
}

val checkKotlinAnsiCompiledVmServerBoot: TaskProvider<CheckAnsiServerLauncherTask> by tasks.registering(
    CheckAnsiServerLauncherTask::class,
) {
    group = "verification"
    description = "Boots the ANSI socket launcher with --script-vm compiled and verifies a town/shop route."

    dependsOn(installKotlinAnsi)
    dependsOn(emitExternalCompiledBundle)
    executableFile.set(kotlinAnsiExecutable)
    bundleFile.set(externalBundleArtifact)
    javaHomePath.set(
        kotlinRuntimeJavaLauncher.map { launcher ->
            launcher.metadata.installationPath.asFile.absolutePath
        },
    )
    inheritedPath.set(providers.environmentVariable("PATH").orElse(""))
    stateSlug.set("strong-replay")
    scriptVmMode.set(AnsiScriptVmMode.Compiled)
    workingDirectoryPath.set(layout.projectDirectory.asFile.absolutePath)
    logFile.set(layout.buildDirectory.file("logs/check-kotlin-ansi-compiled-vm-server.log"))
}

val validateGameDsl: TaskProvider<JavaExec> by tasks.registering(JavaExec::class) {
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
        gameDslSourceFile.asFile.absolutePath,
    )
}

tasks.check {
    dependsOn(validateGameDsl)
    dependsOn(tasks.named(appSourceSet.classesTaskName))
    dependsOn(checkNativeSmokeBundle)
    dependsOn(checkNativeJsonBundle)
    dependsOn(checkNativeCompiledVmProbe)
    dependsOn(checkExternalCompiledBundle)
    dependsOn(inspectExternalCompiledBundle)
    dependsOn(checkRustMudBundleInspect)
    dependsOn(checkKotlinAnsiBundleBoot)
    dependsOn(checkBundleAnsiBundleBoot)
    dependsOn(checkCompiledRuntimeBundleBoot)
    dependsOn(checkCompiledRuntimeTownScript)
    dependsOn(checkKotlinAnsiServerBoot)
    dependsOn(checkKotlinAnsiCompiledVmServerBoot)
}
