package com.thunder.wildernessodysseyapi.weather.client;

import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;

/** Immutable renderer-only mixture of canonical precipitation phases. */
public record PrecipitationBlend(float rain, float snow, float hail) {

    public static final PrecipitationBlend NONE = new PrecipitationBlend(0.0F, 0.0F, 0.0F);

    public PrecipitationBlend {
        rain = unit(rain);
        snow = unit(snow);
        hail = unit(hail);
        float sum = rain + snow + hail;
        if (sum > 1.0F) {
            rain /= sum;
            snow /= sum;
            hail /= sum;
        }
    }

    /**
     * Derives a visual phase mix from the already spatially blended weather sample.
     * Gameplay continues to use the sample's canonical enum.
     */
    public static PrecipitationBlend from(WeatherSample sample) {
        WeatherSample weather = sample == null ? WeatherSample.CLEAR : sample;
        if (!weather.hasPrecipitation()) {
            return NONE;
        }

        return fromPhase(
                weather.precipitationType(),
                weather.temperature(),
                weather.stormEnergy(),
                weather.instability()
        );
    }

    /** Allocation-light phase mixing for spatial precipitation render samples. */
    public static PrecipitationBlend fromPhase(
            PrecipitationType type,
            double temperature,
            double stormEnergy,
            double instability
    ) {
        PrecipitationType phase = type == null ? PrecipitationType.NONE : type;
        if (phase == PrecipitationType.NONE) {
            return NONE;
        }
        float snow = (float) (1.0D - smoothstep(-1.5D, 3.5D, temperature));
        if (phase == PrecipitationType.SNOW) {
            snow = Math.max(0.28F, snow);
        } else if (phase == PrecipitationType.RAIN) {
            snow = Math.min(0.72F, snow);
        }
        float hail = 0.0F;
        if (phase == PrecipitationType.HAIL) {
            // Hail remains tied to the authoritative severe core rather than
            // spreading solely because a neighboring interpolated cell storms.
            hail = unit((float) (0.58D + stormEnergy * 0.26D + instability * 0.16D));
        }
        float liquidOrSnow = 1.0F - hail;
        return new PrecipitationBlend(
                liquidOrSnow * (1.0F - snow),
                liquidOrSnow * snow,
                hail
        );
    }

    /** Returns whether any visible phase has a meaningful contribution. */
    public boolean visible() {
        return rain + snow + hail > 1.0E-4F;
    }

    private static double smoothstep(double edge0, double edge1, double value) {
        double amount = Math.max(0.0D, Math.min(1.0D, (value - edge0) / (edge1 - edge0)));
        return amount * amount * (3.0D - 2.0D * amount);
    }

    private static float unit(float value) {
        return Float.isFinite(value) ? Math.max(0.0F, Math.min(1.0F, value)) : 0.0F;
    }
}
