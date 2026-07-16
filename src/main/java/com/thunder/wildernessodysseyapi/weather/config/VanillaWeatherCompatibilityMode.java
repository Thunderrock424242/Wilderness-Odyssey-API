package com.thunder.wildernessodysseyapi.weather.config;

/**
 * Defines how localized weather coexists with Minecraft's global rain state.
 */
public enum VanillaWeatherCompatibilityMode {
    /** Keep global state for vanilla/Riftfall gameplay while clients render localized weather. */
    PRESERVE_GLOBAL,

    /** Suppress global precipitation so only the localized authority drives weather. */
    SUPPRESS_GLOBAL
}
