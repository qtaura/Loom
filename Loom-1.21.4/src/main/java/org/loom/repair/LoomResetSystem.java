package org.loom.repair;

import org.loom.jobs.Job;
import org.loom.log.LoomLogger;
import org.loom.printing.BreakResult;
import org.loom.printing.PlacementEngine;
import org.loom.scanning.ScanRegion;
import org.loom.scanning.WorldScanner;
import org.loom.state.ProgressTracker;

/**
 * Clears the build area by breaking all non-air blocks.
 *
 * <h3>Multi-tick state machine</h3>
 * <pre>
 *   IDLE → SCAN → BREAK → VERIFY_BREAK → ADVANCE
 *                                            │
 *                                     ┌──────┘
 *                                     │
 *                                ┌────▼────┐
 *                                │more?     │
 *                                └────┬────┘
 *                               yes   │   no
 *                                     │    │
 *                                     ▼    ▼
 *                                  BREAK  DONE
 * </pre>
 *
 * <p>Reuses PlacementEngine.breakBlock() and WorldScanner for block-level
 * operations. Unlike Nerv which relies on external redstone to clear the
 * area, Loom actively breaks every block. This works on any server without
 * requiring pre-built infrastructure.
 */
public class LoomResetSystem {

    private static final String TAG = "Reset";
    private static final int MAX_RETRIES = 3;

    private enum Phase {
        IDLE, SCAN, BREAK, VERIFY_BREAK, ADVANCE, DONE
    }

    private final WorldScanner worldScanner;
    private final PlacementEngine placementEngine;
    private final ProgressTracker progressTracker;
    private final LoomLogger logger;

    private Phase phase;
    private ResetPlan plan;
    private ResetEntry currentEntry;
    private int retryCount;
    private Job job;

    public LoomResetSystem(WorldScanner worldScanner,
                            PlacementEngine placementEngine,
                            ProgressTracker progressTracker,
                            LoomLogger logger) {
        this.worldScanner = worldScanner;
        this.placementEngine = placementEngine;
        this.progressTracker = progressTracker;
        this.logger = logger;
        this.phase = Phase.IDLE;
    }

    public boolean isActive() {
        return phase != Phase.IDLE && phase != Phase.DONE;
    }

    public void start(Job job) {
        this.job = job;
        this.phase = Phase.SCAN;
        this.plan = null;
        this.currentEntry = null;
        this.retryCount = 0;
        logger.info(TAG, "Starting area reset for job %s", job.getId());
    }

    public void cancel() {
        phase = Phase.IDLE;
        plan = null;
        currentEntry = null;
    }

    public void tick() {
        switch (phase) {
            case IDLE -> {}
            case SCAN -> tickScan();
            case BREAK -> tickBreak();
            case VERIFY_BREAK -> tickVerifyBreak();
            case ADVANCE -> tickAdvance();
            case DONE -> {}
        }
    }

    private void tickScan() {
        int width = 128;
        int height = 128;

        ScanRegion region = new ScanRegion(
            job.getOriginX(), job.getOriginY(),
            job.getOriginZ(),
            job.getOriginX() + width - 1, job.getOriginY(),
            job.getOriginZ() + height - 1);

        var scanResult = worldScanner.scanRegion(region);

        plan = new ResetPlan(job.getId());
        for (int x = job.getOriginX(); x < job.getOriginX() + width; x++) {
            for (int z = job.getOriginZ(); z < job.getOriginZ() + height; z++) {
                var state = worldScanner.getBlockAt(x, job.getOriginY(), z);
                if (!state.isAir()) {
                    plan.addEntry(new ResetEntry(x, job.getOriginY(), z));
                }
            }
        }

        logger.info(TAG, "Scan found %d blocks to clear for job %s", plan.size(), job.getId());

        if (plan.size() == 0) {
            logger.info(TAG, "Area already clear");
            phase = Phase.DONE;
        } else {
            phase = Phase.ADVANCE;
        }
    }

    private void tickBreak() {
        BreakResult result = placementEngine.breakBlock(
            currentEntry.getWorldX(),
            currentEntry.getWorldY(),
            currentEntry.getWorldZ());

        switch (result) {
            case SUCCESS, ALREADY_AIR -> {
                retryCount = 0;
                phase = Phase.VERIFY_BREAK;
            }
            case IN_PROGRESS -> {}
            case OUT_OF_REACH, FAILED -> {
                retryCount++;
                if (retryCount > MAX_RETRIES) {
                    logger.warn(TAG, "Break failed at (%d,%d,%d), skipping",
                        currentEntry.getWorldX(), currentEntry.getWorldY(),
                        currentEntry.getWorldZ());
                    retryCount = 0;
                    phase = Phase.ADVANCE;
                }
            }
        }
    }

    private void tickVerifyBreak() {
        var state = worldScanner.getBlockAt(
            currentEntry.getWorldX(),
            currentEntry.getWorldY(),
            currentEntry.getWorldZ());

        if (state.isAir()) {
            retryCount = 0;
            phase = Phase.ADVANCE;
        } else {
            retryCount++;
            if (retryCount > MAX_RETRIES) {
                logger.warn(TAG, "Break verification failed at (%d,%d,%d), skipping",
                    currentEntry.getWorldX(), currentEntry.getWorldY(),
                    currentEntry.getWorldZ());
                retryCount = 0;
                phase = Phase.ADVANCE;
            } else {
                phase = Phase.BREAK;
            }
        }
    }

    private void tickAdvance() {
        ResetEntry next = plan.next();
        if (next == null) {
            progressTracker.clear(job.getId());
            logger.info(TAG, "Area reset complete for job %s", job.getId());
            phase = Phase.DONE;
        } else {
            currentEntry = next;
            retryCount = 0;
            phase = Phase.BREAK;
        }
    }
}
