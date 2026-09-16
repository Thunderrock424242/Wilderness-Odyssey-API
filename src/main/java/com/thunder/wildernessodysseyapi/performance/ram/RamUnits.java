package com.thunder.wildernessodysseyapi.performance.ram;

final class RamUnits {
    static final long MIB = 1024L * 1024L;
    static final long GIB = 1024L * 1024L * 1024L;

    private RamUnits() {}

    static double gib(long bytes) {
        return bytes <= 0 ? 0.0 : (double) bytes / GIB;
    }

    static long ceilGiB(long bytes) {
        if (bytes <= 0) return 0;
        return ((bytes + GIB - 1) / GIB) * GIB;
    }

    static long clamp(long value, long min, long max) {
        return Math.max(min, Math.min(max, value));
    }
}
