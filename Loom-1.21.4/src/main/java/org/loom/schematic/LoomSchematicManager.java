package org.loom.schematic;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Default implementation of {@link SchematicManager}.
 *
 * <p>Maintains an in-memory cache of loaded schematics and delegates
 * file loading to format-specific {@link SchematicLoader} implementations.
 */
public class LoomSchematicManager implements SchematicManager {

    private final Map<String, Schematic> cache;
    private final Map<SchematicFormat, SchematicLoader> loaders;

    public LoomSchematicManager() {
        this.cache = new HashMap<>();
        this.loaders = new HashMap<>();

        // TODO: Register loaders
        // loaders.put(SchematicFormat.LITEMATICA, new LitematicaLoader());
        // loaders.put(SchematicFormat.SPONGE_SCHEMATIC, new SpongeSchematicLoader());
    }

    @Override
    public Schematic loadSchematic(String path) {
        // TODO: Determine format from file extension
        // TODO: Select correct loader
        // TODO: Load and cache the schematic
        // TODO: Return the loaded schematic
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public Optional<Schematic> getSchematic(String id) {
        return Optional.ofNullable(cache.get(id));
    }

    @Override
    public String getBlockAt(String schematicId, int x, int y) {
        Schematic s = cache.get(schematicId);
        return s != null ? s.getBlockAt(x, y) : "minecraft:air";
    }

    @Override
    public Map<Integer, SchematicPalette> getPalette(String schematicId) {
        Schematic s = cache.get(schematicId);
        return s != null ? s.getPalette() : Map.of();
    }

    @Override
    public int getWidth(String schematicId) {
        Schematic s = cache.get(schematicId);
        return s != null ? s.getWidth() : 0;
    }

    @Override
    public int getHeight(String schematicId) {
        Schematic s = cache.get(schematicId);
        return s != null ? s.getHeight() : 0;
    }

    @Override
    public int getTotalBlocks(String schematicId) {
        Schematic s = cache.get(schematicId);
        return s != null ? s.getTotalBlocks() : 0;
    }

    @Override
    public void unloadSchematic(String id) {
        cache.remove(id);
    }

    @Override
    public ValidationResult validate(String id) {
        Schematic s = cache.get(id);
        if (s == null) {
            return ValidationResult.invalid(java.util.List.of("Schematic not loaded: " + id));
        }
        // TODO: Validate dimensions (must be 128x128 for standard map art)
        // TODO: Validate block types are supported carpet colors
        return ValidationResult.valid();
    }
}
