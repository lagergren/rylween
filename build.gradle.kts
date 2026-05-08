import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import rylween.gradle.CheckRustMudBundleInspectTask
import rylween.gradle.CompileNativeCExecutableTask
import rylween.gradle.GenerateCompiledGameHarnessTask
import rylween.gradle.GenerateGameCatalogHandlesTask

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

val main: SourceSet by sourceSets.getting

val game: SourceSet by sourceSets.creating {
    java.srcDir("src/game/kotlin")
    java.srcDir(generatedGameHarnessDir)
    java.srcDir(generatedGameCatalogDir)
    compileClasspath += main.output
    runtimeClasspath += output + compileClasspath
}

val bundleTool: SourceSet by sourceSets.creating {
    java.srcDir("src/tools/kotlin")
    compileClasspath += main.output + game.output
    runtimeClasspath += output + compileClasspath
}

val gameSourceSet: SourceSet = game
val bundleToolSourceSet: SourceSet = bundleTool

val test: SourceSet by sourceSets.getting
test.apply {
    compileClasspath += game.output
    runtimeClasspath += game.output
}

configurations.named(gameSourceSet.implementationConfigurationName) {
    extendsFrom(configurations.implementation.get())
}

configurations.named(bundleToolSourceSet.implementationConfigurationName) {
    extendsFrom(configurations.implementation.get())
}

dependencies {
    implementation(kotlin("reflect"))
    testImplementation(kotlin("test"))
    testImplementation(project(":ansi-runtime"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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

tasks.named<KotlinCompile>(bundleToolSourceSet.getCompileTaskName("kotlin")) {
    dependsOn(tasks.named(gameSourceSet.classesTaskName))
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

    dependsOn(tasks.named(bundleToolSourceSet.classesTaskName))
    classpath = bundleToolSourceSet.runtimeClasspath
    mainClass.set("rylween.tools.RylweenCompiledBundleArtifactMain")
    args(nativeSmokeBundlePath)
    inputs.files(bundleToolSourceSet.runtimeClasspath)
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

    dependsOn(tasks.named(bundleToolSourceSet.classesTaskName))
    classpath = bundleToolSourceSet.runtimeClasspath
    mainClass.set("rylween.tools.RylweenExternalBundleArtifactMain")
    args(externalBundleArtifactPath)
    inputs.files(bundleToolSourceSet.runtimeClasspath)
    outputs.file(externalBundleArtifact)
}

val checkExternalCompiledBundle: TaskProvider<JavaExec> by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Validates the deterministic JSON compiled bundle artifact."

    dependsOn(emitExternalCompiledBundle)
    classpath = bundleToolSourceSet.runtimeClasspath
    mainClass.set("rylween.tools.RylweenExternalBundleCheckMain")
    args(externalBundleArtifactPath)
    inputs.file(externalBundleArtifact)
}

val inspectExternalCompiledBundle: TaskProvider<JavaExec> by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Writes a readable inspection summary for the deterministic JSON compiled bundle artifact."

    dependsOn(emitExternalCompiledBundle)
    classpath = bundleToolSourceSet.runtimeClasspath
    mainClass.set("rylween.tools.RylweenExternalBundleInspectMain")
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
    dependsOn(inspectExternalCompiledBundle)
    manifestFile.set(rustMudManifest)
    sourceDirectory.set(layout.projectDirectory.dir("rust/rylween-mud/src"))
    bundleFile.set(externalBundleArtifact)
    kotlinInspectionFile.set(externalBundleInspection)
    requireRust.set(requireRustMud)
    outputFile.set(rustMudBundleInspection)
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
    dependsOn(checkNativeSmokeBundle)
    dependsOn(checkNativeJsonBundle)
    dependsOn(checkNativeCompiledVmProbe)
    dependsOn(checkExternalCompiledBundle)
    dependsOn(inspectExternalCompiledBundle)
    dependsOn(checkRustMudBundleInspect)
    dependsOn(":ansi-terminal:check")
    dependsOn(":ansi-runtime:check")
    dependsOn(":compiled-runtime:check")
}
