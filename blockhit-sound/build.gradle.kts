import org.gradle.api.tasks.compile.JavaCompile

plugins {
    id("net.weavemc.gradle") version "1.3.3"
}

group = "net.weavemc.mods.blockhitsound"
version = "1.0.0"

weave {
    configure {
        name = "Blockhit Sound"
        modId = "blockhit-sound"
        entryPoints = listOf("net.weavemc.mods.blockhitsound.BlockhitSoundMod")
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
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(8))
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}
