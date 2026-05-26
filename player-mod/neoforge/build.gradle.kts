plugins {
    java
    id("net.neoforged.moddev")
}

neoForge { version = "21.1.86" }

dependencies {
    implementation(project(":player-mod:common"))
    compileOnly("com.google.code.gson:gson:2.10.1")
}

tasks.jar {
    from(project(":player-mod:common").sourceSets.main.get().output)
    archiveClassifier = "neoforge"
}
