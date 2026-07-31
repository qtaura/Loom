package org.loom.batch;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Nerv-compatible file discovery and ordering.
 *
 * <p>Scans a directory for supported schematic files (.nbt, .litematic),
 * sorts them by Nerv's algorithm (filename length, then alphabetically),
 * and tracks which files have been started.
 */
public class FileDiscovery {

    /**
     * Discovers and sorts supported files in the given directory.
     *
     * <p>Ordering matches Nerv's {@code getNextMapFile()}:
     * <ol>
     *   <li>Sort by filename length (shorter first)</li>
     *   <li>Then alphabetically within same length</li>
     * </ol>
     *
     * @param folder the directory to scan
     * @return sorted list of supported files, never null
     */
    public static List<File> discover(File folder) {
        File[] files = folder.listFiles();
        if (files == null) return List.of();

        Arrays.sort(files, Comparator
            .comparingInt((File f) -> f.getName().length())
            .thenComparing(File::getName));

        List<File> result = new ArrayList<>();
        for (File file : files) {
            if (!file.isFile()) continue;
            String name = file.getName().toLowerCase();
            if (name.endsWith(".nbt") || name.endsWith(".litematic")) {
                result.add(file);
            }
        }
        return result;
    }

    /**
     * Creates (or ensures) the _finished_maps subdirectory.
     */
    public static File getFinishedFolder(File mapFolder) {
        File finished = new File(mapFolder, "_finished_maps");
        if (!finished.exists()) finished.mkdirs();
        return finished;
    }
}
