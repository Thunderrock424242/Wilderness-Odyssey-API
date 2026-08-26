package com.thunder.wildernessodysseyapi.cinematic;

/** Terminal reasons used by sequence cleanup and diagnostics. */
public enum CinematicStopReason {
    COMPLETE,
    MANUAL_CANCEL,
    PLAYER_DIED,
    PLAYER_DISCONNECTED,
    DIMENSION_CHANGED,
    SERVER_STOPPING,
    INVALID_STATE,
    ERROR
}
