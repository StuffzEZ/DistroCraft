package net.distrocraft.plugin.network;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.util.Map;

public final class PluginProtocol {

    public static final int VERSION      = 1;
    public static final int DEFAULT_PORT = 25566;
    public static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private PluginProtocol() {}

    public enum MessageType { HELLO, REGISTER, TASK, RESULT, PING, PONG, DISCONNECT }

    public record BaseMessage(String type) {
        public MessageType messageType() { return MessageType.valueOf(type); }
    }
    public record HelloMessage(String type, int version, String serverId) {
        public HelloMessage(String serverId) {
            this(MessageType.HELLO.name(), VERSION, serverId);
        }
    }
    public record RegisterMessage(String type, String clientId, int maxThreads, String playerName, Map<String, Integer> capabilities) {
        public RegisterMessage(String clientId, int maxThreads, String playerName) {
            this(MessageType.REGISTER.name(), clientId, maxThreads, playerName, Map.of("threads", maxThreads));
        }
        public RegisterMessage(String clientId, int maxThreads, String playerName, Map<String, Integer> capabilities) {
            this(MessageType.REGISTER.name(), clientId, maxThreads, playerName, capabilities);
        }
    }
    public record TaskMessage(String type, String taskId, String kind, JsonObject payload) {
        public TaskMessage(String taskId, String kind, JsonObject payload) {
            this(MessageType.TASK.name(), taskId, kind, payload);
        }
    }
    public record ResultMessage(String type, String taskId, boolean success, JsonObject data, String error) {}
    public record PingMessage(String type)  { public PingMessage()  { this(MessageType.PING.name()); } }
    public record PongMessage(String type)  { public PongMessage()  { this(MessageType.PONG.name()); } }
    public record DisconnectMessage(String type, String reason) {
        public DisconnectMessage(String reason) { this(MessageType.DISCONNECT.name(), reason); }
    }

    public static String        serialise(Object o)        { return GSON.toJson(o); }
    public static BaseMessage   peekType(String l)         { return GSON.fromJson(l, BaseMessage.class); }
    public static HelloMessage  parseHello(String l)       { return GSON.fromJson(l, HelloMessage.class); }
    public static RegisterMessage parseRegister(String l)  { return GSON.fromJson(l, RegisterMessage.class); }
    public static TaskMessage   parseTask(String l)        { return GSON.fromJson(l, TaskMessage.class); }
    public static ResultMessage parseResult(String l)      { return GSON.fromJson(l, ResultMessage.class); }
}
