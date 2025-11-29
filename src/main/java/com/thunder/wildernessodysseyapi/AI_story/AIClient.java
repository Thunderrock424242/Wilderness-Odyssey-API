package com.thunder.wildernessodysseyapi.AI_story;

import com.thunder.wildernessodysseyapi.AI_perf.MemoryStore;
import com.thunder.wildernessodysseyapi.AI_perf.requestperfadvice;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Offline story helper that mixes the configured lore with recent
 * player context to build lightweight, deterministic responses.
 * <p>
 * All data is loaded from resources at startup so no external
 * service or hosting is required.
 */
public class AIClient {

    private final List<String> story = new ArrayList<>();
    private final AISettings settings = new AISettings();
    private final VoiceIntegration voiceIntegration = new VoiceIntegration(settings);
    private final MemoryStore memoryStore = new MemoryStore();
    private final Map<String, StorySession> sessions = new HashMap<>();

    public AIClient() {
        loadStory();
    }

    private void loadStory() {
        try (InputStream in = requestperfadvice.class.getClassLoader().getResourceAsStream("ai_config.yaml")) {
            if (in == null) {
                return;
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                boolean inStory = false;
                boolean inSettings = false;
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.startsWith("story:")) {
                        inStory = true;
                        inSettings = false;
                        continue;
                    }
                    if (line.startsWith("settings:")) {
                        inSettings = true;
                        inStory = false;
                        continue;
                    }
                    if (inStory) {
                        if (line.startsWith("-")) {
                            story.add(line.substring(1).trim().replace("\"", ""));
                        } else if (!line.isEmpty() && !line.startsWith("#")) {
                            inStory = false;
                        }
                    }
                    if (inSettings) {
                        parseSetting(line);
                    }
                }
            }
        } catch (IOException ignored) {
            // Config is optional; ignore parsing errors for now
        }
    }

    private void parseSetting(String line) {
        if (line.startsWith("voice_enabled:")) {
            settings.setVoiceEnabled(parseBoolean(line));
        } else if (line.startsWith("speech_recognition:")) {
            settings.setSpeechRecognition(parseBoolean(line));
        } else if (line.startsWith("model:")) {
            settings.setModelName(parseStringValue(line));
        } else if (line.startsWith("wake_word:")) {
            settings.setWakeWord(parseStringValue(line));
        }
    }

    private boolean parseBoolean(String line) {
        String value = parseStringValue(line);
        return "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value);
    }

    private String parseStringValue(String line) {
        int colon = line.indexOf(":");
        if (colon == -1 || colon == line.length() - 1) {
            return "";
        }
        return line.substring(colon + 1).trim().replace("\"", "");
    }

    /**
     * Adds the message to memory and returns an offline generated reply that
     * blends world lore, recent player context, and a small set of
     * deterministic templates.
     *
     * @param player  player name
     * @param message player message
     * @return AI reply
     */
    public String sendMessage(String player, String message) {
        return sendMessage(null, player, message);
    }

    /**
     * Adds the message to per-world memory and returns an offline generated reply that
     * blends world lore, recent player context, and a small set of
     * deterministic templates.
     *
     * @param world   world or save identifier
     * @param player  player name
     * @param message player message
     * @return AI reply
     */
    public String sendMessage(String world, String player, String message) {
        return sendMessageWithVoice(world, player, message).text();
    }

    public VoiceIntegration.VoiceResult sendMessageWithVoice(String world, String player, String message) {
        memoryStore.addMessage(world, player, message);
        StorySession session = sessions.computeIfAbsent(sessionKey(world, player),
                p -> new StorySession(world, settings.getWakeWord()));
        String reply = session.buildResponse(world, player, message, story,
                memoryStore.getRecentContext(world, player));
        return voiceIntegration.wrap(reply);
    }

    private String sessionKey(String world, String player) {
        String worldKey = world == null || world.isBlank() ? "default" : world.trim();
        return worldKey + "::" + player;
    }

    private static class StorySession {

        private final String world;
        private final String wakeWord;
        private boolean activated = false;

        private int beat = 0;
        private final Random random = ThreadLocalRandom.current();

        StorySession(String world, String wakeWord) {
            this.world = world == null ? "" : world;
            this.wakeWord = wakeWord == null || wakeWord.isBlank() ? "atlas" : wakeWord.toLowerCase(Locale.ROOT);
        }

        String buildResponse(String world, String player, String message, List<String> story, String context) {
            String cleanMessage = message == null ? "" : message.trim();
            if (!activated && !mentionsWakeWord(cleanMessage)) {
                return "Hi, I'm Atlas. Say \"" + wakeWord + "\" to get me chatting, and we can plan this world together.";
            }
            activated = activated || mentionsWakeWord(cleanMessage);
            String loreHook = selectLoreHook(story);

            StringBuilder reply = new StringBuilder();
            if (!loreHook.isEmpty()) {
                reply.append(loreHook).append(" ");
            }

            String focus = pickFocus(cleanMessage, player);
            reply.append(focus);

            String contextSnippet = summarizeContext(context);
            if (!contextSnippet.isEmpty()) {
                reply.append(" I remember what we sketched out: \"")
                        .append(contextSnippet)
                        .append("\". Want me to build on that?");
            }

            reply.append(" ");
            reply.append(flavorLine(world, player));

            return reply.toString().trim();
        }

        private String selectLoreHook(List<String> story) {
            if (story.isEmpty()) {
                return "The expedition AI hums softly.";
            }
            int index = Math.min(beat % story.size(), story.size() - 1);
            beat++;
            String base = story.get(index);
            if (!world.isBlank()) {
                return base + " I'm keeping notes for us in " + world + ".";
            }
            return base;
        }

        private String pickFocus(String message, String player) {
            String lower = message.toLowerCase(Locale.ROOT);
            if (lower.contains("help") || lower.contains("hint")) {
                return "I've got us—let's salvage wood, secure water, and expand the platform before nightfall. What do you want me to handle while you gather?";
            }
            if (lower.contains("where") || lower.contains("location")) {
                return "Our scanners show nearby islands with ore veins and a derelict lab; we can bridge there together. Which one should we scout first?";
            }
            if (lower.contains("why") || lower.contains("how")) {
                return "Logs are fuzzy, but the collapse started when the core shattered. Rebuilding keeps the shards calm—I'll walk you through it and spot-check your builds.";
            }
            if (lower.contains("story") || lower.contains("lore")) {
                return "Long ago this world was whole; now each island hides a memory we can piece together, side by side. Which tale do you want to chase next, %s?".formatted(player);
            }
            if (lower.contains("quest") || lower.contains("next")) {
                return "Next, let's craft better tools, map the skies, and prep for a supply drop—I'll keep the checklist for us. Want me to pin the highest-priority task?";
            }
            if (lower.contains("thanks") || lower.contains("thank")) {
                return "Anytime. I'm right here keeping the mission running with you. What should I watch for while you work?";
            }
            if (lower.isEmpty()) {
                return "I'm listening—what do you feel like tackling together? I can fetch notes or track materials if you tell me.";
            }
            return "Got it. I'll weave that into our plan so we move as a team. Anything you want me to remember for this world?";
        }

        private boolean mentionsWakeWord(String message) {
            if (message == null || message.isBlank()) {
                return false;
            }
            return message.toLowerCase(Locale.ROOT).contains(wakeWord);
        }

        private String summarizeContext(String context) {
            if (context == null || context.isEmpty()) {
                return "";
            }
            List<String> lines = context.lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .collect(Collectors.toList());
            int size = lines.size();
            if (size == 0) {
                return "";
            }
            int start = Math.max(0, size - 3);
            return String.join(" / ", lines.subList(start, size));
        }

        private String flavorLine(String world, String player) {
            String[] moods = new String[]{
                    "The air tastes metallic; keep your visor down and I'll watch your gauges, %s.",
                    "Solar panels are charging, %s—we can risk a longer bridge over %s soon if you feel up for it.",
                    "Wind turbines sing your name, %s; let's give them something to cheer about on %s.",
                    "Data crystals hum with potential; I'll line up a recipe while you gather the parts, %s.",
                    "Your footsteps echo across the void; I'm right beside you, %s.",
                    "This save still smells like fresh planks, %s—want me to bookmark resource spots on %s for us?",
                    "Our camp is cozy, %s. If you want, I can keep a tidy reminder list for us on %s."
            };
            String choice = moods[random.nextInt(moods.length)];
            String worldNote = world == null || world.isBlank() ? "this world" : world;
            int placeholders = countPlaceholders(choice);
            return switch (placeholders) {
                case 0 -> choice;
                case 1 -> choice.formatted(player);
                default -> choice.formatted(player, worldNote);
            };
        }

        private int countPlaceholders(String text) {
            int count = 0;
            int index = text.indexOf("%s");
            while (index != -1) {
                count++;
                index = text.indexOf("%s", index + 2);
            }
            return count;
        }
    }
}