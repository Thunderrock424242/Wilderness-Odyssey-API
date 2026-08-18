package com.thunder.wildernessodysseyapi.ecosystem.api;

/**
 * Describes the server-owned high-level activity selected by the ecosystem controller.
 *
 * <p>The primary states intentionally describe broad animal intent rather than
 * pathfinding phases. This lets a herd follower inherit a leader's decision
 * without copying low-level navigation internals.</p>
 */
public enum EcosystemBehaviorState {
    IDLE,
    FORAGE,
    TRAVEL,
    DRINK,
    REST,
    SLEEP,
    ALERT,
    SEEK_SHELTER,
    FLEE,
    MIGRATE,

    /** @deprecated retained for source compatibility; new decisions use {@link #DRINK}. */
    @Deprecated(forRemoval = false)
    SEEKING_WATER,
    /** @deprecated retained for source compatibility; new decisions use {@link #DRINK}. */
    @Deprecated(forRemoval = false)
    DRINKING,
    /** @deprecated retained for source compatibility; new decisions use {@link #SEEK_SHELTER}. */
    @Deprecated(forRemoval = false)
    SEEKING_SHELTER,
    /** @deprecated retained for source compatibility; new decisions use {@link #SEEK_SHELTER}. */
    @Deprecated(forRemoval = false)
    SHELTERING,
    /** @deprecated retained for source compatibility; new decisions use {@link #FLEE}. */
    @Deprecated(forRemoval = false)
    FLEEING,
    /** @deprecated retained for source compatibility; new decisions use {@link #TRAVEL}. */
    @Deprecated(forRemoval = false)
    REGROUPING,
    /** @deprecated retained for source compatibility; predator foraging now uses {@link #FORAGE}. */
    @Deprecated(forRemoval = false)
    HUNTING
}
