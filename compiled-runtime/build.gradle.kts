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

tasks.check {
    dependsOn(checkCompiledRuntimeBundleBoot)
    dependsOn(checkCompiledRuntimeTownScript)
    dependsOn(checkCompiledRuntimeLoadScript)
    dependsOn(checkCompiledRuntimePartyJournalScript)
    dependsOn(checkCompiledRuntimeWildernessActionsScript)
    dependsOn(checkCompiledRuntimeDungeonScript)
    dependsOn(checkCompiledRuntimeCastleScript)
}
