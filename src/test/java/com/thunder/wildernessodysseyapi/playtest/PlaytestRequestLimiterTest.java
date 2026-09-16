package com.thunder.wildernessodysseyapi.playtest;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class PlaytestRequestLimiterTest {
    @Test
    void cooldownSurvivesCompletionAndReconnect() {
        AtomicLong now = new AtomicLong();
        var limiter = new PlaytestRequestLimiter(now::get);
        UUID player = UUID.randomUUID();
        assertTrue(limiter.tryAcquire(player, 30));
        limiter.complete(player);
        now.set(29_000_000_000L);
        assertFalse(limiter.tryAcquire(UUID.fromString(player.toString()), 30));
        now.set(30_000_000_000L);
        assertTrue(limiter.tryAcquire(player, 30));
    }

    @Test
    void boundsConcurrentRequestsAndGlobalSpam() {
        AtomicLong now = new AtomicLong();
        var limiter = new PlaytestRequestLimiter(now::get);
        UUID first = UUID.randomUUID();
        assertTrue(limiter.tryAcquire(first, 1));
        assertFalse(limiter.tryAcquire(UUID.randomUUID(), 1));
        for (int i = 1; i < 4; i++) {
            now.addAndGet(1_000_000_000L);
            assertTrue(limiter.tryAcquire(UUID.randomUUID(), 1));
        }
        now.addAndGet(1_000_000_000L);
        assertFalse(limiter.tryAcquire(first, 1));
        assertFalse(limiter.tryAcquire(UUID.randomUUID(), 1));
        limiter.complete(first);
        assertTrue(limiter.tryAcquire(first, 1));
    }
}