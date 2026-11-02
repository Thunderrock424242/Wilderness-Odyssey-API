package com.thunder.wildernessodysseyapi.watersystem.water.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WaterMeshLodPolicyTest {
    @Test
    void reducesDistantCalmWaterButRetainsNearAndSensitiveDetail() {
        assertEquals(2, select(2, false, 32, 0, 1));
        assertEquals(1, select(2, false, 160, 0, 2));
        assertEquals(2, select(2, true, 500, 0, 1));
        assertEquals(1, select(1, true, 0, 1, 2));
        assertEquals(2, WaterMeshLodPolicy.select(2, false, false, 500 * 500, 0, 1, 64));
    }

    @Test
    void distanceBandPreventsRepeatedRebuildsAndStormsExtendDetail() {
        assertEquals(1, select(2, false, 80, 0, 1));
        assertEquals(2, select(2, false, 80, 0, 2));
        assertEquals(2, select(2, false, 88, 0, 2));
        assertEquals(1, select(2, false, 89, 0, 2));
        assertEquals(2, select(2, false, 100, 1, 1));
        assertEquals(1, select(2, false, 100, 0, 1));
    }

    @Test
    void deduplicatesRequestsAndDefersDemotionButAllowsPromptPromotion() {
        assertFalse(WaterMeshLodPolicy.shouldQueue(2, 2, 1, 39));
        assertTrue(WaterMeshLodPolicy.shouldQueue(2, 2, 1, 40));
        assertFalse(WaterMeshLodPolicy.shouldQueue(2, 1, 1, 40));
        assertTrue(WaterMeshLodPolicy.shouldQueue(1, 1, 2, 0));
        assertFalse(WaterMeshLodPolicy.shouldQueue(2, 2, 2, 100));
    }

    @Test
    void malformedPositionDoesNotReduceDetail() {
        assertEquals(2, WaterMeshLodPolicy.select(2, true, false, Double.NaN, 0, 1, 64));
        assertEquals(2, WaterMeshLodPolicy.select(2, true, false, -1, 0, 1, 64));
    }

    @Test
    void retainsShallowRiverAndCurrentRichColumnsAndUsesExactChunkDistance() {
        assertTrue(WaterMeshLodPolicy.sensitiveColumn(5.9f, 0, 0, 0));
        assertTrue(WaterMeshLodPolicy.sensitiveColumn(24, 0.45f, 0, 0));
        assertTrue(WaterMeshLodPolicy.sensitiveColumn(24, 0, 0, 128));
        assertFalse(WaterMeshLodPolicy.sensitiveColumn(24, 0.1f, 0, 0));
        assertEquals(0, WaterMeshLodPolicy.distanceSquaredToChunk(16, 8, 0, 0));
        assertEquals(16, WaterMeshLodPolicy.distanceSquaredToChunk(20, 8, 0, 0));
        assertEquals(256, WaterMeshLodPolicy.distanceSquaredToChunk(30_000_016, 8, 29_999_984, 0));
    }

    private static int select(int ceiling, boolean sensitive, double distance, float sea, int previous) {
        return WaterMeshLodPolicy.select(ceiling, true, sensitive, distance * distance, sea, previous, 64);
    }
}
