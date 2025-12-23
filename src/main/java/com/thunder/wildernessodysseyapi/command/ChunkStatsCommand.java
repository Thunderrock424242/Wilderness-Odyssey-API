package com.thunder.wildernessodysseyapi.command;

import com.mojang.brigadier.CommandDispatcher;
import com.thunder.wildernessodysseyapi.chunk.ChunkStreamManager;
import com.thunder.wildernessodysseyapi.chunk.ChunkStreamStats;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Reports chunk streaming lifecycle and cache health.
 */
public final class ChunkStatsCommand {
    private ChunkStatsCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("chunkstats")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> execute(ctx.getSource())));
    }

    private static int execute(CommandSourceStack source) {
        ChunkStreamStats stats = ChunkStreamManager.snapshot();
        if (!stats.enabled()) {
            source.sendSuccess(() -> Component.literal(ChatFormatting.YELLOW + "Chunk streaming disabled."), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal(ChatFormatting.AQUA + "Chunk streaming"), false);
        source.sendSuccess(() -> Component.literal(" - Tracked: " + stats.trackedChunks()), false);
        source.sendSuccess(() -> Component.literal(" - Hot cache: " + stats.hotCached()), false);
        source.sendSuccess(() -> Component.literal(" - Warm cache: " + stats.warmCached() + " (hit rate: " + formatPercent(stats.warmCacheHitRate()) + ")"), false);
        source.sendSuccess(() -> Component.literal(" - In-flight I/O: " + stats.inFlightIo()), false);
        source.sendSuccess(() -> Component.literal(" - Pending saves: " + stats.pendingSaves() + " (queue depth: " + stats.ioQueueDepth() + ")"), false);
        source.sendSuccess(() -> Component.literal(" - Tickets: " + stats.totalTickets() + " " + stats.ticketCounts()), false);
        source.sendSuccess(() -> Component.literal(" - States: " + stats.stateCounts()), false);
        return 1;
    }

    private static String formatPercent(double rate) {
        return String.format("%.1f%%", rate * 100.0D);
    }
}
