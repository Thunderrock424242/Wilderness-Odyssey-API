package com.thunder.wildernessodysseyapi.weather.client.surface;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class SurfaceHeightCacheTest {
    @Test
    void probesAreLazyCachedAndBoundedWhenEveryCandidateIsDry() {
        var cache = new SurfaceHeightCache();
        AtomicInteger calls = new AtomicInteger();
        cache.beginRefresh(0, 0, 64, 0, 2);
        assertEquals(70, cache.height(0, 0, (x, z) -> { calls.incrementAndGet(); return 70; }));
        assertEquals(70, cache.height(0, 0, (x, z) -> 99));
        assertEquals(70, cache.height(1, 0, (x, z) -> { calls.incrementAndGet(); return 70; }));
        assertEquals(Integer.MIN_VALUE, cache.height(2, 0, (x, z) -> { calls.incrementAndGet(); return 70; }));
        assertEquals(2, calls.get());
        cache.beginRefresh(0, 0, 64, 10, 2);
        assertEquals(70, cache.height(0, 0, (x, z) -> 99));
        assertEquals(0, cache.probesThisRefresh());
        cache.beginRefresh(0, 0, 64, 40, 2);
        assertEquals(99, cache.height(0, 0, (x, z) -> 99));
    }

    @Test
    void movingAndClearingDiscardTerrainOutsideTheCurrentFootprint() {
        var cache = new SurfaceHeightCache();
        cache.beginRefresh(0, 0, 8, 0, 2);
        cache.height(0, 0, (x, z) -> 70);
        cache.beginRefresh(100, 100, 8, 1, 2);
        assertEquals(0, cache.size());
        cache.height(100, 100, (x, z) -> 80);
        cache.clear();
        assertEquals(0, cache.size());
    }
}
