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

    /** Small complementary GPU wave scale retained while legacy meshes still contain CPU displacement. */
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
        float boundedOceanWeight = Math.max(0.0f, finiteOrZero(oceanWeight));
        float boundedRiverWeight = Math.max(0.0f, finiteOrZero(riverWeight));
        float boundedLakeWeight = Math.max(0.0f, finiteOrZero(lakeWeight));
        float weightSum = boundedOceanWeight + boundedRiverWeight + boundedLakeWeight;
        float weightNormalizer = Math.max(weightSum, 1.0e-4f);
        boundedOceanWeight /= weightNormalizer;
        boundedRiverWeight /= weightNormalizer;
        boundedLakeWeight /= weightNormalizer;
        float waveHeight = sampleSnapshotProfile(
                GerstnerWaveProfile.OCEAN, worldX, worldZ, timeSeconds,
                boundedSea, windX, windZ) * boundedOceanWeight;
        waveHeight += sampleSnapshotProfile(
                GerstnerWaveProfile.RIVER, worldX, worldZ, timeSeconds,
                boundedSea, windX, windZ) * boundedRiverWeight;
        waveHeight += sampleSnapshotProfile(
                GerstnerWaveProfile.POND, worldX, worldZ, timeSeconds,
                boundedSea, windX, windZ) * boundedLakeWeight;
        return finiteOrZero(baseSurfaceY) + waveHeight
                + finiteOrZero(tideOffset) * boundedOceanWeight;
    }

    // Mirrors accumulateWave in gerstner_water.vsh for one authored profile.
    private static float sampleSnapshotProfile(
            GerstnerWaveProfile profile,
            float worldX,
            float worldZ,
            float timeSeconds,
            float boundedSea,
            float windX,
            float windZ
    ) {
        float waveHeight = 0.0f;
        for (int index = 0; index < Math.min(4, profile.waveCount); index++) {
            float componentBlend = profile.waveCount <= 1
                    ? 0.0f
                    : index / (float) (profile.waveCount - 1);
            float directionBlend = clamp(
                    boundedSea * (0.12f + componentBlend * 0.42f), 0.0f, 0.62f);
            float directionX = profile.dirX[index] * (1.0f - directionBlend) + windX * directionBlend;
            float directionZ = profile.dirZ[index] * (1.0f - directionBlend) + windZ * directionBlend;
            float directionLength = (float) Math.sqrt(directionX * directionX + directionZ * directionZ);
            if (directionLength > 1.0e-6f) {
                directionX /= directionLength;
                directionZ /= directionLength;
            } else {
                directionX = profile.dirX[index];
                directionZ = profile.dirZ[index];
            }
            float energy = mix(0.92f + boundedSea * 0.16f,
                    0.86f + boundedSea * 0.48f, componentBlend);
            float phase = profile.waveNumber[index] * (worldX * directionX + worldZ * directionZ)
                    - profile.angularFrequency[index] * finiteOrZero(timeSeconds) + profile.phaseOffset[index];
            waveHeight += profile.amplitude[index] * energy * (float) Math.sin(phase);
        }
        return waveHeight;
    }

    private static float finiteOrZero(float value) {
        return Float.isFinite(value) ? value : 0.0f;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float mix(float first, float second, float amount) {
        return first + (second - first) * amount;
    }
}
