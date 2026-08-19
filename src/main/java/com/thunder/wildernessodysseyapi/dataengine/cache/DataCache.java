package com.thunder.wildernessodysseyapi.dataengine.cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.IntConsumer;

/**
 * THREAD SAFE. Bounded access-order cache with optional tick-based expiration.
 *
 * <p>Expiration is checked on lookup rather than by scanning the whole cache
 * every tick. The access-order map evicts the least recently used entry when
 * its configured bound is reached.</p>
 */
public final class DataCache<K, V> {
    private final LinkedHashMap<K, CacheEntry<V>> entries = new LinkedHashMap<>(16, 0.75F, true);
    private final int maximumEntries;
    private final Runnable hitRecorder;
    private final Runnable missRecorder;
    private final IntConsumer sizeRecorder;

    /** Creates a standalone cache without engine-level metric callbacks. */
    public DataCache(int maximumEntries) {
        this(maximumEntries, () -> { }, () -> { }, ignored -> { });
    }

    public DataCache(
            int maximumEntries,
            Runnable hitRecorder,
            Runnable missRecorder,
            IntConsumer sizeRecorder
    ) {
        if (maximumEntries < 1) {
            throw new IllegalArgumentException("Maximum cache size must be positive");
        }
        this.maximumEntries = maximumEntries;
        this.hitRecorder = Objects.requireNonNull(hitRecorder, "Hit recorder is required");
        this.missRecorder = Objects.requireNonNull(missRecorder, "Miss recorder is required");
        this.sizeRecorder = Objects.requireNonNull(sizeRecorder, "Size recorder is required");
    }

    /** Looks up a value and removes it lazily if its tick TTL has expired. */
    public synchronized Optional<V> get(K key, long currentTick) {
        CacheEntry<V> entry = entries.get(key);
        if (entry == null) {
            missRecorder.run();
            return Optional.empty();
        }
        if (entry.expiresAtTick >= 0L && currentTick >= entry.expiresAtTick) {
            entries.remove(key);
            sizeRecorder.accept(entries.size());
            missRecorder.run();
            return Optional.empty();
        }
        hitRecorder.run();
        return Optional.of(entry.value);
    }

    /** Inserts a value that remains until invalidated or evicted. */
    public synchronized void put(K key, V value) {
        put(key, value, -1L, 0L);
    }

    /**
     * Inserts a value with a tick TTL. A negative TTL disables expiration; zero
     * makes the entry unavailable at the current tick.
     */
    public synchronized void put(K key, V value, long ttlTicks, long currentTick) {
        Objects.requireNonNull(key, "Cache key is required");
        Objects.requireNonNull(value, "Cache value is required");
        long expiresAtTick = ttlTicks < 0L ? -1L : saturatingAdd(currentTick, ttlTicks);
        entries.put(key, new CacheEntry<>(value, expiresAtTick));
        evictToBound();
        sizeRecorder.accept(entries.size());
    }

    /** Invalidates one cached value. */
    public synchronized boolean invalidate(K key) {
        boolean removed = entries.remove(key) != null;
        if (removed) {
            sizeRecorder.accept(entries.size());
        }
        return removed;
    }

    /** Clears every value, normally on server stop or owning-world unload. */
    public synchronized void clear() {
        entries.clear();
        sizeRecorder.accept(0);
    }

    public synchronized int size() {
        return entries.size();
    }

    public int maximumEntries() {
        return maximumEntries;
    }

    private void evictToBound() {
        while (entries.size() > maximumEntries) {
            var iterator = entries.entrySet().iterator();
            if (!iterator.hasNext()) {
                return;
            }
            iterator.next();
            iterator.remove();
        }
    }

    private static long saturatingAdd(long value, long increment) {
        if (increment > 0L && value > Long.MAX_VALUE - increment) {
            return Long.MAX_VALUE;
        }
        return value + increment;
    }

    private record CacheEntry<V>(V value, long expiresAtTick) {
    }
}
