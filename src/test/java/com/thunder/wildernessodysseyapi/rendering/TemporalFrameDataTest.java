package com.thunder.wildernessodysseyapi.rendering;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class TemporalFrameDataTest {

    @Test
    void nativeFrameDoesNotFabricateTemporalInputs() {
        TemporalFrameData frame = TemporalFrameData.unavailable(
                new TemporalFrameData.Resolution(1_920, 1_080),
                new TemporalFrameData.Resolution(1_920, 1_080),
                16_000_000L
        );

        assertFalse(frame.hasTemporalReconstructionInputs());
        assertFalse(frame.color().isPresent());
        assertFalse(frame.depth().isPresent());
        assertFalse(frame.motionVectors().isPresent());
    }
}
