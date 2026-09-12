plugins {
    id("net.weavemc.gradle") version "1.3.3"
}

group = "net.weavemc.disclosure"
version = "1.0.0"

weave {
    configure {
        name = "Weave Disclosure"
        modId = "weave-disclosure"
        entryPoints = listOf("net.weavemc.disclosure.WeaveDisclosure")
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
}

java.toolchain.languageVersion.set(JavaLanguageVersion.of(8))
