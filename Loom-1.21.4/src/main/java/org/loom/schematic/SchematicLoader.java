package org.loom.schematic;

/**
 * Loads a schematic from a specific file format.
 *
 * <p>Each format has its own loader implementation.
 * The {@link SchematicManager} selects the correct loader based on file extension.
 */
public interface SchematicLoader {

    /**
     * Returns the format this loader handles.
     */
    SchematicFormat getFormat();

    /**
     * Loads a schematic from the given file path.
     *
     * @param path the filesystem path to the schematic file
     * @return the loaded schematic
     * @throws Exception if the file cannot be read or is malformed
     */
    Schematic load(String path) throws Exception;
}
