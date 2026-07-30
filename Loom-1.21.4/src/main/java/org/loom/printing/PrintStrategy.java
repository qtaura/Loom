package org.loom.printing;

import org.loom.util.Material;

/**
 * Defines the traversal order for the {@link PrinterController} through
 * the build area.
 *
 * <p>Different strategies optimize for different goals: minimal backtracking,
 * minimal inventory swaps, or layer-by-layer construction.
 */
public interface PrintStrategy {

    /**
     * Returns the next position to place, starting from the given position
     * and moving according to this strategy's rules.
     *
     * @param currentX current schematic-relative X
     * @param currentY current schematic-relative Y
     * @param maxX     maximum X (exclusive) — typically schematic width
     * @param maxY     maximum Y (exclusive) — typically schematic height
     * @return the next position, or {@code null} if all positions have been visited
     */
    int[] nextPosition(int currentX, int currentY, int maxX, int maxY);

    /**
     * Returns a human-readable name for this strategy.
     */
    String getName();
}
