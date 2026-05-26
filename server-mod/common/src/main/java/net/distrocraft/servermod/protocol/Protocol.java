package net.distrocraft.servermod.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

public final class Protocol {

    public static final int VERSION = 1;
    public static final int DEFAULT_PORT = 25566;
    public static final Gson GSON = new GsonBuilder().serializeNulls().create();

    private Protocol() {}

    public enum MessageType {
        HELLO, REGISTER, TASK, RESULT, PING, PONG, DISCONNECT
    }

    public record BaseMessage(String type) {
        public MessageType messageType() { return MessageType.valueOf(type); }
    }

    public record HelloMessage(
            String type,
            int version,
            String serverId
    ) {
        public HelloMessage(String serverId) {
            this(MessageType.HELLO.name(), VERSION, serverId);
        }
    }

    public record RegisterMessage(
            String type,
            String clientId,
            int maxThreads,
            String playerName
    ) {
        public RegisterMessage(String clientId, int maxThreads, String playerName) {
            this(MessageType.REGISTER.name(), clientId, maxThreads, playerName);
        }
    }

    public record TaskMessage(
            String type,
            String taskId,
            String kind,
            JsonObject payload
    ) {
        public TaskMessage(String taskId, String kind, JsonObject payload) {
            this(MessageType.TASK.name(), taskId, kind, payload);
        }
    }

    public record ResultMessage(
            String type,
            String taskId,
            boolean success,
            JsonObject data,
            String error
    ) {
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

    public record DisconnectMessage(String type, String reason) {
        public DisconnectMessage(String reason) {
            this(MessageType.DISCONNECT.name(), reason);
        }
    }

    public static String serialise(Object msg) {
        return GSON.toJson(msg);
    }

    public static BaseMessage peekType(String line) {
        return GSON.fromJson(line, BaseMessage.class);
    }

    public static HelloMessage parseHello(String line) {
        return GSON.fromJson(line, HelloMessage.class);
    }

    public static RegisterMessage parseRegister(String line) {
        return GSON.fromJson(line, RegisterMessage.class);
    }

    public static TaskMessage parseTask(String line) {
        return GSON.fromJson(line, TaskMessage.class);
    }

    public static ResultMessage parseResult(String line) {
        return GSON.fromJson(line, ResultMessage.class);
    }
}
