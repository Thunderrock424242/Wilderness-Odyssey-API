package com.thunder.wildernessodysseyapi.watersystem.water.entity;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Stores client-computed, inertially smoothed boat surface response.
 *
 * <p>Bobbing is kept render-only rather than changing the entity position each
 * tick. That avoids cumulative vertical drift and server correction while the
 * boat still appears to follow the modeled water surface.</p>
 */
public final class BoatTiltStore {

    private static final float[] FLAT = new float[]{0.0f, 0.0f, 0.0f};
    private static final float ANGULAR_STIFFNESS = 0.18f;
    private static final float ANGULAR_DAMPING = 0.64f;
    private static final float BOB_STIFFNESS = 0.24f;
    private static final float BOB_DAMPING = 0.70f;
    private static final ConcurrentHashMap<Integer, float[]> RESPONSES =
            new ConcurrentHashMap<>(32);

    private BoatTiltStore() {
    }

    /**
     * Stores pitch, roll, and vertical render offset for one boat.
     */
    public static void set(int entityId, float pitch, float roll, float bob) {
        float[] response = RESPONSES.computeIfAbsent(entityId, key -> new float[6]);
        integrateResponse(response, pitch, roll, bob);
    }

    /**
     * Returns {@code [pitch, roll, bob]} or a shared flat response when absent.
     */
    public static float[] get(int entityId) {
        return RESPONSES.getOrDefault(entityId, FLAT);
    }

    // Spring-damper integration gives visible pitch, roll, and heave angular
    // momentum instead of snapping a rigid model to each sampled wave slope.
    static void integrateResponse(float[] response, float pitch, float roll, float bob) {
        integrateAxis(response, 0, 3, pitch, ANGULAR_STIFFNESS, ANGULAR_DAMPING);
        integrateAxis(response, 1, 4, roll, ANGULAR_STIFFNESS, ANGULAR_DAMPING);
        integrateAxis(response, 2, 5, bob, BOB_STIFFNESS, BOB_DAMPING);
    }

    private static void integrateAxis(
            float[] response,
            int valueIndex,
            int velocityIndex,
            float target,
            float stiffness,
            float damping
    ) {
        float acceleration = (target - response[valueIndex]) * stiffness;
        response[velocityIndex] = (response[velocityIndex] + acceleration) * damping;
        response[valueIndex] += response[velocityIndex];
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
