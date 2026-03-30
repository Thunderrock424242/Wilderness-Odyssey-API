package com.thunder.wildernessodysseyapi.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.thunder.wildernessodysseyapi.watersystem.volumetric.VolumetricFluidManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/**
 * Operator command surface for the volumetric fluid simulation.
 */
public final class VolumetricFluidCommand {

    private VolumetricFluidCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("volfluid")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("stats")
                        .executes(VolumetricFluidCommand::reportStats))
                .then(Commands.literal("clear")
                        .executes(VolumetricFluidCommand::clear))
                .then(Commands.literal("seed")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 64))
                                .executes(VolumetricFluidCommand::seed))));
    }

    private static int reportStats(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        VolumetricFluidManager.SimulationSnapshot snapshot = VolumetricFluidManager.getSnapshot(level);
        context.getSource().sendSuccess(() -> Component.literal(String.format(
                "VolFluid: cells=%d, controlled=%d, totalVolume=%.2f, avgSpeed=%.4f",
                snapshot.activeCells(), snapshot.controlledBlocks(), snapshot.totalVolume(), snapshot.averageSpeed()
        )), false);
        return snapshot.activeCells();
    }

    private static int clear(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        VolumetricFluidManager.clear(level);
        context.getSource().sendSuccess(() -> Component.literal("Volumetric fluid grid cleared in this dimension."), true);
        return 1;
    }

    private static int seed(CommandContext<CommandSourceStack> context) {
        int radius = IntegerArgumentType.getInteger(context, "radius");
        BlockPos center = BlockPos.containing(context.getSource().getPosition());
        ServerLevel level = context.getSource().getLevel();
        int injected = VolumetricFluidManager.seedFromExistingWater(level, center, radius);
        context.getSource().sendSuccess(() -> Component.literal(
                "Seeded volumetric grid from " + injected + " water blocks in radius " + radius + "."
        ), true);
        return injected;
    }
}
