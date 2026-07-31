package org.loom;

import com.github.rfresh2.EventConsumer;
import com.zenith.event.client.ClientBotTick;
import com.zenith.module.api.Module;
import org.loom.jobs.Job;
import org.loom.jobs.JobManager;
import org.loom.scheduling.PrintTask;
import org.loom.scheduling.TaskPriority;
import org.loom.scheduling.TaskScheduler;

import java.util.List;
import java.util.Optional;

import static com.github.rfresh2.EventConsumer.of;

/**
 * Main ZenithProxy module for Loom.
 *
 * <p>Subscribes to the bot tick loop and advances the
 * {@link TaskScheduler} one step per tick. On enable, checks for
 * an interrupted active job and resumes it.
 */
public class LoomModule extends Module {

    private final TaskScheduler taskScheduler;
    private final JobManager jobManager;

    public LoomModule(TaskScheduler taskScheduler, JobManager jobManager) {
        this.taskScheduler = taskScheduler;
        this.jobManager = jobManager;
    }

    @Override
    public boolean enabledSetting() {
        return true;
    }

    @Override
    public List<EventConsumer<?>> registerEvents() {
        return List.of(
            of(ClientBotTick.class, this::handleBotTick),
            of(ClientBotTick.Starting.class, this::handleBotTickStarting),
            of(ClientBotTick.Stopped.class, this::handleBotTickStopped)
        );
    }

    @Override
    public void onEnable() {
        // Resume interrupted active job on startup
        Optional<Job> activeJob = jobManager.getActiveJob();
        if (activeJob.isPresent()) {
            Job job = activeJob.get();
            info("Resuming interrupted job: %s", job.getId());
            PrintTask printTask = new PrintTask(job,
                LoomPlugin.printerController, jobManager);
            taskScheduler.submit(printTask, TaskPriority.NORMAL);
        }
    }

    @Override
    public void onDisable() {
        // Pause active task on module disable
        var active = taskScheduler.getActiveTask();
        if (active != null) {
            taskScheduler.pause(active);
        }
    }

    private void handleBotTickStarting(ClientBotTick.Starting event) {
        // Reset any transient state when bot control begins
    }

    private void handleBotTickStopped(ClientBotTick.Stopped event) {
        // Pause active task when bot control stops
        var active = taskScheduler.getActiveTask();
        if (active != null) {
            taskScheduler.pause(active);
        }
    }

    private void handleBotTick(ClientBotTick event) {
        taskScheduler.tick();
    }
}
