package com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.Worldedit.WorldEditStructurePlacer;
import com.thunder.wildernessodysseyapi.WorldGen.worldgen.configurable.StructureConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.fml.ModList;

/**
 * Generates bunker structures when suitable chunks load.
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID)
public class BunkerStructureGenerator {
    private static final int DEFAULT_MIN_DISTANCE_CHUNKS = 12000;

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!ModList.get().isLoaded("worldedit")) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;

        BlockPos chunkPos = chunk.getPos().getWorldPosition();
        BlockPos surfacePos = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, chunkPos);

        StructureSpawnTracker tracker = StructureSpawnTracker.get(level);

        // Skip if a bunker has already spawned at this position
        if (tracker.hasSpawnedAt(surfacePos)) return;

        int minDist = StructureConfig.BUNKER_MIN_DISTANCE.get();
        int maxCount = StructureConfig.BUNKER_MAX_COUNT.get();
        int minChunks = minDist <= 0 ? DEFAULT_MIN_DISTANCE_CHUNKS : minDist;

        if (tracker.getSpawnCount() >= maxCount) return;
        if (!tracker.isFarEnough(surfacePos, minChunks)) return;

        if (!WorldEditStructurePlacer.isWorldEditReady()) {
            scheduleDeferredPlacement(level, surfacePos, tracker, minChunks, maxCount, 0);
            return;
        }

        // Load from data packs if available, else fall back to bundled schematic
        WorldEditStructurePlacer placer = new WorldEditStructurePlacer(ModConstants.MOD_ID, "bunker.schem");
        AABB bounds = placer.placeStructure(level, surfacePos);
        if (bounds != null) {
            tracker.addSpawnPos(surfacePos);
            BunkerProtectionHandler.addBunkerBounds(bounds);
        }
    }

    private static void scheduleDeferredPlacement(ServerLevel level, BlockPos surfacePos, StructureSpawnTracker tracker,
                                                  int minChunks, int maxCount, int attempt) {
        level.getServer().execute(() -> {
            if (tracker.hasSpawnedAt(surfacePos)) return;
            if (tracker.getSpawnCount() >= maxCount) return;
            if (!tracker.isFarEnough(surfacePos, minChunks)) return;

            if (!WorldEditStructurePlacer.isWorldEditReady()) {
                if (attempt == 40) {
                    ModConstants.LOGGER.warn("WorldEdit still initializing; bunker placement at {} delayed", surfacePos);
                }
                if (attempt < 100) {
                    scheduleDeferredPlacement(level, surfacePos, tracker, minChunks, maxCount, attempt + 1);
                }
                return;
            }

            WorldEditStructurePlacer placer = new WorldEditStructurePlacer(ModConstants.MOD_ID, "bunker.schem");
            AABB bounds = placer.placeStructure(level, surfacePos);
            if (bounds != null) {
                tracker.addSpawnPos(surfacePos);
                BunkerProtectionHandler.addBunkerBounds(bounds);
            }
        });
    }
}
