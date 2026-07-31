package org.loom.repair;

import org.loom.jobs.Job;
import org.loom.log.LoomLogger;
import org.loom.printing.BreakResult;
import org.loom.printing.PlacementEngine;
import org.loom.printing.PlacementResult;
import org.loom.scanning.Discrepancy;
import org.loom.scanning.ScanRegion;
import org.loom.scanning.ScanResult;
import org.loom.scanning.WorldScanner;
import org.loom.schematic.SchematicManager;
import org.loom.state.ProgressTracker;
import org.loom.util.Material;

import java.util.List;

/**
 * Detects and repairs incorrect blocks in the build area.
 *
 * <h3>Multi-tick state machine</h3>
 * <pre>
 *   IDLE → SCAN → BREAK → VERIFY_BREAK → PLACE → VERIFY_PLACE → ADVANCE
 *                                                                     │
 *                                          ┌──────────────────────────┘
 *                                          │
 *                                     ┌────▼────┐
 *                                     │more errs?│
 *                                     └────┬────┘
 *                                    yes   │   no
 *                                          │    │
 *                                          ▼    ▼
 *                                       BREAK  DONE
 * </pre>
 *
 * <p>Each phase transitions one step per tick. Never blocks.
 */
public class LoomErrorRepair {

    private static final String TAG = "Repair";
    private static final int MAX_RETRIES = 3;

    private enum Phase {
        IDLE,
        SCAN,
        BREAK,
        VERIFY_BREAK,
        PLACE,
        VERIFY_PLACE,
        ADVANCE,
        DONE
    }

    private final WorldScanner worldScanner;
    private final SchematicManager schematicManager;
    private final PlacementEngine placementEngine;
    private final ProgressTracker progressTracker;
    private final LoomLogger logger;

    private Phase phase;
    private RepairPlan plan;
    private RepairEntry currentEntry;
    private int retryCount;
    private Job job;

    public LoomErrorRepair(WorldScanner worldScanner,
                            SchematicManager schematicManager,
                            PlacementEngine placementEngine,
                            ProgressTracker progressTracker,
                            LoomLogger logger) {
        this.worldScanner = worldScanner;
        this.schematicManager = schematicManager;
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
        logger.info(TAG, "Starting error scan for job %s", job.getId());
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
            case PLACE -> tickPlace();
            case VERIFY_PLACE -> tickVerifyPlace();
            case ADVANCE -> tickAdvance();
            case DONE -> {}
        }
    }

    // ==================================================================
    // Phase implementations
    // ==================================================================

    private void tickScan() {
        int width = schematicManager.getWidth(job.getSchematicId());
        int height = schematicManager.getHeight(job.getSchematicId());

        ScanRegion region = new ScanRegion(
            job.getOriginX(), job.getOriginY(),
            job.getOriginZ(),
            job.getOriginX() + width - 1, job.getOriginY(),
            job.getOriginZ() + height - 1);

        ScanResult scanResult = worldScanner.scanRegion(region);
        List<Discrepancy> discrepancies = worldScanner.compareToSchematic(
            job.getSchematicId(), scanResult,
            job.getOriginX(), job.getOriginY(), job.getOriginZ());

        plan = new RepairPlan(job.getId());
        for (Discrepancy d : discrepancies) {
            int sx = d.getWorldX() - job.getOriginX();
            int sy = d.getWorldZ() - job.getOriginZ();
            plan.addEntry(new RepairEntry(
                d.getWorldX(), d.getWorldY(), d.getWorldZ(),
                sx, sy, d.getExpected()));
        }

        logger.info(TAG, "Scan found %d errors for job %s", plan.size(), job.getId());

        if (plan.size() == 0) {
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
                progressTracker.markWrong(
                    currentEntry.getSchematicX(),
                    currentEntry.getSchematicY());
                retryCount = 0;
                phase = Phase.VERIFY_BREAK;
            }
            case IN_PROGRESS -> {} // wait
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

        if (state.isAir() || state.isReplaceable()) {
            retryCount = 0;
            phase = Phase.PLACE;
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

    private void tickPlace() {
        Material material = Material.fromIdentifier(
            currentEntry.getExpectedMaterial());
        if (material == null) {
            phase = Phase.ADVANCE;
            return;
        }

        PlacementResult result = material.isCarpet()
            ? placementEngine.placeCarpet(
                currentEntry.getWorldX(),
                currentEntry.getWorldY() - 1,
                currentEntry.getWorldZ(), material)
            : placementEngine.placeBlock(
                currentEntry.getWorldX(),
                currentEntry.getWorldY(),
                currentEntry.getWorldZ(), material);

        switch (result) {
            case SUCCESS -> {
                retryCount = 0;
                phase = Phase.VERIFY_PLACE;
            }
            case NO_MATERIAL -> {
                logger.warn(TAG, "No material for repair at (%d,%d,%d), skipping",
                    currentEntry.getWorldX(), currentEntry.getWorldY(),
                    currentEntry.getWorldZ());
                phase = Phase.ADVANCE;
            }
            case OUT_OF_REACH, OBSTRUCTED, FAILED_RETRIES_EXHAUSTED -> {
                retryCount++;
                if (retryCount > MAX_RETRIES) {
                    logger.warn(TAG, "Place failed at (%d,%d,%d), skipping",
                        currentEntry.getWorldX(), currentEntry.getWorldY(),
                        currentEntry.getWorldZ());
                    retryCount = 0;
                    phase = Phase.ADVANCE;
                }
            }
        }
    }

    private void tickVerifyPlace() {
        if (worldScanner.verifyBlock(
            currentEntry.getWorldX(),
            currentEntry.getWorldY(),
            currentEntry.getWorldZ(),
            currentEntry.getExpectedMaterial())) {

            progressTracker.markPlaced(
                currentEntry.getSchematicX(),
                currentEntry.getSchematicY(),
                currentEntry.getExpectedMaterial());
            retryCount = 0;
            phase = Phase.ADVANCE;
        } else {
            retryCount++;
            if (retryCount > MAX_RETRIES) {
                logger.warn(TAG, "Place verification failed at (%d,%d,%d), skipping",
                    currentEntry.getWorldX(), currentEntry.getWorldY(),
                    currentEntry.getWorldZ());
                retryCount = 0;
                phase = Phase.ADVANCE;
            } else {
                phase = Phase.PLACE;
            }
        }
    }

    private void tickAdvance() {
        RepairEntry next = plan.next();
        if (next == null) {
            logger.info(TAG, "Repair complete for job %s", job.getId());
            phase = Phase.DONE;
        } else {
            currentEntry = next;
            retryCount = 0;
            phase = Phase.BREAK;
        }
    }
}
