package com.thunder.wildernessodysseyapi.weather.api;

/**
 * Derived lifecycle of a localized storm cell.
 *
 * <p>The stage is calculated from continuous atmospheric fields and is not
 * separately persisted. This avoids contradictory state while still giving
 * renderers and diagnostics a stable storm-shape vocabulary.</p>
 */
public enum StormStage {
    CALM,
    DEVELOPING,
    MATURE,
    DISSIPATING
}
