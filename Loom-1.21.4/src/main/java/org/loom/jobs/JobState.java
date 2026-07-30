package org.loom.jobs;

/**
 * Lifecycle states for a {@link Job}.
 */
public enum JobState {

    /** Job is queued and waiting to be started. */
    QUEUED,

    /** Job is actively being worked on. */
    ACTIVE,

    /** Job has been paused and can be resumed. */
    PAUSED,

    /** Job completed successfully. */
    COMPLETED,

    /** Job was cancelled by the user. */
    CANCELLED
}
