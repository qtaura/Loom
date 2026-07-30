package org.loom.scanning;

import com.zenith.cache.data.chunk.Chunk;
import com.zenith.mc.block.Block;
import com.zenith.mc.block.BlockDataManager;
import com.zenith.mc.block.FluidState;
import org.loom.log.LoomLogger;
import org.loom.schematic.SchematicManager;

import java.util.ArrayList;
import java.util.List;

import static com.zenith.Globals.BLOCK_DATA;
import static com.zenith.Globals.CACHE;

/**
 * Default implementation of {@link WorldScanner}.
 *
 * <p>Reads block data from ZenithProxy's chunk cache. This is the <b>only</b>
 * subsystem in Loom allowed to interact with {@code CACHE.getChunkCache()}
 * directly. All other subsystems that need world state must go through this class.
 *
 * <h3>Design</h3>
 * <ul>
 *   <li><b>Read-only:</b> Never modifies the world, never sends packets,
 *       never forces chunk loading.</li>
 *   <li><b>Unloaded chunks:</b> Returns {@link BlockState#AIR} for any
 *       position in a chunk that isn't currently loaded.</li>
 *   <li><b>Hot-path optimized:</b> {@code getBlockAt()} does minimal allocation
 *       and returns the singleton {@code AIR} when possible.</li>
 *   <li><b>Fluid awareness:</b> {@code isObstructed()} checks for water
 *       and lava via ZenithProxy's {@link FluidState} data.</li>
 * </ul>
 */
public class LoomWorldScanner implements WorldScanner {

    private static final String TAG = "WorldScanner";

    private final SchematicManager schematicManager;
    private final LoomLogger logger;

    /**
     * @param schematicManager used by {@link #compareToSchematic} to read expected blocks
     * @param logger           subsystem-tagged logger
     */
    public LoomWorldScanner(SchematicManager schematicManager, LoomLogger logger) {
        this.schematicManager = schematicManager;
        this.logger = logger;
    }

    // ======================================================================
    // Public API
    // ======================================================================

    @Override
    public BlockState getBlockAt(int worldX, int worldY, int worldZ) {
        int chunkX = worldX >> 4;
        int chunkZ = worldZ >> 4;

        if (!CACHE.getChunkCache().isChunkLoaded(chunkX, chunkZ)) {
            return BlockState.AIR;
        }

        Chunk chunk = CACHE.getChunkCache().get(chunkX, chunkZ);
        if (chunk == null) {
            return BlockState.AIR;
        }

        int relativeX = worldX & 15;
        int relativeZ = worldZ & 15;
        int blockStateId = chunk.getBlockStateId(relativeX, worldY, relativeZ);

        if (blockStateId == 0) {
            return BlockState.AIR;
        }

        Block blockData = BLOCK_DATA.getBlockDataFromBlockStateId(blockStateId);
        if (blockData == null) {
            return BlockState.AIR;
        }

        return BlockState.of(blockStateId, blockData.name(), blockData.isAir(), blockData.replaceable());
    }

    @Override
    public ScanResult scanRegion(ScanRegion region) {
        ScanResult result = new ScanResult();

        for (int x = region.getMinX(); x <= region.getMaxX(); x++) {
            for (int z = region.getMinZ(); z <= region.getMaxZ(); z++) {
                for (int y = region.getMinY(); y <= region.getMaxY(); y++) {
                    BlockState state = getBlockAt(x, y, z);
                    if (!state.isAir()) {
                        result.put(x, y, z, state);
                    }
                }
            }
        }

        logger.debug(TAG, "Scanned %s: %d non-air blocks", region, result.size());
        return result;
    }

    @Override
    public List<Discrepancy> compareToSchematic(String schematicId, ScanResult scanResult,
                                                 int originX, int originY, int originZ) {
        List<Discrepancy> discrepancies = new ArrayList<>();

        var schematic = schematicManager.getSchematic(schematicId);
        if (schematic.isEmpty()) {
            logger.warn(TAG, "Cannot compare: schematic '%s' not loaded", schematicId);
            return discrepancies;
        }

        int width = schematicManager.getWidth(schematicId);
        int height = schematicManager.getHeight(schematicId);

        for (int sx = 0; sx < width; sx++) {
            for (int sy = 0; sy < height; sy++) {
                int worldX = originX + sx;
                int worldY = originY;
                int worldZ = originZ + sy;

                String expected = schematicManager.getBlockAt(schematicId, sx, sy);
                if ("minecraft:air".equals(expected)) continue;

                // Use scan result as a cache first, fall back to direct chunk read
                BlockState actual = scanResult.get(worldX, worldY, worldZ);
                if (actual == BlockState.AIR) {
                    // Position wasn't in the scan result (was air or chunk not loaded).
                    // Double-check via direct chunk read in case the scan was incomplete.
                    actual = getBlockAt(worldX, worldY, worldZ);
                }

                if (!actual.equalsMaterial(expected)) {
                    discrepancies.add(new Discrepancy(
                        worldX, worldY, worldZ,
                        expected, actual.getBlockName()
                    ));
                }
            }
        }

        logger.debug(TAG, "Schematic comparison for '%s': %d discrepancies", schematicId, discrepancies.size());
        return discrepancies;
    }

    @Override
    public boolean isObstructed(int worldX, int worldY, int worldZ) {
        int chunkX = worldX >> 4;
        int chunkZ = worldZ >> 4;

        if (!CACHE.getChunkCache().isChunkLoaded(chunkX, chunkZ)) {
            return true; // unknown = obstructed (conservative)
        }

        Chunk chunk = CACHE.getChunkCache().get(chunkX, chunkZ);
        if (chunk == null) {
            return true;
        }

        int relativeX = worldX & 15;
        int relativeZ = worldZ & 15;
        int blockStateId = chunk.getBlockStateId(relativeX, worldY, relativeZ);

        if (blockStateId == 0) {
            return false; // air is never an obstruction
        }

        // Check for fluids (water, lava)
        FluidState fluid = BLOCK_DATA.getFluidState(blockStateId);
        if (fluid != null) {
            return true;
        }

        // Check if block is non-replaceable
        Block blockData = BLOCK_DATA.getBlockDataFromBlockStateId(blockStateId);
        if (blockData == null) {
            return true; // unknown block = obstructed (conservative)
        }

        return !blockData.replaceable() && !blockData.isAir();
    }

    @Override
    public boolean verifyBlock(int worldX, int worldY, int worldZ, String expectedMaterial) {
        BlockState actual = getBlockAt(worldX, worldY, worldZ);
        return actual.equalsMaterial(expectedMaterial);
    }
}
