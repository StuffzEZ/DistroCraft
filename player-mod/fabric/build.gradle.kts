plugins {
    java
    id("fabric-loom")
}

repositories {
    maven { url = uri("https://maven.terraformersmc.com/releases/") }
}

dependencies {
    minecraft("com.mojang:minecraft:1.21.1")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:0.16.5")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.102.0+1.21.1")
    modImplementation("com.terraformersmc:modmenu:11.0.1")
    implementation(project(":player-mod:common"))
    include(project(":player-mod:common"))
}

tasks.jar {
    from(project(":player-mod:common").sourceSets.main.get().output)
    archiveClassifier = "fabric"
}
