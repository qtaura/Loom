package org.loom.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;

/**
 * Displays overall Loom status: active job, progress, inventory, position.
 *
 * <pre>
 * loom status
 * </pre>
 */
public class StatusCommand extends Command {

    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("loomStatus")
            .category(CommandCategory.MODULE)
            .description("Show Loom status")
            .usageLines("")
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("loomStatus")
            .executes(c -> {
                // TODO: Show active job, progress %, current position, inventory summary
                // TODO: Show recovery state if recovering
                c.getSource().getEmbed()
                    .title("Loom Status")
                    .addField("Job", "TODO")
                    .addField("Progress", "TODO")
                    .addField("Position", "TODO");
                return OK;
            });
    }

    @Override
    public void defaultEmbed(Embed embed) {
        embed.primaryColor();
    }
}
