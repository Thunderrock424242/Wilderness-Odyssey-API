package com.thunder.wildernessodysseyapi.weather.client;

import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WindSample;
import com.thunder.wildernessodysseyapi.weather.client.cloud.CloudFieldSample;
import net.minecraft.world.phys.Vec3;

/**
 * Immutable camera-local interpretation shared by weather presentation for one frame.
 *
 * <p>This record is never authoritative. It consolidates the formulas that
 * renderers previously recomputed independently from the same synchronized sample.</p>
 */
public record WeatherVisualState(
        WeatherSample weather,
        Vec3 surfaceWind,
        Vec3 cloudWind,
        PrecipitationBlend precipitationBlend,
        float precipitationIntensity,
        float cloudCoverage,
        float cloudDensity,
        float fogDensity,
        float skyDarkening,
        float stormSeverity,
        float wetness,
        float puddleCoverage,
        float lightningIllumination,
        float gustStrength,
        double weatherTime
) {
    public static final WeatherVisualState CLEAR = new WeatherVisualState(
            WeatherSample.CLEAR,
            Vec3.ZERO,
            Vec3.ZERO,
            PrecipitationBlend.NONE,
            0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0D
    );

    public WeatherVisualState {
        weather = weather == null ? WeatherSample.CLEAR : weather;
        surfaceWind = sanitize(surfaceWind);
        cloudWind = sanitize(cloudWind);
        precipitationBlend = precipitationBlend == null ? PrecipitationBlend.NONE : precipitationBlend;
        precipitationIntensity = unit(precipitationIntensity);
        cloudCoverage = unit(cloudCoverage);
        cloudDensity = unit(cloudDensity);
        fogDensity = unit(fogDensity);
        skyDarkening = unit(skyDarkening);
        stormSeverity = unit(stormSeverity);
        wetness = unit(wetness);
        puddleCoverage = unit(puddleCoverage);
        lightningIllumination = unit(lightningIllumination);
        gustStrength = Math.max(0.0F, Float.isFinite(gustStrength) ? gustStrength : 0.0F);
        weatherTime = Double.isFinite(weatherTime) ? Math.max(0.0D, weatherTime) : 0.0D;
    }

    /** Creates one coherent visual state from current synchronized owners. */
    public static WeatherVisualState from(
            WeatherSample weather,
            CloudFieldSample cloud,
            WindSample wind,
            float fogDensity,
            float skyDarkening,
            float lightningIllumination,
            double weatherTime
    ) {
        WeatherSample sample = weather == null ? WeatherSample.CLEAR : weather;
        CloudFieldSample field = cloud == null ? CloudFieldSample.CLEAR : cloud;
        WindSample resolvedWind = wind == null ? WindSample.calm(null) : wind;
        float coverage = unit((float) (field.effectiveCloudWater() * 0.72D
                + field.humidity() * field.support() * 0.20D
                + field.precipitationIntensity() * field.support() * 0.28D));
        float density = unit((float) (coverage * 0.68D
                + field.cloudDepth() * field.support() * 0.22D
                + field.stormEnergy() * field.support() * 0.24D));
        return new WeatherVisualState(
                sample,
                resolvedWind.velocity(),
                new Vec3(sample.cloudWind().x(), sample.verticalMotion() * 0.10D, sample.cloudWind().z())
                        .scale(Math.max(1.0F, resolvedWind.speed())),
                PrecipitationBlend.from(sample),
                (float) sample.precipitationIntensity(),
                coverage,
                density,
                fogDensity,
                skyDarkening,
                (float) Math.max(sample.stormEnergy(), sample.thunderIntensity()),
                (float) sample.surface().wetness(),
                (float) sample.surface().puddleCoverage(),
                lightningIllumination,
                resolvedWind.gust(),
                weatherTime
        );
    }

    private static Vec3 sanitize(Vec3 value) {
        if (value == null || !Double.isFinite(value.x) || !Double.isFinite(value.y) || !Double.isFinite(value.z)) {
            return Vec3.ZERO;
        }
        return value;
    }

    private static float unit(float value) {
        return Float.isFinite(value) ? Math.max(0.0F, Math.min(1.0F, value)) : 0.0F;
    }
}
