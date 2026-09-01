package com.thunder.wildernessodysseyapi.environment.glacial.runtime;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlacialWorkBudgetTest {

    @Test
    void neverExceedsConfiguredPerLevelCap() {
        assertEquals(16, GlacialWorkBudget.samplesPerTick(16, 1_000));
        assertEquals(7, GlacialWorkBudget.samplesPerTick(7, 100));
    }

    @Test
    void scalesDownForSmallLoadedSetsAndStopsWhenNoWorkExists() {
        assertEquals(4, GlacialWorkBudget.samplesPerTick(16, 1));
        assertEquals(0, GlacialWorkBudget.samplesPerTick(16, 0));
        assertEquals(0, GlacialWorkBudget.samplesPerTick(0, 8));
    }
}
