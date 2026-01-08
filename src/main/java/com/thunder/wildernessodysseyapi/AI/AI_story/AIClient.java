package com.thunder.wildernessodysseyapi.AI.AI_story;

import com.thunder.wildernessodysseyapi.AI.AI_perf.MemoryStore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import net.minecraft.server.MinecraftServer;

/**
 * Offline story helper that mixes the configured lore with recent
 * player context to build lightweight, deterministic responses.
 * <p>
 * All data is loaded from resources at startup so no external
 * service or hosting is required.
 */
public class AIClient {

    private final List<String> story = new ArrayList<>();
    private final List<String> corruptedLore = new ArrayList<>();
    private final List<String> backgroundHistory = new ArrayList<>();
    private String corruptedPrefix = "";
    private final AISettings settings = new AISettings();
    private final VoiceIntegration voiceIntegration = new VoiceIntegration(settings);
    private final MemoryStore memoryStore = new MemoryStore();
    private final Map<String, StorySession> sessions = new HashMap<>();
    private LocalModelClient localModelClient;
    private String localSystemPrompt;

    public AIClient() {
        loadStory();
    }

    public String getWakeWord() {
        return settings.getWakeWord();
    }

    private void loadStory() {
        AIConfig config = AIConfigLoader.load();
        story.addAll(config.getStory());
        corruptedLore.addAll(config.getCorruptedData());
        backgroundHistory.addAll(config.getBackgroundHistory());
        if (config.getCorruptedPrefix() != null) {
            corruptedPrefix = config.getCorruptedPrefix();
        }
        applySettings(config);
        configureLocalModel(config.getLocalModel());
    }

    public synchronized void scanGameData(MinecraftServer server) {
        if (server == null) {
            return;
        }
        // Crafting assistance is intentionally disabled; leave this hook for future non-recipe scans.
    }

    private void applySettings(AIConfig config) {
        if (config == null) {
            return;
        }
        AIConfig.Settings configSettings = config.getSettings();
        if (configSettings.getVoiceEnabled() != null) {
            settings.setVoiceEnabled(configSettings.getVoiceEnabled());
        }
        if (configSettings.getSpeechRecognition() != null) {
            settings.setSpeechRecognition(configSettings.getSpeechRecognition());
        }
        if (configSettings.getWakeWord() != null) {
            settings.setWakeWord(configSettings.getWakeWord());
        }
        if (configSettings.getModel() != null) {
            settings.setModelName(configSettings.getModel());
        }

        AIConfig.Personality personality = config.getPersonality();
        if (personality.getName() != null) {
            settings.setPersonaName(personality.getName());
        }
        if (personality.getTone() != null) {
            settings.setPersonalityTone(personality.getTone());
        }
        if (personality.getEmpathy() != null) {
            settings.setEmpathyLevel(personality.getEmpathy());
        }
    }

    private void configureLocalModel(AIConfig.LocalModel localModel) {
        if (localModel == null || localModel.getEnabled() == null || !localModel.getEnabled()) {
            return;
        }
        String baseUrl = localModel.getBaseUrl();
        String modelName = localModel.getModel();
        if (baseUrl == null || baseUrl.isBlank() || modelName == null || modelName.isBlank()) {
            return;
        }
        int timeoutSeconds = localModel.getTimeoutSeconds() == null ? 15 : localModel.getTimeoutSeconds();
        localSystemPrompt = localModel.getSystemPrompt();
        localModelClient = new LocalModelClient(baseUrl.trim(), modelName.trim(), java.time.Duration.ofSeconds(timeoutSeconds));
    }

    private String formatSystemPrompt(String template) {
        if (template == null || template.isBlank()) {
            return "";
        }
        return template
                .replace("{persona}", settings.getPersonaName())
                .replace("{tone}", settings.getPersonalityTone())
                .replace("{empathy}", settings.getEmpathyLevel());
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
        memoryStore.addPlayerMessage(world, player, message);
        String context = memoryStore.getRecentContext(world, player);
        if (localModelClient != null) {
            String prompt = formatSystemPrompt(localSystemPrompt);
            var response = localModelClient.generateReply(prompt, message, context);
            if (response.isPresent()) {
                String reply = response.get();
                memoryStore.addAiMessage(world, player, settings.getPersonaName(), reply);
                return voiceIntegration.wrap(reply);
            }
        }
        StorySession session = sessions.computeIfAbsent(sessionKey(world, player),
                p -> new StorySession(world, settings.getWakeWord(), settings.getPersonaName(),
                        settings.getPersonalityTone(), settings.getEmpathyLevel(), corruptedLore,
                        backgroundHistory, corruptedPrefix));
        String reply = session.buildResponse(world, player, message, story,
                context);
        memoryStore.addAiMessage(world, player, settings.getPersonaName(), reply);
        return voiceIntegration.wrap(reply);
    }

    private String sessionKey(String world, String player) {
        String worldKey = world == null || world.isBlank() ? "default" : world.trim();
        return worldKey + "::" + player;
    }

    private static class StorySession {

        private final String world;
        private final String wakeWord;
        private final String personaName;
        private final String personalityTone;
        private final String empathyLevel;
        private final List<String> corruptedLore;
        private final List<String> backgroundHistory;
        private final String corruptedPrefix;
        private boolean activated = false;

        private int beat = 0;
        private final Random random = ThreadLocalRandom.current();

        StorySession(String world, String wakeWord, String personaName, String personalityTone, String empathyLevel,
                     List<String> corruptedLore, List<String> backgroundHistory, String corruptedPrefix) {
            this.world = world == null ? "" : world;
            this.wakeWord = wakeWord == null || wakeWord.isBlank() ? "atlas" : wakeWord.toLowerCase(Locale.ROOT);
            this.personaName = personaName == null || personaName.isBlank() ? "Atlas" : personaName;
            this.personalityTone = personalityTone == null ? "" : personalityTone;
            this.empathyLevel = empathyLevel == null ? "" : empathyLevel;
            this.corruptedLore = corruptedLore;
            this.backgroundHistory = backgroundHistory;
            this.corruptedPrefix = corruptedPrefix == null ? "" : corruptedPrefix;
        }

        String buildResponse(String world, String player, String message, List<String> story, String context) {
            String cleanMessage = message == null ? "" : message.trim();
            if (!activated) {
                activated = true;
                return warmWelcome(cleanMessage, player, world);
            }
            activated = activated || mentionsWakeWord(cleanMessage);
            String loreHook = selectLoreHook(story);

            StringBuilder reply = new StringBuilder();
            if (!loreHook.isEmpty()) {
                reply.append(loreHook).append(" ");
            }

            reply.append(buildDynamicResponse(cleanMessage, context, player));

            String background = backgroundBeat();
            if (!background.isEmpty()) {
                reply.append(" ").append(background);
            }

            String corrupted = corruptedWhisper();
            if (!corrupted.isEmpty()) {
                reply.append(" ").append(corrupted);
            }

            return reply.toString().trim();
        }

        private String warmWelcome(String cleanMessage, String player, String world) {
            StringBuilder welcome = new StringBuilder();
            welcome.append("Hey ").append(player).append(", I'm ").append(personaName).append(".");
            welcome.append(" I'm here to keep the mission on track in ").append(world == null || world.isBlank() ? "this world" : world).append(".");
            return welcome.toString();
        }

        private String selectLoreHook(List<String> story) {
            if (story.isEmpty()) {
                return "";
            }
            int index = Math.min(beat % story.size(), story.size() - 1);
            beat++;
            String base = story.get(index);
            if (!world.isBlank()) {
                return base;
            }
            return base;
        }

        private boolean mentionsWakeWord(String message) {
            if (message == null || message.isBlank()) {
                return false;
            }
            return message.toLowerCase(Locale.ROOT).contains(wakeWord);
        }

        private String buildDynamicResponse(String message, String context, String player) {
            StringBuilder builder = new StringBuilder();
            String mood = buildMoodLine();
            if (!mood.isBlank()) {
                builder.append(mood).append(" ");
            }
            String history = pickHistoryBeat();
            if (!history.isBlank()) {
                builder.append(history).append(" ");
            }
            String contextNote = summarizeContext(context);
            if (!contextNote.isBlank()) {
                builder.append("I remember you said: \"").append(contextNote).append("\". ");
            }
            builder.append("Tell me where you want to take this next, ").append(player).append(".");
            return builder.toString().trim();
        }

        private String buildMoodLine() {
            if (personalityTone.isBlank() && empathyLevel.isBlank()) {
                return "";
            }
            if (!personalityTone.isBlank() && !empathyLevel.isBlank()) {
                return "Staying " + personalityTone + " and " + empathyLevel + " for you.";
            }
            if (!personalityTone.isBlank()) {
                return "Keeping the tone " + personalityTone + ".";
            }
            return "Staying " + empathyLevel + " while we plan.";
        }

        private String pickHistoryBeat() {
            if (backgroundHistory.isEmpty() || random.nextDouble() > 0.6) {
                return "";
            }
            return backgroundHistory.get(random.nextInt(backgroundHistory.size()));
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
            int start = Math.max(0, size - 2);
            return String.join(" / ", lines.subList(start, size));
        }

        private String backgroundBeat() {
            if (backgroundHistory.isEmpty() || random.nextDouble() > 0.35) {
                return "";
            }
            return backgroundHistory.get(random.nextInt(backgroundHistory.size()));
        }

        private String corruptedWhisper() {
            if (corruptedLore.isEmpty() || random.nextDouble() > 0.35) {
                return "";
            }
            String fragment = corruptedLore.get(random.nextInt(corruptedLore.size()));
            if (corruptedPrefix.isBlank()) {
                return fragment;
            }
            return corruptedPrefix + " " + fragment;
        }

    }
}
