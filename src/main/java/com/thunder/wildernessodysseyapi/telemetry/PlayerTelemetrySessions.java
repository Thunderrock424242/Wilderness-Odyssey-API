package com.thunder.wildernessodysseyapi.telemetry;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-thread session identity and monotonic duration; no network or client state is involved. */
final class PlayerTelemetrySessions {
    private final Map<UUID, Session> sessions = new HashMap<>();

    Session begin(UUID player, Instant now, long nanos) {
        if (sessions.containsKey(player)) {
            return null;
        }
        Session session = new Session(UUID.randomUUID(), now, nanos);
        sessions.put(player, session);
        return session;
    }

    Session end(UUID player) {
        return sessions.remove(player);
    }

    void clear() {
        sessions.clear();
    }

    record Session(UUID id, Instant startedAt, long startNanos) {
        long durationSeconds(long nowNanos) {
            return Math.max(0, (nowNanos - startNanos) / 1_000_000_000L);
        }
    }
}