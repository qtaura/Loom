package org.loom.navigation;

/**
 * Result of a navigation attempt or request.
 */
public enum NavigationResult {

    /** Successfully arrived at the destination. */
    ARRIVED,

    /** Navigation was attempted but the bot got stuck. */
    STUCK,

    /** Pathfinder could not compute a valid path. */
    PATH_FAILED,

    /** Navigation was cancelled by the caller. */
    CANCELLED,

    /** The navigation request was accepted and navigation has begun. */
    ACCEPTED,

    /** The navigation request was rejected because a conflicting operation is in progress. */
    REJECTED
}
