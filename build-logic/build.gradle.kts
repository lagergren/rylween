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
