package com.thunder.wildernessodysseyapi.util;

/**
 * Collection of lightweight math helpers that avoid pulling in heavier
 * dependencies. These utilities are intentionally small and allocation free so
 * they can be used freely in hot code paths.
 */
public final class LightweightMath {
    private LightweightMath() {
    }

    /**
     * Linearly interpolates between {@code a} and {@code b} by {@code t}.
     */
    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /**
     * Clamps {@code value} between {@code min} and {@code max}.
     */
    public static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Floors the double value to an integer without creating garbage.
     */
    public static int fastFloor(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }
}
