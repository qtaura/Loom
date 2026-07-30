package org.loom.scheduling;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.UUID;

/**
 * Default implementation of {@link TaskScheduler}.
 *
 * <p>Uses a priority queue for pending tasks. The highest-priority task
 * is always the active task. A newly submitted task preempts the current
 * task if its priority is strictly greater.
 */
public class LoomTaskScheduler implements TaskScheduler {

    private final PriorityQueue<TaskHandle> pendingQueue;
    private TaskHandle activeTask;

    public LoomTaskScheduler() {
        this.pendingQueue = new PriorityQueue<>(
            Comparator.comparing(TaskHandle::getPriority).reversed()
        );
        this.activeTask = null;
    }

    @Override
    public TaskHandle submit(Task task, TaskPriority priority) {
        String taskId = task.getClass().getSimpleName() + "-" + UUID.randomUUID().toString().substring(0, 8);
        TaskHandle handle = new TaskHandle(taskId, task, priority);

        // TODO: Check if this task should preempt the current active task
        // TODO: If so, pause current task, enqueue it, and start new task
        // TODO: Otherwise, enqueue the new task

        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void cancel(TaskHandle handle) {
        // TODO: If active, call onFail and dequeue next
        // TODO: If pending, remove from queue
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void pause(TaskHandle handle) {
        // TODO: Call task.onPause() and set state to PAUSED
        // TODO: Dequeue next task if this was active
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void resume(TaskHandle handle) {
        // TODO: Re-enqueue at original priority
        // TODO: Call task.onResume() when it becomes active
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public TaskHandle getActiveTask() {
        return activeTask;
    }

    @Override
    public void tick() {
        // TODO: If active task is complete, transition to COMPLETED and dequeue next
        // TODO: If idle and queue has tasks, dequeue next and start it
        // TODO: If active task exists, call activeTask.getTask().tick()
        // TODO: Publish task state change events
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
