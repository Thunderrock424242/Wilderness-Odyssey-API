package com.thunder.wildernessodysseyapi.weather.client.cloud;

/**
 * Shares cloud-deck world heights between cloud and precipitation rendering.
 *
 * <p>Both renderers must use the same base translation. Otherwise a weather
 * shaft can visibly continue above the cloud volume that produced it.</p>
 */
public final class CloudAltitudeModel {

    private static final double RENDER_BASE_OFFSET_BLOCKS = 0.33;

    private CloudAltitudeModel() {
    }

    /** Returns the translated world base used by all localized cloud geometry. */
    public static double dimensionBaseY(double dimensionCloudHeight) {
        return Double.isFinite(dimensionCloudHeight)
                ? dimensionCloudHeight + RENDER_BASE_OFFSET_BLOCKS
                : Double.NaN;
    }

    /** Returns the lowest visible cloud-deck base that can release precipitation. */
    public static double precipitationBaseY(double dimensionCloudHeight, CloudFieldSample field) {
        double worldBase = dimensionBaseY(dimensionCloudHeight);
        double deckOffset = CloudLayerProfile.evaluate(field).lowestVisibleBaseOffsetBlocks();
        return Double.isFinite(worldBase) && Double.isFinite(deckOffset)
                ? worldBase + deckOffset
                : Double.NaN;
    }

    /** Caps a desired rain-column top at the source cloud base. */
    public static int precipitationTopY(int desiredTopY, double cloudBaseY) {
        if (!Double.isFinite(cloudBaseY)) {
            return Integer.MIN_VALUE;
        }
        return Math.min(desiredTopY, (int) Math.floor(cloudBaseY));
    }
}
