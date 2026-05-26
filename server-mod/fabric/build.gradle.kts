plugins {
    java
    id("fabric-loom")
}

dependencies {
    minecraft("com.mojang:minecraft:1.21.1")
    mappings(loom.officialMojangMappings())
    modImplementation("net.fabricmc:fabric-loader:0.16.5")
    modImplementation("net.fabricmc.fabric-api:fabric-api:0.102.0+1.21.1")
    implementation(project(":server-mod:common"))
    include(project(":server-mod:common"))
}

tasks.jar {
    from(project(":server-mod:common").sourceSets.main.get().output)
    archiveClassifier = "fabric"
}
