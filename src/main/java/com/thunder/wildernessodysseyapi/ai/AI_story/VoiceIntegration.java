package com.thunder.wildernessodysseyapi.ai.AI_story;

import java.io.File;
import java.io.IOException;
/**
 * Offline-friendly voice stub that can be swapped for an actual TTS/ASR pipeline.
 */
public class VoiceIntegration {

    // Check the Operating System before trying to run external AI scripts
    private static final boolean IS_WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

    public static boolean tryPlayVoiceLine(String textToSpeech) {
        try {
            ProcessBuilder builder;

            if (IS_WINDOWS) {
                // Windows execution
                builder = new ProcessBuilder("cmd.exe", "/c", "ai_voice_script.bat", textToSpeech);
            } else {
                // Linux/Mac execution (Requires a .sh script or calling python directly)
                File linuxScript = new File("ai_voice_script.sh");
                if (!linuxScript.exists()) {
                    com.thunder.wildernessodysseyapi.core.ModConstants.LOGGER.warn("AI Voice disabled: Missing Linux script ai_voice_script.sh");
                    return false; // Fail gracefully, don't crash!
                }
                builder = new ProcessBuilder("bash", "ai_voice_script.sh", textToSpeech);
            }

            Process process = builder.start();
            return true;

        } catch (IOException e) {
            com.thunder.wildernessodysseyapi.core.ModConstants.LOGGER.error("Failed to initialize AI Voice: " + e.getMessage());
            return false;
        }
    }
}