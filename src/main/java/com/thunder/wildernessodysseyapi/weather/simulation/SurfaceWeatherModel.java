package com.thunder.wildernessodysseyapi.weather.simulation;

import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.SurfaceWeatherState;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;

/**
 * Evolves ground wetness, puddles, snowpack, and freeze/thaw memory.
 *
 * <p>The model is pure and bounded. It records climate-scale surface response;
 * block placement is handled separately on loaded columns with a strict budget.</p>
 */
public final class SurfaceWeatherModel {

    private SurfaceWeatherModel() {
    }

    /** Advances surface state by one normalized atmosphere step. */
    public static SurfaceWeatherState simulate(
            SurfaceWeatherState current,
            WeatherSample atmosphere,
            AtmosphereEnvironment environment,
            double step
    ) {
        SurfaceWeatherState surface = current == null ? SurfaceWeatherState.DRY : current;
        WeatherSample weather = atmosphere == null ? WeatherSample.CLEAR : atmosphere;
        AtmosphereEnvironment inputs = environment == null ? AtmosphereEnvironment.TEMPERATE : environment;
        double rate = unit(step);
        double precipitation = weather.precipitationIntensity();
        boolean snow = weather.precipitationType() == PrecipitationType.SNOW;
        boolean wetPrecipitation = weather.precipitationType() == PrecipitationType.RAIN
                || weather.precipitationType() == PrecipitationType.HAIL;

        double wetGain = (wetPrecipitation ? precipitation * 0.18 : snow ? precipitation * 0.035 : 0.0) * rate;
        double drying = (0.004
                + Math.max(0.0, weather.temperature() - 12.0) * 0.00045
                + weather.wind().magnitude() * 0.006
                + inputs.daylight() * 0.004) * rate;
        double wetness = unit(surface.wetness() + wetGain - drying);

        double puddleTarget = unit((wetness - 0.58) / 0.42)
                * (0.55 + Math.max(0.0, precipitation) * 0.45);
        double drainage = 0.010 + Math.max(0.0, weather.temperature() - 20.0) * 0.0004;
        double puddles = approach(surface.puddleCoverage(), puddleTarget, (puddleTarget > surface.puddleCoverage()
                ? 0.08 : drainage) * rate);

        double snowGain = snow ? precipitation * 0.055 * rate : 0.0;
        double melt = Math.max(0.0, weather.temperature() - 0.5)
                * (0.003 + inputs.daylight() * 0.003) * rate;
        double snowpack = unit(surface.snowpack() + snowGain - melt);

        double freezeTarget = weather.temperature() <= -2.0
                ? unit((wetness * 0.45 + puddles * 0.55 + snowpack * 0.18)
                * (-weather.temperature() / 12.0))
                : 0.0;
        double frozen = approach(
                surface.frozenFraction(),
                freezeTarget,
                (freezeTarget > surface.frozenFraction() ? 0.055 : 0.085) * rate
        );
        if (weather.temperature() > 3.0) {
            frozen = Math.max(0.0, frozen - weather.temperature() * 0.0025 * rate);
        }
        return new SurfaceWeatherState(wetness, puddles, snowpack, frozen);
    }

    private static double approach(double current, double target, double amount) {
        return current + (target - current) * unit(amount);
    }

    private static double unit(double value) {
        return Math.max(0.0, Math.min(1.0, Double.isFinite(value) ? value : 0.0));
    }
}
