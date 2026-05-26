package net.distrocraft.servermod.task;

import com.google.gson.JsonObject;

@FunctionalInterface
public interface TaskHandler {
    JsonObject execute(JsonObject payload) throws Exception;
}
