package com.thunder.wildernessodysseyapi.command;

import com.mojang.brigadier.CommandDispatcher;
import com.thunder.wildernessodysseyapi.chunk.ChunkStreamManager;
import com.thunder.wildernessodysseyapi.chunk.ChunkStreamStats;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.util.Comparator;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Detailed chunk streaming debug overlay for administrators.
 */
public final class DebugChunkCommand {
    private DebugChunkCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("debugchunk")
                .requires(source -> source.hasPermission(2))
                .executes(ctx -> execute(ctx.getSource())));
    }

    private static int execute(CommandSourceStack source) {
        ChunkStreamStats stats = ChunkStreamManager.snapshot();
        if (!stats.enabled()) {
            source.sendSuccess(() -> Component.literal(ChatFormatting.YELLOW + "Chunk streaming disabled."), false);
            return 1;
        }

        source.sendSuccess(() -> Component.literal(ChatFormatting.AQUA + "Chunk streaming debug (tracked: " + stats.trackedChunks() + ")"), false);
        source.sendSuccess(() -> Component.literal("States: " + formatCounts(stats.stateCounts())), false);
        source.sendSuccess(() -> Component.literal("Tickets (" + stats.totalTickets() + "): " + formatCounts(stats.ticketCounts())), false);
        source.sendSuccess(() -> Component.literal("Caches: hot=" + stats.hotCached() + ", warm=" + stats.warmCached()
                + " (hit " + stats.warmCacheHits() + ", miss " + stats.warmCacheMisses()
                + ", rate " + formatPercent(stats.warmCacheHitRate()) + ")"), false);
        source.sendSuccess(() -> Component.literal("I/O: in-flight loads=" + stats.inFlightIo()
                + ", save queue=" + stats.pendingSaves() + " (depth " + stats.ioQueueDepth() + ")"), false);
        return 1;
    }

    private static String formatCounts(Map<?, Integer> counts) {
        if (counts.isEmpty()) {
            return "{}";
        }
        return counts.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(", ", "{", "}"));
    }

    private static String formatPercent(double rate) {
        return String.format("%.1f%%", rate * 100.0D);
    }
}
