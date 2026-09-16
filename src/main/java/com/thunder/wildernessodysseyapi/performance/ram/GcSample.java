package com.thunder.wildernessodysseyapi.performance.ram;

public record GcSample(
        long timestampMillis,
        String collectorName,
        String action,
        String cause,
        long durationMillis,
        long heapBeforeBytes,
        long heapAfterBytes
) {
    public long freedBytes() {
        return Math.max(0L, heapBeforeBytes - heapAfterBytes);
    }
}
