package org.loom.schematic;

import com.viaversion.nbt.io.NBTIO;
import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.ListTag;
import com.viaversion.nbt.tag.Tag;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads vanilla Minecraft Structure (.nbt) files.
 *
 * <p>Structure files are uncompressed NBT containing:
 * <ul>
 *   <li>A {@code palette} list of compound tags with {@code Name} key</li>
 *   <li>A {@code blocks} list of {@code {state: int, pos: [x,y,z]}} entries</li>
 *   <li>A {@code size} list of {@code [width, height, depth]}</li>
 * </ul>
 *
 * <p>For map art, the loader extracts only the highest Y layer from the
 * block list and builds a dense 2D grid compatible with
 * {@link Schematic#getBlockAt}.
 */
public class VanillaStructureLoader implements SchematicLoader {

    @Override
    public SchematicFormat getFormat() {
        return SchematicFormat.NBT_STRUCTURE;
    }

    @Override
    public Schematic load(String path) throws Exception {
        File file = new File(path);
        if (!file.exists()) {
            throw new IOException("Structure file not found: " + path);
        }

        // Vanilla structure NBT is uncompressed
        CompoundTag root;
        try (InputStream in = new FileInputStream(file);
             DataInputStream dataIn = new DataInputStream(in)) {
            root = NBTIO.readTag(dataIn, null, false, CompoundTag.class);
        }

        String id = deriveId(file.getName());

        // Parse palette
        ListTag<CompoundTag> paletteList = getRequired(
            root, "palette", ListTag.class, path);
        List<CompoundTag> paletteEntries = paletteList.getValue();
        Map<Integer, SchematicPalette> palette = parsePalette(paletteEntries);

        // Parse blocks
        ListTag<CompoundTag> blockList = getRequired(
            root, "blocks", ListTag.class, path);
        List<CompoundTag> blocks = blockList.getValue();

        // Parse size
        ListTag<?> sizeTag = getRequired(root, "size", ListTag.class, path);
        List<?> sizeValues = sizeTag.getValue();
        int width  = ((Number) sizeValues.get(0)).intValue();
        int height = ((Number) sizeValues.get(2)).intValue(); // Z in size = schematic height
        int depth  = ((Number) sizeValues.get(1)).intValue(); // Y in size = depth/layers

        // Build 2D grid
        String[][] materials = new String[height][width];

        // Find highest Y and extract map layer
        int maxY = findMaxY(blocks);

        for (CompoundTag blockEntry : blocks) {
            ListTag<? extends Tag> posTag = (ListTag<? extends Tag>) blockEntry.get("pos");
            if (posTag == null) continue;

            List<?> posValues = posTag.getValue();
            if (posValues.size() < 3) continue;

            int x = ((Number) posValues.get(0)).intValue();
            int y = ((Number) posValues.get(1)).intValue();
            int z = ((Number) posValues.get(2)).intValue();

            // Only extract blocks at the highest Y layer
            if (y != maxY) continue;
            if (x < 0 || x >= width || z < 0 || z >= height) continue;

            Tag stateTag = blockEntry.get("state");
            if (stateTag == null) continue;
            int stateIdx = ((Number) stateTag.getValue()).intValue();

            if (stateIdx >= 0 && stateIdx < paletteEntries.size()) {
                String name = (String) paletteEntries.get(stateIdx).get("Name").getValue();
                materials[z][x] = name;
            }
        }

        return new Schematic(id, width, height, materials, palette, SchematicFormat.NBT_STRUCTURE);
    }

    // ==================================================================
    // Helpers
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

    private static Map<Integer, SchematicPalette> parsePalette(List<CompoundTag> entries) {
        Map<Integer, SchematicPalette> palette = new HashMap<>();
        for (int i = 0; i < entries.size(); i++) {
            Tag nameTag = entries.get(i).get("Name");
            if (nameTag == null) continue;
            String name = (String) nameTag.getValue();
            boolean isCarpet = name.endsWith("_carpet");
            palette.put(i, new SchematicPalette(i, name, isCarpet));
        }
        return palette;
    }

    private static int findMaxY(List<CompoundTag> blocks) {
        int maxY = Integer.MIN_VALUE;
        for (CompoundTag blockEntry : blocks) {
            ListTag<? extends Tag> posTag = (ListTag<? extends Tag>) blockEntry.get("pos");
            if (posTag == null || posTag.getValue().size() < 3) continue;
            int y = ((Number) posTag.getValue().get(1)).intValue();
            if (y > maxY) maxY = y;
        }
        return maxY == Integer.MIN_VALUE ? 0 : maxY;
    }
}
