package com.thunder.wildernessodysseyapi.SpawnBlock;


import com.thunder.wildernessodysseyapi.block.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

import static com.thunder.wildernessodysseyapi.WildernessOdysseyAPIMainModClass.MOD_ID;

@EventBusSubscriber(modid = MOD_ID)
public class WorldSpawnHandler {

    @SubscribeEvent
    public static void onWorldLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel world) {
            // Locate the spawn block in the world
            BlockPos spawnBlockPos = findWorldSpawnBlock(world);

            if (spawnBlockPos != null) {
                // Set spawn point to the block's position
                world.setDefaultSpawnPos(spawnBlockPos.above(), 0.0F);
            }
        }
    }

    private static BlockPos findWorldSpawnBlock(ServerLevel world) {
        for (ChunkAccess chunk : world.getChunkSource().chunkMap.getChunks()) {
            for (BlockPos pos : BlockPos.betweenClosed(chunk.getPos().getMinBlockX(), world.getMinBuildHeight(), chunk.getPos().getMinBlockZ(),
                    chunk.getPos().getMaxBlockX(), world.getMaxBuildHeight(), chunk.getPos().getMaxBlockZ())) {
                if (world.getBlockState(pos).is(ModBlocks.WORLD_SPAWN_BLOCK.get())) {
                    return pos; // Return the first occurrence of the block
                }
            }
        }
        return null; // Return null if no block is found
    }
}