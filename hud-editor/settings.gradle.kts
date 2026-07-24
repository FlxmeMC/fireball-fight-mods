pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://gitlab.com/api/v4/projects/80566527/packages/maven")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "hud-editor"
