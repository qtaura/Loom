package org.loom.jobs;

import org.loom.log.LoomLogger;
import org.loom.schematic.SchematicManager;
import org.loom.state.ProgressTracker;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Default implementation of {@link JobManager}.
 *
 * <p>Manages job lifecycle: creation, persistence, state transitions,
 * and startup recovery. Delegates per-block progress to
 * {@link ProgressTracker} and schematic validation to
 * {@link SchematicManager}.
 *
 * <p>Only one job may be {@link JobState#ACTIVE} at a time.
 */
public class LoomJobManager implements JobManager {

    private static final String TAG = "JobManager";

    private final List<Job> jobs;
    private final JobStore store;
    private final SchematicManager schematicManager;
    private final ProgressTracker progressTracker;
    private final LoomLogger logger;
    private Job interruptedJob;

    public LoomJobManager(SchematicManager schematicManager,
                          ProgressTracker progressTracker,
                          LoomLogger logger) {
        this.jobs = new ArrayList<>();
        this.store = new JobStore();
        this.schematicManager = schematicManager;
        this.progressTracker = progressTracker;
        this.logger = logger;
    }

    // ==================================================================
    // Lifecycle
    // ==================================================================

    @Override
    public synchronized Job createJob(String schematicId, int originX, int originY, int originZ) {
        var schematic = schematicManager.getSchematic(schematicId);
        if (schematic.isEmpty()) {
            logger.warn(TAG, "Cannot create job: schematic '%s' not loaded", schematicId);
            return null;
        }

        String id = UUID.randomUUID().toString().substring(0, 8);
        Job job = new Job(id, schematicId, originX, originY, originZ);
        jobs.add(job);
        store.save(job);
        logger.info(TAG, "Created job %s: %s at (%d,%d,%d)",
            id, schematicId, originX, originY, originZ);
        return job;
    }

    @Override
    public Optional<Job> getJob(String jobId) {
        return jobs.stream().filter(j -> j.getId().equals(jobId)).findFirst();
    }

    @Override
    public List<Job> getAllJobs() {
        return List.copyOf(jobs);
    }

    @Override
    public Optional<Job> getActiveJob() {
        return jobs.stream().filter(j -> j.getState() == JobState.ACTIVE).findFirst();
    }

    @Override
    public Optional<Job> getInterruptedJob() {
        return Optional.ofNullable(interruptedJob);
    }

    @Override
    public List<Job> getQueuedJobs() {
        return jobs.stream().filter(j -> j.getState() == JobState.QUEUED).toList();
    }

    // ==================================================================
    // State transitions
    // ==================================================================

    @Override
    public synchronized void startJob(String jobId) {
        // Deactivate any currently active job
        for (Job j : jobs) {
            if (j.getState() == JobState.ACTIVE) {
                j.setState(JobState.PAUSED);
                store.save(j);
            }
        }

        Job job = findJob(jobId);
        if (job == null) return;

        job.setState(JobState.ACTIVE);
        store.save(job);
        logger.info(TAG, "Started job %s", jobId);
    }

    @Override
    public synchronized void pauseJob(String jobId) {
        Job job = findJob(jobId);
        if (job == null) return;

        job.setState(JobState.PAUSED);
        store.save(job);
        progressTracker.save(jobId);
        logger.info(TAG, "Paused job %s", jobId);
    }

    @Override
    public synchronized void resumeJob(String jobId) {
        startJob(jobId);
        logger.info(TAG, "Resumed job %s", jobId);
    }

    @Override
    public synchronized void completeJob(String jobId) {
        Job job = findJob(jobId);
        if (job == null) return;

        job.setState(JobState.COMPLETED);
        store.save(job);
        progressTracker.save(jobId);
        logger.info(TAG, "Completed job %s", jobId);
    }

    @Override
    public synchronized void failJob(String jobId, String reason) {
        Job job = findJob(jobId);
        if (job == null) return;

        job.setState(JobState.PAUSED);
        store.save(job);
        progressTracker.save(jobId);
        logger.warn(TAG, "Job %s failed: %s", jobId, reason);
    }

    @Override
    public synchronized void cancelJob(String jobId) {
        Job job = findJob(jobId);
        if (job == null) return;

        job.setState(JobState.CANCELLED);
        store.save(job);
        progressTracker.clear(jobId);
        logger.info(TAG, "Cancelled job %s", jobId);
    }

    @Override
    public synchronized void deleteJob(String jobId) {
        jobs.removeIf(j -> j.getId().equals(jobId));
        store.delete(jobId);
        progressTracker.clear(jobId);
        logger.info(TAG, "Deleted job %s", jobId);
    }

    @Override
    public synchronized void reorderJob(String jobId, int newIndex) {
        Job job = findJob(jobId);
        if (job == null) return;

        jobs.remove(job);
        int clampedIndex = Math.max(0, Math.min(newIndex, jobs.size()));
        jobs.add(clampedIndex, job);
        logger.debug(TAG, "Reordered job %s to index %d", jobId, clampedIndex);
    }

    // ==================================================================
    // Queries
    // ==================================================================

    @Override
    public JobProgress getProgress(String jobId) {
        Job job = findJob(jobId);
        if (job == null) return new JobProgress(jobId, 0, 0);
        return new JobProgress(jobId,
            progressTracker.getTotalBlocks(),
            progressTracker.getTotalPlaced());
    }

    // ==================================================================
    // Persistence
    // ==================================================================

    @Override
    public void saveJob(String jobId) {
        Job job = findJob(jobId);
        if (job != null) store.save(job);
    }

    @Override
    public void loadAllJobs() {
        jobs.clear();
        List<Job> loaded = store.loadAll();
        jobs.addAll(loaded);

        // If there's an ACTIVE job from a previous session, it was interrupted.
        interruptedJob = null;
        for (Job job : jobs) {
            if (job.getState() == JobState.ACTIVE) {
                job.setState(JobState.PAUSED);
                interruptedJob = job;
                store.save(job);
                logger.info(TAG, "Recovered interrupted job %s (set to PAUSED)", job.getId());
            }
        }

        logger.info(TAG, "Loaded %d jobs", jobs.size());
    }

    // ==================================================================
    // Internal
    // ==================================================================

    private Job findJob(String jobId) {
        return jobs.stream().filter(j -> j.getId().equals(jobId)).findFirst().orElse(null);
    }
}
