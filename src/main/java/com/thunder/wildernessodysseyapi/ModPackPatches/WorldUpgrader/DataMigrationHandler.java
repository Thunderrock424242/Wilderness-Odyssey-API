package com.thunder.wildernessodysseyapi.ModPackPatches.WorldUpgrader;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;

public class DataMigrationHandler {
    public static void migrateWorld(MinecraftServer server) {
        System.out.println("[Wilderness Odyssey] Starting automatic world migration...");

        RegistryAccess registry = server.registryAccess();

        handleAutomaticBlockRemapping(registry, server);
        handleAutomaticEntityRemapping(server);
        handleAutomaticItemRemapping(server);

        System.out.println("[Wilderness Odyssey] World migration complete!");
    }

    public static void handleAutomaticBlockRemapping(RegistryAccess registry, MinecraftServer server) {
        Registry<Block> blockRegistry = registry.registryOrThrow(Registry.BLOCK_REGISTRY);

        for (Level level : server.getAllLevels()) {
            for (LevelChunk chunk : level.getChunkSource().chunkMap.getLoadedChunksIterable()) {
                for (BlockPos pos : chunk.getPos().getAllPositions()) {
                    Block block = chunk.getBlockState(pos).getBlock();
                    ResourceLocation blockID = blockRegistry.getKey(block);

                    if (blockID == null || block == Blocks.AIR) {
                        System.out.println("[Wilderness Odyssey] Found missing block at " + pos + ", replacing...");
                        chunk.setBlockState(pos, Blocks.STONE.defaultBlockState(), false);
                        chunk.setUnsaved(true);
                    }
                }
            }
        }
    }

    public static void handleAutomaticEntityRemapping(MinecraftServer server) {
        Registry<?> entityRegistry = server.registryAccess().registryOrThrow(Registry.ENTITY_TYPE_REGISTRY);

        for (Level level : server.getAllLevels()) {
            for (Entity entity : level.getEntities().getAll()) {
                ResourceLocation entityID = entityRegistry.getKey(entity.getType());

                if (entityID == null) {
                    System.out.println("[Wilderness Odyssey] Removing missing entity at " + entity.blockPosition());
                    entity.remove(Entity.RemovalReason.DISCARDED);
                }
            }
        }
    }

    public static void handleAutomaticItemRemapping(MinecraftServer server) {
        server.getPlayerList().getPlayers().forEach(player -> {
            player.getInventory().items.forEach(stack -> {
                if (!stack.isEmpty() && stack.getItem() == null) {
                    System.out.println("[Wilderness Odyssey] Found missing item in " + player.getName().getString() + "'s inventory, replacing...");
                    stack.set(Item.byBlock(Blocks.STONE));
                }
            });
        });
    }
}