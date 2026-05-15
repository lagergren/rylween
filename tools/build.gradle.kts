// Builds bundle artifact emitters and native smoke-check tooling.
import rylween.gradle.CompileNativeCExecutableTask

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
    implementation(project(":game"))
    implementation(project(":runtime"))
}

tasks.jar {
    archiveBaseName = "rylween-tools"
}

val mainSourceSet: SourceSet = sourceSets.named("main").get()
val nativeSmokeBundle: Provider<RegularFile> = rootProject.layout.buildDirectory.file("compiled/rylween-native-smoke.rsmoke")
val nativeSmokeSource: RegularFile = layout.projectDirectory.file("src/main/c/compiled-smoke/rylween_bundle_smoke.c")
val nativeSmokeExecutable: Provider<RegularFile> = layout.buildDirectory.file("native/compiled-smoke/rylween-bundle-smoke")
val nativeJsonSource: RegularFile = layout.projectDirectory.file("src/main/c/bundle-json/rylween_bundle_json.c")
val nativeJsonShimSource: RegularFile = layout.projectDirectory.file("src/main/c/bundle-json/json5_compat.c")
val nativeJson5Source: RegularFile = layout.projectDirectory.file("src/main/c/json5/json5.c")
val nativeJson5IncludeDir: String =
    layout.projectDirectory
        .dir("src/main/c/json5")
        .asFile.path
val nativeJsonExecutable: Provider<RegularFile> = layout.buildDirectory.file("native/bundle-json/rylween-bundle-json")
val externalBundleArtifact: Provider<RegularFile> = rootProject.layout.buildDirectory.file("compiled/rylween-bundle.json")
val externalBundleInspection: Provider<RegularFile> = rootProject.layout.buildDirectory.file("compiled/rylween-bundle.inspect.txt")
val nativeSmokeBundlePath: String = nativeSmokeBundle.get().asFile.path
val nativeSmokeExecutablePath: String = nativeSmokeExecutable.get().asFile.path
val nativeJsonExecutablePath: String = nativeJsonExecutable.get().asFile.path
val externalBundleArtifactPath: String = externalBundleArtifact.get().asFile.path
val externalBundleInspectionPath: String = externalBundleInspection.get().asFile.path
val nativeCCompiler: Provider<String> = providers.environmentVariable("CC").orElse("cc")
val nativeCWarningFlags: List<String> = listOf("-std=c17", "-Wall", "-Wextra", "-Werror", "-pedantic")

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

    dependsOn(tasks.named(mainSourceSet.classesTaskName))
    classpath = mainSourceSet.runtimeClasspath
    mainClass.set("rylween.tools.RylweenCompiledBundleArtifactMain")
    args(nativeSmokeBundlePath)
    inputs.files(mainSourceSet.runtimeClasspath)
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
        includeDirs.add(nativeJson5IncludeDir)
        additionalSourceFiles.from(nativeJsonShimSource, nativeJson5Source)
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

    dependsOn(tasks.named(mainSourceSet.classesTaskName))
    classpath = mainSourceSet.runtimeClasspath
    mainClass.set("rylween.tools.RylweenExternalBundleArtifactMain")
    args(externalBundleArtifactPath)
    inputs.files(mainSourceSet.runtimeClasspath)
    outputs.file(externalBundleArtifact)
}

val checkExternalCompiledBundle: TaskProvider<JavaExec> by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Validates the deterministic JSON compiled bundle artifact."

    dependsOn(emitExternalCompiledBundle)
    classpath = mainSourceSet.runtimeClasspath
    mainClass.set("rylween.tools.RylweenExternalBundleCheckMain")
    args(externalBundleArtifactPath)
    inputs.file(externalBundleArtifact)
}

val inspectExternalCompiledBundle: TaskProvider<JavaExec> by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Writes a readable inspection summary for the deterministic JSON compiled bundle artifact."

    dependsOn(emitExternalCompiledBundle)
    classpath = mainSourceSet.runtimeClasspath
    mainClass.set("rylween.tools.RylweenExternalBundleInspectMain")
    args(externalBundleArtifactPath, externalBundleInspectionPath)
    inputs.file(externalBundleArtifact)
    outputs.file(externalBundleInspection)
}

tasks.check {
    dependsOn(checkNativeSmokeBundle)
    dependsOn(checkNativeJsonBundle)
    dependsOn(checkNativeCompiledVmProbe)
    dependsOn(checkExternalCompiledBundle)
    dependsOn(inspectExternalCompiledBundle)
}
