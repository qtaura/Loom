package org.loom.jobs;

/**
 * High-level progress summary for a {@link Job}.
 *
 * <p>This is a summary for display and persistence. The authoritative
 * per-block state lives in {@link org.loom.state.ProgressTracker}.
 */
public class JobProgress {

    private final String jobId;
    private final int totalBlocks;
    private final int placedBlocks;
    private final double percentComplete;

    public JobProgress(String jobId, int totalBlocks, int placedBlocks) {
        this.jobId = jobId;
        this.totalBlocks = totalBlocks;
        this.placedBlocks = placedBlocks;
        this.percentComplete = totalBlocks > 0 ? (double) placedBlocks / totalBlocks * 100.0 : 0.0;
    }

    public String getJobId() {
        return jobId;
    }

    public int getTotalBlocks() {
        return totalBlocks;
    }

    public int getPlacedBlocks() {
        return placedBlocks;
    }

    public double getPercentComplete() {
        return percentComplete;
    }

    @Override
    public String toString() {
        return String.format("JobProgress{%s: %d/%d (%.1f%%)}", jobId, placedBlocks, totalBlocks, percentComplete);
    }
}
