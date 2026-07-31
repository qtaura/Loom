package org.loom.recovery;

import com.zenith.Proxy;
import org.loom.event.BotStuckEvent;
import org.loom.event.CombatDetectedEvent;
import org.loom.event.RecoveryCompletedEvent;
import org.loom.event.RecoveryStartedEvent;
import org.loom.jobs.JobManager;
import org.loom.log.LoomLogger;
import org.loom.navigation.Navigator;
import org.loom.scheduling.TaskPriority;
import org.loom.scheduling.TaskScheduler;
import org.loom.state.ProgressTracker;
import org.loom.util.AsyncLoomEventBus;

import java.util.Optional;

import static com.zenith.Globals.CACHE;

/**
 * Default implementation of {@link RecoverySystem}.
 *
 * <p>Monitors bot health and connection each tick. On detecting a failure
 * condition, saves progress, creates the appropriate {@link RecoveryAction},
 * and submits a {@link RecoveryTask} at {@link TaskPriority#CRITICAL}.
 */
public class LoomRecoverySystem implements RecoverySystem {

    private static final String TAG = "Recovery";
    private static final int MAX_ATTEMPTS = 5;

    private final Navigator navigator;
    private final ProgressTracker progressTracker;
    private final JobManager jobManager;
    private final TaskScheduler taskScheduler;
    private final LoomLogger logger;
    private final AsyncLoomEventBus eventBus;
    private final int buildOriginX;
    private final int buildOriginZ;

    private RecoveryAction currentRecovery;
    private int recoveryAttempts;
    private String currentJobId;

    public LoomRecoverySystem(Navigator navigator,
                               ProgressTracker progressTracker,
                               JobManager jobManager,
                               TaskScheduler taskScheduler,
                               LoomLogger logger,
                               AsyncLoomEventBus eventBus,
                               int buildOriginX,
                               int buildOriginZ) {
        this.navigator = navigator;
        this.progressTracker = progressTracker;
        this.jobManager = jobManager;
        this.taskScheduler = taskScheduler;
        this.logger = logger;
        this.eventBus = eventBus;
        this.buildOriginX = buildOriginX;
        this.buildOriginZ = buildOriginZ;
        this.currentRecovery = null;
        this.recoveryAttempts = 0;
    }

    // ==================================================================
    // Public API
    // ==================================================================

    @Override
    public void onDeath() {
        if (isRecovering()) return;
        recoveryAttempts++;
        if (recoveryAttempts > MAX_ATTEMPTS) {
            failActiveJob("Death recovery failed after " + MAX_ATTEMPTS + " attempts");
            return;
        }

        String jobId = getActiveJobId();
        logger.warn(TAG, "Death detected (attempt %d/%d), saving progress", recoveryAttempts, MAX_ATTEMPTS);
        progressTracker.save(jobId);

        currentRecovery = new DeathRecovery(
            navigator, progressTracker, jobId, buildOriginX, buildOriginZ);
        currentRecovery.onStart();
        eventBus.publish(new RecoveryStartedEvent(RecoveryReason.DEATH));
    }

    @Override
    public void onDisconnect() {
        if (isRecovering()) return;
        recoveryAttempts++;
        if (recoveryAttempts > MAX_ATTEMPTS) {
            failActiveJob("Disconnect recovery failed after " + MAX_ATTEMPTS + " attempts");
            return;
        }

        String jobId = getActiveJobId();
        logger.warn(TAG, "Disconnect detected (attempt %d/%d), saving progress", recoveryAttempts, MAX_ATTEMPTS);
        progressTracker.save(jobId);

        currentRecovery = new DisconnectRecovery(
            navigator, progressTracker, jobId, buildOriginX, buildOriginZ);
        currentRecovery.onStart();
        eventBus.publish(new RecoveryStartedEvent(RecoveryReason.DISCONNECT));
    }

    @Override
    public void onStuck(int stuckX, int stuckZ) {
        if (isRecovering()) return;
        recoveryAttempts++;
        if (recoveryAttempts > MAX_ATTEMPTS) {
            failActiveJob("Stuck recovery failed after " + MAX_ATTEMPTS + " attempts");
            return;
        }

        logger.warn(TAG, "Stuck detected at (%d,%d), attempt %d/%d",
            stuckX, stuckZ, recoveryAttempts, MAX_ATTEMPTS);

        currentRecovery = new StuckRecovery(navigator, stuckX, stuckZ);
        currentRecovery.onStart();
        eventBus.publish(new BotStuckEvent(stuckX, stuckZ, 0));
    }

    @Override
    public void onCombat(String threatName, double threatX, double threatZ) {
        logger.warn(TAG, "Combat detected: %s at (%.0f,%.0f) — not yet implemented",
            threatName, threatX, threatZ);
        eventBus.publish(new CombatDetectedEvent(threatName, threatX, threatZ, 0));
    }

    @Override
    public boolean isRecovering() {
        return currentRecovery != null;
    }

    @Override
    public RecoveryReason getRecoveryType() {
        return currentRecovery != null ? currentRecovery.getReason() : null;
    }

    @Override
    public void cancelRecovery() {
        if (currentRecovery != null) {
            currentRecovery.onFail();
            currentRecovery = null;
        }
        recoveryAttempts = 0;
    }

    @Override
    public void tick() {
        // Monitor for death
        if (!CACHE.getPlayerCache().isAlive()) {
            onDeath();
        }

        // Monitor for disconnect
        if (!Proxy.getInstance().isConnected()) {
            onDisconnect();
        }

        // Monitor for stuck (delegated to Navigator)
        if (navigator.isStuck()) {
            int[] target = navigator.getCurrentPathTarget();
            onStuck(target[0], target[1]);
        }

        // Advance active recovery
        if (currentRecovery != null) {
            try {
                boolean done = currentRecovery.tick();
                if (done) {
                    currentRecovery.onComplete();
                    eventBus.publish(new RecoveryCompletedEvent(currentRecovery.getReason()));
                    logger.info(TAG, "Recovery complete (%s)", currentRecovery.getReason());
                    currentRecovery = null;
                    recoveryAttempts = 0;
                }
            } catch (Exception e) {
                logger.error(TAG, "Recovery threw: " + e.getMessage(), e);
                currentRecovery.onFail();
                currentRecovery = null;
            }
        }
    }

    // ==================================================================
    // Internal
    // ==================================================================

    private void failActiveJob(String reason) {
        logger.error(TAG, reason, null);
        // Mark job as failed via JobManager
        jobManager.getAllJobs().stream()
            .filter(j -> j.getState() == org.loom.jobs.JobState.ACTIVE)
            .findFirst()
            .ifPresent(j -> jobManager.failJob(j.getId(), reason));
        cancelRecovery();
    }

    private String getActiveJobId() {
        var activeJob = jobManager.getActiveJob();
        return activeJob.map(org.loom.jobs.Job::getId).orElse("unknown");
    }
}
