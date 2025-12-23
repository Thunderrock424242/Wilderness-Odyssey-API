package com.thunder.wildernessodysseyapi.chunk;

import java.util.Arrays;

/**
 * Compact fingerprint for a chunk slice payload.
 */
record SliceFingerprint(long hash, int length) {
    static SliceFingerprint from(long[] data) {
        long hash = 0xcbf29ce484222325L;
        for (long value : data) {
            hash ^= value;
            hash *= 0x100000001b3L;
        }
        hash ^= data.length * 0x9e3779b97f4a7c15L;
        return new SliceFingerprint(hash, data.length);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof SliceFingerprint other)) {
            return false;
        }
        return hash == other.hash && length == other.length;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new long[]{hash, length});
    }
}
