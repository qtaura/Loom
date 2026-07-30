package org.loom.schematic;

/**
 * Loads Litematica (.litematic) schematic files.
 *
 * <p>Litematica schematics are NBT-compressed files containing a block palette,
 * dimensions, and a block data array. This loader extracts the map art layer
 * and builds a dense 2D grid of material names.
 */
public class LitematicaLoader implements SchematicLoader {

    @Override
    public SchematicFormat getFormat() {
        return SchematicFormat.LITEMATICA;
    }

    @Override
    public Schematic load(String path) throws Exception {
        // TODO: Open the .litematic file as NBT
        // TODO: Parse width, height from metadata
        // TODO: Parse block palette (index → block name)
        // TODO: Parse block data array and build materials[][] grid
        // TODO: Return new Schematic(id, width, height, materials, palette, LITEMATICA)
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
