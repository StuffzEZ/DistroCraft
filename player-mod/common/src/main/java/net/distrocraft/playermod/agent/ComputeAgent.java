package net.distrocraft.playermod.agent;

import com.google.gson.JsonObject;
import net.distrocraft.playermod.network.ClientProtocol;
import net.distrocraft.playermod.task.TaskExecutor;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.Consumer;

public final class ComputeAgent {

    private final String host;
    private final int    port;
    private final String clientId;
    private final int    threads;
    private final Map<String, Integer> capabilities;
    private final String playerName;
    private final Consumer<String> sendCallback;

    private Socket        socket;
    private BufferedReader reader;
    private BufferedWriter writer;

    private volatile boolean running    = false;
    private volatile boolean connected  = false;

    private final ExecutorService taskPool;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "distrocraft-agent-scheduler"); t.setDaemon(true); return t;
            });

    private Consumer<String> onStatusChange = s -> {};
    private Consumer<String> onError        = e -> {};

    private volatile int tasksCompleted = 0;
    private volatile int tasksFailed    = 0;

    public ComputeAgent(String host, int port, int threads, String playerName,
                        Map<String, Integer> capabilities) {
        this.host         = host;
        this.port         = port;
        this.clientId     = UUID.randomUUID().toString();
        this.threads      = threads;
        this.capabilities = capabilities != null ? capabilities : Map.of("threads", threads);
        this.playerName   = playerName;
        this.sendCallback = null;
        this.taskPool     = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "distrocraft-task-worker"); t.setDaemon(true); return t;
        });
    }

    public ComputeAgent(String clientId, int threads, String playerName,
                        Map<String, Integer> capabilities, Consumer<String> sendCallback) {
        this.host          = null;
        this.port          = -1;
        this.clientId      = clientId;
        this.threads       = threads;
        this.capabilities  = capabilities != null ? capabilities : Map.of("threads", threads);
        this.playerName    = playerName;
        this.sendCallback  = sendCallback;
        this.taskPool      = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "distrocraft-task-worker"); t.setDaemon(true); return t;
        });
    }

    public void start() {
        if (running) return;
        running = true;
        scheduler.scheduleAtFixedRate(this::sendPing, 8, 8, TimeUnit.SECONDS);

        if (sendCallback != null) {
            connected = true;
            onStatusChange.accept("Connected \u2014 " + resourcesString());
        } else {
            Thread connector = new Thread(this::connectAndRun, "distrocraft-agent-main");
            connector.setDaemon(true);
            connector.start();
        }
    }

    public Map<String, Integer> getCapabilities() { return capabilities; }

    private String resourcesString() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : capabilities.entrySet()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(e.getKey()).append("=").append(e.getValue());
        }
        return sb.toString();
    }

    public void stop() {
        running = false;
        connected = false;
        if (socket != null) {
            try { socket.close(); } catch (IOException ignored) {}
        }
        taskPool.shutdown();
        scheduler.shutdown();
        onStatusChange.accept("Disconnected");
    }

    public boolean isConnected() { return connected; }
    public int getTasksCompleted() { return tasksCompleted; }
    public int getTasksFailed()    { return tasksFailed; }
    public String getClientId()    { return clientId; }

    public void onStatusChange(Consumer<String> cb) { this.onStatusChange = cb; }
    public void onError(Consumer<String> cb)        { this.onError = cb; }

    public boolean processMessage(String json) {
        if (!connected) return false;
        try {
            ClientProtocol.MessageType type = ClientProtocol.peekType(json).messageType();
            switch (type) {
                case TASK -> {
                    ClientProtocol.TaskMessage task = ClientProtocol.parseTask(json);
                    taskPool.submit(() -> executeTask(task));
                    return true;
                }
                case PING -> send(new ClientProtocol.PongMessage());
                case PONG -> {}
                case DISCONNECT -> {
                    running = false;
                    connected = false;
                    onStatusChange.accept("Server disconnected us");
                }
                default -> {}
            }
            return true;
        } catch (Exception e) {
            onError.accept("Message handling error: " + e.getMessage());
            return false;
        }
    }

    private void connectAndRun() {
        while (running) {
            try {
                onStatusChange.accept("Connecting to " + host + ":" + port);
                socket = new Socket(host, port);
                socket.setTcpNoDelay(true);
                reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                writer = new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

                handshake();
                connected = true;
                onStatusChange.accept("Connected \u2014 " + resourcesString());

                readLoop();

            } catch (IOException e) {
                connected = false;
                if (running) {
                    onError.accept("Connection error: " + e.getMessage());
                    onStatusChange.accept("Reconnecting in 10s\u2026");
                    try { Thread.sleep(10_000); } catch (InterruptedException ie) { break; }
                }
            }
        }
    }

    private void handshake() throws IOException {
        String helloLine = reader.readLine();
        if (helloLine == null) throw new IOException("Server closed connection immediately");
        ClientProtocol.HelloMessage hello = ClientProtocol.parseHello(helloLine);
        if (hello.version() != ClientProtocol.VERSION) {
            throw new IOException("Protocol version mismatch: server=" + hello.version() +
                                  " client=" + ClientProtocol.VERSION);
        }

        send(new ClientProtocol.RegisterMessage(clientId, threads, playerName, capabilities));
    }

    private void readLoop() throws IOException {
        String line;
        while (running && (line = reader.readLine()) != null) {
            processMessage(line);
        }
    }

    private void executeTask(ClientProtocol.TaskMessage task) {
        try {
            JsonObject result = TaskExecutor.execute(task);
            send(ClientProtocol.ResultMessage.ok(task.taskId(), result));
            tasksCompleted++;
        } catch (Exception e) {
            send(ClientProtocol.ResultMessage.fail(task.taskId(), e.getMessage()));
            tasksFailed++;
        }
    }

    private synchronized void send(Object msg) {
        if (!connected && !(msg instanceof ClientProtocol.RegisterMessage)) return;
        if (sendCallback != null) {
            sendCallback.accept(ClientProtocol.serialise(msg));
        } else if (writer != null) {
            try {
                writer.write(ClientProtocol.serialise(msg));
                writer.newLine();
                writer.flush();
            } catch (IOException e) {
                connected = false;
            }
        }
    }

    private void sendPing() {
        send(new ClientProtocol.PingMessage());
    }
}
