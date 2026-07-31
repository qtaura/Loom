package org.loom.scheduling;

import org.loom.batch.BatchOrchestrator;
import org.loom.jobs.Job;
import org.loom.repair.LoomResetSystem;

/**
 * A task that drives {@link LoomResetSystem} to clear the build area.
 *
 * <p>When the reset completes, notifies the {@link BatchOrchestrator}
 * so it can advance to the next file in the batch.
 */
public class ResetTask extends Task {

    private final Job job;
    private final LoomResetSystem resetSystem;
    private final BatchOrchestrator orchestrator;
    private final int chestX, chestY, chestZ;

    public ResetTask(Job job, LoomResetSystem resetSystem,
                      BatchOrchestrator orchestrator,
                      int chestX, int chestY, int chestZ) {
        super("ResetTask-" + job.getId());
        this.job = job;
        this.resetSystem = resetSystem;
        this.orchestrator = orchestrator;
        this.chestX = chestX;
        this.chestY = chestY;
        this.chestZ = chestZ;
    }

    @Override
    public void onStart() {
        resetSystem.start(job, chestX, chestY, chestZ);
    }

    @Override
    public void tick() {
        resetSystem.tick();
    }

    @Override
    public void onPause() { resetSystem.cancel(); }

    @Override
    public void onFail(Throwable cause) {
        resetSystem.cancel();
        if (orchestrator != null) orchestrator.onResetComplete(true);
    }

    @Override
    public void onComplete() {
        if (orchestrator != null) orchestrator.onResetComplete(false);
    }

    @Override
    public boolean isComplete() {
        return !resetSystem.isActive();
    }
}
