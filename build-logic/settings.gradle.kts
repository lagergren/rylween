rootProject.name = "build-logic"

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage") // TODO: Incubating, since time immemorial...
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    // Share the main build's catalog so build-logic declares no versions of its own.
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
