// Root build aggregation for shared versioning, verification wiring, and cleanup.
plugins {
    base
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.gradle.versions)
    alias(libs.plugins.version.catalog.update)
}

group = "org.rylween"
version =
    providers
        .gradleProperty("rylween.version")
        .get()

tasks.check {
    dependsOn("checkDependencyUpdates")
    dependsOn(":core:check")
    dependsOn(":game:check")
    dependsOn(":tools:check")
    dependsOn(":terminal:check")
    dependsOn(":runtime:check")
    dependsOn(":runtime-web:check")
    dependsOn(":runtime-bitmap-web:check")
    dependsOn(":web-common:check")
}

tasks.register<Exec>("checkDependencyUpdates") {
    group = "versioning"
    description =
        "Reports dependency/plugin updates declared through gradle/libs.versions.toml. " +
            "Run ./gradlew updateDependencyCatalog --console=plain to apply catalog updates."
    commandLine(
        layout.projectDirectory.file("gradlew").asFile.absolutePath,
        "dependencyUpdates",
        "--no-parallel",
        "--no-configuration-cache",
        "--console=plain",
    )
    doLast {
        logger.warn(
            "Dependency update report complete. To rewrite gradle/libs.versions.toml, run: " +
                "./gradlew updateDependencyCatalog --console=plain",
        )
    }
}

tasks.register<Exec>("updateDependencyCatalog") {
    group = "versioning"
    description = "Updates gradle/libs.versions.toml with available dependency/plugin versions."
    commandLine(
        layout.projectDirectory.file("gradlew").asFile.absolutePath,
        "versionCatalogUpdate",
        "--no-parallel",
        "--no-configuration-cache",
        "--console=plain",
    )
}

tasks.clean {
    delete(subprojects.map { project -> project.layout.buildDirectory })
    delete(layout.projectDirectory.dir("build-logic/build"))
    // Kotlin/Gradle recreates these local compiler/session/error caches on demand.
    delete(layout.projectDirectory.dir(".kotlin"))
    delete(layout.projectDirectory.dir("build-logic/.kotlin"))
}
