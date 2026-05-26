package net.distrocraft.plugin.task;

import com.google.gson.JsonObject;
import java.util.UUID;

public final class PluginTask {

    public enum State { PENDING, DISPATCHED, COMPLETED, FAILED, CANCELLED }

    private final String     id;
    private final String     kind;
    private final JsonObject payload;
    private volatile State   state = State.PENDING;
    private JsonObject       result;
    private String           assignedClientId;
    private long             dispatchedAt;

    public PluginTask(String kind, JsonObject payload) {
        this.id      = UUID.randomUUID().toString();
        this.kind    = kind;
        this.payload = payload;
    }

    public String     getId()                { return id; }
    public String     getKind()              { return kind; }
    public JsonObject getPayload()           { return payload; }
    public State      getState()             { return state; }
    public JsonObject getResult()            { return result; }
    public String     getAssignedClientId()  { return assignedClientId; }
    public long       getDispatchedAt()      { return dispatchedAt; }

    public void setState(State s)            { this.state = s; }
    public void setResult(JsonObject r)      { this.result = r; }
    public void setAssignedClientId(String i){ this.assignedClientId = i; }
    public void setDispatchedAt(long t)      { this.dispatchedAt = t; }

    public static PluginTask of(String kind, JsonObject payload) {
        return new PluginTask(kind, payload);
    }

    public static PluginTask chunkGen(int cx, int cz, String dim, long seed) {
        JsonObject p = new JsonObject();
        p.addProperty("cx", cx); p.addProperty("cz", cz);
        p.addProperty("dimension", dim); p.addProperty("seed", seed);
        return new PluginTask("CHUNK_GEN", p);
    }

    public static PluginTask physicsSim(String simType, JsonObject params) {
        JsonObject p = new JsonObject();
        p.addProperty("simulationType", simType);
        p.add("params", params);
        return new PluginTask("PHYSICS_SIM", p);
    }
}
