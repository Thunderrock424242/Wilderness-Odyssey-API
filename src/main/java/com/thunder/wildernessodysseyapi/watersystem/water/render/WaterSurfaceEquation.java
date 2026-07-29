package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.thunder.wildernessodysseyapi.watersystem.water.wave.GerstnerWaveProfile;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaveSpectrumState;

/**
 * Defines the shared CPU-side surface-height equation used by rendering and immersion.
 *
 * <p>Base fill, body-weighted tide, Gerstner displacement, and local transient
 * displacement are composed in one place so camera transitions can sample the
 * same physical surface that the GPU presents.</p>
 */
public final class WaterSurfaceEquation {

    /** Small complementary scale retained only for dormant legacy CPU-displaced meshes. */
    public static final float LEGACY_GPU_COMPLEMENT_SCALE = 0.12f;

    private WaterSurfaceEquation() {
    }

    /** Evaluates one bounded visible surface height. */
    public static float surfaceHeight(
            float baseSurfaceY,
            float worldX,
            float worldZ,
            float timeSeconds,
            GerstnerWaveProfile profile,
            WaveSpectrumState spectrum,
            int waveLimit,
            float waveBlend,
            float tideOffset,
            float oceanWeight,
            float transientHeight
    ) {
        GerstnerWaveProfile safeProfile = profile == null ? GerstnerWaveProfile.POND : profile;
        WaveSpectrumState safeSpectrum = spectrum == null ? WaveSpectrumState.NEUTRAL : spectrum;
        float waves = safeProfile.sampleAt(
                worldX,
                worldZ,
                finiteOrZero(timeSeconds),
                Math.max(0, waveLimit),
                safeSpectrum
        ).height();
        return finiteOrZero(baseSurfaceY)
                + waves * clamp(finiteOrZero(waveBlend), 0.0f, 1.0f)
                + finiteOrZero(tideOffset) * clamp(finiteOrZero(oceanWeight), 0.0f, 1.0f)
                + clamp(finiteOrZero(transientHeight), -0.25f, 0.25f);
    }

    /**
     * Mirrors the built-in vertex shader's stable snapshot displacement.
     *
     * <p>Camera immersion uses this path so surface entry/exit agrees with the
     * GPU even while synchronized sea state rotates and energizes the profile.</p>
     */
    public static float snapshotSurfaceHeight(
            float baseSurfaceY,
            float worldX,
            float worldZ,
            float timeSeconds,
            WaveSpectrumState oceanSpectrum,
            int oceanWaveLimit,
            int riverWaveLimit,
            int pondWaveLimit,
            float oceanWeight,
            float riverWeight,
            float lakeWeight,
            float tideOffset,
            float transientHeight
    ) {
        WaveSpectrumState safeOceanSpectrum = oceanSpectrum == null
                ? WaveSpectrumState.NEUTRAL
                : oceanSpectrum;
        float boundedOceanWeight = Math.max(0.0f, finiteOrZero(oceanWeight));
        float boundedRiverWeight = Math.max(0.0f, finiteOrZero(riverWeight));
        float boundedLakeWeight = Math.max(0.0f, finiteOrZero(lakeWeight));
        float weightSum = boundedOceanWeight + boundedRiverWeight + boundedLakeWeight;
        float weightNormalizer = Math.max(weightSum, 1.0e-4f);
        boundedOceanWeight /= weightNormalizer;
        boundedRiverWeight /= weightNormalizer;
        boundedLakeWeight /= weightNormalizer;

        float boundedTime = finiteOrZero(timeSeconds);
        float waveHeight = GerstnerWaveProfile.OCEAN.sampleAt(
                worldX, worldZ, boundedTime, Math.max(0, oceanWaveLimit), safeOceanSpectrum
        ).height() * boundedOceanWeight;
        waveHeight += GerstnerWaveProfile.RIVER.sampleAt(
                worldX, worldZ, boundedTime, Math.max(0, riverWaveLimit), WaveSpectrumState.NEUTRAL
        ).height() * boundedRiverWeight;
        waveHeight += GerstnerWaveProfile.POND.sampleAt(
                worldX, worldZ, boundedTime, Math.max(0, pondWaveLimit), WaveSpectrumState.NEUTRAL
        ).height() * boundedLakeWeight;
        return finiteOrZero(baseSurfaceY)
                + waveHeight
                + finiteOrZero(tideOffset) * boundedOceanWeight
                + clamp(finiteOrZero(transientHeight), -0.25f, 0.25f);
    }

    /**
     * Legacy scalar-sea overload retained for API callers outside the active snapshot renderer.
     *
     * @deprecated pass the synchronized {@link WaveSpectrumState} and explicit
     * wave limits so CPU sampling matches the active GPU spectrum.
     */
    @Deprecated(forRemoval = false)
    public static float snapshotSurfaceHeight(
            float baseSurfaceY,
            float worldX,
            float worldZ,
            float timeSeconds,
            float seaState,
            float windDirectionX,
            float windDirectionZ,
            float oceanWeight,
            float riverWeight,
            float lakeWeight,
            float tideOffset
    ) {
        float windLength = (float) Math.sqrt(windDirectionX * windDirectionX + windDirectionZ * windDirectionZ);
        float windX = windLength > 1.0e-6f ? windDirectionX / windLength : 1.0f;
        float windZ = windLength > 1.0e-6f ? windDirectionZ / windLength : 0.0f;
        float boundedSea = clamp(seaState, 0.0f, 1.0f);
        WaveSpectrumState approximateSpectrum = new WaveSpectrumState(
                0.92f + boundedSea * 0.16f,
                0.86f + boundedSea * 0.48f,
                windX,
                windZ,
                boundedSea * 0.62f
        );
        return snapshotSurfaceHeight(
                baseSurfaceY,
                worldX,
                worldZ,
                timeSeconds,
                approximateSpectrum,
                GerstnerWaveProfile.OCEAN.waveCount,
                GerstnerWaveProfile.RIVER.waveCount,
                GerstnerWaveProfile.POND.waveCount,
                oceanWeight,
                riverWeight,
                lakeWeight,
                tideOffset,
                0.0f
        );
    }

    private static float finiteOrZero(float value) {
        return Float.isFinite(value) ? value : 0.0f;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

}
