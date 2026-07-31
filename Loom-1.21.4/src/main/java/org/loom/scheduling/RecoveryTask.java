package org.loom.scheduling;

import org.loom.recovery.LoomRecoverySystem;

/**
 * A task that drives the {@link LoomRecoverySystem}.
 *
 * <p>Submitted at {@link TaskPriority#CRITICAL} to preempt all other tasks.
 * The recovery system itself manages the recovery state machine internally.
 * This task simply allows the scheduler to track completion.
 */
public class RecoveryTask extends Task {

    private final LoomRecoverySystem recovery;

    public RecoveryTask(LoomRecoverySystem recovery) {
        super("RecoveryTask");
        this.recovery = recovery;
    }

    @Override
    public void onStart() {
        // Recovery already initiated by the detection in LoomRecoverySystem.tick()
    }

    @Override
    public void tick() {
        // Recovery is advanced by LoomRecoverySystem.tick() in LoomModule
    }

    @Override
    public void onPause() {
        recovery.cancelRecovery();
    }

    @Override
    public void onFail(Throwable cause) {
        recovery.cancelRecovery();
    }

    @Override
    public boolean isComplete() {
        return !recovery.isRecovering();
    }
}
