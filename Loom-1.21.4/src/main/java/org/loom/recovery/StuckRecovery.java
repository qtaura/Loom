package org.loom.recovery;

import org.loom.navigation.Navigator;

/**
 * Handles stuck recovery: cancel current navigation, move to a nearby
 * position, and re-path.
 */
public class StuckRecovery extends RecoveryAction {

    private enum Phase { CANCEL_NAV, BACK_OFF, RE_PATH, DONE }

    private final Navigator navigator;
    private final int stuckX;
    private final int stuckZ;
    private Phase phase;

    public StuckRecovery(Navigator navigator, int stuckX, int stuckZ) {
        super(RecoveryReason.STUCK);
        this.navigator = navigator;
        this.stuckX = stuckX;
        this.stuckZ = stuckZ;
    }

    @Override
    public void onStart() {
        navigator.cancel();
        phase = Phase.CANCEL_NAV;
    }

    @Override
    public boolean tick() {
        return switch (phase) {
            case CANCEL_NAV -> {
                if (navigator.isNavigating()) {
                    yield false;
                }
                phase = Phase.BACK_OFF;
                yield false;
            }
            case BACK_OFF -> {
                // Move a few blocks away from the stuck position
                if (!navigator.isNavigating()) {
                    navigator.goTo(stuckX + 5, stuckZ + 5);
                }
                if (navigator.isNavigating()) {
                    yield false;
                }
                phase = Phase.DONE;
                yield true;
            }
            case RE_PATH -> true;
            case DONE -> true;
        };
    }
}
