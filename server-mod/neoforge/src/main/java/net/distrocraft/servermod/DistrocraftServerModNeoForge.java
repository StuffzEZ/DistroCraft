package net.distrocraft.servermod;

import net.distrocraft.servermod.coordinator.TaskCoordinator;
import net.distrocraft.servermod.util.CoordinatorConfig;
import net.distrocraft.servermod.util.DistroLogger;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.minecraft.server.level.ServerPlayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(DistrocraftServerModNeoForge.MOD_ID)
public class DistrocraftServerModNeoForge {

    public static final String MOD_ID = "distrocraft_server";
    private static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    private TaskCoordinator coordinator;

    public DistrocraftServerModNeoForge(IEventBus modBus) {
        DistroLogger.setBackend(new DistroLogger.Backend() {
            @Override public void info(String m)  { LOGGER.info(m);  }
            @Override public void warn(String m)  { LOGGER.warn(m);  }
            @Override public void error(String m) { LOGGER.error(m); }
            @Override public void debug(String m) { LOGGER.debug(m); }
        });

        DistroLogger.info("Distrocraft Server Mod (NeoForge) initialising");

        modBus.addListener(this::onRegisterPayloads);
        NeoForge.EVENT_BUS.addListener(this::onServerStarting);
        NeoForge.EVENT_BUS.addListener(this::onServerStopping);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogin);
        NeoForge.EVENT_BUS.addListener(this::onPlayerLogout);
    }

    private void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MOD_ID);
        registrar.playToServer(DistrocraftPayload.TYPE, DistrocraftPayload.CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                if (coordinator != null) {
                    coordinator.handlePayloadMessage(
                            context.player().getUUID().toString(), payload.json());
                }
            })
        );
    }

    private void onServerStarting(ServerStartingEvent event) {
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
    }

    private void onServerStopping(ServerStoppingEvent event) {
        if (coordinator != null) coordinator.stop();
    }

    private void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (coordinator == null) return;
        ServerPlayer player = (ServerPlayer) event.getEntity();
        String uuid = player.getUUID().toString();
        String name = player.getGameProfile().getName();
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        coordinator.registerPayloadClient(uuid, name, threads,
            json -> PacketDistributor.sendToPlayer(player, new DistrocraftPayload(json)));
    }

    private void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (coordinator == null) return;
        coordinator.unregisterPayloadClient(event.getEntity().getUUID().toString());
    }
}
