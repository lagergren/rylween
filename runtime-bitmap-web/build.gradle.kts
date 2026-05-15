// Builds the bitmap browser runtime, generated client resources, and launcher distribution.
import org.gradle.jvm.application.tasks.CreateStartScripts

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
    implementation(project(":web-common"))
    implementation(project(":core"))
    implementation(project(":game"))
    implementation(project(":runtime"))
}

val runtimeBitmapWebJar: TaskProvider<Jar> = tasks.jar
val runtimeBitmapWebJarFile: Provider<RegularFile> = runtimeBitmapWebJar.flatMap { it.archiveFile }
val runtimeBitmapWebClasspathConfig: Configuration = configurations.runtimeClasspath.get()
val runtimeBitmapWebJars: FileCollection =
    runtimeBitmapWebClasspathConfig.filter { file -> file.isFile && file.extension == "jar" }
val bitmapWebGeneratedResources: Provider<Directory> = layout.buildDirectory.dir("generated/resources/bitmapWeb")
val browserSmokeBrowser: Provider<String> =
    providers
        .gradleProperty("rylween.browserSmoke.browser")
        .orElse("chromium")
val browserSmokeWorkDir: Provider<Directory> = layout.buildDirectory.dir("bitmap-browser-smoke")
val browserSmokeReportDir: Provider<Directory> = layout.buildDirectory.dir("reports/bitmap-browser-smoke")
val playwrightBrowsersDir: Provider<Directory> = layout.buildDirectory.dir("playwright-browsers")
val playwrightCli: Configuration by configurations.creating
val browserSmokeSourceSet: SourceSet =
    sourceSets.create("browserSmoke") {
        kotlin.srcDir("src/browserSmoke/kotlin")
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += output + compileClasspath
    }

configurations[browserSmokeSourceSet.implementationConfigurationName].extendsFrom(configurations.implementation.get())

dependencies {
    add(browserSmokeSourceSet.implementationConfigurationName, libs.playwright)
    playwrightCli(libs.playwright)
}

val npmInstallBitmapWeb: TaskProvider<Exec> by tasks.registering(Exec::class) {
    group = "generation"
    description = "Installs the bitmap web TypeScript compiler."

    workingDir = layout.projectDirectory.asFile
    inputs.file(layout.projectDirectory.file("package.json"))
    inputs.file(layout.projectDirectory.file("package-lock.json"))
    outputs.dir(layout.projectDirectory.dir("node_modules"))
    commandLine("npm", "ci", "--ignore-scripts")
}
val generateBitmapWebClientJs: TaskProvider<Exec> by tasks.registering(Exec::class) {
    group = "generation"
    description = "Compiles the authored bitmap web TypeScript client to the served JavaScript resource."

    dependsOn(npmInstallBitmapWeb)
    workingDir = layout.projectDirectory.asFile
    inputs.file(layout.projectDirectory.file("src/main/resources/bitmap-web/client.ts"))
    inputs.file(layout.projectDirectory.file("tsconfig.json"))
    outputs.file(bitmapWebGeneratedResources.map { directory -> directory.file("bitmap-web/client.js") })
    commandLine("npm", "run", "build")
}

sourceSets.named("main") {
    resources.srcDir(bitmapWebGeneratedResources)
}

tasks.processResources {
    dependsOn(generateBitmapWebClientJs)
}

tasks.jar {
    archiveBaseName.set("rylween-runtime-bitmap-web")
    manifest {
        attributes["Main-Class"] = "rylween.bitmapweb.RylweenBitmapWebRuntimeMainKt"
    }
}

val runtimeBitmapWebStartScripts: TaskProvider<CreateStartScripts> by tasks.registering(CreateStartScripts::class) {
    group = "application"
    description = "Generates direct start scripts for the bitmap web runtime."

    dependsOn(runtimeBitmapWebJar)
    applicationName = "rylween-bitmap-web"
    mainClass.set("rylween.bitmapweb.RylweenBitmapWebRuntimeMainKt")
    outputDir =
        layout.buildDirectory
            .dir("scripts/runtimeBitmapWebApp")
            .get()
            .asFile
    classpath = files(runtimeBitmapWebJarFile) + runtimeBitmapWebJars
}

val installRuntimeBitmapWeb: TaskProvider<Sync> by tasks.registering(Sync::class) {
    group = "application"
    description = "Installs the bitmap web runtime."

    dependsOn(runtimeBitmapWebStartScripts)
    into(layout.buildDirectory.dir("install/rylween-bitmap-web"))
    from(runtimeBitmapWebStartScripts) {
        into("bin")
        filePermissions {
            unix("rwxr-xr-x")
        }
    }
    from(files(runtimeBitmapWebJarFile) + runtimeBitmapWebJars) {
        into("lib")
    }
}

val installBitmapSmokeBrowser: TaskProvider<JavaExec> by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Downloads the Playwright-managed browser used by the bitmap browser smoke."

    classpath = playwrightCli
    mainClass.set("com.microsoft.playwright.CLI")
    inputs.property("browser", browserSmokeBrowser)
    outputs.dir(playwrightBrowsersDir)
    environment("PLAYWRIGHT_BROWSERS_PATH", playwrightBrowsersDir.get().asFile.absolutePath)
    args("install", browserSmokeBrowser.get())
}

val checkBitmapBrowserSmoke: TaskProvider<JavaExec> by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Runs a browser canvas smoke against the bitmap web runtime using a Gradle-managed Playwright browser."

    dependsOn(installRuntimeBitmapWeb)
    dependsOn(":runtime:installRuntime")
    dependsOn(":tools:emitExternalCompiledBundle")
    dependsOn(installBitmapSmokeBrowser)
    dependsOn(tasks.named(browserSmokeSourceSet.classesTaskName))
    classpath = browserSmokeSourceSet.runtimeClasspath
    mainClass.set("rylween.bitmapweb.smoke.BitmapBrowserSmokeKt")
    environment("PLAYWRIGHT_BROWSERS_PATH", playwrightBrowsersDir.get().asFile.absolutePath)
    environment("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")
    args(
        "--root",
        rootProject.layout.projectDirectory.asFile.absolutePath,
        "--work-dir",
        browserSmokeWorkDir.get().asFile.absolutePath,
        "--report-dir",
        browserSmokeReportDir.get().asFile.absolutePath,
        "--browser",
        browserSmokeBrowser.get(),
    )
}
