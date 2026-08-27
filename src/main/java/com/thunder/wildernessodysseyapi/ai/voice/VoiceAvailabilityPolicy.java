package com.thunder.wildernessodysseyapi.ai.voice;

/** Pure policy shared by tests and the client boundary for optional private-world voice. */
public final class VoiceAvailabilityPolicy {
    private VoiceAvailabilityPolicy() {
    }

    /** Voice requires both explicit opt-in and an unpublished integrated world. */
    public static boolean permits(boolean enabled, boolean privateSingleplayer) {
        return enabled && privateSingleplayer;
    }
}
