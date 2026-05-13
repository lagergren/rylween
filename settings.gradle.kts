pluginManagement {
    includeBuild("build-logic")
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "rylween"

include("runtime")
include("runtime-web")
include("runtime-bitmap-web")
include("web-common")
include("core")
include("game")
include("tools")
include("terminal")
