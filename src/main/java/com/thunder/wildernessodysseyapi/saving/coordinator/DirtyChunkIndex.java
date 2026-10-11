package com.thunder.wildernessodysseyapi.saving.coordinator;

import java.util.LinkedHashMap;
import java.util.Objects;

/** Bounded server-thread metadata, with no chunk/level references and globally unique mutation generations. */
public final class DirtyChunkIndex {
    private final LinkedHashMap<Key, Entry> entries = new LinkedHashMap<>();
    private final int capacity;
    private final int minimumDirtyTicks;
    private final int minimumQuietTicks;
    private long generation;
    private long omitted;

    public DirtyChunkIndex(int capacity, int minimumDirtyTicks, int minimumQuietTicks) {
        this.capacity = Math.max(1, capacity);
        this.minimumDirtyTicks = Math.max(0, minimumDirtyTicks);
        this.minimumQuietTicks = Math.max(0, minimumQuietTicks);
    }

    public boolean markDirty(Key key, long tick) {
        Entry entry = entries.get(key);
        if (entry == null) {
            if (entries.size() >= capacity) {
                var iterator = entries.entrySet().iterator();
                int checked = 0;
                while (iterator.hasNext() && checked++ < 32) {
                    Entry candidate = iterator.next().getValue();
                    if (!candidate.dirty && candidate.pendingGeneration == 0) { iterator.remove(); break; }
                }
            }
            if (entries.size() >= capacity) { omitted++; return false; }
            entry = new Entry();
            entries.put(key, entry);
        }
        if (!entry.dirty) entry.firstDirtyTick = tick;
        entry.dirty = true;
        entry.lastDirtyTick = tick;
        entry.generation = ++generation;
        return true;
    }

    public void markClean(Key key) {
        Entry entry = entries.get(key);
        if (entry != null && entry.pendingGeneration == 0) entry.dirty = false;
    }

    /** At most 32 metadata checks per call, rotating skipped entries so hot chunks cannot starve cold ones. */
    public Key candidate(long tick, long availableNanos) {
        int checks = Math.min(32, entries.size());
        for (int check = 0; check < checks; check++) {
            var iterator = entries.entrySet().iterator();
            var item = iterator.next();
            Key key = item.getKey();
            Entry entry = item.getValue();
            iterator.remove();
            entries.put(key, entry);
            if (eligible(entry, tick) && entry.estimatedCost <= availableNanos) return key;
        }
        return null;
    }

    public long begin(Key key) {
        Entry entry = entries.get(key);
        if (entry == null || !entry.dirty || entry.pendingGeneration != 0) return 0;
        entry.pendingGeneration = entry.generation;
        return entry.generation;
    }

    /** Returns true only when the matching failed write needs native dirty state restored. */
    public boolean complete(Key key, long savedGeneration, long tick, boolean success, long costNanos) {
        Entry entry = entries.get(key);
        if (entry == null || savedGeneration == 0 || entry.pendingGeneration != savedGeneration) return false;
        entry.pendingGeneration = 0;
        // Keep a conservative prior cost; an expensive chunk is not knowingly admitted below that cost.
        entry.estimatedCost = Math.max(1, costNanos);
        if (success) {
            entry.lastSuccessfulSaveTick = tick;
            if (entry.generation == savedGeneration) entry.dirty = false;
        } else entry.dirty = true;
        return !success;
    }

    public void forget(Key key) { entries.remove(key); }
    public void forgetDimension(String dimension) { entries.keySet().removeIf(key -> key.dimension().equals(dimension)); }
    public void clear() { entries.clear(); }

    public Snapshot snapshot(long tick) {
        int dirty = 0, hot = 0, cold = 0, pending = 0;
        for (Entry entry : entries.values()) {
            if (entry.pendingGeneration != 0) pending++;
            if (entry.dirty) { dirty++; if (eligible(entry, tick)) cold++; else hot++; }
        }
        return new Snapshot(entries.size(), dirty, hot, cold, pending, omitted);
    }

    private boolean eligible(Entry entry, long tick) {
        return entry.dirty && entry.pendingGeneration == 0 && tick - entry.firstDirtyTick >= minimumDirtyTicks
                && tick - entry.lastDirtyTick >= minimumQuietTicks;
    }

    public record Key(String dimension, long position) { public Key { Objects.requireNonNull(dimension); } }
    public record Snapshot(int tracked, int dirty, int hot, int cold, int pending, long omitted) { }
    private static final class Entry {
        private boolean dirty;
        private long firstDirtyTick;
        private long lastDirtyTick;
        private long generation;
        private long pendingGeneration;
        private long lastSuccessfulSaveTick;
        private long estimatedCost = 1_000_000;
    }
}
