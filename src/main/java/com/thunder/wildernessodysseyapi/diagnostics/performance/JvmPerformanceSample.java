package com.thunder.wildernessodysseyapi.diagnostics.performance;

/** Immutable JVM evidence. Negative values mean unavailable, never zero pressure. */
public record JvmPerformanceSample(long heapUsedBytes, long heapMaximumBytes, int processors,
                                   long gcCollectionMillis, double processCpuLoad, double gcTimeFraction) {
    public static JvmPerformanceSample unavailable() {
        return new JvmPerformanceSample(-1, -1, -1, -1, -1, -1);
    }
}
