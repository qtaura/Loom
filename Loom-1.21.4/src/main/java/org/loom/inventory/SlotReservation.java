package org.loom.inventory;

import org.loom.util.Material;

/**
 * Binds a material type to a specific hotbar slot.
 *
 * <p>Used by {@link LoomInventoryManager} to reserve slots for
 * frequently-used materials, avoiding repeated swaps.
 */
public class SlotReservation {

    private final int slot;
    private final Material material;

    public SlotReservation(int slot, Material material) {
        this.slot = slot;
        this.material = material;
    }

    public int getSlot() {
        return slot;
    }

    public Material getMaterial() {
        return material;
    }

    @Override
    public String toString() {
        return "SlotReservation{slot=" + slot + ", material=" + material + "}";
    }
}
