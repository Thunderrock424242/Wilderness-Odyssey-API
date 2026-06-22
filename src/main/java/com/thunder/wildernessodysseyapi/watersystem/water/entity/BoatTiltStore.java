package com.thunder.wildernessodysseyapi.watersystem.water.entity;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores client-computed boat surface response for the boat render mixin.
 *
 * <p>Bobbing is kept render-only rather than changing the entity position each
 * tick. That avoids cumulative vertical drift and server correction while the
 * boat still appears to follow the modeled water surface.</p>
 */
public final class BoatTiltStore {

    private static final float[] FLAT = new float[]{0.0f, 0.0f, 0.0f};
    private static final ConcurrentHashMap<Integer, float[]> RESPONSES =
            new ConcurrentHashMap<>(32);

    private BoatTiltStore() {
    }

    /**
     * Stores pitch, roll, and vertical render offset for one boat.
     */
    public static void set(int entityId, float pitch, float roll, float bob) {
        float[] response = RESPONSES.computeIfAbsent(entityId, key -> new float[3]);
        response[0] = pitch;
        response[1] = roll;
        response[2] = bob;
    }

    /**
     * Returns {@code [pitch, roll, bob]} or a shared flat response when absent.
     */
    public static float[] get(int entityId) {
        return RESPONSES.getOrDefault(entityId, FLAT);
    }

    /** Removes response state for an entity that is no longer tracked. */
    public static void remove(int entityId) {
        RESPONSES.remove(entityId);
    }

    /** Clears all client boat response state during world shutdown. */
    public static void clear() {
        RESPONSES.clear();
    }
}
