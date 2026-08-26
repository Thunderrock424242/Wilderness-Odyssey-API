package com.thunder.wildernessodysseyapi.cinematic;

/** Describes how much player control a server-authored cinematic stage permits. */
public enum CinematicControlPolicy {
    /** Movement, gameplay interactions, inventory screens, and perspective changes are suppressed. */
    LOCKED,
    /** Presentation remains active while ordinary player control has been returned. */
    PRESENTATION_ONLY
}
