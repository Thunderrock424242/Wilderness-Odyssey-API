package com.thunder.wildernessodysseyapi.ai.story;

import com.thunder.wildernessodysseyapi.ai.perf.MemoryStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;

/**
 * AI client that answers from bundled deterministic prompts and local learned facts.
 */
public class AIClient {

    private final List<String> story = new ArrayList<>();
    private final List<String> corruptedLore = new ArrayList<>();
    private final List<String> backgroundHistory = new ArrayList<>();
    private final AISettings settings = new AISettings();
    private final VoiceIntegration voiceIntegration = new VoiceIntegration(settings);
    private final MemoryStore memoryStore = new MemoryStore();
    private final AIKnowledgeStore knowledgeStore = new AIKnowledgeStore();
    private final AIOnboardingStore onboardingStore = new AIOnboardingStore();
    private final AIFallbackResponder fallbackResponder = new AIFallbackResponder();
    private boolean onboardingEnabled;
    private String onboardingCompletionMessage = "You're all set. You can ask me anything now.";
    private String onboardingInvalidChoiceMessage = "Pick one of the numbered options so I can guide you.";
    private final List<AIConfig.OnboardingStep> onboardingSteps = new ArrayList<>();

    public AIClient() {
        loadStory();
    }

    public String getWakeWord() {
        return settings.getWakeWord();
    }

    public String getDisplayName() {
        return settings.getPersonaName();
    }

    public boolean isAtlasEnabled() {
        return settings.isAtlasEnabled();
    }

    public boolean isAiInvocation(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains(settings.getWakeWord()) || fallbackResponder.findMentionedPersonaName(message).isPresent();
    }

    public String resolveSpeaker(String message) {
        return fallbackResponder.findMentionedPersonaName(message).orElse(settings.getPersonaName());
    }

    private void loadStory() {
        AIConfig config = AIConfigLoader.load();
        story.addAll(config.getStory());
        corruptedLore.addAll(config.getCorruptedData());
        backgroundHistory.addAll(config.getBackgroundHistory());
        applySettings(config);
        configureOnboarding(config.getOnboarding());
        fallbackResponder.configure(config.getFallback(), settings.getPersonaName(), settings.getWakeWord());
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
        if (configSettings.getAtlasEnabled() != null) {
            settings.setAtlasEnabled(configSettings.getAtlasEnabled());
        }
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

    private void configureOnboarding(AIConfig.Onboarding onboarding) {
        if (onboarding == null) {
            return;
        }
        onboardingEnabled = Boolean.TRUE.equals(onboarding.getEnabled());
        if (onboarding.getCompletionMessage() != null) {
            onboardingCompletionMessage = onboarding.getCompletionMessage();
        }
        if (onboarding.getInvalidChoiceMessage() != null) {
            onboardingInvalidChoiceMessage = onboarding.getInvalidChoiceMessage();
        }
        onboardingSteps.clear();
        onboardingSteps.addAll(onboarding.getSteps());
        if (onboardingEnabled && onboardingSteps.isEmpty()) {
            onboardingSteps.addAll(buildDefaultOnboardingSteps());
        }
    }

    /**
     * Adds the message to memory and returns a scripted reply.
     *
     * @param player  player name
     * @param message player message
     * @return AI reply
     */
    public String sendMessage(String player, String message) {
        return sendMessage(null, player, message);
    }

    /**
     * Adds the message to per-world memory and returns a scripted reply.
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
        return sendMessageWithVoice(world, player, message, AIFallbackResponder.ResponseContext.empty());
    }

    public VoiceIntegration.VoiceResult sendMessageWithVoice(String world, String player, String message,
                                                            AIFallbackResponder.ResponseContext responseContext) {
        if (!settings.isAtlasEnabled()) {
            return voiceIntegration.wrap(settings.getPersonaName(), "");
        }
        AIFallbackResponder.ResponseContext safeResponseContext =
                responseContext == null ? AIFallbackResponder.ResponseContext.empty() : responseContext;
        String speaker = resolveSpeaker(message);
        String learnedFact = knowledgeStore.extractLearnedFact(message);
        if (learnedFact != null) {
            boolean added = knowledgeStore.addFact(learnedFact);
            String reply = added
                    ? "Got it. I'll remember: " + learnedFact
                    : "I already have that logged: " + learnedFact;
            memoryStore.addAiMessage(world, player, speaker, reply);
            return voiceIntegration.wrap(speaker, reply);
        }
        memoryStore.addPlayerMessage(world, player, message);
        String knowledgeContext = knowledgeStore.getContextSnippet();
        if (isMemoryRecallRequest(message) && !knowledgeContext.isBlank()) {
            String reply = knowledgeContext.replace("Atlas learned:", "I have logged:");
            memoryStore.addAiMessage(world, player, speaker, reply);
            return voiceIntegration.wrap(speaker, reply);
        }
        var deterministicFallback = fallbackResponder.buildReply(message, safeResponseContext);
        String reply = deterministicFallback.map(AIFallbackResponder.FallbackReply::text)
                .orElse("Archive gap detected. I do not have a recovered answer for that yet.");
        String replySpeaker = deterministicFallback.map(AIFallbackResponder.FallbackReply::speaker).orElse(speaker);
        memoryStore.addAiMessage(world, player, replySpeaker, reply);
        return voiceIntegration.wrap(replySpeaker, reply);
    }

    public String handleOnboarding(UUID playerId, String message) {
        if (!onboardingEnabled || onboardingSteps.isEmpty() || playerId == null) {
            return null;
        }
        int stepIndex = onboardingStore.getStep(playerId);
        if (stepIndex >= onboardingSteps.size()) {
            return null;
        }
        AIConfig.OnboardingStep step = onboardingSteps.get(stepIndex);
        int choiceIndex = resolveChoiceIndex(step, message);
        if (choiceIndex < 0) {
            return buildOnboardingPrompt(step, onboardingInvalidChoiceMessage);
        }
        String response = resolveChoiceResponse(step, choiceIndex);
        int nextStepIndex = stepIndex + 1;
        if (nextStepIndex >= onboardingSteps.size()) {
            onboardingStore.setStep(playerId, onboardingSteps.size());
            return combineResponses(response, onboardingCompletionMessage);
        }
        onboardingStore.setStep(playerId, nextStepIndex);
        String nextPrompt = buildOnboardingPrompt(onboardingSteps.get(nextStepIndex), null);
        return combineResponses(response, nextPrompt);
    }

    private boolean isMemoryRecallRequest(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("what do you remember")
                || lower.contains("what have you learned")
                || lower.contains("learned facts")
                || lower.contains("memory log");
    }

    private int resolveChoiceIndex(AIConfig.OnboardingStep step, String message) {
        if (step == null || step.getChoices().isEmpty()) {
            return -1;
        }
        if (message == null) {
            return -1;
        }
        String trimmed = message.trim();
        if (trimmed.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < step.getChoices().size(); i++) {
            String option = step.getChoices().get(i);
            int optionNumber = i + 1;
            if (trimmed.equalsIgnoreCase(option) || trimmed.equals(String.valueOf(optionNumber))) {
                return i;
            }
            String lower = trimmed.toLowerCase(Locale.ROOT);
            if (lower.contains(option.toLowerCase(Locale.ROOT))) {
                return i;
            }
            if (lower.startsWith(optionNumber + ")") || lower.startsWith(optionNumber + ".")) {
                return i;
            }
        }
        return -1;
    }

    private String resolveChoiceResponse(AIConfig.OnboardingStep step, int choiceIndex) {
        if (step == null) {
            return "";
        }
        if (choiceIndex >= 0 && choiceIndex < step.getResponses().size()) {
            return step.getResponses().get(choiceIndex);
        }
        if (choiceIndex >= 0 && choiceIndex < step.getChoices().size()) {
            return "Logged: " + step.getChoices().get(choiceIndex) + ".";
        }
        return "";
    }

    private String buildOnboardingPrompt(AIConfig.OnboardingStep step, String extraLine) {
        if (step == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        if (extraLine != null && !extraLine.isBlank()) {
            builder.append(extraLine.trim()).append(" ");
        }
        if (step.getPrompt() != null && !step.getPrompt().isBlank()) {
            builder.append(step.getPrompt().trim());
        }
        if (!step.getChoices().isEmpty()) {
            builder.append(" ");
            for (int i = 0; i < step.getChoices().size(); i++) {
                if (i > 0) {
                    builder.append(" ");
                }
                builder.append(i + 1).append(") ").append(step.getChoices().get(i));
            }
        }
        return builder.toString().trim();
    }

    private String combineResponses(String first, String second) {
        String left = first == null ? "" : first.trim();
        String right = second == null ? "" : second.trim();
        if (left.isEmpty()) {
            return right;
        }
        if (right.isEmpty()) {
            return left;
        }
        return left + " " + right;
    }

    private List<AIConfig.OnboardingStep> buildDefaultOnboardingSteps() {
        List<AIConfig.OnboardingStep> steps = new ArrayList<>();
        steps.add(buildStep(
                "Welcome back to the surface. Which briefing do you want first?",
                List.of("Mission goals", "Supply checklist", "Hazard warnings"),
                List.of(
                        "Mission goals loaded: secure shelter, mark resources, and avoid deep craters.",
                        "Supply checklist loaded: water, rations, light sources, and repair tools.",
                        "Hazard warnings loaded: toxic dust, unstable debris, and rogue sensors.")));
        steps.add(buildStep(
                "Pick your expedition focus.",
                List.of("Exploration", "Rescue", "Research"),
                List.of(
                        "Exploration path set. I'll prioritize navigation tips and point-of-interest scans.",
                        "Rescue path set. I'll prioritize survivor signals and safe routes.",
                        "Research path set. I'll prioritize anomaly logs and artifact tracking.")));
        steps.add(buildStep(
                "How should I communicate?",
                List.of("Short updates", "Detailed reports", "Only when asked"),
                List.of(
                        "Short updates enabled.",
                        "Detailed reports enabled.",
                        "Silent standby enabled, I will respond only when addressed.")));
        return steps;
    }

    private AIConfig.OnboardingStep buildStep(String prompt, List<String> choices, List<String> responses) {
        AIConfig.OnboardingStep step = new AIConfig.OnboardingStep();
        step.setPrompt(prompt);
        if (choices != null) {
            step.getChoices().addAll(choices);
        }
        if (responses != null) {
            step.getResponses().addAll(responses);
        }
        return step;
    }



}
