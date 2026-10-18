import org.gradle.api.tasks.compile.JavaCompile

plugins {
    id("net.weavemc.gradle") version "1.3.3"
}

group = "net.weavemc.mods.timer"
version = "1.2.0"

weave {
    configure {
        name = "Timer"
        modId = "timer"
        entryPoints = listOf("net.weavemc.mods.timer.TimerMod")
        mixinConfigs = listOf("timer.mixins.json")
        dependencies = listOf("minecraft", "java", "server-context", "lunar-mod-api")
        mcpMappings()
    }
    version("1.8.9")
}

repositories {
    mavenCentral()
    maven("https://repo.spongepowered.org/maven/")
    maven("https://gitlab.com/api/v4/projects/80566527/packages/maven")
}

dependencies {
    compileOnly("net.weavemc.mods:server-context:1.0.1")
    testImplementation("net.weavemc.mods:server-context:1.0.1")
    implementation("net.weavemc.api:api:1.3.3")
    implementation("net.weavemc.api:api-v1_8:1.3.3")
    compileOnly("net.weavemc.mods:lunar-mod-api:1.0.0")
    testImplementation("net.weavemc.mods:lunar-mod-api:1.0.0")
    compileOnly("org.spongepowered:mixin:0.8.5")
    testImplementation("junit:junit:4.13.2")
    testImplementation(files(".gradle/weave/client-mcp-named.jar"))
    testRuntimeOnly("com.google.guava:guava:17.0")
    testRuntimeOnly("com.google.code.gson:gson:2.2.4")
    testRuntimeOnly("org.apache.logging.log4j:log4j-api:2.0-beta9")
    testRuntimeOnly("org.apache.logging.log4j:log4j-core:2.0-beta9")
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(8))

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
}

tasks.test {
    useJUnit()
}
