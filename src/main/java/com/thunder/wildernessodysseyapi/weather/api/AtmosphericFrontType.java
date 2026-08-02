package com.thunder.wildernessodysseyapi.weather.api;

/**
 * Derived type of air-mass boundary crossing an atmospheric cell.
 *
 * <p>Fronts are calculated from neighboring immutable samples rather than
 * stored as independent weather presets.</p>
 */
public enum AtmosphericFrontType {
    NONE,
    WARM,
    COLD,
    STATIONARY,
    OCCLUDED
}
