package org.loom;

import com.github.rfresh2.EventConsumer;
import com.zenith.event.client.ClientBotTick;
import com.zenith.module.api.Module;
import org.loom.jobs.Job;
import org.loom.jobs.JobManager;
import org.loom.recovery.RecoverySystem;
import org.loom.scheduling.PrintTask;
import org.loom.scheduling.RecoveryTask;
import org.loom.scheduling.TaskPriority;
import org.loom.scheduling.TaskScheduler;

import java.util.List;
import java.util.Optional;

import static com.github.rfresh2.EventConsumer.of;

/**
 * Main ZenithProxy module for Loom.
 *
 * <p>Subscribes to the bot tick loop, runs recovery monitoring first,
 * then advances the {@link TaskScheduler} one step per tick.
 */
public class LoomModule extends Module {

    private final TaskScheduler taskScheduler;
    private final JobManager jobManager;
    private final RecoverySystem recoverySystem;

    public LoomModule(TaskScheduler taskScheduler,
                       JobManager jobManager,
                       RecoverySystem recoverySystem) {
        this.taskScheduler = taskScheduler;
        this.jobManager = jobManager;
        this.recoverySystem = recoverySystem;
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
        Optional<Job> activeJob = jobManager.getActiveJob();
        if (activeJob.isPresent()) {
            Job job = activeJob.get();
            info("Resuming interrupted job: %s", job.getId());
            PrintTask printTask = new PrintTask(job,
                LoomPlugin.printerController, jobManager, LoomPlugin.eventBus);
            taskScheduler.submit(printTask, TaskPriority.NORMAL);
        }
    }

    @Override
    public void onDisable() {
        var active = taskScheduler.getActiveTask();
        if (active != null) {
            taskScheduler.pause(active);
        }
    }

    private void handleBotTickStarting(ClientBotTick.Starting event) {}

    private void handleBotTickStopped(ClientBotTick.Stopped event) {
        var active = taskScheduler.getActiveTask();
        if (active != null) {
            taskScheduler.pause(active);
        }
    }

    private void handleBotTick(ClientBotTick event) {
        // Recovery runs first — can preempt the active task
        recoverySystem.tick();

        // If a recovery just started, submit a RecoveryTask to preempt printing
        if (recoverySystem.isRecovering()) {
            var active = taskScheduler.getActiveTask();
            if (active == null || active.getPriority() != TaskPriority.CRITICAL) {
                taskScheduler.submit(new RecoveryTask(recoverySystem),
                    TaskPriority.CRITICAL);
            }
        }

        taskScheduler.tick();
    }
}
