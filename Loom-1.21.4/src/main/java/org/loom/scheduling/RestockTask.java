package org.loom.scheduling;

import org.loom.inventory.restock.RestockRequest;

/**
 * A task that drives the {@link org.loom.inventory.restock.ChestRestocker} to
 * replenish materials from a storage area.
 *
 * <p>This task preempts the current {@link PrintTask} via a higher priority
 * to ensure the bot never runs out of materials mid-build.
 */
public class RestockTask extends Task {

    private final RestockRequest request;

    public RestockTask(RestockRequest request) {
        super("RestockTask");
        this.request = request;
    }

    /**
     * Returns the restock request defining what materials are needed.
     */
    public RestockRequest getRequest() {
        return request;
    }

    @Override
    public void onStart() {
        // TODO: Initialize ChestRestocker with the request
        // TODO: Save current print position to ProgressTracker
    }

    @Override
    public void tick() {
        // TODO: Delegate to ChestRestocker.tick()
        // TODO: Advance through: navigate → open chest → withdraw → return
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void onComplete() {
        // TODO: Publish RestockCompletedEvent
        // TODO: Signal InventoryManager that materials are replenished
    }

    @Override
    public void onFail(Throwable cause) {
        // TODO: Publish RestockFailedEvent
        // TODO: Log which materials could not be restocked
    }

    @Override
    public boolean isComplete() {
        // TODO: Delegate to ChestRestocker
        return false;
    }
}
