package org.loom.inventory;

import org.loom.util.Material;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Default implementation of {@link LoomInventoryManager}.
 */
public class LoomInventoryManagerImpl implements LoomInventoryManager {

    private final MaterialLedger ledger;
    private final int restockThreshold;

    public LoomInventoryManagerImpl(int restockThreshold) {
        this.ledger = new MaterialLedger();
        this.restockThreshold = restockThreshold;
    }

    @Override
    public int getCount(Material material) {
        return ledger.getCount(material);
    }

    @Override
    public boolean hasMaterial(Material material) {
        return ledger.getCount(material) > 0;
    }

    @Override
    public int reserveSlot(Material material) {
        // TODO: Find item in inventory
        // TODO: If in hotbar, reserve that slot
        // TODO: If not in hotbar, swap into an available slot via INVENTORY.submit()
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void releaseSlot(int slot) {
        // TODO: Remove reservation for this slot
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public List<MaterialRequest> getRequiredRestock() {
        // TODO: Delegate to ledger.getMaterialsBelowThreshold(restockThreshold)
        return new ArrayList<>();
    }

    @Override
    public void requestRestock(List<MaterialRequest> materials) {
        // TODO: This is a signal — restock is handled by ChestRestocker via TaskScheduler
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public Optional<Integer> getHotbarSlot(Material material) {
        // TODO: Check all hotbar slots for the material
        return Optional.empty();
    }

    @Override
    public int getAvailableSlots() {
        // TODO: Count empty inventory slots from cache
        return 0;
    }

    @Override
    public void refresh() {
        ledger.refresh();
    }
}
