package org.loom.inventory;

import com.zenith.cache.data.inventory.Container;
import com.zenith.mc.item.ItemRegistry;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.loom.util.Material;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.zenith.Globals.CACHE;

/**
 * Tracks current counts and slot locations of materials in the bot's inventory.
 *
 * <p>Reads ground truth from ZenithProxy's {@code InventoryCache} and
 * maintains a cached ledger for fast queries. Call {@link #refresh()}
 * to rebuild from the live inventory.
 *
 * <p>Hot-bar slots are 36-44 (9 slots). Main inventory is 9-35 (27 slots).
 */
public class MaterialLedger {

    private final Map<Material, Integer> counts;
    private final Map<Material, List<Integer>> slotLocations;
    private int freeSlots;

    public MaterialLedger() {
        this.counts = new HashMap<>();
        this.slotLocations = new HashMap<>();
        this.freeSlots = 0;
    }

    /**
     * Returns the total count of a material across all inventory slots.
     */
    public int getCount(Material material) {
        return counts.getOrDefault(material, 0);
    }

    /**
     * Returns all inventory slots holding this material.
     */
    public List<Integer> getSlotsFor(Material material) {
        return slotLocations.getOrDefault(material, List.of());
    }

    /**
     * Returns the first slot (0-indexed from player inventory container)
     * holding this material, or -1 if not found.
     */
    public int findFirstSlot(Material material) {
        List<Integer> slots = slotLocations.get(material);
        return (slots != null && !slots.isEmpty()) ? slots.get(0) : -1;
    }

    /**
     * Returns the number of empty inventory slots.
     */
    public int getFreeSlots() {
        return freeSlots;
    }

    /**
     * Returns all materials that are below their restock threshold.
     */
    public List<MaterialRequest> getMaterialsBelowThreshold(int threshold) {
        List<MaterialRequest> requests = new ArrayList<>();
        for (Map.Entry<Material, Integer> entry : counts.entrySet()) {
            if (entry.getValue() < threshold) {
                int needed = threshold - entry.getValue();
                requests.add(new MaterialRequest(entry.getKey(), needed));
            }
        }
        return requests;
    }

    /**
     * Rebuilds the ledger from ZenithProxy's live inventory cache.
     */
    public void refresh() {
        counts.clear();
        slotLocations.clear();
        freeSlots = 0;

        Container inventory = CACHE.getPlayerCache().getInventoryCache().getPlayerInventory();
        int size = inventory.getSize();

        for (int slot = 0; slot < size; slot++) {
            ItemStack stack = inventory.getItemStack(slot);

            if (stack == null || stack.getId() == 0 || stack.getAmount() <= 0) {
                if (slot >= 9 && slot <= 44) {
                    freeSlots++;
                }
                continue;
            }

            var itemData = ItemRegistry.REGISTRY.get(stack.getId());
            if (itemData == null) continue;

            String identifier = "minecraft:" + itemData.name();
            Material material = Material.fromIdentifier(identifier);
            if (material == null) continue;

            int current = counts.getOrDefault(material, 0);
            counts.put(material, current + stack.getAmount());

            slotLocations.computeIfAbsent(material, k -> new ArrayList<>()).add(slot);
        }
    }
}
