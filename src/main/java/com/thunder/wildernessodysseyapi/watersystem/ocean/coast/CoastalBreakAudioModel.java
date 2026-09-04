package com.thunder.wildernessodysseyapi.watersystem.ocean.coast;

/** Bounded, deterministic audio mix for a visible breaker, independent of spray. */
public final class CoastalBreakAudioModel {

    private CoastalBreakAudioModel() {
    }

    /** Sound follows the raised breaking crest even when little spray is produced. */
    public static boolean isAudibleBreak(CoastalWaveModel.Sample wave) {
        return wave.stage() == CoastalWaveModel.Stage.BREAKING
                && wave.stagePhase() >= 0.25f && wave.stagePhase() <= 0.72f
                && wave.breakerLift() > 0.02f;
    }

    /** Shared event strength for nearby-break selection and diagnostics. */
    public static float impactStrength(CoastalWaveModel.Sample wave) {
        return clamp(0.30f + wave.energy() * 0.50f + wave.breakerLift() * 0.15f, 1.0f);
    }

    /** Respects both profile and user mute; gain never exceeds two per crash. */
    public static Mix mix(
            CoastalWaveProfile profile,
            CoastalWaveModel.Sample wave,
            float userVolume,
            long segmentId
    ) {
        long seed = segmentId ^ wave.cycleIndex() * 0x9E3779B97F4A7C15L;
        float variation = 0.94f + Math.floorMod(seed, 13L) / 100.0f;
        float impact = clamp(profile.crashSoundVolume() * clamp(userVolume, 2.0f)
                * (0.78f + wave.energy() * 0.82f) * variation, 2.0f);
        float wash = impact * (0.22f + wave.energy() * 0.12f);
        float pitch = 0.82f + Math.floorMod(seed >>> 8, 13L) / 100.0f;
        return new Mix(impact, wash, pitch);
    }

    private static float clamp(float value, float maximum) {
        return Float.isFinite(value) ? Math.max(0.0f, Math.min(maximum, value)) : 0.0f;
    }

    /** One crash plus a quieter wash body, played together at the same breaker. */
    public record Mix(float impactVolume, float washVolume, float pitch) {
    }
}
