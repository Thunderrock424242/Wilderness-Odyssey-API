package com.thunder.wildernessodysseyapi.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.thunder.wildernessodysseyapi.ModPackPatches.worldupgrade.WorldUpgradeManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Controls the server-side world upgrade queue.
 */
public final class WorldUpgradeCommand {
    private WorldUpgradeCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("worldupgrade")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("start").executes(context -> {
                    WorldUpgradeManager.start(context.getSource().getServer());
                    context.getSource().sendSuccess(() -> Component.literal("World upgrade queue started."), true);
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("pause").executes(context -> {
                    WorldUpgradeManager.pause(context.getSource().getServer());
                    context.getSource().sendSuccess(() -> Component.literal("World upgrade queue paused."), true);
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("status").executes(context -> {
                    WorldUpgradeManager.WorldUpgradeStatus status = WorldUpgradeManager.status(context.getSource().getServer());
                    context.getSource().sendSuccess(() -> Component.literal(String.format(
                            "running=%s targetVersion=%d packVersion=%s queued=%d processed=%d migrated=%d failed=%d",
                            status.running(),
                            status.targetVersion(),
                            status.trackedPackVersion(),
                            status.queuedChunks(),
                            status.processedChunks(),
                            status.migratedChunks(),
                            status.failedChunks()
                    )), false);
                    return Command.SINGLE_SUCCESS;
                })));
    }
}
