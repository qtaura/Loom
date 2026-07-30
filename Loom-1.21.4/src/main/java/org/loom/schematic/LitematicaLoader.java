package org.loom.schematic;

import com.viaversion.nbt.io.NBTIO;
import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.ListTag;
import com.viaversion.nbt.tag.LongArrayTag;
import com.viaversion.nbt.tag.Tag;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Loads Litematica (.litematic) schematic files.
 *
 * <p>Litematica schematics are GZip-compressed NBT files containing:
 * <ul>
 *   <li>A {@code Metadata} compound with name, dimensions, region count</li>
 *   <li>A {@code Regions} compound with one or more named regions</li>
 *   <li>Each region has a {@code BlockStatePalette} list and
 *       {@code BlockStates} packed long array</li>
 * </ul>
 *
 * <p>The loader extracts the first region, decodes the packed palette indices,
 * and builds a dense 2D grid of material name strings indexed by (x, z).
 * For map art, the schematic is treated as a single layer (y=0 for all blocks).
 */
public class LitematicaLoader implements SchematicLoader {

    @Override
    public SchematicFormat getFormat() {
        return SchematicFormat.LITEMATICA;
    }

    @Override
    public Schematic load(String path) throws Exception {
        File file = new File(path);
        if (!file.exists()) {
            throw new IOException("Schematic file not found: " + path);
        }

        CompoundTag root;
        try (InputStream in = new FileInputStream(file);
             InputStream gzip = new GZIPInputStream(in);
             DataInputStream dataIn = new DataInputStream(gzip)) {
            root = NBTIO.readTag(dataIn, null, false, CompoundTag.class);
        }

        String id = deriveId(file.getName());
        getRequired(root, "Metadata", CompoundTag.class, path); // validate present
        CompoundTag regions = getRequired(root, "Regions", CompoundTag.class, path);

        CompoundTag region = selectRegion(regions, path);

        // Parse dimensions from region
        CompoundTag sizeTag = getRequired(region, "Size", CompoundTag.class, path);
        int width = getInt(sizeTag, "x");
        int height = getInt(sizeTag, "z"); // Z in NBT maps to schematic Y (height)
        int depth  = getInt(sizeTag, "y"); // only used for validation

        // Parse palette
        ListTag<CompoundTag> paletteList = getRequired(
            region, "BlockStatePalette", ListTag.class, path);
        List<CompoundTag> paletteEntries = paletteList.getValue();
        Map<Integer, SchematicPalette> palette = parsePalette(paletteEntries);

        // Parse block states
        LongArrayTag blockStatesTag = getRequired(
            region, "BlockStates", LongArrayTag.class, path);
        long[] blockData = blockStatesTag.getValue();

        // Decode packed palette indices into material grid
        int totalBlocks = width * height * depth;
        int bitsPerEntry = computeBitsPerEntry(paletteEntries.size());
        String[][] materials = decodeBlockStates(
            blockData, totalBlocks, bitsPerEntry, paletteEntries, width, height, depth);

        return new Schematic(id, width, height, materials, palette, SchematicFormat.LITEMATICA);
    }

    // ==================================================================
    // NBT helpers
    // ==================================================================

    private static String deriveId(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Tag> T getRequired(CompoundTag parent, String key,
                                                  Class<T> type, String path) throws IOException {
        Tag tag = parent.get(key);
        if (tag == null) {
            throw new IOException("Missing required tag '" + key + "' in " + path);
        }
        if (!type.isInstance(tag)) {
            throw new IOException("Tag '" + key + "' in " + path
                + " expected " + type.getSimpleName() + " but got " + tag.getClass().getSimpleName());
        }
        return (T) tag;
    }

    private static int getInt(CompoundTag parent, String key) {
        Tag tag = parent.get(key);
        if (tag == null) return 0;
        return ((Number) tag.getValue()).intValue();
    }

    /**
     * Selects the first region from the Regions compound.
     */
    private static CompoundTag selectRegion(CompoundTag regions, String path) throws IOException {
        var entries = regions.getValue();
        if (entries.isEmpty()) {
            throw new IOException("No regions found in schematic: " + path);
        }
        // Return the first (and typically only) region
        Object firstValue = entries.values().iterator().next();
        if (firstValue instanceof CompoundTag ct) {
            return ct;
        }
        throw new IOException("Region is not a CompoundTag in " + path);
    }

    // ==================================================================
    // Palette parsing
    // ==================================================================

    private static Map<Integer, SchematicPalette> parsePalette(List<CompoundTag> entries) {
        Map<Integer, SchematicPalette> palette = new HashMap<>();
        for (int i = 0; i < entries.size(); i++) {
            CompoundTag entry = entries.get(i);
            String name = ((String) entry.get("Name").getValue());
            boolean isCarpet = name.endsWith("_carpet");
            palette.put(i, new SchematicPalette(i, name, isCarpet));
        }
        return palette;
    }

    // ==================================================================
    // Block state decoding
    // ==================================================================

    private static int computeBitsPerEntry(int paletteSize) {
        if (paletteSize <= 1) return 1;
        int bits = 32 - Integer.numberOfLeadingZeros(paletteSize - 1);
        return Math.max(2, bits);
    }

    /**
     * Decodes the packed block state long array into a 2D material grid.
     *
     * <p>Litematica stores blocks in X→Z→Y order (x changes fastest, then z, then y).
     * For a single-layer map art (depth=1), the Y dimension is fixed at 0.
     *
     * @param blockData     the packed long array
     * @param totalBlocks   total number of blocks (width * height * depth)
     * @param bitsPerEntry  bits used per palette index
     * @param palette       the palette entries (index → CompoundTag)
     * @param width         schematic width (X)
     * @param height        schematic height (Z in Minecraft, mapped to schematic Y)
     * @param depth         schematic depth (Y in Minecraft, should be 1 for map art)
     * @return 2D array materials[height][width] of material name strings
     */
    private static String[][] decodeBlockStates(long[] blockData, int totalBlocks,
                                                 int bitsPerEntry, List<CompoundTag> palette,
                                                 int width, int height, int depth) {
        String[][] materials = new String[height][width];
        long mask = (1L << bitsPerEntry) - 1;
        int valuesPerLong = 64 / bitsPerEntry;

        for (int bi = 0; bi < totalBlocks; bi++) {
            int longIndex = bi / valuesPerLong;
            int bitIndex  = (bi % valuesPerLong) * bitsPerEntry;
            int paletteIdx = (int) ((blockData[longIndex] >>> bitIndex) & mask);

            // Convert linear index to (x, z, y)
            // Litematica order: x changes fastest, then z, then y
            int x = bi % width;
            int rem = bi / width;
            int z = rem % height;
            int y = rem / height; // for single-layer, always 0

            if (y > 0) continue; // skip extra layers (map art is one layer)

            String name;
            if (paletteIdx < palette.size()) {
                CompoundTag entry = palette.get(paletteIdx);
                name = (String) entry.get("Name").getValue();
            } else {
                name = "minecraft:air";
            }

            materials[z][x] = name;
        }

        return materials;
    }
}
