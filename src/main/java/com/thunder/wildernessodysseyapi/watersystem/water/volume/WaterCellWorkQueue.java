package com.thunder.wildernessodysseyapi.watersystem.water.volume;

import java.util.LinkedHashMap;

/**
 * Deduplicated chunk-local work backed by durable canonical cells. The owner
 * admits only existing cells, so this index is bounded by packed cell positions.
 * Cooling entries rotate instead of blocking ready work behind them.
 */
final class WaterCellWorkQueue {
    private static final int MAX_PROBES = 8;
    private final LinkedHashMap<Integer, Long> dueTicks = new LinkedHashMap<>();

    // An adjacent wake must not cancel a retry cooldown for missing terrain.
    void offer(int position, long dueTick) { dueTicks.merge(position, dueTick, Math::max); }
    void remove(int position) { dueTicks.remove(position); }
    int size() { return dueTicks.size(); }
    void clear() { dueTicks.clear(); }

    Integer poll(long tick) {
        int probes = Math.min(MAX_PROBES, dueTicks.size());
        for (int probe = 0; probe < probes; probe++) {
            var iterator = dueTicks.entrySet().iterator();
            var entry = iterator.next();
            int position = entry.getKey();
            long due = entry.getValue();
            iterator.remove();
            if (due <= tick) return position;
            dueTicks.put(position, due);
        }
        return null;
    }
}
