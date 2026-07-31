package org.loom.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;
import org.loom.jobs.Job;
import org.loom.jobs.JobManager;
import org.loom.jobs.JobProgress;
import org.loom.printing.PrinterController;
import org.loom.scheduling.PrintTask;
import org.loom.scheduling.TaskPriority;
import org.loom.scheduling.TaskScheduler;
import org.loom.schematic.SchematicManager;
import org.loom.state.ProgressTracker;
import org.loom.util.AsyncLoomEventBus;

import java.util.Optional;

import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static com.zenith.Globals.CACHE;

/**
 * Main Loom command: build start, pause, resume, cancel, status, jobs, origin.
 *
 * <pre>
 * loom build &lt;schematicId&gt;
 * loom pause
 * loom resume
 * loom cancel
 * loom status
 * loom jobs
 * loom origin here
 * </pre>
 */
public class LoomCommand extends Command {

    private final JobManager jobManager;
    private final TaskScheduler taskScheduler;
    private final PrinterController printerController;
    private final ProgressTracker progressTracker;
    private final SchematicManager schematicManager;
    private final AsyncLoomEventBus eventBus;

    public LoomCommand(JobManager jobManager,
                        TaskScheduler taskScheduler,
                        PrinterController printerController,
                        ProgressTracker progressTracker,
                        SchematicManager schematicManager,
                        AsyncLoomEventBus eventBus) {
        this.jobManager = jobManager;
        this.taskScheduler = taskScheduler;
        this.printerController = printerController;
        this.progressTracker = progressTracker;
        this.schematicManager = schematicManager;
        this.eventBus = eventBus;
    }

    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("loom")
            .category(CommandCategory.MODULE)
            .description("Loom map art printer")
            .usageLines(
                "build <schematicId>",
                "pause",
                "resume",
                "cancel",
                "status",
                "jobs",
                "origin here"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("loom")
            // --- build <schematicId> ---
            .then(literal("build")
                .then(argument("schematicId", string()).executes(c -> {
                    String schematicId = c.getArgument("schematicId", String.class);

                    var schematic = schematicManager.getSchematic(schematicId);
                    if (schematic.isEmpty()) {
                        c.getSource().getEmbed()
                            .title("Schematic not loaded")
                            .description("Use /loomSchematic load <path> first")
                            .errorColor();
                        return ERROR;
                    }

                    int originX = (int) Math.floor(CACHE.getPlayerCache().getX());
                    int originY = (int) Math.floor(CACHE.getPlayerCache().getY());
                    int originZ = (int) Math.floor(CACHE.getPlayerCache().getZ());

                    Job job = jobManager.createJob(schematicId, originX, originY, originZ);
                    if (job == null) {
                        c.getSource().getEmbed()
                            .title("Failed to create job")
                            .errorColor();
                        return ERROR;
                    }

                    PrintTask task = new PrintTask(job, printerController, jobManager, eventBus);
                    taskScheduler.submit(task, TaskPriority.NORMAL);

                    c.getSource().getEmbed()
                        .title("Build started")
                        .description("Job " + job.getId() + " — " + schematicId)
                        .addField("Origin", originX + ", " + originY + ", " + originZ)
                        .primaryColor();
                    return OK;
                })))

            // --- pause ---
            .then(literal("pause").executes(c -> {
                var active = taskScheduler.getActiveTask();
                if (active == null) {
                    c.getSource().getEmbed().title("No active job").errorColor();
                    return ERROR;
                }
                taskScheduler.pause(active);
                c.getSource().getEmbed().title("Build paused").primaryColor();
                return OK;
            }))

            // --- resume ---
            .then(literal("resume").executes(c -> {
                Optional<Job> activeJob = jobManager.getActiveJob();
                if (activeJob.isEmpty()) {
                    c.getSource().getEmbed()
                        .title("No job to resume")
                        .description("Use /loom build <schematicId> to start")
                        .errorColor();
                    return ERROR;
                }
                Job job = activeJob.get();
                PrintTask task = new PrintTask(job, printerController, jobManager, eventBus);
                taskScheduler.submit(task, TaskPriority.NORMAL);
                c.getSource().getEmbed()
                    .title("Build resumed")
                    .description("Job " + job.getId())
                    .primaryColor();
                return OK;
            }))

            // --- cancel ---
            .then(literal("cancel").executes(c -> {
                var active = taskScheduler.getActiveTask();
                if (active == null) {
                    c.getSource().getEmbed().title("No active job").errorColor();
                    return ERROR;
                }
                taskScheduler.cancel(active);
                c.getSource().getEmbed().title("Build cancelled").errorColor();
                return OK;
            }))

            // --- status ---
            .then(literal("status").executes(c -> {
                Embed embed = c.getSource().getEmbed().primaryColor();

                var active = taskScheduler.getActiveTask();
                if (active == null) {
                    embed.title("Loom Status — Idle");
                    return OK;
                }

                embed.title("Loom Status")
                    .addField("Task", active.getTaskId(), false)
                    .addField("Priority", active.getPriority().name(), false);

                int[] pos = printerController.getCurrentPosition();
                int placed = progressTracker.getTotalPlaced();
                int total = progressTracker.getTotalBlocks();
                double pct = progressTracker.getPercentComplete();

                embed.addField("Position", pos[0] + "," + pos[1], false)
                    .addField("Progress", String.format("%d/%d (%.1f%%)", placed, total, pct), false);

                Optional<Job> activeJob = jobManager.getActiveJob();
                activeJob.ifPresent(job ->
                    embed.addField("Job", job.getId(), false));

                return OK;
            }))

            // --- jobs ---
            .then(literal("jobs").executes(c -> {
                Embed embed = c.getSource().getEmbed().title("Jobs").primaryColor();

                var jobs = jobManager.getAllJobs();
                if (jobs.isEmpty()) {
                    embed.description("No jobs");
                    return OK;
                }

                for (Job job : jobs) {
                    JobProgress progress = jobManager.getProgress(job.getId());
                    embed.addField(job.getId(),
                        String.format("%s | %s | %.1f%%",
                            job.getSchematicId(), job.getState(),
                            progress.getPercentComplete()),
                        false);
                }
                return OK;
            }))

            // --- origin here ---
            .then(literal("origin").then(literal("here").executes(c -> {
                int x = (int) Math.floor(CACHE.getPlayerCache().getX());
                int y = (int) Math.floor(CACHE.getPlayerCache().getY());
                int z = (int) Math.floor(CACHE.getPlayerCache().getZ());
                c.getSource().getEmbed()
                    .title("Origin set")
                    .description(String.format("(%d, %d, %d)", x, y, z));
                return OK;
            })));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        embed.primaryColor();
    }
}
