package org.loom.recovery;

/**
 * Handles combat recovery: flee from the threat, wait for cooldown,
 * then return to the build area.
 */
public class CombatRecovery extends RecoveryAction {

    private final int fleeDistance;

    public CombatRecovery(int fleeDistance) {
        super(RecoveryReason.COMBAT);
        this.fleeDistance = fleeDistance;
    }

    @Override
    public void onStart() {
        // TODO: Save progress immediately
        // TODO: Cancel current navigation
        // TODO: Emit RecoveryStartedEvent(COMBAT)
    }

    @Override
    public boolean tick() {
        // TODO: Phase 1: FLEE — path away from threat
        // TODO: Phase 2: WAIT — stay at safe distance for cooldown
        // TODO: Phase 3: RETURN — navigate back to build area
        // For now, immediately complete to avoid blocking other recovery
        return true;
    }

    @Override
    public void onComplete() {
        // TODO: Emit RecoveryCompletedEvent(COMBAT)
        // TODO: Signal TaskScheduler to resume PrintTask
    }
}
