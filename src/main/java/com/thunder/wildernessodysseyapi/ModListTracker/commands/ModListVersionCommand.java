package com.thunder.wildernessodysseyapi.ModListTracker.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.thunder.wildernessodysseyapi.ModListTracker.ModTracker;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Command to display the mod list stored for a specific pack version.
 */
public class ModListVersionCommand {
    /** Registers the /modlistversion command. */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("modlistversion")
                .then(Commands.argument("version", StringArgumentType.string())
                        .executes(ctx -> {
                            showVersion(ctx.getSource(), StringArgumentType.getString(ctx, "version"));
                            return Command.SINGLE_SUCCESS;
                        })));
    }

    private static void showVersion(CommandSourceStack source, String version) {
        Map<String, String> mods = ModTracker.getModsForVersion(version);
        if (mods.isEmpty()) {
            source.sendFailure(Component.literal("No data for version " + version));
            return;
        }
        String list = mods.entrySet().stream()
                .map(e -> e.getKey() + " v" + e.getValue())
                .collect(Collectors.joining(", "));
        source.sendSuccess(() -> Component.literal("\u00A7a[Mods in " + version + "]: " + list), false);
    }
}
