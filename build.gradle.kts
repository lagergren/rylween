// Root build aggregation for shared versioning, verification wiring, and cleanup.
plugins {
    base
    alias(libs.plugins.kotlin.jvm) apply false
}

group = "org.rylween"
version =
    providers
        .gradleProperty("rylween.version")
        .get()

tasks.check {
    dependsOn(":core:check")
    dependsOn(":game:check")
    dependsOn(":tools:check")
    dependsOn(":terminal:check")
    dependsOn(":runtime:check")
    dependsOn(":runtime-web:check")
    dependsOn(":runtime-bitmap-web:check")
    dependsOn(":web-common:check")
}

tasks.clean {
    delete(subprojects.map { project -> project.layout.buildDirectory })
    delete(layout.projectDirectory.dir("build-logic/build"))
    // Kotlin/Gradle recreates these local compiler/session/error caches on demand.
    delete(layout.projectDirectory.dir(".kotlin"))
    delete(layout.projectDirectory.dir("build-logic/.kotlin"))
}
