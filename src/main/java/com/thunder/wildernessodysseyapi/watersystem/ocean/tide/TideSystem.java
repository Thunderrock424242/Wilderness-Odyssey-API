package com.thunder.wildernessodysseyapi.watersystem.ocean.tide;

import net.minecraft.world.level.Level;

/**
 * TideSystem
 *
 * Calculates the current tide height offset based on the Minecraft
 * moon phase (0–7) and time-of-day, producing a smooth tidal cycle.
 *
 * Minecraft moon phases:
 *   0 = Full moon     → highest tide (spring tide)
 *   1 = Waning gibbous
 *   2 = Last quarter
 *   3 = Waning crescent
 *   4 = New moon      → second highest (spring tide)
 *   5 = Waxing crescent
 *   6 = First quarter → lowest tide (neap tide)
 *   7 = Waxing gibbous
 *
 * Tidal model:
 *   - Spring tides at full + new moon (phases 0, 4) → ±MAX_SPRING blocks
 *   - Neap tides at quarter moons (phases 2, 6) → ±MAX_NEAP blocks
 *   - Two tidal cycles per Minecraft day (semidiurnal tide)
 *   - Tide height = lunar_amplitude(phase) * sin(2 * dayFraction * 2π)
 */
public class TideSystem {

    // Maximum tide rise/fall during spring tides (blocks)
    public static final float MAX_SPRING_AMPLITUDE = 1.8f;

    // Maximum tide rise/fall during neap tides (blocks)
    public static final float MAX_NEAP_AMPLITUDE   = 0.6f;

    // One Minecraft day = 24000 ticks
    private static final float TICKS_PER_DAY = 24000f;

    // Two high tides per day (semidiurnal)
    private static final float TIDAL_CYCLES_PER_DAY = 2f;

    /**
     * Get the current tide height offset in blocks.
     * Positive = high tide (water higher), negative = low tide.
     *
     * @param level  the world (used for day time + moon phase)
     * @return       Y offset in blocks
     */
    public static float getTideOffset(Level level) {
        int moonPhase = level.getMoonPhase();           // 0–7
        long dayTime  = level.getDayTime() % (long)TICKS_PER_DAY;

        float lunarAmplitude = getLunarAmplitude(moonPhase);
        float dayFraction    = dayTime / TICKS_PER_DAY; // 0 → 1

        // Semidiurnal: two complete cycles per day
        float tideAngle = dayFraction * TIDAL_CYCLES_PER_DAY * 2f * (float)Math.PI;
        return lunarAmplitude * (float)Math.sin(tideAngle);
    }

    /**
     * Get the rate of tide change (blocks/second) — used for current strength.
     * Derivative of getTideOffset with respect to time.
     */
    public static float getTideRate(Level level) {
        int moonPhase = level.getMoonPhase();
        long dayTime  = level.getDayTime() % (long)TICKS_PER_DAY;

        float lunarAmplitude = getLunarAmplitude(moonPhase);
        float dayFraction    = dayTime / TICKS_PER_DAY;

        float tideAngle = dayFraction * TIDAL_CYCLES_PER_DAY * 2f * (float)Math.PI;
        float dAngleDt  = TIDAL_CYCLES_PER_DAY * 2f * (float)Math.PI / TICKS_PER_DAY * 20f; // per second
        return lunarAmplitude * dAngleDt * (float)Math.cos(tideAngle);
    }

    /**
     * Get a [0, 1] visual indicator of current tide level.
     * 1.0 = highest possible tide, 0.0 = lowest possible tide.
     */
    public static float getTideNormalised(Level level) {
        return (getTideOffset(level) / MAX_SPRING_AMPLITUDE) * 0.5f + 0.5f;
    }

    /**
     * Get descriptive tide name for display (e.g. HUD or debug).
     */
    public static String getTideName(Level level) {
        float offset = getTideOffset(level);
        float rate   = getTideRate(level);
        int phase    = level.getMoonPhase();

        String typeName = (phase == 0 || phase == 4) ? "Spring" : 
                          (phase == 2 || phase == 6) ? "Neap" : "Mixed";

        if (Math.abs(rate) < 0.001f) {
            return typeName + (offset > 0 ? " High Tide" : " Low Tide");
        }
        return typeName + (rate > 0 ? " Flooding" : " Ebbing");
    }

    /**
     * Returns the tidal direction vector (normalised XZ) for ocean current.
     * During flooding tide the current flows inland (positive Z axis by convention).
     * During ebbing tide it flows seaward.
     */
    public static float[] getTidalCurrentDirection(Level level) {
        float rate = getTideRate(level);
        // Simplified: tidal current runs along Z axis, reversed by ebb/flood
        float sign = rate > 0 ? 1f : -1f;
        return new float[]{ 0f, sign };
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Map moon phase (0–7) to a tidal amplitude.
     * Uses a cosine interpolation between spring and neap.
     *
     * Phase 0 (full) and phase 4 (new) → spring tide (max amplitude)
     * Phase 2 and phase 6 (quarters)    → neap tide (min amplitude)
     */
    private static float getLunarAmplitude(int moonPhase) {
        // Normalise phase to 0–2π (one full lunar cycle)
        float angle = moonPhase / 8f * 2f * (float)Math.PI;

        // cos²(angle/2) maps: 0→1, π/2→0.5, π→0, ...  then re-map to [neap, spring]
        float t = (float)Math.cos(angle);  // -1 to 1
        float normalised = (t + 1f) * 0.5f; // 0 to 1

        return MAX_NEAP_AMPLITUDE + normalised * (MAX_SPRING_AMPLITUDE - MAX_NEAP_AMPLITUDE);
    }
}
