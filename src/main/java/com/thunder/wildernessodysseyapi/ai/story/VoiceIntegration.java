package com.thunder.wildernessodysseyapi.ai.story;

/**
 * Offline-friendly voice stub. Voice playback is intentionally disabled in the
 * CurseForge build so the mod never launches external scripts or processes.
 */
public class VoiceIntegration {

    public VoiceIntegration(AISettings settings) {
    }

    public record VoiceResult(String speaker, String text) {}

    public VoiceResult wrap(String speaker, String reply) {
        return new VoiceResult(speaker, reply);
    }

    public static boolean tryPlayVoiceLine(String textToSpeech) {
        return false;
    }
}
