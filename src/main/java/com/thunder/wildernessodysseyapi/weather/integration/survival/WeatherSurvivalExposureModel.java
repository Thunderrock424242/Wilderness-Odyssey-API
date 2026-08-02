package com.thunder.wildernessodysseyapi.weather.integration.survival;

import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.simulation.WeatherHazardModel;

/**
 * Converts authoritative localized weather into bounded survival-mod effects.
 *
 * <p>The model is side-effect free so optional adapters share the same balance
 * rules without depending on either survival mod. Cold Sweat receives a
 * relative ambient-temperature offset, while thirst receives only additional
 * outdoor dehydration pressure.</p>
 */
public final class WeatherSurvivalExposureModel {

    private static final double CELSIUS_PER_MINECRAFT_UNIT = 25.0;

    private WeatherSurvivalExposureModel() {
    }

    /**
     * Returns the localized outdoor offset added after Cold Sweat's biome
     * modifier, preserving that mod's configured structures and dimensions.
     */
    public static double coldSweatOffsetCelsius(
            WeatherSample sample,
            double biomeBaselineCelsius,
            boolean exposedToSky,
            double maximumOffsetCelsius
    ) {
        if (!exposedToSky || sample == null) {
            return 0.0;
        }
        double maximum = clamp(finiteOr(maximumOffsetCelsius, 0.0), 0.0, 30.0);
        if (maximum == 0.0) {
            return 0.0;
        }

        WeatherSample weather = sample;
        double baseline = finiteOr(biomeBaselineCelsius, weather.temperature());
        double airMassOffset = weather.temperature() - baseline;
        double normalizedWind = unit(weather.wind().magnitude());
        double windChill = weather.temperature() < 10.0
                ? -2.5 * unit((10.0 - weather.temperature()) / 25.0) * normalizedWind
                : 0.0;
        double wetCooling = switch (weather.precipitationType()) {
            case RAIN -> -1.5 * weather.precipitationIntensity();
            case SNOW -> -2.5 * weather.precipitationIntensity();
            case HAIL -> -2.0 * weather.precipitationIntensity();
            case NONE -> 0.0;
        };
        double humidHeat = weather.temperature() > 24.0
                ? 2.5 * unit((weather.temperature() - 24.0) / 18.0)
                * unit((weather.humidity() - 0.55) / 0.45)
                : 0.0;

        return clamp(airMassOffset + windChill + wetCooling + humidHeat, -maximum, maximum);
    }

    /** Converts the Cold Sweat offset into that mod's Minecraft temperature units. */
    public static double coldSweatOffsetMinecraftUnits(
            WeatherSample sample,
            double biomeBaselineCelsius,
            boolean exposedToSky,
            double maximumOffsetCelsius
    ) {
        return coldSweatOffsetCelsius(
                sample,
                biomeBaselineCelsius,
                exposedToSky,
                maximumOffsetCelsius
        ) / CELSIUS_PER_MINECRAFT_UNIT;
    }

    /**
     * Returns extra Thirst Was Taken exhaustion for one configured interval.
     *
     * <p>When Cold Sweat is active, most direct heat weight is removed because
     * Thirst Was Taken already consumes Cold Sweat body temperature. Dry air,
     * wind, drought, and humidity stress remain Wilderness-owned inputs.</p>
     */
    public static double thirstExhaustionPerInterval(
            WeatherSample sample,
            boolean exposedToSky,
            boolean coldSweatIntegrated,
            double maximumExhaustion
    ) {
        if (!exposedToSky || sample == null) {
            return 0.0;
        }
        double maximum = clamp(finiteOr(maximumExhaustion, 0.0), 0.0, 0.25);
        if (maximum == 0.0) {
            return 0.0;
        }

        WeatherHazardModel.HazardProfile hazards = WeatherHazardModel.evaluate(sample);
        double heat = unit((sample.temperature() - 22.0) / 20.0);
        double dryAir = unit((0.62 - sample.humidity()) / 0.62);
        double humidHeat = heat * unit((sample.humidity() - 0.70) / 0.30);
        double dryingWind = heat * dryAir * unit(sample.wind().magnitude());
        double thermalWeight = coldSweatIntegrated ? 0.12 : 0.42;
        double exposure = heat * thermalWeight
                + dryAir * heat * 0.28
                + humidHeat * 0.18
                + dryingWind * 0.16
                + hazards.drought() * 0.34
                + hazards.heatWave() * (coldSweatIntegrated ? 0.12 : 0.30);
        return maximum * unit(exposure);
    }

    private static double unit(double value) {
        return clamp(finiteOr(value, 0.0), 0.0, 1.0);
    }

    private static double finiteOr(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
