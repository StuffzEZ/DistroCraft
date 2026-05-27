package net.distrocraft.playermod.config;

import net.distrocraft.playermod.agent.AgentConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class DistrocraftConfigScreen extends Screen {

    private final Screen parent;
    private AgentConfig config;
    private final Path configDir;

    private EditBox hostField;
    private EditBox portField;
    private EditBox threadsField;
    private Button autoStartBtn;
    private Button showHudBtn;
    private Button saveBtn;

    private boolean autoStart;
    private boolean showHud;

    protected DistrocraftConfigScreen(Screen parent) {
        super(Component.literal("Distrocraft Config"));
        this.parent = parent;
        this.configDir = FabricLoader.getInstance().getConfigDir();
        this.config = AgentConfig.load(configDir);
        this.autoStart = config.autoStart;
        this.showHud = config.showHud;
    }

    @Override
    protected void init() {
        int mid = width / 2;
        int y = 40;

        addRenderableWidget(Component.literal("Host:"), mid - 100, y, 200, 20);
        hostField = new EditBox(font, mid - 100, y + 12, 200, 20, Component.literal("Host"));
        hostField.setValue(config.host);
        addRenderableWidget(hostField);
        y += 40;

        addRenderableWidget(Component.literal("Port:"), mid - 100, y, 200, 20);
        portField = new EditBox(font, mid - 100, y + 12, 200, 20, Component.literal("Port"));
        portField.setValue(String.valueOf(config.port));
        addRenderableWidget(portField);
        y += 40;

        addRenderableWidget(Component.literal("Threads:"), mid - 100, y, 200, 20);
        threadsField = new EditBox(font, mid - 100, y + 12, 200, 20, Component.literal("Threads"));
        threadsField.setValue(String.valueOf(config.threads));
        addRenderableWidget(threadsField);
        y += 40;

        autoStartBtn = Button.builder(
                Component.literal("Auto-start: " + (autoStart ? "ON" : "OFF")),
                b -> { autoStart = !autoStart; b.setMessage(Component.literal("Auto-start: " + (autoStart ? "ON" : "OFF"))); }
        ).bounds(mid - 100, y, 200, 20).build();
        addRenderableWidget(autoStartBtn);
        y += 28;

        showHudBtn = Button.builder(
                Component.literal("Show HUD: " + (showHud ? "ON" : "OFF")),
                b -> { showHud = !showHud; b.setMessage(Component.literal("Show HUD: " + (showHud ? "ON" : "OFF"))); }
        ).bounds(mid - 100, y, 200, 20).build();
        addRenderableWidget(showHudBtn);
        y += 36;

        saveBtn = Button.builder(
                Component.literal("Save & Close"),
                b -> saveAndClose()
        ).bounds(mid - 50, y, 100, 20).build();
        addRenderableWidget(saveBtn);
    }

    private void addRenderableWidget(Component label, int x, int y, int w, int h) {
        addRenderableWidget(new net.minecraft.client.gui.components.StringWidget(x, y, w, h, label, font));
    }

    private void saveAndClose() {
        config.host = hostField.getValue();
        try {
            config.port = Integer.parseInt(portField.getValue());
        } catch (NumberFormatException ignored) {}
        try {
            config.threads = Math.max(1, Integer.parseInt(threadsField.getValue()));
        } catch (NumberFormatException ignored) {}
        config.autoStart = autoStart;
        config.showHud = showHud;
        config.resources.put("threads", config.threads);
        config.save(configDir);
        this.config = AgentConfig.load(configDir);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.sendSystemMessage(
                    Component.literal("[Distrocraft] Configuration saved"));
        }
        onClose();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        renderBackground(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(font, title, width / 2, 20, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, delta);
    }
}
