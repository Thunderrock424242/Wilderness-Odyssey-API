package com.thunder.wildernessodysseyapi.AI.AI_story;

/**
 * Offline-friendly voice stub that can be swapped for an actual TTS/ASR pipeline.
 */
public class VoiceIntegration {

    public record VoiceResult(String text, String voiceLine, boolean voiceQueued, boolean speechInputAllowed) {
    }

    private final AISettings settings;

    public VoiceIntegration(AISettings settings) {
        this.settings = settings;
    }

    public VoiceResult wrap(String text) {
        return new VoiceResult(text, null, false, settings.isSpeechRecognition());
    }
}
