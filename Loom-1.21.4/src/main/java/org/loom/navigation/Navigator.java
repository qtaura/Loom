package org.loom.navigation;

/**
 * Moves the bot within and around the build area.
 *
 * <p>A thin wrapper around ZenithProxy's BARITONE pathfinder with
 * build-area-specific logic: precise positioning on the placement grid,
 * stuck detection, and efficient intra-area movement.
 */
public interface Navigator {

    /**
     * Navigates to a specific world coordinate within the build area.
     *
     * @param x world X
     * @param z world Z
     * @return the navigation result
     */
    NavigationResult goTo(int x, int z);

    /**
     * Navigates to the configured storage area.
     *
     * @return the navigation result
     */
    NavigationResult goToStorage();

    /**
     * Navigates back to the build area origin.
     *
     * @return the navigation result
     */
    NavigationResult goToBuildArea();

    /**
     * Moves to the precise position needed to place a block at the given world
     * coordinates, accounting for the placement face.
     *
     * @param worldX block X to place at
     * @param worldY block Y to place at
     * @param worldZ block Z to place at
     * @param face   the placement face direction
     * @return the navigation result
     */
    NavigationResult moveToPlacementPosition(int worldX, int worldY, int worldZ, String face);

    /**
     * Returns true if the bot is currently navigating (BARITONE is active).
     */
    boolean isNavigating();

    /**
     * Returns true if the bot appears to be stuck (no progress despite active pathfinding).
     */
    boolean isStuck();

    /**
     * Cancels the current navigation.
     */
    void cancel();

    /**
     * Returns the current path target as (x, z), or (-1, -1) if idle.
     */
    int[] getCurrentPathTarget();

    /**
     * Returns the result of the last completed navigation.
     *
     * <p>Returns {@code null} if no navigation has been attempted since the
     * Navigator was created or since the last cancellation.
     */
    NavigationResult getLastNavigationResult();

    /**
     * Returns the last successfully reached destination as (x, z),
     * or (-1, -1) if no destination has been reached yet.
     */
    int[] getLastDestination();
}
