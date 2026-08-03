package com.thunder.wildernessodysseyapi.weather.wildfire;

import com.thunder.wildernessodysseyapi.weather.config.WeatherConfig;

/**
 * Pure probability, cooldown, and scan-budget rules for campfire wildfires.
 *
 * <p>World access and fire placement remain in {@link WildfireScheduler}. A
 * squared risk curve makes threshold conditions much rarer than the configured
 * maximum while allowing the most exceptional conditions to approach it.</p>
 */
public final class WildfireIgnitionPolicy {

    private static final int MAXIMUM_CHUNKS_PER_DIMENSION_CHECK = 64;

    private WildfireIgnitionPolicy() {
    }

    /** Returns the single bounded ignition probability for one due scheduler check. */
    public static double ignitionChance(
            WildfireRiskModel.RiskProfile risk,
            WeatherConfig.WildfireSettings settings
    ) {
        WeatherConfig.WildfireSettings controls = settings == null
                ? WeatherConfig.WildfireSettings.DEFAULT
                : settings;
        if (!controls.enabled() || risk == null || !risk.eligible()) {
            return 0.0;
        }
        return controls.maximumChancePerCheck() * risk.risk() * risk.risk();
    }

    /** Returns the total loaded-chunk inspection budget for one dimension check. */
    public static int candidateChunkBudget(
            int playerCount,
            WeatherConfig.WildfireSettings settings
    ) {
        if (playerCount <= 0) {
            return 0;
        }
        WeatherConfig.WildfireSettings controls = settings == null
                ? WeatherConfig.WildfireSettings.DEFAULT
                : settings;
        long requested = (long) playerCount * controls.candidateChunksPerPlayer();
        return (int) Math.min(MAXIMUM_CHUNKS_PER_DIMENSION_CHECK, requested);
    }

    /** Returns whether a game-time cooldown includes the current tick. */
    public static boolean cooldownElapsed(long gameTime, long nextAllowedTick) {
        return gameTime >= nextAllowedTick;
    }
}
