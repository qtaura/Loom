package org.loom.scheduling;

/**
 * Lifecycle states for a {@link Task}.
 */
public enum TaskState {

    /** Task is queued and waiting to execute. */
    PENDING,

    /** Task is currently executing. */
    RUNNING,

    /** Task has been paused and can be resumed. */
    PAUSED,

    /** Task completed successfully. */
    COMPLETED,

    /** Task failed and cannot be resumed. */
    FAILED
}
