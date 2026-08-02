package com.thunder.wildernessodysseyapi.weather.config;

/**
 * Selects which installed system owns weather simulation and presentation.
 */
public enum WeatherOwnershipMode {
    /** Use Wilderness weather unless a configured external weather mod is installed. */
    AUTO,

    /** Always let Wilderness weather own configured dimensions. */
    WILDERNESS,

    /** Hand weather ownership to vanilla or an external weather mod. */
    EXTERNAL
}
