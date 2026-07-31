package org.loom.jobs;

import com.google.gson.Gson;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles JSON persistence of job metadata to disk.
 *
 * <p>Each job is stored at {@code plugins/config/jobs/<jobId>.json}.
 * Format:
 * <pre>
 * {
 *   "id": "abc123",
 *   "schematicId": "map1",
 *   "originX": 1000,
 *   "originY": 64,
 *   "originZ": 2000,
 *   "createdAt": 1700000000000,
 *   "state": "ACTIVE"
 * }
 * </pre>
 */
public class JobStore {

    private static final Path JOBS_DIR = Path.of("plugins", "config", "jobs");
    private static final Gson GSON = new Gson();

    /**
     * Persists a job to disk.
     */
    public void save(Job job) {
        ensureDirectory();
        File file = resolveFile(job.getId());

        try (FileWriter writer = new FileWriter(file)) {
            GSON.toJson(job, writer);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save job: " + job.getId(), e);
        }
    }

    /**
     * Loads a single job from disk.
     *
     * @return the job, or null if file doesn't exist or is corrupt
     */
    public Job load(String jobId) {
        File file = resolveFile(jobId);
        if (!file.exists()) return null;

        try (FileReader reader = new FileReader(file)) {
            return GSON.fromJson(reader, Job.class);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load job: " + jobId, e);
        }
    }

    /**
     * Loads all jobs from the jobs directory.
     */
    public List<Job> loadAll() {
        List<Job> result = new ArrayList<>();
        File dir = JOBS_DIR.toFile();
        if (!dir.exists()) return result;

        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return result;

        for (File file : files) {
            try (FileReader reader = new FileReader(file)) {
                Job job = GSON.fromJson(reader, Job.class);
                if (job != null) result.add(job);
            } catch (IOException ignored) {
                // Skip corrupt files
            }
        }
        return result;
    }

    /**
     * Deletes a job file from disk.
     */
    public void delete(String jobId) {
        File file = resolveFile(jobId);
        if (file.exists()) file.delete();
    }

    private static File resolveFile(String jobId) {
        return JOBS_DIR.resolve(jobId + ".json").toFile();
    }

    private static void ensureDirectory() {
        File dir = JOBS_DIR.toFile();
        if (!dir.exists()) dir.mkdirs();
    }
}
