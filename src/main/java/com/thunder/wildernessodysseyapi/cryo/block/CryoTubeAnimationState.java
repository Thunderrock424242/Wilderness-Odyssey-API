package com.thunder.wildernessodysseyapi.cryo.block;

/**
 * High-level synchronized cryo-tube state reserved for a future GeckoLib renderer.
 *
 * <p>No animation asset is implied by these values; they are the stable bridge
 * that a later Blockbench/GeckoLib controller can consume.</p>
 */
public enum CryoTubeAnimationState {
    IDLE,
    WARNING,
    UNLOCK,
    OPENING,
    OPEN
}
