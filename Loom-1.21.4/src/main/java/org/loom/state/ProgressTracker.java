package org.loom.state;

import org.loom.printing.PrintStrategy;

/**
 * Tracks per-block placement progress so work can resume after any interruption.
 *
 * <p>Uses schematic-relative coordinates ({@code (x, y)} relative to the
 * schematic's bottom-left corner). The caller maps world → schematic using
 * the job's origin offset.
 *
 * <p>Progress is persisted via {@link ProgressStore} after every row completion.
 */
public interface ProgressTracker {

    /**
     * Marks a schematic-relative position as placed.
     *
     * @param x        schematic-relative X
     * @param y        schematic-relative Y
     * @param material the material that was placed
     */
    void markPlaced(int x, int y, String material);

    /**
     * Returns true if the position has already been placed.
     */
    boolean isPlaced(int x, int y);

    /**
     * Marks a position as having the wrong block (grief or error).
     * The printer will re-place this position on next pass.
     *
     * @param x schematic-relative X
     * @param y schematic-relative Y
     */
    void markWrong(int x, int y);

    /**
     * Returns the next unplaced position starting from the given coordinates,
     * using the specified traversal strategy.
     *
     * @param startX   starting schematic X
     * @param startY   starting schematic Y
     * @param strategy the traversal strategy
     * @return the next unplaced position as [x, y], or null if all placed
     */
    int[] getNextUnplaced(int startX, int startY, PrintStrategy strategy);

    /**
     * Returns the completion percentage (0.0–100.0).
     */
    double getPercentComplete();

    /**
     * Saves progress for a job to disk.
     *
     * @param jobId the job identifier
     */
    void save(String jobId);

    /**
     * Loads progress for a job from disk.
     *
     * @param jobId the job identifier
     */
    void load(String jobId);

    /**
     * Clears all progress for a job.
     *
     * @param jobId the job identifier
     */
    void clear(String jobId);

    /**
     * Returns the total number of blocks placed for the current job.
     */
    int getTotalPlaced();

    /**
     * Returns the total number of blocks in the schematic.
     */
    int getTotalBlocks();
}
