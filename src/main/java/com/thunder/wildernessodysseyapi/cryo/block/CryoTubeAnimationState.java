package com.thunder.wildernessodysseyapi.cryo.block;

/** High-level synchronized states consumed by the cryo tube's GeckoLib animation controller. */
public enum CryoTubeAnimationState {
    IDLE,
    SUSPENDED,
    DIAGNOSTIC,
    REWARMING,
    CARDIAC_PACING,
    DRAINING,
    MASK_RELEASE,
    OPENING,
    OPEN
}
