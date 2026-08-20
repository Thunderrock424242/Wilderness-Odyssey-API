package com.thunder.wildernessodysseyapi.worldupgrade.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.thunder.wildernessodysseyapi.worldupgrade.WorldUpgradeManager;
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
                .then(Commands.literal("retry-failed").executes(context -> {
                    int requeued = WorldUpgradeManager.retryFailed(context.getSource().getServer());
                    context.getSource().sendSuccess(
                            () -> Component.literal("World upgrade failure state cleared; requeued " + requeued
                                    + " known task(s). Outdated chunks remain protected by their stored versions."),
                            true
                    );
                    return Command.SINGLE_SUCCESS;
                }))
                .then(Commands.literal("complete").executes(context -> {
                    boolean completed = WorldUpgradeManager.complete(context.getSource().getServer());
                    if (completed) {
                        context.getSource().sendSuccess(
                                () -> Component.literal("Pending world upgrade marked complete; future outdated chunks remain version-checked."),
                                true
                        );
                        return Command.SINGLE_SUCCESS;
                    }
                    context.getSource().sendFailure(Component.literal(
                            "World upgrade cannot complete while paused, while tasks are queued, while failures remain, "
                                    + "or when no rollout is pending."
                    ));
                    return 0;
                }))
                .then(Commands.literal("status").executes(context -> {
                    WorldUpgradeManager.WorldUpgradeStatus status = WorldUpgradeManager.status(context.getSource().getServer());
                    context.getSource().sendSuccess(() -> Component.literal(String.format(
                            "running=%s targetVersion=%d completedPack=%s pendingPack=%s queued=%d processed=%d migrated=%d failed=%d",
                            status.running(),
                            status.targetVersion(),
                            status.completedPackVersion(),
                            status.pendingPackVersion(),
                            status.queuedChunks(),
                            status.processedChunks(),
                            status.migratedChunks(),
                            status.failedChunks()
                    )), false);
                    return Command.SINGLE_SUCCESS;
                })));
    }
}
