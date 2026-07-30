package org.loom.recovery;

/**
 * Default implementation of {@link RecoverySystem}.
 */
public class LoomRecoverySystem implements RecoverySystem {

    private final int maxRecoveryAttempts;
    private final int combatFleeDistance;
    private RecoveryAction currentRecovery;
    private int recoveryAttempts;

    public LoomRecoverySystem(int maxRecoveryAttempts, int combatFleeDistance) {
        this.maxRecoveryAttempts = maxRecoveryAttempts;
        this.combatFleeDistance = combatFleeDistance;
        this.currentRecovery = null;
        this.recoveryAttempts = 0;
    }

    @Override
    public void onDeath() {
        // TODO: Save progress
        // TODO: Start DeathRecovery
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void onDisconnect() {
        // TODO: Save progress
        // TODO: Start DisconnectRecovery
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void onStuck(int stuckX, int stuckZ) {
        // TODO: Cancel current navigation
        // TODO: Try re-pathing
        // TODO: If still stuck, start a stuck recovery (move back, then around)
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void onCombat(String threatName, double threatX, double threatZ) {
        // TODO: Start CombatRecovery with flee distance
        throw new UnsupportedOperationException("Not implemented yet");
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
        // TODO: Cancel current recovery action
        // TODO: Reset state
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void tick() {
        // TODO: Monitor CACHE.getPlayerCache().isAlive() → if dead, onDeath()
        // TODO: Monitor connection state → if disconnected, onDisconnect()
        // TODO: If recovery is active, call currentRecovery.tick()
        // TODO: If recovery complete, emit event and resume tasks
    }
}
