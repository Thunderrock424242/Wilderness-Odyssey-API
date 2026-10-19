package com.thunder.wildernessodysseyapi.watersystem.water.hydrology;

/**
 * Read-only continuous background current derived from existing Manning vectors.
 * Convex interpolation never creates discharge, stage, inventory or finite cells.
 */
public final class RiverCurrentField {
    private static final float TICKS_PER_SECOND = 20.0f;
    private RiverCurrentField() { }

    /**
     * Blends the four surrounding chunk-center samples. Missing, dry or unrelated
     * basins retain the home vector. The caller supplies cached/loaded snapshots.
     */
    public static Sample interpolate(double worldX, double worldZ, Sample home, Sampler sampler) {
        if (!home.surfaceWater() || !Double.isFinite(worldX) || !Double.isFinite(worldZ)) return home;
        double gridX = (worldX - 8.0) / 16.0;
        double gridZ = (worldZ - 8.0) / 16.0;
        if (gridX < Integer.MIN_VALUE || gridX >= Integer.MAX_VALUE
                || gridZ < Integer.MIN_VALUE || gridZ >= Integer.MAX_VALUE) return home;
        int minimumX = (int) Math.floor(gridX);
        int minimumZ = (int) Math.floor(gridZ);
        double tx = gridX - minimumX;
        double tz = gridZ - minimumZ;
        double currentX = 0;
        double currentZ = 0;
        for (int z = 0; z < 2; z++) for (int x = 0; x < 2; x++) {
            double weight = (x == 0 ? 1 - tx : tx) * (z == 0 ? 1 - tz : tz);
            if (weight <= 0) continue;
            Sample sample = sampler.sample(minimumX + x, minimumZ + z);
            if (sample == null || !sample.surfaceWater() || sample.basinId() != home.basinId()) sample = home;
            currentX += sample.currentX() * weight;
            currentZ += sample.currentZ() * weight;
        }
        return new Sample(home.basinId(), (float) currentX, (float) currentZ, true);
    }

    /**
     * Returns blocks/second: canonical motion already uses SI seconds, while the
     * compatibility-preserved watershed snapshot stores blocks/tick. Select one
     * absolute vector owner after converting units, never add the same motion twice.
     */
    public static float currentX(float detailedX, float detailedZ, float backgroundX) {
        return hasDetailedMotion(detailedX, detailedZ) ? finite(detailedX) : physicalVelocity(backgroundX);
    }

    /** Selects the same vector owner as currentX, including a zero transverse component. */
    public static float currentZ(float detailedX, float detailedZ, float backgroundZ) {
        return hasDetailedMotion(detailedX, detailedZ) ? finite(detailedZ) : physicalVelocity(backgroundZ);
    }

    /** Converts a packed regional velocity to canonical/entity velocity units without changing storage. */
    public static float physicalVelocity(float blocksPerTick) {
        return finite(finite(blocksPerTick) * TICKS_PER_SECOND);
    }

    private static boolean hasDetailedMotion(float x, float z) {
        return Math.abs(finite(x)) + Math.abs(finite(z)) > 1.0e-6f;
    }

    private static float finite(float value) { return Float.isFinite(value) ? value : 0; }

    /** Currents retain the existing watershed snapshot units (blocks per tick). */
    public record Sample(long basinId, float currentX, float currentZ, boolean surfaceWater) {
        public Sample {
            currentX = finite(currentX);
            currentZ = finite(currentZ);
        }
    }

    @FunctionalInterface
    public interface Sampler { Sample sample(int chunkX, int chunkZ); }
}
