package com.thunder.wildernessodysseyapi.watersystem.water.render;

/**
 * Legacy sine-wave sampler retained for binary compatibility.
 *
 * <p>Wilderness Odyssey's renderer now uses the Gerstner wave stack. New
 * integrations should use {@code GerstnerWaveAnimator} instead.</p>
 *
 * @deprecated use the active Gerstner wave APIs in the {@code wave} package
 */
@Deprecated(forRemoval = true)
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
    private static long lastFrameNanos = -1L;

    /**
     * Call once per frame (from the render mixin HEAD inject).
     * Advances the compatibility timer from a monotonic clock. The active
     * Gerstner renderer uses Minecraft world time; this legacy path still
     * clamps stalls so wall-clock changes and loading pauses cannot jump it.
     */
    public static void updateIfNeeded() {
        long now = System.nanoTime();
        if (lastFrameNanos < 0L) {
            lastFrameNanos = now;
            return;
        }
        float deltaSeconds = Math.max(0.0f, Math.min(
                0.10f,
                (now - lastFrameNanos) / 1_000_000_000.0f
        ));
        currentTime += deltaSeconds;
        lastFrameNanos = now;
    }

    /** Resets the dormant compatibility clock during a world handoff. */
    public static void reset() {
        currentTime = 0.0f;
        lastFrameNanos = -1L;
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
