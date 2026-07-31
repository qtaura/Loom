package org.loom.inventory.restock;

import com.zenith.feature.inventory.InventoryActionRequest;
import com.zenith.feature.inventory.actions.ShiftClick;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ShiftClickItemAction;
import org.loom.event.RestockCompletedEvent;
import org.loom.inventory.LoomInventoryManager;
import org.loom.log.LoomLogger;
import org.loom.navigation.NavigationResult;
import org.loom.navigation.Navigator;
import org.loom.util.AsyncLoomEventBus;

import static com.zenith.Globals.CACHE;
import static com.zenith.Globals.INVENTORY;

/**
 * Default implementation of {@link ChestRestocker}.
 *
 * <p>Multi-tick state machine:
 * <ol>
 *   <li>{@code NAV_TO_STORAGE} — navigate to chest coordinates</li>
 *   <li>{@code OPEN_CHEST} — path to chest and right-click via BARITONE</li>
 *   <li>{@code WAIT_OPEN} — wait for chest inventory to appear</li>
 *   <li>{@code WITHDRAW} — shift-click matched stacks from chest</li>
 *   <li>{@code CLOSE} — close chest container</li>
 *   <li>{@code NAV_BACK} — navigate back to build area</li>
 *   <li>{@code DONE} — refresh inventory, signal completion</li>
 * </ol>
 */
public class LoomChestRestocker implements ChestRestocker {

    private static final String TAG = "Restocker";
    private static final int RESTOCK_PRIORITY = 5500;
    private static final int MAX_OPEN_RETRIES = 3;
    private static final int MAX_STACKS_PER_TICK = 5;

    private enum Phase {
        IDLE,
        NAV_TO_STORAGE,
        OPEN_CHEST,
        WAIT_OPEN,
        WITHDRAW,
        CLOSE,
        NAV_BACK,
        DONE
    }

    private final Navigator navigator;
    private final LoomInventoryManager inventoryManager;
    private final LoomLogger logger;
    private final AsyncLoomEventBus eventBus;
    private final int buildOriginX;
    private final int buildOriginZ;

    private Phase phase;
    private RestockRequest currentRequest;
    private int openRetries;
    private int chestSlotIndex;

    public LoomChestRestocker(Navigator navigator,
                               LoomInventoryManager inventoryManager,
                               LoomLogger logger,
                               AsyncLoomEventBus eventBus,
                               int buildOriginX,
                               int buildOriginZ) {
        this.navigator = navigator;
        this.inventoryManager = inventoryManager;
        this.logger = logger;
        this.eventBus = eventBus;
        this.buildOriginX = buildOriginX;
        this.buildOriginZ = buildOriginZ;
        this.phase = Phase.IDLE;
    }

    // ==================================================================
    // Public API
    // ==================================================================

    @Override
    public void restock(RestockRequest request) {
        this.currentRequest = request;
        this.phase = Phase.NAV_TO_STORAGE;
        this.openRetries = 0;
        this.chestSlotIndex = 0;
        logger.info(TAG, "Starting restock for %d materials at (%d,%d,%d)",
            request.getMaterials().size(),
            request.getStorageX(), request.getStorageY(), request.getStorageZ());
    }

    @Override
    public void tick() {
        switch (phase) {
            case IDLE -> {}
            case NAV_TO_STORAGE -> tickNavToStorage();
            case OPEN_CHEST -> tickOpenChest();
            case WAIT_OPEN -> tickWaitOpen();
            case WITHDRAW -> tickWithdraw();
            case CLOSE -> tickClose();
            case NAV_BACK -> tickNavBack();
            case DONE -> {} // caller checks isRestocking()
        }
    }

    @Override
    public boolean isRestocking() {
        return phase != Phase.IDLE && phase != Phase.DONE;
    }

    @Override
    public boolean isAtStorage() {
        if (currentRequest == null) return false;
        double px = CACHE.getPlayerCache().getX();
        double pz = CACHE.getPlayerCache().getZ();
        double dx = px - currentRequest.getStorageX();
        double dz = pz - currentRequest.getStorageZ();
        return Math.abs(dx) < 2 && Math.abs(dz) < 2;
    }

    @Override
    public boolean isAtBuildArea() {
        double px = CACHE.getPlayerCache().getX();
        double pz = CACHE.getPlayerCache().getZ();
        double dx = px - buildOriginX;
        double dz = pz - buildOriginZ;
        return Math.abs(dx) < 2 && Math.abs(dz) < 2;
    }

    @Override
    public void cancelRestock() {
        phase = Phase.IDLE;
        currentRequest = null;
        // Cancel navigation if running
        navigator.cancel();
        logger.info(TAG, "Restock cancelled");
    }

    // ==================================================================
    // Phase ticks
    // ==================================================================

    private void tickNavToStorage() {
        int sx = currentRequest.getStorageX();
        int sz = currentRequest.getStorageZ();

        if (isAtStorage()) {
            phase = Phase.OPEN_CHEST;
            return;
        }

        NavigationResult result = navigator.goTo(sx, sz);
        if (result == NavigationResult.ACCEPTED || result == NavigationResult.ARRIVED) {
            // Navigation in progress — wait for completion on next ticks
        } else {
            logger.warn(TAG, "Navigation to storage rejected, retrying");
        }
    }

    private void tickOpenChest() {
        navigator.openChest(currentRequest.getStorageX(),
            currentRequest.getStorageY(), currentRequest.getStorageZ());

        openRetries++;
        phase = Phase.WAIT_OPEN;
        logger.debug(TAG, "Opening chest, attempt %d", openRetries);
    }

    private void tickWaitOpen() {
        int openContainerId = CACHE.getPlayerCache().getInventoryCache().getOpenContainerId();

        if (openContainerId != 0) {
            logger.debug(TAG, "Chest opened (container %d)", openContainerId);
            chestSlotIndex = 0;
            phase = Phase.WITHDRAW;
            return;
        }

        // Check if navigation finished but chest didn't open
        if (!navigator.isBusy()) {
            if (openRetries < MAX_OPEN_RETRIES) {
                phase = Phase.OPEN_CHEST;
            } else {
                logger.warn(TAG, "Failed to open chest after %d attempts", MAX_OPEN_RETRIES);
                phase = Phase.NAV_BACK;
            }
        }
    }

    private void tickWithdraw() {
        int containerId = CACHE.getPlayerCache().getInventoryCache().getOpenContainerId();
        if (containerId == 0) {
            // Chest was closed (maybe by server), try reopening
            phase = Phase.OPEN_CHEST;
            return;
        }

        var container = CACHE.getPlayerCache().getInventoryCache().getContainers().get(containerId);
        if (container == null) {
            phase = Phase.CLOSE;
            return;
        }

        int size = container.getSize();
        int stacksWithdrawn = 0;

        // Scan chest slots for needed materials
        for (int i = chestSlotIndex; i < size && stacksWithdrawn < MAX_STACKS_PER_TICK; i++) {
            var stack = container.getItemStack(i);
            if (stack == null) continue;

            var itemData = com.zenith.mc.item.ItemRegistry.REGISTRY.get(stack.getId());
            if (itemData == null) continue;

            String id = "minecraft:" + itemData.name();
            var material = org.loom.util.Material.fromIdentifier(id);
            if (material == null) continue;

            // Check if this material is in our restock list
            boolean needed = currentRequest.getMaterials().stream()
                .anyMatch(m -> m.getMaterial().equals(material));
            if (!needed) continue;

            // Shift-click to move to inventory
            INVENTORY.submit(InventoryActionRequest.builder()
                .owner(this)
                .priority(RESTOCK_PRIORITY)
                .actions(new ShiftClick(i, ShiftClickItemAction.LEFT_CLICK))
                .build());

            stacksWithdrawn++;
        }

        chestSlotIndex += stacksWithdrawn;

        if (chestSlotIndex >= size) {
            // Scanned all slots
            phase = Phase.CLOSE;
        }
    }

    private void tickClose() {
        // Close the container via InventoryManager
        INVENTORY.submit(InventoryActionRequest.builder()
            .owner(this)
            .priority(RESTOCK_PRIORITY)
            .actions(new com.zenith.feature.inventory.actions.CloseContainer())
            .build());

        phase = Phase.NAV_BACK;
        logger.debug(TAG, "Closing chest");
    }

    private void tickNavBack() {
        if (isAtBuildArea()) {
            phase = Phase.DONE;
                inventoryManager.refresh();
                eventBus.publish(new RestockCompletedEvent(0));
                logger.info(TAG, "Restock complete");
            return;
        }

        NavigationResult result = navigator.goTo(buildOriginX, buildOriginZ);
        if (result == NavigationResult.ACCEPTED || result == NavigationResult.ARRIVED) {
            // Navigation in progress
        }
    }
}
