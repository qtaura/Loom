# Loom vs Nerv Printer — Complete Feature Audit

> User-behavior comparison. Architecture is irrelevant — only outcomes matter.
> Question: *If a Nerv user opened Loom today, would it behave the same?*

---

## Priority 1 — Critical Path (Can you print a map?)

### 1. Schematic Loading

| | Nerv | Loom |
|---|---|---|
| **Purpose** | Load map art design from file | Same |
| **Formats** | `.nbt` (vanilla structure blocks) | `.litematic`, `.nbt` |
| **Palette parsing** | `getBlockPalette()` reads `{Name}` from NBT | `SchematicPalette` reads `Name` from palette entries |
| **Block resolution** | `Registries.BLOCK.get(Identifier.of(name))` → Minecraft Block | `Material.fromIdentifier("minecraft:" + name)` → Loom Material |
| **Map array** | `Block[128][128]` with position extraction | `String[128][128]` dense grid |
| **Air handling** | Blocks not in palette → ignored | Blocks with `minecraft:air` → skipped |
| **Ignored blocks** | Configurable via GUI block list | Not implemented |
| **File discovery** | `getNextMapFile()` sorts by name length then alphabetically; `_finished_maps/` folder for completed | Files loaded individually by path |
| **Multi-map support** | Auto-advances to next `.nbt` file after completion | Job queue via JobManager |

**Behavioral differences:**
- Nerv auto-discovers `.nbt` files in a folder and processes them sequentially. Loom requires explicit `loadSchematic(path)` per file.
- Nerv supports a block ignore list (e.g. skip white carpet for transparent maps). Loom does not.
- Nerv moves completed files to `_finished_maps/`. Loom tracks completion via Job state.

**Compatibility:** △ 85% — Core loading compatible. Missing: auto-file-discovery, block ignore list, finished-file folder.

**To reach full parity:** Ignored block filter (0.5 day), auto-file discovery (0.5 day).

---

### 2. Printer / Placement

| | Nerv | Loom |
|---|---|---|
| **Purpose** | Place blocks to build the map | Same |
| **Placement method** | `BlockUtils.place(pos, Hand.MAIN_HAND, slot, rotate, 50, true, true, false)` | `PlacementEngine.placeCarpet()` → rotation + packet send |
| **Rotation** | `Rotations.rotate(yaw, pitch, 50)` — Meteor Rotations API | `PlacementEngine.calculateLookAngle()` → `INPUTS.submit(yaw, pitch)` |
| **Multi-placement per tick** | `allowedPlacements = floor(timeDiff / placeDelay)` — batch places multiple blocks per tick based on elapsed time | One block per tick |
| **linesPerRun** | Places 1-5 columns in parallel (per pass) | One column at a time |
| **Traversal** | Snake pattern (north-south alternating per column) | Row-major (left→right, advance row) |
| **Placement verification** | Post-line scan: compares 128 positions against `map[][]`, logs errors | Per-block: `WorldScanner.verifyBlock()` after each placement |
| **Error detection** | `Utils.getInvalidPlacements()` — scans columns within working interval, compares actual vs expected block | `WorldScanner.compareToSchematic()` — full region comparison |
| **Error action** | Ignore / ToggleOff / Reset / Repair | Pause on exhaustion (no per-error-action config) |
| **checkpointBuffer** | 0.2 block tolerance for checkpoint arrival | Hard coded 2-block tolerance for navigation arrival |
| **minPlaceDistance** | Configurable minimum distance to avoid placing at player feet | Not implemented |

**Behavioral differences:**
- Nerv places up to N blocks per tick based on elapsed time. Loom places 1 per tick. On low-tickrate servers, Nerv is faster. On high-tickrate, both are similar.
- Nerv's `linesPerRun` allows placing 1-5 columns simultaneously (withing placement range). Loom places one block at a time. This is Nucleus's biggest speed advantage.
- Nerv walks forward continuously while placing blocks in range. Loom navigates to exact position, stops, places, advances. Different movement models.
- Nerv's snake pattern eliminates backtracking between columns. Loom's row-major requires walking back to start of each row.
- Nerv detects placement errors after each line and can auto-repair. Loom detects per-block and retries.

**Compatibility:** △ 75% — Core placement works. Missing: batch placement, snake traversal, multi-column per pass, min-place-distance.

**To reach full parity:** Snake strategy (0.5 day), minPlaceDistance (0.2 day).

---

### 3. Navigation / Movement

| | Nerv | Loom |
|---|---|---|
| **Purpose** | Move the player through the build area | Same |
| **Method** | Forward key held + rotation towards checkpoint | `BARITONE.pathTo()` pathfinding via Navigator |
| **Checkpoint system** | `calculateBuildingPath()` generates waypoints for each column. Player walks towards them. | `Navigator.goTo(x, z)` + poll `isNavigating()` |
| **Stuck detection** | None (just walks) | `Navigator.isStuck()` — position delta < 3 blocks for >10s |
| **Sprinting** | SprintMode: Off / NotPlacing / Always | Not implemented |
| **Obstacle avoidance** | Manual (player places blocks around them as they walk) | BARITONE handles automatically |

**Behavioral differences:**
- Loom's pathfinding is objectively superior — handles obstacles, water, climbing automatically. Nerv just holds W.
- Nerv's checkpoint system is simpler and more predictable for flat areas. Loom's pathfinder may take suboptimal paths.
- Nerv stops placing when walking between checkpoints (restocking, dump station). Loom always pathfinds between positions.

**Compatibility:** ✓ 100% — Loom's navigation is strictly better. No missing functionality.

---

### 4. Inventory Management

| | Nerv | Loom |
|---|---|---|
| **Purpose** | Track and select materials for placement | Same |
| **Material tracking** | Manual scan of 36 slots per operation | `MaterialLedger` — cached ledger refreshed on demand |
| **Slot selection** | Scans availableSlots for matching Item, calls `Utils.performSwap(slot, hotbarSlot)` | `inventoryManager.reserveSlot()` → returns hotbar index, auto-swaps via Zenith INVENTORY |
| **Swap algorithm** | `swapIntoHotbar()` — considerers distance-to-next-use, item frequency, and empty slots | Simple: first-come-first-served slot reservation |
| **Swap method** | `IClientPlayerInteractionManager.clickSlot(syncId, from, to, SWAP, player)` | Triple-click swap via `ClickItem` actions submitted to Zenith INVENTORY |
| **Dump station** | Dedicated position + rotation for throwing unwanted items. Items identified via `getDumpSlot()` | Not implemented |
| **Inv slots needed** | Warns if <2 free slots available | `getAvailableSlots()` reports count |

**Behavioral differences:**
- Nerv's swap algorithm is more sophisticated — it considers which hotbar item will be needed furthest in the future to minimize swaps. Loom uses simple first-come-first-served.
- Nerv has a dedicated dump station for discarding non-map items. Loom has no item dumping.
- Both track materials and swap into hotbar correctly.

**Compatibility:** △ 80% — Core inventory works. Missing: item dumping, sophisticated swap algorithm.

**To reach full parity:** Item dumping (1 day).

---

### 5. Restocking

| | Nerv | Loom |
|---|---|---|
| **Purpose** | Refill materials from chests when inventory runs low | Same |
| **Material dictionary** | `HashMap<Item, ArrayList<(chestPos, openPos)>>` — maps item to list of chest+open positions | `RestockRequest` with single storage coordinate |
| **Restock detection** | `getRequiredItems()` scans upcoming blocks, checks if materials fit in available slots | `LoomInventoryManager.getRequiredRestock()` — threshold-based |
| **Chest selection** | `getBestChest(item)` — nearest chest for the item, skips already-checked chests | Single storage location from config |
| **Withdrawal** | Shift-clicks full stacks from chest. Multi-material: loops through `restockList`. Handles partial stacks. | `LoomChestRestocker` shift-clicks via `ShiftClick` action. Scans chest from top. |
| **Multi-material** | Yes — iterates all materials in restock list, one at a time, with round-robin chest selection | Scans chest for all needed materials in one pass |
| **Multiple chests** | Yes — material can be in multiple chests, selects nearest | Single storage location |
| **Failover** | If chest empty, marks chest as checked, tries next chest for that material | Navigates back (no failover) |
| **preRestockDelay** | 10 ticks wait after opening chest before taking items | Not implemented |
| **postRestockDelay** | 10 ticks wait after restocking before continuing | Not implemented |
| **invActionDelay** | 2 ticks between each inventory action | 0 ticks (batch) — may trigger anti-cheat |

**Behavioral differences:**
- Nerv supports multiple chests per material with failover. Loom uses single storage location.
- Nerv has configurable delays before/after restocking and between actions. Loom uses batch actions.
- Nerv can partially withdraw (tracks amounts needed). Loom withdraws all matching stacks.
- Both achieve the same goal: replenish materials and resume printing.

**Compatibility:** △ 70% — Core restock works. Missing: multi-chest failover, configurable delays, partial withdrawal.

---

### 6. Progress & Persistence

| | Nerv | Loom |
|---|---|---|
| **Purpose** | Resume printing after interruption | Same |
| **Progress tracking** | None per-block. Interval-based resumption (slave system handles work distribution). Restarts from `workingInterval` start on activation reset. | `ProgressTracker` — boolean[128][128] grid, JSON persistence |
| **Job persistence** | Config save/load only (chest positions, map corner, material dict) — NOT per-block progress | `JobManager` — save/load jobs to disk. `loadAllJobs()` auto-recovers on startup. |
| **Resume on restart** | No — `activationReset.get()` determines whether state resets. If true, all state is lost on toggle. | Yes — `getInterruptedJob()` auto-resumes after proxy restart |
| **Row-level save** | None | Saves on every row completion |
| **File format** | Custom JSON via `ConfigSerializer`/`ConfigDeserializer` | Gson JSON with `{jobId, width, height, entries: []}` |

**Behavioral differences:**
- Loom has per-block progress persistence. Nerv has none. This is a categorical improvement.
- Nerv requires `activationReset = false` to preserve any state across module toggle. Loom always persists.
- Loom auto-resumes interrupted jobs on proxy restart. Nerv cannot — requires manual restart.

**Compatibility:** ✓ 100% — Loom surpasses Nerv on persistence.

---

### 7. Recovery

| | Nerv | Loom |
|---|---|---|
| **Purpose** | Handle interruptions during printing | Same |
| **Death recovery** | None explicitly. Player respawns, module might still be active. | `DeathRecovery` — wait respawn → navigate to build area → resume |
| **Disconnect recovery** | None. Relies on Meteor auto-reconnect. | `DisconnectRecovery` — wait reconnect → navigate back → resume |
| **Stuck recovery** | None (player just keeps walking forward) | `StuckRecovery` — cancel nav → back off 5 blocks → re-path |
| **Combat recovery** | None | Stub (returns immediately) |
| **Position reset** | `posResetTimeout` — 10 tick wait after server teleport before continuing | Not implemented |

**Behavioral differences:**
- Loom has active recovery for death, disconnect, and stuck. Nerv has none — just keeps trying.
- Nerv has a configurable position-reset timeout after server teleports. Loom relies on Zenith to handle.

**Compatibility:** ✓ 100%+ — Loom surpasses Nerv on recovery.

---

### 8. Map Area Reset

| | Nerv | Loom |
|---|---|---|
| **Purpose** | Clear build area before new map | Same |
| **Method** | Open trapped chest → wait for external redstone → poll `isMapAreaClear()` | Same (Nerv-compatible trapped-chest approach) |
| **Clear check** | 128×128 positions: `isAir() && fluidState.isEmpty()` | 128×128 positions: `isAir()` |
| **Chest close delay** | 10 ticks | 10 ticks |
| **Interact timeout** | 80 ticks | 80 ticks |
| **Auto-trigger** | End of build + on ErrorAction.Reset | Manual: submit ResetTask |
| **breakCarpetAboveReset** | Option to break carpet above trapped chest first | Not implemented |

**Behavioral differences:**
- Both use identical trapped-chest approach with matching delays.
- Reset auto-triggers at end of build in Nerv. Loom requires explicit invocation.
- Nerv optionally breaks carpet above reset chest. Loom doesn't.

**Compatibility:** △ 85% — Core reset behavior identical. Missing: auto-trigger, break-carpet-above.

---

### 9. Commands / User Interface

| | Nerv | Loom |
|---|---|---|
| **Purpose** | Control the printer | Same |
| **Toggle** | Meteor GUI toggle + keybind | `/loom build <schematic>` |
| **Pause/Resume** | Master-slave chat commands + GUI buttons | `/loom pause`, `/loom resume` |
| **Status** | Module info string shows current file name | `/loom status` — job, position, progress %, blocks |
| **Job list** | None | `/loom jobs` — all jobs with state and progress |
| **Origin set** | Interactive block-click registration wizard | `/loom origin here` |
| **Config** | 50+ settings in Meteor GUI panels | 20 fields in `LoomConfig` JSON |
| **Schematic management** | Auto-discovery from folder | `/loomSchematic load <path>` |

**Behavioral differences:**
- Nerv has a rich GUI with categorized settings panels. Loom has CLI commands + Discord embeds.
- Nerv's setup wizard walks you through selecting each chest/position by clicking blocks. Loom uses config file + commands.
- Both provide full control: start, pause, resume, cancel.

**Compatibility:** ✓ 95% — Full printing control available. GUI vs CLI is a platform difference (Meteor client vs ZenithProxy).

---

### 10. Chunk Handling

| | Nerv | Loom |
|---|---|---|
| **Purpose** | Read world state reliably | Same |
| **Method** | `MapAreaCache` caches chunks on unload; reads from world when loaded | `CACHE.getChunkCache()` — Zenith maintains complete chunk mirror |
| **Unloaded chunks** | Returns cached block state if available, warns if not | Returns `BlockState.AIR` — treats as empty |
| **Area clear check** | Requires all chunks loaded (cached or live) | Depends on Zenith chunk loading (BOT movement loads chunks) |

**Behavioral differences:**
- Loom's approach is superior — Zenith never unloads chunks. No custom caching needed.
- Nerv's approach is necessary for client-side mods (Minecraft unloads distant chunks).

**Compatibility:** ✓ 100% — Loom's approach is strictly better.

---

## Priority 2 — Important (Quality of life)

### 11. Item Dumping

| | Nerv | Loom |
|---|---|---|
| **Purpose** | Remove non-map items from inventory | Not implemented |
| **Method** | Walk to dump station position, rotate to dump yaw/pitch, drop items via `InvUtils.drop().slot(slot)` | — |
| **Dump detection** | `getDumpSlot()` finds items not in `requiredItems` among `availableSlots` | — |
| **Integration** | Runs before restocking and between maps | — |

**Compatibility:** ✗ 0% — Not implemented. Required when inventory fills with non-map items (cobble, dirt from walking, etc.).

**Complexity:** Small (1 day). Add `dumpUnwanted()` to InventoryManager, `dumpStation` position to config.

---

### 12. Ignored Blocks

| | Nerv | Loom |
|---|---|---|
| **Purpose** | Skip certain block types (transparent maps) | Not implemented |
| **Method** | Block list setting, filtered during palette loading | — |
| **Effect** | Blocks in ignore list are treated as "don't place" | — |

**Compatibility:** ✗ 0% — Not implemented. Needed for semi-transparent map art.

**Complexity:** Trivial (0.3 day). Add filter in SchematicManager during load.

---

### 13. Print Strategies (Traversal)

| | Nerv | Loom |
|---|---|---|
| **Purpose** | Control how the build area is traversed | Same |
| **Snake pattern** | North→South per column, alternates direction each column. `startNorthToSouth` reverses start direction. | Not implemented |
| **Row-major** | N/A (snake is the only pattern) | Implemented |
| **Column-major** | N/A | Stub |
| **Layer strategy** | N/A | Stub |

**Behavioral differences:**
- Nerv's snake pattern eliminates backtracking — bot places blocks going north, then turns around and places south on next column.
- Loom's row-major walks back to column start each row, wasting movement.

**Compatibility:** △ 75% — Row-major works but is less efficient. Snake pattern needed for parity.

**Complexity:** Small (0.5 day). Implement `SnakeStrategy` implementing `PrintStrategy`.

---

### 14. Sprint / Speed Control

| | Nerv | Loom |
|---|---|---|
| **Purpose** | Control bot movement speed | Not implemented |
| **Method** | `SprintMode`: Off / NotPlacing / Always. Toggles sprint key. | — |
| **Effect** | Faster traversal between columns | — |

**Compatibility:** ✗ 0% — Not implemented. Minor efficiency loss.

**Complexity:** Trivial (0.3 day). Add sprint toggle to Navigator/InputRequest.

---

### 15. Configuration Flexibility

| | Nerv | Loom |
|---|---|---|
| **Purpose** | User-customizable behavior | Same |
| **Settings count** | 50+ settings in 5 groups | 20 fields in flat structure |
| **Config save/load** | JSON via `ConfigSerializer`/Gson. Multiple config files supported. | Auto-serialized JSON via Zenith `registerConfig()`. Single file. |
| **Runtime changes** | GUI changes apply immediately | Changes apply after config reload (auto-save on command) |
| **preSwapDelay / postSwapDelay** | Configurable delays before/after hotbar swaps | Not configurable |
| **posResetTimeout** | Delay after server teleport | Not implemented |
| **retryInteractTimer** | Timeout for chest interactions | Hard-coded 80 ticks |
| **invActionDelay** | Delay between inventory actions | 0 (batch) |

**Behavioral differences:**
- Nerv has ~2.5x more configurable settings. Many are anti-cheat/anti-lag tweaks specific to anarchy servers.
- Loom's config is simpler by design — Zenith handles anti-cheat/anti-lag internally.

**Compatibility:** △ 80% — Core settings present. Missing: swap/restock delays, position-reset timeout.

---

## Priority 3 — Future / Advanced

### 16. Fullblock / Staircased Printer

| | Nerv | Loom |
|---|---|---|
| **Purpose** | Build fullblock (non-carpet) maps with recycling | Not implemented |
| **Method** | `StaircasedPrinter` — mines blocks to recycle, uses item sorter, supports staircased maps with jump logic, bed sleeping | — |
| **Tool management** | `getBestTool()` selects optimal tool for block type | — |
| **Recycling** | Breaks placed blocks, feeds back into item sorter for reuse | — |

**Compatibility:** ✗ 0% — Not in scope. Fullblock printing requires tool management, recycling, staircased map support.

---

### 17. Map Generation (Cartography Table)

| | Nerv | Loom |
|---|---|---|
| **Purpose** | Create filled map items from empty maps + glass panes | Not implemented |
| **Method** | Walk to map center, right-click empty map, walk fill-square, walk to cartography table, lock with glass pane | — |
| **Map retry** | Takes N extra maps in case generation fails | — |
| **Finished map storage** | Puts filled maps in finished map chest | — |

**Compatibility:** ✗ 0% — Not in scope. On anarchy servers, filled maps are cosmetic — you only need the carpet layout.

---

### 18. Map Namer

| | Nerv | Loom |
|---|---|---|
| **Purpose** | Name filled maps with position coordinates | Not implemented |
| **Method** | Anvil interaction, format `MapName_X_Y`, pauses on XP/break | — |

**Compatibility:** ✗ 0% — Not in scope. Organizational utility only.

---

### 19. Multi-User / Slave System

| | Nerv | Loom |
|---|---|---|
| **Purpose** | Multiple players building same map simultaneously | Not implemented |
| **Method** | Chat-based `/w` commands. Master assigns intervals. Slaves report completion/errors. | — |
| **Interval distribution** | Divides 128 columns among N+1 users. Master gets middle section. | — |
| **Anti-spam** | Configurable message delay + random suffix | — |

**Compatibility:** ✗ 0% — Not possible in Zenith's single-bot architecture. Would require multiple ZenithProxy processes with external IPC.

---

### 20. Rendering

| | Nerv | Loom |
|---|---|---|
| **Purpose** | Visual feedback of map area, chests, checkpoints | Not applicable |
| **Method** | `Render3DEvent` — colored boxes in world | — |
| **Why not in Loom** | ZenithProxy is headless. Optional spectator rendering via packets. | — |

**Compatibility:** N/A — Platform difference (client mod vs headless proxy). Not a feature gap.

---

### 21. Interactive Setup Wizard

| | Nerv | Loom |
|---|---|---|
| **Purpose** | Configure positions by clicking blocks | Not implemented |
| **Method** | `State.SelectingMapArea` → `SelectingReset` → `SelectingTable` → `SelectingDumpStation` → `SelectingFinishedMapChest` → `SelectingChests` | Config file + `/loom origin here` |
| **Storage** | Saves/loads to `_configs/` JSON files | `LoomConfig` auto-JSON |

**Compatibility:** △ 50% — Config-driven vs interactive wizard. Config approach is cleaner for headless use. Wizard is more user-friendly for GUI users.

---

## Summary Matrix

| Subsystem | Status | Compatibility |
|---|---|---|
| Schematic Loading | △ | 85% |
| Printer / Placement | △ | 75% |
| Navigation / Movement | ✓ | 100% |
| Inventory Management | △ | 80% |
| Restocking | △ | 70% |
| Progress & Persistence | ✓ | 100%+ |
| Recovery | ✓ | 100%+ |
| Map Area Reset | △ | 85% |
| Commands / UI | ✓ | 95% |
| Chunk Handling | ✓ | 100% |
| **Priority 1 Average** | | **89%** |
| Item Dumping | ✗ | 0% |
| Ignored Blocks | ✗ | 0% |
| Print Strategies | △ | 75% |
| Sprint / Speed | ✗ | 0% |
| Configuration | △ | 80% |
| **Priority 2 Average** | | **31%** |
| Fullblock Printer | ✗ | 0% |
| Map Generation | ✗ | 0% |
| Map Namer | ✗ | 0% |
| Multi-User | ✗ | 0% |
| Rendering | N/A | — |
| Setup Wizard | △ | 50% |
| **Priority 3 Average** | | **8%** |

**Overall weighted (P1×0.6 + P2×0.3 + P3×0.1): 66%**

---

## Priority 1 Roadmap (Must Complete)

| # | Feature | Effort | Impact |
|---|---|---|---|
| P1-1 | Ignored block filter | 0.3 day | Transparent map support |
| P1-2 | Snake traversal strategy | 0.5 day | Eliminates backtracking — major speed improvement |
| P1-3 | Configurable restock delays | 0.3 day | Anti-cheat compatibility on strict servers |
| P1-4 | Auto-file discovery | 0.5 day | Seamless multi-map workflow |
| P1-5 | Auto-trigger reset on job complete | 0.3 day | Matches Nerv's end-of-build reset |

**Total:** ~2 days to reach ~95% Priority 1 compatibility.

---

## Priority 2 Roadmap (Should Complete)

| # | Feature | Effort |
|---|---|---|
| P2-1 | Item dumping | 1 day |
| P2-2 | Sprint mode | 0.3 day |
| P2-3 | Configurable swap delays | 0.3 day |
| P2-4 | Multi-chest restock failover | 1 day |
| P2-5 | Finished-file folder | 0.3 day |

**Total:** ~3 days.

---

## Priority 3 Roadmap (Future)

| # | Feature | Effort |
|---|---|---|
| P3-1 | Fullblock printer | 5-7 days |
| P3-2 | Map generation | 3 days |
| P3-3 | Multi-bot IPC | Weeks |

---

## Verdict

**Can an experienced Nerv Printer user switch to Loom today without losing meaningful functionality?**

**MOSTLY.**

Evidence:
- Core printing pipeline: 89% compatible. A user can load a schematic, print a full map, restock, reset, and recover from interruptions.
- What's missing for daily use: ignored blocks (transparent maps), snake traversal (speed), item dumping (inventory management), configurable restock delays (anti-cheat). None are show-stoppers but all impact quality-of-life.
- With ~2 days of P1 work, compatibility rises to ~95% — at which point the answer becomes YES for all realistic carpet-printing use cases.
- Loom surpasses Nerv on persistence, recovery, and pathfinding. These are meaningful advantages on anarchy servers where death and disconnection are common.
