package org.loom.schematic;

import org.loom.log.LoomLogger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Default implementation of {@link SchematicManager}.
 *
 * <p>Maintains an in-memory cache of loaded schematics and delegates
 * file loading to format-specific {@link SchematicLoader} implementations.
 * The loader is selected based on file extension.
 */
public class LoomSchematicManager implements SchematicManager {

    private static final String TAG = "Schematic";

    private final Map<String, Schematic> cache;
    private final Map<SchematicFormat, SchematicLoader> loaders;
    private final LoomLogger logger;
    private final Set<String> ignoredBlocks;

    public LoomSchematicManager(LoomLogger logger, List<String> ignoredBlocks) {
        this.cache = new HashMap<>();
        this.loaders = new HashMap<>();
        this.logger = logger;
        this.ignoredBlocks = new HashSet<>(ignoredBlocks);

        loaders.put(SchematicFormat.LITEMATICA, new LitematicaLoader());
        loaders.put(SchematicFormat.SPONGE_SCHEMATIC, new SpongeSchematicLoader());
        loaders.put(SchematicFormat.NBT_STRUCTURE, new VanillaStructureLoader());
    }

    @Override
    public Schematic loadSchematic(String path) {
        String id = deriveId(path);

        Schematic cached = cache.get(id);
        if (cached != null) {
            logger.info(TAG, "Schematic already loaded: %s", id);
            return cached;
        }

        SchematicFormat format = detectFormat(path);
        SchematicLoader loader = loaders.get(format);

        if (loader == null) {
            String msg = "No loader for format: " + format;
            logger.error(TAG, msg, null);
            throw new UnsupportedOperationException(msg);
        }

        try {
            Schematic schematic = loader.load(path);
            applyIgnoredBlockFilter(schematic);
            cache.put(id, schematic);
            logger.info(TAG, "Loaded '%s': %dx%d, %d blocks, %s",
                id, schematic.getWidth(), schematic.getHeight(),
                schematic.getTotalBlocks(), format);
            return schematic;
        } catch (IOException e) {
            logger.error(TAG, "Failed to load schematic: " + path, e);
            throw new RuntimeException("Failed to load schematic: " + path, e);
        } catch (Exception e) {
            logger.error(TAG, "Failed to load schematic: " + path, e);
            throw new RuntimeException("Failed to load schematic: " + path, e);
        }
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
        logger.info(TAG, "Unloaded: %s", id);
    }

    @Override
    public ValidationResult validate(String id) {
        Schematic s = cache.get(id);
        if (s == null) {
            return ValidationResult.invalid(List.of("Schematic not loaded: " + id));
        }

        List<String> errors = new ArrayList<>();

        if (s.getWidth() < 1 || s.getWidth() > 256) {
            errors.add("Width " + s.getWidth() + " outside valid range [1, 256]");
        }
        if (s.getHeight() < 1 || s.getHeight() > 256) {
            errors.add("Height " + s.getHeight() + " outside valid range [1, 256]");
        }
        if (s.getPalette().isEmpty()) {
            errors.add("Palette is empty");
        }
        if (s.getTotalBlocks() == 0) {
            errors.add("No non-air blocks");
        }

        return errors.isEmpty()
            ? ValidationResult.valid()
            : ValidationResult.invalid(errors);
    }

    private static String deriveId(String path) {
        String name = Path.of(path).getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /**
     * Matches Nerv's ignored-blocks behavior: removes palette entries
     * for ignored block types and replaces them with air in the grid.
     * This means ignored blocks are neither placed nor flagged as errors.
     */
    private void applyIgnoredBlockFilter(Schematic schematic) {
        if (ignoredBlocks.isEmpty()) return;

        int replaced = 0;
        for (int y = 0; y < schematic.getHeight(); y++) {
            for (int x = 0; x < schematic.getWidth(); x++) {
                String block = schematic.getBlockAt(x, y);
                if (ignoredBlocks.contains(block)) {
                    schematic.replaceBlock(x, y, "minecraft:air");
                    replaced++;
                }
            }
        }

        if (replaced > 0) {
            logger.info(TAG, "Filtered %d blocks matching ignore list", replaced);
        }
    }

    private static SchematicFormat detectFormat(String path) {
        String lower = path.toLowerCase();
        if (lower.endsWith(".litematic")) return SchematicFormat.LITEMATICA;
        if (lower.endsWith(".schem") || lower.endsWith(".schematic")) return SchematicFormat.SPONGE_SCHEMATIC;
        if (lower.endsWith(".nbt")) return SchematicFormat.NBT_STRUCTURE;
        throw new IllegalArgumentException("Unknown format: " + path);
    }
}
