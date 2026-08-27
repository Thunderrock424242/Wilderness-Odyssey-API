package com.thunder.wildernessodysseyapi.watersystem.water.wave;

import com.thunder.wildernessodysseyapi.core.ModAttachments;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WaterCompatibility;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.GeneratedWaterChunk;
import com.thunder.wildernessodysseyapi.watersystem.water.volume.WildernessWaterAuthority;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;

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
 * <p>Generated water metadata is authoritative when present. Vanilla or
 * third-party water without that metadata falls back to biome tags plus one
 * bounded local shape sample; render paths never flood-fill a water body or
 * force neighboring chunks to load.</p>
 *
 * Results are cached in small horizontal cells to avoid repeated biome lookups
 * without forcing one biome sample to classify an entire chunk of shoreline.
 */
public class WaterBodyClassifier {

    private static final int CACHE_CELL_SHIFT = 1;
    private static final int FALLBACK_SAMPLE_RADIUS = 8;
    private static final int MAX_CACHED_CELLS_PER_LEVEL = 16_384;
    private static final int[][] CARDINAL_OFFSETS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    public enum WaterType {
        OCEAN,
        RIVER,
        POND,
        COAST,
        LAKE
    }

    /** Returns whether a surface participates in sea-state energy and tides. */
    public static boolean isOceanic(WaterType type) {
        return type == WaterType.OCEAN || type == WaterType.COAST;
    }

    // Cache: packed 2x2-block XZ cell to water type.
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

    /**
     * Invalidates only nearby cached shape samples after a block edit. This
     * keeps shoreline transitions current without discarding another region's
     * classification work.
     */
    public static void invalidate(LevelReader level, BlockPos pos) {
        ConcurrentHashMap<Long, WaterType> cache = CACHES.get(level);
        if (cache == null || cache.isEmpty()) {
            return;
        }
        int centerX = pos.getX() >> CACHE_CELL_SHIFT;
        int centerZ = pos.getZ() >> CACHE_CELL_SHIFT;
        int radius = (FALLBACK_SAMPLE_RADIUS >> CACHE_CELL_SHIFT) + 1;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                cache.remove(cellKey(centerX + dx, centerZ + dz));
            }
        }
    }

    /** Invalidates one synchronized chunk and the nearby fallback sample halo. */
    public static void invalidateChunk(LevelReader level, int chunkX, int chunkZ) {
        ConcurrentHashMap<Long, WaterType> cache = CACHES.get(level);
        if (cache == null || cache.isEmpty()) {
            return;
        }
        int cellsPerChunk = 16 >> CACHE_CELL_SHIFT;
        int minimumX = chunkX * cellsPerChunk;
        int minimumZ = chunkZ * cellsPerChunk;
        int radius = (FALLBACK_SAMPLE_RADIUS >> CACHE_CELL_SHIFT) + 1;
        for (int cellX = minimumX - radius;
             cellX < minimumX + cellsPerChunk + radius;
             cellX++) {
            for (int cellZ = minimumZ - radius;
                 cellZ < minimumZ + cellsPerChunk + radius;
                 cellZ++) {
                cache.remove(cellKey(cellX, cellZ));
            }
        }
    }

    // -------------------------------------------------------------------------

    private static WaterType doClassify(LevelReader level, BlockPos pos) {
        if (!hasAuthoritativeWater(level, pos)) {
            return WaterType.POND;
        }

        boolean immediateShoreline = isImmediateShoreline(level, pos);
        GeneratedWaterChunk.BodyType generatedType = generatedType(level, pos);
        if (generatedType != null) {
            return mapGeneratedType(generatedType, immediateShoreline);
        }

        Holder<Biome> biomeHolder = level.getBiome(pos);
        int waterCount = 0;
        int minimumX = Integer.MAX_VALUE;
        int maximumX = Integer.MIN_VALUE;
        int minimumZ = Integer.MAX_VALUE;
        int maximumZ = Integer.MIN_VALUE;
        for (int dx = -FALLBACK_SAMPLE_RADIUS; dx <= FALLBACK_SAMPLE_RADIUS; dx += 2) {
            for (int dz = -FALLBACK_SAMPLE_RADIUS; dz <= FALLBACK_SAMPLE_RADIUS; dz += 2) {
                BlockPos check = pos.offset(dx, 0, dz);
                if (level instanceof Level concreteLevel && !concreteLevel.hasChunkAt(check)) {
                    continue;
                }
                if (hasAuthoritativeWater(level, check)) {
                    waterCount++;
                    minimumX = Math.min(minimumX, dx);
                    maximumX = Math.max(maximumX, dx);
                    minimumZ = Math.min(minimumZ, dz);
                    maximumZ = Math.max(maximumZ, dz);
                }
            }
        }

        int spanX = waterCount == 0 ? 0 : maximumX - minimumX + 2;
        int spanZ = waterCount == 0 ? 0 : maximumZ - minimumZ + 2;
        return classifyFallback(
                biomeHolder.is(BiomeTags.IS_OCEAN) || biomeHolder.is(BiomeTags.IS_DEEP_OCEAN),
                biomeHolder.is(BiomeTags.IS_RIVER),
                biomeHolder.is(BiomeTags.IS_BEACH),
                immediateShoreline,
                waterCount,
                spanX,
                spanZ
        );
    }

    static WaterType mapGeneratedType(
            GeneratedWaterChunk.BodyType bodyType,
            boolean immediateShoreline
    ) {
        return switch (bodyType) {
            case OCEAN -> immediateShoreline ? WaterType.COAST : WaterType.OCEAN;
            case RIVER -> WaterType.RIVER;
            case LAKE -> WaterType.LAKE;
            case AQUIFER, SPRING -> WaterType.POND;
        };
    }

    static WaterType classifyFallback(
            boolean oceanBiome,
            boolean riverBiome,
            boolean beachBiome,
            boolean immediateShoreline,
            int waterCount,
            int spanX,
            int spanZ
    ) {
        if (oceanBiome) {
            return immediateShoreline || beachBiome ? WaterType.COAST : WaterType.OCEAN;
        }
        if (riverBiome) {
            return WaterType.RIVER;
        }
        int shortSpan = Math.max(1, Math.min(spanX, spanZ));
        int longSpan = Math.max(spanX, spanZ);
        if (waterCount >= 12 && longSpan >= shortSpan * 5 / 2) {
            return WaterType.RIVER;
        }
        if (beachBiome && immediateShoreline && waterCount >= 20) {
            return WaterType.COAST;
        }
        if (waterCount >= 30) {
            return WaterType.LAKE;
        }
        return WaterType.POND;
    }

    private static GeneratedWaterChunk.BodyType generatedType(LevelReader level, BlockPos pos) {
        if (!(level instanceof Level concreteLevel) || !concreteLevel.hasChunkAt(pos)) {
            return null;
        }
        LevelChunk chunk = concreteLevel.getChunkAt(pos);
        GeneratedWaterChunk generated = chunk.getExistingData(ModAttachments.GENERATED_WATER)
                .orElse(null);
        GeneratedWaterChunk.WaterSpan span = generated == null ? null : generated.spanAt(pos);
        return span == null ? null : span.cell().bodyType();
    }

    private static boolean isImmediateShoreline(LevelReader level, BlockPos pos) {
        for (int[] offset : CARDINAL_OFFSETS) {
            BlockPos neighbor = pos.offset(offset[0], 0, offset[1]);
            if (level instanceof Level concreteLevel && !concreteLevel.hasChunkAt(neighbor)) {
                continue;
            }
            if (!hasAuthoritativeWater(level, neighbor)) {
                return true;
            }
        }
        return false;
    }

    private static long cellKey(int cellX, int cellZ) {
        return ((long) cellX & 0xFFFFFFFFL) | (((long) cellZ & 0xFFFFFFFFL) << 32);
    }

    private static boolean hasAuthoritativeWater(LevelReader level, BlockPos pos) {
        if (level instanceof Level concreteLevel) {
            return WildernessWaterAuthority.isWaterAt(concreteLevel, pos);
        }
        return WaterCompatibility.hasTaggedWater(level, pos);
    }
}
