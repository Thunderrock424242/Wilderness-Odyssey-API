package com.thunder.wildernessodysseyapi.ai.voice;

/**
 * One display/spoken response shared by Aether chat, authored cinematics, and lore playback.
 *
 * <p>Display text remains the player-facing authority. Speech text may be a
 * natural rendering of the same facts, but cannot introduce additional lore.</p>
 */
public record VoiceLine(
        String speaker,
        String displayText,
        String speechText,
        VoiceEmotion emotion,
        float radioEffect
) {
    public static final int MAX_SPEAKER_CHARACTERS = 64;
    public static final int MAX_DISPLAY_CHARACTERS = 2_000;

    public VoiceLine {
        speaker = bounded(speaker, MAX_SPEAKER_CHARACTERS, "Aether");
        displayText = bounded(displayText, MAX_DISPLAY_CHARACTERS, "");
        String spoken = bounded(speechText, VoiceTextSanitizer.MAX_SPEECH_CHARACTERS, "");
        speechText = VoiceTextSanitizer.sanitize(spoken.isBlank() ? displayText : spoken);
        emotion = emotion == null ? VoiceEmotion.NORMAL : emotion;
        radioEffect = Float.isFinite(radioEffect)
                ? Math.max(0.0F, Math.min(0.35F, radioEffect))
                : 0.0F;
    }

    /** Creates an authored line without invoking or modifying the LLM. */
    public static VoiceLine authored(
            String speaker,
            String displayText,
            String speechText,
            VoiceEmotion emotion,
            float radioEffect
    ) {
        return new VoiceLine(speaker, displayText, speechText, emotion, radioEffect);
    }

    private static String bounded(String value, int limit, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String cleaned = value.replace("\u0000", "").trim();
        if (cleaned.length() <= limit) {
            return cleaned;
        }
        int end = limit;
        if (Character.isHighSurrogate(cleaned.charAt(end - 1))) {
            end--;
        }
        return cleaned.substring(0, end).stripTrailing();
    }
}
