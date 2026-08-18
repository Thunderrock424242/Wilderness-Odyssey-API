package com.thunder.wildernessodysseyapi.ecosystem.behavior;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WildlifeDisturbancePolicyTest {

    private static final WildlifeDisturbancePolicy.Settings SETTINGS =
            new WildlifeDisturbancePolicy.Settings(0.25, 0.50, 0.75, 0.85, 0.55, 0.25);

    @Test
    void responseBandsReduceButNeverDisableWildlifeSpawns() {
        assertEquals(1.0, WildlifeDisturbancePolicy.spawnChance(0.24, SETTINGS));
        assertEquals(0.85, WildlifeDisturbancePolicy.spawnChance(0.25, SETTINGS));
        assertEquals(0.55, WildlifeDisturbancePolicy.spawnChance(0.50, SETTINGS));
        assertEquals(0.25, WildlifeDisturbancePolicy.spawnChance(0.75, SETTINGS));
        assertTrue(WildlifeDisturbancePolicy.spawnChance(1.0, SETTINGS) > 0.0);
    }

    @Test
    void onlyStrongBandTriggersDestinationAvoidance() {
        assertFalse(WildlifeDisturbancePolicy.stronglyAvoided(0.74, SETTINGS));
        assertTrue(WildlifeDisturbancePolicy.stronglyAvoided(0.75, SETTINGS));
    }

    @Test
    void malformedOrderingAndMultipliersAreMadeConservative() {
        WildlifeDisturbancePolicy.Settings malformed =
                new WildlifeDisturbancePolicy.Settings(0.70, 0.40, 0.20, 0.50, 0.90, 0.80);

        assertEquals(0.70, malformed.mildThreshold());
        assertEquals(0.70, malformed.reducedThreshold());
        assertEquals(0.70, malformed.strongThreshold());
        assertEquals(0.50, malformed.mildSpawnMultiplier());
        assertEquals(0.50, malformed.reducedSpawnMultiplier());
        assertEquals(0.50, malformed.strongSpawnMultiplier());
    }
}
