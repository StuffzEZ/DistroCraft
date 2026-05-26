package net.distrocraft.playermod.task;

import com.google.gson.JsonObject;

import java.util.concurrent.ConcurrentHashMap;

public final class TaskHandlerRegistry {

    private final ConcurrentHashMap<String, TaskHandler> handlers = new ConcurrentHashMap<>();

    public void register(String kind, TaskHandler handler) {
        handlers.put(kind, handler);
    }

    public TaskHandler get(String kind) {
        return handlers.get(kind);
    }

    public boolean hasHandler(String kind) {
        return handlers.containsKey(kind);
    }

    public JsonObject execute(String kind, JsonObject payload) throws Exception {
        TaskHandler handler = handlers.get(kind);
        if (handler == null) {
            throw new IllegalArgumentException("No handler registered for task kind: " + kind);
        }
        return handler.execute(payload);
    }
}
