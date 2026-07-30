package org.loom.state;

import org.loom.printing.PrintStrategy;

import java.util.HashSet;
import java.util.Set;

/**
 * Default implementation of {@link ProgressTracker}.
 *
 * <p>Uses an in-memory {@link Set} of placed positions for fast lookup
 * and delegates persistence to {@link ProgressStore}.
 */
public class LoomProgressTracker implements ProgressTracker {

    private final ProgressStore store;
    private final Set<ProgressEntry> placedPositions;
    private final Set<ProgressEntry> wrongPositions;
    private int totalBlocks;
    private String currentJobId;

    public LoomProgressTracker(ProgressStore store) {
        this.store = store;
        this.placedPositions = new HashSet<>();
        this.wrongPositions = new HashSet<>();
        this.totalBlocks = 0;
        this.currentJobId = null;
    }

    @Override
    public void markPlaced(int x, int y, String material) {
        // TODO: Add to placedPositions set
        // TODO: Remove from wrongPositions if present
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public boolean isPlaced(int x, int y) {
        // TODO: Check placedPositions set for entry with matching (x, y)
        return false;
    }

    @Override
    public void markWrong(int x, int y) {
        // TODO: Add to wrongPositions set
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public int[] getNextUnplaced(int startX, int startY, PrintStrategy strategy) {
        // TODO: Iterate from (startX, startY) using strategy pattern
        // TODO: Skip already-placed positions
        // TODO: Return first unplaced position or null
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public double getPercentComplete() {
        return totalBlocks > 0 ? (double) placedPositions.size() / totalBlocks * 100.0 : 0.0;
    }

    @Override
    public void save(String jobId) {
        // TODO: Delegate to store.save(jobId, placedPositions as list)
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void load(String jobId) {
        // TODO: Set currentJobId
        // TODO: Load entries from store.load(jobId)
        // TODO: Populate placedPositions set
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void clear(String jobId) {
        // TODO: Clear placedPositions and wrongPositions
        // TODO: Delete progress file via store.delete(jobId)
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public int getTotalPlaced() {
        return placedPositions.size();
    }

    @Override
    public int getTotalBlocks() {
        return totalBlocks;
    }
}
