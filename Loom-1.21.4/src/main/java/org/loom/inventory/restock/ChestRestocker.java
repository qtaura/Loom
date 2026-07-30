package org.loom.inventory.restock;

/**
 * Executes the restock workflow: leave build area, go to storage, withdraw
 * materials, return to build area, and signal that printing can resume.
 *
 * <p>Called tick-by-tick by the {@link org.loom.scheduling.RestockTask}.
 */
public interface ChestRestocker {

    /**
     * Starts the restock workflow for the given request.
     *
     * @param request the restock request specifying materials and storage location
     */
    void restock(RestockRequest request);

    /**
     * Advances the restock workflow by one step.
     * Called each bot tick while the RestockTask is active.
     */
    void tick();

    /**
     * Returns true if the restock workflow is currently in progress.
     */
    boolean isRestocking();

    /**
     * Returns true if the bot is currently at the storage area.
     */
    boolean isAtStorage();

    /**
     * Returns true if the bot is currently at the build area.
     */
    boolean isAtBuildArea();

    /**
     * Cancels the current restock workflow.
     */
    void cancelRestock();
}
