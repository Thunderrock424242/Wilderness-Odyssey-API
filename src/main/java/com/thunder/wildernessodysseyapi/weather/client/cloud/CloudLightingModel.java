package com.thunder.wildernessodysseyapi.weather.client.cloud;

import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;

/**
 * Converts the overhead cloud field into bounded daylight and storm-fog optics.
 *
 * <p>Minecraft exposes one camera-local sky-light response rather than a
 * position-dependent terrain shadow map. This model therefore darkens the
 * vanilla sky and lightmap beneath the synchronized cloud footprint without
 * introducing a custom world shader or changing server light propagation.</p>
 */
public final class CloudLightingModel {

    private static final double WEATHER_FOG_ATTENUATION = 0.88;
    private static final double MINIMUM_WEATHER_FOG_DISTANCE = 32.0;

    private CloudLightingModel() {
    }

    /** Returns optical values for the synchronized cloud directly overhead. */
    public static OpticalState evaluate(CloudFieldSample field) {
        if (field == null || field.support() <= 0.0) {
            return OpticalState.CLEAR;
        }

        double coverage = CloudCoverageModel.coverage(field);
        double cloudWater = field.effectiveCloudWater();
        double precipitation = field.effectivePrecipitation();
        double storm = field.stormEnergy() * field.support();
        double opticalDensity = unit(
                coverage * 0.38
                        + cloudWater * 0.32
                        + precipitation * 0.24
                        + storm * 0.18
        );
        double shadow = unit(coverage * (0.30 + opticalDensity * 0.62));
        double skyDarkeningSignal = unit(Math.max(
                shadow,
                precipitation * 0.72 + storm * 0.24
        ));
        double stormFog = unit(
                cloudWater * 0.10
                        + precipitation * 0.52
                        + storm * precipitation * 0.30
        );
        return new OpticalState(
                coverage,
                opticalDensity,
                shadow,
                skyDarkeningSignal,
                stormFog
        );
    }

    /** Combines legacy weather darkness with physical overhead cloud opacity. */
    public static double skyDarkening(WeatherSample weather, CloudFieldSample field) {
        WeatherSample safeWeather = weather == null ? WeatherSample.CLEAR : weather;
        return Math.max(safeWeather.skyDarkening(), evaluate(field).skyDarkeningSignal());
    }

    /** Combines humid-air haze with the denser precipitation curtain. */
    public static double fogContribution(WeatherSample weather, CloudFieldSample field) {
        WeatherSample safeWeather = weather == null ? WeatherSample.CLEAR : weather;
        return Math.max(safeWeather.fogContribution(), evaluate(field).stormFog());
    }

    /**
     * Shortens a vanilla air-fog far plane without weakening a denser owner.
     *
     * <p>Blindness, Darkness, and other render hooks may already supply a far
     * plane below the normal 32-block weather floor. Local weather must never
     * increase that stronger fog distance.</p>
     */
    public static double attenuatedFogFarPlane(double sourceFar, double fogContribution) {
        if (!Double.isFinite(sourceFar)) {
            return sourceFar;
        }
        double weatherTarget = Math.max(
                MINIMUM_WEATHER_FOG_DISTANCE,
                sourceFar * (1.0 - unit(fogContribution) * WEATHER_FOG_ATTENUATION)
        );
        return Math.min(sourceFar, weatherTarget);
    }

    private static double unit(double value) {
        return Math.max(0.0, Math.min(1.0, Double.isFinite(value) ? value : 0.0));
    }

    /** Immutable overhead optics exposed to rendering and F3 diagnostics. */
    public record OpticalState(
            double coverage,
            double opticalDensity,
            double shadow,
            double skyDarkeningSignal,
            double stormFog
    ) {
        public static final OpticalState CLEAR = new OpticalState(0.0, 0.0, 0.0, 0.0, 0.0);

        public OpticalState {
            coverage = unit(coverage);
            opticalDensity = unit(opticalDensity);
            shadow = unit(shadow);
            skyDarkeningSignal = unit(skyDarkeningSignal);
            stormFog = unit(stormFog);
        }
    }
}
