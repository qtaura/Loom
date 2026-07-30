package org.loom.scheduling;

/**
 * Central orchestrator for Loom.
 *
 * <p>The task scheduler maintains a priority queue of pending tasks,
 * ensures only one task is active at a time, handles preemption,
 * and dispatches to the correct subsystem based on task type.
 *
 * <p>All bot action flows through the scheduler. It is ticked once
 * per client tick by {@link org.loom.LoomModule}.
 */
public interface TaskScheduler {

    /**
     * Submits a task for execution at the given priority.
     *
     * @param task     the task to schedule
     * @param priority the execution priority
     * @return a handle that can be used to query or control the task
     */
    TaskHandle submit(Task task, TaskPriority priority);

    /**
     * Cancels the task identified by the handle.
     *
     * <p>If the task is currently active, it will be stopped and removed.
     * If it is pending, it will be dequeued.
     *
     * @param handle the task handle to cancel
     */
    void cancel(TaskHandle handle);

    /**
     * Pauses the task identified by the handle.
     *
     * <p>The task's {@link Task#onPause()} method will be called.
     * The scheduler will select a new active task if one is pending.
     *
     * @param handle the task handle to pause
     */
    void pause(TaskHandle handle);

    /**
     * Resumes a previously paused task.
     *
     * <p>The task will be re-queued at its original priority.
     * It will preempt the current task if its priority is higher.
     *
     * @param handle the task handle to resume
     */
    void resume(TaskHandle handle);

    /**
     * Returns the currently active task, if any.
     *
     * @return the active task handle, or {@code null} if idle
     */
    TaskHandle getActiveTask();

    /**
     * Advances the scheduler by one tick.
     *
     * <p>Called by the module's {@code ClientBotTick} handler.
     * Performs one atomic scheduling decision and ticks the active task.
     */
    void tick();
}
