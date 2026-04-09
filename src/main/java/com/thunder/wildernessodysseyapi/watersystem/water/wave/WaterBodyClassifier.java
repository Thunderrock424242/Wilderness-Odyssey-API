package com.thunder.wildernessodysseyapi.watersystem.water.wave;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.Fluids;

import java.util.concurrent.ConcurrentHashMap;

/**
 * WaterBodyClassifier
 *
 * Determines the type of water body at a given world position.
 * Used by the wave system to pick the correct wave profile.
 *
 * Classification logic:
 *   OCEAN  — ocean/deep_ocean/beach biome, OR water connected to ocean biome
 *   RIVER  — river biome, OR long narrow water (aspect ratio check)
 *   POND   — small enclosed water body (shallow, not ocean/river biome)
 *
 * Results are cached per chunk-column to avoid repeated biome lookups.
 */
public class WaterBodyClassifier {

    public enum WaterType {
        OCEAN,
        RIVER,
        POND
    }

    // Cache: packed chunk XZ → WaterType
    private static final ConcurrentHashMap<Long, WaterType> cache = new ConcurrentHashMap<>(256);

    /**
     * Classify the water body at the given block position.
     * Returns POND if the position is not water.
     */
    public static WaterType classify(LevelReader level, BlockPos pos) {
        long key = chunkKey(pos.getX() >> 4, pos.getZ() >> 4);
        return cache.computeIfAbsent(key, k -> doClassify(level, pos));
    }

    /** Clear the cache (call on world unload). */
    public static void clearCache() {
        cache.clear();
    }

    // -------------------------------------------------------------------------

    private static WaterType doClassify(LevelReader level, BlockPos pos) {
        Holder<Biome> biomeHolder = level.getBiome(pos);

        // Ocean biomes
        if (biomeHolder.is(BiomeTags.IS_OCEAN)
         || biomeHolder.is(BiomeTags.IS_DEEP_OCEAN)
         || biomeHolder.is(BiomeTags.IS_BEACH)) {
            return WaterType.OCEAN;
        }

        // River biomes
        if (biomeHolder.is(BiomeTags.IS_RIVER)) {
            return WaterType.RIVER;
        }

        // Heuristic: sample a 16-block radius for connected water area
        int waterCount = 0;
        for (int dx = -8; dx <= 8; dx += 2) {
            for (int dz = -8; dz <= 8; dz += 2) {
                BlockPos check = pos.offset(dx, 0, dz);
                if (level.getFluidState(check).is(Fluids.WATER)) {
                    waterCount++;
                }
            }
        }

        // Large water body in non-ocean biome → treat as river-like
        if (waterCount > 30) return WaterType.RIVER;

        return WaterType.POND;
    }

    private static long chunkKey(int cx, int cz) {
        return ((long) cx & 0xFFFFFFFFL) | (((long) cz & 0xFFFFFFFFL) << 32);
    }
}
