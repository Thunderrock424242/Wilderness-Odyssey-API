package com.thunder.wildernessodysseyapi.command;

import com.mojang.brigadier.CommandDispatcher;
import com.thunder.wildernessodysseyapi.async.AsyncTaskManager;
import com.thunder.wildernessodysseyapi.async.AsyncTaskStats;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Reports async task system stats for administrators.
 */
public final class AsyncStatsCommand {

    private AsyncStatsCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("asyncstats")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> execute(ctx.getSource())));
    }

    private static int execute(CommandSourceStack source) {
        AsyncTaskStats stats = AsyncTaskManager.snapshot();
        if (!stats.enabled()) {
            source.sendSuccess(() -> Component.literal(ChatFormatting.YELLOW + "Async system disabled."), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal(ChatFormatting.AQUA + "Async task system"), false);
        source.sendSuccess(() -> Component.literal(" - Workers: " + stats.activeCpuWorkers() + "/" + stats.configuredThreads()), false);
        source.sendSuccess(() -> Component.literal(" - Worker queue: " + stats.queuedWorkerTasks() + "/" + stats.queueCapacity()), false);
        source.sendSuccess(() -> Component.literal(" - Main-thread backlog: " + stats.mainThreadBacklog()), false);
        source.sendSuccess(() -> Component.literal(" - Applied last tick: " + stats.appliedLastTick()), false);
        source.sendSuccess(() -> Component.literal(" - Rejected tasks: " + stats.rejectedTasks()), false);
        return 1;
    }
}
