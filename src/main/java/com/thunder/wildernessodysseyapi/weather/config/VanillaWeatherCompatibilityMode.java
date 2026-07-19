package com.thunder.wildernessodysseyapi.weather.config;

/**
 * Defines how localized weather coexists with Minecraft's global rain state.
 */
public enum VanillaWeatherCompatibilityMode {
    /** Keep global state for unmigrated/Riftfall consumers alongside localized adapters. */
    PRESERVE_GLOBAL,

    /** Suppress global precipitation for consumers not yet using the local authority. */
    SUPPRESS_GLOBAL
}
