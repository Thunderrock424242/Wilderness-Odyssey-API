package com.thunder.wildernessodysseyapi.lorebook;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Verifies the validation boundary shared by Codex journal networking and persistence. */
class CodexJournalTextTest {

    @Test
    void normalizesLineEndingsAndRejectsFormattingControls() {
        String sanitized = CodexJournalText.sanitize("First\r\nSecond\rThird\u0000\u00A7c");

        assertEquals("First\nSecond\nThirdc", sanitized);
    }

    @Test
    void clampsWithoutLeavingAnUnpairedSurrogate() {
        String rawText = "a".repeat(CodexJournalText.MAX_LENGTH - 1) + "\uD83C\uDF32";

        String sanitized = CodexJournalText.sanitize(rawText);

        assertEquals(CodexJournalText.MAX_LENGTH - 1, sanitized.length());
        assertFalse(Character.isHighSurrogate(sanitized.charAt(sanitized.length() - 1)));
    }
}
