package com.thunder.wildernessodysseyapi.util;

import com.thunder.wildernessodysseyapi.MemUtils.MemoryUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MemoryUtilsTest {

    @Test
    @DisplayName("Recommended RAM uses base value when usage is low")
    void testRecommendedBase() {
        int result = MemoryUtils.calculateRecommendedRAM(1000, 0);
        assertEquals(4096, result);
    }

    @Test
    @DisplayName("Adds extra RAM per mod batch")
    void testExtraPerMods() {
        int result = MemoryUtils.calculateRecommendedRAM(4000, 25);
        assertEquals(4352, result); // BASE 4096 + (25/10)*128 = 4352
    }

    @Test
    @DisplayName("Uses usage + 512 when usage is higher than recommendation")
    void testUsageAboveRecommended() {
        int result = MemoryUtils.calculateRecommendedRAM(5000, 0);
        assertEquals(5512, result);
    }

    @Test
    @DisplayName("Equal usage does not trigger bump")
    void testUsageEqualRecommended() {
        int result = MemoryUtils.calculateRecommendedRAM(4224, 10);
        assertEquals(4224, result);
    }
}
