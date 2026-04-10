package com.thunder.wildernessodysseyapi.ai.AI_story;

import java.io.File;
import java.io.IOException;
/**
 * Offline-friendly voice stub that can be swapped for an actual TTS/ASR pipeline.
 */
public class VoiceIntegration {

    private final AISettings settings;
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

    // Restored constructor that AIClient is looking for
    public VoiceIntegration(AISettings settings) {
        this.settings = settings;
    }

    // Restored VoiceResult object
    public record VoiceResult(String speaker, String text) {}

    // Restored wrap method
    public VoiceResult wrap(String speaker, String reply) {
        if (reply != null && !reply.isBlank() && settings.isVoiceEnabled()) {
            tryPlayVoiceLine(reply);
        }
        return new VoiceResult(speaker, reply);
    }

    // The Linux crash-prevention logic
    public static boolean tryPlayVoiceLine(String textToSpeech) {
        try {
            ProcessBuilder builder;

            if (IS_WINDOWS) {
                // Windows execution
                builder = new ProcessBuilder("cmd.exe", "/c", "ai_voice_script.bat", textToSpeech);
            } else {
                // Linux/Mac execution
                File linuxScript = new File("ai_voice_script.sh");
                if (!linuxScript.exists()) {
                    com.thunder.wildernessodysseyapi.core.ModConstants.LOGGER.warn("AI Voice disabled: Missing Linux script ai_voice_script.sh");
                    return false;
                }
                builder = new ProcessBuilder("bash", "ai_voice_script.sh", textToSpeech);
            }

            builder.start();
            return true;

        } catch (IOException e) {
            com.thunder.wildernessodysseyapi.core.ModConstants.LOGGER.error("Failed to initialize AI Voice: " + e.getMessage());
            return false;
        }
    }
}