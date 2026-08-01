import org.gradle.api.tasks.compile.JavaCompile

plugins {
    id("net.weavemc.gradle") version "1.3.3"
}

group = "com.spawnprot.mod"
version = "1.0.1"

weave {
    configure {
        name = "SpawnProt"
        modId = "spawnprot"
        entryPoints = listOf("com.spawnprot.mod.SpawnProtMod")
        dependencies = listOf("minecraft", "java", "hud-editor")
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
    compileOnly("net.weavemc.mods:hud-editor:1.0.1")
    testImplementation("junit:junit:4.13.2")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(8))
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.test {
    useJUnit()
}
