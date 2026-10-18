pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://gitlab.com/api/v4/projects/80566527/packages/maven")
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "spawnprot-mod"

includeBuild("../lunar-mod-api") {
    dependencySubstitution {
        substitute(module("net.weavemc.mods:lunar-mod-api")).using(project(":"))
    }
}

includeBuild("../server-context") {
    dependencySubstitution {
        substitute(module("net.weavemc.mods:server-context")).using(project(":"))
    }
}
