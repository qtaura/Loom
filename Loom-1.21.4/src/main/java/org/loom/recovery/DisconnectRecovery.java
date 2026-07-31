package org.loom.recovery;

import com.zenith.Proxy;
import org.loom.navigation.Navigator;
import org.loom.state.ProgressTracker;

/**
 * Handles disconnect recovery: wait for Zenith to auto-reconnect,
 * then navigate back to the build area.
 */
public class DisconnectRecovery extends RecoveryAction {

    private enum Phase { WAIT_RECONNECT, NAVIGATE_BACK, DONE }

    private final Navigator navigator;
    private final ProgressTracker progressTracker;
    private final String jobId;
    private final int buildOriginX;
    private final int buildOriginZ;
    private Phase phase;

    public DisconnectRecovery(Navigator navigator, ProgressTracker progressTracker,
                               String jobId, int buildOriginX, int buildOriginZ) {
        super(RecoveryReason.DISCONNECT);
        this.navigator = navigator;
        this.progressTracker = progressTracker;
        this.jobId = jobId;
        this.buildOriginX = buildOriginX;
        this.buildOriginZ = buildOriginZ;
    }

    @Override
    public void onStart() {
        progressTracker.save(jobId);
        phase = Phase.WAIT_RECONNECT;
    }

    @Override
    public boolean tick() {
        return switch (phase) {
            case WAIT_RECONNECT -> {
                if (Proxy.getInstance().isConnected()) {
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
