package com.thunder.wildernessodysseyapi.weather.api;

/**
 * Localized precipitation form selected by the authoritative atmosphere.
 * Existing enum order is persistent data: append new phases after the original four.
 */
public enum PrecipitationType {
    NONE,
    RAIN,
    SNOW,
    HAIL,
    /** Refrozen ice pellets produced by a warm layer above colder surface air. */
    SLEET,
    /** Supercooled liquid drops that freeze on contact with cold surfaces. */
    FREEZING_RAIN;

    /** Returns whether precipitation reaches the surface as liquid water. */
    public boolean isLiquid() {
        return this == RAIN || this == FREEZING_RAIN;
    }

    /** Returns whether precipitation reaches the surface as ice or snow. */
    public boolean isFrozen() {
        return this == SNOW || isIcePellet();
    }

    /** Returns whether the existing pellet visuals can represent this phase. */
    public boolean isIcePellet() {
        return this == HAIL || this == SLEET;
    }

    /** Preserves hail's existing rain interaction while including freezing rain. */
    public boolean usesRainInteractions() {
        return isLiquid() || this == HAIL;
    }

    /** Maps snow and accumulated sleet to vanilla's frozen precipitation hooks. */
    public boolean usesSnowInteractions() {
        return this == SNOW || this == SLEET;
    }
}
