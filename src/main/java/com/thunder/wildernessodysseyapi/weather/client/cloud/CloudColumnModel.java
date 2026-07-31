package com.thunder.wildernessodysseyapi.weather.client.cloud;

/**
 * Converts synchronized thermodynamic fields into a vertical cloud column.
 *
 * <p>The dimension's normal cloud height remains the anchor. Humid air lowers
 * the visible base, while convection, cloud depth, and storm energy build
 * upward. Keeping this calculation pure makes quality settings affect only the
 * number of visual slices, never the authoritative weather footprint.</p>
 */
public final class CloudColumnModel {

    private CloudColumnModel() {
    }

    /** Returns the cloud-base offset from the dimension's configured cloud height. */
    public static double baseOffsetBlocks(CloudFieldSample field) {
        CloudFieldSample sample = field == null ? CloudFieldSample.CLEAR : field;
        double warmLift = Math.max(0.0, sample.temperature() - 18.0) * 0.18;
        double moistureLowering = sample.humidity() * 16.0;
        double ascentLowering = Math.max(0.0, sample.verticalMotion()) * 8.0;
        return clamp(10.0 + warmLift - moistureLowering - ascentLowering, -10.0, 18.0);
    }

    /** Returns the visible vertical development in blocks. */
    public static double depthBlocks(CloudFieldSample field) {
        CloudFieldSample sample = field == null ? CloudFieldSample.CLEAR : field;
        double depth = 5.0
                + sample.cloudDepth() * 44.0
                + sample.stormEnergy() * 24.0
                + Math.max(0.0, sample.verticalMotion()) * 12.0;
        return clamp(depth, 4.0, 82.0);
    }

    /**
     * Distributes a column's opacity across its slices so changing the quality
     * setting does not make clouds disproportionately opaque.
     */
    public static double sliceOpacity(double columnOpacity, int layerCount) {
        int safeLayers = Math.max(1, layerCount);
        double boundedOpacity = clamp(columnOpacity, 0.0, 0.985);
        return 1.0 - Math.pow(1.0 - boundedOpacity, 1.0 / safeLayers);
    }

    /** Returns how deeply a camera lies inside the local cloud column. */
    public static double cameraImmersion(
            CloudFieldSample field,
            double cameraY,
            double dimensionCloudHeight
    ) {
        CloudFieldSample sample = field == null ? CloudFieldSample.CLEAR : field;
        double base = dimensionCloudHeight + baseOffsetBlocks(sample);
        double depth = depthBlocks(sample);
        double top = base + depth;
        double edge = Math.min(6.0, depth * 0.25);
        double lowerFade = smoothstep(base - edge, base + edge, cameraY);
        double upperFade = 1.0 - smoothstep(top - edge, top + edge, cameraY);
        return clamp(lowerFade * upperFade * sample.support() * sample.cloudWater(), 0.0, 1.0);
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        if (edge1 <= edge0) {
            return value >= edge1 ? 1.0 : 0.0;
        }
        double amount = clamp((value - edge0) / (edge1 - edge0), 0.0, 1.0);
        return amount * amount * (3.0 - 2.0 * amount);
    }

    private static double clamp(double value, double minimum, double maximum) {
        double finite = Double.isFinite(value) ? value : minimum;
        return Math.max(minimum, Math.min(maximum, finite));
    }
}
