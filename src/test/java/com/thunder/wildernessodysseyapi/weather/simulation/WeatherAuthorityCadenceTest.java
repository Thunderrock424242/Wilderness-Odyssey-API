package com.thunder.wildernessodysseyapi.weather.simulation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeatherAuthorityCadenceTest {

    @Test
    void firstMaintenancePassIsDue() {
        assertTrue(WeatherAuthority.isElapsedDue(500L, Long.MIN_VALUE, 60));
    }

    @Test
    void elapsedCadenceDoesNotRequireAnExactModuloBoundary() {
        assertFalse(WeatherAuthority.isElapsedDue(159L, 100L, 60));
        assertTrue(WeatherAuthority.isElapsedDue(160L, 100L, 60));
        assertTrue(WeatherAuthority.isElapsedDue(173L, 100L, 60));
    }

    @Test
    void tickCounterRollbackStartsOneFreshPass() {
        assertTrue(WeatherAuthority.isElapsedDue(20L, 1_000L, 60));
    }
}
