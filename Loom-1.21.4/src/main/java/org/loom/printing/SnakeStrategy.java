package org.loom.printing;

/**
 * Snake pattern traversal matching Nerv Printer's behavior.
 *
 * <p>Columns are traversed left-to-right. Within each column, the
 * direction alternates:
 * <ul>
 *   <li>Even columns (0, 2, 4...): top→bottom (y increases)</li>
 *   <li>Odd columns (1, 3, 5...): bottom→top (y decreases)</li>
 * </ul>
 *
 * <p>This eliminates the backtracking of row-major traversal.
 * After finishing a column, you are already at the start of the
 * next column — no need to walk back.
 *
 * <pre>
 * Visual (4×3 grid):
 *   Row-major:        Snake:
 *   (0,0)→(1,0)      (0,0)→(0,1)→(0,2)
 *   →down             →right
 *   (1,0)→(0,0)      (1,2)→(1,1)→(1,0)
 *   →down             →right
 *   (0,1)→(1,1)      (2,0)→(2,1)→(2,2)
 *   →down             →right
 *   (1,1)→(0,1)      (3,2)→(3,1)→(3,0)
 * </pre>
 */
public class SnakeStrategy implements PrintStrategy {

    @Override
    public int[] nextPosition(int currentX, int currentY, int maxX, int maxY) {
        // Start: first call has currentX = -1
        if (currentX < 0) {
            return new int[]{0, 0};
        }

        boolean evenColumn = (currentX % 2) == 0;

        if (evenColumn) {
            // Moving south (y increases)
            if (currentY + 1 < maxY) {
                return new int[]{currentX, currentY + 1};
            }
            // End of column — move to next column, start at bottom
            if (currentX + 1 < maxX) {
                return new int[]{currentX + 1, maxY - 1};
            }
            // End of grid
            return null;
        } else {
            // Moving north (y decreases)
            if (currentY - 1 >= 0) {
                return new int[]{currentX, currentY - 1};
            }
            // End of column — move to next column, start at top
            if (currentX + 1 < maxX) {
                return new int[]{currentX + 1, 0};
            }
            // End of grid
            return null;
        }
    }

    @Override
    public String getName() {
        return "Snake";
    }
}
