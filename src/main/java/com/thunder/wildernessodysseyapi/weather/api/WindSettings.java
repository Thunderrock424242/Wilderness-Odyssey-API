package com.thunder.wildernessodysseyapi.weather.api;

/**
 * Immutable, server-authored controls for derived regional wind.
 *
 * <p>Speeds use blocks per second. Gust frequency is the average number of
 * coherent gust cycles per Minecraft minute (1,200 ticks). The record clamps
 * untrusted network and config values before the wind model can consume them.</p>
 *
 * @param enabled whether wind queries may return non-zero values
 * @param baseWindStrength sustained ambient speed in blocks per second
 * @param gustFrequency regional gust cycles per Minecraft minute
 * @param gustStrength maximum additive gust speed in blocks per second
 * @param stormWindMultiplier weather amplification at maximum storm severity
 * @param maxWindSpeed hard cap for effective wind speed in blocks per second
 */
public record WindSettings(
        boolean enabled,
        float baseWindStrength,
        float gustFrequency,
        float gustStrength,
        float stormWindMultiplier,
        float maxWindSpeed
) {
    private static final float MAX_SUPPORTED_SPEED = 64.0F;
    private static final float MAX_GUST_FREQUENCY = 60.0F;
    private static final float MAX_STORM_MULTIPLIER = 4.0F;

    public static final WindSettings DEFAULT = new WindSettings(
            true,
            2.5F,
            2.0F,
            5.0F,
            1.8F,
            24.0F
    );
    public static final WindSettings DISABLED = new WindSettings(
            false,
            0.0F,
            0.0F,
            0.0F,
            1.0F,
            24.0F
    );

    public WindSettings {
        maxWindSpeed = clamp(maxWindSpeed, 0.0F, MAX_SUPPORTED_SPEED, 24.0F);
        baseWindStrength = clamp(baseWindStrength, 0.0F, maxWindSpeed, 2.5F);
        gustFrequency = clamp(gustFrequency, 0.0F, MAX_GUST_FREQUENCY, 2.0F);
        gustStrength = clamp(gustStrength, 0.0F, maxWindSpeed, 5.0F);
        stormWindMultiplier = clamp(
                stormWindMultiplier,
                0.0F,
                MAX_STORM_MULTIPLIER,
                1.8F
        );
    }

    private static float clamp(float value, float minimum, float maximum, float fallback) {
        float finite = Float.isFinite(value) ? value : fallback;
        return Math.max(minimum, Math.min(maximum, finite));
    }
}
