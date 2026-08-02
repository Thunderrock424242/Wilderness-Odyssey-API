package com.thunder.wildernessodysseyapi.weather.integration;

import com.thunder.wildernessodysseyapi.weather.config.WeatherOwnershipMode;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies that only one configured weather system may own a dimension. */
class WeatherOwnershipPolicyTest {

    @Test
    void autoYieldsToInstalledConfiguredOwner() {
        WeatherOwnershipPolicy.Decision decision = WeatherOwnershipPolicy.resolve(
                WeatherOwnershipMode.AUTO,
                Set.of("simpleclouds"),
                Set.of("simpleclouds", "weather2"),
                Set.of()
        );

        assertFalse(decision.wildernessOwnsWeather());
        assertEquals("simpleclouds", decision.owner());
    }

    @Test
    void explicitModesOverrideAutomaticDetection() {
        assertTrue(WeatherOwnershipPolicy.resolve(
                WeatherOwnershipMode.WILDERNESS,
                Set.of("weather2"),
                Set.of("weather2"),
                Set.of("weather2")
        ).wildernessOwnsWeather());
        assertFalse(WeatherOwnershipPolicy.resolve(
                WeatherOwnershipMode.EXTERNAL,
                Set.of(),
                Set.of(),
                Set.of()
        ).wildernessOwnsWeather());
    }

    @Test
    void apiClaimYieldsEvenWhenClaimWasNotPreconfigured() {
        WeatherOwnershipPolicy.Decision decision = WeatherOwnershipPolicy.resolve(
                WeatherOwnershipMode.AUTO,
                Set.of(),
                Set.of(),
                Set.of("futureweather")
        );

        assertFalse(decision.wildernessOwnsWeather());
        assertEquals("futureweather", decision.owner());
    }
}
