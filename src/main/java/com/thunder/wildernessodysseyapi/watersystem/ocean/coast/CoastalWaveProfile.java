package com.thunder.wildernessodysseyapi.watersystem.ocean.coast;

/**
 * Immutable tuning for one physical shoreline character.
 *
 * <p>Profiles change the presentation of the shared deterministic coastal
 * model. They do not own weather, tides, water volume, or multiplayer state.
 * Those inputs remain sourced from the existing Wilderness water authorities.</p>
 */
public record CoastalWaveProfile(
        ShoreType shoreType,
        float waveHeightMultiplier,
        float waveFrequencyMultiplier,
        float breakerDistanceBlocks,
        float breakerStrength,
        float runUpDistanceBlocks,
        float retreatSpeed,
        float foamAmount,
        float crashSoundVolume,
        float crashSoundRadiusBlocks,
        float turbulence,
        int shorelineWetnessDurationTicks
) {

    public static final CoastalWaveProfile TEMPERATE = new CoastalWaveProfile(
            ShoreType.TEMPERATE, 1.00f, 1.00f, 5.5f, 0.92f,
            5.0f, 1.00f, 0.82f, 0.78f, 24.0f, 0.72f, 180
    );
    public static final CoastalWaveProfile DUNE = new CoastalWaveProfile(
            ShoreType.DUNE, 0.92f, 0.92f, 7.0f, 0.76f, 7.5f,
            0.82f, 0.68f, 0.62f, 22.0f, 0.48f, 240
    );
    public static final CoastalWaveProfile ROCKY = new CoastalWaveProfile(
            ShoreType.ROCKY, 1.10f, 1.08f, 3.6f, 1.18f, 2.8f,
            1.28f, 1.00f, 1.00f, 32.0f, 1.20f, 120
    );
    public static final CoastalWaveProfile COLD = new CoastalWaveProfile(
            ShoreType.COLD, 0.90f, 0.86f, 5.0f, 0.88f, 4.2f,
            0.88f, 0.72f, 0.68f, 22.0f, 0.62f, 260
    );
    public static final CoastalWaveProfile GLACIAL = new CoastalWaveProfile(
            ShoreType.GLACIAL, 0.76f, 0.72f, 3.8f, 0.82f, 2.6f,
            0.72f, 0.58f, 0.72f, 28.0f, 0.56f, 320
    );
    public static final CoastalWaveProfile TROPICAL = new CoastalWaveProfile(
            ShoreType.TROPICAL, 1.04f, 1.04f, 6.5f, 0.98f, 6.4f,
            0.94f, 0.94f, 0.82f, 26.0f, 0.82f, 220
    );

    public CoastalWaveProfile {
        shoreType = shoreType == null ? ShoreType.TEMPERATE : shoreType;
        waveHeightMultiplier = finiteClamp(waveHeightMultiplier, 0.1f, 3.0f, 1.0f);
        waveFrequencyMultiplier = finiteClamp(waveFrequencyMultiplier, 0.25f, 3.0f, 1.0f);
        breakerDistanceBlocks = finiteClamp(breakerDistanceBlocks, 1.0f, 24.0f, 5.0f);
        breakerStrength = finiteClamp(breakerStrength, 0.1f, 2.0f, 1.0f);
        runUpDistanceBlocks = finiteClamp(runUpDistanceBlocks, 0.5f, 16.0f, 4.0f);
        retreatSpeed = finiteClamp(retreatSpeed, 0.25f, 3.0f, 1.0f);
        foamAmount = finiteClamp(foamAmount, 0.0f, 2.0f, 0.8f);
        crashSoundVolume = finiteClamp(crashSoundVolume, 0.0f, 2.0f, 0.75f);
        crashSoundRadiusBlocks = finiteClamp(crashSoundRadiusBlocks, 4.0f, 64.0f, 24.0f);
        turbulence = finiteClamp(turbulence, 0.0f, 2.0f, 0.7f);
        shorelineWetnessDurationTicks = Math.max(20, Math.min(1_200, shorelineWetnessDurationTicks));
    }

    /** Returns the canonical profile for a classified shore. */
    public static CoastalWaveProfile forType(ShoreType shoreType) {
        return switch (shoreType == null ? ShoreType.TEMPERATE : shoreType) {
            case TEMPERATE -> TEMPERATE;
            case DUNE -> DUNE;
            case ROCKY -> ROCKY;
            case COLD -> COLD;
            case GLACIAL -> GLACIAL;
            case TROPICAL -> TROPICAL;
        };
    }

    private static float finiteClamp(float value, float minimum, float maximum, float fallback) {
        return Float.isFinite(value) ? Math.max(minimum, Math.min(maximum, value)) : fallback;
    }

    /** Visual/terrain character selected while the cached shoreline is discovered. */
    public enum ShoreType {
        TEMPERATE,
        DUNE,
        ROCKY,
        COLD,
        GLACIAL,
        TROPICAL
    }
}
