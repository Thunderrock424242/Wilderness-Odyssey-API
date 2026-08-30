package com.thunder.wildernessodysseyapi.weather.client.precipitation;

import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;

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
        double edgeCrossfade = 1.0 - smoothstep(0.78, 1.0, normalized);
        return unit((float) (Math.max(0.0, radial) * edgeCrossfade * intensity));
    }

    /**
     * Selects a stable subset of world columns so rainfall reads as individual
     * streaks instead of an opaque wall while the player moves.
     */
    public static boolean shouldRenderNearColumn(
            int blockX,
            int blockZ,
            double intensity,
            double configuredDensity
    ) {
        double weatherAmount = Math.sqrt(unit(intensity));
        double density = unit(configuredDensity) * (0.38 + weatherAmount * 0.62);
        return columnNoise(blockX, blockZ, 0xD1B54A32D192ED03L) < density;
    }

    /** Applies the client opacity preference without allowing invalid alpha. */
    public static float scaledAlpha(float alpha, double opacityMultiplier) {
        return unit((float) (alpha * Math.max(0.0, finiteOrZero(opacityMultiplier))));
    }

    /** Returns whether the camera-local phase may use Minecraft's rain ambience. */
    public static boolean usesRainSound(PrecipitationType type, double intensity) {
        return (type == PrecipitationType.RAIN || type == PrecipitationType.HAIL)
                && intensity > PRECIPITATION_EPSILON;
    }

    /** Uses fewer vertical texture repeats so snow reads as flakes instead of a white wall. */
    static float snowTextureVerticalScale() {
        return 1.0F / 12.0F;
    }

    /** Softens the stock snow texture while preserving the sampled storm intensity. */
    static float snowAlpha(float alpha) {
        return scaledAlpha(alpha, 0.68);
    }

    /** Keeps snow visible at night without forcing every flake close to full brightness. */
    static int softenedSnowLight(int packedLight) {
        int sky = packedLight >> 16 & 65535;
        int block = packedLight & 65535;
        return ((sky * 7 + 240) / 8) << 16 | (block * 7 + 240) / 8;
    }

    /** Returns the bounded pellet count used for near hail or its sparse distant silhouette. */
    static int hailPelletCount(boolean distant) {
        return distant ? 2 : 5;
    }

    /** Returns deterministic fast-fall progress for one hail pellet. */
    static float hailPelletProgress(int blockX, int blockZ, int pellet, double renderTicks) {
        long pelletSalt = 0xDB4F0B9175AE2165L + (long) pellet * 0x9E3779B97F4A7C15L;
        double phase = columnNoise(blockX, blockZ, pelletSalt);
        double speed = 0.030 + columnNoise(blockX, blockZ, pelletSalt ^ 0x94D049BB133111EBL) * 0.012;
        double progress = phase + finiteOrZero(renderTicks) * speed;
        return (float) (progress - Math.floor(progress));
    }

    /** Returns a sub-block icy pellet size with stable per-column variation. */
    static float hailPelletSize(int blockX, int blockZ, int pellet, boolean distant) {
        double variation = columnNoise(
                blockX,
                blockZ,
                0xBF58476D1CE4E5B9L + (long) pellet * 0x632BE59BD9B4E019L
        );
        float size = (float) (0.050 + variation * 0.040);
        return distant ? size * 1.35F : size;
    }

    /** Tones down icy pellets so they do not resemble glowing white splash particles. */
    static float hailAlpha(float alpha, boolean distant) {
        return scaledAlpha(alpha, distant ? 0.46 : 0.72);
    }

    /** Density controls element count; opacity remains a secondary response. */
    static int elementCount(byte zone, boolean snow, double intensity, double configuredDensity) {
        double amount = unit(intensity) * unit(configuredDensity);
        if (zone == 2) {
            return 1;
        }
        int maximum = zone == 0 ? (snow ? 6 : 5) : (snow ? 3 : 2);
        return Math.max(1, Math.min(maximum, 1 + (int) Math.floor(amount * maximum)));
    }

    /** Deterministic world-space fall phase with velocity and gust variation. */
    static float fallProgress(
            int blockX,
            int blockZ,
            int element,
            double renderTicks,
            boolean snow,
            double gustFactor
    ) {
        long salt = 0xA24BAED4963EE407L + (long) element * 0x9E3779B97F4A7C15L;
        double phase = columnNoise(blockX, blockZ, salt);
        double variation = 0.82 + columnNoise(blockX, blockZ, salt ^ 0x9FB21C651E98DF25L) * 0.36;
        double gust = 1.0 + unit(gustFactor) * (snow ? 0.10 : 0.24);
        double speed = (snow ? 0.012 : 0.052) * variation * gust;
        double progress = phase + finiteOrZero(renderTicks) * speed;
        return (float) (progress - Math.floor(progress));
    }

    /** Short world-space streak length; mid-field elements are intentionally cheaper and longer. */
    static float streakLength(int blockX, int blockZ, int element, byte zone, boolean snow) {
        double variation = columnNoise(
                blockX,
                blockZ,
                0xD6E8FEB86659FD93L + (long) element * 0x632BE59BD9B4E019L
        );
        if (snow) {
            return (float) ((zone == 0 ? 0.22 : 0.34) + variation * (zone == 0 ? 0.34 : 0.48));
        }
        return (float) ((zone == 0 ? 1.25 : 3.5) + variation * (zone == 0 ? 2.75 : 4.5));
    }

    /** Subtle per-element width variation prevents a repeated transparent-sheet silhouette. */
    static float streakHalfWidth(int blockX, int blockZ, int element, byte zone, boolean snow) {
        double variation = columnNoise(
                blockX,
                blockZ,
                0xC13FA9A902A6328FL + (long) element * 0x94D049BB133111EBL
        );
        if (snow) {
            return (float) ((zone == 0 ? 0.055 : 0.09) + variation * (zone == 0 ? 0.07 : 0.08));
        }
        return (float) ((zone == 0 ? 0.018 : 0.045) + variation * (zone == 0 ? 0.025 : 0.045));
    }

    /** Stable alpha variation breaks up uniform repeated streaks. */
    static float elementAlpha(int blockX, int blockZ, int element, float alpha, boolean snow) {
        double variation = 0.68 + columnNoise(
                blockX,
                blockZ,
                0x91E10DA5C79E7B1DL + (long) element * 0xBF58476D1CE4E5B9L
        ) * 0.32;
        return scaledAlpha(alpha, variation * (snow ? 0.82 : 1.0));
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

    private static double unit(double value) {
        return Math.max(0.0, Math.min(1.0, Double.isFinite(value) ? value : 0.0));
    }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }
}
