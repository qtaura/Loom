package org.loom.jobs;

import java.util.List;
import java.util.Optional;

/**
 * Manages long-running build jobs.
 *
 * <p>A job is "build this schematic at this world position." The JobManager
 * owns the lifecycle of jobs across plugin restarts. It creates, serializes,
 * queues, and tracks jobs. Per-block placement state is delegated to
 * {@link org.loom.state.ProgressTracker}.
 */
public interface JobManager {

    /**
     * Creates a new build job for the given schematic at the given world origin.
     *
     * @param schematicId the registered schematic identifier
     * @param originX     world X origin of the build area
     * @param originY     world Y origin (base layer) of the build area
     * @param originZ     world Z origin of the build area
     * @return the newly created job
     */
    Job createJob(String schematicId, int originX, int originY, int originZ);

    /**
     * Returns the currently active job, if one exists.
     */
    Optional<Job> getActiveJob();

    /**
     * Returns all queued jobs in priority order.
     */
    List<Job> getQueuedJobs();

    /**
     * Cancels a job by its ID.
     *
     * @param jobId the job to cancel
     */
    void cancelJob(String jobId);

    /**
     * Moves a job to a new position in the queue.
     *
     * @param jobId    the job to reorder
     * @param newIndex the target position (0-based)
     */
    void reorderJob(String jobId, int newIndex);

    /**
     * Returns progress summary for a job.
     */
    JobProgress getProgress(String jobId);

    /**
     * Persists a single job to disk.
     */
    void saveJob(String jobId);

    /**
     * Loads all jobs from disk. Called at plugin startup.
     */
    void loadAllJobs();
}
