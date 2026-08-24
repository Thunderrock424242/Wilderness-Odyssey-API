package com.thunder.wildernessodysseyapi.ecosystem.distant;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DistantWildlifeManagerCadenceTest {

    @Test
    void missedIntervalsCollapseIntoOneDuePass() {
        assertTrue(DistantWildlifeManager.intervalElapsed(400L, 100L, 100));
        assertFalse(DistantWildlifeManager.intervalElapsed(450L, 400L, 100));
        assertTrue(DistantWildlifeManager.intervalElapsed(500L, 400L, 100));
    }

    @Test
    void freshAndRolledBackClocksAreDueWithoutReplayingHistory() {
        assertTrue(DistantWildlifeManager.intervalElapsed(20L, Long.MIN_VALUE, 100));
        assertTrue(DistantWildlifeManager.intervalElapsed(20L, 200L, 100));
    }
}
