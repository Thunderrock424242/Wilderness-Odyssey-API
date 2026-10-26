package com.thunder.wildernessodysseyapi.environment.audio;

/** Bounded acoustic transmission through a supplied, read-only voxel scene. */
public final class SoundOcclusionModel {

    private static final double MAXIMUM_DISTANCE = 64.0;
    private static final int MAXIMUM_CELLS_PER_RAY = 192;
    private static final double MINIMUM_GAIN = 0.005;

    private SoundOcclusionModel() {
    }

    /** Returns a volume multiplier for a small bundle of direct acoustic rays. */
    public static float gain(Point listener, Point source, LossSampler sampler) {
        if (!listener.valid() || !source.valid()) {
            return 1.0f;
        }
        double dx = source.x - listener.x;
        double dy = source.y - listener.y;
        double dz = source.z - listener.z;
        if (dx * dx + dy * dy + dz * dz < 0.01) {
            return 1.0f;
        }
        double horizontalLength = Math.hypot(dx, dz);
        double sideX = horizontalLength < 0.001 ? 0.8 : -dz / horizontalLength * 0.8;
        double sideZ = horizontalLength < 0.001 ? 0.0 : dx / horizontalLength * 0.8;
        // A small source aperture avoids treating one thin obstruction as an
        // entire shoreline. These are direct paths, not reflected/diffracted rays.
        double center = trace(listener, source, sampler);
        double left = trace(listener, new Point(source.x + sideX, source.y, source.z + sideZ), sampler);
        double right = trace(listener, new Point(source.x - sideX, source.y, source.z - sideZ), sampler);
        return (float) (center * 0.5 + left * 0.25 + right * 0.25);
    }

    private static double trace(Point listener, Point source, LossSampler sampler) {
        double dx = source.x - listener.x;
        double dy = source.y - listener.y;
        double dz = source.z - listener.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double scale = Math.min(1.0, MAXIMUM_DISTANCE / distance);
        dx *= scale;
        dy *= scale;
        dz *= scale;

        int x = (int) Math.floor(listener.x);
        int y = (int) Math.floor(listener.y);
        int z = (int) Math.floor(listener.z);
        int stepX = dx > 0.0 ? 1 : dx < 0.0 ? -1 : 0;
        int stepY = dy > 0.0 ? 1 : dy < 0.0 ? -1 : 0;
        int stepZ = dz > 0.0 ? 1 : dz < 0.0 ? -1 : 0;
        double nextX = firstBoundary(listener.x, x, dx, stepX);
        double nextY = firstBoundary(listener.y, y, dy, stepY);
        double nextZ = firstBoundary(listener.z, z, dz, stepZ);
        double intervalX = stepX == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(dx);
        double intervalY = stepY == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(dy);
        double intervalZ = stepZ == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(dz);
        double entered = 0.0;
        double loss = 0.0;
        for (int cell = 0; cell < MAXIMUM_CELLS_PER_RAY; cell++) {
            double exited = Math.min(1.0, Math.min(nextX, Math.min(nextY, nextZ)));
            if (exited > entered) {
                CellPath path = new CellPath(x, y, z,
                        new Point(listener.x + dx * entered, listener.y + dy * entered, listener.z + dz * entered),
                        new Point(listener.x + dx * exited, listener.y + dy * exited, listener.z + dz * exited));
                double cellLoss = sampler.loss(path);
                if (Double.isNaN(cellLoss)) {
                    break;
                }
                loss += Math.max(0.0, cellLoss);
                if (loss >= -Math.log(MINIMUM_GAIN)) {
                    return MINIMUM_GAIN;
                }
            }
            if (exited >= 1.0) {
                break;
            }
            // Advance every tied axis so corner crossings never visit voxels
            // that the ray only touches at a zero-length edge.
            if (nextX <= exited) {
                x += stepX;
                nextX += intervalX;
            }
            if (nextY <= exited) {
                y += stepY;
                nextY += intervalY;
            }
            if (nextZ <= exited) {
                z += stepZ;
                nextZ += intervalZ;
            }
            entered = exited;
        }
        return Math.max(MINIMUM_GAIN, Math.exp(-loss));
    }

    private static double firstBoundary(double origin, int cell, double direction, int step) {
        return step == 0 ? Double.POSITIVE_INFINITY
                : (cell + (step > 0 ? 1.0 : 0.0) - origin) / direction;
    }

    /** Immutable acoustic endpoint in world coordinates. */
    public record Point(double x, double y, double z) {
        private boolean valid() {
            return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                    && Math.abs(x) <= 32_000_000 && Math.abs(y) <= 32_000_000 && Math.abs(z) <= 32_000_000;
        }
    }

    /** The part of one acoustic ray inside a single voxel. */
    public record CellPath(int x, int y, int z, Point start, Point end) {
        public double length() {
            double dx = end.x - start.x;
            double dy = end.y - start.y;
            double dz = end.z - start.z;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        /** Returns the ray length inside an axis-aligned shape in world coordinates. */
        public double intersectionLength(
                double minX, double minY, double minZ, double maxX, double maxY, double maxZ
        ) {
            double entered = 0.0;
            double exited = 1.0;
            for (int axis = 0; axis < 3; axis++) {
                double origin = axis == 0 ? start.x : axis == 1 ? start.y : start.z;
                double direction = (axis == 0 ? end.x : axis == 1 ? end.y : end.z) - origin;
                double lower = axis == 0 ? minX : axis == 1 ? minY : minZ;
                double upper = axis == 0 ? maxX : axis == 1 ? maxY : maxZ;
                if (Math.abs(direction) < 1.0E-12) {
                    if (origin < lower || origin > upper) {
                        return 0.0;
                    }
                    continue;
                }
                double first = (lower - origin) / direction;
                double second = (upper - origin) / direction;
                entered = Math.max(entered, Math.min(first, second));
                exited = Math.min(exited, Math.max(first, second));
                if (exited <= entered) {
                    return 0.0;
                }
            }
            return (exited - entered) * length();
        }
    }

    /** Supplies nonnegative acoustic loss for a voxel segment; NaN stops an unavailable path. */
    @FunctionalInterface
    public interface LossSampler {
        double loss(CellPath path);
    }
}
