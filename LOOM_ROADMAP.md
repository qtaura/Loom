# Loom Long-Term Roadmap

> From MVP to full Nerv Printer parity.
> Based on `NERV_PRINTER_COMPARISON_REPORT.md`.

---

## Completed Subsystems

| # | Subsystem | Status |
|---|---|---|
| 1 | Navigator | Complete |
| 2 | WorldScanner | Complete |
| 3 | PlacementEngine | Complete |
| 4 | SchematicManager (Litematica) | Complete |
| 5 | LoomInventoryManager (ledger + reservation) | Complete |
| 6 | PrinterController (state machine) | Complete |

---

## Feature Categorization

### Critical
Required before the bot can autonomously complete a full map from start to finish. Without these, the bot cannot recover from interruptions, restock materials, or resume work.

| # | Feature | New or Existing Subsystem | Dependencies | Complexity |
|---|---|---|---|---|
| C1 | ProgressTracker | Existing (`state/`) — implement `LoomProgressTracker` + `ProgressStore` | None (file I/O only) | Medium (2 days) |
| C2 | Item swap via Zenith INVENTORY | Existing (`inventory/`) — integrate `LoomInventoryManagerImpl.reserveSlot()` with `INVENTORY.submit()` | Zenith `INVENTORY` | Small (1 day) |
| C3 | TaskScheduler | Existing (`scheduling/`) — implement `LoomTaskScheduler` | PrinterController, ChestRestocker (for task types) | Medium (2 days) |
| C4 | ChestRestocker | Existing (`inventory/restock/`) — implement `LoomChestRestocker` | Navigator, LoomInventoryManager, Zenith `INVENTORY`, `BARITONE` | Large (3-4 days) |
| C5 | RecoverySystem | Existing (`recovery/`) — implement `LoomRecoverySystem` + 3 recovery actions | Navigator, ProgressTracker, TaskScheduler | Large (3 days) |
| C6 | JobManager | Existing (`jobs/`) — implement `LoomJobManager` | ProgressTracker, SchematicManager | Medium (2 days) |
| C7 | Block breaking (for error repair) | Existing (`printing/`) — add `breakBlock()` to PlacementEngine | Zenith `BARITONE.breakBlock()`, WorldScanner | Small (1 day) |
| C8 | Error repair orchestration | Existing (`printing/`) — add repair phase to PrinterController | Block breaking, PlacementEngine, WorldScanner | Medium (2 days) |

### Important
Required to reach full Nerv Printer feature parity for carpet map art.

| # | Feature | New or Existing Subsystem | Dependencies | Complexity |
|---|---|---|---|---|
| I1 | Vanilla NBT loader | New (`schematic/NBTStructureLoader.java`) | viaversion-nbt | Small (1 day) |
| I2 | Map area reset (clear all blocks) | Existing (`printing/`) — add ResetController or integrate into PrinterController | Block breaking, Navigator, WorldScanner | Medium (2 days) |
| I3 | Item dumping (discard unneeded) | Existing (`inventory/`) — add `dumpUnwanted()` to LoomInventoryManager | Zenith `INVENTORY` (drop action) | Small (1 day) |
| I4 | ColumnMajorStrategy | Existing (`printing/`) — implement existing stub | PrintStrategy interface | Small (0.5 day) |
| I5 | Ignored block filter | Existing (`schematic/`) — add `ignoredBlocks` to SchematicManager | LoomConfig | Small (0.5 day) |
| I6 | Sprint/walk speed control | Existing (`navigation/`) — expose speed control in Navigator | BARITONE, InputRequest | Small (0.5 day) |

### Future
Major enhancements. Not required for Nerv parity but valuable long-term.

| # | Feature | New or Existing Subsystem | Complexity |
|---|---|---|---|
| F1 | Multi-bot coordination | New (`multibot/`) — IPC between ZenithProxy processes | Very Large (weeks) |
| F2 | Fullblock printing (Staircased) | New module + strategy + tool management | Large (5-7 days) |
| F3 | Map generation (cartography table) | New (`mapgen/`) — map item creation, glass pane locking | Large (3-4 days) |
| F4 | Map naming (anvil) | New (`naming/`) — anvil interaction, XP management | Medium (2 days) |
| F5 | Sponge schematic support | Existing (`schematic/`) — implement `SpongeSchematicLoader` | Medium (2 days) |
| F6 | Spectator rendering | New (`render/`) — packet-based overlay for connected players | Medium (2 days) |
| F7 | Batch placement per tick | Existing (`printing/`) — Place multiple blocks per tick | Medium (2 days) |
| F8 | Snake pattern traversal | Existing (`printing/`) — implement `SnakeStrategy` | Small (0.5 day) |

---

## Implementation Roadmap

### Phase 1 — MVP: Autonomous Single-Map Completion

Goal: The bot loads a schematic, prints the entire map, restocks autonomously, and resumes after interruption.

```
Phase 1a: ProgressTracker              ─── 2 days
  └─ LoomProgressTracker: markPlaced, isPlaced, getNextUnplaced
  └─ ProgressStore: JSON save/load to plugins/config/progress/<jobId>.json
  └─ Integrate into PrinterController.ADVANCE phase

Phase 1b: Item Swap Integration         ─── 1 day
  └─ LoomInventoryManagerImpl.reserveSlot() uses INVENTORY.submit()
  └─ swapIntoHotbar() with simple LRU eviction
  └─ Integrate into PlacementEngine pipeline

Phase 1c: Block Breaking                ─── 1 day
  └─ PlacementEngine.breakBlock(x, y, z) via BARITONE.breakBlock()
  └─ Used by error repair and map reset

Phase 1d: JobManager                    ─── 2 days
  └─ LoomJobManager: createJob, getActiveJob, saveJob, loadAllJobs
  └─ Jobs persisted to plugins/config/jobs/<id>.json
  └─ Auto-load active job on startup

Phase 1e: TaskScheduler                 ─── 2 days
  └─ LoomTaskScheduler: submit, cancel, pause, resume, tick
  └─ Priority queue with preemption
  └─ PrintTask + RestockTask integration
  └─ Integrate into LoomModule.handleBotTick()

Phase 1f: ChestRestocker                ─── 3-4 days
  └─ LoomChestRestocker state machine:
     NAVIGATE → OPEN_CHEST → SCAN → WITHDRAW → CLOSE → RETURN
  └─ Uses Navigator.goToStorage/goToBuildArea
  └─ Uses Zenith INVENTORY for shift-click withdraw
  └─ Uses Zenith BARITONE.getTo(Blocks.CHEST, true) for chest opening
  └─ Triggers RestockNeededEvent → TaskScheduler preempts with RestockTask

Phase 1g: Error Repair                  ─── 2 days
  └─ PrinterController adds REPAIR phase to state machine
  └─ On verification failure: break wrong block → dump → place correct
  └─ Configurable retry budget

Phase 1h: RecoverySystem                ─── 3 days
  └─ LoomRecoverySystem: onDeath, onDisconnect, onStuck, onCombat
  └─ DeathRecovery: wait respawn → navigate to build area → resume
  └─ DisconnectRecovery: wait reconnect → navigate back → resume
  └─ CombatRecovery: flee → wait → return → resume
  └─ Saves progress before any recovery action
  └─ Integrate into LoomModule tick (before TaskScheduler)

Phase 1i: Integration & End-to-End Test ─── 2 days
  └─ Wire all systems through LoomModule.tick()
  └─ End-to-end test: load schematic → print 128x128 map → restock → complete
  └─ Test death/disconnect recovery
  └─ Test error repair
```

**MVP total: ~18-20 days**

### Phase 2 — Nerv Parity: Carpet Map Art

Goal: Reach feature parity with Nerv Printer for carpet maps. Support both `.litematic` and `.nbt` formats. Multi-map job queues.

```
Phase 2a: NBTStructureLoader            ─── 1 day
  └─ Parse vanilla structure block NBT (palette + blocks list)
  └─ Extract highest-Y layer, center to 128x128 grid
  └─ Register in LoomSchematicManager

Phase 2b: Ignored Block Filter          ─── 0.5 day
  └─ LoomConfig.ignoredBlocks list
  └─ SchematicManager filters during loadSchematic()

Phase 2c: Item Dumping                  ─── 1 day
  └─ LoomInventoryManager.dumpUnwanted()
  └─ Identifies items not in material palette
  └─ Uses Zenith INVENTORY for drop actions

Phase 2d: Map Area Reset                ─── 2 days
  └─ PrinterController adds RESET phase
  └─ Scans build area, breaks any non-air blocks
  └─ Runs before starting new map in a job queue

Phase 2e: ColumnMajorStrategy           ─── 0.5 day
  └─ Implement existing stub
  └─ Configurable via LoomConfig.printStrategy

Phase 2f: Speed Control                 ─── 0.5 day
  └─ Navigator supports sprint toggle
  └─ Navigator supports configurable speed via InputRequest

Phase 2g: Multi-Map Job Queue           ─── 2 days
  └─ JobManager queues multiple jobs
  └─ PrinterController auto-advances to next job
  └─ ProgressTracker handles per-job state
```

**Phase 2 total: ~7.5 days**

### Phase 3 — Future: Beyond Nerv

Goal: Capabilities Nerv Printer never had. Multi-process coordination. Fullblock support.

```
Phase 3a: Multi-Bot Coordinator         ─── weeks
  └─ External process manager
  └─ IPC via filesystem or TCP
  └─ Work distribution (interval assignment)
  └─ Status synchronization

Phase 3b: Fullblock Printer             ─── 5-7 days
  └─ New module or strategy variant
  └─ Block recycling (break → pickup → sort)
  └─ Tool management (best tool selection)
  └─ Staircased map support (y-layer awareness)

Phase 3c: Spectator Rendering           ─── 2 days
  └─ Packet-based overlay rendering
  └─ Map area outline, progress bar, checkpoint indicators
  └─ Sent to all connected spectator sessions

Phase 3d: Sponge Schematic Support      ─── 2 days
  └─ Parse Sponge v2 format (Width/Height/Length, Palette, BlockData)
  └─ Varint byte[] decoding

Phase 3e: Map Generation                ─── 3 days
  └─ Cartography table interaction
  └─ Map + glass pane crafting
  └─ Map fill area exploration
```

---

## Dependency Graph

```
                    ┌──────────────────┐
                    │  ProgressTracker │ ← Phase 1a (no deps)
                    └────────┬─────────┘
                             │
              ┌──────────────┼──────────────┐
              │              │              │
     ┌────────▼────────┐ ┌──▼───────────┐  │
     │   Item Swap     │ │  JobManager  │  │
     │   (Phase 1b)    │ │  (Phase 1d)  │  │
     └────────┬────────┘ └──────┬───────┘  │
              │                 │          │
     ┌────────▼────────┐       │          │
     │  Block Breaking  │       │          │
     │   (Phase 1c)     │       │          │
     └────────┬─────────┘       │          │
              │                 │          │
     ┌────────▼─────────────────▼──────────▼──┐
     │           TaskScheduler (Phase 1e)      │
     └────────┬───────────────────────────────┘
              │
     ┌────────┼────────┐
     │        │        │
  ┌──▼─────┐ ┌▼──────┐ ┌▼──────────┐
  │Chest   │ │Error  │ │ Recovery   │
  │Restock │ │Repair │ │ System     │
  │(Ph 1f) │ │(Ph 1g)│ │ (Phase 1h) │
  └────────┘ └───────┘ └────────────┘

MVP complete after Phase 1i

Phase 2 features depend on MVP being complete
Phase 3 features are independent of each other
```

---

## Subsystem Changes Required

### New Subsystems

None. The 15 existing subsystems cover all needed functionality. New features integrate into existing subsystems:

| Feature | Integrates Into |
|---|---|
| NBT Structure Loader | `schematic/` (new loader class) |
| Block breaking | `printing/` (PlacementEngine method) |
| Error repair | `printing/` (PrinterController phase) |
| Item dumping | `inventory/` (LoomInventoryManagerImpl method) |
| Map area reset | `printing/` (PrinterController phase) |
| Multi-bot IPC | New top-level `multibot/` (Phase 3, optional) |
| Spectator rendering | New `render/` (Phase 3, optional) |

### Existing Subsystems to Modify

| Subsystem | What Changes |
|---|---|
| `PlacementEngine` | Add `breakBlock()` |
| `LoomInventoryManagerImpl` | Add `swapIntoHotbar()`, `dumpUnwanted()` |
| `PrinterController` | Add REPAIR phase, RESET phase |
| `LoomPrinterController` | Wire ProgressTracker.markPlaced(), retry budget |
| `LoomModule` | Wire all subsystems into tick loop |
| `LoomConfig` | Add `ignoredBlocks`, `printStrategy` fields |

---

## Recommended Next Implementation

**ProgressTracker (Phase 1a).**

Rationale:
1. **Zero dependencies.** Only needs file I/O. No other unfinished subsystem blocks it.
2. **Unlocks everything downstream.** JobManager, RecoverySystem, and PrinterController all depend on it.
3. **Highest risk if delayed.** Without progress persistence, any crash, death, disconnect, or proxy restart loses all work. On 6b6t, death is frequent.
4. **Immediate value.** Once implemented, PrinterController can call `markPlaced()` and the state machine becomes fully functional within a single session.

After ProgressTracker, the chain flows naturally:

```
ProgressTracker → Item Swap → Block Breaking → JobManager
                                                    ↓
          RecoverySystem ← ChestRestocker ← TaskScheduler
                    ↓
              Error Repair → MVP Complete
```

---

## Effort Summary

| Phase | Duration | Deliverable |
|---|---|---|
| Phase 1a-1i (MVP) | 18-20 days | Bot completes full map autonomously, restocks, resumes after death/disconnect |
| Phase 2a-2g (Parity) | 7-8 days | Full Nerv Printer feature parity for carpet maps |
| Phase 3a-3e (Future) | 3-5 weeks | Multi-bot, fullblock, rendering, sponge, map gen |
| **Total to Nerv parity** | **25-28 days** | Loom matches or exceeds all Nerv carpet printing capabilities |
