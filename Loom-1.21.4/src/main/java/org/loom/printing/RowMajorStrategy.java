package org.loom.printing;

/**
 * Row-major traversal: left to right, then advance one row up.
 * Standard printer strategy for minimal backtracking.
 */
public class RowMajorStrategy implements PrintStrategy {

    @Override
    public int[] nextPosition(int currentX, int currentY, int maxX, int maxY) {
        // TODO: Implement row-major traversal
        // TODO: If currentX + 1 < maxX, return (currentX + 1, currentY)
        // TODO: Else if currentY + 1 < maxY, return (0, currentY + 1)
        // TODO: Else return null (complete)
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public String getName() {
        return "RowMajor";
    }
}
