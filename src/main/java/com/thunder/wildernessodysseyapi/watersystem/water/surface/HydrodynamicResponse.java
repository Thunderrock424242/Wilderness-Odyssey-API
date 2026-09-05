package com.thunder.wildernessodysseyapi.watersystem.water.surface;

/** Pure, bounded response shared by local surface effects and terrain erosion. */
public final class HydrodynamicResponse {
    private HydrodynamicResponse() { }

    /** Depth-limited breaker fraction; depth is water thickness, never world Y. */
    public static float breaking(float waveHeight, float depth, float slope) {
        return unit((positive(waveHeight) / Math.max(0.15f, positive(depth)) - 0.55f) * 1.5f
                + positive(slope) * 0.12f);
    }

    /** Normalized pressure from current, breaking and falling-water impact. */
    public static float erosionPressure(float speed, float breaking, float fallingSpeed, float storm) {
        float current = unit(positive(speed) / 3.0f);
        float impact = unit(positive(fallingSpeed) / 6.0f);
        return unit((current * current * 0.55f + unit(breaking) * 0.30f + impact * 0.65f)
                * (0.35f + unit(storm) * 0.65f));
    }

    /** Accumulates finite exposure seconds without catch-up bursts after an unload. */
    public static float accumulate(float previous, float pressure, float seconds, float resistance) {
        float elapsed = Math.min(2.0f, positive(seconds));
        return Math.min(positive(resistance), Math.max(0.0f,
                positive(previous) + (unit(pressure) - 0.005f) * elapsed));
    }

    /** Sanitizes a normalized environmental input. */
    public static float unit(float value) { return Math.min(1.0f, positive(value)); }

    private static float positive(float value) {
        return Float.isFinite(value) ? Math.max(0.0f, value) : 0.0f;
    }
}
