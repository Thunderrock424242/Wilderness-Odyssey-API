package com.thunder.wildernessodysseyapi.weather.wildfire;

import com.thunder.wildernessodysseyapi.weather.config.WeatherConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies wildfire rarity and multiplayer work bounds without a loaded world. */
class WildfireIgnitionPolicyTest {

    @Test
    void ineligibleOrDisabledRiskNeverIgnites() {
        WeatherConfig.WildfireSettings enabled = settings(true, 0.01, 4);
        WildfireRiskModel.RiskProfile ineligible = risk(false, 1.0);

        assertEquals(0.0, WildfireIgnitionPolicy.ignitionChance(ineligible, enabled));
        assertEquals(
                0.0,
                WildfireIgnitionPolicy.ignitionChance(risk(true, 1.0), settings(false, 1.0, 4))
        );
    }

    @Test
    void squaredRiskCurveApproachesButNeverExceedsConfiguredMaximum() {
        WeatherConfig.WildfireSettings settings = settings(true, 0.02, 4);
        double moderate = WildfireIgnitionPolicy.ignitionChance(risk(true, 0.50), settings);
        double extreme = WildfireIgnitionPolicy.ignitionChance(risk(true, 1.0), settings);

        assertEquals(0.005, moderate, 1.0E-9);
        assertEquals(0.02, extreme, 1.0E-9);
        assertTrue(extreme > moderate);
    }

    @Test
    void cooldownBoundaryAndMultiplayerChunkBudgetAreBounded() {
        WeatherConfig.WildfireSettings settings = settings(true, 0.01, 16);

        assertTrue(WildfireIgnitionPolicy.cooldownElapsed(48_000L, 48_000L));
        assertFalse(WildfireIgnitionPolicy.cooldownElapsed(47_999L, 48_000L));
        assertEquals(0, WildfireIgnitionPolicy.candidateChunkBudget(0, settings));
        assertEquals(16, WildfireIgnitionPolicy.candidateChunkBudget(1, settings));
        assertEquals(64, WildfireIgnitionPolicy.candidateChunkBudget(100, settings));
    }

    private static WeatherConfig.WildfireSettings settings(
            boolean enabled,
            double maximumChance,
            int chunksPerPlayer
    ) {
        return new WeatherConfig.WildfireSettings(
                enabled,
                600,
                48_000,
                168_000,
                2,
                chunksPerPlayer,
                10,
                12,
                maximumChance
        );
    }

    private static WildfireRiskModel.RiskProfile risk(boolean eligible, double score) {
        return new WildfireRiskModel.RiskProfile(
                eligible,
                score,
                1.0,
                1.0,
                1.0,
                1.0,
                1.0,
                true
        );
    }
}
