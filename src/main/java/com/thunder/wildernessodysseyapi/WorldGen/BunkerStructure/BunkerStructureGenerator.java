package com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure;

import com.thunder.wildernessodysseyapi.Core.ModConstants;
import com.thunder.wildernessodysseyapi.WorldGen.BunkerStructure.Worldedit.WorldEditStructurePlacer;
import com.thunder.wildernessodysseyapi.WorldGen.worldgen.ModBiomes;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.level.biome.Biomes;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * Generates bunker structures when suitable chunks load.
 */
@EventBusSubscriber(modid = ModConstants.MOD_ID)
public class BunkerStructureGenerator {
    private static final int MIN_DISTANCE_CHUNKS = 12000;

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!(event.getChunk() instanceof LevelChunk chunk)) return;

        BlockPos chunkPos = chunk.getPos().getWorldPosition();
        BlockPos surfacePos = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, chunkPos);

        var biomeHolder = level.getBiome(surfacePos);
        if (!biomeHolder.is(ModBiomes.METEOR_IMPACT_ZONE) && !biomeHolder.is(Biomes.JUNGLE)) return;

        StructureSpawnTracker tracker = StructureSpawnTracker.get(level);
        if (tracker.hasSpawned()) {
            BlockPos last = tracker.getLastSpawnPos();
            long dx = (surfacePos.getX() - last.getX()) / 16L;
            long dz = (surfacePos.getZ() - last.getZ()) / 16L;
            long dist = Math.max(Math.abs(dx), Math.abs(dz));
            if (dist < MIN_DISTANCE_CHUNKS) return;
        }

        WorldEditStructurePlacer placer = new WorldEditStructurePlacer(ModConstants.MOD_ID, "bunkerfinal.schem");
        AABB bounds = placer.placeStructure(level, surfacePos);
        if (bounds != null) {
            tracker.markAsSpawned();
            tracker.setLastSpawnPos(surfacePos);
            BunkerProtectionHandler.setBunkerBounds(bounds);
        }
    }
}
