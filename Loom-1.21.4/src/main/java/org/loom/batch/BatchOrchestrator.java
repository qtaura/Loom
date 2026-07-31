package org.loom.batch;

import org.loom.jobs.Job;
import org.loom.jobs.JobManager;
import org.loom.log.LoomLogger;
import org.loom.printing.PrinterController;
import org.loom.scheduling.PrintTask;
import org.loom.scheduling.TaskPriority;
import org.loom.scheduling.TaskScheduler;
import org.loom.schematic.SchematicManager;
import org.loom.util.AsyncLoomEventBus;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Orchestrates automatic batch printing of all schematics in a directory.
 *
 * <p>Workflow matching Nerv's behavior:
 * <ol>
 *   <li>Discover files in map folder, sort by length then alphabetically</li>
 *   <li>Load each as a schematic, create a job, submit a PrintTask</li>
 *   <li>When a print completes: move file to _finished_maps/</li>
 *   <li>Automatically advance to the next file</li>
 *   <li>Stop when no files remain</li>
 * </ol>
 */
public class BatchOrchestrator {

    private static final String TAG = "Batch";

    private final SchematicManager schematicManager;
    private final JobManager jobManager;
    private final TaskScheduler taskScheduler;
    private final PrinterController printerController;
    private final AsyncLoomEventBus eventBus;
    private final LoomLogger logger;
    private final String mapFolderPath;
    private final boolean moveToFinished;

    private List<File> files;
    private int fileIndex;

    public BatchOrchestrator(SchematicManager schematicManager,
                              JobManager jobManager,
                              TaskScheduler taskScheduler,
                              PrinterController printerController,
                              AsyncLoomEventBus eventBus,
                              LoomLogger logger,
                              String mapFolderPath,
                              boolean moveToFinished) {
        this.schematicManager = schematicManager;
        this.jobManager = jobManager;
        this.taskScheduler = taskScheduler;
        this.printerController = printerController;
        this.eventBus = eventBus;
        this.logger = logger;
        this.mapFolderPath = mapFolderPath;
        this.moveToFinished = moveToFinished;
        this.files = List.of();
        this.fileIndex = 0;
    }

    /**
     * Starts the batch workflow by discovering files and submitting
     * the first PrintTask.
     */
    public void start() {
        File folder = new File(mapFolderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            logger.warn(TAG, "Map folder not found: %s", mapFolderPath);
            return;
        }

        files = FileDiscovery.discover(folder);
        if (files.isEmpty()) {
            logger.info(TAG, "No supported files found in %s", mapFolderPath);
            return;
        }

        logger.info(TAG, "Found %d files in %s", files.size(), mapFolderPath);
        submitNextFile();
    }

    /**
     * Called when a PrintTask completes. Moves the completed file
     * and starts the next one.
     */
    public void onPrintComplete(String jobId) {
        if (fileIndex == 0 && files.isEmpty()) return;

        // Move completed file to _finished_maps/
        if (fileIndex > 0 && fileIndex <= files.size()) {
            File completedFile = files.get(fileIndex - 1);
            if (moveToFinished) {
                moveToFinishedFolder(completedFile);
            }
        }

        // Submit next file
        if (fileIndex < files.size()) {
            submitNextFile();
        } else {
            logger.info(TAG, "Batch complete — all %d files processed", files.size());
        }
    }

    // ==================================================================
    // Internal
    // ==================================================================

    private void submitNextFile() {
        if (fileIndex >= files.size()) return;

        File file = files.get(fileIndex);
        fileIndex++;

        logger.info(TAG, "Loading %s (%d/%d)", file.getName(), fileIndex, files.size());

        try {
            var schematic = schematicManager.loadSchematic(file.getAbsolutePath());
            int originX = 0; // Will be set by PrinterController from config
            int originY = 0;
            int originZ = 0;

            Job job = jobManager.createJob(schematic.getId(), originX, originY, originZ);
            if (job == null) {
                logger.warn(TAG, "Failed to create job for %s, skipping", file.getName());
                submitNextFile();
                return;
            }

            PrintTask task = new PrintTask(job, printerController, jobManager, eventBus, this);
            taskScheduler.submit(task, TaskPriority.NORMAL);
        } catch (Exception e) {
            logger.error(TAG, "Failed to load " + file.getName(), e);
            submitNextFile();
        }
    }

    private void moveToFinishedFolder(File file) {
        File mapFolder = new File(mapFolderPath);
        File finished = FileDiscovery.getFinishedFolder(mapFolder);
        File dest = new File(finished, file.getName());

        try {
            // Handle collisions: append (1), (2), etc.
            File uniqueDest = dest;
            int collision = 1;
            while (uniqueDest.exists()) {
                String name = file.getName();
                int dot = name.lastIndexOf('.');
                String base = dot > 0 ? name.substring(0, dot) : name;
                String ext = dot > 0 ? name.substring(dot) : "";
                uniqueDest = new File(finished, base + "(" + collision + ")" + ext);
                collision++;
            }
            Files.move(file.toPath(), uniqueDest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            logger.info(TAG, "Moved %s to _finished_maps/", file.getName());
        } catch (IOException e) {
            logger.error(TAG, "Failed to move " + file.getName(), e);
        }
    }
}
