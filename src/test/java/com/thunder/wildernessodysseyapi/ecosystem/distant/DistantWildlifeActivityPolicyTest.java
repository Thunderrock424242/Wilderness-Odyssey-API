package com.thunder.wildernessodysseyapi.ecosystem.distant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies group-level schedule, weather, and disturbance responses. */
class DistantWildlifeActivityPolicyTest {

    @Test
    void dayAndNightSchedulesChangeMovementWithoutIndividualAi() {
        double daytimeDeer = scale(false, 6_000L, 0.0, 0.0, 0.0);
        double nighttimeDeer = scale(false, 18_000L, 0.0, 0.0, 0.0);
        double daytimeOwl = scale(true, 6_000L, 0.0, 0.0, 0.0);
        double nighttimeOwl = scale(true, 18_000L, 0.0, 0.0, 0.0);

        assertTrue(daytimeDeer > nighttimeDeer);
        assertTrue(nighttimeOwl > daytimeOwl);
    }

    @Test
    void severeWeatherSuppressesSensitiveGroupsAndDisturbanceDrivesMovement() {
        double clear = scale(false, 6_000L, 0.0, 0.0, 0.0);
        double storm = scale(false, 6_000L, 1.0, 1.0, 0.0);
        double disturbed = scale(false, 6_000L, 0.0, 0.0, 1.0);

        assertTrue(storm < clear);
        assertTrue(disturbed > clear);
    }

    private static double scale(
            boolean nocturnal,
            long dayTime,
            double precipitation,
            double thunder,
            double disturbance
    ) {
        return DistantWildlifeActivityPolicy.movementScale(
                nocturnal, dayTime, true, precipitation, thunder, disturbance
        );
    }
}
