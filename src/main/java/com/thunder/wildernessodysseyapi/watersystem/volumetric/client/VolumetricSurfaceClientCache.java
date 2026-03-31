package com.thunder.wildernessodysseyapi.watersystem.volumetric.client;

import com.thunder.wildernessodysseyapi.watersystem.volumetric.VolumetricFluidManager.SimulatedFluid;
import com.thunder.wildernessodysseyapi.watersystem.volumetric.VolumetricFluidManager.SurfaceSample;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client-side cache for synchronized volumetric fluid surface samples.
 */
public final class VolumetricSurfaceClientCache {
    private static final Map<ResourceLocation, Map<SimulatedFluid, Map<Long, SurfaceSample>>> CACHE_BY_DIMENSION = new ConcurrentHashMap<>();
    private static volatile long lastUpdateGameTime;

    private VolumetricSurfaceClientCache() {
    }

    public static void replace(ResourceLocation dimensionId, SimulatedFluid fluidType, List<SurfaceSample> samples, long gameTime) {
        Map<Long, SurfaceSample> map = getOrCreateFluidMap(dimensionId, fluidType);
        map.clear();
        for (SurfaceSample sample : samples) {
            map.put(columnKey(sample.blockX(), sample.blockZ()), sample);
        }
        lastUpdateGameTime = gameTime;
    }

    public static List<SurfaceSample> snapshot(ResourceLocation dimensionId, SimulatedFluid fluidType) {
        Map<Long, SurfaceSample> fluidMap = getFluidMapOrNull(dimensionId, fluidType);
        if (fluidMap == null) {
            return List.of();
        }
        return new ArrayList<>(fluidMap.values());
    }

    public static int size(ResourceLocation dimensionId, SimulatedFluid fluidType) {
        Map<Long, SurfaceSample> fluidMap = getFluidMapOrNull(dimensionId, fluidType);
        return fluidMap == null ? 0 : fluidMap.size();
    }

    public static long lastUpdateGameTime() {
        return lastUpdateGameTime;
    }

    public static boolean isStale(long currentGameTime, int maxStaleAgeTicks) {
        return (currentGameTime - lastUpdateGameTime) > maxStaleAgeTicks;
    }

    public static void clearAll() {
        CACHE_BY_DIMENSION.clear();
    }

    private static long columnKey(int x, int z) {
        return (((long) x) << 32) ^ (z & 0xffffffffL);
    }

    private static Map<Long, SurfaceSample> getOrCreateFluidMap(ResourceLocation dimensionId, SimulatedFluid fluidType) {
        Map<SimulatedFluid, Map<Long, SurfaceSample>> byFluid = CACHE_BY_DIMENSION.computeIfAbsent(
                dimensionId, ignored -> new EnumMap<>(SimulatedFluid.class)
        );
        return byFluid.computeIfAbsent(fluidType, ignored -> new ConcurrentHashMap<>());
    }

    private static Map<Long, SurfaceSample> getFluidMapOrNull(ResourceLocation dimensionId, SimulatedFluid fluidType) {
        Map<SimulatedFluid, Map<Long, SurfaceSample>> byFluid = CACHE_BY_DIMENSION.get(dimensionId);
        if (byFluid == null) {
            return null;
        }
        return byFluid.get(fluidType);
    }
}
