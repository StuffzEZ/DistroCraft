package net.distrocraft.app.agent;

import com.google.gson.JsonObject;
import net.distrocraft.app.network.AppProtocol;
import net.distrocraft.app.task.AppTaskExecutor;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class StandaloneAgent {

    private final String host;
    private final int    port;
    private final String clientId;
    private final int    threads;
    private final String label;

    private Socket         socket;
    private BufferedReader reader;
    private BufferedWriter writer;

    private volatile boolean running   = false;
    private volatile boolean connected = false;

    private final ExecutorService taskPool;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "dc-scheduler"); t.setDaemon(true); return t;
            });

    private Consumer<String> onStatus = s -> {};
    private Consumer<String> onLog    = s -> {};

    private volatile int done = 0, failed = 0;

    public StandaloneAgent(String host, int port, int threads, String label) {
        this.host     = host;
        this.port     = port;
        this.threads  = threads;
        this.label    = label;
        this.clientId = UUID.randomUUID().toString();
        this.taskPool = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "dc-worker"); t.setDaemon(true); return t;
        });
    }

    public void start() {
        if (running) return;
        running = true;
        Thread t = new Thread(this::loop, "dc-agent");
        t.setDaemon(true);
        t.start();
    }

    public void stop() {
        running = false;
        connected = false;
        try { if (socket != null) socket.close(); } catch (IOException ignored) {}
        taskPool.shutdown();
        scheduler.shutdown();
        onStatus.accept("Stopped");
    }

    public boolean isConnected()   { return connected; }
    public int getTasksDone()      { return done; }
    public int getTasksFailed()    { return failed; }
    public String getClientId()    { return clientId; }

    public void onStatus(Consumer<String> cb) { this.onStatus = cb; }
    public void onLog(Consumer<String> cb)    { this.onLog    = cb; }

    private void loop() {
        while (running) {
            try {
                onStatus.accept("Connecting to " + host + ":" + port + "\u2026");
                socket = new Socket(host, port);
                socket.setTcpNoDelay(true);
                reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                writer = new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

                String helloLine = reader.readLine();
                if (helloLine == null) throw new IOException("No HELLO from server");
                AppProtocol.HelloMessage hello = AppProtocol.parseHello(helloLine);
                if (hello.version() != AppProtocol.VERSION)
                    throw new IOException("Protocol version mismatch");
                send(new AppProtocol.RegisterMessage(clientId, threads, label));

                connected = true;
                onStatus.accept("Connected \u2014 " + threads + " threads");
                onLog.accept("Registered with server " + hello.serverId());

                scheduler.scheduleAtFixedRate(
                        () -> send(new AppProtocol.PingMessage()), 8, 8, TimeUnit.SECONDS);

                String line;
                while (running && (line = reader.readLine()) != null) {
                    final String msg = line;
                    AppProtocol.MessageType type = AppProtocol.peekType(msg).messageType();
                    switch (type) {
                        case TASK -> {
                            AppProtocol.TaskMessage task = AppProtocol.parseTask(msg);
                            onLog.accept("Received task " + task.taskId() + " [" + task.kind() + "]");
                            taskPool.submit(() -> runTask(task));
                        }
                        case PING -> send(new AppProtocol.PongMessage());
                        case PONG -> {}
                        case DISCONNECT -> { running = false; connected = false; return; }
                        default -> {}
                    }
                }

            } catch (IOException e) {
                connected = false;
                if (running) {
                    onLog.accept("Error: " + e.getMessage() + " \u2014 retrying in 10s");
                    onStatus.accept("Reconnecting\u2026");
                    try { Thread.sleep(10_000); } catch (InterruptedException ie) { break; }
                }
            }
        }
    }

    private void runTask(AppProtocol.TaskMessage task) {
        try {
            JsonObject result = AppTaskExecutor.execute(task);
            send(AppProtocol.ResultMessage.ok(task.taskId(), result));
            done++;
            onLog.accept("Completed " + task.taskId());
        } catch (Exception e) {
            send(AppProtocol.ResultMessage.fail(task.taskId(), e.getMessage()));
            failed++;
            onLog.accept("Failed " + task.taskId() + ": " + e.getMessage());
        }
    }

    private synchronized void send(Object msg) {
        if (writer == null) return;
        try {
            writer.write(AppProtocol.serialise(msg));
            writer.newLine();
            writer.flush();
        } catch (IOException e) { connected = false; }
    }
}
