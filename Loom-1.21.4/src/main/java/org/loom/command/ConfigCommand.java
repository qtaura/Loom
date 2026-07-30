package org.loom.command;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.zenith.command.api.Command;
import com.zenith.command.api.CommandCategory;
import com.zenith.command.api.CommandContext;
import com.zenith.command.api.CommandUsage;
import com.zenith.discord.Embed;

import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.zenith.command.brigadier.ToggleArgumentType.toggle;

/**
 * Runtime configuration management: tweak settings without restart.
 *
 * <pre>
 * loom config speed &lt;ticks&gt;
 * loom config restock threshold &lt;count&gt;
 * loom config storage set &lt;x&gt; &lt;y&gt; &lt;z&gt;
 * loom config verify &lt;on|off&gt;
 * loom config debug &lt;on|off&gt;
 * </pre>
 */
public class ConfigCommand extends Command {

    @Override
    public CommandUsage commandUsage() {
        return CommandUsage.builder()
            .name("loomConfig")
            .category(CommandCategory.MODULE)
            .description("Loom runtime configuration")
            .usageLines(
                "speed <ticks>",
                "restock threshold <count>",
                "storage set <x> <y> <z>",
                "verify <on|off>",
                "debug <on|off>"
            )
            .build();
    }

    @Override
    public LiteralArgumentBuilder<CommandContext> register() {
        return command("loomConfig")
            .then(literal("speed").then(argument("ticks", integer(0, 100)).executes(c -> {
                // TODO: LoomConfig.placementDelayTicks = getInteger(c, "ticks")
                c.getSource().getEmbed().title("Placement speed set");
                return OK;
            })))
            .then(literal("restock").then(literal("threshold")
                .then(argument("count", integer(1, 512)).executes(c -> {
                    // TODO: LoomConfig.restockThreshold = getInteger(c, "count")
                    c.getSource().getEmbed().title("Restock threshold set");
                    return OK;
                }))))
            .then(literal("storage").then(literal("set")
                .then(argument("x", integer()).then(argument("y", integer())
                    .then(argument("z", integer()).executes(c -> {
                        // TODO: LoomConfig.storageX/Y/Z = getInteger(...)
                        c.getSource().getEmbed().title("Storage position set");
                        return OK;
                    }))))))
            .then(literal("verify").then(argument("toggle", toggle()).executes(c -> {
                // TODO: LoomConfig.verifyPlacements = getToggle(c, "toggle")
                c.getSource().getEmbed().title("Placement verification " + "TODO");
                return OK;
            })))
            .then(literal("debug").then(argument("toggle", toggle()).executes(c -> {
                // TODO: LoomConfig.debugEnabled = getToggle(c, "toggle")
                c.getSource().getEmbed().title("Debug mode " + "TODO");
                return OK;
            })));
    }

    @Override
    public void defaultEmbed(Embed embed) {
        // TODO: Show current config values
        embed.primaryColor();
    }
}
