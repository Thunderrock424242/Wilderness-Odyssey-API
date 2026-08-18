package com.thunder.wildernessodysseyapi.weather.integration.season;

import com.thunder.wildernessodysseyapi.core.ModConstants;
import com.thunder.wildernessodysseyapi.weather.api.AtmosphereCellKey;
import com.thunder.wildernessodysseyapi.weather.config.WeatherConfig;
import com.thunder.wildernessodysseyapi.weather.integration.SeasonalWeatherInfluence;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Read-only reflective adapter for the Homeostatic Seasons public calendar API.
 *
 * <p>Homeostatic remains the owner of its calendar, configured dimensions, and
 * seasonal block behavior. Wilderness reads only the current sub-season and
 * its configured timing before translating that position into atmospheric
 * offsets owned by {@link SeasonCycleProfile}.</p>
 */
final class HomeostaticSeasonsWeatherInfluence implements SeasonalWeatherInfluence {

    private static final int SUB_SEASONS_PER_YEAR = 12;
    private static final double MID_SUB_SEASON = 0.5;

    private final Method getCurrentSeason;
    private final Method getSeasonTime;
    private final Method getTimeUntilNextSeason;
    private final Method getSeasonLength;
    private boolean failureLogged;

    private HomeostaticSeasonsWeatherInfluence(
            Method getCurrentSeason,
            Method getSeasonTime,
            Method getTimeUntilNextSeason,
            Method getSeasonLength
    ) {
        this.getCurrentSeason = getCurrentSeason;
        this.getSeasonTime = getSeasonTime;
        this.getTimeUntilNextSeason = getTimeUntilNextSeason;
        this.getSeasonLength = getSeasonLength;
    }

    /** Resolves the documented Homeostatic calendar methods lazily and optionally. */
    static SeasonalWeatherInfluence create() {
        try {
            ClassLoader loader = HomeostaticSeasonsWeatherInfluence.class.getClassLoader();
            Class<?> apiType = Class.forName(
                    "homeostaticseasons.api.HomeostaticSeasonsAPI",
                    false,
                    loader
            );
            Class<?> seasonType = Class.forName(
                    "homeostaticseasons.api.Season",
                    false,
                    loader
            );
            return new HomeostaticSeasonsWeatherInfluence(
                    apiType.getMethod("getCurrentSeason", Level.class),
                    apiType.getMethod("getSeasonTime", Level.class, seasonType),
                    apiType.getMethod("getTimeUntilNextSeason", Level.class),
                    seasonType.getMethod("getSeasonLength")
            );
        } catch (ClassNotFoundException | NoSuchMethodException | LinkageError exception) {
            ModConstants.LOGGER.warn(
                    "Homeostatic Seasons is installed but its calendar API could not be resolved; seasonal weather influence is disabled",
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
            Object season = getCurrentSeason.invoke(null, level);
            if (!(season instanceof Enum<?> subSeason)) {
                return SeasonalOffset.NONE;
            }

            long seasonStart = numberValue(getSeasonTime.invoke(null, level, season));
            long seasonLength = numberValue(getSeasonLength.invoke(season));
            long ticksUntilNext = numberValue(getTimeUntilNextSeason.invoke(null, level));
            double cyclePhase = cyclePhase(
                    subSeason.ordinal(),
                    seasonStart >= 0L,
                    seasonLength,
                    ticksUntilNext
            );
            return SeasonCycleProfile.temperate(cyclePhase, settings);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException exception) {
            logFailureOnce(exception);
            return SeasonalOffset.NONE;
        }
    }

    /**
     * Converts Homeostatic's ordered sub-season into Wilderness' normalized year.
     *
     * <p>Configured calendars expose trustworthy tick progress. Fixed and
     * real-time calendars report the current sub-season but not a compatible
     * total duration, so they deliberately use that sub-season's midpoint.</p>
     */
    static double cyclePhase(
            int subSeasonOrdinal,
            boolean configuredCalendar,
            long seasonLengthTicks,
            long ticksUntilNextSeason
    ) {
        if (subSeasonOrdinal < 0 || subSeasonOrdinal >= SUB_SEASONS_PER_YEAR) {
            throw new IllegalArgumentException("Homeostatic sub-season ordinal is outside its documented 12-season year");
        }

        double subSeasonProgress = MID_SUB_SEASON;
        if (configuredCalendar
                && seasonLengthTicks > 0L
                && ticksUntilNextSeason >= 0L
                && ticksUntilNextSeason <= seasonLengthTicks) {
            subSeasonProgress = 1.0 - ticksUntilNextSeason / (double) seasonLengthTicks;
        }
        subSeasonProgress = Math.max(0.0, Math.min(Math.nextDown(1.0), subSeasonProgress));
        return (subSeasonOrdinal + subSeasonProgress) / SUB_SEASONS_PER_YEAR;
    }

    private static long numberValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException("Homeostatic Seasons returned non-numeric calendar timing");
    }

    private void logFailureOnce(Exception exception) {
        if (failureLogged) {
            return;
        }
        failureLogged = true;
        ModConstants.LOGGER.warn(
                "Homeostatic Seasons stopped supplying calendar data; Wilderness weather will use neutral seasonal influence",
                exception
        );
    }
}
