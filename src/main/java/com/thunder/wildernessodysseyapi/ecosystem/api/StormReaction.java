package com.thunder.wildernessodysseyapi.ecosystem.api;

/** High-level wildlife response to the shared incoming-weather forecast. */
public enum StormReaction {
    NORMAL,
    WAITING_FOR_LEADER,
    ALERT,
    SEEK_SHELTER;

    /** Returns whether this response should temporarily suppress idle wandering. */
    public boolean active() {
        return this == ALERT || this == SEEK_SHELTER;
    }
}
