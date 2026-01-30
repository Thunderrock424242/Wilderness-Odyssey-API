package com.thunder.wildernessodysseyapi.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.thunder.wildernessodysseyapi.ocean.OceanWaveManager;
import com.thunder.wildernessodysseyapi.ocean.OceanWaveManager.WaveSnapshot;
import com.thunder.wildernessodysseyapi.tide.TideManager;
import com.thunder.wildernessodysseyapi.tide.TideManager.TideSnapshot;
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
        WaveSnapshot waveSnapshot = OceanWaveManager.snapshot(level);
        BlockPos pos = BlockPos.containing(source.getPosition());

        double amplitude = TideManager.getLocalAmplitude(level, pos);
        double tideHeight = snapshot.normalizedHeight() * amplitude;
        double trendPerTick = snapshot.verticalChangePerTick() * amplitude;
        double waveAmplitude = OceanWaveManager.getLocalWaveAmplitude(level, pos);
        double waveHeight = waveSnapshot.normalizedHeight() * waveAmplitude;
        double waveTrendPerTick = waveSnapshot.verticalChangePerTick() * waveAmplitude;
        double combinedHeight = tideHeight + waveHeight;

        String message;
        if (waveAmplitude > 0.0D) {
            message = String.format(
                    "Tide at %d %d %d: %.2f blocks (%s, Δ=%.4f/tick, cycle %.1f min). " +
                            "Waves: %.2f blocks (%s, Δ=%.4f/tick, period %.1f s). Combined: %.2f blocks.",
                    pos.getX(), pos.getY(), pos.getZ(),
                    tideHeight,
                    snapshot.trendDescription(),
                    trendPerTick,
                    snapshot.cycleTicks() / 20.0D / 60.0D,
                    waveHeight,
                    waveSnapshot.trendDescription(),
                    waveTrendPerTick,
                    waveSnapshot.cycleTicks() / 20.0D,
                    combinedHeight
            );
        } else {
            message = String.format(
                    "Tide at %d %d %d: %.2f blocks (%s, Δ=%.4f/tick, cycle %.1f min).",
                    pos.getX(), pos.getY(), pos.getZ(),
                    tideHeight,
                    snapshot.trendDescription(),
                    trendPerTick,
                    snapshot.cycleTicks() / 20.0D / 60.0D
            );
        }

        source.sendSuccess(() -> Component.literal(message), false);
        return (int) Math.round(combinedHeight * 100.0D);
    }
}
