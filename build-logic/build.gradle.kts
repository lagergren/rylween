// Builds the local Gradle task and convention plugin code used by project build scripts.
plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation(libs.ktlint.gradle)
}
