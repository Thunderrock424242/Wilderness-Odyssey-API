package com.thunder.wildernessodysseyapi.cinematic.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CinematicCameraShakeTest {
    @Test
    void smoothFalloffEndsAtZero() {
        assertEquals(0.0F, CinematicCameraShake.sample(
                12.0F, 0.5F, 1.0F, CinematicCameraShake.Falloff.SMOOTH
        ));
    }

    @Test
    void shakeNeverExceedsItsBoundedIntensity() {
        float sample = CinematicCameraShake.sample(
                7.25F, 0.5F, 0.0F, CinematicCameraShake.Falloff.NONE
        );
        assertTrue(Math.abs(sample) <= 0.5F);
    }
}
