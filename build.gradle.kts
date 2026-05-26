plugins {
    id("net.neoforged.moddev") version "2.0.78" apply false
    id("fabric-loom") version "1.7.+" apply false
}

subprojects {
    apply(plugin = "java")

    configure<JavaPluginExtension> {
        toolchain.languageVersion = JavaLanguageVersion.of(21)
    }

    repositories {
        mavenCentral()
        maven("https://maven.neoforged.net/releases")
        maven("https://maven.fabricmc.net/")
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}
