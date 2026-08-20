package com.thunder.wildernessodysseyapi.watersystem.ocean.shore;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers the bounded fair scheduler used for shoreline regions. */
class ShorelineWaterManagerTest {

    @Test
    void rotatesLimitedBudgetAcrossEveryRegion() {
        int[] updatesPerRegion = new int[5];
        int cursor = 0;

        for (int tick = 0; tick < 5; tick++) {
            int[] order = ShorelineWaterManager.roundRobinOrder(5, cursor, 2);
            Arrays.stream(order).forEach(index -> updatesPerRegion[index]++);
            cursor = (cursor + order.length) % updatesPerRegion.length;
        }

        assertArrayEquals(new int[]{2, 2, 2, 2, 2}, updatesPerRegion);
    }

    @Test
    void oversizedBudgetNeverUpdatesARegionTwicePerTick() {
        int[] order = ShorelineWaterManager.roundRobinOrder(3, 2, 20);

        assertArrayEquals(new int[]{2, 0, 1}, order);
    }

    @Test
    void emptyOrPausedSchedulerProducesNoWork() {
        assertEquals(0, ShorelineWaterManager.roundRobinOrder(0, 0, 4).length);
        assertEquals(0, ShorelineWaterManager.roundRobinOrder(4, 0, 0).length);
    }

    @Test
    void bathymetryRefreshBudgetAllowsOnlyOneRegionPerLevelTick() {
        assertTrue(ShorelineWaterManager.hasBathymetryRefreshCapacity(0));
        assertFalse(ShorelineWaterManager.hasBathymetryRefreshCapacity(1));
        assertFalse(ShorelineWaterManager.hasBathymetryRefreshCapacity(10));
    }

    @Test
    void newAndExpiredBathymetryRefreshesWithoutLongOverflow() {
        assertTrue(ShorelineWaterManager.isBathymetryRefreshDue(0L, Long.MIN_VALUE));
        assertFalse(ShorelineWaterManager.isBathymetryRefreshDue(99L, 0L));
        assertTrue(ShorelineWaterManager.isBathymetryRefreshDue(100L, 0L));
        assertTrue(ShorelineWaterManager.isBathymetryRefreshDue(20L, 200L));
    }
}
