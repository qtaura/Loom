package org.loom.recovery;

/**
 * Reasons that trigger a recovery action.
 */
public enum RecoveryReason {

    /** The bot has died. */
    DEATH,

    /** The connection to the server was lost. */
    DISCONNECT,

    /** The bot is stuck and cannot make progress. */
    STUCK,

    /** The bot is under attack by a player or mob. */
    COMBAT,

    /** The world state doesn't match the schematic (area may have been griefed). */
    WORLD_MISMATCH
}
