plugins {
    java
    id("net.neoforged.moddev") version "2.0.78" apply false
    id("fabric-loom") version "1.7.+" apply false
}

subprojects {
    apply(plugin = "java")

    java {
        toolchain.languageVersion = JavaLanguageVersion.of(21)
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    repositories {
        mavenCentral()
        maven("https://maven.neoforged.net/releases")
        maven("https://maven.fabricmc.net/")
    }
}
