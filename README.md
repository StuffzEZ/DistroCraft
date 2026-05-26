# Distrocraft

Distributed computing for Minecraft — let your players contribute their idle CPU cycles to power your server.

## Overview

Distrocraft splits CPU-bound work into small tasks and dispatches them to connected player machines. Results are returned asynchronously to the server. It works on top of a plain TCP connection (port 25566 by default) that is completely separate from the Minecraft connection.

## Components

| Directory | What it is |
|---|---|
| `server-mod/` | NeoForge + Fabric 1.21.x server-side mod (multiloader) |
| `player-mod/` | NeoForge + Fabric 1.21.x client-side player mod (multiloader) |
| `player-app/` | Standalone Java app (GUI + headless CLI) — no Minecraft required |
| `server-plugin/` | Paper 1.21.x server plugin (alternative to the server mod) |

Use **either** `server-mod` **or** `server-plugin` on your server — not both.
Players use **either** `player-mod` **or** `player-app`.

## Quick Start

### 1. Server (mod or plugin)

**NeoForge / Fabric mod:**
```
server-mod/neoforge/build/libs/distrocraft-server-mod-neoforge.jar
server-mod/fabric/build/libs/distrocraft-server-mod-fabric.jar
```
Drop the correct jar into your server's `mods/` folder. The coordinator starts automatically on port `25566`.

**Paper plugin:**
```
server-plugin/build/libs/distrocraft-server-plugin.jar
```
Drop into `plugins/`. Configure via `plugins/DistrocraftServer/config.yml`.

### 2. Players (mod)
Drop the correct jar into `.minecraft/mods/`.

In-game commands (type in chat, they start with `/distro`):
```
/distro start                   — connect to the server's coordinator
/distro stop                    — disconnect
/distro status                  — show connection status and stats
/distro set host <host>         — set coordinator host (default: same as server)
/distro set port <port>         — set coordinator port (default: 25566)
/distro set threads <n>         — number of worker threads to contribute
```

A small HUD overlay in the top-right shows live status.

### 3. Players (standalone app)
```bash
# GUI mode
java -jar distrocraft-client-app.jar

# Headless / CLI mode
java -jar distrocraft-client-app.jar <host> [port] [threads] [label]
# e.g.
java -jar distrocraft-client-app.jar play.myserver.com 25566 4 Steve
```

## Building

Requirements: Java 21, Gradle 8+

```bash
# Server mod (NeoForge)
cd server-mod && ./gradlew :neoforge:jar

# Server mod (Fabric)
cd server-mod && ./gradlew :fabric:jar

# Player mod (NeoForge)
cd player-mod && ./gradlew :neoforge:jar

# Player mod (Fabric)
cd player-mod && ./gradlew :fabric:jar

# Standalone app (fat jar)
cd player-app && ./gradlew jar

# Paper plugin
cd server-plugin && ./gradlew jar
```

## Protocol

All communication is **newline-delimited JSON over TCP** (port 25566 by default).

```
SERVER → CLIENT  HELLO      {"type":"HELLO","version":1,"serverId":"<uuid>"}
CLIENT → SERVER  REGISTER   {"type":"REGISTER","clientId":"<uuid>","maxThreads":4,"playerName":"Steve"}
SERVER → CLIENT  TASK       {"type":"TASK","taskId":"<uuid>","kind":"CHUNK_GEN","payload":{...}}
CLIENT → SERVER  RESULT     {"type":"RESULT","taskId":"<uuid>","success":true,"data":{...}}
CLIENT → SERVER  RESULT     {"type":"RESULT","taskId":"<uuid>","success":false,"error":"..."}
EITHER           PING/PONG  {"type":"PING"} / {"type":"PONG"}
SERVER → CLIENT  DISCONNECT {"type":"DISCONNECT","reason":"..."}
```

## Task Kinds

Task kinds are **arbitrary strings** — there is no enum. Any string is a valid task kind. The protocol transmits it as-is.

**Built-in example handlers** (registered in `TaskExecutor` and `AppTaskExecutor`):

| Kind | Payload | Result |
|---|---|---|
| `CHUNK_GEN` | `cx, cz, dimension, seed` | `cx, cz, heightmap` (256 comma-sep ints) |
| `PATHFIND` | `x1,y1,z1, x2,y2,z2, context` | `found, estimatedLength, pathJson` |
| `STRUCTURE_SCAN` | `x1,z1, x2,z2, seed, structureId` | `candidateX, candidateZ, confidence` |
| `PHYSICS_SIM` | `simulationType, params {mass, fx, fy, fz, dt, steps}` | `simulationType, stepsCompleted, dvx, dvy, dvz, energy` |
| `CUSTOM` | any JsonObject | `echo` + `processed:true` |

### Registering new task kinds

There is **no central registry** of kinds. The protocol accepts any string. You just need matching handlers on both sides:

**Client-side** (player mod or app):
```java
// player-mod
TaskExecutor.getRegistry().register("AERONAUTICS_PHYSICS", payload -> {
    // your physics solver here
    JsonObject result = new JsonObject();
    // ...
    return result;
});

// player-app
AppTaskExecutor.getRegistry().register("AERONAUTICS_PHYSICS", payload -> {
    // same logic
});
```

Then submit the task from the server:
```java
DistroTask task = DistroTask.of("AERONAUTICS_PHYSICS", payload);
coordinator.submitTask(task);
```

The built-in executors are **stubs** — replace them with real implementations (e.g., actual noise functions for chunk gen, real A* pathfinding, or Create Aeronautics physics solvers). The protocol and wiring are production-ready.

## Configuration

### Server mod
`config/distrocraft-server.properties` (auto-created with defaults):
```properties
port=25566
enabled=true
requireInGame=true
maxClientsPerPlayer=1
debugLogging=false
taskTimeoutSec=60
clientTimeoutSec=30
```

### Paper plugin
`plugins/DistrocraftServer/config.yml` — see inline comments.

### Player mod
`.minecraft/config/distrocraft-client.properties`:
```properties
host=localhost
port=25566
threads=2          # defaults to half your CPU core count
autoStart=false    # auto-connect when joining a server
showHud=true
```

## Security Notes

- The coordinator TCP port is **separate** from the MC port. You must open it in your firewall.
- Clients receive only `JsonObject` payloads — no server code is executed on the client.
- Tasks execute in a sandboxed thread pool with no MC world access.
- Set `require-in-game: true` (default) so only your active players can register clients.
- Consider running the coordinator on a non-default port if you expose it publicly.

## Licence
GNU GPLv3

## Acknowledgements
This project was developed with the assistance of AI (Claude by Anthropic).
