package com.thunder.wildernessodysseyapi.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.changelog.ChangelogManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class ChangelogCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("changelog")
                .then(Commands.argument("version", StringArgumentType.word())
                        .suggests((context, builder) -> {
                            for (String version : ChangelogManager.getAvailableVersions()) {
                                builder.suggest(version);
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> ChangelogManager.sendChangelog(ctx.getSource(),
                                StringArgumentType.getString(ctx, "version"))))
                .executes(ctx -> ChangelogManager.sendChangelog(ctx.getSource(), ModConstants.VERSION))
        );
    }
}
