package net.distrocraft.app;

import net.distrocraft.app.agent.StandaloneAgent;
import net.distrocraft.app.ui.AppGui;

import javax.swing.*;

public final class DistrocraftApp {

    public static void main(String[] args) throws Exception {
        if (args.length >= 1) {
            runCli(args);
        } else {
            runGui();
        }
    }

    private static void runGui() {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            new AppGui().setVisible(true);
        });
    }

    private static void runCli(String[] args) throws InterruptedException {
        String host    = args[0];
        int    port    = args.length > 1 ? Integer.parseInt(args[1]) : 25566;
        int    threads = args.length > 2 ? Integer.parseInt(args[2])
                : Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        String label   = args.length > 3 ? args[3] : System.getProperty("user.name", "distrocraft");

        System.out.println("Distrocraft client (headless)");
        System.out.println("Host=" + host + " Port=" + port + " Threads=" + threads + " Label=" + label);

        StandaloneAgent agent = new StandaloneAgent(host, port, threads, label);
        agent.onStatus(s -> System.out.println("[Status] " + s));
        agent.onLog(s    -> System.out.println("[Log]    " + s));
        agent.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down\u2026");
            agent.stop();
        }));

        while (true) {
            Thread.sleep(10_000);
            System.out.printf("[Stats] connected=%b done=%d failed=%d%n",
                    agent.isConnected(), agent.getTasksDone(), agent.getTasksFailed());
        }
    }
}
