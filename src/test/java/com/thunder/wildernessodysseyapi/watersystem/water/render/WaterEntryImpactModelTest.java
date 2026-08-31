package com.thunder.wildernessodysseyapi.watersystem.water.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies that water-entry presentation scales with real impact energy. */
class WaterEntryImpactModelTest {

    @Test
    void gentleWadingKeepsAVisibleButSmallCue() {
        WaterEntryImpactModel.Impact impact = WaterEntryImpactModel.evaluate(
                0.6f, 1.8f, 0.0, 0.08, 0.0f, false, 8);

        assertTrue(impact.particleCount() >= 1);
        assertTrue(impact.particleCount() <= 3);
        assertTrue(impact.spawnRadius() < 0.6f);
    }

    @Test
    void fallingEntryProducesMoreSprayThanWading() {
        WaterEntryImpactModel.Impact wading = WaterEntryImpactModel.evaluate(
                0.6f, 1.8f, 0.0, 0.08, 0.0f, false, 8);
        WaterEntryImpactModel.Impact falling = WaterEntryImpactModel.evaluate(
                0.6f, 1.8f, 0.55, 0.20, 8.0f, false, 8);

        assertTrue(falling.strength() > wading.strength());
        assertTrue(falling.particleCount() > wading.particleCount());
        assertTrue(falling.upwardSpeed() > wading.upwardSpeed());
        assertTrue(falling.rippleStrength() > wading.rippleStrength());
    }

    @Test
    void lowTierBudgetStillEmitsAndNeverOverflows() {
        WaterEntryImpactModel.Impact strong = WaterEntryImpactModel.evaluate(
                1.4f, 0.7f, 0.8, 0.45, 12.0f, true, 3);
        WaterEntryImpactModel.Impact disabled = WaterEntryImpactModel.evaluate(
                1.4f, 0.7f, 0.8, 0.45, 12.0f, true, 0);

        assertEquals(3, strong.particleCount());
        assertEquals(0, disabled.particleCount());
        assertTrue(strong.spawnRadius() <= 1.55f);
        assertTrue(strong.strength() <= 1.0f);
    }

    @Test
    void invalidMotionFallsBackToFiniteBounds() {
        WaterEntryImpactModel.Impact impact = WaterEntryImpactModel.evaluate(
                Float.NaN, Float.POSITIVE_INFINITY,
                Double.NaN, Double.POSITIVE_INFINITY,
                Float.NaN, false, 8);

        assertTrue(Float.isFinite(impact.strength()));
        assertTrue(Float.isFinite(impact.spawnRadius()));
        assertTrue(Float.isFinite(impact.outwardSpeed()));
    }
}
