package com.thunder.wildernessodysseyapi.ecosystem.api;

/** Soft species preference used by the bounded ecosystem shelter locator. */
public enum ShelterPreference {
    /** Accept the nearest standable position with overhead cover. */
    ANY_COVER,
    /** Prefer leaf canopy and dense forest cover, while retaining a safe fallback. */
    DENSE_CANOPY,
    /** Prefer nearby solid cave, overhang, or built-roof cover. */
    SOLID_OVERHEAD,
    /** Do not perform land-shelter behavior for this species. */
    NONE
}
