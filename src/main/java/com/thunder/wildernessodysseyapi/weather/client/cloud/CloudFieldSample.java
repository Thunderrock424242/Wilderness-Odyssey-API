package com.thunder.wildernessodysseyapi.weather.client.cloud;

import com.thunder.wildernessodysseyapi.weather.api.CloudType;
import com.thunder.wildernessodysseyapi.weather.api.CloudTypeClassifier;
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
 * @param temperature air temperature in degrees Celsius
 * @param humidity relative humidity
 * @param verticalMotion normalized rising or sinking air
 * @param cloudDepth normalized vertical cloud development
 * @param windX east-west atmospheric motion
 * @param windZ north-south atmospheric motion
 * @param cloudWindX east-west motion at cloud altitude
 * @param cloudWindZ north-south motion at cloud altitude
 * @param support fraction of the spatial sample backed by synchronized cells
 */
public record CloudFieldSample(
        double cloudWater,
        double precipitationIntensity,
        double stormEnergy,
        double instability,
        double temperature,
        double humidity,
        double verticalMotion,
        double cloudDepth,
        double windX,
        double windZ,
        double cloudWindX,
        double cloudWindZ,
        double support
) {
    public static final CloudFieldSample CLEAR = new CloudFieldSample(
            0.0, 0.0, 0.0, 0.0, 15.0, 0.45, 0.0, 0.0,
            0.0, 0.0, 0.0, 0.0, 0.0
    );

    /**
     * Keeps the original render-sample constructor available for focused
     * callers that do not yet need the vertical cloud-column fields.
     */
    public CloudFieldSample(
            double cloudWater,
            double precipitationIntensity,
            double stormEnergy,
            double instability,
            double windX,
            double windZ,
            double support
    ) {
        this(
                cloudWater,
                precipitationIntensity,
                stormEnergy,
                instability,
                15.0,
                0.45,
                0.0,
                Math.max(cloudWater, stormEnergy),
                windX,
                windZ,
                windX,
                windZ,
                support
        );
    }

    public CloudFieldSample {
        cloudWater = unit(cloudWater);
        precipitationIntensity = unit(precipitationIntensity);
        stormEnergy = unit(stormEnergy);
        instability = unit(instability);
        temperature = clamp(finiteOrZero(temperature), WeatherSample.MIN_TEMPERATURE, WeatherSample.MAX_TEMPERATURE);
        humidity = unit(humidity);
        verticalMotion = clamp(finiteOrZero(verticalMotion), -1.0, 1.0);
        cloudDepth = unit(cloudDepth);
        windX = clamp(finiteOrZero(windX), -1.0, 1.0);
        windZ = clamp(finiteOrZero(windZ), -1.0, 1.0);
        cloudWindX = clamp(finiteOrZero(cloudWindX), -1.0, 1.0);
        cloudWindZ = clamp(finiteOrZero(cloudWindZ), -1.0, 1.0);
        support = unit(support);
    }

    /** Returns normalized wind shear between the surface and cloud layer. */
    public double windShear() {
        return clamp(Math.hypot(cloudWindX - windX, cloudWindZ - windZ), 0.0, 1.5);
    }

    /** Returns the cloud genus implied by this spatially blended field. */
    public CloudType cloudType() {
        return CloudTypeClassifier.classify(
                cloudWater,
                humidity,
                instability,
                stormEnergy,
                precipitationIntensity,
                verticalMotion,
                cloudDepth,
                windShear()
        );
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
        double temperature = 0.0;
        double humidity = 0.0;
        double verticalMotion = 0.0;
        double cloudDepth = 0.0;
        double windX = 0.0;
        double windZ = 0.0;
        double cloudWindX = 0.0;
        double cloudWindZ = 0.0;
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
            temperature += sample.temperature() * weight;
            humidity += sample.humidity() * weight;
            verticalMotion += sample.verticalMotion() * weight;
            cloudDepth += sample.cloudDepth() * weight;
            windX += sample.wind().x() * weight;
            windZ += sample.wind().z() * weight;
            cloudWindX += sample.cloudWind().x() * weight;
            cloudWindZ += sample.cloudWind().z() * weight;
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
                temperature * normalization,
                humidity * normalization,
                verticalMotion * normalization,
                cloudDepth * normalization,
                windX * normalization,
                windZ * normalization,
                cloudWindX * normalization,
                cloudWindZ * normalization,
                support
        );
    }

    /** Smoothly blends two render samples during a network snapshot transition. */
    public static CloudFieldSample interpolate(CloudFieldSample from, CloudFieldSample to, double amount) {
        CloudFieldSample safeFrom = from == null ? CLEAR : from;
        CloudFieldSample safeTo = to == null ? CLEAR : to;
        double alpha = unit(amount);
        double support = lerp(safeFrom.support, safeTo.support, alpha);
        if (support <= 1.0E-9) {
            return CLEAR;
        }

        // Interpolate support-weighted fields first, then normalize once. This
        // keeps effective coverage linear while a synchronized region appears
        // or disappears instead of multiplying two independent transitions.
        return new CloudFieldSample(
                interpolateEffective(safeFrom.cloudWater, safeFrom.support,
                        safeTo.cloudWater, safeTo.support, alpha, support),
                interpolateEffective(safeFrom.precipitationIntensity, safeFrom.support,
                        safeTo.precipitationIntensity, safeTo.support, alpha, support),
                interpolateEffective(safeFrom.stormEnergy, safeFrom.support,
                        safeTo.stormEnergy, safeTo.support, alpha, support),
                interpolateEffective(safeFrom.instability, safeFrom.support,
                        safeTo.instability, safeTo.support, alpha, support),
                interpolateEffective(safeFrom.temperature, safeFrom.support,
                        safeTo.temperature, safeTo.support, alpha, support),
                interpolateEffective(safeFrom.humidity, safeFrom.support,
                        safeTo.humidity, safeTo.support, alpha, support),
                interpolateEffective(safeFrom.verticalMotion, safeFrom.support,
                        safeTo.verticalMotion, safeTo.support, alpha, support),
                interpolateEffective(safeFrom.cloudDepth, safeFrom.support,
                        safeTo.cloudDepth, safeTo.support, alpha, support),
                interpolateEffective(safeFrom.windX, safeFrom.support,
                        safeTo.windX, safeTo.support, alpha, support),
                interpolateEffective(safeFrom.windZ, safeFrom.support,
                        safeTo.windZ, safeTo.support, alpha, support),
                interpolateEffective(safeFrom.cloudWindX, safeFrom.support,
                        safeTo.cloudWindX, safeTo.support, alpha, support),
                interpolateEffective(safeFrom.cloudWindZ, safeFrom.support,
                        safeTo.cloudWindZ, safeTo.support, alpha, support),
                support
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

    private static double interpolateEffective(
            double from,
            double fromSupport,
            double to,
            double toSupport,
            double amount,
            double blendedSupport
    ) {
        return lerp(from * fromSupport, to * toSupport, amount) / blendedSupport;
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
