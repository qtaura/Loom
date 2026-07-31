# Nerv Printer Dump System — Forensic Analysis

> Source: `CarpetPrinter.java` (lines 864-881, 1364-1412, 1150-1165)
> Helper: `Utils.java` (lines 53-106, 63-76)

---

## 1. getRequiredItems() — Line-by-Line Trace

### Code
```java
private HashMap<Item, Integer> getRequiredItems() {
    // Calculate the next items to restock
    HashMap<Item, Integer> requiredItems = new HashMap<>();
    boolean northToSouth = true;
    boolean hasFoundAir = false;
    for (int x = workingInterval.getLeft(); x <= workingInterval.getRight(); x += linesPerRun.get()) {
        for (int z = 0; z < 128; z++) {
            for (int lineBonus = 0; lineBonus < linesPerRun.get(); lineBonus++) {
                int adjustedX = x + lineBonus;
                if (adjustedX > workingInterval.getRight()) break;
                int adjustedZ = z;
                if (!northToSouth) adjustedZ = 127 - z;
                BlockState blockState = MapAreaCache.getCachedBlockState(mapCorner.add(adjustedX, 0, adjustedZ));
                if (blockState.isAir() && map[adjustedX][adjustedZ] != null) {
                    if (!hasFoundAir) {
                        hasFoundAir = true;
                        BlockState oppositeBlockState = MapAreaCache.getCachedBlockState(mapCorner.add(adjustedX, 0, 127 - adjustedZ));
                        if (!oppositeBlockState.isAir() && z < 64) {
                            northToSouth = !northToSouth;
                            adjustedZ = 127 - z;
                        }
                    }
                    Item material = map[adjustedX][adjustedZ].asItem();
                    if (!requiredItems.containsKey(material)) requiredItems.put(material, 0);
                    requiredItems.put(material, requiredItems.get(material) + 1);
                    if (Utils.stacksRequired(requiredItems.values()) > availableSlots.size()) {
                        requiredItems.put(material, requiredItems.get(material) - 1);
                        return requiredItems;
                    }
                }
            }
        }
        northToSouth = !northToSouth;
    }
    return requiredItems;
}
```

### Variable Explanation

| Variable | Type | Purpose |
|---|---|---|
| `requiredItems` | `HashMap<Item, Integer>` | Maps Minecraft Item → count needed. Built incrementally. |
| `northToSouth` | `boolean` | Current snake traversal direction. Toggles each column group. |
| `hasFoundAir` | `boolean` | Whether the first unplaced (air) position has been found. Used for snake-correction. |
| `x` | `int` | Column group start. Increments by `linesPerRun` (default 3). |
| `z` | `int` | Row index (0–127). |
| `lineBonus` | `int` | Offset within column group (0 to `linesPerRun-1`). |
| `adjustedX` | `int` | Actual column being checked = `x + lineBonus`. |
| `adjustedZ` | `int` | Row index, potentially reversed based on direction. |
| `blockState` | `BlockState` | Actual block at the world position (from MapAreaCache). |
| `map[adjustedX][adjustedZ]` | `Block` | Expected block from loaded NBT schematic. Null if ignored. |
| `material` | `Item` | `.asItem()` of the expected block. |
| `availableSlots` | `ArrayList<Integer>` | Slots that are empty or contain `materialDict` items (set by `setupSlots()`). |

### Loop Structure

```
Outer: x from workingInterval.left to workingInterval.right, step linesPerRun
  │
  Middle: z from 0 to 127
  │
  Inner: lineBonus from 0 to linesPerRun-1
```

**Does not skip completed columns.** Unlike `calculateBuildingPath()` which has a `lineFinished` check, `getRequiredItems()` does NOT skip any columns. It scans every column, every row, regardless of whether blocks are already placed. The scan runs until inventory capacity is exceeded or the entire map is scanned.

### Stopping Condition

The scan stops when:
```
Utils.stacksRequired(requiredItems.values()) > availableSlots.size()
```

`stacksRequired()` sums up `ceil(count / 64)` for each required item count. When this exceeds the number of available inventory slots, the last increment is undone and the method returns immediately. This means `getRequiredItems()` returns "how many of each material do I need for the FIRST N unplaced blocks, limited by my available inventory space."

### The hasFoundAir / Snake Correction Logic

When the first air (unplaced) block is found, the code checks its opposite-side counterpart:
```
BlockState oppositeBlockState = MapAreaCache.getCachedBlockState(mapCorner.add(adjustedX, 0, 127 - adjustedZ));
if (!oppositeBlockState.isAir() && z < 64) {
    northToSouth = !northToSouth;
    adjustedZ = 127 - z;
}
```

This detects if the snake pattern was reversed at some point (e.g., due to a mid-print pause/resume). If the first air block is in the top half of the map (z < 64) and its mirror position on the opposite side is NOT air, the scan direction is flipped. This is a heuristic for snake-pattern recovery.

**Cannot be verified from the code** whether this correction is always accurate. It assumes that if the first air block is in the top half and its mirror is not air, the snake direction reversed. This is heuristic, not guaranteed correct.

---

## 2. Utils.getInvInformation() — Line-by-Line Trace

### Code
```java
public static Pair<ArrayList<Integer>, HashMap<Item, Integer>> getInvInformation(
    HashMap<Item, Integer> requiredItems, ArrayList<Integer> availableSlots) {
    ArrayList<Integer> dumpSlots = new ArrayList<>();
    HashMap<Item, Integer> materialInInv = new HashMap<>();
    for (int slot : availableSlots) {
        if (mc.player.getInventory().getStack(slot).isEmpty()) continue;
        Item item = mc.player.getInventory().getStack(slot).getItem();
        if (requiredItems.containsKey(item)) {
            int requiredAmount = requiredItems.get(item);
            int requiredModulusAmount = (requiredAmount - (requiredAmount / 64) * 64);
            if (requiredModulusAmount == 0) requiredModulusAmount = 64;
            int stackAmount = mc.player.getInventory().getStack(slot).getCount();
            if (requiredAmount > 0 && requiredModulusAmount <= stackAmount) {
                int oldEntry = requiredItems.remove(item);
                requiredItems.put(item, Math.max(0, oldEntry - stackAmount));
                if (materialInInv.containsKey(item)) {
                    oldEntry = materialInInv.remove(item);
                    materialInInv.put(item, oldEntry + stackAmount);
                } else {
                    materialInInv.put(item, stackAmount);
                }
                continue;  // ← KEEP
            }
        }
        dumpSlots.add(slot);  // ← DUMP
    }
    return new Pair(dumpSlots, materialInInv);
}
```

### Variable Explanation

| Variable | Type | Purpose |
|---|---|---|
| `dumpSlots` | `ArrayList<Integer>` | Slot indices (0–35) that will be dumped. Built by exclusion. |
| `materialInInv` | `HashMap<Item, Integer>` | How many of each material are currently in available slots (after the dump logic runs). |
| `slot` | `int` | Current inventory slot being examined (from availableSlots list). |
| `item` | `Item` | The Minecraft item in the current slot. |
| `requiredAmount` | `int` | How many of this item are still needed (from getRequiredItems). |
| `requiredModulusAmount` | `int` | `requiredAmount % 64`, with 0→64. Represents the partial-stack remainder. |
| `stackAmount` | `int` | Count of items in the current stack (1–64). |

### The KEEP Condition

```java
if (requiredAmount > 0 && requiredModulusAmount <= stackAmount) {
    // KEEP
    requiredItems.put(item, Math.max(0, oldEntry - stackAmount));
    materialInInv.put(item, ...);
    continue;
}
// else fall through to DUMP
```

An item is KEPT if:
1. The item's name is in `requiredItems` (the getRequiredItems map)
2. `requiredAmount > 0` (still need more of this item)
3. `requiredModulusAmount <= stackAmount` (the partial-stack remainder ≤ the stack size)

When kept, `requiredAmount` is reduced by `stackAmount`. The item is added to `materialInInv`.

An item is DUMPED if:
1. The item is NOT in `requiredItems` at all, OR
2. `requiredAmount` is already 0 (we have enough), OR
3. `requiredModulusAmount > stackAmount` (the stack is too small)

### Trace Example

**Setup:** Need 150 white_carpet. Have 3 stacks of 64 in inventory. `availableSlots` includes the 3 slots.

```
Slot 1: white_carpet x64
  requiredAmount = 150
  requiredModulusAmount = 150 - (150/64)*64 = 150 - 128 = 22
  22 <= 64 → KEEP
  requiredAmount = max(0, 150 - 64) = 86
  materialInInv = {white_carpet: 64}

Slot 2: white_carpet x64
  requiredAmount = 86
  requiredModulusAmount = 86 - (86/64)*64 = 86 - 64 = 22
  22 <= 64 → KEEP
  requiredAmount = max(0, 86 - 64) = 22
  materialInInv = {white_carpet: 128}

Slot 3: white_carpet x64
  requiredAmount = 22
  requiredModulusAmount = 22 - (22/64)*64 = 22
  22 <= 64 → KEEP
  requiredAmount = max(0, 22 - 64) = 0
  materialInInv = {white_carpet: 192}
```

Result: all 3 stacks KEPT. requiredAmount is now 0. Any additional white_carpet stacks in availableSlots would be dumped (requiredAmount = 0, first condition fails).

**Setup:** Need 22 white_carpet. Have stack of 64 and stack of 10.

```
Slot 1: white_carpet x64
  requiredAmount = 22
  requiredModulusAmount = 22
  22 <= 64 → KEEP
  requiredAmount = max(0, 22 - 64) = 0
  materialInInv = {white_carpet: 64}

Slot 2: white_carpet x10
  requiredAmount = 0
  requiredAmount > 0 ? → FALSE → DUMP
```

Result: 64-stack KEPT, 10-stack DUMPED. The 10-stack is dumped because we already satisfied the required amount with the 64-stack. The modulus condition was not the deciding factor — `requiredAmount > 0` was.

**Setup:** Need 80 white_carpet. Have stack of 10 (partial).

```
Slot 1: white_carpet x10
  requiredAmount = 80
  requiredModulusAmount = 80 - 64 = 16
  16 > 10 → DUMP
```

Result: 10-stack DUMPED because `requiredModulusAmount (16) > stackAmount (10)`. The stack of 10 cannot cover the partial remainder of 16.

---

## 3. Exactly What Causes an Item to Be Dumped

From the only execution path that drops items (lines 868-881):

```java
if (state == State.Dumping) {
    int dumpSlot = getDumpSlot();
    if (dumpSlot == -1) {
        // Nothing to dump → refill + continue walking
        refillInventory(...);
        state = State.Walking;
    } else {
        InvUtils.drop().slot(dumpSlot);
        timeoutTicks = invActionDelay.get();
    }
}
```

`getDumpSlot()` returns `invInformation.getLeft().get(0)` — the first element of `dumpSlots`.

An item reaches `dumpSlots` in `getInvInformation()` through ONE of three paths:

**Path A — Item not in requiredItems:** The slot contains an item whose Minecraft Item type is not a key in the `requiredItems` map. The `requiredItems` map is built by `getRequiredItems()` which only includes items from the NBT schematic's `map[][]` array. Any item NOT present in the schematic's palette is dumped.

**Path B — requiredAmount already 0:** The item IS in requiredItems, but previous slots already satisfied the full required count. `requiredAmount` has been reduced to 0 by earlier iterations.

**Path C — Stack too small:** `requiredModulusAmount > stackAmount`. The remaining non-full-stack portion (requiredAmount % 64, with 0→64) exceeds the size of the current stack. This is the narrow condition where a partial stack that COULD be useful is dumped anyway.

---

## 4. Exactly What Causes an Item to Be Preserved

An item is preserved when `getInvInformation()` hits the `continue` statement:

1. Item IS a key in `requiredItems` (the getRequiredItems map)
2. `requiredAmount > 0` (still need more)
3. `requiredModulusAmount <= stackAmount`

Satisfying all three = preserved. The item's entire stack count is credited against `requiredAmount`.

Additionally, items in slots NOT in `availableSlots` are never examined. They survive without being touched.

---

## 5. How Are Tools Handled?

`availableSlots` is built by `Utils.getAvailableSlots(materialDict)`:

```java
for (int slot = 0; slot < 36; slot++) {
    if (mc.player.getInventory().getStack(slot).isEmpty()) {
        slots.add(slot);     // ← empty → available
        continue;
    }
    Item item = mc.player.getInventory().getStack(slot).getItem();
    if (materials.containsKey(item)) {
        slots.add(slot);     // ← in materialDict → available
    }
    // else: has item, not in materialDict → NOT in availableSlots
}
```

`materialDict` maps Items known from registered chests (the user interacts with chests containing map materials during setup). Tools (pickaxes, shears) are not registered as materials. Therefore, tool slots are NOT in `availableSlots`. They are never scanned by `getInvInformation()` and are never dumped.

**Verdict:** Tools are silently preserved because they fall outside the availableSlots filter.

---

## 6. How Are Rockets/Other Utility Items Handled?

Same as tools. Unless the user registered a chest containing rockets during setup, rockets are not in `materialDict`. They occupy slots not in `availableSlots`. Never dumped.

---

## 7. How Are Totems Handled?

Same. Not in `materialDict`. Not in `availableSlots`. Never dumped.

---

## 8. How Are Maps Handled?

Empty maps and glass panes are taken from a registered chest (the user sets up a `mapMaterialChest`). The `mapMaterialChests` list stores these chest positions. During `AwaitMapChestResponse`, items are withdrawn from the chest. The items `Items.MAP` and `Items.GLASS_PANE` are placed into the player's inventory.

However, `availableSlots` is built from `materialDict` (material chests), NOT from `mapMaterialChests`. Unless the user registered the map chest's items into `materialDict` during the chest registration phase, maps and glass panes may or may not be in `availableSlots`.

**Cannot be verified from the code** whether maps/panes from `mapMaterialChests` also appear in `materialDict`. The `materialDict` is built during the `SelectingChests` → `AwaitRegisterResponse` flow, which registers chests based on their contents. Maps and panes in the map material chest would be registered in `materialDict` IF the user interacted with that chest during the chest registration phase.

---

## 9. How Are Glass Panes Handled?

Same analysis as maps. Depends on whether the glass pane chest was registered into `materialDict`.

---

## 10. How Are Shulker Boxes Handled?

Shulker boxes are not in `materialDict` (they are regular containers, not registered material chests). Not in `availableSlots`. Never dumped.

---

## 11. How Are Extra Carpet Stacks Handled?

Carpet items are in `materialDict` (they are registered via chest interaction). They ARE in `availableSlots`. `getRequiredItems()` determines how many are needed. `getInvInformation()` keeps enough to satisfy the required count. Any stacks beyond what's needed are dumped.

Specifically: once `requiredAmount` is reduced to 0 for a material, all subsequent slots containing that material are dumped (Path B above).

---

## 12. How Does Dumping Interact with Restocking?

Dumping and restocking are **tied together** in the Dumping state:

```
state == State.Dumping
  │
  ├─ getDumpSlot() calls getRequiredItems() + getInvInformation()
  │
  ├─ If dumpSlots NOT empty: drop first slot → stay in Dumping
  │
  └─ If dumpSlots IS empty:
       ├─ getRequiredItems() (fresh call)
       ├─ getInvInformation() (fresh call)
       ├─ refillInventory(materialInInv)
       │    ├─ requiredItems (fresh getRequiredItems)
       │    ├─ Subtract what's already in inventory from requiredItems
       │    ├─ For each still-needed item: add to restockList
       │    └─ addClosestRestockCheckpoint() → adds "refill" checkpoint
       └─ state = Walking (walks to restock checkpoint)
```

Key detail: `refillInventory()` is called with `materialInInv` (what's already in inventory). It calls `getRequiredItems()` AGAIN (third call). It subtracts `materialInInv` from the new requiredItems. The remainder becomes the restock list.

This means `getRequiredItems()` is called THREE times during a single dump cycle:
1. In `getDumpSlot()` (line 1365)
2. In the dump complete handler (line 871)
3. In `refillInventory()` (line 1153)

Each call recomputes requiredItems from scratch. The scan position depends on what has been placed since the last call.

---

## 13. Does Nerv Dump Based On...

| Criterion | Yes/No | Evidence |
|---|---|---|
| **Palette?** | Indirectly yes | `getRequiredItems()` reads from `map[][]` which is built from the NBT palette. Items not in the palette never appear in `requiredItems` and are dumped. |
| **Remaining required blocks?** | Yes | `getRequiredItems()` scans the map for unplaced blocks and counts what's needed. |
| **Chest contents?** | No | `materialDict` determines which slots are in `availableSlots`, but chest CONTENTS are not consulted during dumping. |
| **Inventory capacity?** | Yes | `stacksRequired() > availableSlots.size()` is the stopping condition. |
| **Something else?** | Yes | `availableSlots` is a filter that excludes tool/totem/utility slots from consideration entirely. |

---

## Complete Example Trace

**Setup:** `availableSlots = [0, 1, 2, 3, 4, 5]` (6 slots). Map needs: 150 white_carpet, 80 red_carpet.

**Inventory before:**
```
Slot | Item               | Count | In materialDict? | In availableSlots?
  0  | white_carpet       |   64  | Yes             | Yes
  1  | white_carpet       |   64  | Yes             | Yes
  2  | white_carpet       |   64  | Yes             | Yes
  3  | white_carpet       |   10  | Yes             | Yes
  4  | red_carpet         |   64  | Yes             | Yes
  5  | red_carpet         |   64  | Yes             | Yes
  6  | diamond_pickaxe    |    1  | No              | No
  7  | totem              |    1  | No              | No
  ... (other slots not in availableSlots)
```

**getRequiredItems() call:**
Scans map, finds 150 white_carpet + 80 red_carpet are needed. `stacksRequired([150, 80]) = ceil(150/64) + ceil(80/64) = 3 + 2 = 5`. `5 <= 6` (availableSlots.size). Scan continues. If more items are found that push stacksRequired > 6, scan stops.

**getInvInformation(requiredItems, availableSlots) call:**
```
Slot 0: white_carpet x64, required=150, modulus=22, 22<=64 → KEEP.
        requiredAmount = 86

Slot 1: white_carpet x64, required=86, modulus=22, 22<=64 → KEEP.
        requiredAmount = 22

Slot 2: white_carpet x64, required=22, modulus=22, 22<=64 → KEEP.
        requiredAmount = 0

Slot 3: white_carpet x10, required=0, required>0? → FALSE → DUMP

Slot 4: red_carpet x64, required=80, modulus=16, 16<=64 → KEEP.
        requiredAmount = 16

Slot 5: red_carpet x64, required=16, modulus=16, 16<=64 → KEEP.
        requiredAmount = 0
```

**dumpSlots = [3]** (only slot 3 dumped — the white_carpet x10 partial stack)

**materialInInv = {white_carpet: 192, red_carpet: 128}**

**Inventory after:**
```
Slot 3: (empty) — the 10 white_carpet was dropped
Slots 6, 7: diamond_pickaxe, totem — untouched (not in availableSlots)
```

**refillInventory(materialInInv) then:**
- RequiredItems (fresh): {white_carpet: 150, red_carpet: 80}
- Subtract materialInInv: {white_carpet: 150-192=-42 → 0, red_carpet: 80-128=-48 → 0}
- Both ≤ 0 → no restock needed
- No restock checkpoints added
- state = Walking (continues to map generation checkpoints)
```
