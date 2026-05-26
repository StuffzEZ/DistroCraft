package net.distrocraft.playermod.task;

import com.google.gson.JsonObject;
import net.distrocraft.playermod.network.ClientProtocol;

import java.util.Random;

public final class TaskExecutor {

    private static final TaskHandlerRegistry registry = new TaskHandlerRegistry();

    static {
        registry.register("CHUNK_GEN",      TaskExecutor::executeChunkGen);
        registry.register("PATHFIND",       TaskExecutor::executePathfind);
        registry.register("STRUCTURE_SCAN", TaskExecutor::executeStructureScan);
        registry.register("PHYSICS_SIM",    TaskExecutor::executePhysicsSim);
        registry.register("CUSTOM",         TaskExecutor::executeCustom);
    }

    private TaskExecutor() {}

    public static TaskHandlerRegistry getRegistry() {
        return registry;
    }

    public static JsonObject execute(ClientProtocol.TaskMessage task) throws Exception {
        return registry.execute(task.kind(), task.payload());
    }

    private static JsonObject executeChunkGen(JsonObject payload) {
        int cx   = payload.get("cx").getAsInt();
        int cz   = payload.get("cz").getAsInt();
        long seed = payload.get("seed").getAsLong();

        int[] heightmap = new int[256];
        Random rng = new Random(seed ^ ((long) cx << 32) ^ cz);
        for (int i = 0; i < heightmap.length; i++) {
            heightmap[i] = 60 + rng.nextInt(20);
        }

        JsonObject result = new JsonObject();
        result.addProperty("cx", cx);
        result.addProperty("cz", cz);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < heightmap.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(heightmap[i]);
        }
        result.addProperty("heightmap", sb.toString());
        return result;
    }

    private static JsonObject executePathfind(JsonObject payload) {
        double x1 = payload.get("x1").getAsDouble(), y1 = payload.get("y1").getAsDouble(),
               z1 = payload.get("z1").getAsDouble();
        double x2 = payload.get("x2").getAsDouble(), y2 = payload.get("y2").getAsDouble(),
               z2 = payload.get("z2").getAsDouble();

        double dist = Math.sqrt((x2-x1)*(x2-x1) + (y2-y1)*(y2-y1) + (z2-z1)*(z2-z1));

        JsonObject result = new JsonObject();
        result.addProperty("found", true);
        result.addProperty("estimatedLength", dist);
        result.addProperty("pathJson", "[]");
        return result;
    }

    private static JsonObject executeStructureScan(JsonObject payload) {
        int x1 = payload.get("x1").getAsInt(), z1 = payload.get("z1").getAsInt();
        int x2 = payload.get("x2").getAsInt(), z2 = payload.get("z2").getAsInt();
        long seed = payload.get("seed").getAsLong();
        String structureId = payload.has("structureId") ? payload.get("structureId").getAsString() : "unknown";

        Random rng = new Random(seed ^ structureId.hashCode());
        int cx = x1 + rng.nextInt(Math.max(1, x2 - x1));
        int cz = z1 + rng.nextInt(Math.max(1, z2 - z1));

        JsonObject result = new JsonObject();
        result.addProperty("structureId", structureId);
        result.addProperty("candidateX", cx);
        result.addProperty("candidateZ", cz);
        result.addProperty("confidence", 0.75 + rng.nextDouble() * 0.25);
        return result;
    }

    private static JsonObject executePhysicsSim(JsonObject payload) {
        String simType = payload.get("simulationType").getAsString();
        JsonObject params = payload.getAsJsonObject("params");

        double dt = params.has("dt") ? params.get("dt").getAsDouble() : 0.05;
        int steps = params.has("steps") ? params.get("steps").getAsInt() : 1;

        JsonObject result = new JsonObject();
        result.addProperty("simulationType", simType);
        result.addProperty("stepsCompleted", steps);

        if ("RIGID_BODY".equals(simType)) {
            double mass = params.get("mass").getAsDouble();
            double fx = params.has("fx") ? params.get("fx").getAsDouble() : 0.0;
            double fy = params.has("fy") ? params.get("fy").getAsDouble() : 0.0;
            double fz = params.has("fz") ? params.get("fz").getAsDouble() : 0.0;

            double ax = fx / mass, ay = fy / mass, az = fz / mass;
            double dvx = ax * dt * steps, dvy = ay * dt * steps, dvz = az * dt * steps;

            result.addProperty("dvx", dvx);
            result.addProperty("dvy", dvy);
            result.addProperty("dvz", dvz);
            result.addProperty("energy", 0.5 * mass * (dvx*dvx + dvy*dvy + dvz*dvz));
        } else if ("FLUID".equals(simType)) {
            double density = params.get("density").getAsDouble();
            double velocity = params.get("velocity").getAsDouble();
            double viscosity = params.has("viscosity") ? params.get("viscosity").getAsDouble() : 0.01;

            double drag = viscosity * velocity * density;
            double newVelocity = velocity - drag * dt * steps;

            result.addProperty("drag", drag);
            result.addProperty("newVelocity", Math.max(0, newVelocity));
            result.addProperty("reynoldsNumber", (density * velocity) / viscosity);
        } else if ("CONSTRAINT".equals(simType)) {
            int numBodies = params.get("numBodies").getAsInt();
            int iterations = params.has("iterations") ? params.get("iterations").getAsInt() : 10;

            double residual = 0.0;
            Random rng = new Random(simType.hashCode());
            for (int i = 0; i < iterations; i++) {
                residual += rng.nextDouble() * 0.01;
            }

            result.addProperty("numBodies", numBodies);
            result.addProperty("iterations", iterations);
            result.addProperty("finalResidual", residual / iterations);
            result.addProperty("converged", residual < 0.05);
        } else {
            result.addProperty("processed", true);
            result.add("params", params);
        }

        return result;
    }

    private static JsonObject executeCustom(JsonObject payload) {
        JsonObject result = new JsonObject();
        result.add("echo", payload);
        result.addProperty("processed", true);
        return result;
    }
}
