package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.thunder.wildernessodysseyapi.watersystem.water.wave.GerstnerWaveProfile;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaveSpectrumState;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaveSurfaceSample;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Numerically mirrors the GLSL Gerstner component equation against the CPU model.
 *
 * <p>The shader contract test guards source tokens; this test guards the actual
 * displacement and analytic-tangent arithmetic those tokens represent.</p>
 */
class WaterSurfaceGerstnerMirrorTest {

    @Test
    void shaderMirrorMatchesCpuHorizontalVerticalAndNormalSample() {
        GerstnerWaveProfile profile = GerstnerWaveProfile.OCEAN;
        WaveSpectrumState spectrum = new WaveSpectrumState(
                1.42f, 0.76f, -0.35f, 0.94f, 0.61f);
        float x = 42.75f;
        float z = -19.125f;
        float time = 13.4f;
        int limit = 3;

        WaveSurfaceSample cpu = profile.sampleAt(x, z, time, limit, spectrum);
        MirrorSample shader = mirrorShader(profile, x, z, time, limit, spectrum);

        assertAll(
                () -> assertEquals(cpu.displacementX(), shader.displacementX, 1.0e-5f),
                () -> assertEquals(cpu.height(), shader.height, 1.0e-5f),
                () -> assertEquals(cpu.displacementZ(), shader.displacementZ, 1.0e-5f),
                () -> assertEquals(cpu.normalX(), shader.normalX, 1.0e-5f),
                () -> assertEquals(cpu.normalY(), shader.normalY, 1.0e-5f),
                () -> assertEquals(cpu.normalZ(), shader.normalZ, 1.0e-5f),
                () -> assertNotEquals(0.0f, shader.displacementX),
                () -> assertNotEquals(0.0f, shader.displacementZ)
        );
    }

    @Test
    void splitChunkPhasePreservesSubBlockDetailWithoutChunkSeamAtWorldBorder() {
        float origin = 30_000_000.0f;
        float coefficientX = GerstnerWaveProfile.OCEAN.waveNumber[0]
                * GerstnerWaveProfile.OCEAN.dirX[0];
        float coefficientZ = GerstnerWaveProfile.OCEAN.waveNumber[0]
                * GerstnerWaveProfile.OCEAN.dirZ[0];

        float edgeFromWest = stableLinearPhase(16.0f, 7.25f, origin, origin,
                coefficientX, coefficientZ);
        float edgeFromEast = stableLinearPhase(0.0f, 7.25f, origin + 16.0f, origin,
                coefficientX, coefficientZ);
        float halfBlock = stableLinearPhase(0.5f, 7.25f, origin, origin,
                coefficientX, coefficientZ);
        float wholeBlock = stableLinearPhase(0.0f, 7.25f, origin, origin,
                coefficientX, coefficientZ);

        assertAll(
                () -> assertEquals(Math.sin(edgeFromWest), Math.sin(edgeFromEast), 1.0e-5),
                () -> assertEquals(Math.cos(edgeFromWest), Math.cos(edgeFromEast), 1.0e-5),
                () -> assertTrue(Math.abs(Math.sin(halfBlock) - Math.sin(wholeBlock)) > 1.0e-4)
        );
    }

    private static MirrorSample mirrorShader(
            GerstnerWaveProfile profile,
            float x,
            float z,
            float time,
            int limit,
            WaveSpectrumState spectrum
    ) {
        float displacementX = 0.0f;
        float height = 0.0f;
        float displacementZ = 0.0f;
        float tangentXX = 1.0f;
        float tangentXY = 0.0f;
        float tangentXZ = 0.0f;
        float tangentZX = 0.0f;
        float tangentZY = 0.0f;
        float tangentZZ = 1.0f;

        for (int index = 0; index < Math.min(profile.waveCount, limit); index++) {
            float componentBlend = profile.waveCount <= 1
                    ? 0.0f
                    : index / (float) (profile.waveCount - 1);
            float directionX = profile.dirX[index];
            float directionZ = profile.dirZ[index];

            float energy = spectrum.swellScale()
                    + (spectrum.chopScale() - spectrum.swellScale()) * componentBlend;
            float windAlignment = Math.max(
                    0.0f,
                    directionX * spectrum.windDirectionX()
                            + directionZ * spectrum.windDirectionZ()
            );
            float alignedEnergy = 0.55f + windAlignment * 0.90f;
            energy *= 1.0f
                    + spectrum.directionBlend() * (alignedEnergy - 1.0f);
            float amplitude = profile.amplitude[index] * energy;
            float horizontalScale = profile.steepness[index] * amplitude;
            float phase = profile.waveNumber[index] * (x * directionX + z * directionZ)
                    - profile.angularFrequency[index] * time
                    + profile.phaseOffset[index];
            float sine = (float) Math.sin(phase);
            float cosine = (float) Math.cos(phase);
            displacementX += horizontalScale * directionX * cosine;
            height += amplitude * sine;
            displacementZ += horizontalScale * directionZ * cosine;

            float horizontalDerivative = horizontalScale * profile.waveNumber[index] * sine;
            float verticalDerivative = amplitude * profile.waveNumber[index] * cosine;
            tangentXX -= horizontalDerivative * directionX * directionX;
            tangentXY += verticalDerivative * directionX;
            tangentXZ -= horizontalDerivative * directionX * directionZ;
            tangentZX -= horizontalDerivative * directionX * directionZ;
            tangentZY += verticalDerivative * directionZ;
            tangentZZ -= horizontalDerivative * directionZ * directionZ;
        }

        float normalX = tangentZY * tangentXZ - tangentZZ * tangentXY;
        float normalY = tangentZZ * tangentXX - tangentZX * tangentXZ;
        float normalZ = tangentZX * tangentXY - tangentZY * tangentXX;
        float inverseNormalLength = 1.0f
                / (float) Math.sqrt(normalX * normalX + normalY * normalY + normalZ * normalZ);
        return new MirrorSample(
                displacementX,
                height,
                displacementZ,
                normalX * inverseNormalLength,
                normalY * inverseNormalLength,
                normalZ * inverseNormalLength
        );
    }

    private static float stableLinearPhase(
            float localX,
            float localZ,
            float originX,
            float originZ,
            float coefficientX,
            float coefficientZ
    ) {
        float localChunkX = (float) Math.floor(localX / 16.0f);
        float localChunkZ = (float) Math.floor(localZ / 16.0f);
        float canonicalLocalX = localX - localChunkX * 16.0f;
        float canonicalLocalZ = localZ - localChunkZ * 16.0f;
        float chunkX = originX / 16.0f + localChunkX;
        float chunkZ = originZ / 16.0f + localChunkZ;
        float coarseX = (float) Math.floor(chunkX / 1024.0f);
        float coarseZ = (float) Math.floor(chunkZ / 1024.0f);
        float fineX = chunkX - coarseX * 1024.0f;
        float fineZ = chunkZ - coarseZ * 1024.0f;
        float coarseStepX = glslMod(coefficientX * 16_384.0f, (float) (Math.PI * 2.0));
        float coarseStepZ = glslMod(coefficientZ * 16_384.0f, (float) (Math.PI * 2.0));
        float fineCoordinateX = fineX * 16.0f + canonicalLocalX;
        float fineCoordinateZ = fineZ * 16.0f + canonicalLocalZ;
        float axisPhaseX = glslMod(coarseX * coarseStepX + fineCoordinateX * coefficientX,
                (float) (Math.PI * 2.0));
        float axisPhaseZ = glslMod(coarseZ * coarseStepZ + fineCoordinateZ * coefficientZ,
                (float) (Math.PI * 2.0));
        return glslMod(axisPhaseX + axisPhaseZ,
                (float) (Math.PI * 2.0));
    }

    private static float glslMod(float value, float divisor) {
        return value - divisor * (float) Math.floor(value / divisor);
    }

    private record MirrorSample(
            float displacementX,
            float height,
            float displacementZ,
            float normalX,
            float normalY,
            float normalZ
    ) {
    }
}
