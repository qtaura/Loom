package org.loom.scheduling;

/**
 * A unit of work that can be scheduled, executed, paused, resumed, and cancelled.
 *
 * <p>Subsystems submit concrete task implementations to the {@link TaskScheduler}.
 * Each task encapsulates a specific domain action (printing, restocking, recovery).
 */
public abstract class Task {

    private final String id;

    protected Task(String id) {
        this.id = id;
    }

    /**
     * Returns a human-readable identifier for this task.
     */
    public String getId() {
        return id;
    }

    /**
     * Called when this task becomes the active task and begins execution.
     */
    public void onStart() {
        // TODO: Subclasses override to initialize state
    }

    /**
     * Advances the task by one tick.
     *
     * <p>Called every bot tick while this task is the active task.
     * Implementations should perform one atomic unit of work and return.
     * They must NOT block or loop within this method.
     */
    public abstract void tick();

    /**
     * Called when the task is paused (preempted by a higher-priority task).
     */
    public void onPause() {
        // TODO: Subclasses override to save transient state
    }

    /**
     * Called when the task is resumed after being paused.
     */
    public void onResume() {
        // TODO: Subclasses override to restore transient state
    }

    /**
     * Called when the task completes successfully.
     */
    public void onComplete() {
        // TODO: Subclasses override to finalize
    }

    /**
     * Called when the task fails and cannot be resumed.
     */
    public void onFail(Throwable cause) {
        // TODO: Subclasses override to handle failure
    }

    /**
     * Returns true if the task has more work to do.
     * When false, the scheduler transitions the task to COMPLETED.
     */
    public abstract boolean isComplete();

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{id='" + id + "'}";
    }
}
