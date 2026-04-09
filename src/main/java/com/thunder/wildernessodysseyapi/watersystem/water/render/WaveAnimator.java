package com.thunder.wildernessodysseyapi.watersystem.water.render;

/**
 * WaveAnimator
 *
 * Provides wave height values for any world-space (x, z) position.
 * Uses a combination of two sine waves at different frequencies and
 * directions to produce natural-looking ocean surface motion.
 *
 * Wave height formula:
 *   h(x, z, t) = A1 * sin(F1*x + F1*z*0.7 + t*S1)
 *              + A2 * sin(F2*x*0.8 - F2*z + t*S2)
 *
 * Tune the constants below to match the visual style you want.
 */
public class WaveAnimator {

    // --- Tuning constants ---

    /** Maximum crest-to-trough amplitude in blocks (0.1 = 1/10th of a block) */
    public static final float WAVE_AMPLITUDE_1 = 0.07f;
    public static final float WAVE_AMPLITUDE_2 = 0.04f;

    /** Spatial frequency — higher = tighter wave spacing */
    public static final float WAVE_FREQ_1 = 0.35f;
    public static final float WAVE_FREQ_2 = 0.6f;

    /** Time speed multiplier — higher = faster waves */
    public static final float WAVE_SPEED_1 = 1.2f;
    public static final float WAVE_SPEED_2 = 0.8f;

    // --- Internal state ---

    private static float currentTime = 0f;
    private static long lastFrameTime = -1L;

    /**
     * Call once per frame (from the render mixin HEAD inject).
     * Advances the wave timer using real elapsed time so waves
     * look the same regardless of game tick rate or TPS.
     */
    public static void updateIfNeeded() {
        long now = System.currentTimeMillis();
        if (lastFrameTime < 0) {
            lastFrameTime = now;
            return;
        }
        float deltaSeconds = (now - lastFrameTime) / 1000f;
        currentTime += deltaSeconds;
        lastFrameTime = now;
    }

    /**
     * Returns the Y offset to apply to a water surface vertex at world (x, z).
     *
     * @param worldX  block X coordinate (integer part is fine)
     * @param worldZ  block Z coordinate (integer part is fine)
     * @return        Y displacement in blocks (can be negative)
     */
    public static float getWaveHeight(float worldX, float worldZ) {
        float wave1 = WAVE_AMPLITUDE_1
                * (float) Math.sin(WAVE_FREQ_1 * worldX + WAVE_FREQ_1 * worldZ * 0.7f
                                   + currentTime * WAVE_SPEED_1);

        float wave2 = WAVE_AMPLITUDE_2
                * (float) Math.sin(WAVE_FREQ_2 * worldX * 0.8f - WAVE_FREQ_2 * worldZ
                                   + currentTime * WAVE_SPEED_2);

        return wave1 + wave2;
    }

    /**
     * Returns the current animation time in seconds.
     * Useful for passing as a shader uniform.
     */
    public static float getTime() {
        return currentTime;
    }
}
