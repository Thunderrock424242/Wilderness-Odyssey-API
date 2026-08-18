package com.thunder.wildernessodysseyapi.environment.simulation;

import com.thunder.wildernessodysseyapi.environment.api.EnvironmentDimensionProfile;
import com.thunder.wildernessodysseyapi.environment.api.EnvironmentInfluence;
import com.thunder.wildernessodysseyapi.environment.api.EnvironmentQuery;
import com.thunder.wildernessodysseyapi.environment.api.RegionalEnvironmentSnapshot;
import com.thunder.wildernessodysseyapi.meteor.api.MeteorSiteServices;
import com.thunder.wildernessodysseyapi.meteor.api.MeteorSiteSnapshot;
import com.thunder.wildernessodysseyapi.riftfall.RiftfallStage;
import com.thunder.wildernessodysseyapi.riftfall.RiftfallSystem;
import com.thunder.wildernessodysseyapi.vegetation.api.ReactiveVegetationServices;
import com.thunder.wildernessodysseyapi.vegetation.api.VegetationClimateState;
import com.thunder.wildernessodysseyapi.vegetation.api.VegetationDisturbanceSample;
import com.thunder.wildernessodysseyapi.weather.api.SeasonalClimateState;
import com.thunder.wildernessodysseyapi.weather.api.WeatherQuery;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WeatherServices;
import com.thunder.wildernessodysseyapi.weather.api.WeatherThreatForecast;
import com.thunder.wildernessodysseyapi.watersystem.ocean.tide.TideSystem;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WaterServices;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedConditions;
import com.thunder.wildernessodysseyapi.watersystem.water.api.WatershedLocalFlow;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Short-lived chunk-scale cache for combined regional environment snapshots.
 *
 * <p>The manager is server-thread only. It never ticks a simulation, loads a
 * chunk, or mutates owner state; cache invalidation only causes the next caller
 * to re-query the authoritative public services.</p>
 */
public final class RegionalEnvironmentManager implements EnvironmentQuery {

    private static final int CACHE_TICKS = 20;
    private static final int MAXIMUM_REGIONS_PER_LEVEL = 4_096;
    private static final int METEOR_QUERY_RADIUS = 512;
    private static final int FORECAST_TICKS = 2_400;
    private static final RegionalEnvironmentManager INSTANCE = new RegionalEnvironmentManager();

    private final Map<ServerLevel, LinkedHashMap<Long, CacheEntry>> levels = new WeakHashMap<>();

    private RegionalEnvironmentManager() {
    }

    /** Returns the process-wide server cache. */
    public static RegionalEnvironmentManager get() {
        return INSTANCE;
    }

    @Override
    public RegionalEnvironmentSnapshot sample(ServerLevel level, BlockPos position) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(position, "position");
        long key = ChunkPos.asLong(position.getX() >> 4, position.getZ() >> 4);
        long gameTime = level.getGameTime();
        LinkedHashMap<Long, CacheEntry> cache = levels.computeIfAbsent(
                level,
                ignored -> new LinkedHashMap<>(64, 0.75F, true)
        );
        CacheEntry existing = cache.get(key);
        if (existing != null && gameTime - existing.createdAt() <= CACHE_TICKS) {
            return existing.snapshot();
        }

        BlockPos anchor = new BlockPos(
                ChunkPos.getX(key) * 16 + 8,
                Math.max(level.getMinBuildHeight(), Math.min(level.getMaxBuildHeight() - 1, position.getY())),
                ChunkPos.getZ(key) * 16 + 8
        );
        RegionalEnvironmentSnapshot snapshot = build(level, anchor, gameTime);
        cache.put(key, new CacheEntry(gameTime, snapshot));
        trim(cache);
        return snapshot;
    }

    /** Removes cached chunks intersecting an authoritative change radius. */
    public void invalidate(ServerLevel level, BlockPos center, int radiusBlocks) {
        if (level == null || center == null) {
            return;
        }
        LinkedHashMap<Long, CacheEntry> cache = levels.get(level);
        if (cache == null || cache.isEmpty()) {
            return;
        }
        int radius = Math.max(0, Math.min(512, radiusBlocks));
        int minimumChunkX = Math.floorDiv(center.getX() - radius, 16);
        int maximumChunkX = Math.floorDiv(center.getX() + radius, 16);
        int minimumChunkZ = Math.floorDiv(center.getZ() - radius, 16);
        int maximumChunkZ = Math.floorDiv(center.getZ() + radius, 16);
        cache.keySet().removeIf(key -> {
            int x = ChunkPos.getX(key);
            int z = ChunkPos.getZ(key);
            return x >= minimumChunkX && x <= maximumChunkX
                    && z >= minimumChunkZ && z <= maximumChunkZ;
        });
    }

    /** Releases one unloading level without retaining a strong world reference. */
    public void clear(ServerLevel level) {
        levels.remove(level);
    }

    /** Releases all ephemeral snapshots during shutdown. */
    public void clearAll() {
        levels.clear();
    }

    private static RegionalEnvironmentSnapshot build(ServerLevel level, BlockPos anchor, long gameTime) {
        EnvironmentDimensionProfile profile = EnvironmentDimensionProfile.forLevel(level);
        WeatherQuery weatherQuery = WeatherServices.query();
        WeatherSample weather = profile.atmosphere()
                ? weatherQuery.sample(level, anchor) : WeatherSample.CLEAR;
        WeatherThreatForecast forecast = profile.atmosphere()
                ? weatherQuery.getApproachingWeather(level, anchor, FORECAST_TICKS)
                : WeatherThreatForecast.NONE;
        SeasonalClimateState season = profile.atmosphere()
                ? weatherQuery.seasonalClimateAt(level, anchor) : SeasonalClimateState.NONE;
        WatershedConditions watershed = profile.dynamicWater()
                ? WaterServices.access().getWatershedConditions(level, anchor)
                : WatershedConditions.NONE;
        WatershedLocalFlow localFlow = profile.dynamicWater()
                ? WaterServices.access().getLocalWatershedFlow(level, anchor)
                : WatershedLocalFlow.NONE;
        TideSystem.TideSample tide = profile.dynamicWater()
                ? TideSystem.sample(level)
                : new TideSystem.TideSample(0.0F, 0.0F, 0.0F, 0.5F, level.getMoonPhase(), 0.0F, 0.0F);
        VegetationClimateState vegetation = profile.reactiveVegetation()
                ? ReactiveVegetationServices.climateAt(level, anchor).orElse(VegetationClimateState.DEFAULT)
                : VegetationClimateState.DEFAULT;
        VegetationDisturbanceSample vegetationDisturbance = profile.reactiveVegetation()
                ? ReactiveVegetationServices.disturbanceAt(level, anchor)
                : VegetationDisturbanceSample.NONE;
        MeteorSiteSnapshot meteor = profile.radiation()
                ? MeteorSiteServices.nearest(level, anchor, METEOR_QUERY_RADIUS).orElse(MeteorSiteSnapshot.NONE)
                : MeteorSiteSnapshot.NONE;
        RiftfallStage riftfall = profile.riftfall() ? RiftfallSystem.stage(level) : RiftfallStage.CLEAR;
        var influence = profile.participates()
                ? EnvironmentInfluenceModel.evaluate(
                        weather,
                        forecast,
                        season,
                        watershed,
                        tide,
                        vegetation,
                        vegetationDisturbance,
                        meteor,
                        riftfall
                )
                : EnvironmentInfluence.INERT;
        return new RegionalEnvironmentSnapshot(
                anchor,
                gameTime,
                profile,
                weather,
                forecast,
                season,
                watershed,
                localFlow,
                tide,
                vegetation,
                vegetationDisturbance,
                meteor,
                riftfall,
                influence
        );
    }

    private static void trim(LinkedHashMap<Long, CacheEntry> cache) {
        Iterator<Long> iterator = cache.keySet().iterator();
        while (cache.size() > MAXIMUM_REGIONS_PER_LEVEL && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private record CacheEntry(long createdAt, RegionalEnvironmentSnapshot snapshot) {
    }
}
