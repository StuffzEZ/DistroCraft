1. **`ComputeAgent` (player mod)** — The `send()` method guards with `!connected` but skips the check for `RegisterMessage`. However, the `sendCallback` variant never does the TCP handshake, so `RegisterMessage` is sent but there's no server-side `HELLO` parsing. Also, `scheduler` in the `sendCallback` path is never initialized with a `scheduleAtFixedRate` for pings — it's only done in the TCP `connectAndRun()` path. So ping scheduling is missing for the payload mode.

2. **`TaskCoordinator`** — Duplicate `import java.util.function.Consumer;` import.

3. **`ComputeAgent`** — In `processMessage()`, when `sendCallback != null` (payload/in-game mode), it calls `send()` for PONG but `send()` checks `!connected && !(msg instanceof RegisterMessage)` — this is fine. But the `connected` check in `send()` won't block `PongMessage` since `connected = true` was set in `start()`. This is okay, but the ping scheduler is never started in payload mode.

4. **`DistrocraftServerModFabric`** — The server registers the client with `threads = Runtime.getRuntime().availableProcessors() / 2` **on the server machine**, not the player's machine. It should wait for the client's `REGISTER` message with their actual capabilities — but in payload mode the `HELLO` is sent and then the `RegisterMessage` from the client comes back via `handlePayloadMessage`. However, `registerPayloadClient` is called immediately on JOIN with server-side thread count before the client sends its `REGISTER`. The actual `RegisterMessage` from the client is never parsed in payload mode — `handlePayloadMessage` goes straight to `handleMessage` which only handles `RESULT`, `PING`, `PONG`. The client's capabilities are never used.

5. **`ConnectedClient` (server mod)** — `isConnected()` returns `connected` but the socket-based constructor doesn't set `connected = false` when the socket closes externally (only on `send()` failure or `disconnect()`). The heartbeat eviction relies on `isAlive()` for this, which is correct.

6. **`AgentConfig.load()`** — The `configDir` in NeoForge is resolved from the mod file path, not the standard config dir. This may resolve to the wrong location (inside `mods/` rather than `config/`).

7. **`DistrocraftPlayerModNeoForge`** — The `configDir` is set as `container.getModInfo().getOwningFile().getFile().getFilePath().getParent().resolve("config")` — this gives `mods/../config` which is correct on standard installs but fragile. The standard NeoForge API is `FMLPaths.CONFIGDIR.get()`.

8. **`StandaloneAgent`** — The `scheduler.scheduleAtFixedRate` for pings is called inside the `loop()` method on every reconnect, which will stack up multiple ping schedulers after each reconnect.

9. **`AppGui.showResourcesDialog()`** — Imports `java.util.ArrayList` and `java.util.List` locally inside the method using fully-qualified references in the actual code (they're already imported at the top — no bug there), but this is fine.

10. **`TaskCoordinator.registerPayloadClient()`** — Sends a `HelloMessage` to the client, but the client (in `ComputeAgent` payload mode) never reads/parses a `HelloMessage` from a callback. The `processMessage()` switch doesn't handle `HELLO`. So the `HelloMessage` is sent but silently dropped. The client never sends back a `RegisterMessage` either — so the server uses whatever capabilities were passed to `registerPayloadClient()` rather than the client's actual advertised capabilities.

The most impactful bugs to fix:

- **Bug 1**: Ping scheduler not started in payload mode (`ComputeAgent`)
- **Bug 2**: Duplicate import in `TaskCoordinator`
- **Bug 3**: `StandaloneAgent` stacking ping schedulers on reconnect
- **Bug 4**: NeoForge config dir using fragile path resolution
- **Bug 5**: `HELLO` message sent to payload client but never handled (causes confusion/log noise)
- **Bug 6**: Server registers payload clients with server CPU count, not player's