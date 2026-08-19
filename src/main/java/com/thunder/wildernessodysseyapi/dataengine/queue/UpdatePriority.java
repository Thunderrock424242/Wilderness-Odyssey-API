package com.thunder.wildernessodysseyapi.dataengine.queue;

/**
 * Orders Wilderness Data Engine work from authoritative gameplay actions to
 * work that may be postponed during server load.
 */
public enum UpdatePriority {
    CRITICAL,
    HIGH,
    NORMAL,
    LOW,
    BACKGROUND;

    /** Returns whether this priority must run before {@code other}. */
    public boolean isMoreUrgentThan(UpdatePriority other) {
        return ordinal() < other.ordinal();
    }
}
