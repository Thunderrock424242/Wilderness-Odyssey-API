package com.thunder.wildernessodysseyapi.watersystem.water.network;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/** Fair per-player pending work; a missing nearby baseline gets one priority slot. */
final class WaterSyncSchedule {
    private final Map<Long, Long> pendingSince = new HashMap<>();
    private List<Candidate> candidates = List.of();
    private long nextChunk = Long.MIN_VALUE;

    List<Long> order(List<Candidate> pending, long tick) {
        candidates = pending.stream().sorted(Comparator.comparingInt(Candidate::distanceSquared)
                .thenComparingLong(Candidate::chunkKey)).toList();
        var retained = new HashSet<Long>();
        for (Candidate candidate : candidates) {
            retained.add(candidate.chunkKey);
            pendingSince.putIfAbsent(candidate.chunkKey, tick);
        }
        pendingSince.keySet().retainAll(retained);
        List<Long> result = new ArrayList<>(candidates.size());
        long priority = candidates.stream().filter(Candidate::baseline).mapToLong(Candidate::chunkKey)
                .findFirst().orElse(Long.MIN_VALUE);
        if (priority != Long.MIN_VALUE) result.add(priority);
        int start = 0;
        for (int i = 0; i < candidates.size(); i++) if (candidates.get(i).chunkKey == nextChunk) start = i;
        for (int i = 0; i < candidates.size(); i++) {
            long key = candidates.get((start + i) % candidates.size()).chunkKey;
            if (key != priority) result.add(key);
        }
        return result;
    }

    void visited(long key) {
        for (int i = 0; i < candidates.size(); i++) {
            if (candidates.get(i).chunkKey == key) {
                nextChunk = candidates.get((i + 1) % candidates.size()).chunkKey;
                return;
            }
        }
    }

    void forget(long key) { pendingSince.remove(key); }
    int backlog() { return pendingSince.size(); }
    long oldestPendingTicks(long tick) {
        return pendingSince.values().stream().mapToLong(since -> Math.max(0, tick - since)).max().orElse(0);
    }
    record Candidate(long chunkKey, int distanceSquared, boolean baseline) { }
}
