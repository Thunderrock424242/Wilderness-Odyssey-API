package com.thunder.wildernessodysseyapi.watersystem.water.wave;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GerstnerWaveProfileTest {

    @Test
    void derivesAngularFrequencyFromGravityAndDepth() {
        float depth = 3.5f;
        float wavelength = 12.0f;
        GerstnerWaveProfile profile = singleWave(depth, wavelength);

        float waveNumber = (float) (Math.PI * 2.0 / wavelength);
        float expected = (float) Math.sqrt(
                GerstnerWaveProfile.GRAVITY * waveNumber * Math.tanh(waveNumber * depth)
        );

        assertEquals(expected, profile.angularFrequency[0], 1.0e-6f);
    }

    @Test
    void repeatsAfterOneWavePeriod() {
        GerstnerWaveProfile profile = singleWave(20.0f, 9.0f);
        float period = (float) (Math.PI * 2.0 / profile.angularFrequency[0]);

        WaveSurfaceSample first = profile.sampleAt(4.25f, -7.5f, 2.35f);
        WaveSurfaceSample repeated = profile.sampleAt(4.25f, -7.5f, 2.35f + period);

        assertAll(
                () -> assertEquals(first.displacementX(), repeated.displacementX(), 1.0e-5f),
                () -> assertEquals(first.height(), repeated.height(), 1.0e-5f),
                () -> assertEquals(first.displacementZ(), repeated.displacementZ(), 1.0e-5f),
                () -> assertEquals(first.velocityX(), repeated.velocityX(), 1.0e-5f),
                () -> assertEquals(first.velocityY(), repeated.velocityY(), 1.0e-5f),
                () -> assertEquals(first.velocityZ(), repeated.velocityZ(), 1.0e-5f)
        );
    }

    @Test
    void producesUnitLengthAnalyticNormal() {
        WaveSurfaceSample sample = GerstnerWaveProfile.OCEAN.sampleAt(31.5f, -18.75f, 7.2f);
        float length = (float) Math.sqrt(
                sample.normalX() * sample.normalX()
                        + sample.normalY() * sample.normalY()
                        + sample.normalZ() * sample.normalZ()
        );

        assertEquals(1.0f, length, 1.0e-5f);
    }

    @Test
    void analyticVelocityMatchesDisplacementDerivative() {
        GerstnerWaveProfile profile = singleWave(8.0f, 6.5f);
        float time = 1.7f;
        float delta = 1.0e-3f;

        WaveSurfaceSample previous = profile.sampleAt(2.0f, 3.0f, time - delta);
        WaveSurfaceSample current = profile.sampleAt(2.0f, 3.0f, time);
        WaveSurfaceSample next = profile.sampleAt(2.0f, 3.0f, time + delta);
        float inverseWindow = 1.0f / (2.0f * delta);

        assertAll(
                () -> assertEquals(
                        (next.displacementX() - previous.displacementX()) * inverseWindow,
                        current.velocityX(),
                        2.0e-3f
                ),
                () -> assertEquals(
                        (next.height() - previous.height()) * inverseWindow,
                        current.velocityY(),
                        2.0e-3f
                ),
                () -> assertEquals(
                        (next.displacementZ() - previous.displacementZ()) * inverseWindow,
                        current.velocityZ(),
                        2.0e-3f
                )
        );
    }

    @Test
    void zeroWaveLimitReturnsFlatSurface() {
        WaveSurfaceSample sample = GerstnerWaveProfile.OCEAN.sampleAt(10.0f, 20.0f, 3.0f, 0);
        assertEquals(WaveSurfaceSample.flat(), sample);
    }

    private static GerstnerWaveProfile singleWave(float depth, float wavelength) {
        return new GerstnerWaveProfile.Builder(1, depth)
                .wave(0, 0.2f, wavelength, 0.8f, 0.6f, 0.55f, 0.37f)
                .build();
    }
}
