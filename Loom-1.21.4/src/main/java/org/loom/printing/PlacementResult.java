package org.loom.printing;

/**
 * Result of a single block placement attempt by the {@link PlacementEngine}.
 */
public enum PlacementResult {

    /** Block was confirmed placed. */
    SUCCESS,

    /** All retry attempts were exhausted and the block was not placed. */
    FAILED_RETRIES_EXHAUSTED,

    /** Something (entity, fluid, wrong block) is obstructing the position. */
    OBSTRUCTED,

    /** The required material is not available in inventory. */
    NO_MATERIAL,

    /** The placement position is out of the bot's reach. */
    OUT_OF_REACH
}
