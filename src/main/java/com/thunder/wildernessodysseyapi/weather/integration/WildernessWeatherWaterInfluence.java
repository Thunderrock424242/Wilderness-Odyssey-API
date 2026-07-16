package com.thunder.wildernessodysseyapi.weather.integration;

import com.thunder.wildernessodysseyapi.weather.api.AtmosphereCellKey;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterAccess;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterServices;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Samples vanilla, mod-tagged, and Wilderness-owned surface water without mutation.
 *
 * <p>An 8 by 8 deterministic lattice is used for a default 256-block cell.
 * Each probe touches one loaded surface column, so the adapter never enumerates
 * canonical water positions, scans a volume, or force-loads a chunk. Results
 * are cached and bounded because terrain-scale moisture changes slowly.</p>
 */
public final class WildernessWeatherWaterInfluence implements WeatherWaterInfluence {

    private static final int PROBES_PER_AXIS = 8;
    private static final int TOTAL_PROBES = PROBES_PER_AXIS * PROBES_PER_AXIS;
    private static final int MAX_CACHED_CELLS = 2048;

    private final WaterAccess waterAccess;
    private final LinkedHashMap<Long, CachedSample> cache = new LinkedHashMap<>(128, 0.75f, true);

    /** Creates an adapter backed by the stable public Wilderness water service. */
    public WildernessWeatherWaterInfluence() {
        this(WaterServices.access());
    }

    WildernessWeatherWaterInfluence(WaterAccess waterAccess) {
        this.waterAccess = waterAccess;
    }

    @Override
    public synchronized WaterInfluenceSample sample(
            ServerLevel level,
            AtmosphereCellKey cell,
            int cellSize,
            int refreshIntervalTicks
    ) {
        long gameTime = level.getGameTime();
        long packedCell = cell.packed();
        CachedSample cached = cache.get(packedCell);
        if (cached != null && gameTime - cached.sampledAtTick < Math.max(20, refreshIntervalTicks)) {
            return cached.sample;
        }

        WaterInfluenceSample sampled = sampleLoadedSurface(level, cell, Math.max(16, cellSize));
        if (sampled.loadedProbeFraction() == 0.0f && cached != null) {
            // Preserve the last known surface context when a dormant region's
            // chunks unload; an absence of loaded probes is not evidence that
            // the water disappeared.
            sampled = cached.sample;
        }
        cache.put(packedCell, new CachedSample(gameTime, sampled));
        trimCache();
        return sampled;
    }

    @Override
    public synchronized void clear() {
        cache.clear();
    }

    // Samples only surface columns whose chunks are already present in memory.
    private WaterInfluenceSample sampleLoadedSurface(
            ServerLevel level,
            AtmosphereCellKey cell,
            int cellSize
    ) {
        int loaded = 0;
        int wet = 0;
        int ocean = 0;
        int river = 0;
        int inland = 0;
        int taggedOnly = 0;
        long minX = (long) cell.x() * cellSize;
        long minZ = (long) cell.z() * cellSize;

        for (int probeZ = 0; probeZ < PROBES_PER_AXIS; probeZ++) {
            for (int probeX = 0; probeX < PROBES_PER_AXIS; probeX++) {
                int blockX = boundedBlock(minX + ((2L * probeX + 1L) * cellSize) / (2L * PROBES_PER_AXIS));
                int blockZ = boundedBlock(minZ + ((2L * probeZ + 1L) * cellSize) / (2L * PROBES_PER_AXIS));
                LevelChunk chunk = level.getChunkSource().getChunkNow(blockX >> 4, blockZ >> 4);
                if (chunk == null) {
                    continue;
                }

                loaded++;
                int surfaceY = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX & 15, blockZ & 15) - 1;
                surfaceY = Math.max(level.getMinBuildHeight(), Math.min(level.getMaxBuildHeight() - 1, surfaceY));
                BlockPos surface = new BlockPos(blockX, surfaceY, blockZ);
                boolean wildernessWater = waterAccess.isWaterAt(level, surface);
                boolean taggedWater = chunk.getFluidState(surface).is(FluidTags.WATER);
                if (!wildernessWater && !taggedWater) {
                    continue;
                }

                wet++;
                if (!wildernessWater && taggedWater) {
                    taggedOnly++;
                }
                Holder<Biome> biome = chunk.getNoiseBiome(
                        QuartPos.fromBlock(blockX),
                        QuartPos.fromBlock(surfaceY),
                        QuartPos.fromBlock(blockZ)
                );
                if (biome.is(BiomeTags.IS_OCEAN)) {
                    ocean++;
                } else if (biome.is(BiomeTags.IS_RIVER)) {
                    river++;
                } else {
                    inland++;
                }
            }
        }

        if (loaded == 0) {
            return WaterInfluenceSample.UNKNOWN;
        }
        float inverseLoaded = 1.0f / loaded;
        return new WaterInfluenceSample(
                wet * inverseLoaded,
                ocean * inverseLoaded,
                river * inverseLoaded,
                inland * inverseLoaded,
                taggedOnly * inverseLoaded,
                loaded / (float) TOTAL_PROBES
        );
    }

    private void trimCache() {
        while (cache.size() > MAX_CACHED_CELLS) {
            Map.Entry<Long, CachedSample> eldest = cache.entrySet().iterator().next();
            cache.remove(eldest.getKey());
        }
    }

    private static int boundedBlock(long coordinate) {
        return (int) Math.max(-30_000_000L, Math.min(30_000_000L, coordinate));
    }

    private record CachedSample(long sampledAtTick, WaterInfluenceSample sample) {
    }
}
