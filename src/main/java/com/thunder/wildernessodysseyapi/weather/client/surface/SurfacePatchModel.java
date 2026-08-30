package com.thunder.wildernessodysseyapi.weather.client.surface;

import java.util.ArrayList;
import java.util.List;

/** Pure deterministic noise, flatness, and contour rules for wet-surface meshes. */
public final class SurfacePatchModel {

    private static final double NOISE_SCALE = 1.0D / 5.0D;

    private SurfacePatchModel() {
    }

    /** Returns a continuous world-space coverage field shared by neighboring blocks. */
    public static float field(double worldX, double worldZ, double coverage, long salt) {
        double boundedCoverage = unit(coverage);
        double threshold = 0.12D + boundedCoverage * 0.78D;
        return (float) (threshold - valueNoise(worldX * NOISE_SCALE, worldZ * NOISE_SCALE, salt));
    }

    /** Puddles require a level center and four-neighbor surface. */
    public static boolean flatEnough(int center, int north, int east, int south, int west, int maximumStep) {
        if (center == Integer.MIN_VALUE) {
            return false;
        }
        int step = Math.max(0, maximumStep);
        return validNeighbor(center, north, step)
                && validNeighbor(center, east, step)
                && validNeighbor(center, south, step)
                && validNeighbor(center, west, step);
    }

    /**
     * Triangulates one square from four connected corner field values.
     *
     * <p>Splitting around the center resolves saddle cases without producing a
     * giant square sticker. Adjacent cells share identical edge intersections.</p>
     */
    public static List<Triangle> triangulate(float northWest, float northEast, float southEast, float southWest) {
        float center = (northWest + northEast + southEast + southWest) * 0.25F;
        List<Triangle> triangles = new ArrayList<>(8);
        clipTriangle(triangles,
                new Point(0.5F, 0.5F, center),
                new Point(0.0F, 0.0F, northWest),
                new Point(1.0F, 0.0F, northEast));
        clipTriangle(triangles,
                new Point(0.5F, 0.5F, center),
                new Point(1.0F, 0.0F, northEast),
                new Point(1.0F, 1.0F, southEast));
        clipTriangle(triangles,
                new Point(0.5F, 0.5F, center),
                new Point(1.0F, 1.0F, southEast),
                new Point(0.0F, 1.0F, southWest));
        clipTriangle(triangles,
                new Point(0.5F, 0.5F, center),
                new Point(0.0F, 1.0F, southWest),
                new Point(0.0F, 0.0F, northWest));
        return List.copyOf(triangles);
    }

    private static void clipTriangle(List<Triangle> destination, Point first, Point second, Point third) {
        Point[] input = {first, second, third};
        Point[] output = new Point[6];
        int outputCount = 0;
        Point previous = input[input.length - 1];
        boolean previousInside = previous.value >= 0.0F;
        for (Point current : input) {
            boolean currentInside = current.value >= 0.0F;
            if (currentInside != previousInside) {
                output[outputCount++] = intersection(previous, current);
            }
            if (currentInside) {
                output[outputCount++] = current;
            }
            previous = current;
            previousInside = currentInside;
        }
        if (outputCount < 3) {
            return;
        }
        Point root = output[0];
        for (int index = 1; index < outputCount - 1; index++) {
            Point secondPoint = output[index];
            Point thirdPoint = output[index + 1];
            destination.add(new Triangle(
                    root.x, root.z,
                    secondPoint.x, secondPoint.z,
                    thirdPoint.x, thirdPoint.z
            ));
        }
    }

    private static Point intersection(Point from, Point to) {
        float denominator = from.value - to.value;
        float amount = Math.abs(denominator) <= 1.0E-6F ? 0.5F : from.value / denominator;
        amount = Math.max(0.0F, Math.min(1.0F, amount));
        return new Point(
                from.x + (to.x - from.x) * amount,
                from.z + (to.z - from.z) * amount,
                0.0F
        );
    }

    private static boolean validNeighbor(int center, int neighbor, int maximumStep) {
        return neighbor != Integer.MIN_VALUE && Math.abs(center - neighbor) <= maximumStep;
    }

    private static double valueNoise(double x, double z, long salt) {
        int minimumX = floorToInt(x);
        int minimumZ = floorToInt(z);
        double xAmount = smooth(x - minimumX);
        double zAmount = smooth(z - minimumZ);
        double north = lerp(hash(minimumX, minimumZ, salt), hash(minimumX + 1, minimumZ, salt), xAmount);
        double south = lerp(hash(minimumX, minimumZ + 1, salt), hash(minimumX + 1, minimumZ + 1, salt), xAmount);
        return lerp(north, south, zAmount);
    }

    private static double hash(int x, int z, long salt) {
        long value = salt;
        value ^= (long) x * 0x9E3779B97F4A7C15L;
        value ^= (long) z * 0xC2B2AE3D27D4EB4FL;
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        value ^= value >>> 31;
        return (double) (value >>> 11) * 0x1.0p-53;
    }

    private static int floorToInt(double value) {
        int truncated = (int) value;
        return value < truncated ? truncated - 1 : truncated;
    }

    private static double smooth(double value) {
        return value * value * (3.0D - 2.0D * value);
    }

    private static double lerp(double from, double to, double amount) {
        return from + (to - from) * amount;
    }

    private static double unit(double value) {
        return Double.isFinite(value) ? Math.max(0.0D, Math.min(1.0D, value)) : 0.0D;
    }

    private record Point(float x, float z, float value) {
    }

    /** One local-space triangle in a unit world block. */
    public record Triangle(float x0, float z0, float x1, float z1, float x2, float z2) {
    }
}
