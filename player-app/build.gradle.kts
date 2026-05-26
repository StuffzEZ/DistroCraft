plugins {
    java
    application
}

group = "net.distrocraft"
version = "1.0.0"

java {
    toolchain.languageVersion = JavaLanguageVersion.of(21)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.10.1")
}

application {
    mainClass = "net.distrocraft.app.DistrocraftApp"
}

tasks.jar {
    manifest { attributes["Main-Class"] = "net.distrocraft.app.DistrocraftApp" }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveFileName = "distrocraft-client-app.jar"
}
