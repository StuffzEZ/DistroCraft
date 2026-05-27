package net.distrocraft.playermod.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.util.Map;

public final class ClientProtocol {

    public static final int VERSION      = 1;
    public static final int DEFAULT_PORT = 25566;
    public static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private ClientProtocol() {}

    public enum MessageType { HELLO, REGISTER, TASK, RESULT, PING, PONG, DISCONNECT }

    public record BaseMessage(String type) {
        public MessageType messageType() {
            if (type == null) return null;
            try { return MessageType.valueOf(type); }
            catch (IllegalArgumentException e) { return null; }
        }
    }

    public record HelloMessage(String type, int version, String serverId) {}

    public record RegisterMessage(String type, String clientId, int maxThreads, String playerName, Map<String, Integer> capabilities) {
        public RegisterMessage(String clientId, int maxThreads, String playerName) {
            this(MessageType.REGISTER.name(), clientId, maxThreads, playerName, Map.of("threads", maxThreads));
        }
        public RegisterMessage(String clientId, int maxThreads, String playerName, Map<String, Integer> capabilities) {
            this(MessageType.REGISTER.name(), clientId, maxThreads, playerName, capabilities);
        }
    }

    public record TaskMessage(String type, String taskId, String kind, JsonObject payload) {}

    public record ResultMessage(String type, String taskId, boolean success, JsonObject data, String error) {
        public static ResultMessage ok(String taskId, JsonObject data) {
            return new ResultMessage(MessageType.RESULT.name(), taskId, true, data, null);
        }
        public static ResultMessage fail(String taskId, String error) {
            return new ResultMessage(MessageType.RESULT.name(), taskId, false, null, error);
        }
    }

    public record PingMessage(String type) {
        public PingMessage() { this(MessageType.PING.name()); }
    }

    public record PongMessage(String type) {
        public PongMessage() { this(MessageType.PONG.name()); }
    }

    public static String serialise(Object msg)          { if (msg == null) return "{}"; return GSON.toJson(msg); }
    public static BaseMessage  peekType(String line)    { return GSON.fromJson(line, BaseMessage.class); }
    public static HelloMessage parseHello(String line)  { return GSON.fromJson(line, HelloMessage.class); }
    public static TaskMessage  parseTask(String line)   { return GSON.fromJson(line, TaskMessage.class); }
}
