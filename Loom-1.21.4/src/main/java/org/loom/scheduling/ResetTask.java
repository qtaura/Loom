package org.loom.scheduling;

import org.loom.jobs.Job;
import org.loom.repair.LoomResetSystem;

/**
 * Nerv-compatible reset task. Preempts printing at HIGH priority.
 */
public class ResetTask extends Task {

    private final Job job;
    private final LoomResetSystem resetSystem;
    private final int chestX;
    private final int chestY;
    private final int chestZ;

    public ResetTask(Job job, LoomResetSystem resetSystem,
                      int chestX, int chestY, int chestZ) {
        super("ResetTask-" + job.getId());
        this.job = job;
        this.resetSystem = resetSystem;
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
