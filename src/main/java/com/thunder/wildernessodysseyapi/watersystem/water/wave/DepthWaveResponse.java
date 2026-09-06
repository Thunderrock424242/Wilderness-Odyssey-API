package com.thunder.wildernessodysseyapi.watersystem.water.wave;

/** Bounded shoaling envelope shared by Gerstner consumers and its GPU mirror. */
public final class DepthWaveResponse {
    private DepthWaveResponse() { }

    /**
     * Shoals moderate-depth swell, dissipates it in very shallow water and
     * leaves deep-water carriers unchanged. Carrier time is deliberately not
     * multiplied by local depth: that would teleport crests when terrain edits
     * change the depth cache. The coastal solver owns shoreward propagation.
     */
    public static float amplitudeScale(float waterDepth) {
        float depth = Float.isFinite(waterDepth) ? Math.max(0.0f, waterDepth) : 24.0f;
        float shallow = Math.min(1.0f, depth / 3.0f);
        float offshore = Math.min(1.0f, Math.max(0.0f, (depth - 3.0f) / 13.0f));
        return (0.45f + shallow * 0.75f) * (1.0f - offshore) + offshore;
    }

    /** Smoothly sharpens shallow ocean crests without adding another wave clock or mesh. */
    public static float shapeCrest(float height, float depth, float oceanWeight) {
        float safeHeight = Float.isFinite(height) ? height : 0.0f;
        return safeHeight + crestFactor(depth, oceanWeight)
                * ((float) Math.sqrt(safeHeight * safeHeight + 0.04f) - 0.2f);
    }

    /** Chain-rule derivative shared by surface normals and orbital vertical velocity. */
    public static float crestDerivative(float height, float depth, float oceanWeight) {
        float safeHeight = Float.isFinite(height) ? height : 0.0f;
        return 1.0f + crestFactor(depth, oceanWeight) * safeHeight
                / (float) Math.sqrt(safeHeight * safeHeight + 0.04f);
    }

    private static float crestFactor(float depth, float oceanWeight) {
        float safeDepth = Float.isFinite(depth) ? Math.max(0.0f, depth) : 24.0f;
        float weight = Float.isFinite(oceanWeight) ? Math.max(0.0f, Math.min(1.0f, oceanWeight)) : 0.0f;
        return 0.35f * weight * smooth(0.3f, 2.0f, safeDepth) * (1.0f - smooth(6.0f, 16.0f, safeDepth));
    }

    private static float smooth(float low, float high, float value) {
        float t = Math.max(0.0f, Math.min(1.0f, (value - low) / (high - low)));
        return t * t * (3.0f - 2.0f * t);
    }
}
