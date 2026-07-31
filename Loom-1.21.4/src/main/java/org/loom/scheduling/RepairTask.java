package org.loom.scheduling;

import org.loom.jobs.Job;
import org.loom.repair.LoomErrorRepair;

/**
 * A task that drives {@link LoomErrorRepair} to detect and fix incorrect blocks.
 *
 * <p>Submitted at {@link TaskPriority#HIGH} to preempt printing.
 * When repair completes, the paused {@link PrintTask} is automatically
 * resumed by the scheduler.
 */
public class RepairTask extends Task {

    private final Job job;
    private final LoomErrorRepair repair;

    public RepairTask(Job job, LoomErrorRepair repair) {
        super("RepairTask-" + job.getId());
        this.job = job;
        this.repair = repair;
    }

    @Override
    public void onStart() {
        repair.start(job);
    }

    @Override
    public void tick() {
        repair.tick();
    }

    @Override
    public void onPause() {
        repair.cancel();
    }

    @Override
    public void onFail(Throwable cause) {
        repair.cancel();
    }

    @Override
    public boolean isComplete() {
        return !repair.isActive();
    }
}
