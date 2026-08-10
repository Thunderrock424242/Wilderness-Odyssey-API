package com.thunder.wildernessodysseyapi.weather.severe;

import com.thunder.wildernessodysseyapi.weather.system.WeatherSystemType;
import com.thunder.wildernessodysseyapi.weather.system.WeatherSystemStage;

/**
 * Defines which severe systems may apply physical wind to nearby entities.
 *
 * <p>Ordinary storms and fronts remain atmospheric simulation inputs only.
 * A tornado or warm-ocean cyclone may move players and other living entities
 * after the severe identity survives its forming stage. That short maturity
 * boundary prevents one borderline thunderstorm sample from producing an
 * unexplained physical shove.</p>
 */
public final class SevereWeatherWindPolicy {

    private SevereWeatherWindPolicy() {
    }

    /** Returns whether the supplied system may apply bounded wind movement. */
    public static boolean canApplyEntityWind(WeatherSystemType type, WeatherSystemStage stage) {
        return type != null
                && type.severe()
                && stage != null
                && stage != WeatherSystemStage.FORMING;
    }
}
