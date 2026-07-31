package org.loom.jobs;

/**
 * A long-running build job representing "build this schematic at this world position."
 *
 * <p>Jobs are created by the {@link JobManager} and contain the metadata needed
 * to start, resume, and track a build. The per-block placement state lives in
 * {@link org.loom.state.ProgressTracker}, not here.
 */
public class Job {

    private final String id;
    private final String schematicId;
    private final int originX;
    private final int originY;
    private final int originZ;
    private final long createdAt;
    private JobState state;

    public Job(String id, String schematicId, int originX, int originY, int originZ) {
        this.id = id;
        this.schematicId = schematicId;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.createdAt = System.currentTimeMillis();
        this.state = JobState.QUEUED;
    }

    public String getId() {
        return id;
    }

    public String getSchematicId() {
        return schematicId;
    }

    public int getOriginX() {
        return originX;
    }

    public int getOriginY() {
        return originY;
    }

    public int getOriginZ() {
        return originZ;
    }

    public long getCreatedAt() {
        return createdAt > 0 ? createdAt : System.currentTimeMillis();
    }

    public JobState getState() {
        return state != null ? state : JobState.QUEUED;
    }

    public void setState(JobState state) {
        this.state = state;
    }

    @Override
    public String toString() {
        return "Job{id='" + id + "', schematic='" + schematicId + "', origin=("
            + originX + "," + originY + "," + originZ + "), state=" + state + "}";
    }
}
