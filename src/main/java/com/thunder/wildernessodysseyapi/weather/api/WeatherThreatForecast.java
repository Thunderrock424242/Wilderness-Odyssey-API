package com.thunder.wildernessodysseyapi.weather.api;

import com.thunder.wildernessodysseyapi.weather.system.WeatherSystemStage;
import com.thunder.wildernessodysseyapi.weather.system.WeatherSystemType;

import java.util.Objects;

/**
 * Immutable server forecast for one approaching localized weather system.
 *
 * <p>Distance is measured to the current system edge and ETA is the predicted
 * first intersection with that edge. The source id and lifecycle stage make
 * diagnostics possible without exposing mutable tracker state.</p>
 */
public record WeatherThreatForecast(
        WeatherThreat type,
        double intensity,
        double distanceBlocks,
        long estimatedArrivalTicks,
        double confidence,
        long sourceSystemId,
        WeatherSystemType sourceSystem,
        WeatherSystemStage sourceStage
) {

    public static final WeatherThreatForecast NONE = new WeatherThreatForecast(
            WeatherThreat.NONE,
            0.0,
            Double.POSITIVE_INFINITY,
            Long.MAX_VALUE,
            0.0,
            0L,
            null,
            null
    );

    public WeatherThreatForecast {
        type = Objects.requireNonNullElse(type, WeatherThreat.NONE);
        intensity = unit(intensity);
        boolean hasSource = type != WeatherThreat.NONE && sourceSystemId > 0L && sourceSystem != null;
        distanceBlocks = hasSource ? nonNegative(distanceBlocks) : Double.POSITIVE_INFINITY;
        estimatedArrivalTicks = hasSource ? Math.max(0L, estimatedArrivalTicks) : Long.MAX_VALUE;
        confidence = unit(confidence);
        sourceSystemId = hasSource ? sourceSystemId : 0L;
        sourceSystem = hasSource ? sourceSystem : null;
        sourceStage = hasSource
                ? Objects.requireNonNullElse(sourceStage, WeatherSystemStage.FORMING)
                : null;
    }

    /** Returns whether a tracked weather system is predicted to reach the query region. */
    public boolean incoming() {
        return type != WeatherThreat.NONE && sourceSystemId > 0L;
    }

    /**
     * Returns a normalized activity scale for optional ambient-wildlife systems.
     *
     * <p>Light rain deliberately remains fully active. Existing soundscapes can
     * opt into this signal without treating the forecast as a sound authority.</p>
     */
    public double ambientWildlifeActivityScale() {
        return switch (type) {
            case NONE, LIGHT_RAIN -> 1.0;
            case RAIN -> 0.88;
            case THUNDERSTORM -> 0.58;
            case SEVERE_STORM -> 0.30;
            case EXTREME_WEATHER -> 0.12;
        };
    }

    private static double unit(double value) {
        return Math.max(0.0, Math.min(1.0, Double.isFinite(value) ? value : 0.0));
    }

    private static double nonNegative(double value) {
        return Math.max(0.0, Double.isFinite(value) ? value : 0.0);
    }
}
