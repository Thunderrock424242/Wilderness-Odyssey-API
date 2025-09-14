package com.thunder.wildernessodysseyapi.ai;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple client that reads lore from {@code ai_config.yaml} and
 * echoes player messages. This acts as a placeholder for a future
 * networked AI service.
 */
public class AIClient {

    private final List<String> story = new ArrayList<>();
    private final MemoryStore memoryStore = new MemoryStore();

    public AIClient() {
        loadStory();
    }

    private void loadStory() {
        try (InputStream in = AIClient.class.getClassLoader().getResourceAsStream("ai_config.yaml")) {
            if (in == null) {
                return;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                boolean inStory = false;
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("story:")) {
                        inStory = true;
                        continue;
                    }
                    if (inStory) {
                        if (line.startsWith("-")) {
                            story.add(line.substring(1).trim().replace("\"", ""));
                        } else if (!line.isEmpty() && !line.startsWith("#")) {
                            inStory = false;
                        }
                    }
                }
            }
        } catch (IOException ignored) {
            // Config is optional; ignore parsing errors for now
        }
    }

    /**
     * Adds the message to memory and returns a canned reply that includes
     * the first line of the loaded story as context.
     *
     * @param player  player name
     * @param message player message
     * @return AI reply
     */
    public String sendMessage(String player, String message) {
        memoryStore.addMessage(player, message);
        String prefix = story.isEmpty() ? "" : story.get(0) + " ";
        return prefix + "You said: " + message;
    }
}

