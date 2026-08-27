package com.thunder.wildernessodysseyapi.ai.voice;

import java.util.Locale;

/** Bounded delivery moods understood by both the local voice service and Minecraft client. */
public enum VoiceEmotion {
    NORMAL,
    CALM,
    CONCERNED,
    URGENT,
    DAMAGED,
    WEAK,
    MYSTERIOUS;

    /** Resolves untrusted model or service text without creating new runtime modes. */
    public static VoiceEmotion fromModelValue(String value) {
        if (value == null || value.isBlank()) {
            return NORMAL;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return NORMAL;
        }
    }

    /** Stable lowercase wire value used by the Python service. */
    public String wireName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
