package com.thunder.wildernessodysseyapi.watersystem.water.render;

/** Pure intensity model for the bounded local river soundscape. */
public final class RiverSoundscapeModel {

    private RiverSoundscapeModel() {
    }

    /** Blends discharge, local current, confluence, flood, and rain energy. */
    public static float intensity(
            float discharge,
            float currentStrength,
            boolean confluence,
            boolean flooding,
            float rainIntensity
    ) {
        float flow = unit(discharge) * 0.40f
                + unit(currentStrength / 1.4f) * 0.42f
                + (confluence ? 0.12f : 0.0f)
                + (flooding ? 0.16f : 0.0f)
                + unit(rainIntensity) * 0.08f;
        return unit(flow);
    }

    /** Returns a quiet-to-lively one-shot interval in client ticks. */
    public static int intervalTicks(float intensity) {
        return Math.max(12, Math.round(62.0f - unit(intensity) * 46.0f));
    }

    private static float unit(float value) {
        float finite = Float.isFinite(value) ? value : 0.0f;
        return Math.max(0.0f, Math.min(1.0f, finite));
    }
}
