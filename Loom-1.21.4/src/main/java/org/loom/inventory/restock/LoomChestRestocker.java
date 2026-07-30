package org.loom.inventory.restock;

/**
 * Default implementation of {@link ChestRestocker}.
 *
 * <p>State machine phases:
 * <ol>
 *   <li>Navigate to storage area</li>
 *   <li>Open chest</li>
 *   <li>Withdraw needed materials (shift-click from chest to inventory)</li>
 *   <li>Navigate back to build area</li>
 *   <li>Navigate to last placement position</li>
 *   <li>Signal completion</li>
 * </ol>
 */
public class LoomChestRestocker implements ChestRestocker {

    // TODO: Inject Navigator, LoomInventoryManager
    // TODO: Define phases enum for state machine

    private boolean restocking;
    private RestockRequest currentRequest;

    public LoomChestRestocker() {
        this.restocking = false;
        this.currentRequest = null;
    }

    @Override
    public void restock(RestockRequest request) {
        // TODO: Set currentRequest
        // TODO: Set restocking = true
        // TODO: Initiate navigation to storage
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void tick() {
        // TODO: Advance through restock phases based on current state
        // TODO: Phase: NAVIGATE_TO_STORAGE → OPEN_CHEST → WITHDRAW → NAVIGATE_BACK → ARRIVE
        // TODO: For each material, shift-click from chest to inventory
        // TODO: When complete, set restocking = false, emit RestockCompletedEvent
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public boolean isRestocking() {
        return restocking;
    }

    @Override
    public boolean isAtStorage() {
        // TODO: Check bot position against storage coordinates
        return false;
    }

    @Override
    public boolean isAtBuildArea() {
        // TODO: Check bot position against build area
        return false;
    }

    @Override
    public void cancelRestock() {
        // TODO: Cancel navigation
        // TODO: Set restocking = false
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
