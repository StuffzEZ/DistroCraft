package net.distrocraft.servermod.util;

public final class DistroLogger {

    private static Backend backend = new ConsoleBackend();

    public interface Backend {
        void info(String msg);
        void warn(String msg);
        void error(String msg);
        void debug(String msg);
    }

    public static void setBackend(Backend b) { backend = b; }

    public static void info(String msg)  { backend.info(msg); }
    public static void warn(String msg)  { backend.warn(msg); }
    public static void error(String msg) { backend.error(msg); }
    public static void debug(String msg) { backend.debug(msg); }

    private static final class ConsoleBackend implements Backend {
        @Override public void info(String m)  { System.out.println("[Distrocraft/INFO]  " + m); }
        @Override public void warn(String m)  { System.out.println("[Distrocraft/WARN]  " + m); }
        @Override public void error(String m) { System.err.println("[Distrocraft/ERROR] " + m); }
        @Override public void debug(String m) { /* suppress by default */ }
    }
}
