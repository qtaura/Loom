package org.loom.state;

import org.loom.event.StateLoadedEvent;
import org.loom.printing.PrintStrategy;
import org.loom.util.AsyncLoomEventBus;

import java.util.ArrayList;
import java.util.List;

/**
 * Default implementation of {@link ProgressTracker}.
 *
 * <p>Uses a {@code boolean[][]} grid for O(1) {@link #isPlaced} lookup
 * and delegates persistence to {@link ProgressStore}.
 *
 * <p>Grid dimensions are initialized via {@link #startJob} which must be
 * called before any placement tracking begins.
 */
public class LoomProgressTracker implements ProgressTracker {

    private final ProgressStore store;
    private final AsyncLoomEventBus eventBus;

    private String jobId;
    private boolean[][] placed;
    private int width;
    private int height;
    private int totalBlocks;
    private int totalPlaced;

    public LoomProgressTracker(ProgressStore store, AsyncLoomEventBus eventBus) {
        this.store = store;
        this.eventBus = eventBus;
        this.jobId = null;
        this.width = 0;
        this.height = 0;
        this.totalBlocks = 0;
        this.totalPlaced = 0;
        this.placed = new boolean[0][0];
    }

    // ==================================================================
    // Lifecycle
    // ==================================================================

    @Override
    public void startJob(String jobId, int width, int height, int totalBlocks) {
        this.jobId = jobId;
        this.width = width;
        this.height = height;
        this.totalBlocks = totalBlocks;
        this.totalPlaced = 0;
        this.placed = new boolean[height][width];

        // Try to resume from disk
        ProgressStore.ProgressFileData data = store.load(jobId);
        if (data != null) {
            // Only restore if dimensions match
            if (data.width() == width && data.height() == height) {
                for (ProgressEntry entry : data.entries()) {
                    int x = entry.getX();
                    int y = entry.getY();
                    if (x >= 0 && x < width && y >= 0 && y < height && !placed[y][x]) {
                        placed[y][x] = true;
                        totalPlaced++;
                    }
                }
                this.totalBlocks = data.totalBlocks();
                eventBus.publish(new StateLoadedEvent(jobId, totalPlaced, totalBlocks));
            }
        }
    }

    // ==================================================================
    // Tracking
    // ==================================================================

    @Override
    public void markPlaced(int x, int y, String material) {
        if (x < 0 || x >= width || y < 0 || y >= height) return;
        if (!placed[y][x]) {
            placed[y][x] = true;
            totalPlaced++;
        }
    }

    @Override
    public boolean isPlaced(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) return false;
        return placed[y][x];
    }

    @Override
    public void markWrong(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) return;
        if (placed[y][x]) {
            placed[y][x] = false;
            totalPlaced--;
        }
    }

    @Override
    public int[] getNextUnplaced(int startX, int startY, PrintStrategy strategy) {
        int cx = startX;
        int cy = startY;

        int maxIterations = width * height;
        for (int i = 0; i < maxIterations; i++) {
            int[] next = strategy.nextPosition(cx, cy, width, height);
            if (next == null) return null;

            cx = next[0];
            cy = next[1];

            if (!placed[cy][cx]) {
                return new int[]{cx, cy};
            }
        }

        return null;
    }

    // ==================================================================
    // Persistence
    // ==================================================================

    @Override
    public void save(String jobId) {
        List<ProgressEntry> entries = new ArrayList<>();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (placed[y][x]) {
                    entries.add(new ProgressEntry(x, y, ""));
                }
            }
        }
        store.save(jobId, width, height, totalBlocks, entries);
    }

    @Override
    public void load(String jobId) {
        ProgressStore.ProgressFileData data = store.load(jobId);
        if (data == null) return;

        this.jobId = jobId;
        this.width = data.width();
        this.height = data.height();
        this.totalBlocks = data.totalBlocks();
        this.totalPlaced = 0;
        this.placed = new boolean[height][width];

        for (ProgressEntry entry : data.entries()) {
            int x = entry.getX();
            int y = entry.getY();
            if (x >= 0 && x < width && y >= 0 && y < height && !placed[y][x]) {
                placed[y][x] = true;
                totalPlaced++;
            }
        }
    }

    @Override
    public void clear(String jobId) {
        store.delete(jobId);
        if (jobId.equals(this.jobId)) {
            this.placed = new boolean[height][width];
            this.totalPlaced = 0;
        }
    }

    // ==================================================================
    // Query
    // ==================================================================

    @Override
    public double getPercentComplete() {
        return totalBlocks > 0 ? (double) totalPlaced / totalBlocks * 100.0 : 0.0;
    }

    @Override
    public int getTotalPlaced() {
        return totalPlaced;
    }

    @Override
    public int getTotalBlocks() {
        return totalBlocks;
    }
}
