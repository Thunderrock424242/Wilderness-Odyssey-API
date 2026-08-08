package com.thunder.wildernessodysseyapi.weather.integration;

import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;

import java.util.Objects;

/**
 * Translates vanilla weather-command state into localized atmospheric samples.
 *
 * <p>Vanilla owns command parsing, permissions, duration selection, and command
 * feedback. The localized weather authority uses this adapter only to mirror
 * the resulting clear, rain, or thunder state into its dimension-owned cells.</p>
 */
public final class VanillaWeatherCommandAdapter {

    private VanillaWeatherCommandAdapter() {
    }

    /**
     * Resolves the vanilla weather flags passed to
     * {@code ServerLevel.setWeatherParameters}.
     */
    public static State fromParameters(boolean raining, boolean thundering) {
        if (!raining) {
            return State.CLEAR;
        }
        return thundering ? State.THUNDER : State.RAIN;
    }

    /**
     * Applies one vanilla command state without discarding local temperature or wind.
     *
     * <p>This compatibility overload retains the original cold-cell behavior.
     * The live authority supplies explicit biome/season snow eligibility.</p>
     */
    public static WeatherSample apply(WeatherSample current, State state) {
        return apply(current, state, true);
    }

    /** Applies one vanilla command state with authoritative natural-snow eligibility. */
    public static WeatherSample apply(WeatherSample current, State state, boolean snowClimateEligible) {
        WeatherSample old = Objects.requireNonNullElse(current, WeatherSample.CLEAR);
        return switch (Objects.requireNonNull(state, "state")) {
            case CLEAR -> new WeatherSample(
                    old.temperature(),
                    Math.min(old.humidity(), 0.55),
                    Math.max(old.pressure(), 1.0),
                    old.wind(),
                    0.0,
                    Math.min(old.instability(), 0.25),
                    0.0,
                    0.0,
                    PrecipitationType.NONE,
                    old.verticalMotion(),
                    old.cloudDepth(),
                    old.cloudWind(),
                    old.surface()
            );
            case RAIN -> precipitation(old, snowClimateEligible, 0.92, 0.96, 0.88, 0.58, 0.48, 0.90);
            case THUNDER -> precipitation(old, snowClimateEligible, 0.96, 0.90, 0.96, 0.85, 0.90, 1.0);
        };
    }

    private static WeatherSample precipitation(
            WeatherSample old,
            boolean snowClimateEligible,
            double humidity,
            double pressure,
            double cloudWater,
            double instability,
            double stormEnergy,
            double precipitationIntensity
    ) {
        PrecipitationType type = snowClimateEligible
                && old.temperature() <= WeatherSample.SNOW_MAX_TEMPERATURE
                ? PrecipitationType.SNOW
                : PrecipitationType.RAIN;
        return new WeatherSample(
                old.temperature(),
                Math.max(humidity, old.humidity()),
                Math.min(pressure, old.pressure()),
                old.wind(),
                Math.max(cloudWater, old.cloudWater()),
                Math.max(instability, old.instability()),
                stormEnergy,
                precipitationIntensity,
                type,
                old.verticalMotion(),
                old.cloudDepth(),
                old.cloudWind(),
                old.surface()
        );
    }

    /** Vanilla weather states that can be produced by the command. */
    public enum State {
        CLEAR,
        RAIN,
        THUNDER
    }
}
