package org.loom.scheduling;

import org.loom.inventory.restock.ChestRestocker;
import org.loom.inventory.restock.RestockRequest;

/**
 * A task that drives the {@link ChestRestocker} to replenish materials
 * from a storage area.
 *
 * <p>Submitted at {@link TaskPriority#HIGH} to preempt the current
 * {@link PrintTask} when materials run low.
 */
public class RestockTask extends Task {

    private final RestockRequest request;
    private final ChestRestocker restocker;

    public RestockTask(RestockRequest request, ChestRestocker restocker) {
        super("RestockTask-" + request.getStorageX() + "," + request.getStorageZ());
        this.request = request;
        this.restocker = restocker;
    }

    public RestockRequest getRequest() {
        return request;
    }

    @Override
    public void onStart() {
        restocker.restock(request);
    }

    @Override
    public void tick() {
        restocker.tick();
    }

    @Override
    public void onPause() {
        restocker.cancelRestock();
    }

    @Override
    public void onComplete() {
        // Restock finished — inventory was already refreshed by ChestRestocker
    }

    @Override
    public void onFail(Throwable cause) {
        restocker.cancelRestock();
    }

    @Override
    public boolean isComplete() {
        return !restocker.isRestocking();
    }
}
