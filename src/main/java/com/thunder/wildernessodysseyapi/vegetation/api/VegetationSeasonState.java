package com.thunder.wildernessodysseyapi.vegetation.api;

/**
 * Coarse calendar state retained by one vegetation region.
 *
 * <p>The values describe plant-relevant conditions instead of mirroring any
 * external season mod's enum, which keeps compatibility modules optional.</p>
 */
public enum VegetationSeasonState {
    /** No external calendar currently owns a season phase. */
    UNKNOWN,
    /** Ordinary active growth conditions. */
    GROWING,
    /** Calendar-backed wet-season conditions. */
    WET,
    /** Calendar-backed summer or dry-season stress. */
    DRY,
    /** Calendar-backed winter dormancy. */
    DORMANT
}
