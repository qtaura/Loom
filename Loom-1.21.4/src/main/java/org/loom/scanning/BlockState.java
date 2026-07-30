package org.loom.scanning;

/**
 * An immutable snapshot of a Minecraft block at a specific world position.
 *
 * <p>Wraps the block state ID and material name read from
 * ZenithProxy's chunk cache via {@link LoomWorldScanner}.
 *
 * <p>A static {@link #AIR} constant is provided for positions where
 * the chunk is not loaded, avoiding unnecessary allocations.
 */
public class BlockState {

    private final int blockStateId;
    private final String blockName;
    private final boolean air;
    private final boolean replaceable;

    /** Reusable air instance for unloaded chunks / empty positions. */
    public static final BlockState AIR = new BlockState(0, "minecraft:air", true, true);

    private BlockState(int blockStateId, String blockName, boolean air, boolean replaceable) {
        this.blockStateId = blockStateId;
        this.blockName = blockName;
        this.air = air;
        this.replaceable = replaceable;
    }

    /**
     * Creates a BlockState from ZenithProxy block data.
     *
     * @param blockStateId the raw block state ID from the chunk section
     * @param blockName    the fully qualified block name (e.g. "minecraft:white_carpet")
     * @param air          true if the block is air
     * @param replaceable  true if the block can be replaced by placement
     */
    public static BlockState of(int blockStateId, String blockName, boolean air, boolean replaceable) {
        if (air && blockStateId == 0) return AIR;
        return new BlockState(blockStateId, blockName, air, replaceable);
    }

    /** @deprecated Use {@link #of(int, String, boolean, boolean)}. Retained for backward compatibility. */
    @Deprecated
    public BlockState(int blockId, String blockName) {
        this.blockStateId = blockId;
        this.blockName = blockName;
        this.air = blockId == 0 || "minecraft:air".equals(blockName);
        this.replaceable = false;
    }

    public int getBlockStateId() {
        return blockStateId;
    }

    /** @deprecated Use {@link #getBlockStateId()}. */
    @Deprecated
    public int getBlockId() {
        return blockStateId;
    }

    public String getBlockName() {
        return blockName;
    }

    /**
     * Returns true if this block is air (empty space).
     */
    public boolean isAir() {
        return air;
    }

    /**
     * Returns true if this block can be replaced by placement
     * (air, water, tall grass, and other replaceable blocks).
     */
    public boolean isReplaceable() {
        return replaceable;
    }

    /**
     * Returns true if this block's material name matches the expected value.
     */
    public boolean equalsMaterial(String materialName) {
        return blockName.equals(materialName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof BlockState that)) return false;
        return blockStateId == that.blockStateId;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(blockStateId);
    }

    @Override
    public String toString() {
        return "BlockState{" + blockName + "}";
    }
}
