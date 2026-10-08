package com.thunder.wildernessodysseyapi.weather.simulation;

import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class AtmosphereClimateCacheTest {
    @Test
    void retainedCellsAboveOldLruCapacityReuseTheirTerrainUntilRefreshIsDue() {
        var cache = new AtmosphereClimateCache<Integer>();
        Set<Long> retained = new HashSet<>();
        for (long key = 0; key < 3000; key++) retained.add(key);
        cache.beginPass(retained, 0, 400, 3000);
        AtomicInteger probes = new AtomicInteger();
        for (long key = 0; key < 3000; key++) assertEquals(7, cache.sample(key, 0, () -> { probes.incrementAndGet(); return 7; }, () -> 0));
        cache.beginPass(retained, 60, 400, 64);
        for (long key = 0; key < 3000; key++) assertEquals(7, cache.sample(key, 60, () -> { probes.incrementAndGet(); return 9; }, () -> 0));
        assertEquals(3000, probes.get());
        assertEquals(0, cache.samplesThisPass());
    }

    @Test
    void coldAndExpiredTerrainWorkIsBoundedFairAndRemovedWithItsCells() {
        var cache = new AtmosphereClimateCache<Integer>();
        Set<Long> retained = Set.of(1L, 2L, 3L, 4L, 5L);
        for (int pass = 0; pass < 3; pass++) {
            cache.beginPass(retained, pass, 400, 2);
            for (long key : new TreeSet<>(retained)) cache.sample(key, pass, () -> 7, () -> 0);
            assertTrue(cache.samplesThisPass() <= 2);
        }
        for (long key : retained) assertEquals(7, cache.peek(key));
        cache.beginPass(Set.of(5L), 600, 400, 1);
        assertNull(cache.peek(1));
        assertEquals(9, cache.sample(5, 600, () -> 9, () -> 0));
    }
}
