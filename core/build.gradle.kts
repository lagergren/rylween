plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    id("org.jetbrains.kotlin.plugin.scripting")
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
    api(kotlin("scripting-common"))
    api(kotlin("scripting-jvm"))
    testImplementation(kotlin("test"))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.jar {
    archiveBaseName = "rylween-core"
}
