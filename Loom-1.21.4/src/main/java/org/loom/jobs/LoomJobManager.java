package org.loom.jobs;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Default implementation of {@link JobManager}.
 */
public class LoomJobManager implements JobManager {

    private final List<Job> jobs;
    private Job activeJob;

    public LoomJobManager() {
        this.jobs = new ArrayList<>();
        this.activeJob = null;
    }

    @Override
    public Job createJob(String schematicId, int originX, int originY, int originZ) {
        // TODO: Validate that schematic exists in SchematicManager
        // TODO: Generate unique job ID
        // TODO: Create Job instance
        // TODO: Add to jobs list
        // TODO: Persist to disk
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public Optional<Job> getActiveJob() {
        return Optional.ofNullable(activeJob);
    }

    @Override
    public List<Job> getQueuedJobs() {
        return List.copyOf(jobs);
    }

    @Override
    public void cancelJob(String jobId) {
        // TODO: Find job by ID
        // TODO: Set state to CANCELLED
        // TODO: Clear progress from ProgressTracker
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void reorderJob(String jobId, int newIndex) {
        // TODO: Find job by ID
        // TODO: Move to new position in list
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public JobProgress getProgress(String jobId) {
        // TODO: Query ProgressTracker for this job
        // TODO: Return JobProgress with totals
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void saveJob(String jobId) {
        // TODO: Serialize job metadata to JSON
        // TODO: Write to plugins/config/jobs/<id>.json
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void loadAllJobs() {
        // TODO: Scan plugins/config/jobs/ directory
        // TODO: Deserialize each job file
        // TODO: Rebuild job list
        // TODO: Identify active job from persisted state
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
