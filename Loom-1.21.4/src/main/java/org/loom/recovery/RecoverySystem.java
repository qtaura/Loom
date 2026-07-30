package org.loom.recovery;

/**
 * Monitors the bot for failure conditions and executes recovery actions.
 *
 * <p>The recovery system observes (watches for death, disconnect, stuck, combat)
 * but does not participate in normal printing. It intervenes externally when
 * a failure condition is detected.
 *
 * <p>Only one recovery can be active at a time.
 */
public interface RecoverySystem {

    /**
     * Called when the bot dies.
     */
    void onDeath();

    /**
     * Called when the bot disconnects from the server.
     */
    void onDisconnect();

    /**
     * Called when the navigator reports the bot is stuck.
     *
     * @param stuckX the X position where stuck was detected
     * @param stuckZ the Z position where stuck was detected
     */
    void onStuck(int stuckX, int stuckZ);

    /**
     * Called when combat is detected (another player or hostile mob nearby).
     *
     * @param threatName    name of the threat entity
     * @param threatX       X position of the threat
     * @param threatZ       Z position of the threat
     */
    void onCombat(String threatName, double threatX, double threatZ);

    /**
     * Returns true if a recovery is currently in progress.
     */
    boolean isRecovering();

    /**
     * Returns the type of the current recovery, or empty if not recovering.
     */
    RecoveryReason getRecoveryType();

    /**
     * Cancels the current recovery (if any) and returns to idle.
     */
    void cancelRecovery();

    /**
     * Advances the recovery system by one tick.
     * Called every bot tick by the module handler.
     */
    void tick();
}
