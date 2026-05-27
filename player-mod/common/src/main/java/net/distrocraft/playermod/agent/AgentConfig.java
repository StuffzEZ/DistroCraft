package net.distrocraft.playermod.agent;

import java.io.*;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

public final class AgentConfig {

    public String               host       = "localhost";
    public int                  port       = 25566;
    public int                  threads    = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
    public Map<String, Integer> resources  = new LinkedHashMap<>();
    public boolean              autoStart  = false;
    public boolean              showHud    = true;

    private static final String FILENAME = "distrocraft-client.properties";

    public static AgentConfig load(Path configDir) {
        AgentConfig cfg = new AgentConfig();
        Path file = configDir.resolve(FILENAME);
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                Properties p = new Properties();
                p.load(in);
                cfg.host      = p.getProperty("host",      cfg.host);
                cfg.port      = Integer.parseInt(p.getProperty("port",    String.valueOf(cfg.port)));
                cfg.threads   = Integer.parseInt(p.getProperty("threads", String.valueOf(cfg.threads)));
                cfg.autoStart = Boolean.parseBoolean(p.getProperty("autoStart", String.valueOf(cfg.autoStart)));
                cfg.showHud   = Boolean.parseBoolean(p.getProperty("showHud",   String.valueOf(cfg.showHud)));
                // Load per-resource entries (resource.<name>=<value>)
                for (String key : p.stringPropertyNames()) {
                    if (key.startsWith("resource.")) {
                        String resName = key.substring(9);
                        try {
                            cfg.resources.put(resName, Integer.parseInt(p.getProperty(key)));
                        } catch (NumberFormatException ignored) {}
                    }
                }
            } catch (IOException | NumberFormatException e) {
                System.err.println("[Distrocraft] Failed to load config: " + e.getMessage());
            }
        }
        // Ensure threads is always in resources
        if (!cfg.resources.containsKey("threads")) {
            cfg.resources.put("threads", cfg.threads);
        }
        return cfg;
    }

    public void save(Path configDir) {
        try {
            Files.createDirectories(configDir);
            Path file = configDir.resolve(FILENAME);
            Properties p = new Properties();
            p.setProperty("host",      host);
            p.setProperty("port",      String.valueOf(port));
            p.setProperty("threads",   String.valueOf(threads));
            p.setProperty("autoStart", String.valueOf(autoStart));
            p.setProperty("showHud",   String.valueOf(showHud));
            for (Map.Entry<String, Integer> e : resources.entrySet()) {
                p.setProperty("resource." + e.getKey(), String.valueOf(e.getValue()));
            }
            try (OutputStream out = Files.newOutputStream(file)) {
                p.store(out, "Distrocraft Client Configuration");
            }
        } catch (IOException e) {
            System.err.println("[Distrocraft] Failed to save config: " + e.getMessage());
        }
    }

    public static AgentConfig defaults() {
        return new AgentConfig();
    }
}
