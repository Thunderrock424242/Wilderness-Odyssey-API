package com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure;

import com.thunder.ticktoklib.TickTokHelper;
import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.SpawnBlock.CryoSpawnData;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.SpawnBlock.PlayerSpawnHandler;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.SpawnBlock.WorldSpawnHandler;
import com.thunder.wildernessodysseyapi.WorldGen.structure.NBTStructurePlacer;
import com.thunder.wildernessodysseyapi.WorldGen.util.DeferredTaskScheduler;
import com.thunder.wildernessodysseyapi.WorldGen.worldgen.configurable.StructureConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Generates bunker structures when suitable chunks load.
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID)
public class BunkerStructureGenerator {
    private static final int DEFAULT_MIN_DISTANCE_CHUNKS = 32;
    private static final int INITIAL_PLACEMENT_DELAY_TICKS = 20 * 5;
    private static final int RETRY_DELAY_TICKS = 1;
    private static final int MAX_DEFERRED_ATTEMPTS = TickTokHelper.duration(0, 2, 0, 0);

    private static final NBTStructurePlacer BUNKER_PLACER =
            new NBTStructurePlacer(ModConstants.MOD_ID, "bunker");

    private static final Set<Long> scheduledPositions = new HashSet<>();

    public static void resetDeferredState() {
        scheduledPositions.clear();
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;

        BlockPos chunkPos = chunk.getPos().getWorldPosition();
        BlockPos surfacePos = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, chunkPos);

        StructureSpawnTracker tracker = StructureSpawnTracker.get(level);
        if (tracker.hasSpawnedAt(surfacePos)) return;

        int minDist = StructureConfig.BUNKER_MIN_DISTANCE.get();
        int maxCount = StructureConfig.BUNKER_MAX_COUNT.get();
        int minChunks = minDist <= 0 ? DEFAULT_MIN_DISTANCE_CHUNKS : minDist;

        if (tracker.getSpawnCount() >= maxCount) return;
        if (!tracker.isFarEnough(surfacePos, minChunks)) return;

        long key = surfacePos.asLong();
        if (!scheduledPositions.add(key)) return;

        scheduleDeferredPlacement(level, surfacePos, tracker, minChunks, maxCount, 0, INITIAL_PLACEMENT_DELAY_TICKS);
    }

    private static void scheduleDeferredPlacement(ServerLevel level, BlockPos surfacePos, StructureSpawnTracker tracker,
                                                  int minChunks, int maxCount, int attempt) {
        scheduleDeferredPlacement(level, surfacePos, tracker, minChunks, maxCount, attempt, RETRY_DELAY_TICKS);
    }

    private static void scheduleDeferredPlacement(ServerLevel level, BlockPos surfacePos, StructureSpawnTracker tracker,
                                                  int minChunks, int maxCount, int attempt, int delayTicks) {
        DeferredTaskScheduler.schedule(level, () -> {
            long key = surfacePos.asLong();

            if (tracker.hasSpawnedAt(surfacePos)) {
                scheduledPositions.remove(key);
                return;
            }

            if (tracker.getSpawnCount() >= maxCount || !tracker.isFarEnough(surfacePos, minChunks)) {
                scheduledPositions.remove(key);
                return;
            }

            NBTStructurePlacer.PlacementResult result = BUNKER_PLACER.place(level, surfacePos);
            if (result != null) {
                tracker.addSpawnPos(surfacePos);
                BunkerProtectionHandler.addBunkerBounds(result.bounds());

                List<BlockPos> cryoPositions = result.cryoPositions();
                if (!cryoPositions.isEmpty()) {
                    CryoSpawnData data = CryoSpawnData.get(level);
                    data.replaceAll(cryoPositions);
                    PlayerSpawnHandler.setSpawnBlocks(cryoPositions);
                    WorldSpawnHandler.refreshWorldSpawn(level);
                }

                scheduledPositions.remove(key);
            } else if (attempt < MAX_DEFERRED_ATTEMPTS) {
                scheduleDeferredPlacement(level, surfacePos, tracker, minChunks, maxCount, attempt + 1);
            } else {
                scheduledPositions.remove(key);
            }
        }, Math.max(1, delayTicks));
    }
}
