package com.thunder.wildernessodysseyapi.dataengine.cache;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataCacheTest {
    @Test
    void recordsHitMissExpirationAndInvalidation() {
        AtomicInteger hits = new AtomicInteger();
        AtomicInteger misses = new AtomicInteger();
        DataCache<String, Integer> cache = new DataCache<>(
                4,
                hits::incrementAndGet,
                misses::incrementAndGet,
                ignored -> { }
        );

        cache.put("temporary", 7, 5L, 10L);
        assertEquals(7, cache.get("temporary", 14L).orElseThrow());
        assertTrue(cache.get("temporary", 15L).isEmpty());
        cache.put("persistent", 9);
        assertTrue(cache.invalidate("persistent"));
        assertFalse(cache.invalidate("persistent"));
        assertEquals(1, hits.get());
        assertEquals(1, misses.get());
    }

    @Test
    void evictsLeastRecentlyUsedEntryAtBound() {
        DataCache<String, Integer> cache = new DataCache<>(2);
        cache.put("a", 1);
        cache.put("b", 2);
        cache.get("a", 0L);
        cache.put("c", 3);

        assertTrue(cache.get("a", 0L).isPresent());
        assertTrue(cache.get("b", 0L).isEmpty());
        assertTrue(cache.get("c", 0L).isPresent());
        assertEquals(2, cache.size());
    }
}
