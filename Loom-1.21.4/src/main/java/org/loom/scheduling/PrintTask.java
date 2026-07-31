package org.loom.scheduling;

import org.loom.jobs.Job;
import org.loom.jobs.JobManager;
import org.loom.printing.PrinterController;

/**
 * A task that drives the {@link PrinterController} to build a single map art.
 *
 * <p>Bridges the generic {@link TaskScheduler} to the domain-specific
 * printer pipeline. Delegates tick-by-tick control to the printer and
 * manages job lifecycle via {@link JobManager}.
 */
public class PrintTask extends Task {

    private final Job job;
    private final PrinterController printerController;
    private final JobManager jobManager;

    public PrintTask(Job job, PrinterController printerController, JobManager jobManager) {
        super("PrintTask-" + job.getId());
        this.job = job;
        this.printerController = printerController;
        this.jobManager = jobManager;
    }

    public Job getJob() {
        return job;
    }

    @Override
    public void onStart() {
        jobManager.startJob(job.getId());
        printerController.start(job);
    }

    @Override
    public void tick() {
        printerController.tick();
    }

    @Override
    public void onPause() {
        printerController.pause();
        jobManager.pauseJob(job.getId());
    }

    @Override
    public void onResume() {
        printerController.resume();
        jobManager.resumeJob(job.getId());
    }

    @Override
    public void onComplete() {
        printerController.cancel();
        jobManager.completeJob(job.getId());
    }

    @Override
    public void onFail(Throwable cause) {
        printerController.pause();
        jobManager.failJob(job.getId(), cause.getMessage());
    }

    @Override
    public boolean isComplete() {
        return !printerController.isPrinting();
    }
}
