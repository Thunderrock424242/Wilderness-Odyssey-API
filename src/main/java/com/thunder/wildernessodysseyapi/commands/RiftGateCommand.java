package com.thunder.wildernessodysseyapi.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.thunder.wildernessodysseyapi.tpdim.RiftGate;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Optional;

@EventBusSubscriber
public class RiftGateCommand {

    @SubscribeEvent
    public static void registerCommand(RegisterCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("tptodimension")
                        .requires(source -> source.hasPermission(4))
                        .then(Commands.argument("target_entity", EntityArgument.entity())
                                .then(Commands.argument("dimension", DimensionArgument.dimension())
                                        .executes(ctx -> executeCommand(ctx, null, null)) // No biome, no position
                                        .then(Commands.argument("biome", StringArgumentType.string())
                                                .executes(ctx -> executeCommand(ctx, StringArgumentType.getString(ctx, "biome"), null)) // Biome, no position
                                                .then(Commands.argument("destination", BlockPosArgument.blockPos())
                                                        .executes(ctx -> executeCommand(ctx, StringArgumentType.getString(ctx, "biome"), BlockPosArgument.getLoadedBlockPos(ctx, "destination"))) // Biome + Position
                                                )
                                        )
                                )
                        )
        );
    }

    private static int executeCommand(CommandContext<CommandSourceStack> context, String biomeName, BlockPos targetPos) {
        try {
            CommandSourceStack source = context.getSource();
            Entity targetEntity = EntityArgument.getEntity(context, "target_entity");
            ServerLevel targetDimension = DimensionArgument.getDimension(context, "dimension");

            // If biome is specified, find a position in that biome
            if (biomeName != null) {
                Optional<BlockPos> biomeLocation = findBiomePosition(targetDimension, biomeName);
                if (biomeLocation.isPresent()) {
                    targetPos = biomeLocation.get();
                } else {
                    source.sendFailure(Component.literal("Biome '" + biomeName + "' not found in dimension!"));
                    return 0;
                }
            }

            // Ensure we have a position to teleport to
            if (targetPos == null) {
                targetPos = targetDimension.getSharedSpawnPos();
            }

            RiftGate.execute(targetEntity, targetDimension, targetPos.getX(), targetPos.getY(), targetPos.getZ());
            source.sendSuccess(() -> targetEntity.getName().copy().append(" has been teleported!"), true);
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Failed to execute teleport command: " + e.getMessage()));
        }
        return 1;
    }

    private static Optional<BlockPos> findBiomePosition(ServerLevel world, String biomeName) {
        for (int x = -500; x < 500; x += 16) {
            for (int z = -500; z < 500; z += 16) {
                BlockPos pos = new BlockPos(x, world.getHeight(), z);
                Biome biome = world.getBiomeManager().getBiome(pos).value();
                ResourceKey<Biome> biomeKey = world.registryAccess().registryOrThrow(Biome.BIOME_REGISTRY).getResourceKey(biome).orElse(null);

                if (biomeKey != null && biomeKey.location().toString().contains(biomeName.toLowerCase())) {
                    return Optional.of(pos);
                }
            }
        }
        return Optional.empty();
    }
}