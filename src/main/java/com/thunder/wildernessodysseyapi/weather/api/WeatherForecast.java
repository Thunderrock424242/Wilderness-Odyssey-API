package com.thunder.wildernessodysseyapi.weather.api;

import com.thunder.wildernessodysseyapi.weather.system.WeatherSystemType;

/** Compact player-facing forecast derived from pressure, wind, and tracked fronts. */
public record WeatherForecast(
        WeatherPhenomenon currentPhenomenon,
        double currentIntensity,
        double pressureTrend,
        WindVector wind,
        WeatherSystemType approachingSystem,
        double distanceBlocks,
        long estimatedArrivalTicks,
        double confidence
) {
    public WeatherForecast {
        currentPhenomenon = currentPhenomenon == null ? WeatherPhenomenon.NONE : currentPhenomenon;
        currentIntensity = unit(currentIntensity);
        pressureTrend = clamp(pressureTrend, -0.20, 0.20);
        wind = wind == null ? WindVector.ZERO : wind.limited(1.0);
        distanceBlocks = approachingSystem == null ? Double.POSITIVE_INFINITY : Math.max(0.0, distanceBlocks);
        estimatedArrivalTicks = approachingSystem == null ? Long.MAX_VALUE : Math.max(0L, estimatedArrivalTicks);
        confidence = unit(confidence);
    }

    /** Human-readable pressure tendency for command and instrument displays. */
    public String pressureTendency() {
        if (pressureTrend <= -0.008) {
            return "falling";
        }
        if (pressureTrend >= 0.008) {
            return "rising";
        }
        return "steady";
    }

    private static double unit(double value) {
        return clamp(value, 0.0, 1.0);
    }

    private static double clamp(double value, double minimum, double maximum) {
        double finite = Double.isFinite(value) ? value : 0.0;
        return Math.max(minimum, Math.min(maximum, finite));
    }
}
