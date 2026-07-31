package org.loom.inventory;

import com.zenith.feature.inventory.InventoryActionRequest;
import com.zenith.feature.inventory.actions.ClickItem;
import com.zenith.feature.inventory.actions.SetHeldItem;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ClickItemAction;
import org.loom.util.Material;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static com.zenith.Globals.INVENTORY;

/**
 * Default implementation of {@link LoomInventoryManager}.
 */
public class LoomInventoryManagerImpl implements LoomInventoryManager {

    private static final int HOTBAR_START = 36;
    private static final int HOTBAR_END   = 44;
    private static final int HOTBAR_SIZE  = 9;
    private static final int SWAP_PRIORITY = 5000;

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
        // Only reserve if material is already in a hotbar slot
        Optional<Integer> existing = getHotbarSlot(material);
        if (existing.isPresent()) {
            int slot = existing.get();
            reservedSlots.add(slot);
            return slot;
        }
        return -1; // needs swap first
    }

    @Override
    public synchronized void releaseSlot(int slot) {
        reservedSlots.remove(slot);
    }

    // ==================================================================
    // Swap
    // ==================================================================

    @Override
    public int swapIntoHotbar(Material material, int targetHotbarSlot) {
        if (targetHotbarSlot < 0 || targetHotbarSlot >= HOTBAR_SIZE) return -1;

        // Find material in main inventory (not already in hotbar)
        int sourceSlot = -1;
        List<Integer> slots = ledger.getSlotsFor(material);
        for (int slot : slots) {
            if (slot >= 9 && slot <= 35) {
                sourceSlot = slot;
                break;
            }
        }
        if (sourceSlot < 0) return -1;

        int targetContainerSlot = HOTBAR_START + targetHotbarSlot;

        // Triple-click swap: pick up source → swap with target → place back
        // SetHeldItem selects the hotbar slot so the swap targets the right slot
        var request = InventoryActionRequest.builder()
            .owner(this)
            .priority(SWAP_PRIORITY)
            .actionDelayTicks(0)
            .actions(
                new SetHeldItem(targetHotbarSlot),
                new ClickItem(sourceSlot, ClickItemAction.LEFT_CLICK),
                new ClickItem(targetContainerSlot, ClickItemAction.LEFT_CLICK),
                new ClickItem(sourceSlot, ClickItemAction.LEFT_CLICK)
            )
            .build();

        INVENTORY.submit(request);

        // Optimistically update ledger to reflect the swap
        // (inventory slots will be confirmed when server responds)
        ledger.refresh();

        return targetContainerSlot;
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
    }
}
