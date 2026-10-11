package com.thunder.wildernessodysseyapi.anomaly;

/** Pure bounds and scheduling policy for server-owned player-local atmosphere. */
public final class AnomalyAtmospherePolicy {
    private AnomalyAtmospherePolicy() { }

    /** Missing or non-finite samples contribute no influence. */
    public static double blend(double[] samples) {
        if (samples.length == 0) return 0;
        double total = 0;
        for (double sample : samples) {
            total += Double.isFinite(sample) ? Math.clamp(sample, 0, 1) : 0;
        }
        return total / samples.length;
    }

    /** Smoothly fades the existing rift owner's influence over 96 blocks. */
    public static double riftInfluence(double distance) {
        if (!Double.isFinite(distance)) return 0;
        double fraction = Math.clamp(distance / 96.0, 0, 1);
        return 1 - fraction * fraction * (3 - 2 * fraction);
    }

    public static boolean shouldSend(long previousTick, long tick, boolean changed) {
        return changed || tick < previousTick || tick - previousTick >= 200;
    }
}
