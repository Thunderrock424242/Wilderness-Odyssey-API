package com.thunder.wildernessodysseyapi.performance.tickengine;

/** Smoothed server load state used only to govern optional Wilderness Odyssey work. */
public enum TickPressure {
    RELAXED,
    BUSY,
    HIGH,
    CRITICAL,
    OVERLOADED;

    /** Returns the next healthier state without jumping multiple recovery levels. */
    public TickPressure recoverOneLevel() {
        return switch (this) {
            case RELAXED -> RELAXED;
            case BUSY -> RELAXED;
            case HIGH -> BUSY;
            case CRITICAL -> HIGH;
            case OVERLOADED -> CRITICAL;
        };
    }
}
