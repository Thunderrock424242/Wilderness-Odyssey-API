package com.thunder.wildernessodysseyapi.weather.client.cloud;

import com.thunder.wildernessodysseyapi.weather.api.CloudType;

/**
 * Converts authoritative atmospheric fields into Minecraft-style cloud voxels.
 *
 * <p>The weather envelope is sampled at the voxel's real world position. Wind
 * only shifts deterministic small-scale morphology, so visible cloud detail
 * moves without letting the entire cloud mass drift away from its rain cell.</p>
 */
public final class CloudCoverageModel {

    public static final int CLOUD_TILE_SIZE = 12;
    public static final int BASE_THICKNESS = 4;
    public static final double PRECIPITATION_COVERAGE_THRESHOLD = 1.0E-4;

    private CloudCoverageModel() {
    }

    /**
     * Returns normalized cloud coverage with a precipitation-driven floor.
     * Any meaningful rainy sample therefore approaches continuous overcast.
     */
    public static double coverage(CloudFieldSample field) {
        if (field == null || field.support() <= 0.0) {
            return 0.0;
        }
        double cloudCoverage = smoothstep(0.08, 0.65, field.cloudWater());
        double precipitationShape = smoothstep(
                PRECIPITATION_COVERAGE_THRESHOLD,
                0.30,
                field.precipitationIntensity()
        );
        double precipitationFloor = precipitationShape
                * (0.72 + field.precipitationIntensity() * 0.28);
        double layeredCoverage = CloudLayerProfile.evaluate(field).visibleCoverage();
        return unit(Math.max(Math.max(cloudCoverage * field.support(), layeredCoverage),
                precipitationFloor * field.support()));
    }

    /** Returns whether one 12-block cloud voxel should be occupied. */
    public static boolean isPresent(
            CloudFieldSample field,
            int worldTileX,
            int worldTileZ,
            double windDetailOffsetX,
            double windDetailOffsetZ
    ) {
        if (field == null || field.support() <= 0.0) {
            return false;
        }
        if (field.effectivePrecipitation() >= PRECIPITATION_COVERAGE_THRESHOLD) {
            return true;
        }
        double coverage = coverage(field);
        if (coverage <= 0.015) {
            return false;
        }
        return morphologyNoise(
                worldTileX,
                worldTileZ,
                windDetailOffsetX,
                windDetailOffsetZ,
                field.cloudType()
        ) < coverage;
    }

    /** Returns a blocky 4, 8, or 12 block cloud height. */
    public static int thickness(CloudFieldSample field) {
        if (field == null) {
            return BASE_THICKNESS;
        }
        double precipitation = field.effectivePrecipitation();
        double convection = Math.max(field.stormEnergy(), field.instability()) * field.support();
        if (precipitation >= 0.45 || convection >= 0.72) {
            return 12;
        }
        if (precipitation >= PRECIPITATION_COVERAGE_THRESHOLD || convection >= 0.42) {
            return 8;
        }
        return BASE_THICKNESS;
    }

    /** Returns normalized storm shading applied independently to each voxel. */
    public static double darkness(CloudFieldSample field) {
        if (field == null) {
            return 0.0;
        }
        return unit((field.cloudWater() * 0.18
                + field.precipitationIntensity() * 0.48
                + field.stormEnergy() * 0.34) * field.support());
    }

    /** Returns the translucent alpha for one occupied voxel. */
    public static double opacity(CloudFieldSample field, double multiplier) {
        double coverage = coverage(field);
        double precipitation = field == null ? 0.0 : field.effectivePrecipitation();
        CloudType.Shape shape = field == null ? CloudType.Shape.CLEAR : field.cloudType().shape();
        double baseOpacity = switch (shape) {
            case CLEAR -> 0.0;
            case WISPY -> 0.42;
            case LAYERED -> 0.72;
            case CELLULAR -> 0.64;
            case CONVECTIVE -> 0.78;
        };
        return clamp((baseOpacity + coverage * 0.20 + precipitation * 0.10) * multiplier, 0.20, 0.98);
    }

    /**
     * Produces coherent, deterministic cloud detail in world-tile space.
     * Offsets are measured in blocks and may be advanced by local wind.
     */
    static double morphologyNoise(
            int worldTileX,
            int worldTileZ,
            double windDetailOffsetX,
            double windDetailOffsetZ
    ) {
        return morphologyNoise(
                worldTileX,
                worldTileZ,
                windDetailOffsetX,
                windDetailOffsetZ,
                CloudType.CUMULUS
        );
    }

    static double morphologyNoise(
            int worldTileX,
            int worldTileZ,
            double windDetailOffsetX,
            double windDetailOffsetZ,
            CloudType type
    ) {
        double x = worldTileX + windDetailOffsetX / CLOUD_TILE_SIZE;
        double z = worldTileZ + windDetailOffsetZ / CLOUD_TILE_SIZE;
        CloudType.Shape shape = type == null ? CloudType.Shape.CELLULAR : type.shape();
        return switch (shape) {
            case CLEAR -> 1.0;
            case WISPY -> valueNoise(x / 12.0, z / 2.2, 0x3C6EF372FE94F82BL) * 0.78
                    + valueNoise(x / 4.5, z / 1.4, 0xA54FF53A5F1D36F1L) * 0.22;
            case LAYERED -> valueNoise(x / 10.0, z / 10.0, 0x510E527FADE682D1L) * 0.86
                    + valueNoise(x / 3.5, z / 3.5, 0x9B05688C2B3E6C1FL) * 0.14;
            case CELLULAR -> valueNoise(x / 6.0, z / 6.0, 0x6A09E667F3BCC909L) * 0.72
                    + valueNoise(x / 2.25, z / 2.25, 0xBB67AE8584CAA73BL) * 0.28;
            case CONVECTIVE -> valueNoise(x / 8.5, z / 8.5, 0x1F83D9ABFB41BD6BL) * 0.66
                    + valueNoise(x / 1.8, z / 1.8, 0x5BE0CD19137E2179L) * 0.34;
        };
    }

    private static double valueNoise(double x, double z, long seed) {
        long x0 = floorToLong(x);
        long z0 = floorToLong(z);
        double xAmount = smoothFraction(x - x0);
        double zAmount = smoothFraction(z - z0);
        double north = lerp(hashUnit(x0, z0, seed), hashUnit(x0 + 1, z0, seed), xAmount);
        double south = lerp(hashUnit(x0, z0 + 1, seed), hashUnit(x0 + 1, z0 + 1, seed), xAmount);
        return lerp(north, south, zAmount);
    }

    private static double hashUnit(long x, long z, long seed) {
        long value = seed;
        value ^= x * 0x9E3779B97F4A7C15L;
        value ^= z * 0xC2B2AE3D27D4EB4FL;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (double) (value >>> 11) * 0x1.0p-53;
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double amount = unit((value - edge0) / (edge1 - edge0));
        return amount * amount * (3.0 - 2.0 * amount);
    }

    private static double smoothFraction(double value) {
        double bounded = unit(value);
        return bounded * bounded * (3.0 - 2.0 * bounded);
    }

    private static double lerp(double from, double to, double amount) {
        return from + (to - from) * amount;
    }

    private static int floorToInt(double value) {
        int truncated = (int) value;
        return value < truncated ? truncated - 1 : truncated;
    }

    private static long floorToLong(double value) {
        long truncated = (long) value;
        return value < truncated ? truncated - 1L : truncated;
    }

    private static double unit(double value) {
        return clamp(Double.isFinite(value) ? value : 0.0, 0.0, 1.0);
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
