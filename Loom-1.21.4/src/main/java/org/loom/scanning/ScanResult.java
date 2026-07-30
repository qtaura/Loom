package org.loom.scanning;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Result of a region scan: maps world positions to their current block state.
 *
 * <p>The internal map only stores non-air blocks for memory efficiency.
 * Positions not in the map are implicitly air.
 */
public class ScanResult {

    private final Map<String, BlockState> blocks;

    public ScanResult() {
        this.blocks = new HashMap<>();
    }

    /**
     * Stores a block state at the given world coordinates.
     */
    public void put(int x, int y, int z, BlockState state) {
        blocks.put(key(x, y, z), state);
    }

    /**
     * Returns the block state at the given coordinates, or {@link BlockState#AIR}
     * if the position was not stored.
     */
    public BlockState get(int x, int y, int z) {
        return blocks.getOrDefault(key(x, y, z), BlockState.AIR);
    }

    /**
     * Returns the number of non-air blocks stored in this result.
     */
    public int size() {
        return blocks.size();
    }

    /**
     * Returns an unmodifiable view of all stored entries for iteration.
     */
    public Set<Map.Entry<String, BlockState>> entrySet() {
        return Collections.unmodifiableSet(blocks.entrySet());
    }

    /**
     * Parses a key back into world coordinates.
     *
     * @param key the position key in "x,y,z" format
     * @return int[3] containing {x, y, z}
     */
    public static int[] parseKey(String key) {
        String[] parts = key.split(",", 3);
        return new int[]{
            Integer.parseInt(parts[0]),
            Integer.parseInt(parts[1]),
            Integer.parseInt(parts[2])
        };
    }

    static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    @Override
    public String toString() {
        return "ScanResult{" + blocks.size() + " blocks}";
    }
}
