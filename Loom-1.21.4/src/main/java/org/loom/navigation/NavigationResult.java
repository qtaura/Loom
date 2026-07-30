package org.loom.navigation;

/**
 * Result of a navigation attempt.
 */
public enum NavigationResult {

    /** Successfully arrived at the destination. */
    ARRIVED,

    /** Navigation was attempted but the bot got stuck. */
    STUCK,

    /** Pathfinder could not compute a valid path. */
    PATH_FAILED,

    /** Navigation was cancelled. */
    CANCELLED
}
