import org.gradle.api.tasks.compile.JavaCompile

plugins {
    id("net.weavemc.gradle") version "1.3.3"
}

// Vendored from https://github.com/adoringlol/Lunar-tiles-lib (commit 5c8b9763), used with the author's permission.
group = "net.weavemc.mods"
version = "1.0.1"

weave {
    configure {
        name = "Lunar Mod API"
        modId = "lunar-mod-api"
        entryPoints = listOf("com.weave.lunarmods.internal.LunarModsInitializer")
        dependencies = listOf("minecraft", "java")
        mcpMappings()
    }
    version("1.8.9")
}

repositories {
    mavenCentral()
    maven("https://gitlab.com/api/v4/projects/80566527/packages/maven")
}

dependencies {
    implementation("net.weavemc.api:api:1.3.3")
    implementation("net.weavemc.api:api-v1_8:1.3.3")
    testImplementation("junit:junit:4.13.2")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(8))
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}
