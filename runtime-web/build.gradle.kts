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
    implementation(project(":runtime"))
    implementation(project(":terminal"))
    runtimeOnly(project(":game"))
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

val runtimeWebJar: TaskProvider<Jar> = tasks.named<Jar>("jar")
val runtimeWebJarFile: Provider<RegularFile> = runtimeWebJar.flatMap { it.archiveFile }
val runtimeWebClasspathConfig: Configuration = configurations.runtimeClasspath.get()
val runtimeWebJars: FileCollection =
    runtimeWebClasspathConfig.filter { file -> file.isFile && file.extension == "jar" }

tasks.jar {
    archiveBaseName.set("rylween-runtime-web")
    manifest {
        attributes["Main-Class"] = "rylween.web.RylweenWebRuntimeMainKt"
    }
}

val runtimeWebStartScripts: TaskProvider<CreateStartScripts> by tasks.registering(CreateStartScripts::class) {
    group = "application"
    description = "Generates direct start scripts for the web runtime."

    dependsOn(runtimeWebJar)
    applicationName = "rylween-web"
    mainClass.set("rylween.web.RylweenWebRuntimeMainKt")
    outputDir =
        layout.buildDirectory
            .dir("scripts/runtimeWebApp")
            .get()
            .asFile
    classpath = files(runtimeWebJarFile) + runtimeWebJars
}

val installRuntimeWeb: TaskProvider<Sync> by tasks.registering(Sync::class) {
    group = "application"
    description = "Installs the web runtime."

    dependsOn(runtimeWebStartScripts)
    into(layout.buildDirectory.dir("install/rylween-web"))
    from(runtimeWebStartScripts) {
        into("bin")
        filePermissions {
            unix("rwxr-xr-x")
        }
    }
    from(files(runtimeWebJarFile) + runtimeWebJars) {
        into("lib")
    }
}
