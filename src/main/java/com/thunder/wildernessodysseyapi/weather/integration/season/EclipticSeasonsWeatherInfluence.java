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
 * Read-only reflective adapter for the Ecliptic Seasons public calendar API.
 *
 * <p>Reflection keeps the integration optional at class-loading time. The
 * adapter reads only the global solar-term clock; Wilderness remains the sole
 * authority for its atmospheric cells and never asks Ecliptic to mutate
 * weather.</p>
 */
final class EclipticSeasonsWeatherInfluence implements SeasonalWeatherInfluence {

    private final Object api;
    private final Method isSeasonEnabled;
    private final Method getSolarTerm;
    private final Method getDayInTerm;
    private final Method getLastingDaysOfEachTerm;
    private boolean failureLogged;

    private EclipticSeasonsWeatherInfluence(
            Object api,
            Method isSeasonEnabled,
            Method getSolarTerm,
            Method getDayInTerm,
            Method getLastingDaysOfEachTerm
    ) {
        this.api = api;
        this.isSeasonEnabled = isSeasonEnabled;
        this.getSolarTerm = getSolarTerm;
        this.getDayInTerm = getDayInTerm;
        this.getLastingDaysOfEachTerm = getLastingDaysOfEachTerm;
    }

    /** Resolves the stable API surface without introducing a required dependency. */
    static SeasonalWeatherInfluence create() {
        try {
            ClassLoader loader = EclipticSeasonsWeatherInfluence.class.getClassLoader();
            Class<?> apiType = Class.forName(
                    "com.teamtea.eclipticseasons.api.EclipticSeasonsApi",
                    false,
                    loader
            );
            Object api = apiType.getMethod("getInstance").invoke(null);
            return new EclipticSeasonsWeatherInfluence(
                    api,
                    apiType.getMethod("isSeasonEnabled", Level.class),
                    apiType.getMethod("getSolarTerm", Level.class),
                    apiType.getMethod("getDayInTerm", Level.class),
                    apiType.getMethod("getLastingDaysOfEachTerm", Level.class)
            );
        } catch (ClassNotFoundException | NoSuchMethodException
                 | IllegalAccessException | InvocationTargetException | LinkageError exception) {
            ModConstants.LOGGER.warn(
                    "Ecliptic Seasons is installed but its calendar API could not be resolved; seasonal weather influence is disabled",
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
            if (!Boolean.TRUE.equals(isSeasonEnabled.invoke(api, level))) {
                return SeasonalOffset.NONE;
            }
            Object solarTerm = getSolarTerm.invoke(api, level);
            if (!(solarTerm instanceof Enum<?> term) || "NONE".equals(term.name())) {
                return SeasonalOffset.NONE;
            }
            int lastingDays = Math.max(1, ((Number) getLastingDaysOfEachTerm.invoke(api, level)).intValue());
            int dayInTerm = Math.max(0, ((Number) getDayInTerm.invoke(api, level)).intValue());
            double termProgress = Math.min(0.999, dayInTerm / (double) lastingDays);
            double cyclePhase = (term.ordinal() + termProgress) / 24.0;
            return SeasonCycleProfile.temperate(cyclePhase, settings);
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException exception) {
            logFailureOnce(exception);
            return SeasonalOffset.NONE;
        }
    }

    private void logFailureOnce(Exception exception) {
        if (failureLogged) {
            return;
        }
        failureLogged = true;
        ModConstants.LOGGER.warn(
                "Ecliptic Seasons stopped supplying calendar data; Wilderness weather will use neutral seasonal influence",
                exception
        );
    }
}
