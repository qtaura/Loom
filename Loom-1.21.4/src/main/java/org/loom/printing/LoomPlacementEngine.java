package org.loom.printing;

import com.zenith.Proxy;
import com.zenith.feature.player.InputRequest;
import com.zenith.feature.player.InputRequestFuture;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSwingPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;
import org.loom.inventory.LoomInventoryManager;
import org.loom.log.LoomLogger;
import org.loom.navigation.Navigator;
import org.loom.scanning.WorldScanner;
import org.loom.util.Material;

import static com.zenith.Globals.CACHE;
import static com.zenith.Globals.INPUTS;

/**
 * Default implementation of {@link PlacementEngine}.
 *
 * <p>Uses ZenithProxy's input system for rotation and direct packet sends
 * for block placement. Breaking operations are stateful and multi-tick.
 *
 * <h3>Placement (stateless)</h3>
 * <ol>
 *   <li>Pre-checks: obstruction, material availability, reach distance</li>
 *   <li>Select correct hotbar slot via {@link LoomInventoryManager}</li>
 *   <li>Calculate placement face and look angle</li>
 *   <li>Submit rotation to Zenith {@code INPUTS} at priority 7500</li>
 *   <li>Send {@code ServerboundUseItemOnPacket} + {@code ServerboundSwingPacket}</li>
 *   <li>Return {@link PlacementResult#SUCCESS}</li>
 * </ol>
 *
 * <h3>Breaking (stateful, multi-tick)</h3>
 * <ol>
 *   <li>Validate target exists and is reachable</li>
 *   <li>Submit breaking to Zenith {@code BARITONE.breakBlock()}</li>
 *   <li>Return {@link BreakResult#IN_PROGRESS} each tick while BARITONE is active</li>
 *   <li>When BARITONE completes, verify via {@link WorldScanner}</li>
 *   <li>Return {@link BreakResult#SUCCESS} or {@link BreakResult#FAILED}</li>
 * </ol>
 */
public class LoomPlacementEngine implements PlacementEngine {

    private static final String TAG = "PlacementEngine";
    private static final int PLACEMENT_PRIORITY = 7500; // above Baritone (7000)
    private static final double MAX_REACH = 4.5;

    private final WorldScanner worldScanner;
    private final LoomInventoryManager inventoryManager;
    private final LoomLogger logger;
    private final Navigator navigator;

    // --- Break state (one active break at a time) ---
    private int breakTargetX = -1;
    private int breakTargetY = -1;
    private int breakTargetZ = -1;

    public LoomPlacementEngine(WorldScanner worldScanner,
                               LoomInventoryManager inventoryManager,
                               LoomLogger logger,
                               Navigator navigator) {
        this.worldScanner = worldScanner;
        this.inventoryManager = inventoryManager;
        this.logger = logger;
        this.navigator = navigator;
    }

    // ======================================================================
    // Public API
    // ======================================================================

    @Override
    public PlacementResult placeBlock(int worldX, int worldY, int worldZ, Material material) {
        return placeInternal(worldX, worldY, worldZ, material, false);
    }

    @Override
    public PlacementResult placeCarpet(int worldX, int worldY, int worldZ, Material material) {
        return placeInternal(worldX, worldY, worldZ, material, true);
    }

    @Override
    public boolean canPlaceAt(int worldX, int worldY, int worldZ, Material material) {
        int checkY = material.isCarpet() ? worldY + 1 : worldY;

        if (!isInReach(worldX, checkY, worldZ)) return false;
        if (worldScanner.isObstructed(worldX, checkY, worldZ)) return false;
        return inventoryManager.hasMaterial(material);
    }

    @Override
    public String getPlacementFace(int worldX, int worldY, int worldZ) {
        double px = CACHE.getPlayerCache().getX();
        double py = CACHE.getPlayerCache().getEyeY();
        double pz = CACHE.getPlayerCache().getZ();

        double dx = (worldX + 0.5) - px;
        double dy = (worldY + 0.5) - py;
        double dz = (worldZ + 0.5) - pz;

        double ax = Math.abs(dx);
        double ay = Math.abs(dy);
        double az = Math.abs(dz);

        if (ay >= ax && ay >= az) return dy >= 0 ? "down" : "up";
        if (ax >= ay && ax >= az) return dx >= 0 ? "west" : "east";
        return dz >= 0 ? "north" : "south";
    }

    @Override
    public Rotation calculateLookAngle(int worldX, int worldY, int worldZ, String face) {
        double px = CACHE.getPlayerCache().getX();
        double py = CACHE.getPlayerCache().getY();
        double pz = CACHE.getPlayerCache().getZ();
        double eyeY = py + 1.62;

        double tx = worldX + 0.5;
        double ty = worldY + 0.5;
        double tz = worldZ + 0.5;

        switch (face) {
            case "up":    ty = worldY + 1.0; break;
            case "down":  ty = worldY;       break;
            case "north": tz = worldZ;       break;
            case "south": tz = worldZ + 1.0; break;
            case "west":  tx = worldX;       break;
            case "east":  tx = worldX + 1.0; break;
        }

        double dx = tx - px;
        double dy = ty - eyeY;
        double dz = tz - pz;
        double hDist = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, hDist));

        return new Rotation(yaw, pitch);
    }

    // ======================================================================
    // Break API (stateful, multi-tick)
    // ======================================================================

    @Override
    public BreakResult breakBlock(int worldX, int worldY, int worldZ) {
        // --- Check if target is already broken ---
        var current = worldScanner.getBlockAt(worldX, worldY, worldZ);
        if (current.isAir()) {
            logger.debug(TAG, "Already broken at (%d,%d,%d)", worldX, worldY, worldZ);
            clearBreakState();
            return BreakResult.ALREADY_AIR;
        }

        // --- Check if breaking a different block ---
        if (isBreakingDifferent(worldX, worldY, worldZ)) {
            logger.debug(TAG, "Break target changed from (%d,%d,%d) to (%d,%d,%d)",
                breakTargetX, breakTargetY, breakTargetZ, worldX, worldY, worldZ);
            navigator.cancel();
            clearBreakState();
        }

        // --- Check if breaking in progress ---
        if (breakTargetX == worldX && breakTargetY == worldY && breakTargetZ == worldZ) {
            if (navigator.isBusy()) {
                return BreakResult.IN_PROGRESS;
            }
            // Navigator finished — verify
            current = worldScanner.getBlockAt(worldX, worldY, worldZ);
            if (current.isAir()) {
                logger.debug(TAG, "Break confirmed at (%d,%d,%d)", worldX, worldY, worldZ);
                clearBreakState();
                return BreakResult.SUCCESS;
            }
            logger.debug(TAG, "BARITONE finished but block not broken at (%d,%d,%d)",
                worldX, worldY, worldZ);
            clearBreakState();
            return BreakResult.FAILED;
        }

        // --- Check reach ---
        if (!isInReach(worldX, worldY, worldZ)) {
            logger.debug(TAG, "Break target (%d,%d,%d) out of reach", worldX, worldY, worldZ);
            return BreakResult.OUT_OF_REACH;
        }

        // --- Start breaking ---
        breakTargetX = worldX;
        breakTargetY = worldY;
        breakTargetZ = worldZ;

        navigator.breakBlock(worldX, worldY, worldZ);
        logger.debug(TAG, "Started breaking at (%d,%d,%d)", worldX, worldY, worldZ);
        return BreakResult.IN_PROGRESS;
    }

    private boolean isBreakingDifferent(int x, int y, int z) {
        return breakTargetX >= 0
            && (breakTargetX != x || breakTargetY != y || breakTargetZ != z);
    }

    private void clearBreakState() {
        breakTargetX = -1;
        breakTargetY = -1;
        breakTargetZ = -1;
    }

    // ======================================================================
    // Internal pipeline
    // ======================================================================

    private PlacementResult placeInternal(int worldX, int worldY, int worldZ,
                                           Material material, boolean isCarpet) {
        // --- Pre-checks ---
        int checkY = isCarpet ? worldY + 1 : worldY;

        if (!isInReach(worldX, checkY, worldZ)) {
            logger.debug(TAG, "Target (%d,%d,%d) out of reach", worldX, checkY, worldZ);
            return PlacementResult.OUT_OF_REACH;
        }
        if (worldScanner.isObstructed(worldX, checkY, worldZ)) {
            logger.debug(TAG, "Position (%d,%d,%d) obstructed", worldX, checkY, worldZ);
            return PlacementResult.OBSTRUCTED;
        }
        if (!inventoryManager.hasMaterial(material)) {
            logger.debug(TAG, "Missing material: %s", material.getDisplayName());
            return PlacementResult.NO_MATERIAL;
        }

        // --- Slot selection ---
        int slot = inventoryManager.reserveSlot(material);
        if (slot < 0) {
            // Material not in hotbar — try swapping it in
            int freeSlot = findFreeHotbarSlot();
            if (freeSlot < 0) {
                logger.warn(TAG, "No free hotbar slot for %s", material.getDisplayName());
                return PlacementResult.NO_MATERIAL;
            }
            int result = inventoryManager.swapIntoHotbar(material, freeSlot);
            if (result < 0) {
                logger.warn(TAG, "Swap failed for %s", material.getDisplayName());
                return PlacementResult.NO_MATERIAL;
            }
            // Swap submitted — on next call the material will be in hotbar
            logger.debug(TAG, "Swapped %s into hotbar slot %d", material.getDisplayName(), freeSlot);
            return PlacementResult.FAILED_RETRIES_EXHAUSTED;
        }

        // --- Face and rotation ---
        String face = isCarpet ? "up" : getPlacementFace(worldX, worldY, worldZ);
        Rotation rotation = calculateLookAngle(
            worldX, worldY, worldZ, face);

        // --- Rotate ---
        if (!submitRotation(rotation)) {
            logger.debug(TAG, "Rotation rejected by InputManager");
            inventoryManager.releaseSlot(slot);
            return PlacementResult.FAILED_RETRIES_EXHAUSTED;
        }

        // --- Place ---
        sendPlacementPacket(worldX, worldY, worldZ, face);

        logger.debug(TAG, "Placed %s at (%d,%d,%d) face=%s",
            material.getDisplayName(), worldX, isCarpet ? worldY + 1 : worldY, worldZ, face);

        inventoryManager.releaseSlot(slot);
        return PlacementResult.SUCCESS;
    }

    private boolean isInReach(int worldX, int worldY, int worldZ) {
        double px = CACHE.getPlayerCache().getX();
        double py = CACHE.getPlayerCache().getY();
        double pz = CACHE.getPlayerCache().getZ();
        double eyeY = py + 1.62;

        double dx = (worldX + 0.5) - px;
        double dy = (worldY + 0.5) - eyeY;
        double dz = (worldZ + 0.5) - pz;

        return Math.sqrt(dx * dx + dy * dy + dz * dz) <= MAX_REACH;
    }

    private boolean submitRotation(Rotation rotation) {
        InputRequest request = InputRequest.builder()
            .owner(this)
            .yaw(rotation.yaw())
            .pitch(rotation.pitch())
            .priority(PLACEMENT_PRIORITY)
            .build();

        return INPUTS.submit(request) != InputRequestFuture.rejected;
    }

    private void sendPlacementPacket(int x, int y, int z, String face) {
        var mcplDir = switch (face) {
            case "up"    -> org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction.UP;
            case "down"  -> org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction.DOWN;
            case "north" -> org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction.NORTH;
            case "south" -> org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction.SOUTH;
            case "west"  -> org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction.WEST;
            case "east"  -> org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction.EAST;
            default       -> org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction.UP;
        };

        var packet = new ServerboundUseItemOnPacket(
            x, y, z, mcplDir, Hand.MAIN_HAND,
            0.5f, 0.5f, 0.5f, false, false, 0);

        var session = Proxy.getInstance().getClient();
        if (session != null && session.isConnected()) {
            session.send(packet);
            session.send(new ServerboundSwingPacket(Hand.MAIN_HAND));
        }
    }

    /**
     * Returns the first hotbar slot likely to be usable for swapping.
     * Currently a simple heuristic: prefers slot 0.
     */
    private int findFreeHotbarSlot() {
        return 0;
    }
}
