package com.thunder.wildernessodysseyapi.lorebook.client.codex;

import com.thunder.wildernessodysseyapi.ai.voice.VoiceEmotion;
import com.thunder.wildernessodysseyapi.ai.voice.VoiceLine;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LoreNarrationTest {
    @Test
    void readsTheVisibleSpreadAsAuthoredRequiemLore() {
        VoiceLine line = LoreNarration.fromSpread(
                "Cryogenic Ledger",
                "Dr. Vale",
                List.of("Forty-seven chambers were sealed.", "The final manifest is damaged.")
        );

        assertEquals("Requiem", line.speaker());
        assertEquals("Cryogenic Ledger", line.displayText());
        assertEquals(
                "Cryogenic Ledger, by Dr. Vale. Forty-seven chambers were sealed. The final manifest is damaged.",
                line.speechText()
        );
        assertEquals(VoiceEmotion.MYSTERIOUS, line.emotion());
        assertEquals(0.04F, line.radioEffect());
    }
}
