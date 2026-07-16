package com.thunder.wildernessodysseyapi.weather.api;

/**
 * Immutable horizontal wind expressed as normalized atmospheric-cell motion.
 *
 * <p>Positive X points east and positive Z points south. Components consumed by
 * {@link WeatherSample} are bounded to {@code [-1, 1]}, where one represents
 * the strongest transport speed supported by the first-pass simulation.</p>
 *
 * @param x east-west component
 * @param z north-south component
 */
public record WindVector(double x, double z) {
    public static final WindVector ZERO = new WindVector(0.0, 0.0);

    /** Returns the Euclidean wind magnitude. */
    public double magnitude() {
        return Math.hypot(x, z);
    }

    /** Returns a vector whose magnitude is no greater than {@code maximum}. */
    public WindVector limited(double maximum) {
        double safeMaximum = Math.max(0.0, finiteOrZero(maximum));
        double magnitude = magnitude();
        if (magnitude <= safeMaximum || magnitude == 0.0) {
            return this;
        }
        double scale = safeMaximum / magnitude;
        return new WindVector(x * scale, z * scale);
    }

    /** Smoothly interpolates between two immutable wind vectors. */
    public static WindVector lerp(WindVector from, WindVector to, double alpha) {
        WindVector safeFrom = from == null ? ZERO : from;
        WindVector safeTo = to == null ? ZERO : to;
        double t = clamp(finiteOrZero(alpha), 0.0, 1.0);
        return new WindVector(
                safeFrom.x + (safeTo.x - safeFrom.x) * t,
                safeFrom.z + (safeTo.z - safeFrom.z) * t
        );
    }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
