package com.thunder.wildernessodysseyapi.ai.story;

import com.thunder.wildernessodysseyapi.ai.perf.MemoryStore;
import com.thunder.wildernessodysseyapi.ai.story.provider.OllamaChatClient;
import com.thunder.wildernessodysseyapi.async.AsyncTaskManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.server.MinecraftServer;

/**
 * Coordinates the central Aether intelligence, its local-model specialists,
 * conversation memory, and the scripted provider-failure responder.
 */
public class AIClient {

    private final List<String> story = new ArrayList<>();
    private final List<String> corruptedLore = new ArrayList<>();
    private final List<String> backgroundHistory = new ArrayList<>();
    private final List<String> authoritativeKnowledge = new ArrayList<>();
    private final List<String> knowledgeBoundaries = new ArrayList<>();
    private final AISettings settings = new AISettings();
    private final VoiceIntegration voiceIntegration = new VoiceIntegration();
    private final MemoryStore memoryStore = new MemoryStore();
    private final AIPlayerProfileStore playerProfileStore = new AIPlayerProfileStore();
    private final AIOnboardingStore onboardingStore = new AIOnboardingStore();
    private final AIFallbackResponder fallbackResponder = new AIFallbackResponder();
    private final OllamaChatClient ollamaChatClient = new OllamaChatClient();
    private AISubsystemRegistry subsystemRegistry = new AISubsystemRegistry("Aether", List.of());
    private boolean onboardingEnabled;
    private String onboardingCompletionMessage = "You're all set. You can ask me anything now.";
    private String onboardingInvalidChoiceMessage = "Pick one of the numbered options so I can guide you.";
    private final List<AIConfig.OnboardingStep> onboardingSteps = new ArrayList<>();
    private boolean playerMemoryEnabled = true;
    private boolean naturalPlayerLearningEnabled = true;
    private int maxPlayerMemories = AIPlayerProfileStore.DEFAULT_MAX_MEMORIES;

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
        return lower.contains(settings.getWakeWord()) || subsystemRegistry.findExplicitSpeaker(message).isPresent();
    }

    public String resolveSpeaker(String message) {
        return subsystemRegistry.findExplicitSpeaker(message).orElse(subsystemRegistry.centralName());
    }

    private void loadStory() {
        AIConfig config = AIConfigLoader.load();
        story.addAll(config.getStory());
        corruptedLore.addAll(config.getCorruptedData());
        backgroundHistory.addAll(config.getBackgroundHistory());
        authoritativeKnowledge.addAll(config.getAuthoritativeKnowledge());
        knowledgeBoundaries.addAll(config.getKnowledgeBoundaries());
        applySettings(config);
        configurePlayerMemory(config.getPlayerMemory());
        subsystemRegistry = new AISubsystemRegistry(settings.getPersonaName(), config.getSubsystems());
        configureOnboarding(config.getOnboarding());
        fallbackResponder.configure(config.getFallback(), settings.getPersonaName(), settings.getWakeWord());
    }

    public synchronized void scanGameData(MinecraftServer server) {
        if (!AIChatAccessPolicy.isAvailable(server) || !settings.isOllamaEnabled()) {
            return;
        }
        AsyncTaskManager.trySubmitIoWork("Aether_Ollama_Warmup", () -> ollamaChatClient.warmUp(settings));
    }

    private void applySettings(AIConfig config) {
        if (config == null) {
            return;
        }
        AIConfig.Settings configSettings = config.getSettings();
        if (configSettings.getAtlasEnabled() != null) {
            settings.setAtlasEnabled(configSettings.getAtlasEnabled());
        }
        if (configSettings.getWakeWord() != null) {
            settings.setWakeWord(configSettings.getWakeWord());
        }
        if (configSettings.getProvider() != null) {
            settings.setProvider(configSettings.getProvider());
        }
        if (configSettings.getEndpoint() != null) {
            settings.setEndpoint(configSettings.getEndpoint());
        }
        if (configSettings.getModel() != null) {
            settings.setModelName(configSettings.getModel());
        }
        if (configSettings.getRequestTimeoutSeconds() != null) {
            settings.setRequestTimeoutSeconds(configSettings.getRequestTimeoutSeconds());
        }
        if (configSettings.getMaxHistoryMessages() != null) {
            settings.setMaxHistoryMessages(configSettings.getMaxHistoryMessages());
        }
        if (configSettings.getMaxResponseCharacters() != null) {
            settings.setMaxResponseCharacters(configSettings.getMaxResponseCharacters());
        }
        if (configSettings.getMaxOutputTokens() != null) {
            settings.setMaxOutputTokens(configSettings.getMaxOutputTokens());
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

    private void configurePlayerMemory(AIConfig.PlayerMemory playerMemory) {
        if (playerMemory == null) {
            return;
        }
        if (playerMemory.getEnabled() != null) {
            playerMemoryEnabled = playerMemory.getEnabled();
        }
        if (playerMemory.getNaturalLearningEnabled() != null) {
            naturalPlayerLearningEnabled = playerMemory.getNaturalLearningEnabled();
        }
        if (playerMemory.getMaxMemoriesPerPlayer() != null) {
            maxPlayerMemories = Math.max(
                    1,
                    Math.min(AIPlayerProfileStore.HARD_MAX_MEMORIES, playerMemory.getMaxMemoriesPerPlayer())
            );
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
        return sendMessageWithVoice(world, player, player, message, responseContext);
    }

    public VoiceIntegration.VoiceResult sendMessageWithVoice(
            String world,
            String playerProfileKey,
            String player,
            String message,
            AIFallbackResponder.ResponseContext responseContext
    ) {
        if (!settings.isAtlasEnabled()) {
            return voiceIntegration.wrap(settings.getPersonaName(), "");
        }
        AIFallbackResponder.ResponseContext safeResponseContext =
                responseContext == null ? AIFallbackResponder.ResponseContext.empty() : responseContext;
        Optional<String> requiredSpeaker = subsystemRegistry.findExplicitSpeaker(message);
        String speaker = requiredSpeaker.orElse(subsystemRegistry.centralName());

        // Profile deletion stays available even when learning is disabled so
        // the privacy control can always remove previously stored details.
        if (AIPlayerProfileStore.isForgetRequest(message)) {
            memoryStore.addPlayerMessage(world, player, message);
            boolean removed = playerProfileStore.clear(playerProfileKey);
            String reply = removed
                    ? "I've cleared the personal details you shared with me. We can start fresh."
                    : "I don't have a saved profile for you, so there was nothing to forget.";
            memoryStore.addAiMessage(world, player, speaker, reply);
            return voiceIntegration.wrap(speaker, reply);
        }

        AIPlayerProfileStore.LearningResult learning = playerMemoryEnabled
                ? playerProfileStore.learn(
                        playerProfileKey,
                        message,
                        naturalPlayerLearningEnabled,
                        maxPlayerMemories
                )
                : AIPlayerProfileStore.LearningResult.none();
        if (learning.explicitRequest()) {
            memoryStore.addPlayerMessage(world, player, message);
            String reply;
            if (!learning.accepted()) {
                reply = learning.rejectionMessage();
            } else if (learning.changed()) {
                reply = "I'll remember that about you: " + learning.memory() + ".";
            } else {
                reply = "I already remember that about you: " + learning.memory() + ".";
            }
            memoryStore.addAiMessage(world, player, speaker, reply);
            return voiceIntegration.wrap(speaker, reply);
        }

        memoryStore.addPlayerMessage(world, player, message);
        String playerProfileContext = playerMemoryEnabled
                ? playerProfileStore.getContextSnippet(playerProfileKey, maxPlayerMemories)
                : "";
        if (AIPlayerProfileStore.isRecallRequest(message)) {
            String reply = playerMemoryEnabled
                    ? playerProfileStore.describeForPlayer(playerProfileKey, maxPlayerMemories)
                    : "Player profile memory is disabled in the local Aether configuration.";
            memoryStore.addAiMessage(world, player, speaker, reply);
            return voiceIntegration.wrap(speaker, reply);
        }

        if (settings.isOllamaEnabled()) {
            String systemPrompt = AetherSystemPrompt.build(
                    settings,
                    story,
                    backgroundHistory,
                    corruptedLore,
                    authoritativeKnowledge,
                    knowledgeBoundaries,
                    subsystemRegistry,
                    requiredSpeaker.orElse(""),
                    safeResponseContext,
                    playerProfileContext
            );
            List<MemoryStore.ConversationMessage> history = memoryStore.getRecentMessages(
                    world,
                    player,
                    settings.getMaxHistoryMessages()
            );
            OllamaChatClient.ModelResponse modelResponse = ollamaChatClient.generate(
                    settings,
                    systemPrompt,
                    requiredSpeaker.orElse(""),
                    subsystemRegistry.allowedSpeakers(),
                    subsystemRegistry.centralName(),
                    history
            );
            if (modelResponse.successful()) {
                speaker = subsystemRegistry.canonicalOrCentral(modelResponse.speaker());
                String verifierPrompt = AetherSystemPrompt.buildVerifier(
                        story,
                        backgroundHistory,
                        corruptedLore,
                        authoritativeKnowledge,
                        knowledgeBoundaries,
                        speaker,
                        subsystemRegistry.profileFor(speaker).orElse(null),
                        safeResponseContext,
                        playerProfileContext,
                        message,
                        modelResponse.displayText(),
                        modelResponse.speechText()
                );
                OllamaChatClient.VerificationResponse verification =
                        ollamaChatClient.verify(settings, verifierPrompt);
                String verifiedReply = verification.successful() && verification.approved()
                        ? modelResponse.text()
                        : safeUngroundedReply(message);
                memoryStore.addAiMessage(world, player, speaker, verifiedReply);
                if (verification.successful() && verification.approved()) {
                    return voiceIntegration.wrap(
                            speaker,
                            modelResponse.displayText(),
                            modelResponse.speechText(),
                            modelResponse.emotion(),
                            modelResponse.radioEffect()
                    );
                }
                return voiceIntegration.wrap(speaker, verifiedReply);
            }
        }

        // Scripted intent matching is an availability fallback, not the normal
        // conversation authority. It runs only when Ollama is disabled or the
        // local request did not produce a usable reply.
        Optional<AIFallbackResponder.FallbackReply> authoredReply =
                fallbackResponder.buildReply(message, safeResponseContext);
        speaker = authoredReply.map(AIFallbackResponder.FallbackReply::speaker)
                .map(subsystemRegistry::canonicalOrCentral)
                .orElse(speaker);
        String reply = authoredReply.map(AIFallbackResponder.FallbackReply::text)
                .orElse("Archive gap detected. I do not have a recovered answer for that yet.");
        if (settings.isOllamaEnabled()) {
            reply = fallbackResponder.appendUnavailableHint(reply);
        }
        memoryStore.addAiMessage(world, player, speaker, reply);
        return voiceIntegration.wrap(speaker, reply);
    }

    private static String safeUngroundedReply(String message) {
        String lower = message == null ? "" : message.trim().toLowerCase(Locale.ROOT);
        if (lower.equals("hi") || lower.equals("hello") || lower.equals("hey")
                || lower.equals("idk") || lower.equals("ok") || lower.equals("okay")
                || lower.contains("how are you") || lower.contains("how is your day")
                || lower.contains("thank you") || lower.contains("thanks")) {
            return "I'm here and operational, if a little fragmented. What would you like to talk about?";
        }
        return "I don't have a recovered record for that. If you find field evidence, I can help interpret it.";
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
