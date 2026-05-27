package net.distrocraft.plugin.coordinator;

import com.google.gson.JsonObject;
import net.distrocraft.plugin.network.PluginProtocol;
import net.distrocraft.plugin.task.PluginTask;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.logging.Logger;

public final class PluginCoordinator {

    private static final long PING_INTERVAL_MS  = 10_000;
    private static final long CLIENT_TIMEOUT_MS = 30_000;
    private static final long TASK_TIMEOUT_MS   = 60_000;

    private final int    port;
    private final String serverId = UUID.randomUUID().toString();
    private final Logger logger;

    private final ConcurrentHashMap<String, PluginConnectedClient> clients  = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, PluginTask>            allTasks = new ConcurrentHashMap<>();
    private final LinkedBlockingQueue<PluginTask>                  pending  = new LinkedBlockingQueue<>();

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
            r -> { Thread t = new Thread(r, "dc-plugin-sched"); t.setDaemon(true); return t; });
    private final ExecutorService ioPool = Executors.newCachedThreadPool(
            r -> { Thread t = new Thread(r, "dc-plugin-io"); t.setDaemon(true); return t; });

    private ServerSocket serverSocket;
    private volatile boolean running = false;

    private volatile BiConsumer<PluginTask, JsonObject> onComplete = (t, r) -> {};
    private volatile BiConsumer<PluginTask, String>     onFail     = (t, e) -> {};

    public PluginCoordinator(int port, Logger logger) {
        this.port   = port;
        this.logger = logger;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        serverSocket.setReuseAddress(true);
        running = true;
        logger.info("[Distrocraft] Coordinator started on port " + port);
        ioPool.submit(this::acceptLoop);
        ioPool.submit(this::dispatchLoop);
        scheduler.scheduleAtFixedRate(this::heartbeatAndEvict, 5, 5, TimeUnit.SECONDS);
    }

    public void stop() {
        running = false;
        try { if (serverSocket != null) serverSocket.close(); } catch (IOException ignored) {}
        clients.values().forEach(c -> c.disconnect("Server stopping"));
        scheduler.shutdownNow();
        ioPool.shutdownNow();
        logger.info("[Distrocraft] Coordinator stopped");
    }

    public void submitTask(PluginTask task) {
        allTasks.put(task.getId(), task);
        pending.offer(task);
    }

    public void onComplete(BiConsumer<PluginTask, JsonObject> cb) { this.onComplete = cb; }
    public void onFail(BiConsumer<PluginTask, String> cb)         { this.onFail = cb; }

    public int getClientCount()  { return clients.size(); }
    public int getPendingCount() { return pending.size(); }
    public Collection<PluginConnectedClient> getClients() {
        return Collections.unmodifiableCollection(clients.values());
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                socket.setTcpNoDelay(true);
                ioPool.submit(() -> handleConnection(socket));
            } catch (IOException e) {
                if (running) logger.warning("[Distrocraft] Accept error: " + e.getMessage());
            }
        }
    }

    private void handleConnection(Socket socket) {
        String registeredId = null;
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            BufferedWriter tmpWriter = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));

            tmpWriter.write(PluginProtocol.serialise(new PluginProtocol.HelloMessage(serverId)));
            tmpWriter.newLine(); tmpWriter.flush();

            String line = reader.readLine();
            if (line == null) { socket.close(); return; }
            if (PluginProtocol.peekType(line).messageType() != PluginProtocol.MessageType.REGISTER) {
                socket.close(); return;
            }
            PluginProtocol.RegisterMessage reg = PluginProtocol.parseRegister(line);
            int threads = Math.max(1, Math.min(reg.maxThreads(), 32));
            Map<String, Integer> caps = reg.capabilities();
            if (caps == null) caps = Map.of("threads", threads);

            PluginConnectedClient client = new PluginConnectedClient(
                    reg.clientId(), reg.playerName(), threads, caps, socket);
            clients.put(reg.clientId(), client);
            registeredId = reg.clientId();
            logger.info("[Distrocraft] Client registered: " + client);

            String msg;
            while (running && (msg = reader.readLine()) != null) {
                handleMessage(client, msg);
            }

        } catch (IOException e) {
            logger.warning("[Distrocraft] Connection error: " + e.getMessage());
        } finally {
            if (registeredId != null) {
                clients.remove(registeredId);
                requeueFrom(registeredId);
                logger.info("[Distrocraft] Client disconnected: " + registeredId);
            }
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private void handleMessage(PluginConnectedClient client, String line) {
        try {
            switch (PluginProtocol.peekType(line).messageType()) {
                case RESULT -> {
                    PluginProtocol.ResultMessage res = PluginProtocol.parseResult(line);
                    client.releaseTask(res.taskId());
                    PluginTask task = allTasks.get(res.taskId());
                    if (task == null) return;
                    if (res.success()) {
                        task.setResult(res.data());
                        task.setState(PluginTask.State.COMPLETED);
                        onComplete.accept(task, res.data());
                    } else {
                        task.setState(PluginTask.State.FAILED);
                        onFail.accept(task, res.error());
                    }
                }
                case PING -> client.send(new PluginProtocol.PongMessage());
                case PONG -> client.recordPong();
                case DISCONNECT -> {
                    clients.remove(client.getClientId());
                    requeueFrom(client.getClientId());
                    client.disconnect("Client requested disconnect");
                }
                default   -> {}
            }
        } catch (Exception e) {
            logger.warning("[Distrocraft] Bad message: " + e.getMessage());
        }
    }

    private void dispatchLoop() {
        while (running) {
            try {
                PluginTask task = pending.poll(500, TimeUnit.MILLISECONDS);
                if (task == null) continue;
                if (task.getState() == PluginTask.State.CANCELLED) continue;

                PluginConnectedClient target = clients.values().stream()
                        .filter(PluginConnectedClient::hasCapacity)
                        .min(Comparator.comparingInt(c -> c.getActiveTasks().size()))
                        .orElse(null);

                if (target == null) {
                    pending.offer(task); Thread.sleep(200); continue;
                }

                task.setState(PluginTask.State.DISPATCHED);
                task.setAssignedClientId(target.getClientId());
                task.setDispatchedAt(System.currentTimeMillis());
                target.assignTask(task.getId());
                target.send(new PluginProtocol.TaskMessage(task.getId(), task.getKind(), task.getPayload()));

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); break;
            }
        }
    }

    private void heartbeatAndEvict() {
        long now = System.currentTimeMillis();
        clients.values().forEach(c -> {
            if (now - c.getLastPingSent() > PING_INTERVAL_MS) {
                c.send(new PluginProtocol.PingMessage());
                c.recordPingSent();
            }
        });
        clients.values().removeIf(c -> {
            if (!c.isAlive(CLIENT_TIMEOUT_MS)) {
                requeueFrom(c.getClientId());
                c.disconnect("Timeout");
                return true;
            }
            return false;
        });
        allTasks.values().stream()
                .filter(t -> t.getState() == PluginTask.State.DISPATCHED)
                .filter(t -> now - t.getDispatchedAt() > TASK_TIMEOUT_MS)
                .forEach(t -> {
                    t.setState(PluginTask.State.PENDING);
                    t.setAssignedClientId(null);
                    pending.offer(t);
                });
    }

    private void requeueFrom(String clientId) {
        allTasks.values().stream()
                .filter(t -> clientId.equals(t.getAssignedClientId()))
                .filter(t -> t.getState() == PluginTask.State.DISPATCHED)
                .forEach(t -> {
                    t.setState(PluginTask.State.PENDING);
                    t.setAssignedClientId(null);
                    pending.offer(t);
                });
    }
}
