package net.distrocraft.plugin.util;

import org.bukkit.configuration.file.FileConfiguration;

public final class PluginConfig {

    public final int     port;
    public final boolean enabled;
    public final boolean requireInGame;
    public final int     maxClientsPerPlayer;
    public final boolean debugLogging;
    public final int     taskTimeoutSec;
    public final int     clientTimeoutSec;

    public PluginConfig(FileConfiguration cfg) {
        this.port                = cfg.getInt    ("port",                25566);
        this.enabled             = cfg.getBoolean("enabled",             true);
        this.requireInGame       = cfg.getBoolean("require-in-game",     true);
        this.maxClientsPerPlayer = cfg.getInt    ("max-clients-per-player", 1);
        this.debugLogging        = cfg.getBoolean("debug-logging",       false);
        this.taskTimeoutSec      = cfg.getInt    ("task-timeout-sec",    60);
        this.clientTimeoutSec    = cfg.getInt    ("client-timeout-sec",  30);
    }
}
