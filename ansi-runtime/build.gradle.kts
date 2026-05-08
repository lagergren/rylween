import org.gradle.jvm.application.tasks.CreateStartScripts
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import rylween.gradle.AnsiScriptVmMode
import rylween.gradle.CheckAnsiServerLauncherTask
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
    implementation(libs.clikt)
    implementation(libs.jline)
}

val rootSourceSets: SourceSetContainer = rootProject.extensions.getByType()
val rootGameSourceSet: SourceSet = rootSourceSets.named("game").get()

val mainSourceSet: SourceSet = sourceSets.named("main").get()
mainSourceSet.apply {
    resources.srcDir(rootProject.layout.projectDirectory.dir("data"))
    resources.include("ansi/**", "tiles/**", "rylween-doc.txt", "ALTAIR.displayme")
    compileClasspath += rootGameSourceSet.output
    runtimeClasspath += rootGameSourceSet.output
}

val bundleApp: SourceSet by sourceSets.creating {
    java.srcDir("src/main/kotlin")
    java.include(
        "rylween/app/AppAssets.kt",
        "rylween/app/CliDiagnostics.kt",
        "rylween/app/CommonCliFlags.kt",
        "rylween/app/GameBoot.kt",
        "rylween/app/RylweenAnsiMain.kt",
        "rylween/frontend/terminal/**",
    )
    resources.srcDir(rootProject.layout.projectDirectory.dir("data"))
    resources.include("ansi/**", "tiles/**", "rylween-doc.txt", "ALTAIR.displayme")
    runtimeClasspath += output + compileClasspath
}

val bundleAppSourceSet: SourceSet = bundleApp

configurations.named(bundleAppSourceSet.implementationConfigurationName) {
    extendsFrom(configurations.implementation.get())
}

tasks.named<KotlinCompile>(mainSourceSet.getCompileTaskName("kotlin")) {
    dependsOn(rootProject.tasks.named(rootGameSourceSet.classesTaskName))
}

tasks.named<KotlinCompile>(bundleAppSourceSet.getCompileTaskName("kotlin")) {
    dependsOn(rootProject.tasks.named("jar"))
}

val javaToolchains: JavaToolchainService = extensions.getByType()
val kotlinRuntimeJavaLauncher: Provider<JavaLauncher> =
    javaToolchains.launcherFor {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
val externalBundleArtifact: Provider<RegularFile> =
    rootProject.layout.buildDirectory.file("compiled/rylween-bundle.json")
val kotlinAnsiExecutable: Provider<RegularFile> = layout.buildDirectory.file("install/rylween-kotlin/bin/rylween")
val bundleAnsiExecutable: Provider<RegularFile> = layout.buildDirectory.file("install/rylween-bundle-ansi/bin/rylween-bundle")
val rootRuntimeJar: TaskProvider<Jar> = rootProject.tasks.named<Jar>("jar")
val rootRuntimeJarFile: Provider<RegularFile> = rootRuntimeJar.flatMap { it.archiveFile }

val runKotlinTown: TaskProvider<JavaExec> by tasks.registering(JavaExec::class) {
    group = "application"
    description = "Boots the compiled game in the pure Kotlin terminal town runner."

    dependsOn(tasks.named(mainSourceSet.classesTaskName))
    classpath = mainSourceSet.runtimeClasspath
    mainClass.set("rylween.app.RylweenTerminalMainKt")
    standardInput = System.`in`
}

val runKotlinGame: TaskProvider<JavaExec> by tasks.registering(JavaExec::class) {
    group = "application"
    description = "Boots the compiled game in the pure Kotlin terminal wilderness runner."

    dependsOn(tasks.named(mainSourceSet.classesTaskName))
    classpath = mainSourceSet.runtimeClasspath
    mainClass.set("rylween.app.RylweenGameTerminalMain")
    standardInput = System.`in`
}

val kotlinAnsiJar: TaskProvider<Jar> by tasks.registering(Jar::class) {
    group = "build"
    description = "Packages the generated-game ANSI runtime launcher."

    dependsOn(tasks.named(mainSourceSet.classesTaskName))
    archiveBaseName.set("rylween-kotlin")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(mainSourceSet.output)
    from(rootGameSourceSet.output)
    manifest {
        attributes["Main-Class"] = "rylween.app.RylweenAnsiMain"
    }
}

val kotlinAnsiJarFile: Provider<RegularFile> = kotlinAnsiJar.flatMap { it.archiveFile }
val kotlinAnsiRuntimeJars: FileCollection =
    configurations.runtimeClasspath.get().filter { file -> file.isFile && file.extension == "jar" }

val kotlinAnsiStartScripts: TaskProvider<CreateStartScripts> by tasks.registering(CreateStartScripts::class) {
    group = "application"
    description = "Generates direct start scripts for the ANSI runtime."

    dependsOn(kotlinAnsiJar)
    dependsOn(rootRuntimeJar)
    applicationName = "rylween"
    mainClass.set("rylween.app.RylweenAnsiMain")
    outputDir =
        layout.buildDirectory
            .dir("scripts/kotlinAnsiApp")
            .get()
            .asFile
    classpath = files(kotlinAnsiJarFile, rootRuntimeJarFile) + kotlinAnsiRuntimeJars
}

val installKotlinAnsi: TaskProvider<Sync> by tasks.registering(Sync::class) {
    group = "application"
    description = "Installs the generated-game ANSI runtime launcher."

    dependsOn(kotlinAnsiStartScripts)
    into(layout.buildDirectory.dir("install/rylween-kotlin"))
    from(kotlinAnsiStartScripts) {
        into("bin")
        filePermissions {
            unix("rwxr-xr-x")
        }
    }
    from(files(kotlinAnsiJarFile, rootRuntimeJarFile) + kotlinAnsiRuntimeJars) {
        into("lib")
    }
}

val bundleAnsiJar: TaskProvider<Jar> by tasks.registering(Jar::class) {
    group = "build"
    description = "Packages the bundle-only ANSI runtime without the generated game source set."

    dependsOn(tasks.named(bundleAppSourceSet.classesTaskName))
    archiveBaseName.set("rylween-bundle-ansi")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(bundleAppSourceSet.output)
    manifest {
        attributes["Main-Class"] = "rylween.app.RylweenAnsiMain"
    }
}

val bundleAnsiJarFile: Provider<RegularFile> = bundleAnsiJar.flatMap { it.archiveFile }
val bundleAnsiRuntimeClasspath: Configuration = configurations.getByName(bundleAppSourceSet.runtimeClasspathConfigurationName)
val bundleAnsiRuntimeJars: FileCollection =
    bundleAnsiRuntimeClasspath.filter { file -> file.isFile && file.extension == "jar" }

val bundleAnsiStartScripts: TaskProvider<CreateStartScripts> by tasks.registering(CreateStartScripts::class) {
    group = "application"
    description = "Generates direct start scripts for the bundle-only ANSI runtime."

    dependsOn(bundleAnsiJar)
    dependsOn(rootRuntimeJar)
    applicationName = "rylween-bundle"
    mainClass.set("rylween.app.RylweenAnsiMain")
    outputDir =
        layout.buildDirectory
            .dir("scripts/bundleAnsiApp")
            .get()
            .asFile
    classpath = files(bundleAnsiJarFile, rootRuntimeJarFile) + bundleAnsiRuntimeJars
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
    from(files(bundleAnsiJarFile, rootRuntimeJarFile) + bundleAnsiRuntimeJars) {
        into("lib")
    }
}

val checkKotlinAnsiBundleBoot: TaskProvider<RunInstalledLauncherWithJavaTask> by tasks.registering(
    RunInstalledLauncherWithJavaTask::class,
) {
    group = "verification"
    description = "Boots the ANSI launcher through --bundle to verify artifact-backed startup gating."

    dependsOn(installKotlinAnsi)
    dependsOn(":emitExternalCompiledBundle")
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
    dependsOn(":emitExternalCompiledBundle")
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

val checkKotlinAnsiServerBoot: TaskProvider<CheckAnsiServerLauncherTask> by tasks.registering(
    CheckAnsiServerLauncherTask::class,
) {
    group = "verification"
    description = "Boots the ANSI launcher in --serve socket mode with the compiled VM and verifies a town/shop route."

    dependsOn(installKotlinAnsi)
    dependsOn(":emitExternalCompiledBundle")
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
    workingDirectoryPath.set(rootProject.layout.projectDirectory.asFile.absolutePath)
    logFile.set(layout.buildDirectory.file("logs/check-kotlin-ansi-server.log"))
}

val checkKotlinAnsiCompiledVmServerBoot: TaskProvider<CheckAnsiServerLauncherTask> by tasks.registering(
    CheckAnsiServerLauncherTask::class,
) {
    group = "verification"
    description = "Boots the ANSI socket launcher with --script-vm compiled and verifies a town/shop route."

    dependsOn(installKotlinAnsi)
    dependsOn(":emitExternalCompiledBundle")
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
    workingDirectoryPath.set(rootProject.layout.projectDirectory.asFile.absolutePath)
    logFile.set(layout.buildDirectory.file("logs/check-kotlin-ansi-compiled-vm-server.log"))
}

tasks.check {
    dependsOn(checkKotlinAnsiBundleBoot)
    dependsOn(checkBundleAnsiBundleBoot)
    dependsOn(checkKotlinAnsiServerBoot)
    dependsOn(checkKotlinAnsiCompiledVmServerBoot)
}
