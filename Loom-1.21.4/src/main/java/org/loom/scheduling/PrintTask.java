package org.loom.scheduling;

import org.loom.batch.BatchOrchestrator;
import org.loom.event.*;
import org.loom.jobs.Job;
import org.loom.jobs.JobManager;
import org.loom.printing.PrinterController;
import org.loom.util.AsyncLoomEventBus;

/**
 * A task that drives the {@link PrinterController} to build a single map art.
 */
public class PrintTask extends Task {

    private final Job job;
    private final PrinterController printerController;
    private final JobManager jobManager;
    private final AsyncLoomEventBus eventBus;
    private final BatchOrchestrator batchOrchestrator;

    public PrintTask(Job job, PrinterController printerController,
                      JobManager jobManager, AsyncLoomEventBus eventBus,
                      BatchOrchestrator batchOrchestrator) {
        super("PrintTask-" + job.getId());
        this.job = job;
        this.printerController = printerController;
        this.jobManager = jobManager;
        this.eventBus = eventBus;
        this.batchOrchestrator = batchOrchestrator;
    }

    public Job getJob() {
        return job;
    }

    @Override
    public void onStart() {
        jobManager.startJob(job.getId());
        printerController.start(job);
        eventBus.publish(new PrintStartedEvent(job.getId(), job.getSchematicId()));
    }

    @Override
    public void tick() {
        printerController.tick();
    }

    @Override
    public void onPause() {
        printerController.pause();
        jobManager.pauseJob(job.getId());
        var pos = printerController.getCurrentPosition();
        eventBus.publish(new PrintPausedEvent(job.getId(), pos[0], pos[1]));
    }

    @Override
    public void onResume() {
        printerController.resume();
        jobManager.resumeJob(job.getId());
        var pos = printerController.getCurrentPosition();
        eventBus.publish(new PrintResumedEvent(job.getId(), pos[0], pos[1]));
    }

    @Override
    public void onComplete() {
        printerController.cancel();
        jobManager.completeJob(job.getId());
        eventBus.publish(new PrintCompletedEvent(job.getId(), 0, 0));

        // Trigger next file in batch
        if (batchOrchestrator != null) {
            batchOrchestrator.onPrintComplete(job.getId());
        }
    }

    @Override
    public void onFail(Throwable cause) {
        printerController.pause();
        jobManager.failJob(job.getId(), cause.getMessage());
        eventBus.publish(new PrintFailedEvent(job.getId(), cause.getMessage()));
    }

    @Override
    public boolean isComplete() {
        return !printerController.isPrinting();
    }
}
