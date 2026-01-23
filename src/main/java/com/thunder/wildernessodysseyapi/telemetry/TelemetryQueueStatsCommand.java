package com.thunder.wildernessodysseyapi.telemetry;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

import java.time.Instant;
import java.util.Optional;

public final class TelemetryQueueStatsCommand {
    private TelemetryQueueStatsCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("telemetrystats")
                .requires(source -> source.hasPermission(2))
                .executes(context -> {
                    CommandSourceStack source = context.getSource();
                    TelemetryQueue.TelemetryQueueStats stats = TelemetryQueue.get(source.getServer()).stats();
                    Optional<Instant> lastSuccess = stats.lastSuccess();
                    source.sendSuccess(() -> Component.translatable(
                            "command.wildernessodysseyapi.telemetry.stats",
                            stats.pending(),
                            stats.failed(),
                            lastSuccess.map(Instant::toString).orElse("never")
                    ), false);
                    return 1;
                }));
    }
}
