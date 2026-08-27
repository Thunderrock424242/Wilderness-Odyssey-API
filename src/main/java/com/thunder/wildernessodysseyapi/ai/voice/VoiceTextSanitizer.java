package com.thunder.wildernessodysseyapi.ai.voice;

/** Converts display-oriented Aether text into bounded, natural TTS input. */
public final class VoiceTextSanitizer {
    public static final int MAX_SPEECH_CHARACTERS = 2_000;

    private VoiceTextSanitizer() {
    }

    /**
     * Removes archive labels, formatting syntax, control characters, and signal
     * markers that should remain visible but should never be spoken literally.
     */
    public static String sanitize(String displayText) {
        if (displayText == null || displayText.isBlank()) {
            return "";
        }
        String cleaned = displayText
                .replace("\u0000", "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("(?s)```.*?```", " ")
                .replaceAll("(?i)\\[(?:archive|signal|system|warning|data|record)[^]]*]", " ")
                .replaceAll("(?m)^\\s*(?:>{2,}|<{2,}|[-=]{3,})\\s*$", " ")
                .replaceAll("(?m)^\\s*#{1,6}\\s*", "")
                .replaceAll("[>*_~`]", " ")
                .replaceAll("\\p{Cntrl}", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.length() <= MAX_SPEECH_CHARACTERS) {
            return cleaned;
        }
        int end = MAX_SPEECH_CHARACTERS;
        if (Character.isHighSurrogate(cleaned.charAt(end - 1))) {
            end--;
        }
        return cleaned.substring(0, end).stripTrailing();
    }
}
