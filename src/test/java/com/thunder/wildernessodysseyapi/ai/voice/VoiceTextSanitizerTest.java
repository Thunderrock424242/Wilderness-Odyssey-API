package com.thunder.wildernessodysseyapi.ai.voice;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies that visual archive syntax never leaks into generated speech. */
class VoiceTextSanitizerTest {
    @Test
    void removesArchiveFormattingWhilePreservingNaturalWords() {
        String display = "[ARCHIVE CORRUPTED] >>>\n**Cryogenic records** indicate 47 occupants...";

        String speech = VoiceTextSanitizer.sanitize(display);

        assertEquals("Cryogenic records indicate 47 occupants...", speech);
        assertFalse(speech.contains("ARCHIVE"));
        assertFalse(speech.contains(">>>"));
    }

    @Test
    void voiceLineFallsBackToSanitizedDisplayAndBoundsMetadata() {
        VoiceLine line = new VoiceLine("Aether", "[SIGNAL LOST] Still here.", "", null, 4.0F);

        assertEquals("Still here.", line.speechText());
        assertEquals(VoiceEmotion.NORMAL, line.emotion());
        assertEquals(0.35F, line.radioEffect());
    }

    @Test
    void explicitSpeechIsSanitizedAndNonFiniteEffectsAreRejected() {
        VoiceLine line = new VoiceLine(
                "Requiem",
                "[ARCHIVE CORRUPTED] Record 12",
                "[RECORD 12] **Forty-seven** occupants.",
                VoiceEmotion.MYSTERIOUS,
                Float.NaN
        );

        assertEquals("Forty-seven occupants.", line.speechText());
        assertEquals(0.0F, line.radioEffect());
    }

    @Test
    void oversizedSpeechIsBounded() {
        String speech = VoiceTextSanitizer.sanitize("x".repeat(3_000));

        assertEquals(VoiceTextSanitizer.MAX_SPEECH_CHARACTERS, speech.length());
        assertTrue(speech.chars().allMatch(character -> character == 'x'));
    }
}
