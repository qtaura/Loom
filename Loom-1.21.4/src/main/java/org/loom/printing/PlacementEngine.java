package org.loom.printing;

import org.loom.util.Material;

/**
 * Low-level block placement engine.
 *
 * <p>Given a world position and a material, executes the full sequence of
 * actions needed to place that exact block: calculate face and angle,
 * select the correct hotbar slot, execute the placement action, and verify.
 *
 * <p>The engine is stateless between calls. Each call is self-contained.
 */
public interface PlacementEngine {

    /**
     * Places a solid block at the given world position.
     *
     * @param worldX   world X coordinate
     * @param worldY   world Y coordinate
     * @param worldZ   world Z coordinate
     * @param material the material to place
     * @return the result of the placement attempt
     */
    PlacementResult placeBlock(int worldX, int worldY, int worldZ, Material material);

    /**
     * Places a carpet at the given world position (on top of the supporting block).
     *
     * @param worldX   world X coordinate
     * @param worldY   world Y coordinate of the supporting block (carpet goes on Y+1)
     * @param worldZ   world Z coordinate
     * @param material the carpet material to place
     * @return the result of the placement attempt
     */
    PlacementResult placeCarpet(int worldX, int worldY, int worldZ, Material material);

    /**
     * Checks whether a block can be placed at a given position (not obstructed, in reach).
     *
     * @param worldX   world X
     * @param worldY   world Y
     * @param worldZ   world Z
     * @param material the material to check
     * @return true if placement is possible
     */
    boolean canPlaceAt(int worldX, int worldY, int worldZ, Material material);

    /**
     * Calculates the face to click for placing a block at the given position.
     *
     * @param worldX world X
     * @param worldY world Y
     * @param worldZ world Z
     * @return the direction of the face to click
     */
    String getPlacementFace(int worldX, int worldY, int worldZ);

    /**
     * Computes the yaw and pitch required to look at the target placement face.
     *
     * @param worldX world X
     * @param worldY world Y
     * @param worldZ world Z
     * @param face   the placement face direction
     * @return the required rotation
     */
    Rotation calculateLookAngle(int worldX, int worldY, int worldZ, String face);

    /**
     * Breaks the block at the given world position.
     *
     * <p>This is a stateful, multi-tick operation. Call once per tick until
     * the result is {@link BreakResult#SUCCESS}, {@link BreakResult#FAILED},
     * or {@link BreakResult#ALREADY_AIR}.
     *
     * <p>Uses Zenith's {@code BARITONE.breakBlock()} for the actual breaking
     * and {@code WorldScanner} for post-break verification.
     *
     * @param worldX world X
     * @param worldY world Y
     * @param worldZ world Z
     * @return the current state of the break operation
     */
    BreakResult breakBlock(int worldX, int worldY, int worldZ);
}
