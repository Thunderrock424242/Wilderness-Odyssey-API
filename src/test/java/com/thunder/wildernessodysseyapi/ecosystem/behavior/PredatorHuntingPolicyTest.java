package com.thunder.wildernessodysseyapi.ecosystem.behavior;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies every anti-massacre gate required before an ecosystem hunt begins. */
class PredatorHuntingPolicyTest {

    @Test
    void allowsHungryWildPredatorAfterCooldownWithProtectedPopulation() {
        assertTrue(PredatorHuntingPolicy.mayHunt(
                true, true, false, 0.85, 0.80, 20_000L, 10_000L, 5, 4));
    }

    @Test
    void rejectsTameBusyCoolingOrPopulationUnsafePredators() {
        assertFalse(PredatorHuntingPolicy.mayHunt(
                true, false, false, 0.85, 0.80, 20_000L, 10_000L, 5, 4));
        assertFalse(PredatorHuntingPolicy.mayHunt(
                true, true, true, 0.85, 0.80, 20_000L, 10_000L, 5, 4));
        assertFalse(PredatorHuntingPolicy.mayHunt(
                true, true, false, 0.85, 0.80, 9_999L, 10_000L, 5, 4));
        assertFalse(PredatorHuntingPolicy.mayHunt(
                true, true, false, 0.85, 0.80, 20_000L, 10_000L, 3, 4));
        assertFalse(PredatorHuntingPolicy.mayHunt(
                true, true, false, 0.79, 0.80, 20_000L, 10_000L, 5, 4));
    }
}
