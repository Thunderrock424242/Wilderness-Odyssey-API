package com.thunder.wildernessodysseyapi.weather.api;

/**
 * Immutable seasonal inputs exposed by the localized-weather authority.
 *
 * <p>The record deliberately exposes only bounded climate influences, not an
 * optional mod's calendar classes. Consumers can therefore react to seasons
 * without acquiring a hard dependency on any particular season mod.</p>
 *
 * @param temperatureOffsetCelsius seasonal air-temperature adjustment
 * @param evaporationMultiplier seasonal surface-evaporation multiplier
 * @param fireSeasonFactor normalized warm or dry-season strength
 * @param snowSeasonFactor normalized winter snowfall eligibility
 * @param calendarAvailable whether an external calendar supplied the state
 */
public record SeasonalClimateState(
        double temperatureOffsetCelsius,
        double evaporationMultiplier,
        double fireSeasonFactor,
        double snowSeasonFactor,
        boolean calendarAvailable
) {

    /** Neutral state used when no season authority is available. */
    public static final SeasonalClimateState NONE = new SeasonalClimateState(
            0.0,
            1.0,
            0.0,
            0.0,
            false
    );

    /** Clamps optional-integration values before they cross the public API boundary. */
    public SeasonalClimateState {
        temperatureOffsetCelsius = clamp(temperatureOffsetCelsius, -30.0, 30.0, 0.0);
        evaporationMultiplier = clamp(evaporationMultiplier, 0.25, 2.0, 1.0);
        fireSeasonFactor = clamp(fireSeasonFactor, 0.0, 1.0, 0.0);
        snowSeasonFactor = clamp(snowSeasonFactor, 0.0, 1.0, 0.0);
    }

    private static double clamp(double value, double minimum, double maximum, double fallback) {
        return Math.max(minimum, Math.min(maximum, Double.isFinite(value) ? value : fallback));
    }
}
