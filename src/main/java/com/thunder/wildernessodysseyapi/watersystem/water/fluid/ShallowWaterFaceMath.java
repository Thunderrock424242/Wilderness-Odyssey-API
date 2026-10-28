package com.thunder.wildernessodysseyapi.watersystem.water.fluid;

/** Shared local-inertial face arithmetic; it owns neither represented nor canonical water. */
public final class ShallowWaterFaceMath {

    private static final double GRAVITY = 9.81;

    private ShallowWaterFaceMath() { }

    /** Returns signed discharge toward the positive cell, in square metres per second. */
    public static double discharge(double previous, double negativeBed, double positiveBed,
                                   double negativeSurface, double positiveSurface,
                                   double seconds, double cellSize, double drag, double maximumSpeed) {
        double faceDepth = Math.max(0.0,
                Math.max(negativeSurface, positiveSurface) - Math.max(negativeBed, positiveBed));
        if (faceDepth <= 1.0e-8) return 0.0;
        double gradient = (positiveSurface - negativeSurface) / cellSize;
        double finitePrevious = Double.isFinite(previous) ? previous : 0.0;
        double result = (finitePrevious - GRAVITY * faceDepth * gradient * seconds) * drag;
        return Math.max(-maximumSpeed * faceDepth, Math.min(maximumSpeed * faceDepth, result));
    }

    /** Two-dimensional CFL bound, including both horizontal face directions. */
    public static double stableStepSeconds(double maximumDepth, double cellSize, double maximumSpeed) {
        return 0.42 * cellSize / (2.0 * (maximumSpeed + Math.sqrt(GRAVITY * Math.max(1.0e-8, maximumDepth))));
    }
}
