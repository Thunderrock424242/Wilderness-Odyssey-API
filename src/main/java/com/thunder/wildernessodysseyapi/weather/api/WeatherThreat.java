package com.thunder.wildernessodysseyapi.weather.api;

/**
 * Coarse incoming-weather severity intended for gameplay consumers.
 *
 * <p>The values describe a moving localized weather system before its edge
 * reaches the queried position. They do not replace precipitation type or the
 * continuous intensity stored in {@link WeatherSample}.</p>
 */
public enum WeatherThreat {
    NONE,
    LIGHT_RAIN,
    RAIN,
    THUNDERSTORM,
    SEVERE_STORM,
    EXTREME_WEATHER;

    /** Returns whether this threat is meaningful enough for ordinary wildlife awareness. */
    public boolean significant() {
        return ordinal() >= RAIN.ordinal();
    }
}
