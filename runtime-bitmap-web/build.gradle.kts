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
val generateBitmapWebClientJs: TaskProvider<Copy> by tasks.registering(Copy::class) {
    group = "generation"
    description = "Copies the authored bitmap web TypeScript client to the served JavaScript resource."

    from(layout.projectDirectory.file("src/main/resources/bitmap-web/client.ts")) {
        rename { "client.js" }
    }
    into(bitmapWebGeneratedResources.map { directory -> directory.dir("bitmap-web") })
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
