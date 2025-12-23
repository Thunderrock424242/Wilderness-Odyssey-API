package com.thunder.wildernessodysseyapi.chunk;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Very small interning pool for long-array slices. Uses soft references so the JVM can
 * reclaim memory when pressure rises.
 */
final class SliceInternPool {
    private final Map<SliceFingerprint, SliceReference> pool = new ConcurrentHashMap<>();
    private final ReferenceQueue<long[]> queue = new ReferenceQueue<>();
    private final int maxEntries;

    SliceInternPool(int maxEntries) {
        this.maxEntries = Math.max(32, maxEntries);
    }

    long[] intern(SliceFingerprint fingerprint, long[] data) {
        reapStale();

        SliceReference existing = pool.get(fingerprint);
        long[] existingValue = existing == null ? null : existing.get();
        if (existingValue != null && Arrays.equals(existingValue, data)) {
            return existingValue;
        }

        long[] canonical = Arrays.copyOf(data, data.length);
        pool.put(fingerprint, new SliceReference(canonical, fingerprint, queue));
        evictIfNeeded();
        return canonical;
    }

    void clear() {
        pool.clear();
        while (queue.poll() != null) {
            // drain
        }
    }

    private void evictIfNeeded() {
        if (pool.size() <= maxEntries) {
            return;
        }
        Iterator<SliceFingerprint> it = pool.keySet().iterator();
        if (it.hasNext()) {
            it.next();
            it.remove();
        }
    }

    private void reapStale() {
        SliceReference ref;
        while ((ref = (SliceReference) queue.poll()) != null) {
            pool.remove(ref.fingerprint());
        }
    }

    private static final class SliceReference extends SoftReference<long[]> {
        private final SliceFingerprint fingerprint;

        private SliceReference(long[] referent, SliceFingerprint fingerprint, ReferenceQueue<long[]> queue) {
            super(referent, queue);
            this.fingerprint = fingerprint;
        }

        private SliceFingerprint fingerprint() {
            return fingerprint;
        }
    }
}
