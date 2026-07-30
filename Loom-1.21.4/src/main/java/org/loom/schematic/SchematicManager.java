package org.loom.schematic;

import java.util.Optional;

/**
 * Loads, caches, and provides access to map art schematics.
 *
 * <p>Schematics are loaded from the local filesystem and cached in memory.
 * The manager supports multiple formats via {@link SchematicLoader} plugins.
 */
public interface SchematicManager {

    /**
     * Loads a schematic from the given filesystem path.
     *
     * <p>The format is determined by file extension (.litematic, .schem, .nbt).
     * The loaded schematic is cached by its ID for subsequent lookups.
     *
     * @param path the filesystem path to the schematic file
     * @return the loaded schematic
     */
    Schematic loadSchematic(String path);

    /**
     * Returns a previously loaded schematic by its ID.
     *
     * @param id the schematic identifier
     * @return the schematic, or empty if not loaded
     */
    Optional<Schematic> getSchematic(String id);

    /**
     * Returns the material name at a given schematic position.
     *
     * @param schematicId the schematic identifier
     * @param x           schematic-relative X
     * @param y           schematic-relative Y
     * @return the material name
     */
    String getBlockAt(String schematicId, int x, int y);

    /**
     * Returns the block palette for a loaded schematic.
     *
     * @param schematicId the schematic identifier
     * @return the palette map, or empty map if not loaded
     */
    java.util.Map<Integer, SchematicPalette> getPalette(String schematicId);

    /**
     * Returns the width of a loaded schematic.
     */
    int getWidth(String schematicId);

    /**
     * Returns the height of a loaded schematic.
     *
     * <p>For standard map art, this is 128 (matching the width).
     */
    int getHeight(String schematicId);

    /**
     * Returns the total number of non-air blocks in a loaded schematic.
     */
    int getTotalBlocks(String schematicId);

    /**
     * Unloads a schematic from the in-memory cache.
     *
     * @param id the schematic identifier
     */
    void unloadSchematic(String id);

    /**
     * Validates that a schematic is compatible with Loom's requirements.
     *
     * @param id the schematic identifier
     * @return validation result with any errors
     */
    ValidationResult validate(String id);
}
