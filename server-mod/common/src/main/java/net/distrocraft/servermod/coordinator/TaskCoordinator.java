package net.distrocraft.servermod.coordinator;

import com.google.gson.JsonObject;
import net.distrocraft.servermod.protocol.Protocol;
import net.distrocraft.servermod.task.DistroTask;
import net.distrocraft.servermod.util.DistroLogger;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class TaskCoordinator {

    private static final long PING_INTERVAL_MS   = 10_000;
    private static final long CLIENT_TIMEOUT_MS  = 30_000;
    private static final long TASK_TIMEOUT_MS    = 60_000;
    private static final int  DISPATCH_THREADS   = 2;

    private final int port;
    private final String serverId;
    private final boolean payloadOnly;

    private final ConcurrentHashMap<String, ConnectedClient> clients = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, DistroTask> allTasks = new ConcurrentHashMap<>();
    private final LinkedBlockingQueue<DistroTask> pendingQueue = new LinkedBlockingQueue<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> { Thread t = new Thread(r, "distrocraft-scheduler"); t.setDaemon(true); return t; });
    private final ExecutorService ioPool = Executors.newCachedThreadPool(
            r -> { Thread t = new Thread(r, "distrocraft-io"); t.setDaemon(true); return t; });

    private ServerSocket serverSocket;
    private volatile boolean running = false;

    private BiConsumer<DistroTask, JsonObject> onTaskComplete = (t, r) -> {};
    private BiConsumer<DistroTask, String>     onTaskFail     = (t, e) -> {};

    public TaskCoordinator(int port) {
        this.port = port;
        this.serverId = UUID.randomUUID().toString();
        this.payloadOnly = false;
    }

    public TaskCoordinator() {
        this.port = -1;
        this.serverId = UUID.randomUUID().toString();
        this.payloadOnly = true;
    }

    public void start() throws IOException {
        running = true;
        if (!payloadOnly) {
            serverSocket = new ServerSocket(port);
            serverSocket.setReuseAddress(true);
            DistroLogger.info("Distrocraft coordinator listening on port " + port);
            ioPool.submit(this::acceptLoop);
        } else {
            DistroLogger.info("Distrocraft coordinator started (payload-only mode, no TCP)");
        }

        for (int i = 0; i < DISPATCH_THREADS; i++) {
            ioPool.submit(this::dispatchLoop);
        }

        scheduler.scheduleAtFixedRate(this::heartbeatAndEvict, 5, 5, TimeUnit.SECONDS);
    }

    public void stop() {
        running = false;
        if (serverSocket != null) {
            try { serverSocket.close(); } catch (IOException ignored) {}
        }
        clients.values().forEach(c -> c.disconnect("Server stopping"));
        scheduler.shutdownNow();
        ioPool.shutdownNow();
        DistroLogger.info("Distrocraft coordinator stopped");
    }

    public void submitTask(DistroTask task) {
        allTasks.put(task.getId(), task);
        pendingQueue.offer(task);
    }

    public void onTaskComplete(BiConsumer<DistroTask, JsonObject> cb) { this.onTaskComplete = cb; }
    public void onTaskFail(BiConsumer<DistroTask, String>     cb) { this.onTaskFail = cb; }

    public int getConnectedClientCount()  { return clients.size(); }
    public int getPendingTaskCount()      { return pendingQueue.size(); }
    public Collection<ConnectedClient> getClients() { return Collections.unmodifiableCollection(clients.values()); }
    public String getServerId()           { return serverId; }

    public String registerPayloadClient(String playerUuid, String playerName, int maxThreads, Consumer<String> sendCallback) {
        return registerPayloadClient(playerUuid, playerName, maxThreads, null, sendCallback);
    }

    public String registerPayloadClient(String playerUuid, String playerName, int maxThreads,
                                         Map<String, Integer> capabilities, Consumer<String> sendCallback) {
        int threads = Math.max(1, Math.min(maxThreads, 32));
        try {
            ConnectedClient client = new ConnectedClient(playerUuid, playerName, threads, capabilities, sendCallback);
            clients.put(playerUuid, client);
            client.send(new Protocol.HelloMessage(serverId));
            DistroLogger.info("Payload client registered: " + client);
            return serverId;
        } catch (Exception e) {
            DistroLogger.warn("Failed to register payload client: " + e.getMessage());
            return null;
        }
    }

    public void handlePayloadMessage(String clientId, String json) {
        ConnectedClient client = clients.get(clientId);
        if (client != null) {
            handleMessage(client, json);
        }
    }

    public void unregisterPayloadClient(String clientId) {
        ConnectedClient client = clients.remove(clientId);
        if (client != null) {
            requeueTasksFrom(clientId);
            DistroLogger.info("Payload client unregistered: " + clientId);
        }
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                ioPool.submit(() -> handleNewConnection(socket));
            } catch (IOException e) {
                if (running) DistroLogger.warn("Accept error: " + e.getMessage());
            }
        }
    }

    private void handleNewConnection(Socket socket) {
        String remoteAddr = socket.getRemoteSocketAddress().toString();
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

            BufferedWriter tmpWriter = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
            tmpWriter.write(Protocol.serialise(new Protocol.HelloMessage(serverId)));
            tmpWriter.newLine();
            tmpWriter.flush();

            String line = reader.readLine();
            if (line == null) { socket.close(); return; }
            if (Protocol.peekType(line).messageType() != Protocol.MessageType.REGISTER) {
                socket.close(); return;
            }
            Protocol.RegisterMessage reg = Protocol.parseRegister(line);
            int threads = Math.max(1, Math.min(reg.maxThreads(), 32));
            Map<String, Integer> caps = reg.capabilities();
            if (caps == null) caps = Map.of("threads", threads);

            ConnectedClient client = new ConnectedClient(
                    reg.clientId(), reg.playerName(), threads, caps, socket);
            clients.put(reg.clientId(), client);
            DistroLogger.info("Client registered: " + client);

            String msgLine;
            while (running && (msgLine = reader.readLine()) != null) {
                handleMessage(client, msgLine);
            }

            clients.remove(reg.clientId());
            DistroLogger.info("Client disconnected: " + client.getClientId());
            requeueTasksFrom(reg.clientId());

        } catch (IOException e) {
            DistroLogger.warn("IO error with " + remoteAddr + ": " + e.getMessage());
        }
    }

    private void handleMessage(ConnectedClient client, String line) {
        try {
            Protocol.MessageType type = Protocol.peekType(line).messageType();
            switch (type) {
                case RESULT -> {
                    Protocol.ResultMessage result = Protocol.parseResult(line);
                    client.releaseTask(result.taskId());
                    DistroTask task = allTasks.get(result.taskId());
                    if (task == null) return;
                    if (result.success()) {
                        task.setState(DistroTask.TaskState.COMPLETED);
                        task.setResult(result.data());
                        onTaskComplete.accept(task, result.data());
                    } else {
                        task.setState(DistroTask.TaskState.FAILED);
                        onTaskFail.accept(task, result.error());
                    }
                }
                case PING -> client.send(new Protocol.PongMessage());
                case PONG -> client.recordPong();
                default -> DistroLogger.warn("Unexpected message type from client: " + type);
            }
        } catch (Exception e) {
            DistroLogger.warn("Bad message from " + client.getClientId() + ": " + e.getMessage());
        }
    }

    private void dispatchLoop() {
        while (running) {
            try {
                DistroTask task = pendingQueue.poll(500, TimeUnit.MILLISECONDS);
                if (task == null) continue;
                if (task.getState() == DistroTask.TaskState.CANCELLED) continue;

                ConnectedClient target = pickClient();
                if (target == null) {
                    pendingQueue.offer(task);
                    Thread.sleep(200);
                    continue;
                }

                task.setState(DistroTask.TaskState.DISPATCHED);
                task.setAssignedClientId(target.getClientId());
                task.setDispatchedAt(System.currentTimeMillis());
                target.assignTask(task.getId());
                target.send(new Protocol.TaskMessage(task.getId(), task.getKind(), task.getPayload()));
                DistroLogger.debug("Dispatched " + task.getId() + " \u2192 " + target.getClientId());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private ConnectedClient pickClient() {
        return clients.values().stream()
                .filter(ConnectedClient::hasCapacity)
                .filter(ConnectedClient::isConnected)
                .min(Comparator.comparingInt(c -> c.getActiveTasks().size()))
                .orElse(null);
    }

    private void heartbeatAndEvict() {
        long now = System.currentTimeMillis();

        clients.values().forEach(c -> {
            if (now - c.getLastPingSent() > PING_INTERVAL_MS) {
                c.send(new Protocol.PingMessage());
                c.recordPingSent();
            }
        });

        clients.values().removeIf(c -> {
            if (!c.isAlive(CLIENT_TIMEOUT_MS)) {
                DistroLogger.warn("Client timed out: " + c.getClientId());
                requeueTasksFrom(c.getClientId());
                c.disconnect("Timeout");
                return true;
            }
            return false;
        });

        allTasks.values().stream()
                .filter(t -> t.getState() == DistroTask.TaskState.DISPATCHED)
                .filter(t -> now - t.getDispatchedAt() > TASK_TIMEOUT_MS)
                .forEach(t -> {
                    DistroLogger.warn("Task timed out, re-queuing: " + t.getId());
                    t.setState(DistroTask.TaskState.PENDING);
                    t.setAssignedClientId(null);
                    pendingQueue.offer(t);
                });
    }

    private void requeueTasksFrom(String clientId) {
        allTasks.values().stream()
                .filter(t -> clientId.equals(t.getAssignedClientId()))
                .filter(t -> t.getState() == DistroTask.TaskState.DISPATCHED)
                .forEach(t -> {
                    t.setState(DistroTask.TaskState.PENDING);
                    t.setAssignedClientId(null);
                    pendingQueue.offer(t);
                });
    }
}
