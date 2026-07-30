package org.loom.inventory;

import org.loom.util.Material;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks current counts of each material in the bot's inventory.
 *
 * <p>Reads ground truth from ZenithProxy's {@code InventoryCache} and
 * maintains a cached ledger for fast queries.
 */
public class MaterialLedger {

    private final Map<Material, Integer> counts;

    public MaterialLedger() {
        this.counts = new HashMap<>();
    }

    /**
     * Returns the current count of a material in inventory.
     */
    public int getCount(Material material) {
        return counts.getOrDefault(material, 0);
    }

    /**
     * Refreshes all counts from the bot's actual inventory.
     * Called periodically and after inventory changes.
     */
    public void refresh() {
        // TODO: Read from CACHE.getPlayerCache().getInventoryCache()
        // TODO: Update counts map for each material in the palette
    }

    /**
     * Returns all materials that are below their restock threshold.
     *
     * @param threshold the minimum count before restock is needed
     */
    public List<MaterialRequest> getMaterialsBelowThreshold(int threshold) {
        // TODO: Iterate counts, return MaterialRequest for each below threshold
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
