package org.loom.inventory;

import org.loom.util.Material;

import java.util.List;
import java.util.Optional;

/**
 * Tracks and manages the bot's inventory for map art construction.
 *
 * <p>Knows which slots hold which carpet colors, how many of each remain,
 * and when restocking is needed. Uses ZenithProxy's {@code InventoryManager}
 * for slot swaps and item drops.
 */
public interface LoomInventoryManager {

    /**
     * Returns the current count of a material in the bot's inventory.
     */
    int getCount(Material material);

    /**
     * Returns true if the material is available in any quantity.
     */
    boolean hasMaterial(Material material);

    /**
     * Reserves a hotbar slot for a specific material type.
     * Swaps the item into the hotbar if it's not already there.
     *
     * @param material the material to reserve a slot for
     * @return the slot number, or -1 if no slot could be allocated
     */
    int reserveSlot(Material material);

    /**
     * Releases a previously reserved slot.
     *
     * @param slot the slot to release
     */
    void releaseSlot(int slot);

    /**
     * Returns a list of materials that need restocking.
     * A material needs restocking when its count falls below the configured threshold.
     *
     * @return list of material requests, empty if no restock needed
     */
    List<MaterialRequest> getRequiredRestock();

    /**
     * Called after a restock completes to refresh the ledger.
     */
    void requestRestock(List<MaterialRequest> materials);

    /**
     * Returns the hotbar slot currently holding the given material, if any.
     */
    Optional<Integer> getHotbarSlot(Material material);

    /**
     * Returns the number of free inventory slots.
     */
    int getAvailableSlots();

    /**
     * Refreshes the material ledger from the bot's actual inventory.
     */
    void refresh();
}
