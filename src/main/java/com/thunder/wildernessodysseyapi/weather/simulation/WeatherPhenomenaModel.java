package com.thunder.wildernessodysseyapi.weather.simulation;

import com.thunder.wildernessodysseyapi.weather.api.AtmosphericFrontType;
import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;

/** Applies physically bounded lake, ocean, drought, heat, and hail feedback. */
public final class WeatherPhenomenaModel {

    private WeatherPhenomenaModel() {
    }

    /** Enhances one completed atmosphere step without touching world state. */
    public static WeatherSample apply(
            WeatherSample sample,
            AtmosphereEnvironment environment,
            AtmosphericFrontModel.FrontState front,
            double step
    ) {
        WeatherSample weather = sample == null ? WeatherSample.CLEAR : sample;
        AtmosphereEnvironment inputs = environment == null ? AtmosphereEnvironment.TEMPERATE : environment;
        AtmosphericFrontModel.FrontState boundary = front == null
                ? AtmosphericFrontModel.FrontState.NONE
                : front;
        double rate = unit(step);
        double lakeEffect = inputs.lakeEffectPotential(weather.temperature(), weather.wind().magnitude());
        double oceanStorm = inputs.oceanStormPotential(weather.temperature(), weather.humidity());
        double drought = unit((0.38 - weather.humidity()) / 0.38)
                * unit((weather.temperature() - 22.0) / 20.0)
                * unit((weather.pressure() - 1.0) / 0.16)
                * (1.0 - inputs.waterCoverage());
        double heat = unit((inputs.targetTemperatureCelsius(0.0) - 30.0) / 18.0)
                * unit((weather.pressure() - 0.98) / 0.18)
                * (1.0 - weather.humidity() * 0.55);

        double temperature = weather.temperature() + heat * 0.22 * rate;
        double humidity = unit(weather.humidity()
                + lakeEffect * 0.035 * rate
                + oceanStorm * 0.024 * rate
                - drought * 0.025 * rate);
        double cloudWater = unit(weather.cloudWater()
                + lakeEffect * 0.040 * rate
                + oceanStorm * 0.032 * rate
                - drought * 0.020 * rate);
        double instability = unit(weather.instability()
                + oceanStorm * 0.026 * rate
                + Math.max(0.0, inputs.seasonalStorminessOffset()) * 0.018 * rate);
        double stormEnergy = unit(weather.stormEnergy()
                + oceanStorm * (0.018 + boundary.strength() * 0.012) * rate
                - drought * 0.012 * rate);
        double verticalMotion = clamp(weather.verticalMotion()
                + lakeEffect * 0.025 * rate
                + oceanStorm * 0.020 * rate, -1.0, 1.0);
        double precipitation = weather.precipitationIntensity();
        PrecipitationType type = weather.precipitationType();
        if (lakeEffect >= 0.28 && temperature <= WeatherSample.SNOW_MAX_TEMPERATURE) {
            precipitation = unit(Math.max(precipitation, lakeEffect * 0.62));
            type = PrecipitationType.SNOW;
        }

        boolean hailColumn = precipitation >= 0.24
                && stormEnergy >= 0.70
                && instability >= 0.62
                && verticalMotion >= 0.28
                && weather.cloudDepth() >= 0.62
                && temperature > -12.0;
        if (hailColumn) {
            type = PrecipitationType.HAIL;
            precipitation = Math.max(precipitation, 0.38 + stormEnergy * 0.42);
        }
        if (precipitation <= 0.001) {
            type = PrecipitationType.NONE;
        }

        return new WeatherSample(
                temperature,
                humidity,
                weather.pressure(),
                weather.wind(),
                cloudWater,
                instability,
                stormEnergy,
                unit(precipitation),
                type,
                verticalMotion,
                unit(weather.cloudDepth() + oceanStorm * 0.020 * rate),
                weather.cloudWind(),
                weather.surface()
        );
    }

    private static double unit(double value) {
        return clamp(value, 0.0, 1.0);
    }

    private static double clamp(double value, double minimum, double maximum) {
        double finite = Double.isFinite(value) ? value : minimum;
        return Math.max(minimum, Math.min(maximum, finite));
    }
}
