package org.loom.recovery;

/**
 * A recovery action that returns the bot to a working state after an interruption.
 *
 * <p>Concrete implementations handle specific failure modes: death, disconnect,
 * stuck, and combat. Each action is a small state machine advanced one step
 * per tick by the {@link RecoverySystem}.
 */
public abstract class RecoveryAction {

    private final RecoveryReason reason;

    protected RecoveryAction(RecoveryReason reason) {
        this.reason = reason;
    }

    public RecoveryReason getReason() {
        return reason;
    }

    /**
     * Called when the recovery action begins.
     */
    public void onStart() {
        // TODO: Initialize state for this recovery
    }

    /**
     * Advances the recovery by one tick.
     *
     * @return true if recovery is complete, false if still in progress
     */
    public abstract boolean tick();

    /**
     * Called when the recovery action completes successfully.
     */
    public void onComplete() {
        // TODO: Clean up after recovery
    }

    /**
     * Called if the recovery action fails or times out.
     */
    public void onFail() {
        // TODO: Handle recovery failure (defer to user if N attempts exhausted)
    }
}
