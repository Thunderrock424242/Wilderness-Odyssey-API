package com.thunder.wildernessodysseyapi.weather.simulation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Retains immutable terrain aggregates for admitted cells and budgets refreshes.
 * Server-thread callers keep simulation running from stored inputs while the
 * oldest missing/expired terrain samples are refreshed in bounded passes.
 */
public final class AtmosphereClimateCache<T> {
    private final Map<Long, Entry<T>> entries = new HashMap<>();
    private final Set<Long> refreshable = new HashSet<>();
    private int samplesThisPass;

    /** Drops evicted cells and admits only a bounded oldest-first refresh set. */
    public void beginPass(Set<Long> retained, long tick, int refreshTicks, int budget) {
        entries.keySet().retainAll(retained);
        refreshable.clear();
        samplesThisPass = 0;
        var due = new ArrayList<Long>();
        for (long key : retained) {
            Entry<T> entry = entries.get(key);
            if (entry == null || entry.sampledAt == Long.MIN_VALUE
                    || tick < entry.sampledAt || tick - entry.sampledAt >= Math.max(20, refreshTicks)) {
                due.add(key);
            }
        }
        due.sort(Comparator.<Long>comparingLong(key -> {
            Entry<T> entry = entries.get(key);
            return entry == null ? Long.MIN_VALUE : entry.sampledAt;
        }).thenComparingLong(Long::longValue));
        for (int i = 0; i < Math.min(Math.max(0, budget), due.size()); i++) refreshable.add(due.get(i));
    }

    /** Returns stored context or takes one permitted immutable sample. */
    public T sample(long key, long tick, Supplier<T> sampler, Supplier<T> fallback) {
        Entry<T> entry = entries.get(key);
        if (refreshable.remove(key)) {
            T sampled = sampler.get();
            entries.put(key, new Entry<>(sampled, tick));
            samplesThisPass++;
            return sampled;
        }
        if (entry != null) return entry.value;
        // Unsampled fallback is deliberately still due on the next pass.
        T value = fallback.get();
        entries.put(key, new Entry<>(value, Long.MIN_VALUE));
        return value;
    }

    public T peek(long key) {
        Entry<T> entry = entries.get(key);
        return entry == null ? null : entry.value;
    }

    public int samplesThisPass() { return samplesThisPass; }

    /** Releases all level-derived context at unload/config generation changes. */
    public void clear() {
        entries.clear();
        refreshable.clear();
        samplesThisPass = 0;
    }

    private record Entry<T>(T value, long sampledAt) { }
}
