package org.loom.scheduling;

import org.loom.log.LoomLogger;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.UUID;

/**
 * Default implementation of {@link TaskScheduler}.
 *
 * <p>Priority-based scheduling with preemption. A higher-priority task
 * pauses the current active task, which resumes when the higher-priority
 * task completes.
 *
 * <h3>Tick model</h3>
 * <ol>
 *   <li>If active task exists, tick it</li>
 *   <li>If active task reports {@code isComplete()}, transition to COMPLETED
 *       and dequeue next pending task</li>
 *   <li>If idle and queue has tasks, dequeue highest priority as active</li>
 * </ol>
 */
public class LoomTaskScheduler implements TaskScheduler {

    private static final String TAG = "Scheduler";

    private final PriorityQueue<TaskHandle> pendingQueue;
    private TaskHandle activeTask;
    private final LoomLogger logger;

    public LoomTaskScheduler(LoomLogger logger) {
        this.logger = logger;
        this.pendingQueue = new PriorityQueue<>(
            Comparator.comparing(TaskHandle::getPriority).reversed()
        );
        this.activeTask = null;
    }

    // ==================================================================
    // Public API
    // ==================================================================

    @Override
    public synchronized TaskHandle submit(Task task, TaskPriority priority) {
        String taskId = task.getClass().getSimpleName() + "-"
            + UUID.randomUUID().toString().substring(0, 6);
        TaskHandle handle = new TaskHandle(taskId, task, priority);

        // Check preemption: if new task has higher priority than active, preempt
        if (activeTask != null
            && activeTask.getState() == TaskState.RUNNING
            && priority.compareTo(activeTask.getPriority()) > 0) {

            logger.info(TAG, "Preempt: %s (%s) preempts %s (%s)",
                handle.getTaskId(), priority,
                activeTask.getTaskId(), activeTask.getPriority());

            activeTask.getTask().onPause();
            activeTask.setState(TaskState.PAUSED);
            pendingQueue.add(activeTask);
            activateTask(handle);
        } else if (activeTask == null
            || activeTask.getState() == TaskState.PAUSED
            || activeTask.getState() == TaskState.COMPLETED
            || activeTask.getState() == TaskState.FAILED) {

            activateTask(handle);
        } else {
            pendingQueue.add(handle);
            logger.debug(TAG, "Queued %s (%s), %d pending",
                handle.getTaskId(), priority, pendingQueue.size());
        }

        return handle;
    }

    @Override
    public synchronized void cancel(TaskHandle handle) {
        if (handle == activeTask) {
            handle.getTask().onPause();
            handle.setState(TaskState.FAILED);
            activeTask = null;
            dequeueNext();
        } else {
            pendingQueue.remove(handle);
            handle.setState(TaskState.FAILED);
        }
        logger.debug(TAG, "Cancelled %s", handle.getTaskId());
    }

    @Override
    public synchronized void pause(TaskHandle handle) {
        if (handle == activeTask && handle.getState() == TaskState.RUNNING) {
            handle.getTask().onPause();
            handle.setState(TaskState.PAUSED);
            activeTask = null;
            dequeueNext();
            logger.debug(TAG, "Paused %s", handle.getTaskId());
        }
    }

    @Override
    public synchronized void resume(TaskHandle handle) {
        if (handle.getState() == TaskState.PAUSED) {
            handle.getTask().onResume();
            handle.setState(TaskState.PENDING);
            // Re-queue at original priority — may preempt current
            TaskHandle newHandle = submit(handle.getTask(), handle.getPriority());
            // Sync the caller's handle reference
            handle.setState(newHandle.getState());
        }
    }

    @Override
    public TaskHandle getActiveTask() {
        return activeTask;
    }

    @Override
    public synchronized void tick() {
        // Tick the active task
        if (activeTask != null && activeTask.getState() == TaskState.RUNNING) {
            try {
                activeTask.getTask().tick();
            } catch (Exception e) {
                logger.error(TAG, "Task " + activeTask.getTaskId() + " threw", e);
                activeTask.getTask().onFail(e);
                activeTask.setState(TaskState.FAILED);
                activeTask = null;
                dequeueNext();
                return;
            }

            // Check completion
            if (activeTask.getTask().isComplete()) {
                activeTask.getTask().onComplete();
                activeTask.setState(TaskState.COMPLETED);
                logger.info(TAG, "Completed %s", activeTask.getTaskId());
                activeTask = null;
                dequeueNext();
            }
        } else if (activeTask == null) {
            dequeueNext();
        }
    }

    // ==================================================================
    // Internal
    // ==================================================================

    private void activateTask(TaskHandle handle) {
        handle.setState(TaskState.RUNNING);
        activeTask = handle;
        handle.getTask().onStart();
        logger.info(TAG, "Activated %s (%s)", handle.getTaskId(), handle.getPriority());
    }

    private void dequeueNext() {
        if (!pendingQueue.isEmpty()) {
            TaskHandle next = pendingQueue.poll();
            activateTask(next);
        }
    }
}
