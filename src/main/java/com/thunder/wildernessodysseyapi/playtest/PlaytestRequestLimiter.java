package com.thunder.wildernessodysseyapi.playtest;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/**
 * Bounds player webhook requests, including reconnects and concurrent submissions.
 * Instances belong to one command dispatcher/server lifetime, never to a player entity.
 */
public final class PlaytestRequestLimiter {
    private static final int MAX_TRACKED_PLAYERS = 4096;
    private static final int MAX_IN_FLIGHT = 4;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, Boolean> pending = new HashMap<>();
    private final LongSupplier clock;
    private long nextGlobalRequest;
    private boolean started;

    public PlaytestRequestLimiter() {
        this(System::nanoTime);
    }

    PlaytestRequestLimiter(LongSupplier clock) {
        this.clock = clock;
    }

    /** Reserves one submission without waiting; expired entries are pruned on demand. */
    public synchronized boolean tryAcquire(UUID player, int cooldownSeconds) {
        long now = clock.getAsLong();
        cooldowns.entrySet().removeIf(entry -> now - entry.getValue() >= 0);
        if (pending.containsKey(player) || cooldowns.containsKey(player)
                || pending.size() >= MAX_IN_FLIGHT || cooldowns.size() >= MAX_TRACKED_PLAYERS
                || (started && now - nextGlobalRequest < 0)) {
            return false;
        }
        cooldowns.put(player, now + Math.max(1, Math.min(3600, cooldownSeconds)) * 1_000_000_000L);
        pending.put(player, Boolean.TRUE);
        nextGlobalRequest = now + 1_000_000_000L;
        started = true;
        return true;
    }

    /** Honors a service throttle across all players without sleeping a server or network thread. */
    public synchronized void pause(int seconds) {
        nextGlobalRequest = clock.getAsLong() + Math.max(1, seconds) * 1_000_000_000L;
        started = true;
    }
    /** Releases the concurrency slot after completion; the cooldown survives logout/retry. */
    public synchronized void complete(UUID player) {
        pending.remove(player);
    }
}
