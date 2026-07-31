package org.loom.schematic;

import java.util.Map;

/**
 * A loaded schematic representing a map art design.
 *
 * <p>Stores a dense 2D grid of material names for O(1) lookup at any position.
 * The palette maps palette indices (from the file format) to material name strings.
 */
public class Schematic {

    private final String id;
    private final int width;
    private final int height;
    private final String[][] materials;
    private final Map<Integer, SchematicPalette> palette;
    private final SchematicFormat format;

    public Schematic(String id, int width, int height, String[][] materials,
                     Map<Integer, SchematicPalette> palette, SchematicFormat format) {
        this.id = id;
        this.width = width;
        this.height = height;
        this.materials = materials;
        this.palette = Map.copyOf(palette);
        this.format = format;
    }

    public String getId() { return id; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public SchematicFormat getFormat() { return format; }

    /**
     * Returns the material name at the given schematic-relative position.
     *
     * @param x schematic-relative X (0 to width-1)
     * @param y schematic-relative Y (0 to height-1)
     * @return the material name, or "minecraft:air" if out of bounds
     */
    public String getBlockAt(int x, int y) {
        if (x < 0 || x >= width || y < 0 || y >= height) {
            return "minecraft:air";
        }
        return materials[y][x];
    }

    /**
     * Replaces a block in the materials grid with a new material.
     * Used by the schematic manager for post-load filtering (e.g. ignored blocks).
     */
    void replaceBlock(int x, int y, String material) {
        if (x >= 0 && x < width && y >= 0 && y < height) {
            materials[y][x] = material;
        }
    }

    /**
     * Returns the full palette for this schematic.
     */
    public Map<Integer, SchematicPalette> getPalette() {
        return palette;
    }

    /**
     * Returns the total number of non-air blocks in this schematic.
     */
    public int getTotalBlocks() {
        int count = 0;
        for (int row = 0; row < height; row++) {
            for (int col = 0; col < width; col++) {
                if (!"minecraft:air".equals(materials[row][col])) {
                    count++;
                }
            }
        }
        return count;
    }

    @Override
    public String toString() {
        return "Schematic{id='" + id + "', " + width + "x" + height
            + ", blocks=" + getTotalBlocks() + ", format=" + format + "}";
    }
}
