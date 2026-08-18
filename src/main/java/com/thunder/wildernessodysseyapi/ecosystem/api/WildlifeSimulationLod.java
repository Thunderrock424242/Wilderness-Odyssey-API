package com.thunder.wildernessodysseyapi.ecosystem.api;

/**
 * Distance-based amount of individual environmental behavior simulated for an animal.
 */
public enum WildlifeSimulationLod {
    /** Full cached environmental decisions and temporary navigation actions. */
    ACTIVE,
    /** Full decisions at a reduced frequency. */
    NEAR,
    /** Schedule-only group/ecosystem state without individual searches or pathfinding. */
    DISTANT,
    /** No individual environmental AI until the animal becomes relevant again. */
    DORMANT
}
