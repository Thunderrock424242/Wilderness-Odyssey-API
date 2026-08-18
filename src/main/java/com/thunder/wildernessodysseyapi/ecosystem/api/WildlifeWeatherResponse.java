package com.thunder.wildernessodysseyapi.ecosystem.api;

import java.util.Locale;

/** Diagnostic classification of the localized weather response used by wildlife. */
public enum WildlifeWeatherResponse {
    NOT_SAMPLED,
    CLEAR,
    LIGHT_RAIN_IGNORED,
    HEAVY_PRECIPITATION,
    THUNDERSTORM,
    SEVERE_WIND,
    FLOODING;

    /** Returns a compact lowercase value for commands and inspection screens. */
    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Returns whether this response may justify seeking shelter. */
    public boolean requiresShelterResponse() {
        return this == HEAVY_PRECIPITATION
                || this == THUNDERSTORM
                || this == SEVERE_WIND
                || this == FLOODING;
    }
}
