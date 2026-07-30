package org.loom.scanning;

import java.util.HashMap;
import java.util.Map;

/**
 * Result of a region scan: maps world positions to their current block state.
 */
public class ScanResult {

    private final Map<String, BlockState> blocks;

    public ScanResult() {
        this.blocks = new HashMap<>();
    }

    public void put(int x, int y, int z, BlockState state) {
        blocks.put(key(x, y, z), state);
    }

    public BlockState get(int x, int y, int z) {
        return blocks.get(key(x, y, z));
    }

    public int size() {
        return blocks.size();
    }

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    @Override
    public String toString() {
        return "ScanResult{" + blocks.size() + " blocks}";
    }
}
