package net.distrocraft.playermod;

import net.distrocraft.playermod.agent.AgentConfig;
import net.distrocraft.playermod.agent.ComputeAgent;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class DistrocraftPlayerModFabric implements ClientModInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger("distrocraft_player");

    private ComputeAgent agent;
    private AgentConfig  config;
    private Path         configDir;
    private String       statusLine = "Distrocraft: idle";

    @Override
    public void onInitializeClient() {
        configDir = FabricLoader.getInstance().getConfigDir();
        config = AgentConfig.load(configDir);

        PayloadTypeRegistry.playS2C().register(DistrocraftPayload.TYPE, DistrocraftPayload.CODEC);

        ClientPlayNetworking.registerGlobalReceiver(DistrocraftPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                if (agent != null) agent.processMessage(payload.json());
            });
        });

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (!config.showHud) return;
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null) return;
            var font  = mc.font;
            int width = mc.getWindow().getGuiScaledWidth();
            String txt = statusLine;
            int x = width - font.width(txt) - 4;
            drawContext.drawString(font, txt, x, 4, 0xAAFFFFFF, false);
        });

        ClientSendMessageEvents.ALLOW_CHAT.register(msg -> {
            if (!msg.startsWith("/distro ")) return true;
            handleCommand(msg.substring(8).trim().split("\\s+"));
            return false;
        });

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (config.autoStart) {
                startAgent();
            } else {
                client.setScreen(new ConfirmScreen(
                    result -> {
                        if (result) startAgent();
                    },
                    Component.literal("Distrocraft"),
                    Component.literal("This server uses Distrocraft to distribute computation. "
                            + "Share your spare resources?\n\n"
                            + "You can change this later with /distro start and /distro stop, "
                            + "or set auto-start in the config screen.")
                ));
            }
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
            stopAgent());

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> stopAgent());

        LOGGER.info("Distrocraft Player Mod (Fabric) loaded");
    }

    private void handleCommand(String[] parts) {
        Minecraft mc = Minecraft.getInstance();
        if (parts.length == 0) return;
        switch (parts[0]) {
            case "start"  -> { startAgent(); chat(mc, "Agent starting\u2026"); }
            case "stop"   -> { stopAgent();  chat(mc, "Agent stopped."); }
            case "status" -> chat(mc, statusLine +
                    (agent != null ? " | done=" + agent.getTasksCompleted() +
                                    " fail=" + agent.getTasksFailed() : ""));
            case "set"    -> {
                if (parts.length < 3) { chat(mc, "Usage: /distro set <key> <value>"); return; }
                switch (parts[1]) {
                    case "threads" -> {
                        try {
                            config.threads = Integer.parseInt(parts[2]);
                            config.resources.put("threads", config.threads);
                            config.save(configDir);
                            chat(mc, "threads set to " + parts[2]);
                        } catch (NumberFormatException e) {
                            chat(mc, "Invalid value: " + parts[2]);
                        }
                    }
                    case "autoStart" -> {
                        config.autoStart = Boolean.parseBoolean(parts[2]);
                        config.save(configDir);
                        chat(mc, "autoStart set to " + config.autoStart);
                    }
                    case "showHud" -> {
                        config.showHud = Boolean.parseBoolean(parts[2]);
                        config.save(configDir);
                        chat(mc, "showHud set to " + config.showHud);
                    }
                    case "host" -> {
                        config.host = parts[2];
                        config.save(configDir);
                        chat(mc, "host set to " + config.host);
                    }
                    case "port" -> {
                        try {
                            config.port = Integer.parseInt(parts[2]);
                            config.save(configDir);
                            chat(mc, "port set to " + config.port);
                        } catch (NumberFormatException e) {
                            chat(mc, "Invalid port: " + parts[2]);
                        }
                    }
                    case "resources" -> {
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
                        if (config.resources.containsKey("threads")) {
                            config.threads = config.resources.get("threads");
                        }
                        config.save(configDir);
                        chat(mc, "Resources updated: " + config.resources);
                    }
                    default -> chat(mc, "Keys: threads, host, port, autoStart, showHud, resources");
                }
            }
            default -> chat(mc, "Commands: start, stop, status, set [threads|host|port|autoStart|showHud|resources]");
        }
    }

    private void startAgent() {
        stopAgent();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        String uuid = mc.player.getUUID().toString();
        String name = mc.player.getGameProfile().getName();
        agent = new ComputeAgent(uuid, config.threads, name, config.resources,
            json -> ClientPlayNetworking.send(new DistrocraftPayload(json)));
        agent.onStatusChange(s -> statusLine = "Distrocraft: " + s);
        agent.onError(e       -> LOGGER.warn("Agent error: {}", e));
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
