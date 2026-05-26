pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.neoforged.net/releases")
        maven("https://maven.fabricmc.net/")
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

rootProject.name = "distrocraft"

include(
    ":server-mod:common",
    ":server-mod:fabric",
    ":server-mod:neoforge",
    ":player-mod:common",
    ":player-mod:fabric",
    ":player-mod:neoforge",
    ":player-app",
    ":server-plugin"
)
