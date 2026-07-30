package org.loom.printing;

/**
 * Column-major traversal: bottom to top within a column, then advance one column right.
 */
public class ColumnMajorStrategy implements PrintStrategy {

    @Override
    public int[] nextPosition(int currentX, int currentY, int maxX, int maxY) {
        // TODO: Implement column-major traversal
        // TODO: If currentY + 1 < maxY, return (currentX, currentY + 1)
        // TODO: Else if currentX + 1 < maxX, return (currentX + 1, 0)
        // TODO: Else return null (complete)
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public String getName() {
        return "ColumnMajor";
    }
}
