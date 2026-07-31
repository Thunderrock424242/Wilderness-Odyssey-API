package com.thunder.wildernessodysseyapi.weather.integration.season;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.weather.api.AtmosphereCellKey;
import com.thunder.wildernessodysseyapi.weather.config.WeatherConfig;
import com.thunder.wildernessodysseyapi.weather.integration.SeasonalWeatherInfluence;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Read-only reflective adapter for the Serene Seasons public API.
 *
 * <p>The cycle state is dimension-owned by Serene. Tropical classification is
 * sampled only from an already-loaded center chunk so atmospheric updates
 * never force terrain to load.</p>
 */
final class SereneSeasonsWeatherInfluence implements SeasonalWeatherInfluence {

    private final Method getSeasonState;
    private final Method usesTropicalSeasons;
    private final Method getCycleTicks;
    private final Method getCycleDuration;
    private final Method getTropicalSeason;
    private boolean failureLogged;

    private SereneSeasonsWeatherInfluence(
            Method getSeasonState,
            Method usesTropicalSeasons,
            Method getCycleTicks,
            Method getCycleDuration,
            Method getTropicalSeason
    ) {
        this.getSeasonState = getSeasonState;
        this.usesTropicalSeasons = usesTropicalSeasons;
        this.getCycleTicks = getCycleTicks;
        this.getCycleDuration = getCycleDuration;
        this.getTropicalSeason = getTropicalSeason;
    }

    /** Resolves the documented Serene calendar methods lazily and optionally. */
    static SeasonalWeatherInfluence create() {
        try {
            ClassLoader loader = SereneSeasonsWeatherInfluence.class.getClassLoader();
            Class<?> helperType = Class.forName(
                    "sereneseasons.api.season.SeasonHelper",
                    false,
                    loader
            );
            Class<?> stateType = Class.forName(
                    "sereneseasons.api.season.ISeasonState",
                    false,
                    loader
            );
            return new SereneSeasonsWeatherInfluence(
                    helperType.getMethod("getSeasonState", Level.class),
                    helperType.getMethod("usesTropicalSeasons", Holder.class),
                    stateType.getMethod("getSeasonCycleTicks"),
                    stateType.getMethod("getCycleDuration"),
                    stateType.getMethod("getTropicalSeason")
            );
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError exception) {
            ModConstants.LOGGER.warn(
                    "Serene Seasons is installed but its calendar API could not be resolved; seasonal weather influence is disabled",
                    exception
            );
            return SeasonalWeatherInfluence.NONE;
        }
    }

    @Override
    public SeasonalOffset sample(ServerLevel level, AtmosphereCellKey cell, int cellSize) {
        WeatherConfig.SeasonSettings settings = WeatherConfig.seasons();
        if (!settings.enabled()) {
            return SeasonalOffset.NONE;
        }
        try {
            Object state = getSeasonState.invoke(null, level);
            if (state == null) {
                return SeasonalOffset.NONE;
            }
            Holder<Biome> biome = loadedCenterBiome(level, cell, cellSize);
            if (biome != null && Boolean.TRUE.equals(usesTropicalSeasons.invoke(null, biome))) {
                Object tropical = getTropicalSeason.invoke(state);
                if (tropical instanceof Enum<?> tropicalSeason) {
                    return SeasonCycleProfile.tropical(tropicalSeason.name(), settings);
                }
            }

            int cycleDuration = Math.max(1, ((Number) getCycleDuration.invoke(state)).intValue());
            int cycleTicks = Math.max(0, ((Number) getCycleTicks.invoke(state)).intValue());
            return SeasonCycleProfile.temperate(cycleTicks / (double) cycleDuration, settings);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException exception) {
            logFailureOnce(exception);
            return SeasonalOffset.NONE;
        }
    }

    private static Holder<Biome> loadedCenterBiome(
            ServerLevel level,
            AtmosphereCellKey cell,
            int cellSize
    ) {
        int blockX;
        int blockZ;
        try {
            blockX = cell.centerBlockX(cellSize);
            blockZ = cell.centerBlockZ(cellSize);
        } catch (ArithmeticException exception) {
            return null;
        }
        LevelChunk chunk = level.getChunkSource().getChunkNow(blockX >> 4, blockZ >> 4);
        if (chunk == null) {
            return null;
        }
        int surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, blockX & 15, blockZ & 15) - 1;
        surfaceY = Math.max(level.getMinBuildHeight(), Math.min(level.getMaxBuildHeight() - 1, surfaceY));
        return chunk.getNoiseBiome(
                QuartPos.fromBlock(blockX),
                QuartPos.fromBlock(surfaceY),
                QuartPos.fromBlock(blockZ)
        );
    }

    private void logFailureOnce(Exception exception) {
        if (failureLogged) {
            return;
        }
        failureLogged = true;
        ModConstants.LOGGER.warn(
                "Serene Seasons stopped supplying calendar data; Wilderness weather will use neutral seasonal influence",
                exception
        );
    }
}
