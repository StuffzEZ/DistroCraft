package net.distrocraft.plugin;

import net.distrocraft.plugin.command.DistrocraftCommand;
import net.distrocraft.plugin.coordinator.PluginCoordinator;
import net.distrocraft.plugin.util.PluginConfig;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class DistrocraftPlugin extends JavaPlugin {

    private PluginCoordinator coordinator;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        PluginConfig cfg = new PluginConfig(getConfig());

        if (!cfg.enabled) {
            getLogger().info("[Distrocraft] Disabled in config.yml \u2014 skipping startup.");
            return;
        }

        coordinator = new PluginCoordinator(cfg.port, getLogger());

        coordinator.onComplete((task, result) -> {
            if (cfg.debugLogging)
                getLogger().info("[Distrocraft] Task " + task.getId() + " completed: " + result);
        });
        coordinator.onFail((task, err) ->
            getLogger().warning("[Distrocraft] Task " + task.getId() + " failed: " + err)
        );

        try {
            coordinator.start();
        } catch (Exception e) {
            getLogger().severe("[Distrocraft] Failed to start coordinator: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        DistrocraftCommand cmdHandler = new DistrocraftCommand(coordinator);
        PluginCommand cmd = getCommand("distrocraft");
        if (cmd != null) {
            cmd.setExecutor(cmdHandler);
            cmd.setTabCompleter(cmdHandler);
        }

        getLogger().info("[Distrocraft] Plugin enabled \u2014 coordinator on port " + cfg.port);
    }

    @Override
    public void onDisable() {
        if (coordinator != null) {
            coordinator.stop();
            coordinator = null;
        }
        getLogger().info("[Distrocraft] Plugin disabled.");
    }

    public PluginCoordinator getCoordinator() {
        return coordinator;
    }
}
