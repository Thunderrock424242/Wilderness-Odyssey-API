package com.thunder.wildernessodysseyapi.watersystem.water.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies bounded ripple aging against the same exact frame clock as surface wakes. */
class RippleRendererTest {

    @Test
    void oldWorldRipplesRetainFractionalExpansion() {
        long startTick = 1L << 28;
        float early = RippleRenderer.lifeFraction(
                WaterAnimationClock.sampleTick(startTick, 0.25f), startTick);
        float late = RippleRenderer.lifeFraction(
                WaterAnimationClock.sampleTick(startTick, 0.75f), startTick);
        assertEquals(0.25f / 30, early, 0.000001f);
        assertEquals(0.75f / 30, late, 0.000001f);
        assertTrue(late > early);
    }

    @Test
    void futureAndExpiredRipplesStayInsideTheirLifetime() {
        assertEquals(0, RippleRenderer.lifeFraction(99, 100));
        assertEquals(0, RippleRenderer.lifeFraction(100, 100));
        assertEquals(0.5f, RippleRenderer.lifeFraction(115, 100));
        assertEquals(1, RippleRenderer.lifeFraction(130, 100));
        assertEquals(1, RippleRenderer.lifeFraction(1000, 100));
    }
}
