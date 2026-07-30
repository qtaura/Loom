package org.loom.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;

import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.string;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

/**
 * Controls build jobs: start, pause, resume, cancel, and set origin.
 *
 * <pre>
 * loom build start &lt;schematic&gt;
 * loom build pause
 * loom build resume
 * loom build cancel
 * loom origin set &lt;x&gt; &lt;y&gt; &lt;z&gt;
 * loom origin here
 * </pre>
 */
public class BuildCommand extends Command {

    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("loom")
            .category(CommandCategory.MODULE)
            .description("Loom build commands")
            .usageLines(
                "builde start <schematicId>",
                "builde pause",
                "builde resume",
                "builde cancel",
                "origin set <x> <y> <z>",
                "origin here"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("loom")
            .then(literal("builde").then(literal("start")
                .then(argument("schematicId", string()).executes(c -> {
                    String schematicId = c.getArgument("schematicId", String.class);
                    // TODO: JobManager.createJob(schematicId, originX, originY, originZ)
                    // TODO: TaskScheduler.submit(PrintTask)
                    c.getSource().getEmbed().title("Build started for " + schematicId);
                    return OK;
                }))))
            .then(literal("builde").then(literal("pause").executes(c -> {
                // TODO: TaskScheduler.pause(activePrintTask)
                c.getSource().getEmbed().title("Build paused");
                return OK;
            })))
            .then(literal("builde").then(literal("resume").executes(c -> {
                // TODO: TaskScheduler.resume(pausedPrintTask)
                c.getSource().getEmbed().title("Build resumed");
                return OK;
            })))
            .then(literal("builde").then(literal("cancel").executes(c -> {
                // TODO: TaskScheduler.cancel(activePrintTask)
                c.getSource().getEmbed().title("Build cancelled");
                return OK;
            })))
            .then(literal("origin").then(literal("set")
                .then(argument("x", integer()).then(argument("y", integer())
                    .then(argument("z", integer()).executes(c -> {
                        // TODO: Set config build origin
                        c.getSource().getEmbed().title("Origin set");
                        return OK;
                    }))))))
            .then(literal("origin").then(literal("here").executes(c -> {
                // TODO: Read current position and set as origin
                c.getSource().getEmbed().title("Origin set to current position");
                return OK;
            })));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        // TODO: Show job status, progress, current position
        embed.primaryColor();
    }
}
