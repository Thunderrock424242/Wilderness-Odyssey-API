package com.thunder.wildernessodysseyapi.watersystem.water.render;

import org.junit.jupiter.api.Test;
import com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river.RiverChannelColumn;
import com.thunder.wildernessodysseyapi.watersystem.water.worldgen.river.RiverGenerationChunk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the pure caps and environmental gates behind ambient particles. */
class WaterAmbientEffectsTest {

    @Test
    void rapidAndConfluenceFoamUsesActualPhysicalMotionAndKeepsLakesCalm() {
        var stream = reach(RiverChannelColumn.Kind.STREAM, 64, 63, 64);
        var gentle = WaterAmbientEffects.reachActivity(stream, 0.1f, false);
        var rapid = WaterAmbientEffects.reachActivity(stream, 2.5f, false);
        var confluence = WaterAmbientEffects.reachActivity(stream, 1.2f, true);
        var ordinary = WaterAmbientEffects.reachActivity(stream, 1.2f, false);
        assertEquals(0, gentle.foam());
        assertTrue(rapid.foam() > 0.5f);
        assertTrue(confluence.foam() > ordinary.foam());
        assertEquals(0, rapid.mist());
        var lake = WaterAmbientEffects.reachActivity(
                reach(RiverChannelColumn.Kind.LAKE, 64, 60, 64), 4, true);
        assertEquals(0, lake.foam());
        assertEquals(0, lake.spray());
    }

    @Test
    void waterfallSprayAndPlungeMistScaleWithDropWithoutInventingStillWaterEnergy() {
        var shortFall = reach(RiverChannelColumn.Kind.WATERFALL, 64, 61, 66);
        var tallFall = reach(RiverChannelColumn.Kind.WATERFALL, 64, 61, 80);
        var shortActivity = WaterAmbientEffects.reachActivity(shortFall, 1.8f, false);
        var tallActivity = WaterAmbientEffects.reachActivity(tallFall, 1.8f, false);
        assertTrue(shortActivity.spray() > 0);
        assertTrue(tallActivity.mist() > shortActivity.mist());
        assertTrue(tallActivity.spray() <= 1);
        assertEquals(0, WaterAmbientEffects.reachActivity(tallFall, 0, true).spray());
        assertEquals(0, WaterAmbientEffects.reachActivity(tallFall, 0, true).mist());
    }

    private static RiverGenerationChunk.Reach reach(RiverChannelColumn.Kind kind, int water, int floor, int top) {
        return new RiverGenerationChunk.Reach(water, floor, top, 1, 0, 8, kind);
    }

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
