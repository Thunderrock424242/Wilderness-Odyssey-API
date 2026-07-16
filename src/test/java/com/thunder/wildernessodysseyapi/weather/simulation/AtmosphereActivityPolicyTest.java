package com.thunder.wildernessodysseyapi.weather.simulation;

import com.thunder.wildernessodysseyapi.weather.api.AtmosphereCellKey;
import com.thunder.wildernessodysseyapi.weather.api.AtmosphereView;
import com.thunder.wildernessodysseyapi.weather.api.PrecipitationType;
import com.thunder.wildernessodysseyapi.weather.api.WeatherSample;
import com.thunder.wildernessodysseyapi.weather.api.WindVector;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies grace, storm persistence, and bounded reactivation work. */
class AtmosphereActivityPolicyTest {

    @Test
    void gracePeriodIncludesItsBoundaryButNotOlderDormantCells() {
        AtmosphereView view = view(WeatherSample.CLEAR, 500L, 900L);

        assertTrue(AtmosphereActivityPolicy.shouldSimulate(view, 1_000L, 100L, 0.55));
        assertFalse(AtmosphereActivityPolicy.shouldSimulate(view, 1_001L, 100L, 0.55));
        assertEquals(0L, AtmosphereActivityPolicy.inactiveAge(view, 800L));
    }

    @Test
    void persistentStormRemainsScheduledAfterActivityGraceExpires() {
        AtmosphereView persistentStorm = view(stormSample(0.80), 100L, 100L);

        assertTrue(AtmosphereActivityPolicy.shouldSimulate(persistentStorm, 10_000L, 100L, 0.55));
    }

    @Test
    void catchUpWorkIsAtLeastOneStepAndNeverExceedsItsBound() {
        AtmosphereView stale = view(WeatherSample.CLEAR, 100L, 100L);
        AtmosphereView recent = view(WeatherSample.CLEAR, 990L, 990L);

        assertEquals(4, AtmosphereActivityPolicy.catchUpSteps(stale, 10_000L, 60, 4));
        assertEquals(1, AtmosphereActivityPolicy.catchUpSteps(recent, 1_000L, 60, 4));
        assertEquals(0, AtmosphereActivityPolicy.catchUpSteps(null, 1_000L, 60, 4));
    }

    private static AtmosphereView view(WeatherSample sample, long lastSimulatedTick, long lastActiveTick) {
        return new AtmosphereView(
                new AtmosphereCellKey(0, 0),
                sample,
                1L,
                lastSimulatedTick,
                lastActiveTick
        );
    }

    private static WeatherSample stormSample(double stormEnergy) {
        return new WeatherSample(
                18.0,
                0.9,
                0.95,
                WindVector.ZERO,
                0.9,
                0.8,
                stormEnergy,
                0.7,
                PrecipitationType.RAIN
        );
    }
}
