package com.thunder.wildernessodysseyapi.watersystem.water.entity;

import java.util.concurrent.ConcurrentHashMap;

/**
 * BoatTiltStore
 *
 * Stores the computed pitch and roll angles for each boat entity
 * so the render mixin can read them when drawing the boat model.
 *
 * Keyed by entity ID (integer). Old entries are evicted when the
 * entity is no longer tracked (on death/dismount the entity tick
 * stops calling BoatTiltStore.set, so entries go stale but are
 * harmless — they're never read for removed entities).
 */
public class BoatTiltStore {

    // Packed float pair: [pitch, roll] per entity ID
    private static final ConcurrentHashMap<Integer, float[]> tilts =
        new ConcurrentHashMap<>(32);

    public static void set(int entityId, float pitch, float roll) {
        float[] angles = tilts.computeIfAbsent(entityId, k -> new float[2]);
        angles[0] = pitch;
        angles[1] = roll;
    }

    /** Returns [pitch, roll] for the given entity, or [0, 0] if not tracked. */
    public static float[] get(int entityId) {
        return tilts.getOrDefault(entityId, new float[]{0f, 0f});
    }

    public static void remove(int entityId) {
        tilts.remove(entityId);
    }

    public static void clear() {
        tilts.clear();
    }
}
