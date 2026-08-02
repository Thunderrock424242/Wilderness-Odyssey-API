package com.thunder.wildernessodysseyapi.weather.simulation;

import com.thunder.wildernessodysseyapi.weather.api.AtmosphereCellKey;
import com.thunder.wildernessodysseyapi.weather.integration.SeasonalWeatherInfluence;
import com.thunder.wildernessodysseyapi.weather.integration.WaterInfluenceSample;
import com.thunder.wildernessodysseyapi.weather.integration.WeatherWaterInfluence;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Captures compact environmental inputs for the pure atmosphere engine.
 *
 * <p>Terrain climate uses a fixed 3 by 3 surface lattice and a bounded cache.
 * Missing chunks are skipped through {@code getChunkNow}; no weather sample
 * can force terrain to load. Water coverage is delegated to an isolated
 * read-only adapter so weather never depends on mutable water storage details.</p>
 */
public final class AtmosphereInputSampler {

    private static final int CLIMATE_PROBES_PER_AXIS = 3;
    private static final int MAX_CACHED_CELLS = 2048;

    private final WeatherWaterInfluence waterInfluence;
    private final SeasonalWeatherInfluence seasonalInfluence;
    private final LinkedHashMap<Long, CachedClimate> climateCache =
            new LinkedHashMap<>(128, 0.75f, true);

    /** Creates the normal sampler with optional integrations injected at the boundary. */
    public AtmosphereInputSampler(
            WeatherWaterInfluence waterInfluence,
            SeasonalWeatherInfluence seasonalInfluence
    ) {
        this.waterInfluence = Objects.requireNonNull(waterInfluence, "waterInfluence");
        this.seasonalInfluence = Objects.requireNonNullElse(seasonalInfluence, SeasonalWeatherInfluence.NONE);
    }

    /**
     * Captures an immutable input record on the logical server thread.
     */
    public synchronized AtmosphereEnvironment sample(
            ServerLevel level,
            AtmosphereCellKey cell,
            int cellSize,
            int refreshIntervalTicks
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(cell, "cell");
        int refresh = Math.max(20, refreshIntervalTicks);
        long gameTime = level.getGameTime();
        CachedClimate cached = climateCache.get(cell.packed());
        if (cached == null || gameTime - cached.sampledAtTick >= refresh) {
            Climate climate = sampleLoadedClimate(level, cell, cellSize);
            if (climate.loadedProbes > 0 || cached == null) {
                cached = new CachedClimate(gameTime, climate.withFallback(dimensionFallback(level)));
            } else {
                // Keep the last known terrain climate while a formerly active
                // region is dormant; repeated misses do not cause hot polling.
                cached = new CachedClimate(gameTime, cached.climate);
            }
            climateCache.put(cell.packed(), cached);
            trimCache();
        }

        WaterInfluenceSample water = waterInfluence.sample(
                level,
                cell,
                cellSize,
                refresh
        );
        SeasonalWeatherInfluence.SeasonalOffset season =
                seasonalInfluence.sample(level, cell, cellSize);
        Climate climate = cached.climate;
        double humidity = clamp01(climate.humidity + season.humidity());
        return new AtmosphereEnvironment(
                climate.temperatureCelsius,
                humidity,
                climate.elevation,
                water.moisturePotential(),
                daylight(level),
                dimensionTemperatureOffset(level.dimensionType()),
                season.temperatureCelsius(),
                deterministicVariation(level.getSeed(), cell.packed()),
                season.storminess(),
                season.evaporationMultiplier(),
                climate.terrainGradientX,
                climate.terrainGradientZ,
                climate.terrainRoughness,
                water.oceanCoverage() * water.loadedProbeFraction(),
                water.inlandWaterCoverage() * water.loadedProbeFraction()
        );
    }

    /** Clears level-derived caches during unload or server shutdown. */
    public synchronized void clear() {
        climateCache.clear();
        waterInfluence.clear();
    }

    private static Climate sampleLoadedClimate(ServerLevel level, AtmosphereCellKey cell, int cellSize) {
        int size = Math.max(16, cellSize);
        long minX = (long) cell.x() * size;
        long minZ = (long) cell.z() * size;
        double temperature = 0.0;
        double humidity = 0.0;
        double elevation = 0.0;
        double[][] probeElevations = new double[CLIMATE_PROBES_PER_AXIS][CLIMATE_PROBES_PER_AXIS];
        boolean[][] probeLoaded = new boolean[CLIMATE_PROBES_PER_AXIS][CLIMATE_PROBES_PER_AXIS];
        int loaded = 0;

        for (int probeZ = 0; probeZ < CLIMATE_PROBES_PER_AXIS; probeZ++) {
            for (int probeX = 0; probeX < CLIMATE_PROBES_PER_AXIS; probeX++) {
                int blockX = boundedBlock(minX + ((2L * probeX + 1L) * size)
                        / (2L * CLIMATE_PROBES_PER_AXIS));
                int blockZ = boundedBlock(minZ + ((2L * probeZ + 1L) * size)
                        / (2L * CLIMATE_PROBES_PER_AXIS));
                LevelChunk chunk = level.getChunkSource().getChunkNow(blockX >> 4, blockZ >> 4);
                if (chunk == null) {
                    continue;
                }

                int surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, blockX & 15, blockZ & 15) - 1;
                surfaceY = Math.max(level.getMinBuildHeight(), Math.min(level.getMaxBuildHeight() - 1, surfaceY));
                Holder<Biome> biomeHolder = chunk.getNoiseBiome(
                        QuartPos.fromBlock(blockX),
                        QuartPos.fromBlock(surfaceY),
                        QuartPos.fromBlock(blockZ)
                );
                Biome biome = biomeHolder.value();
                float minecraftTemperature = biome.getModifiedClimateSettings().temperature();
                float downfall = biome.getModifiedClimateSettings().downfall();
                temperature += (minecraftTemperature - 0.15) * 25.0;
                humidity += biome.hasPrecipitation() ? downfall : downfall * 0.25;
                elevation += surfaceY;
                probeElevations[probeZ][probeX] = surfaceY;
                probeLoaded[probeZ][probeX] = true;
                loaded++;
            }
        }

        if (loaded == 0) {
            return Climate.UNKNOWN;
        }
        double inverse = 1.0 / loaded;
        double meanElevation = elevation * inverse;
        double gradientScale = Math.max(1.0, size * (2.0 / 3.0));
        double gradientX = edgeAverage(probeElevations, probeLoaded, true, false, meanElevation)
                - edgeAverage(probeElevations, probeLoaded, true, true, meanElevation);
        double gradientZ = edgeAverage(probeElevations, probeLoaded, false, false, meanElevation)
                - edgeAverage(probeElevations, probeLoaded, false, true, meanElevation);
        double variance = 0.0;
        for (int probeZ = 0; probeZ < CLIMATE_PROBES_PER_AXIS; probeZ++) {
            for (int probeX = 0; probeX < CLIMATE_PROBES_PER_AXIS; probeX++) {
                if (!probeLoaded[probeZ][probeX]) {
                    continue;
                }
                double delta = probeElevations[probeZ][probeX] - meanElevation;
                variance += delta * delta;
            }
        }
        double roughness = clamp01(Math.sqrt(variance * inverse) / 48.0);
        return new Climate(
                temperature * inverse,
                humidity * inverse,
                meanElevation,
                loaded,
                gradientX / gradientScale,
                gradientZ / gradientScale,
                roughness
        );
    }

    private static Climate dimensionFallback(ServerLevel level) {
        DimensionType dimension = level.dimensionType();
        if (dimension.ultraWarm()) {
            return new Climate(38.0, 0.12, 64.0, 1, 0.0, 0.0, 0.0);
        }
        if (dimension.hasCeiling()) {
            return new Climate(12.0, 0.42, 64.0, 1, 0.0, 0.0, 0.0);
        }
        return new Climate(15.0, 0.45, 64.0, 1, 0.0, 0.0, 0.0);
    }

    private static double edgeAverage(
            double[][] elevations,
            boolean[][] loaded,
            boolean xAxis,
            boolean minimumEdge,
            double fallback
    ) {
        int fixedIndex = minimumEdge ? 0 : CLIMATE_PROBES_PER_AXIS - 1;
        double total = 0.0;
        int count = 0;
        for (int index = 0; index < CLIMATE_PROBES_PER_AXIS; index++) {
            int z = xAxis ? index : fixedIndex;
            int x = xAxis ? fixedIndex : index;
            if (!loaded[z][x]) {
                continue;
            }
            total += elevations[z][x];
            count++;
        }
        return count == 0 ? fallback : total / count;
    }

    private static double daylight(ServerLevel level) {
        if (!level.dimensionType().hasSkyLight()) {
            return 0.5;
        }
        double dayFraction = Math.floorMod(level.getDayTime(), 24_000L) / 24_000.0;
        return clamp01(0.5 + Math.cos((dayFraction - 0.25) * Math.PI * 2.0) * 0.5);
    }

    private static double dimensionTemperatureOffset(DimensionType dimension) {
        if (dimension.ultraWarm()) {
            return 20.0;
        }
        if (!dimension.hasSkyLight() && dimension.hasCeiling()) {
            return -2.0;
        }
        return 0.0;
    }

    private static double deterministicVariation(long worldSeed, long packedCell) {
        long value = worldSeed ^ Long.rotateLeft(packedCell * 0x9E3779B97F4A7C15L, 21);
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return ((value >>> 11) * 0x1.0p-53) * 2.0 - 1.0;
    }

    private void trimCache() {
        while (climateCache.size() > MAX_CACHED_CELLS) {
            Map.Entry<Long, CachedClimate> eldest = climateCache.entrySet().iterator().next();
            climateCache.remove(eldest.getKey());
        }
    }

    private static int boundedBlock(long coordinate) {
        return (int) Math.max(-30_000_000L, Math.min(30_000_000L, coordinate));
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, Double.isFinite(value) ? value : 0.0));
    }

    private record CachedClimate(long sampledAtTick, Climate climate) {
    }

    private record Climate(
            double temperatureCelsius,
            double humidity,
            double elevation,
            int loadedProbes,
            double terrainGradientX,
            double terrainGradientZ,
            double terrainRoughness
    ) {
        private static final Climate UNKNOWN = new Climate(0.0, 0.0, 0.0, 0, 0.0, 0.0, 0.0);

        private Climate withFallback(Climate fallback) {
            return loadedProbes > 0 ? this : fallback;
        }
    }
}
