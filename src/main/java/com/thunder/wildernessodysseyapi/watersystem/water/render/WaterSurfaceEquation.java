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
        return snapshotSurfaceHeight(
                baseSurfaceY,
                worldX,
                worldZ,
                timeSeconds,
                oceanSpectrum,
                oceanWaveLimit,
                riverWaveLimit,
                pondWaveLimit,
                oceanWeight,
                riverWeight,
                lakeWeight,
                0.0f,
                0.0f,
                1.0f,
                tideOffset,
                transientHeight
        );
    }

    /**
     * Mirrors the active snapshot vertex shader, including current-relative
     * river direction and loaded-surface continuity taper.
     *
     * <p>World position and time remain double precision until profile phase
     * evaluation. The flow inputs are authoritative synchronized snapshot data;
     * callers must not substitute unsynchronized client prediction.</p>
     */
    public static float snapshotSurfaceHeight(
            float baseSurfaceY,
            double worldX,
            double worldZ,
            double timeSeconds,
            WaveSpectrumState oceanSpectrum,
            int oceanWaveLimit,
            int riverWaveLimit,
            int pondWaveLimit,
            float oceanWeight,
            float riverWeight,
            float lakeWeight,
            float currentX,
            float currentZ,
            float surfaceContinuity,
            float tideOffset,
            float transientHeight
    ) {
        return snapshotSurfaceHeight(
                baseSurfaceY,
                worldX,
                worldZ,
                timeSeconds,
                oceanSpectrum,
                WaveSpectrumState.NEUTRAL,
                oceanWaveLimit,
                riverWaveLimit,
                pondWaveLimit,
                oceanWeight,
                riverWeight,
                lakeWeight,
                currentX,
                currentZ,
                surfaceContinuity,
                tideOffset,
                transientHeight
        );
    }

    /**
     * Mirrors the active snapshot surface with an explicit enclosed-water
     * spectrum. Older callers retain neutral lake energy through the overload
     * above, while immersion can follow the shader's depth-bounded wind proxy.
     */
    public static float snapshotSurfaceHeight(
            float baseSurfaceY,
            double worldX,
            double worldZ,
            double timeSeconds,
            WaveSpectrumState oceanSpectrum,
            WaveSpectrumState enclosedWaterSpectrum,
            int oceanWaveLimit,
            int riverWaveLimit,
            int pondWaveLimit,
            float oceanWeight,
            float riverWeight,
            float lakeWeight,
            float currentX,
            float currentZ,
            float surfaceContinuity,
            float tideOffset,
            float transientHeight
    ) {
        return snapshotSurfaceHeight(baseSurfaceY, worldX, worldZ, timeSeconds,
                oceanSpectrum, enclosedWaterSpectrum, oceanWaveLimit, riverWaveLimit,
                pondWaveLimit, oceanWeight, riverWeight, lakeWeight, currentX, currentZ,
                surfaceContinuity, tideOffset, transientHeight, 24.0f);
    }

    /** Mirrors the active shader's cached-depth shoaling envelope. */
    public static float snapshotSurfaceHeight(
            float baseSurfaceY, double worldX, double worldZ, double timeSeconds,
            WaveSpectrumState oceanSpectrum, WaveSpectrumState enclosedWaterSpectrum,
            int oceanWaveLimit, int riverWaveLimit, int pondWaveLimit,
            float oceanWeight, float riverWeight, float lakeWeight,
            float currentX, float currentZ, float surfaceContinuity,
            float tideOffset, float transientHeight, float waterDepth
    ) {
        WaveSpectrumState safeOceanSpectrum = oceanSpectrum == null
                ? WaveSpectrumState.NEUTRAL
                : oceanSpectrum;
        WaveSpectrumState safeEnclosedSpectrum = enclosedWaterSpectrum == null
                ? WaveSpectrumState.NEUTRAL
                : enclosedWaterSpectrum;
        float boundedOceanWeight = Math.max(0.0f, finiteOrZero(oceanWeight));
        float boundedRiverWeight = Math.max(0.0f, finiteOrZero(riverWeight));
        float boundedLakeWeight = Math.max(0.0f, finiteOrZero(lakeWeight));
        float weightSum = boundedOceanWeight + boundedRiverWeight + boundedLakeWeight;
        float weightNormalizer = Math.max(weightSum, 1.0e-4f);
        boundedOceanWeight /= weightNormalizer;
        boundedRiverWeight /= weightNormalizer;
        boundedLakeWeight /= weightNormalizer;

        float waveHeight = GerstnerWaveProfile.OCEAN.sampleAt(
                worldX, worldZ, finiteOrZero(timeSeconds), Math.max(0, oceanWaveLimit), safeOceanSpectrum
        ).height() * boundedOceanWeight;
        waveHeight += GerstnerWaveProfile.RIVER.sampleAt(
                worldX,
                worldZ,
                finiteOrZero(timeSeconds),
                Math.max(0, riverWaveLimit),
                WaveSpectrumState.NEUTRAL,
                currentX,
                currentZ
        ).height() * boundedRiverWeight;
        waveHeight += GerstnerWaveProfile.LAKE.sampleAt(
                worldX,
                worldZ,
                finiteOrZero(timeSeconds),
                Math.max(0, pondWaveLimit),
                safeEnclosedSpectrum
        ).height() * boundedLakeWeight;
        float continuity = surfaceContinuityFactor(surfaceContinuity);
        waveHeight *= com.thunder.wildernessodysseyapi.watersystem.water.wave.DepthWaveResponse.amplitudeScale(waterDepth);
        return finiteOrZero(baseSurfaceY)
                + (waveHeight + clamp(finiteOrZero(transientHeight), -0.25f, 0.25f)) * continuity
                + finiteOrZero(tideOffset) * boundedOceanWeight * continuity;
    }

    /** Converts encoded mesh continuity into the exact GPU vertical-wave taper. */
    public static float surfaceContinuityFactor(float surfaceContinuity) {
        return smoothStep(0.18f, 0.92f, finiteOrZero(surfaceContinuity));
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
                GerstnerWaveProfile.LAKE.waveCount,
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

    private static double finiteOrZero(double value) {
        return Double.isFinite(value) ? value : 0.0;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static float smoothStep(float edge0, float edge1, float value) {
        float t = clamp((value - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

}
