package com.thunder.wildernessodysseyapi.watersystem.water.wave;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DepthWaveResponseTest {
    @Test void existingGerstnerProfileUsesDepthForHeightAndOrbitalVelocity() {
        var deep = GerstnerWaveProfile.OCEAN.sampleAt(11.0, 17.0, 4.0, 4,
                WaveSpectrumState.NEUTRAL, 0, 0, 24);
        var shallow = GerstnerWaveProfile.OCEAN.sampleAt(11.0, 17.0, 4.0, 4,
                WaveSpectrumState.NEUTRAL, 0, 0, 3);
        assertEquals(deep.height() * 1.2f, shallow.height(), 0.00001f);
        assertEquals(deep.velocityY() * 1.2f, shallow.velocityY(), 0.00001f);
        assertEquals(1.0f, shallow.normalX() * shallow.normalX() + shallow.normalY() * shallow.normalY()
                + shallow.normalZ() * shallow.normalZ(), 0.00001f);
    }

    @Test void shoalsThenDissipatesWithoutAmplifyingDeepOcean() {
        assertEquals(1, DepthWaveResponse.amplitudeScale(24), 0.00001f);
        assertEquals(1.2f, DepthWaveResponse.amplitudeScale(3), 0.00001f);
        assertTrue(DepthWaveResponse.amplitudeScale(0.5f) < DepthWaveResponse.amplitudeScale(3));
        assertEquals(1, DepthWaveResponse.amplitudeScale(Float.NaN), 0.00001f);
    }
}
