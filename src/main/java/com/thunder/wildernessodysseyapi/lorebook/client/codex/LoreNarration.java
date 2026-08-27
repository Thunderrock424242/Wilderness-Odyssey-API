package com.thunder.wildernessodysseyapi.lorebook.client.codex;

import com.thunder.wildernessodysseyapi.ai.voice.VoiceEmotion;
import com.thunder.wildernessodysseyapi.ai.voice.VoiceLine;

import java.util.List;

/** Builds deterministic Requiem narration from the recovered pages currently visible in the Codex. */
final class LoreNarration {
    private LoreNarration() {
    }

    static VoiceLine fromSpread(String title, String author, List<String> pageTexts) {
        String safeTitle = title == null || title.isBlank() ? "Recovered Journal" : title.trim();
        String safeAuthor = author == null || author.isBlank() ? "Unknown" : author.trim();
        StringBuilder speech = new StringBuilder(safeTitle)
                .append(", by ").append(safeAuthor).append(".");
        if (pageTexts != null) {
            for (String pageText : pageTexts) {
                if (pageText != null && !pageText.isBlank()) {
                    speech.append(' ').append(pageText.trim());
                }
            }
        }
        return VoiceLine.authored(
                "Requiem",
                safeTitle,
                speech.toString(),
                VoiceEmotion.MYSTERIOUS,
                0.04F
        );
    }
}
