package com.thunder.wildernessodysseyapi.weather.client.cloud;

import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;

/**
 * Immutable render-focused view of the atmospheric fields that shape clouds.
 *
 * <p>The continuous values are normalized from available synchronized cells.
 * {@code support} records how much of the bilinear footprint was actually
 * present, allowing the cloud renderer to fade at a network-region edge rather
 * than stretching the nearest cloud cell indefinitely.</p>
 *
 * @param cloudWater condensed cloud moisture
 * @param precipitationIntensity active rain or snow intensity
 * @param stormEnergy accumulated severe-weather energy
 * @param instability convective instability
 * @param windX east-west atmospheric motion
 * @param windZ north-south atmospheric motion
 * @param support fraction of the spatial sample backed by synchronized cells
 */
public record CloudFieldSample(
        double cloudWater,
        double precipitationIntensity,
        double stormEnergy,
        double instability,
        double windX,
        double windZ,
        double support
) {
    public static final CloudFieldSample CLEAR = new CloudFieldSample(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0);

    public CloudFieldSample {
        cloudWater = unit(cloudWater);
        precipitationIntensity = unit(precipitationIntensity);
        stormEnergy = unit(stormEnergy);
        instability = unit(instability);
        windX = clamp(finiteOrZero(windX), -1.0, 1.0);
        windZ = clamp(finiteOrZero(windZ), -1.0, 1.0);
        support = unit(support);
    }

    /**
     * Builds a support-aware bilinear sample from four atmospheric cells.
     *
     * <p>Null entries represent cells outside the synchronized region. Their
     * weights reduce support but do not dilute the values supplied by cells
     * that are present.</p>
     */
    public static CloudFieldSample spatial(
            WeatherSample northWest,
            WeatherSample northEast,
            WeatherSample southWest,
            WeatherSample southEast,
            double xAmount,
            double zAmount
    ) {
        double x = unit(xAmount);
        double z = unit(zAmount);
        double[] weights = {
                (1.0 - x) * (1.0 - z),
                x * (1.0 - z),
                (1.0 - x) * z,
                x * z
        };
        WeatherSample[] samples = {northWest, northEast, southWest, southEast};

        double support = 0.0;
        double cloudWater = 0.0;
        double precipitation = 0.0;
        double stormEnergy = 0.0;
        double instability = 0.0;
        double windX = 0.0;
        double windZ = 0.0;
        for (int index = 0; index < samples.length; index++) {
            WeatherSample sample = samples[index];
            if (sample == null) {
                continue;
            }
            double weight = weights[index];
            support += weight;
            cloudWater += sample.cloudWater() * weight;
            precipitation += sample.precipitationIntensity() * weight;
            stormEnergy += sample.stormEnergy() * weight;
            instability += sample.instability() * weight;
            windX += sample.wind().x() * weight;
            windZ += sample.wind().z() * weight;
        }
        if (support <= 1.0E-9) {
            return CLEAR;
        }

        double normalization = 1.0 / support;
        return new CloudFieldSample(
                cloudWater * normalization,
                precipitation * normalization,
                stormEnergy * normalization,
                instability * normalization,
                windX * normalization,
                windZ * normalization,
                support
        );
    }

    /** Smoothly blends two render samples during a network snapshot transition. */
    public static CloudFieldSample interpolate(CloudFieldSample from, CloudFieldSample to, double amount) {
        CloudFieldSample safeFrom = from == null ? CLEAR : from;
        CloudFieldSample safeTo = to == null ? CLEAR : to;
        double alpha = unit(amount);
        return new CloudFieldSample(
                lerp(safeFrom.cloudWater, safeTo.cloudWater, alpha),
                lerp(safeFrom.precipitationIntensity, safeTo.precipitationIntensity, alpha),
                lerp(safeFrom.stormEnergy, safeTo.stormEnergy, alpha),
                lerp(safeFrom.instability, safeTo.instability, alpha),
                lerp(safeFrom.windX, safeTo.windX, alpha),
                lerp(safeFrom.windZ, safeTo.windZ, alpha),
                lerp(safeFrom.support, safeTo.support, alpha)
        );
    }

    /** Returns cloud water attenuated by synchronized-region support. */
    public double effectiveCloudWater() {
        return cloudWater * support;
    }

    /** Returns precipitation attenuated by synchronized-region support. */
    public double effectivePrecipitation() {
        return precipitationIntensity * support;
    }

    private static double lerp(double from, double to, double amount) {
        return from + (to - from) * amount;
    }

    private static double unit(double value) {
        return clamp(finiteOrZero(value), 0.0, 1.0);
    }

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
