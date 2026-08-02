package com.thunder.wildernessodysseyapi.watersystem.water.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the pure caps and environmental gates behind ambient particles. */
class WaterAmbientEffectsTest {

    @Test
    void emissionCadenceAndAttemptsStayStrictlyBounded() {
        assertTrue(WaterAmbientEffects.shouldEmitAt(40L, 2));
        assertFalse(WaterAmbientEffects.shouldEmitAt(41L, 2));
        assertFalse(WaterAmbientEffects.shouldEmitAt(40L, 0));
        assertEquals(15, WaterAmbientEffects.attemptBudget(3));
        assertEquals(0, WaterAmbientEffects.attemptBudget(-2));
    }

    @Test
    void sprayRequiresBreakingOceanOrFastCurrentEnergy() {
        assertEquals(0.0f, WaterAmbientEffects.surfaceSprayIntensity(
                0.1f, 0.0f, 1.0f, 0.0f, 0.0f));
        assertTrue(WaterAmbientEffects.surfaceSprayIntensity(
                0.9f, 0.95f, 1.0f, 0.0f, 0.5f) > 0.6f);
        assertTrue(WaterAmbientEffects.surfaceSprayIntensity(
                0.1f, 0.0f, 0.0f, 1.5f, 1.0f) > 0.9f);
    }

    @Test
    void bubbleChanceRisesWithCurrentAndPlayerMotionButStaysBounded() {
        assertEquals(0.08f, WaterAmbientEffects.bubbleProbability(0.0f, 0.0f));
        assertEquals(0.50f, WaterAmbientEffects.bubbleProbability(4.0f, 4.0f));
    }
}
