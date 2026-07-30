package org.loom.navigation;

import com.zenith.feature.pathfinder.PathingRequestFuture;
import com.zenith.feature.pathfinder.goals.GoalXZ;
import com.zenith.util.math.MathHelper;
import org.loom.log.LoomLogger;

import static com.zenith.Globals.BARITONE;
import static com.zenith.Globals.CACHE;

/**
 * Default implementation of {@link Navigator}.
 *
 * <p>Wraps ZenithProxy's BARITONE pathfinder. This is the <b>only</b> subsystem
 * in Loom allowed to interact with {@code BARITONE} directly. All other
 * subsystems that need navigation must go through this class.
 *
 * <h3>Design</h3>
 * <ul>
 *   <li><b>Non-blocking:</b> {@code goTo()} returns immediately with {@link NavigationResult#ACCEPTED}
 *       or {@link NavigationResult#ARRIVED}. Actual arrival is detected by polling
 *       {@link #isNavigating()}.</li>
 *   <li><b>Callback-driven:</b> A {@code PathingRequestFuture} listener updates
 *       internal state when BARITONE completes the path.</li>
 *   <li><b>Overlap prevention:</b> A new request while navigating cancels the
 *       current path before starting a new one (unless the destination is the same).</li>
 *   <li><b>Stuck detection:</b> Tracks position over time. If the bot hasn't
 *       moved significantly while BARITONE is active, it's flagged as stuck.</li>
 * </ul>
 *
 * <h3>Thread Safety</h3>
 * <p>State mutation methods ({@code goTo}, {@code cancel}) are synchronized.
 * State fields are volatile. The BARITONE completion callback fires on a
 * separate executor thread; it writes to volatile fields and is idempotent.
 */
public class LoomNavigator implements Navigator {

    private static final String TAG = "Navigator";

    // ---------- Configuration ----------
    private final int storageX;
    private final int storageY;
    private final int storageZ;
    private final int buildOriginX;
    private final int buildOriginZ;
    private final LoomLogger logger;

    // ---------- Stuck detection ----------
    /** Duration in milliseconds before the bot is considered stuck. */
    private static final long STUCK_THRESHOLD_MS = 10_000;
    /** Minimum horizontal movement in blocks to reset stuck detection. */
    private static final int STUCK_MOVEMENT_THRESHOLD = 3;

    // ---------- Navigation state (volatile for cross-thread visibility) ----------
    private volatile int targetX;
    private volatile int targetZ;
    private volatile int lastDestinationX;
    private volatile int lastDestinationZ;
    private volatile PathingRequestFuture activeFuture;
    private volatile NavigationResult lastResult;
    private long navigationStartTime;
    private int stuckCheckX;
    private int stuckCheckZ;
    private long stuckSinceTime;

    // ---------- Construction ----------

    /**
     * @param storageX     world X of the restock chest area
     * @param storageY     world Y of the restock chest area
     * @param storageZ     world Z of the restock chest area
     * @param buildOriginX world X of the build area origin
     * @param buildOriginZ world Z of the build area origin
     * @param logger       LoomLogger for subsystem-tagged output
     */
    public LoomNavigator(int storageX, int storageY, int storageZ,
                         int buildOriginX, int buildOriginZ,
                         LoomLogger logger) {
        this.storageX = storageX;
        this.storageY = storageY;
        this.storageZ = storageZ;
        this.buildOriginX = buildOriginX;
        this.buildOriginZ = buildOriginZ;
        this.logger = logger;
        resetState();
    }

    private void resetState() {
        this.targetX = -1;
        this.targetZ = -1;
        this.lastDestinationX = -1;
        this.lastDestinationZ = -1;
        this.activeFuture = null;
        this.lastResult = null;
        this.navigationStartTime = 0;
        this.stuckCheckX = 0;
        this.stuckCheckZ = 0;
        this.stuckSinceTime = 0;
    }

    // ======================================================================
    // Public API
    // ======================================================================

    /**
     * {@inheritDoc}
     *
     * <p>If a different destination is currently being navigated to, the
     * current path is cancelled before starting the new one. If the same
     * destination is already being navigated to, this returns {@code ACCEPTED}
     * without restarting.
     */
    @Override
    public synchronized NavigationResult goTo(int x, int z) {
        int currentX = MathHelper.floorI(CACHE.getPlayerCache().getX());
        int currentZ = MathHelper.floorI(CACHE.getPlayerCache().getZ());

        // Already at destination?
        if (currentX == x && currentZ == z) {
            logger.debug(TAG, "Already at destination ({}, {})", x, z);
            this.lastDestinationX = x;
            this.lastDestinationZ = z;
            this.lastResult = NavigationResult.ARRIVED;
            return NavigationResult.ARRIVED;
        }

        // Already navigating to the same target?
        if (isNavigating() && targetX == x && targetZ == z) {
            logger.debug(TAG, "Already navigating to ({}, {})", x, z);
            return NavigationResult.ACCEPTED;
        }

        // Different target — cancel current navigation first
        if (isNavigating()) {
            logger.debug(TAG, "Cancelling current navigation to ({}, {}) for new target ({}, {})",
                targetX, targetZ, x, z);
            cancelInternal();
        }

        // Start new navigation
        return startNavigation(x, z);
    }

    @Override
    public NavigationResult goToStorage() {
        logger.info(TAG, "Navigating to storage at ({}, {}, {})", storageX, storageY, storageZ);
        return goTo(storageX, storageZ);
    }

    @Override
    public NavigationResult goToBuildArea() {
        logger.info(TAG, "Navigating to build area origin at ({}, {})", buildOriginX, buildOriginZ);
        return goTo(buildOriginX, buildOriginZ);
    }

    @Override
    public NavigationResult moveToPlacementPosition(int worldX, int worldY, int worldZ, String face) {
        //
        // For carpet placement: the carpet goes ON TOP of the supporting block
        // at (worldX, worldY-1, worldZ). The bot needs to stand adjacent to
        // that block within reach distance (typically ~4.5 blocks).
        //
        // A simple heuristic: stand one block south (positive Z) of the target.
        // This gives a clear line of sight to the top face of the supporting block.
        //
        int standX = worldX;
        int standZ = worldZ + 1;

        logger.debug(TAG, "Moving to placement position for ({}, {}, {}), standing at ({}, {})",
            worldX, worldY, worldZ, standX, standZ);

        return goTo(standX, standZ);
    }

    @Override
    public synchronized void cancel() {
        if (!isNavigating() && activeFuture == null) return;

        logger.debug(TAG, "Cancelling navigation to ({}, {})", targetX, targetZ);
        this.lastResult = NavigationResult.CANCELLED;
        cancelInternal();
    }

    @Override
    public boolean isNavigating() {
        PathingRequestFuture future = this.activeFuture;
        if (future == null) return false;

        if (future.isCompleted()) {
            return false;
        }

        if (!BARITONE.isActive()) {
            return false;
        }

        return true;
    }

    @Override
    public boolean isStuck() {
        if (!isNavigating()) return false;

        int currentX = MathHelper.floorI(CACHE.getPlayerCache().getX());
        int currentZ = MathHelper.floorI(CACHE.getPlayerCache().getZ());

        int dx = Math.abs(currentX - stuckCheckX);
        int dz = Math.abs(currentZ - stuckCheckZ);

        long now = System.currentTimeMillis();

        if (dx < STUCK_MOVEMENT_THRESHOLD && dz < STUCK_MOVEMENT_THRESHOLD) {
            if (stuckSinceTime == 0) {
                stuckSinceTime = now;
                return false;
            }
            if (now - stuckSinceTime > STUCK_THRESHOLD_MS) {
                logger.warn(TAG, "Bot appears stuck at ({}, {}) for {}ms, target ({}, {})",
                    currentX, currentZ, now - stuckSinceTime, targetX, targetZ);
                return true;
            }
            return false;
        }

        // Bot moved — reset stuck tracking to new position
        stuckCheckX = currentX;
        stuckCheckZ = currentZ;
        stuckSinceTime = 0;
        return false;
    }

    @Override
    public int[] getCurrentPathTarget() {
        return new int[]{targetX, targetZ};
    }

    @Override
    public NavigationResult getLastNavigationResult() {
        return lastResult;
    }

    @Override
    public int[] getLastDestination() {
        return new int[]{lastDestinationX, lastDestinationZ};
    }

    // ======================================================================
    // Internal
    // ======================================================================

    /**
     * Starts a new BARITONE navigation to the given coordinates.
     * Caller must hold the monitor lock.
     */
    private NavigationResult startNavigation(int x, int z) {
        GoalXZ goal = new GoalXZ(x, z);

        this.targetX = x;
        this.targetZ = z;
        this.lastResult = null;
        this.navigationStartTime = System.currentTimeMillis();

        int currentX = MathHelper.floorI(CACHE.getPlayerCache().getX());
        int currentZ = MathHelper.floorI(CACHE.getPlayerCache().getZ());
        this.stuckCheckX = currentX;
        this.stuckCheckZ = currentZ;
        this.stuckSinceTime = 0;

        try {
            this.activeFuture = BARITONE.pathTo(goal);
            this.activeFuture.addExecutedListener(this::onPathComplete);
            logger.debug(TAG, "Started navigation to ({}, {}) from ({}, {})", x, z, currentX, currentZ);
            return NavigationResult.ACCEPTED;
        } catch (Exception e) {
            logger.error(TAG, "Failed to start navigation to (" + x + ", " + z + ")", e);
            resetState();
            return NavigationResult.REJECTED;
        }
    }

    /**
     * Cancels the current BARITONE path without changing {@code lastResult}.
     * Caller must hold the monitor lock.
     */
    private void cancelInternal() {
        if (activeFuture != null) {
            BARITONE.stop();
            activeFuture = null;
        }
        targetX = -1;
        targetZ = -1;
        stuckSinceTime = 0;
    }

    /**
     * Callback invoked by BARITONE's executor when the pathing request
     * completes (success or failure). Runs on a BARITONE executor thread.
     */
    private void onPathComplete(PathingRequestFuture future) {
        if (future.isAccepted()) {
            this.lastDestinationX = this.targetX;
            this.lastDestinationZ = this.targetZ;
            this.lastResult = NavigationResult.ARRIVED;
            long elapsed = System.currentTimeMillis() - navigationStartTime;
            logger.debug(TAG, "Arrived at ({}, {}) in {}ms", lastDestinationX, lastDestinationZ, elapsed);
        } else {
            this.lastResult = NavigationResult.PATH_FAILED;
            logger.warn(TAG, "Path failed for target ({}, {})", targetX, targetZ);
        }

        this.activeFuture = null;
        this.targetX = -1;
        this.targetZ = -1;
    }
}
