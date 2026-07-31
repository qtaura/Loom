package org.loom.scheduling;

import org.loom.jobs.Job;
import org.loom.repair.LoomResetSystem;

/**
 * A task that drives {@link LoomResetSystem} to clear the build area.
 *
 * <p>Submitted at {@link TaskPriority#HIGH} to preempt printing.
 * Used before starting a new job to ensure the build area is clear.
 */
public class ResetTask extends Task {

    private final Job job;
    private final LoomResetSystem resetSystem;

    public ResetTask(Job job, LoomResetSystem resetSystem) {
        super("ResetTask-" + job.getId());
        this.job = job;
        this.resetSystem = resetSystem;
    }

    @Override
    public void onStart() {
        resetSystem.start(job);
    }

    @Override
    public void tick() {
        resetSystem.tick();
    }

    @Override
    public void onPause() {
        resetSystem.cancel();
    }

    @Override
    public void onFail(Throwable cause) {
        resetSystem.cancel();
    }

    @Override
    public boolean isComplete() {
        return !resetSystem.isActive();
    }
}
