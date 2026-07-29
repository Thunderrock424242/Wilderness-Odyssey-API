package com.thunder.wildernessodysseyapi.watersystem.water.render;

import com.thunder.wildernessodysseyapi.watersystem.water.wave.GerstnerWaveProfile;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaveSpectrumState;
import com.thunder.wildernessodysseyapi.watersystem.water.wave.WaveSurfaceSample;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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
            float directionBlend = spectrum.directionBlend()
                    * (0.35f + componentBlend * 0.65f);
            float directionX = profile.dirX[index] * (1.0f - directionBlend)
                    + spectrum.windDirectionX() * directionBlend;
            float directionZ = profile.dirZ[index] * (1.0f - directionBlend)
                    + spectrum.windDirectionZ() * directionBlend;
            float inverseDirectionLength = 1.0f
                    / (float) Math.sqrt(directionX * directionX + directionZ * directionZ);
            directionX *= inverseDirectionLength;
            directionZ *= inverseDirectionLength;

            float energy = spectrum.swellScale()
                    + (spectrum.chopScale() - spectrum.swellScale()) * componentBlend;
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
