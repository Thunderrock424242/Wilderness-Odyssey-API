package com.thunder.wildernessodysseyapi.ai.story;

import com.thunder.wildernessodysseyapi.ai.voice.VoiceEmotion;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VoiceIntegrationTest {
    @Test
    void preservesDisplayTextWhileBuildingSafeSpeechMetadata() {
        VoiceIntegration integration = new VoiceIntegration();

        VoiceIntegration.VoiceResult result = integration.wrap(
                "Aether",
                "[ARCHIVE CORRUPTED] Forty-seven records remain.",
                "Forty-seven records remain.",
                VoiceEmotion.CONCERNED,
                0.18F
        );

        assertEquals("[ARCHIVE CORRUPTED] Forty-seven records remain.", result.text());
        assertEquals("Forty-seven records remain.", result.speechText());
        assertEquals(VoiceEmotion.CONCERNED, result.emotion());
        assertEquals(0.18F, result.radioEffect());
    }

    @Test
    void legacyTextOnlyRepliesUseSanitizedDisplayAsSpeech() {
        VoiceIntegration.VoiceResult result = new VoiceIntegration().wrap(
                "Aether",
                "[SIGNAL LOST] Still here."
        );

        assertEquals("[SIGNAL LOST] Still here.", result.displayText());
        assertEquals("Still here.", result.speechText());
    }
}
