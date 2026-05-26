package net.distrocraft.playermod;

import net.distrocraft.playermod.agent.AgentConfig;
import net.distrocraft.playermod.agent.ComputeAgent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientChatEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

@Mod(DistrocraftPlayerModNeoForge.MOD_ID)
public class DistrocraftPlayerModNeoForge {

    public static final String MOD_ID = "distrocraft_player";
    private static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    private ComputeAgent agent;
    private AgentConfig  config;
    private Path         configDir;
    private String       statusLine = "Distrocraft: idle";

    public DistrocraftPlayerModNeoForge(IEventBus modBus, ModContainer container) {
        configDir = container.getModInfo().getOwningFile()
                .getFile().getFilePath().getParent().resolve("config");
        config = AgentConfig.load(configDir);

        modBus.addListener(this::onRegisterPayloads);
        NeoForge.EVENT_BUS.addListener(this::onClientLogin);
        NeoForge.EVENT_BUS.addListener(this::onClientLogout);
        NeoForge.EVENT_BUS.addListener(this::onChat);
        NeoForge.EVENT_BUS.addListener(this::onRenderHud);

        LOGGER.info("Distrocraft Player Mod (NeoForge) loaded");
    }

    private void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MOD_ID);
        registrar.playToClient(DistrocraftPayload.TYPE, DistrocraftPayload.CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                if (agent != null) agent.processMessage(payload.json());
            })
        );
    }

    private void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        startAgent();
    }

    private void onClientLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        stopAgent();
    }

    private void onChat(ClientChatEvent event) {
        String msg = event.getMessage();
        if (!msg.startsWith("/distro ")) return;
        event.setCanceled(true);

        String[] parts = msg.substring(8).trim().split("\\s+");
        Minecraft mc = Minecraft.getInstance();

        switch (parts[0]) {
            case "start"  -> { startAgent(); chat(mc, "Distrocraft agent starting\u2026"); }
            case "stop"   -> { stopAgent();  chat(mc, "Distrocraft agent stopped."); }
            case "status" -> chat(mc, statusLine +
                    (agent != null ? " | done=" + agent.getTasksCompleted() +
                                    " fail=" + agent.getTasksFailed() : ""));
            case "set" -> {
                if (parts.length < 3) { chat(mc, "Usage: /distro set <key> <value> or /distro set resources <key>=<value> ..."); return; }
                if ("threads".equals(parts[1]) && parts.length >= 3) {
                    try {
                        config.threads = Integer.parseInt(parts[2]);
                        config.resources.put("threads", config.threads);
                        config.save(configDir);
                        chat(mc, "threads set to " + parts[2]);
                    } catch (NumberFormatException e) {
                        chat(mc, "Invalid value: " + parts[2]);
                    }
                } else if ("resources".equals(parts[1])) {
                    for (int i = 2; i < parts.length; i++) {
                        String[] kv = parts[i].split("=", 2);
                        if (kv.length == 2) {
                            try {
                                config.resources.put(kv[0], Integer.parseInt(kv[1]));
                            } catch (NumberFormatException e) {
                                chat(mc, "Invalid value for " + kv[0] + ": " + kv[1]);
                            }
                        } else {
                            chat(mc, "Usage: <key>=<value>, got: " + parts[i]);
                        }
                    }
                    // Keep threads field in sync
                    if (config.resources.containsKey("threads")) {
                        config.threads = config.resources.get("threads");
                    }
                    config.save(configDir);
                    chat(mc, "Resources updated: " + config.resources);
                } else {
                    chat(mc, "Usage: /distro set threads <value> or /distro set resources <key>=<value> ...");
                }
            }
            default -> chat(mc, "Commands: start, stop, status, set [threads|resources]");
        }
    }

    private void onRenderHud(RenderGuiLayerEvent.Post event) {
        if (!config.showHud) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;
        var font   = mc.font;
        int width  = mc.getWindow().getGuiScaledWidth();
        String txt = statusLine;
        int x = width - font.width(txt) - 4;
        event.getGuiGraphics().drawString(font, txt, x, 4, 0xAAFFFFFF, false);
    }

    private void startAgent() {
        stopAgent();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String uuid = mc.player.getUUID().toString();
        String name = mc.player.getGameProfile().getName();
        agent = new ComputeAgent(uuid, config.threads, name, config.resources,
            json -> PacketDistributor.sendToServer(new DistrocraftPayload(json)));
        agent.onStatusChange(s -> statusLine = "Distrocraft: " + s);
        agent.onError(e       -> LOGGER.warn("Distrocraft agent error: {}", e));
        agent.start();
    }

    private void stopAgent() {
        if (agent != null) { agent.stop(); agent = null; }
        statusLine = "Distrocraft: idle";
    }

    private void chat(Minecraft mc, String text) {
        if (mc.player != null)
            mc.player.sendSystemMessage(Component.literal("[Distrocraft] " + text));
    }
}
