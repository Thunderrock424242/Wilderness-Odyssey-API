package com.thunder.wildernessodysseyapi.command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.thunder.wildernessodysseyapi.analytics.AnalyticsSnapshot;
import com.thunder.wildernessodysseyapi.analytics.AnalyticsTracker;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.UUID;

/**
 * Presents the latest analytics snapshot to privileged users by UUID or the server console.
 */
public final class AnalyticsCommand {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private AnalyticsCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("analytics")
                .requires(AnalyticsCommand::hasAccess)
                .executes(ctx -> sendSnapshot(ctx.getSource(), false))
                .then(Commands.literal("json")
                        .executes(ctx -> sendSnapshot(ctx.getSource(), true))));
    }

    private static boolean hasAccess(CommandSourceStack source) {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return true;
        }
        UUID id = player.getUUID();
        return AnalyticsTracker.isAllowed(id);
    }

    private static int sendSnapshot(CommandSourceStack source, boolean asJson) {
        Optional<AnalyticsSnapshot> snapshot = AnalyticsTracker.lastSnapshot();
        if (snapshot.isEmpty()) {
            source.sendFailure(Component.literal("No analytics snapshot is available yet."));
            return 0;
        }
        if (asJson) {
            source.sendSuccess(() -> Component.literal(GSON.toJson(snapshot.get())), false);
            return 1;
        }
        AnalyticsSnapshot snap = snapshot.get();
        String formatted = String.join("\n",
                "Analytics Snapshot",
                "Players: " + snap.playerCount + "/" + snap.maxPlayers,
                "Memory: " + snap.usedMemoryMb + "MB used / " + snap.totalMemoryMb + "MB allocated",
                "Peak Memory: " + snap.peakMemoryMb + "MB (recommended " + snap.recommendedMemoryMb + "MB)",
                "Worst Tick: " + snap.worstTickMillis + "ms",
                "CPU Load: " + String.format("%.1f%%", snap.cpuLoad * 100),
                "Status: " + snap.overloadedReason
        );
        source.sendSuccess(() -> Component.literal(formatted), false);
        return 1;
    }
}
