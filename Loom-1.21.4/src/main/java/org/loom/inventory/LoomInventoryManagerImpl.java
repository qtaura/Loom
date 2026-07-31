package org.loom.inventory;

import org.loom.util.Material;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Default implementation of {@link LoomInventoryManager}.
 *
 * <p>Tracks material counts, hotbar slot reservations, and restock needs.
 * Delegates inventory scanning to {@link MaterialLedger}.
 *
 * <p>Slot reservations are conceptual — this class tracks which hotbar
 * slots are reserved for which materials but does NOT perform slot swaps.
 * The caller (PlacementEngine) handles actual inventory manipulation
 * via ZenithProxy's {@code INVENTORY} system.
 */
public class LoomInventoryManagerImpl implements LoomInventoryManager {

    private static final int HOTBAR_START = 36;
    private static final int HOTBAR_END   = 44; // inclusive
    private static final int HOTBAR_SIZE  = 9;

    private final MaterialLedger ledger;
    private final int restockThreshold;
    private final Set<Integer> reservedSlots;

    public LoomInventoryManagerImpl(int restockThreshold) {
        this.ledger = new MaterialLedger();
        this.restockThreshold = restockThreshold;
        this.reservedSlots = new HashSet<>();
    }

    // ==================================================================
    // Query API
    // ==================================================================

    @Override
    public int getCount(Material material) {
        return ledger.getCount(material);
    }

    @Override
    public boolean hasMaterial(Material material) {
        return ledger.getCount(material) > 0;
    }

    @Override
    public Optional<Integer> getHotbarSlot(Material material) {
        List<Integer> slots = ledger.getSlotsFor(material);
        for (int slot : slots) {
            if (slot >= HOTBAR_START && slot <= HOTBAR_END) {
                return Optional.of(slot - HOTBAR_START);
            }
        }
        return Optional.empty();
    }

    @Override
    public int getAvailableSlots() {
        return ledger.getFreeSlots();
    }

    @Override
    public List<MaterialRequest> getRequiredRestock() {
        return ledger.getMaterialsBelowThreshold(restockThreshold);
    }

    // ==================================================================
    // Reservation API
    // ==================================================================

    @Override
    public synchronized int reserveSlot(Material material) {
        // First: is it already in a hotbar slot?
        Optional<Integer> hotbarSlot = getHotbarSlot(material);
        if (hotbarSlot.isPresent()) {
            int slot = hotbarSlot.get();
            reservedSlots.add(slot);
            return slot;
        }

        // Second: find it anywhere in inventory
        int anySlot = ledger.findFirstSlot(material);
        if (anySlot < 0) {
            return -1; // material not found
        }

        // Third: find a free hotbar slot
        for (int hb = 0; hb < HOTBAR_SIZE; hb++) {
            if (!reservedSlots.contains(hb)) {
                reservedSlots.add(hb);
                // Return the hotbar index. Caller must swap the item into this slot.
                return hb;
            }
        }

        return -1; // no free hotbar slot
    }

    @Override
    public synchronized void releaseSlot(int slot) {
        reservedSlots.remove(slot);
    }

    // ==================================================================
    // Lifecycle
    // ==================================================================

    @Override
    public void refresh() {
        ledger.refresh();
    }

    @Override
    public void requestRestock(List<MaterialRequest> materials) {
        // Restock is handled by ChestRestocker via TaskScheduler.
        // This method exists as a signal point for the architecture.
        // After a restock completes, ChestRestocker calls refresh().
    }
}
