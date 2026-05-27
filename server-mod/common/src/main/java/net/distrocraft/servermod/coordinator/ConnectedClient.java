package net.distrocraft.servermod.coordinator;

import net.distrocraft.servermod.protocol.Protocol;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class ConnectedClient {

    private final String clientId;
    private final String playerName;
    private final Socket socket;
    private final BufferedWriter writer;
    private final Consumer<String> sendCallback;
    private final int maxThreads;
    private final Map<String, Integer> capabilities;
    private final Set<String> activeTasks = ConcurrentHashMap.newKeySet();
    private volatile boolean connected = true;
    private long lastPingSent = 0;
    private long lastPongReceived = System.currentTimeMillis();

    public ConnectedClient(String clientId,
                           String playerName,
                           int maxThreads,
                           Map<String, Integer> capabilities,
                           Socket socket) throws IOException {
        this.clientId = clientId;
        this.playerName = playerName;
        this.maxThreads = maxThreads;
        this.capabilities = capabilities != null ? capabilities : Map.of("threads", maxThreads);
        this.socket = socket;
        this.writer = new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
        this.sendCallback = null;
    }

    public ConnectedClient(String clientId,
                           String playerName,
                           int maxThreads,
                           Map<String, Integer> capabilities,
                           Consumer<String> sendCallback) {
        this.clientId = clientId;
        this.playerName = playerName;
        this.maxThreads = maxThreads;
        this.capabilities = capabilities != null ? capabilities : Map.of("threads", maxThreads);
        this.socket = null;
        this.writer = null;
        this.sendCallback = sendCallback;
    }

    public void send(Object message) {
        Consumer<String> callback;
        BufferedWriter out;
        synchronized (this) {
            if (!connected) return;
            callback = sendCallback;
            out = writer;
        }
        String json = Protocol.serialise(message);
        if (callback != null) {
            callback.accept(json);
        } else if (out != null) {
            try {
                writer.write(json);
                writer.newLine();
                writer.flush();
            } catch (IOException e) {
                connected = false;
                if (socket != null) {
                    try { socket.close(); } catch (IOException ignored) {}
                }
            }
        }
    }

    public void disconnect(String reason) {
        if (!connected) return;
        send(new Protocol.DisconnectMessage(reason));
        connected = false;
        if (socket != null) {
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    public boolean hasCapacity() {
        return connected && activeTasks.size() < maxThreads;
    }

    public int availableSlots() {
        return Math.max(0, maxThreads - activeTasks.size());
    }

    public void assignTask(String taskId)  { activeTasks.add(taskId); }
    public void releaseTask(String taskId) { activeTasks.remove(taskId); }

    public void recordPong() { this.lastPongReceived = System.currentTimeMillis(); }
    public void recordPingSent() { this.lastPingSent = System.currentTimeMillis(); }
    public boolean isAlive(long timeoutMs) {
        return (System.currentTimeMillis() - lastPongReceived) < timeoutMs;
    }

    public String getClientId()              { return clientId; }
    public String getPlayerName()            { return playerName; }
    public int getMaxThreads()               { return maxThreads; }
    public Map<String, Integer> getCapabilities()  { return capabilities; }
    public Consumer<String> getSendCallback()       { return sendCallback; }
    public boolean isConnected() {
        return connected && (socket == null || socket.isConnected());
    }
    public Set<String> getActiveTasks()      { return Collections.unmodifiableSet(activeTasks); }
    public long getLastPingSent()            { return lastPingSent; }
    public Socket getSocket()                { return socket; }

    @Override
    public String toString() {
        String name = playerName != null ? playerName : "app:" + clientId.substring(0, Math.min(8, clientId.length()));
        return "Client{" + name + ", threads=" + maxThreads +
               ", resources=" + capabilities +
               ", active=" + activeTasks.size() + ", alive=" + connected + "}";
    }
}
