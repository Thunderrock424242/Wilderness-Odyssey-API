package com.thunder.wildernessodysseyapi.ModPackPatches.telemetry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Utility for sampling telemetry events.
 */
public final class TelemetrySampling {
    private static final Map<String, AtomicLong> COUNTERS = new ConcurrentHashMap<>();

    private TelemetrySampling() {
    }

    public static boolean shouldSample(String key, int sampleEveryNth, double sampleRatePercent) {
        if (sampleEveryNth <= 1 && sampleRatePercent >= 100.0) {
            return true;
        }

        if (sampleEveryNth > 1) {
            long count = COUNTERS.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
            if (count % sampleEveryNth != 0) {
                return false;
            }
        }

        if (sampleRatePercent <= 0.0) {
            return false;
        }

        if (sampleRatePercent >= 100.0) {
            return true;
        }

        double roll = ThreadLocalRandom.current().nextDouble(0.0, 100.0);
        return roll < sampleRatePercent;
    }
}
