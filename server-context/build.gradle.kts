import org.gradle.api.tasks.compile.JavaCompile

plugins {
    id("net.weavemc.gradle") version "1.3.3"
}

group = "net.weavemc.mods"
version = "1.0.1"

weave {
    configure {
        name = "Server Context"
        modId = "server-context"
        entryPoints = listOf("net.weavemc.mods.servercontext.ServerContext")
        dependencies = listOf("minecraft", "java")
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
    implementation("net.weavemc.api:api:1.3.3")
    implementation("net.weavemc.api:api-v1_8:1.3.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation(files(".gradle/weave/client-mcp-named.jar"))
    testRuntimeOnly("com.google.guava:guava:17.0")
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

tasks.register<JavaExec>("verifyDestination") {
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("net.weavemc.mods.servercontext.DestinationProbe")
    args(providers.gradleProperty("destinationHosts").orElse("na.zonix.us").get().split(","))
}
