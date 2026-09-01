package com.thunder.wildernessodysseyapi.watersystem.ocean.coast;

/**
 * Immutable client-local result of one deterministic wave entering BREAKING.
 *
 * <p>The event is consumed by bounded audio, spray, and diagnostics. It is not
 * authoritative gameplay state and is deliberately not sent over the network.</p>
 */
public record CoastalWaveBreakEvent(
        long segmentId,
        long cycleIndex,
        double x,
        double y,
        double z,
        float strength,
        float waveHeight,
        CoastalWaveProfile.ShoreType shoreType,
        float weatherIntensity
) {
    public CoastalWaveBreakEvent {
        x = finiteOr(x, 0.0);
        y = finiteOr(y, 0.0);
        z = finiteOr(z, 0.0);
        strength = clamp01(strength);
        waveHeight = finiteClamp(waveHeight, 0.0f, 8.0f, 0.0f);
        shoreType = shoreType == null ? CoastalWaveProfile.ShoreType.TEMPERATE : shoreType;
        weatherIntensity = clamp01(weatherIntensity);
    }

    private static double finiteOr(double value, double fallback) {
        return Double.isFinite(value) ? value : fallback;
    }

    private static float clamp01(float value) {
        return Float.isFinite(value) ? Math.max(0.0f, Math.min(1.0f, value)) : 0.0f;
    }

    private static float finiteClamp(float value, float minimum, float maximum, float fallback) {
        return Float.isFinite(value) ? Math.max(minimum, Math.min(maximum, value)) : fallback;
    }
}
