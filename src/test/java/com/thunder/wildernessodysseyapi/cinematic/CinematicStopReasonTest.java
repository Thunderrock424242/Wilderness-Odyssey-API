package com.thunder.wildernessodysseyapi.cinematic;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CinematicStopReasonTest {
    @Test
    void intentionalSkipsAndNormalCompletionFinishStoryEnrollment() {
        assertTrue(CinematicStopReason.COMPLETE.completesSequence());
        assertTrue(CinematicStopReason.SKIPPED.completesSequence());
    }

    @Test
    void interruptionsRemainEligibleForReplay() {
        assertFalse(CinematicStopReason.MANUAL_CANCEL.completesSequence());
        assertFalse(CinematicStopReason.PLAYER_DIED.completesSequence());
        assertFalse(CinematicStopReason.PLAYER_DISCONNECTED.completesSequence());
        assertFalse(CinematicStopReason.DIMENSION_CHANGED.completesSequence());
        assertFalse(CinematicStopReason.SERVER_STOPPING.completesSequence());
        assertFalse(CinematicStopReason.INVALID_STATE.completesSequence());
        assertFalse(CinematicStopReason.ERROR.completesSequence());
    }
}
