package com.thunder.wildernessodysseyapi.cinematic.client;

import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;

/** Tracks deliberate Space taps independently of Minecraft movement input. */
final class CinematicSkipInput {
    private static final long DOUBLE_TAP_WINDOW_NANOS = 500_000_000L;

    private long firstTapNanos;
    private boolean hasFirstTap;
    private boolean spaceDown;
    private boolean requestSent;

    boolean onSpaceKey(int action, long nowNanos) {
        if (action == GLFW_RELEASE) {
            spaceDown = false;
            return false;
        }
        if (action != GLFW_PRESS || spaceDown || requestSent) {
            return false;
        }
        spaceDown = true;
        long elapsed = nowNanos - firstTapNanos;
        if (hasFirstTap && elapsed >= 0L && elapsed <= DOUBLE_TAP_WINDOW_NANOS) {
            hasFirstTap = false;
            requestSent = true;
            return true;
        }
        firstTapNanos = nowNanos;
        hasFirstTap = true;
        return false;
    }

    /** Drops partial input when a screen or focus change takes keyboard ownership. */
    void disarm() {
        hasFirstTap = false;
        spaceDown = false;
    }

    /** Starts fresh for the next playback, including its one-request limit. */
    void reset() {
        disarm();
        requestSent = false;
    }
}
