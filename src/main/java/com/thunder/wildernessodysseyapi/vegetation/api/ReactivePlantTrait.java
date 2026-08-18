package com.thunder.wildernessodysseyapi.vegetation.api;

/** Declares which regional conditions a registered plant is prepared to use. */
public enum ReactivePlantTrait {
    /** Plant can expose an open and closed flower presentation. */
    FLOWER,
    /** Plant can present wet, lush, recovering, or dry moisture states. */
    MOISTURE_REACTIVE,
    /** Plant can react to the coarse external calendar state. */
    SEASON_REACTIVE,
    /** Plant can use sustained wetness for bounded spread opportunities. */
    MUSHROOM,
    /** Reserved for future environment-aware snow retention. */
    SNOW_REACTIVE,
    /** Reserved for future seasonal leaf-litter contributors. */
    LEAF_LITTER_SOURCE
}
