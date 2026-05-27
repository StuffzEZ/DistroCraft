package net.distrocraft.servermod.task;

import com.google.gson.JsonObject;

import java.util.UUID;

public final class DistroTask {

    private final String id;
    private final String kind;
    private final JsonObject payload;
    private volatile TaskState state;
    private volatile JsonObject result;
    private volatile String assignedClientId;
    private volatile long dispatchedAt;

    public DistroTask(String kind, JsonObject payload) {
        this.id = UUID.randomUUID().toString();
        this.kind = kind;
        this.payload = payload;
        this.state = TaskState.PENDING;
    }

    public enum TaskState {
        PENDING,
        DISPATCHED,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    public String getId()                 { return id; }
    public String getKind()               { return kind; }
    public JsonObject getPayload()        { return payload; }
    public TaskState getState()           { return state; }
    public JsonObject getResult()         { return result; }
    public String getAssignedClientId()   { return assignedClientId; }
    public long getDispatchedAt()         { return dispatchedAt; }

    public void setState(TaskState state) { this.state = state; }
    public void setResult(JsonObject result) { this.result = result; }
    public void setAssignedClientId(String id) { this.assignedClientId = id; }
    public void setDispatchedAt(long ts)  { this.dispatchedAt = ts; }

    public static DistroTask of(String kind, JsonObject payload) {
        return new DistroTask(kind, payload);
    }

    public static DistroTask chunkGen(int cx, int cz, String dimension, long seed) {
        JsonObject p = new JsonObject();
        p.addProperty("cx", cx);
        p.addProperty("cz", cz);
        p.addProperty("dimension", dimension);
        p.addProperty("seed", seed);
        return new DistroTask("CHUNK_GEN", p);
    }

    public static DistroTask pathfind(int x1, int y1, int z1,
                                      int x2, int y2, int z2,
                                      String contextJson) {
        JsonObject p = new JsonObject();
        p.addProperty("x1", x1); p.addProperty("y1", y1); p.addProperty("z1", z1);
        p.addProperty("x2", x2); p.addProperty("y2", y2); p.addProperty("z2", z2);
        p.addProperty("context", contextJson);
        return new DistroTask("PATHFIND", p);
    }

    public static DistroTask physicsSim(String simulationType, JsonObject params) {
        JsonObject p = new JsonObject();
        p.addProperty("simulationType", simulationType);
        p.add("params", params);
        return new DistroTask("PHYSICS_SIM", p);
    }

    @Override
    public String toString() {
        return "DistroTask{id=" + id + ", kind=" + kind + ", state=" + state + "}";
    }
}
