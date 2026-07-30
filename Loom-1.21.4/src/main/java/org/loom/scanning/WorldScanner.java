package org.loom.scanning;

import java.util.List;

/**
 * Reads the actual world state from ZenithProxy's chunk cache.
 *
 * <p>The scanner is read-only. It never modifies the world or forces
 * chunk loading — it only queries chunks that are already loaded
 * (ZenithProxy loads chunks naturally as the bot moves).
 */
public interface WorldScanner {

    /**
     * Returns the block state at a specific world coordinate.
     *
     * @param worldX world X
     * @param worldY world Y
     * @param worldZ world Z
     * @return the block state, or an air block if the chunk is not loaded
     */
    BlockState getBlockAt(int worldX, int worldY, int worldZ);

    /**
     * Scans a rectangular region and returns all block states within it.
     *
     * <p>Only chunks that are currently loaded will be scanned.
     * Unloaded positions are reported as air.
     *
     * @param region the region to scan
     * @return the scan result
     */
    ScanResult scanRegion(ScanRegion region);

    /**
     * Compares a scan result against a schematic and returns a list of
     * positions where the world doesn't match the expected blocks.
     *
     * @param schematicId the registered schematic to compare against
     * @param scanResult  the scan to compare
     * @param originX     the schematic's world X offset
     * @param originY     the schematic's world Y offset
     * @param originZ     the schematic's world Z offset
     * @return list of discrepancies, empty if all blocks match
     */
    List<Discrepancy> compareToSchematic(String schematicId, ScanResult scanResult, int originX, int originY, int originZ);

    /**
     * Checks if a position is obstructed (entity, fluid, non-replaceable block).
     *
     * @param worldX world X
     * @param worldY world Y
     * @param worldZ world Z
     * @return true if something is blocking placement at this position
     */
    boolean isObstructed(int worldX, int worldY, int worldZ);

    /**
     * Verifies that a block at a given position matches the expected material.
     * Used to confirm that a placement succeeded.
     *
     * @param worldX          world X
     * @param worldY          world Y
     * @param worldZ          world Z
     * @param expectedMaterial the material name to check for
     * @return true if the block at the position matches the expected material
     */
    boolean verifyBlock(int worldX, int worldY, int worldZ, String expectedMaterial);
}
