package org.loom.schematic;

/**
 * Loads Sponge schematic (.schem) files.
 *
 * <p>Not yet implemented. Sponge schematics use a different NBT structure
 * (Schematic format v2) with {@code Width/Height/Length}, {@code Palette},
 * and {@code BlockData} byte arrays encoding varint palette indices.
 */
public class SpongeSchematicLoader implements SchematicLoader {

    @Override
    public SchematicFormat getFormat() {
        return SchematicFormat.SPONGE_SCHEMATIC;
    }

    @Override
    public Schematic load(String path) throws Exception {
        throw new UnsupportedOperationException(
            "Sponge schematic (.schem) format is not yet supported. Use .litematic format instead.");
    }
}
