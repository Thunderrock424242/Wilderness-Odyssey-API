package com.thunder.wildernessodysseyapi.watersystem.ocean.coast;

/** Stateless whitewater envelopes using the existing wave clock and cached shore geometry. */
public final class CoastalFoamModel {
    private CoastalFoamModel() { }

    /** Keeps broken-wave foam through wash and retreat, with no persistent world allocation. */
    public static float trail(CoastalWaveModel.Sample wave, float distanceFromShore) {
        if (wave == null || !Float.isFinite(distanceFromShore) || distanceFromShore < 0.0f
                || distanceFromShore > wave.breakerDistanceBlocks() + 1.0f) return 0.0f;
        float envelope = switch (wave.stage()) {
            case INCOMING, SHOALING -> 0.0f;
            case BREAKING -> wave.stagePhase();
            case RUN_UP -> 1.0f - wave.stagePhase() * 0.25f;
            case RETREAT -> 0.75f * (1.0f - wave.stagePhase());
        };
        if (wave.stage() == CoastalWaveModel.Stage.BREAKING
                && distanceFromShore < wave.crestDistanceFromShoreBlocks()) return 0.0f;
        return Math.max(0.0f, Math.min(1.0f, envelope * (0.48f + wave.energy() * 0.52f)));
    }

    /** Concentrates wash foam at its moving front while retaining a thinner patterned interior. */
    public static float wash(float distance, float reach, int x, int z, float phase) {
        float front = Math.max(0.0f, 1.0f - Math.abs(reach - distance) / 1.25f);
        double pattern = Math.sin(x * 1.73 + z * 2.31 - phase * Math.PI * 2.0);
        return Math.min(1.0f, 0.25f + front * 0.60f + (float) (pattern * 0.5 + 0.5) * 0.15f);
    }
}
