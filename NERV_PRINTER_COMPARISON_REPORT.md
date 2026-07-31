# Nerv Printer vs Loom — Feature Parity Comparison

> Analysis date: 2026-07-30
> Nerv Printer version: master (1.21.11, Meteor Client addon)
> Loom version: current (1.21.4, ZenithProxy plugin)

---

## 1. Overall Architecture

### Nerv Printer
- **Platform:** Meteor Client addon (Fabric mod), runs inside a Minecraft client
- **Pattern:** Monolithic state machine inside each module class (~1860 lines CarpetPrinter, ~2240 lines StaircasedPrinter)
- **Entry point:** `Addon.java` extends `MeteorAddon`, registers 3 modules
- **Threading:** Single-threaded, runs on the Minecraft client tick (`TickEvent.Pre`)
- **Lifecycle:** `onActivate()` / `onDeactivate()` per Meteor module lifecycle
- **Control:** User walks around holding forward; the module places blocks around them as they move

### Loom
- **Platform:** ZenithProxy plugin, runs headless on a server
- **Pattern:** 15 cleanly separated subsystems with interfaces, each ~100-400 lines
- **Entry point:** `LoomPlugin.java` implements `ZenithProxyPlugin`, wires 14 subsystems via constructor DI
- **Threading:** Single-threaded bot tick (`ClientBotTick`), async callbacks for non-blocking operations
- **Lifecycle:** `Module.enable()` / `Module.disable()` per Zenith module lifecycle
- **Control:** Fully autonomous — bot navigates itself via BARITONE pathfinder

### Assessment
Loom's modular architecture is a **substantial improvement**. Nerv's monolithic state machine with ~18 interleaved states is difficult to reason about, test, and extend. Loom's separation into Navigator, PlacementEngine, PrinterController, InventoryManager, etc. is far more maintainable.

---

## 2. Schematic Loading

### Nerv Printer
- **Format:** Vanilla NBT structure blocks (`.nbt`), NOT Litematica
- **Parser:** `Utils.getBlockPalette()` + `Utils.generateMapArray()` — reads `palette` (NbtList of CompoundTags with `Name` key) and `blocks` (NbtList of `{state: paletteIndex, pos: [x,y,z]}`)
- **Map data:** `Block[128][128]` array with Minecraft `Block` objects
- **Palette:** `HashMap<Integer, Pair<Block, Integer>>` — maps palette index to (Block, occurrence count)
- **Ignored blocks:** Configurable block blacklist for semi-transparent maps
- **Formats:** `.nbt` only

### Loom
- **Format:** Litematica (`.litematic`), Sponge (`.schem`)
- **Parser:** `LitematicaLoader.java` using viaversion-nbt. Reads GZip-compressed NBT, decodes packed `BlockStates` long[] with variable bit-width
- **Map data:** `String[materials][height][width]` — material name strings
- **Palette:** `Map<Integer, SchematicPalette>` — maps palette index to material name + isCarpet boolean
- **Ignored blocks:** Not yet implemented
- **Formats:** `.litematic` (full), `.schem` (stub)

### Assessment
| Feature | Nerv | Loom |
|---|---|---|
| Litematica support | No (`.nbt` only) | Yes |
| Sponge schematic support | No | Stub |
| Vanilla NBT support | Yes | No |
| Packed block state decoding | N/A (list format) | Yes (long[] bit-packed) |
| Ignored block filter | Yes | Missing |
| Material name resolution | Minecraft Block registry | Material.fromIdentifier() |

**Missing:** Vanilla `.nbt` structure block loader. This is the format Nerv uses — it's simpler than Litematica (palette + block list rather than packed long array). Loom should add an `NBTStructureLoader` for compatibility.

---

## 3. Printing / Placement

### Nerv Printer
- **Placement logic:** `tryPlacingBlock(BlockPos)` — finds material in hotbar slots, calls `BlockUtils.place(pos, Hand.MAIN_HAND, slot, rotate, 50, true, true, false)`
- **Traversal:** Column-by-column ("snake" pattern, north-south alternating). Multiple lines per run (`linesPerRun` setting, 1-5). Walks along a column, places blocks in reach.
- **Movement:** Player walks forward (`setForwardPressed(true)`) towards checkpoints. Rotates to face checkpoint direction. No pathfinding.
- **Range:** `placeRange` (1-5 blocks), `minPlaceDistance` (avoids collisions)
- **Speed control:** `placeDelay` in milliseconds. Calculates `allowedPlacements` based on elapsed time since last tick.
- **Verification:** After each "line" completes, scans for misplacements via `Utils.getInvalidPlacements()`. Compares expected block from `map[][]` to actual `BlockState` at position.
- **Error handling:** `ErrorAction` enum: Ignore, ToggleOff, Reset (full map reset), Repair (break + replace)
- **Checkpoints:** List of `(Vec3d, (action, targetBlock))` — walking targets with associated actions (lineEnd, refill, dump, reset, break, fillMap, cartographyTable, etc.)

### Loom
- **Placement logic:** `PlacementEngine.placeCarpet()` — pre-checks, slot selection, face detection, rotation submission to INPUTS, `ServerboundUseItemOnPacket` + `ServerboundSwingPacket` sent directly
- **Traversal:** Row-major via `RowMajorStrategy` — left→right, advance row. Strategy is replaceable (ColumnMajorStrategy, LayerStrategy stubs exist).
- **Movement:** Full BARITONE pathfinding via `Navigator.goTo()`. Non-blocking — one navigation step per tick.
- **Range:** `MAX_REACH = 4.5` in PlacementEngine
- **Speed control:** `placementDelayTicks` in PrinterController (configurable delay between placement and verification)
- **Verification:** `WorldScanner.verifyBlock()` — reads chunk cache, compares block name to expected material
- **Error handling:** 9-phase state machine in `LoomPrinterController`. Per-block retry budget. Automatic pause on NO_MATERIAL/OBSTRUCTED.
- **State machine:** SCAN → CHECK_MATERIAL → MOVE → WAIT_MOVE → EXECUTE_PLACE → VERIFY_PLACE → ADVANCE

### Assessment
| Feature | Nerv | Loom |
|---|---|---|
| Pathfinding | No (manual walking) | Yes (BARITONE) |
| Placement speed control | ms-based, batch-tick | Tick-based, one per tick |
| Traversal strategies | Snake pattern only | Row-major, pluggable strategies |
| Placement verification | Post-line scan | Post-placement (next tick) |
| Error repair | Break + replace | Retry (pauses if exhausted) |
| Map auto-reset | Yes (trapped chest) | Not yet |
| Multiple lines per pass | Yes (1-5) | No (one block per tick) |

**Missing:** The ability to place multiple blocks per tick (like Nerv's `linesPerRun`/`allowedPlacements` batch). Loom places one block per tick which is simpler and more reliable but potentially slower. For ZenithProxy, one-per-tick is appropriate since the bot is always active.

---

## 4. Map Generation (Cartography Table)

### Nerv Printer
- **Full map workflow:** After building, walks to map material chest → takes map + glass pane → walks to map center → right-clicks empty map (creates Filled Map) → walks map fill square → walks to cartography table → locks map with glass pane → puts filled map in finished map chest → walks to reset trapped chest → resets area → loads next NBT file
- **Map fill square:** Configurable radius for exploring to fill the map
- **Map retry:** Takes N retry empty maps in case generation fails
- **Move to finished folder:** Moves completed NBT files to `_finished_maps/`

### Loom
- **Not implemented.** Loom has no map generation, cartography table interaction, or map locking logic. This is outside the current scope (carpet map art construction only).

### Assessment
**Missing entirely.** This is the biggest gap — Nerv's workflow treats map generation as integral to the printing cycle. For anarchy servers like 6b6t, map generation is optional (you just need the carpet layout, not the actual filled map item). Whether Loom needs this depends on the use case.

---

## 5. Restocking / Inventory

### Nerv Printer
- **Material dictionary:** `HashMap<Item, ArrayList<Pair<BlockPos, Vec3d>>>` — maps item type to list of (chest position, open position) pairs
- **Restock detection:** Scans upcoming blocks via `getRequiredItems()`, calculates how many stacks are needed, checks if they fit in available slots
- **Restock flow:** Walk to nearest chest for the material → interact (open chest) → wait for inventory packet → take full stacks (shift-click) → close chest → continue. Multi-material restock supported (loops through items).
- **Slot management:** `availableSlots` (all usable slots), `availableHotBarSlots` (hotbar subset). `getDumpSlot()` identifies items to drop. `dumpStation` for discarding unneeded items.
- **Swap logic:** `swapIntoHotbar()` — sophisticated algorithm considering distance-to-next-use, item frequency, and empty slots. Uses `performSwap()` via `IClientPlayerInteractionManager.clickSlot()`.
- **Dump station:** Dedicated position to throw unwanted items. Bot walks there, rotates, and drops items.

### Loom
- **Material ledger:** `MaterialLedger` — counts per material, slot locations (O(1) lookup after refresh)
- **Restock detection:** `getRequiredRestock()` — returns materials below configurable threshold. Not yet integrated into printing loop.
- **Restock flow:** `ChestRestocker` interface exists, implementation is a stub. No chest interaction yet.
- **Slot management:** `reserveSlot()` / `releaseSlot()` — conceptual reservation only. No actual item movement.
- **Swap logic:** Not implemented — `reserveSlot()` returns a hotbar index but doesn't move items.

### Assessment
| Feature | Nerv | Loom |
|---|---|---|
| Material tracking | Per-item counts | Per-Material counts with slot locations |
| Hotbar swap algorithm | Sophisticated (lookahead, frequency, distance) | Not implemented |
| Restock chest interaction | Full (open, scan, shift-click, close) | Stub |
| Item dumping | Dedicated dump station | Not implemented |
| Multi-material restock | Yes (iterates restockList) | Not implemented |

**Missing:** Chest interaction (open/close/read/withdraw), item swap logic, item dumping. Loom's `LoomInventoryManagerImpl` tracks what's in inventory but doesn't move anything. This is by design — Zenith's `INVENTORY` system handles actual inventory actions — but the integration isn't written yet.

---

## 6. Multi-User / Slave System

### Nerv Printer
- **SlaveSystem:** Master-slave coordination via Minecraft chat (`/w` messages)
- **Registration:** Master sends `register` to nearby players, they `accept`
- **Interval distribution:** Divides the 128-column map among N+1 users. Master takes middle section, slaves get others via `interval:start:end` messages.
- **Commands:** `pause`, `start`, `skip`, `mine`, `remove` via chat
- **Error reporting:** Slaves send `error:x:z` to master
- **Anti-spam:** Configurable delay between messages, optional random suffix
- **State sync:** Slaves report `finished` when their interval is done. Master waits for all slaves before ending building.

### Loom
- **Not implemented.** No multi-bot coordination. ZenithProxy architecture is single-bot (see ZENITH_MULTI_ACCOUNT_REPORT.md).

### Assessment
**Missing entirely.** This is a fundamental architectural difference — Nerv runs on a Minecraft client where multiple players can be in-game simultaneously. ZenithProxy runs one bot per process. Loom's architecture explicitly prevents multi-bot via plugin API. Multiple bots would require separate ZenithProxy processes with external IPC.

---

## 7. Map Area Cache

### Nerv Printer
- **MapAreaCache:** Caches chunk data when chunks unload within the 128x128 map area
- **Purpose:** On anarchy servers, the render distance may be low. The cache ensures block state queries work even when chunks unload.
- **Method:** Listens to `UnloadChunkS2CPacket`, checks if chunk is within map area, caches the `Chunk` object before it unloads.
- **Clear check:** `isMapAreaClear()` iterates all 16,384 positions to check if all are air (for map reset confirmation).

### Loom
- **WorldScanner:** Reads from `CACHE.getChunkCache()` which ZenithProxy maintains as a persistent mirror of all received chunk data.
- **Advantage:** ZenithProxy's chunk cache never unloads chunks — it's a full mirror. No need for custom chunk caching.

### Assessment
| Feature | Nerv | Loom |
|---|---|---|
| Chunk unloading issue | Yes (client render distance) | No (proxy maintains all chunks) |
| Custom chunk cache | `MapAreaCache` | Not needed |
| Area clear check | `isMapAreaClear()` | Not implemented |

Loom's architecture is **superior** here — ZenithProxy already maintains a complete world mirror, eliminating the need for custom chunk caching.

---

## 8. NBT Format Support

### Nerv Printer
- **Format:** Vanilla Minecraft structure block NBT
- **Structure:**
  ```
  CompoundTag {
    "palette": ListTag [{ "Name": "minecraft:white_carpet" }, ...]
    "blocks": ListTag [{ "state": 0, "pos": [x, y, z] }, ...]
  }
  ```
- **Position extraction:** Scans all blocks to find min/max x/z, centers the map to a 128x128 grid, extracts only the highest Y layer

### Loom
- **Format:** Litematica (GZip NBT)
- **Structure:**
  ```
  CompoundTag {
    "Regions": {
      "<name>": {
        "Size": { "x": 128, "y": 1, "z": 128 }
        "BlockStatePalette": [{ "Name": "minecraft:white_carpet" }, ...]
        "BlockStates": long[] (packed palette indices)
      }
    }
  }
  ```
- **Decoding:** Variable bit-width packed palette indices in X→Z→Y order

### Assessment
Nerv's `.nbt` format and Loom's `.litematic` format are **different but both standard**. Loom should add a vanilla NBT structure loader for compatibility with existing Nerv map files and vanilla structure blocks.

---

## 9. Configuration System

### Nerv Printer
- **Config save/load:** JSON files via `ConfigSerializer`/`ConfigDeserializer` using Gson
- **Config data:** Reset position, cartography table, finished map chest, map material chests, dump station (yaw/pitch), map corner, material dictionary, printer type
- **Runtime config:** Meteor settings GUI (50+ settings across groups: General, Advanced, Multi User, Error Handling, Render)
- **Activation reset:** Option to reset all state on module toggle (allows pause/resume when disabled)

### Loom
- **Config:** `LoomConfig.java` — auto-serialized JSON POJO via ZenithProxy's `registerConfig()`
- **Config data:** Schematic path, build origin, storage coordinates, restock thresholds, recovery settings, timing
- **Runtime config:** 4 commands: `loom build`, `loom schematic`, `loom status`, `loom config`
- **Persistence:** `ProgressTracker` for per-block placement state (stub)

### Assessment
Equivalent capability. Loom uses ZenithProxy's built-in config system which auto-serializes. Nerv uses manual JSON with Gson. Both adequate.

---

## 10. Rendering

### Nerv Printer
- **3D rendering:** Via Meteor's `Render3DEvent` — boxes for map area (128x128 outline), chest positions, open positions (small cubes), checkpoints, special interaction points (reset, cartography table, dump station)
- **Color:** Configurable via color picker setting

### Loom
- **Not implemented.** ZenithProxy is headless. Optional in-game rendering via packets to connected players, but not implemented.

### Assessment
Not applicable — ZenithProxy is server-side. Optional spectator rendering could be added later via packet manipulation.

---

## 11. Fullblock / Staircased Printer

### Nerv Printer
- **StaircasedPrinter:** Second module for fullblock (non-carpet) maps. Supports:
  - Block recycling (mines placed blocks to feed back into item sorter)
  - Tool management (automatic best tool selection for breaking)
  - Jump logic for staircased maps
  - Bed sleeping (night skip)
  - Shears for wool
  - Configurable mining range

### Loom
- **Not implemented.** Loom is focused exclusively on carpet map art.

### Assessment
Outside Loom's current scope. The architecture could support it via a different `PrintStrategy` and material configuration.

---

## 12. Map Namer

### Nerv Printer
- **MapNamer:** Semi-automatically names Filled Map items using format `MapName_X_Y`
- **Interaction:** Pauses on anvil break / insufficient XP. Can be resumed.
- **Purpose:** Organizational — helps sort maps in chests by position

### Loom
- **Not implemented.** Out of scope.

---

## Feature Parity Checklist

| Subsystem | Nerv Capability | Loom Status |
|---|---|---|
| **Schematic loading** | Vanilla NBT (.nbt) | Litematica (.litematic) full; Sponge stub; NBT missing |
| **Block placement** | Meteor BlockUtils, batched per tick | Single-shot per tick via PlacementEngine |
| **Placement verification** | Post-line area scan | Post-placement WorldScanner.verifyBlock() |
| **Pathfinding** | Manual walk (checkpoints) | Full BARITONE pathfinding via Navigator |
| **Traversal** | Snake pattern (N-S alternating) | Row-major (pluggable strategies) |
| **Multiple lines per pass** | Yes (linesPerRun 1-5) | No (one block per tick) |
| **Inventory tracking** | Manual slot scanning | MaterialLedger with slot locations |
| **Hotbar swap** | Lookahead + frequency algorithm | Not implemented (returns slot index only) |
| **Restock from chests** | Full (open, scan, withdraw, close) | Stub (ChestRestocker interface only) |
| **Item dumping** | Dedicated dump station | Not implemented |
| **Map generation** | Cartography table (map + glass pane) | Not implemented |
| **Map area reset** | Trapped chest interaction | Not implemented |
| **Multi-user / slaves** | Chat-based master-slave | Not possible (single-bot Zenith) |
| **Chunk caching** | Custom MapAreaCache | Not needed (Zenith's chunk mirror) |
| **Error detection** | Is / Should block comparison | WorldScanner.compareToSchematic() |
| **Error repair** | Break + replace, reset, toggle-off | Retry (pauses if exhausted) |
| **Configuration** | 50+ settings, JSON save/load | 20 fields, auto JSON via Zenith |
| **Rendering** | 3D boxes in world | N/A (headless proxy) |
| **Fullblock printing** | StaircasedPrinter (recycle, tools) | Not in scope |
| **Map naming** | MapNamer (anvil naming) | Not in scope |
| **Sprint/movement control** | SprintMode (Off/NotPlacing/Always) | Via Navigator (BARITONE) |

---

## Critical Gaps

1. **Vanilla NBT loader** — Nerv's `.nbt` format is simpler than Litematica. Adding an `NBTStructureLoader` would enable loading existing Nerv map files.

2. **ChestRestocker implementation** — The interface exists but the implementation is a stub. Without it, Loom can't replenish materials autonomously.

3. **Item swap logic** — `reserveSlot()` returns a hotbar index but doesn't move items. The PlacementEngine needs to use Zenith's `INVENTORY` to actually swap items.

4. **Map area reset** — No mechanism to clear the build area when starting a new map (Nerv uses a trapped chest + area clear check).

5. **Error repair (break + replace)** — Loom retries placement but can't break a wrong block and re-place. This is needed for anarchy servers where griefing/damage causes misplacements.

6. **Progress persistence** — ProgressTracker interface exists but is a stub. Critical for resuming after death/disconnect.

---

## Where Loom Excels

1. **Architecture** — Clean separation of 15 subsystems vs Nerv's monolithic module. Easier to test, extend, and maintain.

2. **Pathfinding** — Full BARITONE pathfinding enables fully autonomous operation. Nerv requires manual walking.

3. **Litematica support** — Native support for the most common map art schematic format.

4. **World mirror** — ZenithProxy's chunk cache is a complete world mirror that never unloads. Nerv needs custom chunk caching for low render distance.

5. **Non-blocking design** — Every subsystem is tick-friendly. Nervous's synchronous inventory operations can stall the client.

6. **Config persistence** — Auto-serialized JSON via Zenith. Less boilerplate than Nerv's manual Gson.

---

## Verdict

**Loom can fully reproduce Nerv Printer's carpet printing behavior** once 3 gaps are addressed:

1. `NBTStructureLoader` (vanilla .nbt format) — 1 day
2. `LoomInventoryManagerImpl` item swap integration with Zenith's `INVENTORY` — 1 day  
3. `ChestRestocker` full implementation — 2-3 days

Map generation, multi-user, fullblock printing, and rendering are outside Loom's scope and not needed for automated carpet map art on 6b6t.
