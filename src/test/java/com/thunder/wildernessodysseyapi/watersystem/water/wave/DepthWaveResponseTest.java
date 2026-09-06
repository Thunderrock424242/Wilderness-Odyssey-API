package com.thunder.wildernessodysseyapi.watersystem.water.wave;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DepthWaveResponseTest {
    @Test void existingGerstnerProfileUsesDepthForHeightAndOrbitalVelocity() {
        var deep = GerstnerWaveProfile.OCEAN.sampleAt(11.0, 17.0, 4.0, 4,
                WaveSpectrumState.NEUTRAL, 0, 0, 24);
        var shallow = GerstnerWaveProfile.OCEAN.sampleAt(11.0, 17.0, 4.0, 4,
                WaveSpectrumState.NEUTRAL, 0, 0, 3);
        assertEquals(DepthWaveResponse.shapeCrest(deep.height() * 1.2f, 3, 1), shallow.height(), 0.00001f);
        assertEquals(deep.velocityY() * 1.2f * DepthWaveResponse.crestDerivative(deep.height() * 1.2f, 3, 1),
                shallow.velocityY(), 0.00001f);
        assertEquals(1.0f, shallow.normalX() * shallow.normalX() + shallow.normalY() * shallow.normalY()
                + shallow.normalZ() * shallow.normalZ(), 0.00001f);
    }

    @Test void shoalsThenDissipatesWithoutAmplifyingDeepOcean() {
        assertEquals(1, DepthWaveResponse.amplitudeScale(24), 0.00001f);
        assertEquals(1.2f, DepthWaveResponse.amplitudeScale(3), 0.00001f);
        assertTrue(DepthWaveResponse.amplitudeScale(0.5f) < DepthWaveResponse.amplitudeScale(3));
        assertEquals(1, DepthWaveResponse.amplitudeScale(Float.NaN), 0.00001f);
    }

    @Test void coastalShapingLeavesDeepAndInlandWaterUnchanged() {
        assertEquals(0.5f, DepthWaveResponse.shapeCrest(0.5f, 24, 1), 0.00001f);
        assertEquals(0.5f, DepthWaveResponse.shapeCrest(0.5f, 3, 0), 0.00001f);
        assertEquals(0.0f, DepthWaveResponse.shapeCrest(0, 3, 1), 0.00001f);
        assertTrue(DepthWaveResponse.shapeCrest(0.5f, 3, 1) > 0.5f);
    }

    @Test void coastalVelocityMatchesActualHeightMotion() {
        double time = 4.0;
        float step = 0.001f;
        var profile = GerstnerWaveProfile.OCEAN;
        var before = profile.sampleAt(11, 17, time - step, 4, WaveSpectrumState.NEUTRAL, 0, 0, 3);
        var after = profile.sampleAt(11, 17, time + step, 4, WaveSpectrumState.NEUTRAL, 0, 0, 3);
        var center = profile.sampleAt(11, 17, time, 4, WaveSpectrumState.NEUTRAL, 0, 0, 3);
        assertEquals((after.height() - before.height()) / (2 * step), center.velocityY(), 0.002f);
    }
}
