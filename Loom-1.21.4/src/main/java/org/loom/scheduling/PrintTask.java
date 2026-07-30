package org.loom.scheduling;

import org.loom.jobs.Job;

/**
 * A task that drives the {@link org.loom.printing.PrinterController} to build
 * a single map art from a {@link Job}.
 *
 * <p>This task bridges the generic {@link TaskScheduler} to the domain-specific
 * printer pipeline. It holds a reference to the job and delegates tick-by-tick
 * control to the printer.
 */
public class PrintTask extends Task {

    private final Job job;

    public PrintTask(Job job) {
        super("PrintTask-" + job.getId());
        this.job = job;
    }

    /**
     * Returns the job this task is building.
     */
    public Job getJob() {
        return job;
    }

    @Override
    public void onStart() {
        // TODO: Initialize PrinterController with this job
        // TODO: Load progress if resuming
        // TODO: Validate schematic is loaded
    }

    @Override
    public void tick() {
        // TODO: Delegate to PrinterController.tick()
        // TODO: Check for completion
        // TODO: Signal restock if needed
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void onPause() {
        // TODO: Pause PrinterController
        // TODO: Save progress to disk
    }

    @Override
    public void onResume() {
        // TODO: Resume PrinterController
        // TODO: Reload progress if needed
    }

    @Override
    public void onComplete() {
        // TODO: Mark job as completed in JobManager
        // TODO: Save final progress
        // TODO: Publish PrintCompletedEvent
    }

    @Override
    public void onFail(Throwable cause) {
        // TODO: Save progress
        // TODO: Publish PrintFailedEvent
        // TODO: Defer to RecoverySystem
    }

    @Override
    public boolean isComplete() {
        // TODO: Delegate to PrinterController.isPrinting() == false
        // and all blocks placed
        return false;
    }
}
