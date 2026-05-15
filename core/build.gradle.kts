// Builds reusable DSL, compiler, compiled-bundle, and runtime model primitives.
plugins {
    `java-library`
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
    implementation(kotlin("reflect"))
}

tasks.jar {
    archiveBaseName = "rylween-core"
}
