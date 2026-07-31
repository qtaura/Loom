package org.loom.printing;

/**
 * Result of a block-breaking attempt by the {@link PlacementEngine}.
 */
public enum BreakResult {

    /** Block was confirmed broken (now air). */
    SUCCESS,

    /** Breaking failed and cannot continue. */
    FAILED,

    /** The target position is out of the bot's reach. */
    OUT_OF_REACH,

    /** Breaking has been initiated and is in progress. */
    IN_PROGRESS,

    /** The target position is already air. */
    ALREADY_AIR
}
