package org.loom.schematic;

/**
 * Loads Sponge schematic (.schem) files.
 */
public class SpongeSchematicLoader implements SchematicLoader {

    @Override
    public SchematicFormat getFormat() {
        return SchematicFormat.SPONGE_SCHEMATIC;
    }

    @Override
    public Schematic load(String path) throws Exception {
        // TODO: Open the .schem file as NBT
        // TODO: Parse dimensions and palette
        // TODO: Build materials[][] grid
        // TODO: Return new Schematic(...)
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
