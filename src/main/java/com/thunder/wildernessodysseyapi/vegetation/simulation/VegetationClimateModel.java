package com.thunder.wildernessodysseyapi.vegetation.simulation;

import com.thunder.wildernessodysseyapi.vegetation.api.VegetationClimateState;
import com.thunder.wildernessodysseyapi.vegetation.api.VegetationSeasonState;
import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.SeasonalClimateState;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;

/**
 * Pure bounded model for one due regional vegetation update.
 *
 * <p>The model advances exactly one loaded-region step. It never catches up
 * time spent unloaded, so inactive chunks perform no hidden simulation on the
 * next load.</p>
 */
public final class VegetationClimateModel {

    private VegetationClimateModel() {
    }

    /** Advances retained moisture, rainfall, drought, storm, and calendar state. */
    public static VegetationClimateState advance(
            VegetationClimateState current,
            WeatherSample weather,
            SeasonalClimateState seasonal,
            double droughtSensitivity,
            double rainRecoveryRate,
            long gameTime
    ) {
        VegetationClimateState previous = current == null
                ? VegetationClimateState.DEFAULT : current;
        WeatherSample sample = weather == null ? WeatherSample.CLEAR : weather;
        SeasonalClimateState season = seasonal == null ? SeasonalClimateState.NONE : seasonal;
        double droughtScale = clamp(droughtSensitivity, 0.0, 4.0, 1.0);
        double recovery = unit(rainRecoveryRate);

        boolean liquidRain = sample.precipitationType() == PrecipitationType.RAIN
                || sample.precipitationType() == PrecipitationType.HAIL;
        double rain = liquidRain ? sample.precipitationIntensity() : 0.0;
        double rainfallTarget = unit(rain * 0.82 + sample.surface().wetness() * 0.18);
        double rainfallRate = rain > 0.0 ? 0.16 + rain * 0.16 : 0.018;
        double recentRainfall = approach(previous.recentRainfall(), rainfallTarget, rainfallRate);

        double airDryness = unit((0.62 - sample.humidity()) / 0.62);
        double heat = unit((sample.temperature() - 16.0) / 24.0);
        double ventilation = unit(sample.wind().magnitude());
        double wetTarget = unit(
                0.10
                        + sample.surface().wetness() * 0.56
                        + recentRainfall * 0.30
                        + sample.humidity() * 0.16
        );
        double recoveryStep = rain > 0.0
                ? recovery * (0.45 + rain * 0.55)
                : 0.012;
        double dryingStep = (0.004 + airDryness * 0.008 + heat * 0.008 + ventilation * 0.003)
                * droughtScale;
        double moisture = approach(
                previous.moisture(),
                wetTarget,
                wetTarget >= previous.moisture() ? recoveryStep : dryingStep
        );

        double droughtTarget = unit(
                ((0.56 - moisture) / 0.56) * droughtScale
                        + airDryness * 0.18 * droughtScale
                        + heat * 0.12 * droughtScale
                        - recentRainfall * 0.28
        );
        double droughtRate = droughtTarget > previous.droughtLevel()
                ? 0.018 * droughtScale
                : Math.max(0.012, recovery * (0.55 + rain * 0.45));
        double drought = approach(previous.droughtLevel(), droughtTarget, droughtRate);
        double storm = unit(sample.stormEnergy() * 0.68
                + sample.precipitationIntensity() * 0.22
                + sample.instability() * 0.10);

        return new VegetationClimateState(
                moisture,
                recentRainfall,
                drought,
                storm,
                seasonState(season),
                Math.max(0L, gameTime),
                previous.lastVegetationUpdateTick(),
                previous.plantsProcessed(),
                previous.averageProcessingMicros()
        );
    }

    private static VegetationSeasonState seasonState(SeasonalClimateState season) {
        if (!season.calendarAvailable()) {
            return VegetationSeasonState.UNKNOWN;
        }
        if (season.snowSeasonFactor() >= 0.20) {
            return VegetationSeasonState.DORMANT;
        }
        if (season.fireSeasonFactor() >= 0.20) {
            return VegetationSeasonState.DRY;
        }
        if (season.evaporationMultiplier() <= 0.97) {
            return VegetationSeasonState.WET;
        }
        return VegetationSeasonState.GROWING;
    }

    private static double approach(double current, double target, double amount) {
        double rate = unit(amount);
        return unit(current + (target - current) * rate);
    }

    private static double unit(double value) {
        return clamp(value, 0.0, 1.0, 0.0);
    }

    private static double clamp(double value, double minimum, double maximum, double fallback) {
        return Math.max(minimum, Math.min(maximum, Double.isFinite(value) ? value : fallback));
    }
}
