import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.jvm.application.tasks.CreateStartScripts
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import rylween.gradle.RunInstalledLauncherWithJavaTask
import java.io.File
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

plugins {
    alias(libs.plugins.kotlin.jvm)
    id("rylween.qa-conventions")
}

enum class AnsiScriptVmMode(
    val cliArgument: String,
) {
    Compiled("compiled"),
}

/**
 * Archived ANSI socket launcher smoke task kept with the legacy runtime.
 */
abstract class CheckAnsiServerLauncherTask : DefaultTask() {
    @get:InputFile
    abstract val executableFile: RegularFileProperty

    @get:InputFile
    abstract val bundleFile: RegularFileProperty

    @get:Input
    abstract val javaHomePath: Property<String>

    @get:Input
    abstract val inheritedPath: Property<String>

    @get:Input
    abstract val stateSlug: Property<String>

    @get:Input
    abstract val scriptVmMode: Property<AnsiScriptVmMode>

    @get:Input
    abstract val workingDirectoryPath: Property<String>

    @get:OutputFile
    abstract val logFile: RegularFileProperty

    @TaskAction
    fun run() {
        val port = freeTcpPort()
        val javaHome = File(javaHomePath.get())
        val log = logFile.get().asFile.apply { parentFile.mkdirs() }
        val process =
            ProcessBuilder(
                executableFile.get().asFile.path,
                "--bundle",
                bundleFile.get().asFile.path,
                "--serve",
                "127.0.0.1:$port",
                "--state",
                stateSlug.get(),
                "--script-vm",
                scriptVmMode.get().cliArgument,
            ).directory(File(workingDirectoryPath.get()))
                .redirectErrorStream(true)
                .apply {
                    environment()["JAVA_HOME"] = javaHome.absolutePath
                    environment()["PATH"] = javaHome.resolve("bin").path + File.pathSeparator + inheritedPath.get()
                    environment()["RYL_LOG"] = "file:${log.path}"
                    environment()["RYL_LOG_LEVEL"] = "debug"
                }.start()
        try {
            val socket = connectWithRetry(port)
            socket.use { client ->
                client.soTimeout = 10_000
                val input = client.getInputStream()
                val output = client.getOutputStream()
                val firstScreen = readUntil(input, "Wilderness", maxBytes = 64 * 1024)
                require("Wilderness" in firstScreen) {
                    "ANSI server did not render the wilderness screen"
                }
                output.write("22ESBW1PLLXY".toByteArray(Charsets.UTF_8))
                output.flush()
                val playthrough = readUntil(input, "Fare thee well.", maxBytes = 512 * 1024)
                listOf(
                    "Village of Thord",
                    "Armor or Weapon?",
                    "(1) Sword",
                    "Deal!",
                    "Fare thee well.",
                ).forEach { fragment ->
                    require(fragment in playthrough) {
                        "ANSI server playthrough did not render '$fragment'"
                    }
                }
            }
            require(process.waitFor(15, TimeUnit.SECONDS)) {
                "ANSI server did not exit after quit input"
            }
            require(process.exitValue() == 0) {
                "ANSI server exited with ${process.exitValue()}"
            }
            require(log.readText().contains("[AUD][server] client connected")) {
                "ANSI server log did not record a client connection"
            }
            require("status=EnteredTown" in log.readText() && "town=thord" in log.readText()) {
                "ANSI server log did not record the Thord handoff"
            }
            require("mode=${scriptVmMode.get().cliArgument}" in log.readText()) {
                "ANSI server log did not record the ${scriptVmMode.get().cliArgument} script VM mode"
            }
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }

    private fun freeTcpPort(): Int = ServerSocket(0).use { server -> server.localPort }

    private fun connectWithRetry(port: Int): Socket {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
        var lastError: Exception? = null
        while (System.nanoTime() < deadline) {
            try {
                return Socket("127.0.0.1", port)
            } catch (error: Exception) {
                lastError = error
                Thread.sleep(100)
            }
        }
        throw IllegalStateException("could not connect to ANSI server on port $port", lastError)
    }

    private fun readUntil(
        input: InputStream,
        needle: String,
        maxBytes: Int,
    ): String {
        val buffer = StringBuilder()
        while (buffer.length < maxBytes && needle !in buffer) {
            val value =
                try {
                    input.read()
                } catch (_: SocketTimeoutException) {
                    -1
                }
            if (value < 0) break
            buffer.append(value.toChar())
        }
        return buffer.toString()
    }
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
    resources.include("ansi/**", "tiles/**", "adventurers-of-rylween.md", "ALTAIR.displayme")
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
    resources.include("ansi/**", "tiles/**", "adventurers-of-rylween.md", "ALTAIR.displayme")
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
