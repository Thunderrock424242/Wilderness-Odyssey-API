package com.thunder.wildernessodysseyapi.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.thunder.wildernessodysseyapi.ModPackPatches.Ocean.tide.TideManager;
import com.thunder.wildernessodysseyapi.ModPackPatches.Ocean.tide.TideManager.TideSnapshot;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * Simple command for surfacing tide information to operators and curious players.
 */
public final class TideInfoCommand {

    private TideInfoCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("tide")
                .requires(source -> source.hasPermission(0))
                .executes(TideInfoCommand::reportTide));
    }

    private static int reportTide(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        TideSnapshot snapshot = TideManager.snapshot(level);
        BlockPos pos = BlockPos.containing(source.getPosition());

        double amplitude = TideManager.getLocalAmplitude(level, pos);
        double tideHeight = snapshot.normalizedHeight() * amplitude;
        double trendPerTick = snapshot.verticalChangePerTick() * amplitude;

        String message = String.format(
                "Tide at %d %d %d: %.2f blocks (%s, Δ=%.4f/tick, cycle %.1f min)",
                pos.getX(), pos.getY(), pos.getZ(),
                tideHeight,
                snapshot.trendDescription(),
                trendPerTick,
                snapshot.cycleTicks() / 20.0D / 60.0D
        );

        source.sendSuccess(() -> Component.literal(message), false);
        return (int) Math.round(tideHeight * 100.0D);
    }
}
