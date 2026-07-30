package org.loom.state;

import java.util.List;

/**
 * Handles serialization and deserialization of progress data to disk.
 *
 * <p>Each job gets its own progress file under {@code plugins/config/progress/<jobId>.json}.
 * The file contains a list of {@link ProgressEntry} records.
 */
public class ProgressStore {

    /**
     * Saves progress entries for a job to disk.
     *
     * @param jobId    the job identifier
     * @param entries  the list of placed positions
     */
    public void save(String jobId, List<ProgressEntry> entries) {
        // TODO: Serialize entries to JSON
        // TODO: Write to plugins/config/progress/<jobId>.json
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Loads progress entries for a job from disk.
     *
     * @param jobId the job identifier
     * @return the list of placed positions, or empty if no progress file exists
     */
    public List<ProgressEntry> load(String jobId) {
        // TODO: Read from plugins/config/progress/<jobId>.json
        // TODO: Deserialize into ProgressEntry list
        // TODO: Return empty list if file doesn't exist
        throw new UnsupportedOperationException("Not implemented yet");
    }

    /**
     * Deletes the progress file for a job.
     *
     * @param jobId the job identifier
     */
    public void delete(String jobId) {
        // TODO: Delete plugins/config/progress/<jobId>.json
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
