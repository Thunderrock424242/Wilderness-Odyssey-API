package com.thunder.wildernessodysseyapi.cinematic.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;
import static org.lwjgl.glfw.GLFW.GLFW_REPEAT;

class CinematicSkipInputTest {
    private final CinematicSkipInput input = new CinematicSkipInput();

    @Test
    void requestsSkipAfterTwoSeparatePressesWithinHalfASecond() {
        assertFalse(input.onSpaceKey(GLFW_PRESS, 1_000_000_000L));
        assertFalse(input.onSpaceKey(GLFW_RELEASE, 1_050_000_000L));
        assertTrue(input.onSpaceKey(GLFW_PRESS, 1_500_000_000L));
    }

    @Test
    void holdingSpaceNeverCountsAsTwoTaps() {
        assertFalse(input.onSpaceKey(GLFW_PRESS, 1_000_000_000L));
        assertFalse(input.onSpaceKey(GLFW_REPEAT, 1_100_000_000L));
        assertFalse(input.onSpaceKey(GLFW_PRESS, 1_200_000_000L));
        assertFalse(input.onSpaceKey(GLFW_REPEAT, 1_300_000_000L));
        assertFalse(input.onSpaceKey(GLFW_RELEASE, 1_400_000_000L));
    }

    @Test
    void anExpiredTapStartsANewPair() {
        assertFalse(input.onSpaceKey(GLFW_PRESS, 1_000_000_000L));
        assertFalse(input.onSpaceKey(GLFW_RELEASE, 1_100_000_000L));
        assertFalse(input.onSpaceKey(GLFW_PRESS, 1_500_000_001L));
        assertFalse(input.onSpaceKey(GLFW_RELEASE, 1_600_000_000L));
        assertTrue(input.onSpaceKey(GLFW_PRESS, 1_700_000_000L));
    }

    @Test
    void emitsOnlyOneRequestUntilTheSequenceResets() {
        assertFalse(input.onSpaceKey(GLFW_PRESS, 1_000_000_000L));
        assertFalse(input.onSpaceKey(GLFW_RELEASE, 1_050_000_000L));
        assertTrue(input.onSpaceKey(GLFW_PRESS, 1_100_000_000L));
        assertFalse(input.onSpaceKey(GLFW_RELEASE, 1_150_000_000L));
        assertFalse(input.onSpaceKey(GLFW_PRESS, 1_200_000_000L));
        assertFalse(input.onSpaceKey(GLFW_RELEASE, 1_250_000_000L));
        assertFalse(input.onSpaceKey(GLFW_PRESS, 1_300_000_000L));

        input.reset();
        assertFalse(input.onSpaceKey(GLFW_PRESS, 2_000_000_000L));
        assertFalse(input.onSpaceKey(GLFW_RELEASE, 2_050_000_000L));
        assertTrue(input.onSpaceKey(GLFW_PRESS, 2_100_000_000L));
    }

    @Test
    void aNewSequenceCannotReuseThePreviousFirstTap() {
        assertFalse(input.onSpaceKey(GLFW_PRESS, 1_000_000_000L));
        assertFalse(input.onSpaceKey(GLFW_RELEASE, 1_050_000_000L));
        input.reset();
        assertFalse(input.onSpaceKey(GLFW_PRESS, 1_100_000_000L));
        assertFalse(input.onSpaceKey(GLFW_RELEASE, 1_150_000_000L));
        assertTrue(input.onSpaceKey(GLFW_PRESS, 1_200_000_000L));
    }

    @Test
    void openingAScreenDisarmsThePendingTap() {
        assertFalse(input.onSpaceKey(GLFW_PRESS, 1_000_000_000L));
        input.disarm();
        assertFalse(input.onSpaceKey(GLFW_RELEASE, 1_050_000_000L));
        assertFalse(input.onSpaceKey(GLFW_PRESS, 1_100_000_000L));
        assertFalse(input.onSpaceKey(GLFW_RELEASE, 1_150_000_000L));
        assertTrue(input.onSpaceKey(GLFW_PRESS, 1_200_000_000L));
    }

    @Test
    void disarmingCannotSendAnotherRequestWhileWaitingForTheServer() {
        assertFalse(input.onSpaceKey(GLFW_PRESS, 1_000_000_000L));
        assertFalse(input.onSpaceKey(GLFW_RELEASE, 1_050_000_000L));
        assertTrue(input.onSpaceKey(GLFW_PRESS, 1_100_000_000L));
        input.disarm();
        assertFalse(input.onSpaceKey(GLFW_PRESS, 1_200_000_000L));
        assertFalse(input.onSpaceKey(GLFW_RELEASE, 1_250_000_000L));
        assertFalse(input.onSpaceKey(GLFW_PRESS, 1_300_000_000L));
    }
}
