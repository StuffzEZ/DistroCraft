package net.distrocraft.servermod;

import net.distrocraft.servermod.coordinator.TaskCoordinator;
import net.distrocraft.servermod.util.CoordinatorConfig;
import net.distrocraft.servermod.util.DistroLogger;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import java.util.Map;

public class DistrocraftServerModFabric implements DedicatedServerModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("distrocraft_server");
    private TaskCoordinator coordinator;

    @Override
    public void onInitializeServer() {
        DistroLogger.setBackend(new DistroLogger.Backend() {
            @Override public void info(String m)  { LOGGER.info(m);  }
            @Override public void warn(String m)  { LOGGER.warn(m);  }
            @Override public void error(String m) { LOGGER.error(m); }
            @Override public void debug(String m) { LOGGER.debug(m); }
        });

        DistroLogger.info("Distrocraft Server Mod (Fabric) initialising");

        PayloadTypeRegistry.playC2S().register(DistrocraftPayload.TYPE, DistrocraftPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(DistrocraftPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            String json = payload.json();
            context.server().execute(() -> {
                if (coordinator != null) {
                    coordinator.handlePayloadMessage(player.getUUID().toString(), json);
                }
            });
        });

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            CoordinatorConfig cfg = CoordinatorConfig.defaults();
            if (!cfg.enabled) { DistroLogger.info("Distrocraft disabled in config"); return; }
            coordinator = new TaskCoordinator();
            try {
                coordinator.start();
                coordinator.onTaskComplete((task, result) ->
                    LOGGER.info("Task {} completed: {}", task.getId(), result));
                coordinator.onTaskFail((task, err) ->
                    LOGGER.warn("Task {} failed: {}", task.getId(), err));
            } catch (Exception e) {
                LOGGER.error("Failed to start Distrocraft coordinator", e);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (coordinator != null) coordinator.stop();
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (coordinator == null) return;
            ServerPlayer player = handler.getPlayer();
            String uuid = player.getUUID().toString();
            String name = player.getGameProfile().getName();
            int threads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
            Map<String, Integer> caps = Map.of("threads", threads);
            coordinator.registerPayloadClient(uuid, name, threads, caps,
                json -> ServerPlayNetworking.send(player, new DistrocraftPayload(json)));
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (coordinator == null) return;
            coordinator.unregisterPayloadClient(handler.getPlayer().getUUID().toString());
        });
    }
}
