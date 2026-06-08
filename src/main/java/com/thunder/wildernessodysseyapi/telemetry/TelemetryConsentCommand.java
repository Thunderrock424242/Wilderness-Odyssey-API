package com.thunder.wildernessodysseyapi.telemetry;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.thunder.wildernessodysseyapi.telemetry.TelemetryConsentStore.ConsentDecision;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Commands to set telemetry consent state.
 */
public final class TelemetryConsentCommand {
    private TelemetryConsentCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("telemetryconsent")
                .then(option("accept", ConsentDecision.ACCEPTED))
                .then(option("decline", ConsentDecision.DECLINED))
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayer();
                    if (player == null) {
                        return 0;
                    }
                    ConsentDecision decision = TelemetryConsentStore.get(player.server).getDecision(player.getUUID());
                    player.sendSystemMessage(Component.translatable("command.wildernessodysseyapi.telemetry.status", decision.serialized()));
                    return 1;
                }));
    }

    private static ArgumentBuilder<CommandSourceStack, ?> option(String name, ConsentDecision decision) {
        return Commands.literal(name)
                .executes(context -> {
                    ServerPlayer player = context.getSource().getPlayer();
                    if (player == null) {
                        return 0;
                    }
                    TelemetryConsentStore store = TelemetryConsentStore.get(player.server);
                    store.setDecision(player.getUUID(), decision);
                    player.sendSystemMessage(Component.translatable("command.wildernessodysseyapi.telemetry.set", decision.serialized()));
                    return 1;
                });
    }
}
