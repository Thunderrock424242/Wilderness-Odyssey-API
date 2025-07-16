package com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.SpawnBlock;

import com.thunder.wildernessodysseyapi.WorldGen.blocks.WorldSpawnBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID;


/**
 * The type World spawn handler.
 */
@EventBusSubscriber(modid = MOD_ID)
public class WorldSpawnHandler {

    /**
     * On world load.
     *
     * @param event the event
     */
    @SubscribeEvent
    public static void onWorldLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel world) {
            // Locate all spawn blocks in the world
            List<BlockPos> spawnBlockPositions = findAllWorldSpawnBlocks(world);

            if (!spawnBlockPositions.isEmpty()) {
                // Select a random spawn block
                Random random = new Random();
                BlockPos spawnBlockPos = spawnBlockPositions.get(random.nextInt(spawnBlockPositions.size()));

                // Set the world's default spawn position
                world.setDefaultSpawnPos(spawnBlockPos.above(), 0.0F);
            } else {
                // Log a warning or handle cases where no spawn blocks are found
                System.err.println("No World Spawn Blocks found in the world!");
            }
        }
    }

    /**
     * Find all world spawn blocks list.
     *
     * @param world the world
     * @return the list
     */
    static List<BlockPos> findAllWorldSpawnBlocks(ServerLevel world) {
        List<BlockPos> spawnBlocks = new ArrayList<>();

        for (LevelChunk chunk : world.getChunkSource().chunkMap.getChunks()) {
            for (BlockPos pos : BlockPos.betweenClosed(
                    chunk.getPos().getMinBlockX(), world.getMinBuildHeight(),
                    chunk.getPos().getMinBlockZ(), chunk.getPos().getMaxBlockX(),
                    world.getMaxBuildHeight(), chunk.getPos().getMaxBlockZ())) {
                if (world.getBlockState(pos).is(WorldSpawnBlock.WORLD_SPAWN_BLOCK.get())) {
                    spawnBlocks.add(pos);
                }
            }
        }
        return spawnBlocks;
    }

}
