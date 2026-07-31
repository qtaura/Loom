package org.loom.state;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles JSON serialization and deserialization of progress data to disk.
 *
 * <p>Each job gets its own file at {@code plugins/config/progress/<jobId>.json}.
 * Format:
 * <pre>
 * {
 *   "jobId": "map1",
 *   "width": 128,
 *   "height": 128,
 *   "totalBlocks": 16384,
 *   "entries": [
 *     {"x": 0, "y": 0, "material": "minecraft:white_carpet"},
 *     ...
 *   ]
 * }
 * </pre>
 */
public class ProgressStore {

    private static final Path PROGRESS_DIR = Path.of("plugins", "config", "progress");
    private static final Gson GSON = new Gson();

    /**
     * Serializable container matching the JSON file format.
     */
    @SuppressWarnings("unused")
    private static class ProgressFile {
        String jobId;
        int width;
        int height;
        int totalBlocks;
        List<ProgressEntry> entries;

        ProgressFile(String jobId, int width, int height, int totalBlocks, List<ProgressEntry> entries) {
            this.jobId = jobId;
            this.width = width;
            this.height = height;
            this.totalBlocks = totalBlocks;
            this.entries = entries;
        }
    }

    /**
     * Saves progress entries and job metadata to disk.
     */
    public void save(String jobId, int width, int height, int totalBlocks,
                      List<ProgressEntry> entries) {
        ensureDirectory();
        File file = resolveFile(jobId);

        ProgressFile data = new ProgressFile(jobId, width, height, totalBlocks, entries);

        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(data, writer);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save progress for job: " + jobId, e);
        }
    }

    /**
     * Loads progress data from disk.
     *
     * @param jobId the job identifier
     * @return the loaded progress data, or null if no file exists
     */
    public ProgressFileData load(String jobId) {
        File file = resolveFile(jobId);
        if (!file.exists()) {
            return null;
        }

        try (FileReader reader = new FileReader(file)) {
            ProgressFile data = GSON.fromJson(reader, ProgressFile.class);
            if (data == null || data.entries == null) {
                return null;
            }
            return new ProgressFileData(
                data.width, data.height, data.totalBlocks, data.entries);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load progress for job: " + jobId, e);
        }
    }

    /**
     * Deletes the progress file for a job.
     */
    public void delete(String jobId) {
        File file = resolveFile(jobId);
        if (file.exists()) {
            file.delete();
        }
    }

    private static File resolveFile(String jobId) {
        return PROGRESS_DIR.resolve(jobId + ".json").toFile();
    }

    private static void ensureDirectory() {
        File dir = PROGRESS_DIR.toFile();
        if (!dir.exists()) {
            dir.mkdirs();
        }
    }

    /**
     * Deserialized progress data returned by {@link #load}.
     */
    public record ProgressFileData(int width, int height, int totalBlocks,
                                    List<ProgressEntry> entries) {}
}
