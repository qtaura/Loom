package org.loom.scanning;

/**
 * Wrapper around a Minecraft block type read from the world cache.
 */
public class BlockState {

    private final int blockId;
    private final String blockName;

    public BlockState(int blockId, String blockName) {
        this.blockId = blockId;
        this.blockName = blockName;
    }

    public int getBlockId() {
        return blockId;
    }

    public String getBlockName() {
        return blockName;
    }

    /**
     * Returns true if this block is air (empty space).
     */
    public boolean isAir() {
        return blockId == 0 || "minecraft:air".equals(blockName);
    }

    /**
     * Returns true if this block can be replaced by placement (air, water, grass, etc.).
     */
    public boolean isReplaceable() {
        // TODO: Check against a set of replaceable block types
        return isAir();
    }

    /**
     * Returns true if this block matches the expected material.
     */
    public boolean equalsMaterial(String materialName) {
        return blockName.equals(materialName);
    }

    @Override
    public String toString() {
        return "BlockState{" + blockName + "}";
    }
}
