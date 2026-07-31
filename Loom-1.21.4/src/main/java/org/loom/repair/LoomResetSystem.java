package org.loom.repair;

import org.loom.jobs.Job;
import org.loom.log.LoomLogger;
import org.loom.navigation.NavigationResult;
import org.loom.navigation.Navigator;
import org.loom.scanning.WorldScanner;

/**
 * Nerv-compatible reset system. Navigates to chest, interacts,
 * waits for external redstone to clear the area, polls until clear.
 */
public class LoomResetSystem {

    private static final String TAG = "Reset";
    private static final int CHEST_CLOSE_DELAY_TICKS = 10;

    private enum Phase {
        IDLE, NAV_TO_CHEST, WAIT_CHEST, WAIT_DELAY, NAV_TO_CENTER, WAIT_CLEAR, DONE
    }

    private final Navigator navigator;
    private final WorldScanner worldScanner;
    private final LoomLogger logger;

    private Phase phase;
    private Job job;
    private int delayTicks;
    private int resetChestX, resetChestY, resetChestZ;

    public LoomResetSystem(Navigator navigator, WorldScanner worldScanner, LoomLogger logger) {
        this.navigator = navigator;
        this.worldScanner = worldScanner;
        this.logger = logger;
        this.phase = Phase.IDLE;
    }

    public boolean isActive() { return phase != Phase.IDLE && phase != Phase.DONE; }
    public void cancel() { phase = Phase.IDLE; navigator.cancel(); }

    public void start(Job job, int chestX, int chestY, int chestZ) {
        this.job = job;
        this.resetChestX = chestX;
        this.resetChestY = chestY;
        this.resetChestZ = chestZ;
        this.delayTicks = 0;
        this.phase = Phase.NAV_TO_CHEST;
        logger.info(TAG, "Starting reset for job %s", job.getId());
    }

    public void tick() {
        switch (phase) {
            case IDLE, DONE -> {}
            case NAV_TO_CHEST -> {
                if (navigator.goTo(resetChestX, resetChestZ) == NavigationResult.ARRIVED) {
                    navigator.openChest(resetChestX, resetChestY, resetChestZ);
                    phase = Phase.WAIT_CHEST;
                }
            }
            case WAIT_CHEST -> {
                int containerId = com.zenith.Globals.CACHE.getPlayerCache()
                    .getInventoryCache().getOpenContainerId();
                if (containerId != 0) {
                    delayTicks = CHEST_CLOSE_DELAY_TICKS;
                    phase = Phase.WAIT_DELAY;
                }
            }
            case WAIT_DELAY -> {
                if (--delayTicks <= 0) {
                    var session = com.zenith.Proxy.getInstance().getClient();
                    if (session != null) {
                        session.sendAsync(new org.geysermc.mcprotocollib.protocol.packet
                            .ingame.serverbound.inventory.ServerboundContainerClosePacket(
                            com.zenith.Globals.CACHE.getPlayerCache()
                                .getInventoryCache().getOpenContainerId()));
                    }
                    phase = Phase.NAV_TO_CENTER;
                }
            }
            case NAV_TO_CENTER -> {
                if (navigator.goTo(job.getOriginX() + 64, job.getOriginZ() + 64) == NavigationResult.ARRIVED) {
                    phase = Phase.WAIT_CLEAR;
                }
            }
            case WAIT_CLEAR -> {
                int width = 128, height = 128;
                boolean allClear = true;
                for (int x = 0; x < width && allClear; x++) {
                    for (int z = 0; z < height && allClear; z++) {
                        if (!worldScanner.getBlockAt(job.getOriginX() + x, job.getOriginY(), job.getOriginZ() + z).isAir()) {
                            allClear = false; break;
                        }
                    }
                }
                if (allClear) { logger.info(TAG, "Area clear"); phase = Phase.DONE; }
            }
        }
    }
}
