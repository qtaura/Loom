package org.loom.printing;

import org.loom.inventory.LoomInventoryManager;
import org.loom.jobs.Job;
import org.loom.log.LoomLogger;
import org.loom.navigation.NavigationResult;
import org.loom.navigation.Navigator;
import org.loom.scanning.WorldScanner;
import org.loom.schematic.SchematicManager;
import org.loom.state.ProgressTracker;
import org.loom.event.BlockPlacedEvent;
import org.loom.event.RowCompletedEvent;
import org.loom.util.AsyncLoomEventBus;

/**
 * Default implementation of {@link PrinterController}.
 *
 * <h3>Per-tick state machine</h3>
 * <pre>
 *   IDLE → SCAN → CHECK_MAT → MOVE → WAIT_MOVE → PLACE → VERIFY
 *                                                          ↓
 *   COMPLETED ←────────────────────────── ADVANCE ←── success
 *   PAUSED   ←── error/retries exhausted
 * </pre>
 *
 * <p>Each {@link #tick()} call advances exactly one phase.
 * Never blocks, never loops, never sends duplicate placement packets
 * in the same tick.
 */
public class LoomPrinterController implements PrinterController {

    private static final String TAG = "Printer";

    private enum Phase {
        IDLE,
        SCAN_TARGET,
        CHECK_MATERIAL,
        MOVE_TO_TARGET,
        WAIT_MOVE,
        EXECUTE_PLACE,
        VERIFY_PLACE,
        ADVANCE,
        PAUSED
    }

    private final Navigator navigator;
    private final WorldScanner worldScanner;
    private final PlacementEngine placementEngine;
    private final SchematicManager schematicManager;
    private final LoomInventoryManager inventoryManager;
    private final ProgressTracker progressTracker;
    private final LoomLogger logger;
    private final AsyncLoomEventBus eventBus;
    private final PrintStrategy strategy;
    private final int maxRetries;
    private final int placementDelayTicks;

    private Job currentJob;
    private int schematicX;
    private int schematicY;
    private int retryCount;
    private int delayCounter;
    private Phase phase;
    private boolean paused;

    public LoomPrinterController(Navigator navigator,
                                  WorldScanner worldScanner,
                                  PlacementEngine placementEngine,
                                  SchematicManager schematicManager,
                                  LoomInventoryManager inventoryManager,
                                  ProgressTracker progressTracker,
                                  LoomLogger logger,
                                  AsyncLoomEventBus eventBus,
                                  PrintStrategy strategy,
                                  int maxRetries,
                                  int placementDelayTicks) {
        this.navigator = navigator;
        this.worldScanner = worldScanner;
        this.placementEngine = placementEngine;
        this.schematicManager = schematicManager;
        this.inventoryManager = inventoryManager;
        this.progressTracker = progressTracker;
        this.logger = logger;
        this.eventBus = eventBus;
        this.strategy = strategy;
        this.maxRetries = maxRetries;
        this.placementDelayTicks = placementDelayTicks;
        this.phase = Phase.IDLE;
        this.schematicX = -1;
        this.schematicY = -1;
        this.retryCount = 0;
        this.delayCounter = 0;
        this.paused = false;
    }

    // ==================================================================
    // Lifecycle
    // ==================================================================

    @Override
    public void start(Job job) {
        this.currentJob = job;
        this.retryCount = 0;
        this.delayCounter = 0;
        this.paused = false;

        // Start at (-1, 0) so the first ADVANCE gives us (0, 0)
        this.schematicX = -1;
        this.schematicY = 0;
        this.phase = Phase.ADVANCE;

        int width = schematicManager.getWidth(job.getSchematicId());
        int height = schematicManager.getHeight(job.getSchematicId());
        int blocks = schematicManager.getTotalBlocks(job.getSchematicId());
        progressTracker.startJob(job.getId(), width, height, blocks);

        logger.info(TAG, "Started job %s: schematic=%s origin=(%d,%d,%d) size=%dx%d",
            job.getId(), job.getSchematicId(),
            job.getOriginX(), job.getOriginY(), job.getOriginZ(),
            width, height);
    }

    @Override
    public void pause() {
        if (phase == Phase.IDLE || phase == Phase.PAUSED) return;
        this.paused = true;
        if (currentJob != null) progressTracker.save(currentJob.getId());
        logger.info(TAG, "Paused at (%d, %d)", schematicX, schematicY);
    }

    @Override
    public void resume() {
        if (!paused) return;
        this.paused = false;
        this.phase = Phase.SCAN_TARGET;
        logger.info(TAG, "Resumed at (%d, %d)", schematicX, schematicY);
    }

    @Override
    public void cancel() {
        if (currentJob != null) progressTracker.save(currentJob.getId());
        this.currentJob = null;
        this.schematicX = -1;
        this.schematicY = -1;
        this.phase = Phase.IDLE;
        this.paused = false;
        this.retryCount = 0;
        logger.info(TAG, "Cancelled");
    }

    // ==================================================================
    // Tick
    // ==================================================================

    @Override
    public void tick() {
        if (phase == Phase.IDLE) return;

        if (paused && phase != Phase.PAUSED) {
            phase = Phase.PAUSED;
            return;
        }

        if (phase == Phase.PAUSED) return;

        switch (phase) {
            case SCAN_TARGET   -> tickScanTarget();
            case CHECK_MATERIAL -> tickCheckMaterial();
            case MOVE_TO_TARGET -> tickMoveToTarget();
            case WAIT_MOVE     -> tickWaitMove();
            case EXECUTE_PLACE -> tickExecutePlace();
            case VERIFY_PLACE  -> tickVerifyPlace();
            case ADVANCE       -> tickAdvance();
            default            -> {}
        }
    }

    // ==================================================================
    // Phase implementations
    // ==================================================================

    private void tickScanTarget() {
        int width = schematicManager.getWidth(currentJob.getSchematicId());
        int height = schematicManager.getHeight(currentJob.getSchematicId());

        if (schematicX < 0 || schematicX >= width || schematicY >= height) {
            phase = Phase.ADVANCE;
            return;
        }

        String expected = schematicManager.getBlockAt(
            currentJob.getSchematicId(), schematicX, schematicY);

        if ("minecraft:air".equals(expected)) {
            phase = Phase.ADVANCE;
            return;
        }

        // Skip already-placed positions from previous sessions
        if (progressTracker.isPlaced(schematicX, schematicY)) {
            logger.debug(TAG, "Already tracked as placed at (%d,%d), skipping", schematicX, schematicY);
            phase = Phase.ADVANCE;
            return;
        }

        int worldX = currentJob.getOriginX() + schematicX;
        int worldY = currentJob.getOriginY();
        int worldZ = currentJob.getOriginZ() + schematicY;

        // Check if correct block already exists
        var material = org.loom.util.Material.fromIdentifier(expected);
        if (material != null && worldScanner.verifyBlock(worldX, worldY, worldZ, expected)) {
            logger.debug(TAG, "Already placed at (%d,%d) → %s", schematicX, schematicY, expected);
            phase = Phase.ADVANCE;
            return;
        }

        if (worldScanner.isObstructed(worldX, worldY, worldZ)) {
            logger.warn(TAG, "Obstructed at (%d,%d), pausing", schematicX, schematicY);
            pause();
            return;
        }

        phase = Phase.CHECK_MATERIAL;
    }

    private void tickCheckMaterial() {
        String expected = schematicManager.getBlockAt(
            currentJob.getSchematicId(), schematicX, schematicY);
        var material = org.loom.util.Material.fromIdentifier(expected);

        if (material == null) {
            logger.warn(TAG, "Unknown material '%s' at (%d,%d), skipping", expected, schematicX, schematicY);
            phase = Phase.ADVANCE;
            return;
        }

        if (!inventoryManager.hasMaterial(material)) {
            logger.warn(TAG, "Missing %s at (%d,%d), pausing for restock", expected, schematicX, schematicY);
            pause();
            return;
        }

        phase = Phase.MOVE_TO_TARGET;
    }

    private void tickMoveToTarget() {
        int worldX = currentJob.getOriginX() + schematicX;
        int worldZ = currentJob.getOriginZ() + schematicY;

        var result = navigator.goTo(worldX, worldZ);

        if (result == NavigationResult.ARRIVED) {
            // Already there, skip to placement
            phase = Phase.EXECUTE_PLACE;
        } else if (result == NavigationResult.ACCEPTED) {
            phase = Phase.WAIT_MOVE;
        } else {
            logger.warn(TAG, "Navigation rejected for (%d,%d), retrying", schematicX, schematicY);
            // Stay in MOVE_TO_TARGET for next tick retry
        }
    }

    private void tickWaitMove() {
        if (navigator.isNavigating()) return;

        var result = navigator.getLastNavigationResult();
        if (result == NavigationResult.ARRIVED) {
            phase = Phase.EXECUTE_PLACE;
        } else if (navigator.isStuck()) {
            retryCount++;
            if (retryCount > maxRetries) {
                logger.warn(TAG, "Navigation stuck at (%d,%d) after %d retries, pausing",
                    schematicX, schematicY, retryCount);
                pause();
                return;
            }
            logger.debug(TAG, "Navigation stuck at (%d,%d), retry %d/%d",
                schematicX, schematicY, retryCount, maxRetries);
            navigator.cancel();
            phase = Phase.MOVE_TO_TARGET;
        } else {
            retryCount++;
            if (retryCount > maxRetries) {
                logger.warn(TAG, "Navigation failed at (%d,%d) after %d retries, pausing",
                    schematicX, schematicY, retryCount);
                pause();
                return;
            }
            logger.debug(TAG, "Navigation failed at (%d,%d), retry %d/%d",
                schematicX, schematicY, retryCount, maxRetries);
            phase = Phase.MOVE_TO_TARGET;
        }
    }

    private void tickExecutePlace() {
        // Respect placement delay
        if (delayCounter > 0) {
            delayCounter--;
            return;
        }

        int worldX = currentJob.getOriginX() + schematicX;
        int worldY = currentJob.getOriginY();
        int worldZ = currentJob.getOriginZ() + schematicY;

        String expected = schematicManager.getBlockAt(
            currentJob.getSchematicId(), schematicX, schematicY);
        var material = org.loom.util.Material.fromIdentifier(expected);

        if (material == null) {
            phase = Phase.ADVANCE;
            return;
        }

        PlacementResult result = material.isCarpet()
            ? placementEngine.placeCarpet(worldX, worldY - 1, worldZ, material)
            : placementEngine.placeBlock(worldX, worldY, worldZ, material);

        switch (result) {
            case SUCCESS -> {
                retryCount = 0;
                delayCounter = placementDelayTicks;
                phase = Phase.VERIFY_PLACE;
            }
            case NO_MATERIAL -> {
                logger.warn(TAG, "No material for %s at (%d,%d), pausing", expected, schematicX, schematicY);
                pause();
            }
            case OBSTRUCTED -> {
                logger.warn(TAG, "Obstructed at (%d,%d), pausing", schematicX, schematicY);
                pause();
            }
            case OUT_OF_REACH -> {
                logger.warn(TAG, "Out of reach at (%d,%d), navigating closer", schematicX, schematicY);
                phase = Phase.MOVE_TO_TARGET;
            }
            case FAILED_RETRIES_EXHAUSTED -> {
                retryCount++;
                if (retryCount > maxRetries) {
                    logger.warn(TAG, "Placement failed at (%d,%d) after %d retries, pausing",
                        schematicX, schematicY, retryCount);
                    pause();
                } else {
                    logger.debug(TAG, "Placement failed at (%d,%d), retry %d/%d",
                        schematicX, schematicY, retryCount, maxRetries);
                    phase = Phase.EXECUTE_PLACE;
                }
            }
        }
    }

    private void tickVerifyPlace() {
        int worldX = currentJob.getOriginX() + schematicX;
        int worldY = currentJob.getOriginY();
        int worldZ = currentJob.getOriginZ() + schematicY;

        String expected = schematicManager.getBlockAt(
            currentJob.getSchematicId(), schematicX, schematicY);

        if (worldScanner.verifyBlock(worldX, worldY, worldZ, expected)) {
            progressTracker.markPlaced(schematicX, schematicY, expected);
            eventBus.publish(new BlockPlacedEvent(worldX, worldY, worldZ, expected));
            logger.debug(TAG, "Verified %s at (%d,%d) → world (%d,%d,%d)",
                expected, schematicX, schematicY, worldX, worldY, worldZ);
            retryCount = 0;
            phase = Phase.ADVANCE;
        } else {
            retryCount++;
            if (retryCount > maxRetries) {
                logger.warn(TAG, "Verification failed at (%d,%d) after %d retries, pausing",
                    schematicX, schematicY, retryCount);
                pause();
            } else {
                logger.debug(TAG, "Verification failed at (%d,%d), retry %d/%d",
                    schematicX, schematicY, retryCount, maxRetries);
                phase = Phase.EXECUTE_PLACE;
            }
        }
    }

    private void tickAdvance() {
        int width = schematicManager.getWidth(currentJob.getSchematicId());
        int height = schematicManager.getHeight(currentJob.getSchematicId());

        int[] next = strategy.nextPosition(schematicX, schematicY, width, height);
        if (next == null) {
            progressTracker.save(currentJob.getId());
            logger.info(TAG, "Job %s completed, %d/%d blocks (%.1f%%)",
                currentJob.getId(), progressTracker.getTotalPlaced(),
                progressTracker.getTotalBlocks(), progressTracker.getPercentComplete());
            phase = Phase.IDLE;
            currentJob = null;
            return;
        }

        // Save progress on row completion (when y changes)
        if (next[1] != schematicY) {
            progressTracker.save(currentJob.getId());
            eventBus.publish(new RowCompletedEvent(schematicY));
        }

        schematicX = next[0];
        schematicY = next[1];
        retryCount = 0;
        phase = Phase.SCAN_TARGET;
    }

    // ==================================================================
    // Query
    // ==================================================================

    @Override
    public int[] getCurrentPosition() {
        return new int[]{schematicX, schematicY};
    }

    @Override
    public int getRow() {
        return schematicY;
    }

    @Override
    public int getColumn() {
        return schematicX;
    }

    @Override
    public boolean isPrinting() {
        return phase != Phase.IDLE && !paused;
    }
}
