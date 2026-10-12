package com.thunder.wildernessodysseyapi.watersystem.water.volume;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.TreeMap;

/**
 * Deduplicated chunk-local work backed by durable canonical cells. The owner
 * admits only existing cells, so this index is bounded by packed cell positions.
 * Retry deadlines are indexed so unavailable neighborhoods cannot hide ready
 * water behind a bounded probe window and end the dimension's flow pass.
 */
final class WaterCellWorkQueue {
    private final Map<Integer, Long> dueTicks = new HashMap<>();
    private final TreeMap<Long, LinkedHashSet<Integer>> deadlines = new TreeMap<>();

    // An adjacent wake must not cancel a retry cooldown for missing terrain.
    void offer(int position, long dueTick) {
        Long previous = dueTicks.get(position);
        if (previous != null) {
            if (previous >= dueTick) return;
            removeDeadline(position, previous);
        }
        dueTicks.put(position, dueTick);
        deadlines.computeIfAbsent(dueTick, ignored -> new LinkedHashSet<>()).add(position);
    }

    void remove(int position) {
        Long due = dueTicks.remove(position);
        if (due != null) removeDeadline(position, due);
    }

    int size() { return dueTicks.size(); }

    void clear() {
        dueTicks.clear();
        deadlines.clear();
    }

    Integer poll(long tick) {
        var first = deadlines.firstEntry();
        if (first == null || first.getKey() > tick) return null;
        int position = first.getValue().iterator().next();
        remove(position);
        return position;
    }

    private void removeDeadline(int position, long due) {
        var positions = deadlines.get(due);
        positions.remove(position);
        if (positions.isEmpty()) deadlines.remove(due);
    }
}
