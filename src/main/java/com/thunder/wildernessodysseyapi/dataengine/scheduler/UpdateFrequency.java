package com.thunder.wildernessodysseyapi.dataengine.scheduler;

/** Default cadence families for Wilderness-owned simulation systems. */
public enum UpdateFrequency {
    EVERY_TICK(1),
    FAST(4),
    NORMAL(20),
    SLOW(100),
    EVENT_ONLY(0);

    private final int defaultIntervalTicks;

    UpdateFrequency(int defaultIntervalTicks) {
        this.defaultIntervalTicks = defaultIntervalTicks;
    }

    /** Returns the default interval, or zero for event-only systems. */
    public int defaultIntervalTicks() {
        return defaultIntervalTicks;
    }
}
