package com.thunder.wildernessodysseyapi.performance.ram;

public record RamSample(
        long timestampMillis,
        long heapUsedBytes,
        long heapCommittedBytes,
        long heapMaxBytes,
        long nonHeapUsedBytes,
        long systemTotalBytes,
        long systemFreeBytes
) {}
