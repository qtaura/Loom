# Loom Internal Architecture

> Design document. No implementation code.

---

## Overview

Loom is a ZenithProxy plugin that fully automates carpet map art construction on anarchy servers. A map art is a 128×128 block area where each block is a specific colored carpet placed on a solid base layer. Loom handles schematic loading, inventory management, precise block placement, restocking from chests, recovery from all failure modes, and persists progress so work can always resume.

Loom uses ZenithProxy's APIs — module lifecycle, event bus, pathfinder, inventory manager, world cache, and command system — and adds domain-specific logic on top. It never reimplements what Zenith already provides.

---

## Design Principles

1. **Single Responsibility** — Each subsystem does exactly one thing. No overlap.
2. **Interface-First** — Every subsystem exposes a clean interface. Callers never depend on implementation details.
3. **Push Control Flow, Pull Events** — Direct method calls for orchestration. Event bus for cross-cutting notification.
4. **Leverage Zenith** — Pathfinding, inventory actions, world cache, rotation, and tick loop are delegated to Zenith.
5. **Always Resumable** — Any interruption (death, disconnect, server restart) must allow exact resumption from where we left off.
6. **Persist Early, Persist Often** — Progress is flushed to disk after every batch of placements.

---

## Subsystem Catalog

### 1. Task Scheduler

**Purpose:** Central coordinator. Decides what Loom should be doing at any given moment. All bot action flows through the scheduler.

**Responsibilities:**
- Maintain a priority queue of pending tasks
- Advance task state machines (pending → running → paused → completed → failed)
- Ensure only one task is active at a time
- Dispatch to the correct subsystem based on task type
- Handle task preemption (e.g., restock interrupts printing, printing resumes after restock)
- Re-queue interrupted tasks at appropriate priority
- Call `syncEnabledFromConfig()` on the Loom module when tasks start/stop

**Public API:**
```
submit(Task, priority) → TaskHandle
cancel(TaskHandle)
pause(TaskHandle)
resume(TaskHandle)
getActiveTask() → Optional<TaskHandle>
tick()                     // called from ClientBotTick handler
```

**Dependencies:** Job Manager (reads what work exists), Loom Events (publishes task lifecycle), Loom Logger

**Must NOT:** Execute any Minecraft interaction directly. It only orchestrates.

**Zenith integration:** Runs within the `Module.registerEvents()` tick loop via `ClientBotTick`.

---

### 2. Job Manager

**Purpose:** Manage long-running build jobs. A job is "build this schematic at this world position." The job manager owns the lifecycle of jobs across plugin restarts.

**Responsibilities:**
- Create jobs from a schematic + world origin
- Serialize/deserialize jobs to disk (each job is a JSON + progress file)
- Maintain an ordered job queue (build this, then that)
- Auto-load the active job on plugin startup
- Provide the active job to the Task Scheduler
- Report overall progress per job (0–100%)

**Public API:**
```
createJob(schematicId, originX, originY) → Job
getActiveJob() → Optional<Job>
getQueuedJobs() → List<Job>
cancelJob(jobId)
reorderJob(jobId, newIndex)
getProgress(jobId) → JobProgress
saveJob(jobId)
loadAllJobs()
```

**Dependencies:** Schematic Manager (validates schematic exists), Progress Tracker (owns per-block progress; job manager owns job-level metadata), File I/O

**Must NOT:** Execute placement, pathfind, or touch inventory. It manages metadata only.

**Zenith integration:** Config persistence uses the same `plugins/config/` directory pattern Zenith uses (`PluginAPI.registerConfig()`).

---

### 3. Printer Controller

**Purpose:** Core map art printing logic. Given a job (schematic + origin + progress state), the printer advances through the build area column by column, delegating individual placements.

**Responsibilities:**
- Determine the next block position that needs placement
- Skip positions where the correct block already exists (via World Scanner)
- Delegate single-block placement to the Placement Engine
- Handle printer pause/resume/cancel
- Signal Inventory Manager when materials for upcoming rows are insufficient
- Respect a configurable placement speed (ticks between placements)
- Yield control back to the Task Scheduler each tick

**Public API:**
```
start(job)                       // begins printing from saved or start position
pause()
resume()
cancel()
tick()                           // advance one step per call
getCurrentPosition() → BlockPos
getRow() → int
getColumn() → int
isPrinting() → boolean
```

**Dependencies:** Schematic Manager (what block goes where), Progress Tracker (what's already placed), Placement Engine (execute a placement), Navigation (move within build area), Inventory Manager (material availability), World Scanner (skip already-correct blocks), Loom Events (publishes block placed, row complete, etc.)

**Strategies (PrintStrategy interface):** The printer delegates traversal order to a strategy object:
- `RowMajorStrategy` — left to right, back to front (default, minimal backtracking)
- `ColumnMajorStrategy` — front to back, left to right
- `LayerStrategy` — complete one Y-level before ascending (for multi-layer builds)

**Must NOT:** Handle inventory restocking (signals Inventory Manager instead), handle block placement mechanics (delegates to Placement Engine), compute paths (delegates to Navigation), handle recoveries (Recovery System observes and intervenes externally).

**Zenith integration:** Runs within the `Module` tick loop. Does not block — each `tick()` call does one atomic step and returns.

---

### 4. Placement Engine

**Purpose:** Low-level block placement. Given a world position and a material, execute the sequence of actions needed to place that exact block.

**Responsibilities:**
- Calculate the face to click and the angle to face for correct placement
- Select the correct hotbar slot for the material (via Inventory Manager)
- Execute the right-click action (via Zenith Input Manager for look, plus packet send)
- Verify the block was placed (via World Scanner post-placement check)
- Retry on failure (up to configurable limit)
- Handle carpet-specific placement: identify the supporting block face, ensure carpet item is in hand
- Handle base-block placement differently from carpet placement (different faces)

**Public API:**
```
placeBlock(worldPos, material) → PlacementResult
placeCarpet(worldPos, material) → PlacementResult
canPlaceAt(worldPos, material) → boolean
getPlacementFace(worldPos) → Direction
calculateLookAngle(worldPos, face) → Rotation
```

**PlacementResult:**
- `SUCCESS` — block confirmed placed
- `FAILED_RETRIES_EXHAUSTED` — tried N times, gave up
- `OBSTRUCTED` — something is in the way
- `NO_MATERIAL` — don't have the item
- `OUT_OF_REACH` — too far from target

**Dependencies:** Inventory Manager (slot selection), World Scanner (pre-check + post-verify), Zenith `INPUTS` (look rotation), Zenith `CACHE.getPlayerCache()` (bot position)

**Must NOT:** Decide what to place or where. That's Printer Controller's job. Must NOT handle inventory restocking.

**Zenith integration:** Uses `INPUTS.submit(InputRequest)` for precise rotation, and `sendClientPacketAsync()` for right-click packets.

---

### 5. Loom Inventory Manager

**Purpose:** Tracks the bot's inventory from the perspective of the map art build. Knows which slots hold which carpet colors, how many of each remain, and when restocking is needed.

**Responsibilities:**
- Maintain a real-time ledger of material counts per type
- Reserve hotbar slots for frequently used materials
- Swap items into the hotbar as needed
- Detect when a material falls below the restock threshold
- Emit `RestockNeededEvent` when materials run critically low
- Auto-discard junk items that aren't in the material palette
- Answer "do we have enough X to finish the current row/column?"

**Public API:**
```
getCount(material) → int
hasMaterial(material) → boolean
reserveSlot(material) → int           // allocate a hotbar slot, swap if needed
releaseSlot(slot)
getRequiredRestock() → List<MaterialRequest>
requestRestock(materials)             // called by Task Scheduler when restock needed
getHotbarSlot(material) → Optional<Integer>
getAvailableSlots() → int
```

**MaterialLedger** (internal): Tracks every material in the palette, current count, restock threshold, and reserved slots.

**Dependencies:** Zenith `CACHE.getPlayerCache().getInventoryCache()` (ground truth), Zenith `INVENTORY` (for swap/drop actions), Loom Events, Schematic Manager (palette)

**Must NOT:** Handle chest interaction or navigation. That's Chest Restocking's job.

**Zenith integration:** Reads from Zenith's `InventoryCache`. Uses `INVENTORY.submit(InventoryActionRequest)` for slot swaps and item drops.

---

### 6. Chest Restocking

**Purpose:** Execute the full restock workflow — leave the build area, go to storage, withdraw materials, return, and signal that printing can resume.

**Responsibilities:**
- Accept a `RestockRequest` (list of materials + quantities needed)
- Navigate to the configured storage area
- Open the appropriate chests
- Withdraw exactly the needed materials
- Navigate back to the build area
- Signal Inventory Manager that restock is complete
- Handle chests being empty (emit event, pause job)

**Public API:**
```
restock(request: RestockRequest)
isRestocking() → boolean
isAtStorage() → boolean
isAtBuildArea() → boolean
cancelRestock()
```

**Dependencies:** Navigation (go to storage, go to build area), Loom Inventory Manager (material counts updated as items are withdrawn), Zenith `INVENTORY` (open chest, shift-click items), Zenith `BARITONE` (pathfinding to storage coordinates)

**Must NOT:** Decide when to restock (Inventory Manager does that). Handle placement or printing.

**Zenith integration:** Uses `BARITONE.getTo(Blocks.CHEST, true)` to navigate to and open chests. Uses `INVENTORY.submit(InventoryActionRequest)` for shift-click withdraw.

---

### 7. Navigation

**Purpose:** Move the bot precisely within and around the build area. Thin wrapper around Zenith's BARITONE pathfinder with build-area-specific logic.

**Responsibilities:**
- Path to a specific world coordinate within the build area
- Path to/from the storage area
- Align the bot precisely for block placement (center of block, correct face)
- Detect when the bot is stuck and emit a stuck event
- Maintain a cached set of valid standable positions within the build area to reduce pathfinding overhead
- Handle short-range "stepping" without full pathfinder invocation

**Public API:**
```
goTo(x, z) → NavigationResult           // navigate within build area
goToStorage() → NavigationResult
goToBuildArea() → NavigationResult
moveToPlacementPosition(worldPos, face) → NavigationResult
isNavigating() → boolean
isStuck() → boolean
cancel()
getCurrentPathTarget() → Optional<BlockPos>
```

**Dependencies:** Zenith `BARITONE` (pathfinding, `isActive()`), Zenith `CACHE` (position, chunks loaded), Loom Events (stuck detection)

**Must NOT:** Decide where to go. Callers tell it destinations. Handle block placement.

**Zenith integration:** Delegates to `BARITONE.pathTo()` for long-range and `BARITONE.thisWay()` for short stepping.

---

### 8. Recovery System

**Purpose:** Handle all failure modes. Get the bot back to a working state after any interruption, then resume the active job.

**Responsibilities:**
- Monitor for death (via Zenith events / health check)
- Monitor for disconnect (via Zenith events)
- Monitor for stuck (via Navigation stuck events)
- Monitor for combat (via Zenith entity events)
- Execute the appropriate recovery action:
  - **Death:** Wait for respawn, navigate to build area, re-equip, resume job
  - **Disconnect:** Wait for reconnect, resume job
  - **Stuck:** Cancel current navigation, re-path, retry
  - **Combat:** Run away / log out, wait, return, resume
- Save progress before any recovery action begins
- Emit `RecoveryStartedEvent` / `RecoveryCompletedEvent`
- Ensure only one recovery is active at a time

**Public API:**
```
onDeath()
onDisconnect()
onStuck(position)
onCombat(threatInfo)
isRecovering() → boolean
getRecoveryType() → Optional<RecoveryType>
cancelRecovery()
```

**RecoveryType enum:** `DEATH`, `DISCONNECT`, `STUCK`, `COMBAT`, `WORLD_MISMATCH`

**Dependencies:** Navigation (return to build area), Progress Tracker (save/resume state), Task Scheduler (pause/resume printing), Zenith events (`ClientBotTick.Stopped`, packet-level disconnect detection), Loom Events

**Must NOT:** Execute printing, decide block placements, manage inventory.

**Zenith integration:** Subscribes to Zenith events via module's `registerEvents()`. Uses `BARITONE.pathTo()` for return navigation. Uses `CACHE.getPlayerCache().isAlive()` for death detection.

---

### 9. Progress Tracker

**Purpose:** Persist exactly which blocks have been placed so that work can resume after any interruption, including full proxy restart.

**Responsibilities:**
- Maintain a per-job bitmap (or sparse set) of completed positions
- Persist to disk after every row completion (configurable batch size)
- Load progress from disk on startup
- Answer "is this position already placed?" queries (hot path, must be fast)
- Mark position as placed
- Compute the next unplaced position given a traversal strategy
- Compute completion percentage
- Clear progress on job cancel (with confirmation)

**Public API:**
```
markPlaced(x, y, material)
isPlaced(x, y) → boolean
markWrong(x, y)                    // if World Scanner finds wrong block
getNextUnplaced(startX, startY, strategy) → Optional<BlockPos>
getPercentComplete() → double
save(jobId)
load(jobId) → ProgressData
clear(jobId)
getTotalPlaced() → int
getTotalBlocks() → int
```

**Storage format:** Sparse per-job file mapping `(x, y) → material` for placed positions. For a 128×128 map this is at most 16,384 entries, easily held in memory. The file is JSON lines or a simple binary format for fast incremental writes.

**Dependencies:** Schematic Manager (knows total block count), File I/O

**Must NOT:** Know about Minecraft. `x` and `y` are schematic-relative coordinates, not world coordinates. The caller maps world → schematic.

**Zenith integration:** Uses the `plugins/config/` directory for file storage, consistent with Zenith's config pattern.

---

### 10. World Scanner

**Purpose:** Read the actual world state. What blocks exist at what coordinates? Is the area obstructed?

**Responsibilities:**
- Read a block at a specific world coordinate from the chunk cache
- Batch-scan a rectangular region
- Compare scanned region against a schematic to identify discrepancies
- Check if a position is obstructed (entity, fluid, wrong block) before placing
- Verify a placement succeeded (was the block actually placed?)
- Return results efficiently for hot-path queries

**Public API:**
```
getBlockAt(worldX, worldY, worldZ) → BlockState
scanRegion(minPos, maxPos) → ScanResult
compareToSchematic(scanResult, schematic, origin) → List<Discrepancy>
isObstructed(worldX, worldY, worldZ) → boolean
verifyBlock(worldX, worldY, worldZ, expectedMaterial) → boolean
```

**ScanResult:** A sparse grid of `BlockState` keyed by position.
**Discrepancy:** `(worldPos, expected, actual)`.
**BlockState:** Wrapper around `(blockType, metadata/state)` with helper methods (`isAir()`, `isReplaceable()`, `equals(material)`).

**Dependencies:** Zenith `CACHE.getChunkCache()` (only reads loaded chunks — chunk loading is Zenith's responsibility), Schematic Manager (for comparison)

**Must NOT:** Modify the world. It is read-only. Must NOT force chunk loading (Zenith handles that via the pathfinder).

**Zenith integration:** Reads from `CACHE.getChunkCache()`. Relies on Zenith to have chunks loaded (the pathfinder naturally loads chunks as the bot moves).

---

### 11. Schematic Manager

**Purpose:** Load, parse, and provide O(1) lookup into map art schematics.

**Responsibilities:**
- Load schematics from `.litematic`, `.nbt`, or `.schem` files
- Parse the block palette (map material ID → Minecraft block/item)
- Provide `getBlockAt(x, y)` for printer placement lookups
- Validate that the schematic is compatible (correct dimensions, supported blocks)
- Cache parsed schematics in memory
- Support multiple schematics loaded simultaneously (for multi-map projects)

**Public API:**
```
loadSchematic(path) → Schematic
getSchematic(id) → Optional<Schematic>
getBlockAt(schematicId, x, y) → Material
getPalette(schematicId) → Map<Integer, Material>
getWidth(schematicId) → int
getHeight(schematicId) → int       // for map art, width==height==128
getTotalBlocks(schematicId) → int
unloadSchematic(id)
validate(schematicId) → ValidationResult
```

**Schematic data class:**
```
class Schematic {
    String id;
    int width, height;           // 128×128 for standard map art
    Material[width][height];     // dense grid for O(1) lookup
    Map<Integer, Material> palette;
    SchematicFormat format;
}
```

**SchematicFormat enum:** `LITEMATICA`, `SPONGE_SCHEMATIC`, `NBT_STRUCTURE`

**Dependencies:** File I/O, NBT parsing library (via Zenith's bundled dependencies or shaded)

**Must NOT:** Know about world state. Pure data holder + loader. No Minecraft interaction.

---

### 12. Configuration

**Purpose:** All user-configurable settings. Serialized automatically by Zenith.

**Responsibilities:** Hold every tunable value in one typed POJO.

**Public API (config fields):**
```
class LoomConfig {
    BuildConfig build;           // schematic path, world origin, placement speed, strategy
    StorageConfig storage;       // chest coordinates, restock thresholds
    RecoveryConfig recovery;     // max retries, combat flee distance, reconnect delay
    AntiAFKConfig antiAfk;       // movement pattern, interval
    PlacementConfig placement;   // ticks between placements, max retries, verification enabled
    MiscConfig misc;             // debug mode, log verbosity
}
```

**Dependencies:** Zenith `PluginAPI.registerConfig()` (auto JSON serialization), none else (config has zero logic)

**Must NOT:** Contain runtime state or mutable state the subsystems write to (that's Progress Tracker's job).

---

### 13. Commands

**Purpose:** User interface for controlling Loom. Available in terminal, in-game, and Discord (inherited from Zenith `Command`).

**Responsibilities:**
- Provide commands that call subsystem APIs — never bypass them
- `loom build start <schematic>` — start a build job
- `loom build pause` / `loom build resume` / `loom build cancel`
- `loom status` — show current job, progress percentage, inventory status, position
- `loom schematic load <path>` / `loom schematic list` / `loom schematic info <id>`
- `loom storage set <x> <y> <z>` / `loom storage restock`
- `loom origin set <x> <y>` / `loom origin here`
- `loom config <key> <value>` — tweak settings at runtime

**Dependencies:** Every major subsystem (to query status and issue commands), Zenith `Command` base class, Zenith `CommandCategory.MODULE`

**Must NOT:** Contain business logic. Commands are thin wrappers that call subsystem APIs, format output, and return.

**Zenith integration:** Each command extends `Command`, registered via `pluginAPI.registerCommand()`.

---

### 14. Loom Events

**Purpose:** Loom-specific event bus for decoupled cross-cutting communication. Separate from Zenith's `EVENT_BUS` for Loom-internal concerns, though Loom also subscribes to Zenith events where needed.

**Responsibilities:** Define typed events that travel between subsystems without direct coupling.

**Event catalog:**
| Event | Publisher | Subscribers |
|---|---|---|
| `PrintStartedEvent` | Printer Controller | Recovery System, Commands |
| `PrintPausedEvent` | Task Scheduler | Recovery System |
| `PrintResumedEvent` | Task Scheduler | Recovery System |
| `PrintCompletedEvent` | Printer Controller | Job Manager, Commands |
| `PrintFailedEvent` | Printer Controller | Recovery System, Commands |
| `BlockPlacedEvent(x,y,material)` | Placement Engine | Progress Tracker, World Scanner |
| `RowCompletedEvent(row)` | Printer Controller | Progress Tracker (flush to disk) |
| `RestockNeededEvent(materials)` | Inventory Manager | Task Scheduler |
| `RestockCompletedEvent` | Chest Restocking | Task Scheduler, Inventory Manager |
| `RestockFailedEvent(reason)` | Chest Restocking | Task Scheduler, Commands |
| `RecoveryStartedEvent(type)` | Recovery System | Task Scheduler |
| `RecoveryCompletedEvent(type)` | Recovery System | Task Scheduler |
| `BotStuckEvent(position, duration)` | Navigation | Recovery System |
| `CombatDetectedEvent(entity)` | Recovery System (monitoring) | Task Scheduler |
| `StateLoadedEvent(jobId, progress)` | Progress Tracker | Printer Controller |

**Dependencies:** None. Pure data records.

**Must NOT:** Contain logic. Events are immutable data carriers.

**Zenith integration:** Loom may use its own internal event dispatcher (a simple listener pattern) or piggyback on Zenith's `EVENT_BUS` for events that other Zenith modules might find useful. For Loom-internal events, a separate dispatcher avoids polluting Zenith's bus.

---

### 15. Logging

**Purpose:** Structured, tagged logging for debugging and monitoring.

**Responsibilities:**
- Provide subsystem-tagged log methods
- Support log levels (debug, info, warn, error)
- Emit key metrics as structured log lines (blocks placed, time per row, restock frequency)

**Public API:**
```
info(tag, message, args...)
debug(tag, message, args...)
warn(tag, message, args...)
error(tag, message, args..., throwable)
metric(name, value, tags...)
```

**Dependencies:** Zenith `ComponentLogger` (via `PluginAPI.getLogger()`)

**Must NOT:** Be the progress tracker, event bus, or data store.

---

## Subsystem Dependency Graph

```
                  ┌──────────────┐
                  │ Task Scheduler│ ← top-level orchestrator
                  └──────┬───────┘
                         │
          ┌──────────────┼──────────────┐
          │              │              │
   ┌──────▼──────┐ ┌─────▼──────┐ ┌─────▼──────┐
   │ Job Manager │ │Printer Ctl │ │ Recovery   │
   └──────┬──────┘ └─────┬──────┘ │ System     │
          │              │        └─────┬──────┘
          │       ┌──────┼──────┐      │
          │       │      │      │      │
   ┌──────▼──┐ ┌──▼──┐ ┌─▼──┐ ┌─▼──┐   │
   │Progress │ │Place│ │Nav │ │Inv │   │
   │Tracker  │ │ment │ │    │ │Mgr │   │
   └─────────┘ │Eng  │ └─┬──┘ └─┬──┘   │
               └──┬──┘   │      │      │
                  │      │  ┌───▼──────▼──┐
           ┌──────▼──┐   │  │    Chest     │
           │  World   │   │  │  Restocking  │
           │ Scanner  │   │  └──────────────┘
           └────┬─────┘   │
                │         │
           ┌────▼─────────▼──┐
           │    Schematic    │
           │    Manager      │
           └─────────────────┘

Cross-cutting (used by many):
  ┌──────────┐ ┌──────────┐ ┌──────────┐
  │ Config   │ │ Events   │ │ Logging  │
  └──────────┘ └──────────┘ └──────────┘
```

---

## Control Flow: Single Tick

```
ClientBotTick fires
  │
  ├─ LoomModule.handleTick()
  │    │
  │    ├─ RecoverySystem.tick()
  │    │    ├─ Check health → dead? → RecoverySystem.onDeath()
  │    │    ├─ Check connection → disconnected? → RecoverySystem.onDisconnect()
  │    │    └─ Is recovering? → advance recovery step
  │    │
  │    ├─ if recovering: return  (no other work while recovering)
  │    │
  │    └─ TaskScheduler.tick()
  │         │
  │         ├─ InventoryManager.tick()
  │         │    └─ Materials critically low? → emit RestockNeededEvent
  │         │
  │         ├─ If RestockNeededEvent pending:
  │         │    └─ Start ChestRestocking task (preempts current task)
  │         │
  │         ├─ If ChestRestocking is active:
  │         │    └─ ChestRestocker.tick() → navigate, open chest, withdraw
  │         │
  │         ├─ If print task is active:
  │         │    └─ PrinterController.tick()
  │         │         │
  │         │         ├─ Get next position from ProgressTracker
  │         │         ├─ Skip if already placed (check ProgressTracker)
  │         │         ├─ WorldScanner.getBlockAt() → skip if correct block
  │         │         ├─ InventoryManager.hasMaterial() → skip if missing
  │         │         ├─ Place cooldown elapsed? → continue
  │         │         ├─ Navigator.goTo(placement position)
  │         │         ├─ PlacementEngine.placeBlock(position, material)
  │         │         ├─ WorldScanner.verifyBlock() → placed?
  │         │         └─ ProgressTracker.markPlaced(x, y, material)
  │         │
  │         └─ If no task and no recovery:
  │              └─ Idle (wait for command)
  │
  └─ end tick
```

---

## Data Flow: Starting a New Build

```
1. User runs:  /loom build start my_map
2. LoomBuildCommand:
     a. SchematicManager.loadSchematic("my_map") → Schematic
     b. Config.getBuildOrigin() → (worldX, worldY)
     c. JobManager.createJob(schematic.id, worldX, worldY) → Job
     d. ProgressTracker.clear(job.id)
     e. TaskScheduler.submit(PrintTask(job), priority=HIGH)
3. TaskScheduler.tick():
     a. Sees PrintTask is active
     b. PrinterController.start(job)
        - Reads schematic dimensions
        - Initializes traversal at (0, 0)
        - Begins printing
```

---

## Data Flow: Restock Mid-Build

```
1. PrinterController enters a new row
2. InventoryManager.getCount(material) < RESTOCK_THRESHOLD
3. InventoryManager emits RestockNeededEvent([material1, material2...])
4. TaskScheduler receives event:
     a. Pauses PrinterController (saves position to ProgressTracker)
     b. Creates RestockTask(materials)
     c. Submits at priority=CRITICAL (preempts all)
5. ChestRestocker.restock():
     a. Navigator.goToStorage()
     b. Open each configured chest
     c. For each material: withdraw X stacks
     d. Navigator.goToBuildArea()
     e. Navigate back to last placement position
     f. Emit RestockCompletedEvent
6. TaskScheduler receives RestockCompletedEvent:
     a. Resumes PrinterController from saved position
```

---

## Data Flow: Death Recovery

```
1. RecoverySystem monitors CACHE.getPlayerCache().isAlive() each tick
2. Bot dies → isAlive() returns false
3. RecoverySystem:
     a. ProgressTracker.save() → flush to disk
     b. Emit RecoveryStartedEvent(DEATH)
     c. Wait for respawn (tick loop monitors until alive again)
     d. Navigator.goToBuildArea()
     e. Navigator.goTo(lastPlacementPosition)
     f. InventoryManager re-equips (checks what was lost, logs warning)
     g. Emit RecoveryCompletedEvent(DEATH)
4. TaskScheduler resumes PrinterController
```

---

## Package Structure

```
org.loom/
│
├── LoomPlugin.java                      // @Plugin, implements ZenithProxyPlugin
├── LoomConfig.java                      // root config POJO
├── LoomModule.java                      // Module subclass, tick loop entry
│
├── scheduling/                          // Task Scheduler
│   ├── TaskScheduler.java               // interface
│   ├── LoomTaskScheduler.java           // impl
│   ├── Task.java                        // abstract base
│   ├── TaskHandle.java                  // identifier + state
│   ├── TaskPriority.java                // enum: CRITICAL, HIGH, NORMAL, LOW
│   ├── TaskState.java                   // enum: PENDING, RUNNING, PAUSED, COMPLETED, FAILED
│   ├── PrintTask.java                   // task wrapping PrinterController
│   └── RestockTask.java                 // task wrapping ChestRestocking
│
├── jobs/                                // Job Manager
│   ├── JobManager.java                  // interface
│   ├── LoomJobManager.java              // impl
│   ├── Job.java                         // data: id, schematicId, origin, createdAt
│   ├── JobState.java                    // enum: QUEUED, ACTIVE, PAUSED, COMPLETED, CANCELLED
│   └── JobProgress.java                 // data: totalBlocks, placedBlocks, percent
│
├── printing/                            // Printer Controller + Placement Engine
│   ├── PrinterController.java           // interface
│   ├── LoomPrinterController.java       // impl
│   ├── PrintStrategy.java               // interface for traversal order
│   ├── RowMajorStrategy.java
│   ├── ColumnMajorStrategy.java
│   ├── LayerStrategy.java
│   ├── PlacementEngine.java             // interface
│   ├── LoomPlacementEngine.java         // impl
│   ├── PlacementResult.java             // enum
│   └── Rotation.java                    // value class: yaw, pitch
│
├── navigation/                          // Navigation
│   ├── Navigator.java                   // interface
│   ├── LoomNavigator.java               // impl
│   └── NavigationResult.java            // enum: ARRIVED, STUCK, PATH_FAILED
│
├── inventory/                           // Loom Inventory Manager
│   ├── LoomInventoryManager.java        // interface
│   ├── LoomInventoryManagerImpl.java    // impl
│   ├── MaterialLedger.java              // tracks counts per material
│   ├── SlotReservation.java             // hotbar slot → material binding
│   ├── MaterialRequest.java             // material + quantity needed
│   │
│   └── restock/                         // Chest Restocking
│       ├── ChestRestocker.java          // interface
│       ├── LoomChestRestocker.java      // impl
│       ├── RestockRequest.java          // List<MaterialRequest> + storage coords
│       └── RestockStation.java          // chest location configuration
│
├── recovery/                            // Recovery System
│   ├── RecoverySystem.java              // interface
│   ├── LoomRecoverySystem.java          // impl
│   ├── RecoveryReason.java              // enum: DEATH, DISCONNECT, STUCK, COMBAT, WORLD_MISMATCH
│   ├── RecoveryAction.java              // abstract, subclassed per reason
│   ├── DeathRecovery.java
│   ├── DisconnectRecovery.java
│   └── CombatRecovery.java
│
├── state/                               // Progress Tracker
│   ├── ProgressTracker.java             // interface
│   ├── LoomProgressTracker.java         // impl
│   ├── ProgressEntry.java               // x, y, material placed
│   └── ProgressStore.java               // file serialization/deserialization
│
├── scanning/                            // World Scanner
│   ├── WorldScanner.java                // interface
│   ├── LoomWorldScanner.java            // impl
│   ├── ScanRegion.java                  // bounding box
│   ├── ScanResult.java                  // Map<BlockPos, BlockState>
│   ├── BlockState.java                  // wrapper around Zenith block data
│   └── Discrepancy.java                 // expected vs actual block
│
├── schematic/                           // Schematic Manager
│   ├── SchematicManager.java            // interface
│   ├── LoomSchematicManager.java        // impl
│   ├── Schematic.java                   // data: id, width, height, materials[][], palette
│   ├── SchematicPalette.java            // palette entry: id → Material
│   ├── SchematicFormat.java             // enum: LITEMATICA, SPONGE, NBT_STRUCTURE
│   ├── SchematicLoader.java             // interface per format
│   ├── LitematicaLoader.java
│   ├── SpongeSchematicLoader.java
│   └── ValidationResult.java            // validation errors list
│
├── command/                             // Commands
│   ├── BuildCommand.java                // start, pause, resume, cancel builds
│   ├── SchematicCommand.java            // load, list, info
│   ├── StatusCommand.java               // overall status display
│   └── ConfigCommand.java               // runtime config tuning
│
├── event/                               // Loom Events
│   ├── LoomEvent.java                    // marker interface
│   ├── PrintStartedEvent.java
│   ├── PrintPausedEvent.java
│   ├── PrintResumedEvent.java
│   ├── PrintCompletedEvent.java
│   ├── PrintFailedEvent.java
│   ├── BlockPlacedEvent.java
│   ├── RowCompletedEvent.java
│   ├── RestockNeededEvent.java
│   ├── RestockCompletedEvent.java
│   ├── RestockFailedEvent.java
│   ├── RecoveryStartedEvent.java
│   ├── RecoveryCompletedEvent.java
│   ├── BotStuckEvent.java
│   ├── CombatDetectedEvent.java
│   └── StateLoadedEvent.java
│
├── log/                                 // Logging
│   └── LoomLogger.java                  // tagged logger wrapping ComponentLogger
│
└── util/                                // Internal utilities (none of the above)
    ├── BlockPos.java                     // or use Zenith's
    ├── Material.java                     // carpet color enum + item mapping
    ├── AsyncLoomEventBus.java            // simple thread-safe listener registry
    └── TickThrottle.java                 // rate limiting helper
```

---

## Startup Sequence

```
ZenithProxy starts
  → PluginManager discovers Loom JAR
  → PluginManager.loadPlugin()
    → LoomPlugin() instantiated
    → LoomPlugin.onLoad(pluginAPI)
       1. LOG = pluginAPI.getLogger()
       2. CONFIG = pluginAPI.registerConfig("loom-config", LoomConfig.class)
       3. SchematicManager.loadSchematics()  (preload from configured directory)
       4. ProgressTracker.load(activeJobId)  (resume last session)
       5. JobManager.loadAllJobs()
       6. pluginAPI.registerModule(loomModule)
       7. pluginAPI.registerCommand(BuildCommand)
       8. pluginAPI.registerCommand(SchematicCommand)
       9. pluginAPI.registerCommand(StatusCommand)
      10. pluginAPI.registerCommand(ConfigCommand)
      11. LOG.info("Loom loaded")

  → LoomModule.syncEnabledFromConfig()
    → LoomModule.enable()
      → subscribes to ClientBotTick, ClientBotTick.Starting, ClientBotTick.Stopped
      → if active job exists: TaskScheduler.submit(PrintTask(job))
```

---

## Key Design Decisions

1. **Printer Controller does NOT manage inventory restocking.** It signals need; the Task Scheduler preempts with a RestockTask. This keeps the printer simple and the restock logic isolated.

2. **Progress Tracker uses schematic-relative coordinates.** World → schematic offset is a simple translation. This way progress files are independent of where you build in the world. Same schematic, different world position, same progress semantics.

3. **Recovery System observes, does not participate.** The printer, navigator, and inventory manager don't know about recovery. Recovery watches externally and intervenes. This prevents recovery logic from leaking into every subsystem.

4. **Placement Engine is stateless between calls.** It takes a position+material, executes placement, returns result. It does not remember previous placements. That's Printer Controller's job.

5. **Loom has its own event bus (AsyncLoomEventBus) for internal events.** This avoids coupling Loom's event types to Zenith's global event bus, keeps Zenith's bus clean, and allows Loom to use typed, domain-specific events.

6. **All subsystem implementations are behind interfaces.** This enables testing with mocks/stubs, makes the system composable, and allows swapping implementations (e.g., different print strategies).

7. **Single tick = single action.** The `LoomModule.handleTick()` advances exactly one subsystem step per tick. No loops within a tick. This keeps the bot responsive and prevents tick starvation.

8. **Config is read-only at runtime.** Subsystems read config to determine behavior but never write to it. The only mutable persisted state is Progress Tracker. Config can be changed via commands and takes effect on the next tick.
