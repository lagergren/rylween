// Builds the local Gradle task and convention plugin code used by project build scripts.
plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("org.jlleitschuh.gradle:ktlint-gradle:14.2.0")
}
