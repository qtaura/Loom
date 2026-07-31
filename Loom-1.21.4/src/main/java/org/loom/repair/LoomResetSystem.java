package org.loom.repair;

import com.zenith.Proxy;
import org.loom.jobs.Job;
import org.loom.log.LoomLogger;
import org.loom.navigation.NavigationResult;
import org.loom.navigation.Navigator;
import org.loom.scanning.WorldScanner;

/**
 * Nerv-compatible reset system.
 *
 * <p>Reproduces Nerv Printer's trapped-chest reset workflow:
 * <ol>
 *   <li>Navigate to the reset trapped chest position</li>
 *   <li>Interact with (open) the trapped chest</li>
 *   <li>Wait a configurable number of ticks</li>
 *   <li>Close the chest screen</li>
 *   <li>Navigate to the map center</li>
 *   <li>Poll the build area until all 128×128 positions are air</li>
 * </ol>
 *
 * <p>This relies on an external redstone mechanism triggered by the
 * trapped chest to physically clear the area. Loom only waits for
 * the area to become clear — it does not actively break blocks.
 */
public class LoomResetSystem {

    private static final String TAG = "Reset";
    private static final int INTERACT_TIMEOUT_TICKS = 80;
    private static final int CHEST_CLOSE_DELAY_TICKS = 10;

    private enum Phase {
        IDLE,
        NAV_TO_CHEST,
        INTERACT_CHEST,
        WAIT_CHEST_RESPONSE,
        WAIT_CLOSE_DELAY,
        NAV_TO_CENTER,
        WAIT_AREA_CLEAR,
        DONE
    }

    private final Navigator navigator;
    private final WorldScanner worldScanner;
    private final LoomLogger logger;

    private Phase phase;
    private Job job;
    private int chestCloseTicks;
    private int interactTimeout;
    private int resetChestX;
    private int resetChestY;
    private int resetChestZ;

    public LoomResetSystem(Navigator navigator,
                            WorldScanner worldScanner,
                            LoomLogger logger) {
        this.navigator = navigator;
        this.worldScanner = worldScanner;
        this.logger = logger;
        this.phase = Phase.IDLE;
    }

    public boolean isActive() {
        return phase != Phase.IDLE && phase != Phase.DONE;
    }

    /**
     * Starts the reset workflow.
     *
     * @param job          the job whose build area to reset
     * @param chestX       world X of the trapped chest
     * @param chestY       world Y of the trapped chest
     * @param chestZ       world Z of the trapped chest
     */
    public void start(Job job, int chestX, int chestY, int chestZ) {
        this.job = job;
        this.resetChestX = chestX;
        this.resetChestY = chestY;
        this.resetChestZ = chestZ;
        this.phase = Phase.NAV_TO_CHEST;
        this.chestCloseTicks = 0;
        this.interactTimeout = 0;
        logger.info(TAG, "Starting trapped-chest reset for job %s at chest (%d,%d,%d)",
            job.getId(), chestX, chestY, chestZ);
    }

    public void cancel() {
        phase = Phase.IDLE;
        navigator.cancel();
    }

    public void tick() {
        switch (phase) {
            case IDLE -> {}
            case NAV_TO_CHEST -> tickNavToChest();
            case INTERACT_CHEST -> tickInteractChest();
            case WAIT_CHEST_RESPONSE -> tickWaitChestResponse();
            case WAIT_CLOSE_DELAY -> tickWaitCloseDelay();
            case NAV_TO_CENTER -> tickNavToCenter();
            case WAIT_AREA_CLEAR -> tickWaitAreaClear();
            case DONE -> {}
        }
    }

    // ==================================================================
    // Phase implementations
    // ==================================================================

    private void tickNavToChest() {
        NavigationResult result = navigator.goTo(resetChestX, resetChestZ);
        if (result == NavigationResult.ARRIVED) {
            phase = Phase.INTERACT_CHEST;
        } else if (result != NavigationResult.ACCEPTED) {
            // Navigation rejected, retry next tick
        }
    }

    private void tickInteractChest() {
        // Match Nerv: use BARITONE's chest interaction to open the chest
        navigator.openChest(resetChestX, resetChestY, resetChestZ);
        interactTimeout = INTERACT_TIMEOUT_TICKS;

        // Check if chest opened
        int containerId = com.zenith.Globals.CACHE.getPlayerCache()
            .getInventoryCache().getOpenContainerId();
        if (containerId != 0) {
            logger.debug(TAG, "Chest opened (container %d)", containerId);
            chestCloseTicks = CHEST_CLOSE_DELAY_TICKS;
            phase = Phase.WAIT_CLOSE_DELAY;
        } else {
            phase = Phase.WAIT_CHEST_RESPONSE;
        }
    }

    private void tickWaitChestResponse() {
        // Check if chest opened since last tick
        int containerId = com.zenith.Globals.CACHE.getPlayerCache()
            .getInventoryCache().getOpenContainerId();
        if (containerId != 0) {
            logger.debug(TAG, "Chest opened (container %d)", containerId);
            chestCloseTicks = CHEST_CLOSE_DELAY_TICKS;
            phase = Phase.WAIT_CLOSE_DELAY;
            return;
        }

        interactTimeout--;
        if (interactTimeout <= 0) {
            // Match Nerv: retry interaction on timeout
            logger.debug(TAG, "Chest interaction timed out, retrying");
            phase = Phase.INTERACT_CHEST;
        }
    }

    private void tickWaitCloseDelay() {
        chestCloseTicks--;
        if (chestCloseTicks <= 0) {
            // Match Nerv: close the chest screen
            var session = Proxy.getInstance().getClient();
            if (session != null) {
                session.sendAsync(new org.geysermc.mcprotocollib.protocol.packet
                    .ingame.serverbound.inventory.ServerboundContainerClosePacket(
                    com.zenith.Globals.CACHE.getPlayerCache()
                        .getInventoryCache().getOpenContainerId()));
            }
            logger.debug(TAG, "Closed reset chest");
            phase = Phase.NAV_TO_CENTER;
        }
    }

    private void tickNavToCenter() {
        int centerX = job.getOriginX() + 64;
        int centerZ = job.getOriginZ() + 64;
        NavigationResult result = navigator.goTo(centerX, centerZ);
        if (result == NavigationResult.ARRIVED) {
            phase = Phase.WAIT_AREA_CLEAR;
        }
    }

    private void tickWaitAreaClear() {
        // Match Nerv: poll all 128×128 positions until clear
        int width = 128;
        int height = 128;

        boolean allClear = true;
        for (int x = 0; x < width && allClear; x++) {
            for (int z = 0; z < height && allClear; z++) {
                var state = worldScanner.getBlockAt(
                    job.getOriginX() + x, job.getOriginY(),
                    job.getOriginZ() + z);
                if (!state.isAir()) {
                    allClear = false;
                }
            }
        }

        if (allClear) {
            logger.info(TAG, "Area clear — reset complete for job %s", job.getId());
            phase = Phase.DONE;
        }
    }
}
