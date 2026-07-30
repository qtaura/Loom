package org.loom.scheduling;

/**
 * Priority levels for tasks submitted to the {@link TaskScheduler}.
 *
 * <p>Higher ordinal means higher priority. A higher-priority task preempts
 * a lower-priority one currently executing.
 */
public enum TaskPriority {

    /** For recovery tasks (death, disconnect, combat). Preempts all other work. */
    CRITICAL,

    /** For restock tasks that the printer is waiting on. */
    HIGH,

    /** Default priority for print/build tasks. */
    NORMAL,

    /** Background tasks like area cleanup or pre-scanning. */
    LOW
}
