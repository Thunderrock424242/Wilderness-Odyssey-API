package com.thunder.wildernessodysseyapi.weather.lightning;

import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.config.WeatherConfig;

import java.util.Objects;

/**
 * Pure cadence and probability rules for localized natural lightning.
 *
 * <p>World access and entity creation remain in {@link LocalizedLightningScheduler}.
 * Keeping these decisions pure makes multiplayer bounds, cooldown edges, and
 * storm-strength scaling independently testable.</p>
 */
public final class LightningStrikePolicy {

    private static final double MINIMUM_ELIGIBLE_THUNDER = 0.35;

    private LightningStrikePolicy() {
    }

    /**
     * Returns the single per-dimension strike probability for one scheduler check.
     *
     * <p>Eligibility is authoritative on {@link WeatherSample}; the lower
     * probability floor lets a marginal thunderstorm eventually strike while
     * preserving a visibly faster cadence for high-energy storms.</p>
     */
    public static double strikeChance(
            WeatherSample sample,
            WeatherConfig.LightningSettings settings
    ) {
        WeatherSample safeSample = Objects.requireNonNullElse(sample, WeatherSample.CLEAR);
        WeatherConfig.LightningSettings safeSettings = Objects.requireNonNullElse(
                settings,
                WeatherConfig.LightningSettings.DEFAULT
        );
        if (!safeSettings.enabled() || !safeSample.lightningEligible()) {
            return 0.0;
        }

        double normalizedThunder = clamp(
                (safeSample.thunderIntensity() - MINIMUM_ELIGIBLE_THUNDER)
                        / (1.0 - MINIMUM_ELIGIBLE_THUNDER),
                0.0,
                1.0
        );
        return safeSettings.maximumChancePerCheck()
                * (0.25 + normalizedThunder * 0.75);
    }

    /** Returns whether a game-time cooldown includes the current tick. */
    public static boolean cooldownElapsed(long gameTime, long nextAllowedTick) {
        return gameTime >= nextAllowedTick;
    }

    /**
     * Returns the bounded number of candidate columns to inspect this check.
     *
     * <p>One player can supply multiple random columns, but no players means
     * no loaded player-relevant strike region and therefore no work.</p>
     */
    public static int candidateBudget(
            int playerCount,
            WeatherConfig.LightningSettings settings
    ) {
        if (playerCount <= 0) {
            return 0;
        }
        WeatherConfig.LightningSettings safeSettings = Objects.requireNonNullElse(
                settings,
                WeatherConfig.LightningSettings.DEFAULT
        );
        return safeSettings.maxCandidateAttempts();
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
