package org.loom.recovery;

import org.loom.navigation.Navigator;
import org.loom.state.ProgressTracker;

import static com.zenith.Globals.CACHE;

/**
 * Handles death recovery: wait for auto-respawn via Zenith,
 * navigate back to the build area, and signal completion.
 */
public class DeathRecovery extends RecoveryAction {

    private enum Phase { WAIT_RESPAWN, NAVIGATE_BACK, DONE }

    private final Navigator navigator;
    private final ProgressTracker progressTracker;
    private final String jobId;
    private final int buildOriginX;
    private final int buildOriginZ;
    private Phase phase;

    public DeathRecovery(Navigator navigator, ProgressTracker progressTracker,
                          String jobId, int buildOriginX, int buildOriginZ) {
        super(RecoveryReason.DEATH);
        this.navigator = navigator;
        this.progressTracker = progressTracker;
        this.jobId = jobId;
        this.buildOriginX = buildOriginX;
        this.buildOriginZ = buildOriginZ;
    }

    @Override
    public void onStart() {
        progressTracker.save(jobId);
        phase = Phase.WAIT_RESPAWN;
    }

    @Override
    public boolean tick() {
        return switch (phase) {
            case WAIT_RESPAWN -> {
                if (CACHE.getPlayerCache().isAlive()) {
                    phase = Phase.NAVIGATE_BACK;
                }
                yield false;
            }
            case NAVIGATE_BACK -> {
                if (!navigator.isNavigating()) {
                    navigator.goTo(buildOriginX, buildOriginZ);
                }
                if (navigator.isNavigating()) {
                    yield false;
                }
                phase = Phase.DONE;
                yield true;
            }
            case DONE -> true;
        };
    }
}
