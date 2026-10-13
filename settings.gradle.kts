pluginManagement {
    includeBuild("build-logic")
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // The one version outside gradle/libs.versions.toml: settings plugins resolve before the catalog exists.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "rylween"

include("runtime")
include("runtime-web")
include("runtime-bitmap-web")
project(":runtime").projectDir = file("runtimes/runtime")
project(":runtime-web").projectDir = file("runtimes/runtime-web")
project(":runtime-bitmap-web").projectDir = file("runtimes/runtime-bitmap-web")
include("web-common")
include("core")
include("game")
include("tools")
include("terminal")
project(":web-common").projectDir = file("support/web-common")
project(":tools").projectDir = file("support/tools")
project(":terminal").projectDir = file("support/terminal")
