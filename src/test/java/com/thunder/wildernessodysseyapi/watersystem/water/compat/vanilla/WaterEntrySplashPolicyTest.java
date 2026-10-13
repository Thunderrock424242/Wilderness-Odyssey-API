package com.thunder.wildernessodysseyapi.watersystem.water.compat.vanilla;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaterEntrySplashPolicyTest {

    @Test
    void shallowEntryIsSilentAndThreeBlocksIsInclusive() {
        assertFalse(WaterEntrySplashPolicy.shouldPlay(0.0f, false, 3.0));
        assertFalse(WaterEntrySplashPolicy.shouldPlay(2.99f, false, 3.0));
        assertTrue(WaterEntrySplashPolicy.shouldPlay(3.0f, false, 3.0));
        assertTrue(WaterEntrySplashPolicy.shouldPlay(8.0f, false, 3.0));
    }

    @Test
    void fishAreSilentRegardlessOfFallHeight() {
        assertFalse(WaterEntrySplashPolicy.shouldPlay(3.0f, true, 3.0));
        assertFalse(WaterEntrySplashPolicy.shouldPlay(50.0f, true, 3.0));
    }

    @Test
    void serverCanRequireAHigherFallButNeverLowerThanThreeBlocks() {
        assertFalse(WaterEntrySplashPolicy.shouldPlay(3.0f, false, 5.0));
        assertTrue(WaterEntrySplashPolicy.shouldPlay(5.0f, false, 5.0));
        assertFalse(WaterEntrySplashPolicy.shouldPlay(2.0f, false, 0.0));
    }

    @Test
    void invalidFallDistanceCannotCreateAnImpactSound() {
        assertFalse(WaterEntrySplashPolicy.shouldPlay(-1.0f, false, 3.0));
        assertFalse(WaterEntrySplashPolicy.shouldPlay(Float.NaN, false, 3.0));
        assertFalse(WaterEntrySplashPolicy.shouldPlay(Float.POSITIVE_INFINITY, false, 3.0));
    }
}
