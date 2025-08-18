package com.thunder.wildernessodysseyapi.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.fml.ModList;
import java.awt.GraphicsEnvironment;

import java.util.*;

/**
 * Scans nearby chunks and reports the structures that were generated.
 * Useful for identifying mods that may be over-generating structures
 * and causing messy world generation.
 */
public class WorldGenScanCommand {

    /**
     * Register the command with the dispatcher.
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("worldgenscan")
                        .executes(ctx -> execute(ctx, 1))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 8))
                                .executes(ctx -> execute(ctx, IntegerArgumentType.getInteger(ctx, "radius"))))
        );
    }

    private static int execute(CommandContext<CommandSourceStack> ctx, int radius) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.serverLevel();
        ChunkPos center = player.chunkPosition();

        Registry<Structure> structReg = level.registryAccess()
                .registryOrThrow(Registries.STRUCTURE);

        Map<ResourceLocation, Integer> structureCounts = new HashMap<>();
        Map<ResourceLocation, Integer> featureCounts = new HashMap<>();
        Map<ResourceLocation, Integer> biomeCounts = new HashMap<>();

        ctx.getSource().sendSuccess(
                () -> Component.nullToEmpty(ChatFormatting.GOLD + "Scanning worldgen in radius " + radius + "..."),
                false
        );

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                ChunkPos pos = new ChunkPos(center.x + dx, center.z + dz);
                BlockPos worldPos = pos.getWorldPosition();

                // Structures
                structReg.entrySet().forEach(entry -> {
                    if (level.structureManager().getStructureWithPieceAt(worldPos, entry.getValue()).isValid()) {
                        structureCounts.merge(entry.getKey().location(), 1, Integer::sum);
                    }
                });

                // Biome and features at chunk center
                BlockPos samplePos = worldPos.offset(8, player.getBlockY(), 8);
                Holder<Biome> biomeHolder = level.getBiome(samplePos);
                biomeHolder.unwrapKey().ifPresent(key ->
                        biomeCounts.merge(key.location(), 1, Integer::sum));

                BiomeGenerationSettings gen = biomeHolder.value().getGenerationSettings();
                for (HolderSet<PlacedFeature> step : gen.features()) {
                    for (Holder<PlacedFeature> holder : step) {
                        holder.unwrapKey().ifPresent(key ->
                                featureCounts.merge(key.location(), 1, Integer::sum));
                    }
                }
            }
        }

        if (structureCounts.isEmpty() && featureCounts.isEmpty() && biomeCounts.isEmpty()) {
            ctx.getSource().sendSuccess(
                    () -> Component.nullToEmpty(ChatFormatting.RED + "No structures, features, or biomes found in radius " + radius),
                    false
            );
        } else {
            ctx.getSource().sendSuccess(
                    () -> Component.nullToEmpty(ChatFormatting.AQUA + "Worldgen results for radius " + radius + ":"),
                    false
            );

            if (!structureCounts.isEmpty()) {
                ctx.getSource().sendSuccess(
                        () -> Component.nullToEmpty(ChatFormatting.YELLOW + "Structures:"),
                        false
                );
                structureCounts.entrySet().stream()
                        .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                        .forEach(entry -> {
                            String modName = ModList.get().getModContainerById(entry.getKey().getNamespace())
                                    .map(c -> c.getModInfo().getDisplayName())
                                    .orElse("Unknown Mod");
                            ctx.getSource().sendSuccess(
                                    () -> Component.nullToEmpty(" - " + entry.getKey().getPath() + " [" + modName + "]: " + entry.getValue()),
                                    false
                            );
                        });
            }

            if (!featureCounts.isEmpty()) {
                ctx.getSource().sendSuccess(
                        () -> Component.nullToEmpty(ChatFormatting.YELLOW + "Features:"),
                        false
                );
                featureCounts.entrySet().stream()
                        .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                        .forEach(entry -> {
                            String modName = ModList.get().getModContainerById(entry.getKey().getNamespace())
                                    .map(c -> c.getModInfo().getDisplayName())
                                    .orElse("Unknown Mod");
                            ctx.getSource().sendSuccess(
                                    () -> Component.nullToEmpty(" - " + entry.getKey().getPath() + " [" + modName + "]: " + entry.getValue()),
                                    false
                            );
                        });
            }

            if (!biomeCounts.isEmpty()) {
                ctx.getSource().sendSuccess(
                        () -> Component.nullToEmpty(ChatFormatting.YELLOW + "Biomes:"),
                        false
                );
                biomeCounts.entrySet().stream()
                        .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                        .forEach(entry -> {
                            String modName = ModList.get().getModContainerById(entry.getKey().getNamespace())
                                    .map(c -> c.getModInfo().getDisplayName())
                                    .orElse("Unknown Mod");
                            ctx.getSource().sendSuccess(
                                    () -> Component.nullToEmpty(" - " + entry.getKey().getPath() + " [" + modName + "]: " + entry.getValue()),
                                    false
                            );
                        });
            }

            if (!GraphicsEnvironment.isHeadless()) {
                WorldGenScanViewer.show(structureCounts, featureCounts, biomeCounts);
            }
        }

        return 1;
    }
}

