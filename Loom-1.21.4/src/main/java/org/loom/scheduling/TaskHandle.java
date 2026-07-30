package org.loom.scheduling;

/**
 * A handle to a task submitted to the {@link TaskScheduler}.
 *
 * <p>Used to query state, pause, resume, or cancel a task without holding
 * a direct reference to the task instance.
 */
public class TaskHandle {

    private final String taskId;
    private final Task task;
    private TaskState state;
    private TaskPriority priority;

    public TaskHandle(String taskId, Task task, TaskPriority priority) {
        this.taskId = taskId;
        this.task = task;
        this.priority = priority;
        this.state = TaskState.PENDING;
    }

    public String getTaskId() {
        return taskId;
    }

    public Task getTask() {
        return task;
    }

    public TaskState getState() {
        return state;
    }

    public void setState(TaskState state) {
        this.state = state;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public void setPriority(TaskPriority priority) {
        this.priority = priority;
    }

    @Override
    public String toString() {
        return "TaskHandle{taskId='" + taskId + "', state=" + state + ", priority=" + priority + "}";
    }
}
