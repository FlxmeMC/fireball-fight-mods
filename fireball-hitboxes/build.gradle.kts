import org.gradle.api.tasks.compile.JavaCompile

plugins {
    id("net.weavemc.gradle") version "1.3.3"
}

group = "net.weavemc.mods"
version = "1.0.0"

weave {
    configure {
        name = "Fireball Hitboxes"
        modId = "fireball-hitboxes"
        entryPoints = listOf("net.weavemc.mods.fireballhitboxes.FireballHitboxesMod")
        hooks = listOf("net.weavemc.mods.fireballhitboxes.FireballRenderHook")
        dependencies = listOf("minecraft", "java", "server-context")
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
    implementation("org.ow2.asm:asm-tree:9.9.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation(files(".gradle/weave/client-mcp-named.jar"))
    testRuntimeOnly("com.google.guava:guava:17.0")
    testRuntimeOnly("org.apache.logging.log4j:log4j-api:2.0-beta9")
    testRuntimeOnly("org.apache.logging.log4j:log4j-core:2.0-beta9")
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(8))

tasks.withType<JavaCompile>().configureEach {
    // ASM uses modern Deprecated metadata; Java 8 cannot resolve its forRemoval member.
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-classfile", "-Werror"))
}

tasks.test {
    useJUnit()
}
