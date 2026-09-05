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
}
