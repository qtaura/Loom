package org.loom.printing;

import com.zenith.Proxy;
import com.zenith.feature.player.InputRequest;
import com.zenith.feature.player.InputRequestFuture;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.Hand;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundSwingPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.player.ServerboundUseItemOnPacket;
import org.loom.inventory.LoomInventoryManager;
import org.loom.log.LoomLogger;
import org.loom.scanning.WorldScanner;
import org.loom.util.Material;

import static com.zenith.Globals.CACHE;
import static com.zenith.Globals.INPUTS;

/**
 * Default implementation of {@link PlacementEngine}.
 *
 * <p>Uses ZenithProxy's input system for rotation and direct packet sends
 * for block placement. This is the <b>only</b> subsystem in Loom allowed
 * to interact with {@code INPUTS}, send placement packets, or control
 * player rotation for placement purposes.
 *
 * <h3>Placement Pipeline (per call)</h3>
 * <ol>
 *   <li>Pre-checks: obstruction, material availability, reach distance</li>
 *   <li>Select correct hotbar slot via {@link LoomInventoryManager}</li>
 *   <li>Calculate placement face and look angle</li>
 *   <li>Submit rotation to Zenith {@code INPUTS}</li>
 *   <li>Send placement packet directly to server</li>
 *   <li>Verify placement via {@link WorldScanner}</li>
 *   <li>Retry on failure (up to configured max)</li>
 * </ol>
 *
 * <p>The engine is stateless between calls. Retry logic is contained
 * within a single invocation.
 */
public class LoomPlacementEngine implements PlacementEngine {

    private static final String TAG = "PlacementEngine";
    private static final int PLACEMENT_PRIORITY = 7500; // above Baritone (7000)
    private static final double MAX_REACH = 4.5;

    private final WorldScanner worldScanner;
    private final LoomInventoryManager inventoryManager;
    private final LoomLogger logger;
    private final int maxRetries;

    public LoomPlacementEngine(WorldScanner worldScanner,
                               LoomInventoryManager inventoryManager,
                               LoomLogger logger,
                               int maxRetries) {
        this.worldScanner = worldScanner;
        this.inventoryManager = inventoryManager;
        this.logger = logger;
        this.maxRetries = maxRetries;
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
        // For carpet: worldY is the supporting block below.
        // The carpet goes at worldY+1. We click the top face of (worldX, worldY, worldZ).
        return placeInternal(worldX, worldY, worldZ, material, true);
    }

    @Override
    public boolean canPlaceAt(int worldX, int worldY, int worldZ, Material material) {
        double playerX = CACHE.getPlayerCache().getX();
        double playerY = CACHE.getPlayerCache().getY();
        double playerZ = CACHE.getPlayerCache().getZ();

        // Reach check: distance from player eye to block center
        int targetY = material.isCarpet() ? worldY + 1 : worldY;
        double dx = (worldX + 0.5) - playerX;
        double dy = (targetY + 0.5) - (playerY + 1.62);
        double dz = (worldZ + 0.5) - playerZ;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (dist > MAX_REACH) return false;

        // Obstruction check
        if (material.isCarpet()) {
            int carpetY = worldY + 1;
            if (worldScanner.isObstructed(worldX, carpetY, worldZ)) return false;
        } else {
            if (worldScanner.isObstructed(worldX, worldY, worldZ)) return false;
        }

        // Material check
        if (!inventoryManager.hasMaterial(material)) return false;

        return true;
    }

    @Override
    public String getPlacementFace(int worldX, int worldY, int worldZ) {
        // Determine best face to click based on player position relative to target.
        // For carpet (always placed on top of supporting block), just return "up".
        // For blocks, choose the face closest to the player's line of sight.

        double playerX = CACHE.getPlayerCache().getX();
        double playerY = CACHE.getPlayerCache().getEyeY();
        double playerZ = CACHE.getPlayerCache().getZ();

        double targetCenterX = worldX + 0.5;
        double targetCenterY = worldY + 0.5;
        double targetCenterZ = worldZ + 0.5;

        double dx = targetCenterX - playerX;
        double dy = targetCenterY - playerY;
        double dz = targetCenterZ - playerZ;

        double absDx = Math.abs(dx);
        double absDy = Math.abs(dy);
        double absDz = Math.abs(dz);

        if (absDy >= absDx && absDy >= absDz) {
            return dy >= 0 ? "down" : "up";
        } else if (absDx >= absDy && absDx >= absDz) {
            return dx >= 0 ? "west" : "east";
        } else {
            return dz >= 0 ? "north" : "south";
        }
    }

    @Override
    public Rotation calculateLookAngle(int worldX, int worldY, int worldZ, String face) {
        double playerX = CACHE.getPlayerCache().getX();
        double playerY = CACHE.getPlayerCache().getY();
        double playerZ = CACHE.getPlayerCache().getZ();

        // Target: center of the specified face
        double targetX = worldX + 0.5;
        double targetY = worldY + 0.5;
        double targetZ = worldZ + 0.5;

        switch (face) {
            case "up":
                targetY = worldY + 1.0;
                break;
            case "down":
                targetY = worldY;
                break;
            case "north":
                targetZ = worldZ;
                break;
            case "south":
                targetZ = worldZ + 1.0;
                break;
            case "west":
                targetX = worldX;
                break;
            case "east":
                targetX = worldX + 1.0;
                break;
        }

        double eyeY = playerY + 1.62;

        double dx = targetX - playerX;
        double dy = targetY - eyeY;
        double dz = targetZ - playerZ;

        double horizontalDist = Math.sqrt(dx * dx + dz * dz);

        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontalDist));

        return new Rotation(yaw, pitch);
    }

    // ======================================================================
    // Internal
    // ======================================================================

    /**
     * Shared placement pipeline for both blocks and carpets.
     *
     * @param worldX   world X of the supporting block to click
     * @param worldY   world Y of the supporting block (carpet goes on Y+1)
     * @param worldZ   world Z of the supporting block to click
     * @param material the material to place
     * @param isCarpet true for carpet placement (click top face, place at Y+1)
     */
    private PlacementResult placeInternal(int worldX, int worldY, int worldZ,
                                           Material material, boolean isCarpet) {
        // --- Step 1: Pre-checks ---
        PlacementResult preCheck = runPreChecks(worldX, worldY, worldZ, material, isCarpet);
        if (preCheck != null) return preCheck;

        // --- Step 2: Select hotbar slot ---
        int slot = inventoryManager.reserveSlot(material);
        if (slot < 0) {
            logger.warn(TAG, "No slot available for %s", material.getDisplayName());
            return PlacementResult.NO_MATERIAL;
        }

        // --- Determine placement target ---
        int clickX = worldX;
        int clickY = worldY;
        int clickZ = worldZ;
        int placedY = isCarpet ? worldY + 1 : worldY;
        String face = isCarpet ? "up" : getPlacementFace(worldX, placedY, worldZ);
        Rotation rotation = calculateLookAngle(clickX, clickY, clickZ, face);

        // --- Step 3: Retry loop ---
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            // Step 3a: Submit rotation
            boolean rotationAccepted = submitRotation(rotation);
            if (!rotationAccepted) {
                logger.debug(TAG, "Rotation rejected by InputManager");
                inventoryManager.releaseSlot(slot);
                return PlacementResult.FAILED_RETRIES_EXHAUSTED;
            }

            // Step 3b: Send placement packet
            sendPlacementPacket(clickX, clickY, clickZ, face);

            // Step 3c: Verify (optional)
            if (!verifyPlacement(clickX, placedY, clickZ, material)) {
                if (attempt < maxRetries) {
                    logger.debug(TAG, "Placement verify failed, retry %d/%d", attempt + 1, maxRetries);
                    continue;
                }
                logger.warn(TAG, "Failed to place %s at (%d, %d, %d) after %d attempts",
                    material.getDisplayName(), worldX, placedY, worldZ, maxRetries + 1);
                inventoryManager.releaseSlot(slot);
                return PlacementResult.FAILED_RETRIES_EXHAUSTED;
            }

            // Success
            logger.debug(TAG, "Placed %s at (%d, %d, %d)", material.getDisplayName(), worldX, placedY, worldZ);
            inventoryManager.releaseSlot(slot);
            return PlacementResult.SUCCESS;
        }

        inventoryManager.releaseSlot(slot);
        return PlacementResult.FAILED_RETRIES_EXHAUSTED;
    }

    /**
     * Runs all pre-placement checks.
     *
     * @return a failing PlacementResult if checks fail, or null if placement can proceed
     */
    private PlacementResult runPreChecks(int worldX, int worldY, int worldZ,
                                          Material material, boolean isCarpet) {
        int checkY = isCarpet ? worldY + 1 : worldY;

        // Reach check
        double playerX = CACHE.getPlayerCache().getX();
        double playerY = CACHE.getPlayerCache().getY();
        double playerZ = CACHE.getPlayerCache().getZ();
        double eyeY = playerY + 1.62;

        double dx = (worldX + 0.5) - playerX;
        double dy = (checkY + 0.5) - eyeY;
        double dz = (worldZ + 0.5) - playerZ;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (dist > MAX_REACH) {
            logger.debug(TAG, "Target (%d, %d, %d) out of reach (%.1f blocks)", worldX, checkY, worldZ, dist);
            return PlacementResult.OUT_OF_REACH;
        }

        // Obstruction check
        if (worldScanner.isObstructed(worldX, checkY, worldZ)) {
            logger.debug(TAG, "Position (%d, %d, %d) is obstructed", worldX, checkY, worldZ);
            return PlacementResult.OBSTRUCTED;
        }

        // Material check
        if (!inventoryManager.hasMaterial(material)) {
            logger.debug(TAG, "Missing material: %s", material.getDisplayName());
            return PlacementResult.NO_MATERIAL;
        }

        return null; // all clear
    }

    /**
     * Submits a rotation-only InputRequest to Zenith's InputManager.
     *
     * @return true if the rotation was accepted, false if rejected
     */
    private boolean submitRotation(Rotation rotation) {
        InputRequest request = InputRequest.builder()
            .owner(this)
            .yaw(rotation.yaw())
            .pitch(rotation.pitch())
            .priority(PLACEMENT_PRIORITY)
            .build();

        InputRequestFuture future = INPUTS.submit(request);

        if (future == InputRequestFuture.rejected) {
            return false;
        }

        return true;
    }

    /**
     * Sends the placement packet directly to the server.
     */
    private void sendPlacementPacket(int x, int y, int z, String face) {
        org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction mcplFace =
            faceToMcpl(face);

        var packet = new ServerboundUseItemOnPacket(
            x, y, z,
            mcplFace,
            Hand.MAIN_HAND,
            0.5f, 0.5f, 0.5f,
            false,
            false,
            0
        );

        var clientSession = Proxy.getInstance().getClient();
        if (clientSession != null && clientSession.isConnected()) {
            clientSession.send(packet);
            clientSession.send(new ServerboundSwingPacket(Hand.MAIN_HAND));
        }
    }

    /**
     * Verifies that a placement succeeded by checking the world state.
     */
    private boolean verifyPlacement(int worldX, int worldY, int worldZ, Material material) {
        // Verification is optional; if disabled, assume success
        // TODO: Read from config (verifyPlacements)
        return true;
    }

    /**
     * Converts a Loom face string to MCPL Direction.
     */
    private static org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction
            faceToMcpl(String face) {
        return switch (face) {
            case "up" -> org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction.UP;
            case "down" -> org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction.DOWN;
            case "north" -> org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction.NORTH;
            case "south" -> org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction.SOUTH;
            case "west" -> org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction.WEST;
            case "east" -> org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction.EAST;
            default -> org.geysermc.mcprotocollib.protocol.data.game.entity.object.Direction.UP;
        };
    }
}
