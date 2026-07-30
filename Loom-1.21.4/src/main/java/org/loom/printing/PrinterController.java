package org.loom.printing;

import org.loom.jobs.Job;

/**
 * Core map art printing controller.
 *
 * <p>Given a job (schematic + origin + progress state), the printer advances
 * through the build area column by column, delegating individual placements
 * to the {@link PlacementEngine}.
 *
 * <p>The printer is ticked once per client tick by the {@code PrintTask}.
 * Each tick performs one atomic step: check next position, skip if already
 * placed, place if needed, or advance.
 */
public interface PrinterController {

    /**
     * Starts or resumes printing for the given job.
     *
     * @param job the job to build
     */
    void start(Job job);

    /**
     * Pauses printing. Progress is saved before pausing.
     */
    void pause();

    /**
     * Resumes printing from where it was paused.
     */
    void resume();

    /**
     * Cancels printing entirely. The job state is set to CANCELLED.
     */
    void cancel();

    /**
     * Advances the printer by one step.
     *
     * <p>Called each bot tick by the active {@code PrintTask}.
     * Performs one atomic unit of work and returns.
     */
    void tick();

    /**
     * Returns the current schematic-relative position being worked on,
     * or {@code null} if not printing.
     */
    int[] getCurrentPosition();

    /**
     * Returns the current schematic-relative row, or -1 if not printing.
     */
    int getRow();

    /**
     * Returns the current schematic-relative column, or -1 if not printing.
     */
    int getColumn();

    /**
     * Returns true if the printer is currently executing.
     */
    boolean isPrinting();
}
