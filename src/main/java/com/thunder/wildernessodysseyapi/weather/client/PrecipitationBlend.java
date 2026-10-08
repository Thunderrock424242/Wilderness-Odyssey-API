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
     * Presents the canonical phase of one weather sample.
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

    /** Keeps the previous public signature while respecting the column's phase. */
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
        // The full atmospheric column already decided the phase. Surface air
        // and instability cannot classify it a second time on the client.
        return switch (phase) {
            case RAIN, FREEZING_RAIN -> new PrecipitationBlend(1.0F, 0.0F, 0.0F);
            case SNOW -> new PrecipitationBlend(0.0F, 1.0F, 0.0F);
            case SLEET, HAIL -> new PrecipitationBlend(0.0F, 0.0F, 1.0F);
            default -> NONE;
        };
    }

    /** Blends four authoritative phases, weighted by their precipitation flux. */
    public static PrecipitationBlend spatial(WeatherSample northWest, WeatherSample northEast,
            WeatherSample southWest, WeatherSample southEast, double xAmount, double zAmount) {
        double x = unit((float) xAmount);
        double z = unit((float) zAmount);
        double a = intensity(northWest) * (1 - x) * (1 - z);
        double b = intensity(northEast) * x * (1 - z);
        double c = intensity(southWest) * (1 - x) * z;
        double d = intensity(southEast) * x * z;
        return normalized(
                contribution(northWest, 0) * a + contribution(northEast, 0) * b
                        + contribution(southWest, 0) * c + contribution(southEast, 0) * d,
                contribution(northWest, 1) * a + contribution(northEast, 1) * b
                        + contribution(southWest, 1) * c + contribution(southEast, 1) * d,
                contribution(northWest, 2) * a + contribution(northEast, 2) * b
                        + contribution(southWest, 2) * c + contribution(southEast, 2) * d);
    }

    /** Packet transitions blend phases; short-term extrapolation retains the newest phase. */
    public static PrecipitationBlend temporal(PrecipitationBlend from, double fromIntensity,
            PrecipitationBlend to, double toIntensity, double amount) {
        double alpha = unit((float) amount);
        double previous = unit((float) fromIntensity) * (1 - alpha);
        double current = unit((float) toIntensity) * alpha;
        return normalized(from.rain * previous + to.rain * current,
                from.snow * previous + to.snow * current, from.hail * previous + to.hail * current);
    }

    private static double intensity(WeatherSample sample) {
        return sample == null ? 0 : sample.precipitationIntensity();
    }

    private static int contribution(WeatherSample sample, int channel) {
        if (sample == null) return 0;
        return switch (sample.precipitationType()) {
            case RAIN, FREEZING_RAIN -> channel == 0 ? 1 : 0;
            case SNOW -> channel == 1 ? 1 : 0;
            case SLEET, HAIL -> channel == 2 ? 1 : 0;
            default -> 0;
        };
    }

    private static PrecipitationBlend normalized(double rain, double snow, double hail) {
        double total = rain + snow + hail;
        return total <= 1.0E-9 ? NONE
                : new PrecipitationBlend((float) (rain / total), (float) (snow / total), (float) (hail / total));
    }

    /** Returns whether any visible phase has a meaningful contribution. */
    public boolean visible() {
        return rain + snow + hail > 1.0E-4F;
    }

    private static float unit(float value) {
        return Float.isFinite(value) ? Math.max(0.0F, Math.min(1.0F, value)) : 0.0F;
    }
}
