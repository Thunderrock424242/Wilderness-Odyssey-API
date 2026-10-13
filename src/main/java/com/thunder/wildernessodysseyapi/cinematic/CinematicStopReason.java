package com.thunder.wildernessodysseyapi.cinematic;

/** Terminal reasons used by sequence cleanup and diagnostics. */
public enum CinematicStopReason {
    COMPLETE,
    SKIPPED,
    MANUAL_CANCEL,
    PLAYER_DIED,
    PLAYER_DISCONNECTED,
    DIMENSION_CHANGED,
    SERVER_STOPPING,
    INVALID_STATE,
    ERROR;

    /** Intentional skips finish story enrollment; interrupted sessions remain eligible for replay. */
    public boolean completesSequence() {
        return this == COMPLETE || this == SKIPPED;
    }
}
