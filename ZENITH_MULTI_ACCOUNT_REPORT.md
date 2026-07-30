# ZenithProxy Multi-Account Architecture — Technical Report

> Investigation date: 2026-07-30
> ZenithProxy version: 1.21.4
> Source branch: `rfresh2/ZenithProxy` `1.21.4`

---

## 1. How Multiple Minecraft Accounts Are Represented

ZenithProxy does **not** support multiple bot accounts connecting to the destination server. Its architecture models **one bot account + many viewer accounts**:

### The Bot Account
- **One** `ClientSession` (`Proxy.java:104`) connects the proxy to the destination Minecraft server.
- `ClientSession` extends `TcpClientSession` from MCProtocolLib. It logs in via Microsoft/MSA authentication (`Proxy.java:793-843`), connects to the configured server address, and relays packets.
- Source: `com.zenith.network.client.ClientSession`

### Connected Player Accounts (Viewers)
- **Many** `ServerSession` instances connect to ZenithProxy's own local TCP server.
- `ServerSession` extends `TcpServerSession`. Each has a Minecraft account authenticated via Mojang session validation. The `GameProfile` is stored in `ServerSession.profileCache`.
- Source: `com.zenith.network.server.ServerSession`

### Classification
Server sessions are classified into two roles:

```
Proxy.java:104-105
    protected final AtomicReference<ServerSession> currentPlayer = new AtomicReference<>();
    protected final FastArrayList<ServerSession> activeConnections = new FastArrayList<>(ServerSession.class);
```

| Field | Type | Contains |
|---|---|---|
| `Proxy.currentPlayer` | `AtomicReference<ServerSession>` | The **single controlling player** (or null) |
| `Proxy.activeConnections` | `FastArrayList<ServerSession>` | **All** connected sessions (player + spectators) |

`ServerSession` tracks role via boolean flags:

```
ServerSession.java:107-108
    protected boolean isPlayer = false;
    protected boolean isSpectator = false;
```

- `isPlayer = true` — This session is the controlling player. Their input packets are forwarded to the destination server.
- `isSpectator = true` — View-only. Their packets are **not** forwarded to the server.
- If both are false, the session is still authenticating.

---

## 2. Whether Plugins Can Control More Than One Bot Simultaneously

**No.** The standard plugin API does not support multi-bot control.

### Evidence

**Single ClientSession:**
```
Proxy.java:104
    protected ClientSession client;
```
The `Proxy` singleton holds exactly one `ClientSession`. There is no collection or registry for multiple bot connections.

**Single connect method:**
```
Proxy.java:683-690
    public synchronized void connect() {
        connect(CONFIG.client.server.address, CONFIG.client.server.port);
    }
```
The `connect()` method is `synchronized` and throws `IllegalStateException` if already connected:
```
Proxy.java:681-682
    if (this.isConnected()) throw new IllegalStateException("Already connected!");
```

**Plugin API has no multi-bot methods:**
```
PluginAPI.java (full interface)
    <T> T registerConfig(String fileName, Class<T> configClass);
    void registerModule(Module module);
    void registerCommand(Command command);
    ComponentLogger getLogger();
    PluginInfo getPluginInfo();
```
No method to create a second client, register a second bot, or obtain a session handle.

**Module system is per-proxy, not per-bot:**
```
Globals.java:85
    public static final ModuleManager MODULE;
```
There is one `ModuleManager` that holds all registered modules. Modules listen to `ClientBotTick`, which fires for **the** singular bot:
```
ClientBotTick.java:3-5
    // Tick emitted when no player is controlling the client
    // i.e. when the zenith "bot" handlers are controlling the player
    public record ClientBotTick() { ... }
```

---

## 3. What ActiveConnections Represents

`activeConnections` is **all** connected `ServerSession` instances — both the controlling player and all spectators.

```
Proxy.java:105
    protected final FastArrayList<ServerSession> activeConnections = new FastArrayList<>(ServerSession.class);
```

### Population

`ServerSession.setLoggedIn()` adds the session:
```
ServerSession.java:330-332
    public void setLoggedIn() {
        this.isLoggedIn = true;
        if (!Proxy.getInstance().getActiveConnections().contains(this))
            Proxy.getInstance().getActiveConnections().add(this);
    }
```

### Removal

`ServerSession.callDisconnected()` removes the session:
```
ServerSession.java:281-282
    Proxy.getInstance().getCurrentPlayer().compareAndSet(this, null);
    Proxy.getInstance().getActiveConnections().remove(this);
```

### Usage Patterns

Packets from the destination server are broadcast to ALL active connections:
```
ClientSession.java:260-269
    var connections = Proxy.getInstance().getActiveConnections().getArray();
    for (int i = 0; i < connections.length; i++) {
        var connection = connections[i];
        if (state == ProtocolState.CONFIGURATION && !connection.isConfigured()) continue;
        if (connection.isSpectator() && PacketCodecRegistries.SPECTATOR_PACKET_FILTER.contains(p.getClass())) continue;
        connection.sendAsync(p);
    }
```

Spectators are filtered out separately:
```
Proxy.java:542-553
    public List<ServerSession> getSpectatorConnections() {
        var connections = getActiveConnections().getArray();
        if (connections.length == 0) return Collections.emptyList();
        if (connections.length == 1 && hasActivePlayer()) return Collections.emptyList();
        final List<ServerSession> result = new ArrayList<>(...);
        for (int i = 0; i < connections.length; i++) {
            var connection = connections[i];
            if (connection.isSpectator()) result.add(connection);
        }
        return result;
    }
```

---

## 4. Whether Core Services Are Global Singletons or Per-Session

**All core services are global singletons.** There is one instance per ZenithProxy process.

### Evidence from Globals.java

```
Globals.java:55-93
    public static final DataCache CACHE;
    public static final SimpleEventBus EVENT_BUS;
    public static final ScheduledExecutorService EXECUTOR;
    public static final ModuleManager MODULE;
    public static final InputManager INPUTS;
    public static final Bot BOT;
    public static final Baritone BARITONE;
    public static final InventoryManager INVENTORY;
```

All are `static final` fields initialized in the `static {}` block at startup. There is exactly one of each per JVM process.

### Specific Service Analysis

| Service | Singleton? | Source | Responsible For |
|---|---|---|---|
| `CACHE` (DataCache) | Yes, one per process | `Globals.java:56` | World state mirror for the single bot |
| `BARITONE` (Baritone) | Yes, one per process | `Globals.java:71` | Pathfinding for the single bot |
| `INVENTORY` (InventoryManager) | Yes, one per process | `Globals.java:72` | Inventory action queue for the single bot |
| `INPUTS` (InputManager) | Yes, one per process | `Globals.java:69` | Movement/look input queue for the single bot |
| `BOT` (Bot) | Yes, one per process | `Globals.java:70` | Client-side player simulation for the single bot |
| `MODULE` (ModuleManager) | Yes, one per process | `Globals.java:68` | Module registry (built-in + plugin modules) |
| `EVENT_BUS` (SimpleEventBus) | Yes, one per process | `Globals.java:58` | Event publish/subscribe for the entire proxy |

### Bot is a Singleton

```
Bot.java:84-91 (constructor)
    public Bot() {
        EVENT_BUS.subscribe(
            this,
            of(ClientBotTick.class, TICK_PRIORITY, this::tick),
            of(ClientBotTick.class, POST_TICK_PRIORITY, this::postTick),
            of(ClientBotTick.Starting.class, this::handleClientTickStarting),
            of(ClientBotTick.Stopped.class, this::handleClientTickStopped),
            of(ClientTickEvent.class, this::tickWhilePlayerControlling)
        );
    }
```

`Bot` subscribes to `ClientBotTick` events and handles movement simulation, collision detection, and packet transmission for **one** entity (the cached player).

### Baritone is a Singleton

```
Baritone.java:72-78 (constructor)
    public Baritone() {
        ...
        EVENT_BUS.subscribe(
            this,
            of(ClientBotTick.class, this::onClientBotTick),
            of(ClientBotTick.class, POST_TICK_PRIORITY, this::onClientBotTickPost),
            of(ClientBotTick.Starting.class, this::onClientBotTickStarting),
            of(ClientBotTick.Stopped.class, this::onClientBotTickStopped)
        );
    }
```

`Baritone` tracks one active goal at a time:
```
Baritone.java:98-100
    public boolean isActive() {
        return getPathingBehavior().getGoal() != null || getPathingControlManager().isActive();
    }
```

---

## 5. Whether the Current Plugin API Assumes Exactly One Controlled Bot

**Yes.** The plugin API is fundamentally single-bot.

### Evidence

1. **`ZenithProxyPlugin.onLoad(PluginAPI)`** is called once, with no session/bot identifier parameter. A plugin registers its modules and commands globally.

2. **`Module`** subscribes to events on the global `EVENT_BUS`. `ClientBotTick` fires for exactly one bot. There is no per-bot event bus or session-scoped events.

3. **`PluginAPI`** has no method to create, destroy, or reference a specific bot client. It cannot enumerate connected players (that's `Proxy.getInstance().getActiveConnections()`).

4. **`DataCache`** tracks exactly one player entity:
```
PlayerCache.java:47
    protected EntityPlayer thePlayer = (EntityPlayer) new EntityPlayer(true).setEntityId(-1);
```

5. **`CACHE.getPlayerCache()`** assumes one player. There is no `CACHE.getPlayerCache(botId)` overload.

6. The static `Globals` imports used in example modules (`BARITONE`, `CACHE`, `INVENTORY`) all resolve to the single global instance.

---

## 6. Multi-Bot Plugin Architecture (If Required)

If Loom needed to control multiple bots simultaneously with the current ZenithProxy architecture, **it would not be possible through the standard plugin API**. The options would be:

### Option A: Multiple ZenithProxy Processes
Run a separate ZenithProxy process per bot account on different ports. Each process loads Loom as a plugin. Communication between processes would need IPC (files, sockets, message queue) for coordination. This is the only approach that works with ZenithProxy as it exists today.

### Option B: Fork ZenithProxy
Fork ZenithProxy's core to allow multiple `ClientSession` instances and per-session `DataCache`, `Baritone`, `Bot`, etc. This requires deep architectural changes:

1. Replace all `static final` singletons in `Globals` with per-session instances.
2. Add session-scoped event buses (`ClientBotTick` would need a session ID).
3. Add multi-session support to `PluginAPI` (e.g., `registerModuleForSession(Module, sessionId)`).
4. Refactor `ModuleManager` to associate modules with specific sessions.
5. Add session lifecycle management (create, destroy, enumerate).
6. Update `INVENTORY`, `INPUTS`, `BARITONE` to accept a session parameter.

The scale of change required is essentially a rewrite of ZenithProxy's core, as the entire architecture is built around the 1:1 proxy-to-bot assumption.

### Recommendation for Loom

Loom should be designed for a **single-bot** architecture. One ZenithProxy instance = one bot = one map art being built at a time. If multi-bot is needed in the future, it should be achieved via multiple ZenithProxy processes rather than a plugin-level abstraction, as the proxy itself provides no multi-bot primitives.

---

## Summary Table

| Question | Answer |
|---|---|
| Multiple bot accounts? | No. One `ClientSession` per ZenithProxy process. |
| Multiple viewer accounts? | Yes. `ServerSession` instances connect to proxy's local server. |
| `activeConnections`? | All connected `ServerSession` instances (player + spectators). |
| Plugins control multiple bots? | Not possible through standard plugin API. |
| `BARITONE` singleton? | Yes. `Globals.BARITONE` — one instance. |
| `CACHE` singleton? | Yes. `Globals.CACHE` — one `DataCache`. |
| `INVENTORY` singleton? | Yes. `Globals.INVENTORY` — one `InventoryManager`. |
| `INPUTS` singleton? | Yes. `Globals.INPUTS` — one `InputManager`. |
| `BOT` singleton? | Yes. `Globals.BOT` — one `Bot` simulation. |
| `EVENT_BUS` singleton? | Yes. `Globals.EVENT_BUS` — one `SimpleEventBus`. |
| Plugin API assumes single bot? | Yes. No session parameters, no multi-bot registration methods. |
| Multi-bot possible? | Only via multiple ZenithProxy processes. Plugin API offers no multi-bot support. |
