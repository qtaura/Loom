package org.loom.printing;

/**
 * Row-major traversal: left to right, then advance one row forward (positive Z).
 * Returns positions as [x, y] where y advances when x reaches max.
 */
public class RowMajorStrategy implements PrintStrategy {

    @Override
    public int[] nextPosition(int currentX, int currentY, int maxX, int maxY) {
        int nextX = currentX + 1;
        int nextY = currentY;

        if (nextX >= maxX) {
            nextX = 0;
            nextY = currentY + 1;
        }

        if (nextY >= maxY) {
            return null;
        }

        return new int[]{nextX, nextY};
    }

    @Override
    public String getName() {
        return "RowMajor";
    }
}
