package com.thunder.wildernessodysseyapi.watersystem.water.api;

/**
 * Reusable allocation-free result for hot water query paths.
 *
 * <p>Renderers and entity tick handlers should retain one instance and pass it
 * to {@link WaterAccess#sample}. Convenience API methods may allocate objects,
 * while this form exposes current and normal components as primitive values.</p>
 */
public final class WaterSample {

    private boolean water;
    private double surfaceHeight = Double.NaN;
    private double depth;
    private double currentX;
    private double currentY;
    private double currentZ;
    private double normalX;
    private double normalY = 1.0;
    private double normalZ;

    /** Clears this object to its dry, flat default. */
    public WaterSample clear() {
        return set(false, Double.NaN, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 0.0);
    }

    /** Replaces all values so implementations can fill a caller-owned sample. */
    public WaterSample set(
            boolean water,
            double surfaceHeight,
            double depth,
            double currentX,
            double currentY,
            double currentZ,
            double normalX,
            double normalY,
            double normalZ
    ) {
        this.water = water;
        this.surfaceHeight = surfaceHeight;
        this.depth = Math.max(0.0, depth);
        this.currentX = currentX;
        this.currentY = currentY;
        this.currentZ = currentZ;
        this.normalX = normalX;
        this.normalY = normalY;
        this.normalZ = normalZ;
        return this;
    }

    /** Returns whether the exact sampled position is inside custom water. */
    public boolean water() {
        return water;
    }

    /** Returns animated surface Y, or NaN for a dry column. */
    public double surfaceHeight() {
        return surfaceHeight;
    }

    /** Returns water depth above the sampled position. */
    public double depth() {
        return depth;
    }

    /** Returns current X without allocating a vector. */
    public double currentX() {
        return currentX;
    }

    /** Returns current Y without allocating a vector. */
    public double currentY() {
        return currentY;
    }

    /** Returns current Z without allocating a vector. */
    public double currentZ() {
        return currentZ;
    }

    /** Returns surface-normal X without allocating a vector. */
    public double normalX() {
        return normalX;
    }

    /** Returns surface-normal Y without allocating a vector. */
    public double normalY() {
        return normalY;
    }

    /** Returns surface-normal Z without allocating a vector. */
    public double normalZ() {
        return normalZ;
    }
}
