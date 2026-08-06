package com.thunder.wildernessodysseyapi.weather.client.precipitation;

/** Pure animation and spawn rules for restrained precipitation impacts. */
public final class PrecipitationImpactModel {

    private PrecipitationImpactModel() {
    }

    /** Returns a bounded spawn probability that grows smoothly with rainfall. */
    public static double spawnProbability(double intensity, double configuredDensity, double particleFactor) {
        double rain = unit(intensity);
        double density = unit(configuredDensity);
        double particles = unit(particleFactor);
        return unit(Math.pow(rain, 1.35) * density * particles);
    }

    /** Returns an eased animation radius for one surface family. */
    public static float radius(float progress, ImpactSurface surface, float intensity) {
        float life = unit(progress);
        float strength = 0.72F + unit(intensity) * 0.28F;
        float maximum = switch (surface) {
            case WATER -> 0.48F;
            case LEAF -> 0.23F;
            case HAIL -> 0.18F;
            case HARD -> 0.28F;
        };
        return (0.035F + maximum * (1.0F - (1.0F - life) * (1.0F - life))) * strength;
    }

    /** Returns a soft alpha curve with no bright white flash at impact. */
    public static float alpha(float progress, ImpactSurface surface, float intensity) {
        float life = unit(progress);
        float fade = (1.0F - life) * (1.0F - life);
        float family = surface == ImpactSurface.WATER ? 0.34F : 0.24F;
        return unit(fade * family * (0.55F + unit(intensity) * 0.45F));
    }

    /** Returns the short lifetime appropriate for a tiny rainfall impact. */
    public static int lifetimeTicks(ImpactSurface surface) {
        return surface == ImpactSurface.WATER ? 12 : surface == ImpactSurface.HAIL ? 8 : 9;
    }

    private static float unit(float value) {
        return Math.max(0.0F, Math.min(1.0F, Float.isFinite(value) ? value : 0.0F));
    }

    private static double unit(double value) {
        return Math.max(0.0, Math.min(1.0, Double.isFinite(value) ? value : 0.0));
    }

    /** Surface families whose impacts use different sizes and color response. */
    public enum ImpactSurface {
        WATER,
        HARD,
        LEAF,
        HAIL
    }
}
