package net.distrocraft.plugin.coordinator;

import net.distrocraft.plugin.network.PluginProtocol;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class PluginConnectedClient {

    private final String              clientId;
    private final String              playerName;
    private final int                 maxThreads;
    private final Map<String, Integer> capabilities;
    private final Socket              socket;
    private final BufferedWriter      writer;

    private final Set<String> activeTasks = ConcurrentHashMap.newKeySet();
    private volatile boolean  connected   = true;
    private long          lastPingSent      = 0;
    private volatile long lastPongReceived  = System.currentTimeMillis();

    public PluginConnectedClient(String clientId, String playerName,
                                  int maxThreads, Map<String, Integer> capabilities,
                                  Socket socket) throws IOException {
        this.clientId     = clientId;
        this.playerName   = playerName;
        this.maxThreads   = maxThreads;
        this.capabilities = capabilities != null ? capabilities : Map.of("threads", maxThreads);
        this.socket       = socket;
        this.writer       = new BufferedWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
    }

    public void send(Object message) {
        BufferedWriter out;
        synchronized (this) {
            if (!connected) return;
            out = writer;
        }
        try {
            out.write(PluginProtocol.serialise(message));
            out.newLine();
            out.flush();
        } catch (IOException e) {
            synchronized (this) { connected = false; }
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    public void disconnect(String reason) {
        if (!connected) return;
        send(new PluginProtocol.DisconnectMessage(reason));
        connected = false;
        try { socket.close(); } catch (IOException ignored) {}
    }

    public boolean hasCapacity()           { return connected && activeTasks.size() < maxThreads; }
    public void assignTask(String id)      { activeTasks.add(id); }
    public void releaseTask(String id)     { activeTasks.remove(id); }
    public void recordPong()               { lastPongReceived = System.currentTimeMillis(); }
    public void recordPingSent()           { lastPingSent     = System.currentTimeMillis(); }
    public boolean isAlive(long timeoutMs) { return (System.currentTimeMillis() - lastPongReceived) < timeoutMs; }

    public String              getClientId()     { return clientId; }
    public String              getPlayerName()   { return playerName; }
    public int                 getMaxThreads()   { return maxThreads; }
    public Map<String, Integer> getCapabilities() { return capabilities; }
    public boolean             isConnected()     { return connected; }
    public Set<String> getActiveTasks()  { return Collections.unmodifiableSet(activeTasks); }
    public long        getLastPingSent() { return lastPingSent; }

    @Override public String toString() {
        String name = playerName != null ? playerName : "app:" + clientId.substring(0, Math.min(8, clientId.length()));
        return "Client{" + name + ", threads=" + maxThreads +
               ", resources=" + capabilities + ", active=" + activeTasks.size() + "}";
    }
}
