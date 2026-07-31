package com.thunder.wildernessodysseyapi.weather.client.precipitation;

/**
 * Pure quality, opacity, and deterministic-motion rules for precipitation.
 *
 * <p>Keeping these calculations outside the renderer makes the hard column
 * budget and camera-edge behavior independently testable.</p>
 */
public final class PrecipitationVisualModel {

    public static final double PRECIPITATION_EPSILON = 1.0E-4;

    private PrecipitationVisualModel() {
    }

    /** Matches Minecraft's radial near-rain falloff while using column intensity. */
    public static float nearAlpha(double intensity, double distance, int radius, boolean snow) {
        if (intensity <= PRECIPITATION_EPSILON || radius <= 0) {
            return 0.0F;
        }
        double normalized = Math.max(0.0, distance) / radius;
        double radial = (1.0 - normalized * normalized) * (snow ? 0.30 : 0.50) + 0.50;
        return unit((float) (Math.max(0.0, radial) * intensity));
    }

    /** Fades sparse distant curtains before the storm-fog far plane. */
    public static float distantAlpha(
            double intensity,
            double distance,
            int nearRadius,
            int farRadius
    ) {
        if (intensity <= PRECIPITATION_EPSILON || farRadius <= nearRadius) {
            return 0.0F;
        }
        double amount = smoothstep(nearRadius, farRadius, distance);
        return unit((float) (intensity * (1.0 - amount) * 0.42));
    }

    /**
     * Returns the top-of-column offset needed for precipitation that lands
     * downwind at the unshifted bottom coordinate.
     */
    public static float topWindOffset(
            double windComponent,
            double columnHeight,
            double maximumSlantBlocks,
            boolean snow
    ) {
        double boundedWind = Math.max(-1.0, Math.min(1.0, finiteOrZero(windComponent)));
        double heightResponse = Math.min(1.0, Math.max(0.0, columnHeight) / 32.0);
        double drag = snow ? 1.0 : 0.68;
        double maximum = Math.max(0.0, finiteOrZero(maximumSlantBlocks));
        return (float) (-boundedWind * maximum * heightResponse * drag);
    }

    /**
     * Returns a symmetric lattice radius whose circle cannot exceed the cap.
     */
    public static int boundedDistantRadiusBlocks(int requestedBlocks, int spacingBlocks, int maximumShafts) {
        int spacing = clamp(spacingBlocks, 1, 1_024);
        int requestedCells = Math.max(0, requestedBlocks / spacing);
        int cap = Math.max(0, maximumShafts);
        int radiusCells = requestedCells;
        while (radiusCells > 0 && latticePointCount(radiusCells) > cap) {
            radiusCells--;
        }
        return radiusCells * spacing;
    }

    /** Returns the exact number of integer lattice points in a circular radius. */
    static int latticePointCount(int radiusCells) {
        int radius = Math.max(0, radiusCells);
        int count = 0;
        for (int z = -radius; z <= radius; z++) {
            for (int x = -radius; x <= radius; x++) {
                if ((long) x * x + (long) z * z <= (long) radius * radius) {
                    count++;
                }
            }
        }
        return count;
    }

    /** Returns a stable pseudo-random unit value for one world column. */
    static double columnNoise(int blockX, int blockZ, long salt) {
        long value = salt;
        value ^= (long) blockX * 0x9E3779B97F4A7C15L;
        value ^= (long) blockZ * 0xC2B2AE3D27D4EB4FL;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (double) (value >>> 11) * 0x1.0p-53;
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double amount = Math.max(0.0, Math.min(1.0, (value - edge0) / (edge1 - edge0)));
        return amount * amount * (3.0 - 2.0 * amount);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float unit(float value) {
        return Math.max(0.0F, Math.min(1.0F, Float.isFinite(value) ? value : 0.0F));
    }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }
}
