package com.thunder.wildernessodysseyapi.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.thunder.wildernessodysseyapi.watersystem.volumetric.VolumetricFluidManager;
import com.thunder.wildernessodysseyapi.watersystem.volumetric.VolumetricFluidManager.SimulatedFluid;
import com.thunder.wildernessodysseyapi.watersystem.volumetric.VolumetricSurfaceMesher;
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
                .then(Commands.literal("tide")
                        .executes(VolumetricFluidCommand::reportTideDebug))
                .then(Commands.literal("clear")
                        .executes(context -> clear(context, SimulatedFluid.WATER))
                        .then(Commands.literal("water")
                                .executes(context -> clear(context, SimulatedFluid.WATER)))
                        .then(Commands.literal("lava")
                                .executes(context -> clear(context, SimulatedFluid.LAVA))))
                .then(Commands.literal("seed")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 64))
                                .executes(context -> seed(context, SimulatedFluid.WATER))
                                .then(Commands.literal("water")
                                        .executes(context -> seed(context, SimulatedFluid.WATER)))
                                .then(Commands.literal("lava")
                                        .executes(context -> seed(context, SimulatedFluid.LAVA))))));
    }

    private static int reportStats(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        BlockPos center = BlockPos.containing(context.getSource().getPosition());
        VolumetricFluidManager.SimulationSnapshot waterSnapshot = VolumetricFluidManager.getSnapshot(level, SimulatedFluid.WATER);
        VolumetricFluidManager.SimulationSnapshot lavaSnapshot = VolumetricFluidManager.getSnapshot(level, SimulatedFluid.LAVA);
        var waterSamples = VolumetricFluidManager.sampleSurface(level, SimulatedFluid.WATER, center, 32, 1024);
        var lavaSamples = VolumetricFluidManager.sampleSurface(level, SimulatedFluid.LAVA, center, 32, 1024);
        VolumetricSurfaceMesher.MeshSnapshot waterMesh = VolumetricSurfaceMesher.buildMesh(waterSamples, 1.50D);
        VolumetricSurfaceMesher.MeshSnapshot lavaMesh = VolumetricSurfaceMesher.buildMesh(lavaSamples, 1.50D);
        context.getSource().sendSuccess(() -> Component.literal(String.format(
                "VolFluid Water: cells=%d, controlled=%d, totalVolume=%.2f, avgSpeed=%.4f, samples=%d, tris=%d, area=%.1f | Lava: cells=%d, controlled=%d, totalVolume=%.2f, avgSpeed=%.4f, samples=%d, tris=%d, area=%.1f | preset=%s, replaceWater=%s, replaceLava=%s",
                waterSnapshot.activeCells(), waterSnapshot.controlledBlocks(), waterSnapshot.totalVolume(), waterSnapshot.averageSpeed(),
                waterMesh.sampleCount(), waterMesh.triangles(), waterMesh.estimatedArea(),
                lavaSnapshot.activeCells(), lavaSnapshot.controlledBlocks(), lavaSnapshot.totalVolume(), lavaSnapshot.averageSpeed(),
                lavaMesh.sampleCount(), lavaMesh.triangles(), lavaMesh.estimatedArea(),
                VolumetricFluidManager.activePreset(),
                VolumetricFluidManager.shouldReplaceVanillaWaterEngine(),
                VolumetricFluidManager.shouldReplaceVanillaLavaEngine()
        )), false);
        return waterSnapshot.activeCells() + lavaSnapshot.activeCells();
    }

    private static int clear(CommandContext<CommandSourceStack> context, SimulatedFluid fluidType) {
        ServerLevel level = context.getSource().getLevel();
        VolumetricFluidManager.clear(level, fluidType);
        context.getSource().sendSuccess(() -> Component.literal("Volumetric " + fluidType.name().toLowerCase() + " grid cleared in this dimension."), true);
        return 1;
    }

    private static int seed(CommandContext<CommandSourceStack> context, SimulatedFluid fluidType) {
        int radius = IntegerArgumentType.getInteger(context, "radius");
        BlockPos center = BlockPos.containing(context.getSource().getPosition());
        ServerLevel level = context.getSource().getLevel();
        int injected = VolumetricFluidManager.seedFromExistingFluid(level, center, radius, fluidType);
        context.getSource().sendSuccess(() -> Component.literal(
                "Seeded volumetric " + fluidType.name().toLowerCase() + " grid from " + injected + " blocks in radius " + radius + "."
        ), true);
        return injected;
    }

    private static int reportTideDebug(CommandContext<CommandSourceStack> context) {
        ServerLevel level = context.getSource().getLevel();
        BlockPos pos = BlockPos.containing(context.getSource().getPosition());
        VolumetricFluidManager.SurfaceSample sample = VolumetricFluidManager.sampleAtPosition(level, pos, SimulatedFluid.WATER);
        context.getSource().sendSuccess(() -> Component.literal(String.format(
                "VolFluid Tide Debug @ %d %d %d -> volume=%.3f, shoreline=%.3f, tideOffset=%.3f, moonFactor=%.3f, surfaceY=%.3f",
                pos.getX(), pos.getY(), pos.getZ(),
                sample.volume(),
                sample.shorelineFactor(),
                sample.tideOffset(),
                sample.moonPhaseFactor(),
                sample.surfaceY()
        )), false);
        return 1;
    }
}
