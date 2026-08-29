package com.thunder.wildernessodysseyapi.cinematic.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CinematicCueClockTest {
    @Test
    void delayedCueCatchesAudioAndSubtitleUpToAuthoritativeGameTime() {
        assertEquals(3, CinematicCueClock.elapsedTicks(1_003L, 1_000L, 100));
        assertEquals(150_000L, CinematicCueClock.audioOffsetMicroseconds(3));
        assertEquals(0.6F, CinematicCueClock.subtitleAlpha(1_003.0D, 1_000L, 100), 0.0001F);
    }

    @Test
    void subtitleFadesAgainstCueTimeAndExpiresWithoutClientTickDrift() {
        assertEquals(0.0F, CinematicCueClock.subtitleAlpha(999.0D, 1_000L, 100));
        assertEquals(1.0F, CinematicCueClock.subtitleAlpha(1_005.0D, 1_000L, 100));
        assertEquals(0.5F, CinematicCueClock.subtitleAlpha(1_095.0D, 1_000L, 100));
        assertEquals(0.0F, CinematicCueClock.subtitleAlpha(1_100.0D, 1_000L, 100));
    }

    @Test
    void elapsedTimeIsBoundedToTheAuthoredCue() {
        assertEquals(0, CinematicCueClock.elapsedTicks(900L, 1_000L, 100));
        assertEquals(100, CinematicCueClock.elapsedTicks(1_250L, 1_000L, 100));
    }
}
