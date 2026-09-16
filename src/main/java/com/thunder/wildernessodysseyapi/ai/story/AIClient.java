package com.thunder.wildernessodysseyapi.ai.story;

import com.thunder.wildernessodysseyapi.ai.perf.MemoryStore;
import com.thunder.wildernessodysseyapi.ai.story.provider.AetherBackendClient;
import com.thunder.wildernessodysseyapi.ai.story.provider.AetherRequest;
import com.thunder.wildernessodysseyapi.ai.story.provider.BackendStatus;
import com.thunder.wildernessodysseyapi.async.AsyncTaskManager;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/**
 * Owns game-side conversation memory, onboarding, routing and deterministic fallback.
 * Permanent model prompts and verification belong to the standalone Aether service.
 */
public class AIClient implements AutoCloseable {
    private final AISettings settings = new AISettings();
    private final VoiceIntegration voiceIntegration = new VoiceIntegration();
    private final MemoryStore memoryStore = new MemoryStore();
    private final AIPlayerProfileStore playerProfileStore;
    private final AIOnboardingStore onboardingStore;
    private final AIFallbackResponder fallbackResponder = new AIFallbackResponder();
    private final AetherBackendClient backend;
    private final AISubsystemRegistry subsystemRegistry;
    private volatile Thread serverThread;
    private volatile boolean active;
    private boolean onboardingEnabled;
    private String onboardingCompletionMessage = "You're all set. You can ask me anything now.";
    private String onboardingInvalidChoiceMessage = "Pick one of the numbered options so I can guide you.";
    private final List<AIConfig.OnboardingStep> onboardingSteps = new ArrayList<>();
    private boolean playerMemoryEnabled = true;
    private boolean naturalPlayerLearningEnabled;
    private int maxPlayerMemories = AIPlayerProfileStore.DEFAULT_MAX_MEMORIES;

    /** Loads only server-owned configuration; no model process or network starts here. */
    public AIClient() {
        this(AIConfigLoader.load(), FMLPaths.CONFIGDIR.get(), null);
    }

    /** Injectable state and network guard for offline integration tests. */
    AIClient(AIConfig config, Path stateDirectory, BooleanSupplier networkGuard) {
        playerProfileStore = new AIPlayerProfileStore(stateDirectory.resolve("aether_player_profiles.yaml"));
        onboardingStore = new AIOnboardingStore(stateDirectory.resolve("ai_onboarding.yaml"));
        applySettings(config);
        configurePlayerMemory(config.getPlayerMemory());
        subsystemRegistry = new AISubsystemRegistry(settings.getPersonaName(), config.getSubsystems());
        configureOnboarding(config.getOnboarding());
        fallbackResponder.configure(config.getFallback(), settings.getPersonaName(), settings.getWakeWord());
        backend = new AetherBackendClient(settings.getBackend(), networkGuard == null
                ? () -> active && Thread.currentThread() != serverThread : networkGuard);
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

    /** Ordinary multiplayer conversation is never forwarded unless Aether is addressed. */
    public boolean isAiInvocation(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return java.util.regex.Pattern.compile("(?<![\\p{L}\\p{N}_])"
                        + java.util.regex.Pattern.quote(settings.getWakeWord()) + "(?![\\p{L}\\p{N}_])")
                .matcher(lower).find() || subsystemRegistry.findExplicitSpeaker(message).isPresent();
    }

    public String resolveSpeaker(String message) {
        return subsystemRegistry.findExplicitSpeaker(message).orElse(subsystemRegistry.centralName());
    }

    /** Records the owning tick thread, then checks the gateway on the bounded shared I/O pool. */
    public void scanGameData(MinecraftServer server) {
        if (!AIChatAccessPolicy.isAvailable(server)) {
            return;
        }
        serverThread = Thread.currentThread();
        active = true;
        if (settings.isAtlasEnabled() && settings.getBackend().enabled()) {
            AsyncTaskManager.trySubmitIoWork("Aether_Backend_Health", backend::checkHealth);
        }
    }

    /** Returns a cached observation without touching the network. */
    public BackendStatus getBackendStatus() {
        return backend.status();
    }

    private void applySettings(AIConfig config) {
        AIConfig.Settings configured = config.getSettings();
        if (configured.getAtlasEnabled() != null) {
            settings.setAtlasEnabled(configured.getAtlasEnabled());
        }
        if (configured.getWakeWord() != null) {
            settings.setWakeWord(configured.getWakeWord());
        }
        if (configured.getMaxHistoryMessages() != null) {
            settings.setMaxHistoryMessages(configured.getMaxHistoryMessages());
        }
        if (configured.getMaxResponseCharacters() != null) {
            settings.setMaxResponseCharacters(configured.getMaxResponseCharacters());
        }
        settings.setBackend(config.getBackend());
        AIConfig.Personality personality = config.getPersonality();
        settings.setPersonaName(personality.getName());
        settings.setPersonalityTone(personality.getTone());
        settings.setEmpathyLevel(personality.getEmpathy());
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
        onboardingSteps.addAll(onboarding.getSteps());
        if (onboardingEnabled && onboardingSteps.isEmpty()) {
            onboardingSteps.addAll(buildDefaultOnboardingSteps());
        }
    }

    private void configurePlayerMemory(AIConfig.PlayerMemory playerMemory) {
        if (playerMemory.getEnabled() != null) {
            playerMemoryEnabled = playerMemory.getEnabled();
        }
        if (playerMemory.getNaturalLearningEnabled() != null) {
            naturalPlayerLearningEnabled = playerMemory.getNaturalLearningEnabled();
        }
        if (playerMemory.getMaxMemoriesPerPlayer() != null) {
            maxPlayerMemories = Math.max(1, Math.min(AIPlayerProfileStore.HARD_MAX_MEMORIES,
                    playerMemory.getMaxMemoriesPerPlayer()));
        }
    }

    /** Compatibility entry point for callers already running on an I/O worker. */
    public String sendMessage(String player, String message) {
        return sendMessage(null, player, message);
    }

    /** Compatibility entry point for callers already running on an I/O worker. */
    public String sendMessage(String world, String player, String message) {
        return sendMessageWithVoice(world, player, message).text();
    }

    public VoiceIntegration.VoiceResult sendMessageWithVoice(String world, String player, String message) {
        return sendMessageWithVoice(world, player, message, AIFallbackResponder.ResponseContext.empty());
    }

    public VoiceIntegration.VoiceResult sendMessageWithVoice(String world, String player, String message,
                                                            AIFallbackResponder.ResponseContext context) {
        return sendMessageWithVoice(world, player, player, message, context);
    }

    public VoiceIntegration.VoiceResult sendMessageWithVoice(String world, String profileKey, String player,
                                                            String message, AIFallbackResponder.ResponseContext context) {
        UUID compatibilityId = UUID.nameUUIDFromBytes(("aether:" + player).getBytes(StandardCharsets.UTF_8));
        return sendMessageWithVoice(world, profileKey, compatibilityId, player, message, context);
    }

    /**
     * Processes captured values on a worker; no Minecraft objects are accessed here.
     * Profile notes stay local unless the operator explicitly enables sharing them.
     */
    public VoiceIntegration.VoiceResult sendMessageWithVoice(String world, String profileKey, UUID playerId,
            String player, String message, AIFallbackResponder.ResponseContext responseContext) {
        if (!settings.isAtlasEnabled()) {
            return voiceIntegration.wrap(settings.getPersonaName(), "");
        }
        AIFallbackResponder.ResponseContext context = responseContext == null
                ? AIFallbackResponder.ResponseContext.empty() : responseContext;
        String speaker = resolveSpeaker(message);
        // The save/player key is local-only. Neither its filesystem identity nor hash is transmitted.
        String conversationKey = profileKey == null ? playerId.toString() : profileKey;
        if (AIPlayerProfileStore.isForgetRequest(message)) {
            memoryStore.clearPlayer(conversationKey);
            boolean removed = playerProfileStore.clear(profileKey);
            return voiceIntegration.wrap(speaker, removed
                    ? "I've cleared the personal details you shared with me. We can start fresh."
                    : "I've cleared our recent conversation. I don't have a saved profile for you.");
        }

        AIPlayerProfileStore.LearningResult learning = playerMemoryEnabled
                ? playerProfileStore.learn(profileKey, message, naturalPlayerLearningEnabled, maxPlayerMemories)
                : AIPlayerProfileStore.LearningResult.none();
        if (learning.explicitRequest()) {
            String reply = !learning.accepted() ? learning.rejectionMessage()
                    : learning.changed() ? "I'll remember that about you: " + learning.memory() + "."
                    : "I already remember that about you: " + learning.memory() + ".";
            // Durable profile exchanges are intentionally excluded from outgoing conversation history.
            return voiceIntegration.wrap(speaker, reply);
        }
        if (AIPlayerProfileStore.isRecallRequest(message)) {
            return voiceIntegration.wrap(speaker, playerMemoryEnabled
                    ? playerProfileStore.describeForPlayer(profileKey, maxPlayerMemories)
                    : "Player profile memory is disabled in the Aether configuration.");
        }

        List<MemoryStore.ConversationMessage> history =
                memoryStore.getRecentMessages(world, conversationKey, settings.getMaxHistoryMessages());
        memoryStore.addPlayerMessage(world, conversationKey, message);
        String profile = settings.getBackend().sendPlayerMemory() && playerMemoryEnabled
                ? playerProfileStore.getContextSnippet(profileKey, maxPlayerMemories) : "";
        List<String> tags = context.tags().stream().limit(128).toList();
        AetherRequest.Context dynamicContext = new AetherRequest.Context(
                world == null ? "minecraft:overworld" : world,
                contextValue(tags, "biome:", ""), tags.contains("surface"),
                contextValue(tags, "interface:", "server_chat"), tags, profile);
        AetherRequest request = new AetherRequest(UUID.randomUUID().toString(), settings.getBackend().serverId(),
                dynamicContext.dimension(), playerId.toString(), player,
                subsystemRegistry.findExplicitSpeaker(message).orElse(""), message, dynamicContext,
                history.stream().map(line -> new AetherRequest.HistoryMessage(
                        line.role() == MemoryStore.Role.PLAYER ? "user" : "assistant",
                        line.speaker(), line.text())).toList());
        AetherBackendClient.ModelResponse model = backend.generate(
                request, subsystemRegistry.allowedSpeakers(), settings.getMaxResponseCharacters());
        if (model.successful()) {
            memoryStore.addAiMessage(world, conversationKey, model.speaker(), model.displayText());
            return voiceIntegration.wrap(model.speaker(), model.displayText(), model.speechText(),
                    model.emotion(), model.radioEffect());
        }
        VoiceIntegration.VoiceResult fallback = fallback(message, context);
        memoryStore.addAiMessage(world, conversationKey, fallback.speaker(), fallback.text());
        return fallback;
    }

    /** Pure, deterministic response for rejected jobs, outages and model verification failures. */
    public VoiceIntegration.VoiceResult fallback(String message, AIFallbackResponder.ResponseContext context) {
        Optional<AIFallbackResponder.FallbackReply> authored = fallbackResponder.buildReply(message, context);
        String speaker = authored.map(AIFallbackResponder.FallbackReply::speaker)
                .map(subsystemRegistry::canonicalOrCentral).orElseGet(() -> resolveSpeaker(message));
        String text = authored.map(AIFallbackResponder.FallbackReply::text)
                .orElse("Archive gap detected. I do not have a recovered answer for that yet.");
        if (settings.getBackend().enabled()) {
            text = fallbackResponder.appendUnavailableHint(text);
        }
        return voiceIntegration.wrap(speaker, text);
    }

    private static String contextValue(List<String> tags, String prefix, String defaultValue) {
        return tags.stream().filter(tag -> tag.startsWith(prefix)).map(tag -> tag.substring(prefix.length()))
                .findFirst().orElse(defaultValue);
    }

    /** Invalidates pending network work and transient conversations when the server session stops. */
    @Override
    public void close() {
        active = false;
        backend.close();
        memoryStore.close();
    }


    /** Reads cached progress only; persistence happens in the worker-side handler. */
    public boolean hasPendingOnboarding(UUID playerId) {
        return onboardingEnabled && playerId != null && onboardingStore.getStep(playerId) < onboardingSteps.size();
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
