package com.thunder.wildernessodysseyapi.weather.api;

import com.thunder.wildernessodysseyapi.weather.simulation.WeatherAuthority;

/**
 * Stable public door into localized weather queries.
 *
 * <p>Consumers should depend on {@link WeatherQuery}, not atmosphere storage,
 * cell coordinates, or network snapshots. This preserves the singular server
 * authority while allowing its implementation to evolve.</p>
 */
public final class WeatherServices {

    private static final WeatherQuery QUERY = WeatherAuthority.get();

    private WeatherServices() {
    }

    /** Returns the process-wide query facade resolved against supplied levels. */
    public static WeatherQuery query() {
        return QUERY;
    }
}
