package com.thunder.wildernessodysseyapi.worldgen.secretvillage;

import com.thunder.wildernessodysseyapi.worldgen.config.StructureConfig;
import com.thunder.wildernessodysseyapi.worldgen.structure.NBTStructurePlacer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Random;

import static com.thunder.wildernessodysseyapi.core.ModConstants.MOD_ID;

/**
 * Applies deterministic rarity and biome checks before placing a Secret Order village.
 */
public final class SecretOrderVillagePlacer {

    private static final NBTStructurePlacer VILLAGE_PLACER = new NBTStructurePlacer(MOD_ID, "village");

    private SecretOrderVillagePlacer() {
    }

    /**
     * Attempts to place the village in a loaded server chunk.
     *
     * <p>The chunk position seeds the roll so reloading a chunk cannot reroll
     * placement. The spawn chance is config-backed for pack balancing.</p>
     *
     * @param level the authoritative server level
     * @param chunk the chunk being considered for placement
     */
    public static void tryPlace(ServerLevel level, LevelChunk chunk) {
        BlockPos chunkOrigin = chunk.getPos().getWorldPosition();
        Random random = new Random(chunkOrigin.asLong());
        if (random.nextFloat() > StructureConfig.SECRET_ORDER_VILLAGE_SPAWN_CHANCE.get()) {
            return;
        }

        BlockPos surfacePosition = level.getHeightmapPos(Heightmap.Types.WORLD_SURFACE, chunkOrigin);
        if (!level.getBiome(surfacePosition).is(BiomeTags.IS_JUNGLE)
                || level.getBiome(surfacePosition).is(BiomeTags.IS_OCEAN)) {
            return;
        }

        placeStructure(level, surfacePosition);
    }

    /**
     * Places the village template at an already validated surface position.
     *
     * @param level the authoritative server level
     * @param position the world position used as the template origin
     * @return {@code true} when the structure placer produced a placement result
     */
    public static boolean placeStructure(ServerLevel level, BlockPos position) {
        return VILLAGE_PLACER.place(level, position) != null;
    }
}
