package com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.SpawnBlock;

import com.thunder.wildernessodysseyapi.WorldGen.blocks.CryoTubeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import com.thunder.wildernessodysseyapi.WorldGen.worldgen.structures.MeteorImpactData;
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
                BlockPos meteorPos = MeteorImpactData.get(world).getImpactPos();
                BlockPos spawnBlockPos;
                if (meteorPos != null) {
                    spawnBlockPos = spawnBlockPositions.stream()
                            .min((a, b) -> Double.compare(a.distSqr(meteorPos), b.distSqr(meteorPos)))
                            .orElse(spawnBlockPositions.get(0));
                } else {
                    Random random = new Random();
                    spawnBlockPos = spawnBlockPositions.get(random.nextInt(spawnBlockPositions.size()));
                }

                // Set the world's default spawn position
                world.setDefaultSpawnPos(spawnBlockPos.above(), 0.0F);
            } else {
                // Log a warning or handle cases where no spawn blocks are found
                System.err.println("No Cryo Tube Blocks found in the world!");
            }
        }
    }

    /**
     * Find all world spawn blocks list.
     *
     * @param world the world
     * @return the list
     */
    public static List<BlockPos> findAllWorldSpawnBlocks(ServerLevel world) {
        List<BlockPos> spawnBlocks = new ArrayList<>();

        try {
            // ChunkMap#getChunks became protected; use reflection to access it
            var method = world.getChunkSource().chunkMap.getClass().getDeclaredMethod("getChunks");
            method.setAccessible(true);
            Iterable<?> holders = (Iterable<?>) method.invoke(world.getChunkSource().chunkMap);
            for (Object holderObj : holders) {
                // Each holder represents a chunk; attempt to extract a loaded LevelChunk
                var holderClass = holderObj.getClass();
                var getTicking = holderClass.getMethod("getTickingChunk");
                LevelChunk chunk = (LevelChunk) getTicking.invoke(holderObj);
                if (chunk != null) {
                    for (BlockPos pos : BlockPos.betweenClosed(chunk.getPos().getMinBlockX(), world.getMinBuildHeight(),
                            chunk.getPos().getMinBlockZ(), chunk.getPos().getMaxBlockX(), world.getMaxBuildHeight(), chunk.getPos().getMaxBlockZ())) {
                        if (world.getBlockState(pos).is(CryoTubeBlock.CRYO_TUBE_BLOCK.get())) {
                            spawnBlocks.add(pos);
                        }
                    }
                }
            }
        } catch (ReflectiveOperationException e) {
            // If reflection fails, fall back to empty result
            e.printStackTrace();
        }
        return spawnBlocks;
    }

}
