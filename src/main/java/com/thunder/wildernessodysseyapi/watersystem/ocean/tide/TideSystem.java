package com.thunder.wildernessodysseyapi.watersystem.ocean.tide;

import net.minecraft.world.level.Level;

/**
 * Computes moon-phase-driven ocean tides for Wilderness water.
 *
 * <p>The model keeps the gameplay cost tiny: it samples synchronized Minecraft
 * world time and moon phase, then returns a deterministic semidiurnal tide.
 * Full and new moons create spring tides, quarter moons create neap tides, and
 * the tide timing drifts through the lunar cycle so each moon phase has a
 * distinct ocean feel.</p>
 */
public final class TideSystem {

    /** Maximum tide rise/fall during spring tides, in blocks. */
    public static final float MAX_SPRING_AMPLITUDE = 1.8f;
    /** Maximum tide rise/fall during neap tides, in blocks. */
    public static final float MAX_NEAP_AMPLITUDE = 0.6f;

    private static final float TICKS_PER_DAY = 24_000.0f;
    private static final float LUNAR_PHASES = 8.0f;
    private static final float TIDAL_CYCLES_PER_DAY = 2.0f;
    private static final float TWO_PI = (float) (Math.PI * 2.0);

    private TideSystem() {
    }

    /**
     * Returns the current tide height offset in blocks.
     *
     * <p>Positive values are high tide; negative values are low tide.</p>
     */
    public static float getTideOffset(Level level) {
        return sample(level).offset();
    }

    /**
     * Returns the current tide velocity in blocks per second.
     *
     * <p>This is the main signal used for tidal current strength. It ignores
     * the tiny derivative of the slowly changing spring/neap amplitude because
     * gameplay currents should follow flood/ebb motion, not the week-scale
     * amplitude envelope.</p>
     */
    public static float getTideRate(Level level) {
        return sample(level).rate();
    }

    /** Returns a normalized 0..1 display value for the current tide height. */
    public static float getTideNormalised(Level level) {
        return sample(level).normalised();
    }

    /** Returns a short descriptive tide name for HUD/debug display. */
    public static String getTideName(Level level) {
        TideSample sample = sample(level);
        String typeName = sample.springFactor() > 0.72f
                ? "Spring"
                : sample.springFactor() < 0.28f
                        ? "Neap"
                        : "Mixed";

        if (Math.abs(sample.rate()) < 0.001f) {
            return typeName + (sample.offset() > 0.0f ? " High Tide" : " Low Tide");
        }
        return typeName + (sample.rate() > 0.0f ? " Flooding" : " Ebbing");
    }

    /**
     * Returns the simplified tidal current direction in X/Z.
     *
     * <p>The current reverses during ebb and flood. Shore systems can blend
     * this with their local coastline normal later; this global vector is a
     * cheap deterministic fallback.</p>
     */
    public static float[] getTidalCurrentDirection(Level level) {
        float sign = getTideRate(level) > 0.0f ? 1.0f : -1.0f;
        return new float[]{0.0f, sign};
    }

    /** Returns a complete tide sample for renderers, physics, HUDs, and tests. */
    public static TideSample sample(Level level) {
        long dayTime = Math.max(0L, level.getDayTime());
        float dayFraction = (dayTime % (long) TICKS_PER_DAY) / TICKS_PER_DAY;
        float lunarPhase = fractionalMoonPhase(level, dayFraction);
        float springFactor = lunarSpringFactor(lunarPhase);
        float amplitude = MAX_NEAP_AMPLITUDE
                + springFactor * (MAX_SPRING_AMPLITUDE - MAX_NEAP_AMPLITUDE);

        // Real tides lag the moon. This phase offset makes each Minecraft moon
        // phase shift high/low tide timing instead of only changing amplitude.
        float lunarTimingOffset = lunarPhase / LUNAR_PHASES * TWO_PI;
        float tideAngle = dayFraction * TIDAL_CYCLES_PER_DAY * TWO_PI + lunarTimingOffset;
        float offset = amplitude * (float) Math.sin(tideAngle);
        float anglePerSecond = TIDAL_CYCLES_PER_DAY * TWO_PI / TICKS_PER_DAY * 20.0f;
        float rate = amplitude * anglePerSecond * (float) Math.cos(tideAngle);
        float normalised = clamp01((offset / MAX_SPRING_AMPLITUDE) * 0.5f + 0.5f);

        return new TideSample(
                offset,
                rate,
                amplitude,
                normalised,
                level.getMoonPhase(),
                springFactor,
                tideAngle
        );
    }

    /** Returns the spring/neap amplitude for a discrete Minecraft moon phase. */
    public static float getLunarAmplitude(int moonPhase) {
        float springFactor = lunarSpringFactor(Math.floorMod(moonPhase, (int) LUNAR_PHASES));
        return MAX_NEAP_AMPLITUDE + springFactor * (MAX_SPRING_AMPLITUDE - MAX_NEAP_AMPLITUDE);
    }

    private static float fractionalMoonPhase(Level level, float dayFraction) {
        return Math.floorMod(level.getMoonPhase(), (int) LUNAR_PHASES) + dayFraction;
    }

    private static float lunarSpringFactor(float moonPhase) {
        float angle = moonPhase / LUNAR_PHASES * TWO_PI;
        return Math.abs((float) Math.cos(angle));
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    /** Immutable tide sample for one world tick. */
    public record TideSample(
            float offset,
            float rate,
            float amplitude,
            float normalised,
            int moonPhase,
            float springFactor,
            float tideAngle
    ) {
    }
}
