package org.loom.scheduling;

import org.loom.recovery.RecoverySystem;

/**
 * A task that wraps an active recovery.
 *
 * <p>Submitted at {@link TaskPriority#CRITICAL} to preempt all other tasks.
 * The recovery system itself advances via {@link LoomModule}'s tick loop.
 * This task simply provides scheduler tracking — when recovery completes,
 * this task marks itself complete and the scheduler resumes the previous task.
 */
public class RecoveryTask extends Task {

    private final RecoverySystem recoverySystem;

    public RecoveryTask(RecoverySystem recoverySystem) {
        super("Recovery-" + (recoverySystem.getRecoveryType() != null
            ? recoverySystem.getRecoveryType().name() : "unknown"));
        this.recoverySystem = recoverySystem;
    }

    @Override
    public void onStart() {
        // Recovery is initiated by LoomRecoverySystem detection methods
    }

    @Override
    public void tick() {
        // Recovery advances via LoomModule.handleBotTick → recoverySystem.tick()
    }

    @Override
    public void onPause() {
        recoverySystem.cancelRecovery();
    }

    @Override
    public void onFail(Throwable cause) {
        recoverySystem.cancelRecovery();
    }

    @Override
    public boolean isComplete() {
        return !recoverySystem.isRecovering();
    }
}
