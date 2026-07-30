package org.loom.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;

import static com.mojang.brigadier.arguments.StringArgumentType.string;

/**
 * Manages schematics: load, list, info, and validate.
 *
 * <pre>
 * loom schematic load &lt;path&gt;
 * loom schematic list
 * loom schematic info &lt;id&gt;
 * loom schematic validate &lt;id&gt;
 * </pre>
 */
public class SchematicCommand extends Command {

    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("loomSchematic")
            .category(CommandCategory.MODULE)
            .description("Loom schematic commands")
            .usageLines(
                "schematic load <path>",
                "schematic list",
                "schematic info <id>"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("loomSchematic")
            .then(literal("load")
                .then(argument("path", string()).executes(c -> {
                    String path = c.getArgument("path", String.class);
                    // TODO: SchematicManager.loadSchematic(path)
                    c.getSource().getEmbed().title("Schematic loaded");
                    return OK;
                })))
            .then(literal("list").executes(c -> {
                // TODO: List all loaded schematics
                c.getSource().getEmbed().title("Schematics");
                return OK;
            }))
            .then(literal("info")
                .then(argument("id", string()).executes(c -> {
                    String id = c.getArgument("id", String.class);
                    // TODO: Show schematic dimensions, block count, format
                    c.getSource().getEmbed().title("Schematic: " + id);
                    return OK;
                })));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        embed.primaryColor();
    }
}
