package com.thunder.wildernessodysseyapi.weather.simulation;

import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.WindVector;

/** Classifies precipitation from wet-bulb thermal structure; calendars never grant snow permission. */
public final class PrecipitationPhaseModel {
    private PrecipitationPhaseModel() { }

    /** Legacy single-level call derives a lapse-rate column from the actual air temperature. */
    public static PrecipitationType classify(double intensity, double airTemperatureCelsius,
            double humidity, AtmosphereEnvironment environment) {
        return classify(intensity, AtmosphericColumn.initial(airTemperatureCelsius, 1013.25, humidity,
                WindVector.ZERO, WindVector.ZERO), 0, 0, 2000);
    }

    /**
     * Integrates warm-layer melting and underlying cold-layer refreezing in degree-metres.
     * Thresholds approximate hydrometeor residence time, rather than a full drop-size spectrum.
     */
    public static PrecipitationType classify(double intensity, AtmosphericColumn column,
            double instabilityJoulesPerKg, double updraftMetresPerSecond, double cloudDepthMetres) {
        if (!Double.isFinite(intensity) || intensity <= 0.0) {
            return PrecipitationType.NONE;
        }
        if (instabilityJoulesPerKg >= 1500 && updraftMetresPerSecond >= 10
                && column.upper().temperatureCelsius() <= -10 && cloudDepthMetres >= 2500) {
            return PrecipitationType.HAIL;
        }
        double warmArea = 0;
        double surfaceColdArea = 0;
        double surfaceColdDepth = 0;
        boolean reachedWarmLayer = false;
        for (int i = 1; i < 4; i++) {
            AtmosphericLayer bottom = column.layer(i - 1);
            AtmosphericLayer top = column.layer(i);
            double t0 = bottom.wetBulbCelsius();
            double t1 = top.wetBulbCelsius();
            double depth = top.heightMetres() - bottom.heightMetres();
            warmArea += positiveArea(t0, t1, depth);
            if (!reachedWarmLayer) {
                if (t0 <= 0 && t1 <= 0) {
                    surfaceColdArea += -(t0 + t1) * depth * 0.5;
                    surfaceColdDepth += depth;
                } else if (t0 < 0) {
                    double coldDepth = depth * -t0 / (t1 - t0);
                    surfaceColdArea += -t0 * coldDepth * 0.5;
                    surfaceColdDepth += coldDepth;
                }
            }
            reachedWarmLayer |= t0 > 0 || t1 > 0;
        }
        double surfaceWetBulb = column.surface().wetBulbCelsius();
        if (warmArea < 150 && surfaceWetBulb <= 1.0) {
            return PrecipitationType.SNOW;
        }
        if (surfaceWetBulb <= 0) {
            return surfaceColdArea >= 750 && surfaceColdDepth >= 300
                    ? PrecipitationType.SLEET : PrecipitationType.FREEZING_RAIN;
        }
        return PrecipitationType.RAIN;
    }

    private static double positiveArea(double bottom, double top, double depth) {
        if (bottom >= 0 && top >= 0) {
            return (bottom + top) * depth * 0.5;
        }
        if (bottom <= 0 && top <= 0) {
            return 0;
        }
        double warm = Math.max(bottom, top);
        return warm * depth * warm / Math.abs(top - bottom) * 0.5;
    }

    /** Compatibility query based only on environmental temperature, not a season permission bit. */
    @Deprecated
    public static boolean supportsNaturalSnow(AtmosphereEnvironment environment) {
        AtmosphereEnvironment climate = environment == null ? AtmosphereEnvironment.TEMPERATE : environment;
        return climate.targetTemperatureCelsius(0) <= 1.5;
    }
}
