package com.thunder.wildernessodysseyapi.ai.story;

import com.thunder.wildernessodysseyapi.ai.voice.VoiceEmotion;
import com.thunder.wildernessodysseyapi.ai.voice.VoiceLine;

/**
 * Common-side adapter that keeps display and spoken forms together without
 * loading any client audio classes on a dedicated server.
 *
 * <p>The separately started loopback voice service remains optional. This
 * adapter never launches scripts or external processes.</p>
 */
public class VoiceIntegration {

    public record VoiceResult(
            String speaker,
            String displayText,
            String speechText,
            VoiceEmotion emotion,
            float radioEffect
    ) {
        /** Compatibility accessor for existing text-only callers. */
        public String text() {
            return displayText;
        }

        public VoiceLine asVoiceLine() {
            return new VoiceLine(speaker, displayText, speechText, emotion, radioEffect);
        }
    }

    public VoiceResult wrap(String speaker, String reply) {
        return wrap(speaker, reply, "", VoiceEmotion.NORMAL, 0.0F);
    }

    public VoiceResult wrap(
            String speaker,
            String displayText,
            String speechText,
            VoiceEmotion emotion,
            float radioEffect
    ) {
        VoiceLine line = new VoiceLine(speaker, displayText, speechText, emotion, radioEffect);
        return new VoiceResult(
                line.speaker(),
                line.displayText(),
                line.speechText(),
                line.emotion(),
                line.radioEffect()
        );
    }
}
