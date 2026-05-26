package net.distrocraft.servermod.util;

public final class CoordinatorConfig {

    public int    port           = 25566;
    public boolean enabled       = true;
    public boolean requireInGame = true;
    public int    maxClientsPerPlayer = 1;
    public boolean debugLogging   = false;
    public int    taskTimeoutSec  = 60;
    public int    clientTimeoutSec = 30;

    public static CoordinatorConfig defaults() {
        return new CoordinatorConfig();
    }

    public static CoordinatorConfig fromProperties(java.util.Properties p) {
        CoordinatorConfig c = new CoordinatorConfig();
        c.port              = Integer.parseInt(p.getProperty("port",           String.valueOf(c.port)));
        c.enabled           = Boolean.parseBoolean(p.getProperty("enabled",   String.valueOf(c.enabled)));
        c.requireInGame     = Boolean.parseBoolean(p.getProperty("requireInGame", String.valueOf(c.requireInGame)));
        c.maxClientsPerPlayer = Integer.parseInt(p.getProperty("maxClientsPerPlayer", String.valueOf(c.maxClientsPerPlayer)));
        c.debugLogging      = Boolean.parseBoolean(p.getProperty("debugLogging", String.valueOf(c.debugLogging)));
        c.taskTimeoutSec    = Integer.parseInt(p.getProperty("taskTimeoutSec", String.valueOf(c.taskTimeoutSec)));
        c.clientTimeoutSec  = Integer.parseInt(p.getProperty("clientTimeoutSec", String.valueOf(c.clientTimeoutSec)));
        return c;
    }

    public java.util.Properties toProperties() {
        java.util.Properties p = new java.util.Properties();
        p.setProperty("port",                String.valueOf(port));
        p.setProperty("enabled",             String.valueOf(enabled));
        p.setProperty("requireInGame",       String.valueOf(requireInGame));
        p.setProperty("maxClientsPerPlayer", String.valueOf(maxClientsPerPlayer));
        p.setProperty("debugLogging",        String.valueOf(debugLogging));
        p.setProperty("taskTimeoutSec",      String.valueOf(taskTimeoutSec));
        p.setProperty("clientTimeoutSec",    String.valueOf(clientTimeoutSec));
        return p;
    }
}
