package com.thunder.wildernessodysseyapi.watersystem.water.wave;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void environmentalEnergyScalesAmplitudeWithoutChangingDispersion() {
        GerstnerWaveProfile profile = singleWave(12.0f, 8.0f);
        float originalFrequency = profile.angularFrequency[0];
        WaveSurfaceSample neutral = profile.sampleAt(2.0f, -3.0f, 1.4f);
        WaveSurfaceSample energetic = profile.sampleAt(
                2.0f,
                -3.0f,
                1.4f,
                1,
                new WaveSpectrumState(2.0f, 2.0f, 1.0f, 0.0f, 0.0f)
        );

        assertAll(
                () -> assertEquals(neutral.height() * 2.0f, energetic.height(), 1.0e-6f),
                () -> assertEquals(neutral.velocityY() * 2.0f, energetic.velocityY(), 1.0e-6f),
                () -> assertEquals(originalFrequency, profile.angularFrequency[0], 0.0f)
        );
    }

    @Test
    void rotatesAuthoredRiverSpreadOntoLocalCurrent() {
        GerstnerWaveProfile authored = new GerstnerWaveProfile.Builder(1, 4.0f)
                .wave(0, 0.12f, 5.5f, 0.8f, 0.6f, 0.3f, 0.42f)
                .build();
        GerstnerWaveProfile explicitlyRotated = new GerstnerWaveProfile.Builder(1, 4.0f)
                .wave(0, 0.12f, 5.5f, -0.6f, 0.8f, 0.3f, 0.42f)
                .build();

        WaveSurfaceSample flowAligned = authored.sampleAt(
                18.25,
                -7.75,
                3.2,
                1,
                WaveSpectrumState.NEUTRAL,
                0.0f,
                2.0f
        );
        WaveSurfaceSample expected = explicitlyRotated.sampleAt(
                18.25,
                -7.75,
                3.2,
                1,
                WaveSpectrumState.NEUTRAL
        );

        assertAll(
                () -> assertEquals(expected.displacementX(), flowAligned.displacementX(), 1.0e-6f),
                () -> assertEquals(expected.height(), flowAligned.height(), 1.0e-6f),
                () -> assertEquals(expected.displacementZ(), flowAligned.displacementZ(), 1.0e-6f),
                () -> assertEquals(expected.normalX(), flowAligned.normalX(), 1.0e-6f),
                () -> assertEquals(expected.normalZ(), flowAligned.normalZ(), 1.0e-6f)
        );
    }

    @Test
    void doublePhasePreservesSubBlockAndTickMotionNearWorldBorder() {
        GerstnerWaveProfile profile = GerstnerWaveProfile.OCEAN;
        double coordinate = 30_000_000.0;
        double longRunningTime = 5_000_000.0;

        WaveSurfaceSample firstPosition = profile.sampleAt(
                coordinate + 0.25, coordinate - 0.25, longRunningTime, 4, WaveSpectrumState.NEUTRAL);
        WaveSurfaceSample secondPosition = profile.sampleAt(
                coordinate + 0.75, coordinate - 0.25, longRunningTime, 4, WaveSpectrumState.NEUTRAL);
        WaveSurfaceSample nextTick = profile.sampleAt(
                coordinate + 0.25, coordinate - 0.25, longRunningTime + 0.05, 4,
                WaveSpectrumState.NEUTRAL);

        assertEquals((float) (coordinate + 0.25), (float) (coordinate + 0.75));
        assertTrue(Math.abs(firstPosition.height() - secondPosition.height()) > 1.0e-5f);
        assertTrue(Math.abs(firstPosition.height() - nextTick.height()) > 1.0e-5f);
    }

    private static GerstnerWaveProfile singleWave(float depth, float wavelength) {
        return new GerstnerWaveProfile.Builder(1, depth)
                .wave(0, 0.2f, wavelength, 0.8f, 0.6f, 0.55f, 0.37f)
                .build();
    }
}
