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
