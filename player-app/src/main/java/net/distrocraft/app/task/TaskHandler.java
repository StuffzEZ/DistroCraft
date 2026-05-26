package net.distrocraft.app.task;

import com.google.gson.JsonObject;

@FunctionalInterface
public interface TaskHandler {
    JsonObject execute(JsonObject payload) throws Exception;
}
