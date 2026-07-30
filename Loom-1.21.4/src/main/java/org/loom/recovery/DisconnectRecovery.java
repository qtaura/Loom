package org.loom.recovery;

/**
 * Handles disconnect recovery: wait for reconnect, then resume the job.
 */
public class DisconnectRecovery extends RecoveryAction {

    public DisconnectRecovery() {
        super(RecoveryReason.DISCONNECT);
    }

    @Override
    public void onStart() {
        // TODO: Save progress immediately
        // TODO: Emit RecoveryStartedEvent(DISCONNECT)
    }

    @Override
    public boolean tick() {
        // TODO: Check if reconnected (CACHE is populated)
        // TODO: If reconnected, navigate back to build area
        // TODO: Return true when ready to resume
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void onComplete() {
        // TODO: Emit RecoveryCompletedEvent(DISCONNECT)
        // TODO: Signal TaskScheduler to resume PrintTask
    }
}
