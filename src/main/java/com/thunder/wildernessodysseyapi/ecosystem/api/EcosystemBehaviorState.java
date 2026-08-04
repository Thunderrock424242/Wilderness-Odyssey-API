package com.thunder.wildernessodysseyapi.ecosystem.api;

/** Describes the server-owned high-level activity selected by the ecosystem controller. */
public enum EcosystemBehaviorState {
    IDLE,
    SEEKING_WATER,
    DRINKING,
    SEEKING_SHELTER,
    SHELTERING,
    FLEEING,
    REGROUPING,
    HUNTING
}
