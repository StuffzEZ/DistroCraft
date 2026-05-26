package net.distrocraft.app.task;

import com.google.gson.JsonObject;
import net.distrocraft.app.network.AppProtocol;

import java.util.Random;

public final class AppTaskExecutor {

    private static final TaskHandlerRegistry registry = new TaskHandlerRegistry();

    static {
        registry.register("CHUNK_GEN",      AppTaskExecutor::chunkGen);
        registry.register("PATHFIND",       AppTaskExecutor::pathfind);
        registry.register("STRUCTURE_SCAN", AppTaskExecutor::structureScan);
        registry.register("PHYSICS_SIM",    AppTaskExecutor::physicsSim);
        registry.register("CUSTOM",         AppTaskExecutor::custom);
    }

    private AppTaskExecutor() {}

    public static TaskHandlerRegistry getRegistry() {
        return registry;
    }

    public static JsonObject execute(AppProtocol.TaskMessage task) throws Exception {
        return registry.execute(task.kind(), task.payload());
    }

    private static JsonObject chunkGen(JsonObject p) {
        int cx = p.get("cx").getAsInt(), cz = p.get("cz").getAsInt();
        long seed = p.get("seed").getAsLong();
        int[] hm = new int[256];
        Random rng = new Random(seed ^ ((long) cx << 32) ^ cz);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 256; i++) {
            hm[i] = 60 + rng.nextInt(20);
            if (i > 0) sb.append(',');
            sb.append(hm[i]);
        }
        JsonObject r = new JsonObject();
        r.addProperty("cx", cx); r.addProperty("cz", cz);
        r.addProperty("heightmap", sb.toString());
        return r;
    }

    private static JsonObject pathfind(JsonObject p) {
        double dx = p.get("x2").getAsDouble()-p.get("x1").getAsDouble();
        double dy = p.get("y2").getAsDouble()-p.get("y1").getAsDouble();
        double dz = p.get("z2").getAsDouble()-p.get("z1").getAsDouble();
        JsonObject r = new JsonObject();
        r.addProperty("found", true);
        r.addProperty("estimatedLength", Math.sqrt(dx*dx+dy*dy+dz*dz));
        r.addProperty("pathJson", "[]");
        return r;
    }

    private static JsonObject structureScan(JsonObject p) {
        int x1 = p.get("x1").getAsInt(), z1 = p.get("z1").getAsInt();
        int x2 = p.get("x2").getAsInt(), z2 = p.get("z2").getAsInt();
        long seed = p.get("seed").getAsLong();
        Random rng = new Random(seed);
        JsonObject r = new JsonObject();
        r.addProperty("candidateX", x1 + rng.nextInt(Math.max(1, x2-x1)));
        r.addProperty("candidateZ", z1 + rng.nextInt(Math.max(1, z2-z1)));
        r.addProperty("confidence", 0.75 + rng.nextDouble() * 0.25);
        return r;
    }

    private static JsonObject physicsSim(JsonObject p) {
        String simType = p.get("simulationType").getAsString();
        JsonObject params = p.getAsJsonObject("params");

        double dt = params.has("dt") ? params.get("dt").getAsDouble() : 0.05;
        int steps = params.has("steps") ? params.get("steps").getAsInt() : 1;

        JsonObject r = new JsonObject();
        r.addProperty("simulationType", simType);
        r.addProperty("stepsCompleted", steps);

        if ("RIGID_BODY".equals(simType)) {
            double mass = params.get("mass").getAsDouble();
            double fx = params.has("fx") ? params.get("fx").getAsDouble() : 0.0;
            double fy = params.has("fy") ? params.get("fy").getAsDouble() : 0.0;
            double fz = params.has("fz") ? params.get("fz").getAsDouble() : 0.0;
            double ax = fx / mass, ay = fy / mass, az = fz / mass;
            r.addProperty("dvx", ax * dt * steps);
            r.addProperty("dvy", ay * dt * steps);
            r.addProperty("dvz", az * dt * steps);
            r.addProperty("energy", 0.5 * mass * (ax*ax + ay*ay + az*az) * dt*dt * steps*steps);
        } else if ("FLUID".equals(simType)) {
            double density = params.get("density").getAsDouble();
            double velocity = params.get("velocity").getAsDouble();
            double viscosity = params.has("viscosity") ? params.get("viscosity").getAsDouble() : 0.01;
            double drag = viscosity * velocity * density;
            r.addProperty("drag", drag);
            r.addProperty("newVelocity", Math.max(0, velocity - drag * dt * steps));
            r.addProperty("reynoldsNumber", (density * velocity) / Math.max(1e-10, viscosity));
        } else if ("CONSTRAINT".equals(simType)) {
            int numBodies = params.get("numBodies").getAsInt();
            int iterations = params.has("iterations") ? params.get("iterations").getAsInt() : 10;
            double residual = 0.0;
            Random rng = new Random(simType.hashCode());
            for (int i = 0; i < iterations; i++) residual += rng.nextDouble() * 0.01;
            r.addProperty("numBodies", numBodies);
            r.addProperty("iterations", iterations);
            r.addProperty("finalResidual", residual / iterations);
            r.addProperty("converged", residual < 0.05);
        } else {
            r.addProperty("processed", true);
            r.add("params", params);
        }
        return r;
    }

    private static JsonObject custom(JsonObject p) {
        JsonObject r = new JsonObject(); r.add("echo", p); r.addProperty("processed", true);
        return r;
    }
}
