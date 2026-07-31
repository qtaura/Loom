# Nerv Map Production Pipeline — Reverse Engineering & Loom Design

> Analysis: Complete execution trace from print finish to next schematic start.
> Includes: every state transition, inventory interaction, chest interaction,
>   map generation, cartography table, map locking, finished map handling,
>   reset, scheduler interaction, retries, recovery.

---

## Part 1 — Exact Nerv Execution Trace

### Phase: Print Complete → Post-Print Cleanup

```
PRINT COMPLETES
  │  (all checkpoints processed, all slaves finished)
  │
  ▼
endBuilding()
  ├─ info("Finished building map")
  ├─ state = Walking
  ├─ knownErrors.clear()
  ├─ SlaveSystem.setAllSlavesUnfinished()
  ├─ bestChest = getBestChest(Items.CARTOGRAPHY_TABLE)
  │    └─ Finds nearest chest containing CARTOGRAPHY_TABLE item (maps/panes)
  ├─ checkpoints.add(dumpStation, "dump")
  ├─ checkpoints.add(bestChest.openPos, "mapMaterialChest")
  ├─ Files.renameTo(mapFile, _finished_maps/mapFile)  ← move completed .nbt
  └─ return true
```

**State transition:** Walking (player walks to next checkpoint)

### Phase: Item Dumping

```
Checkpoint "dump" reached
  │
  ▼
state = State.Dumping
  ├─ setForwardPressed(false)
  ├─ set player yaw/pitch to dump station rotation
  │
  ▼ (each tick while Dumping)
getDumpSlot()
  ├─ Calls getRequiredItems() to determine what materials are needed
  ├─ Calls Utils.getInvInformation(requiredItems, availableSlots)
  │    └─ Returns (dumpSlots, materialInInv)
  │    └─ dumpSlots: slots containing items NOT in requiredItems
  ├─ If dumpSlot == -1 (nothing to dump):
  │    ├─ refillInventory(invInformation.getRight())
  │    │    └─ Calculates remaining required items
  │    │    └─ Fills restockList with needed stacks
  │    │    └─ addClosestRestockCheckpoint() → adds "refill" checkpoint
  │    └─ state = Walking
  ├─ Else:
  │    ├─ Log: "Dumping <itemName> (slot <slot>)"
  │    ├─ InvUtils.drop().slot(dumpSlot)
  │    ├─ timeoutTicks = invActionDelay (2 ticks)
  │    └─ stays in Dumping for next slot
```

**State transitions:** Dumping → Walking (when all items dumped and inventory refilled)

**Key detail:** Dumping and restocking are interleaved. If the inventory has space for more materials after dumping, it restocks from chests immediately.

### Phase: Map Generation Setup

```
Checkpoint "mapMaterialChest" reached (with InventoryS2CPacket handler)
  │
  ▼
state = State.AwaitMapChestResponse
  ├─ interactWithBlock(mapMaterialChest)  ← opens chest
  │    └─ setForwardPressed(false)
  │    └─ setYaw/Pitch towards chest
  │    └─ BlockUtils.interact(hitResult, Hand.MAIN_HAND, true)
  │    └─ interactTimeout = retryInteractTimer (80 ticks)
  │
  ▼ (when InventoryS2CPacket arrives)
handleInventoryPacket():
  ├─ Scans chest slots 0 to size-37 (chest contents before player inventory)
  ├─ Searches for Items.MAP (empty map) → mapSlot
  ├─ Searches for Items.GLASS_PANE → paneSlot
  ├─ If mapSlot == -1 or paneSlot == -1:
  │    └─ warning("Not enough Empty Maps/Glass Panes")
  │    └─ return (stuck)
  ├─ Utils.getOneItem(mapSlot, false, availableSlots, availableHotBarSlots, packet)
  │    └─ Triple click: PICKUP map ←→ swap ←→ place back
  ├─ For retryMaps extra maps:
  │    └─ Utils.getOneItem(mapSlot, true, ...)  ← avoid first hotbar
  ├─ Utils.getOneItem(paneSlot, true, ...)  ← take glass pane
  ├─ mc.player.setSelectedSlot(availableHotBarSlots.get(0))
  ├─ checkpoints.add(mapCenter, "fillMap")
  └─ state = Walking
```

**Key detail:** Takes `retryMaps + 1` empty maps in case map generation fails (default: 3+1=4 maps). Glass pane goes to inventory (not hotbar).

### Phase: Map Creation

```
Checkpoint "fillMap" reached (at map center)
  │
  ▼
Right-click with empty map:
  ├─ mc.getNetworkHandler().sendPacket(
  │    new PlayerInteractItemC2SPacket(Hand.MAIN_HAND,
  │        Utils.getNextInteractID(), mc.player.getYaw(), mc.player.getPitch()))
  ├─ checkpoints.add(0, currentPosition, "verifyMap")  ← insert before next checkpoint
  └─ return
```

**Key detail:** The packet is sent but the filled map appears asynchronously. The next checkpoint ("verifyMap") checks if it succeeded.

### Phase: Map Verification & Fill Square

```
Checkpoint "verifyMap" reached
  │
  ▼
Scan inventory for Items.FILLED_MAP:
  │
  ├─ FOUND:
  │    ├─ If mapFillSquareSize == 0:
  │    │    └─ checkpoints.add(cartographyTable.openPos, "cartographyTable")
  │    ├─ Else (mapFillSquareSize > 0):
  │    │    ├─ checkpoints.add(goal + (-size, 0, size), "sprint")  ← NW corner
  │    │    ├─ checkpoints.add(goal + (size, 0, size), "sprint")   ← NE corner
  │    │    ├─ checkpoints.add(goal + (size, 0, -size), "sprint")  ← SE corner
  │    │    ├─ checkpoints.add(goal + (-size, 0, -size), "sprint") ← SW corner
  │    │    └─ checkpoints.add(cartographyTable.openPos, "cartographyTable")
  │    └─ The sprint checkpoints walk a square around the map to fill it
  │
  ├─ NOT FOUND (map generation failed):
  │    ├─ Scan inventory for Items.MAP (empty map)
  │    ├─ If found: Utils.performSwap(mapSlot, hotbarSlot[0])
  │    │    └─ checkpoints.add(0, goal, "fillMap")  ← retry
  │    ├─ If not found (ran out of maps):
  │    │    └─ checkpoints.add(bestChest.openPos, "mapMaterialChest")  ← get more
  └─ return
```

**Key detail:** Map fill square walks 4 corners to explore unknown chunks. `mapFillSquareSize` is configurable (default 1). The filled map item is created by right-clicking the empty map — the Minecraft client auto-fills it.

### Phase: Map Locking (Cartography Table)

```
Checkpoint "cartographyTable" reached
  │
  ▼
state = State.AwaitCartographyResponse
  ├─ interactWithBlock(cartographyTable.getLeft())  ← opens table
  │
  ▼ (when InventoryS2CPacket arrives)
handleInventoryPacket():
  ├─ searchingMap = true
  ├─ For each slot in availableSlots:
  │    ├─ Slot correction: if slot < 9 → slot += 30; else → slot -= 6
  │    │    └─ (maps inventory slot to cartography table slot index)
  │    ├─ If searchingMap && stack == Items.FILLED_MAP:
  │    │    └─ clickSlot(syncId, slot, 0, QUICK_MOVE, player)  ← shift-click into table
  │    │    └─ searchingMap = false
  │    ├─ If !searchingMap && stack == Items.GLASS_PANE:
  │    │    └─ clickSlot(syncId, slot, 0, QUICK_MOVE, player)  ← shift-click into table
  │    │    └─ break
  ├─ clickSlot(syncId, 2, 0, QUICK_MOVE, player)  ← take result (locked map)
  ├─ checkpoints.add(finishedMapChest.openPos, "finishedMapChest")
  └─ state = Walking
```

**Key detail:** The cartography table has 3 slots: [0] = map input, [1] = glass pane input, [2] = output (locked map). First shift-clicks the Filled Map into slot 0, then the Glass Pane into slot 1, then shift-clicks the result from slot 2.

### Phase: Finished Map Storage

```
Checkpoint "finishedMapChest" reached
  │
  ▼
state = State.AwaitFinishedMapChestResponse
  ├─ interactWithBlock(finishedMapChest.getLeft())  ← opens chest
  │
  ▼ (when InventoryS2CPacket arrives)
handleInventoryPacket():
  ├─ Scans inventory slots (size-36 to size) for Items.FILLED_MAP
  ├─ Shift-clicks filled map from inventory into chest
  ├─ If breakCarpetAboveReset && carpet above reset chest:
  │    └─ checkpoints.add(reset.openPos, "break", reset.blockPos.up())
  ├─ checkpoints.add(reset.openPos, "reset")
  └─ state = Walking
```

**Key detail:** The filled (locked) map goes into the finished map chest. Not part of the carpet-printing workflow but part of Nerv's full automation.

### Phase: Reset Area

```
Checkpoint "break" reached (optional, if breakCarpetAboveReset)
  │
  ▼
state = State.AwaitBlockBreak
  ├─ miningPos = reset block pos + up
  ├─ setForwardPressed(false)
  ├─ Rotations.rotate(yaw, pitch, 50)
  ├─ BlockUtils.breakBlock(miningPos, true)
  │
  ▼ (each tick while breaking)
  ├─ If block is now air → miningPos = null, state = Walking
  └─ Else → keep breaking

Checkpoint "reset" reached
  │
  ▼
state = State.AwaitResetResponse
  ├─ interactWithBlock(reset.getLeft())  ← opens trapped chest
  ├─ interactTimeout = retryInteractTimer (80 ticks)
  │
  ▼ (when InventoryS2CPacket arrives)
handleInventoryPacket():
  ├─ interactTimeout = 0
  ├─ closeNextInvPacket = false
  └─ closeResetChestTicks = resetChestCloseDelay (10 ticks)

After closeResetChestTicks countdown:
  ├─ mc.player.closeHandledScreen()
  ├─ checkpoints.add(mapCenter, "awaitClear")
  └─ state = Walking

Checkpoint "awaitClear" reached
  │
  ▼
state = State.AwaitAreaClear
  └─ setForwardPressed(false)

Each tick while AwaitAreaClear:
  └─ MapAreaCache.isMapAreaClear()
       ├─ For each of 128×128 positions:
       │    └─ blockState.isAir() && fluidState.isEmpty()
       └─ Returns true when all clear

When clear:
  └─ state = State.AwaitNBTFile
```

### Phase: Next File + Restart

```
State.AwaitNBTFile
  │
  ▼
prepareNextMapFile()
  ├─ mapFile = Utils.getNextMapFile(mapFolder, startedFiles, moveToFinishedFolder)
  │    └─ Sorts by name length, then alphabetically
  │    └─ Returns first .nbt not in startedFiles
  ├─ If null:
  │    ├─ "All nbt files finished"
  │    └─ toggle() (disable module)
  ├─ If found:
       └─ loadNBTFile()
            ├─ NbtIo.readCompressed(mapFile.toPath(), sizeTracker)
            ├─ palette = Utils.getBlockPalette(paletteList)
            ├─ Remove ignored blocks from palette
            ├─ map = Utils.generateMapArray(blockList, blockPaletteDict)
            └─ Log requirements

startBuilding()
  ├─ SlaveSystem.startAllSlaves()
  ├─ setupSlots()
  ├─ MapAreaCache.reset(mapCorner)
  ├─ calculateBuildingPath(startNorthToSouth, sprintFirst=true)
  ├─ checkpoints.add(dumpStation, "dump")
  └─ state = Walking   ← print cycle repeats
```

---

## Part 2 — Loom Architecture Design

### What Nerv Does vs. What Loom Needs

| Nerv Step | Required for Carpet Map Art? | Loom Equivalent |
|---|---|---|
| Dump unwanted items | Yes — frees inventory space | `LoomInventoryManager.dumpUnwanted()` → not implemented |
| Get maps + glass panes | No — cosmetic only | Out of scope |
| Fill map (right-click) | No — cosmetic only | Out of scope |
| Map fill square (walk corners) | No — cosmetic only | Out of scope |
| Lock map (cartography table) | No — cosmetic only | Out of scope |
| Store finished map | No — cosmetic only | Out of scope |
| Move completed `.nbt` file | Yes — organization | `BatchOrchestrator.moveToFinishedFolder()` — ✅ implemented |
| Break carpet above reset | Maybe — if interactions blocked | Not implemented |
| Reset area (trapped chest) | Yes — clears for next map | `LoomResetSystem` — ✅ implemented |
| Wait for area clear | Yes — confirmation | `LoomResetSystem.WAIT_CLEAR` — ✅ implemented |
| Load next file | Yes — batch workflow | `BatchOrchestrator.submitNextPrint()` — ✅ implemented |

### Recommended Loom Post-Print Flow

```
PrintTask.onComplete()
  │
  ├─ jobManager.completeJob()
  ├─ eventBus.publish(PrintCompletedEvent)
  │
  └─ batchOrchestrator.onPrintComplete(jobId)
       │
       ├─ 1. MOVE FILE: Files.move(file, _finished_maps/file)
       │       ✅ Implemented
       │
       ├─ 2. DUMP (Phase 2c — not yet implemented):
       │    Target implementation:
       │    └─ Submit DumpTask at HIGH priority
       │       └─ DumpTask.tick()
       │            ├─ Scan inventory for items NOT in schematic palette
       │            ├─ Drop each via INVENTORY.submit(DropItem)
       │            └─ When done → advance
       │
       ├─ 3. RESET:
       │    └─ Submit ResetTask(job, resetSystem) at HIGH priority
       │       └─ ResetTask.tick() → resetSystem.tick()
       │            ├─ NAV_TO_CHEST
       │            ├─ WAIT_CHEST → WAIT_DELAY → close
       │            ├─ NAV_TO_CENTER
       │            └─ WAIT_CLEAR → DONE
       │       └─ onComplete → orchestrator.onResetComplete(false)
       │       ✅ Implemented
       │
       └─ 4. NEXT FILE:
            └─ advanceToNextFile()
                 └─ submitNextPrint()
                      ├─ schematicManager.loadSchematic(file)
                      ├─ jobManager.createJob(schematicId, origin)
                      └─ taskScheduler.submit(PrintTask, NORMAL)
                 ✅ Implemented
```

### Updated BatchOrchestrator Design

```
BatchOrchestrator
  ├─ files: List<File>              (discovered .nbt/.litematic files)
  ├─ fileIndex: int                 (current position)
  ├─ phase: enum { PRINTING, DUMPING, RESETTING, IDLE }
  │
  ├─ onPrintComplete(jobId)
  │    ├─ moveToFinishedFolder(file)
  │    ├─ phase = DUMPING
  │    └─ submitDumpTask()          ← NEW
  │
  ├─ onDumpComplete()
  │    ├─ phase = RESETTING
  │    └─ submitResetTask()         ← existing
  │
  ├─ onResetComplete(failed)
  │    ├─ phase = IDLE
  │    └─ advanceToNextFile()       ← existing
  │
  └─ submitNextPrint()               ← existing
```

### What Changes Are Needed

| Component | Change |
|---|---|
| `BatchOrchestrator` | Add DUMPING phase, `onDumpComplete()` callback, tie dump → reset → next-print chain |
| `LoomInventoryManager` | Add `dumpUnwanted()` method — identifies items not in current schematic palette, drops via INVENTORY |
| `DumpTask` (new) | Task wrapper for inventory dumping, HIGH priority. Calls `orchestrator.onDumpComplete()` on finish |
| `LoomConfig` | Already has `storageX/Y/Z` for chest position. Dump station is the same as storage area in Nerv. |

### What Should NOT Be Implemented (Out of Scope)

- Cartography table interaction (empty maps, glass panes, map locking)
- Map fill square exploration
- Finished map chest storage
- `breakCarpetAboveReset` — niche optimization for specific server setups
- Slave/master multi-user coordination

### Justification

On anarchy servers (6b6t), carpet map art is the end product. Filled map items are organizational tools for sorting maps in chests — they serve no functional purpose for the bot. The carpet layout IS the art. Removing these steps eliminates ~60% of Nerv's post-print complexity while retaining the essential workflow: clean up → reset → next map.
