// Builds the compiled bundle artifact emitter and its checks.

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

val mainSourceSet: SourceSet = sourceSets.main.get()
val externalBundleArtifact: Provider<RegularFile> = rootProject.layout.buildDirectory.file("compiled/rylween-bundle.json")
val externalBundleInspection: Provider<RegularFile> = rootProject.layout.buildDirectory.file("compiled/rylween-bundle.inspect.txt")
val externalBundleArtifactPath: String = externalBundleArtifact.get().asFile.path
val externalBundleInspectionPath: String = externalBundleInspection.get().asFile.path

val emitExternalCompiledBundle: TaskProvider<JavaExec> =
    tasks.register<JavaExec>("emitExternalCompiledBundle") {
        group = "verification"
        description = "Emits the deterministic JSON compiled bundle artifact."

        dependsOn(tasks.named(mainSourceSet.classesTaskName))
        classpath = mainSourceSet.runtimeClasspath
        mainClass.set("rylween.tools.RylweenExternalBundleArtifactMain")
        args(externalBundleArtifactPath)
        inputs.files(mainSourceSet.runtimeClasspath)
        outputs.file(externalBundleArtifact)
    }

val checkExternalCompiledBundle: TaskProvider<JavaExec> =
    tasks.register<JavaExec>("checkExternalCompiledBundle") {
        group = "verification"
        description = "Validates the deterministic JSON compiled bundle artifact."

        dependsOn(emitExternalCompiledBundle)
        classpath = mainSourceSet.runtimeClasspath
        mainClass.set("rylween.tools.RylweenExternalBundleCheckMain")
        args(externalBundleArtifactPath)
        inputs.file(externalBundleArtifact)
    }

val inspectExternalCompiledBundle: TaskProvider<JavaExec> =
    tasks.register<JavaExec>("inspectExternalCompiledBundle") {
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
    dependsOn(checkExternalCompiledBundle)
    dependsOn(inspectExternalCompiledBundle)
}
