package com.thunder.wildernessodysseyapi.watersystem.water.wave;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.Fluids;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
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
 * Results are cached in small horizontal cells to avoid repeated biome lookups
 * without forcing one biome sample to classify an entire chunk of shoreline.
 */
public class WaterBodyClassifier {

    private static final int CACHE_CELL_SHIFT = 2;
    private static final int MAX_CACHED_CELLS_PER_LEVEL = 16_384;

    public enum WaterType {
        OCEAN,
        RIVER,
        POND
    }

    // Cache: packed 4x4-block XZ cell to water type.
    private static final Map<LevelReader, ConcurrentHashMap<Long, WaterType>> CACHES =
            Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Classify the water body at the given block position.
     * Returns POND if the position is not water.
     */
    public static WaterType classify(LevelReader level, BlockPos pos) {
        long key = cellKey(pos.getX() >> CACHE_CELL_SHIFT, pos.getZ() >> CACHE_CELL_SHIFT);
        ConcurrentHashMap<Long, WaterType> cache;
        synchronized (CACHES) {
            cache = CACHES.computeIfAbsent(level, ignored -> new ConcurrentHashMap<>(256));
        }
        if (cache.size() >= MAX_CACHED_CELLS_PER_LEVEL && !cache.containsKey(key)) {
            cache.clear();
        }
        return cache.computeIfAbsent(key, k -> doClassify(level, pos));
    }

    /** Clear the cache (call on world unload). */
    public static void clearCache() {
        CACHES.clear();
    }

    /** Clears classification state for one unloading level. */
    public static void clearCache(LevelReader level) {
        CACHES.remove(level);
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
        // Broad connected water is ocean-like even when a modded biome forgot
        // the vanilla ocean tag. Narrower connected bands remain river-like.
        if (waterCount > 50) return WaterType.OCEAN;
        if (waterCount > 30) return WaterType.RIVER;

        return WaterType.POND;
    }

    private static long cellKey(int cellX, int cellZ) {
        return ((long) cellX & 0xFFFFFFFFL) | (((long) cellZ & 0xFFFFFFFFL) << 32);
    }
}
