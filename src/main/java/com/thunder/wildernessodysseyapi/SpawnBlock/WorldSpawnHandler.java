package com.thunder.wildernessodysseyapi.SpawnBlock;

import com.thunder.wildernessodysseyapi.blocks.WorldSpawnBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.ArrayList;
import java.util.List;

import static com.thunder.wildernessodysseyapi.MainModClass.WildernessOdysseyAPIMainModClass.MOD_ID;

@EventBusSubscriber(modid = MOD_ID)
public class WorldSpawnHandler {

    @SubscribeEvent
    public static void onWorldLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel world) {
            // Locate the spawn block in the world
            BlockPos spawnBlockPos = (BlockPos) findAllWorldSpawnBlocks(world);

            // Set spawn point to the block's position
            world.setDefaultSpawnPos(spawnBlockPos.above(), 0.0F);
        }
    }

    static List<BlockPos> findAllWorldSpawnBlocks(ServerLevel world) {
        List<BlockPos> spawnBlocks = new ArrayList<>();

        for (int x = world.getMinBuildHeight(); x < world.getMaxBuildHeight(); x += 16) {
            for (int z = world.getMinBuildHeight(); z < world.getMaxBuildHeight(); z += 16) {
                LevelChunk chunk = world.getChunkSource().getChunk(x >> 4, z >> 4, false);
                if (chunk != null) {
                    for (BlockPos pos : BlockPos.betweenClosed(chunk.getPos().getMinBlockX(), world.getMinBuildHeight(),
                            chunk.getPos().getMinBlockZ(), chunk.getPos().getMaxBlockX(), world.getMaxBuildHeight(), chunk.getPos().getMaxBlockZ())) {
                        if (world.getBlockState(pos).is(WorldSpawnBlock.WORLD_SPAWN_BLOCK.get())) {
                            spawnBlocks.add(pos);
                        }
                    }
                }
            }
        }
        return spawnBlocks;
    }

}
