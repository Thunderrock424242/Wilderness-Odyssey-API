package com.thunder.wildernessodysseyapi.WorldGen.SecretOrderVillage;

import com.thunder.wildernessodysseyapi.WorldGen.structure.NBTStructurePlacer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Random;

/**
 * Places the secret order village structure in the world.
 */
public class SecretOrderVillagePlacer {
    private static final NBTStructurePlacer VILLAGE_PLACER =
            new NBTStructurePlacer("wildernessodysseyapi", "village");

    /**
     * Attempts to spawn the structure in the given chunk.
     */
    public static void tryPlace(ServerLevel level, LevelChunk chunk) {
        BlockPos chunkPos = chunk.getPos().getWorldPosition();
        Random rand = new Random(chunkPos.asLong());

        if (rand.nextFloat() > 0.001f) return; // Rare spawn chance

        BlockPos surfacePos = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, chunkPos);
        if (!level.getBiome(surfacePos).is(BiomeTags.IS_JUNGLE)) return;
        if (level.getBiome(surfacePos).is(BiomeTags.IS_OCEAN)) return;

        placeStructure(level, surfacePos);
    }

    /**
     * Actually loads the structure and places it.
     */
    public static boolean placeStructure(ServerLevel world, BlockPos position) {
        NBTStructurePlacer.PlacementResult result = VILLAGE_PLACER.place(world, position);
        return result != null;
    }
}
