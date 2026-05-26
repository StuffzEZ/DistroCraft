plugins {
    java
    id("io.papermc.paperweight.userdev") version "1.7.1"
}

group   = "net.distrocraft"
version = "1.0.1"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    paperweight.paperDevBundle("1.21.1-R0.1-SNAPSHOT")
    implementation("com.google.code.gson:gson:2.10.1")
}

tasks.assemble {
    dependsOn(tasks.reobfJar)
}

tasks.jar {
    from(configurations.runtimeClasspath.get()
        .filter { it.name.contains("gson") }
        .map { zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveFileName = "distrocraft-server-plugin.jar"
}
