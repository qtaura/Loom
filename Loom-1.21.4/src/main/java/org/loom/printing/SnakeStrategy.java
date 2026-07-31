package org.loom.printing;

/**
 * Snake pattern traversal — reproduces Nerv Printer's column visitation
 * order for the equivalent of {@code linesPerRun = 1}.
 *
 * <h3>Nerv's model (linesPerRun = 1)</h3>
 * <pre>
 *   Column 0: N→S  (y=0→127)
 *   Column 1: S→N  (y=127→0)
 *   Column 2: N→S  (y=0→127)
 *   Column 3: S→N  (y=127→0)
 *   ...
 * </pre>
 *
 * <h3>Loom's equivalent</h3>
 * <pre>
 *   Even columns (0,2,4...): y increases (north→south)
 *   Odd columns (1,3,5...): y decreases (south→north)
 * </pre>
 *
 * <p>For {@code linesPerRun > 1}, Nerv skips columns (visiting 0, 3, 6...)
 * because the placement sweep covers multiple columns per pass. Loom places
 * one block per tick and always visits every column — this is a justified
 * architectural difference, not a behavioral mismatch. The {@code linesPerRun=1}
 * path is the correct equivalent for Loom's point-to-point model.
 *
 * <h3>Trace (10-column grid, maxY=128)</h3>
 * <pre>
 *   (-1,0) → [0,0]→[0,1]→...→[0,127]
 *                                → [1,127]→[1,126]→...→[1,0]
 *                                                     → [2,0]→[2,1]→...
 *                                                              → [3,127]→...
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
