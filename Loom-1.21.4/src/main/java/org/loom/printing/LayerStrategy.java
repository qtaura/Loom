package org.loom.printing;

/**
 * Layer-first traversal: complete one Y-level entirely before moving to the next.
 * Useful for multi-layer builds where the base must be completed first.
 */
public class LayerStrategy implements PrintStrategy {

    @Override
    public int[] nextPosition(int currentX, int currentY, int maxX, int maxY) {
        // TODO: Implement layer-first traversal
        // TODO: Complete entire current layer before incrementing Z (height)
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public String getName() {
        return "LayerFirst";
    }
}
