package com.thunder.wildernessodysseyapi.temporalrift.echo;

/** Pure deterministic geography: no chunks, mutable random source, or world state. */
public final class EchoRegionModel {
    /** Region width in blocks; negative coordinates use floor division. */
    public static final int REGION_SIZE = 256;

    private EchoRegionModel() {
    }

    /** Stable packed region coordinates, independent of chunk loading order. */
    public static long regionId(int x, int z) {
        return (long) Math.floorDiv(x, REGION_SIZE) & 0xffffffffL
                | (long) Math.floorDiv(z, REGION_SIZE) << 32;
    }

    /** Selects a region; an all-zero weight configuration safely means stable. */
    public static EchoStabilityLevel level(long seed, long region, int stable, int desynced, int fractured) {
        long total = Math.max(0, stable) + (long) Math.max(0, desynced) + Math.max(0, fractured);
        if (total == 0) {
            return EchoStabilityLevel.STABLE;
        }
        long sample = Math.floorMod(mix(seed ^ mix(region) ^ 0x4543484f45415254L), total);
        return sample < stable ? EchoStabilityLevel.STABLE
                : sample < (long) stable + desynced ? EchoStabilityLevel.DESYNCED : EchoStabilityLevel.FRACTURED;
    }

    /** Smooth radial falloff, zero at and beyond the configured influence radius. */
    public static double fractureInfluence(double distance, double radius, double strength) {
        if (radius <= 0 || distance >= radius || !Double.isFinite(distance)) {
            return 0;
        }
        double t = Math.max(0, Math.min(1, 1 - distance / radius));
        return Math.max(0, Math.min(1, strength)) * t * t * (3 - 2 * t);
    }

    /** Shared avalanche hash for deterministic region and sampled synchronization decisions. */
    public static long mix(long value) {
        value ^= value >>> 33;
        value *= 0xff51afd7ed558ccdL;
        value ^= value >>> 33;
        value *= 0xc4ceb9fe1a85ec53L;
        return value ^ value >>> 33;
    }
}
